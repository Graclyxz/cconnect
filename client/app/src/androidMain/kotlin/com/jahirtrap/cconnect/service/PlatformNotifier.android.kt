package com.jahirtrap.cconnect.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jahirtrap.cconnect.appContext
import com.jahirtrap.cconnect.shared.R

const val EXTRA_OPEN_CHAT = "open_chat"

internal actual val platformNotifier: PlatformNotifier = AndroidNotifier

private object AndroidNotifier : PlatformNotifier {

    @Volatile
    var onActivate: (() -> Unit)? = null

    override fun init(onActivate: () -> Unit) {
        this.onActivate = onActivate
    }

    override fun notify(kind: Notifier.Kind, title: String, text: String?, actions: List<Notifier.Action>) {
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(kind)
        val builder = NotificationCompat.Builder(appContext, channelId(kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(kind))
            .setAutoCancel(true)
            .setPriority(if (kind == Notifier.Kind.Interaction) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        actions.take(3).forEachIndexed { index, action ->
            builder.addAction(0, action.label, actionIntent(kind, index, action))
        }
        runCatching { manager.notify(kind.notifId, builder.build()) }
    }

    override fun cancel(kind: Notifier.Kind) {
        NotificationManagerCompat.from(appContext).cancel(kind.notifId)
    }

    private fun contentIntent(kind: Notifier.Kind): PendingIntent? {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launch.putExtra(EXTRA_OPEN_CHAT, true)
        return PendingIntent.getActivity(
            appContext, kind.notifId, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(kind: Notifier.Kind, index: Int, action: Notifier.Action): PendingIntent {
        val intent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, action.requestId)
            putExtra(NotificationActionReceiver.EXTRA_OPTION_ID, action.optionId)
        }
        return PendingIntent.getBroadcast(
            appContext, kind.notifId * 10 + index, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun channelId(kind: Notifier.Kind): String = when (kind) {
        Notifier.Kind.TaskDone -> "task_done"
        Notifier.Kind.Interaction -> "interaction"
    }

    private fun channelName(kind: Notifier.Kind): String = when (kind) {
        Notifier.Kind.TaskDone -> "Tareas terminadas"
        Notifier.Kind.Interaction -> "Permisos y preguntas"
    }

    private fun importance(kind: Notifier.Kind): Int = when (kind) {
        Notifier.Kind.TaskDone -> NotificationManager.IMPORTANCE_DEFAULT
        Notifier.Kind.Interaction -> NotificationManager.IMPORTANCE_HIGH
    }

    private fun ensureChannel(kind: Notifier.Kind) {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val id = channelId(kind)
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id, channelName(kind), importance(kind)))
        }
    }
}
