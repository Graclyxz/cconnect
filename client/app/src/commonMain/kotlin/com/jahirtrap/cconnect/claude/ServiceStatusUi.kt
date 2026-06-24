package com.jahirtrap.cconnect.claude

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jahirtrap.cconnect.resources.Res
import com.jahirtrap.cconnect.resources.*
import com.jahirtrap.cconnect.ui.theme.palette
import org.jetbrains.compose.resources.stringResource

@Composable
fun serviceIndicatorColor(indicator: String): Color = when (indicator) {
    "none" -> palette.green
    "minor" -> palette.yellow
    "major" -> palette.orange
    "critical" -> palette.red
    "maintenance" -> palette.blue
    else -> palette.gray
}

@Composable
fun serviceIndicatorLabel(indicator: String): String = stringResource(
    when (indicator) {
        "none" -> Res.string.status_operational
        "minor" -> Res.string.status_minor
        "major" -> Res.string.status_major
        "critical" -> Res.string.status_critical
        "maintenance" -> Res.string.status_maintenance
        else -> Res.string.status_unknown
    }
)

@Composable
fun componentStatusColor(status: String): Color = when (status) {
    "operational" -> palette.green
    "degraded_performance" -> palette.yellow
    "partial_outage" -> palette.orange
    "major_outage" -> palette.red
    "under_maintenance" -> palette.blue
    else -> palette.gray
}

@Composable
fun componentStatusLabel(status: String): String = stringResource(
    when (status) {
        "operational" -> Res.string.status_component_operational
        "degraded_performance" -> Res.string.status_component_degraded
        "partial_outage" -> Res.string.status_component_partial
        "major_outage" -> Res.string.status_component_outage
        "under_maintenance" -> Res.string.status_maintenance
        else -> Res.string.status_unknown
    }
)

@Composable
fun incidentStatusLabel(status: String): String = stringResource(
    when (status) {
        "investigating" -> Res.string.incident_investigating
        "identified" -> Res.string.incident_identified
        "monitoring" -> Res.string.incident_monitoring
        "resolved" -> Res.string.incident_resolved
        "postmortem" -> Res.string.incident_postmortem
        else -> Res.string.incident_investigating
    }
)
