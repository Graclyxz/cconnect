package com.jahirtrap.cconect.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jahirtrap.cconect.R
import com.jahirtrap.cconect.data.PERMISSION_MODES
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenSettings: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.connect() }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("CConect", modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.new_session)) },
                    selected = false,
                    onClick = { vm.newSession(); scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.session_history)) },
                    selected = false,
                    onClick = {},
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onOpenSettings() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰") }
                    },
                    title = {
                        Column {
                            Text("CConect")
                            Text(statusLabel(state), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.newSession() }) { Text("+") }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatMessageItem(message)
                    }
                }
                Composer(
                    permissionMode = state.permissionMode,
                    streaming = state.streaming,
                    onPermissionMode = vm::setPermissionMode,
                    onSend = vm::sendPrompt,
                    onStop = vm::stop,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(state: ChatUiState): String = when (state.connection) {
    ConnectionState.Connecting -> stringResource(R.string.connecting)
    ConnectionState.Disconnected -> state.error ?: stringResource(R.string.disconnected)
    ConnectionState.Connected -> state.sessionId?.take(8) ?: ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    permissionMode: String,
    streaming: Boolean,
    onPermissionMode: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { menuOpen = true }) { Text(permissionMode) }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                PERMISSION_MODES.forEach { mode ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(mode) },
                        onClick = { onPermissionMode(mode); menuOpen = false },
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.type_message)) },
                modifier = Modifier.weight(1f),
            )
            if (streaming) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.stop)) }
            } else {
                TextButton(
                    onClick = { onSend(input); input = "" },
                    enabled = input.isNotBlank(),
                ) { Text(stringResource(R.string.send)) }
            }
        }
    }
}
