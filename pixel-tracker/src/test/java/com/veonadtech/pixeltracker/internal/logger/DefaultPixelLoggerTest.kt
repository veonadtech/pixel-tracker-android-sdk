package com.veonadtech.pixeltracker.internal.logger

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DefaultPixelLoggerTest {

    private lateinit var logger: DefaultPixelLogger

    @Before
    fun setUp() {
        logger = DefaultPixelLogger()
    }

    @Test
    fun `isDebugMode defaults to false`() {
        assertFalse(logger.isDebugMode)
    }

    @Test
    fun `isDebugMode can be set to true`() {
        logger.isDebugMode = true
        assertTrue(logger.isDebugMode)
    }

    // When isDebugMode = false, all log calls must be no-ops (no crash)

    @Test
    fun `logAppearance in non-debug mode does not throw`() {
        logger.isDebugMode = false
        logger.logAppearance("px1", "1234567890")
    }

    @Test
    fun `logDisappearance in non-debug mode does not throw`() {
        logger.isDebugMode = false
        logger.logDisappearance("px1", "1234567890")
    }

    @Test
    fun `logRefresh in non-debug mode does not throw`() {
        logger.isDebugMode = false
        logger.logRefresh("px1", "1234567890")
    }

    @Test
    fun `logError in non-debug mode does not throw`() {
        logger.isDebugMode = false
        logger.logError("px1", "Network failure", "1234567890")
    }

    // In debug mode all calls should also be safe

    @Test
    fun `logAppearance in debug mode does not throw`() {
        logger.isDebugMode = true
        logger.logAppearance("px1", "1234567890")
    }

    @Test
    fun `logDisappearance in debug mode does not throw`() {
        logger.isDebugMode = true
        logger.logDisappearance("px1", "1234567890")
    }

    @Test
    fun `logRefresh in debug mode does not throw`() {
        logger.isDebugMode = true
        logger.logRefresh("px1", "1234567890")
    }

    @Test
    fun `logError in debug mode does not throw`() {
        logger.isDebugMode = true
        logger.logError("px1", "Network failure", "1234567890")
    }
}