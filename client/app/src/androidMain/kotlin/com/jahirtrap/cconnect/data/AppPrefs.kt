package com.jahirtrap.cconnect.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jahirtrap.cconnect.appContext

actual class AppPrefs actual constructor(name: String, secure: Boolean) {
    private val prefs = if (secure) {
        EncryptedSharedPreferences.create(
            appContext,
            name,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } else {
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    actual fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    actual fun contains(key: String): Boolean = prefs.contains(key)

    actual fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    actual fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    actual fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    actual fun remove(key: String) { prefs.edit().remove(key).apply() }
}
