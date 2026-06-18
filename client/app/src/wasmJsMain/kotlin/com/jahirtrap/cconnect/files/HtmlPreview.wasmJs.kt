package com.jahirtrap.cconnect.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.html_preview_unavailable
import com.jahirtrap.cconnect.resources.open_in_browser
import com.jahirtrap.cconnect.ui.ActionButton
import com.jahirtrap.cconnect.ui.EmptyState
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun HtmlPreview(
    url: String,
    filename: String,
    onOpenExternally: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(stringResource(Res.string.html_preview_unavailable))
        ActionButton(
            text = stringResource(Res.string.open_in_browser),
            onClick = onOpenExternally,
            icon = Lucide.ExternalLink,
        )
    }
}
