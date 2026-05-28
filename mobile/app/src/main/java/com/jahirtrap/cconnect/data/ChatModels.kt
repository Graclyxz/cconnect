package com.jahirtrap.cconnect.data

enum class Role { USER, ASSISTANT, THINKING, TOOL, TOOL_RESULT, SUMMARY, INTERACTION, SYSTEM, ERROR }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val toolName: String? = null,
    val interaction: InteractionData? = null,
)

data class InteractionOption(
    val id: String,
    val label: String? = null,
    val description: String? = null,
)

data class InteractionData(
    val requestId: String,
    val kind: String,
    val options: List<InteractionOption>,
    val freeText: String,
    val title: String? = null,
    val resolved: String? = null,
    val resolvedText: String? = null,
)

data class ModelOption(val id: String, val label: String)

data class PermissionMode(val id: String, val label: String)

data class TodoItem(val id: String? = null, val content: String, val status: String, val activeForm: String = "")

data class Capabilities(
    val permissionModes: List<PermissionMode> = listOf(PermissionMode("default", "Default")),
    val effortLevels: List<String> = listOf("low", "medium", "high", "xhigh", "max"),
    val models: List<ModelOption> = listOf(ModelOption("opus", "Opus 4.7")),
    val colors: List<String> = listOf("red", "orange", "yellow", "green", "cyan", "blue", "purple", "pink"),
)

sealed interface ServerEvent {
    data object Open : ServerEvent
    data class Ready(val sessionId: String?) : ServerEvent
    data class AssistantText(val text: String) : ServerEvent
    data class Thinking(val text: String) : ServerEvent
    data class ToolUse(val name: String?, val input: String?) : ServerEvent
    data class ToolResult(val content: String?) : ServerEvent
    data class Todos(val items: List<TodoItem>) : ServerEvent
    data class Task(val id: String, val content: String?, val status: String?) : ServerEvent
    data class Result(val sessionId: String?) : ServerEvent
    data object Done : ServerEvent
    data object Interrupted : ServerEvent
    data class Err(val message: String) : ServerEvent
    data class Closed(val reason: String) : ServerEvent
    data class InteractionRequest(
        val requestId: String,
        val kind: String,
        val toolName: String?,
        val input: String?,
        val title: String?,
        val options: List<InteractionOption>,
        val freeText: String,
    ) : ServerEvent
}
