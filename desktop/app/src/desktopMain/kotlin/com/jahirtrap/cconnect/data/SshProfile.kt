package com.jahirtrap.cconnect.data

data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String,
    val os: String? = null,
) {
    val address: String get() = "$user@$host${if (port != 22) ":$port" else ""}"
}
