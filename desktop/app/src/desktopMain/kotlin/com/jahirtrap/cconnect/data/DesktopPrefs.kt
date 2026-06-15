package com.jahirtrap.cconnect.data

import java.util.prefs.Preferences

class DesktopPrefs(name: String) {
    @PublishedApi internal val node: Preferences = Preferences.userRoot().node("com/jahirtrap/cconnect/$name")

    fun getString(key: String, default: String?): String? = node.get(key, default)
    fun getBoolean(key: String, default: Boolean): Boolean = node.getBoolean(key, default)
    fun getInt(key: String, default: Int): Int = node.getInt(key, default)
    fun contains(key: String): Boolean = runCatching { node.keys().contains(key) }.getOrDefault(false)

    class Editor(@PublishedApi internal val node: Preferences) {
        fun putString(key: String, value: String?) { if (value == null) node.remove(key) else node.put(key, value) }
        fun putBoolean(key: String, value: Boolean) = node.putBoolean(key, value)
        fun putInt(key: String, value: Int) = node.putInt(key, value)
        fun remove(key: String) = node.remove(key)
    }
}

inline fun DesktopPrefs.edit(block: DesktopPrefs.Editor.() -> Unit) {
    DesktopPrefs.Editor(node).apply(block)
}
