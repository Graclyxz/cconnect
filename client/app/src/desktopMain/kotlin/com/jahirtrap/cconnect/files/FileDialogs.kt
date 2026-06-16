package com.jahirtrap.cconnect.files

import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object FileDialogs {
    private var lastDir: String = System.getProperty("user.home") ?: "."

    fun openMultiple(): List<File> {
        val dialog = FileDialog(null as Frame?, null, FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.directory = lastDir
        dialog.isVisible = true
        val files = dialog.files?.toList().orEmpty()
        files.firstOrNull()?.parent?.let { lastDir = it }
        return files
    }

    fun save(name: String): File? {
        val dialog = FileDialog(null as Frame?, null, FileDialog.SAVE)
        dialog.directory = lastDir
        dialog.file = name
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val chosen = dialog.file ?: return null
        lastDir = dir
        return File(dir, chosen)
    }

    fun chooseDirectory(): File? =
        TinyFileDialogs.tinyfd_selectFolderDialog("Select folder", lastDir + File.separator)
            ?.let { File(it).also { f -> lastDir = f.absolutePath } }
}
