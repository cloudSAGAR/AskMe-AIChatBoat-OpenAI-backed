package com.example.askmechat.data.repository

import android.util.Log
import com.example.askmechat.data.remote.groq.GroqConfig
import com.example.askmechat.data.remote.groq.GroqMessageDto
import com.example.askmechat.data.remote.groq.GroqRequestDto
import com.example.askmechat.data.remote.groq.GroqStreamResponseDto
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
 * [ChatRepository] backed by Groq's OpenAI-compatible Chat Completions API.
 *
 * Why Groq here alongside Gemini:
 *   • Free tier is actually free — no `limit: 0` per-project surprises.
 *   • No credit card, no billing setup, works globally.
 *   • Llama 3.3 70B on LPU hardware is extremely fast at streaming.
 *   • Same OkHttp + SSE pattern as our other repos.
 */
class GroqChatRepositoryImpl(
    private val apiKey: String = GroqConfig.API_KEY,
    private val modelName: String = GroqConfig.MODEL_NAME
) : ChatRepository {

    /** Conversation history per sessionId, OpenAI-style messages list. */
    private val sessions = mutableMapOf<String, MutableList<GroqMessageDto>>()

    private val okHttp get() = RetrofitProvider.okHttpClient
    private val gson get() = RetrofitProvider.gson

    override fun sendPrompt(
        prompt: String,
        sessionId: String,
        deviceId: String,
        userCity: String,
        userId: Int?
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank() || apiKey == "YOUR_GROQ_API_KEY") {
            emit(
                StreamChunk.Error(
                    "Missing Groq API key. Open GroqConfig.kt and paste your " +
                        "key from https://console.groq.com/keys."
                )
            )
            return@flow
        }

        emit(StreamChunk.Loading)

        val history = sessions.getOrPut(sessionId) {
            // Seed with the system prompt on first use of this session.
            mutableListOf(
                GroqMessageDto(role = "system", content = GroqConfig.SYSTEM_INSTRUCTION)
            )
        }

        val enrichedPrompt = buildPromptWithContext(prompt, userCity)
        history.add(GroqMessageDto(role = "user", content = enrichedPrompt))

        val requestDto = GroqRequestDto(
            model = modelName,
            messages = history.toList(),
            stream = true,
            temperature = GroqConfig.TEMPERATURE,
            maxTokens = GroqConfig.MAX_OUTPUT_TOKENS
        )
        val requestJson = gson.toJson(requestDto)
        logLongLines("REQUEST BODY", requestJson)

        val body = requestJson
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(GroqConfig.CHAT_COMPLETIONS_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val accumulated = StringBuilder()
        var textBlockFinalized = false

        try {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    Log.e(TAG, "<-- HTTP ${response.code} ${response.message}")
                    logLongLines("RESPONSE BODY (error)", bodyString)
                    emit(StreamChunk.Error(friendlyErrorFor(response.code, bodyString)))
                    history.removeAt(history.lastIndex)
                    return@use
                }
                Log.d(TAG, "<-- HTTP ${response.code} (streaming)")

                val responseBody = response.body ?: run {
                    emit(StreamChunk.Error("Empty response body from Groq"))
                    history.removeAt(history.lastIndex)
                    return@use
                }

                BufferedReader(InputStreamReader(responseBody.byteStream())).use { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val line: String? = try {
                            reader.readLine()
                        } catch (ioe: IOException) {
                            Log.w(TAG, "Groq stream read aborted: ${ioe.message}")
                            null
                        } ?: break

                        if (line.isNullOrBlank()) continue

                        val jsonLine = when {
                            line.startsWith("data: ") -> line.removePrefix("data: ")
                            line.startsWith("data:") -> line.removePrefix("data:")
                            else -> continue
                        }.trim()
                        if (jsonLine.isEmpty()) continue
                        if (jsonLine == "[DONE]") break

                        try {
                            val chunk = gson.fromJson(jsonLine, GroqStreamResponseDto::class.java)

                            chunk.error?.let { err ->
                                emit(StreamChunk.Error("Groq: ${err.message ?: "unknown error"}"))
                                return@use
                            }

                            val textChunk = chunk.choices
                                ?.firstOrNull()
                                ?.delta
                                ?.content
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
                        history.add(
                            GroqMessageDto(role = "assistant", content = accumulated.toString())
                        )
                    } else {
                        emit(StreamChunk.Error("Groq returned an empty response"))
                        history.removeAt(history.lastIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq stream failed", e)
            if (accumulated.isNotEmpty() && !textBlockFinalized) {
                emit(StreamChunk.PartialSuccess(accumulated.toString()))
                history.add(
                    GroqMessageDto(role = "assistant", content = accumulated.toString())
                )
            } else {
                emit(StreamChunk.Error(e.message ?: "Groq request failed"))
                if (history.isNotEmpty() && history.last().role == "user") {
                    history.removeAt(history.lastIndex)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getStarterSuggestions(): List<String> = STARTER_SUGGESTIONS

    private fun buildPromptWithContext(prompt: String, userCity: String): String {
        return if (userCity.isNotBlank()) "[User is in $userCity]\n$prompt" else prompt
    }

    private fun friendlyErrorFor(httpCode: Int, bodyString: String): String {
        val apiMessage: String? = runCatching {
            val env = gson.fromJson(bodyString, GroqStreamResponseDto::class.java)
            env?.error?.message
        }.getOrNull()

        return when (httpCode) {
            401 -> "Groq rejected the API key (401). Double-check GroqConfig.API_KEY " +
                "against https://console.groq.com/keys."
            429 -> "Rate-limited by Groq. ${apiMessage ?: "Try again in a few seconds."}"
            404 -> "Model \"$modelName\" not found on Groq. Try llama-3.3-70b-versatile, " +
                "llama-3.1-8b-instant or gemma2-9b-it."
            in 500..599 -> "Groq had a server error ($httpCode) — try again shortly."
            else -> apiMessage?.let { "Groq error ($httpCode): $it" }
                ?: "Groq request failed with HTTP $httpCode."
        }
    }

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

    companion object {
        private const val TAG = "GroqChatRepoImpl"

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
