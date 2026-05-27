package com.jahirtrap.cconect.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jahirtrap.cconect.data.ChatClient
import com.jahirtrap.cconect.data.ChatMessage
import com.jahirtrap.cconect.data.Role
import com.jahirtrap.cconect.data.ServerEvent
import com.jahirtrap.cconect.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState { Disconnected, Connecting, Connected }

data class ChatUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val permissionMode: String = "default",
    val sessionId: String? = null,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val client = ChatClient(viewModelScope)

    private val _state = MutableStateFlow(ChatUiState(permissionMode = settings.permissionMode))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var nextId = 0L
    private var currentAssistantId: Long? = null

    init {
        viewModelScope.launch {
            client.events.collect(::onEvent)
        }
    }

    fun connect() {
        if (!settings.isConfigured) return
        _state.update { it.copy(connection = ConnectionState.Connecting, error = null) }
        client.connect(settings.host, settings.port)
    }

    fun sendPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.streaming) return
        addMessage(Role.USER, trimmed)
        currentAssistantId = null
        _state.update { it.copy(streaming = true, error = null) }
        client.sendPrompt(trimmed)
    }

    fun stop() {
        if (_state.value.streaming) client.sendInterrupt()
    }

    fun setPermissionMode(mode: String) {
        settings.permissionMode = mode
        _state.update { it.copy(permissionMode = mode) }
        if (_state.value.connection == ConnectionState.Connected) client.sendSetPermissionMode(mode)
    }

    fun newSession() {
        currentAssistantId = null
        _state.update { it.copy(messages = emptyList(), sessionId = null, streaming = false) }
        client.sendStart(settings.cwd, _state.value.permissionMode, resume = null)
    }

    private fun onEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.Open -> client.sendStart(settings.cwd, _state.value.permissionMode, _state.value.sessionId)
            is ServerEvent.Ready -> _state.update {
                it.copy(connection = ConnectionState.Connected, sessionId = event.sessionId ?: it.sessionId)
            }
            is ServerEvent.AssistantText -> appendAssistant(event.text)
            is ServerEvent.Thinking -> {
                currentAssistantId = null
                addMessage(Role.THINKING, event.text)
            }
            is ServerEvent.ToolUse -> {
                currentAssistantId = null
                addMessage(Role.TOOL, event.input.orEmpty(), toolName = event.name)
            }
            is ServerEvent.ToolResult -> Unit
            is ServerEvent.Result -> _state.update { it.copy(sessionId = event.sessionId ?: it.sessionId) }
            is ServerEvent.Done -> {
                currentAssistantId = null
                _state.update { it.copy(streaming = false) }
            }
            is ServerEvent.Interrupted -> {
                currentAssistantId = null
                _state.update { it.copy(streaming = false) }
            }
            is ServerEvent.Err -> {
                currentAssistantId = null
                _state.update { it.copy(streaming = false) }
                addMessage(Role.ERROR, event.message)
            }
            is ServerEvent.Closed -> _state.update {
                it.copy(connection = ConnectionState.Disconnected, streaming = false, error = event.reason)
            }
        }
    }

    private fun appendAssistant(delta: String) {
        val id = currentAssistantId
        if (id == null) {
            val newId = nextId++
            currentAssistantId = newId
            _state.update { it.copy(messages = it.messages + ChatMessage(newId, Role.ASSISTANT, delta)) }
        } else {
            _state.update {
                it.copy(messages = it.messages.map { m -> if (m.id == id) m.copy(text = m.text + delta) else m })
            }
        }
    }

    private fun addMessage(role: Role, text: String, toolName: String? = null) {
        _state.update { it.copy(messages = it.messages + ChatMessage(nextId++, role, text, toolName)) }
    }

    override fun onCleared() {
        client.close()
    }
}
