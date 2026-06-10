package com.jahirtrap.cconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jahirtrap.cconnect.data.Settings
import com.jahirtrap.cconnect.data.remote.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val optionId = intent.getStringExtra(EXTRA_OPTION_ID) ?: return
        Settings(context)
        Notifier.cancel(context, Notifier.Kind.Interaction)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Http.post("/chat/interaction", buildJsonObject {
                    put("id", requestId)
                    put("option_id", optionId)
                })
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_OPTION_ID = "option_id"
    }
}
