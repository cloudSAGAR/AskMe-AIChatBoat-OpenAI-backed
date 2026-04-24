package com.example.askmechat.domain.repository

import com.example.askmechat.domain.model.StreamChunk
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level contract for the chat backend. The implementation lives in
 * the data layer ([com.example.askmechat.data.repository.ChatRepositoryImpl]),
 * which is free to swap HTTP clients, add caching or add offline-first
 * behaviour without any ripple into the domain or presentation layers.
 */
interface ChatRepository {

    /**
     * Send a prompt and stream the response back as a series of chunks.
     *
     * The returned flow emits in the order defined by [StreamChunk] and
     * terminates naturally when the response is complete.
     *
     * @param prompt The user's raw message.
     * @param sessionId Conversation id — preserved across turns so the
     *                  backend can thread context.
     * @param deviceId Stable-per-install identifier.
     * @param userCity Optional geographic context.
     * @param userId Optional authenticated user id.
     */
    fun sendPrompt(
        prompt: String,
        sessionId: String,
        deviceId: String,
        userCity: String = "",
        userId: Int? = null
    ): Flow<StreamChunk>

    /** Starter suggestion chips shown when the conversation is empty. */
    suspend fun getStarterSuggestions(): List<String>
}
