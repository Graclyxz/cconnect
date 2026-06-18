package com.jahirtrap.cconnect.files

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import com.jahirtrap.cconnect.appContext

@Composable
actual fun ClipboardPasteEffect(enabled: Boolean, onFiles: (List<AttachmentFile>) -> Unit) {
}

private fun clipboardManager(): ClipboardManager? =
    appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

private fun clipboardUris(): List<Uri> {
    val clip = clipboardManager()?.primaryClip ?: return emptyList()
    val uris = ArrayList<Uri>()
    for (i in 0 until clip.itemCount) {
        clip.getItemAt(i)?.uri?.let { uris.add(it) }
    }
    return uris
}

actual fun clipboardHasFiles(): Boolean = clipboardUris().isNotEmpty()

actual suspend fun readClipboardFiles(): List<AttachmentFile> =
    clipboardUris().map { AttachmentFile(it) }
