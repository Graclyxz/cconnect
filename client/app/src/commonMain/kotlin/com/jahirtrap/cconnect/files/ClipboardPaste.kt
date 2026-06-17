package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable

@Composable
expect fun ClipboardPasteEffect(enabled: Boolean, onFiles: (List<AttachmentFile>) -> Unit)

expect fun clipboardHasFiles(): Boolean

expect suspend fun readClipboardFiles(): List<AttachmentFile>
