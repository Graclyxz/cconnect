package com.jahirtrap.cconnect

import android.app.Application

class CConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installAppContext(this)
    }
}
