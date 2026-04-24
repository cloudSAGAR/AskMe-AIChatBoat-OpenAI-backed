package com.example.askmechat.data.remote.api

/**
 * Central place for the demo app's chat endpoint configuration.
 *
 * ⚠  STUBBED — replace these URLs with your own backend when it's ready.
 *
 * Contract expected:
 *   • POST {CHAT_BASE_URL}{CHAT_PATH} — returns a line-delimited JSON
 *     stream whose chunks match [com.example.askmechat.data.remote.dto.ChatStreamResponseDto].
 *   • GET  {SUGGESTIONS_URL} — returns a JSON list of suggestion items
 *     (optional; repository falls back to a static list if unused).
 */
object ChatEndpoints {

    /** Base URL for your streaming chat backend. Must end with `/`. */
    const val CHAT_BASE_URL: String = "https://example.com/"

    /** Relative path portion of the chat endpoint (no leading slash). */
    const val CHAT_PATH: String =  "webhook/your-chat-id/chat"

    /** Full URL to the optional suggestions endpoint. */
    const val SUGGESTIONS_URL: String = "https://example.com/webhook/your-suggestions-id"

    /** Toggle streaming on/off. Set to false if your backend returns one-shot JSON. */
    const val ENABLE_STREAMING: Boolean = true
}
