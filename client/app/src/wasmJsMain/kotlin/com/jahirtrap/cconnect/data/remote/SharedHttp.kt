package com.jahirtrap.cconnect.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

private val sharedClient = HttpClient(Js)

internal actual suspend fun fetchSharedText(url: String): String? = runCatching {
    val response = sharedClient.get(url) {
        Backend.authHeaders.forEach { (name, value) -> header(name, value) }
    }
    if (response.status.isSuccess()) response.bodyAsText() else null
}.getOrNull()
