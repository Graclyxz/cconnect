package com.jahirtrap.cconnect.claude

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Blocks
import com.composables.icons.lucide.FilePen
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Unplug
import com.composables.icons.lucide.Wand
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.chat.ChatViewModel
import com.jahirtrap.cconnect.chat.ConnectionState
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.ClaudeApi
import com.jahirtrap.cconnect.data.remote.CliApi
import com.jahirtrap.cconnect.data.remote.GitHubApi
import com.jahirtrap.cconnect.settings.PreferenceRow
import com.jahirtrap.cconnect.settings.SettingsGroup
import com.jahirtrap.cconnect.ui.ActionButton
import com.jahirtrap.cconnect.ui.AppBottomSheet
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.CustomIcons
import com.jahirtrap.cconnect.ui.Claude
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.EnvironmentSelectDialog
import com.jahirtrap.cconnect.ui.InputField
import com.jahirtrap.cconnect.ui.MarkdownText
import com.jahirtrap.cconnect.ui.SelectField
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.theme.palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClaudeScreen(onClose: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }

    var cliInfo by remember { mutableStateOf<CliApi.CliInfo?>(null) }
    var extensions by remember { mutableStateOf<ClaudeApi.Extensions?>(null) }
    var skills by remember { mutableStateOf<List<ClaudeApi.Skill>?>(null) }
    var mcpServers by remember { mutableStateOf<List<ClaudeApi.McpServer>?>(null) }
    var userPrompt by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var envMenu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ClaudeSheet?>(null) }
    var editingPrompt by remember { mutableStateOf(false) }
    val activeName = state.environments.firstOrNull { it.id == state.activeEnvironmentId }?.name
    val serverReady = Backend.isConfigured && state.connection == ConnectionState.Connected

    suspend fun load() {
        cliInfo = CliApi.status()
        extensions = ClaudeApi.extensions()
        skills = ClaudeApi.skills()
        mcpServers = ClaudeApi.mcp()
        userPrompt = ClaudeApi.userPrompt()
        loaded = true
        refreshing = false
    }
    LaunchedEffect(state.activeEnvironmentId) { loaded = false; load() }
    LaunchedEffect(state.connection) { if (state.connection == ConnectionState.Connected) load() }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.claude),
                subtitle = if (!serverReady && loaded) stringResource(R.string.server_unavailable) else activeName,
                subtitleLeading = if (!serverReady && loaded) ({ StatusDot(palette.red) }) else null,
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
                actions = {
                    TooltipIconButton(label = stringResource(R.string.environment), onClick = { envMenu = true }) {
                        Icon(Lucide.Server, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (envMenu) {
            EnvironmentSelectDialog(
                environments = state.environments,
                activeId = state.activeEnvironmentId,
                onSelect = { if (it != state.activeEnvironmentId) vm.selectEnvironment(it) },
                onDismiss = { envMenu = false },
            )
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; scope.launch { load() } },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            if (!loaded) {
                CenteredProgress(Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp),
                ) {
                    SettingsGroup(stringResource(R.string.cli)) {
                        PreferenceRow(
                            CustomIcons.Claude,
                            stringResource(R.string.cli),
                            cliInfo?.activeVersion ?: "—",
                            enabled = serverReady,
                            alert = stringResource(R.string.compat_cli_outdated).takeIf { state.cliOutdated },
                            trailing = {
                                TooltipIconButton(label = stringResource(R.string.changelog), onClick = { sheet = ClaudeSheet.Changelog }, enabled = serverReady) {
                                    Icon(Lucide.FileText, contentDescription = null)
                                }
                            },
                        )
                        cliInfo?.let { info ->
                            CliInlineControls(
                                info = info,
                                enabled = serverReady,
                                onChanged = { cliInfo = it; vm.refreshVersionInfo() },
                            )
                        }
                        PreferenceRow(
                            Lucide.FilePen,
                            stringResource(R.string.user_prompt),
                            userPrompt?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(80)
                                ?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.user_prompt_summary),
                            enabled = serverReady,
                        ) { editingPrompt = true }
                    }
                    SettingsGroup(stringResource(R.string.extensions)) {
                        val pluginList = extensions?.plugins
                        val enabledCount = pluginList?.count { it.enabled } ?: 0
                        PreferenceRow(
                            Lucide.Blocks,
                            stringResource(R.string.plugins),
                            if (pluginList != null) {
                                pluralStringResource(R.plurals.enabled_count, enabledCount, enabledCount, pluginList.size)
                            } else "—",
                            enabled = serverReady,
                        ) { sheet = ClaudeSheet.Plugins }
                        PreferenceRow(
                            Lucide.Wand,
                            stringResource(R.string.skills),
                            skills?.size?.toString() ?: "—",
                            enabled = serverReady,
                        ) { sheet = ClaudeSheet.Skills }
                        PreferenceRow(
                            Lucide.Unplug,
                            stringResource(R.string.mcp_servers),
                            mcpServers?.size?.toString() ?: "—",
                            enabled = serverReady,
                        ) { sheet = ClaudeSheet.Mcp }
                        PreferenceRow(
                            Lucide.Store,
                            stringResource(R.string.marketplaces),
                            extensions?.marketplaces?.size?.toString() ?: "—",
                            enabled = serverReady,
                        ) { sheet = ClaudeSheet.Marketplaces }
                    }
                }
            }
        }
    }

    if (editingPrompt) {
        PromptDialog(
            initial = userPrompt.orEmpty(),
            onConfirm = { text ->
                scope.launch {
                    if (ClaudeApi.setUserPrompt(text)) userPrompt = text
                    editingPrompt = false
                }
            },
            onDismiss = { editingPrompt = false },
        )
    }

    when (sheet) {
        ClaudeSheet.Changelog -> ClaudeChangelogSheet(
            cliVersion = cliInfo?.activeVersion,
            onDismiss = { sheet = null },
        )
        ClaudeSheet.Plugins -> ListSheet(stringResource(R.string.plugins), onDismiss = { sheet = null }) {
            items(extensions?.plugins.orEmpty(), key = { "${it.name}@${it.marketplace}" }) { plugin ->
                SheetRow(
                    title = plugin.name,
                    subtitle = listOfNotNull(plugin.marketplace, plugin.version, plugin.scope).joinToString(" • "),
                    enabled = plugin.enabled,
                )
            }
        }
        ClaudeSheet.Skills -> ListSheet(stringResource(R.string.skills), onDismiss = { sheet = null }) {
            items(skills.orEmpty(), key = { "${it.plugin}/${it.name}" }) { skill ->
                SheetRow(
                    title = skill.name,
                    subtitle = skill.description ?: skill.plugin,
                    enabled = skill.enabled,
                )
            }
        }
        ClaudeSheet.Mcp -> ListSheet(stringResource(R.string.mcp_servers), onDismiss = { sheet = null }) {
            items(mcpServers.orEmpty(), key = { it.name }) { server ->
                SheetRow(
                    title = server.name,
                    subtitle = listOfNotNull(server.type, server.detail).joinToString(" • "),
                    enabled = true,
                )
            }
        }
        ClaudeSheet.Marketplaces -> ListSheet(stringResource(R.string.marketplaces), onDismiss = { sheet = null }) {
            items(extensions?.marketplaces.orEmpty(), key = { it.name }) { market ->
                SheetRow(title = market.name, subtitle = market.repo, enabled = true)
            }
        }
        null -> {}
    }
}

