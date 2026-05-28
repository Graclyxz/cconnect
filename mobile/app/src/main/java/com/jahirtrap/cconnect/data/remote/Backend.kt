package com.jahirtrap.cconnect.data.remote

import android.util.Base64

// Single source of the backend address; every service reads from here.
object Backend {
    var kind: String = "http"
    var host: String = ""
    var port: Int? = null
    var authKind: String = "none"
    var authToken: String = ""
    var authUser: String = ""
    var authPassword: String = ""
    var authHeaderName: String = ""
    var authHeaderValue: String = ""

    private val secure: Boolean get() = kind == "https"
    private val portSuffix: String
        get() {
            val p = port ?: return ""
            val default = if (secure) 443 else 80
            return if (p == default) "" else ":$p"
        }

    val baseUrl: String get() = "${if (secure) "https" else "http"}://$host$portSuffix/api"
    val wsUrl: String get() = "${if (secure) "wss" else "ws"}://$host$portSuffix/api/chat/ws"
    val isConfigured: Boolean get() = host.isNotBlank()

    private val authorizationHeader: String?
        get() = when (authKind) {
            "bearer" -> if (authToken.isBlank()) null else "Bearer $authToken"
            "basic" -> if (authUser.isBlank() && authPassword.isBlank()) null
            else "Basic " + Base64.encodeToString("$authUser:$authPassword".toByteArray(), Base64.NO_WRAP)
            else -> null
        }

    val authHeaders: List<Pair<String, String>>
        get() = buildList {
            authorizationHeader?.let { add("Authorization" to it) }
            if (authKind == "header" && authHeaderName.isNotBlank() && authHeaderValue.isNotBlank()) {
                add(authHeaderName to authHeaderValue)
            }
        }
}
