package com.jahirtrap.cconnect.data.remote

import com.jahirtrap.cconnect.data.SharedEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SharedWatchSocket(
    private val scope: CoroutineScope,
    private val config: () -> BackendConfig,
) {
    private val _entries = MutableStateFlow<List<SharedEntry>?>(null)
    val entries: StateFlow<List<SharedEntry>?> = _entries.asStateFlow()

    private var ws: WsConnection? = null
    private var generation = 0
    private var closed = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var watchedPath = ""

    fun connect() {
        closed = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        open()
    }

    fun watch(path: String) {
        watchedPath = path
        _entries.value = null
        sendWatch()
    }

    fun refresh() = sendWatch()

    private fun sendWatch() {
        ws?.send(buildJsonObject { put("type", "watch"); put("path", watchedPath) }.toString())
    }

    private fun open() {
        val gen = ++generation
        ws?.cancel()
        ws = openWebSocket(config().sharedWsUrl, config().authHeaders, object : WsListener {
            override fun onOpen() { if (gen == generation) { reconnectAttempts = 0; sendWatch() } }
            override fun onMessage(text: String) { if (gen == generation) parse(text) }
            override fun onFailure(reason: String) { if (gen == generation) onDrop() }
            override fun onClosed(reason: String) { if (gen == generation) onDrop() }
        })
    }

    private fun onDrop() {
        if (closed) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoff = minOf(15_000L, 1000L shl minOf(reconnectAttempts, 4))
            reconnectAttempts++
            delay(backoff)
            if (!closed) open()
        }
    }

    fun close() {
        closed = true
        reconnectJob?.cancel()
        generation++
        ws?.close(1000, null)
        ws = null
    }

    private fun parse(text: String) {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        if (obj["type"]?.jsonPrimitive?.contentOrNull != "snapshot") return
        if ((obj["path"]?.jsonPrimitive?.contentOrNull ?: "") != watchedPath) return
        _entries.value = obj["entries"]?.jsonArray?.map { SharedApi.parseEntry(it) } ?: emptyList()
    }
}
