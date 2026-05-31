package com.jahirtrap.cconnect.data

enum class Role { USER, ASSISTANT, THINKING, TOOL, TOOL_RESULT, SUMMARY, INTERACTION, FILE_CHANGE, COMPACT, SYSTEM, ERROR }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val toolName: String? = null,
    val toolUseId: String? = null,
    val interaction: InteractionData? = null,
    val path: String? = null,
    val diffLines: List<DiffLine>? = null,
    val compact: CompactData? = null,
    val sourceIndex: Int = -1,
    val labelOnly: Boolean = false,
    val result: String? = null,    // tool output, folded into the tool block (full mode)
)

data class CompactData(
    val trigger: String?,    // "auto" | "manual"
    val preTokens: Int?,
    val postTokens: Int?,
    val summary: String,
)

enum class DiffKind { HEADER, HUNK, ADD, DEL, CTX }

data class DiffLine(val kind: DiffKind, val text: String)

fun diffKindOf(value: String?): DiffKind = when (value) {
    "header" -> DiffKind.HEADER
    "hunk" -> DiffKind.HUNK
    "add" -> DiffKind.ADD
    "del" -> DiffKind.DEL
    else -> DiffKind.CTX
}

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

data class CommandOption(
    val name: String,
    val description: String,
    val kind: String,
    val requireConfirmation: Boolean = false,
)

data class TodoItem(val id: String? = null, val content: String, val status: String, val activeForm: String = "")

data class Capabilities(
    val permissionModes: List<PermissionMode> = listOf(PermissionMode("default", "Default")),
    val effortLevels: List<String> = listOf("low", "medium", "high", "xhigh", "max"),
    val models: List<ModelOption> = listOf(ModelOption("opus", "Opus 4.7")),
    val colors: List<String> = listOf("red", "orange", "yellow", "green", "cyan", "blue", "purple", "pink"),
    val commands: List<CommandOption> = emptyList(),
    val defaults: CapabilitiesDefaults = CapabilitiesDefaults(),
)

data class CapabilitiesDefaults(
    val permissionMode: String = "bypassPermissions",
    val effort: String = "xhigh",
    val model: String = "opus[1m]",
)

sealed interface ServerEvent {
    data object Open : ServerEvent
    data class Ready(val sessionId: String?, val project: String? = null) : ServerEvent
    data class AssistantText(val text: String) : ServerEvent
    data class Thinking(val text: String, val labelOnly: Boolean = false) : ServerEvent
    data class ToolUse(val id: String?, val name: String?, val input: String?, val result: String? = null) : ServerEvent
    data class ToolResult(val toolUseId: String?, val content: String?) : ServerEvent
    data class FileChange(val id: String?, val path: String, val diffLines: List<DiffLine>, val labelOnly: Boolean = false) : ServerEvent
    data class Compact(val trigger: String?, val preTokens: Int?, val postTokens: Int?, val summary: String) : ServerEvent
    data class CompactSummary(val trigger: String?, val preTokens: Int?, val postTokens: Int?, val summary: String) : ServerEvent
    data class AskText(val text: String) : ServerEvent
    data object AskDone : ServerEvent
    data class Usage(val markdown: String) : ServerEvent
    data class Todos(val items: List<TodoItem>) : ServerEvent
    data class Task(val id: String, val content: String?, val status: String?) : ServerEvent
    data class Result(val sessionId: String?) : ServerEvent
    data object Done : ServerEvent
    data object Interrupted : ServerEvent
    data class Err(val message: String) : ServerEvent
    data class Closed(val reason: String) : ServerEvent
    data class HistoryChunk(
        val sessionId: String,
        val startIndex: Int,
        val items: List<SessionMessage>,
        val hasMore: Boolean,
    ) : ServerEvent
    data class InteractionRequest(
        val requestId: String,
        val kind: String,
        val toolName: String?,
        val toolUseId: String?,
        val input: String?,
        val title: String?,
        val options: List<InteractionOption>,
        val freeText: String,
    ) : ServerEvent
}
