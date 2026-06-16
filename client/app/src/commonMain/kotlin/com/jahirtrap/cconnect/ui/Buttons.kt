package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = MaterialTextButton(onClick = onClick, modifier = modifier.handCursor(enabled), enabled = enabled, content = content)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = MaterialButton(onClick = onClick, modifier = modifier.handCursor(enabled), enabled = enabled, content = content)

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = MaterialIconButton(onClick = onClick, modifier = modifier.handCursor(enabled), enabled = enabled, content = content)
