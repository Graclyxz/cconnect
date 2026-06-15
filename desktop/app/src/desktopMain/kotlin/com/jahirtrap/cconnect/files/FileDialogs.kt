package com.jahirtrap.cconnect.files

import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object FileDialogs {
    fun openMultiple(): List<File> {
        val dialog = FileDialog(null as Frame?, null, FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.isVisible = true
        return dialog.files?.toList().orEmpty()
    }

    fun save(name: String): File? {
        val dialog = FileDialog(null as Frame?, null, FileDialog.SAVE)
        dialog.file = name
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val chosen = dialog.file ?: return null
        return File(dir, chosen)
    }

    fun chooseDirectory(): File? =
        TinyFileDialogs.tinyfd_selectFolderDialog("Select folder", "")?.let { File(it) }
}
