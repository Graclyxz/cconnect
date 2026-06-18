package com.jahirtrap.cconnect.settings

actual fun batteryOptimizationIgnored(): Boolean? = null

actual fun requestIgnoreBatteryOptimization() {}

actual fun notificationsEnabled(): Boolean = true

actual fun requestEnableNotifications() {}
