package com.jahirtrap.cconnect.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.jahirtrap.cconnect.MainActivity
import com.jahirtrap.cconnect.R

object Notifier {

    const val EXTRA_OPEN_CHAT = "open_chat"

    data class Action(val label: String, val requestId: String, val optionId: String)

    enum class Kind(val channelId: String, val channelName: Int, val importance: Int, val notifId: Int) {
        TaskDone("task_done", R.string.channel_task_done, NotificationManager.IMPORTANCE_DEFAULT, 2),
        Interaction("interaction", R.string.channel_interaction, NotificationManager.IMPORTANCE_HIGH, 3),
    }

    val appInForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun notify(context: Context, kind: Kind, title: String, text: String?, actions: List<Action> = emptyList()) {
        if (appInForeground) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context, kind)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_CHAT, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context, kind.notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(if (kind.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        actions.take(3).forEachIndexed { index, action ->
            val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, action.requestId)
                putExtra(NotificationActionReceiver.EXTRA_OPTION_ID, action.optionId)
            }
            val pending = PendingIntent.getBroadcast(
                context, kind.notifId * 10 + index, actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, action.label, pending)
        }
        runCatching { NotificationManagerCompat.from(context).notify(kind.notifId, builder.build()) }
    }

    fun cancel(context: Context, kind: Kind) {
        NotificationManagerCompat.from(context).cancel(kind.notifId)
    }

    fun cleanupChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf("session_fgs", "session", "background").forEach { manager.deleteNotificationChannel(it) }
    }

    private fun ensureChannel(context: Context, kind: Kind) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(kind.channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(kind.channelId, context.getString(kind.channelName), kind.importance)
            )
        }
    }
}
