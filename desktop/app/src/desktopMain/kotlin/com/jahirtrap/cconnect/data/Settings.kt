package com.jahirtrap.cconnect.data

import com.jahirtrap.cconnect.data.remote.Backend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

class Settings {
    private val prefs = DesktopPrefs("cconnect")

    init {
        migrateLegacyHost()
        syncBackend()
    }

    var environments: List<EnvironmentProfile>
        get() = parseEnvironments(prefs.getString("environments", null))
        set(value) {
            prefs.edit { putString("environments", encodeEnvironments(value)) }
            syncBackend()
        }

    var activeEnvironmentId: String?
        get() = prefs.getString("active_environment", null)
        set(value) {
            prefs.edit { putString("active_environment", value) }
            syncBackend()
        }

    val activeEnvironment: EnvironmentProfile?
        get() = environments.let { list -> list.firstOrNull { it.id == activeEnvironmentId } ?: list.firstOrNull() }

    var notifyTaskDone: Boolean
        get() = prefs.getBoolean("notify_task_done", false)
        set(value) = prefs.edit { putBoolean("notify_task_done", value) }

    var notifyInteraction: Boolean
        get() = prefs.getBoolean("notify_interaction", true)
        set(value) = prefs.edit { putBoolean("notify_interaction", value) }

    var notifPermissionRequested: Boolean
        get() = prefs.getBoolean("notif_permission_requested", false)
        set(value) = prefs.edit { putBoolean("notif_permission_requested", value) }

    var markdownPreviewFormatted: Boolean
        get() = prefs.getBoolean("markdown_preview_formatted", true)
        set(value) = prefs.edit { putBoolean("markdown_preview_formatted", value) }

    var fileSortField: String
        get() = prefs.getString("file_sort_field", "date") ?: "date"
        set(value) = prefs.edit { putString("file_sort_field", value) }

    var fileSortAscending: Boolean
        get() = prefs.getBoolean("file_sort_ascending", false)
        set(value) = prefs.edit { putBoolean("file_sort_ascending", value) }

    fun upsertEnvironment(profile: EnvironmentProfile) {
        val list = environments.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list.add(profile)
        environments = list
        if (activeEnvironmentId == null) activeEnvironmentId = profile.id
    }

    fun deleteEnvironment(id: String) {
        environments = environments.filterNot { it.id == id }
        if (activeEnvironmentId == id) activeEnvironmentId = environments.firstOrNull()?.id
    }

    private fun syncBackend() {
        activeEnvironment?.let {
            Backend.kind = it.kind
            Backend.host = it.host
            Backend.port = it.port
            Backend.authKind = it.authKind
            Backend.authToken = it.authToken
            Backend.authUser = it.authUser
            Backend.authPassword = it.authPassword
            Backend.authHeaderName = it.authHeaderName
            Backend.authHeaderValue = it.authHeaderValue
        }
    }

    var cwd: String
        get() = prefs.getString("cwd", "") ?: ""
        set(value) = prefs.edit { putString("cwd", value) }

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
        get() = activeEnvironment != null

    // Reset app-local prefs (backend-owned settings reset via /api/settings/reset).
    fun resetDefaults() {
        prefs.edit {
            listOf("cwd", "language", "theme_mode", "dynamic_color", "accent_index")
                .forEach { remove(it) }
        }
    }

    private fun migrateLegacyHost() {
        if (prefs.contains("environments")) return
        val legacy = prefs.getString("host", "") ?: ""
        if (legacy.isNotBlank()) {
            val profile = EnvironmentProfile(
                id = UUID.randomUUID().toString(),
                name = "Default",
                host = legacy,
                port = prefs.getInt("port", 8723),
            )
            environments = listOf(profile)
            activeEnvironmentId = profile.id
        }
    }

    private fun parseEnvironments(raw: String?): List<EnvironmentProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { el ->
                val o = el.jsonObject
                val legacySecure = o["secure"]?.jsonPrimitive?.booleanOrNull
                val legacyToken = o["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val kindStored = o["kind"]?.jsonPrimitive?.contentOrNull
                val resolvedKind = kindStored?.let { if (it == "url") if (legacySecure == true) "https" else "http" else it }
                    ?: if (legacySecure == true) "https" else "http"
                val portRaw = o["port"]?.jsonPrimitive?.intOrNull
                val resolvedPort = when {
                    resolvedKind == "https" -> portRaw?.takeIf { it != 443 }
                    else -> portRaw ?: 8723
                }
                EnvironmentProfile(
                    id = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    kind = resolvedKind,
                    host = o["host"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    port = resolvedPort,
                    authKind = o["authKind"]?.jsonPrimitive?.contentOrNull
                        ?: if (legacyToken.isNotBlank()) "bearer" else "none",
                    authToken = o["authToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                        ?: legacyToken,
                    authUser = o["authUser"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    authPassword = o["authPassword"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    authHeaderName = o["authHeaderName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    authHeaderValue = o["authHeaderValue"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    directory = o["directory"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeEnvironments(list: List<EnvironmentProfile>): String =
        buildJsonArray {
            list.forEach { p ->
                addJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    put("kind", p.kind)
                    put("host", p.host)
                    p.port?.let { put("port", it) }
                    put("authKind", p.authKind)
                    put("authToken", p.authToken)
                    put("authUser", p.authUser)
                    put("authPassword", p.authPassword)
                    put("authHeaderName", p.authHeaderName)
                    put("authHeaderValue", p.authHeaderValue)
                    put("directory", p.directory)
                }
            }
        }.toString()
}
