package com.veonadtech.pixeltracker.internal.network

import com.veonadtech.pixeltracker.internal.model.PixelEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PixelNetworkManagerTest {

    private lateinit var manager: PixelNetworkManager

    @Before
    fun setUp() {
        manager = PixelNetworkManager(
            baseUrl = "https://example.invalid", // unreachable – we only test queueing
            isDebugMode = false
        )
    }

    @After
    fun tearDown() {
        manager.shutdown()
    }

    private fun event(
        pixelId: String = "px",
        type: PixelEvent.EventType = PixelEvent.EventType.APPEARANCE
    ) = PixelEvent(
        pixelId = pixelId,
        eventType = type,
        timestamp = System.currentTimeMillis(),
        sdkVersion = "test"
    )

    // ─── isActive() ──────────────────────────────────────────────────────────

    @Test
    fun `isActive returns true after creation`() {
        assertTrue(manager.isActive())
    }

    @Test
    fun `isActive returns false after shutdown`() {
        manager.shutdown()
        assertFalse(manager.isActive())
    }

    // ─── enqueueEvent() ──────────────────────────────────────────────────────

    @Test
    fun `enqueueEvent single event does not throw`() {
        try {
            manager.enqueueEvent(event())
        } catch (e: Throwable) {
            fail("enqueueEvent() should not throw: ${e.message}")
        }
    }

    @Test
    fun `enqueueEvent multiple event types does not throw`() {
        PixelEvent.EventType.values().forEach { type ->
            manager.enqueueEvent(event(type = type))
        }
    }

    @Test
    fun `enqueueEvent up to channel capacity does not throw`() {
        // Channel capacity is 500; sending 490 events should succeed
        repeat(490) { i ->
            manager.enqueueEvent(event(pixelId = "px_$i"))
        }
    }

    @Test
    fun `enqueueEvent after shutdown does not crash the caller`() {
        manager.shutdown()
        // Channel is closed; trySend returns a failure result but must NOT propagate an exception
        // to the caller. Any result other than an exception is acceptable.
        try {
            manager.enqueueEvent(event())
            // if we reach here – fine, it silently dropped
        } catch (e: Exception) {
            // ClosedSendChannelException is internal to kotlinx.coroutines;
            // the public contract should not surface it. Fail explicitly so the
            // SDK team knows to add a try-catch in enqueueEvent().
            fail("enqueueEvent() after shutdown must not throw, but got ${e::class.simpleName}: ${e.message}")
        }
    }

    // ─── shutdown() ──────────────────────────────────────────────────────────

    @Test
    fun `shutdown cancels all processor jobs`() {
        manager.shutdown()
        assertFalse(manager.isActive())
    }

    @Test
    fun `shutdown is idempotent`() {
        manager.shutdown()
        try {
            manager.shutdown()
        } catch (e: Throwable) {
            fail("Second shutdown() should not throw: ${e.message}")
        }
    }

    // ─── PixelEvent model ─────────────────────────────────────────────────────

    @Test
    fun `PixelEvent stores all fields correctly`() {
        val ts = System.currentTimeMillis()
        val e = PixelEvent(
            pixelId = "my_pixel",
            eventType = PixelEvent.EventType.REFRESH,
            timestamp = ts,
            sdkVersion = "1.0",
            errorMessage = "err"
        )
        assertEquals("my_pixel", e.pixelId)
        assertEquals(PixelEvent.EventType.REFRESH, e.eventType)
        assertEquals(ts, e.timestamp)
        assertEquals("1.0", e.sdkVersion)
        assertEquals("err", e.errorMessage)
    }

    @Test
    fun `PixelEvent errorMessage defaults to null`() {
        val e = PixelEvent("px", PixelEvent.EventType.APPEARANCE, 1L, "v")
        assertNull(e.errorMessage)
    }

    @Test
    fun `PixelEvent EventType contains APPEARANCE REFRESH ERROR`() {
        val types = PixelEvent.EventType.values().toSet()
        assertTrue(types.contains(PixelEvent.EventType.APPEARANCE))
        assertTrue(types.contains(PixelEvent.EventType.REFRESH))
        assertTrue(types.contains(PixelEvent.EventType.ERROR))
    }
}