package com.jahirtrap.cconnect.files

private val MIME_BY_EXTENSION = mapOf(
    "html" to "text/html", "htm" to "text/html",
    "txt" to "text/plain", "text" to "text/plain",
    "css" to "text/css", "csv" to "text/csv",
    "xml" to "text/xml",
    "js" to "application/javascript",
    "json" to "application/json",
    "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
    "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
    "svg" to "image/svg+xml", "ico" to "image/x-icon",
    "pdf" to "application/pdf",
)

actual fun guessMimeType(filename: String): String? =
    MIME_BY_EXTENSION[filename.substringAfterLast('.', "").lowercase()]
