package com.example.pixeltracker.network

import android.util.Log
import com.example.pixeltracker.model.PixelEvent
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class PixelNetworkManager(
    private val baseUrl: String,
    private val isDebugMode: Boolean
) {

    private companion object {
        const val MAX_RETRIES = 3
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 8_000L
        const val TIMEOUT_SECONDS = 10L
        const val CHANNEL_CAPACITY = 500
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Channel вместо Mutex + ArrayDeque.
     * DROP_OLDEST защищает от OOM при спайке событий.
     */
    private val eventChannel = Channel<PixelEvent>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private var processorJob: Job

    init {
        processorJob = startProcessor()
    }

    /**
     * Публичный API — неблокирующий.
     */
    fun enqueueEvent(event: PixelEvent) {
        val result = eventChannel.trySend(event)
        if (result.isFailure) {
            Log.w("PixelNetworkManager", "Event dropped due to full buffer: $event")
        }
    }

    /**
     * Основной процессор очереди.
     * Работает только когда есть события.
     */
    private fun startProcessor(): Job {
        return scope.launch {
            for (event in eventChannel) {
                sendEventWithRetry(event)
            }
        }
    }

    /**
     * Retry с exponential backoff.
     */
    private suspend fun sendEventWithRetry(event: PixelEvent) {
        repeat(MAX_RETRIES) { attempt ->

            val success = try {
                sendEvent(event)
            } catch (e: Exception) {
                Log.w("PixelNetworkManager", "Send failed: ${e.message}")
                false
            }

            if (success) return

            if (attempt < MAX_RETRIES - 1) {
                val delayTime = calculateBackoff(attempt)
                delay(delayTime)
            } else {
                Log.e(
                    "PixelNetworkManager",
                    "Failed after $MAX_RETRIES attempts: $event"
                )
            }
        }
    }

    /**
     * Реальная отправка.
     */
    private suspend fun sendEvent(event: PixelEvent): Boolean {

        val json = gson.toJson(event)
        val url = "$baseUrl/track"

        if (isDebugMode) {
            Log.d("PixelNetworkManager", "Sending event to $url")
            Log.d("PixelNetworkManager", "Payload: $json")
        }

        val request = Request.Builder()
            .url(url)
            .post(
                json.toRequestBody("application/json".toMediaType())
            )
            .build()

        return suspendCancellableCoroutine { continuation ->

            val call = httpClient.newCall(request)

            continuation.invokeOnCancellation {
                if (isDebugMode) {
                    Log.d("PixelNetworkManager", "Request cancelled")
                }
                call.cancel()
            }

            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (isDebugMode) {
                        Log.e(
                            "PixelNetworkManager",
                            "Network failure: ${e.message}",
                            e
                        )
                    }

                    if (!continuation.isCompleted) {
                        continuation.resume(false) {}
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {

                        if (isDebugMode) {
                            Log.d(
                                "PixelNetworkManager",
                                "Response code: ${response.code}"
                            )

                            if (!response.isSuccessful) {
                                Log.w(
                                    "PixelNetworkManager",
                                    "Request failed. HTTP ${response.code}"
                                )
                            }
                        }

                        if (!continuation.isCompleted) {
                            continuation.resume(response.isSuccessful) {}
                        }
                    }
                }
            })
        }
    }

    /**
     * Exponential backoff: 1s → 2s → 4s (с ограничением)
     */
    private fun calculateBackoff(attempt: Int): Long {
        val delay = INITIAL_RETRY_DELAY_MS * 2.0.pow(attempt).toLong()
        return min(delay, MAX_RETRY_DELAY_MS)
    }

    /**
     * Корректное завершение работы SDK.
     */
    fun shutdown() {
        eventChannel.close()
        processorJob.cancel()
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    /**
     * Для debug.
     */
    fun isActive(): Boolean = processorJob.isActive
}
