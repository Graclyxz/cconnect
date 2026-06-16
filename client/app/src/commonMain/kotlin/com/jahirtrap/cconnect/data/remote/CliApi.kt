package com.jahirtrap.cconnect.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object CliApi {

    data class CliInfo(
        val source: String,
        val sources: List<String>,
        val resolvedPath: String?,
        val activeVersion: String?,
        val bundledVersion: String?,
        val systemPath: String?,
        val systemVersion: String?,
        val customPath: String?,
    )

    private fun parse(o: JsonObject) = CliInfo(
        source = o["source"]?.jsonPrimitive?.contentOrNull ?: "system",
        sources = o["sources"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: listOf("system", "custom", "bundled"),
        resolvedPath = o["resolved_path"]?.jsonPrimitive?.contentOrNull,
        activeVersion = o["active_version"]?.jsonPrimitive?.contentOrNull,
        bundledVersion = o["bundled_version"]?.jsonPrimitive?.contentOrNull,
        systemPath = o["system_path"]?.jsonPrimitive?.contentOrNull,
        systemVersion = o["system_version"]?.jsonPrimitive?.contentOrNull,
        customPath = o["custom_path"]?.jsonPrimitive?.contentOrNull,
    )

    suspend fun status(): CliInfo? = Http.get("/cli")?.jsonObject?.let(::parse)

    suspend fun setSource(source: String, customPath: String?): CliInfo? =
        Http.post("/cli", buildJsonObject {
            put("source", source)
            if (customPath != null) put("custom_path", customPath)
        })?.jsonObject?.let(::parse)

    suspend fun update(source: String? = null, customPath: String? = null): Pair<Boolean, String> {
        val body = buildJsonObject {
            if (source != null) put("source", source)
            if (customPath != null) put("custom_path", customPath)
        }
        val o = Http.post("/cli/update", body)?.jsonObject ?: return false to "No response"
        return (o["ok"]?.jsonPrimitive?.booleanOrNull ?: false) to (o["message"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }
}
