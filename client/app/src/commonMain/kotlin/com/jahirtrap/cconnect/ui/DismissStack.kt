package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

object DismissStack {
    private val stack = ArrayDeque<() -> Unit>()

    fun add(handler: () -> Unit) { stack.addLast(handler) }
    fun remove(handler: () -> Unit) { stack.remove(handler) }

    fun isNotEmpty(): Boolean = stack.isNotEmpty()

    fun dismissTop(): Boolean {
        val handler = stack.lastOrNull() ?: return false
        handler()
        return true
    }
}

@Composable
fun Dismissable(enabled: Boolean = true, onDismiss: () -> Unit) {
    val current by rememberUpdatedState(onDismiss)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler: () -> Unit = { current() }
        DismissStack.add(handler)
        onDispose { DismissStack.remove(handler) }
    }
}
