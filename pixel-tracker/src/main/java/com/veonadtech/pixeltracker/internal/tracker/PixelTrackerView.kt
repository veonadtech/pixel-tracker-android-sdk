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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

@Suppress("ViewConstructor")
internal class PixelTrackerView(
    context: Context,
    private val config: PixelConfig,
    private val logger: PixelLogger
) : View(context), PixelHandle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var listener: PixelEventListener? = null

    @Volatile
    private var isTracking = false

    @Volatile
    private var wasVisible = false

    private var totalAppearances = AtomicInteger(0)

    @Volatile
    private var nextRefreshTime = 0L

    @Volatile
    private var refreshTimeMs = config.refreshTimeSeconds * 1000

    @Volatile
    private var visibilityCheckInterval: Long = 3000L

    private var refreshJob: Job? = null

    private var visibilityLoopJob: Job? = null

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
        visibilityLoopJob = checkVisibilityLoop()
    }

    override fun stop() {
        isTracking = false
        visibilityLoopJob?.cancel()
        handleDisappearance()
    }

    override fun updateRefreshTime(seconds: Long) {
        require(seconds >= 0) { "Refresh time seconds must be non-negative, got $seconds" }
        refreshTimeMs = seconds * 1000
    }

    override fun setVisibilityCheckInterval(seconds: Long) {
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

    override fun destroy() {
        stop()
        scope.cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    // Visibility logic

    private fun checkVisibilityLoop(): Job {
        return scope.launch {
            try {
                while (isTracking) {
                    delay(visibilityCheckInterval)
                    handleVisibility()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.logError(config.pixelId,"Visibility loop error: ${t.message}", System.currentTimeMillis().toString())
            }
        }
    }

    private fun handleVisibility() {
        val visible = isPixelVisible()

        if (visible && !wasVisible) {
            handleAppearance()
        }

        if (!visible && wasVisible) {
            handleDisappearance()
        }
    }

    private fun handleAppearance() {
        totalAppearances.incrementAndGet()
        wasVisible = true
        scheduleRefresh()
        notifyAppearance()
    }

    private fun handleDisappearance() {
        wasVisible = false
        refreshJob?.cancel()
        notifyDisappearance()
    }

    private fun scheduleRefresh() {
        if (refreshTimeMs <= 0) return

        nextRefreshTime = System.currentTimeMillis() + refreshTimeMs

        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(refreshTimeMs)
            if (isPixelVisible()) {
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