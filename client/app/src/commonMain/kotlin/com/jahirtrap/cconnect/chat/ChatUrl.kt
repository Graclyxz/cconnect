package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.Composable

expect fun readChatLocation(): Triple<Int, String, String>?

expect fun syncChatLocation(tab: Int, sessionId: String?, projectKey: String?)

@Composable
expect fun ChatPopstate(onLocation: (Int, String?, String?) -> Unit)
