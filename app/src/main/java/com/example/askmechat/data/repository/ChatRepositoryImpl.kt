package com.example.askmechat.data.repository

import android.util.Log
import com.example.askmechat.data.remote.api.ChatApiService
import com.example.askmechat.data.remote.api.ChatEndpoints
import com.example.askmechat.data.remote.dto.ChatRequestDto
import com.example.askmechat.data.remote.dto.ChatStreamResponseDto
import com.example.askmechat.data.remote.dto.VisualizationResponseDto
import com.example.askmechat.data.remote.mapper.toDomain
import com.example.askmechat.domain.model.StreamChunk
import com.example.askmechat.domain.repository.ChatRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Concrete [ChatRepository] backed by a streaming Retrofit endpoint.
 *
 * Responsibilities kept here:
 *   • Build the wire-format request.
 *   • Read line-delimited JSON from the server as it arrives.
 *   • Parse "begin" / "item" / "end" events, accumulating text and
 *     detecting visualization blocks.
 *   • Translate everything into [StreamChunk]s so the domain contract is
 *     honoured and the presentation layer never sees DTOs.
 */
class ChatRepositoryImpl(
    private val api: ChatApiService,
    private val gson: Gson
) : ChatRepository {

    override fun sendPrompt(
        prompt: String,
        sessionId: String,
        deviceId: String,
        userCity: String,
        userId: Int?
    ): Flow<StreamChunk> = flow {
        try {
            val request = ChatRequestDto(
                sessionId = sessionId,
                deviceId = deviceId,
                chatInput = prompt,
                userCity = userCity,
                userId = userId
            )

            Log.w(TAG, "POST ${ChatEndpoints.CHAT_BASE_URL}${ChatEndpoints.CHAT_PATH}")

            emit(StreamChunk.Loading)

            val responseBody = api.sendMessageStreaming(
                url = ChatEndpoints.CHAT_PATH,
                request = request
            )

            responseBody.use { body ->
                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                reader.use {
                    val accumulatedText = StringBuilder()
                    val currentBlock = StringBuilder()
                    var textBlockFinalized = false
                    var hadBlankAfterContent = false
                    var line: String?

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        try {
                            line = reader.readLine()
                        } catch (ioe: IOException) {
                            Log.w(TAG, "Stream read aborted: ${ioe.message}")
                            break
                        }
                        if (line == null) break

                        line?.let { jsonLine ->
                            if (jsonLine.isBlank()) return@let

                            try {
                                val chunk = gson.fromJson(jsonLine, ChatStreamResponseDto::class.java)
                                when (chunk.type) {
                                    "begin" -> {
                                        currentBlock.clear()
                                    }
                                    "item" -> {
                                        val content = chunk.content
                                        if (!content.isNullOrEmpty()) {
                                            currentBlock.append(content)
                                            if (!textBlockFinalized) {
                                                if (hadBlankAfterContent &&
                                                    accumulatedText.isNotEmpty() &&
                                                    !accumulatedText.last().isWhitespace()
                                                ) {
                                                    accumulatedText.append(" ")
                                                }
                                                hadBlankAfterContent = false
                                                accumulatedText.append(content)
                                                emit(StreamChunk.Streaming(accumulatedText.toString()))
                                            }
                                        } else if (!textBlockFinalized && accumulatedText.isNotEmpty()) {
                                            hadBlankAfterContent = true
                                        }
                                    }
                                    "end" -> {
                                        val blockStr = currentBlock.toString().trim()

                                        if (blockStr.contains("visualizationType")) {
                                            try {
                                                val jsonContent = extractJsonFromMarkdown(blockStr)
                                                val viz = gson.fromJson(
                                                    jsonContent,
                                                    VisualizationResponseDto::class.java
                                                )
                                                val points = viz.toDomain()
                                                if (points.isNotEmpty()) {
                                                    emit(StreamChunk.MapData(points))
                                                }
                                            } catch (parseEx: Exception) {
                                                Log.e(TAG, "viz parse failed: ${parseEx.message}")
                                            }
                                        } else if (!textBlockFinalized && accumulatedText.isNotEmpty()) {
                                            textBlockFinalized = true
                                            emit(StreamChunk.Success(accumulatedText.toString()))
                                        }

                                        currentBlock.clear()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "JSON parse error: ${e.message}")
                                if (!textBlockFinalized) {
                                    accumulatedText.append(jsonLine)
                                    emit(StreamChunk.Streaming(accumulatedText.toString()))
                                }
                            }
                        }
                    }

                    if (!textBlockFinalized) {
                        if (accumulatedText.isNotEmpty()) {
                            emit(StreamChunk.PartialSuccess(accumulatedText.toString()))
                        } else {
                            emit(StreamChunk.Error("No content received from server"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming error: ${e.message}")
            emit(StreamChunk.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getStarterSuggestions(): List<String> = STARTER_SUGGESTIONS

    /**
     * Extracts JSON content from markdown code blocks.
     * Handles both ```json...``` and plain ```...``` blocks.
     */
    private fun extractJsonFromMarkdown(text: String): String {
        val codeBlockPattern = Regex("""```(?:json)?\s*(.*?)\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(text)
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            val jsonPattern = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
            jsonPattern.find(text)?.value?.trim() ?: text
        }
    }

    companion object {
        private const val TAG = "ChatRepositoryImpl"

        /**
         * Hand-picked starter prompts covering a spread of the things a
         * general-purpose AI assistant does well. Designed so at least one
         * resonates with any first-time user.
         */
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
