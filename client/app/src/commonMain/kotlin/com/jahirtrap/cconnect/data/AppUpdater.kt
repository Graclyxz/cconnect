package com.jahirtrap.cconnect.data

expect object AppUpdater {
    fun openRelease(url: String): Boolean
    fun reload(): Boolean
    suspend fun download(url: String, version: String, onProgress: (Float) -> Unit = {}): Boolean
    fun pendingVersion(): String?
    fun install(): Boolean
    fun consumeIfInstalled(currentVersion: String)
}
