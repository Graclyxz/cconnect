package com.jahirtrap.cconnect.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.*
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.horizontalScrollbar
import com.jahirtrap.cconnect.ui.theme.sessionColorOf
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabStrip() {
    val tabs = TabsController.tabs
    val activeId = TabsController.activeId
    val scroll = TabsController.stripScroll
    val activeRequester = remember { BringIntoViewRequester() }
    val activeIndex = tabs.indexOfFirst { it.id == activeId }
    LaunchedEffect(activeId, activeIndex, tabs.size) {
        activeRequester.bringIntoView()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScrollbar(scroll, touchIndicator = false, wheelScroll = true)
            .horizontalScroll(scroll)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            key(tab.id) {
                TabChip(
                    modifier = if (tab.id == activeId) Modifier.bringIntoViewRequester(activeRequester) else Modifier,
                    label = tab.title ?: stringResource(Res.string.new_chat),
                    dot = sessionColorOf(tab.color),
                    active = tab.id == activeId,
                    showClose = tabs.size > 1,
                    onClick = { TabsController.selectTab(tab.id) },
                    onClose = { TabsController.closeTab(tab.id) },
                )
            }
        }
        TooltipIconButton(label = stringResource(Res.string.new_tab), onClick = { TabsController.newTab() }) {
            Icon(Lucide.Plus, contentDescription = null)
        }
    }
}

@Composable
private fun TabChip(
    modifier: Modifier = Modifier,
    label: String,
    dot: Color?,
    active: Boolean,
    showClose: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .heightIn(min = 32.dp)
            .padding(start = 10.dp, end = if (showClose) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 170.dp),
        )
        if (showClose) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClose).pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(Res.string.close_tab),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun TabSwitcher() {
    var open by remember { mutableStateOf(false) }
    val tabs = TabsController.tabs
    val activeId = TabsController.activeId
    Box {
        TooltipIconButton(label = stringResource(Res.string.tabs), onClick = { open = true }) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${tabs.size}", style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            tabs.forEach { tab ->
                key(tab.id) {
                    CompactDropdownItem(
                        text = tab.title ?: stringResource(Res.string.new_chat),
                        selected = tab.id == activeId,
                        leadingIcon = {
                            val c = sessionColorOf(tab.color) ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        },
                        trailing = {
                            if (tabs.size > 1) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable { TabsController.closeTab(tab.id) }.pointerHoverIcon(PointerIcon.Hand),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Lucide.X,
                                        contentDescription = stringResource(Res.string.close_tab),
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = { TabsController.selectTab(tab.id); open = false },
                    )
                }
            }
            CompactDropdownItem(
                text = stringResource(Res.string.new_tab),
                leadingIcon = { Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = { TabsController.newTab(); open = false },
            )
        }
    }
}
