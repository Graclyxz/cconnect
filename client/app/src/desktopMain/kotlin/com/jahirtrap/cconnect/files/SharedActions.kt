package com.jahirtrap.cconnect.files

actual suspend fun downloadShared(url: String, filename: String): Boolean =
    FileTransfer.enqueueToDownloads(url, filename)

actual suspend fun saveSharedAs(url: String, filename: String): Boolean {
    val dest = FileDialogs.save(filename) ?: return false
    return FileTransfer.saveTo(url, dest)
}

actual suspend fun openSharedExternally(url: String, filename: String): Boolean =
    FileTransfer.openExternally(url, filename)
