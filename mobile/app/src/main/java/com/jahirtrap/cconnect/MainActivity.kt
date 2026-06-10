package com.jahirtrap.cconnect

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.jahirtrap.cconnect.chat.ChatScreen
import com.jahirtrap.cconnect.claude.ClaudeScreen
import com.jahirtrap.cconnect.files.FileExplorerScreen
import com.jahirtrap.cconnect.files.FilePreviewScreen
import com.jahirtrap.cconnect.monitor.MonitorScreen
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.service.Notifier
import com.jahirtrap.cconnect.settings.SettingsScreen
import com.jahirtrap.cconnect.terminal.TerminalScreen
import com.jahirtrap.cconnect.ui.theme.CConnectTheme
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.themeModeOf
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.Locale

class MainActivity : AppCompatActivity() {
    val openChatRequests = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            !settings.notifPermissionRequested
        ) {
            settings.notifPermissionRequested = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Notifier.cleanupChannels(this)
        // Android ships a stripped BC provider; swap it for the full BouncyCastle so sshj
        // has curve25519, ed25519, chacha20-poly1305, etc. Modern OpenSSH defaults need this.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        // Richer text selection menu.
        ComposeFoundationFlags.isNewContextMenuEnabled = true
        enableEdgeToEdge()
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Notifier.EXTRA_OPEN_CHAT, false)) openChatRequests.intValue++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val baseContext = LocalContext.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current!!
    val settings = remember { Settings(baseContext) }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
    var accentIndex by remember { mutableStateOf(settings.accentIndex) }
    var language by remember { mutableStateOf(settings.language) }
    var showSettings by remember { mutableStateOf(!settings.isConfigured) }
    var settingsHighlight by remember { mutableStateOf<String?>(null) }
    var showExplorer by remember { mutableStateOf(false) }
    var showClaude by remember { mutableStateOf(false) }
    var showMonitor by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<PreviewRequest?>(null) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalFromSettings by remember { mutableStateOf(false) }
    // Hoisted so the open/closed state survives navigating to settings and back.
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val openChatRequests = (baseContext as? MainActivity)?.openChatRequests?.intValue ?: 0
    LaunchedEffect(openChatRequests) {
        if (openChatRequests > 0) {
            showSettings = false
            showExplorer = false
            showClaude = false
            showMonitor = false
            showTerminal = false
            previewFile = null
        }
    }

    // Override the locale in-place so changing language recomposes instead of recreating the activity.
    val localizedContext = remember(language) {
        if (language.isBlank()) {
            baseContext
        } else {
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language))
            baseContext.createConfigurationContext(config)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
    ) {
        CConnectTheme(
            themeMode = themeModeOf(themeMode),
            dynamicColor = dynamicColor,
            accent = accentAt(accentIndex),
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

                showExplorer -> FileExplorerScreen(onClose = { showExplorer = false }, onOpenPreview = { url, name, onDelete -> previewFile = PreviewRequest(url, name, onDelete) })

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
                    onOpenExplorer = { showExplorer = true },
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

private data class PreviewRequest(
    val url: String,
    val name: String,
    val onDelete: (() -> Unit)?,
)
