package com.example.pixeltracker

import android.util.Log
import com.example.pixeltracker.model.PixelEvent
import com.example.pixeltracker.network.PixelNetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * Реализация PixelLogger, которая отправляет события на сервер через PixelNetworkManager.
 * Отправляет события ВСЕГДА, независимо от debug режима.
 */
internal class NetworkPixelLogger(
    val delegate: PixelLogger? = null
) : PixelLogger {

    private val libraryVersion: String = try {
        BuildConfig.LIBRARY_VERSION
    } catch (_: Exception) {
        "unknown"
    }

    private val networkManager: PixelNetworkManager? = PixelTracker.getNetworkManager()

    override fun logAppearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
        enqueueNetworkEvent(
            pixelId = pixelId,
            eventType = PixelEvent.EventType.APPEARANCE,
            timestamp = timestamp
        )
    }

    override fun logRefresh(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
        enqueueNetworkEvent(
            pixelId = pixelId,
            eventType = PixelEvent.EventType.REFRESH,
            timestamp = timestamp
        )
    }

    override fun logError(pixelId: String, error: String, timestamp: String) {
        enqueueNetworkEvent(
            pixelId = pixelId,
            eventType = PixelEvent.EventType.ERROR,
            timestamp = timestamp,
            errorMessage = error
        )
    }

    override fun logDisappearance(pixelId: String, timestamp: String, metadata: Map<String, Any>) {
    }

    private fun enqueueNetworkEvent(
        pixelId: String,
        eventType: PixelEvent.EventType,
        timestamp: String,
        errorMessage: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                networkManager?.enqueueEvent(
                    PixelEvent(
                        pixelId = pixelId,
                        eventType = eventType,
                        timestamp = timestamp.toLongOrNull() ?: System.currentTimeMillis(),
                        sdkVersion = libraryVersion,
                        errorMessage = errorMessage
                    )
                )
            } catch (e: Exception) {
                // Логируем ошибки отправки только в debug режиме
                if ((delegate as? DefaultPixelLogger)?.isDebugMode == true) {
                    Log.e("NetworkPixelLogger", "Failed to enqueue event: ${e.message}")
                }
            }
        }
    }
}