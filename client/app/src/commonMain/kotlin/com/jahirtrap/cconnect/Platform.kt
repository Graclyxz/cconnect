package com.jahirtrap.cconnect

internal expect val isWebPlatform: Boolean

internal expect val isAndroidPlatform: Boolean

internal expect val supportsTraySetting: Boolean

internal expect fun isCoarsePointer(): Boolean

internal expect fun bringAppToFront()
