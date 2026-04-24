package com.example.askmechat.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Parsed structure of the visualization JSON block emitted by the AI when
 * it wants to attach map data to an answer.
 */
data class VisualizationResponseDto(
    @SerializedName("visualizationType")
    val visualizationType: String? = null,

    @SerializedName("mapData")
    val mapData: List<MapPointDto>? = null,

    @SerializedName("reasoning")
    val reasoning: String? = null
)

data class MapPointDto(
    @SerializedName("name")
    val name: String = "",

    @SerializedName("address")
    val address: String = "",

    @SerializedName("lat")
    val lat: Double = 0.0,

    @SerializedName("lng")
    val lng: Double = 0.0
)
