package com.jahirtrap.cconnect.files

expect suspend fun downloadShared(url: String, filename: String): Boolean

expect suspend fun saveSharedAs(url: String, filename: String): Boolean

expect suspend fun openSharedExternally(url: String, filename: String): Boolean
