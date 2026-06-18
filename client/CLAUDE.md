# CLAUDE.md — CConnect Client (desktop + web + Android)

Compose Multiplatform app (Kotlin, Material3 1.5 alpha, MaterialExpressive) that
drives the CConnect backend via REST + WebSocket. One codebase, three targets:

- **desktop** — JVM (`jvm("desktop")`), packaged as native installers for
  Windows, Linux and macOS.
- **wasmJs** — the same UI compiled to WebAssembly, a static site hosted on
  Cloudflare Pages.
- **android** — `androidTarget()`. Because AGP 9 rejects the KMP plugin on a
  `com.android.application` module, `:app` is a `com.android.library` and the thin
  `:androidApp` module (`com.android.application`) wraps it into the APK.

`desktopMain` and `androidMain` share an intermediate **`jvmSharedMain`** source
set (both JVM) for JVM-only code — notably the SSH terminal (sshj + the libvterm
emulator) and `SshConnection`.

Talks to a Claude Code instance running on a PC, locally over Tailscale or
publicly over a Tailscale Funnel.

Package: `com.jahirtrap.cconnect`. **This is the single CConnect app** — desktop,
web and Android all build from `commonMain`; real platform differences live behind
expect/actual and the `LocalIsTouch` / `isWebPlatform` / `isAndroidPlatform`
locals. `mobile/` is the old standalone Android app, now **legacy** — not built or
kept in sync.

---

## Architecture

```
ChatScreen (UI) ──> ChatViewModel ──> ChatSocket (WebSocketConn) ──> backend /api/chat/ws
                                  └─> SessionsApi / SharedApi / ClaudeApi / ... (Http) ──> backend /api/...
```

- Single ViewModel per screen; state via `StateFlow<ChatUiState>`. The VM and all
  business logic live in **`commonMain`** and are shared verbatim with both
  targets (and conceptually with mobile).
- Networking is abstracted behind **expect/actual**: `data/remote/HttpTransport`,
  `WebSocketConn`, `SharedHttp`, `UrlCodec`, `AppImageLoader` and the
  `GitHubApi` cache hooks have a desktop actual (OkHttp + JVM) and a wasmJs
  actual (Ktor/browser `fetch` + the DOM WebSocket). `Backend` holds the active
  connection (kind, host, port, authKind, auth fields), mirrored from `Settings`
  on start and on every switch.
- All requests carry `Backend.authHeaders` (Bearer, Basic, or custom), the same
  headers on downloads, uploads, Coil image loads and previews.

## Project Structure

`commonMain` holds the screens, view-models, models and the whole `ui/` toolkit;
each platform supplies the `actual` plumbing in `desktopMain` / `wasmJsMain`.

```
client/app/src/
├── commonMain/kotlin/com/jahirtrap/cconnect/
│   ├── Platform.kt              # expect isWebPlatform / isCoarsePointer() / bringAppToFront() + desktopWindowToFront hook
│   ├── chat/                    # ChatScreen, ChatBlocks, ChatViewModel(+Factory), PermissionUi, ChatUrl(expect)
│   ├── claude/                  # ClaudeScreen + ClaudeDetailScreen (enum ClaudeKind) — same hub as mobile
│   ├── data/
│   │   ├── ChatModels / SessionModels / EnvironmentProfile / QrConnectionPayload / SshProfile+SshStore
│   │   ├── AppCompat.kt         # version-range compare for the app/server/CLI contract
│   │   ├── AppUpdater.kt        # expect: openRelease / downloadAndInstall / reload
│   │   ├── Settings.kt / AppPrefs.kt(expect)   # persisted prefs (desktop: java Prefs/file; web: localStorage)
│   │   ├── Clock / DateFormat / NumberFormat    # expect time/number formatting (no kotlinx-datetime dep)
│   │   └── remote/              # Backend, Http, ChatSocket, Sessions/Shared/Claude/Cli/Capabilities/Settings/System Api,
│   │                           #   GitHubApi, + expect HttpTransport/WebSocketConn/SharedHttp/UrlCodec/AppImageLoader
│   ├── files/                   # FileExplorerScreen, FilePreviewScreen, UploadManager, FileDrop, FilePicker(expect),
│   │                           #   AttachmentFile(expect), ClipboardPaste(expect), FilesUrl(expect), SharedActions(expect), PreviewKind
│   ├── monitor/MonitorScreen.kt # live PC monitor (resource graphs + server logs)
│   ├── service/Notifier.kt      # expect notifications (desktop tray/notify-send; web Notification API)
│   ├── settings/                # SettingsScreen + SettingsComponents (SettingsGroup/PreferenceRow, reused by claude/)
│   ├── terminal/                # TerminalScreen + TerminalSession(expect) + OsIcons  (SSH; real impl desktop-only)
│   └── ui/                      # the shared toolkit (see below) incl. Touch, HistoryNav, DismissStack, ClipboardShortcuts,
│                               #   BackInterceptor, Dialogs, Menus, AppBottomSheet, PopupMenu, MarkdownText, theme/
├── desktopMain/kotlin/...       # actuals: Main.kt (Window + App), WindowTitleBar, OkHttp transport, AWT clipboard,
│   │                           #   FileDialogs (lwjgl tinyfd), FileTransfer, SshConnection+TerminalEmulator+TerminalView
│   └── ...
├── wasmJsMain/
│   ├── kotlin/...               # actuals: Main.kt (ComposeViewport + App), Ktor/browser transport, document listeners,
│   │                           #   FilesUrl/ChatUrl via window.history, ClipboardPaste via "paste" event
│   └── resources/               # index.html, cconnect.js entry, sw.js (service worker), manifest.json, favicon.png, _redirects
```

