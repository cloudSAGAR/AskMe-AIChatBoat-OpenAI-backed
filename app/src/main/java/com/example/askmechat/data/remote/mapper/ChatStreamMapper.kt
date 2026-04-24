package com.example.askmechat.data.remote.mapper

import com.example.askmechat.data.remote.dto.MapPointDto
import com.example.askmechat.data.remote.dto.VisualizationResponseDto
import com.example.askmechat.domain.model.MapPoint

/**
 * Extension mappers that convert data-layer DTOs into pure domain models.
 * Kept small and explicit so the inverse direction is easy if needed.
 */
internal fun MapPointDto.toDomain(): MapPoint = MapPoint(
    name = name,
    address = address,
    lat = lat,
    lng = lng
)

internal fun VisualizationResponseDto.toDomain(): List<MapPoint> =
    mapData?.map { it.toDomain() }.orEmpty()
