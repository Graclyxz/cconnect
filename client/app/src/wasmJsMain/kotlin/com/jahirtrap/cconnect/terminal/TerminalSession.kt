package com.jahirtrap.cconnect.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.jahirtrap.cconnect.data.SshProfile
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.back
import com.jahirtrap.cconnect.resources.web_unavailable
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.TooltipIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun TerminalSession(
    profile: SshProfile,
    onClose: () -> Unit,
    onOsDetected: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = profile.name.ifBlank { profile.host },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(Res.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        EmptyState(stringResource(Res.string.web_unavailable), Modifier.fillMaxSize().padding(pad))
    }
}
