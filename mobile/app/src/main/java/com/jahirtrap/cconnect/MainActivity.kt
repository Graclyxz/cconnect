package com.jahirtrap.cconnect

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.jahirtrap.cconnect.chat.ChatScreen
import com.jahirtrap.cconnect.files.FileExplorerScreen
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.settings.SettingsScreen
import com.jahirtrap.cconnect.ui.theme.CConnectTheme
import com.jahirtrap.cconnect.ui.theme.accentAt
import com.jahirtrap.cconnect.ui.theme.themeModeOf
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val baseContext = LocalContext.current
    val settings = remember { Settings(baseContext) }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
    var accentIndex by remember { mutableStateOf(settings.accentIndex) }
    var language by remember { mutableStateOf(settings.language) }
    var showSettings by remember { mutableStateOf(!settings.isConfigured) }
    var showExplorer by remember { mutableStateOf(false) }
    // Hoisted so the open/closed state survives navigating to settings and back.
    val drawerState = rememberDrawerState(DrawerValue.Closed)

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
                    onClose = { showSettings = false },
                )

                showExplorer -> FileExplorerScreen(onClose = { showExplorer = false })

                else -> ChatScreen(
                    onOpenSettings = { showSettings = true },
                    onOpenExplorer = { showExplorer = true },
                    drawerState = drawerState,
                    themeMode = themeMode,
                    onThemeMode = { themeMode = it; settings.themeMode = it },
                    language = language,
                    onLanguage = { language = it; settings.language = it },
                )
            }
        }
    }
}
