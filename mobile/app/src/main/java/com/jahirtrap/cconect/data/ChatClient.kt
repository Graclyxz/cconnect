package com.jahirtrap.cconect.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class ChatClient(private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<ServerEvent> = _events

    private fun emit(event: ServerEvent) {
        scope.launch { _events.emit(event) }
    }

    fun connect(host: String, port: Int) {
        close()
        val request = Request.Builder().url("ws://$host:$port/api/chat/ws").build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = emit(ServerEvent.Open)

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { emit(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                emit(ServerEvent.Closed(t.message ?: "connection failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                emit(ServerEvent.Closed(reason.ifBlank { "closed" }))
            }
        })
    }

    fun sendStart(cwd: String, permissionMode: String, resume: String?) {
        send(buildJsonObject {
            put("type", "start")
            put("cwd", cwd)
            put("permission_mode", permissionMode)
            if (resume != null) put("resume", resume) else put("resume", JsonNull)
            put("fork", false)
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

    fun close() {
        ws?.close(1000, null)
        ws = null
    }

    private fun send(payload: JsonObject) {
        ws?.send(payload.toString())
    }

    private fun parse(text: String): ServerEvent? {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull
        return when (str("type")) {
            "ready" -> ServerEvent.Ready(str("session_id"))
            "assistant_text" -> ServerEvent.AssistantText(str("text").orEmpty())
            "thinking" -> ServerEvent.Thinking(str("text").orEmpty())
            "tool_use" -> ServerEvent.ToolUse(str("name"), obj["input"]?.toString())
            "tool_result" -> ServerEvent.ToolResult(obj["content"]?.toString())
            "result" -> ServerEvent.Result(str("session_id"))
            "done" -> ServerEvent.Done
            "interrupted" -> ServerEvent.Interrupted
            "error" -> ServerEvent.Err(obj["message"]?.toString() ?: "error")
            else -> null
        }
    }
}
