package com.jahirtrap.cconnect.settings

expect object QrScan {
    fun isAvailable(): Boolean
    fun scan(onResult: (String?) -> Unit)
}
