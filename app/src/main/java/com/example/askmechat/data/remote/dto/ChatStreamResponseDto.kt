package com.example.askmechat.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * A single wire-format chunk from the streaming chat endpoint.
 * Maps to a [com.example.askmechat.domain.model.StreamChunk] via
 * [com.example.askmechat.data.remote.mapper.ChatStreamMapper].
 */
data class ChatStreamResponseDto(
    @SerializedName("type")
    val type: String,

    @SerializedName("content")
    val content: String? = null,

    @SerializedName("metadata")
    val metadata: StreamMetadataDto? = null
)

data class StreamMetadataDto(
    @SerializedName("nodeId")
    val nodeId: String? = null,

    @SerializedName("nodeName")
    val nodeName: String? = null,

    @SerializedName("itemIndex")
    val itemIndex: Int? = null,

    @SerializedName("runIndex")
    val runIndex: Int? = null,

    @SerializedName("timestamp")
    val timestamp: Long? = null
)
