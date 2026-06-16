package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.Composable

expect fun readChatLocation(): Pair<String, String>?

expect fun syncChatLocation(sessionId: String?, projectKey: String?)

@Composable
expect fun ChatPopstate(onLocation: (String?, String?) -> Unit)
