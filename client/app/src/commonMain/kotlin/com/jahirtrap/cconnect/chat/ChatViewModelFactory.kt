package com.jahirtrap.cconnect.chat

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

val chatViewModelFactory = viewModelFactory { initializer { ChatViewModel() } }
