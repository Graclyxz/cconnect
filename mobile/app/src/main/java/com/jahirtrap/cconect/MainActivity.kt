package com.jahirtrap.cconect

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jahirtrap.cconect.chat.ChatScreen
import com.jahirtrap.cconect.data.Settings
import com.jahirtrap.cconect.settings.SettingsScreen
import com.jahirtrap.cconect.ui.theme.CConectTheme
import com.jahirtrap.cconect.ui.theme.accentAt
import com.jahirtrap.cconect.ui.theme.themeModeOf

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
    var accentIndex by remember { mutableStateOf(settings.accentIndex) }
    var language by remember { mutableStateOf(settings.language) }
    var showSettings by remember { mutableStateOf(!settings.isConfigured) }

    CConectTheme(
        themeMode = themeModeOf(themeMode),
        dynamicColor = dynamicColor,
        accent = accentAt(accentIndex),
    ) {
        if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeMode = { themeMode = it; settings.themeMode = it },
                dynamicColor = dynamicColor,
                onDynamicColor = { dynamicColor = it; settings.dynamicColor = it },
                accentIndex = accentIndex,
                onAccent = { accentIndex = it; settings.accentIndex = it },
                language = language,
                onLanguage = { language = it; settings.language = it; applyLanguage(it) },
                onSaved = { showSettings = false },
                onBack = if (settings.isConfigured) ({ showSettings = false }) else null,
            )
        } else {
            ChatScreen(onOpenSettings = { showSettings = true })
        }
    }
}
