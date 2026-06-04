package com.jahirtrap.cconnect.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Trash
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.chat.ChatViewModel
import com.jahirtrap.cconnect.chat.ConnectionState
import com.jahirtrap.cconnect.data.SharedEntry
import com.jahirtrap.cconnect.data.remote.SharedApi
import com.jahirtrap.cconnect.ui.AppBottomSheet
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.MarkdownText
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.EnvironmentSelectDialog
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.ListRow
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.theme.palette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(onClose: () -> Unit, onOpenPreview: (url: String, filename: String) -> Unit) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()

    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<SharedEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SharedEntry?>(null) }
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }
    var envMenu by remember { mutableStateOf(false) }
    val activeName = state.environments.firstOrNull { it.id == state.activeEnvironmentId }?.name

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val url = pendingSaveUrl
        if (uri != null && url != null) scope.launch { FileTransfer.saveTo(context, url, uri) }
        pendingSaveUrl = null
    }

    fun child(name: String) = if (path.isEmpty()) name else "$path/$name"

    fun reload() {
        scope.launch {
            loading = true
            val result = runCatching { SharedApi.list(path) }.getOrNull()
            failed = result == null
            entries = result ?: emptyList()
            loaded = true
            loading = false
            refreshing = false
        }
    }
    LaunchedEffect(state.activeEnvironmentId, path) { loaded = false; entries = emptyList(); reload() }
    LaunchedEffect(state.connection) { if (state.connection == ConnectionState.Connected) reload() }

    fun goUp() {
        if (path.isEmpty()) onClose() else path = path.substringBeforeLast('/', "")
    }
    BackHandler(onBack = ::goUp)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.files),
                subtitle = if (failed) stringResource(R.string.server_unavailable) else activeName,
                subtitleLeading = if (failed) ({ StatusDot(palette.red) }) else null,
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.back), onClick = ::goUp) {
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Breadcrumb(path = path, onNavigate = { path = it })
            PullToRefreshBox(
                isRefreshing = if (path.isEmpty()) loading else refreshing,
                onRefresh = { refreshing = true; reload() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    when {
                        entries.isNotEmpty() -> items(entries, key = { it.name }) { entry ->
                            EntryRow(
                                entry = entry,
                                onOpen = { if (entry.isDir) path = child(entry.name) },
                                onView = { onOpenPreview(SharedApi.downloadUrl(child(entry.name)), entry.name) },
                                onSave = { FileTransfer.enqueueToDownloads(context, SharedApi.downloadUrl(child(entry.name)), entry.name) },
                                onSaveAs = { pendingSaveUrl = SharedApi.downloadUrl(child(entry.name)); saveLauncher.launch(entry.name) },
                                onShare = {
                                    scope.launch {
                                        FileTransfer.shareIntent(context, SharedApi.downloadUrl(child(entry.name)), entry.name)?.let {
                                            context.startActivity(android.content.Intent.createChooser(it, null))
                                        }
                                    }
                                },
                                onDelete = { deleting = entry },
                            )
                        }
                        loaded -> item { EmptyState(stringResource(R.string.no_files), Modifier.fillParentMaxSize()) }
                    }
                }
            }
        }
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_file_confirm, entry.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { scope.launch { SharedApi.delete(child(entry.name)); deleting = null; reload() } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(44.dp)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.House,
            contentDescription = null,
            tint = if (segments.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onNavigate("") }
                .padding(6.dp)
                .size(20.dp),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var cumulative = ""
            segments.forEachIndexed { i, seg ->
                cumulative = if (cumulative.isEmpty()) seg else "$cumulative/$seg"
                val target = cumulative
                val isLast = i == segments.lastIndex
                Icon(
                    Lucide.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp).size(16.dp),
                )
                Text(
                    seg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigate(target) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: SharedEntry,
    onOpen: () -> Unit,
    onView: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val previewable = !entry.isDir && isPreviewable(entry.name)
    ListRow(
        icon = if (entry.isDir) Lucide.Folder else Lucide.File,
        title = entry.name,
        subtitle = if (!entry.isDir) "${formatSize(entry.size)} • ${formatDate(entry.modified)}" else null,
        onClick = {
            when {
                entry.isDir -> onOpen()
                previewable -> onView()
                else -> menu = true
            }
        },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Lucide.EllipsisVertical, contentDescription = null, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!entry.isDir) {
                        if (previewable) {
                            CompactDropdownItem(
                                text = stringResource(R.string.view),
                                leadingIcon = { Icon(Lucide.Eye, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onView() },
                            )
                        }
                        CompactDropdownItem(
                            text = stringResource(R.string.save),
                            leadingIcon = { Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { menu = false; onSave() },
                        )
                        CompactDropdownItem(
                            text = stringResource(R.string.save_as),
                            leadingIcon = { Icon(Lucide.Save, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { menu = false; onSaveAs() },
                        )
                        CompactDropdownItem(
                            text = stringResource(R.string.share),
                            leadingIcon = { Icon(Lucide.Share2, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { menu = false; onShare() },
                        )
                    }
                    CompactDropdownItem(
                        text = stringResource(R.string.delete),
                        leadingIcon = { Icon(Lucide.Trash, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

private fun formatDate(epochSeconds: Double): String =
    SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date((epochSeconds * 1000).toLong()))
