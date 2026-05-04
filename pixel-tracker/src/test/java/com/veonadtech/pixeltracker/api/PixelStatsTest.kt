package com.veonadtech.pixeltracker.api

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PixelStatsTest {

    private fun stats(
        appearances: Int = 0,
        visible: Boolean = false,
        refreshEnabled: Boolean = false,
        nextRefreshMs: Long = 0L
    ) = PixelStats(
        totalAppearances = AtomicInteger(appearances),
        isCurrentlyVisible = visible,
        refreshEnabled = refreshEnabled,
        nextRefreshInMs = nextRefreshMs
    )

    @Test
    fun `default stats have zero appearances`() {
        assertEquals(0, stats().totalAppearances.get())
    }

    @Test
    fun `isCurrentlyVisible reflects constructor argument`() {
        assertFalse(stats(visible = false).isCurrentlyVisible)
        assertTrue(stats(visible = true).isCurrentlyVisible)
    }

    @Test
    fun `refreshEnabled reflects constructor argument`() {
        assertFalse(stats(refreshEnabled = false).refreshEnabled)
        assertTrue(stats(refreshEnabled = true).refreshEnabled)
    }

    @Test
    fun `nextRefreshInMs reflects constructor argument`() {
        assertEquals(5000L, stats(nextRefreshMs = 5000L).nextRefreshInMs)
    }

    @Test
    fun `totalAppearances AtomicInteger can be incremented externally`() {
        val s = stats(appearances = 3)
        s.totalAppearances.incrementAndGet()
        assertEquals(4, s.totalAppearances.get())
    }

    @Test
    fun `two stats objects with same values are equal (data class)`() {
        val ai = AtomicInteger(2)
        // data class compares AtomicInteger by reference – we just check field values manually
        val a = PixelStats(ai, true, true, 1000L)
        val b = PixelStats(ai, true, true, 1000L)
        assertEquals(a.isCurrentlyVisible, b.isCurrentlyVisible)
        assertEquals(a.refreshEnabled, b.refreshEnabled)
        assertEquals(a.nextRefreshInMs, b.nextRefreshInMs)
        assertSame(a.totalAppearances, b.totalAppearances)
    }
}