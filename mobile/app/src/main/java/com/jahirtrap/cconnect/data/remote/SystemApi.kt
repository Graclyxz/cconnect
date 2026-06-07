package com.jahirtrap.cconnect.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

object SystemApi {

    data class DiskInfo(
        val mount: String,
        val used: Long,
        val total: Long,
        val percent: Float,
    )

    data class GpuInfo(
        val name: String,
        val percent: Float,
        val memUsed: Long,
        val memTotal: Long,
        val memPercent: Float,
        val temp: Int?,
    )

    data class SystemInfo(
        val hostname: String,
        val os: String,
        val uptime: Double,
        val cpuPercent: Float,
        val cpuCores: Int,
        val memoryUsed: Long,
        val memoryTotal: Long,
        val memoryPercent: Float,
        val gpu: GpuInfo?,
        val disks: List<DiskInfo>,
    )

    data class LogEntry(
        val ts: Double,
        val level: String,
        val message: String,
    )

    data class LogsChunk(val items: List<LogEntry>, val offset: Long)

    suspend fun info(): SystemInfo? {
        val o = Http.get("/system")?.jsonObject ?: return null
        val cpu = o["cpu"]?.jsonObject
        val memory = o["memory"]?.jsonObject
        return SystemInfo(
            hostname = o["hostname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            os = o["os"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            uptime = o["uptime"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            cpuPercent = cpu?.get("percent")?.jsonPrimitive?.floatOrNull ?: 0f,
            cpuCores = cpu?.get("cores")?.jsonPrimitive?.intOrNull ?: 0,
            memoryUsed = memory?.get("used")?.jsonPrimitive?.longOrNull ?: 0L,
            memoryTotal = memory?.get("total")?.jsonPrimitive?.longOrNull ?: 0L,
            memoryPercent = memory?.get("percent")?.jsonPrimitive?.floatOrNull ?: 0f,
            gpu = (o["gpu"] as? JsonObject)?.let { gpu ->
                GpuInfo(
                    name = gpu["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    percent = gpu["percent"]?.jsonPrimitive?.floatOrNull ?: 0f,
                    memUsed = gpu["mem_used"]?.jsonPrimitive?.longOrNull ?: 0L,
                    memTotal = gpu["mem_total"]?.jsonPrimitive?.longOrNull ?: 0L,
                    memPercent = gpu["mem_percent"]?.jsonPrimitive?.floatOrNull ?: 0f,
                    temp = gpu["temp"]?.jsonPrimitive?.intOrNull,
                )
            },
            disks = o["disks"]?.jsonArray?.map { el ->
                val d = el.jsonObject
                DiskInfo(
                    mount = d["mount"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    used = d["used"]?.jsonPrimitive?.longOrNull ?: 0L,
                    total = d["total"]?.jsonPrimitive?.longOrNull ?: 0L,
                    percent = d["percent"]?.jsonPrimitive?.floatOrNull ?: 0f,
                )
            }.orEmpty(),
        )
    }

    suspend fun logs(after: Long = 0): LogsChunk? {
        val query = mapOf("after" to after.toString())
        val o = Http.get("/system/logs", query)?.jsonObject ?: return null
        return LogsChunk(
            items = o["items"]?.jsonArray?.map { el ->
                val entry = el.jsonObject
                LogEntry(
                    ts = entry["ts"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    level = entry["level"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    message = entry["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }.orEmpty(),
            offset = o["offset"]?.jsonPrimitive?.longOrNull ?: after,
        )
    }
}
