package com.jahirtrap.cconnect.data

actual object AppUpdater {

    actual fun openRelease(url: String): Boolean {
        openUrl(url)
        return true
    }

    actual suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit): Boolean = false
}

private fun openUrl(url: String) {
    js("window.open(url, '_blank')")
}
