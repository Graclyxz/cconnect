package com.jahirtrap.cconnect.data

actual object AppUpdater {

    actual fun openRelease(url: String): Boolean {
        openUrl(url)
        return true
    }

    actual suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit): Boolean = false

    actual fun reload(): Boolean {
        reloadFresh()
        return true
    }
}

private fun openUrl(url: String) {
    js("window.open(url, '_blank')")
}

private fun reloadFresh() {
    js("(function(){ if (self.caches && caches.keys) { caches.keys().then(function(ks){ return Promise.all(ks.map(function(k){ return caches.delete(k); })); }).then(function(){ location.reload(); }, function(){ location.reload(); }); } else { location.reload(); } })()")
}
