package com.jahirtrap.cconect.data

import android.content.Context
import androidx.core.content.edit

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("cconect", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) = prefs.edit { putString("host", value) }

    var port: Int
        get() = prefs.getInt("port", 8723)
        set(value) = prefs.edit { putInt("port", value) }

    var cwd: String
        get() = prefs.getString("cwd", "") ?: ""
        set(value) = prefs.edit { putString("cwd", value) }

    var permissionMode: String
        get() = prefs.getString("permission_mode", "default") ?: "default"
        set(value) = prefs.edit { putString("permission_mode", value) }

    // "" = follow system, otherwise a language tag like "en" / "es"
    var language: String
        get() = prefs.getString("language", "") ?: ""
        set(value) = prefs.edit { putString("language", value) }

    // "system" | "light" | "dark"
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit { putString("theme_mode", value) }

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(value) = prefs.edit { putBoolean("dynamic_color", value) }

    var accentIndex: Int
        get() = prefs.getInt("accent_index", 4)
        set(value) = prefs.edit { putInt("accent_index", value) }

    val isConfigured: Boolean
        get() = host.isNotBlank() && cwd.isNotBlank()
}
