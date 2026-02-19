package com.example.pixeltracker.api

import androidx.annotation.ColorInt

data class PixelConfig(

    val pixelId: String,
    val refreshTimeSeconds: Long = 0,
    val pixelSize: Int = 1,
    val visibilityThreshold: Int = 1,
    @param:ColorInt val color: Int? = null
)