package com.example.askmechat.data.remote.groq

/**
 * Configuration for the Groq chat backend.
 *
 * ⚠  STUBBED — paste your Groq API key into [API_KEY].
 *
 * How to get one (truly free, no credit card, no billing setup):
 *   1. Open https://console.groq.com/keys in a browser.
 *   2. Sign in (Google / GitHub / email are all fine).
 *   3. Click "Create API Key" → give it any name → copy the key.
 *   4. Paste it below.
 *
 * Why Groq:
 *   • Free tier is actually free — no `limit: 0` surprises like Gemini.
 *   • Extremely fast inference (LPU hardware).
 *   • Streaming chat via the OpenAI-compatible Chat Completions API.
 *
 * Free-tier rate limits (as of 2026):
 *   • llama-3.3-70b-versatile  → 30 req/min, 6k tokens/min
 *   • llama-3.1-8b-instant     → 30 req/min, 14k tokens/min
 *   • gemma2-9b-it             → 30 req/min, 14k tokens/min
 *   • mixtral-8x7b-32768       → 30 req/min, 5k tokens/min
 */
object GroqConfig {

    /** Paste your key here. Don't commit with a real key in it. */
    const val API_KEY: String = "YOUR_GROQ_API_KEY"

    /**
     * Model to use. Llama-3.3-70B is the most capable free model.
     * Swap to `llama-3.1-8b-instant` if you want cheaper/faster responses
     * or `gemma2-9b-it` for Google's open model on Groq hardware.
     */
    const val MODEL_NAME: String = "llama-3.3-70b-versatile"

    /**
     * System instruction — the "personality" of the assistant. Baked into
     * every request as the first message with role="system".
     */
    const val SYSTEM_INSTRUCTION: String =
        "You are AskMe, a friendly, concise AI companion. " +
            "Prefer short, well-structured answers. Use **bold** for key terms, " +
            "numbered or bulleted lists when you have multiple items, " +
            "and avoid unnecessary preamble. If the user asks about a place, " +
            "suggest specific named options when possible."

    /** Sampling temperature — higher = more creative, lower = more focused. */
    const val TEMPERATURE: Float = 0.8f

    /** Maximum tokens to generate per response. */
    const val MAX_OUTPUT_TOKENS: Int = 1024

    /** Full Groq chat completions URL. */
    const val CHAT_COMPLETIONS_URL: String =
        "https://api.groq.com/openai/v1/chat/completions"
}
