package com.jahirtrap.cconnect.data.remote

import com.jahirtrap.cconnect.data.ProjectInfo
import com.jahirtrap.cconnect.data.SessionInfo
import com.jahirtrap.cconnect.data.SessionMessage
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

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

    suspend fun sessions(project: String): List<SessionInfo> =
        Http.get("/sessions", mapOf("project" to project))?.jsonArray?.map { el ->
            val o = el.jsonObject
            SessionInfo(
                sessionId = o["session_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                lastActive = o["last_active"]?.jsonPrimitive?.doubleOrNull,
                size = o["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                preview = o["preview"]?.jsonPrimitive?.contentOrNull,
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
}
