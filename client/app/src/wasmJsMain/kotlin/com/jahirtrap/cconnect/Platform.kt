package com.jahirtrap.cconnect

internal actual val isWebPlatform: Boolean = true

internal actual val isAndroidPlatform: Boolean = false

internal actual val supportsTraySetting: Boolean = false

internal actual fun isCoarsePointer(): Boolean =
    js("((window.matchMedia && window.matchMedia('(pointer: coarse)').matches) || navigator.maxTouchPoints > 0)")

internal actual fun bringAppToFront() {}
