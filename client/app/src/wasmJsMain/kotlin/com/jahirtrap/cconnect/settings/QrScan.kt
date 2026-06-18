package com.jahirtrap.cconnect.settings

actual object QrScan {
    actual fun isAvailable(): Boolean = false
    actual fun scan(onResult: (String?) -> Unit) {}
}
