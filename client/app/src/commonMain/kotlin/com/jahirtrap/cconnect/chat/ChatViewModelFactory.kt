package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

fun chatViewModelFactory(ctx: TabContext): ViewModelProvider.Factory =
    viewModelFactory { initializer { ChatViewModel(ctx) } }

val LocalChatViewModelFactory = staticCompositionLocalOf<ViewModelProvider.Factory> {
    error("LocalChatViewModelFactory not provided")
}
