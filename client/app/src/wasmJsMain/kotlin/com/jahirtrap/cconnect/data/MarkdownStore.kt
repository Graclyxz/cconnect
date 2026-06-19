package com.jahirtrap.cconnect.data

import kotlinx.browser.localStorage

private const val KEY = "cconnect.markdown_scratch"

actual fun loadMarkdownScratch(): String = localStorage.getItem(KEY) ?: ""

actual fun saveMarkdownScratch(text: String) {
    localStorage.setItem(KEY, text)
}
