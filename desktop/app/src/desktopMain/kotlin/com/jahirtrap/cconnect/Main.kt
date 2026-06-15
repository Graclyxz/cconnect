package com.jahirtrap.cconnect

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.jahirtrap.cconnect.chat.ChatScreen
import com.jahirtrap.cconnect.claude.ClaudeScreen
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.files.FileExplorerScreen
import com.jahirtrap.cconnect.files.FilePreviewScreen
import com.jahirtrap.cconnect.monitor.MonitorScreen
import com.jahirtrap.cconnect.service.Notifier
import com.jahirtrap.cconnect.settings.SettingsScreen
import com.jahirtrap.cconnect.terminal.TerminalScreen
import com.jahirtrap.cconnect.ui.theme.CConnectTheme
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.themeModeOf
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.security.Security
import java.util.Locale

fun main() {
    // The JVM ships a stripped BC provider; swap it for the full BouncyCastle so sshj
    // has curve25519, ed25519, chacha20-poly1305, etc. Modern OpenSSH defaults need this.
    Security.removeProvider("BC")
    Security.insertProviderAt(BouncyCastleProvider(), 1)
    val systemLocale = Locale.getDefault()
    application {
        val windowState = rememberWindowState(size = DpSize(480.dp, 860.dp))
        Window(onCloseRequest = ::exitApplication, state = windowState, title = "CConnect") {
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) { Notifier.appInForeground = true }
                    override fun windowLostFocus(e: WindowEvent?) { Notifier.appInForeground = false }
                }
                window.addWindowFocusListener(listener)
                Notifier.init { window.toFront(); window.requestFocus() }
                onDispose { window.removeWindowFocusListener(listener) }
            }
            App(systemLocale)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(systemLocale: Locale) {
    val settings = remember { Settings() }
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
    var accentIndex by remember { mutableStateOf(settings.accentIndex) }
    var language by remember { mutableStateOf(settings.language) }
    var showSettings by remember { mutableStateOf(!settings.isConfigured) }
    var settingsHighlight by remember { mutableStateOf<String?>(null) }
    var showExplorer by remember { mutableStateOf(false) }
    var explorerArchive by remember { mutableStateOf<String?>(null) }
    var showClaude by remember { mutableStateOf(false) }
    var showMonitor by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<PreviewRequest?>(null) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalFromSettings by remember { mutableStateOf(false) }
    // Hoisted so the open/closed state survives navigating to settings and back.
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Override the locale in-place so changing language recomposes instead of recreating the window.
    LaunchedEffect(language) {
        Locale.setDefault(if (language.isBlank()) systemLocale else Locale.forLanguageTag(language))
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
        CConnectTheme(
            themeMode = themeModeOf(themeMode),
            dynamicColor = dynamicColor,
            accent = accentAt(accentIndex),
        ) {
            key(language) {
                when {
                    showSettings -> SettingsScreen(
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it; settings.themeMode = it },
                        dynamicColor = dynamicColor,
                        onDynamicColor = { dynamicColor = it; settings.dynamicColor = it },
                        accentIndex = accentIndex,
                        onAccent = { accentIndex = it; settings.accentIndex = it },
                        language = language,
                        onLanguage = { language = it; settings.language = it },
                        onOpenSshHosts = {
                            terminalFromSettings = true
                            showSettings = false
                            showTerminal = true
                        },
                        highlight = settingsHighlight,
                        onClose = { showSettings = false; settingsHighlight = null },
                    )

                    showExplorer -> FileExplorerScreen(
                        onClose = { showExplorer = false; explorerArchive = null },
                        onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) },
                        initialArchive = explorerArchive,
                    )

                    showClaude -> ClaudeScreen(
                        onClose = { showClaude = false },
                        onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) },
                    )

                    showMonitor -> MonitorScreen(onClose = { showMonitor = false })

                    showTerminal -> TerminalScreen(onClose = {
                        showTerminal = false
                        if (terminalFromSettings) {
                            terminalFromSettings = false
                            showSettings = true
                        }
                    })

                    else -> ChatScreen(
                        onOpenSettings = { target -> settingsHighlight = target; showSettings = true },
                        onOpenExplorer = { target -> explorerArchive = target; showExplorer = true },
                        onOpenClaude = { showClaude = true },
                        onOpenMonitor = { showMonitor = true },
                        onOpenTerminal = { showTerminal = true },
                        onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) },
                        drawerState = drawerState,
                    )
                }
                previewFile?.let { request ->
                    FilePreviewScreen(
                        url = request.url,
                        filename = request.name,
                        onClose = { previewFile = null },
                        onDelete = request.onDelete,
                    )
                }
            }
        }
    }
}

private data class PreviewRequest(
    val url: String,
    val name: String,
    val onDelete: (() -> Unit)?,
)
