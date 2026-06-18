package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable

actual fun readFilesLocation(): Triple<String, String?, String>? = null
actual fun syncFilesLocation(path: String, archive: String?, archiveDir: String) {}
actual fun filesHistoryBack() {}

@Composable
actual fun FilesPopstate(onLocation: (String, String?, String) -> Unit) {}
