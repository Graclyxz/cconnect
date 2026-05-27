package com.jahirtrap.cconnect.data.remote

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
            SessionMessage(
                type = o["type"]?.jsonPrimitive?.contentOrNull,
                role = o["role"]?.jsonPrimitive?.contentOrNull,
                text = o["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                timestamp = o["timestamp"]?.jsonPrimitive?.contentOrNull,
                uuid = o["uuid"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()

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
