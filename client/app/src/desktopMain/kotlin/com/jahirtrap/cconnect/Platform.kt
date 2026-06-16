package com.jahirtrap.cconnect

internal actual val isWebPlatform: Boolean = false

internal actual fun isCoarsePointer(): Boolean = false

internal var desktopWindowToFront: (() -> Unit)? = null

internal actual fun bringAppToFront() {
    desktopWindowToFront?.invoke()
}
