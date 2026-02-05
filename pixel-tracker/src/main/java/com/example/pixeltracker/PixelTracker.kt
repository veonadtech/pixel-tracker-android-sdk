package com.example.pixeltracker

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.isVisible
import kotlinx.coroutines.*
import java.util.*
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

    // Приватные свойства
    private val tag: String = "PixelTracker"
    private var wasVisible = false
    private var visibilityCount = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var checkJob: Job? = null
    private var isTracking = false

    // Интерфейс логирования
    interface PixelLogger {
        fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>)
        fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>)
        fun logError(pixelId: String, error: String, timestamp: String)
    }

    private var logger: PixelLogger = DefaultPixelLogger()

    private class DefaultPixelLogger : PixelLogger {
        override fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
            Log.d("PixelTracker", "🔵 Pixel appeared: $pixelId at $timestamp")
        }

        override fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
            Log.d("PixelTracker", "🔴 Pixel disappeared: $pixelId at $timestamp")
        }

        override fun logError(pixelId: String, error: String, timestamp: String) {
            Log.e("PixelTracker", "❌ Pixel $pixelId error: $error at $timestamp")
        }
    }

    init {
        setupView()
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
     * Начинает отслеживание видимости пикселя
     */
    fun startTracking() {
        if (isTracking) return

        isTracking = true

        // Запускаем периодическую проверку
        startPeriodicCheck()

        // Первая проверка после отрисовки
        post { checkVisibility() }

        if (isDebugMode) {
            Log.d(tag, "Starting pixel tracking with ID: $pixelId")
        }
    }

    /**
     * Останавливает отслеживание
     */
    fun stopTracking() {
        if (!isTracking) return

        isTracking = false
        wasVisible = false

        // Останавливаем периодическую проверку
        stopPeriodicCheck()

        if (isDebugMode) {
            Log.d(tag, "Stopping pixel tracking with ID: $pixelId")
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
    }

    /**
     * Останавливает периодическую проверку
     */
    private fun stopPeriodicCheck() {
        checkJob?.cancel()
        checkJob = null
    }

    /**
     * Проверяет видимость пикселя
     */
    private fun checkVisibility() {
        val isVisibleNow = isActuallyVisible()

        if (isVisibleNow && !wasVisible) {
            // Пиксель стал видимым
            visibilityCount++
            wasVisible = true

            logger.logAppearance(pixelId, getTimestamp(), getVisibilityMetadata(true))

        } else if (!isVisibleNow && wasVisible) {
            // Пиксель стал невидимым
            wasVisible = false

            logger.logDisappearance(pixelId, getTimestamp(), getVisibilityMetadata(false))
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
            "visible" to isVisible,
            "total_appearances" to visibilityCount,
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
     * Устанавливает кастомный логгер
     */
    fun setCustomLogger(customLogger: PixelLogger) {
        this.logger = customLogger
    }

    /**
     * Получает статистику
     */
    fun getStats(): Map<String, Any> = mapOf(
        "pixelId" to pixelId,
        "totalAppearances" to visibilityCount,
        "isCurrentlyVisible" to isActuallyVisible(),
        "isTracking" to isTracking,
        "checkInterval" to checkInterval
    )

    /**
     * Принудительно проверяет видимость (публичный метод)
     */
    fun checkVisibilityNow() {
        checkVisibility()
    }

    private fun getTimestamp(): String {
        return System.currentTimeMillis().toString()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Фиксированный размер
        setMeasuredDimension(40, 40)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isTracking) {
            // Перезапускаем отслеживание при повторном присоединении
            startPeriodicCheck()
        }
    }

    override fun onDetachedFromWindow() {
        stopTracking()
        coroutineScope.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /**
         * Создает экземпляр PixelTracker (упрощенная фабрика)
         */
        @JvmStatic
        fun create(
            context: Context,
            pixelId: String
        ): PixelTracker {
            return PixelTracker(context, pixelId)
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