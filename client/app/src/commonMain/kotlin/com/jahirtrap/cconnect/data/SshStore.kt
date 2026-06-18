package com.jahirtrap.cconnect.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SshStore {
    private val prefs = AppPrefs("cconnect_ssh", secure = true)

    var profiles: List<SshProfile>
        get() = decode(prefs.getString("profiles", null))
        set(value) = prefs.edit { putString("profiles", encode(value)) }

    fun upsert(profile: SshProfile) {
        val list = profiles.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list.add(profile)
        profiles = list
    }

    fun delete(id: String) {
        profiles = profiles.filterNot { it.id == id }
    }

    private fun decode(raw: String?): List<SshProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { el ->
                val o = el.jsonObject
                SshProfile(
                    id = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    host = o["host"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    port = o["port"]?.jsonPrimitive?.intOrNull ?: 22,
                    user = o["user"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    password = o["password"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    os = o["os"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<SshProfile>): String =
        buildJsonArray {
            list.forEach { p ->
                addJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    put("host", p.host)
                    put("port", p.port)
                    put("user", p.user)
                    put("password", p.password)
                    p.os?.let { put("os", it) }
                }
            }
        }.toString()
}
