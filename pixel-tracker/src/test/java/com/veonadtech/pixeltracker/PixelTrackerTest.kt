package com.veonadtech.pixeltracker

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.veonadtech.pixeltracker.api.PixelConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class PixelTrackerTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    @After
    fun resetSdk() {
        // Always start from a clean state
        PixelTracker.shutdown()
    }

    // ─── isInitialized() ─────────────────────────────────────────────────────

    @Test
    fun `isInitialized returns false before initialize`() {
        assertFalse(PixelTracker.isInitialized())
    }

    @Test
    fun `isInitialized returns true after successful initialize`() {
        PixelTracker.initialize("https://example.com", false)
        assertTrue(PixelTracker.isInitialized())
    }

    @Test
    fun `isInitialized returns false after shutdown`() {
        PixelTracker.initialize("https://example.com", false)
        PixelTracker.shutdown()
        assertFalse(PixelTracker.isInitialized())
    }

    // ─── initialize() ────────────────────────────────────────────────────────

    @Test
    fun `initialize with valid URL calls Success callback`() {
        var status: InitStatus? = null
        PixelTracker.initialize("https://example.com", false) { status = it }
        assertTrue(status is InitStatus.Success)
    }

    @Test
    fun `initialize with blank URL calls Failure callback`() {
        var status: InitStatus? = null
        PixelTracker.initialize("   ", false) { status = it }
        assertTrue(status is InitStatus.Failure)
    }

    @Test
    fun `initialize with empty URL calls Failure callback`() {
        var status: InitStatus? = null
        PixelTracker.initialize("", false) { status = it }
        assertTrue(status is InitStatus.Failure)
        val failure = status as InitStatus.Failure
        assertEquals("Invalid configuration", failure.reason)
        assertTrue(failure.exception is IllegalArgumentException)
    }

    @Test
    fun `initialize twice returns Success with already-initialized message`() {
        PixelTracker.initialize("https://example.com", false)
        var secondStatus: InitStatus? = null
        PixelTracker.initialize("https://other.com", false) { secondStatus = it }
        assertTrue(secondStatus is InitStatus.Success)
        assertEquals("SDK already initialized", (secondStatus as InitStatus.Success).message)
    }

    @Test
    fun `initialize without callback does not throw`() {
        try {
            PixelTracker.initialize("https://example.com", false)
        } catch (e: Throwable) {
            fail("Should not throw: ${e.message}")
        }
    }

    @Test
    fun `initialize in debug mode does not throw`() {
        var status: InitStatus? = null
        PixelTracker.initialize("https://example.com", isDebugMode = true) { status = it }
        assertTrue(status is InitStatus.Success)
    }

    // ─── attach() ────────────────────────────────────────────────────────────

    @Test
    fun `attach before initialize returns null`() {
        val result = PixelTracker.attach(
            context,
            FrameLayout(context),
            PixelConfig("test")
        )
        assertNull(result)
    }

    @Test
    fun `attach after initialize returns non-null PixelHandle`() {
        PixelTracker.initialize("https://example.com", false)
        val handle = PixelTracker.attach(context, FrameLayout(context), PixelConfig("px"))
        assertNotNull(handle)
    }

    @Test
    fun `attach adds pixel view to container`() {
        PixelTracker.initialize("https://example.com", false)
        val container = FrameLayout(context)
        assertEquals(0, container.childCount)
        PixelTracker.attach(context, container, PixelConfig("px"))
        assertEquals(1, container.childCount)
    }

    @Test
    fun `attach with different configs returns different handles`() {
        PixelTracker.initialize("https://example.com", false)
        val h1 = PixelTracker.attach(context, FrameLayout(context), PixelConfig("px1"))
        val h2 = PixelTracker.attach(context, FrameLayout(context), PixelConfig("px2"))
        assertNotSame(h1, h2)
    }

    @Test
    fun `shutdown after attach does not throw`() {
        PixelTracker.initialize("https://example.com", false)
        PixelTracker.attach(context, FrameLayout(context), PixelConfig("px"))
        try {
            PixelTracker.shutdown()
        } catch (e: Throwable) {
            fail("shutdown() should not throw: ${e.message}")
        }
    }

    @Test
    fun `shutdown is idempotent`() {
        PixelTracker.initialize("https://example.com", false)
        PixelTracker.shutdown()
        try {
            PixelTracker.shutdown()
        } catch (e: Throwable) {
            fail("Second shutdown() should not throw: ${e.message}")
        }
    }
}