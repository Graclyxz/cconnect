package com.jahirtrap.cconnect.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

object GitHubApi {
    private const val OWNER = "jahirxtrap"
    private const val REPO = "cconnect"
    const val REPO_URL = "https://github.com/$OWNER/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"

    private val client = OkHttpClient()

    data class Release(val tag: String, val url: String, val apkUrl: String?)
    data class Profile(val login: String, val name: String?, val avatarUrl: String, val url: String)

    private suspend fun fetch(url: String) = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else Json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                }
        }.getOrNull()
    }

    suspend fun latestRelease(): Release? {
        val o = fetch("https://api.github.com/repos/$OWNER/$REPO/releases/latest") ?: return null
        val apk = o["assets"]?.jsonArray?.firstNotNullOfOrNull { asset ->
            val a = asset.jsonObject
            a["browser_download_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.endsWith(".apk") }
        }
        return Release(
            tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null,
            url = o["html_url"]?.jsonPrimitive?.contentOrNull ?: REPO_URL,
            apkUrl = apk,
        )
    }

    suspend fun ownerProfile(): Profile? {
        val o = fetch("https://api.github.com/users/$OWNER") ?: return null
        return Profile(
            login = o["login"]?.jsonPrimitive?.contentOrNull ?: OWNER,
            name = o["name"]?.jsonPrimitive?.contentOrNull,
            avatarUrl = o["avatar_url"]?.jsonPrimitive?.contentOrNull ?: return null,
            url = o["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$OWNER",
        )
    }
}
