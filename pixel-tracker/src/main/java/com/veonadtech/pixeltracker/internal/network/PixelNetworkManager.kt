package com.veonadtech.pixeltracker.internal.network

import android.util.Log
import com.veonadtech.pixeltracker.internal.model.PixelEvent
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages the network queue and delivery of pixel events to a remote server.
 */
internal class PixelNetworkManager(
    private val baseUrl: String,
    private val isDebugMode: Boolean
) {

    private companion object {
        const val MAX_RETRIES = 3
        const val MAX_CONCURRENT_REQUESTS = 4
        const val TOTAL_TIMEOUT_MS = 10_000L
        const val INITIAL_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 8_000L
        const val TIMEOUT_SECONDS = 10L
        const val CHANNEL_CAPACITY = 500
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private val processorJobs = mutableListOf<Job>()

    init {
        startProcessors()
    }

    fun enqueueEvent(event: PixelEvent) {
        val result = eventChannel.trySend(event)
        if (result.isFailure) {
            Log.w("PixelNetworkManager", "Event dropped due to full buffer: $event")
        }
    }

    private fun startProcessors() {
        repeat(MAX_CONCURRENT_REQUESTS) { processorIndex ->
            processorJobs.add(
                scope.launch {
                    try {
                        for (event in eventChannel) {
                            processEventWithTimeout(event, processorIndex)
                        }
                    } catch (e: CancellationException) {
                        if (isDebugMode) {
                            Log.d("PixelNetworkManager", "Processor $processorIndex cancelled")
                        }
                        throw e
                    } catch (t: Throwable) {
                        if (isDebugMode) {
                            Log.e("PixelNetworkManager", "Processor $processorIndex failed", t)
                        }
                    }
                }
            )
        }
    }

    private suspend fun processEventWithTimeout(event: PixelEvent, processorIndex: Int) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            val json = gson.toJson(event)
            sendEventWithRetry(event, json)
        } ?: run {
            if (isDebugMode) {
                Log.e(
                    "PixelNetworkManager",
                    "[Processor $processorIndex] Event timed out after ${TOTAL_TIMEOUT_MS}ms: $event"
                )
            }
        }
    }

    private suspend fun sendEventWithRetry(event: PixelEvent, json: String) {
        repeat(MAX_RETRIES) { attempt ->

            when (sendEvent(json)) {
                SendResult.SUCCESS -> return
                SendResult.NON_RETRYABLE_ERROR -> {
                    if (isDebugMode) {
                        Log.w("PixelNetworkManager", "Non-retryable error: $event")
                    }
                    return
                }
                SendResult.RETRYABLE_ERROR -> {
                    if (attempt < MAX_RETRIES - 1) {
                        delay(calculateBackoff(attempt))
                    } else {
                        if (isDebugMode) {
                            Log.e("PixelNetworkManager", "Failed after retries: $event")
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun sendEvent(json: String): SendResult {

        if (isDebugMode) {
            Log.d("PixelNetworkManager", "Sending event to $baseUrl")
            Log.d("PixelNetworkManager", "Payload: $json")
        }

        val request = Request.Builder()
            .url(baseUrl)
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

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(SendResult.RETRYABLE_ERROR) {}
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (isDebugMode) {
                            Log.d("PixelNetworkManager", "Response code: ${response.code}")
                        }
                            val result = when {
                            response.isSuccessful -> SendResult.SUCCESS
                            response.code in 500..599 -> SendResult.RETRYABLE_ERROR
                            else -> SendResult.NON_RETRYABLE_ERROR
                        }
                        continuation.resume(result) {}
                    }
                }
            })
        }
    }

    /**
     * Exponential backoff: 1s → 2s → 4s (with cap)
     */
    private fun calculateBackoff(attempt: Int): Long {
        val delay = INITIAL_RETRY_DELAY_MS * 2.0.pow(attempt).toLong()
        return min(delay, MAX_RETRY_DELAY_MS)
    }

    fun shutdown() {
        if (isDebugMode) {
            Log.d("PixelNetworkManager", "Shutting down...")
        }
        eventChannel.close()

        processorJobs.forEach { it.cancel() }
        processorJobs.clear()

        scope.cancel()

        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()

        if (isDebugMode) {
            Log.d("PixelNetworkManager", "Shutdown complete")
        }
    }

    /**
     * For debug purposes.
     */
    fun isActive(): Boolean = processorJobs.any { it.isActive }
}

private enum class SendResult {
    SUCCESS,
    RETRYABLE_ERROR,
    NON_RETRYABLE_ERROR
}
