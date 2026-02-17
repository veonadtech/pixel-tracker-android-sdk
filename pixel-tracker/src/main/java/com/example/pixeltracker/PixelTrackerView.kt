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
class PixelTrackerView @JvmOverloads constructor(
    context: Context,
    private val pixelId: String,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Публичные свойства
    var visibilityThreshold: Int = 1
    var isDebugMode: Boolean = false
        set(value) {
            field = value
            updateLoggerDebugMode()
        }
    var checkInterval: Long = 3000L // Интервал автоматической проверки
    var refreshTime: Long = 0L // 0 = считать только при появлении, >0 = считать через указанное время
        set(value) {
            field = max(0L, value)
        }
    var pixelSize: Int = 1
        set(value) {
            field = max(1, value)
        }

    // Интерфейс для слушателя событий
    interface PixelEventListener {
        fun onAppearance(pixelId: String, timestamp: String)
        fun onDisappearance(pixelId: String, timestamp: String)
        fun onRefresh(pixelId: String, timestamp: String)
        fun onError(pixelId: String, error: String, timestamp: String)
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
    private var eventListener: PixelEventListener? = null

    private val libraryVersion: String = try {
        BuildConfig.LIBRARY_VERSION
    } catch (_: Exception) {
        "unknown"
    }

    private var logger: PixelLogger = createDefaultLogger()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Размер зависит от режима отладки
        val size = if (isDebugMode) pixelSize else 1
        setMeasuredDimension(size, size)
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

    init {
        setupView()
        logInitialization()
    }

    fun setEventListener(listener: PixelEventListener?) {
        this.eventListener = listener
    }

    private fun setupView() {
        setBackgroundColor(0x00000000)
        minimumWidth = 1
        minimumHeight = 1
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun createDefaultLogger(): PixelLogger {
        val defaultLogger = DefaultPixelLogger().apply {
            this.isDebugMode = this@PixelTrackerView.isDebugMode
        }
        return NetworkPixelLogger(defaultLogger)
    }

    private fun updateLoggerDebugMode() {
        when (val currentLogger = logger) {
            is NetworkPixelLogger -> {
                (currentLogger.delegate as? DefaultPixelLogger)?.isDebugMode = isDebugMode
            }
            is DefaultPixelLogger -> {
                currentLogger.isDebugMode = isDebugMode
            }
        }
    }

    /**
     * Логирование инициализации класса
     */
    private fun logInitialization() {
        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] PixelTracker initialized - Pixel ID: $pixelId, Size: ${pixelSize}x$pixelSize, Refresh: ${refreshTime}ms")
        }
    }

    fun startTracking() {
        if (isTracking) return

        isTracking = true
        isContinuousTracking = false
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0

        startPeriodicCheck()

        post { checkPixelVisibility() }

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Starting pixel tracking - ID: $pixelId, Refresh: ${refreshTime}ms, Size: ${pixelSize}x$pixelSize, Interval: ${checkInterval}ms")
        }
    }

    fun stopTracking() {
        if (!isTracking) return

        isTracking = false
        wasVisible = false
        isContinuousTracking = false
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0

        stopPeriodicCheck()
        stopRefreshTimer()

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Stopping pixel tracking - ID: $pixelId, Total: $visibilityCount")
        }
    }

    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = coroutineScope.launch {
            while (isTracking && isAttachedToWindow) {
                delay(checkInterval)
                if (isShown && isVisible) {
                    checkPixelVisibility()
                }
            }
        }

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Periodic check started - Interval: ${checkInterval}ms")
        }
    }

    private fun stopPeriodicCheck() {
        checkJob?.cancel()
        checkJob = null

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Periodic check stopped")
        }
    }

    private fun startRefreshTimer() {
        if (refreshTime <= 0) return

        refreshTimerJob?.cancel()
        refreshTimerJob = coroutineScope.launch {
            val currentTime = System.currentTimeMillis()
            val timeUntilRefresh = nextRefreshTime - currentTime

            if (timeUntilRefresh > 0) {
                delay(timeUntilRefresh)
            }

            // Таймер сработал - засчитываем повторный показ
            if (isContinuousTracking && isPixelActuallyVisible() && isTracking) {
                visibilityCount++
                continuousVisibilityStartTime = System.currentTimeMillis()
                nextRefreshTime = continuousVisibilityStartTime + refreshTime

                logger.logRefresh(pixelId, getTimestamp(), getRefreshMetadata())
                eventListener?.onRefresh(pixelId, getTimestamp())

                if (isDebugMode) {
                    Log.d(tag, "[$libraryVersion] Refresh counted - ID: $pixelId, Total: $visibilityCount, Next: ${refreshTime}ms")
                }

                startRefreshTimer()
            }
        }
    }

    private fun stopRefreshTimer() {
        refreshTimerJob?.cancel()
        refreshTimerJob = null
    }

    private fun checkPixelVisibility() {
        val isVisibleNow = isPixelActuallyVisible()

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
            eventListener?.onAppearance(pixelId, getTimestamp())

            if (isDebugMode) {
                Log.d(tag, "[$libraryVersion] Pixel appeared - ID: $pixelId, Total: $visibilityCount, Refresh: ${if (refreshTime > 0) "enabled" else "disabled"}")
            }

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
            eventListener?.onDisappearance(pixelId, getTimestamp())

            if (isDebugMode) {
                Log.d(tag, "[$libraryVersion] Pixel disappeared - ID: $pixelId, Total: $visibilityCount")
            }

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
                eventListener?.onRefresh(pixelId, getTimestamp())

                if (isDebugMode) {
                    Log.d(tag, "[$libraryVersion] Automatic refresh - ID: $pixelId, Total: $visibilityCount, Next: ${refreshTime}ms")
                }
            }
        }
    }

    private fun isPixelActuallyVisible(): Boolean {
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

    fun getStats(): Map<String, Any> = mapOf(
        "library_version" to libraryVersion,
        "pixelId" to pixelId,
        "totalAppearances" to visibilityCount,
        "isCurrentlyVisible" to isPixelActuallyVisible(),
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
        checkPixelVisibility()
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

}