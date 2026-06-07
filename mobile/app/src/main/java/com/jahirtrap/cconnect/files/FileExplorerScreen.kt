package com.jahirtrap.cconnect.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ClipboardCopy
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.File
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.chat.ChatViewModel
import com.jahirtrap.cconnect.chat.ConnectionState
import com.jahirtrap.cconnect.data.SharedEntry
import com.jahirtrap.cconnect.data.remote.SharedApi
import com.jahirtrap.cconnect.ui.AbovePopupMenu
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.EnvironmentSelectDialog
import com.jahirtrap.cconnect.ui.ListRow
import com.jahirtrap.cconnect.ui.RenameDialog
import com.jahirtrap.cconnect.ui.SelectionDot
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.theme.palette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TransferOp(
    val move: Boolean,
    val paths: List<String>,
    val sourceDir: String,
    val folders: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(onClose: () -> Unit, onOpenPreview: (url: String, filename: String, onDelete: (() -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<SharedEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var marking by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<TransferOp?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SharedEntry?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }
    var envMenu by remember { mutableStateOf(false) }
    var barMenu by remember { mutableStateOf(false) }
    val activeName = state.environments.firstOrNull { it.id == state.activeEnvironmentId }?.name

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val url = pendingSaveUrl
        if (uri != null && url != null) scope.launch { FileTransfer.saveTo(context, url, uri) }
        pendingSaveUrl = null
    }
    var pendingUploads by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pendingUploads = uris
    }
    var pendingSaveAll by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val saveTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val files = pendingSaveAll
        if (tree != null && files.isNotEmpty()) scope.launch { FileTransfer.saveAllTo(context, files, tree) }
        pendingSaveAll = emptyList()
    }

    fun child(name: String) = if (path.isEmpty()) name else "$path/$name"

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    fun reload() {
        scope.launch {
            val result = runCatching { SharedApi.list(path) }.getOrNull()
            failed = result == null
            entries = result ?: emptyList()
            loaded = true
            refreshing = false
        }
    }
    LaunchedEffect(state.activeEnvironmentId, path) { exitSelection(); loaded = false; entries = emptyList(); reload() }
    LaunchedEffect(state.activeEnvironmentId) { transfer = null }
    LaunchedEffect(state.connection) { if (state.connection == ConnectionState.Connected) reload() }
    LaunchedEffect(path) {
        var seen = UploadManager.uploads.value.filter { it.status != UploadManager.Status.Uploading }.map { it.id }.toSet()
        UploadManager.uploads.collect { list ->
            val finished = list.filter { it.status != UploadManager.Status.Uploading }
            val fresh = finished.filter { it.id !in seen }
            seen = finished.map { it.id }.toSet()
            if (fresh.any { it.dir == path && it.status == UploadManager.Status.Done }) reload()
        }
    }

    fun goUp() {
        if (path.isEmpty()) onClose() else path = path.substringBeforeLast('/', "")
    }
    BackHandler(
        onBack = {
            when {
                selecting -> exitSelection()
                path.isEmpty() && transfer != null -> transfer = null
                else -> goUp()
            }
        },
    )

    val selectedEntries = entries.filter { it.name in selected }
    val single = if (selected.size == 1) selectedEntries.firstOrNull() else null
    val canShare = selectedEntries.isNotEmpty() && selectedEntries.none { it.isDir }
    val allSelected = entries.isNotEmpty() && selected.size == entries.size
    val currentTransfer = transfer
    val transferAllowed = currentTransfer != null &&
        currentTransfer.folders.none { path == it || path.startsWith("$it/") } &&
        (!currentTransfer.move || path != currentTransfer.sourceDir)

    var shownCount by remember { mutableStateOf(0) }
    var shownSingle by remember { mutableStateOf<SharedEntry?>(null) }
    var shownCanShare by remember { mutableStateOf(false) }
    var shownFiles by remember { mutableStateOf<List<SharedEntry>>(emptyList()) }
    var shownSelection by remember { mutableStateOf<List<SharedEntry>>(emptyList()) }
    var shownTransfer by remember { mutableStateOf<TransferOp?>(null) }
    if (selected.isNotEmpty()) {
        shownCount = selected.size
        shownSingle = single
        shownCanShare = canShare
        shownFiles = if (canShare) selectedEntries else emptyList()
        shownSelection = selectedEntries
    }
    if (currentTransfer != null) shownTransfer = currentTransfer
    var suppressClick by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Crossfade(targetState = selecting, label = "files-topbar") { inSelection ->
                if (inSelection) {
                    AppTopBar(
                        title = shownCount.toString(),
                        navigationIcon = {
                            TooltipIconButton(
                                label = stringResource(R.string.select_all),
                                onClick = { selected = if (allSelected) emptySet() else entries.map { it.name }.toSet() },
                            ) { SelectionDot(allSelected) }
                        },
                        actions = {
                            TooltipIconButton(label = stringResource(R.string.cancel), onClick = ::exitSelection) {
                                Icon(Lucide.X, contentDescription = null)
                            }
                        },
                    )
                } else {
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
                            UploadIndicator()
                            TooltipIconButton(label = stringResource(R.string.environment), onClick = { envMenu = true }) {
                                Icon(Lucide.Server, contentDescription = null)
                            }
                            Box {
                                TooltipIconButton(label = stringResource(R.string.more_options), onClick = { barMenu = true }) {
                                    Icon(Lucide.EllipsisVertical, contentDescription = null)
                                }
                                DropdownMenu(expanded = barMenu, onDismissRequest = { barMenu = false }) {
                                    CompactDropdownItem(
                                        text = stringResource(R.string.new_folder),
                                        leadingIcon = { Icon(Lucide.FolderPlus, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                        onClick = { barMenu = false; creatingFolder = true },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
        bottomBar = {
            Box {
                Spacer(Modifier.navigationBarsPadding())
                AnimatedVisibility(
                    visible = currentTransfer != null,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    shownTransfer?.let { op ->
                        TransferBar(
                            move = op.move,
                            enabled = transferAllowed,
                            onCancel = { transfer = null },
                            onConfirm = {
                                scope.launch {
                                    if (op.move) SharedApi.move(op.paths, path)
                                    else SharedApi.copy(op.paths, path)
                                    transfer = null
                                    reload()
                                }
                            },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = currentTransfer == null && selecting && selected.isNotEmpty() && !marking,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    SelectionToolbar(
                        canShare = shownCanShare,
                    onMove = {
                        transfer = TransferOp(
                            move = true,
                            paths = selectedEntries.map { child(it.name) },
                            sourceDir = path,
                            folders = selectedEntries.filter { it.isDir }.map { child(it.name) },
                        )
                        exitSelection()
                    },
                    onCopy = {
                        transfer = TransferOp(
                            move = false,
                            paths = selectedEntries.map { child(it.name) },
                            sourceDir = path,
                            folders = selectedEntries.filter { it.isDir }.map { child(it.name) },
                        )
                        exitSelection()
                    },
                    onShare = {
                        scope.launch {
                            val files = selectedEntries.map { SharedApi.downloadUrl(child(it.name)) to it.name }
                            FileTransfer.shareMultipleIntent(context, files)?.let {
                                context.startActivity(Intent.createChooser(it, null))
                            }
                            exitSelection()
                        }
                    },
                        onDelete = { confirmingDelete = true },
                        onView = shownSingle?.takeIf { !it.isDir && isPreviewable(it.name) }?.let { entry ->
                            {
                                val rel = child(entry.name)
                                onOpenPreview(SharedApi.downloadUrl(rel), entry.name) { scope.launch { SharedApi.delete(rel); reload() } }
                                exitSelection()
                            }
                        },
                        onRename = shownSingle?.let { entry -> { renaming = entry } },
                        onSave = shownFiles.takeIf { it.isNotEmpty() }?.let { files ->
                            {
                                files.forEach { FileTransfer.enqueueToDownloads(context, SharedApi.downloadUrl(child(it.name)), it.name) }
                                exitSelection()
                            }
                        },
                        onSaveAs = shownFiles.takeIf { it.isNotEmpty() }?.let { files ->
                            {
                                if (files.size == 1) {
                                    pendingSaveUrl = SharedApi.downloadUrl(child(files.first().name))
                                    saveLauncher.launch(files.first().name)
                                } else {
                                    pendingSaveAll = files.map { SharedApi.downloadUrl(child(it.name)) to it.name }
                                    saveTreeLauncher.launch(null)
                                }
                                exitSelection()
                            }
                        },
                        onCopyPath = shownSelection.takeIf { it.isNotEmpty() }?.let { sel ->
                            {
                                scope.launch {
                                    SharedApi.absolutePaths(sel.map { child(it.name) })?.let { paths ->
                                        clipboard.setText(AnnotatedString(paths.joinToString("\n")))
                                    }
                                    exitSelection()
                                }
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !selecting && currentTransfer == null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Surface(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onBackground,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Lucide.Plus,
                            contentDescription = stringResource(R.string.upload_files),
                            tint = MaterialTheme.colorScheme.background,
                        )
                    }
                }
            }
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
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; reload() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                fun itemIndexAt(y: Float): Int? = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { y >= it.offset && y < it.offset + it.size }?.index
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var anchor = -1
                            var base = emptySet<String>()
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    if (transfer != null) return@detectDragGesturesAfterLongPress
                                    itemIndexAt(offset.y)?.let { idx ->
                                        anchor = idx
                                        base = if (selecting) selected else emptySet()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        marking = true
                                        selecting = true
                                        entries.getOrNull(idx)?.let {
                                            selected = base + it.name
                                            suppressClick = it.name
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (anchor < 0) return@detectDragGesturesAfterLongPress
                                    suppressClick = null
                                    itemIndexAt(change.position.y)?.let { idx ->
                                        val range = if (idx >= anchor) anchor..idx else idx..anchor
                                        selected = base + range.mapNotNull { entries.getOrNull(it)?.name }
                                    }
                                    val edge = 96.dp.toPx()
                                    when {
                                        change.position.y > size.height - edge -> scope.launch { listState.scrollBy(24.dp.toPx()) }
                                        change.position.y < edge -> scope.launch { listState.scrollBy(-24.dp.toPx()) }
                                    }
                                },
                                onDragEnd = { anchor = -1; marking = false },
                                onDragCancel = { anchor = -1; marking = false },
                            )
                        },
                ) {
                    when {
                        entries.isNotEmpty() -> items(entries, key = { it.name }) { entry ->
                            val isSelected = entry.name in selected
                            EntryRow(
                                entry = entry,
                                selecting = selecting,
                                selected = isSelected,
                                onClick = {
                                    when {
                                        suppressClick == entry.name -> suppressClick = null
                                        selecting -> selected = if (isSelected) selected - entry.name else selected + entry.name
                                        entry.isDir -> path = child(entry.name)
                                        isPreviewable(entry.name) -> {
                                            val rel = child(entry.name)
                                            onOpenPreview(SharedApi.downloadUrl(rel), entry.name) { scope.launch { SharedApi.delete(rel); reload() } }
                                        }
                                        currentTransfer == null -> { selecting = true; selected = setOf(entry.name) }
                                        else -> {}
                                    }
                                },
                            )
                        }
                        !loaded -> item { CenteredProgress(Modifier.fillParentMaxSize()) }
                        else -> item { EmptyState(stringResource(R.string.no_files), Modifier.fillParentMaxSize()) }
                    }
                }
            }
        }
    }

    if (pendingUploads.isNotEmpty()) {
        ConfirmDialog(
            title = stringResource(R.string.upload_files),
            text = pluralStringResource(R.plurals.upload_confirm, pendingUploads.size, pendingUploads.size),
            confirmLabel = stringResource(R.string.upload),
            onConfirm = {
                pendingUploads.forEach { UploadManager.enqueue(context, it, path) }
                pendingUploads = emptyList()
            },
            onDismiss = { pendingUploads = emptyList() },
        )
    }

    if (confirmingDelete && selectedEntries.isNotEmpty()) {
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = if (selectedEntries.size == 1) stringResource(R.string.delete_file_confirm, selectedEntries.first().name)
            else stringResource(R.string.delete_items_confirm, selectedEntries.size),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    selectedEntries.forEach { SharedApi.delete(child(it.name)) }
                    confirmingDelete = false
                    exitSelection()
                    reload()
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    renaming?.let { entry ->
        val extension = if (entry.isDir) "" else entry.name.substringAfterLast('.', "")
        val base = if (extension.isNotEmpty()) entry.name.dropLast(extension.length + 1) else entry.name
        val duplicateMessage = stringResource(R.string.already_exists)
        fun fullName(input: String) = input.trim().let { if (extension.isNotEmpty()) "$it.$extension" else it }
        RenameDialog(
            initial = base,
            suffix = if (extension.isNotEmpty()) ".$extension" else null,
            errorOf = { input ->
                val newName = fullName(input)
                if (newName != entry.name && entries.any { it.name.equals(newName, ignoreCase = true) }) duplicateMessage else null
            },
            onConfirm = { input ->
                scope.launch {
                    SharedApi.rename(child(entry.name), fullName(input))
                    renaming = null
                    exitSelection()
                    reload()
                }
            },
            onDismiss = { renaming = null },
        )
    }

    if (creatingFolder) {
        val duplicateMessage = stringResource(R.string.already_exists)
        RenameDialog(
            initial = "",
            title = stringResource(R.string.new_folder),
            confirmLabel = stringResource(R.string.create),
            errorOf = { input -> if (entries.any { it.name.equals(input.trim(), ignoreCase = true) }) duplicateMessage else null },
            onConfirm = { name ->
                scope.launch { SharedApi.mkdir(child(name.trim())); creatingFolder = false; reload() }
            },
            onDismiss = { creatingFolder = false },
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
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val detail = if (entry.isDir) pluralStringResource(R.plurals.item_count, entry.items, entry.items) else formatSize(entry.size)
    val slot by animateFloatAsState(if (selecting) 1f else 0f, label = "selection-slot")
    ListRow(
        icon = if (entry.isDir) Lucide.Folder else Lucide.File,
        title = entry.name,
        subtitle = formatDate(entry.modified),
        leading = if (slot > 0f) ({
            Box(modifier = Modifier.width(34.dp * slot), contentAlignment = Alignment.CenterStart) {
                SelectionDot(selected, Modifier.scale(slot))
            }
        }) else null,
        onClick = onClick,
        subtitleTrailing = {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            )
        },
    )
}

@Composable
private fun TransferBar(move: Boolean, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
            Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (move) R.string.move_here else R.string.copy_here))
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    canShare: Boolean,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onView: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onSaveAs: (() -> Unit)?,
    onCopyPath: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarAction(Lucide.FolderInput, stringResource(R.string.move), onClick = onMove, modifier = Modifier.weight(1f))
            ToolbarAction(Lucide.Copy, stringResource(R.string.copy), onClick = onCopy, modifier = Modifier.weight(1f))
            ToolbarAction(Lucide.Share2, stringResource(R.string.share), onClick = onShare, enabled = canShare, modifier = Modifier.weight(1f))
            ToolbarAction(Lucide.Trash, stringResource(R.string.delete), onClick = onDelete, modifier = Modifier.weight(1f))
            if (onView != null || onRename != null || onSave != null || onSaveAs != null || onCopyPath != null) {
                Box(modifier = Modifier.weight(1f)) {
                    ToolbarAction(
                        Lucide.EllipsisVertical,
                        stringResource(R.string.more),
                        onClick = { menu = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AbovePopupMenu(expanded = menu, onDismiss = { menu = false }) {
                        if (onView != null) {
                            CompactDropdownItem(
                                text = stringResource(R.string.view),
                                leadingIcon = { Icon(Lucide.Eye, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onView() },
                            )
                        }
                        if (onRename != null) {
                            CompactDropdownItem(
                                text = stringResource(R.string.rename),
                                leadingIcon = { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onRename() },
                            )
                        }
                        if (onSave != null) {
                            CompactDropdownItem(
                                text = stringResource(R.string.save),
                                leadingIcon = { Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onSave() },
                            )
                        }
                        if (onSaveAs != null) {
                            CompactDropdownItem(
                                text = stringResource(R.string.save_as),
                                leadingIcon = { Icon(Lucide.Save, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onSaveAs() },
                            )
                        }
                        if (onCopyPath != null) {
                            CompactDropdownItem(
                                text = stringResource(R.string.copy_path),
                                leadingIcon = { Icon(Lucide.ClipboardCopy, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onCopyPath() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun UploadIndicator() {
    val uploads by UploadManager.uploads.collectAsState()
    if (uploads.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val finished = uploads.count { it.status != UploadManager.Status.Uploading }
    val allDone = finished == uploads.size
    val progress = uploads.map { if (it.status == UploadManager.Status.Uploading) it.progress else 1f }.average().toFloat()
    Box {
        TooltipIconButton(label = stringResource(R.string.uploads), onClick = { open = true }) {
            UploadRing(progress = progress, complete = allDone)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = {
                open = false
                if (allDone) UploadManager.clearFinished()
            },
        ) {
            Text(
                "${stringResource(R.string.uploads)} ($finished/${uploads.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                uploads.forEach { UploadRow(it) }
            }
        }
    }
}

@Composable
private fun UploadRing(progress: Float, complete: Boolean) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val accent = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawCircle(color = track, radius = (size.minDimension - stroke.width) / 2, style = stroke)
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        if (complete) {
            Icon(Lucide.Check, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun UploadRow(upload: UploadManager.Upload) {
    CompactDropdownItem(
        text = upload.name,
        color = if (upload.status == UploadManager.Status.Done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        trailing = {
            when (upload.status) {
                UploadManager.Status.Uploading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { upload.progress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Lucide.X,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { UploadManager.cancel(upload.id) }
                            .size(20.dp),
                    )
                }
                UploadManager.Status.Done -> Icon(
                    Lucide.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                UploadManager.Status.Failed -> Icon(
                    Lucide.X,
                    contentDescription = null,
                    tint = palette.red,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

internal fun formatSize(bytes: Long): String {
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
