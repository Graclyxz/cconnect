package com.jahirtrap.cconnect.data

expect object AppUpdater {
    fun openRelease(url: String): Boolean
    suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit = {}): Boolean
    fun reload(): Boolean
}
