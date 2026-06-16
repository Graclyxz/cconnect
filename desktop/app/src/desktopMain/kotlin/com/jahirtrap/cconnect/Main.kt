package com.jahirtrap.cconnect

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.app_icon
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
import com.jahirtrap.cconnect.ui.LocalRefreshTick
import com.jahirtrap.cconnect.ui.theme.CConnectTheme
import com.jahirtrap.cconnect.ui.theme.ThemeMode
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.themeModeOf
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.security.Security
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
fun main() {
    ComposeFoundationFlags.isNewContextMenuEnabled = true
    // The JVM ships a stripped BC provider; swap it for the full BouncyCastle so sshj
    // has curve25519, ed25519, chacha20-poly1305, etc. Modern OpenSSH defaults need this.
    Security.removeProvider("BC")
    Security.insertProviderAt(BouncyCastleProvider(), 1)
    val systemLocale = Locale.getDefault()
    val settings = Settings()
    val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
    application {
        val windowState = rememberWindowState(
            placement = if (settings.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            size = DpSize((screen.width * 0.8f).dp, (screen.height * 0.85f).dp),
            position = WindowPosition(Alignment.Center),
        )
        LaunchedEffect(windowState.placement) {
            settings.windowMaximized = windowState.placement == WindowPlacement.Maximized
        }
        var refreshTick by remember { mutableIntStateOf(0) }
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "CConnect",
            icon = painterResource(Res.drawable.app_icon),
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.R) {
                    refreshTick++
                    true
                } else false
            },
        ) {
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) { Notifier.appInForeground = true }
                    override fun windowLostFocus(e: WindowEvent?) { Notifier.appInForeground = false }
                }
                window.addWindowFocusListener(listener)
                Notifier.init { window.toFront(); window.requestFocus() }
                onDispose { window.removeWindowFocusListener(listener) }
            }
            App(systemLocale, refreshTick, window)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(systemLocale: Locale, refreshTick: Int, window: ComposeWindow) {
    val settings = remember { Settings() }
    val focusManager = LocalFocusManager.current
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
    // Hoisted so the open/collapsed state survives navigating to settings and back.
    var sidebarExpanded by remember { mutableStateOf(settings.sidebarExpanded) }
    LaunchedEffect(sidebarExpanded) { settings.sidebarExpanded = sidebarExpanded }

    fun goBack() {
        when {
            previewFile != null -> previewFile = null
            showSettings -> { showSettings = false; settingsHighlight = null }
            showExplorer -> { showExplorer = false; explorerArchive = null }
            showClaude -> showClaude = false
            showMonitor -> showMonitor = false
            showTerminal -> {
                showTerminal = false
                if (terminalFromSettings) { terminalFromSettings = false; showSettings = true }
            }
            sidebarExpanded -> sidebarExpanded = false
        }
    }

    // Override the locale in-place so changing language recomposes instead of recreating the window.
    LaunchedEffect(language) {
        Locale.setDefault(if (language.isBlank()) systemLocale else Locale.forLanguageTag(language))
    }

    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(themeMode, systemDark) {
        val dark = when (themeModeOf(themeMode)) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        }
        WindowTitleBar.apply(window, dark)
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalRefreshTick provides refreshTick,
    ) {
        CConnectTheme(
            themeMode = themeModeOf(themeMode),
            dynamicColor = dynamicColor,
            accent = accentAt(accentIndex),
        ) {
            val frameBackground = MaterialTheme.colorScheme.background
            LaunchedEffect(frameBackground) {
                val awtColor = java.awt.Color(frameBackground.toArgb())
                window.background = awtColor
                window.contentPane.background = awtColor
            }
            key(language) {
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            var backDown = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val isBack = event.buttons.isBackPressed
                                if (isBack && !backDown) goBack()
                                backDown = isBack
                            }
                        }
                    }.pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                ) {
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
                        expanded = sidebarExpanded,
                        onExpandedChange = { sidebarExpanded = it },
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
}

private data class PreviewRequest(
    val url: String,
    val name: String,
    val onDelete: (() -> Unit)?,
)
