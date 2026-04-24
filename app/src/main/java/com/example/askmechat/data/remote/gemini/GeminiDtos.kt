package com.example.askmechat.data.remote.gemini

import com.google.gson.annotations.SerializedName

/**
 * Wire-format DTOs for Gemini's REST streaming endpoint:
 *   POST /v1beta/models/{model}:streamGenerateContent?alt=sse&key={API_KEY}
 *
 * Only the subset of fields the demo actually uses is modelled.
 */

// ── Request ────────────────────────────────────────────────────────

data class GeminiRequestDto(
    @SerializedName("contents")
    val contents: List<GeminiContentDto>,

    @SerializedName("systemInstruction")
    val systemInstruction: GeminiContentDto? = null,

    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfigDto? = null
)

data class GeminiContentDto(
    @SerializedName("role")
    val role: String? = null, // "user" or "model"; omit for systemInstruction
    @SerializedName("parts")
    val parts: List<GeminiPartDto>
)

data class GeminiPartDto(
    @SerializedName("text")
    val text: String
)

data class GeminiGenerationConfigDto(
    @SerializedName("temperature")
    val temperature: Float? = null,

    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int? = null
)

// ── Streaming response (SSE: `data: {JSON}`) ───────────────────────

data class GeminiStreamResponseDto(
    @SerializedName("candidates")
    val candidates: List<GeminiCandidateDto>? = null,

    @SerializedName("error")
    val error: GeminiErrorDto? = null
)

data class GeminiCandidateDto(
    @SerializedName("content")
    val content: GeminiContentDto? = null,

    @SerializedName("finishReason")
    val finishReason: String? = null
)

data class GeminiErrorDto(
    @SerializedName("code")
    val code: Int? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("status")
    val status: String? = null
)
