package com.jahirtrap.cconnect.terminal

import androidx.compose.foundation.background
import com.jahirtrap.cconnect.ui.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import com.jahirtrap.cconnect.ui.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.jahirtrap.cconnect.ui.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.CirclePlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.*
import com.jahirtrap.cconnect.data.SshProfile
import com.jahirtrap.cconnect.data.SshStore
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.InputField
import com.jahirtrap.cconnect.ui.ListRow
import com.jahirtrap.cconnect.ui.SecretTextField
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.theme.palette
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onClose: () -> Unit) {
    val store = remember { SshStore() }
    var profiles by remember { mutableStateOf(store.profiles) }
    var active by remember { mutableStateOf<SshProfile?>(null) }
    var editing by remember { mutableStateOf<SshProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SshProfile?>(null) }

    if (active != null) {
        TerminalSession(
            profile = active!!,
            onClose = { active = null },
            onOsDetected = { detected ->
                val current = active ?: return@TerminalSession
                if (current.os != detected) {
                    val updated = current.copy(os = detected)
                    store.upsert(updated)
                    profiles = store.profiles
                    active = updated
                }
            },
        )
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.ssh_hosts),
                navigationIcon = {
                    TooltipIconButton(label = stringResource(Res.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
                actions = {
                    TooltipIconButton(label = stringResource(Res.string.add_ssh_host), onClick = { adding = true }) {
                        Icon(Lucide.CirclePlus, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (profiles.isEmpty()) {
                EmptyState(stringResource(Res.string.no_hosts), Modifier.fillMaxSize())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profiles, key = { it.id }) { p ->
                        ListRow(
                            icon = iconForOs(p.os),
                            iconTint = colorForOs(p.os) ?: MaterialTheme.colorScheme.primary,
                            title = p.name.ifBlank { p.host },
                            subtitle = p.address,
                            onClick = { active = p },
                            trailing = {
                                IconButton(onClick = { editing = p }, modifier = Modifier.size(40.dp)) { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(22.dp)) }
                                IconButton(onClick = { deleting = p }, modifier = Modifier.size(40.dp)) { Icon(Lucide.Trash, contentDescription = stringResource(Res.string.delete), modifier = Modifier.size(22.dp)) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        SshEditDialog(
            initial = null,
            onConfirm = { p -> store.upsert(p); profiles = store.profiles; adding = false },
            onDismiss = { adding = false },
        )
    }
    editing?.let { p ->
        SshEditDialog(
            initial = p,
            onConfirm = { saved -> store.upsert(saved); profiles = store.profiles; editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(Res.string.delete),
            text = stringResource(Res.string.delete_ssh_host_confirm, p.name.ifBlank { p.host }),
            confirmLabel = stringResource(Res.string.delete),
            onConfirm = { store.delete(p.id); profiles = store.profiles; deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SshEditDialog(
    initial: SshProfile?,
    onConfirm: (SshProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(if (initial == null) Res.string.add_ssh_host else Res.string.edit_ssh_host),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = {
                    onConfirm(
                        SshProfile(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { host.trim() },
                            host = host.trim(),
                            port = port.trim().toIntOrNull() ?: 22,
                            user = user.trim(),
                            password = password,
                        )
                    )
                },
                enabled = host.isNotBlank() && user.isNotBlank(),
            ) { Text(stringResource(Res.string.save)) }
        },
    ) {
        InputField(value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.ssh_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        InputField(value = host, onValueChange = { host = it }, label = { Text(stringResource(Res.string.host)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        InputField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text(stringResource(Res.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        InputField(value = user, onValueChange = { user = it }, label = { Text(stringResource(Res.string.ssh_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        SecretTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.ssh_password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalSession(
    profile: SshProfile,
    onClose: () -> Unit,
    onOsDetected: (String) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val connection = remember { SshConnection(profile, scope, onOsDetected) }
    val state by connection.state.collectAsState(initial = SshConnection.State.Idle)

    val emulator: TerminalEmulator = remember {
        lateinit var em: TerminalEmulator
        em = TerminalEmulatorFactory.create(
            initialRows = 24,
            initialCols = 80,
            defaultForeground = Color.White,
            defaultBackground = Color.Black,
            onKeyboardInput = { bytes -> connection.send(bytes) },
            onResize = { dims -> connection.resize(dims.columns, dims.rows) },
        )
        em
    }

    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { connection.close() } }

    LaunchedEffect(Unit) { connection.connect() }

    LaunchedEffect(emulator) {
        connection.output.collect { chunk -> emulator.writeInput(chunk) }
    }

    Scaffold(
        topBar = {
            val sshLeading: (@Composable () -> Unit)? = when (state) {
                SshConnection.State.Idle, SshConnection.State.Connecting ->
                    ({ LoadingIndicator(modifier = Modifier.size(14.dp)) })
                SshConnection.State.Connected -> ({ StatusDot(palette.green) })
                else -> ({ StatusDot(palette.red) })
            }
            AppTopBar(
                title = profile.name.ifBlank { profile.host },
                subtitle = statusLabel(state),
                subtitleLeading = sshLeading,
                navigationIcon = {
                    TooltipIconButton(label = stringResource(Res.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
                actions = {
                    TooltipIconButton(
                        label = stringResource(Res.string.ssh_disconnect),
                        onClick = { connection.close(); onClose() },
                    ) { Icon(Lucide.X, contentDescription = null) }
                },
            )
        },
    ) { pad ->
        var touch by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            touch = event.changes.any { it.type == PointerType.Touch }
                        }
                    }
                },
        ) {
            Terminal(
                terminalEmulator = emulator,
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                backgroundColor = Color.Black,
                foregroundColor = Color.White,
                selectionBackgroundColor = MaterialTheme.colorScheme.primary,
                selectionForegroundColor = MaterialTheme.colorScheme.onPrimary,
                keyboardEnabled = true,
                showSoftKeyboard = true,
                focusRequester = focusRequester,
            )
            if (touch) {
                SoftKeyRow(
                    onKey = { connection.send(it); runCatching { focusRequester.requestFocus() } },
                    onShowKeyboard = { runCatching { focusRequester.requestFocus() } },
                    modifier = Modifier.imePadding(),
                )
            }
        }
    }
}

@Composable
private fun statusLabel(state: SshConnection.State): String = when (state) {
    SshConnection.State.Idle, SshConnection.State.Connecting -> stringResource(Res.string.ssh_connecting)
    SshConnection.State.Connected -> stringResource(Res.string.ssh_connected)
    SshConnection.State.Closed -> stringResource(Res.string.ssh_closed)
    is SshConnection.State.Failed -> stringResource(Res.string.connection_error)
}

@Composable
private fun SoftKeyRow(
    onKey: (ByteArray) -> Unit,
    onShowKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ESC = 0x1B; arrows = ESC + "[A/B/C/D"; Ctrl+letter = letter - 64.
            val keys = listOf(
                SoftKey.Lbl("Esc", byteArrayOf(0x1B)),
                SoftKey.Lbl("Tab", byteArrayOf(0x09)),
                SoftKey.Ico(Lucide.ChevronUp, "↑", byteArrayOf(0x1B, 0x5B, 0x41)),
                SoftKey.Ico(Lucide.ChevronDown, "↓", byteArrayOf(0x1B, 0x5B, 0x42)),
                SoftKey.Ico(Lucide.ChevronLeft, "←", byteArrayOf(0x1B, 0x5B, 0x44)),
                SoftKey.Ico(Lucide.ChevronRight, "→", byteArrayOf(0x1B, 0x5B, 0x43)),
                SoftKey.Ico(Lucide.Square, "Ctrl+C", byteArrayOf(0x03)),
                SoftKey.Ico(Lucide.LogOut, "Ctrl+D", byteArrayOf(0x04)),
                SoftKey.Ico(Lucide.Eraser, "Ctrl+L", byteArrayOf(0x0C)),
                SoftKey.Ico(Lucide.Pause, "Ctrl+Z", byteArrayOf(0x1A)),
                SoftKey.Lbl("Home", byteArrayOf(0x1B, 0x5B, 0x48)),
                SoftKey.Lbl("End", byteArrayOf(0x1B, 0x5B, 0x46)),
            )
            items(keys) { key ->
                SoftBtn(onClick = { onKey(key.bytes) }) {
                    when (key) {
                        is SoftKey.Lbl -> Text(key.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        is SoftKey.Ico -> Icon(key.icon, contentDescription = key.desc, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onShowKeyboard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private sealed class SoftKey(val bytes: ByteArray) {
    class Lbl(val label: String, bytes: ByteArray) : SoftKey(bytes)
    class Ico(val icon: androidx.compose.ui.graphics.vector.ImageVector, val desc: String, bytes: ByteArray) : SoftKey(bytes)
}

@Composable
private fun SoftBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
