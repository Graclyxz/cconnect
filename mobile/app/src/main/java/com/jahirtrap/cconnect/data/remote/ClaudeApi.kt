package com.jahirtrap.cconnect.data.remote

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ClaudeApi {

    data class Plugin(
        val name: String,
        val marketplace: String?,
        val version: String?,
        val scope: String?,
        val enabled: Boolean,
    )

    data class Marketplace(val name: String, val repo: String?)

    data class Skill(
        val name: String,
        val description: String?,
        val plugin: String?,
        val enabled: Boolean,
    )

    data class McpServer(val name: String, val type: String?, val detail: String?)

    data class Extensions(val plugins: List<Plugin>, val marketplaces: List<Marketplace>)

    suspend fun userPrompt(): String? =
        Http.get("/claude/prompt")?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

    suspend fun setUserPrompt(text: String): Boolean =
        Http.put("/claude/prompt", buildJsonObject { put("text", text) }) != null

    suspend fun extensions(): Extensions? {
        val data = Http.get("/claude/plugins")?.jsonObject ?: return null
        val plugins = data["plugins"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            Plugin(
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                marketplace = o["marketplace"]?.jsonPrimitive?.contentOrNull,
                version = o["version"]?.jsonPrimitive?.contentOrNull,
                scope = o["scope"]?.jsonPrimitive?.contentOrNull,
                enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
            )
        } ?: emptyList()
        val marketplaces = data["marketplaces"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            Marketplace(
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                repo = o["repo"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()
        return Extensions(plugins, marketplaces)
    }

    suspend fun skills(): List<Skill>? = Http.get("/claude/skills")?.jsonArray?.mapNotNull { el ->
        val o = el.jsonObject
        Skill(
            name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            description = o["description"]?.jsonPrimitive?.contentOrNull,
            plugin = o["plugin"]?.jsonPrimitive?.contentOrNull,
            enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
        )
    }

    suspend fun mcp(): List<McpServer>? = Http.get("/claude/mcp")?.jsonArray?.mapNotNull { el ->
        val o = el.jsonObject
        McpServer(
            name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            type = o["type"]?.jsonPrimitive?.contentOrNull,
            detail = o["detail"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
