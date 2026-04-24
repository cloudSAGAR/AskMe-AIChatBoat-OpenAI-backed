package com.example.askmechat.data.repository

import android.util.Log
import com.example.askmechat.data.remote.gemini.GeminiConfig
import com.example.askmechat.data.remote.gemini.GeminiContentDto
import com.example.askmechat.data.remote.gemini.GeminiGenerationConfigDto
import com.example.askmechat.data.remote.gemini.GeminiPartDto
import com.example.askmechat.data.remote.gemini.GeminiRequestDto
import com.example.askmechat.data.remote.gemini.GeminiStreamResponseDto
import com.example.askmechat.data.remote.network.RetrofitProvider
import com.example.askmechat.domain.model.StreamChunk
import com.example.askmechat.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * [ChatRepository] backed by Gemini's REST streaming endpoint:
 *
 *   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent
 *        ?alt=sse&key={API_KEY}
 *
 * Why REST instead of the official `com.google.ai.client.generativeai`
 * SDK? — the SDK ships Ktor transitively in a way that's unreliable on
 * Android: the first call throws "Failed resolution of:
 * io/ktor/client/plugins/HttpTimeout" because the plugin class isn't on
 * the dex classpath at runtime. Going straight at REST keeps us on the
 * same OkHttp stack already used by the custom-REST impl, so we get:
 *   • no extra runtime deps
 *   • no Proguard/R8 keep rules
 *   • the same streaming pattern (line-delimited JSON → Flow<StreamChunk>)
 *
 * Multi-turn chat: the `contents` field on the request preserves the
 * full conversation, so we keep a per-session [MutableList] of turns and
 * append both the user's prompt and the model's completed reply.
 */
