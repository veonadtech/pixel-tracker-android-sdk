package com.example.pixeltracker.internal.tracker

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.example.pixeltracker.api.PixelConfig
import com.example.pixeltracker.api.PixelEventListener
import com.example.pixeltracker.api.PixelHandle
import com.example.pixeltracker.api.PixelLogger
import com.example.pixeltracker.api.PixelStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Suppress("ViewConstructor")
internal class PixelTrackerView(
    context: Context,
    private val config: PixelConfig,
    private val logger: PixelLogger
) : View(context), PixelHandle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var listener: PixelEventListener? = null

    private var isTracking = false
    private var wasVisible = false
    private var totalAppearances = 0
    private var nextRefreshTime = 0L
    private var visibilityCheckInterval: Long = 3000L
    private var refreshJob: Job? = null

    private var refreshTimeMs = config.refreshTimeSeconds * 1000

    init {
        layoutParams = ViewGroup.LayoutParams(config.pixelSize, config.pixelSize)

        if (config.color != null) {
            setBackgroundColor(config.color)
        } else {
            setBackgroundColor(0x00000000)
        }
    }

    // PixelHandle implementation

    override fun start() {
        if (isTracking) return
        isTracking = true
        checkVisibilityLoop()
    }

    override fun stop() {
        isTracking = false
        refreshJob?.cancel()
    }

    override fun updateRefreshTime(seconds: Long) {
        refreshTimeMs = max(0, seconds * 1000)
    }

    override fun setVisibilityCheckInterval(seconds: Long) {
        visibilityCheckInterval = max(0, seconds * 1000)
    }

    override fun setEventListener(listener: PixelEventListener?) {
        this.listener = listener
    }

    override fun getStats(): PixelStats {
        val visible = isPixelVisible()
        val next = if (visible && refreshTimeMs > 0)
            max(0, nextRefreshTime - System.currentTimeMillis())
        else 0

        return PixelStats(
            totalAppearances,
            visible,
            refreshTimeMs > 0,
            next
        )
    }

    override fun destroy() {
        stop()
        scope.cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    // Visibility logic

    private fun checkVisibilityLoop() {
        scope.launch {
            while (isTracking) {
                delay(visibilityCheckInterval)
                handleVisibility()
            }
        }
    }

    private fun handleVisibility() {
        val visible = isPixelVisible()

        if (visible && !wasVisible) {
            totalAppearances++
            wasVisible = true
            scheduleRefresh()
            notifyAppearance()
        }

        if (!visible && wasVisible) {
            wasVisible = false
            refreshJob?.cancel()
            notifyDisappearance()
        }
    }

    private fun scheduleRefresh() {
        if (refreshTimeMs <= 0) return

        nextRefreshTime = System.currentTimeMillis() + refreshTimeMs

        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(refreshTimeMs)
            if (isPixelVisible()) {
                totalAppearances++
                notifyRefresh()
                scheduleRefresh()
            }
        }
    }

    private fun isPixelVisible(): Boolean {
        if (!isShown || alpha <= 0.01f) return false

        val rect = Rect()
        val visible = getGlobalVisibleRect(rect)

        return visible &&
                rect.width() >= config.visibilityThreshold &&
                rect.height() >= config.visibilityThreshold
    }

    private fun notifyAppearance() {
        val ts = System.currentTimeMillis().toString()
        logger.logAppearance(config.pixelId, ts)
        listener?.onAppearance(config.pixelId, ts)
    }

    private fun notifyDisappearance() {
        val ts = System.currentTimeMillis().toString()
        logger.logDisappearance(config.pixelId, ts)
        listener?.onDisappearance(config.pixelId, ts)
    }

    private fun notifyRefresh() {
        val ts = System.currentTimeMillis().toString()
        logger.logRefresh(config.pixelId, ts)
        listener?.onRefresh(config.pixelId, ts)
    }
}