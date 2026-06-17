package com.jahirtrap.cconnect.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

object AppUpdater {

    private val client = OkHttpClient()

    private fun dir(context: Context) = File(context.cacheDir, "updates")
    private fun marker(context: Context) = File(dir(context), "pending")

    suspend fun download(context: Context, url: String, version: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        clear(context)
        val dir = dir(context).apply { mkdirs() }
        val name = url.substringAfterLast('/').ifBlank { "CConnect-update.apk" }
        val file = File(dir, name)
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body ?: return@withContext false
                if (!resp.isSuccessful) return@withContext false
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
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
            marker(context).writeText("$version\n$name")
            true
        } catch (e: CancellationException) {
            clear(context)
            throw e
        } catch (_: Exception) {
            clear(context)
            false
        }
    }

    fun pendingVersion(context: Context): String? = runCatching {
        val m = marker(context)
        if (!m.isFile) return null
        val lines = m.readLines()
        val version = lines.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val name = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        if (File(dir(context), name).isFile) version else { clear(context); null }
    }.getOrNull()

    fun install(context: Context): Boolean = runCatching {
        val name = marker(context).readLines().getOrNull(1)?.takeIf { it.isNotBlank() } ?: return false
        val file = File(dir(context), name)
        if (!file.isFile) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)

    fun consumeIfInstalled(context: Context, currentVersion: String) {
        runCatching {
            val m = marker(context)
            if (!m.isFile) return
            val version = m.readLines().getOrNull(0)?.takeIf { it.isNotBlank() } ?: return
            if (AppCompat.compare(currentVersion, version) >= 0) clear(context)
        }
    }

    private fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }
}
