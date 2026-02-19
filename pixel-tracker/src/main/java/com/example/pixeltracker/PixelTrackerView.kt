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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Keep
@Suppress("ViewConstructor")
class PixelTrackerView(
    context: Context,
    private val pixelId: String
) : View(context) {

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

    lateinit var logger: PixelLogger
        private set

    init {
        setupView()
        logInitialization()
    }

    // Public API for setting logger

    fun setLogger(logger: PixelLogger) {
        this.logger = logger
        updateLoggerDebugMode()
    }

    fun setEventListener(listener: PixelEventListener?) {
        this.eventListener = listener
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
        viewJob.cancel()
        super.onDetachedFromWindow()
    }

    // Public API

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
        "totalAppearances" to visibilityCount,
        "refreshEnabled" to (refreshTime > 0),
        "isCurrentlyVisible" to isPixelActuallyVisible(),
        "nextRefreshIn" to calculateNextRefreshIn()
    )

    // Private methods

    private fun calculateNextRefreshIn(): Long {
        return if (isContinuousTracking && nextRefreshTime > 0) {
            max(0, nextRefreshTime - System.currentTimeMillis())
        } else {
            0
        }
    }

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

                val ts = getTimestamp()
                logger.logRefresh(pixelId, ts)
                eventListener?.onRefresh(pixelId, ts)

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

            val ts = getTimestamp()
            logger.logAppearance(pixelId, ts)
            eventListener?.onAppearance(pixelId, ts)
        }

        if (!visibleNow && wasVisible) {
            wasVisible = false
            isContinuousTracking = false
            stopRefreshTimer()

            val ts = getTimestamp()
            logger.logDisappearance(pixelId, ts)
            eventListener?.onDisappearance(pixelId, ts)
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

    private fun updateLoggerDebugMode() {
        if (!::logger.isInitialized) return

        when (val current = logger) {
            is NetworkPixelLogger -> {
                current.updateDebugMode(isDebugMode)

            }
            is DefaultPixelLogger -> {
                current.isDebugMode = isDebugMode
            }
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
