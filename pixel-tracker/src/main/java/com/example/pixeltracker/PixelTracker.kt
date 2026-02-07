package com.example.pixeltracker

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Keep
class PixelTracker @JvmOverloads constructor(
    context: Context,
    private val pixelId: String,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Публичные свойства
    var visibilityThreshold: Int = 1
    var isDebugMode: Boolean = false
    var checkInterval: Long = 500L // Интервал автоматической проверки
    var refreshTime: Long = 0L // 0 = считать только при появлении, >0 = считать через указанное время
        set(value) {
            field = max(0L, value) // Минимум 0
        }
    var pixelSize: Int = 1 // Размер пикселя по умолчанию 1x1
        set(value) {
            field = max(1, value) // Минимум 1
        }

    // Приватные свойства
    private val tag: String = "PixelTracker"
    private var wasVisible = false
    private var visibilityCount = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var checkJob: Job? = null
    private var refreshTimerJob: Job? = null
    private var isTracking = false
    private var continuousVisibilityStartTime: Long = 0
    private var nextRefreshTime: Long = 0
    private var isContinuousTracking = false

    // Получение версии библиотеки
    private val libraryVersion: String by lazy {
        try {
            BuildConfig.LIBRARY_VERSION
        } catch (e: Exception) {
            "unknown"
        }
    }

    // Интерфейс логирования
    interface PixelLogger {
        fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>)
        fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>)
        fun logError(pixelId: String, error: String, timestamp: String)
        fun logRefresh(pixelId: String, timestamp: String, metadata: Map<String, Any>)
    }

    private var logger: PixelLogger = DefaultPixelLogger()

    private inner class DefaultPixelLogger : PixelLogger {
        override fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
            Log.d("PixelTracker", "[$libraryVersion] 🔵 Pixel appeared: $pixelId at $timestamp")
        }

        override fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
            Log.d("PixelTracker", "[$libraryVersion] 🔴 Pixel disappeared: $pixelId at $timestamp")
        }

        override fun logError(pixelId: String, error: String, timestamp: String) {
            Log.e("PixelTracker", "[$libraryVersion] ❌ Pixel $pixelId error: $error at $timestamp")
        }

        override fun logRefresh(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
            Log.d("PixelTracker", "[$libraryVersion] 🔄 Pixel refresh counted: $pixelId at $timestamp")
        }
    }

    init {
        setupView()
        logInitialization()
    }

    private fun setupView() {
        setBackgroundColor(0x00000000)
        minimumWidth = 1
        minimumHeight = 1
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * Логирование инициализации класса
     */
    private fun logInitialization() {
        Log.d(tag, "[$libraryVersion] PixelTracker initialized - Pixel ID: $pixelId, Size: ${pixelSize}x$pixelSize, Refresh: ${refreshTime}ms")
    }

    /**
     * Начинает отслеживание видимости пикселя
     */
    fun startTracking() {
        if (isTracking) return

        isTracking = true
        isContinuousTracking = false
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0

        // Запускаем периодическую проверку
        startPeriodicCheck()

        // Первая проверка после отрисовки
        post { checkVisibility() }

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Starting pixel tracking - ID: $pixelId, Refresh: ${refreshTime}ms, Size: ${pixelSize}x$pixelSize, Interval: ${checkInterval}ms")
        }
    }

    /**
     * Останавливает отслеживание
     */
    fun stopTracking() {
        if (!isTracking) return

        isTracking = false
        wasVisible = false
        isContinuousTracking = false
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0

        // Останавливаем периодическую проверку и таймер
        stopPeriodicCheck()
        stopRefreshTimer()

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Stopping pixel tracking - ID: $pixelId, Total: $visibilityCount")
        }
    }

    /**
     * Запускает периодическую проверку видимости
     */
    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = coroutineScope.launch {
            while (isTracking && isAttachedToWindow) {
                delay(checkInterval)
                if (isShown && isVisible) {
                    checkVisibility()
                }
            }
        }

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Periodic check started - Interval: ${checkInterval}ms")
        }
    }

    /**
     * Останавливает периодическую проверку
     */
    private fun stopPeriodicCheck() {
        checkJob?.cancel()
        checkJob = null

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Periodic check stopped")
        }
    }

    /**
     * Запускает таймер для отслеживания времени показа (только если refreshTime > 0)
     */
    private fun startRefreshTimer() {
        if (refreshTime <= 0) return // Не запускаем таймер если refreshTime = 0

        refreshTimerJob?.cancel()
        refreshTimerJob = coroutineScope.launch {
            val currentTime = System.currentTimeMillis()
            val timeUntilRefresh = nextRefreshTime - currentTime

            if (timeUntilRefresh > 0) {
                delay(timeUntilRefresh)
            }

            // Таймер сработал - засчитываем повторный показ
            if (isContinuousTracking && isActuallyVisible() && isTracking) {
                visibilityCount++
                continuousVisibilityStartTime = System.currentTimeMillis()
                nextRefreshTime = continuousVisibilityStartTime + refreshTime

                logger.logRefresh(pixelId, getTimestamp(), getRefreshMetadata())

                if (isDebugMode) {
                    Log.d(tag, "[$libraryVersion] Refresh counted - ID: $pixelId, Total: $visibilityCount, Next: ${refreshTime}ms")
                }

                // Перезапускаем таймер для следующего интервала
                startRefreshTimer()
            }
        }
    }

    /**
     * Останавливает таймер
     */
    private fun stopRefreshTimer() {
        refreshTimerJob?.cancel()
        refreshTimerJob = null
    }

    /**
     * Проверяет видимость пикселя
     */
    private fun checkVisibility() {
        val isVisibleNow = isActuallyVisible()

        if (isVisibleNow && !wasVisible) {
            // Пиксель стал видимым - засчитываем показ
            visibilityCount++
            wasVisible = true
            isContinuousTracking = true

            if (refreshTime > 0) {
                continuousVisibilityStartTime = System.currentTimeMillis()
                nextRefreshTime = continuousVisibilityStartTime + refreshTime
            } else {
                continuousVisibilityStartTime = 0
                nextRefreshTime = 0
            }

            logger.logAppearance(pixelId, getTimestamp(), getVisibilityMetadata(true))

            if (isDebugMode) {
                Log.d(tag, "[$libraryVersion] Pixel appeared - ID: $pixelId, Total: $visibilityCount, Refresh: ${if (refreshTime > 0) "enabled" else "disabled"}")
            }

            // Запускаем таймер только если refreshTime > 0
            if (refreshTime > 0) {
                startRefreshTimer()
            }

        } else if (!isVisibleNow && wasVisible) {
            // Пиксель стал невидимым - сбрасываем непрерывный трекинг
            wasVisible = false
            isContinuousTracking = false
            continuousVisibilityStartTime = 0
            nextRefreshTime = 0

            logger.logDisappearance(pixelId, getTimestamp(), getVisibilityMetadata(false))

            if (isDebugMode) {
                Log.d(tag, "[$libraryVersion] Pixel disappeared - ID: $pixelId, Total: $visibilityCount")
            }

            // Останавливаем таймер
            stopRefreshTimer()

        } else if (isVisibleNow && wasVisible && isContinuousTracking && refreshTime > 0) {
            // Пиксель продолжает быть видимым и refreshTime > 0
            // Проверяем, не настало ли время для следующего показа
            val currentTime = System.currentTimeMillis()
            if (currentTime >= nextRefreshTime) {
                visibilityCount++
                continuousVisibilityStartTime = currentTime
                nextRefreshTime = continuousVisibilityStartTime + refreshTime

                logger.logRefresh(pixelId, getTimestamp(), getRefreshMetadata())

                if (isDebugMode) {
                    Log.d(tag, "[$libraryVersion] Automatic refresh - ID: $pixelId, Total: $visibilityCount, Next: ${refreshTime}ms")
                }
            }
        }
    }

    /**
     * Определяет, виден ли пиксель на экране
     */
    private fun isActuallyVisible(): Boolean {
        if (!isAttachedToWindow || !isShown || !isVisible || alpha <= 0.01f) {
            return false
        }

        // Проверяем глобальную видимую область
        val visibleRect = Rect()
        val hasVisibleRect = getGlobalVisibleRect(visibleRect)

        if (!hasVisibleRect) return false

        // Проверяем размер видимой области
        val isBigEnough = visibleRect.width() >= visibilityThreshold &&
                visibleRect.height() >= visibilityThreshold

        // Проверяем, находится ли пиксель в пределах экрана
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val location = IntArray(2)
        getLocationOnScreen(location)

        val isOnScreen = location[0] < screenWidth &&
                location[0] + width > 0 &&
                location[1] < screenHeight &&
                location[1] + height > 0

        return isBigEnough && isOnScreen
    }

    /**
     * Получает метаданные о видимости
     */
    private fun getVisibilityMetadata(isVisible: Boolean): Map<String, Any> {
        val location = IntArray(2)
        getLocationOnScreen(location)

        val visibleRect = Rect()
        val hasVisibleRect = getGlobalVisibleRect(visibleRect)

        return mapOf(
            "library_version" to libraryVersion,
            "visible" to isVisible,
            "total_appearances" to visibilityCount,
            "refresh_time" to refreshTime,
            "refresh_enabled" to (refreshTime > 0),
            "is_continuous_tracking" to isContinuousTracking,
            "continuous_time" to if (continuousVisibilityStartTime > 0)
                System.currentTimeMillis() - continuousVisibilityStartTime
            else 0,
            "next_refresh_in" to if (nextRefreshTime > 0)
                nextRefreshTime - System.currentTimeMillis()
            else 0,
            "dimensions" to "${width}x${height}",
            "position" to "[${location[0]}, ${location[1]}]",
            "visible_rect" to if (hasVisibleRect)
                "[${visibleRect.left}, ${visibleRect.top}, ${visibleRect.right}, ${visibleRect.bottom}]"
            else "null",
            "alpha" to alpha,
            "is_shown" to isShown,
            "is_visible" to this.isVisible,
            "has_window_focus" to hasWindowFocus(),
            "is_attached" to isAttachedToWindow,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Получает метаданные для refresh события
     */
    private fun getRefreshMetadata(): Map<String, Any> {
        return mapOf(
            "library_version" to libraryVersion,
            "total_appearances" to visibilityCount,
            "refresh_time" to refreshTime,
            "continuous_time" to if (continuousVisibilityStartTime > 0)
                System.currentTimeMillis() - continuousVisibilityStartTime
            else 0,
            "next_refresh_in" to refreshTime, // Следующий refresh через полный интервал
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Устанавливает кастомный логгер
     */
    fun setCustomLogger(customLogger: PixelLogger) {
        this.logger = customLogger
    }

    /**
     * Получает статистику
     */
    fun getStats(): Map<String, Any> = mapOf(
        "library_version" to libraryVersion,
        "pixelId" to pixelId,
        "totalAppearances" to visibilityCount,
        "isCurrentlyVisible" to isActuallyVisible(),
        "isTracking" to isTracking,
        "checkInterval" to checkInterval,
        "refreshTime" to refreshTime,
        "refreshEnabled" to (refreshTime > 0),
        "pixelSize" to pixelSize,
        "isDebugMode" to isDebugMode,
        "isContinuousTracking" to isContinuousTracking,
        "continuousVisibilityTime" to if (continuousVisibilityStartTime > 0)
            System.currentTimeMillis() - continuousVisibilityStartTime
        else 0,
        "nextRefreshIn" to if (nextRefreshTime > 0)
            nextRefreshTime - System.currentTimeMillis()
        else 0
    )

    /**
     * Принудительно проверяет видимость (публичный метод)
     */
    fun checkVisibilityNow() {
        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Manual visibility check requested")
        }
        checkVisibility()
    }

    /**
     * Сбрасывает счетчик показов
     */
    fun resetCount() {
        visibilityCount = 0
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0
        isContinuousTracking = false

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Counter reset - ID: $pixelId")
        }
    }

    private fun getTimestamp(): String {
        return System.currentTimeMillis().toString()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Размер зависит от режима отладки
        val size = if (isDebugMode) pixelSize else 1
        setMeasuredDimension(size, size)

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Pixel measured - Size: ${size}x${size}px")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Attached to window")
        }

        if (isTracking) {
            // Перезапускаем отслеживание при повторном присоединении
            startPeriodicCheck()
        }
    }

    override fun onDetachedFromWindow() {
        stopTracking()
        coroutineScope.cancel()

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Detached from window")
        }

        super.onDetachedFromWindow()
    }

    companion object {
        /**
         * Создает экземпляр PixelTracker
         */
        @JvmStatic
        fun create(
            context: Context,
            pixelId: String,
            refreshTimeSeconds: Long = 0L, // По умолчанию 0 - считать только при появлении
            pixelSize: Int = 1 // По умолчанию 1x1
        ): PixelTracker {
            return PixelTracker(context, pixelId).apply {
                this.refreshTime = refreshTimeSeconds * 1000 // Конвертируем секунды в миллисекунды
                this.pixelSize = pixelSize
            }
        }

        /**
         * Быстрая проверка видимости любого View
         */
        @JvmStatic
        fun isViewVisibleOnScreen(view: View, threshold: Int = 1): Boolean {
            if (!view.isAttachedToWindow || !view.isShown || !view.isVisible || view.alpha <= 0.01f) {
                return false
            }

            val visibleRect = Rect()
            val hasVisibleRect = view.getGlobalVisibleRect(visibleRect)

            return hasVisibleRect &&
                    visibleRect.width() >= threshold &&
                    visibleRect.height() >= threshold
        }
    }
}