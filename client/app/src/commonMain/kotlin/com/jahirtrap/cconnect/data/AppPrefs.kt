package com.jahirtrap.cconnect.data

expect class AppPrefs(name: String, secure: Boolean = false) {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun contains(key: String): Boolean
    fun putString(key: String, value: String?)
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun remove(key: String)
}

fun AppPrefs.edit(block: AppPrefs.() -> Unit) {
    block()
}