## Platform abstractions (expect/actual)

The single most important client-specific concern. Each of these is an `expect`
in `commonMain` with a `desktop` + `wasmJs` actual:

| expect | desktop actual | wasmJs actual |
|---|---|---|
| `isWebPlatform` (Platform.kt) | `false` | `true` |
| `isCoarsePointer()` | `false` | `matchMedia('(pointer: coarse)') \|\| maxTouchPoints>0` |
| `bringAppToFront()` | `window.toFront()+requestFocus()` (via `desktopWindowToFront` set in Main) | no-op |
| `HttpTransport` / `WebSocketConn` / `SharedHttp` | OkHttp + JVM | Ktor client + browser `fetch`/`WebSocket` |
| `AppUpdater` | download installer → `Desktop.open` runs it; `reload`=no-op | `downloadAndInstall`=false; **`reload`** clears SW caches + `location.reload()` |
| `Notifier` | tray / `notify-send -a CConnect` on Linux | Web Notification API; `appInForeground` from `document.hasFocus()` |
| `FilePicker` / `FileDialogs` | lwjgl tinyfd native dialogs | `<input type=file>` | 
| `AttachmentFile` | wraps `java.io.File` | wraps `org.w3c.files.File` |
| `ClipboardPaste` | AWT clipboard (files / image→temp PNG) | document `"paste"` event → `clipboardData.files` |
| `ClipboardShortcuts` (dispatchClipboardShortcut) | fed from the Window `onPreviewKeyEvent` | document `"keydown"` listener |
| `FilesUrl` / `ChatUrl` | no-op (in-memory folder history instead) | `window.history` pushState/popstate (SPA URLs) |
| `Clock` / `DateFormat` / `NumberFormat` / `TimeFormat` | `java.time` / `java.text` | JS `Date` / `Intl` |
| `AppPrefs` | file/Java Prefs | `localStorage` |
| `TerminalSession` | sshj-backed PTY (SshConnection + libvterm TerminalEmulator), shared with android in **`jvmSharedMain`** | stub (no JVM sshj in the browser) |

**Android** (`androidMain`) supplies its own actual for each of the above
(`AppPrefs` → `EncryptedSharedPreferences` for the secure store; `Notifier` →
NotificationManager; `FilePicker`/`AttachmentFile` → SAF; `TerminalSession` →
shared with desktop via `jvmSharedMain`; etc.) plus `isAndroidPlatform = true`.

**Rule:** never branch on platform inside `commonMain` with ad-hoc checks beyond
`isWebPlatform` / `isAndroidPlatform` / the CompositionLocals below; put real
divergence behind an expect/actual.

## Touch & layout detection (single source of truth)

