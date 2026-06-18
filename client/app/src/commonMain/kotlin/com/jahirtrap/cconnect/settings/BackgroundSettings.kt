package com.jahirtrap.cconnect.settings

expect fun batteryOptimizationIgnored(): Boolean?

expect fun requestIgnoreBatteryOptimization()

expect fun notificationsEnabled(): Boolean

expect fun requestEnableNotifications()
