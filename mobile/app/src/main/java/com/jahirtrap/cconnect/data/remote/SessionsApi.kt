package com.jahirtrap.cconnect.data.remote

import com.jahirtrap.cconnect.data.DiffLine
import com.jahirtrap.cconnect.data.InteractionData
import com.jahirtrap.cconnect.data.InteractionOption
import com.jahirtrap.cconnect.data.diffKindOf
import com.jahirtrap.cconnect.data.ProjectInfo
import com.jahirtrap.cconnect.data.SessionInfo
import com.jahirtrap.cconnect.data.SessionMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object SessionsApi {

    suspend fun projects(): List<ProjectInfo> =
        Http.get("/projects")?.jsonArray?.map { el ->
            val o = el.jsonObject
            ProjectInfo(
                projectKey = o["project_key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                path = o["path"]?.jsonPrimitive?.contentOrNull,
                sessionCount = o["session_count"]?.jsonPrimitive?.intOrNull ?: 0,
                lastActive = o["last_active"]?.jsonPrimitive?.doubleOrNull,
            )
        } ?: emptyList()

    // null project => all conversations across projects.
    suspend fun sessions(project: String? = null): List<SessionInfo> {
        val query = project?.let { mapOf("project" to it) } ?: emptyMap()
        return parseSessions(Http.get("/sessions", query))
    }

    private fun parseSessions(data: JsonElement?): List<SessionInfo> =
        data?.jsonArray?.map { el ->
            val o = el.jsonObject
            SessionInfo(
                sessionId = o["session_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                projectKey = o["project_key"]?.jsonPrimitive?.contentOrNull,
                path = o["path"]?.jsonPrimitive?.contentOrNull,
                lastActive = o["last_active"]?.jsonPrimitive?.doubleOrNull,
                size = o["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                preview = o["preview"]?.jsonPrimitive?.contentOrNull,
                title = o["title"]?.jsonPrimitive?.contentOrNull,
                color = o["color"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()

    suspend fun sessionMessages(sessionId: String, project: String): List<SessionMessage> =
        Http.get("/sessions/$sessionId/messages", mapOf("project" to project))?.jsonArray?.map { el ->
            val o = el.jsonObject
            val type = o["type"]?.jsonPrimitive?.contentOrNull
            val text = when (type) {
                "file_change", "interaction" -> ""
                else -> o["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val interaction = if (type == "interaction") parseInteraction(o) else null
            val diffLines = if (type == "file_change") {
                o["diff_lines"]?.jsonArray?.map { d ->
                    val od = d.jsonObject
                    DiffLine(
                        kind = diffKindOf(od["kind"]?.jsonPrimitive?.contentOrNull),
                        text = od["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }
            } else null
            SessionMessage(
                type = type,
                role = o["role"]?.jsonPrimitive?.contentOrNull,
                text = text,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: o["tool_name"]?.jsonPrimitive?.contentOrNull,
                path = o["path"]?.jsonPrimitive?.contentOrNull,
                interaction = interaction,
                diffLines = diffLines,
            )
        } ?: emptyList()

    private fun parseInteraction(o: kotlinx.serialization.json.JsonObject): InteractionData {
        val opts = o["options"]?.jsonArray?.mapNotNull { el ->
            val it = el.jsonObject
            val id = it["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            InteractionOption(
                id = id,
                label = it["label"]?.jsonPrimitive?.contentOrNull,
                description = it["description"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()
        val resolved = o["resolved"]?.jsonPrimitive?.contentOrNull
        return InteractionData(
            requestId = "resumed",
            kind = "question",
            options = opts,
            freeText = o["free_text"]?.jsonPrimitive?.contentOrNull ?: "off",
            title = o["title"]?.jsonPrimitive?.contentOrNull,
            resolved = resolved ?: "",
            resolvedText = o["resolved_text"]?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun deleteSession(sessionId: String, project: String): Boolean =
        Http.delete("/sessions/$sessionId", mapOf("project" to project)) != null

    suspend fun renameSession(sessionId: String, project: String, title: String): Boolean =
        Http.post("/sessions/$sessionId/rename", buildJsonObject {
            put("project", project)
            put("title", title)
        }) != null

    suspend fun autoRenameSession(sessionId: String, project: String): String? =
        Http.post("/sessions/$sessionId/auto-rename", buildJsonObject {
            put("project", project)
        })?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull

    suspend fun setSessionColor(sessionId: String, project: String, color: String): Boolean =
        Http.post("/sessions/$sessionId/color", buildJsonObject {
            put("project", project)
            put("color", color)
        }) != null
}
