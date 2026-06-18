package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun HtmlPreview(
    url: String,
    filename: String,
    onOpenExternally: () -> Unit,
    modifier: Modifier,
)
