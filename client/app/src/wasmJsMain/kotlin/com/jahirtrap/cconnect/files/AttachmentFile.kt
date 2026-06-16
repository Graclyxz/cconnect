package com.jahirtrap.cconnect.files

import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.UrlCodec
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.files.File
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume

actual class AttachmentFile(val file: File) {
    actual val name: String get() = file.name
    actual val size: Long get() = file.size.toDouble().toLong()
}

actual suspend fun uploadAttachment(
    file: AttachmentFile,
    path: String,
    onProgress: (Float) -> Unit,
): String? = suspendCancellableCoroutine { cont ->
    val xhr = XMLHttpRequest()
    xhr.open("PUT", uploadUrl(path))
    Backend.authHeaders.forEach { (name, value) -> xhr.setRequestHeader(name, value) }
    xhr.onload = { _ ->
        val saved = if (xhr.status.toInt() in 200..299) runCatching {
            Json.parseToJsonElement(xhr.responseText)
                .jsonObject["data"]?.jsonObject?.get("path")?.jsonPrimitive?.contentOrNull
        }.getOrNull() else null
        if (saved != null) onProgress(1f)
        cont.resume(saved)
    }
    xhr.onerror = { _ -> cont.resume(null) }
    cont.invokeOnCancellation { runCatching { xhr.abort() } }
    xhr.send(file.file)
}

private fun uploadUrl(path: String): String =
    "${Backend.baseUrl}/shared/" + path.split("/").filter { it.isNotEmpty() }.joinToString("/") { UrlCodec.encode(it) }
