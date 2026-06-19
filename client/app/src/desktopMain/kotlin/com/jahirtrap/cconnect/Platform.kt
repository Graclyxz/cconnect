package com.jahirtrap.cconnect

internal actual val isWebPlatform: Boolean = false

internal actual val isAndroidPlatform: Boolean = false

internal actual val supportsTraySetting: Boolean
    get() = !System.getProperty("os.name").orEmpty().lowercase().contains("linux") &&
        runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false)

internal actual fun isCoarsePointer(): Boolean = false

internal var desktopWindowToFront: (() -> Unit)? = null

internal actual fun bringAppToFront() {
    desktopWindowToFront?.invoke()
}
