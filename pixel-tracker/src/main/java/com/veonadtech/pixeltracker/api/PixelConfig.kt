package com.veonadtech.pixeltracker.api

import androidx.annotation.ColorInt

data class PixelConfig(

    val pixelId: String,
    val refreshTimeSeconds: Long = 0,
    val pixelSize: Int = 1,
    val visibilityThreshold: Int = 1,
    @param:ColorInt val color: Int? = null
) {
    init {
        require(pixelId.isNotBlank()) { "pixelId must not be blank" }
        require(pixelSize > 0) { "pixelSize must be positive" }
        require(refreshTimeSeconds >= 0) { "refreshTimeSeconds must be non-negative" }
        require(visibilityThreshold > 0) { "visibilityThreshold must be positive" }
    }
}
