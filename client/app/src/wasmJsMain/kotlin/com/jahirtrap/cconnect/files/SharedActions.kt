package com.jahirtrap.cconnect.files

actual suspend fun downloadShared(url: String, filename: String): Boolean {
    browserDownload(url, filename)
    return true
}

actual suspend fun saveSharedAs(url: String, filename: String): Boolean = downloadShared(url, filename)

actual suspend fun openSharedExternally(url: String, filename: String): Boolean {
    openInNewTab(url)
    return true
}

private fun browserDownload(url: String, filename: String) {
    js("{ const a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove(); }")
}

private fun openInNewTab(url: String) {
    js("window.open(url, '_blank')")
}
