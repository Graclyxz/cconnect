package com.jahirtrap.cconnect.monitor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import com.jahirtrap.cconnect.R
import com.jahirtrap.cconnect.chat.ChatViewModel
import com.jahirtrap.cconnect.data.remote.SystemApi
import com.jahirtrap.cconnect.files.formatSize
import com.jahirtrap.cconnect.ui.AppTopBar
import com.jahirtrap.cconnect.ui.CenteredProgress
import com.jahirtrap.cconnect.ui.EmptyState
import com.jahirtrap.cconnect.ui.EnvironmentSelectDialog
import com.jahirtrap.cconnect.ui.StatusDot
import com.jahirtrap.cconnect.ui.TooltipIconButton
import com.jahirtrap.cconnect.ui.theme.palette
import com.jahirtrap.cconnect.ui.verticalScrollIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val RECONNECT_MS = 3000L
private const val LOG_CAP = 300
private const val HISTORY_CAP = 90

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MonitorScreen(onClose: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    var info by remember { mutableStateOf<SystemApi.SystemInfo?>(null) }
    var gpu by remember { mutableStateOf<SystemApi.GpuInfo?>(null) }
    var failed by remember { mutableStateOf(false) }
    var cpuHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var gpuHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var memHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var vramHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var logs by remember { mutableStateOf<List<LogItem>>(emptyList()) }
    var followLogs by remember { mutableStateOf(true) }
    var envMenu by remember { mutableStateOf(false) }
    val logScroll = rememberScrollState()

    LaunchedEffect(state.activeEnvironmentId) {
        info = null
        gpu = null
        failed = false
        cpuHistory = emptyList()
        gpuHistory = emptyList()
        memHistory = emptyList()
        vramHistory = emptyList()
        logs = emptyList()
        followLogs = true
        var nextId = 0L
        while (isActive) {
            runCatching {
                SystemApi.stream().collect { event ->
                    when (event) {
                        is SystemApi.Event.Info -> {
                            val snapshot = event.info
                            info = snapshot
                            failed = false
                            cpuHistory = (cpuHistory + snapshot.cpuPercent).takeLast(HISTORY_CAP)
                            memHistory = (memHistory + snapshot.memoryPercent).takeLast(HISTORY_CAP)
                            snapshot.gpu?.let {
                                gpu = it
                                gpuHistory = (gpuHistory + it.percent).takeLast(HISTORY_CAP)
                                vramHistory = (vramHistory + it.memPercent).takeLast(HISTORY_CAP)
                            }
                        }

                        is SystemApi.Event.Logs -> {
                            followLogs = logScroll.value >= logScroll.maxValue - 24
                            logs = (logs + event.items.map { LogItem(nextId++, it) }).takeLast(LOG_CAP)
                        }
                    }
                }
            }
            if (info == null) failed = true
            delay(RECONNECT_MS)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { logScroll.maxValue }.collectLatest { max ->
            if (followLogs && max > 0) logScroll.animateScrollTo(max)
        }
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.monitor),
                subtitle = info?.let { "${it.hostname} • ${it.os} • ${formatUptime(it.uptime)}" }
                    ?: stringResource(R.string.loading),
                subtitleLeading = if (info == null) ({
                    if (failed) StatusDot(palette.red) else LoadingIndicator(modifier = Modifier.size(16.dp))
                }) else null,
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.back), onClick = onClose) {
                        Icon(Lucide.ArrowLeft, contentDescription = null)
                    }
                },
                actions = {
                    TooltipIconButton(label = stringResource(R.string.environment), onClick = { envMenu = true }) {
                        Icon(Lucide.Server, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (envMenu) {
            EnvironmentSelectDialog(
                environments = state.environments,
                activeId = state.activeEnvironmentId,
                onSelect = { if (it != state.activeEnvironmentId) vm.selectEnvironment(it) },
                onDismiss = { envMenu = false },
            )
        }
        val current = info
        when {
            current == null && failed -> EmptyState(stringResource(R.string.connection_error), Modifier.fillMaxSize().padding(padding))
            current == null -> CenteredProgress(Modifier.fillMaxSize().padding(padding))
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                val pagerState = rememberPagerState(pageCount = { 2 })
                val scope = rememberCoroutineScope()
                val labels = listOf(stringResource(R.string.resources), stringResource(R.string.server_logs))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    labels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = i == pagerState.currentPage,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                            icon = {},
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> ResourcesPage(current, gpu, cpuHistory, gpuHistory, memHistory, vramHistory)
                        else -> LogsPage(logs, logScroll)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcesPage(
    current: SystemApi.SystemInfo,
    gpu: SystemApi.GpuInfo?,
    cpuHistory: List<Float>,
    gpuHistory: List<Float>,
    memHistory: List<Float>,
    vramHistory: List<Float>,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            GraphMetric(
                title = "CPU",
                subtitle = pluralStringResource(R.plurals.cores_count, current.cpuCores, current.cpuCores),
                percent = current.cpuPercent,
                history = cpuHistory,
                modifier = Modifier.weight(1f),
            )
            if (gpu != null) {
                Spacer(Modifier.width(12.dp))
                GraphMetric(
                    title = "GPU",
                    subtitle = listOfNotNull(
                        gpu.name.removePrefix("NVIDIA GeForce ").ifBlank { null },
                        gpu.temp?.let { "$it°C" },
                    ).joinToString(" • "),
                    percent = gpu.percent,
                    history = gpuHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        GraphMetric(
            title = stringResource(R.string.memory),
            subtitle = "${formatSize(current.memoryUsed)} / ${formatSize(current.memoryTotal)}",
            percent = current.memoryPercent,
            history = memHistory,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (gpu != null) {
            GraphMetric(
                title = "VRAM",
                subtitle = "${formatSize(gpu.memUsed)} / ${formatSize(gpu.memTotal)}",
                percent = gpu.memPercent,
                history = vramHistory,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.storage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                current.disks.forEach { disk ->
                    BarMetric(
                        title = disk.mount,
                        subtitle = "${formatSize(disk.used)} / ${formatSize(disk.total)}",
                        percent = disk.percent,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LogsPage(logs: List<LogItem>, scroll: ScrollState) {
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }.let { scroll.scrollTo(it) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .verticalScrollIndicator(scroll)
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (logs.isEmpty()) {
            Text(
                stringResource(R.string.no_logs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        logs.forEach { item -> LogRow(item) }
    }
}

@Composable
private fun MetricHeader(title: String, subtitle: String, percent: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "%.1f%%".format(percent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GraphMetric(title: String, subtitle: String, percent: Float, history: List<Float>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        MetricHeader(title, subtitle, percent)
        Spacer(Modifier.height(8.dp))
        Sparkline(
            points = history,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

@Composable
private fun BarMetric(title: String, subtitle: String, percent: Float) {
    val barColor = if (percent >= 90f) palette.red else MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth()) {
        MetricHeader(title, subtitle, percent)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            color = barColor,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Sparkline(points: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.15f)
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val stepX = size.width / (HISTORY_CAP - 1)
        val startX = size.width - (points.size - 1) * stepX
        val inset = 2.dp.toPx()
        val span = size.height - inset * 2
        fun yOf(value: Float) = inset + span * (1f - (value / 100f).coerceIn(0f, 1f))
        val line = Path()
        points.forEachIndexed { index, value ->
            val x = startX + index * stepX
            if (index == 0) line.moveTo(x, yOf(value)) else line.lineTo(x, yOf(value))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(startX, size.height)
            close()
        }
        drawPath(fill, fillColor)
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx()))
    }
}

private data class LogItem(val id: Long, val entry: SystemApi.LogEntry)

@Composable
private fun LogRow(item: LogItem) {
    val entry = item.entry
    val time = remember(item.id) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date((entry.ts * 1000).toLong()))
    }
    val color = when (entry.level) {
        "ERROR", "CRITICAL" -> palette.red
        "WARNING" -> palette.yellow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

private fun formatUptime(seconds: Double): String {
    val total = seconds.toLong()
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val minutes = (total % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
