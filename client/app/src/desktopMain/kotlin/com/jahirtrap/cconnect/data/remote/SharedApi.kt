package com.jahirtrap.cconnect.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import com.jahirtrap.cconnect.data.SharedEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

object SharedApi {

    suspend fun list(path: String = ""): List<SharedEntry>? {
        val query = if (path.isEmpty()) emptyMap() else mapOf("path" to path)
        return Http.get("/shared", query)?.jsonArray?.map(::parseEntry)
    }

    suspend fun delete(path: String): Boolean = Http.delete("/shared/${encode(path)}") != null

    suspend fun search(path: String, query: String): List<SharedEntry>? =
        Http.get("/shared-search", mapOf("q" to query, "path" to path))?.jsonArray?.map(::parseEntry)

    suspend fun archiveList(path: String, inner: String = ""): List<SharedEntry>? =
        Http.get("/shared-archive", mapOf("path" to path, "inner" to inner))?.jsonArray?.map(::parseEntry)

    fun archiveFileUrl(path: String, inner: String): String =
        "${Backend.baseUrl}/shared-archive-file?path=${UrlCodec.encode(path)}&inner=${UrlCodec.encode(inner)}"

    private fun parseEntry(el: JsonElement): SharedEntry {
        val o = el.jsonObject
        return SharedEntry(
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            isDir = o["is_dir"]?.jsonPrimitive?.booleanOrNull ?: false,
            size = o["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            modified = o["modified"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            items = o["items"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    suspend fun mkdir(path: String): Boolean =
        Http.post("/shared/folder", buildJsonObject { put("path", path) }) != null

    suspend fun rename(path: String, name: String): Boolean =
        Http.post("/shared/rename", buildJsonObject { put("path", path); put("name", name) }) != null

    suspend fun absolutePaths(paths: List<String>): List<String>? =
        Http.post("/shared/paths", buildJsonObject { putJsonArray("paths") { paths.forEach { add(it) } } })
            ?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

    suspend fun move(paths: List<String>, dest: String): Boolean =
        Http.post("/shared/move", transferBody(paths, dest)) != null

    suspend fun copy(paths: List<String>, dest: String): Boolean =
        Http.post("/shared/copy", transferBody(paths, dest)) != null

    private fun transferBody(paths: List<String>, dest: String) = buildJsonObject {
        putJsonArray("paths") { paths.forEach { add(it) } }
        put("dest", dest)
    }

    suspend fun compressFormats(): List<String>? =
        Http.get("/shared-capabilities")?.jsonObject?.get("compress_formats")
            ?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }

    suspend fun compress(paths: List<String>, format: String = "zip", name: String? = null): Boolean =
        Http.post("/shared/compress", buildJsonObject {
            putJsonArray("paths") { paths.forEach { add(it) } }
            put("format", format)
            if (name != null) put("name", name)
        }) != null

    suspend fun extract(
        path: String,
        dest: String? = null,
        intoFolder: Boolean = true,
        members: List<String>? = null,
        base: String = "",
    ): Boolean = Http.post("/shared/extract", buildJsonObject {
        put("path", path)
        if (dest != null) put("dest", dest)
        put("into_folder", intoFolder)
        if (members != null) putJsonArray("members") { members.forEach { add(it) } }
        if (base.isNotEmpty()) put("base", base)
    }) != null

    suspend fun upload(
        path: String,
        length: Long,
        open: () -> InputStream?,
        onProgress: (Float) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = object : RequestBody() {
                override fun contentType() = OCTET_STREAM
                override fun contentLength() = if (length > 0) length else -1
                override fun writeTo(sink: BufferedSink) {
                    val input = open() ?: throw IOException("cannot open source")
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
            val request = Request.Builder().url(downloadUrl(path)).put(body).apply {
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

    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).apply {
                Backend.authHeaders.forEach { (name, value) -> header(name, value) }
            }.build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private val client = OkHttpClient()

    private val uploadClient = OkHttpClient.Builder().writeTimeout(0, TimeUnit.MILLISECONDS).build()
    private val OCTET_STREAM = "application/octet-stream".toMediaType()

    fun downloadUrl(path: String): String = "${Backend.baseUrl}/shared/${encode(path)}"

    fun relativeFromUrl(url: String): String? {
        val prefix = "${Backend.baseUrl}/shared/"
        if (!url.startsWith(prefix)) return null
        return url.removePrefix(prefix).substringBefore('?')
            .split("/").joinToString("/") { UrlCodec.decode(it) }
    }

    private fun encode(path: String): String =
        path.split("/").filter { it.isNotEmpty() }.joinToString("/") { UrlCodec.encode(it) }
}
