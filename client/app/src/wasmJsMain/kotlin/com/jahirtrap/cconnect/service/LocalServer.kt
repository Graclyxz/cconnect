package com.jahirtrap.cconnect.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object LocalServer {
    actual val status: StateFlow<LocalServerInfo> = MutableStateFlow(LocalServerInfo()).asStateFlow()
    actual fun start(config: LocalServerConfig) {}
    actual fun stop() {}
    actual fun restart(config: LocalServerConfig) {}
}

actual fun pickDirectory(): String? = null
actual fun pickExecutable(): String? = null
