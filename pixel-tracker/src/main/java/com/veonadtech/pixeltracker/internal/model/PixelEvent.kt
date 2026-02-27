package com.veonadtech.pixeltracker.internal.model

import com.google.gson.annotations.SerializedName

internal data class PixelEvent(
    @SerializedName("pixel_id")
    val pixelId: String,

    @SerializedName("event_type")
    val eventType: EventType,

    @SerializedName("timestamp_ms")
    val timestamp: Long,

    @SerializedName("sdk_version")
    val sdkVersion: String,

    @SerializedName("error_message")
    val errorMessage: String? = null
) {
    enum class EventType {
        @SerializedName("appearance")
        APPEARANCE,

        @SerializedName("refresh")
        REFRESH,

        @SerializedName("error")
        ERROR
    }
}
