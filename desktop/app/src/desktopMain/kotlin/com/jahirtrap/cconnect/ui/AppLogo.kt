package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.*

@Composable
fun AppLogo(size: Dp = 32.dp) {
    Image(
        painter = painterResource(Res.drawable.app_icon),
        contentDescription = null,
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
    )
}
