package com.jahirtrap.cconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
    onSharedLink: ((url: String, filename: String) -> Unit)? = null,
)
