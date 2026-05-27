package com.jahirtrap.cconnect.data.remote

// Single source of the backend address; every service reads from here.
object Backend {
    var host: String = ""
    var port: Int = 8723

    val baseUrl: String get() = "http://$host:$port/api"
    val wsUrl: String get() = "ws://$host:$port/api/chat/ws"
    val isConfigured: Boolean get() = host.isNotBlank()
}
