package com.jahirtrap.cconnect.data

import java.awt.Desktop
import java.net.URI

object AppUpdater {

    fun openRelease(url: String): Boolean =
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else false
        }.getOrDefault(false)
}
