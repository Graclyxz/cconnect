package com.jahirtrap.cconect.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconect.R
import com.jahirtrap.cconect.chat.permissionStyle
import com.jahirtrap.cconect.data.Capabilities
import com.jahirtrap.cconect.data.Settings
import com.jahirtrap.cconect.data.remote.Backend
import com.jahirtrap.cconect.data.remote.CapabilitiesApi
import com.jahirtrap.cconect.ui.theme.ACCENTS

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
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var host by remember { mutableStateOf(settings.host) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var cwd by remember { mutableStateOf(settings.cwd) }
    var permissionMode by remember { mutableStateOf(settings.permissionMode) }
    var model by remember { mutableStateOf(settings.model) }
    var effort by remember { mutableStateOf(settings.effort) }
    var streaming by remember { mutableStateOf(settings.streaming) }
    var caps by remember { mutableStateOf(Capabilities()) }

    LaunchedEffect(Unit) {
        if (Backend.isConfigured) CapabilitiesApi.capabilities()?.let { caps = it }
    }

    onBack?.let { BackHandler(onBack = it) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = stringResource(R.string.back))
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.backend)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.host)) },
                    placeholder = { Text(stringResource(R.string.host_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    label = { Text(stringResource(R.string.project_path)) },
                    placeholder = { Text(stringResource(R.string.project_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.permissions)) {
                Text(stringResource(R.string.permission_mode), style = MaterialTheme.typography.bodyMedium)
                caps.permissionModes.forEach { mode ->
                    val s = permissionStyle(mode)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { permissionMode = mode },
                    ) {
                        RadioButton(selected = permissionMode == mode, onClick = { permissionMode = mode })
                        Icon(s.icon, contentDescription = null, tint = s.color, modifier = Modifier.size(20.dp))
                        Text("  ${s.label}")
                    }
                }
            }

            Section(stringResource(R.string.generation)) {
                Text(stringResource(R.string.model), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    caps.models.forEach { m ->
                        FilterChip(selected = model == m.id, onClick = { model = m.id }, label = { Text(m.label) })
                    }
                }
                Text(stringResource(R.string.effort), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    caps.effortLevels.forEach { e ->
                        FilterChip(selected = effort == e, onClick = { effort = e }, label = { Text(e) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.streaming), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.streaming_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = streaming, onCheckedChange = { streaming = it })
                }
            }

            Section(stringResource(R.string.appearance)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("system", stringResource(R.string.theme_system), themeMode, onThemeMode)
                    ThemeChip("light", stringResource(R.string.theme_light), themeMode, onThemeMode)
                    ThemeChip("dark", stringResource(R.string.theme_dark), themeMode, onThemeMode)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dynamic_color), modifier = Modifier.weight(1f))
                    Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                }
                if (!dynamicColor) {
                    Text(stringResource(R.string.accent), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ACCENTS.forEachIndexed { index, color ->
                            AccentDot(color = color, selected = index == accentIndex) { onAccent(index) }
                        }
                    }
                }
            }

            Section(stringResource(R.string.language)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("", stringResource(R.string.language_system), language, onLanguage)
                    ThemeChip("en", "English", language, onLanguage)
                    ThemeChip("es", "Español", language, onLanguage)
                }
            }

            Button(
                onClick = {
                    settings.host = host.trim()
                    settings.port = port.toIntOrNull() ?: 8723
                    settings.cwd = cwd.trim()
                    settings.permissionMode = permissionMode
                    settings.model = model
                    settings.effort = effort
                    settings.streaming = streaming
                    if (settings.isConfigured) onSaved()
                },
                enabled = host.isNotBlank() && cwd.isNotBlank(),
            ) {
                Text(stringResource(R.string.connect))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(2.dp, border, CircleShape)
            .padding(3.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
    )
}
