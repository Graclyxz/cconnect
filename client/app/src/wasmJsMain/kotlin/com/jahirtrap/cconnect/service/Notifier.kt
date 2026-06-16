package com.jahirtrap.cconnect.service

internal actual val platformNotifier: PlatformNotifier = WebNotifier

private object WebNotifier : PlatformNotifier {

    override fun init(onActivate: () -> Unit) {}

    override fun notify(kind: Notifier.Kind, title: String, text: String?, actions: List<Notifier.Action>) {
        showNotification(title, text.orEmpty())
    }

    override fun cancel(kind: Notifier.Kind) {}
}

private fun showNotification(title: String, body: String) {
    js("if (typeof Notification !== 'undefined') { if (Notification.permission === 'granted') { new Notification(title, { body: body }); } else if (Notification.permission !== 'denied') { Notification.requestPermission().then(function(p) { if (p === 'granted') new Notification(title, { body: body }); }); } }")
}
