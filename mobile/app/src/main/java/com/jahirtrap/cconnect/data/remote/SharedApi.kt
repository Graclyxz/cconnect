package com.jahirtrap.cconnect.data.remote

import android.net.Uri
import com.jahirtrap.cconnect.data.SharedEntry
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SharedApi {

    suspend fun list(path: String = ""): List<SharedEntry>? {
        val query = if (path.isEmpty()) emptyMap() else mapOf("path" to path)
        return Http.get("/shared", query)?.jsonArray?.map { el ->
            val o = el.jsonObject
            SharedEntry(
                name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                isDir = o["is_dir"]?.jsonPrimitive?.booleanOrNull ?: false,
                size = o["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                modified = o["modified"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }
    }

    suspend fun delete(path: String): Boolean = Http.delete("/shared/${encode(path)}") != null

    fun downloadUrl(path: String): String = "${Backend.baseUrl}/shared/${encode(path)}"

    private fun encode(path: String): String =
        path.split("/").filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
}
