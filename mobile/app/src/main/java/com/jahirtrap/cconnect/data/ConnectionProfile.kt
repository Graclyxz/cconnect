package com.jahirtrap.cconnect.data

data class ConnectionProfile(
    val id: String,
    val name: String,
    val kind: String = "http",            // "http" | "https"
    val host: String,
    val port: Int? = null,                // null = implicit default for the scheme (443 for https)
    val authKind: String = "none",        // "none" | "bearer" | "basic" | "header"
    val authToken: String = "",
    val authUser: String = "",
    val authPassword: String = "",
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val directory: String = "",
) {
    val address: String get() = if (port != null) "$host:$port" else host
}
