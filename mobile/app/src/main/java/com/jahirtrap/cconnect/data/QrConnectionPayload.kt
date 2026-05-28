package com.jahirtrap.cconnect.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class QrConnectionPayload(val url: String, val token: String) {
    companion object {
        fun parse(raw: String): QrConnectionPayload? = runCatching {
            val o = Json.parseToJsonElement(raw).jsonObject
            val url = o["url"]?.jsonPrimitive?.contentOrNull
            val token = o["token"]?.jsonPrimitive?.contentOrNull
            if (url.isNullOrBlank() || token.isNullOrBlank()) null
            else QrConnectionPayload(url, token)
        }.getOrNull()
    }
}
