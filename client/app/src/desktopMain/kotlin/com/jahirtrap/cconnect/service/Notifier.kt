package com.jahirtrap.cconnect.service

import java.awt.BasicStroke
import java.awt.Color
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

internal actual val platformNotifier: PlatformNotifier = DesktopNotifier

internal fun isTrayActive(): Boolean = DesktopNotifier.trayActive

internal fun configureTrayMenu(showLabel: String, exitLabel: String, onExit: () -> Unit) =
    DesktopNotifier.configureMenu(showLabel, exitLabel, onExit)

internal object DesktopNotifier : PlatformNotifier {

    private var onActivate: (() -> Unit)? = null
    private var trayIcon: TrayIcon? = null

    val trayActive get() = trayIcon != null

    private val isLinux get() = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    override fun init(onActivate: () -> Unit) {
        this.onActivate = onActivate
        if (!isLinux) ensureTray()
    }

    fun configureMenu(showLabel: String, exitLabel: String, onExit: () -> Unit) {
        val icon = trayIcon ?: return
        icon.popupMenu = PopupMenu().apply {
            add(MenuItem(showLabel).apply { addActionListener { onActivate?.invoke() } })
            add(MenuItem(exitLabel).apply { addActionListener { onExit() } })
        }
    }

    override fun notify(kind: Notifier.Kind, title: String, text: String?, actions: List<Notifier.Action>) {
        if (isLinux) {
            runCatching { ProcessBuilder("notify-send", "-a", "CConnect", "--hint=string:desktop-entry:CConnect", title, text.orEmpty()).start() }
            return
        }
        val tray = ensureTray() ?: return
        tray.displayMessage(title, text.orEmpty(), TrayIcon.MessageType.NONE)
    }

    override fun cancel(kind: Notifier.Kind) {}

    private fun ensureTray(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        trayIcon?.let { return it }
        val tray = SystemTray.getSystemTray()
        val size = (runCatching { tray.trayIconSize.width }.getOrNull() ?: 32).coerceIn(16, 256)
        val icon = TrayIcon(renderAppIcon(size), "CConnect").apply {
            isImageAutoSize = true
            addActionListener { onActivate?.invoke() }
        }
        return runCatching {
            tray.add(icon)
            trayIcon = icon
            icon
        }.getOrNull()
    }

    private fun renderAppIcon(size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            scale(size / 108.0, size / 108.0)
            color = Color(0x17, 0x17, 0x17)
            fill(RoundRectangle2D.Double(0.0, 0.0, 108.0, 108.0, 48.0, 48.0))
            translate(54.0, 54.0); scale(1.6, 1.6); translate(-54.0, -54.0)
            translate(19.93, 18.8); scale(0.55, 0.55)
            color = Color(0x4D, 0x4D, 0x4D)
            stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            draw(Ellipse2D.Double(21.94, 24.0, 80.0, 80.0))
            color = Color.WHITE
            draw(Arc2D.Double(21.94, 24.0, 80.0, 80.0, 50.0, 260.0, Arc2D.OPEN))
            stroke = BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            draw(Path2D.Double().apply { moveTo(46.0, 52.0); lineTo(58.0, 64.0); lineTo(46.0, 76.0) })
            draw(Line2D.Double(64.0, 76.0, 80.0, 76.0))
            dispose()
        }
        return image
    }
}
