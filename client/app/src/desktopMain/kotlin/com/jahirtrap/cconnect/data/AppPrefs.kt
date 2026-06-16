package com.jahirtrap.cconnect.data

import java.util.prefs.Preferences

actual class AppPrefs actual constructor(name: String) {
    private val node: Preferences = Preferences.userRoot().node("com/jahirtrap/cconnect/$name")

    actual fun getString(key: String, default: String?): String? = node.get(key, default)
    actual fun getBoolean(key: String, default: Boolean): Boolean = node.getBoolean(key, default)
    actual fun getInt(key: String, default: Int): Int = node.getInt(key, default)
    actual fun contains(key: String): Boolean = runCatching { node.keys().contains(key) }.getOrDefault(false)

    actual fun putString(key: String, value: String?) { if (value == null) node.remove(key) else node.put(key, value) }
    actual fun putBoolean(key: String, value: Boolean) { node.putBoolean(key, value) }
    actual fun putInt(key: String, value: Int) { node.putInt(key, value) }
    actual fun remove(key: String) { node.remove(key) }
}
