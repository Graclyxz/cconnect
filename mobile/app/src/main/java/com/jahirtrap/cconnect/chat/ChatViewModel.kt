package com.jahirtrap.cconnect.chat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.ChatMessage
import com.jahirtrap.cconnect.data.ConnectionProfile
import com.jahirtrap.cconnect.data.CommandOption
import com.jahirtrap.cconnect.data.CompactData
import com.jahirtrap.cconnect.data.DiffLine
import com.jahirtrap.cconnect.data.InteractionData
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
import com.jahirtrap.cconnect.data.remote.SettingsApi
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

private const val MESSAGE_TAIL_CAP = 500
private const val MESSAGE_INITIAL_CAP = 100

data class SideChatState(
    val boundSessionId: String? = null,    // the main conversation this side chat belongs to
    val sideSessionId: String? = null,     // SDK session of the side conversation, resumed for memory
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
)

data class ChatUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val permissionMode: String = "bypassPermissions",
    val model: String = "opus[1m]",
    val effort: String = "xhigh",
    val streamTokens: Boolean = true,
    val capabilities: Capabilities = Capabilities(),
    val capabilitiesReady: Boolean = false,
    val sessionId: String? = null,
    val activeProjectKey: String? = null,
    val sessionColor: String? = null,
    val todos: List<TodoItem> = emptyList(),
    val error: String? = null,
    val historyProjects: List<ProjectInfo> = emptyList(),
    val historySessions: List<SessionInfo> = emptyList(),
    val historyProjectKey: String? = null,
    val historyLoading: Boolean = false,
    val connections: List<ConnectionProfile> = emptyList(),
    val activeConnectionId: String? = null,
    val oldestLoadedIndex: Int? = null,
    val transcriptLoading: Boolean = false,
    val transcriptExhausted: Boolean = false,
    val followBottom: Boolean = true,
    val compacting: Boolean = false,
    val sideChat: SideChatState? = null,             // persisted side conversation (kept while the session lives)
    val sideChatOpen: Boolean = false,               // whether the side panel is currently shown
    val showWorking: String = "label",               // quick-chat working indicator visibility (label/off)
    val pendingToolIds: Set<String> = emptySet(),    // tools still running (tool_use seen, no result yet)
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext: Context = app
    private val settings = Settings(app)
    private val client = ChatSocket(viewModelScope)

    // Generation settings (model/effort/permission/streaming) are backend-owned
    private val _state = MutableStateFlow(
        Capabilities().defaults.let { d ->
            ChatUiState(
                permissionMode = d.permissionMode,
                model = d.model,
                effort = d.effort,
                streamTokens = true,
                connections = settings.connections,
                activeConnectionId = settings.activeConnection?.id,
            )
        }
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var nextId = 0L
    private var currentAssistantId: Long? = null
    private var currentThinkingId: Long? = null
    private var currentSideAssistantId: Long? = null
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
            client.events.collect { (side, event) -> if (side) onSideEvent(event) else onEvent(event) }
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
            CapabilitiesApi.capabilities()?.let { caps -> _state.update { it.copy(capabilities = caps) } }
            SettingsApi.get()?.let { s ->
                _state.update {
                    it.copy(model = s.model, effort = s.effort, permissionMode = s.permissionMode, streamTokens = s.streaming, showWorking = s.showWorking)
                }
            }
            _state.update { it.copy(capabilitiesReady = true) }
            client.connect()
        }
    }

    private fun startSession(resume: String?) {
        val s = _state.value
        client.sendStart(settings.cwd, s.permissionMode, resume, s.model, s.effort, s.streamTokens)
    }

    fun sendPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.streaming) return
        // /compact shows a progress block.
        val compacting = trimmed == "/compact" || trimmed.startsWith("/compact ")
        if (!compacting) addMessage(Role.USER, trimmed)
        currentAssistantId = null
        currentThinkingId = null
        _state.update {
            val todos = if (it.todos.all { t -> t.status == "completed" }) emptyList() else it.todos
            resetToInitialWindow(it).copy(streaming = true, compacting = compacting, error = null, todos = todos)
        }
        ConnectionService.start(appContext)
        client.sendPrompt(trimmed)
    }

    fun stop() {
        if (_state.value.streaming) client.sendInterrupt()
    }

    fun stopSide() {
        if (_state.value.boundSide()?.streaming == true) client.sendInterrupt("side")
    }

    fun submit(text: String) {
        val cmd = _state.value.capabilities.commands.firstOrNull { "/${it.name}" == text.trim() }
        if (cmd != null) runCommand(cmd) else sendPrompt(text)
    }

    fun runCommand(cmd: CommandOption) {
        when {
            cmd.kind == "usage" -> {
                addMessage(Role.USER, "/${cmd.name}")
                client.sendUsage()
            }
            cmd.kind == "client" && cmd.name == "clear" -> clearConversation()
            else -> sendPrompt("/${cmd.name}")
        }
    }

    private fun ChatUiState.boundSide(): SideChatState? = sideChat?.takeIf { it.boundSessionId == sessionId }

    private fun SideChatState?.promote(sid: String?): SideChatState? =
        if (this != null && boundSessionId == null && sid != null) copy(boundSessionId = sid) else this

    fun openSideChat() {
        _state.update { it.copy(sideChat = it.boundSide() ?: SideChatState(boundSessionId = it.sessionId), sideChatOpen = true) }
    }

    fun closeSideChat() {
        _state.update { it.copy(sideChatOpen = false) }
    }

    fun clearSideChat() {
        currentSideAssistantId = null
        _state.update { it.copy(sideChat = SideChatState(boundSessionId = it.sessionId), sideChatOpen = true) }
    }

    fun sendSideQuestion(text: String) {
        val trimmed = text.trim()
        val sc = _state.value.boundSide() ?: return
        if (trimmed.isEmpty() || sc.streaming) return
        currentSideAssistantId = null
        _state.update {
            val cur = it.boundSide() ?: SideChatState(boundSessionId = it.sessionId)
            it.copy(sideChat = cur.copy(messages = cur.messages + ChatMessage(nextId++, Role.USER, trimmed), streaming = true))
        }
        client.sendAsk(trimmed, sc.sideSessionId)
    }

    fun clearConversation() {
        if (_state.value.streaming) return
        deleteActiveSession()
        newSession()
    }

    private fun deleteActiveSession() {
        val sid = _state.value.sessionId ?: return
        val proj = _state.value.activeProjectKey ?: return
        viewModelScope.launch { SessionsApi.deleteSession(sid, proj) }
        _state.update { it.copy(historySessions = it.historySessions.filterNot { s -> s.sessionId == sid }) }
    }

    fun setPermissionMode(mode: String) {
        _state.update { it.copy(permissionMode = mode) }
        viewModelScope.launch { SettingsApi.update(permissionMode = mode) }
        if (_state.value.connection == ConnectionState.Connected) client.sendSetPermissionMode(mode)
    }

    fun setModel(model: String) {
        _state.update { it.copy(model = model) }
        viewModelScope.launch { SettingsApi.update(model = model) }
    }

    fun setEffort(effort: String) {
        _state.update { it.copy(effort = effort) }
        viewModelScope.launch { SettingsApi.update(effort = effort) }
    }

    fun setStreaming(enabled: Boolean) {
        _state.update { it.copy(streamTokens = enabled) }
        viewModelScope.launch { SettingsApi.update(streaming = enabled) }
    }

    fun newSession() {
        currentAssistantId = null
        currentThinkingId = null
        _state.update {
            it.copy(
                messages = emptyList(),
                sessionId = null,
                sessionColor = null,
                todos = emptyList(),
                streaming = false,
                oldestLoadedIndex = null,
                transcriptLoading = false,
                transcriptExhausted = false,
                pendingToolIds = emptySet(),
            )
        }
        client.resetResume()
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
        currentAssistantId = null
        currentThinkingId = null
        client.close()
        _state.update {
            it.copy(
                activeConnectionId = id,
                connection = ConnectionState.Disconnected,
                capabilitiesReady = false,
                messages = emptyList(),
                sessionId = null,
                activeProjectKey = null,
                sessionColor = null,
                todos = emptyList(),
                streaming = false,
                historyProjects = emptyList(),
                historySessions = emptyList(),
                historyProjectKey = null,
                oldestLoadedIndex = null,
                transcriptLoading = false,
                transcriptExhausted = false,
            )
        }
        connect()
        loadHistory()
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
            val page = SessionsApi.sessionMessages(session.sessionId, projectKey, limit = 100)
            val visible = page.items.filter { it.text.isNotBlank() || it.interaction != null || !it.diffLines.isNullOrEmpty() || it.compact != null || it.labelOnly }
            val loaded = visible.mapIndexed { i, m ->
                ChatMessage(i.toLong(), m.toRole(), m.text, toolName = m.name, path = m.path, interaction = m.interaction, diffLines = m.diffLines, compact = m.compact, sourceIndex = m.index, labelOnly = m.labelOnly, result = m.result)
            }
            nextId = loaded.size.toLong()
            currentAssistantId = null
            currentThinkingId = null
            session.path?.let { settings.cwd = it }
            _state.update {
                it.copy(
                    messages = loaded,
                    sessionId = session.sessionId,
                    activeProjectKey = projectKey,
                    sessionColor = session.color,
                    todos = emptyList(),
                    streaming = false,
                    oldestLoadedIndex = page.startIndex.takeIf { page.items.isNotEmpty() },
                    transcriptLoading = false,
                    transcriptExhausted = !page.hasMore,
                    sideChat = it.sideChat.promote(session.sessionId),
                    pendingToolIds = emptySet(),
                )
            }
            client.resetResume()
            startSession(resume = session.sessionId)
        }
    }

    fun loadMoreHistory() {
        val s = _state.value
        val sid = s.sessionId ?: return
        val before = s.oldestLoadedIndex ?: return
        if (s.transcriptLoading || s.transcriptExhausted) return
        val proj = s.activeProjectKey ?: return
        _state.update { it.copy(transcriptLoading = true) }
        client.sendLoadHistory(sid, proj, beforeIndex = before)
    }

    fun deleteSession(session: SessionInfo) {
        val projectKey = session.projectKey ?: return
        viewModelScope.launch {
            if (SessionsApi.deleteSession(session.sessionId, projectKey)) {
                _state.update {
                    it.copy(historySessions = it.historySessions.filterNot { s -> s.sessionId == session.sessionId })
                }
                if (session.sessionId == _state.value.sessionId) newSession()
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

    private fun SessionMessage.toRole(): Role = when (type) {
        "text" -> if (role == "assistant") Role.ASSISTANT else Role.USER
        "thinking" -> Role.THINKING
        "tool_use" -> Role.TOOL
        "tool_result" -> Role.TOOL_RESULT
        "file_change" -> Role.FILE_CHANGE
        "interaction" -> Role.INTERACTION
        "compact" -> Role.COMPACT
        "summary" -> Role.SUMMARY
        else -> Role.SYSTEM
    }

    private fun onEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.Connecting -> _state.update {
                if (it.connection == ConnectionState.Connected) it
                else it.copy(connection = ConnectionState.Connecting)
            }
            is ServerEvent.Open -> startSession(_state.value.sessionId)
            is ServerEvent.Ready -> {
                if (event.running) ConnectionService.start(appContext) else ConnectionService.stop(appContext)
                historyLoaded = false
                _state.update {
                    val sid = event.sessionId ?: it.sessionId
                    it.copy(
                        connection = ConnectionState.Connected,
                        sessionId = sid,
                        activeProjectKey = event.project ?: it.activeProjectKey,
                        streaming = event.running,
                        sideChat = it.sideChat.promote(sid),
                        messages = it.messages.filterNot { m -> m.ephemeral },
                    )
                }
            }
            is ServerEvent.AssistantText -> {
                currentThinkingId = null
                currentAssistantId = append(currentAssistantId, Role.ASSISTANT, event.text)
            }
            is ServerEvent.Thinking -> {
                if (event.labelOnly) {
                    currentAssistantId = null
                    currentThinkingId = null
                    addMessage(Role.THINKING, "", labelOnly = true)
                } else if (event.text.isNotEmpty()) {
                    currentAssistantId = null
                    currentThinkingId = append(currentThinkingId, Role.THINKING, event.text)
                }
            }
            is ServerEvent.ToolUse -> {
                currentAssistantId = null
                currentThinkingId = null
                addMessage(Role.TOOL, event.input.orEmpty(), toolName = event.name, toolUseId = event.id, result = event.result)
                event.id?.let { id -> _state.update { it.copy(pendingToolIds = it.pendingToolIds + id) } }
            }
            is ServerEvent.ToolResult -> {
                val tid = event.toolUseId
                _state.update { st ->
                    val pending = if (tid != null) st.pendingToolIds - tid else st.pendingToolIds
                    val msgs = if (tid != null && event.content != null)
                        st.messages.map { if (it.toolUseId == tid) it.copy(result = event.content) else it }
                    else st.messages
                    st.copy(pendingToolIds = pending, messages = msgs)
                }
            }
            is ServerEvent.FileChange -> {
                currentAssistantId = null
                currentThinkingId = null
                addMessage(Role.FILE_CHANGE, text = "", toolUseId = event.id, path = event.path, diffLines = event.diffLines, labelOnly = event.labelOnly)
            }
            is ServerEvent.Compact -> {
                currentAssistantId = null
                currentThinkingId = null
                _state.update {
                    it.copy(
                        messages = listOf(ChatMessage(nextId++, Role.COMPACT, compact = CompactData(event.trigger, event.preTokens, event.postTokens, event.summary))),
                        oldestLoadedIndex = null,
                        transcriptExhausted = true,
                    )
                }
            }
            is ServerEvent.CompactSummary -> _state.update { st ->
                st.copy(messages = st.messages.map { m ->
                    if (m.role == Role.COMPACT && m.compact != null) {
                        m.copy(compact = CompactData(event.trigger, event.preTokens, event.postTokens, event.summary))
                    } else m
                })
            }
            is ServerEvent.Command -> {
                currentAssistantId = null
                currentThinkingId = null
                addMessage(Role.ASSISTANT, event.markdown, ephemeral = true)
            }
            is ServerEvent.Todos -> _state.update { it.copy(todos = event.items) }
            is ServerEvent.Task -> upsertTask(event)
            is ServerEvent.Result -> _state.update { st ->
                val sid = event.sessionId ?: st.sessionId
                if (sid != null && st.historySessions.none { it.sessionId == sid }) {
                    val row = SessionInfo(
                        sessionId = sid,
                        projectKey = st.activeProjectKey,
                        path = settings.cwd,
                        lastActive = System.currentTimeMillis() / 1000.0,
                        size = 0L,
                        preview = st.messages.firstOrNull { it.role == Role.USER }?.text?.take(120),
                        title = null,
                        color = st.sessionColor,
                    )
                    st.copy(sessionId = sid, historySessions = listOf(row) + st.historySessions, sideChat = st.sideChat.promote(sid))
                } else {
                    st.copy(sessionId = sid, sideChat = st.sideChat.promote(sid))
                }
            }
            is ServerEvent.Done -> resetStreaming()
            is ServerEvent.Interrupted -> {
                resetStreaming()
                dismissPendingInteractions()
            }
            is ServerEvent.Err -> {
                resetStreaming()
                addMessage(Role.ERROR, event.message)
            }
            is ServerEvent.InteractionRequest -> {
                if (_state.value.messages.none { it.interaction?.requestId == event.requestId }) {
                    currentAssistantId = null
                    currentThinkingId = null
                    val data = InteractionData(
                        requestId = event.requestId,
                        kind = event.kind,
                        options = event.options,
                        freeText = event.freeText,
                        title = event.title,
                    )
                    val tuid = event.toolUseId
                    _state.update { st ->
                        val cleaned = if (tuid != null) st.messages.filterNot { it.role == Role.TOOL && it.toolUseId == tuid } else st.messages
                        st.copy(messages = cleaned + ChatMessage(nextId++, Role.INTERACTION, event.input.orEmpty(), event.toolName, tuid, data))
                    }
                }
            }
            is ServerEvent.Closed -> {
                currentAssistantId = null
                currentThinkingId = null
                currentSideAssistantId = null
                ConnectionService.stop(appContext)
                _state.update {
                    it.copy(
                        connection = ConnectionState.Disconnected,
                        streaming = false,
                        error = event.reason,
                        sideChat = it.sideChat?.copy(streaming = false),
                    )
                }
            }
            is ServerEvent.HistoryChunk -> onHistoryChunk(event)
            else -> {}
        }
    }

    private fun onSideEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.AskWorking -> {
                if (_state.value.showWorking == "label") {
                    currentSideAssistantId = null
                    _state.update { st ->
                        val sc = st.sideChat ?: return@update st
                        st.copy(sideChat = sc.copy(messages = sc.messages + ChatMessage(nextId++, Role.WORKING, "")))
                    }
                }
            }
            is ServerEvent.AskText -> _state.update { st ->
                val sc = st.sideChat ?: return@update st
                val id = currentSideAssistantId
                if (id == null) {
                    val newId = nextId++
                    currentSideAssistantId = newId
                    st.copy(sideChat = sc.copy(messages = sc.messages + ChatMessage(newId, Role.ASSISTANT, event.text)))
                } else {
                    st.copy(sideChat = sc.copy(messages = sc.messages.map { if (it.id == id) it.copy(text = it.text + event.text) else it }))
                }
            }
            is ServerEvent.AskSession -> _state.update { st ->
                st.copy(sideChat = st.sideChat?.copy(sideSessionId = event.sessionId))
            }
            is ServerEvent.InteractionRequest -> {
                currentSideAssistantId = null
                _state.update { st ->
                    val sc = st.sideChat ?: return@update st
                    if (sc.messages.any { it.interaction?.requestId == event.requestId }) return@update st
                    val data = InteractionData(
                        requestId = event.requestId,
                        kind = event.kind,
                        options = event.options,
                        freeText = event.freeText,
                        title = event.title,
                    )
                    val tuid = event.toolUseId
                    val cleaned = if (tuid != null) sc.messages.filterNot { it.role == Role.TOOL && it.toolUseId == tuid } else sc.messages
                    st.copy(sideChat = sc.copy(messages = cleaned + ChatMessage(nextId++, Role.INTERACTION, event.input.orEmpty(), event.toolName, tuid, data)))
                }
            }
            is ServerEvent.Done, is ServerEvent.Interrupted -> {
                currentSideAssistantId = null
                _state.update { it.copy(sideChat = it.sideChat?.copy(streaming = false)) }
                if (event is ServerEvent.Interrupted) dismissSidePendingInteractions()
            }
            is ServerEvent.Err -> {
                currentSideAssistantId = null
                _state.update { st ->
                    val sc = st.sideChat ?: return@update st
                    st.copy(sideChat = sc.copy(streaming = false, messages = sc.messages + ChatMessage(nextId++, Role.ERROR, event.message)))
                }
            }
            else -> {}
        }
    }

    private fun dismissSidePendingInteractions() {
        _state.update { st ->
            val sc = st.sideChat ?: return@update st
            st.copy(sideChat = sc.copy(messages = sc.messages.filterNot { it.role == Role.INTERACTION && it.interaction?.resolved == null }))
        }
    }

    private fun onHistoryChunk(event: ServerEvent.HistoryChunk) {
        if (event.sessionId != _state.value.sessionId) {
            _state.update { it.copy(transcriptLoading = false) }
            return
        }
        val older = event.items
            .filter { it.text.isNotBlank() || it.interaction != null || !it.diffLines.isNullOrEmpty() || it.compact != null || it.labelOnly }
        _state.update { st ->
            val prepended = older.mapIndexed { i, m ->
                ChatMessage(nextId + i, m.toRole(), m.text, toolName = m.name, path = m.path, interaction = m.interaction, diffLines = m.diffLines, compact = m.compact, sourceIndex = m.index, labelOnly = m.labelOnly, result = m.result)
            }
            nextId += prepended.size
            st.copy(
                messages = prepended + st.messages,
                oldestLoadedIndex = event.startIndex,
                transcriptLoading = false,
                transcriptExhausted = !event.hasMore,
            )
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
        _state.update { it.copy(streaming = false, compacting = false, pendingToolIds = emptySet()) }
    }

    private fun dismissPendingInteractions() {
        _state.update { st ->
            if (st.messages.none { it.role == Role.INTERACTION && it.interaction?.resolved == null }) return@update st
            st.copy(messages = st.messages.filterNot { it.role == Role.INTERACTION && it.interaction?.resolved == null })
        }
    }

    private fun append(currentId: Long?, role: Role, delta: String): Long {
        if (currentId == null) {
            val newId = nextId++
            _state.update { applyTailCap(it.copy(messages = it.messages + ChatMessage(newId, role, delta))) }
            return newId
        }
        // .map allocates a fresh ChatMessage per item every chunk; replace just the slot.
        _state.update { st ->
            val idx = st.messages.indexOfLast { it.id == currentId }
            if (idx < 0) return@update st
            val updated = st.messages.toMutableList()
            updated[idx] = updated[idx].copy(text = updated[idx].text + delta)
            st.copy(messages = updated)
        }
        return currentId
    }

    private fun addMessage(
        role: Role,
        text: String,
        toolName: String? = null,
        toolUseId: String? = null,
        interaction: InteractionData? = null,
        path: String? = null,
        diffLines: List<DiffLine>? = null,
        compact: CompactData? = null,
        labelOnly: Boolean = false,
        result: String? = null,
        ephemeral: Boolean = false,
    ) {
        _state.update {
            applyTailCap(it.copy(messages = it.messages + ChatMessage(nextId++, role, text, toolName, toolUseId, interaction, path, diffLines, compact, labelOnly = labelOnly, result = result, ephemeral = ephemeral)))
        }
    }

    private fun applyTailCap(st: ChatUiState): ChatUiState = capFromTail(st, MESSAGE_TAIL_CAP)

    private fun resetToInitialWindow(st: ChatUiState): ChatUiState = capFromTail(st, MESSAGE_INITIAL_CAP)

    private fun capFromTail(st: ChatUiState, cap: Int): ChatUiState {
        if (!st.followBottom || st.messages.size <= cap) return st
        val drop = st.messages.size - cap
        val kept = st.messages.subList(drop, st.messages.size).toList()
        val newCursor = kept.firstOrNull { it.sourceIndex >= 0 }?.sourceIndex ?: st.oldestLoadedIndex
        return st.copy(
            messages = kept,
            oldestLoadedIndex = newCursor,
            transcriptExhausted = false,
        )
    }

    fun setFollowBottom(value: Boolean) {
        if (_state.value.followBottom == value) return
        _state.update { it.copy(followBottom = value) }
    }

    fun answerInteraction(requestId: String, optionId: String, freeText: String?) {
        client.sendInteractionResponse(requestId, optionId, freeText)
        fun List<ChatMessage>.resolve() = map { m ->
            val data = m.interaction
            if (data != null && data.requestId == requestId && data.resolved == null) {
                m.copy(interaction = data.copy(resolved = optionId, resolvedText = freeText))
            } else m
        }
        _state.update { st ->
            st.copy(
                messages = st.messages.resolve(),
                sideChat = st.sideChat?.copy(messages = st.sideChat.messages.resolve()),
            )
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        ConnectionService.stop(appContext)
        client.close()
    }
}
