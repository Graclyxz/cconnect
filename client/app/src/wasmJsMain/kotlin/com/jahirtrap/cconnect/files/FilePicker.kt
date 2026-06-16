package com.jahirtrap.cconnect.files

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import kotlin.coroutines.resume

actual suspend fun pickFiles(): List<AttachmentFile> = suspendCancellableCoroutine { cont ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.multiple = true
    input.onchange = { _ ->
        val files = input.files
        val result = if (files != null) {
            (0 until files.length).mapNotNull { i -> files.item(i)?.let { AttachmentFile(it) } }
        } else {
            emptyList()
        }
        cont.resume(result)
    }
    input.click()
}
