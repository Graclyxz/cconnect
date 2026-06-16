package com.jahirtrap.cconnect.data.remote

actual object UrlCodec {
    actual fun encode(s: String): String = jsEncodeURIComponent(s)
    actual fun decode(s: String): String = jsDecodeURIComponent(s)
}

private fun jsEncodeURIComponent(s: String): String = js("encodeURIComponent(s)")
private fun jsDecodeURIComponent(s: String): String = js("decodeURIComponent(s)")
