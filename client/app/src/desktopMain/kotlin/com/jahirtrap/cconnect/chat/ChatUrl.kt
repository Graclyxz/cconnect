package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.Composable

actual fun readChatLocation(): Pair<String, String>? = null

actual fun syncChatLocation(sessionId: String?, projectKey: String?) {}

actual fun openChatInNewTab(sessionId: String, projectKey: String) {}

@Composable
actual fun ChatPopstate(onLocation: (String?, String?) -> Unit) {}
