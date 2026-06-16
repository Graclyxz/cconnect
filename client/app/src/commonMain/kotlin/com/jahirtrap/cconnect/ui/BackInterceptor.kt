package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

object BackInterceptors {
    private val stack = ArrayDeque<() -> Boolean>()

    fun add(handler: () -> Boolean) { stack.addLast(handler) }
    fun remove(handler: () -> Boolean) { stack.remove(handler) }

    fun handle(): Boolean {
        for (handler in stack.toList().asReversed()) if (handler()) return true
        return false
    }
}

@Composable
fun BackInterceptor(enabled: Boolean = true, onBack: () -> Boolean) {
    val current by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler: () -> Boolean = { current() }
        BackInterceptors.add(handler)
        onDispose { BackInterceptors.remove(handler) }
    }
}
