package com.jahirtrap.cconnect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.jahirtrap.cconnect.chat.ChatScreen
import com.jahirtrap.cconnect.claude.ClaudeScreen
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.files.FileExplorerScreen
import com.jahirtrap.cconnect.files.FilePreviewScreen
import com.jahirtrap.cconnect.monitor.MonitorScreen
import com.jahirtrap.cconnect.settings.SettingsScreen
import com.jahirtrap.cconnect.terminal.TerminalScreen
import com.jahirtrap.cconnect.ui.LocalRefreshTick
import com.jahirtrap.cconnect.ui.theme.CConnectTheme
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.themeModeOf
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val settings = remember { Settings() }
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
    var accentIndex by remember { mutableStateOf(settings.accentIndex) }
    var emojiStyle by remember { mutableStateOf(settings.emojiStyle) }
    var language by remember { mutableStateOf(settings.language) }
    var route by remember { mutableStateOf(if (settings.isConfigured) currentRoute() else "/settings") }
    var settingsHighlight by remember { mutableStateOf<String?>(null) }
    var explorerArchive by remember { mutableStateOf<String?>(null) }
    var previewFile by remember { mutableStateOf<PreviewRequest?>(null) }
    var sidebarExpanded by remember { mutableStateOf(settings.sidebarExpanded) }

    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { route = currentRoute() }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }

    fun navigate(target: String) {
        if (route == target) return
        window.history.pushState(null, "", target)
        route = target
    }

    fun goBack() {
        if (previewFile != null) previewFile = null else window.history.back()
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalRefreshTick provides 0,
    ) {
        CConnectTheme(
            themeMode = themeModeOf(themeMode),
            dynamicColor = dynamicColor,
            accent = accentAt(accentIndex),
            emojiStyle = emojiStyle,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (route) {
                    "/settings" -> SettingsScreen(
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it; settings.themeMode = it },
                        dynamicColor = dynamicColor,
                        onDynamicColor = { dynamicColor = it; settings.dynamicColor = it },
                        accentIndex = accentIndex,
                        onAccent = { accentIndex = it; settings.accentIndex = it },
                        emojiStyle = emojiStyle,
                        onEmojiStyle = { emojiStyle = it; settings.emojiStyle = it },
                        language = language,
                        onLanguage = { language = it; settings.language = it },
                        onOpenSshHosts = { navigate("/terminal") },
                        highlight = settingsHighlight,
                        onClose = { settingsHighlight = null; goBack() },
                    )

                    "/claude" -> ClaudeScreen(
                        onClose = { goBack() },
                        onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) },
                    )

                    "/monitor" -> MonitorScreen(onClose = { goBack() })

                    "/files" -> FileExplorerScreen(
                        onClose = { explorerArchive = null; goBack() },
                        onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) },
                        initialArchive = explorerArchive,
                    )

                    "/terminal" -> TerminalScreen(onClose = { goBack() })

                    else -> ChatScreen(
                        onOpenSettings = { target -> settingsHighlight = target; navigate("/settings") },
                        onOpenExplorer = { target -> explorerArchive = target; navigate("/files") },
                        onOpenClaude = { navigate("/claude") },
                        onOpenMonitor = { navigate("/monitor") },
                        onOpenTerminal = { navigate("/terminal") },
                        onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) },
                        expanded = sidebarExpanded,
                        onExpandedChange = { sidebarExpanded = it; settings.sidebarExpanded = it },
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

private fun currentRoute(): String = when (val path = window.location.pathname) {
    "/settings", "/claude", "/monitor", "/files", "/terminal" -> path
    else -> "/"
}
