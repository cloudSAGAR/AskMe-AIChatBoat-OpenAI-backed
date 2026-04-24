package com.example.askmechat.data.remote.gemini

/**
 * Configuration for the Gemini chat backend.
 *
 * ⚠  STUBBED — paste your Google AI Studio API key into [API_KEY].
 *
 * How to get one (free, no credit card):
 *   1. Open https://aistudio.google.com/app/apikey in a browser.
 *   2. Sign in with any Google account.
 *   3. Click "Create API key" → "Create API key in new project".
 *   4. Copy the key and paste it below.
 *
 * Currently-supported models on the public v1beta endpoint:
 *   • gemini-2.0-flash       ← recommended, free-tier daily quota
 *   • gemini-flash-latest    ← rolling alias that tracks the newest Flash
 *   • gemini-2.5-flash       ← newer, slightly lower free-tier limit
 *   • gemini-pro-latest      ← higher quality, stricter free-tier
 *
 * NOTE: The old `gemini-1.5-flash` / `gemini-1.5-pro` aliases return 404
 * on v1beta — they've been retired. Stick to the list above.
 *
 * For production, move this key into `local.properties` and expose it via
 * `buildConfigField` — don't commit it.
 */
object GeminiConfig {

    /** Paste your key here. Don't commit with a real key in it. */
    const val API_KEY: String = "AIzaSyAre98mHdWo0NmedP8McJ9wu0MSPDJfloM" // "AIzaSyCaoed3vWPZWVQTy3Rkw7nnQmqTs2C66mY"
    // "YOUR_GEMINI_API_KEY"

    /**
     * Model to use. `gemini-2.0-flash` is fast, capable, and has the most
     * generous current free-tier limits. Change to one of the names listed
     * in the file header if you want more capability or the bleeding edge.
     */
    const val MODEL_NAME: String = "gemini-2.0-flash"

    /**
     * System instruction — the "personality" of the assistant. The model
     * sees this before every turn of the conversation.
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
}
