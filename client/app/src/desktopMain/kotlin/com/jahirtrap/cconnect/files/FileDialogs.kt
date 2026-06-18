package com.jahirtrap.cconnect.files

import org.lwjgl.util.tinyfd.TinyFileDialogs
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
        TinyFileDialogs.tinyfd_saveFileDialog("Save", lastDir + File.separator + name, null, null)
            ?.let { path -> File(path).also { f -> f.parent?.let { lastDir = it } } }
    }.getOrNull()

    fun chooseDirectory(): File? = runCatching {
        TinyFileDialogs.tinyfd_selectFolderDialog("Select folder", lastDir + File.separator)
            ?.let { File(it).also { f -> lastDir = f.absolutePath } }
    }.getOrNull()
}
