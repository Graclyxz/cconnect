package com.jahirtrap.cconnect.data

import kotlinx.browser.localStorage

actual class AppPrefs actual constructor(name: String) {
    private val prefix = "$name."

    actual fun getString(key: String, default: String?): String? = localStorage.getItem(prefix + key) ?: default
    actual fun getBoolean(key: String, default: Boolean): Boolean = localStorage.getItem(prefix + key)?.toBooleanStrictOrNull() ?: default
    actual fun getInt(key: String, default: Int): Int = localStorage.getItem(prefix + key)?.toIntOrNull() ?: default
    actual fun contains(key: String): Boolean = localStorage.getItem(prefix + key) != null

    actual fun putString(key: String, value: String?) {
        if (value == null) localStorage.removeItem(prefix + key) else localStorage.setItem(prefix + key, value)
    }
    actual fun putBoolean(key: String, value: Boolean) { localStorage.setItem(prefix + key, value.toString()) }
    actual fun putInt(key: String, value: Int) { localStorage.setItem(prefix + key, value.toString()) }
    actual fun remove(key: String) { localStorage.removeItem(prefix + key) }
}
