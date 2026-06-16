package com.jahirtrap.cconnect.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

private val githubClient = HttpClient(Js)

internal actual suspend fun githubGetText(url: String, accept: String?): String? = runCatching {
    val response = githubClient.get(url) { if (accept != null) header("Accept", accept) }
    if (response.status.isSuccess()) response.bodyAsText() else null
}.getOrNull()

internal actual suspend fun githubReadCache(name: String): String? = null

internal actual suspend fun githubWriteCache(name: String, content: String) {}

internal actual val installerExtensions: List<String> = emptyList()
