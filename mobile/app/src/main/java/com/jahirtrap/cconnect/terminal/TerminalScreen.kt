package com.jahirtrap.cconnect.terminal

import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.SshProfile
import com.jahirtrap.cconnect.data.SshStore
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.SecretTextField
import org.connectbot.terminal.Terminal as TermlibTerminal
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SshStore(context) }
    var profiles by remember { mutableStateOf(store.profiles) }
    var active by remember { mutableStateOf<SshProfile?>(null) }
    var editing by remember { mutableStateOf<SshProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SshProfile?>(null) }

    BackHandler { if (active != null) active = null else onClose() }

    if (active != null) {
        TerminalSession(profile = active!!, onClose = { active = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.ssh_hosts), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Lucide.Plus, contentDescription = stringResource(R.string.add_ssh_host))
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (profiles.isEmpty()) {
                Text(
                    stringResource(R.string.no_ssh_hosts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profiles, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { active = p }
                                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Lucide.SquareTerminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name.ifBlank { p.host }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { editing = p }, modifier = Modifier.size(36.dp)) { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { deleting = p }, modifier = Modifier.size(36.dp)) { Icon(Lucide.Trash, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp)) }
                        }
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
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_ssh_host_confirm, p.name.ifBlank { p.host }),
            confirmLabel = stringResource(R.string.delete),
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
        title = stringResource(if (initial == null) R.string.add_ssh_host else R.string.edit_ssh_host),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ssh_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.host)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text(stringResource(R.string.ssh_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.height(8.dp))
        SecretTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.ssh_password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalSession(profile: SshProfile, onClose: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val connection = remember { SshConnection(profile, scope) }
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
    val view = LocalView.current
    fun showKeyboard() {
        runCatching { focusRequester.requestFocus() }
        val window = (view.context as? Activity)?.window ?: return
        WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.ime())
    }

    DisposableEffect(Unit) { onDispose { connection.close() } }

    LaunchedEffect(Unit) { connection.connect() }

    LaunchedEffect(emulator) {
        connection.output.collect { chunk -> emulator.writeInput(chunk) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(profile.name.ifBlank { profile.host }, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(statusLabel(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { connection.close(); onClose() }) {
                        Icon(Lucide.X, contentDescription = stringResource(R.string.ssh_disconnect))
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            TermlibTerminal(
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
            SoftKeyRow(
                onKey = { connection.send(it) },
                onShowKeyboard = ::showKeyboard,
                modifier = Modifier.imePadding(),
            )
        }
    }
}

private fun statusLabel(state: SshConnection.State): String = when (state) {
    SshConnection.State.Idle, SshConnection.State.Connecting -> "Connecting…"
    SshConnection.State.Connected -> "Connected"
    SshConnection.State.Closed -> "Disconnected"
    is SshConnection.State.Failed -> "Failed: ${state.message}"
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
            val items = listOf(
                "Esc" to byteArrayOf(0x1B),
                "Tab" to byteArrayOf(0x09),
                "↑" to byteArrayOf(0x1B, 0x5B, 0x41),
                "↓" to byteArrayOf(0x1B, 0x5B, 0x42),
                "←" to byteArrayOf(0x1B, 0x5B, 0x44),
                "→" to byteArrayOf(0x1B, 0x5B, 0x43),
                "^C" to byteArrayOf(0x03),
                "^D" to byteArrayOf(0x04),
                "^L" to byteArrayOf(0x0C),
                "^Z" to byteArrayOf(0x1A),
                "/" to byteArrayOf('/'.code.toByte()),
                "|" to byteArrayOf('|'.code.toByte()),
                "-" to byteArrayOf('-'.code.toByte()),
                "_" to byteArrayOf('_'.code.toByte()),
                "~" to byteArrayOf('~'.code.toByte()),
                "Home" to byteArrayOf(0x1B, 0x5B, 0x48),
                "End" to byteArrayOf(0x1B, 0x5B, 0x46),
            )
            items(items) { (label, value) ->
                SoftBtn(label) { onKey(value) }
            }
        }
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onShowKeyboard,
                ),
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

@Composable
private fun SoftBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
