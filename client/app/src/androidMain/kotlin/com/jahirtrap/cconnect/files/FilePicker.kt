package com.jahirtrap.cconnect.files

var androidFilePicker: (suspend () -> List<AttachmentFile>)? = null

actual suspend fun pickFiles(): List<AttachmentFile> = androidFilePicker?.invoke() ?: emptyList()
