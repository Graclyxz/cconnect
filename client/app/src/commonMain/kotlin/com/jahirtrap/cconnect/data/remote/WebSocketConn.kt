package com.jahirtrap.cconnect.data.remote

interface WsListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onClosed(reason: String)
    fun onFailure(reason: String)
}

expect class WsConnection {
    fun send(text: String)
    fun cancel()
    fun close(code: Int, reason: String?)
}

expect fun openWebSocket(url: String, headers: List<Pair<String, String>>, listener: WsListener): WsConnection
