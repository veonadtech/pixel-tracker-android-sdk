package com.example.pixeltracker.internal.logger

import android.util.Log
import com.example.pixeltracker.BuildConfig
import com.example.pixeltracker.api.PixelLogger

/**
 * Implementation of PixelLogger that logs events to Android LogCat only in debug mode.
 */
internal class DefaultPixelLogger : PixelLogger {

    private val libraryVersion: String by lazy {
        try {
            BuildConfig.LIBRARY_VERSION
        } catch (_: Exception) {
            "unknown"
        }
    }

    var isDebugMode: Boolean = false

    override fun logAppearance(pixelId: String, timestamp: String) {
        if (isDebugMode) {
            Log.d("PixelTracker", "[$libraryVersion] 🔵 Pixel appeared: $pixelId at $timestamp")
        }
    }

    override fun logDisappearance(pixelId: String, timestamp: String) {
        if (isDebugMode) {
            Log.d("PixelTracker", "[$libraryVersion] 🔴 Pixel disappeared: $pixelId at $timestamp")
        }
    }

    override fun logRefresh(pixelId: String, timestamp: String) {
        if (isDebugMode) {
            Log.d("PixelTracker", "[$libraryVersion] 🔄 Pixel refresh counted: $pixelId at $timestamp")
        }
    }

    override fun logError(pixelId: String, error: String, timestamp: String) {
        if (isDebugMode) {
            Log.e("PixelTracker", "[$libraryVersion] ❌ Pixel $pixelId error: $error at $timestamp")
        }
    }
}