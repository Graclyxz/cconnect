package com.jahirtrap.cconnect.service

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

internal actual val platformNotifier: PlatformNotifier = DesktopNotifier

private object DesktopNotifier : PlatformNotifier {

    private var onActivate: (() -> Unit)? = null
    private var trayIcon: TrayIcon? = null

    override fun init(onActivate: () -> Unit) {
        this.onActivate = onActivate
    }

    override fun notify(kind: Notifier.Kind, title: String, text: String?, actions: List<Notifier.Action>) {
        if (System.getProperty("os.name").orEmpty().lowercase().contains("linux")) {
            runCatching { ProcessBuilder("notify-send", "-a", "CConnect", "--hint=string:desktop-entry:CConnect", title, text.orEmpty()).start() }
            return
        }
        val tray = ensureTray() ?: return
        tray.displayMessage(title, text.orEmpty(), TrayIcon.MessageType.INFO)
    }

    override fun cancel(kind: Notifier.Kind) {}

    private fun ensureTray(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        trayIcon?.let { return it }
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            val scale = 64.0 / 92.0
            scale(scale, scale)
            translate(-15.94, -18.0)
            color = Color.WHITE
            stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f)
            draw(Ellipse2D.Double(21.94, 24.0, 80.0, 80.0))
            composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)
            draw(Arc2D.Double(21.94, 24.0, 80.0, 80.0, 50.0, 260.0, Arc2D.OPEN))
            stroke = BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            draw(Path2D.Double().apply { moveTo(46.0, 52.0); lineTo(58.0, 64.0); lineTo(46.0, 76.0) })
            draw(Line2D.Double(64.0, 76.0, 80.0, 76.0))
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
