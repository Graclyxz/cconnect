package com.jahirtrap.cconnect.chat

class TabContext(
    var environmentId: String?,
    var cwd: String,
    val initialSessionId: String? = null,
    val initialProjectKey: String? = null,
    val initialTitle: String? = null,
    val initialColor: String? = null,
)
