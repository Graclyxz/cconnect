package com.jahirtrap.cconect.data

data class ProjectInfo(
    val projectKey: String,
    val path: String?,
    val sessionCount: Int,
    val lastActive: Double?,
)

data class SessionInfo(
    val sessionId: String,
    val lastActive: Double?,
    val size: Long,
    val preview: String?,
)

data class SessionMessage(
    val type: String?,
    val role: String?,
    val text: String,
    val timestamp: String?,
    val uuid: String?,
)
