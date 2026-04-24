package com.example.askmechat.presentation.chat.adapter

import androidx.recyclerview.widget.DiffUtil
import com.example.askmechat.domain.model.ChatMessage

class ChatDiffUtil : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = when {
        oldItem is ChatMessage.UserMessage && newItem is ChatMessage.UserMessage ->
            oldItem.text == newItem.text && oldItem.timestamp == newItem.timestamp
        oldItem is ChatMessage.AIMessage && newItem is ChatMessage.AIMessage ->
            oldItem.text == newItem.text &&
                oldItem.timestamp == newItem.timestamp &&
                oldItem.isLoading == newItem.isLoading &&
                oldItem.isStreaming == newItem.isStreaming &&
                oldItem.mapPoints == newItem.mapPoints
        else -> false
    }

    override fun getChangePayload(oldItem: ChatMessage, newItem: ChatMessage): Any? {
        if (oldItem is ChatMessage.AIMessage && newItem is ChatMessage.AIMessage &&
            oldItem.id == newItem.id
        ) {
            return PAYLOAD_STREAMING
        }
        return null
    }

    companion object {
        const val PAYLOAD_STREAMING = "streaming_text"
    }
}
