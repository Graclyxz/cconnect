package com.jahirtrap.cconnect.data

data class ProjectInfo(
    val projectKey: String,
    val path: String?,
    val name: String?,
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
    val color: String?,
)

data class SessionMessage(
    val type: String?,
    val role: String?,
    val text: String,
    val name: String? = null,
    val path: String? = null,
    val interaction: InteractionData? = null,
    val diffLines: List<DiffLine>? = null,
    val compact: CompactData? = null,
    val index: Int = -1,
    val labelOnly: Boolean = false,
    val result: String? = null,
    val images: List<String>? = null,
    val timestamp: Long? = null,
)

data class SharedEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Double,
    val items: Int = 0,
)
