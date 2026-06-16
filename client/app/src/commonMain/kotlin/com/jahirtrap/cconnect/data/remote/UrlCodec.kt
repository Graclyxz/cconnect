package com.jahirtrap.cconnect.data.remote

expect object UrlCodec {
    fun encode(s: String): String
    fun decode(s: String): String
}