- **`LocalIsTouch`** (`ui/Touch.kt`) — provided once in `CConnectTheme` via
  `ProvideIsTouch` (root pointerInput, Initial pass, reads `PointerType` → touch
  on Touch / false on Mouse; **seeded** by `isCoarsePointer()` so web-on-phone
  starts correct, no flash). Read `LocalIsTouch.current` everywhere; do **not**
  add per-component pointer detectors.
- **`LocalMobileLayout`** (also `ui/Touch.kt`) — `true` when the viewport is
  portrait **or** narrow (`height>width || width<600dp`), recomputed on resize
  from `LocalWindowInfo.containerSize`. Drives the responsive panel.
- Uses: `focusable = !LocalIsTouch.current` on the field-style dropdowns
  (SelectField, the chat SelectorChip, AbovePopupMenu) — touch keeps `false`
  like mobile (a focusable popup misbehaves on touch); mouse gets `true` so Esc
  closes them; reload/refresh buttons hidden when `LocalIsTouch`; pull-to-refresh
  gated by touch; interactive scrollbars not drawn on touch.

## Responsive left panel

The chat's environments/projects/sessions panel adapts to `LocalMobileLayout`:

- **Mobile layout** → a `ModalNavigationDrawer` (gesturesEnabled, drawer sheet =
  `ChatPanelContent`), opened by a hamburger in the top bar. `drawerState` is
  hoisted to the App (Main.kt) so it survives navigation (open drawer → Files →
  back → still open), and a `LaunchedEffect(LocalMobileLayout)` closes it on the
  desktop↔mobile switch so it always starts closed in mobile.
- **Wide layout** → the inline 64↔300dp sidebar `Surface` (its own persisted
  `expanded`), independent of the drawer state.
- Matches mobile's `MaterialExpressiveTheme(MotionScheme...)` wrapping. The
  drawer's close button shows on non-touch (the desktop/web difference from
  mobile); on touch it's null (gesture/scrim only).

## Keyboard, mouse & clipboard (focus-independent)

Desktop/web input that has no mobile equivalent. All routed at the **window**
(desktop `Window.onPreviewKeyEvent`) / **document** (web listeners) level so they
work regardless of which composable has focus:

- **`ClipboardShortcutHandler`** (`ui/ClipboardShortcuts.kt`) — `ClipKey {Copy,
  Cut, Paste, Cancel}`. Files registers it (gated `!searching` + no dialog) for
  Ctrl+C/X/V driving the existing move/copy **transfer** system (`TransferOp`),
  Esc=Cancel clears a marked transfer; Ctrl+V also falls back to OS-clipboard
  upload. Chat registers it (gated `!sideActive && !mobileDrawerOpen && !dialog`)
  for Ctrl+V upload + Esc closing the quick-chat.
- **`ClipboardPaste`** (`files/ClipboardPaste.kt`) — OS file/image paste into
  Files (`pendingUploads`) and Chat (`vm.addAttachments`); web via the document
  `"paste"` event (delivers files), desktop via the AWT clipboard inside the
  shortcut handler.
- **`DismissStack`** (`ui/DismissStack.kt`) — shared dialogs/sheets/dropdowns
  register `Dismissable(onDismiss)`; the desktop **mouse-back** button closes the
  topmost one before navigating (`if (!DismissStack.dismissTop() && !HistoryNav.back()) goBack()`).
  Esc layering stays native per focusable popup (so only the topmost closes).
- **`HistoryNav`** (`ui/HistoryNav.kt`) — browser-like back/forward. WEB is
  native (mouse buttons → `window.history` → popstate, folder URLs via
  `FilesUrl`). DESKTOP has an in-memory folder history inside FileExplorerScreen;
  mouse button 4 = back, button 5 = forward (read in Main's pointerInput via
  `isBackPressed`/`isForwardPressed` + AWT button 4/5). The Files toolbar "up"
  button still goes to the parent folder.
- **`BackInterceptor`** (`ui/BackInterceptor.kt`) — the logical back stack
  (deselect → goUp parent → close overlay), consulted by Esc and mouse-back
  fallback.

## Connection model

