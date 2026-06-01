package com.jahirtrap.cconnect.data.remote

import com.jahirtrap.cconnect.data.DiffLine
import com.jahirtrap.cconnect.data.InteractionOption
import com.jahirtrap.cconnect.data.ServerEvent
import com.jahirtrap.cconnect.data.TodoItem
import com.jahirtrap.cconnect.data.diffKindOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class ChatSocket(private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null

    // Per-connect token so callbacks from a superseded socket are ignored — lets a drop auto-reconnect cleanly.
    private var generation = 0
    private var closed = true
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    // Resume cursor across reconnects: re-attach by channel, replay events after lastSeq.
    private var channel: String? = null
    private var lastSeq = 0

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<ServerEvent> = _events

    private fun emit(event: ServerEvent) {
        scope.launch { _events.emit(event) }
    }

    fun connect() {
        closed = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        open()
    }

    fun resetResume() {
        channel = null
        lastSeq = 0
    }

    private fun open() {
        val gen = ++generation
        ws?.cancel()
        val builder = Request.Builder().url(Backend.wsUrl)
        Http.applyAuth(builder)
        val request = builder.build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation) return
                reconnectAttempts = 0
                emit(ServerEvent.Open)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                parse(text)?.let { emit(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != generation) return
                onDrop(t.message ?: "connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != generation) return
                onDrop(reason.ifBlank { "closed" })
            }
        })
    }

    private fun onDrop(reason: String) {
        emit(ServerEvent.Closed(reason))
        if (closed) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoff = minOf(15_000L, 1000L shl minOf(reconnectAttempts, 4))
            reconnectAttempts++
            delay(backoff)
            if (!closed) open()
        }
    }

    fun sendStart(
        cwd: String,
        permissionMode: String,
        resume: String?,
        model: String,
        effort: String,
        partial: Boolean,
    ) {
        send(buildJsonObject {
            put("type", "start")
            put("cwd", cwd)
            put("permission_mode", permissionMode)
            if (resume != null) put("resume", resume) else put("resume", JsonNull)
            put("fork", false)
            put("model", model)
            put("effort", effort)
            put("partial", partial)
            put("base_url", Backend.baseUrl)
            channel?.let { put("channel", it) }
            put("last_seq", lastSeq)
        })
    }

    fun sendPrompt(text: String) {
        send(buildJsonObject {
            put("type", "prompt")
            put("text", text)
        })
    }

    fun sendSetPermissionMode(mode: String) {
        send(buildJsonObject {
            put("type", "set_permission_mode")
            put("mode", mode)
        })
    }

    fun sendInterrupt() {
        send(buildJsonObject { put("type", "interrupt") })
    }

    fun sendAsk(text: String, resume: String?) {
        send(buildJsonObject {
            put("type", "ask")
            put("text", text)
            if (resume != null) put("resume", resume)
        })
    }

    fun sendUsage() {
        send(buildJsonObject { put("type", "usage") })
    }

    fun sendLoadHistory(sessionId: String, project: String, beforeIndex: Int, limit: Int = 100) {
        send(buildJsonObject {
            put("type", "load_history")
            put("session_id", sessionId)
            put("project", project)
            put("before_index", beforeIndex)
            put("limit", limit)
        })
    }

    fun sendInteractionResponse(requestId: String, optionId: String, freeText: String?) {
        send(buildJsonObject {
            put("type", "interaction_response")
            put("id", requestId)
            put("option_id", optionId)
            if (!freeText.isNullOrBlank()) put("free_text", freeText)
        })
    }

    fun close() {
        closed = true
        reconnectJob?.cancel()
        generation++
        ws?.close(1000, null)
        ws = null
        resetResume()
    }

    private fun send(payload: JsonObject) {
        ws?.send(payload.toString())
    }

    private fun parse(text: String): ServerEvent? {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull
        fun flag(key: String): Boolean = obj[key]?.jsonPrimitive?.booleanOrNull == true
        val type = str("type")
        if (type == "ready") {
            // New channel = fresh server session (replay restarts at 1); reset the cursor.
            val newChannel = str("channel")
            if (newChannel != null && newChannel != channel) {
                channel = newChannel
                lastSeq = 0
            }
            return ServerEvent.Ready(str("session_id"), str("project"), newChannel, flag("running"))
        }
        val seq = obj["seq"]?.jsonPrimitive?.intOrNull
        if (seq != null) {
            if (type != "interaction_request" && seq <= lastSeq) return null
            if (seq > lastSeq) lastSeq = seq
        }
        return when (type) {
            "assistant_text" -> ServerEvent.AssistantText(str("text").orEmpty())
            "thinking" -> ServerEvent.Thinking(str("text").orEmpty(), labelOnly = flag("label"))
            "tool_use" -> ServerEvent.ToolUse(str("id"), str("name"), str("input"), result = str("result"))
            "tool_result" -> ServerEvent.ToolResult(str("tool_use_id"), str("content"))
            "file_change" -> ServerEvent.FileChange(
                str("id"),
                str("path").orEmpty(),
                obj["diff_lines"]?.jsonArray?.map { el ->
                    val o = el.jsonObject
                    DiffLine(
                        kind = diffKindOf(o["kind"]?.jsonPrimitive?.contentOrNull),
                        text = o["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                } ?: emptyList(),
                labelOnly = flag("label"),
            )
            "compact" -> ServerEvent.Compact(
                trigger = str("trigger"),
                preTokens = obj["pre_tokens"]?.jsonPrimitive?.intOrNull,
                postTokens = obj["post_tokens"]?.jsonPrimitive?.intOrNull,
                summary = str("summary").orEmpty(),
            )
            "ask_text" -> ServerEvent.AskText(str("text").orEmpty())
            "ask_working" -> ServerEvent.AskWorking
            "ask_session" -> ServerEvent.AskSession(str("session_id").orEmpty())
            "ask_done" -> ServerEvent.AskDone
            "command" -> ServerEvent.Command(str("markdown").orEmpty())
            "compact_summary" -> ServerEvent.CompactSummary(
                trigger = str("trigger"),
                preTokens = obj["pre_tokens"]?.jsonPrimitive?.intOrNull,
                postTokens = obj["post_tokens"]?.jsonPrimitive?.intOrNull,
                summary = str("summary").orEmpty(),
            )
            "todos" -> ServerEvent.Todos(
                obj["items"]?.jsonArray?.map { el ->
                    val o = el.jsonObject
                    TodoItem(
                        content = o["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        status = o["status"]?.jsonPrimitive?.contentOrNull ?: "pending",
                        activeForm = o["active_form"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                } ?: emptyList()
            )
            "task" -> ServerEvent.Task(str("id").orEmpty(), str("content"), str("status"))
            "result" -> ServerEvent.Result(str("session_id"))
            "done" -> ServerEvent.Done
            "interrupted" -> ServerEvent.Interrupted
            "error" -> ServerEvent.Err(
                obj["message"]?.jsonPrimitive?.contentOrNull
                    ?: obj["message"]?.toString()
                    ?: "error"
            )
            "history_chunk" -> ServerEvent.HistoryChunk(
                sessionId = str("session_id").orEmpty(),
                startIndex = obj["start_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                items = obj["items"]?.jsonArray?.map { SessionsApi.parseSessionMessage(it) } ?: emptyList(),
                hasMore = obj["has_more"]?.jsonPrimitive?.contentOrNull == "true",
            )
            "interaction_request" -> ServerEvent.InteractionRequest(
                requestId = str("id").orEmpty(),
                kind = str("kind") ?: "permission",
                toolName = str("tool_name"),
                toolUseId = str("tool_use_id"),
                input = str("input"),
                title = str("title"),
                options = obj["options"]?.jsonArray?.map { el ->
                    val o = el.jsonObject
                    InteractionOption(
                        id = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        label = o["label"]?.jsonPrimitive?.contentOrNull,
                        description = o["description"]?.jsonPrimitive?.contentOrNull,
                    )
                } ?: emptyList(),
                freeText = str("free_text") ?: "off",
            )
            else -> null
        }
    }
}
