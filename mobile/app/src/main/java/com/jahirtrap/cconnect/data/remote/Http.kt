package com.jahirtrap.cconnect.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

// Each verb returns the envelope's `data` on success, or null on failure.
object Http {

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement? =
        execute(Request.Builder().url(buildUrl(path, query)).get())

    suspend fun post(path: String, body: JsonObject? = null): JsonElement? =
        execute(Request.Builder().url(buildUrl(path)).post(jsonBody(body)))

    suspend fun put(path: String, body: JsonObject? = null): JsonElement? =
        execute(Request.Builder().url(buildUrl(path)).put(jsonBody(body)))

    suspend fun delete(path: String, query: Map<String, String> = emptyMap()): JsonElement? =
        execute(Request.Builder().url(buildUrl(path, query)).delete())

    private fun buildUrl(path: String, query: Map<String, String> = emptyMap()) =
        "${Backend.baseUrl}$path".toHttpUrl().newBuilder()
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

    private fun jsonBody(body: JsonObject?): RequestBody =
        (body?.toString() ?: "{}").toRequestBody(JSON_MEDIA_TYPE)

    private suspend fun execute(builder: Request.Builder): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: return@use null
                val obj = json.parseToJsonElement(text).jsonObject
                if (obj["success"]?.jsonPrimitive?.booleanOrNull == true) obj["data"] else null
            }
        }.getOrNull()
    }

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}
