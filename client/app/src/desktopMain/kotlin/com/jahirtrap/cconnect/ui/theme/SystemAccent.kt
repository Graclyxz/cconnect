package com.jahirtrap.cconnect.ui.theme

import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

private var cached: Color? = null
private var computed = false

actual fun systemAccent(): Color? {
    if (!computed) {
        computed = true
        cached = runCatching { readSystemAccent() }.getOrNull()
    }
    return cached
}

private fun readSystemAccent(): Color? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("win") -> {
            val value = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\DWM", "AccentColor")
            Color(value and 0xFF, (value ushr 8) and 0xFF, (value ushr 16) and 0xFF)
        }
        os.contains("mac") || os.contains("darwin") -> macAccent()
        else -> linuxAccent()
    }
}

private fun macAccent(): Color? = when (runCommand(listOf("defaults", "read", "-g", "AppleAccentColor"))?.trim()?.toIntOrNull()) {
    0 -> Color(0xFFFF5257)
    1 -> Color(0xFFF7821B)
    2 -> Color(0xFFFFC600)
    3 -> Color(0xFF62BA46)
    4 -> Color(0xFF007AFF)
    5 -> Color(0xFF953D96)
    6 -> Color(0xFFF74F9E)
    -1 -> Color(0xFF8C8C8C)
    else -> null
}

private fun linuxAccent(): Color? = when (runCommand(listOf("gsettings", "get", "org.gnome.desktop.interface", "accent-color"))?.trim()?.trim('\'')) {
    "blue" -> Color(0xFF3584E4)
    "teal" -> Color(0xFF2190A4)
    "green" -> Color(0xFF3A944A)
    "yellow" -> Color(0xFFC88800)
    "orange" -> Color(0xFFED5B00)
    "red" -> Color(0xFFE62D42)
    "pink" -> Color(0xFFD56199)
    "purple" -> Color(0xFF9141AC)
    "slate" -> Color(0xFF6F8396)
    else -> yaruAccent()
}

private fun yaruAccent(): Color? {
    val theme = runCommand(listOf("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"))
        ?.trim()?.trim('\'') ?: return null
    if (!theme.startsWith("Yaru")) return null
    val token = theme.removePrefix("Yaru").trim('-').removeSuffix("dark").removeSuffix("light").trim('-')
    return when (token) {
        "", "orange" -> Color(0xFFE95420)
        "bark" -> Color(0xFF787859)
        "sage" -> Color(0xFF657B69)
        "olive" -> Color(0xFF4B8501)
        "viridian" -> Color(0xFF03875B)
        "prussiangreen" -> Color(0xFF308280)
        "blue" -> Color(0xFF0073E5)
        "purple" -> Color(0xFF7764D8)
        "magenta" -> Color(0xFFB34CB3)
        "red" -> Color(0xFFDA3450)
        else -> null
    }
}

private fun runCommand(command: List<String>): String? = runCatching {
    val process = ProcessBuilder(command).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    if (process.exitValue() == 0) output else null
}.getOrNull()
