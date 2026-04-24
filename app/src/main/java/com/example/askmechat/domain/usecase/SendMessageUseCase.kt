package com.example.askmechat.domain.usecase

import com.example.askmechat.domain.model.StreamChunk
import com.example.askmechat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams the AI response for a user prompt.
 *
 * This use case is intentionally thin — business rules around threading,
 * retry, cancellation and local echo live in the ViewModel because they
 * are UI-lifecycle concerns, not domain rules. If/when those rules need
 * to be reused across screens they can graduate into this layer.
 */
class SendMessageUseCase(
    private val repository: ChatRepository
) {
    operator fun invoke(
        prompt: String,
        sessionId: String,
        deviceId: String,
        userCity: String = "",
        userId: Int? = null
    ): Flow<StreamChunk> = repository.sendPrompt(
        prompt = prompt,
        sessionId = sessionId,
        deviceId = deviceId,
        userCity = userCity,
        userId = userId
    )
}
