package com.example.askmechat.presentation.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.askmechat.di.ChatServiceLocator

/**
 * Supplies [ChatViewModel] with its use-case dependencies.
 *
 * The Fragment calls `viewModels { ChatViewModelFactory(application) }` and
 * this factory pulls everything it needs from [ChatServiceLocator]. Swapping
 * to Hilt later means deleting this file and annotating the ViewModel —
 * nothing else changes.
 */
class ChatViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                application = application,
                sendMessageUseCase = ChatServiceLocator.sendMessageUseCase,
                getStarterSuggestionsUseCase = ChatServiceLocator.getStarterSuggestionsUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
