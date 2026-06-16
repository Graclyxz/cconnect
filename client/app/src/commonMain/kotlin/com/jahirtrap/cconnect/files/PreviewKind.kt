package com.jahirtrap.cconnect.files

private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")

private val TEXT_APPLICATION_MIMES = setOf(
    "application/json", "application/xml", "application/javascript", "application/typescript",
    "application/x-sh", "application/x-yaml", "application/yaml", "application/toml",
    "application/sql", "application/x-bat",
)
private val TEXT_FALLBACK_EXTENSIONS = setOf(
    "kt", "kts", "gradle", "toml", "ini", "cfg", "conf", "properties", "env", "yml", "yaml",
    "ts", "tsx", "jsx", "rs", "go", "ps1", "diff", "patch", "log", "lock",
)

expect fun guessMimeType(filename: String): String?

enum class PreviewKind { Image, Markdown, Html, Text, None }

fun previewKindOf(filename: String): PreviewKind {
    val extension = filename.substringAfterLast('.', "").lowercase()
    if (extension in MARKDOWN_EXTENSIONS) return PreviewKind.Markdown
    val mime = guessMimeType(filename)
    return when {
        mime == "text/html" -> PreviewKind.Html
        mime?.startsWith("image/") == true -> PreviewKind.Image
        mime?.startsWith("text/") == true -> PreviewKind.Text
        mime in TEXT_APPLICATION_MIMES -> PreviewKind.Text
        extension in TEXT_FALLBACK_EXTENSIONS -> PreviewKind.Text
        else -> PreviewKind.None
    }
}

fun isPreviewable(filename: String): Boolean = previewKindOf(filename) != PreviewKind.None
