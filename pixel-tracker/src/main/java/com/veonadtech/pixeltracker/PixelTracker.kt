package com.veonadtech.pixeltracker

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.MainThread
import com.veonadtech.pixeltracker.api.PixelConfig
import com.veonadtech.pixeltracker.api.PixelHandle
import com.veonadtech.pixeltracker.internal.logger.DefaultPixelLogger
import com.veonadtech.pixeltracker.internal.logger.PixelNetworkLogger
import com.veonadtech.pixeltracker.internal.network.PixelNetworkManager
import com.veonadtech.pixeltracker.internal.tracker.PixelTrackerView
import java.util.Collections

object PixelTracker {

    private const val TAG = "PixelTracker"

    private val lock = Any()

    @Volatile
    private var networkManager: PixelNetworkManager? = null

    @Volatile
    private var pixelNetworkLogger: PixelNetworkLogger? = null

    private val activeViews =
        Collections.synchronizedSet(mutableSetOf<PixelTrackerView>())

    @MainThread
    fun isInitialized(): Boolean {
        synchronized(lock) {
            return networkManager != null && pixelNetworkLogger != null
        }
    }

    @MainThread
    fun initialize(
        baseUrl: String,
        isDebugMode: Boolean = false,
        callback: ((InitStatus) -> Unit)? = null
    ) {
        synchronized(lock) {
            if (networkManager != null) {
                callback?.invoke(InitStatus.Success("SDK already initialized"))
                return
            }

            if (baseUrl.isBlank()) {
                val exception = IllegalArgumentException("BaseUrl cannot be empty")
                callback?.invoke(InitStatus.Failure(exception, "Invalid configuration"))
                return
            }

            try {
                val manager = PixelNetworkManager(baseUrl, isDebugMode)
                pixelNetworkLogger = PixelNetworkLogger(manager).apply {
                    setDelegate(
                        DefaultPixelLogger().apply {
                            this.isDebugMode = isDebugMode
                        }
                    )
                }
                networkManager = manager

                callback?.invoke(InitStatus.Success())
            } catch (e: Exception) {
                callback?.invoke(InitStatus.Failure(e, "Initialization failed"))
            }
        }
    }

    fun attach(
        context: Context,
        container: ViewGroup,
        config: PixelConfig
    ): PixelHandle? {
        synchronized(lock) {
            if (!isInitialized()) {
                Log.e(TAG, "PixelTracker must be initialized before attach(). Call initialize() first.")

                return null
            }

            val logger = pixelNetworkLogger
            if (logger == null) {
                Log.e(TAG, "PixelTracker logger not available even after initialization")

                return null
            }

            try {
                val view = PixelTrackerView(
                    context = context,
                    config = config,
                    logger = logger,
                    onDestroyed = { destroyedView ->
                        activeViews.remove(destroyedView)
                    }
                )

                container.addView(view)
                activeViews.add(view)
                Log.d(TAG, "PixelTrackerView attached successfully. Active views: ${activeViews.size}")

                return view

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create or attach PixelTrackerView: ${e.message}", e)

                return null
            }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            activeViews.toList().forEach { it.shutdown() }
            activeViews.clear()

            networkManager?.shutdown()
            networkManager = null

            pixelNetworkLogger?.shutdown()
            pixelNetworkLogger = null

            Log.d(TAG, "Pixel tracker SDK shutdown")
        }
    }
}

sealed class InitStatus {

    data class Success(val message: String = "SDK initialized") : InitStatus()
    data class Failure(val exception: Exception, val reason: String) : InitStatus()

}
