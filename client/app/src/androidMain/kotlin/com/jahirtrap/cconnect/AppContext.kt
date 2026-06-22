package com.jahirtrap.cconnect

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

lateinit var appContext: Context
    private set

private var activityRef: WeakReference<Activity>? = null
val currentActivity: Activity? get() = activityRef?.get()

fun installAppContext(context: Context) {
    appContext = context.applicationContext
    if (context is Activity) activityRef = WeakReference(context)
}
