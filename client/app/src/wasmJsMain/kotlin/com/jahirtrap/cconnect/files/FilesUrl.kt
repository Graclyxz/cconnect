package com.jahirtrap.cconnect.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event

private fun encodeUri(value: String): String = js("encodeURIComponent(value)")
private fun decodeUri(value: String): String = js("decodeURIComponent(value)")

private fun parseQuery(search: String): Map<String, String> {
    val raw = search.removePrefix("?")
    if (raw.isEmpty()) return emptyMap()
    return raw.split("&").mapNotNull { part ->
        val i = part.indexOf('=')
        if (i < 0) null else decodeUri(part.substring(0, i)) to decodeUri(part.substring(i + 1))
    }.toMap()
}

private fun buildUrl(path: String, archive: String?, archiveDir: String): String {
    val params = buildList {
        if (archive != null) {
            add("a=" + encodeUri(archive))
            if (archiveDir.isNotEmpty()) add("ad=" + encodeUri(archiveDir))
        } else if (path.isNotEmpty()) {
            add("p=" + encodeUri(path))
        }
    }
    return "/files" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

actual fun readFilesLocation(): Triple<String, String?, String>? {
    if (window.location.pathname != "/files") return null
    val q = parseQuery(window.location.search)
    val a = q["a"]
    if (a != null) return Triple(a.substringBeforeLast('/', ""), a, q["ad"] ?: "")
    return Triple(q["p"] ?: "", null, "")
}

actual fun syncFilesLocation(path: String, archive: String?, archiveDir: String) {
    val target = buildUrl(path, archive, archiveDir)
    if (target != window.location.pathname + window.location.search) {
        window.history.pushState(null, "", target)
    }
}

actual fun filesHistoryBack() {
    window.history.back()
}

@Composable
actual fun FilesPopstate(onLocation: (String, String?, String) -> Unit) {
    val current by rememberUpdatedState(onLocation)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = {
            if (window.location.pathname == "/files") {
                readFilesLocation()?.let { current(it.first, it.second, it.third) }
            }
        }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }
}
