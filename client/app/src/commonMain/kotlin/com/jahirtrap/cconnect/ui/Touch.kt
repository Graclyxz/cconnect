package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.jahirtrap.cconnect.isCoarsePointer

val LocalIsTouch = compositionLocalOf { false }

@Composable
fun ProvideIsTouch(content: @Composable () -> Unit) {
    var touch by remember { mutableStateOf(isCoarsePointer()) }
    CompositionLocalProvider(LocalIsTouch provides touch) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val type = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()?.type
                            if (type == PointerType.Touch) touch = true
                            else if (type == PointerType.Mouse) touch = false
                        }
                    }
                },
        ) {
            content()
        }
    }
}
