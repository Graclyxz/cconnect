package com.jahirtrap.cconnect.files

import com.jahirtrap.cconnect.data.formatDecimal

internal val ARCHIVE_SUFFIXES = listOf(".zip", ".7z", ".rar", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz")

fun isArchive(name: String): Boolean = ARCHIVE_SUFFIXES.any { name.lowercase().endsWith(it) }

internal fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "${formatDecimal(value, 1)} ${units[unit]}"
}
