package com.jahirtrap.cconnect.files

import com.jahirtrap.cconnect.data.remote.uploadShared
import java.io.File

actual class AttachmentFile(val file: File) {
    actual val name: String get() = file.name
    actual val size: Long get() = file.length()
}

actual suspend fun uploadAttachment(
    file: AttachmentFile,
    path: String,
    onProgress: (Float) -> Unit,
): String? = uploadShared(path, file.size, { file.file.inputStream() }, onProgress)
