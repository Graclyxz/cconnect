package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jahirtrap.cconnect.BuildConfig
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.connection_error
import com.jahirtrap.cconnect.resources.notif_task_done
import com.jahirtrap.cconnect.resources.notif_question
import com.jahirtrap.cconnect.resources.notif_permission
import com.jahirtrap.cconnect.resources.permission_allow
import com.jahirtrap.cconnect.resources.permission_allow_always
import com.jahirtrap.cconnect.resources.permission_deny
import com.jahirtrap.cconnect.resources.plan
import com.jahirtrap.cconnect.data.AppCompat
import com.jahirtrap.cconnect.data.AppUpdater
import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.ChatMessage
import com.jahirtrap.cconnect.data.EnvironmentProfile
import com.jahirtrap.cconnect.data.CommandOption
import com.jahirtrap.cconnect.data.CompactData
import com.jahirtrap.cconnect.data.DiffLine
import com.jahirtrap.cconnect.data.InteractionData
import com.jahirtrap.cconnect.data.InteractionOption
import com.jahirtrap.cconnect.data.pending
import com.jahirtrap.cconnect.data.QuestionDraft
import com.jahirtrap.cconnect.data.ProjectInfo
import com.jahirtrap.cconnect.data.Role
import com.jahirtrap.cconnect.data.ServerEvent
import com.jahirtrap.cconnect.data.SessionInfo
import com.jahirtrap.cconnect.data.SessionMessage
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.data.remote.GitHubApi
import com.jahirtrap.cconnect.data.TodoItem
import com.jahirtrap.cconnect.data.remote.CapabilitiesApi
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.toBackendConfig
import com.jahirtrap.cconnect.data.remote.ChatSocket
import com.jahirtrap.cconnect.data.remote.SessionsApi
import com.jahirtrap.cconnect.data.remote.SettingsApi
import com.jahirtrap.cconnect.files.AttachmentFile
import com.jahirtrap.cconnect.files.uploadAttachment
import com.jahirtrap.cconnect.service.Notifier
import com.jahirtrap.cconnect.data.remote.UrlCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import com.jahirtrap.cconnect.data.nowMillis

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
    val modelOverride: String = "",
    val effortOverride: String = "",
    val permissionOverride: String = "",
    val streamingOverride: Boolean? = null,
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
    val environments: List<EnvironmentProfile> = emptyList(),
    val activeEnvironmentId: String? = null,
    val oldestLoadedIndex: Int? = null,
    val transcriptLoading: Boolean = false,
    val transcriptExhausted: Boolean = false,
    val followBottom: Boolean = true,
    val compacting: Boolean = false,
    val streamStatus: String? = null,
    val sideChat: SideChatState? = null,             // persisted side conversation (kept while the session lives)
    val sideChatOpen: Boolean = false,               // whether the side panel is currently shown
    val showWorking: String = "label",               // quick-chat working indicator visibility (label/off)
    val pendingToolIds: Set<String> = emptySet(),    // tools still running (tool_use seen, no result yet)
    val rewindPoints: List<SessionsApi.RewindPoint> = emptyList(),
    val rewindLoading: Boolean = false,
    val rewindTarget: SessionsApi.RewindPoint? = null,
    val rewindPreview: SessionsApi.RewindPreview? = null,
    val rewindBusy: Boolean = false,
    val pendingInput: String? = null,                // rewound prompt to restore into the composer
    val latestRelease: GitHubApi.Release? = null,    // newer app release on GitHub, if any
    val appOutdated: Boolean = false,                // server doesn't support this app version
    val serverOutdated: Boolean = false,             // this app doesn't support the server version
    val cliOutdated: Boolean = false,                // Claude Code on the server is older than required
    val versionNotices: List<CompatStatus> = emptyList(),  // dismissable startup notices
    val attachments: List<Attachment> = emptyList(), // files queued in the composer, uploaded on send
    val uploadingAttachments: Boolean = false,
)

data class Attachment(
    val id: Long,
    val file: AttachmentFile,
    val name: String,
    val size: Long,
    val progress: Float = 0f,
)

enum class CompatStatus { AppOutdated, ServerOutdated, CliOutdated, UpdateAvailable }

class ChatViewModel(private val ctx: TabContext) : ViewModel() {
    private val settings = Settings()
    val showTimestamps: Boolean get() = settings.showTimestamps
    private val client = ChatSocket(viewModelScope) { activeEnv()?.toBackendConfig() ?: Backend.snapshot() }

    private fun activeEnv(): EnvironmentProfile? =
        settings.environments.firstOrNull { it.id == ctx.environmentId } ?: settings.activeEnvironment

