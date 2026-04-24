package com.example.askmechat.domain.model

/**
 * Collection of [MapPoint]s attached to a single AI answer.
 * `ownerId` lets the presentation layer tie the group back to its source
 * message or session without coupling to the message itself.
 */
data class MapPointGroup(
    val ownerId: String,
    val points: List<MapPoint>
)
