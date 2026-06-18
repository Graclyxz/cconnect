package com.jahirtrap.cconnect.data.remote

import com.jahirtrap.cconnect.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private val githubClient = OkHttpClient()
private val githubCacheDir: File by lazy { File(appContext.cacheDir, "github").apply { mkdirs() } }

internal actual suspend fun githubGetText(url: String, accept: String?): String? = withContext(Dispatchers.IO) {
    runCatching {
        val builder = Request.Builder().url(url)
        if (accept != null) builder.header("Accept", accept)
        githubClient.newCall(builder.build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()
}

internal actual suspend fun githubReadCache(name: String): String? = withContext(Dispatchers.IO) {
    runCatching { File(githubCacheDir, name).takeIf { it.exists() }?.readText() }.getOrNull()
}

internal actual suspend fun githubWriteCache(name: String, content: String) {
    withContext(Dispatchers.IO) {
        runCatching { File(githubCacheDir, name).writeText(content) }
    }
}

internal actual val installerExtensions: List<String> = listOf(".apk")
