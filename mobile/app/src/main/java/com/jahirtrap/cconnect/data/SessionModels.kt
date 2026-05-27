package com.jahirtrap.cconnect.data

data class ProjectInfo(
    val projectKey: String,
    val path: String?,
    val sessionCount: Int,
    val lastActive: Double?,
)

data class SessionInfo(
    val sessionId: String,
    val projectKey: String?,
    val path: String?,
    val lastActive: Double?,
    val size: Long,
    val preview: String?,
    val title: String?,
)

data class SessionMessage(
    val type: String?,
    val role: String?,
    val text: String,
    val timestamp: String?,
    val uuid: String?,
)
