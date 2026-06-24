package com.jahirtrap.cconnect.files

import com.jahirtrap.cconnect.data.remote.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File

object FileTransfer {

    private val client = OkHttpClient()
    private val downloadsDir: File get() = userDownloadsDir().apply { mkdirs() }

    fun userDownloadsDir(): File {
        val home = System.getProperty("user.home")
        if (System.getProperty("os.name").orEmpty().lowercase().contains("linux")) {
            runCatching {
                val cfg = File(home, ".config/user-dirs.dirs")
                if (cfg.isFile) {
                    cfg.readLines().firstOrNull { it.trimStart().startsWith("XDG_DOWNLOAD_DIR") }?.let { line ->
                        val raw = line.substringAfter('=').trim().trim('"').replace("\$HOME", home)
                        if (raw.isNotBlank()) return File(raw)
                    }
                }
            }
        }
        return File(home, "Downloads")
    }

    suspend fun enqueueToDownloads(url: String, filename: String): Boolean =
        download(url, dedup(downloadsDir, filename))

    suspend fun saveTo(url: String, dest: File): Boolean = download(url, dest)

    suspend fun saveAllTo(files: List<Pair<String, String>>, dir: File): Boolean = withContext(Dispatchers.IO) {
        files.all { (url, filename) -> download(url, dedup(dir, filename)) }
    }

    suspend fun openExternally(url: String, filename: String): Boolean = withContext(Dispatchers.IO) {
        val file = dedup(File(System.getProperty("java.io.tmpdir"), "cconnect"), filename)
        if (!download(url, file)) return@withContext false
        runCatching { Desktop.getDesktop().open(file) }.isSuccess
    }

    suspend fun openMultipleExternally(files: List<Pair<String, String>>): Boolean = withContext(Dispatchers.IO) {
        files.all { (url, filename) -> openExternally(url, filename) }
    }

    private suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            client.newCall(authorizedRequest(url)).execute().use { resp ->
                val body = resp.body ?: return@use false
                if (!resp.isSuccessful) return@use false
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                true
            }
        }.getOrDefault(false)
    }

    private fun dedup(dir: File, name: String): File {
        dir.mkdirs()
        var target = File(dir, name)
        if (!target.exists()) return target
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (target.exists()) {
            target = File(dir, "$stem ($n)$ext")
            n++
        }
        return target
    }

    private fun authorizedRequest(url: String): Request {
        val builder = Request.Builder().url(url)
        if (url.startsWith(Backend.baseUrl)) {
            Backend.authHeaders.forEach { (name, value) -> builder.header(name, value) }
        }
        return builder.build()
    }
}
