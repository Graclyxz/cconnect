package com.jahirtrap.cconnect.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.*
import com.jahirtrap.cconnect.ui.CompactDropdownItem
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.horizontalScrollbar
import com.jahirtrap.cconnect.ui.theme.sessionColorOf
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabStrip() {
    val tabs = TabsController.tabs
    val activeId = TabsController.activeId
    val scroll = TabsController.stripScroll
    val density = LocalDensity.current
    val spacingPx = with(density) { 6.dp.toPx() }
    val widths = remember { mutableStateMapOf<String, Float>() }
    val centers = remember { mutableStateMapOf<String, Float>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableStateOf(0f) }
    var plusLeft by remember { mutableStateOf(0f) }

    fun reorder(id: String) {
        val from = tabs.indexOfFirst { it.id == id }
        if (from !in 0..tabs.lastIndex) return
        if (dragDx > 0f && from < tabs.lastIndex) {
            val w = widths[tabs[from + 1].id] ?: 0f
            if (w > 0f && dragDx > w / 2f + spacingPx) {
                TabsController.moveTab(id, from + 1)
                dragDx -= w + spacingPx
            }
        } else if (dragDx < 0f && from > 0) {
            val w = widths[tabs[from - 1].id] ?: 0f
            if (w > 0f && dragDx < -(w / 2f + spacingPx)) {
                TabsController.moveTab(id, from - 1)
                dragDx += w + spacingPx
            }
        }
    }

    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val step = with(density) { 12.dp.toPx() }
        while (draggingId == id) {
            withFrameNanos { }
            val vp = scroll.viewportSize
            if (vp > 0) {
                val visible = (centers[id] ?: 0f) + dragDx - scroll.value
                val dir = when {
                    visible < edge && scroll.value > 0 -> -1f
                    visible > vp - edge && scroll.value < scroll.maxValue -> 1f
                    else -> 0f
                }
                if (dir != 0f) {
                    val before = scroll.value
                    scroll.scrollBy(dir * step)
                    dragDx += (scroll.value - before)
                    reorder(id)
                }
            }
        }
    }

    LaunchedEffect(activeId, tabs.size, centers[activeId], scroll.viewportSize) {
        if (draggingId != null) return@LaunchedEffect
        val center = centers[activeId] ?: return@LaunchedEffect
        val vp = scroll.viewportSize
        if (vp <= 0) return@LaunchedEffect
        scroll.animateScrollTo((center - vp / 2f).toInt().coerceIn(0, scroll.maxValue))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScrollbar(scroll, touchIndicator = false, wheelScroll = true)
            .horizontalScroll(scroll)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            key(tab.id) {
                val dragging = tab.id == draggingId
                TabChip(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            widths[tab.id] = coords.size.width.toFloat()
                            centers[tab.id] = coords.positionInParent().x + coords.size.width / 2f
                        }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationX = if (dragging) dragDx else 0f }
                        .pointerInput(tab.id) {
                            val threshold = 8.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var started = false
                                var clickedUp = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) break
                                    if (!change.pressed) {
                                        clickedUp = !change.isConsumed
                                        break
                                    }
                                    if (!started) {
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (abs(dx) > threshold && abs(dx) >= abs(dy)) {
                                            started = true
                                            draggingId = tab.id
                                            dragDx = 0f
                                        }
                                    }
                                    if (started) {
                                        dragDx += change.positionChange().x
                                        change.consume()
                                        reorder(tab.id)
                                    }
                                }
                                if (started) {
                                    draggingId = null
                                    dragDx = 0f
                                } else if (clickedUp) {
                                    TabsController.selectTab(tab.id)
                                }
                            }
                        },
                    label = tab.title ?: stringResource(Res.string.new_chat),
                    dot = sessionColorOf(tab.color),
                    active = tab.id == activeId,
                    showClose = tabs.size > 1,
                    onClick = { TabsController.selectTab(tab.id) },
                    onClose = { TabsController.closeTab(tab.id) },
                )
            }
        }
        Box(
            modifier = Modifier
                .onGloballyPositioned { plusLeft = it.positionInParent().x }
                .graphicsLayer {
                    val d = draggingId
                    translationX = if (d != null && plusLeft > 0f) {
                        val right = (centers[d] ?: 0f) + (widths[d] ?: 0f) / 2f + dragDx
                        (right + spacingPx - plusLeft).coerceAtLeast(0f)
                    } else 0f
                },
        ) {
            TooltipIconButton(label = stringResource(Res.string.new_tab), size = 32.dp, onClick = { TabsController.newTab() }) {
                Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            }
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
