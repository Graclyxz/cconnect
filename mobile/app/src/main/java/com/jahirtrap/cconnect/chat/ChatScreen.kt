package com.jahirtrap.cconnect.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.text.selection.SelectionContainer
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.MessagesSquare
import com.composables.icons.lucide.Paperclip
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Slash
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheckBig
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.ui.AbovePopupMenu
import com.jahirtrap.cconnect.ui.AppBottomSheet
import com.jahirtrap.cconnect.ui.AppLogo
import com.jahirtrap.cconnect.ui.AttachmentChip
import com.jahirtrap.cconnect.ui.NoticeCard
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CustomIcons
import com.jahirtrap.cconnect.ui.DropdownScrim
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.Claude
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.Stop
import com.jahirtrap.cconnect.ui.theme.palette
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.ChatMessage
import com.jahirtrap.cconnect.data.CommandOption
import com.jahirtrap.cconnect.data.EnvironmentProfile
import com.jahirtrap.cconnect.data.PermissionMode
import com.jahirtrap.cconnect.data.ProjectInfo
import com.jahirtrap.cconnect.data.Role
import com.jahirtrap.cconnect.data.pending
import com.jahirtrap.cconnect.data.SessionInfo
import com.jahirtrap.cconnect.data.TodoItem
import com.jahirtrap.cconnect.files.FileTransfer
import com.jahirtrap.cconnect.files.isPreviewable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.composables.icons.lucide.History
import com.jahirtrap.cconnect.data.remote.SessionsApi
import com.jahirtrap.cconnect.data.remote.SharedApi
import com.jahirtrap.cconnect.ui.ColorDialog
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.DialogSelectItem
import com.jahirtrap.cconnect.ui.EnvironmentSelectDialog
import com.jahirtrap.cconnect.ui.RenameDialog
import com.jahirtrap.cconnect.ui.SharedLinkActionsDialog
import com.jahirtrap.cconnect.ui.OutlinedPanel
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.theme.sessionColorOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: (highlight: String?) -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenClaude: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenPreview: (url: String, filename: String, onDelete: (() -> Unit)?) -> Unit,
    drawerState: DrawerState,
) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val expandedState = remember { mutableStateMapOf<Long, Boolean>() }

    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var colorTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var confirmCommand by remember { mutableStateOf<CommandOption?>(null) }
    var sharedLinkAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showRewindSheet by remember { mutableStateOf(false) }
    var pendingSaveAsUrl by remember { mutableStateOf<String?>(null) }
    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val url = pendingSaveAsUrl
        if (uri != null && url != null) scope.launch { FileTransfer.saveTo(context, url, uri) }
        pendingSaveAsUrl = null
    }
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            focusManager.clearFocus()
            vm.refreshEnvironments()
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

    // Hidden while following; otherwise shown once scrolled more than half the chat viewport above the bottom.
    val showScrollButton by remember {
        derivedStateOf {
            if (followBottom) return@derivedStateOf false
            val info = listState.layoutInfo
            val viewportH = info.viewportEndOffset - info.viewportStartOffset
            if (viewportH == 0) return@derivedStateOf false
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val belowFold = if (last.index < info.totalItemsCount - 1) viewportH
            else (last.offset + last.size) - info.viewportEndOffset
            belowFold > viewportH / 2
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
            if (!scrolling && isAtBottom) followBottom = true
        }
    }
    LaunchedEffect(followBottom) { vm.setFollowBottom(followBottom) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> if (idx < 10) vm.loadMoreHistory() }
    }
    LaunchedEffect(state.messages.lastOrNull()?.id, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty() && followBottom) {
            listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            Triple(last?.index, last?.size, info.viewportEndOffset)
        }.collect { (index, _, _) ->
            val lastIdx = listState.layoutInfo.totalItemsCount - 1
            if (followBottom && index != null && lastIdx >= 0 && index == lastIdx) {
                listState.scrollToItem(lastIdx, Int.MAX_VALUE)
            }
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        val last = state.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.role == Role.INTERACTION && last.interaction?.pending == true) {
            followBottom = true
            listState.animateScrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) focusManager.clearFocus()
    }

    MaterialExpressiveTheme(motionScheme = MotionScheme.standard()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EnvironmentSelector(
                            environments = state.environments,
                            activeId = state.activeEnvironmentId,
                            onSelect = vm::selectEnvironment,
                            modifier = Modifier.weight(1f),
                        )
                        TooltipIconButton(
                            label = stringResource(R.string.new_session),
                            onClick = { vm.newSession(); scope.launch { drawerState.close() } },
                        ) { Icon(Lucide.SquarePen, contentDescription = null) }
                    }
                    ProjectSelector(
                        projects = state.historyProjects,
                        selected = state.historyProjectKey,
                        onSelect = vm::selectHistoryProject,
                    )
                    val drawerListState = rememberLazyListState()
                    LaunchedEffect(state.historySessions.size) {
                        if (state.historySessions.isNotEmpty()) drawerListState.scrollToItem(0)
                    }
                    PullToRefreshBox(
                        isRefreshing = state.historyLoading,
                        onRefresh = { vm.loadHistory() },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        LazyColumn(
                            state = drawerListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                        ) {
                            when {
                                state.historySessions.isNotEmpty() -> items(state.historySessions, key = { it.sessionId }) { s ->
                                    ConversationRow(
                                        title = s.title ?: s.preview ?: s.sessionId.take(8),
                                        selected = s.sessionId == state.sessionId,
                                        onOpen = { vm.openSession(s); scope.launch { drawerState.close() } },
                                        onRename = { renameTarget = s },
                                        onAutoRename = { vm.autoRenameSession(s) },
                                        onColor = { colorTarget = s },
                                        onDelete = { deleteTarget = s },
                                    )
                                }

                                !state.historyLoading -> item { EmptyState(stringResource(R.string.no_chats), Modifier.fillParentMaxSize()) }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TooltipIconButton(label = stringResource(R.string.files), onClick = onOpenExplorer) {
                            Icon(Lucide.Folder, contentDescription = null)
                        }
                        TooltipIconButton(label = stringResource(R.string.claude), onClick = onOpenClaude) {
                            Icon(CustomIcons.Claude, contentDescription = null)
                        }
                        TooltipIconButton(label = stringResource(R.string.monitor), onClick = onOpenMonitor) {
                            Icon(Lucide.Activity, contentDescription = null)
                        }
                        TooltipIconButton(label = stringResource(R.string.terminal), onClick = onOpenTerminal) {
                            Icon(Lucide.SquareTerminal, contentDescription = null)
                        }
                        Spacer(Modifier.weight(1f))
                        TooltipIconButton(label = stringResource(R.string.settings), onClick = { onOpenSettings(null) }) {
                            Icon(Lucide.Settings, contentDescription = null)
                        }
                    }
                }
            },
        ) {
            MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) {
                Scaffold(
                    snackbarHost = {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.versionNotices.sortedBy { it.ordinal }.forEach { notice ->
                                key(notice) {
                                    NoticeCard(
                                        text = when (notice) {
                                            CompatStatus.AppOutdated -> stringResource(R.string.compat_app_outdated)
                                            CompatStatus.ServerOutdated -> stringResource(R.string.compat_server_outdated)
                                            CompatStatus.CliOutdated -> stringResource(R.string.compat_cli_outdated)
                                            else -> stringResource(R.string.update_available, state.latestRelease?.tag.orEmpty())
                                        },
                                        actionLabel = stringResource(R.string.settings),
                                        onAction = {
                                            vm.dismissNotice(notice)
                                            onOpenSettings(if (notice == CompatStatus.CliOutdated) "cli" else "about")
                                        },
                                        onDismiss = { vm.dismissNotice(notice) },
                                    )
                                }
                            }
                        }
                    },
                    topBar = {
                        val waitingUser = state.messages.any { it.role == Role.INTERACTION && it.interaction?.pending == true }
                        val statusLeading: (@Composable () -> Unit) = when {
                            state.connection == ConnectionState.Disconnected -> ({ StatusDot(palette.red) })
                            state.connection == ConnectionState.Connecting -> ({ LoadingIndicator(modifier = Modifier.size(14.dp)) })
                            waitingUser -> ({ StatusDot(palette.orange) })
                            state.streaming -> ({ LoadingIndicator(modifier = Modifier.size(14.dp)) })
                            else -> ({ StatusDot(palette.green) })
                        }
                        val statusText = when {
                            state.connection == ConnectionState.Disconnected -> stringResource(R.string.server_unavailable)
                            state.connection == ConnectionState.Connecting -> stringResource(R.string.connecting)
                            waitingUser -> stringResource(R.string.waiting_user)
                            state.streaming -> stringResource(R.string.working)
                            else -> state.sessionId?.take(8) ?: stringResource(R.string.new_chat)
                        }
                        AppTopBar(
                            title = stringResource(R.string.app_name),
                            subtitle = statusText,
                            subtitleLeading = statusLeading,
                            navigationIcon = {
                                TooltipIconButton(
                                    label = stringResource(R.string.menu),
                                    onClick = { scope.launch { drawerState.open() } },
                                ) { Icon(Lucide.Menu, contentDescription = null) }
                            },
                            actions = {
                                TaskIndicator(todos = state.todos)
                                if (state.sessionId != null && !state.streaming) {
                                    TooltipIconButton(
                                        label = stringResource(R.string.rewind),
                                        onClick = { vm.loadRewindPoints(); showRewindSheet = true },
                                    ) { Icon(Lucide.History, contentDescription = null) }
                                }
                                TooltipIconButton(
                                    label = stringResource(R.string.new_session),
                                    onClick = { vm.newSession() },
                                ) { Icon(Lucide.SquarePen, contentDescription = null) }
                            },
                        )
                    },
                ) { padding ->
                    val sc = state.sideChat?.takeIf { it.boundSessionId == state.sessionId }
                    val sideActive = state.sideChatOpen && sc != null
                    var mainDraft by rememberSaveable { mutableStateOf("") }
                    var sideDraft by rememberSaveable { mutableStateOf("") }
                    LaunchedEffect(state.pendingInput) {
                        state.pendingInput?.let { mainDraft = it; vm.consumePendingInput() }
                    }
                    val composerFocus = remember { FocusRequester() }
                    val expansion = remember { Animatable(0f) }
                    val peek = 0.58f
                    LaunchedEffect(sideActive) {
                        if (sideActive) {
                            expansion.snapTo(0f)
                            if (imeVisible) composerFocus.requestFocus()
                            expansion.animateTo(peek, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                    LaunchedEffect(expansion.value) {
                        if (sideActive && followBottom && state.messages.isNotEmpty()) {
                            listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
                        }
                    }
                    val dismissSide: () -> Unit = {
                        scope.launch {
                            expansion.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            vm.closeSideChat()
                        }
                        Unit
                    }
                    BackHandler(enabled = sideActive) {
                        if (imeVisible) focusManager.clearFocus() else dismissSide()
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                            val boxHeightPx = constraints.maxHeight.toFloat()
                            val toolbarReveal = (1f - expansion.value / peek).coerceIn(0f, 1f)
                            val panelH = expansion.value.coerceAtLeast(peek)
                            var toolbarHeightPx by remember { mutableStateOf(0) }
                            Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight((1f - panelH * (1f - toolbarReveal)).coerceAtMost(1f - toolbarHeightPx / boxHeightPx).coerceIn(0.0001f, 1f)).clipToBounds()) {
                                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        itemsIndexed(state.messages, key = { _, it -> it.id }) { index, message ->
                                            val running = when (message.role) {
                                                Role.TOOL -> message.toolUseId != null && message.toolUseId in state.pendingToolIds
                                                Role.THINKING -> index == state.messages.lastIndex && state.streaming
                                                else -> false
                                            }
                                            ChatMessageItem(
                                                message,
                                                prevRole = state.messages.getOrNull(index - 1)?.role,
                                                nextRole = state.messages.getOrNull(index + 1)?.role,
                                                running = running,
                                                expanded = expandedState[message.id] ?: false,
                                                onToggle = { expandedState[message.id] = !(expandedState[message.id] ?: false) },
                                                onAnswer = vm::answerInteraction,
                                                onToggleOption = vm::toggleQuestionOption,
                                                onQuestionText = vm::setQuestionFreeText,
                                                onQuestionNotes = vm::setQuestionNotes,
                                                onSubmitQuestions = vm::submitQuestions,
                                                onChatQuestions = vm::chatQuestions,
                                                onSharedLink = { url, filename -> sharedLinkAction = url to filename },
                                            )
                                        }
                                    }
                                }
                                var stickyHeaderHeight by remember { mutableStateOf(0) }
                                val sticky by remember(expandedState) {
                                    derivedStateOf {
                                        val info = listState.layoutInfo
                                        val start = info.viewportStartOffset
                                        val first = info.visibleItemsInfo.firstOrNull { it.offset + it.size > start }
                                            ?: return@derivedStateOf null
                                        val id = first.key as? Long ?: return@derivedStateOf null
                                        if (expandedState[id] != true) return@derivedStateOf null
                                        Triple(id, first.offset - start, first.offset + first.size - start)
                                    }
                                }
                                sticky?.let { (id, topRel, bottomRel) ->
                                    val idx = state.messages.indexOfFirst { it.id == id }
                                    val msg = state.messages.getOrNull(idx)
                                    if (msg != null && hasCollapsibleContent(msg)) {
                                        val gapPx = with(density) { gapAbove(state.messages.getOrNull(idx - 1)?.role, msg.role).roundToPx() }
                                        if (topRel + gapPx < 0) {
                                            val h = if (stickyHeaderHeight > 0) stickyHeaderHeight else with(density) { 40.dp.roundToPx() }
                                            val pushY = minOf(0, bottomRel - h)
                                            StickyCollapsibleHeader(
                                                message = msg,
                                                onCollapse = {
                                                    expandedState[id] = false
                                                    scope.launch { listState.scrollToItem(idx, gapPx) }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .fillMaxWidth()
                                                    .offset { IntOffset(0, pushY) }
                                                    .onSizeChanged { stickyHeaderHeight = it.height },
                                            )
                                        }
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
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .onSizeChanged { toolbarHeightPx = it.height }
                                    .background(MaterialTheme.colorScheme.background)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                            ) {
                                if (state.compacting) CompactProgress()
                                if (!sideActive && state.attachments.isNotEmpty()) {
                                    AttachmentsRow(
                                        attachments = state.attachments,
                                        uploading = state.uploadingAttachments,
                                        onRemove = vm::removeAttachment,
                                    )
                                }
                                ChatToolbar(
                                    ready = state.capabilitiesReady,
                                    disconnected = state.connection == ConnectionState.Disconnected,
                                    connecting = state.connection == ConnectionState.Connecting,
                                    model = state.model,
                                    models = state.capabilities.models,
                                    onModel = vm::setModel,
                                    effort = state.effort,
                                    effortLevels = state.capabilities.effortLevels,
                                    onEffort = vm::setEffort,
                                    permissionMode = state.permissionMode,
                                    permissionModes = state.capabilities.permissionModes,
                                    onPermissionMode = vm::setPermissionMode,
                                    onQuickChat = vm::openSideChat,
                                    quickChatActive = sc != null && sc.messages.isNotEmpty(),
                                )
                            }
                            if (sideActive && sc != null) {
                                val dragModifier = Modifier.pointerInput(boxHeightPx) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            scope.launch {
                                                val v = expansion.value
                                                when {
                                                    v < 0.32f -> { expansion.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)); vm.closeSideChat() }
                                                    v < (peek + 1f) / 2f -> expansion.animateTo(peek, spring(stiffness = Spring.StiffnessMediumLow))
                                                    else -> expansion.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                                                }
                                            }
                                        },
                                    ) { change, dy ->
                                        change.consume()
                                        if (boxHeightPx > 0f) {
                                            val target = (expansion.value - dy / boxHeightPx).coerceIn(0f, 1f)
                                            scope.launch { expansion.snapTo(target) }
                                        }
                                    }
                                }
                                val dockT = ((expansion.value - peek) / (1f - peek)).coerceIn(0f, 1f)
                                SidePanel(
                                    sideChat = sc,
                                    headerModifier = dragModifier,
                                    onClear = vm::clearSideChat,
                                    onAnswer = vm::answerInteraction,
                                    onToggleOption = vm::toggleQuestionOption,
                                    onQuestionText = vm::setQuestionFreeText,
                                    onQuestionNotes = vm::setQuestionNotes,
                                    onSubmitQuestions = vm::submitQuestions,
                                    onChatQuestions = vm::chatQuestions,
                                    onSharedLink = { url, filename -> sharedLinkAction = url to filename },
                                    topCorner = (20 * (1f - dockT)).dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .offset { IntOffset(0, (toolbarReveal * panelH * boxHeightPx).toInt()) }
                                        .fillMaxHeight(panelH),
                                )
                            }
                        }
                        val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                            vm.addAttachments(uris)
                        }
                        Composer(
                            value = if (sideActive) sideDraft else mainDraft,
                            onValueChange = { if (sideActive) sideDraft = it else mainDraft = it },
                            streaming = if (sideActive) (sc?.streaming ?: false) else state.streaming,
                            sessionColor = state.sessionColor,
                            commands = state.capabilities.commands,
                            onCommand = { cmd -> if (cmd.requireConfirmation) confirmCommand = cmd else vm.runCommand(cmd) },
                            onSend = { if (sideActive) vm.sendSideQuestion(it) else vm.submit(it) },
                            onStop = { if (sideActive) vm.stopSide() else vm.stop() },
                            canSend = state.connection == ConnectionState.Connected,
                            commandsEnabled = state.sessionId != null && state.connection == ConnectionState.Connected,
                            focusRequester = composerFocus,
                            onCloseSide = if (sideActive) dismissSide else null,
                            attachments = if (sideActive) emptyList() else state.attachments,
                            uploading = !sideActive && state.uploadingAttachments,
                            onAttach = if (sideActive) null else ({ attachLauncher.launch(arrayOf("*/*")) }),
                        )
                    }
                }
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
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_conversation_confirm, s.title ?: s.preview ?: s.sessionId),
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
    confirmCommand?.let { cmd ->
        ConfirmDialog(
            title = "/${cmd.name}",
            text = cmd.description,
            confirmLabel = stringResource(R.string.confirm),
            onConfirm = { vm.runCommand(cmd); confirmCommand = null },
            onDismiss = { confirmCommand = null },
        )
    }
    sharedLinkAction?.let { (url, filename) ->
        val viewable = isPreviewable(filename)
        SharedLinkActionsDialog(
            filename = filename,
            onView = if (viewable) ({
                onOpenPreview(url, filename, SharedApi.relativeFromUrl(url)?.let { rel ->
                    { scope.launch { SharedApi.delete(rel) } }
                })
            }) else null,
            onSave = { FileTransfer.enqueueToDownloads(context, url, filename) },
            onSaveAs = { pendingSaveAsUrl = url; saveAsLauncher.launch(filename) },
            onShare = {
                scope.launch {
                    FileTransfer.shareIntent(context, url, filename)?.let {
                        context.startActivity(Intent.createChooser(it, null))
                    }
                }
            },
            onDismiss = { sharedLinkAction = null },
        )
    }
    if (showRewindSheet) {
        RewindSheet(
            points = state.rewindPoints,
            loading = state.rewindLoading,
            onSelect = { showRewindSheet = false; vm.selectRewindPoint(it) },
            onDismiss = { showRewindSheet = false },
        )
    }
    state.rewindTarget?.let { target ->
        RewindDialog(
            message = target.text,
            preview = state.rewindPreview,
            busy = state.rewindBusy,
            onConfirm = { both -> vm.confirmRewind(both) },
            onDismiss = { vm.dismissRewind() },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RewindSheet(
    points: List<SessionsApi.RewindPoint>,
    loading: Boolean,
    onSelect: (SessionsApi.RewindPoint) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.rewind), showClose = true) {
        when {
            loading -> CenteredProgress(Modifier.fillMaxWidth().weight(1f))

            points.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.rewind_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(points.asReversed()) { point ->
                    OutlinedPanel(onClick = { onSelect(point) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = point.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RewindDialog(
    message: String,
    preview: SessionsApi.RewindPreview?,
    busy: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val codeAvailable = preview?.canRewind != false
    var both by remember { mutableStateOf(true) }
    LaunchedEffect(preview) { if (preview != null && !preview.canRewind) both = false }
    CompactDialog(
        onDismiss = { if (!busy) onDismiss() },
        title = stringResource(R.string.rewind),
        contentPadding = PaddingValues(0.dp),
        buttons = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(both) }, enabled = !busy) { Text(stringResource(R.string.accept)) }
        },
    ) {
        OutlinedPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (both) Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    preview == null -> {
                        LoadingIndicator(modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.loading), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    preview.canRewind -> Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = palette.green)) { append("+${preview.insertions}") }
                            append(" ")
                            withStyle(SpanStyle(color = palette.red)) { append("−${preview.deletions}") }
                            append(" • " + stringResource(R.string.rewind_files_count, preview.filesChanged.size))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> Text(
                        text = stringResource(R.string.rewind_no_checkpoint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DialogSelectItem(
            label = stringResource(R.string.rewind_both),
            selected = both,
            enabled = !busy && codeAvailable,
            onClick = { both = true },
        )
        DialogSelectItem(
            label = stringResource(R.string.rewind_conversation),
            selected = !both,
            enabled = !busy,
            onClick = { both = false },
        )
    }
}

@Composable
private fun SidePanel(
    sideChat: SideChatState,
    headerModifier: Modifier = Modifier,
    onClear: () -> Unit = {},
    onAnswer: ((String, String, String?) -> Unit)? = null,
    onToggleOption: ((String, Int, String) -> Unit)? = null,
    onQuestionText: ((String, Int, String) -> Unit)? = null,
    onQuestionNotes: ((String, Int, String) -> Unit)? = null,
    onSubmitQuestions: ((String) -> Unit)? = null,
    onChatQuestions: ((String) -> Unit)? = null,
    onSharedLink: ((String, String) -> Unit)? = null,
    topCorner: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().then(headerModifier)) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Lucide.MessagesSquare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.quick_chat),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Lucide.Eraser,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (sideChat.messages.isNotEmpty()) 1f else 0.38f,
                        ),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(enabled = sideChat.messages.isNotEmpty(), onClick = onClear),
                    )
                }
            }
            HorizontalDivider()
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                if (sideChat.messages.isNotEmpty()) listState.scrollToItem(sideChat.messages.lastIndex, Int.MAX_VALUE)
            }
            val isAtBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                    last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 4
                }
            }
            var followBottom by remember { mutableStateOf(true) }
            val showScrollButton by remember {
                derivedStateOf {
                    if (followBottom) return@derivedStateOf false
                    val info = listState.layoutInfo
                    val viewportH = info.viewportEndOffset - info.viewportStartOffset
                    if (viewportH == 0) return@derivedStateOf false
                    val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                    val belowFold = if (last.index < info.totalItemsCount - 1) viewportH
                    else (last.offset + last.size) - info.viewportEndOffset
                    belowFold > viewportH / 2
                }
            }
            LaunchedEffect(listState) {
                listState.interactionSource.interactions.collect { interaction ->
                    if (interaction is DragInteraction.Start) followBottom = false
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                    if (!scrolling && isAtBottom) followBottom = true
                }
            }
            val lastMsg = sideChat.messages.lastOrNull()
            LaunchedEffect(lastMsg?.id, lastMsg?.text) {
                if (sideChat.messages.isNotEmpty() && followBottom) {
                    listState.scrollToItem(sideChat.messages.lastIndex, Int.MAX_VALUE)
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    Triple(last?.index, last?.size, info.viewportEndOffset)
                }.collect { (index, _, _) ->
                    val lastIdx = listState.layoutInfo.totalItemsCount - 1
                    if (followBottom && index != null && lastIdx >= 0 && index == lastIdx) {
                        listState.scrollToItem(lastIdx, Int.MAX_VALUE)
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(sideChat.messages, key = { _, it -> it.id }) { index, message ->
                            ChatMessageItem(
                                message,
                                prevRole = sideChat.messages.getOrNull(index - 1)?.role,
                                nextRole = sideChat.messages.getOrNull(index + 1)?.role,
                                running = message.role == Role.WORKING && index == sideChat.messages.lastIndex && sideChat.streaming,
                                onAnswer = onAnswer,
                                onToggleOption = onToggleOption,
                                onQuestionText = onQuestionText,
                                onQuestionNotes = onQuestionNotes,
                                onSubmitQuestions = onSubmitQuestions,
                                onChatQuestions = onChatQuestions,
                                onSharedLink = onSharedLink,
                            )
                        }
                    }
                }
                if (showScrollButton && sideChat.messages.isNotEmpty()) {
                    Surface(
                        onClick = {
                            followBottom = true
                            scope.launch { listState.scrollToItem(sideChat.messages.lastIndex, Int.MAX_VALUE) }
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
        }
    }
}

private fun hasCollapsibleContent(m: ChatMessage): Boolean = when (m.role) {
    Role.THINKING, Role.TOOL_RESULT, Role.SUMMARY -> !m.labelOnly && m.text.isNotBlank()
    Role.TOOL -> m.text.isNotBlank() || !m.result.isNullOrBlank()
    Role.FILE_CHANGE -> !m.labelOnly && !m.diffLines.isNullOrEmpty()
    Role.COMPACT -> m.compact?.summary?.isNotBlank() == true
    else -> false
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
            Text(
                "${stringResource(R.string.tasks)} ($done/${todos.size})",
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
    ready: Boolean,
    disconnected: Boolean,
    connecting: Boolean,
    model: String,
    models: List<com.jahirtrap.cconnect.data.ModelOption>,
    onModel: (String) -> Unit,
    effort: String,
    effortLevels: List<String>,
    onEffort: (String) -> Unit,
    permissionMode: String,
    permissionModes: List<PermissionMode>,
    onPermissionMode: (String) -> Unit,
    onQuickChat: () -> Unit = {},
    quickChatActive: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val pstyle = permissionStyle(permissionMode)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(start = 8.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onQuickChat)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.MessagesSquare, contentDescription = stringResource(R.string.quick_chat), tint = accent, modifier = Modifier.size(18.dp))
            }
            if (quickChatActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (disconnected) {
                DisconnectedChip()
                return@Row
            }
            if (connecting) {
                ToolbarLoadingChip(stringResource(R.string.connecting))
                return@Row
            }
            if (!ready) {
                ToolbarLoadingChip()
                return@Row
            }
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
            val styles = permissionModes.associate { it.id to permissionStyle(it.id).let { s -> s.icon to s.color } }
            SelectorChip(
                label = permissionModes.firstOrNull { it.id == permissionMode }?.label ?: permissionMode,
                icon = pstyle.icon,
                tint = pstyle.color,
                options = permissionModes.map { it.id to it.label },
                selected = permissionMode,
                optionStyle = { styles[it] ?: (pstyle.icon to pstyle.color) },
                onSelect = onPermissionMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolbarLoadingChip(label: String = stringResource(R.string.loading)) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator(modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun DisconnectedChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(palette.red, box = 18.dp, dot = 10.dp)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.disconnected), style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
    BackHandler(enabled = open) { open = false }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (open) DropdownScrim { open = false }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = false),
        ) {
            options.forEach { (value, display) ->
                val style = optionStyle?.invoke(value)
                CompactDropdownItem(
                    text = display,
                    leadingIcon = style?.let { { Icon(it.first, contentDescription = null, tint = it.second, modifier = Modifier.size(20.dp)) } },
                    selected = value == selected,
                    onClick = { onSelect(value); open = false },
                )
            }
        }
    }
}

