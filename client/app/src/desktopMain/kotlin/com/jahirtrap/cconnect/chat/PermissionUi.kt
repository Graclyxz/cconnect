package com.jahirtrap.cconnect.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.FastForward
import com.composables.icons.lucide.FilePen
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Zap
import com.jahirtrap.cconnect.ui.theme.palette

data class PermissionStyle(val icon: ImageVector, val color: Color)

@Composable
@ReadOnlyComposable
fun permissionStyle(mode: String): PermissionStyle {
    val p = palette
    return when (mode) {
        "acceptEdits" -> PermissionStyle(Lucide.FilePen, p.green)
        "plan" -> PermissionStyle(Lucide.Lightbulb, p.blue)
        "bypassPermissions" -> PermissionStyle(Lucide.Zap, p.red)
        "dontAsk" -> PermissionStyle(Lucide.FastForward, p.orange)
        "auto" -> PermissionStyle(Lucide.Repeat, p.cyan)
        else -> PermissionStyle(Lucide.Shield, p.gray)
    }
}
