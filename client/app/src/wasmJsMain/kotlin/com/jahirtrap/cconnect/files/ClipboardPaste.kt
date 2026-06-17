package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.clipboard.ClipboardEvent
import org.w3c.dom.events.Event

@Composable
actual fun ClipboardPasteEffect(enabled: Boolean, onFiles: (List<AttachmentFile>) -> Unit) {
    if (!enabled) return
    val current by rememberUpdatedState(onFiles)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { event ->
            val files = (event as? ClipboardEvent)?.clipboardData?.files
            if (files != null && files.length > 0) {
                val result = ArrayList<AttachmentFile>()
                var i = 0
                while (i < files.length) {
                    files.item(i)?.let { result.add(AttachmentFile(it)) }
                    i++
                }
                if (result.isNotEmpty()) current(result)
            }
        }
        document.addEventListener("paste", listener)
        onDispose { document.removeEventListener("paste", listener) }
    }
}

actual fun clipboardHasFiles(): Boolean = false

actual suspend fun readClipboardFiles(): List<AttachmentFile> = emptyList()
