package com.jahirtrap.cconnect.files

import com.jahirtrap.cconnect.data.remote.Backend

actual suspend fun downloadShared(url: String, filename: String): Boolean {
    val header = Backend.authHeaders.firstOrNull()
    browserDownload(url, filename, header?.first ?: "", header?.second ?: "")
    return true
}

actual suspend fun saveSharedAs(url: String, filename: String): Boolean = downloadShared(url, filename)

actual suspend fun openSharedExternally(url: String, filename: String): Boolean {
    val header = Backend.authHeaders.firstOrNull()
    openInNewTab(url, header?.first ?: "", header?.second ?: "")
    return true
}

actual suspend fun saveAllShared(items: List<Pair<String, String>>): Boolean {
    items.forEach { (url, name) -> downloadShared(url, name) }
    return true
}

actual suspend fun openAllSharedExternally(items: List<Pair<String, String>>): Boolean {
    val header = Backend.authHeaders.firstOrNull()
    items.forEach { (url, _) -> openInNewTab(url, header?.first ?: "", header?.second ?: "") }
    return true
}

private fun browserDownload(url: String, filename: String, headerName: String, headerValue: String) {
    js(
        """{
        const headers = headerName ? { [headerName]: headerValue } : {};
        fetch(url, { headers: headers })
          .then(function(r) { return r.blob(); })
          .then(function(b) {
            const objUrl = URL.createObjectURL(b);
            const a = document.createElement('a');
            a.href = objUrl;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(objUrl);
          });
    }"""
    )
}

private fun openInNewTab(url: String, headerName: String, headerValue: String) {
    js(
        """{
        const headers = headerName ? { [headerName]: headerValue } : {};
        fetch(url, { headers: headers })
          .then(function(r) { return r.blob(); })
          .then(function(b) { window.open(URL.createObjectURL(b), '_blank'); });
    }"""
    )
}
