package com.veonadtech.pixeltracker.internal.tracker

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.veonadtech.pixeltracker.api.PixelConfig
import com.veonadtech.pixeltracker.api.PixelEventListener
import com.veonadtech.pixeltracker.api.PixelHandle
import com.veonadtech.pixeltracker.api.PixelLogger
import com.veonadtech.pixeltracker.api.PixelStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

@Suppress("ViewConstructor")
internal class PixelTrackerView(
    context: Context,
    private val config: PixelConfig,
    private val logger: PixelLogger,
    private val onDestroyed: (PixelTrackerView) -> Unit
) : View(context), PixelHandle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var listener: PixelEventListener? = null

    @Volatile private var isTracking = false
    @Volatile private var wasVisible = false
    @Volatile private var isShutdown = false

    private val totalAppearances = AtomicInteger(0)

    @Volatile private var nextRefreshTime = 0L
    @Volatile private var refreshTimeMs = config.refreshTimeSeconds * 1000
    @Volatile private var visibilityCheckInterval: Long = 3000L

    private var refreshJob: Job? = null
    private var visibilityLoopJob: Job? = null

    init {
        layoutParams = ViewGroup.LayoutParams(config.pixelSize, config.pixelSize)
        setBackgroundColor(config.color ?: 0x00000000)
    }

    // View lifecycle

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }

    // PixelHandle implementation

    override fun start() {
        if (isTracking || isShutdown) return
        isTracking = true
        visibilityLoopJob = checkVisibilityLoop()
    }

    override fun stop() {
        if (isShutdown) return
        isTracking = false
        visibilityLoopJob?.cancel()
        handleDisappearance()
    }

    override fun destroy() {
        if (isShutdown) return
        shutdown()
    }

    fun shutdown() {
        if (isShutdown) return
        isShutdown = true

        isTracking = false

        refreshJob?.cancel()
        visibilityLoopJob?.cancel()
        scope.cancel()

        (parent as? ViewGroup)?.removeView(this)

        onDestroyed(this)
    }

    // Config updates

    override fun updateRefreshTime(seconds: Long) {
        if (isShutdown) return
        require(seconds >= 0) { "Refresh time seconds must be non-negative, got $seconds" }
        refreshTimeMs = seconds * 1000
    }

    override fun setVisibilityCheckInterval(seconds: Long) {
        if (isShutdown) return
        require(seconds >= 0) { "Visibility check interval seconds must be non-negative, got $seconds" }
        visibilityCheckInterval = seconds * 1000
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

    // Visibility

    private fun checkVisibilityLoop(): Job = scope.launch {
        try {
            while (isTracking && !isShutdown) {
                delay(visibilityCheckInterval)
                handleVisibility()
            }
        } catch (_: CancellationException) {
        }
    }

    private fun handleVisibility() {
        if (isShutdown) return

        val visible = isPixelVisible()

        if (visible && !wasVisible) handleAppearance()
        if (!visible && wasVisible) handleDisappearance()
    }

    private fun handleAppearance() {
        if (isShutdown) return

        totalAppearances.incrementAndGet()
        wasVisible = true
        scheduleRefresh()
        notifyAppearance()
    }

    private fun handleDisappearance() {
        if (isShutdown) return

        wasVisible = false
        refreshJob?.cancel()
        notifyDisappearance()
    }

    private fun scheduleRefresh() {
        if (refreshTimeMs <= 0 || isShutdown) return

        nextRefreshTime = System.currentTimeMillis() + refreshTimeMs

        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(refreshTimeMs)
            if (!isShutdown && isPixelVisible()) {
                totalAppearances.incrementAndGet()
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

    // Notifications

    private fun notifyAppearance() {
        if (isShutdown) return
        val ts = System.currentTimeMillis().toString()
        logger.logAppearance(config.pixelId, ts)
        listener?.onAppearance(config.pixelId, ts)
    }

    private fun notifyDisappearance() {
        if (isShutdown) return
        val ts = System.currentTimeMillis().toString()
        logger.logDisappearance(config.pixelId, ts)
        listener?.onDisappearance(config.pixelId, ts)
    }

    private fun notifyRefresh() {
        if (isShutdown) return
        val ts = System.currentTimeMillis().toString()
        logger.logRefresh(config.pixelId, ts)
        listener?.onRefresh(config.pixelId, ts)
    }
}