package com.jahirtrap.cconnect.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.jahirtrap.cconnect.appContext

var androidNotificationRequest: (() -> Unit)? = null

private val powerManager get() = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

actual fun batteryOptimizationIgnored(): Boolean? =
    powerManager.isIgnoringBatteryOptimizations(appContext.packageName)

actual fun requestIgnoreBatteryOptimization() {
    runCatching {
        appContext.startActivity(
            if (batteryOptimizationIgnored() != true) Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

actual fun notificationsEnabled(): Boolean =
    NotificationManagerCompat.from(appContext).areNotificationsEnabled()

actual fun requestEnableNotifications() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        androidNotificationRequest?.invoke()
    } else runCatching {
        appContext.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
