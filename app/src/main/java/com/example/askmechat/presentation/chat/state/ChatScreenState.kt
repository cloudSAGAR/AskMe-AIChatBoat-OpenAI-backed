package com.example.askmechat.presentation.chat.state

import com.example.askmechat.domain.model.ChatMessage
import com.example.askmechat.domain.model.MapPointGroup

/**
 * Aggregate, UI-facing state for the chat screen.
 *
 * Preferred shape: one `StateFlow<ChatScreenState>` from the ViewModel so
 * the Fragment has a single, atomic snapshot to render per frame. This
 * avoids the "multi-flow-tearing" problem where messages, isSending and
 * mapPoints update on different frames.
 */
data class ChatScreenState(
    val messages: List<ChatMessage> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val mapPoints: MapPointGroup? = null,
    val isSending: Boolean = false,
    val isResponseComplete: Boolean = false,
    val errorMessage: String? = null
) {
    val hasMessages: Boolean get() = messages.isNotEmpty()
}
