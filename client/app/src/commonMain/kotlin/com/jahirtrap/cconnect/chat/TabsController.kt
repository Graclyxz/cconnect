package com.jahirtrap.cconnect.chat

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.jahirtrap.cconnect.data.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object TabsController {
    class Tab(val id: String, val ctx: TabContext) {
        val owner: ViewModelStoreOwner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val factory: ViewModelProvider.Factory = chatViewModelFactory(ctx)
        var title by mutableStateOf<String?>(null)
        var color by mutableStateOf<String?>(null)
        var running by mutableStateOf(false)
        var sessionId: String? = null
        var projectKey: String? = null
    }

    private val settings = Settings()
    private var counter = 0
    private fun nextId(): String = "tab${counter++}"

    private fun defaultTab(): Tab =
        Tab(nextId(), TabContext(settings.activeEnvironment?.id, settings.activeEnvironment?.directory.orEmpty()))

    private fun restore(): Pair<List<Tab>, Int> {
        val raw = settings.tabsState
        if (raw.isBlank()) return listOf(defaultTab()) to 0
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val tabs = (obj["tabs"]?.jsonArray ?: emptyList()).map { el ->
                val o = el.jsonObject
                val sid = o["sid"]?.jsonPrimitive?.contentOrNull
                val proj = o["proj"]?.jsonPrimitive?.contentOrNull
                Tab(
                    nextId(),
                    TabContext(o["env"]?.jsonPrimitive?.contentOrNull, o["cwd"]?.jsonPrimitive?.contentOrNull.orEmpty(), sid, proj),
                ).also { t ->
                    t.title = o["title"]?.jsonPrimitive?.contentOrNull
                    t.sessionId = sid
                    t.projectKey = proj
                }
            }.ifEmpty { listOf(defaultTab()) }
            tabs to (obj["active"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, tabs.lastIndex)
        }.getOrDefault(listOf(defaultTab()) to 0)
    }

    private val restored = restore()
    private val _tabs = mutableStateListOf<Tab>().apply { addAll(restored.first) }
    val tabs: List<Tab> get() = _tabs

    var activeId by mutableStateOf(_tabs[restored.second].id)
        private set

    val active: Tab get() = _tabs.firstOrNull { it.id == activeId } ?: _tabs.first()

    val stripScroll = ScrollState(0)

    fun openTab(environmentId: String?, cwd: String): Tab {
        val tab = Tab(nextId(), TabContext(environmentId, cwd))
        _tabs.add(tab)
        activeId = tab.id
        syncActiveEnv()
        persist()
        return tab
    }

    fun newTab(): Tab {
        val envId = active.ctx.environmentId ?: settings.activeEnvironment?.id
        val dir = settings.environments.firstOrNull { it.id == envId }?.directory.orEmpty()
        return openTab(envId, dir)
    }

    fun openSessionTab(environmentId: String?, cwd: String, sessionId: String, projectKey: String): Tab {
        val tab = Tab(nextId(), TabContext(environmentId, cwd, sessionId, projectKey)).also {
            it.sessionId = sessionId
            it.projectKey = projectKey
        }
        _tabs.add(tab)
        activeId = tab.id
        syncActiveEnv()
        persist()
        return tab
    }

    fun updateActive(title: String?, color: String?, running: Boolean, sessionId: String?, projectKey: String?) {
        val tab = active
        tab.running = running
        tab.color = color
        val changed = tab.title != title || tab.sessionId != sessionId || tab.projectKey != projectKey
        tab.title = title
        tab.sessionId = sessionId
        tab.projectKey = projectKey
        if (changed) persist()
    }

    fun selectTab(id: String) {
        if (id == activeId || _tabs.none { it.id == id }) return
        activeId = id
        syncActiveEnv()
        persist()
    }

    fun closeTab(id: String) {
        val idx = _tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val tab = _tabs.removeAt(idx)
        tab.owner.viewModelStore.clear()
        if (_tabs.isEmpty()) _tabs.add(defaultTab())
        if (activeId == id) activeId = _tabs[idx.coerceAtMost(_tabs.lastIndex)].id
        syncActiveEnv()
        persist()
    }

    fun selectNext() {
        val i = _tabs.indexOfFirst { it.id == activeId }
        if (i < 0 || _tabs.size < 2) return
        selectTab(_tabs[(i + 1) % _tabs.size].id)
    }

    fun selectPrev() {
        val i = _tabs.indexOfFirst { it.id == activeId }
        if (i < 0 || _tabs.size < 2) return
        selectTab(_tabs[(i - 1 + _tabs.size) % _tabs.size].id)
    }

    fun moveActive(delta: Int) {
        val i = _tabs.indexOfFirst { it.id == activeId }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, _tabs.lastIndex)
        if (i == j) return
        _tabs.add(j, _tabs.removeAt(i))
        persist()
    }

    fun closeActive() = closeTab(activeId)

    private fun syncActiveEnv() {
        val envId = active.ctx.environmentId ?: return
        if (settings.activeEnvironmentId != envId) settings.activeEnvironmentId = envId
    }

    private fun persist() {
        val activeIdx = _tabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        val json = buildJsonObject {
            put("active", activeIdx)
            putJsonArray("tabs") {
                _tabs.forEach { t ->
                    addJsonObject {
                        t.ctx.environmentId?.let { put("env", it) }
                        put("cwd", t.ctx.cwd)
                        t.sessionId?.let { put("sid", it) }
                        t.projectKey?.let { put("proj", it) }
                        t.title?.let { put("title", it) }
                    }
                }
            }
        }
        runCatching { settings.tabsState = json.toString() }
    }
}
