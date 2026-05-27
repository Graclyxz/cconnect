package com.jahirtrap.cconnect.data.remote

import com.jahirtrap.cconnect.data.Capabilities
import com.jahirtrap.cconnect.data.ModelOption
import com.jahirtrap.cconnect.data.PermissionMode
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CapabilitiesApi {

    suspend fun capabilities(): Capabilities? {
        val data = Http.get("/capabilities")?.jsonObject ?: return null
        val fallback = Capabilities()
        return Capabilities(
            permissionModes = data["permission_modes"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                PermissionMode(id, o["label"]?.jsonPrimitive?.contentOrNull ?: id)
            } ?: fallback.permissionModes,
            effortLevels = data["effort_levels"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: fallback.effortLevels,
            models = data["models"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ModelOption(id, o["label"]?.jsonPrimitive?.contentOrNull ?: id)
            } ?: fallback.models,
            colors = data["colors"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: fallback.colors,
        )
    }
}
