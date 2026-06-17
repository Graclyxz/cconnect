package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

object HistoryNav {
    private val backStack = ArrayDeque<() -> Boolean>()
    private val forwardStack = ArrayDeque<() -> Boolean>()

    fun addBack(handler: () -> Boolean) { backStack.addLast(handler) }
    fun removeBack(handler: () -> Boolean) { backStack.remove(handler) }
    fun addForward(handler: () -> Boolean) { forwardStack.addLast(handler) }
    fun removeForward(handler: () -> Boolean) { forwardStack.remove(handler) }

    fun back(): Boolean {
        for (handler in backStack.toList().asReversed()) if (handler()) return true
        return false
    }

    fun forward(): Boolean {
        for (handler in forwardStack.toList().asReversed()) if (handler()) return true
        return false
    }
}

@Composable
fun HistoryNavHandler(enabled: Boolean = true, onBack: () -> Boolean, onForward: () -> Boolean) {
    val currentBack by rememberUpdatedState(onBack)
    val currentForward by rememberUpdatedState(onForward)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val back: () -> Boolean = { currentBack() }
        val forward: () -> Boolean = { currentForward() }
        HistoryNav.addBack(back)
        HistoryNav.addForward(forward)
        onDispose {
            HistoryNav.removeBack(back)
            HistoryNav.removeForward(forward)
        }
    }
}
