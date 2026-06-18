package com.jahirtrap.cconnect.files

import android.net.Uri
import android.provider.OpenableColumns
import com.jahirtrap.cconnect.appContext
import com.jahirtrap.cconnect.data.remote.Backend
import com.jahirtrap.cconnect.data.remote.SharedApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private val uploadClient = OkHttpClient.Builder().writeTimeout(0, TimeUnit.MILLISECONDS).build()
private val OCTET_STREAM = "application/octet-stream".toMediaType()

actual class AttachmentFile(val uri: Uri) {

    private val metadata: Pair<String, Long> by lazy { metadataOf(uri) }

    actual val name: String get() = metadata.first
    actual val size: Long get() = metadata.second

    fun openStream(): InputStream? = appContext.contentResolver.openInputStream(uri)
}

private fun metadataOf(uri: Uri): Pair<String, Long> {
    val resolver = appContext.contentResolver
    return resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
        val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
        (name ?: uri.lastPathSegment ?: "file") to size
    } ?: ((uri.lastPathSegment ?: "file") to -1L)
}

actual suspend fun uploadAttachment(
    file: AttachmentFile,
    path: String,
    onProgress: (Float) -> Unit,
): String? = withContext(Dispatchers.IO) {
    val length = file.size
    runCatching {
        val body = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun contentLength() = if (length > 0) length else -1
            override fun writeTo(sink: BufferedSink) {
                val input = file.openStream() ?: throw IOException("cannot open source")
                input.use {
                    val buffer = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        sent += read
                        if (length > 0) onProgress(sent.toFloat() / length)
                    }
                }
            }
        }
        val request = Request.Builder().url(SharedApi.downloadUrl(path)).put(body).apply {
            Backend.authHeaders.forEach { (name, value) -> header(name, value) }
        }.build()
        val call = uploadClient.newCall(request)
        coroutineContext.job.invokeOnCompletion { if (it is CancellationException) call.cancel() }
        call.execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            Json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["data"]?.jsonObject?.get("path")?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()
}
