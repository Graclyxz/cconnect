package com.jahirtrap.cconnect.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.FastForward
import com.composables.icons.lucide.FilePen
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Zap

// Icon + color only; the display label comes from the backend (capabilities).
data class PermissionStyle(val icon: ImageVector, val color: Color)

fun permissionStyle(mode: String): PermissionStyle = when (mode) {
    "default" -> PermissionStyle(Lucide.Shield, Color(0xFF9E9E9E))
    "acceptEdits" -> PermissionStyle(Lucide.FilePen, Color(0xFF56D364))
    "plan" -> PermissionStyle(Lucide.Lightbulb, Color(0xFF79C0FF))
    "bypassPermissions" -> PermissionStyle(Lucide.Zap, Color(0xFFFF7B72))
    "dontAsk" -> PermissionStyle(Lucide.FastForward, Color(0xFFFFA726))
    "auto" -> PermissionStyle(Lucide.Repeat, Color(0xFF26C6DA))
    else -> PermissionStyle(Lucide.Shield, Color(0xFF9E9E9E))
}
