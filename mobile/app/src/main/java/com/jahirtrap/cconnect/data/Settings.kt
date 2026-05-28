package com.jahirtrap.cconnect.data

import android.content.Context
import androidx.core.content.edit
import com.jahirtrap.cconnect.data.remote.Backend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("cconnect", Context.MODE_PRIVATE)

    init {
        migrateLegacyHost()
        syncBackend()
    }

    // Named backend connections; the active one drives Backend.host/port.
    var connections: List<ConnectionProfile>
        get() = parseConnections(prefs.getString("connections", null))
        set(value) {
            prefs.edit { putString("connections", encodeConnections(value)) }
            syncBackend()
        }

    var activeConnectionId: String?
        get() = prefs.getString("active_connection", null)
        set(value) {
            prefs.edit { putString("active_connection", value) }
            syncBackend()
        }

    val activeConnection: ConnectionProfile?
        get() = connections.let { list -> list.firstOrNull { it.id == activeConnectionId } ?: list.firstOrNull() }

    fun upsertConnection(profile: ConnectionProfile) {
        val list = connections.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list.add(profile)
        connections = list
        if (activeConnectionId == null) activeConnectionId = profile.id
    }

    fun deleteConnection(id: String) {
        connections = connections.filterNot { it.id == id }
        if (activeConnectionId == id) activeConnectionId = connections.firstOrNull()?.id
    }

    private fun syncBackend() {
        activeConnection?.let {
            Backend.host = it.host
            Backend.port = it.port
        }
    }

    var cwd: String
        get() = prefs.getString("cwd", "") ?: ""
        set(value) = prefs.edit { putString("cwd", value) }

    var permissionMode: String
        get() = prefs.getString("permission_mode", "bypassPermissions") ?: "bypassPermissions"
        set(value) = prefs.edit { putString("permission_mode", value) }

    var model: String
        get() = prefs.getString("model", "opus[1m]") ?: "opus[1m]"
        set(value) = prefs.edit { putString("model", value) }

    var effort: String
        get() = prefs.getString("effort", "xhigh") ?: "xhigh"
        set(value) = prefs.edit { putString("effort", value) }

    var streaming: Boolean
        get() = prefs.getBoolean("streaming", true)
        set(value) = prefs.edit { putBoolean("streaming", value) }

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
        get() = activeConnection != null

    // Reset everything except saved connections.
    fun resetDefaults() {
        prefs.edit {
            listOf("cwd", "permission_mode", "model", "effort", "streaming", "language", "theme_mode", "dynamic_color", "accent_index")
                .forEach { remove(it) }
        }
    }

    private fun migrateLegacyHost() {
        if (prefs.contains("connections")) return
        val legacy = prefs.getString("host", "") ?: ""
        if (legacy.isNotBlank()) {
            val profile = ConnectionProfile(UUID.randomUUID().toString(), "Default", legacy, prefs.getInt("port", 8723))
            connections = listOf(profile)
            activeConnectionId = profile.id
        }
    }

    private fun parseConnections(raw: String?): List<ConnectionProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { el ->
                val o = el.jsonObject
                ConnectionProfile(
                    id = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    host = o["host"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    port = o["port"]?.jsonPrimitive?.intOrNull ?: 8723,
                    directory = o["directory"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeConnections(list: List<ConnectionProfile>): String =
        buildJsonArray {
            list.forEach { p ->
                addJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    put("host", p.host)
                    put("port", p.port)
                    put("directory", p.directory)
                }
            }
        }.toString()
}