`EnvironmentProfile`: `kind ∈ "http" | "https"`, `port: Int?` (null for https =
implicit 443; defaults 8723 for http), `authKind ∈ none|bearer|basic|header`.
`Backend.authHeaders` flattens auth into headers consumed uniformly. **On web the
environment form offers only HTTPS** (`kind` defaults to "https", the protocol
SelectField hides HTTP) because the HTTPS page can't reach `http://`/`ws://`
backends (mixed content); desktop keeps both for local backends. QR setup is
desktop/web manual (paste URL + token); the camera-scan flow is Android-only
(`androidMain` `QrScan`).

**Per-host overrides:** `EnvironmentProfile` also stores `model` / `effort` /
`permissionMode` (`""` = inherit the server default) and `streaming` (`Boolean?`,
null = inherit), edited from the chat toolbar — each selector has a "Servidor"
entry, and streaming is a `Radio`/`RadioOff` toggle. The effective value
(`override ?: server`) is what the toolbar shows and what the WS `start` sends;
Settings → Generación still sets the **server** default via `SettingsApi.update`.
The environments blob and SSH passwords are kept in a **secure** `AppPrefs`
(`AppPrefs(name, secure = true)` → Android EncryptedSharedPreferences, Windows
DPAPI via JNA; plain elsewhere).

## WebSocket event handling

`ChatSocket` parses server JSON into `ServerEvent`; `ChatViewModel.onEvent` turns
each into a `ChatMessage` or state mutation — identical to mobile. Notable:

