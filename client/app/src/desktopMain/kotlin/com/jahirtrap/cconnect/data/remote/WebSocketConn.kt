package com.jahirtrap.cconnect.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val wsClients = ConcurrentHashMap<Long, OkHttpClient>()

private fun wsClient(pingSeconds: Long): OkHttpClient = wsClients.getOrPut(pingSeconds) {
    OkHttpClient.Builder()
        .pingInterval(pingSeconds, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}

actual class WsConnection(private val ws: WebSocket) {
    actual fun send(text: String) { ws.send(text) }
    actual fun cancel() { ws.cancel() }
    actual fun close(code: Int, reason: String?) { ws.close(code, reason) }
}

actual fun openWebSocket(
    url: String,
    headers: List<Pair<String, String>>,
    listener: WsListener,
    pingSeconds: Long,
): WsConnection {
    val builder = Request.Builder().url(url)
    headers.forEach { (name, value) -> builder.header(name, value) }
    val ws = wsClient(pingSeconds).newWebSocket(builder.build(), object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { listener.onOpen() }
        override fun onMessage(webSocket: WebSocket, text: String) { listener.onMessage(text) }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { listener.onFailure(t.message ?: "connection failed") }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { listener.onClosed(reason) }
    })
    return WsConnection(ws)
}
