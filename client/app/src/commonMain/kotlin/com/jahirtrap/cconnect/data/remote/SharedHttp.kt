package com.jahirtrap.cconnect.data.remote

internal expect suspend fun fetchSharedText(url: String): String?
