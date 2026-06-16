package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable

expect fun readFilesLocation(): Triple<String, String?, String>?
expect fun syncFilesLocation(path: String, archive: String?, archiveDir: String)
expect fun filesHistoryBack()

@Composable
expect fun FilesPopstate(onLocation: (String, String?, String) -> Unit)
