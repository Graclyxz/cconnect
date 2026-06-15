package com.jahirtrap.cconnect

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CConnect",
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        MaterialTheme {
            Text("CConnect Desktop")
        }
    }
}
