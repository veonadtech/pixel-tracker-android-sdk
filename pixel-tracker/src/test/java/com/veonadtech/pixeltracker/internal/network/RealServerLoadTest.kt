package com.veonadtech.pixeltracker.internal.network

import com.veonadtech.pixeltracker.InitStatus
import com.veonadtech.pixeltracker.PixelTracker
import com.veonadtech.pixeltracker.internal.logger.DefaultPixelLogger
import com.veonadtech.pixeltracker.internal.logger.PixelNetworkLogger
import com.veonadtech.pixeltracker.internal.model.PixelEvent
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class RealServerLoadTest {

    companion object {
        private var baseUrl: String? = null
        private var shouldRun: Boolean = false

        @BeforeClass
        @JvmStatic
        fun checkBaseUrl() {
            baseUrl = System.getProperty("baseUrl")?.takeIf { it.isNotBlank() }
                ?: System.getenv("BASE_URL")?.takeIf { it.isNotBlank() }

            shouldRun = !baseUrl.isNullOrBlank()

            if (shouldRun) {
                println("✅ Load tests will run with baseUrl: $baseUrl")
            } else {
                println("⏭️  Load tests SKIPPED. Use -DbaseUrl=URL or -PbaseUrl=URL or export BASE_URL=URL")
            }
        }
    }

    private lateinit var networkManager: PixelNetworkManager
    private lateinit var networkLogger: PixelNetworkLogger

    @Before
    fun setUp() {
        assumeTrue("Load tests skipped: baseUrl not provided. Use -DbaseUrl=URL", shouldRun)

        networkManager = PixelNetworkManager(baseUrl!!, isDebugMode = true)
        networkLogger = PixelNetworkLogger(networkManager).apply {
            setDelegate(DefaultPixelLogger().apply { isDebugMode = true })
        }

        println("✅ NetworkManager created with baseUrl: $baseUrl")
    }

    @After
    fun tearDown() {
        if (::networkLogger.isInitialized) {
            networkLogger.shutdown()
        }
        if (::networkManager.isInitialized) {
            networkManager.shutdown()
        }
    }

    private fun sendEvent(
        pixelId: String,
        type: PixelEvent.EventType = PixelEvent.EventType.APPEARANCE
    ) {
        val ts = System.currentTimeMillis().toString()
        when (type) {
            PixelEvent.EventType.APPEARANCE -> networkLogger.logAppearance(pixelId, ts)
            PixelEvent.EventType.REFRESH    -> networkLogger.logRefresh(pixelId, ts)
            PixelEvent.EventType.ERROR      -> networkLogger.logError(pixelId, "test error", ts)
        }
    }

    // to exec a separate test:
    // ./gradlew :pixel-tracker:testDebugUnitTest
    // --tests "*.RealServerLoadTest.single event reaches real server"   --rerun-tasks
    // -PbaseUrl=https://your-pixel-tracker.server.com/v1/pixel-event --info
    @Test
    fun `single event reaches real server`() {
        val pixelId = "smoke_px_${System.currentTimeMillis()}"
        println("🧪 Test: Single event - $pixelId")
        sendEvent(pixelId)
        Thread.sleep(5_000)
        println("✅ Single event test completed")
    }

    @Test
    fun `100 sequential events reach real server`() {
        val start = System.currentTimeMillis()
        println("🧪 Test: 100 sequential events")

        repeat(100) { i ->
            sendEvent("seq_px_${i}_${System.currentTimeMillis()}")
        }

        println("📤 100 events enqueued in ${System.currentTimeMillis() - start}ms")
        Thread.sleep(15_000)
        println("✅ Done in ${System.currentTimeMillis() - start}ms")
    }

    @Test
    fun `500 concurrent events from 10 threads`() {
        val sent = AtomicInteger(0)
        val latch = CountDownLatch(10)
        val start = System.currentTimeMillis()
        println("🧪 Test: 500 concurrent events from 10 threads")

        repeat(10) { threadIdx ->
            Thread {
                repeat(50) { i ->
                    sendEvent("t${threadIdx}_px${i}_${System.currentTimeMillis()}")
                    sent.incrementAndGet()
                }
                latch.countDown()
            }.start()
        }

        org.junit.Assert.assertTrue("Threads timed out",
                                    latch.await(10, TimeUnit.SECONDS))
        println("📤 ${sent.get()} events enqueued in ${System.currentTimeMillis() - start}ms")
        Thread.sleep(30_000)
        println("✅ Done in ${System.currentTimeMillis() - start}ms")
    }

    @Test
    fun `mixed event types reach real server`() {
        val types = listOf(
            PixelEvent.EventType.APPEARANCE,
            PixelEvent.EventType.REFRESH,
            PixelEvent.EventType.ERROR
        )
        println("🧪 Test: Mixed event types")

        repeat(50) { i ->
            sendEvent("mixed_px_$i", types[i % types.size])
        }
        println("📤 50 mixed events enqueued")
        Thread.sleep(15_000)
        println("✅ Mixed events test completed")
    }

    @Test
    fun `measure throughput - 200 events`() {
        val count = 200
        val start = System.currentTimeMillis()
        println("🧪 Test: Throughput measurement - $count events")

        repeat(count) { i -> sendEvent("tp_px_$i") }

        val ms = System.currentTimeMillis() - start
        val throughput = if (ms > 0) count * 1000 / ms else 0
        println("📤 $count events in ${ms}ms ($throughput ev/s)")
        Thread.sleep(30_000)
        println("✅ Total: ${System.currentTimeMillis() - start}ms")
    }

}