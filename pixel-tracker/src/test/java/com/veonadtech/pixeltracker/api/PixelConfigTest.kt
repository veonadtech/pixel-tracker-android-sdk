package com.veonadtech.pixeltracker.api

import org.junit.Assert.*
import org.junit.Test

class PixelConfigTest {

    // ─── valid construction ───────────────────────────────────────────────────

    @Test
    fun `creates config with only pixelId using defaults`() {
        val config = PixelConfig(pixelId = "test")
        assertEquals("test", config.pixelId)
        assertEquals(0L, config.refreshTimeSeconds)
        assertEquals(1, config.pixelSize)
        assertEquals(1, config.visibilityThreshold)
        assertNull(config.color)
    }

    @Test
    fun `creates config with all parameters`() {
        val config = PixelConfig(
            pixelId = "banner_42",
            refreshTimeSeconds = 30L,
            pixelSize = 10,
            visibilityThreshold = 50,
            color = 0xFFFF0000.toInt()
        )
        assertEquals("banner_42", config.pixelId)
        assertEquals(30L, config.refreshTimeSeconds)
        assertEquals(10, config.pixelSize)
        assertEquals(50, config.visibilityThreshold)
        assertEquals(0xFFFF0000.toInt(), config.color)
    }

    @Test
    fun `null color is allowed`() {
        val config = PixelConfig(pixelId = "px", color = null)
        assertNull(config.color)
    }

    @Test
    fun `refreshTimeSeconds zero is valid (refresh disabled)`() {
        val config = PixelConfig(pixelId = "px", refreshTimeSeconds = 0L)
        assertEquals(0L, config.refreshTimeSeconds)
    }

    @Test
    fun `visibilityThreshold of 1 is valid`() {
        val config = PixelConfig(pixelId = "px", visibilityThreshold = 1)
        assertEquals(1, config.visibilityThreshold)
    }

    @Test
    fun `two identical configs are equal (data class)`() {
        val a = PixelConfig("id", 10L, 2, 5)
        val b = PixelConfig("id", 10L, 2, 5)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `configs with different pixelId are not equal`() {
        assertNotEquals(PixelConfig("a"), PixelConfig("b"))
    }

    // ─── init validation ──────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `blank pixelId throws IllegalArgumentException`() {
        PixelConfig(pixelId = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty pixelId throws IllegalArgumentException`() {
        PixelConfig(pixelId = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pixelSize zero throws IllegalArgumentException`() {
        PixelConfig(pixelId = "px", pixelSize = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative pixelSize throws IllegalArgumentException`() {
        PixelConfig(pixelId = "px", pixelSize = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative refreshTimeSeconds throws IllegalArgumentException`() {
        PixelConfig(pixelId = "px", refreshTimeSeconds = -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `visibilityThreshold zero throws IllegalArgumentException`() {
        PixelConfig(pixelId = "px", visibilityThreshold = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative visibilityThreshold throws IllegalArgumentException`() {
        PixelConfig(pixelId = "px", visibilityThreshold = -5)
    }
}