    private fun baseUrl(): String = activeEnv()?.toBackendConfig()?.baseUrl ?: Backend.baseUrl

    // Generation settings (model/effort/permission/streaming) are backend-owned
    private val _state = MutableStateFlow(
        Capabilities().defaults.let { d ->
            ChatUiState(
                permissionMode = d.permissionMode,
                model = d.model,
                effort = d.effort,
                streamTokens = true,
                environments = settings.environments,
                activeEnvironmentId = ctx.environmentId,
            )
        }
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    var draft by mutableStateOf("")
    var sideDraft by mutableStateOf("")

    private var nextId = 0L
    private var currentAssistantId: Long? = null
    private var currentThinkingId: Long? = null
    private var currentSideAssistantId: Long? = null
    private var historyJob: Job? = null
    private var historyLoaded = false
    private var defaultProjectApplied = false
    private var initialConsumed = false

    init {
        if (ctx.cwd.isBlank()) ctx.cwd = activeEnv()?.directory.orEmpty()
        ctx.initialSessionId?.let { sid ->
            _state.update {
                it.copy(
                    sessionId = sid,
                    activeProjectKey = ctx.initialProjectKey,
                    sessionColor = ctx.initialColor,
                )
            }
        }
        loadEnvOverrides()
        viewModelScope.launch {
            client.events.collect { (side, parent, event) -> if (side) onSideEvent(event) else onEvent(event, parent) }
        }
    }

    private fun applyDefaultDirectory() {
        ctx.cwd = activeEnv()?.directory.orEmpty()
        defaultProjectApplied = false
    }

    private fun loadEnvOverrides() {
        val env = activeEnv()
        _state.update {
            it.copy(
                modelOverride = env?.model ?: "",
                effortOverride = env?.effort ?: "",
                permissionOverride = env?.permissionMode ?: "",
                streamingOverride = env?.streaming,
            )
        }
    }

    fun connect() {
        if (activeEnv() == null) return
        if (_state.value.connection != ConnectionState.Disconnected) return
        _state.update { it.copy(connection = ConnectionState.Connecting, error = null) }
        if (!releaseChecked) {
            releaseChecked = true
            AppUpdater.consumeIfInstalled(BuildConfig.VERSION_NAME)
            viewModelScope.launch { checkForUpdates() }
        }
        viewModelScope.launch {
            refreshServerInfo()
            loadEnvOverrides()
            _state.update { it.copy(capabilitiesReady = true) }
            if (!initialConsumed) {
                initialConsumed = true
                ctx.initialSessionId?.let { sid ->
                    runCatching { loadSessionInto(sessionInfoFor(sid, ctx.initialProjectKey)) }
                }
            }
            client.connect()
        }
    }

    private val dismissedNotices = mutableSetOf<CompatStatus>()

    private fun pushNotice(notices: List<CompatStatus>, notice: CompatStatus): List<CompatStatus> =
        if (notice in dismissedNotices || notice in notices) notices else notices + notice

    private fun evaluateCompat(
        serverVersion: String?,
        supportedApp: String?,
        cliVersion: String? = null,
        supportedCli: String? = null,
    ) {
        val appOutdated = !AppCompat.satisfies(BuildConfig.VERSION_NAME, supportedApp)
        val serverOutdated = !AppCompat.satisfies(serverVersion, AppCompat.SUPPORTED_SERVER)
        val cliOutdated = cliVersion != null && !AppCompat.satisfies(cliVersion, supportedCli)
        if (!appOutdated) dismissedNotices.remove(CompatStatus.AppOutdated)
        if (!serverOutdated) dismissedNotices.remove(CompatStatus.ServerOutdated)
        if (!cliOutdated) dismissedNotices.remove(CompatStatus.CliOutdated)
        _state.update {
            var notices = it.versionNotices
            notices = if (appOutdated) pushNotice(notices, CompatStatus.AppOutdated) else notices - CompatStatus.AppOutdated
            notices = if (serverOutdated) pushNotice(notices, CompatStatus.ServerOutdated) else notices - CompatStatus.ServerOutdated
            notices = if (cliOutdated) pushNotice(notices, CompatStatus.CliOutdated) else notices - CompatStatus.CliOutdated
            it.copy(appOutdated = appOutdated, serverOutdated = serverOutdated, cliOutdated = cliOutdated, versionNotices = notices)
        }
    }

    private fun applyRelease(release: GitHubApi.Release?) {
        val newer = release != null && AppCompat.compare(release.tag, BuildConfig.VERSION_NAME) > 0
        if (!newer) dismissedNotices.remove(CompatStatus.UpdateAvailable)
        _state.update {
            var notices = it.versionNotices
            notices = if (newer) pushNotice(notices, CompatStatus.UpdateAvailable) else notices - CompatStatus.UpdateAvailable
            it.copy(latestRelease = if (newer) release else null, versionNotices = notices)
        }
    }

    private var releaseChecked = false

    suspend fun checkForUpdates() {
        applyRelease(GitHubApi.latestRelease())
    }

    private suspend fun refreshCompat() {
        val caps = CapabilitiesApi.capabilities()
        if (caps != null) {
            _state.update { it.copy(capabilities = caps) }
            evaluateCompat(caps.serverVersion, caps.supportedApp, caps.cliVersion, caps.supportedCli)
        } else {
            CapabilitiesApi.versionInfo()?.let {
                evaluateCompat(it.serverVersion, it.supportedApp, it.cliVersion, it.supportedCli)
            }
        }
    }

    fun refreshVersionInfo() {
        viewModelScope.launch { refreshCompat() }
    }

    private suspend fun refreshServerInfo() {
        refreshCompat()
        SettingsApi.get()?.let { s ->
            _state.update {
                it.copy(model = s.model, effort = s.effort, permissionMode = s.permissionMode, streamTokens = s.streaming, showWorking = s.showWorking)
            }
        }
    }

    fun dismissNotice(notice: CompatStatus) {
        dismissedNotices.add(notice)
        _state.update { it.copy(versionNotices = it.versionNotices - notice) }
    }

    private fun startSession(resume: String?) {
        val s = _state.value
        client.sendStart(
            ctx.cwd,
            s.permissionOverride.ifEmpty { s.permissionMode },
            resume,
            s.modelOverride.ifEmpty { s.model },
            s.effortOverride.ifEmpty { s.effort },
            s.streamingOverride ?: s.streamTokens,
        )
    }

    fun sendPrompt(text: String) {
        val trimmed = text.trim()
        val current = _state.value
        if (current.streaming || current.uploadingAttachments) return
        if (current.attachments.isEmpty()) {
            dispatchPrompt(trimmed)
            return
        }
        uploadJob = viewModelScope.launch {
            _state.update { it.copy(uploadingAttachments = true) }
            try {
                val saved = mutableListOf<String>()
                for (attachment in _state.value.attachments) {
                    val rel = uploadAttachment(
                        file = attachment.file,
                        path = "uploads/${attachment.name}",
                    ) { p ->
                        _state.update { st ->
                            st.copy(attachments = st.attachments.map { a -> if (a.id == attachment.id) a.copy(progress = p) else a })
                        }
                    }
                    if (rel == null) {
                        _state.update {
                            it.copy(
                                uploadingAttachments = false,
                                attachments = it.attachments.map { a -> a.copy(progress = 0f) },
                                error = getString(Res.string.connection_error),
                                pendingInput = trimmed.ifEmpty { null },
                            )
                        }
                        return@launch
                    }
                    saved += rel
                }
                _state.update { it.copy(attachments = emptyList(), uploadingAttachments = false) }
                dispatchPrompt(trimmed, saved)
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        uploadingAttachments = false,
                        attachments = it.attachments.map { a -> a.copy(progress = 0f) },
                        pendingInput = trimmed.ifEmpty { null },
                    )
                }
                throw e
            } finally {
                uploadJob = null
            }
        }
    }

    private fun dispatchPrompt(prompt: String, attachments: List<String> = emptyList()) {
        if ((prompt.isEmpty() && attachments.isEmpty()) || _state.value.streaming) return
        val compacting = prompt == "/compact" || prompt.startsWith("/compact ")
        if (!compacting) addMessage(Role.USER, prompt, attachments = attachments.map { it.substringAfterLast('/') }.takeIf { it.isNotEmpty() })
        currentAssistantId = null
        currentThinkingId = null
        _state.update {
            val todos = if (it.todos.all { t -> t.status == "completed" }) emptyList() else it.todos
            resetToInitialWindow(it).copy(streaming = true, compacting = compacting, streamStatus = null, error = null, todos = todos)
        }
        client.sendPrompt(prompt, attachments)
    }

    private var uploadJob: Job? = null
    private var attachmentId = 0L

    fun addAttachments(files: List<AttachmentFile>) {
        if (files.isEmpty() || _state.value.uploadingAttachments) return
        val items = files.map { file ->
            Attachment(id = attachmentId++, file = file, name = file.name, size = file.size)
        }
        _state.update { it.copy(attachments = it.attachments + items) }
    }

    fun removeAttachment(id: Long) {
        if (_state.value.uploadingAttachments) return
        _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
    }

    fun stop() {
        if (_state.value.uploadingAttachments) {
            uploadJob?.cancel()
            return
        }
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
        settings.updateEnvironment(ctx.environmentId) { it.copy(permissionMode = mode) }
        _state.update { it.copy(permissionOverride = mode) }
        val effective = mode.ifEmpty { _state.value.permissionMode }
        if (_state.value.connection == ConnectionState.Connected) client.sendSetPermissionMode(effective)
    }

    fun setModel(model: String) {
        settings.updateEnvironment(ctx.environmentId) { it.copy(model = model) }
        _state.update { it.copy(modelOverride = model) }
    }

    fun setEffort(effort: String) {
        settings.updateEnvironment(ctx.environmentId) { it.copy(effort = effort) }
        _state.update { it.copy(effortOverride = effort) }
    }

    fun toggleStreaming() {
        val s = _state.value
        val next = !(s.streamingOverride ?: s.streamTokens)
        settings.updateEnvironment(ctx.environmentId) { it.copy(streaming = next) }
        _state.update { it.copy(streamingOverride = next) }
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
                streamStatus = null,
                oldestLoadedIndex = null,
                transcriptLoading = false,
                transcriptExhausted = false,
                pendingToolIds = emptySet(),
            )
        }
        client.resetResume()
        startSession(resume = null)
    }

    fun refreshEnvironments() {
        ctx.environmentId = settings.activeEnvironment?.id
        _state.update { it.copy(environments = settings.environments, activeEnvironmentId = ctx.environmentId) }
    }

    fun selectEnvironment(id: String) {
        if (id == ctx.environmentId) return
        ctx.environmentId = id
        settings.activeEnvironmentId = id
        applyDefaultDirectory()
        currentAssistantId = null
        currentThinkingId = null
        client.close()
        _state.update {
            it.copy(
                activeEnvironmentId = id,
                connection = ConnectionState.Disconnected,
                capabilitiesReady = false,
                messages = emptyList(),
                sessionId = null,
                activeProjectKey = null,
                sessionColor = null,
                todos = emptyList(),
                streaming = false,
                streamStatus = null,
                historyProjects = emptyList(),
                historySessions = emptyList(),
                historyProjectKey = null,
                oldestLoadedIndex = null,
                transcriptLoading = false,
                transcriptExhausted = false,
            )
        }
        loadEnvOverrides()
        connect()
        loadHistory()
    }

    fun ensureHistoryLoaded() {
        if (!historyLoaded) loadHistory()
    }

    fun loadHistory() {
        historyJob?.cancel()
        historyLoaded = true
        historyJob = viewModelScope.launch {
            _state.update { it.copy(historyLoading = true) }
            val projects = SessionsApi.projects()
            val dir = activeEnv()?.directory.orEmpty()
            var finalProjects = projects
            var selectedKey = _state.value.historyProjectKey
            if (dir.isNotBlank()) {
                val targetKey = dir.replace(Regex("[^A-Za-z0-9]"), "-")
                val existing = projects.firstOrNull { it.projectKey == targetKey || it.path == dir }
                if (existing == null) finalProjects = listOf(ProjectInfo(targetKey, dir, null, 0, null)) + projects
                if (!defaultProjectApplied) {
                    defaultProjectApplied = true
                    selectedKey = existing?.projectKey ?: targetKey
                }
            } else if (!defaultProjectApplied) {
                defaultProjectApplied = true
                selectedKey = null
            }
            val sessions = SessionsApi.sessions(selectedKey)
            _state.update { it.copy(historyProjects = finalProjects, historyProjectKey = selectedKey, historySessions = sessions, historyLoading = false) }
        }
    }

    fun selectHistoryProject(projectKey: String?) {
        _state.update { it.copy(historyProjectKey = projectKey, historySessions = emptyList()) }
        if (projectKey != null) {
            _state.value.historyProjects.firstOrNull { it.projectKey == projectKey }?.path?.let { ctx.cwd = it }
        }
        loadHistory()
    }

    fun restoreSession(sessionId: String, projectKey: String) {
        if (_state.value.sessionId == sessionId) return
        viewModelScope.launch {
            val info = SessionsApi.sessions(projectKey).firstOrNull { it.sessionId == sessionId }
                ?: SessionInfo(sessionId, projectKey, null, null, 0L, null, null, null)
            openSession(info)
        }
    }

    fun openSession(session: SessionInfo) {
        viewModelScope.launch {
            if (!loadSessionInto(session)) return@launch
            client.resetResume()
            startSession(resume = session.sessionId)
        }
    }

    private suspend fun sessionInfoFor(sessionId: String, projectKey: String?): SessionInfo =
        SessionsApi.sessions(projectKey.orEmpty()).firstOrNull { it.sessionId == sessionId }
            ?: SessionInfo(sessionId, projectKey, null, null, 0L, null, null, null)

    private fun nestAgents(flat: List<Pair<ChatMessage, String?>>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        val agentAt = mutableMapOf<String, Int>()
        for ((msg, parent) in flat) {
            if (parent != null) {
                val idx = agentAt[parent]
                if (idx != null && (msg.role == Role.TOOL || msg.role == Role.FILE_CHANGE)) {
                    result[idx] = result[idx].copy(children = result[idx].children + msg)
                }
                continue
            }
            if (msg.role == Role.AGENT && msg.toolUseId != null) agentAt[msg.toolUseId] = result.size
            result.add(msg)
        }
        return result
    }

    private suspend fun loadSessionInto(session: SessionInfo): Boolean {
        val projectKey = session.projectKey ?: return false
        val page = SessionsApi.sessionMessages(session.sessionId, projectKey, limit = 100)
        val visible = page.items.filter { it.text.isNotBlank() || it.interaction != null || !it.diffLines.isNullOrEmpty() || it.compact != null || it.labelOnly || !it.images.isNullOrEmpty() }
        val loaded = nestAgents(visible.mapIndexed { i, m ->
            ChatMessage(i.toLong(), m.toRole(), m.text, toolName = m.name, toolUseId = m.toolUseId, path = m.path, interaction = m.interaction, diffLines = m.diffLines, compact = m.compact, sourceIndex = m.index, labelOnly = m.labelOnly, result = m.result, images = imageUrls(m, session.sessionId, projectKey), timestamp = m.timestamp) to m.parent
        })
        nextId = visible.size.toLong()
        currentAssistantId = null
        currentThinkingId = null
        session.path?.let { ctx.cwd = it }
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
        return true
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

    private fun currentProjectKey(): String? =
        _state.value.activeProjectKey
            ?: ctx.cwd.takeIf { it.isNotBlank() }?.replace(Regex("[^A-Za-z0-9]"), "-")

    fun loadRewindPoints() {
        val sid = _state.value.sessionId ?: return
        val proj = currentProjectKey() ?: return
        viewModelScope.launch {
            _state.update { it.copy(rewindLoading = true) }
            val points = SessionsApi.checkpoints(sid, proj)
            _state.update { it.copy(rewindPoints = points, rewindLoading = false) }
        }
    }

    fun selectRewindPoint(point: SessionsApi.RewindPoint) {
        val sid = _state.value.sessionId ?: return
        val proj = currentProjectKey() ?: return
        _state.update { it.copy(rewindTarget = point, rewindPreview = null) }
        viewModelScope.launch {
            val preview = SessionsApi.rewindPreview(sid, proj, point.id)
            _state.update { if (it.rewindTarget?.id == point.id) it.copy(rewindPreview = preview) else it }
        }
    }

    fun dismissRewind() {
        _state.update { it.copy(rewindTarget = null, rewindPreview = null, rewindBusy = false) }
    }

    fun confirmRewind(both: Boolean) {
        val s = _state.value
        val sid = s.sessionId ?: return
        val proj = currentProjectKey() ?: return
        val point = s.rewindTarget ?: return
        if (s.rewindBusy) return
        _state.update { it.copy(rewindBusy = true) }
        viewModelScope.launch {
            val result = SessionsApi.rewind(sid, proj, point, if (both) "both" else "conversation")
            if (result == null) {
                _state.update { it.copy(rewindBusy = false) }
                return@launch
            }
            reloadConversation()
            _state.update { it.copy(rewindTarget = null, rewindPreview = null, rewindBusy = false, pendingInput = point.text) }
        }
    }

    fun consumePendingInput() {
        _state.update { it.copy(pendingInput = null) }
    }

    private suspend fun reloadConversation() {
        val s = _state.value
        val sid = s.sessionId ?: return
        val proj = currentProjectKey() ?: return
        val page = SessionsApi.sessionMessages(sid, proj, limit = 100)
        val visible = page.items.filter { it.text.isNotBlank() || it.interaction != null || !it.diffLines.isNullOrEmpty() || it.compact != null || it.labelOnly || !it.images.isNullOrEmpty() }
        val loaded = nestAgents(visible.mapIndexed { i, m ->
            ChatMessage(i.toLong(), m.toRole(), m.text, toolName = m.name, toolUseId = m.toolUseId, path = m.path, interaction = m.interaction, diffLines = m.diffLines, compact = m.compact, sourceIndex = m.index, labelOnly = m.labelOnly, result = m.result, images = imageUrls(m, sid, proj), timestamp = m.timestamp) to m.parent
        })
        nextId = visible.size.toLong()
        currentAssistantId = null
        currentThinkingId = null
        _state.update {
            it.copy(
                messages = loaded,
                todos = emptyList(),
                oldestLoadedIndex = page.startIndex.takeIf { page.items.isNotEmpty() },
                transcriptLoading = false,
                transcriptExhausted = !page.hasMore,
                pendingToolIds = emptySet(),
            )
        }
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

    private fun imageUrls(m: SessionMessage, sessionId: String, projectKey: String?): List<String>? =
        m.images?.map { ref ->
            "${baseUrl()}/sessions/$sessionId/images/$ref?project=${UrlCodec.encode(projectKey.orEmpty())}"
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
        "agent" -> Role.AGENT
        "plan" -> Role.PLAN
        else -> Role.SYSTEM
    }

    private fun onAgentChild(parent: String, event: ServerEvent) {
        val child: ChatMessage? = when (event) {
            is ServerEvent.ToolUse -> ChatMessage(nextId++, Role.TOOL, event.input.orEmpty(), toolName = event.name, toolUseId = event.id, result = event.result, timestamp = nowMillis())
            is ServerEvent.FileChange -> ChatMessage(nextId++, Role.FILE_CHANGE, "", toolUseId = event.id, path = event.path, diffLines = event.diffLines, labelOnly = event.labelOnly, timestamp = nowMillis())
            else -> null
        }
        _state.update { st ->
            st.copy(messages = st.messages.map { m ->
                if (m.role == Role.AGENT && m.toolUseId == parent) {
                    val kids = when {
                        event is ServerEvent.ToolResult && event.toolUseId != null && event.content != null ->
                            m.children.map { if (it.toolUseId == event.toolUseId) it.copy(result = event.content) else it }
                        child != null -> m.children + child
                        else -> m.children
                    }
                    m.copy(children = kids)
                } else m
            })
        }
    }

    private suspend fun onEvent(event: ServerEvent, parent: String? = null) {
        if (parent != null) {
            onAgentChild(parent, event)
            return
        }
        when (event) {
            is ServerEvent.Connecting -> _state.update {
                if (it.connection == ConnectionState.Connected) it
                else it.copy(connection = ConnectionState.Connecting)
            }
            is ServerEvent.Open -> startSession(_state.value.sessionId)
            is ServerEvent.Ready -> {
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
                viewModelScope.launch { refreshServerInfo() }
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
            is ServerEvent.Plan -> {
                currentAssistantId = null
                currentThinkingId = null
                addMessage(Role.PLAN, event.markdown)
            }
            is ServerEvent.Agent -> {
                currentAssistantId = null
                currentThinkingId = null
                addMessage(Role.AGENT, event.description.orEmpty(), toolName = event.subagentType, toolUseId = event.id, labelOnly = event.labelOnly)
                event.id?.let { id -> _state.update { it.copy(pendingToolIds = it.pendingToolIds + id) } }
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
            is ServerEvent.Compacting -> _state.update { it.copy(compacting = true) }
            is ServerEvent.Status -> _state.update { it.copy(streamStatus = if (event.kind == "ok") null else event.kind) }
            is ServerEvent.Compact -> {
                currentAssistantId = null
                currentThinkingId = null
                _state.update {
                    it.copy(
                        messages = listOf(ChatMessage(nextId++, Role.COMPACT, compact = CompactData(event.trigger, event.preTokens, event.postTokens, event.summary), timestamp = nowMillis())),
                        oldestLoadedIndex = null,
                        transcriptExhausted = true,
                        compacting = false,
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
                        path = ctx.cwd,
                        lastActive = nowMillis() / 1000.0,
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
            is ServerEvent.Done -> {
                if (_state.value.streaming && settings.notifyTaskDone) {
                    Notifier.notify(
                        Notifier.Kind.TaskDone,
                        getString(Res.string.notif_task_done),
                        _state.value.messages.lastOrNull { it.role == Role.ASSISTANT }?.text
                            ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(120),
                    )
                }
                resetStreaming()
            }
            is ServerEvent.Interrupted -> {
                resetStreaming()
                dismissPendingInteractions()
                addMessage(Role.INTERRUPTED, "")
            }
            is ServerEvent.Err -> {
                resetStreaming()
                addMessage(Role.ERROR, event.message)
            }
            is ServerEvent.InteractionRequest -> {
                if (_state.value.messages.none { it.interaction?.requestId == event.requestId }) {
                    currentAssistantId = null
                    currentThinkingId = null
                    val data = interactionDataOf(event)
                    val tuid = event.toolUseId
                    _state.update { st ->
                        val cleaned = if (tuid != null) st.messages.filterNot { it.role == Role.TOOL && it.toolUseId == tuid } else st.messages
                        st.copy(messages = cleaned + ChatMessage(nextId++, Role.INTERACTION, event.input.orEmpty(), event.toolName, tuid, data, timestamp = nowMillis()))
                    }
                    if (settings.notifyInteraction) {
                        val question = event.kind == "questions"
                        val isPlan = event.toolName == "ExitPlanMode"
                        val actions = if (question) emptyList() else event.options
                            .filter { it.id != "different" }
                            .mapNotNull { opt -> notificationOptionLabel(opt)?.let { Notifier.Action(it, event.requestId, opt.id) } }
                        val body = when {
                            question -> event.questions.firstOrNull()?.question?.take(120)
                            isPlan -> event.input.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('#', ' ')?.trim()?.take(120)?.ifBlank { null } ?: getString(Res.string.plan)
                            else -> event.toolName
                        }
                        Notifier.notify(
                            Notifier.Kind.Interaction,
                            getString(if (question) Res.string.notif_question else Res.string.notif_permission),
                            body,
                            actions,
                        )
                    }
                }
            }
            is ServerEvent.InteractionResolved -> updateInteraction(event.requestId) {
                if (it.resolved == null) it.copy(resolved = event.optionId ?: "") else it
            }
            is ServerEvent.Closed -> {
                currentAssistantId = null
                currentThinkingId = null
                currentSideAssistantId = null
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

    private suspend fun onSideEvent(event: ServerEvent) {
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
                    val data = interactionDataOf(event)
                    val tuid = event.toolUseId
                    val cleaned = if (tuid != null) sc.messages.filterNot { it.role == Role.TOOL && it.toolUseId == tuid } else sc.messages
                    st.copy(sideChat = sc.copy(messages = cleaned + ChatMessage(nextId++, Role.INTERACTION, event.input.orEmpty(), event.toolName, tuid, data)))
                }
            }
            is ServerEvent.Done, is ServerEvent.Interrupted -> {
                currentSideAssistantId = null
                _state.update { st ->
                    val sc = st.sideChat ?: return@update st
                    val msgs = if (event is ServerEvent.Interrupted) sc.messages + ChatMessage(nextId++, Role.INTERRUPTED, "") else sc.messages
                    st.copy(sideChat = sc.copy(streaming = false, messages = msgs))
                }
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
            st.copy(sideChat = sc.copy(messages = sc.messages.filterNot { it.role == Role.INTERACTION && it.interaction?.pending == true }))
        }
    }

    private fun onHistoryChunk(event: ServerEvent.HistoryChunk) {
        if (event.sessionId != _state.value.sessionId) {
            _state.update { it.copy(transcriptLoading = false) }
            return
        }
        val older = event.items
            .filter { it.text.isNotBlank() || it.interaction != null || !it.diffLines.isNullOrEmpty() || it.compact != null || it.labelOnly || !it.images.isNullOrEmpty() }
        _state.update { st ->
            val prepended = older.mapIndexed { i, m ->
                ChatMessage(nextId + i, m.toRole(), m.text, toolName = m.name, path = m.path, interaction = m.interaction, diffLines = m.diffLines, compact = m.compact, sourceIndex = m.index, labelOnly = m.labelOnly, result = m.result, images = imageUrls(m, event.sessionId, st.activeProjectKey), timestamp = m.timestamp)
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

    private suspend fun notificationOptionLabel(opt: InteractionOption): String? = when {
        !opt.label.isNullOrBlank() -> opt.label
        opt.id == "allow" -> getString(Res.string.permission_allow)
        opt.id == "allow_always" -> getString(Res.string.permission_allow_always)
        opt.id == "deny" -> getString(Res.string.permission_deny)
        else -> null
    }

    private fun resetStreaming() {
        currentAssistantId = null
        currentThinkingId = null
        _state.update { it.copy(streaming = false, compacting = false, pendingToolIds = emptySet(), streamStatus = it.streamStatus?.takeIf { s -> s == "failed" }) }
    }

    private fun dismissPendingInteractions() {
        _state.update { st ->
            if (st.messages.none { it.role == Role.INTERACTION && it.interaction?.pending == true }) return@update st
            st.copy(messages = st.messages.filterNot { it.role == Role.INTERACTION && it.interaction?.pending == true })
        }
    }

    private fun interactionDataOf(event: ServerEvent.InteractionRequest): InteractionData = InteractionData(
        requestId = event.requestId,
        kind = event.kind,
        options = event.options,
        title = event.title,
        questions = event.questions,
        drafts = if (event.kind == "questions") List(event.questions.size) { QuestionDraft() } else emptyList(),
    )

    private fun append(currentId: Long?, role: Role, delta: String): Long {
        if (currentId == null) {
            val newId = nextId++
            _state.update { applyTailCap(it.copy(messages = it.messages + ChatMessage(newId, role, delta, timestamp = nowMillis()))) }
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
        attachments: List<String>? = null,
    ) {
        _state.update {
            applyTailCap(it.copy(messages = it.messages + ChatMessage(nextId++, role, text, toolName, toolUseId, interaction, path, diffLines, compact, labelOnly = labelOnly, result = result, ephemeral = ephemeral, attachments = attachments, timestamp = nowMillis())))
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

    private fun updateInteraction(requestId: String, transform: (InteractionData) -> InteractionData) {
        fun List<ChatMessage>.apply() = map { m ->
            val data = m.interaction
            if (data != null && data.requestId == requestId) m.copy(interaction = transform(data)) else m
        }
        _state.update { st ->
            st.copy(
                messages = st.messages.apply(),
                sideChat = st.sideChat?.let { it.copy(messages = it.messages.apply()) },
            )
        }
    }

    private fun findInteraction(requestId: String): InteractionData? {
        val st = _state.value
        return (st.messages + (st.sideChat?.messages ?: emptyList()))
            .firstNotNullOfOrNull { m -> m.interaction?.takeIf { it.requestId == requestId } }
    }

    private fun editDraft(requestId: String, qIndex: Int, edit: (QuestionDraft) -> QuestionDraft) =
        updateInteraction(requestId) { data ->
            if (data.submitted || qIndex !in data.drafts.indices) return@updateInteraction data
            data.copy(drafts = data.drafts.toMutableList().also { it[qIndex] = edit(it[qIndex]) })
        }

    fun toggleQuestionOption(requestId: String, qIndex: Int, optionId: String) =
        updateInteraction(requestId) { data ->
            if (data.submitted || qIndex !in data.questions.indices || qIndex !in data.drafts.indices) return@updateInteraction data
            val multi = data.questions[qIndex].multiSelect
            val draft = data.drafts[qIndex]
            val selected = when {
                multi -> if (optionId in draft.selected) draft.selected - optionId else draft.selected + optionId
                optionId in draft.selected -> emptySet()
                else -> setOf(optionId)
            }
            val freeText = if (!multi && selected.isNotEmpty()) "" else draft.freeText
            data.copy(drafts = data.drafts.toMutableList().also { it[qIndex] = draft.copy(selected = selected, freeText = freeText) })
        }

    fun setQuestionFreeText(requestId: String, qIndex: Int, text: String) =
        updateInteraction(requestId) { data ->
            if (data.submitted || qIndex !in data.drafts.indices) return@updateInteraction data
            val single = qIndex in data.questions.indices && !data.questions[qIndex].multiSelect
            data.copy(drafts = data.drafts.toMutableList().also {
                val d = it[qIndex]
                it[qIndex] = d.copy(freeText = text, selected = if (single && text.isNotBlank()) emptySet() else d.selected)
            })
        }

    fun setQuestionNotes(requestId: String, qIndex: Int, text: String) =
        editDraft(requestId, qIndex) { it.copy(notes = text) }

    fun submitQuestions(requestId: String) {
        val data = findInteraction(requestId) ?: return
        if (data.submitted) return
        client.sendQuestionsResponse(requestId, data.drafts)
        val summary = data.questions.mapIndexed { i, q ->
            val draft = data.drafts.getOrElse(i) { QuestionDraft() }
            val labels = q.options.filter { it.id in draft.selected }.mapNotNull { it.label }
            (labels + listOfNotNull(draft.freeText.ifBlank { null })).joinToString(", ")
        }
        val notes = data.questions.indices.map { data.drafts.getOrElse(it) { QuestionDraft() }.notes }
        updateInteraction(requestId) { it.copy(submitted = true, summary = summary, notes = notes) }
    }

    fun chatQuestions(requestId: String) {
        val data = findInteraction(requestId) ?: return
        if (data.submitted) return
        client.sendQuestionsChat(requestId)
        updateInteraction(requestId) { it.copy(submitted = true, declined = true) }
    }

    override fun onCleared() {
        client.close()
    }
}
