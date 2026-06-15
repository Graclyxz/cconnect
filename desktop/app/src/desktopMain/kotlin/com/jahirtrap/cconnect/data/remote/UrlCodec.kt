package com.jahirtrap.cconnect.data.remote

import java.net.URLDecoder
import java.net.URLEncoder

object UrlCodec {
    fun encode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
    fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8)
}
