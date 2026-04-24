package com.example.askmechat.domain.model

/**
 * Domain model for a single geographic point referenced by an AI answer
 * (e.g. a restaurant, bar or place of interest). Pure Kotlin — no Android
 * Parcelable binding here; if the presentation layer needs to pass it via
 * Bundle it can map to a Parcelable wrapper.
 */
data class MapPoint(
    val name: String,
    val address: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)
