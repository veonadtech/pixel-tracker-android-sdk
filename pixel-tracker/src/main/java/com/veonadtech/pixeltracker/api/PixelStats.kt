package com.veonadtech.pixeltracker.api

data class PixelStats(
    val totalAppearances: Int,
    val isCurrentlyVisible: Boolean,
    val refreshEnabled: Boolean,
    val nextRefreshInMs: Long
)