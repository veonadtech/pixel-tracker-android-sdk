package com.veonadtech.pixeltracker.internal.logger

import com.veonadtech.pixeltracker.api.PixelLogger
import com.veonadtech.pixeltracker.internal.model.PixelEvent
import com.veonadtech.pixeltracker.internal.network.PixelNetworkManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class PixelNetworkLoggerTest {

    // A simple stub that records enqueued events
    private val enqueuedEvents = mutableListOf<PixelEvent>()

    private lateinit var networkManager: PixelNetworkManager
    private lateinit var networkLogger: PixelNetworkLogger

    @Before
    fun setUp() {
        networkManager = mock {
            on { enqueueEvent(any()) } doAnswer { invocation ->
                enqueuedEvents.add(invocation.getArgument(0))
                Unit
            }
        }
        networkLogger = PixelNetworkLogger(networkManager)
    }

    @After
    fun tearDown() {
        networkLogger.shutdown()
        enqueuedEvents.clear()
    }

    // ─── delegation to inner PixelLogger ─────────────────────────────────────

    @Test
    fun `setDelegate stores and calls delegate for logAppearance`() {
        val delegate: PixelLogger = mock()
        networkLogger.setDelegate(delegate)

        networkLogger.logAppearance("px1", "1000")

        verify(delegate).logAppearance("px1", "1000")
    }

    @Test
    fun `setDelegate stores and calls delegate for logDisappearance`() {
        val delegate: PixelLogger = mock()
        networkLogger.setDelegate(delegate)

        networkLogger.logDisappearance("px1", "1001")

        verify(delegate).logDisappearance("px1", "1001")
    }

    @Test
    fun `setDelegate stores and calls delegate for logRefresh`() {
        val delegate: PixelLogger = mock()
        networkLogger.setDelegate(delegate)

        networkLogger.logRefresh("px1", "1002")

        verify(delegate).logRefresh("px1", "1002")
    }

    @Test
    fun `setDelegate stores and calls delegate for logError`() {
        val delegate: PixelLogger = mock()
        networkLogger.setDelegate(delegate)

        networkLogger.logError("px1", "boom", "1003")

        verify(delegate).logError("px1", "boom", "1003")
    }

    @Test
    fun `setDelegate to null removes delegate without crash`() {
        val delegate: PixelLogger = mock()
        networkLogger.setDelegate(delegate)
        networkLogger.setDelegate(null)

        networkLogger.logAppearance("px1", "1000")

        verifyNoMoreInteractions(delegate)
    }

    // ─── network enqueuing ────────────────────────────────────────────────────

    @Test
    fun `logAppearance enqueues APPEARANCE event`() {
        networkLogger.logAppearance("px_a", "2000")

        // Give coroutine a moment to enqueue
        Thread.sleep(200)

        assertTrue(enqueuedEvents.any {
            it.pixelId == "px_a" && it.eventType == PixelEvent.EventType.APPEARANCE
        })
    }

    @Test
    fun `logRefresh enqueues REFRESH event`() {
        networkLogger.logRefresh("px_r", "3000")
        Thread.sleep(200)

        assertTrue(enqueuedEvents.any {
            it.pixelId == "px_r" && it.eventType == PixelEvent.EventType.REFRESH
        })
    }

    @Test
    fun `logError enqueues ERROR event with error message`() {
        networkLogger.logError("px_e", "timeout", "4000")
        Thread.sleep(200)

        assertTrue(enqueuedEvents.any {
            it.pixelId == "px_e" &&
                it.eventType == PixelEvent.EventType.ERROR &&
                it.errorMessage == "timeout"
        })
    }

    @Test
    fun `logDisappearance does NOT enqueue a network event`() {
        networkLogger.logDisappearance("px_d", "5000")
        Thread.sleep(200)

        assertTrue(
            "Disappearance should not be sent over the network",
            enqueuedEvents.none { it.pixelId == "px_d" }
        )
    }

    // ─── enqueued event shape ─────────────────────────────────────────────────

    @Test
    fun `enqueued appearance event has valid timestamp`() {
        val ts = System.currentTimeMillis().toString()
        networkLogger.logAppearance("ts_px", ts)
        Thread.sleep(200)

        val event = enqueuedEvents.firstOrNull { it.pixelId == "ts_px" }
        assertNotNull(event)
        assertTrue(event!!.timestamp > 0)
    }

    @Test
    fun `enqueued event has non-blank sdkVersion`() {
        networkLogger.logAppearance("ver_px", "9000")
        Thread.sleep(200)

        val event = enqueuedEvents.firstOrNull { it.pixelId == "ver_px" }
        assertNotNull(event)
        assertTrue(event!!.sdkVersion.isNotBlank())
    }

    // ─── shutdown ─────────────────────────────────────────────────────────────

    @Test
    fun `shutdown does not throw`() {
        try {
            networkLogger.shutdown()
        } catch (e: Throwable) {
            fail("shutdown() should not throw: ${e.message}")
        }
    }
}