package com.jahirtrap.cconnect.service

import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

object Notifier {

    data class Action(val label: String, val requestId: String, val optionId: String)

    enum class Kind(val notifId: Int) {
        TaskDone(2),
        Interaction(3),
    }

    @Volatile
    var appInForeground: Boolean = false

    private var onActivate: (() -> Unit)? = null
    private var trayIcon: TrayIcon? = null

    fun init(onActivate: () -> Unit) {
        this.onActivate = onActivate
    }

    fun notify(kind: Kind, title: String, text: String?, actions: List<Action> = emptyList()) {
        if (appInForeground) return
        val tray = ensureTray() ?: return
        tray.displayMessage(title, text.orEmpty(), TrayIcon.MessageType.INFO)
    }

    fun cancel(kind: Kind) {}

    private fun ensureTray(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        trayIcon?.let { return it }
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(0x21, 0x96, 0xF3)
            fillOval(1, 1, 14, 14)
            dispose()
        }
        val icon = TrayIcon(image, "CConnect").apply {
            isImageAutoSize = true
            addActionListener { onActivate?.invoke() }
        }
        return runCatching {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
            icon
        }.getOrNull()
    }
}
