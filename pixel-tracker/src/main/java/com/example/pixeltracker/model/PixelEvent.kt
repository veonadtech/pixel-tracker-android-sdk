package com.example.pixeltracker.model

import com.google.gson.annotations.SerializedName

data class PixelEvent(
    @SerializedName("pixel_id")
    val pixelId: String,

    @SerializedName("event_type")
    val eventType: EventType,

    @SerializedName("timestamp")
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