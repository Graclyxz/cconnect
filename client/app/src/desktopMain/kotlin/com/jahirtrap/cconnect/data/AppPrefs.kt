package com.jahirtrap.cconnect.data

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64
import java.util.prefs.Preferences

actual class AppPrefs actual constructor(name: String, secure: Boolean) {
    private val node: Preferences = Preferences.userRoot().node("com/jahirtrap/cconnect/$name")
    private val protect = secure && System.getProperty("os.name").orEmpty().lowercase().contains("win")

    actual fun getString(key: String, default: String?): String? {
        val raw = node.get(key, null) ?: return default
        if (!protect) return raw
        return runCatching { String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(raw))) }.getOrDefault(default)
    }
    actual fun getBoolean(key: String, default: Boolean): Boolean = node.getBoolean(key, default)
    actual fun getInt(key: String, default: Int): Int = node.getInt(key, default)
    actual fun contains(key: String): Boolean = runCatching { node.keys().contains(key) }.getOrDefault(false)

    actual fun putString(key: String, value: String?) {
        if (value == null) { node.remove(key); return }
        val stored = if (protect) Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(value.toByteArray())) else value
        node.put(key, stored)
    }
    actual fun putBoolean(key: String, value: Boolean) { node.putBoolean(key, value) }
    actual fun putInt(key: String, value: Int) { node.putInt(key, value) }
    actual fun remove(key: String) { node.remove(key) }
}
