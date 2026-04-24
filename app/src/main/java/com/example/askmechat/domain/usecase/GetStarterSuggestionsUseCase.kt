package com.example.askmechat.domain.usecase

import com.example.askmechat.domain.repository.ChatRepository

/**
 * Returns the list of suggestion chips to display above an empty chat.
 * Kept as a use case (not a ViewModel-local hardcode) so the source can be
 * swapped to remote / Room / feature-flag driven data without touching the
 * presentation layer.
 */
class GetStarterSuggestionsUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(): List<String> = repository.getStarterSuggestions()
}
