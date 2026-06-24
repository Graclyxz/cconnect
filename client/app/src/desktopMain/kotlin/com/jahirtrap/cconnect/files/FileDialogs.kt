package com.jahirtrap.cconnect.files

import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object FileDialogs {
    private var lastDir: String = System.getProperty("user.home") ?: "."

    fun openMultiple(): List<File> = runCatching {
        val result = TinyFileDialogs.tinyfd_openFileDialog(
            "Open", lastDir + File.separator, null, null, true,
        ) ?: return@runCatching emptyList()
        result.split("|").map(::File).also { files ->
            files.firstOrNull()?.parent?.let { lastDir = it }
        }
    }.getOrDefault(emptyList())

    fun save(name: String): File? = runCatching {
        val pick: () -> File? = {
            val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE)
            dialog.directory = lastDir
            dialog.file = name
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir == null || file == null) null else File(dir, file).also { lastDir = dir }
        }
        if (EventQueue.isDispatchThread()) pick()
        else {
            var result: File? = null
            EventQueue.invokeAndWait { result = pick() }
            result
        }
    }.getOrNull()

    fun chooseDirectory(): File? = runCatching {
        TinyFileDialogs.tinyfd_selectFolderDialog("Select folder", lastDir + File.separator)
            ?.let { File(it).also { f -> lastDir = f.absolutePath } }
    }.getOrNull()
}
