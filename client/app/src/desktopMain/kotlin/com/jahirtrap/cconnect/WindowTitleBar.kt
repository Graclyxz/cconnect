package com.jahirtrap.cconnect

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference

object WindowTitleBar {
    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    private val dwm: Dwmapi? by lazy {
        runCatching { Native.load("dwmapi", Dwmapi::class.java) as Dwmapi }.getOrNull()
    }

    fun apply(window: java.awt.Window, dark: Boolean) {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return
        runCatching {
            val api = dwm ?: return
            val pointer = Native.getComponentPointer(window) ?: return
            val hwnd = WinDef.HWND(pointer)
            api.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(if (dark) 1 else 0), 4)
        }
    }
}
