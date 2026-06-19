package com.jahirtrap.cconnect.data

import java.io.File

private val scratchFile: File
    get() = File(System.getProperty("user.home"), ".cconnect/markdown.md")

actual fun loadMarkdownScratch(): String =
    runCatching { scratchFile.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

actual fun saveMarkdownScratch(text: String) {
    runCatching {
        val file = scratchFile
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}
