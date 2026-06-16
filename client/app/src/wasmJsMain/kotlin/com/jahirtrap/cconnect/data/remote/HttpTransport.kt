package com.jahirtrap.cconnect.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

private val client = HttpClient(Js)

internal actual suspend fun httpExecute(
    method: String,
    path: String,
    query: Map<String, String>,
    headers: List<Pair<String, String>>,
    jsonBody: String?,
): String? = runCatching {
    val base = "${Backend.baseUrl}$path"
    val url = if (query.isEmpty()) base
    else base + "?" + query.entries.joinToString("&") { "${UrlCodec.encode(it.key)}=${UrlCodec.encode(it.value)}" }
    val response = client.request(url) {
        this.method = HttpMethod.parse(method)
        headers.forEach { (name, value) -> header(name, value) }
        if (jsonBody != null) {
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
    }
    response.bodyAsText()
}.getOrNull()
