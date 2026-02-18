package com.example.pixeltracker

/**
 * Defines a contract for logging pixel lifecycle events.
 *
 * Implementations of this interface are responsible for handling pixel-related
 * events such as appearance, disappearance, refresh, and error reporting.
 *
 * A logger implementation may:
 * - Output events to Logcat (for debugging)
 * - Send events to a remote analytics or tracking endpoint
 * - Combine multiple logging strategies (e.g., console + network)
 *
 * This interface is intended to be implemented by SDK internal components
 * or by custom client-side integrations.
 */
interface PixelLogger {

    /**
     * Called when the pixel becomes visible on screen and an appearance event is recorded.
     *
     * @param pixelId Unique identifier of the pixel.
     * @param timestamp Timestamp of the event in milliseconds since epoch (as String).
     */
    fun logAppearance(pixelId: String, timestamp: String)

    /**
     * Called when the pixel is no longer visible on screen and a disappearance event is recorded.
     *
     * @param pixelId Unique identifier of the pixel.
     * @param timestamp Timestamp of the event in milliseconds since epoch (as String).
     */
    fun logDisappearance(pixelId: String, timestamp: String)

    /**
     * Called when a refresh event is triggered while the pixel remains continuously visible.
     *
     * A refresh represents a repeated visibility count after the configured
     * refresh interval has elapsed.
     *
     * @param pixelId Unique identifier of the pixel.
     * @param timestamp Timestamp of the event in milliseconds since epoch (as String).
     */
    fun logRefresh(pixelId: String, timestamp: String)

    /**
     * Called when an error occurs during pixel tracking or logging.
     *
     * @param pixelId Unique identifier of the pixel.
     * @param error Human-readable error message describing the failure.
     * @param timestamp Timestamp of the event in milliseconds since epoch (as String).
     */
    fun logError(pixelId: String, error: String, timestamp: String)

}