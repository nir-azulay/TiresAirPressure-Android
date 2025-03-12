package com.example.tiresairpressuremanagement.domain.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceStatus(
    @Json(name = "Pressure") val pressure: Float,
    @Json(name = "operationMode") val operationMode: Int
) {
    companion object {
        const val MODE_STOP = 1
        const val MODE_INFLATE = 2
        const val MODE_DEFLATE = 3
    }
} 