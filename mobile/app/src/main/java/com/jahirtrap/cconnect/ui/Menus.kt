package com.jahirtrap.cconnect.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Shared dropdown menu item, denser than Material's default (which is 48dp tall with
// roomy padding). Used by every selector/menu so they stay consistent.
@Composable
fun CompactDropdownItem(
    text: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = leadingIcon,
        trailingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        onClick = onClick,
    )
}
