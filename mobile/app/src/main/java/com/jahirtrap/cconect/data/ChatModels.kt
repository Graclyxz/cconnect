package com.jahirtrap.cconect.data

enum class Role { USER, ASSISTANT, THINKING, TOOL, SYSTEM, ERROR }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val toolName: String? = null,
)

data class ModelOption(val id: String, val label: String)

data class Capabilities(
    val permissionModes: List<String> = listOf("default", "acceptEdits", "plan", "dontAsk", "bypassPermissions"),
    val effortLevels: List<String> = listOf("low", "medium", "high", "xhigh", "max"),
    val models: List<ModelOption> = listOf(ModelOption("opus", "Opus 4.7")),
)

sealed interface ServerEvent {
    data object Open : ServerEvent
    data class Ready(val sessionId: String?) : ServerEvent
    data class AssistantText(val text: String) : ServerEvent
    data class Thinking(val text: String) : ServerEvent
    data class ToolUse(val name: String?, val input: String?) : ServerEvent
    data class ToolResult(val content: String?) : ServerEvent
    data class Result(val sessionId: String?) : ServerEvent
    data object Done : ServerEvent
    data object Interrupted : ServerEvent
    data class Err(val message: String) : ServerEvent
    data class Closed(val reason: String) : ServerEvent
}