private enum class ClaudeSheet { Changelog, Plugins, Skills, Mcp, Marketplaces }

@Composable
private fun CliInlineControls(
    info: CliApi.CliInfo,
    enabled: Boolean,
    onChanged: (CliApi.CliInfo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var source by remember(info) { mutableStateOf(info.source) }
    var customPath by remember(info) { mutableStateOf(info.customPath ?: "") }
    var saving by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }

    val systemLabel = stringResource(R.string.cli_source_system)
    val customLabel = stringResource(R.string.cli_source_custom)
    val bundledLabel = stringResource(R.string.cli_source_bundled)
    fun labelFor(src: String) = when (src) {
        "system" -> systemLabel
        "custom" -> customLabel
        "bundled" -> bundledLabel
        else -> src
    }
    val sourceOptions = info.sources.map { src ->
        val version = when (src) {
            "system" -> info.systemVersion
            "bundled" -> info.bundledVersion
            else -> null
        }
        src to (labelFor(src) + (version?.let { " - $it" } ?: ""))
    }
    val dirty = source != info.source || (source == "custom" && customPath.trim() != (info.customPath ?: ""))

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
        SelectField(stringResource(R.string.cli_source), source, sourceOptions) { source = it }
        if (source == "custom") {
            Spacer(Modifier.height(10.dp))
            InputField(
                value = customPath,
                onValueChange = { customPath = it },
                label = { Text(stringResource(R.string.cli_custom_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (dirty) {
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = stringResource(R.string.save),
                enabled = enabled && !saving && (source != "custom" || customPath.isNotBlank()),
                onClick = {
                    scope.launch {
                        saving = true
                        CliApi.setSource(source, customPath.trim().ifBlank { null })?.let(onChanged)
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (info.source != "bundled") {
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = stringResource(if (updating) R.string.cli_updating else R.string.cli_update),
                enabled = enabled && !updating,
                onClick = {
                    scope.launch {
                        updating = true
                        CliApi.update()
                        CliApi.status()?.let(onChanged)
                        updating = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ListSheet(
    title: String,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss, title = title, showClose = true) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            content = content,
        )
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String?, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        StatusDot(if (enabled) palette.green else MaterialTheme.colorScheme.outlineVariant, box = 16.dp, dot = 10.dp)
    }
}

@Composable
private fun PromptDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.user_prompt),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) }
        },
    ) {
        Text(
            stringResource(R.string.user_prompt_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        InputField(
            value = text,
            onValueChange = { text = it },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ClaudeChangelogSheet(cliVersion: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<List<GitHubApi.ReleaseNotes>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = GitHubApi.claudeChangelog(context, cliVersion)
        if (result != null) notes = result else failed = true
    }
    AppBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.changelog), showClose = true) {
        val items = notes
        when {
            failed -> EmptyState(stringResource(R.string.connection_error), Modifier.fillMaxWidth().weight(1f))
            items == null -> CenteredProgress(Modifier.fillMaxWidth().weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.tag }) { release ->
                    Column {
                        Text(
                            release.tag,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        MarkdownText(release.body, modifier = Modifier.fillMaxWidth(), selectable = false)
                    }
                }
            }
        }
    }
}
