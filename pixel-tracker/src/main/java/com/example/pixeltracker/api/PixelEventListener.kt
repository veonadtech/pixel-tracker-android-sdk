package com.example.pixeltracker.api

/**
 * Listener interface for receiving pixel lifecycle events.
 *
 * Implement this interface to be notified when a pixel's visibility state changes
 * or when errors occur during tracking.
 *
 * All callbacks are invoked on the main thread, making it safe to update UI
 * components directly from listener methods.
 *
 * @see PixelHandle.setEventListener
 */
interface PixelEventListener {

    /**
     * Called when the pixel becomes visible on screen.
     *
     * This event is triggered when the pixel first becomes visible according
     * to the configured visibilityThreshold.
     *
     * @param pixelId Unique identifier of the pixel that appeared.
     * @param timestamp Timestamp of the appearance event in milliseconds since epoch.
     */
    fun onAppearance(pixelId: String, timestamp: String)

    /**
     * Called when the pixel is no longer visible on screen.
     *
     * This event is triggered when the pixel leaves the visible area of the screen
     * or becomes fully obscured.
     *
     * @param pixelId Unique identifier of the pixel that disappeared.
     * @param timestamp Timestamp of the disappearance event in milliseconds since epoch.
     */
    fun onDisappearance(pixelId: String, timestamp: String)

    /**
     * Called when a refresh event occurs while the pixel remains continuously visible.
     *
     * Refresh events are generated at intervals specified by [PixelHandle.updateRefreshTime]
     * while the pixel stays on screen. This allows counting prolonged exposure time.
     *
     * @param pixelId Unique identifier of the pixel that refreshed.
     * @param timestamp Timestamp of the refresh event in milliseconds since epoch.
     */
    fun onRefresh(pixelId: String, timestamp: String)

    /**
     * Called when an error occurs during pixel tracking.
     *
     * Errors may include network failures, configuration issues, or internal
     * SDK errors. This callback provides an opportunity to log or report issues.
     *
     * @param pixelId Unique identifier of the pixel that encountered an error.
     * @param error Human-readable error message describing the failure.
     * @param timestamp Timestamp of the error event in milliseconds since epoch.
     */
    fun onError(pixelId: String, error: String, timestamp: String)
}