class GeminiChatRepositoryImpl(
    private val apiKey: String = GeminiConfig.API_KEY,
    private val modelName: String = GeminiConfig.MODEL_NAME
) : ChatRepository {

    /** Conversation history per sessionId — mirrors what `Chat` did in the SDK. */
    private val sessions = mutableMapOf<String, MutableList<GeminiContentDto>>()

    private val okHttp get() = RetrofitProvider.okHttpClient
    private val gson get() = RetrofitProvider.gson

    override fun sendPrompt(
        prompt: String,
        sessionId: String,
        deviceId: String,
        userCity: String,
        userId: Int?
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            emit(
                StreamChunk.Error(
                    "Missing Gemini API key. Open GeminiConfig.kt and paste your " +
                        "key from https://aistudio.google.com/app/apikey."
                )
            )
            return@flow
        }

        emit(StreamChunk.Loading)

        val history = sessions.getOrPut(sessionId) { mutableListOf() }
        val enrichedPrompt = buildPromptWithContext(prompt, userCity)

        // Append user turn BEFORE the request so the wire payload contains
        // the full conversation. We'll append the model's reply after.
        history.add(
            GeminiContentDto(
                role = "user",
                parts = listOf(GeminiPartDto(text = enrichedPrompt))
            )
        )

        val requestDto = GeminiRequestDto(
            contents = history.toList(),
            systemInstruction = GeminiContentDto(
                role = null,
                parts = listOf(GeminiPartDto(text = GeminiConfig.SYSTEM_INSTRUCTION))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = GeminiConfig.TEMPERATURE,
                maxOutputTokens = GeminiConfig.MAX_OUTPUT_TOKENS
            )
        )

        val url = buildString {
            append("https://generativelanguage.googleapis.com/v1beta/models/")
            append(modelName)
            append(":streamGenerateContent?alt=sse&key=")
            append(apiKey)
        }
        val requestJson = gson.toJson(requestDto)

        // Log the outgoing payload (truncated on large prompts). The URL
        // is logged without the API key so copy/paste-safe.
        logLongLines("REQUEST BODY", requestJson)

        val body = requestJson
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        val accumulated = StringBuilder()
        var textBlockFinalized = false

        try {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    // Full error body goes to Logcat so we can see the raw
                    // Google response even though the OkHttp interceptor
                    // is set to HEADERS (required for streaming).
                    Log.e(TAG, "<-- HTTP ${response.code} ${response.message}")
                    logLongLines("RESPONSE BODY (error)", bodyString)
                    emit(StreamChunk.Error(friendlyErrorFor(response.code, bodyString)))
                    // Undo the optimistic history append on failure
                    history.removeAt(history.lastIndex)
                    return@use
                }
                Log.d(TAG, "<-- HTTP ${response.code} (streaming)")

                val responseBody = response.body ?: run {
                    emit(StreamChunk.Error("Empty response body from Gemini"))
                    history.removeAt(history.lastIndex)
                    return@use
                }

                BufferedReader(InputStreamReader(responseBody.byteStream())).use { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val line: String? = try {
                            reader.readLine()
                        } catch (ioe: IOException) {
                            Log.w(TAG, "Gemini stream read aborted: ${ioe.message}")
                            null
                        } ?: break

                        if (line.isNullOrBlank()) continue

                        // SSE frames look like `data: { ... }`. Strip the prefix.
                        val jsonLine = when {
                            line.startsWith("data: ") -> line.removePrefix("data: ")
                            line.startsWith("data:") -> line.removePrefix("data:")
                            else -> continue
                        }.trim()
                        if (jsonLine.isEmpty() || jsonLine == "[DONE]") continue

                        try {
                            val chunk = gson.fromJson(jsonLine, GeminiStreamResponseDto::class.java)

                            chunk.error?.let { err ->
                                emit(
                                    StreamChunk.Error(
                                        "Gemini: ${err.message ?: err.status ?: "unknown error"}"
                                    )
                                )
                                return@use
                            }

                            val textChunk = chunk.candidates
                                ?.firstOrNull()
                                ?.content
                                ?.parts
                                ?.mapNotNull { it.text }
                                ?.joinToString(separator = "")
                                .orEmpty()

                            if (textChunk.isNotEmpty()) {
                                accumulated.append(textChunk)
                                emit(StreamChunk.Streaming(accumulated.toString()))
                            }
                        } catch (parseEx: Exception) {
                            Log.e(TAG, "Failed to parse SSE line: $jsonLine", parseEx)
                        }
                    }

                    if (accumulated.isNotEmpty()) {
                        textBlockFinalized = true
                        emit(StreamChunk.Success(accumulated.toString()))
                        // Record the model's full reply as a turn in history.
                        history.add(
                            GeminiContentDto(
                                role = "model",
                                parts = listOf(GeminiPartDto(text = accumulated.toString()))
                            )
                        )
                    } else {
                        emit(StreamChunk.Error("Gemini returned an empty response"))
                        history.removeAt(history.lastIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini stream failed", e)
            if (accumulated.isNotEmpty() && !textBlockFinalized) {
                emit(StreamChunk.PartialSuccess(accumulated.toString()))
                // Best-effort: record the partial reply so follow-ups still
                // have context, albeit incomplete.
                history.add(
                    GeminiContentDto(
                        role = "model",
                        parts = listOf(GeminiPartDto(text = accumulated.toString()))
                    )
                )
            } else {
                emit(StreamChunk.Error(e.message ?: "Gemini request failed"))
                if (history.isNotEmpty()) history.removeAt(history.lastIndex)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getStarterSuggestions(): List<String> = STARTER_SUGGESTIONS

    private fun buildPromptWithContext(prompt: String, userCity: String): String {
        return if (userCity.isNotBlank()) "[User is in $userCity]\n$prompt" else prompt
    }

    /**
     * Logcat drops messages longer than ~4000 chars. Split and prefix each
     * fragment so long JSON blobs survive.
     */
    private fun logLongLines(tagLabel: String, payload: String) {
        Log.w(TAG, "=== $tagLabel (${payload.length} chars) ===")
        if (payload.isEmpty()) {
            Log.w(TAG, "<empty>")
            return
        }
        val chunkSize = 3500
        var offset = 0
        var idx = 0
        while (offset < payload.length) {
            val end = (offset + chunkSize).coerceAtMost(payload.length)
            Log.w(TAG, "[$tagLabel #$idx] ${payload.substring(offset, end)}")
            offset = end
            idx++
        }
        Log.w(TAG, "=== END $tagLabel ===")
    }

    /**
     * Turns Google's verbose JSON error body into a short, human-facing
     * message. Recognises the common auth/quota cases and points at the
     * exact fix. Falls back to just the `error.message` field for
     * everything else — never the raw JSON dump.
     */
    private fun friendlyErrorFor(httpCode: Int, bodyString: String): String {
        val apiMessage: String? = runCatching {
            val env = gson.fromJson(bodyString, GeminiErrorEnvelope::class.java)
            env?.error?.message
        }.getOrNull()

        return when {
            httpCode == 429 && (apiMessage?.contains("limit: 0") == true ||
                apiMessage?.contains("free_tier_requests") == true) ->
                "Your API key's project has no free-tier quota for this model. " +
                    "Fix: either enable the Generative Language API in the key's " +
                    "Google Cloud project, or create a brand-new key via " +
                    "aistudio.google.com/app/apikey (“Create API key in new project”)."

            httpCode == 429 ->
                "Rate-limited by Gemini. ${apiMessage ?: "Try again in a few seconds."}"

            httpCode == 403 ->
                "Gemini refused the request (403). Usually means the API key " +
                    "is invalid, disabled, or the Generative Language API is " +
                    "not enabled on its project."

            httpCode == 404 ->
                "Model \"$modelName\" was not found on v1beta. Try " +
                    "gemini-2.0-flash, gemini-flash-latest or gemini-2.5-flash " +
                    "in GeminiConfig.kt."

            httpCode in 500..599 ->
                "Gemini had a server error ($httpCode). This is on Google's side — " +
                    "please try again in a moment."

            else ->
                apiMessage?.let { "Gemini error ($httpCode): $it" }
                    ?: "Gemini request failed with HTTP $httpCode."
        }
    }

    /** Minimal shape for parsing just the top-level error message. */
    private data class GeminiErrorEnvelope(val error: GeminiErrorBody? = null)
    private data class GeminiErrorBody(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null
    )

    companion object {
        private const val TAG = "GeminiChatRepoImpl"

        private val STARTER_SUGGESTIONS = listOf(
            "Plan a weekend trip to Goa",
            "Write a short poem about the rain",
            "Explain quantum computing simply",
            "Give me a 20-min home workout",
            "Help me debug a null-pointer error",
            "Suggest a dinner recipe under 30 mins",
            "What's trending in tech this week?",
            "Draft a polite resignation letter"
        )
    }
}
