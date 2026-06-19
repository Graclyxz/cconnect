package com.jahirtrap.cconnect

internal actual val isWebPlatform: Boolean = false

internal actual val isAndroidPlatform: Boolean = true

internal actual val supportsTraySetting: Boolean = false

internal actual fun isCoarsePointer(): Boolean = true

internal var bringToFront: (() -> Unit)? = null

internal actual fun bringAppToFront() {
    bringToFront?.invoke()
}
