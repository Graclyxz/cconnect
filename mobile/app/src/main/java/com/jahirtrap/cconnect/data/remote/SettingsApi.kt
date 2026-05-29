package com.jahirtrap.cconnect.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Backend-owned generation settings (the source of truth lives in the backend DB).
// CLI source/version is handled separately by CliApi.
object SettingsApi {

    data class Snapshot(
        val model: String,
        val effort: String,
        val permissionMode: String,
        val streaming: Boolean,
    )

    private fun effectiveStr(o: JsonObject, key: String, fallback: String): String =
        o[key]?.jsonObject?.get("effective")?.jsonPrimitive?.contentOrNull ?: fallback

    private fun effectiveBool(o: JsonObject, key: String, fallback: Boolean): Boolean =
        o[key]?.jsonObject?.get("effective")?.jsonPrimitive?.booleanOrNull ?: fallback

    private fun parse(o: JsonObject) = Snapshot(
        model = effectiveStr(o, "model", "opus"),
        effort = effectiveStr(o, "effort", "xhigh"),
        permissionMode = effectiveStr(o, "permission_mode", "bypassPermissions"),
        streaming = effectiveBool(o, "streaming", true),
    )

    suspend fun get(): Snapshot? = Http.get("/settings")?.jsonObject?.let(::parse)

    suspend fun update(
        model: String? = null,
        effort: String? = null,
        permissionMode: String? = null,
        streaming: Boolean? = null,
    ): Snapshot? = Http.post("/settings", buildJsonObject {
        if (model != null) put("model", model)
        if (effort != null) put("effort", effort)
        if (permissionMode != null) put("permission_mode", permissionMode)
        if (streaming != null) put("streaming", streaming)
    })?.jsonObject?.let(::parse)

    suspend fun reset(): Snapshot? = Http.post("/settings/reset")?.jsonObject?.let(::parse)
}
