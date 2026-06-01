package com.jahirtrap.cconnect.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ScanQrCode
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.Wand
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.ConnectionProfile
import com.jahirtrap.cconnect.data.QrConnectionPayload
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.CapabilitiesApi
import com.jahirtrap.cconnect.data.remote.CliApi
import com.jahirtrap.cconnect.data.remote.SettingsApi
import kotlinx.coroutines.launch
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.ConfirmSelectDialog
import com.jahirtrap.cconnect.ui.DialogActionItem
import com.jahirtrap.cconnect.ui.DialogSelectItem
import com.jahirtrap.cconnect.ui.SecretTextField
import com.jahirtrap.cconnect.ui.SelectDialog
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.SelectField
import com.jahirtrap.cconnect.ui.languageLabel
import com.jahirtrap.cconnect.ui.themeIcon
import com.jahirtrap.cconnect.ui.themeLabel
import com.jahirtrap.cconnect.ui.LANGUAGE_TAGS
import com.jahirtrap.cconnect.ui.THEME_MODES
import com.jahirtrap.cconnect.ui.theme.ACCENTS
import com.jahirtrap.cconnect.ui.theme.palette
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.accentNameAt
import com.jahirtrap.cconnect.ui.theme.dynamicAccent
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeMode: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    accentIndex: Int,
    onAccent: (Int) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
    onOpenSshHosts: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var connections by remember { mutableStateOf(settings.connections) }
    var activeId by remember { mutableStateOf(settings.activeConnection?.id) }
    var caps by remember { mutableStateOf(Capabilities()) }
    var model by remember { mutableStateOf(caps.defaults.model) }
    var effort by remember { mutableStateOf(caps.defaults.effort) }
    var permissionMode by remember { mutableStateOf(caps.defaults.permissionMode) }
    var streaming by remember { mutableStateOf(true) }
    var showThinking by remember { mutableStateOf("full") }
    var showToolUse by remember { mutableStateOf("label") }
    var showFileChange by remember { mutableStateOf("full") }
    var showCompact by remember { mutableStateOf("full") }
    var showWorking by remember { mutableStateOf("label") }
    var cliInfo by remember { mutableStateOf<CliApi.CliInfo?>(null) }
    // The generation/permission/CLI rows are server-owned: dimmed/disabled until a fetch
    // succeeds, with a loading indicator while fetching.
    var serverReady by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(Backend.isConfigured) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadServerSettings() {
        if (!Backend.isConfigured) { serverReady = false; loading = false; cliInfo = null; return }
        loading = true
        CapabilitiesApi.capabilities()?.let { caps = it }
        val s = SettingsApi.get()
        if (s != null) {
            model = s.model; effort = s.effort; permissionMode = s.permissionMode; streaming = s.streaming
            showThinking = s.showThinking; showToolUse = s.showToolUse
            showFileChange = s.showFileChange; showCompact = s.showCompact; showWorking = s.showWorking
        }
        cliInfo = CliApi.status()
        serverReady = s != null
        loading = false
    }

    LaunchedEffect(activeId, connections) { loadServerSettings() }

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; loadServerSettings(); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
            ) {
                PreferenceRow(themeIcon(themeMode), stringResource(R.string.theme), themeLabel(themeMode)) { dialog = SettingsDialog.Theme }
                PreferenceRow(Lucide.Languages, stringResource(R.string.language), languageLabel(language)) { dialog = SettingsDialog.Language }
                PreferenceRow(
                    icon = Lucide.Palette,
                    title = stringResource(R.string.accent),
                    summary = if (dynamicColor) stringResource(R.string.accent_dynamic)
                    else accentNameAt(accentIndex),
                    trailing = { AccentDot(if (dynamicColor) MaterialTheme.colorScheme.primary else accentAt(accentIndex)) },
                ) { dialog = SettingsDialog.Accent }
                PreferenceRow(
                    Lucide.Server,
                    stringResource(R.string.connections),
                    connections.firstOrNull { it.id == activeId }?.let { "${it.name} • ${it.address}" }
                        ?: stringResource(R.string.no_connections),
                ) { dialog = SettingsDialog.Connections }
                PreferenceRow(
                    Lucide.SquareTerminal,
                    stringResource(R.string.ssh_hosts),
                    stringResource(R.string.ssh_hosts_summary),
                    onClick = onOpenSshHosts,
                )
                // Server-owned: always shown, dimmed/disabled until reachable.
                val spinner: (@Composable () -> Unit)? = if (loading) ({ LoadingIndicator(modifier = Modifier.size(20.dp)) }) else null
                val offlineDot: (@Composable () -> Unit)? = if (!serverReady && !loading) ({ StatusDot(palette.red, box = 20.dp, dot = 12.dp) }) else null
                val statusTrailing = spinner ?: offlineDot
                val loadingText = stringResource(R.string.connecting)
                val offlineText = stringResource(R.string.server_unavailable)
                fun serverSummary(real: String) = if (serverReady) real else if (loading) loadingText else offlineText
                PreferenceRow(Lucide.Terminal, stringResource(R.string.cli), serverSummary(cliInfo?.activeVersion ?: "—"), trailing = statusTrailing, enabled = serverReady) { dialog = SettingsDialog.Cli }
                PreferenceRow(Lucide.Sparkles, stringResource(R.string.generation), serverSummary("${caps.models.firstOrNull { it.id == model }?.label ?: model} • $effort"), trailing = statusTrailing, enabled = serverReady) { dialog = SettingsDialog.Generation }
                PreferenceRow(Lucide.Shield, stringResource(R.string.permissions), serverSummary(permissionLabel(caps, permissionMode)), trailing = statusTrailing, enabled = serverReady) { dialog = SettingsDialog.Permissions }
                PreferenceRow(Lucide.Eye, stringResource(R.string.visibility), serverSummary(stringResource(R.string.visibility_summary)), trailing = statusTrailing, enabled = serverReady) { dialog = SettingsDialog.Visibility }
                PreferenceRow(Lucide.History, stringResource(R.string.reset_settings), stringResource(R.string.reset_settings_summary)) { dialog = SettingsDialog.Reset }
            }
        }
    }

    when (dialog) {
        SettingsDialog.Theme -> SelectDialog(
            title = stringResource(R.string.theme),
            options = THEME_MODES.map { it to themeLabel(it) },
            selected = themeMode,
            onSelect = onThemeMode,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Language -> ConfirmSelectDialog(
            title = stringResource(R.string.language),
            options = LANGUAGE_TAGS.map { it to languageLabel(it) },
            selected = language,
            onConfirm = { onLanguage(it); dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Accent -> AccentDialog(
            dynamic = dynamicColor,
            accentIndex = accentIndex,
            dynamicColor = dynamicAccent(themeMode),
            onConfirm = { idx ->
                if (idx == null) onDynamicColor(true)
                else { onDynamicColor(false); onAccent(idx) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Connections -> ConnectionsDialog(
            connections = connections,
            activeId = activeId,
            onSetActive = { id -> settings.activeConnectionId = id; activeId = id },
            onSave = { profile -> settings.upsertConnection(profile); connections = settings.connections; activeId = settings.activeConnection?.id },
            onDelete = { id -> settings.deleteConnection(id); connections = settings.connections; activeId = settings.activeConnection?.id },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Cli -> cliInfo?.let { info ->
            CliDialog(info = info, onChanged = { cliInfo = it }, onDismiss = { dialog = null })
        } ?: run { dialog = null }

        SettingsDialog.Generation -> GenerationDialog(
            caps = caps,
            model = model,
            effort = effort,
            streaming = streaming,
            onConfirm = { m, e, s ->
                model = m; effort = e; streaming = s
                scope.launch { SettingsApi.update(model = m, effort = e, streaming = s) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Permissions -> ConfirmSelectDialog(
            title = stringResource(R.string.permission_mode),
            options = caps.permissionModes.map { it.id to it.label },
            selected = permissionMode,
            onConfirm = { permissionMode = it; scope.launch { SettingsApi.update(permissionMode = it) }; dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Visibility -> VisibilityDialog(
            thinking = showThinking,
            toolUse = showToolUse,
            fileChange = showFileChange,
            compact = showCompact,
            working = showWorking,
            onConfirm = { th, tu, fc, cp, wk ->
                showThinking = th; showToolUse = tu; showFileChange = fc; showCompact = cp; showWorking = wk
                scope.launch { SettingsApi.update(showThinking = th, showToolUse = tu, showFileChange = fc, showCompact = cp, showWorking = wk) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Reset -> ConfirmDialog(
            title = stringResource(R.string.reset_settings),
            text = stringResource(R.string.reset_settings_confirm),
            confirmLabel = stringResource(R.string.reset_settings),
            onConfirm = {
                settings.resetDefaults()
                onThemeMode(settings.themeMode); onLanguage(settings.language)
                onDynamicColor(settings.dynamicColor); onAccent(settings.accentIndex)
                scope.launch {
                    SettingsApi.reset()?.let {
                        model = it.model; effort = it.effort; permissionMode = it.permissionMode; streaming = it.streaming
                        showThinking = it.showThinking; showToolUse = it.showToolUse
                        showFileChange = it.showFileChange; showCompact = it.showCompact; showWorking = it.showWorking
                    }
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

private enum class SettingsDialog { Theme, Language, Accent, Connections, Cli, Generation, Permissions, Visibility, Reset }

@Composable
private fun permissionLabel(caps: Capabilities, mode: String): String =
    caps.permissionModes.firstOrNull { it.id == mode }?.label ?: mode

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun AccentDot(color: Color) {
    Box(modifier = Modifier.size(24.dp).background(color, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentDialog(
    dynamic: Boolean,
    accentIndex: Int,
    dynamicColor: Color,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedDynamic by remember { mutableStateOf(dynamic) }
    var pickedIndex by remember { mutableStateOf(accentIndex) }
    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.accent),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(if (pickedDynamic) null else pickedIndex) }) { Text(stringResource(R.string.save)) }
        },
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 5,
        ) {
            Swatch(color = dynamicColor, selected = pickedDynamic, dynamic = true) { pickedDynamic = true }
            ACCENTS.forEachIndexed { index, (_, color) ->
                Swatch(color = color, selected = !pickedDynamic && index == pickedIndex, dynamic = false) {
                    pickedDynamic = false; pickedIndex = index
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, dynamic: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Lucide.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            dynamic -> Icon(Lucide.Wand, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ConnectionsDialog(
    connections: List<ConnectionProfile>,
    activeId: String?,
    onSetActive: (String) -> Unit,
    onSave: (ConnectionProfile) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ConnectionProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ConnectionProfile?>(null) }
    var scanned by remember { mutableStateOf<ConnectionProfile?>(null) }

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.connections),
        contentPadding = PaddingValues(0.dp),
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) } },
        titleTrailing = {
            IconButton(
                onClick = {
                    QrScanner.scan(context) { raw ->
                        raw?.let(::profileFromQrPayload)?.let { scanned = it }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Lucide.ScanQrCode, contentDescription = stringResource(R.string.scan_qr), modifier = Modifier.size(20.dp))
            }
        },
    ) {
        connections.forEach { c ->
            DialogSelectItem(
                label = c.name,
                subtitle = c.address,
                selected = c.id == activeId,
                onClick = { onSetActive(c.id) },
                trailing = {
                    IconButton(onClick = { editing = c }, modifier = Modifier.size(36.dp)) { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { deleting = c }, modifier = Modifier.size(36.dp)) { Icon(Lucide.Trash, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp)) }
                },
            )
        }
        DialogActionItem(stringResource(R.string.add_connection), Lucide.Plus) { adding = true }
    }

    if (adding) {
        ConnectionEditDialog(initial = null, onConfirm = { onSave(it); adding = false }, onDismiss = { adding = false })
    }
    editing?.let { c ->
        ConnectionEditDialog(initial = c, onConfirm = { onSave(it); editing = null }, onDismiss = { editing = null })
    }
    scanned?.let { c ->
        ConnectionEditDialog(
            initial = c,
            focusName = true,
            onConfirm = { onSave(it); onSetActive(it.id); scanned = null },
            onDismiss = { scanned = null },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_connection_confirm, c.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { onDelete(c.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ConnectionEditDialog(
    initial: ConnectionProfile?,
    onConfirm: (ConnectionProfile) -> Unit,
    onDismiss: () -> Unit,
    focusName: Boolean = false,
) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initial?.kind ?: "http") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "8723") }
    var directory by remember { mutableStateOf(initial?.directory ?: "") }
    var authKind by remember { mutableStateOf(initial?.authKind ?: "none") }
    var authToken by remember { mutableStateOf(initial?.authToken ?: "") }
    var authUser by remember { mutableStateOf(initial?.authUser ?: "") }
    var authPassword by remember { mutableStateOf(initial?.authPassword ?: "") }
    var authHeaderName by remember { mutableStateOf(initial?.authHeaderName ?: "") }
    var authHeaderValue by remember { mutableStateOf(initial?.authHeaderValue ?: "") }

    fun defaultPortFor(k: String) = if (k == "https") "443" else "8723"

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(if (initial == null) R.string.add_connection else R.string.edit_connection),
        titleTrailing = {
            IconButton(
                onClick = {
                    QrScanner.scan(context) { raw ->
                        val payload = raw?.let(QrConnectionPayload::parse) ?: return@scan
                        val parsed = parseHostInput(payload.url) ?: return@scan
                        kind = parsed.kind
                        host = parsed.host
                        port = if (parsed.kind == "https") "" else parsed.port
                        authKind = "bearer"
                        authToken = payload.token
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Lucide.ScanQrCode, contentDescription = stringResource(R.string.scan_qr), modifier = Modifier.size(20.dp))
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    var finalKind = kind
                    var finalHost = host.trim().trimEnd('/')
                    var finalPort = port.trim()
                    val parsed = parseHostInput(finalHost)
                    if (parsed != null) {
                        finalKind = parsed.kind
                        finalHost = parsed.host
                        finalPort = parsed.port
                    }
                    val portInt: Int? = if (finalKind == "https") null else (finalPort.toIntOrNull() ?: 8723)
                    onConfirm(
                        ConnectionProfile(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { finalHost },
                            kind = finalKind,
                            host = finalHost,
                            port = portInt,
                            authKind = authKind,
                            authToken = authToken.trim(),
                            authUser = authUser.trim(),
                            authPassword = authPassword,
                            authHeaderName = authHeaderName.trim(),
                            authHeaderValue = authHeaderValue.trim(),
                            directory = directory.trim(),
                        )
                    )
                },
                enabled = host.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        SelectField(
            label = stringResource(R.string.connection_kind),
            selected = kind,
            options = listOf("http" to "HTTP", "https" to "HTTPS"),
            onSelect = { newKind ->
                if (newKind != kind) {
                    port = defaultPortFor(newKind)
                    kind = newKind
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        val nameFocus = remember { FocusRequester() }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.connection_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus))
        LaunchedEffect(focusName) { if (focusName) nameFocus.requestFocus() }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { input ->
                val parsed = parseHostInput(input)
                if (parsed != null) {
                    host = parsed.host
                    port = parsed.port
                    kind = parsed.kind
                } else {
                    host = input
                }
            },
            label = { Text(stringResource(R.string.host)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (kind == "http") {
            OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        SelectField(
            label = stringResource(R.string.connection_auth),
            selected = authKind,
            options = listOf(
                "none" to stringResource(R.string.auth_none),
                "bearer" to stringResource(R.string.auth_bearer),
                "basic" to stringResource(R.string.auth_basic),
                "header" to stringResource(R.string.auth_header),
            ),
            onSelect = { authKind = it },
        )
        Spacer(Modifier.height(8.dp))
        when (authKind) {
            "bearer" -> SecretTextField(value = authToken, onValueChange = { authToken = it }, label = stringResource(R.string.connection_token), modifier = Modifier.fillMaxWidth())
            "basic" -> {
                OutlinedTextField(value = authUser, onValueChange = { authUser = it }, label = { Text(stringResource(R.string.auth_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecretTextField(value = authPassword, onValueChange = { authPassword = it }, label = stringResource(R.string.auth_password), modifier = Modifier.fillMaxWidth())
            }
            "header" -> {
                OutlinedTextField(value = authHeaderName, onValueChange = { authHeaderName = it }, label = { Text(stringResource(R.string.auth_header_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecretTextField(value = authHeaderValue, onValueChange = { authHeaderValue = it }, label = stringResource(R.string.auth_header_value), modifier = Modifier.fillMaxWidth())
            }
        }
        if (authKind != "none") Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = directory,
            onValueChange = { directory = it },
            label = { Text(stringResource(R.string.connection_directory)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GenerationDialog(
    caps: Capabilities,
    model: String,
    effort: String,
    streaming: Boolean,
    onConfirm: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var m by remember { mutableStateOf(model) }
    var e by remember { mutableStateOf(effort) }
    var s by remember { mutableStateOf(streaming) }
    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.generation),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(m, e, s) }) { Text(stringResource(R.string.save)) }
        },
    ) {
        SelectField(stringResource(R.string.model), m, caps.models.map { it.id to it.label }) { m = it }
        Spacer(Modifier.height(14.dp))
        SelectField(stringResource(R.string.effort), e, caps.effortLevels.map { it to it }) { e = it }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { s = !s }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.streaming), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.streaming_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s, onCheckedChange = { s = it })
        }
    }
}

@Composable
private fun VisibilityDialog(
    thinking: String,
    toolUse: String,
    fileChange: String,
    compact: String,
    working: String,
    onConfirm: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var th by remember { mutableStateOf(thinking) }
    var tu by remember { mutableStateOf(toolUse) }
    var fc by remember { mutableStateOf(fileChange) }
    var cp by remember { mutableStateOf(compact) }
    var wk by remember { mutableStateOf(working) }
    val three = listOf(
        "full" to stringResource(R.string.show_full),
        "label" to stringResource(R.string.show_label),
        "off" to stringResource(R.string.show_off),
    )
    val two = listOf(
        "full" to stringResource(R.string.show_full),
        "label" to stringResource(R.string.show_label),
    )
    val labelOff = listOf(
        "label" to stringResource(R.string.show_label),
        "off" to stringResource(R.string.show_off),
    )
    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.visibility),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(th, tu, fc, cp, wk) }) { Text(stringResource(R.string.save)) }
        },
    ) {
        SelectField(stringResource(R.string.thinking), th, three) { th = it }
        Spacer(Modifier.height(14.dp))
        SelectField(stringResource(R.string.tools), tu, three) { tu = it }
        Spacer(Modifier.height(14.dp))
        SelectField(stringResource(R.string.file_changes), fc, three) { fc = it }
        Spacer(Modifier.height(14.dp))
        SelectField(stringResource(R.string.compacted), cp, two) { cp = it }
        Spacer(Modifier.height(14.dp))
        SelectField(stringResource(R.string.working), wk, labelOff) { wk = it }
    }
}

@Composable
private fun CliDialog(
    info: CliApi.CliInfo,
    onChanged: (CliApi.CliInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(info.source) }
    var customPath by remember { mutableStateOf(info.customPath ?: "") }
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
    // Update targets the saved/active CLI, not the unsaved dropdown selection.
    val canUpdate = info.source != "bundled"

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.cli),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = {
                scope.launch {
                    CliApi.setSource(source, customPath.trim().ifBlank { null })?.let(onChanged)
                    onDismiss()
                }
            }) { Text(stringResource(R.string.save)) }
        },
    ) {
        SelectField(stringResource(R.string.cli_source), source, sourceOptions) { source = it }
        if (source == "custom") {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = customPath,
                onValueChange = { customPath = it },
                label = { Text(stringResource(R.string.cli_custom_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            info.activeVersion ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canUpdate) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        updating = true
                        CliApi.update()
                        CliApi.status()?.let(onChanged)
                        updating = false
                    }
                },
                enabled = !updating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (updating) stringResource(R.string.cli_updating) else stringResource(R.string.cli_update))
            }
        }
    }
}

private fun profileFromQrPayload(raw: String): ConnectionProfile? {
    val payload = QrConnectionPayload.parse(raw) ?: return null
    val parsed = parseHostInput(payload.url) ?: return null
    val port: Int? = if (parsed.kind == "https") null else parsed.port.toIntOrNull()
    return ConnectionProfile(
        id = UUID.randomUUID().toString(),
        name = "",
        kind = parsed.kind,
        host = parsed.host,
        port = port,
        authKind = "bearer",
        authToken = payload.token,
    )
}

private data class ParsedHost(val host: String, val port: String, val kind: String)

// Returns non-null only when the user pasted a URL with a scheme (http(s)/ws(s)); plain
// host text falls through to the normal onValueChange path.
private fun parseHostInput(input: String): ParsedHost? {
    val raw = input.trim()
    val sep = raw.indexOf("://")
    if (sep <= 0) return null
    val parsedKind = when (raw.substring(0, sep).lowercase()) {
        "https", "wss" -> "https"
        "http", "ws" -> "http"
        else -> return null
    }
    val secure = parsedKind == "https"
    val rest = raw.substring(sep + 3).substringBefore('/').substringBefore('?')
    val colon = rest.indexOf(':')
    val host: String
    val port: String
    if (colon >= 0) {
        host = rest.substring(0, colon)
        port = rest.substring(colon + 1).filter(Char::isDigit).ifEmpty { if (secure) "443" else "80" }
    } else {
        host = rest
        port = if (secure) "443" else "80"
    }
    return ParsedHost(host, port, parsedKind)
}
