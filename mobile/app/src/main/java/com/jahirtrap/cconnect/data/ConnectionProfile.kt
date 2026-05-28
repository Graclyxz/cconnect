package com.jahirtrap.cconnect.data

data class ConnectionProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    // Empty = the backend's own working folder.
    val directory: String = "",
)
