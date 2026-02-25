package com.veonadtech.pixeltracker.api

/**
 * Provides a handle to control and interact with a pixel tracking instance.
 *
 * A PixelHandle represents a single tracked pixel and allows the host application
 * to manage its lifecycle, update its configuration, and retrieve statistics.
 *
 * Implementations of this interface are thread-safe and may be used from any thread
 * unless explicitly noted otherwise.
 */
interface PixelHandle {

    /**
     * Starts tracking visibility for this pixel.
     *
     * When started, the pixel will begin monitoring its on-screen visibility
     * according to its configured parameters (threshold, refresh interval, etc.).
     *
     * This method is idempotent: calling it multiple times has no additional effect.
     *
     * @see stop
     */
    fun start()

    /**
     * Stops tracking visibility for this pixel.
     *
     * When stopped, the pixel will no longer monitor its visibility or generate events.
     * Any pending timers or checks are cancelled.
     *
     * This method is idempotent: calling it multiple times has no additional effect.
     *
     * @see start
     */
    fun stop()

    /**
     * Updates the refresh interval for this pixel.
     *
     * The refresh interval determines how often a continuously visible pixel
     * will generate a new [PixelLogger.logRefresh] event.
     *
     * Setting the value to 0 disables refresh events entirely (only initial appearance
     * will be counted).
     *
     * @param seconds Refresh interval in seconds. Must be >= 0.
     * @throws IllegalArgumentException if seconds is negative.
     */
    fun updateRefreshTime(seconds: Long)

    /**
     * Sets the interval at which the pixel's visibility is checked.
     *
     * The visibility check interval determines how frequently the system
     * evaluates whether the pixel is still visible on screen. A shorter interval
     * provides more precise tracking at the cost of higher resource usage.
     *
     * The default value is 3 seconds (3000ms) unless specified otherwise in [PixelConfig].
     *
     * @param seconds Visibility check interval in seconds. Must be >= 0.
     *                Setting to 0 will cause continuous checking (not recommended).
     * @throws IllegalArgumentException if seconds is negative.
     *
     * @see updateRefreshTime
     */
    fun setVisibilityCheckInterval(seconds: Long)

    /**
     * Returns current statistics for this pixel.
     *
     * The returned statistics provide information about the pixel's tracking state,
     * including total appearances, visibility status, and timing information.
     *
     * @return A [PixelStats] object containing the current pixel statistics.
     */
    fun getStats(): PixelStats

    /**
     * Registers a listener to receive pixel lifecycle events.
     *
     * The provided listener will be notified when this pixel appears, disappears,
     * refreshes, or encounters an error. Only one listener can be active at a time;
     * calling this method replaces any previously set listener.
     *
     * @param listener The listener to register, or null to remove the current listener.
     */
    fun setEventListener(listener: PixelEventListener?)

    /**
     * Permanently destroys this pixel handle and releases all associated resources.
     *
     * After calling this method, the handle is no longer valid and should not be used.
     * Any further method calls may result in undefined behavior.
     *
     * This method is automatically called when the associated [com.veonadtech.pixeltracker.internal.tracker.PixelTrackerView]
     * is detached from the window, but may be invoked earlier if explicit cleanup
     * is required.
     */
    fun destroy()
}