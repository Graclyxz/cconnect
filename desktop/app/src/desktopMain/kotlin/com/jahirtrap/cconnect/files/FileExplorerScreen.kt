package com.jahirtrap.cconnect.files

private val ARCHIVE_SUFFIXES = listOf(".zip", ".7z", ".rar", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz")

fun isArchive(name: String): Boolean = ARCHIVE_SUFFIXES.any { name.lowercase().endsWith(it) }
