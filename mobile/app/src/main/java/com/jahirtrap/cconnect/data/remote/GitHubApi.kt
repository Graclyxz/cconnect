package com.jahirtrap.cconnect.data.remote

import android.content.Context
import com.jahirtrap.cconnect.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object GitHubApi {
    private const val OWNER = "jahirxtrap"
    private const val REPO = "cconnect"
    const val REPO_URL = "https://github.com/$OWNER/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"

    private val client = OkHttpClient()

    data class Release(val tag: String, val url: String, val apkUrl: String?)
    data class ReleaseNotes(val tag: String, val body: String)
    data class Profile(val login: String, val name: String?, val avatarUrl: String, val url: String)

    private suspend fun fetch(url: String): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) null
                    else Json.parseToJsonElement(resp.body?.string().orEmpty())
                }
        }.getOrNull()
    }

    private suspend fun readCache(file: File): JsonElement? = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) Json.parseToJsonElement(file.readText()) else null }.getOrNull()
    }

    private suspend fun writeCache(file: File, value: JsonElement) = withContext(Dispatchers.IO) {
        runCatching { file.writeText(value.toString()) }
    }

    suspend fun latestRelease(): Release? {
        val o = fetch("https://api.github.com/repos/$OWNER/$REPO/releases/latest")?.jsonObject ?: return null
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

    suspend fun releaseNotes(context: Context): List<ReleaseNotes>? {
        val file = File(context.filesDir, "github_changelog.json")
        val cached = readCache(file)?.jsonObject
        val cachedItems = cached?.get("items")?.jsonArray?.let(::notesOf)
        if (cached?.get("version")?.jsonPrimitive?.contentOrNull == BuildConfig.VERSION_NAME && cachedItems != null) {
            return cachedItems
        }
        val fetched = fetch("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=50")?.jsonArray
        if (fetched != null) {
            writeCache(file, buildJsonObject {
                put("version", BuildConfig.VERSION_NAME)
                putJsonArray("items") {
                    notesOf(fetched).forEach { note ->
                        addJsonObject {
                            put("tag_name", note.tag)
                            put("body", note.body)
                        }
                    }
                }
            })
            return notesOf(fetched)
        }
        return cachedItems
    }

    private fun notesOf(items: JsonArray): List<ReleaseNotes> = items.mapNotNull { el ->
        val o = el.jsonObject
        ReleaseNotes(
            tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            body = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    // Fetched once and kept forever.
    suspend fun ownerProfile(context: Context): Profile? {
        val file = File(context.filesDir, "github_profile.json")
        readCache(file)?.jsonObject?.let(::profileOf)?.let { return it }
        val o = fetch("https://api.github.com/users/$OWNER")?.jsonObject ?: return null
        val profile = profileOf(o) ?: return null
        writeCache(file, buildJsonObject {
            put("login", profile.login)
            profile.name?.let { put("name", it) }
            put("avatar_url", profile.avatarUrl)
            put("html_url", profile.url)
        })
        return profile
    }

    private fun profileOf(o: JsonObject): Profile? {
        return Profile(
            login = o["login"]?.jsonPrimitive?.contentOrNull ?: OWNER,
            name = o["name"]?.jsonPrimitive?.contentOrNull,
            avatarUrl = o["avatar_url"]?.jsonPrimitive?.contentOrNull ?: return null,
            url = o["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$OWNER",
        )
    }
}