| Event | Role / Effect |
|---|---|
| `assistant_text` / `thinking` | streamed into the current ASSISTANT / THINKING message |
| `tool_use` / `tool_result` | TOOL block (input + folded result), running spinner until the result |
| `file_change` | FILE_CHANGE diff block (backend pre-classifies each line) |
| `interaction_request` | INTERACTION block (permission buttons or question form) |
| `todos` / `task` | top-bar todos / task indicator |
| `command` / `usage` | local-command markdown / ephemeral plan-usage markdown |
| **`compacting`** | sets `state.compacting=true` → the "Compactando" progress bar (fired by the backend's PreCompact hook, so it shows on **auto**-compaction too, not just manual `/compact`) |
| `compact` / `compact_summary` | compaction block + summary; `compact` also clears `compacting` |
| **`status`** | transient retry/reconnect indicator (`streamStatus`) → orange "Reintentando" bar (kind `retrying`) / red "Fallo temporal" (`failed`); `ok` clears it. The backend classifies API failures: transient (5xx / connection / "no response from API") → `status`, usage-limit/auth/etc → `error` |
| `result` / `done` / `interrupted` / `error` | session id / UI transitions; an `error` block now shows a red warning icon + the clean SDK message |
| `history_chunk` | older messages backfill (prepended; non-active session dropped) |

Reconnect/replay (`channel`+`last_seq` resume tokens), the cursor-based
transcript window (100 initial / 500 tail cap), chat attachments (sequential
upload to `shared/uploads/` then `attachments:[relpaths]`; backend builds the
native vision blocks), the file manager, FilePreview (typed renderer + optional
delete via the route-level overlay), the Claude manager, the Monitor (system WS),
markdown rendering, code-edit diffs, and rewind all behave as documented in
`mobile/CLAUDE.md` — the logic is shared `commonMain`. Differences from mobile:

- **Drag & drop upload** (`files/FileDrop.kt`, `Modifier.fileDropTarget`) — OS
  file drops into chat and the files folder (mobile N/A); desktop reads
  `DragData.FilesList`, web `transferData.domDataTransferOrNull.files`.
- **Previews/HTML** open in the browser tab on web / a window on desktop, not a
  WebView.
- **Interactive scrollbars** (`ui/ScrollIndicator.kt`) on scrollable content
  blocks (code, tables, diffs) for mouse, hidden on touch (mobile keeps swipe).

## Version compatibility & updates

Same split as mobile and `backend/CLAUDE.md`: **compat** (AppOutdated /
ServerOutdated / CliOutdated NoticeCards) comes from the backend
(`CapabilitiesApi` → `evaluateCompat`); **"update available"** comes only from
**GitHub** (`checkForUpdates()` on open + the Settings button →
`GitHubApi.latestRelease()` → `latestRelease`). The two never cross.

- **Desktop** downloads the OS installer (`installerExtensions`: .msi/.exe / .deb/.rpm / .dmg) and opens it; the Windows MSI uses a stable `upgradeUuid` + `perUserInstall` so it upgrades in place.
- **Web** has no installer: when `latestRelease != null && isWebPlatform` the
  Settings button is **"Actualizar" → `AppUpdater.reload()`** (clears the SW
  caches + reloads). The service worker (`sw.js`) is network-first with
  `cache: 'no-cache'` so a redeployed build is picked up on reload.

## SSH client (desktop + Android)

`TerminalScreen` (commonMain UI) over `TerminalSession` (expect). The desktop and
Android actual is the full client, **shared in `jvmSharedMain`** — `SshConnection`
(sshj, password auth, `PromiscuousVerifier`, OS probe, debounced resize) + a
libvterm-style `TerminalEmulator`/`TerminalView`, with BouncyCastle for modern
OpenSSH defaults. On touch, input goes through a hidden `BasicTextField`: a space
sentinel makes the on-screen Backspace fire, `KeyboardType.Ascii` +
`autoCorrectEnabled = false` keep characters literal, and `imeAction = Go` sends
CR; physical keys still route through `onPreviewKeyEvent`. The wasmJs actual is a
stub (no JVM sshj in the browser).

## Build / packaging

- **Run desktop:** `./gradlew :app:run`. **Run web:** `./gradlew :app:wasmJsBrowserDevelopmentRun`.
- **Compile-check (use this to verify edits):**
  `./gradlew :app:compileKotlinDesktop :app:compileDebugKotlinAndroid :app:compileKotlinWasmJs`.
- **Android APK:** `./gradlew :androidApp:assembleRelease` →
  `androidApp/build/outputs/apk/release/CConnect-<ver>.apk` (R8 + proguard; signed
  when `client/key.properties` + the keystore exist, unsigned otherwise).
- **Desktop installers:** `./gradlew :app:packageDistributionForCurrentOS` →
  `app/build/compose/binaries/main/{msi,exe,deb,rpm,dmg}/`. `nativeDistributions`
  (build.gradle.kts) sets `packageName=CConnect`, the Windows `upgradeUuid` +
  `perUserInstall`, and per-OS icons. Linux installers are built on `ubuntu-22.04`
  in CI so the `.deb` links against jammy lib names.
- **Web build:** `./gradlew :app:wasmJsBrowserDistribution` →
  `app/build/dist/wasmJs/productionExecutable/` (index.html, cconnect.js, the
  `.wasm`, sw.js, manifest.json, favicon.png, `_redirects`, composeResources).
- **CI:** `.github/workflows/android.yml` (repo root) builds the Android APK on
  push/PR/tag and uploads it as an artifact (signed when the `KEYSTORE_BASE64` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` repo secrets are set, else
  unsigned). The web is served from Cloudflare Pages (`_redirects`
  `/* /index.html 200` gives SPA routing so `/files` etc. survive a reload);
  desktop installers are built per-OS (Linux on `ubuntu-22.04` so the `.deb` links
  against jammy libs). `mobile/` is not built in CI.
- **Version contract:** `appVersionName` + `SUPPORTED_SERVER` are generated into
  `BuildConfig` (see the `generateBuildConfig` task) — keep them in step with the
  backend's `[tool.cconnect]` table. `appVersionName` + `appVersionCode` are also
  set in `androidApp/build.gradle.kts` for the APK.

## Conventions

1. **One app, many platforms.** desktop/web/Android share `commonMain`; gate real
   divergences by `LocalIsTouch` / `isWebPlatform` / `isAndroidPlatform` /
   expect-actual (touch: pull-to-refresh, system back, swipe scroll; desktop/web:
   mouse buttons, OS clipboard, drag&drop, window). `mobile/` is legacy — do not
   keep it in sync.
2. **Backend is the source of truth.** Mirror its event shapes verbatim.
3. **Real platform divergence goes behind expect/actual**, not ad-hoc branches in
   `commonMain`.
4. **Comments are WHY-only.** No noise restating the code.
5. **No inline fully-qualified names.** Always import.
6. **Reusable shared components** (especially `ui/`) aren't deleted even if
   temporarily unused — they keep porting cheap.
7. **Español neutro** for user-facing Spanish (no regionalisms). Accent color
   names stay in English.
