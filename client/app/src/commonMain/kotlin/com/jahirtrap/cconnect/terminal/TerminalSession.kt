package com.jahirtrap.cconnect.terminal

import androidx.compose.runtime.Composable
import com.jahirtrap.cconnect.data.SshProfile

@Composable
expect fun TerminalSession(
    profile: SshProfile,
    onClose: () -> Unit,
    onOsDetected: (String) -> Unit,
)
