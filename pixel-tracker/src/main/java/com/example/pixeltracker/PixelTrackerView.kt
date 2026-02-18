package com.example.pixeltracker

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.isVisible
import kotlinx.coroutines.*
import kotlin.math.max

@Keep
class PixelTrackerView @JvmOverloads constructor(
    context: Context,
    private val pixelId: String,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Public properties

    var visibilityThreshold: Int = 1
    var isDebugMode: Boolean = false
        set(value) {
            field = value
            updateLoggerDebugMode()
        }

    var checkInterval: Long = 3000L

    var refreshTime: Long = 0L
        set(value) {
            field = max(0L, value)
        }

    var pixelSize: Int = 1
        set(value) {
            field = max(1, value)
            requestLayout()
        }

    interface PixelEventListener {
        fun onAppearance(pixelId: String, timestamp: String)
        fun onDisappearance(pixelId: String, timestamp: String)
        fun onRefresh(pixelId: String, timestamp: String)
        fun onError(pixelId: String, error: String, timestamp: String)
    }

    //  Private fields

    private val tag = "PixelTracker"

    private var wasVisible = false
    private var visibilityCount = 0
    private var isTracking = false
    private var isContinuousTracking = false
    private var continuousVisibilityStartTime: Long = 0
    private var nextRefreshTime: Long = 0

    private var checkJob: Job? = null
    private var refreshTimerJob: Job? = null

    private var eventListener: PixelEventListener? = null

    private val viewJob = SupervisorJob()
    private val viewScope = CoroutineScope(Dispatchers.Main.immediate + viewJob)

    private val libraryVersion: String = try {
        BuildConfig.LIBRARY_VERSION
    } catch (_: Exception) {
        "unknown"
    }

    private var logger: PixelLogger = createDefaultLogger()

    init {
        setupView()
        logInitialization()
    }

    // Lifecycle

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = if (isDebugMode) pixelSize else 1
        setMeasuredDimension(size, size)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Attached to window")
        }

        if (isTracking) {
            startPeriodicCheck()
        }
    }

    override fun onDetachedFromWindow() {
        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Detached from window")
        }

        stopTracking()
        viewJob.cancelChildren()

        super.onDetachedFromWindow()
    }

    // Public API

    fun setEventListener(listener: PixelEventListener?) {
        this.eventListener = listener
    }

    fun startTracking() {
        if (isTracking) return

        isTracking = true
        isContinuousTracking = false
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0

        startPeriodicCheck()

        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] Tracking started - ID: $pixelId")
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
            Log.d(tag, "[$libraryVersion] Tracking stopped - ID: $pixelId, Total: $visibilityCount")
        }
    }

    fun checkVisibilityNow() {
        checkPixelVisibility()
    }

    fun resetCount() {
        visibilityCount = 0
        continuousVisibilityStartTime = 0
        nextRefreshTime = 0
        isContinuousTracking = false
    }

    fun getStats(): Map<String, Any> = mapOf(
        "pixelId" to pixelId,
        "totalAppearances" to visibilityCount,
        "isTracking" to isTracking,
        "refreshTime" to refreshTime,
        "pixelSize" to pixelSize
    )

    // Private methods

    private fun setupView() {
        setBackgroundColor(0x00000000)
        minimumWidth = 1
        minimumHeight = 1
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun startPeriodicCheck() {
        checkJob?.cancel()

        checkJob = viewScope.launch {
            while (isTracking && isAttachedToWindow) {
                delay(checkInterval)
                if (isShown && isVisible) {
                    checkPixelVisibility()
                }
            }
        }
    }

    private fun stopPeriodicCheck() {
        checkJob?.cancel()
        checkJob = null
    }

    private fun startRefreshTimer() {
        if (refreshTime <= 0) return

        refreshTimerJob?.cancel()

        refreshTimerJob = viewScope.launch {
            val delayTime = nextRefreshTime - System.currentTimeMillis()
            if (delayTime > 0) delay(delayTime)

            if (isContinuousTracking && isPixelActuallyVisible() && isTracking) {

                visibilityCount++
                continuousVisibilityStartTime = System.currentTimeMillis()
                nextRefreshTime = continuousVisibilityStartTime + refreshTime

                logger.logRefresh(pixelId, getTimestamp())
                eventListener?.onRefresh(pixelId, getTimestamp())

                startRefreshTimer()
            }
        }
    }

    private fun stopRefreshTimer() {
        refreshTimerJob?.cancel()
        refreshTimerJob = null
    }

    private fun checkPixelVisibility() {
        val visibleNow = isPixelActuallyVisible()

        if (visibleNow && !wasVisible) {
            visibilityCount++
            wasVisible = true
            isContinuousTracking = true

            if (refreshTime > 0) {
                continuousVisibilityStartTime = System.currentTimeMillis()
                nextRefreshTime = continuousVisibilityStartTime + refreshTime
                startRefreshTimer()
            }

            logger.logAppearance(pixelId, getTimestamp())
            eventListener?.onAppearance(pixelId, getTimestamp())
        }

        if (!visibleNow && wasVisible) {
            wasVisible = false
            isContinuousTracking = false
            stopRefreshTimer()

            logger.logDisappearance(pixelId, getTimestamp())
            eventListener?.onDisappearance(pixelId, getTimestamp())
        }
    }

    private fun isPixelActuallyVisible(): Boolean {
        if (!isAttachedToWindow || !isShown || alpha <= 0.01f) return false

        val rect = Rect()
        val visible = getGlobalVisibleRect(rect)

        if (!visible) return false

        return rect.width() >= visibilityThreshold &&
                rect.height() >= visibilityThreshold
    }

    private fun createDefaultLogger(): PixelLogger {
        val defaultLogger = DefaultPixelLogger().apply {
            isDebugMode = this@PixelTrackerView.isDebugMode
        }
        return NetworkPixelLogger(defaultLogger)
    }

    private fun updateLoggerDebugMode() {
        when (val current = logger) {
            is NetworkPixelLogger ->
                (current.delegate as? DefaultPixelLogger)?.isDebugMode = isDebugMode
            is DefaultPixelLogger ->
                current.isDebugMode = isDebugMode
        }
    }

    private fun logInitialization() {
        if (isDebugMode) {
            Log.d(tag, "[$libraryVersion] PixelTracker initialized - ID: $pixelId")
        }
    }

    private fun getTimestamp(): String =
        System.currentTimeMillis().toString()

}