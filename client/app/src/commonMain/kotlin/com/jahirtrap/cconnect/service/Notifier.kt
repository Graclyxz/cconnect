package com.jahirtrap.cconnect.service

import kotlin.concurrent.Volatile

object Notifier {

    data class Action(val label: String, val requestId: String, val optionId: String)

    enum class Kind(val notifId: Int) {
        TaskDone(2),
        Interaction(3),
    }

    @Volatile
    var appInForeground: Boolean = false

    fun init(onActivate: () -> Unit) = platformNotifier.init(onActivate)

    fun notify(kind: Kind, title: String, text: String?, actions: List<Action> = emptyList()) {
        if (appInForeground) return
        platformNotifier.notify(kind, title, text, actions)
    }

    fun cancel(kind: Kind) = platformNotifier.cancel(kind)
}

internal interface PlatformNotifier {
    fun init(onActivate: () -> Unit)
    fun notify(kind: Notifier.Kind, title: String, text: String?, actions: List<Notifier.Action>)
    fun cancel(kind: Notifier.Kind)
}

internal expect val platformNotifier: PlatformNotifier
