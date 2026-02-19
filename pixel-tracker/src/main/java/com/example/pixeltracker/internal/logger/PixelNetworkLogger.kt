package com.example.pixeltracker.internal.logger

import android.util.Log
import com.example.pixeltracker.BuildConfig
import com.example.pixeltracker.api.PixelLogger
import com.example.pixeltracker.model.PixelEvent
import com.example.pixeltracker.network.PixelNetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

internal class PixelNetworkLogger(
    private val networkManager: PixelNetworkManager
) : PixelLogger {

    private companion object Companion {
        const val TAG = "NetworkPixelLogger"
    }

    private val delegateRef = AtomicReference<PixelLogger?>()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val sdkVersion: String = try {
        BuildConfig.LIBRARY_VERSION
    } catch (_: Exception) {
        "unknown"
    }

    fun setDelegate(delegate: PixelLogger?) {
        delegateRef.set(delegate)
    }

    override fun logAppearance(pixelId: String, timestamp: String) {
        delegateRef.get()?.logAppearance(pixelId, timestamp)
        enqueue(pixelId, PixelEvent.EventType.APPEARANCE, timestamp)
    }

    override fun logRefresh(pixelId: String, timestamp: String) {
        delegateRef.get()?.logRefresh(pixelId, timestamp)
        enqueue(pixelId, PixelEvent.EventType.REFRESH, timestamp)
    }

    override fun logError(pixelId: String, error: String, timestamp: String) {
        delegateRef.get()?.logError(pixelId, error, timestamp)
        enqueue(pixelId, PixelEvent.EventType.ERROR, timestamp, error)
    }

    override fun logDisappearance(pixelId: String, timestamp: String) {
        delegateRef.get()?.logDisappearance(pixelId, timestamp)
    }

    private fun enqueue(
        pixelId: String,
        eventType: PixelEvent.EventType,
        timestamp: String,
        errorMessage: String? = null
    ) {
        scope.launch {
            try {
                val event = PixelEvent(
                    pixelId = pixelId,
                    eventType = eventType,
                    timestamp = timestamp.toLongOrNull()
                        ?: System.currentTimeMillis(),
                    sdkVersion = sdkVersion,
                    errorMessage = errorMessage
                )

                networkManager.enqueueEvent(event)

            } catch (t: Throwable) {
                val delegate = delegateRef.get()
                if (delegate is DefaultPixelLogger && delegate.isDebugMode) {
                    Log.e(TAG, "Failed to enqueue event", t)
                }
            }
        }
    }

    fun shutdown() {
        job.cancel()
    }
}