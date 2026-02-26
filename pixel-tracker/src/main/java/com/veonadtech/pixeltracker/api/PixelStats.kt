package com.veonadtech.pixeltracker.api

import java.util.concurrent.atomic.AtomicInteger

data class PixelStats(
    val totalAppearances: AtomicInteger,
    val isCurrentlyVisible: Boolean,
    val refreshEnabled: Boolean,
    val nextRefreshInMs: Long
)