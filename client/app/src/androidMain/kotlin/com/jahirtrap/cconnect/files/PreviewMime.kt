package com.jahirtrap.cconnect.files

import java.net.URLConnection

actual fun guessMimeType(filename: String): String? = URLConnection.guessContentTypeFromName(filename)
