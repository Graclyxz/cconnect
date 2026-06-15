package com.jahirtrap.cconnect.files

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

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

    fun chooseDirectory(): File? {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
