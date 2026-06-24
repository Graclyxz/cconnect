package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.Composable

actual fun readChatLocation(): Triple<Int, String, String>? = null

actual fun syncChatLocation(tab: Int, sessionId: String?, projectKey: String?) {}

@Composable
actual fun ChatPopstate(onLocation: (Int, String?, String?) -> Unit) {}
