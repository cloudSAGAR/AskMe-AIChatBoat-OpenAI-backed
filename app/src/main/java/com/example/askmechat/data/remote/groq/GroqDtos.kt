package com.example.askmechat.data.remote.groq

import com.google.gson.annotations.SerializedName

/**
 * Wire-format DTOs for Groq's Chat Completions endpoint (OpenAI-compatible):
 *   POST https://api.groq.com/openai/v1/chat/completions
 *   Authorization: Bearer <API_KEY>
 *
 * Only the subset of fields the demo actually uses is modelled.
 */

// ── Request ────────────────────────────────────────────────────────

data class GroqRequestDto(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<GroqMessageDto>,

    @SerializedName("stream")
    val stream: Boolean = true,

    @SerializedName("temperature")
    val temperature: Float? = null,

    @SerializedName("max_tokens")
    val maxTokens: Int? = null
)

data class GroqMessageDto(
    @SerializedName("role")
    val role: String, // "system" | "user" | "assistant"

    @SerializedName("content")
    val content: String
)

// ── Streaming response (SSE: `data: {JSON}` per chunk, terminator `[DONE]`) ─

data class GroqStreamResponseDto(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("choices")
    val choices: List<GroqChoiceDto>? = null,

    @SerializedName("error")
    val error: GroqErrorDto? = null
)

data class GroqChoiceDto(
    @SerializedName("index")
    val index: Int? = null,

    @SerializedName("delta")
    val delta: GroqDeltaDto? = null,

    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class GroqDeltaDto(
    @SerializedName("role")
    val role: String? = null,

    @SerializedName("content")
    val content: String? = null
)

data class GroqErrorDto(
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("code")
    val code: String? = null
)
