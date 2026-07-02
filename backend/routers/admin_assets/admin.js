"use strict";

const TOKEN_KEY = "cconnect_admin_token";

const $ = (id) => document.getElementById(id);
let ws = null;
let reconnectTimer = null;
let uptimeTimer = null;
let manualClose = false;
let configDirty = false;

function token() { return localStorage.getItem(TOKEN_KEY) || ""; }
function setToken(t) { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY); }

async function api(path, options = {}) {
  const headers = Object.assign({}, options.headers);
  const t = token();
  if (t) headers["Authorization"] = "Bearer " + t;
  if (options.body) headers["Content-Type"] = "application/json";
  const res = await fetch(path, Object.assign({}, options, { headers }));
  if (res.status === 401) { showGate(); throw new Error("unauthorized"); }
  if (!res.ok) throw new Error("HTTP " + res.status);
  return res.json();
}

function wsUrl() {
  const proto = location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${location.host}/api/admin/ws?token=${encodeURIComponent(token())}`;
}

function connect() {
  manualClose = false;
  clearReconnect();
  if (ws) { try { ws.close(); } catch (e) {} ws = null; }
  try {
    ws = new WebSocket(wsUrl());
  } catch (e) {
    scheduleReconnect();
    return;
  }
  ws.onopen = () => setStatus(true);
  ws.onmessage = (ev) => { try { render(JSON.parse(ev.data)); } catch (e) {} };
  ws.onclose = (ev) => {
    setStatus(false);
    if (ev.code === 1008) { showGate(); return; }
    if (!manualClose) scheduleReconnect();
  };
  ws.onerror = () => {};
}

function disconnect() {
  manualClose = true;
  clearReconnect();
  if (ws) { try { ws.close(); } catch (e) {} ws = null; }
}

function scheduleReconnect() {
  clearReconnect();
  reconnectTimer = setTimeout(connect, 2000);
}
function clearReconnect() { if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; } }

function showGate() {
  disconnect();
  $("gate").classList.remove("hidden");
  $("panel").classList.add("hidden");
}

function toast(msg, kind) {
  const el = $("toast");
  el.textContent = msg;
  el.className = "toast" + (kind ? " " + kind : "");
  setTimeout(() => el.classList.add("hidden"), 2600);
}

function setStatus(ok) {
  $("status-dot").className = "dot " + (ok ? "ok" : "bad");
  $("status-text").textContent = ok ? "Live" : "Disconnected";
}

function fmtUptime(sinceEpoch) {
  let s = Math.max(0, Math.floor(Date.now() / 1000 - sinceEpoch));
  const h = Math.floor(s / 3600); s -= h * 3600;
  const m = Math.floor(s / 60); s -= m * 60;
  if (h) return `${h}h ${m}m`;
  if (m) return `${m}m ${s}s`;
  return `${s}s`;
}

function shortId(v, n) { return v ? String(v).slice(0, n || 8) : "—"; }
function projectName(cwd) { return cwd ? cwd.split("/").filter(Boolean).pop() || cwd : "—"; }

function el(tag, opts = {}) {
  const node = document.createElement(tag);
  if (opts.text != null) node.textContent = opts.text;
  if (opts.cls) node.className = opts.cls;
  return node;
}

function actionBtn(label, cls, handler) {
  const b = el("button", { text: label, cls: "btn small " + cls });
  b.type = "button";
  b.addEventListener("click", handler);
  return b;
}

function render(data) {
  $("gate").classList.add("hidden");
  $("panel").classList.remove("hidden");

  const s = data.stats;
  $("stat-conns").textContent = s.connections;
  $("stat-sessions").textContent = s.sessions;
  $("stat-running").textContent = s.running;

  const cfg = data.config;
  $("accept-toggle").checked = !!cfg.accept_connections;
  if (!configDirty) {
    $("cfg-max-connections").value = cfg.max_connections;
    $("cfg-rate-limit").value = cfg.rate_limit_per_min;
    $("cfg-max-queue").value = cfg.max_queue;
  }

  const cbody = $("conn-rows");
  cbody.replaceChildren();
  data.connections.forEach((c) => {
    const tr = el("tr");
    tr.appendChild(el("td", { text: c.id, cls: "mono" }));
    tr.appendChild(el("td", { text: c.ip, cls: "mono" }));
    const up = el("td", { text: fmtUptime(c.since), cls: "uptime-cell" });
    up.dataset.since = c.since;
    tr.appendChild(up);
    tr.appendChild(el("td", { text: shortId(c.session_id, 8), cls: "mono" }));
    const act = el("td", { cls: "actions" });
    act.appendChild(actionBtn("Disconnect", "danger", () => closeConn(c.id)));
    tr.appendChild(act);
    cbody.appendChild(tr);
  });
  $("conn-empty").classList.toggle("hidden", data.connections.length > 0);

  const sbody = $("session-rows");
  sbody.replaceChildren();
  data.sessions.forEach((se) => {
    const tr = el("tr");
    tr.appendChild(el("td", { text: shortId(se.channel, 10), cls: "mono" }));
    tr.appendChild(el("td", { text: projectName(se.cwd) }));
    const state = el("td");
    state.appendChild(el("span", { text: se.running ? "running" : "idle", cls: "pill " + (se.running ? "running" : "idle") }));
    tr.appendChild(state);
    tr.appendChild(el("td", { text: se.sockets }));
    tr.appendChild(el("td", { text: se.queued }));
    const act = el("td", { cls: "actions" });
    if (se.running) act.appendChild(actionBtn("Stop", "ghost", () => interruptSession(se.channel)));
    act.appendChild(actionBtn("Terminate", "danger", () => terminateSession(se.channel)));
    tr.appendChild(act);
    sbody.appendChild(tr);
  });
  $("session-empty").classList.toggle("hidden", data.sessions.length > 0);
}

function tickUptime() {
  document.querySelectorAll(".uptime-cell").forEach((el) => {
    const since = Number(el.dataset.since);
    if (since) el.textContent = fmtUptime(since);
  });
}

async function saveConfig() {
  const patch = {
    accept_connections: $("accept-toggle").checked,
    max_connections: Number($("cfg-max-connections").value) || 0,
    rate_limit_per_min: Number($("cfg-rate-limit").value) || 0,
    max_queue: Number($("cfg-max-queue").value) || 0,
  };
  try {
    await api("/api/admin/config", { method: "POST", body: JSON.stringify(patch) });
    configDirty = false;
    const saved = $("config-saved");
    saved.classList.remove("hidden");
    setTimeout(() => saved.classList.add("hidden"), 1800);
  } catch (e) {
    if (e.message !== "unauthorized") toast("Save failed", "bad");
  }
}

async function toggleAccept() {
  try {
    await api("/api/admin/config", {
      method: "POST",
      body: JSON.stringify({ accept_connections: $("accept-toggle").checked }),
    });
    toast($("accept-toggle").checked ? "Accepting connections" : "Connections paused", "ok");
  } catch (e) {
    if (e.message !== "unauthorized") { $("accept-toggle").checked = !$("accept-toggle").checked; toast("Failed", "bad"); }
  }
}

async function post(path, okMsg) {
  try {
    await api(path, { method: "POST" });
    if (okMsg) toast(okMsg, "ok");
  } catch (e) {
    if (e.message !== "unauthorized") toast("Action failed", "bad");
  }
}

const closeConn = (id) => post(`/api/admin/connections/${id}/close`, "Connection closed");
const interruptSession = (ch) => post(`/api/admin/sessions/${ch}/interrupt`, "Turn stopped");
const terminateSession = (ch) => {
  if (confirm("Terminate this session? Its running turn is stopped and it is evicted.")) {
    post(`/api/admin/sessions/${ch}/terminate`, "Session terminated");
  }
};
async function disconnectAll() {
  if (!confirm("Disconnect every active connection?")) return;
  post("/api/admin/connections/close-all", "All connections closed");
}

function init() {
  $("refresh-btn").addEventListener("click", connect);
  $("save-config-btn").addEventListener("click", saveConfig);
  $("accept-toggle").addEventListener("change", toggleAccept);
  $("disconnect-all-btn").addEventListener("click", disconnectAll);
  ["cfg-max-connections", "cfg-rate-limit", "cfg-max-queue"].forEach((id) =>
    $(id).addEventListener("input", () => { configDirty = true; })
  );

  $("gate-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const t = $("gate-token").value.trim();
    if (!t) return;
    setToken(t);
    $("gate-error").classList.add("hidden");
    connect();
  });

  uptimeTimer = setInterval(tickUptime, 1000);
  connect();
}

document.addEventListener("DOMContentLoaded", init);
