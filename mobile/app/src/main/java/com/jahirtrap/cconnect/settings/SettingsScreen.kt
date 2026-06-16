package com.jahirtrap.cconnect.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ScanQrCode
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.Wand
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.EnvironmentProfile
import com.jahirtrap.cconnect.data.QrEnvironmentPayload
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.data.AppUpdater
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.CapabilitiesApi
import com.jahirtrap.cconnect.data.remote.CliApi
import com.jahirtrap.cconnect.data.remote.SettingsApi
import com.jahirtrap.cconnect.data.remote.AppImageLoader
import com.jahirtrap.cconnect.data.remote.GitHubApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jahirtrap.cconnect.chat.ChatViewModel
import com.jahirtrap.cconnect.chat.ConnectionState
import com.jahirtrap.cconnect.claude.ClaudeChangelogSheet
import coil3.compose.AsyncImage
import com.jahirtrap.cconnect.BuildConfig
import com.composables.icons.lucide.Github
import com.jahirtrap.cconnect.ui.AppBottomSheet
import com.jahirtrap.cconnect.ui.AppLogo
import com.jahirtrap.cconnect.ui.Claude
import com.jahirtrap.cconnect.ui.CustomIcons
import com.jahirtrap.cconnect.ui.ActionButton
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.ColorSwatch
import com.jahirtrap.cconnect.ui.CompactSwitch
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.ConfirmSelectDialog
import com.jahirtrap.cconnect.ui.DialogSelectItem
import com.jahirtrap.cconnect.ui.SecretTextField
import com.jahirtrap.cconnect.ui.InputField
import com.jahirtrap.cconnect.ui.SelectDialog
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.MarkdownText
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

