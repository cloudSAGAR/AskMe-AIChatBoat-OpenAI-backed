package com.example.askmechat.domain.model

import java.util.UUID

/**
 * Core domain entity representing a single chat message.
 *
 * Domain-pure: no Android dependencies, no serialization framework bindings.
 * DTOs and UI wrappers live in their own layers.
 */
sealed class ChatMessage {
    abstract val id: String
    abstract val timestamp: Long

    data class UserMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val sessionId: String = ""
    ) : ChatMessage()

    data class AIMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val isLoading: Boolean = false,
        val isStreaming: Boolean = false,
        val isPartial: Boolean = false,
        val sessionId: String = "",
        val mapPoints: MapPointGroup? = null
    ) : ChatMessage()
}
