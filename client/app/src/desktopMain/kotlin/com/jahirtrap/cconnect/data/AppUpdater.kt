package com.jahirtrap.cconnect.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.net.URI
import kotlin.coroutines.coroutineContext

actual object AppUpdater {

    private val client = OkHttpClient()

    actual fun openRelease(url: String): Boolean =
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else false
        }.getOrDefault(false)

    actual suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val name = url.substringAfterLast('/').ifBlank { "CConnect-update" }
        val dest = File(System.getProperty("java.io.tmpdir"), name)
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body ?: return@withContext false
                if (!resp.isSuccessful) return@withContext false
                val total = body.contentLength()
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                            if (total > 0) onProgress(copied.toFloat() / total)
                        }
                    }
                }
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dest)
                true
            } else false
        } catch (e: CancellationException) {
            dest.delete()
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
