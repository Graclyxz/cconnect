package com.jahirtrap.cconnect.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheckBig
import com.composables.icons.lucide.SquarePen
import com.jahirtrap.cconnect.ui.CustomIcons
import com.jahirtrap.cconnect.ui.Stop
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.ConnectionProfile
import com.jahirtrap.cconnect.data.PermissionMode
import com.jahirtrap.cconnect.data.ProjectInfo
import com.jahirtrap.cconnect.data.SessionInfo
import com.jahirtrap.cconnect.data.TodoItem
import com.jahirtrap.cconnect.ui.ColorDialog
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.LANGUAGE_TAGS
import com.jahirtrap.cconnect.ui.RenameDialog
import com.jahirtrap.cconnect.ui.SelectDialog
import com.jahirtrap.cconnect.ui.THEME_MODES
import com.jahirtrap.cconnect.ui.languageLabel
import com.jahirtrap.cconnect.ui.themeIcon
import com.jahirtrap.cconnect.ui.themeLabel
import com.jahirtrap.cconnect.ui.theme.sessionColorOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    drawerState: DrawerState,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var colorTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            focusManager.clearFocus()
            vm.refreshConnections()
            vm.ensureHistoryLoaded()
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 4
        }
    }

    // Only manual scrolling changes this, so incoming content can't flip it before we react.
    var followBottom by remember { mutableStateOf(true) }

    // Hidden while following so streaming doesn't flicker it; shows only well above the bottom.
    val showScrollButton by remember {
        derivedStateOf {
            if (followBottom) return@derivedStateOf false
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
            val itemsBelow = info.totalItemsCount - 1 - last.index
            itemsBelow >= 2 || (last.offset + last.size) - info.viewportEndOffset > viewportHeight
        }
    }

    LaunchedEffect(Unit) { vm.connect() }

    // A user drag stops the follow immediately so streaming can't fight the gesture.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followBottom = false
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) followBottom = isAtBottom
        }
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty() && followBottom) {
            listState.animateScrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            if (followBottom && state.messages.isNotEmpty()) {
                listState.animateScrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
            }
        } else {
            focusManager.clearFocus()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.newSession(); scope.launch { drawerState.close() } }) {
                        Icon(Lucide.SquarePen, contentDescription = stringResource(R.string.new_session))
                    }
                }
                EnvironmentSelector(
                    connections = state.connections,
                    activeId = state.activeConnectionId,
                    onSelect = vm::selectConnection,
                )
                ProjectSelector(
                    projects = state.historyProjects,
                    selected = state.historyProjectKey,
                    onSelect = vm::selectHistoryProject,
                )
                HorizontalDivider()
                PullToRefreshBox(
                    isRefreshing = state.historyLoading,
                    onRefresh = { vm.loadHistory() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.historySessions, key = { it.sessionId }) { s ->
                            ConversationRow(
                                title = s.title ?: s.preview ?: s.sessionId.take(8),
                                onOpen = { vm.openSession(s); scope.launch { drawerState.close() } },
                                onRename = { renameTarget = s },
                                onAutoRename = { vm.autoRenameSession(s) },
                                onColor = { colorTarget = s },
                                onDelete = { deleteTarget = s },
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Lucide.Languages, contentDescription = stringResource(R.string.language))
                    }
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(themeIcon(themeMode), contentDescription = stringResource(R.string.theme))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Lucide.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Lucide.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            Text(statusLabel(state), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        TaskIndicator(todos = state.todos)
                        IconButton(onClick = { vm.newSession() }) {
                            Icon(Lucide.SquarePen, contentDescription = stringResource(R.string.new_session))
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(state.messages, key = { _, it -> it.id }) { index, message ->
                            ChatMessageItem(
                                message,
                                prevRole = state.messages.getOrNull(index - 1)?.role,
                                nextRole = state.messages.getOrNull(index + 1)?.role,
                            )
                        }
                    }
                    if (showScrollButton && state.messages.isNotEmpty()) {
                        Surface(
                            onClick = {
                                followBottom = true
                                scope.launch { listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE) }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onBackground,
                            shadowElevation = 4.dp,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(40.dp),
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Lucide.ChevronDown,
                                    contentDescription = stringResource(R.string.scroll_to_bottom),
                                    tint = MaterialTheme.colorScheme.background,
                                )
                            }
                        }
                    }
                }
                ChatToolbar(
                    model = state.model,
                    models = state.capabilities.models,
                    onModel = vm::setModel,
                    effort = state.effort,
                    effortLevels = state.capabilities.effortLevels,
                    onEffort = vm::setEffort,
                    permissionMode = state.permissionMode,
                    permissionModes = state.capabilities.permissionModes,
                    onPermissionMode = vm::setPermissionMode,
                )
                Composer(
                    streaming = state.streaming,
                    sessionColor = state.sessionColor,
                    onSend = vm::sendPrompt,
                    onStop = vm::stop,
                )
            }
        }
    }

    renameTarget?.let { s ->
        RenameDialog(
            initial = s.title ?: s.preview ?: "",
            onConfirm = { vm.renameSession(s, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.delete_conversation),
            text = s.title ?: s.preview ?: s.sessionId,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.deleteSession(s); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
    colorTarget?.let { s ->
        ColorDialog(
            colors = state.capabilities.colors,
            selected = s.color,
            onSelect = { vm.setSessionColor(s, it) },
            onDismiss = { colorTarget = null },
        )
    }
    if (showThemeDialog) {
        SelectDialog(
            title = stringResource(R.string.theme),
            options = THEME_MODES.map { it to themeLabel(it) },
            selected = themeMode,
            onSelect = onThemeMode,
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showLanguageDialog) {
        SelectDialog(
            title = stringResource(R.string.language),
            options = LANGUAGE_TAGS.map { it to languageLabel(it) },
            selected = language,
            onSelect = onLanguage,
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun statusLabel(state: ChatUiState): String = when (state.connection) {
    ConnectionState.Connecting -> stringResource(R.string.connecting)
    ConnectionState.Disconnected -> state.error ?: stringResource(R.string.disconnected)
    ConnectionState.Connected -> state.sessionId?.take(8) ?: stringResource(R.string.new_chat)
}

@Composable
private fun TaskIndicator(todos: List<TodoItem>) {
    if (todos.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val done = todos.count { it.status == "completed" }
    val inProgress = todos.count { it.status == "in_progress" }
    Box {
        IconButton(onClick = { open = true }) {
            TaskPie(done = done, inProgress = inProgress, total = todos.size)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                todos.forEach { TaskRow(it) }
            }
        }
    }
}

@Composable
private fun TaskPie(done: Int, inProgress: Int, total: Int) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val doneColor = MaterialTheme.colorScheme.primary
    val runningColor = MaterialTheme.colorScheme.onSurfaceVariant
    val doneSweep = if (total > 0) 360f * done / total else 0f
    val runSweep = if (total > 0) 360f * inProgress / total else 0f
    val allDone = total > 0 && done == total
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawCircle(color = track)
            drawArc(color = doneColor, startAngle = -90f, sweepAngle = doneSweep, useCenter = true)
            drawArc(color = runningColor, startAngle = -90f + doneSweep, sweepAngle = runSweep, useCenter = true)
        }
        if (allDone) {
            Icon(
                Lucide.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TaskRow(todo: TodoItem) {
    val completed = todo.status == "completed"
    val inProgress = todo.status == "in_progress"
    val icon = when {
        completed -> Lucide.SquareCheckBig
        inProgress -> Lucide.CircleDot
        else -> Lucide.Square
    }
    val tint = if (completed || inProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val text = if (inProgress && todo.activeForm.isNotBlank()) todo.activeForm else todo.content
    CompactDropdownItem(
        text = text,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
        color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (completed) TextDecoration.LineThrough else null,
        fontWeight = if (inProgress) FontWeight.SemiBold else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatToolbar(
    model: String,
    models: List<com.jahirtrap.cconnect.data.ModelOption>,
    onModel: (String) -> Unit,
    effort: String,
    effortLevels: List<String>,
    onEffort: (String) -> Unit,
    permissionMode: String,
    permissionModes: List<PermissionMode>,
    onPermissionMode: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val pstyle = permissionStyle(permissionMode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SelectorChip(
            label = models.firstOrNull { it.id == model }?.label ?: model,
            icon = Lucide.Sparkles,
            tint = accent,
            options = models.map { it.id to it.label },
            selected = model,
            onSelect = onModel,
        )
        SelectorChip(
            label = effort,
            icon = Lucide.Gauge,
            tint = accent,
            options = effortLevels.map { it to it },
            selected = effort,
            onSelect = onEffort,
        )
        SelectorChip(
            label = permissionModes.firstOrNull { it.id == permissionMode }?.label ?: permissionMode,
            icon = pstyle.icon,
            tint = pstyle.color,
            options = permissionModes.map { it.id to it.label },
            selected = permissionMode,
            optionStyle = { permissionStyle(it).let { s -> s.icon to s.color } },
            onSelect = onPermissionMode,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    options: List<Pair<String, String>>,
    selected: String,
    optionStyle: ((String) -> Pair<ImageVector, Color>)? = null,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Guard against the dismiss-then-reopen race when re-tapping the chip while open.
    var lastChange by remember { mutableStateOf(0L) }
    fun setOpen(value: Boolean) {
        open = value
        lastChange = System.currentTimeMillis()
    }
    BackHandler(enabled = open) { setOpen(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (System.currentTimeMillis() - lastChange > 200) setOpen(!open)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { setOpen(false) },
            properties = PopupProperties(focusable = false),
        ) {
            options.forEach { (value, display) ->
                val style = optionStyle?.invoke(value)
                CompactDropdownItem(
                    text = display,
                    leadingIcon = style?.let { { Icon(it.first, contentDescription = null, tint = it.second, modifier = Modifier.size(20.dp)) } },
                    selected = value == selected,
                    onClick = { onSelect(value); setOpen(false) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    streaming: Boolean,
    sessionColor: String?,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(22.dp)
    val accent = sessionColorOf(sessionColor)

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 6,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .border(if (accent != null) 2.dp else 1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant, shape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    if (input.isEmpty()) {
                        Text(
                            stringResource(R.string.type_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.width(6.dp))
        if (streaming) {
            CircleActionButton(
                icon = CustomIcons.Stop,
                contentDescription = stringResource(R.string.stop),
                enabled = true,
                onClick = onStop,
            )
        } else {
            CircleActionButton(
                icon = Lucide.ArrowUp,
                contentDescription = stringResource(R.string.send),
                enabled = input.isNotBlank(),
                onClick = { onSend(input); input = "" },
            )
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(iconSize))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvironmentSelector(connections: List<ConnectionProfile>, activeId: String?, onSelect: (String) -> Unit) {
    if (connections.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val active = connections.firstOrNull { it.id == activeId } ?: connections.first()
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Server, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(active.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${active.host}:${active.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Lucide.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            connections.forEach { c ->
                CompactDropdownItem(c.name, selected = c.id == active.id) { onSelect(c.id); open = false }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSelector(projects: List<ProjectInfo>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (selected == null) stringResource(R.string.all_projects)
    else projects.firstOrNull { it.projectKey == selected }?.let(::projectLabel) ?: selected
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Lucide.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CompactDropdownItem(stringResource(R.string.all_projects), selected = selected == null) { onSelect(null); open = false }
            projects.forEach { p ->
                CompactDropdownItem(projectLabel(p), selected = selected == p.projectKey) { onSelect(p.projectKey); open = false }
            }
        }
    }
}

private fun projectLabel(p: ProjectInfo): String =
    p.path?.substringAfterLast('\\')?.substringAfterLast('/')?.ifBlank { null } ?: p.path ?: p.projectKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationRow(
    title: String,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onAutoRename: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Lucide.EllipsisVertical, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                CompactDropdownItem(stringResource(R.string.rename)) { menu = false; onRename() }
                CompactDropdownItem(stringResource(R.string.auto_rename)) { menu = false; onAutoRename() }
                CompactDropdownItem(stringResource(R.string.conversation_color)) { menu = false; onColor() }
                CompactDropdownItem(stringResource(R.string.delete)) { menu = false; onDelete() }
            }
        }
    }
}
