package com.jahirtrap.cconnect.data

import com.jahirtrap.cconnect.appContext
import java.io.File

private val scratchFile: File
    get() = File(appContext.filesDir, "markdown.md")

actual fun loadMarkdownScratch(): String =
    runCatching { scratchFile.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

actual fun saveMarkdownScratch(text: String) {
    runCatching { scratchFile.writeText(text) }
}
