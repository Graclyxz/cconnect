package com.jahirtrap.cconnect.chat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.ChatMessage
import com.jahirtrap.cconnect.data.ConnectionProfile
import com.jahirtrap.cconnect.data.ProjectInfo
import com.jahirtrap.cconnect.data.Role
import com.jahirtrap.cconnect.data.ServerEvent
import com.jahirtrap.cconnect.data.SessionInfo
import com.jahirtrap.cconnect.data.SessionMessage
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.data.TodoItem
import com.jahirtrap.cconnect.data.remote.CapabilitiesApi
import com.jahirtrap.cconnect.data.remote.ChatSocket
import com.jahirtrap.cconnect.data.remote.SessionsApi
import com.jahirtrap.cconnect.service.ConnectionService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Job
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
    val permissionMode: String = "bypassPermissions",
    val model: String = "opus[1m]",
    val effort: String = "xhigh",
    val streamTokens: Boolean = true,
    val capabilities: Capabilities = Capabilities(),
    val sessionId: String? = null,
    val sessionColor: String? = null,
    val todos: List<TodoItem> = emptyList(),
    val error: String? = null,
    val historyProjects: List<ProjectInfo> = emptyList(),
    val historySessions: List<SessionInfo> = emptyList(),
    val historyProjectKey: String? = null,
    val historyLoading: Boolean = false,
    val connections: List<ConnectionProfile> = emptyList(),
    val activeConnectionId: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext: Context = app
    private val settings = Settings(app)
    private val client = ChatSocket(viewModelScope)

    private val _state = MutableStateFlow(
        ChatUiState(
            permissionMode = settings.permissionMode,
            model = settings.model,
            effort = settings.effort,
            streamTokens = settings.streaming,
            connections = settings.connections,
            activeConnectionId = settings.activeConnection?.id,
        )
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var nextId = 0L
    private var currentAssistantId: Long? = null
    private var currentThinkingId: Long? = null
    private var historyJob: Job? = null
    private var historyLoaded = false

    // Reconnect-and-resume when the app returns to the foreground after a drop.
    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (_state.value.connection == ConnectionState.Disconnected) connect()
        }
    }

    init {
        applyDefaultDirectory()
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        viewModelScope.launch {
            client.events.collect(::onEvent)
        }
    }

    private fun applyDefaultDirectory() {
        settings.cwd = settings.activeConnection?.directory.orEmpty()
    }

    fun connect() {
        if (!settings.isConfigured) return
        if (_state.value.connection != ConnectionState.Disconnected) return
        _state.update { it.copy(connection = ConnectionState.Connecting, error = null) }
        viewModelScope.launch {
            CapabilitiesApi.capabilities()?.let { caps ->
                _state.update { it.copy(capabilities = caps) }
            }
            client.connect()
        }
    }

    private fun startSession(resume: String?) {
        client.sendStart(settings.cwd, _state.value.permissionMode, resume, settings.model, settings.effort, settings.streaming)
    }

    fun sendPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.streaming) return
        addMessage(Role.USER, trimmed)
        currentAssistantId = null
        currentThinkingId = null
        _state.update { it.copy(streaming = true, error = null, todos = emptyList()) }
        ConnectionService.start(appContext)
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

    fun setModel(model: String) {
        settings.model = model
        _state.update { it.copy(model = model) }
    }

    fun setEffort(effort: String) {
        settings.effort = effort
        _state.update { it.copy(effort = effort) }
    }

    fun setStreaming(enabled: Boolean) {
        settings.streaming = enabled
        _state.update { it.copy(streamTokens = enabled) }
    }

    fun newSession() {
        currentAssistantId = null
        currentThinkingId = null
        applyDefaultDirectory()
        _state.update { it.copy(messages = emptyList(), sessionId = null, sessionColor = null, todos = emptyList(), streaming = false) }
        startSession(resume = null)
    }

    // Pull the latest connection profiles from settings (they may have been edited in
    // the settings screen while this ViewModel stayed alive).
    fun refreshConnections() {
        _state.update { it.copy(connections = settings.connections, activeConnectionId = settings.activeConnection?.id) }
    }

    // Switch the active backend (device) and reconnect from scratch — the new host has
    // its own sessions/projects, so clear the chat and history.
    fun selectConnection(id: String) {
        if (id == settings.activeConnection?.id) return
        settings.activeConnectionId = id
        applyDefaultDirectory()
        historyLoaded = false
        currentAssistantId = null
        currentThinkingId = null
        client.close()
        _state.update {
            it.copy(
                activeConnectionId = id,
                connection = ConnectionState.Disconnected,
                messages = emptyList(),
                sessionId = null,
                sessionColor = null,
                todos = emptyList(),
                streaming = false,
                historyProjects = emptyList(),
                historySessions = emptyList(),
                historyProjectKey = null,
            )
        }
        connect()
    }

    fun ensureHistoryLoaded() {
        if (!historyLoaded) loadHistory()
    }

    fun loadHistory() {
        historyJob?.cancel()
        historyLoaded = true
        val key = _state.value.historyProjectKey
        historyJob = viewModelScope.launch {
            _state.update { it.copy(historyLoading = true) }
            val projects = SessionsApi.projects()
            val sessions = SessionsApi.sessions(key)
            _state.update { it.copy(historyProjects = projects, historySessions = sessions, historyLoading = false) }
        }
    }

    fun selectHistoryProject(projectKey: String?) {
        _state.update { it.copy(historyProjectKey = projectKey, historySessions = emptyList()) }
        if (projectKey != null) {
            _state.value.historyProjects.firstOrNull { it.projectKey == projectKey }?.path?.let { settings.cwd = it }
        }
        loadHistory()
    }

    fun openSession(session: SessionInfo) {
        val projectKey = session.projectKey ?: return
        viewModelScope.launch {
            val loaded = SessionsApi.sessionMessages(session.sessionId, projectKey)
                .filter { it.text.isNotBlank() }
                .mapIndexed { index, m -> ChatMessage(index.toLong(), m.toRole(), m.text) }
            nextId = loaded.size.toLong()
            currentAssistantId = null
            currentThinkingId = null
            session.path?.let { settings.cwd = it }
            _state.update { it.copy(messages = loaded, sessionId = session.sessionId, sessionColor = session.color, todos = emptyList(), streaming = false) }
            startSession(resume = session.sessionId)
        }
    }

    fun deleteSession(session: SessionInfo) {
        val projectKey = session.projectKey ?: return
        viewModelScope.launch {
            if (SessionsApi.deleteSession(session.sessionId, projectKey)) {
                _state.update {
                    it.copy(historySessions = it.historySessions.filterNot { s -> s.sessionId == session.sessionId })
                }
            }
        }
    }

    fun renameSession(session: SessionInfo, title: String) {
        val projectKey = session.projectKey ?: return
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            if (SessionsApi.renameSession(session.sessionId, projectKey, clean)) {
                updateHistoryTitle(session.sessionId, clean)
            }
        }
    }

    fun autoRenameSession(session: SessionInfo) {
        val projectKey = session.projectKey ?: return
        viewModelScope.launch {
            SessionsApi.autoRenameSession(session.sessionId, projectKey)?.let { title ->
                updateHistoryTitle(session.sessionId, title)
            }
        }
    }

    fun setSessionColor(session: SessionInfo, color: String?) {
        val projectKey = session.projectKey ?: return
        viewModelScope.launch {
            if (SessionsApi.setSessionColor(session.sessionId, projectKey, color.orEmpty())) {
                _state.update { s ->
                    s.copy(
                        historySessions = s.historySessions.map { if (it.sessionId == session.sessionId) it.copy(color = color) else it },
                        sessionColor = if (s.sessionId == session.sessionId) color else s.sessionColor,
                    )
                }
            }
        }
    }

    private fun updateHistoryTitle(sessionId: String, title: String) {
        _state.update {
            it.copy(historySessions = it.historySessions.map { s -> if (s.sessionId == sessionId) s.copy(title = title) else s })
        }
    }

    private fun SessionMessage.toRole(): Role = when (role ?: type) {
        "user" -> Role.USER
        "assistant" -> Role.ASSISTANT
        else -> Role.SYSTEM
    }

    private fun onEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.Open -> startSession(_state.value.sessionId)
            is ServerEvent.Ready -> _state.update {
                it.copy(connection = ConnectionState.Connected, sessionId = event.sessionId ?: it.sessionId)
            }
            is ServerEvent.AssistantText -> {
                currentThinkingId = null
                currentAssistantId = append(currentAssistantId, Role.ASSISTANT, event.text)
            }
            is ServerEvent.Thinking -> if (event.text.isNotEmpty()) {
                currentAssistantId = null
                currentThinkingId = append(currentThinkingId, Role.THINKING, event.text)
            }
            is ServerEvent.ToolUse -> {
                currentAssistantId = null
                currentThinkingId = null
                addMessage(Role.TOOL, event.input.orEmpty(), toolName = event.name)
            }
            is ServerEvent.ToolResult -> Unit
            is ServerEvent.Todos -> _state.update { it.copy(todos = event.items) }
            is ServerEvent.Task -> upsertTask(event)
            is ServerEvent.Result -> _state.update { it.copy(sessionId = event.sessionId ?: it.sessionId) }
            is ServerEvent.Done -> resetStreaming()
            is ServerEvent.Interrupted -> resetStreaming()
            is ServerEvent.Err -> {
                resetStreaming()
                addMessage(Role.ERROR, event.message)
            }
            is ServerEvent.Closed -> {
                currentAssistantId = null
                currentThinkingId = null
                ConnectionService.stop(appContext)
                _state.update {
                    it.copy(connection = ConnectionState.Disconnected, streaming = false, error = event.reason)
                }
            }
        }
    }

    private fun upsertTask(event: ServerEvent.Task) {
        if (event.id.isBlank()) return
        _state.update { st ->
            if (event.status == "deleted") {
                return@update st.copy(todos = st.todos.filterNot { it.id == event.id })
            }
            val existing = st.todos.firstOrNull { it.id == event.id }
            val merged = TodoItem(
                id = event.id,
                content = event.content ?: existing?.content ?: "",
                status = event.status ?: existing?.status ?: "pending",
            )
            val todos = if (existing == null) st.todos + merged
            else st.todos.map { if (it.id == event.id) merged else it }
            st.copy(todos = todos)
        }
    }

    private fun resetStreaming() {
        currentAssistantId = null
        currentThinkingId = null
        ConnectionService.stop(appContext)
        _state.update { it.copy(streaming = false) }
    }

    private fun append(currentId: Long?, role: Role, delta: String): Long {
        if (currentId == null) {
            val newId = nextId++
            _state.update { it.copy(messages = it.messages + ChatMessage(newId, role, delta)) }
            return newId
        }
        _state.update {
            it.copy(messages = it.messages.map { m -> if (m.id == currentId) m.copy(text = m.text + delta) else m })
        }
        return currentId
    }

    private fun addMessage(role: Role, text: String, toolName: String? = null) {
        _state.update { it.copy(messages = it.messages + ChatMessage(nextId++, role, text, toolName)) }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        ConnectionService.stop(appContext)
        client.close()
    }
}
