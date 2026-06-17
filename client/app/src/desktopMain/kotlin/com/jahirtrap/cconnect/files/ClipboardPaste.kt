package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@Composable
actual fun ClipboardPasteEffect(enabled: Boolean, onFiles: (List<AttachmentFile>) -> Unit) {
}

private fun clipboardContents(): Transferable? =
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.getContents(null) }.getOrNull()

actual fun clipboardHasFiles(): Boolean {
    val contents = clipboardContents() ?: return false
    return contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
        contents.isDataFlavorSupported(DataFlavor.imageFlavor)
}

actual suspend fun readClipboardFiles(): List<AttachmentFile> {
    val contents = clipboardContents() ?: return emptyList()
    return runCatching {
        if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val list = contents.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
            list?.filterIsInstance<File>()?.filter { it.isFile }?.map { AttachmentFile(it) } ?: emptyList()
        } else if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image ?: return emptyList()
            val buffered = image as? BufferedImage ?: run {
                val w = image.getWidth(null).coerceAtLeast(1)
                val h = image.getHeight(null).coerceAtLeast(1)
                val target = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = target.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()
                target
            }
            val tmp = File.createTempFile("pasted-", ".png")
            ImageIO.write(buffered, "png", tmp)
            listOf(AttachmentFile(tmp))
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())
}