@Composable
private fun CompactProgress() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.compacting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.size(6.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommandMenuButton(
    commands: List<CommandOption>,
    streaming: Boolean,
    enabled: Boolean = true,
    onCommand: (CommandOption) -> Unit,
) {
    val ready = commands.isNotEmpty() && enabled && !streaming
    var open by remember { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(ready) {
                    detectTapGestures { if (ready) open = true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Slash,
                contentDescription = stringResource(R.string.commands),
                tint = if (ready) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
        AbovePopupMenu(expanded = open, onDismiss = { open = false }) {
            commands.forEach { cmd ->
                CommandMenuItem(cmd) { open = false; onCommand(cmd) }
            }
        }
    }
}

@Composable
private fun CommandMenuItem(cmd: CommandOption, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 112.dp, max = 280.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text("/${cmd.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        if (cmd.description.isNotBlank()) {
            Text(cmd.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    sessionColor: String?,
    commands: List<CommandOption>,
    onCommand: (CommandOption) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    showCommands: Boolean = true,
    canSend: Boolean = true,
    commandsEnabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onCloseSide: (() -> Unit)? = null,
    attachments: List<Attachment> = emptyList(),
    uploading: Boolean = false,
    onAttach: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val accent = sessionColorOf(sessionColor)
    val textStyle = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    var lineHeight by remember { mutableStateOf(with(density) { textStyle.lineHeight.toDp() }) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (onCloseSide != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onCloseSide),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
        } else if (showCommands) {
            CommandMenuButton(commands = commands, streaming = streaming, enabled = commandsEnabled, onCommand = onCommand)
            Spacer(Modifier.width(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 6,
            onTextLayout = { layout ->
                lineHeight = with(density) { (layout.getLineBottom(0) - layout.getLineTop(0)).toDp() }
            },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .border(if (accent != null) 2.dp else 1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant, shape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.type_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    if (onAttach != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                            Icon(
                                Lucide.Paperclip,
                                contentDescription = stringResource(R.string.attach_files),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = !uploading, onClick = onAttach)
                                    .size(22.dp)
                                    .padding(1.dp),
                            )
                        }
                    }
                }
            },
        )
        Spacer(Modifier.width(6.dp))
        if (streaming || uploading) {
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
                enabled = (value.isNotBlank() || attachments.isNotEmpty()) && canSend,
                onClick = { onSend(value); onValueChange("") },
            )
        }
    }
}

@Composable
private fun AttachmentsRow(
    attachments: List<Attachment>,
    uploading: Boolean,
    onRemove: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { attachment ->
            AttachmentChip(
                name = attachment.name,
                trailing = {
                    if (uploading) {
                        CircularProgressIndicator(
                            progress = { attachment.progress },
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            Lucide.X,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRemove(attachment.id) }
                                .size(16.dp),
                        )
                    }
                },
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
private fun EnvironmentSelector(
    environments: List<EnvironmentProfile>,
    activeId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = environments.firstOrNull { it.id == activeId } ?: environments.firstOrNull()
    if (active == null) {
        Row(modifier = modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppLogo()
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                active.name,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                active.address,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Lucide.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        EnvironmentSelectDialog(
            environments = environments,
            activeId = active.id,
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSelector(projects: List<ProjectInfo>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val label = if (selected == null) stringResource(R.string.all_projects)
    else projects.firstOrNull { it.projectKey == selected }?.let(::projectLabel) ?: selected
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .onSizeChanged { fieldWidth = with(density) { it.width.toDp() } }
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Lucide.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = fieldWidth)) {
            CompactDropdownItem(stringResource(R.string.all_projects), selected = selected == null) { onSelect(null); open = false }
            projects.forEach { p ->
                CompactDropdownItem(projectLabel(p), selected = selected == p.projectKey) { onSelect(p.projectKey); open = false }
            }
        }
    }
}

private fun projectLabel(p: ProjectInfo): String = p.name ?: p.path ?: p.projectKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    title: String,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onAutoRename: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
    selected: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else null,
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
