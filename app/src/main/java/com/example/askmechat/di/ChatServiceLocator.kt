package com.example.askmechat.di

import com.example.askmechat.data.remote.network.RetrofitProvider
import com.example.askmechat.data.repository.ChatRepositoryImpl
import com.example.askmechat.data.repository.GeminiChatRepositoryImpl
import com.example.askmechat.data.repository.GroqChatRepositoryImpl
import com.example.askmechat.domain.repository.ChatRepository
import com.example.askmechat.domain.usecase.GetStarterSuggestionsUseCase
import com.example.askmechat.domain.usecase.SendMessageUseCase

/**
 * Lightweight service locator — the single source of truth for wiring the
 * chat feature together. Kept manual to avoid a DI framework for this demo.
 *
 * ⇅  Switch providers by changing [activeProvider] below. Nothing else
 *    needs to change — the domain layer sees the same `ChatRepository`
 *    contract regardless of which impl is selected.
 */
object ChatServiceLocator {

    /**
     * Which AI backend the app should use.
     *
     *  • [Provider.GROQ]         — Free Llama / Gemma / Mixtral via Groq.
     *                              No credit card required. Paste your key
     *                              into GroqConfig.kt.  ← RECOMMENDED
     *  • [Provider.GEMINI]       — Google's Gemini API. Free tier is
     *                              region-dependent; many accounts need
     *                              billing setup to unlock real quotas.
     *                              Paste your key into GeminiConfig.kt.
     *  • [Provider.CUSTOM_REST]  — Your own streaming REST endpoint as
     *                              configured in ChatEndpoints.kt.
     */
    enum class Provider { GROQ, GEMINI, CUSTOM_REST }

    /**
     * Default to Groq — it's the fastest path to a working app because
     * it's free, doesn't require billing, and works globally.
     */
    var activeProvider: Provider = Provider.GROQ

    val chatRepository: ChatRepository by lazy {
        when (activeProvider) {
            Provider.GROQ -> GroqChatRepositoryImpl()
            Provider.GEMINI -> GeminiChatRepositoryImpl()
            Provider.CUSTOM_REST -> ChatRepositoryImpl(
                api = RetrofitProvider.chatApi,
                gson = RetrofitProvider.gson
            )
        }
    }

    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(chatRepository)
    }

    val getStarterSuggestionsUseCase: GetStarterSuggestionsUseCase by lazy {
        GetStarterSuggestionsUseCase(chatRepository)
    }
}
