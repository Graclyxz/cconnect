package com.jahirtrap.cconnect

import android.content.Context

lateinit var appContext: Context
    private set

fun installAppContext(context: Context) {
    appContext = context.applicationContext
}
