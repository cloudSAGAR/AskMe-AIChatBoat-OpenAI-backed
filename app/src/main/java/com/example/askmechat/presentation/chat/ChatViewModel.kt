package com.example.askmechat.presentation.chat

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.askmechat.domain.model.ChatMessage
import com.example.askmechat.domain.model.MapPointGroup
import com.example.askmechat.domain.model.StreamChunk
import com.example.askmechat.domain.usecase.GetStarterSuggestionsUseCase
import com.example.askmechat.domain.usecase.SendMessageUseCase
import com.example.askmechat.presentation.chat.state.ChatScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Drives [ChatScreenState] from use cases.
 *
 * Business rules owned here (as they are lifecycle-aware):
 *   • Cancel any in-flight request when a new one starts.
 *   • Retry the last prompt if the screen resumes and the most-recent AI
 *     answer was a partial response.
 *   • Wake/WiFi locks while streaming so long responses survive a short
 *     backgrounding (no-ops if permission is denied).
 *
 * The ViewModel does NOT reach into Retrofit / the repository directly —
 * it depends only on use cases.
 */
class ChatViewModel(
    application: Application,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getStarterSuggestionsUseCase: GetStarterSuggestionsUseCase
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ChatScreenState())
    val state: StateFlow<ChatScreenState> = _state.asStateFlow()

    private var currentStreamingJob: Job? = null
    private val sessionId = "askmechat-session-${UUID.randomUUID()}"
    private var currentStreamingMessageId: String? = null

    private var streamingWakeLock: PowerManager.WakeLock? = null
    private var streamingWifiLock: WifiManager.WifiLock? = null

    private data class LastRequest(
        val prompt: String,
        val deviceId: String,
        val userCity: String,
        val userId: Int?
    )
    private var lastRequest: LastRequest? = null

    init {
        loadStarterSuggestions()
    }

    // ── Public API ────────────────────────────────────────────────────

    fun sendMessage(
        prompt: String,
        deviceId: String = "askmechat-${UUID.randomUUID()}",
        userCity: String = "",
        userId: Int? = null
    ) {
        if (prompt.isBlank() || _state.value.isSending) return

        currentStreamingJob?.cancel()

        val userMsg = ChatMessage.UserMessage(text = prompt.trim(), sessionId = sessionId)
        val streamingId = UUID.randomUUID().toString()
        currentStreamingMessageId = streamingId
        val placeholder = ChatMessage.AIMessage(
            id = streamingId,
            text = "",
            isStreaming = true,
            sessionId = sessionId
        )

        _state.update { current ->
            current.copy(
                messages = current.messages + userMsg + placeholder,
                mapPoints = null,
                isSending = true,
                isResponseComplete = false,
                errorMessage = null
            )
        }

        lastRequest = LastRequest(prompt, deviceId, userCity, userId)
        acquireStreamingLocks()

        currentStreamingJob = viewModelScope.launch {
            sendMessageUseCase(
                prompt = prompt.trim(),
                sessionId = sessionId,
                deviceId = deviceId,
                userCity = userCity,
                userId = userId
            )
                .catch { e -> markError(e.message ?: "Unknown error occurred") }
                .onCompletion {
                    _state.update { it.copy(isSending = false) }
                    currentStreamingMessageId = null
                    releaseStreamingLocks()
                }
                .collect { chunk -> reduce(chunk) }
        }
    }

    /**
     * Called from Fragment.onResume — if the last AI response was partial
     * (stream aborted while backgrounded), drop it and re-send the prompt.
     */
    fun retryIfNeeded() {
        val last = lastRequest ?: return
        val lastAi = _state.value.messages.lastOrNull { it is ChatMessage.AIMessage }
            as? ChatMessage.AIMessage ?: return
        if (!_state.value.isSending && lastAi.isPartial) {
            Log.w(TAG, "Partial response detected on resume — retrying last request")
            _state.update { current ->
                current.copy(
                    messages = if (current.messages.size >= 2) {
                        current.messages.dropLast(2)
                    } else current.messages.dropLast(1)
                )
            }
            sendMessage(last.prompt, last.deviceId, last.userCity, last.userId)
        }
    }

    /** Called when the user taps on a previous AI bubble that has map data. */
    fun selectMapGroupForMessage(messageId: String) {
        val msg = _state.value.messages
            .firstOrNull { it is ChatMessage.AIMessage && it.id == messageId }
            as? ChatMessage.AIMessage ?: return
        _state.update { it.copy(mapPoints = msg.mapPoints) }
    }

    // ── Reducers ──────────────────────────────────────────────────────

    private fun reduce(chunk: StreamChunk) {
        when (chunk) {
            is StreamChunk.Loading -> Unit // placeholder already in list
            is StreamChunk.Streaming -> patchStreamingMessage(
                chunk.accumulatedText,
                isStreaming = true,
                isPartial = false
            )
            is StreamChunk.Success -> {
                patchStreamingMessage(chunk.fullText, isStreaming = false, isPartial = false)
                _state.update { it.copy(isResponseComplete = true) }
            }
            is StreamChunk.PartialSuccess -> {
                patchStreamingMessage(chunk.partialText, isStreaming = false, isPartial = true)
                _state.update { it.copy(isResponseComplete = true) }
            }
            is StreamChunk.MapData -> {
                val group = MapPointGroup(ownerId = "live", points = chunk.points)
                val id = currentStreamingMessageId
                _state.update { current ->
                    current.copy(
                        messages = if (id != null) {
                            current.messages.map { m ->
                                if (m is ChatMessage.AIMessage && m.id == id) {
                                    m.copy(mapPoints = group)
                                } else m
                            }
                        } else current.messages,
                        mapPoints = group
                    )
                }
            }
            is StreamChunk.Error -> {
                if (!_state.value.isResponseComplete) markError(chunk.message)
                _state.update { it.copy(isResponseComplete = true) }
            }
        }
    }

    private fun patchStreamingMessage(text: String, isStreaming: Boolean, isPartial: Boolean) {
        val id = currentStreamingMessageId ?: return
        _state.update { current ->
            current.copy(
                messages = current.messages.map { m ->
                    if (m is ChatMessage.AIMessage && m.id == id) {
                        m.copy(
                            text = text,
                            isStreaming = isStreaming,
                            isLoading = false,
                            isPartial = isPartial
                        )
                    } else m
                }
            )
        }
    }

    private fun markError(message: String) {
        val id = currentStreamingMessageId ?: return
        _state.update { current ->
            current.copy(
                messages = current.messages.map { m ->
                    if (m is ChatMessage.AIMessage && m.id == id) {
                        m.copy(
                            text = "Sorry, I encountered an error: $message",
                            isStreaming = false,
                            isLoading = false
                        )
                    } else m
                },
                errorMessage = message
            )
        }
    }

    private fun loadStarterSuggestions() {
        viewModelScope.launch {
            val starter = getStarterSuggestionsUseCase()
            _state.update { it.copy(suggestions = starter) }
        }
    }

    // ── Streaming locks ───────────────────────────────────────────────

    private fun acquireStreamingLocks() {
        try {
            val pm = getApplication<Application>()
                .getSystemService(Context.POWER_SERVICE) as PowerManager
            streamingWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AskMeChat:ChatStreaming"
            ).also { it.acquire(3 * 60 * 1000L) }

            @Suppress("DEPRECATION")
            val wm = getApplication<Application>().applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            streamingWifiLock = wm?.createWifiLock(
                WifiManager.WIFI_MODE_FULL,
                "AskMeChat:ChatStreaming"
            )?.also { it.acquire() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire streaming locks: ${e.message}")
        }
    }

    private fun releaseStreamingLocks() {
        try {
            streamingWakeLock?.let { if (it.isHeld) it.release() }
            streamingWakeLock = null
            streamingWifiLock?.let { if (it.isHeld) it.release() }
            streamingWifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release streaming locks: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentStreamingJob?.cancel()
        releaseStreamingLocks()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
