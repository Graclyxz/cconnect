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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.Wand
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.ConnectionProfile
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.CapabilitiesApi
import com.jahirtrap.cconnect.ui.CompactDialog
import com.jahirtrap.cconnect.ui.ConfirmDialog
import com.jahirtrap.cconnect.ui.ConfirmSelectDialog
import com.jahirtrap.cconnect.ui.SelectDialog
import com.jahirtrap.cconnect.ui.SelectField
import com.jahirtrap.cconnect.ui.languageLabel
import com.jahirtrap.cconnect.ui.themeIcon
import com.jahirtrap.cconnect.ui.themeLabel
import com.jahirtrap.cconnect.ui.LANGUAGE_TAGS
import com.jahirtrap.cconnect.ui.THEME_MODES
import com.jahirtrap.cconnect.ui.theme.ACCENTS
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.accentNameAt
import com.jahirtrap.cconnect.ui.theme.dynamicAccent
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
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
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var connections by remember { mutableStateOf(settings.connections) }
    var activeId by remember { mutableStateOf(settings.activeConnection?.id) }
    var model by remember { mutableStateOf(settings.model) }
    var effort by remember { mutableStateOf(settings.effort) }
    var permissionMode by remember { mutableStateOf(settings.permissionMode) }
    var streaming by remember { mutableStateOf(settings.streaming) }
    var caps by remember { mutableStateOf(Capabilities()) }

    LaunchedEffect(activeId, connections) {
        if (Backend.isConfigured) CapabilitiesApi.capabilities()?.let { caps = it }
    }

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                connections.firstOrNull { it.id == activeId }?.let { "${it.name} • ${it.host}:${it.port}" }
                    ?: stringResource(R.string.no_connections),
            ) { dialog = SettingsDialog.Connections }
            PreferenceRow(Lucide.Sparkles, stringResource(R.string.generation), "${caps.models.firstOrNull { it.id == model }?.label ?: model} • $effort") { dialog = SettingsDialog.Generation }
            PreferenceRow(Lucide.Shield, stringResource(R.string.permissions), permissionLabel(caps, permissionMode)) { dialog = SettingsDialog.Permissions }
            PreferenceRow(Lucide.History, stringResource(R.string.reset_settings), stringResource(R.string.reset_settings_summary)) { dialog = SettingsDialog.Reset }
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

        SettingsDialog.Generation -> GenerationDialog(
            caps = caps,
            model = model,
            effort = effort,
            streaming = streaming,
            onConfirm = { m, e, s ->
                model = m; settings.model = m
                effort = e; settings.effort = e
                streaming = s; settings.streaming = s
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Permissions -> ConfirmSelectDialog(
            title = stringResource(R.string.permission_mode),
            options = caps.permissionModes.map { it.id to it.label },
            selected = permissionMode,
            onConfirm = { permissionMode = it; settings.permissionMode = it; dialog = null },
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
                model = settings.model; effort = settings.effort
                permissionMode = settings.permissionMode; streaming = settings.streaming
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

private enum class SettingsDialog { Theme, Language, Accent, Connections, Generation, Permissions, Reset }

@Composable
private fun permissionLabel(caps: Capabilities, mode: String): String =
    caps.permissionModes.firstOrNull { it.id == mode }?.label ?: mode

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    var editing by remember { mutableStateOf<ConnectionProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ConnectionProfile?>(null) }

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.connections),
        contentPadding = PaddingValues(0.dp),
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) } },
    ) {
        connections.forEach { c ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSetActive(c.id) }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (c.id == activeId) Icon(Lucide.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${c.host}:${c.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { editing = c }) { Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { deleting = c }) { Icon(Lucide.Trash, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(18.dp)) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { adding = true }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.add_connection), color = MaterialTheme.colorScheme.primary)
        }
    }

    if (adding) {
        ConnectionEditDialog(initial = null, onConfirm = { onSave(it); adding = false }, onDismiss = { adding = false })
    }
    editing?.let { c ->
        ConnectionEditDialog(initial = c, onConfirm = { onSave(it); editing = null }, onDismiss = { editing = null })
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
private fun ConnectionEditDialog(initial: ConnectionProfile?, onConfirm: (ConnectionProfile) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 8723).toString()) }
    var directory by remember { mutableStateOf(initial?.directory ?: "") }

    CompactDialog(
        onDismiss = onDismiss,
        title = stringResource(if (initial == null) R.string.add_connection else R.string.edit_connection),
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    onConfirm(
                        ConnectionProfile(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { host.trim() },
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 8723,
                            directory = directory.trim(),
                        )
                    )
                },
                enabled = host.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.connection_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.host)) }, placeholder = { Text(stringResource(R.string.host_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
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
