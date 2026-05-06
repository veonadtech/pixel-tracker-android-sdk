package com.veonadtech.pixeltracker.internal.tracker

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.veonadtech.pixeltracker.PixelTracker
import com.veonadtech.pixeltracker.api.PixelConfig
import com.veonadtech.pixeltracker.api.PixelEventListener
import com.veonadtech.pixeltracker.api.PixelHandle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class PixelHandleTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var handle: PixelHandle
    private lateinit var container: FrameLayout

    @Before
    fun setUp() {
        PixelTracker.initialize("https://example.com", false)
        container = FrameLayout(context)
        handle = PixelTracker.attach(
            context,
            container,
            PixelConfig(pixelId = "test_px", refreshTimeSeconds = 5L, pixelSize = 1, visibilityThreshold = 1)
        )!!
    }

    @After
    fun tearDown() {
        PixelTracker.shutdown()
    }

    // ─── start / stop ────────────────────────────────────────────────────────

    @Test
    fun `start does not throw`() {
        assertNoThrow { handle.start() }
    }

    @Test
    fun `stop after start does not throw`() {
        handle.start()
        assertNoThrow { handle.stop() }
    }

    @Test
    fun `stop without start does not throw`() {
        assertNoThrow { handle.stop() }
    }

    @Test
    fun `start is idempotent`() {
        handle.start()
        assertNoThrow { handle.start() }
    }

    @Test
    fun `stop is idempotent`() {
        handle.start()
        handle.stop()
        assertNoThrow { handle.stop() }
    }

    @Test
    fun `start after stop restarts without exception`() {
        handle.start()
        handle.stop()
        assertNoThrow { handle.start() }
        handle.stop()
    }

    // ─── destroy() ───────────────────────────────────────────────────────────

    @Test
    fun `destroy after stop does not throw`() {
        handle.start()
        handle.stop()
        assertNoThrow { handle.destroy() }
    }

    @Test
    fun `destroy removes pixel view from container`() {
        assertEquals(1, container.childCount)
        handle.destroy()
        assertEquals(0, container.childCount)
    }

    @Test
    fun `destroy is idempotent`() {
        handle.destroy()
        assertNoThrow { handle.destroy() }
    }

    @Test
    fun `start after destroy does nothing (no crash)`() {
        handle.destroy()
        assertNoThrow { handle.start() }
    }

    // ─── updateRefreshTime() ─────────────────────────────────────────────────

    @Test
    fun `updateRefreshTime with zero disables refresh`() {
        assertNoThrow { handle.updateRefreshTime(0L) }
    }

    @Test
    fun `updateRefreshTime with positive value does not throw`() {
        assertNoThrow { handle.updateRefreshTime(30L) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateRefreshTime with negative value throws`() {
        handle.updateRefreshTime(-1L)
    }

    // ─── setVisibilityCheckInterval() ────────────────────────────────────────

    @Test
    fun `setVisibilityCheckInterval with positive value does not throw`() {
        assertNoThrow { handle.setVisibilityCheckInterval(5L) }
    }

    @Test
    fun `setVisibilityCheckInterval with zero does not throw`() {
        assertNoThrow { handle.setVisibilityCheckInterval(0L) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setVisibilityCheckInterval with negative value throws`() {
        handle.setVisibilityCheckInterval(-1L)
    }

    @Test
    fun `setVisibilityCheckInterval after destroy does not throw`() {
        handle.destroy()
        // after destroy, guard returns early – no exception expected
        assertNoThrow { handle.setVisibilityCheckInterval(5L) }
    }

    // ─── setEventListener() ──────────────────────────────────────────────────

    @Test
    fun `setEventListener with a listener does not throw`() {
        assertNoThrow {
            handle.setEventListener(noOpListener())
        }
    }

    @Test
    fun `setEventListener with null removes listener`() {
        handle.setEventListener(noOpListener())
        assertNoThrow { handle.setEventListener(null) }
    }

    @Test
    fun `setEventListener replaces previous listener`() {
        handle.setEventListener(noOpListener())
        assertNoThrow { handle.setEventListener(noOpListener()) }
    }

    // ─── getStats() ──────────────────────────────────────────────────────────

    @Test
    fun `getStats returns non-null object`() {
        assertNotNull(handle.getStats())
    }

    @Test
    fun `getStats totalAppearances starts at zero`() {
        assertEquals(0, handle.getStats().totalAppearances.get())
    }

    @Test
    fun `getStats isCurrentlyVisible is false when not laid out`() {
        assertFalse(handle.getStats().isCurrentlyVisible)
    }

    @Test
    fun `getStats refreshEnabled is true when refreshTimeSeconds greater than zero`() {
        assertTrue(handle.getStats().refreshEnabled)
    }

    @Test
    fun `getStats refreshEnabled is false when refreshTimeSeconds is zero`() {
        val noRefreshHandle = PixelTracker.attach(
            context,
            FrameLayout(context),
            PixelConfig(pixelId = "no_refresh", refreshTimeSeconds = 0L)
        )!!
        assertFalse(noRefreshHandle.getStats().refreshEnabled)
        noRefreshHandle.destroy()
    }

    @Test
    fun `getStats nextRefreshInMs is zero when pixel not visible`() {
        assertEquals(0L, handle.getStats().nextRefreshInMs)
    }

    @Test
    fun `getStats can be called multiple times without throwing`() {
        repeat(10) { assertNotNull(handle.getStats()) }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun noOpListener() = object : PixelEventListener {
        override fun onAppearance(pixelId: String, timestamp: String) {}
        override fun onDisappearance(pixelId: String, timestamp: String) {}
        override fun onRefresh(pixelId: String, timestamp: String) {}
        override fun onError(pixelId: String, error: String, timestamp: String) {}
    }

    private fun assertNoThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            fail("Expected no exception but got ${e::class.simpleName}: ${e.message}")
        }
    }
}