private const val KOFI_URL = "https://ko-fi.com/jahirtrap"

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
    highlight: String? = null,
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var environments by remember { mutableStateOf(settings.environments) }
    var activeId by remember { mutableStateOf(settings.activeEnvironment?.id) }
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
    var serverReady by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(Backend.isConfigured) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chatVm = viewModel<ChatViewModel>()
    val chatState by chatVm.state.collectAsState()

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
        chatVm.refreshVersionInfo()
    }

    LaunchedEffect(activeId, environments) { loadServerSettings() }
    LaunchedEffect(chatState.connection) {
        when (chatState.connection) {
            ConnectionState.Connected -> loadServerSettings()
            ConnectionState.Disconnected -> serverReady = false
            else -> {}
        }
    }

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var notifyTaskDone by remember { mutableStateOf(settings.notifyTaskDone) }
    var notifyInteraction by remember { mutableStateOf(settings.notifyInteraction) }
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var ignoringBattery by remember {
        mutableStateOf((context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName))
    }

    val scrollState = rememberScrollState()
    var aboutY by remember { mutableStateOf<Float?>(null) }
    var serverY by remember { mutableStateOf<Float?>(null) }
    val highlightFlash = remember { Animatable(0f) }
    val aboutFlashAlpha = if (highlight == "about") 0.1f * highlightFlash.value else 0f
    val cliFlashAlpha = if (highlight == "cli") 0.1f * highlightFlash.value else 0f
    LaunchedEffect(highlight) {
        val position = when (highlight) {
            "about" -> snapshotFlow { aboutY }
            "cli" -> snapshotFlow { serverY }
            else -> return@LaunchedEffect
        }
        scrollState.animateScrollTo(position.filterNotNull().first().toInt())
        repeat(2) {
            highlightFlash.animateTo(1f, tween(220))
            highlightFlash.animateTo(0f, tween(220))
        }
    }

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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
            ) {
                SettingsGroup(stringResource(R.string.settings_appearance)) {
                    PreferenceRow(themeIcon(themeMode), stringResource(R.string.theme), themeLabel(themeMode)) { dialog = SettingsDialog.Theme }
                    PreferenceRow(Lucide.Languages, stringResource(R.string.language), languageLabel(language)) { dialog = SettingsDialog.Language }
                    PreferenceRow(
                        icon = Lucide.Palette,
                        title = stringResource(R.string.accent),
                        summary = if (dynamicColor) stringResource(R.string.accent_dynamic)
                        else accentNameAt(accentIndex),
                        trailing = { AccentDot(if (dynamicColor) MaterialTheme.colorScheme.primary else accentAt(accentIndex)) },
                    ) { dialog = SettingsDialog.Accent }
                }
                SettingsGroup(stringResource(R.string.background_group)) {
                    val power = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                                ignoringBattery = power.isIgnoringBatteryOptimizations(context.packageName)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    val activeCount = listOf(notifyInteraction, notifyTaskDone).count { it }
                    PreferenceRow(
                        Lucide.Bell,
                        stringResource(R.string.notifications),
                        if (notificationsEnabled) stringResource(R.string.notifications_state, activeCount)
                        else stringResource(R.string.notifications_disabled),
                    ) { dialog = SettingsDialog.Notifications }
                    fun toggleBatteryOptimization() {
                        runCatching {
                            context.startActivity(
                                if (!ignoringBattery) Intent(
                                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                else Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    }
                    PreferenceRow(
                        Lucide.BatteryCharging,
                        stringResource(R.string.battery_optimization),
                        stringResource(R.string.battery_optimization_summary),
                        trailing = { CompactSwitch(ignoringBattery) { toggleBatteryOptimization() } },
                        onClick = ::toggleBatteryOptimization,
                    )
                }
                SettingsGroup(stringResource(R.string.settings_connectivity)) {
                    PreferenceRow(
                        Lucide.Server,
                        stringResource(R.string.environments),
                        environments.firstOrNull { it.id == activeId }?.let { "${it.name} • ${it.address}" }
                            ?: stringResource(R.string.no_environments),
                    ) { dialog = SettingsDialog.Environments }
                    PreferenceRow(
                        Lucide.SquareTerminal,
                        stringResource(R.string.ssh_hosts),
                        stringResource(R.string.ssh_hosts_summary),
                        trailing = {
                            Icon(
                                Lucide.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = onOpenSshHosts,
                    )
                }
                val loadingText = stringResource(R.string.connecting)
                val offlineText = stringResource(R.string.server_unavailable)
                fun serverSummary(real: String) = if (serverReady) real else if (loading) loadingText else offlineText
                Box(modifier = Modifier.onGloballyPositioned { serverY = it.positionInParent().y }) {
                SettingsGroup(
                    label = stringResource(R.string.settings_server),
                    labelTrailing = {
                        when {
                            loading -> LoadingIndicator(modifier = Modifier.size(20.dp))
                            serverReady -> StatusDot(palette.green, box = 20.dp, dot = 12.dp)
                            else -> StatusDot(palette.red, box = 20.dp, dot = 12.dp)
                        }
                    },
                ) {
                    var showCliChangelog by remember { mutableStateOf(false) }
                    if (showCliChangelog) ClaudeChangelogSheet(cliVersion = cliInfo?.activeVersion, onDismiss = { showCliChangelog = false })
                    PreferenceRow(
                        CustomIcons.Claude,
                        stringResource(R.string.cli),
                        serverSummary(cliInfo?.activeVersion ?: "—"),
                        enabled = serverReady,
                        alert = stringResource(R.string.compat_cli_outdated).takeIf { chatState.cliOutdated },
                        modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = cliFlashAlpha)),
                        trailing = {
                            TooltipIconButton(label = stringResource(R.string.changelog), onClick = { showCliChangelog = true }, enabled = serverReady) {
                                Icon(Lucide.FileText, contentDescription = null)
                            }
                        },
                    ) { dialog = SettingsDialog.Cli }
                    PreferenceRow(Lucide.Sparkles, stringResource(R.string.generation), serverSummary("${caps.models.firstOrNull { it.id == model }?.label ?: model} • $effort"), enabled = serverReady) { dialog = SettingsDialog.Generation }
                    PreferenceRow(Lucide.Shield, stringResource(R.string.permissions), serverSummary(permissionLabel(caps, permissionMode)), enabled = serverReady) { dialog = SettingsDialog.Permissions }
                    PreferenceRow(Lucide.Eye, stringResource(R.string.visibility), serverSummary(stringResource(R.string.visibility_summary)), enabled = serverReady) { dialog = SettingsDialog.Visibility }
                }
                }
                SettingsGroup(label = null) {
                    PreferenceRow(Lucide.History, stringResource(R.string.reset_settings), stringResource(R.string.reset_settings_summary)) { dialog = SettingsDialog.Reset }
                }
                Box(modifier = Modifier.onGloballyPositioned { aboutY = it.positionInParent().y }) {
                    SettingsGroup(stringResource(R.string.about)) {
                        val uriHandler = LocalUriHandler.current
                        var showChangelog by remember { mutableStateOf(false) }
                        if (showChangelog) ChangelogSheet(onDismiss = { showChangelog = false })
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(chatState.latestRelease?.url ?: GitHubApi.RELEASES_URL) }
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = aboutFlashAlpha))
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppLogo()
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (chatState.appOutdated) Text(
                                    stringResource(R.string.compat_app_outdated),
                                    style = MaterialTheme.typography.bodySmall, color = palette.red,
                                )
                                if (chatState.serverOutdated) Text(
                                    stringResource(R.string.compat_server_outdated),
                                    style = MaterialTheme.typography.bodySmall, color = palette.red,
                                )
                                chatState.latestRelease?.let { release ->
                                    Text(
                                        stringResource(R.string.update_available, release.tag),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (!chatState.appOutdated && !chatState.serverOutdated && chatState.latestRelease == null) Text(
                                    stringResource(R.string.up_to_date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TooltipIconButton(label = stringResource(R.string.changelog), onClick = { showChangelog = true }) {
                                Icon(Lucide.FileText, contentDescription = null)
                            }
                        }
                        val apkUrl = chatState.latestRelease?.apkUrl
                        if (apkUrl != null) {
                            var progress by remember { mutableStateOf<Float?>(null) }
                            var downloadJob by remember { mutableStateOf<Job?>(null) }
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress ?: 0f },
                                    drawStopIndicator = {},
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                                )
                            }
                            ActionButton(
                                text = stringResource(if (progress != null) R.string.cancel else R.string.update_action),
                                onClick = {
                                    if (progress != null) {
                                        downloadJob?.cancel()
                                    } else {
                                        progress = 0f
                                        downloadJob = scope.launch {
                                            try {
                                                AppUpdater.downloadAndInstall(context, apkUrl) { progress = it }
                                            } finally {
                                                progress = null
                                                downloadJob = null
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            )
                        } else {
                            var checking by remember { mutableStateOf(false) }
                            ActionButton(
                                text = stringResource(if (checking) R.string.checking_updates else R.string.check_updates),
                                enabled = !checking,
                                onClick = {
                                    scope.launch {
                                        checking = true
                                        chatVm.checkForUpdates()
                                        checking = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            )
                        }
                        var ownerProfile by remember { mutableStateOf<GitHubApi.Profile?>(null) }
                        var contributorProfile by remember { mutableStateOf<GitHubApi.Profile?>(null) }
                        LaunchedEffect(Unit) {
                            ownerProfile = GitHubApi.ownerProfile(context)
                            contributorProfile = GitHubApi.contributorProfile(context)
                        }
                        ProfileRow(ownerProfile, stringResource(R.string.creator)) { uriHandler.openUri(it) }
                        ProfileRow(contributorProfile, stringResource(R.string.contributor)) { uriHandler.openUri(it) }
                        PreferenceRow(
                            Lucide.Coffee,
                            stringResource(R.string.support_creator),
                            KOFI_URL.removePrefix("https://"),
                            trailing = { ExternalIndicator() },
                        ) { uriHandler.openUri(KOFI_URL) }
                        PreferenceRow(
                            Lucide.Github,
                            stringResource(R.string.repository),
                            GitHubApi.REPO_URL.removePrefix("https://"),
                            trailing = { ExternalIndicator() },
                        ) { uriHandler.openUri(GitHubApi.REPO_URL) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    when (dialog) {
        SettingsDialog.Notifications -> {
            val activity = LocalActivity.current
            val couldAsk = remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notificationsEnabled = granted
                val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                if (!granted && !canAskAgain && !couldAsk.value) runCatching {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
            CompactDialog(
                onDismiss = { dialog = null },
                title = stringResource(R.string.notifications),
                contentPadding = PaddingValues(0.dp),
                buttons = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } },
            ) {
                if (!notificationsEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            stringResource(R.string.notifications_disabled_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionButton(
                            text = stringResource(R.string.enable_notifications),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    couldAsk.value = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else runCatching {
                                    context.startActivity(
                                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.notify_interaction),
                    summary = stringResource(R.string.notify_interaction_summary),
                    checked = notifyInteraction,
                    enabled = notificationsEnabled,
                ) {
                    notifyInteraction = it
                    settings.notifyInteraction = it
                }
                SwitchRow(
                    title = stringResource(R.string.notify_task_done),
                    summary = stringResource(R.string.notify_task_done_summary),
                    checked = notifyTaskDone,
                    enabled = notificationsEnabled,
                ) {
                    notifyTaskDone = it
                    settings.notifyTaskDone = it
                }
            }
        }

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

        SettingsDialog.Environments -> EnvironmentsDialog(
            environments = environments,
            activeId = activeId,
            onSetActive = { id -> settings.activeEnvironmentId = id; activeId = id },
            onSave = { profile -> settings.upsertEnvironment(profile); environments = settings.environments; activeId = settings.activeEnvironment?.id },
            onDelete = { id -> settings.deleteEnvironment(id); environments = settings.environments; activeId = settings.activeEnvironment?.id },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Cli -> cliInfo?.let { info ->
            CliDialog(
                info = info,
                onChanged = { cliInfo = it; chatVm.refreshVersionInfo() },
                onDismiss = { dialog = null },
            )
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
            confirmLabel = stringResource(R.string.accept),
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

private enum class SettingsDialog { Theme, Language, Accent, Environments, Cli, Generation, Permissions, Visibility, Notifications, Reset }

@Composable
private fun permissionLabel(caps: Capabilities, mode: String): String =
    caps.permissionModes.firstOrNull { it.id == mode }?.label ?: mode

@Composable
fun SettingsGroup(
    label: String?,
    labelTrailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                labelTrailing?.invoke()
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content,
        )
    }
}

@Composable
fun PreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    alert: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (alert != null) {
                Text(alert, style = MaterialTheme.typography.bodySmall, color = palette.red.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(12.dp))
        CompactSwitch(checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun ProfileRow(profile: GitHubApi.Profile?, role: String, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = profile != null) { profile?.let { onOpen(it.url) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatar = profile?.avatarUrl
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                imageLoader = AppImageLoader.get(context),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
        } else {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile?.let { it.name ?: it.login } ?: "…", style = MaterialTheme.typography.bodyLarge)
            Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        ExternalIndicator()
    }
}

@Composable
private fun ExternalIndicator() {
    Icon(
        Lucide.ExternalLink,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AccentDot(color: Color) {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).background(color, CircleShape))
    }
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
            ColorSwatch(color = dynamicColor, selected = pickedDynamic, onClick = { pickedDynamic = true }, icon = Lucide.Wand)
            ACCENTS.forEachIndexed { index, (_, color) ->
                ColorSwatch(
                    color = color,
                    selected = !pickedDynamic && index == pickedIndex,
                    onClick = { pickedDynamic = false; pickedIndex = index },
                )
            }
        }
    }
}

@Composable
private fun EnvironmentsDialog(
    environments: List<EnvironmentProfile>,
    activeId: String?,
    onSetActive: (String) -> Unit,
    onSave: (EnvironmentProfile) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<EnvironmentProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EnvironmentProfile?>(null) }
    var scanned by remember { mutableStateOf<EnvironmentProfile?>(null) }

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.environments),
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
        environments.forEach { c ->
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
        Spacer(Modifier.height(8.dp))
        ActionButton(
            text = stringResource(R.string.add_environment),
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
    }

    if (adding) {
        EnvironmentEditDialog(initial = null, onConfirm = { onSave(it); adding = false }, onDismiss = { adding = false })
    }
    editing?.let { c ->
        EnvironmentEditDialog(initial = c, onConfirm = { onSave(it); editing = null }, onDismiss = { editing = null })
    }
    scanned?.let { c ->
        EnvironmentEditDialog(
            initial = c,
            focusName = true,
            onConfirm = { onSave(it); onSetActive(it.id); scanned = null },
            onDismiss = { scanned = null },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_environment_confirm, c.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { onDelete(c.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun EnvironmentEditDialog(
    initial: EnvironmentProfile?,
    onConfirm: (EnvironmentProfile) -> Unit,
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
        title = stringResource(if (initial == null) R.string.add_environment else R.string.edit_environment),
        titleTrailing = {
            IconButton(
                onClick = {
                    QrScanner.scan(context) { raw ->
                        val payload = raw?.let(QrEnvironmentPayload::parse) ?: return@scan
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
                        EnvironmentProfile(
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
            label = stringResource(R.string.environment_kind),
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
        InputField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.environment_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus))
        LaunchedEffect(focusName) { if (focusName) nameFocus.requestFocus() }
        Spacer(Modifier.height(8.dp))
        InputField(
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
            InputField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        SelectField(
            label = stringResource(R.string.environment_auth),
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
            "bearer" -> SecretTextField(value = authToken, onValueChange = { authToken = it }, label = stringResource(R.string.environment_token), modifier = Modifier.fillMaxWidth())
            "basic" -> {
                InputField(value = authUser, onValueChange = { authUser = it }, label = { Text(stringResource(R.string.auth_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecretTextField(value = authPassword, onValueChange = { authPassword = it }, label = stringResource(R.string.auth_password), modifier = Modifier.fillMaxWidth())
            }
            "header" -> {
                InputField(value = authHeaderName, onValueChange = { authHeaderName = it }, label = { Text(stringResource(R.string.auth_header_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecretTextField(value = authHeaderValue, onValueChange = { authHeaderValue = it }, label = stringResource(R.string.auth_header_value), modifier = Modifier.fillMaxWidth())
            }
        }
        if (authKind != "none") Spacer(Modifier.height(8.dp))
        InputField(
            value = directory,
            onValueChange = { directory = it },
            label = { Text(stringResource(R.string.environment_directory)) },
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
    val canUpdate = source != "bundled"

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
            InputField(
                value = customPath,
                onValueChange = { customPath = it },
                label = { Text(stringResource(R.string.cli_custom_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canUpdate) {
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = if (updating) stringResource(R.string.cli_updating) else stringResource(R.string.cli_update),
                onClick = {
                    scope.launch {
                        updating = true
                        CliApi.update(source, customPath.trim().ifBlank { null })
                        CliApi.status()?.let(onChanged)
                        updating = false
                    }
                },
                enabled = !updating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun profileFromQrPayload(raw: String): EnvironmentProfile? {
    val payload = QrEnvironmentPayload.parse(raw) ?: return null
    val parsed = parseHostInput(payload.url) ?: return null
    val port: Int? = if (parsed.kind == "https") null else parsed.port.toIntOrNull()
    return EnvironmentProfile(
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

@Composable
private fun ChangelogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<List<GitHubApi.ReleaseNotes>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = GitHubApi.releaseNotes(context)
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
