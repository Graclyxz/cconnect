package com.jahirtrap.cconnect.files

actual suspend fun pickFiles(): List<AttachmentFile> =
    FileDialogs.openMultiple().map { AttachmentFile(it) }
