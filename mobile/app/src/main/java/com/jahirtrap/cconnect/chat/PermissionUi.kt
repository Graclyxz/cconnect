package com.jahirtrap.cconnect.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PermissionStyle(val icon: ImageVector, val color: Color, val label: String)

fun permissionStyle(mode: String): PermissionStyle = when (mode) {
    "default" -> PermissionStyle(Icons.Rounded.Shield, Color(0xFF9E9E9E), "Default")
    "acceptEdits" -> PermissionStyle(Icons.Rounded.EditNote, Color(0xFF56D364), "Accept edits")
    "plan" -> PermissionStyle(Icons.Rounded.Lightbulb, Color(0xFF79C0FF), "Plan")
    "bypassPermissions" -> PermissionStyle(Icons.Rounded.Bolt, Color(0xFFFF7B72), "Bypass")
    "dontAsk" -> PermissionStyle(Icons.Rounded.FastForward, Color(0xFFFFA726), "Don't ask")
    "auto" -> PermissionStyle(Icons.Rounded.AutoMode, Color(0xFF26C6DA), "Auto")
    else -> PermissionStyle(Icons.Rounded.Shield, Color(0xFF9E9E9E), mode)
}
