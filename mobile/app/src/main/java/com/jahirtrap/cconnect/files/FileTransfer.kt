package com.jahirtrap.cconnect.files

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object FileTransfer {

    private val client = OkHttpClient()

    fun enqueueToDownloads(context: Context, url: String, filename: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setMimeType(mimeOf(filename))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }

    suspend fun saveTo(context: Context, url: String, dest: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body ?: return@use false
                context.contentResolver.openOutputStream(dest)?.use { out -> body.byteStream().copyTo(out) } != null
            }
        }.getOrDefault(false)
    }

    suspend fun shareIntent(context: Context, url: String, filename: String): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, filename)
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body ?: return@withContext null
                file.outputStream().use { body.byteStream().copyTo(it) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(filename)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }

    private fun mimeOf(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
