# CLAUDE.md — CConnect Mobile

Android app (Jetpack Compose, Kotlin 2.2, Compose BOM 2025.09, Material3
1.5 alpha) that drives the CConnect backend via REST + WebSocket. Talks to a
Claude Code instance running on a PC, locally over Tailscale or publicly over
a Tailscale Funnel.

Package: `com.jahirtrap.cconnect`. Min SDK 26, target SDK 36.

---

## Architecture

```
ChatScreen (UI) ──> ChatViewModel ──> ChatSocket (OkHttp WS) ──> backend /api/chat/ws
                                  └─> SessionsApi / SharedApi / ClaudeApi / ... (Http) ──> backend /api/...
```

- Single ViewModel per screen; state via `StateFlow<ChatUiState>`.
- Networking via OkHttp 4 (`data/remote/Http.kt`). The `Backend` object holds
  the active connection (kind, host, port, authKind, auth fields), mirrored
  from `Settings` on app start and on every switch.
- All requests pass through `Http.applyAuth(builder)` which appends the headers
  built by `Backend.authHeaders` (Bearer, Basic, or custom). The same headers
  ride on downloads (`FileTransfer`), uploads, Coil image loads
  (`AppImageLoader`) and the file-preview WebView.

## Project Structure

```
mobile/app/src/main/java/com/jahirtrap/cconnect/
├── MainActivity.kt              # Navigation hub: chat / settings / explorer / claude / terminal + FilePreview overlay (PreviewRequest(url, name, onDelete?)); BouncyCastle swap; runtime locale
├── chat/
│   ├── ChatScreen.kt            # Top-level screen + drawer (environments/projects/sessions) + scroll logic + notices + rewind sheet/dialog + quick chat + tasks
│   ├── ChatBlocks.kt            # ChatMessageItem and per-role renderers (incl. user attachment chips + transcript images)
│   ├── ChatViewModel.kt         # State + WS events + transcript window + attachments upload + version compat + rewind
│   └── PermissionUi.kt          # permissionStyle() → icon + Palette color per mode
├── claude/
│   ├── ClaudeScreen.kt          # Claude manager hub: CLI (version/changelog/source/update inline), user prompt editor, links to detail screens
│   └── ClaudeDetailScreen.kt    # One parameterized screen (enum ClaudeKind): Plugins / Skills / Mcp / Marketplaces / Memories
├── data/
│   ├── ChatModels.kt            # Role, ChatMessage (attachments/images), InteractionData, ServerEvent, DiffLine, TodoItem, Capabilities
│   ├── SessionModels.kt         # ProjectInfo, SessionInfo, ...
│   ├── AppCompat.kt             # Version-range comparison for the app/server/CLI contract
│   ├── AppUpdater.kt            # GitHub release check + APK download
│   ├── QrConnectionPayload.kt   # Parse the QR JSON {url, token}
│   ├── EnvironmentProfile.kt    # Saved backend connection (kind, host, port?, authKind, ...)
│   ├── Settings.kt              # SharedPreferences-backed; syncs Backend
│   ├── SshProfile.kt / SshStore.kt  # SSH hosts (EncryptedSharedPreferences)
│   └── remote/
│       ├── Backend.kt           # Active connection + computed baseUrl/wsUrl + auth headers
│       ├── Http.kt              # GET/POST/PUT/DELETE helpers, applyAuth, envelope unwrap
│       ├── ChatSocket.kt        # WebSocket client + event parser + resume tokens
│       ├── SessionsApi.kt       # projects/sessions/messages + rename/color/delete + rewind points/preview/confirm
│       ├── SharedApi.kt         # list/delete/mkdir/rename/move/copy/absolutePaths + streamed upload(progress) + downloadUrl/relativeFromUrl
│       ├── ClaudeApi.kt         # /api/claude/*: userPrompt, extensions, catalog, pluginAction, marketplaceAction, skills, mcp, memories
│       ├── SystemApi.kt         # Monitor WS stream (system/logs events) + restart()
│       ├── CliApi.kt            # /api/cli: status/setSource/update
│       ├── CapabilitiesApi.kt, SettingsApi.kt
│       ├── GitHubApi.kt         # Releases + changelogs + profile, disk-cached (see below)
│       └── AppImageLoader.kt    # Coil ImageLoader with auth headers (+ SVG decoding)
├── files/
│   ├── FileExplorerScreen.kt    # File manager over backend/shared/ (see below)
│   ├── FilePreviewScreen.kt     # Typed preview + optional delete (see below)
│   ├── FileTransfer.kt          # DownloadManager + OkHttp save-as / share (with auth headers)
│   └── UploadManager.kt         # Upload queue state (progress ring data, cancel)
├── monitor/
│   └── MonitorScreen.kt         # Live PC monitor: resource graphs + server logs (see below)
├── service/                     # Notifier (background notifications) + NotificationActionReceiver (action buttons)
├── settings/
│   ├── SettingsScreen.kt        # Preferences; SettingsGroup/PreferenceRow are public (reused by claude/); highlight targets
│   └── QrScanner.kt             # play-services-code-scanner wrapper (no camera permission)
├── terminal/
│   ├── TerminalScreen.kt        # SSH host list + edit dialog + termlib-backed session
│   ├── SshConnection.kt         # sshj wrapper: connect, PTY shell, debounced resize, OS probe
│   └── OsIcons.kt               # iconForOs / colorForOs: FA Brands + distro brand colors
└── ui/
    ├── MarkdownText.kt          # CommonMark parser + Compose renderer; unified link handling (see below)
    ├── Dialogs.kt               # CompactDialog, ConfirmDialog, SelectDialog, DialogSelectItem/ActionItem, SharedLinkActionsDialog, RenameDialog, EnvironmentSelectDialog, ...
    ├── AppBottomSheet.kt        # Full-height modal sheet; `dismissible=false` → no handle, no drag gesture, Back/X close with animation
    ├── PopupMenu.kt             # AbovePopupMenu + position provider (popup anchored above, scrim, animation) — chat command menu + Files "More"
    ├── AttachmentChip.kt        # File chip (icon + name + optional remove) — composer + message bubbles
    ├── NoticeCard.kt            # Alert card with action + dismiss (version notices)
    ├── Metrics.kt               # MetricHeader + MetricBar (title/subtitle/% + progress bar, red ≥90%) — Monitor disks & Claude usage
    ├── InputField.kt / SelectField (Menus.kt) / ActionButton.kt / OutlinedPanel.kt / ListRow.kt
    ├── AppTopBar.kt / AppLogo.kt / AppOptions.kt / ColorSwatch.kt / DropdownScrim.kt
    ├── EmptyState.kt / Loading.kt (CenteredProgress, StatusDot) / ScrollIndicator.kt
    ├── SecretTextField.kt       # OutlinedTextField with show/hide toggle for tokens/passwords
    ├── CustomIcons.kt           # PlayFilled, Stop, Claude logo — filled icons matching Lucide shapes
    ├── TooltipIconButton.kt     # IconButton wrapped with M3 PlainTooltip + custom anchor provider
    └── theme/                   # CConnectTheme (MaterialExpressive), Palette (semantic colors), Accents, SessionColors
```

## Connection model

`EnvironmentProfile`:

- `kind` ∈ `"http" | "https"`.
- `port: Int?` — nullable. For `https` it's always `null` (implicit 443); the
  edit dialog hides the port field. For `http` it defaults to 8723.
- `authKind` ∈ `"none" | "bearer" | "basic" | "header"`. `Backend.authHeaders`
  flattens the active auth into a `List<Pair<String,String>>` consumed
  uniformly by `Http`, `ChatSocket`, `FileTransfer`, Coil and the WebView.

`Backend.address` (`host` or `host:port`) is the human-facing string used in
the settings list, the chat topbar, and the active-connection summary.

## QR connection setup

`python run.py --expose tailscale` on the backend prints a QR with
`{"url":"https://<funnel>.ts.net","token":"<bearer>"}`. The mobile flow:

1. Settings → Connections → QR icon in the dialog header (`Lucide.ScanQrCode`).
2. `QrScanner.scan(context, callback)` calls `GmsBarcodeScanning.getClient(...)`
   — no camera permission required, Google's modal UI.
3. `profileFromQrPayload(raw)` parses the JSON, derives `kind`/`host` via
   `parseHostInput`, sets `authKind = "bearer"`, leaves `name = ""`.
4. The edit dialog opens prefilled with `focusName = true` so the name field
   gets focus immediately for the user to type a custom name.
5. On save, an empty name falls back to `host`. The new profile is upserted and
   set active in one step.

## WebSocket event handling

`ChatSocket` parses server JSON into `ServerEvent` sealed-interface variants;
`ChatViewModel.onEvent` turns each into a `ChatMessage` (or state mutation):

| Event | Role / Effect |
|---|---|
| `assistant_text` | streamed into the current `Role.ASSISTANT` message |
| `thinking` | streamed into the current `Role.THINKING` message |
| `tool_use` | `Role.TOOL` with name + input preview; shows a running spinner until its result arrives |
| `tool_result` | folded into the matching `Role.TOOL` block (input monospace + result as a code block), not a separate message |
| **`file_change`** | `Role.FILE_CHANGE` block — path header + `List<DiffLine>` painted line-by-line in `FileChangeBlock`. The backend already classifies each line as `header`/`hunk`/`add`/`del`/`ctx`, so mobile only picks colors and the `+`/`-` prefix. |
| `interaction_request` | `Role.INTERACTION` with buttons (permission) or a question form; on answer the WS receives `interaction_response` and the same message flips to resolved state |
| `todos` | updates top-bar todo list |
| `task` | updates the task indicator (donut pie + dropdown of TaskRows) |
| `command` | local-command output rendered as markdown |
| `result` | stores the new `sessionId` |
| `ask_text` / `ask_done` | streamed into the quick-chat panel, separate from the main thread |
| `usage` | ephemeral markdown message (plan token usage) — shown live, never resumed |
| `compact` / `compact_summary` | compaction block and its summary, filled in live |
| `done` / `interrupted` / `error` | UI transitions |
| `history_chunk` | older messages from the WS backfill — prepended to `state.messages`. Chunks for a non-active `sessionId` are dropped. Updates `oldestLoadedIndex = chunk.startIndex` and `transcriptExhausted = !chunk.hasMore`. |

## Reconnect & replay (connection-independent turns)

The backend turn runs independently of the socket and buffers its events
(sequence-numbered). The app mirrors that so a dropped connection resumes
without losing work:

- **`ChatSocket` holds the resume tokens** `channel` (server's live-session handle
  from `ready`) and `lastSeq` (highest event `seq` rendered). `sendStart` auto-injects
  both, so the existing auto-reconnect (`onDrop` backoff → `open` → `Open` →
  `startSession`) re-attaches and the server replays only what was missed.
- **`ready` carries `channel` + `running`.** A changed `channel` means the server
  made a fresh session (old one reaped / it restarted) → `ChatSocket` resets `lastSeq`
  so the restarted-at-1 replay isn't dropped. `running` drives `streaming` so the
  spinner is correct after a mid-turn re-attach.
- **Every turn event carries `seq`;** `ChatSocket.parse` drops `seq <= lastSeq`
  duplicates — **except `interaction_request`**, which the server re-emits with an old
  seq when a permission was pending. `InteractionRequest` is also deduped by
  `requestId` in the VM so a re-emit never doubles the dialog.
- **`resetResume()`** clears `channel`/`lastSeq` — called on `newSession`/`openSession`
  (and `close`) so a different conversation never resumes a stale channel. NOT called
  on a plain reconnect.
- **`interrupted` dismisses unresolved interaction blocks** (a stop makes a pending
  permission moot).
- In-memory only: `channel`/`lastSeq` are not persisted, so resume works across
  network drops, not across app process death (which falls back to `resume=<session_id>`
  + on-disk history, as before).

## Transcript window (cursor-based sliding pagination)

Long sessions never live in memory in full. The active window is bounded and
filled on demand:

- **Initial open**: `SessionsApi.sessionMessages(limit=100)` over HTTP returns
  the latest 100 messages plus `start_index` and `has_more`. `ChatViewModel`
  seeds `oldestLoadedIndex = start_index` and `transcriptExhausted = !has_more`.
- **Scroll up to backfill**: `ChatScreen` watches `listState.firstVisibleItemIndex`;
  while it sits under 10, `vm.loadMoreHistory()` fires. The VM guards with
  `transcriptLoading` and `transcriptExhausted`, then sends
  `load_history before_index=<oldestLoadedIndex>` over the WS. `onHistoryChunk`
  prepends and advances the cursor. `LazyColumn` preserves visual position
  because keys (`message.id`) stay stable.
- **Tail cap (`MESSAGE_TAIL_CAP = 500`)**: `applyTailCap` runs after every
  append/addMessage. While `followBottom` is true and the list exceeds the cap,
  the oldest items are dropped. The new top's `sourceIndex` becomes the cursor,
  and `transcriptExhausted = false` is reset so scrolling up can pull them again.
- **Initial reset (`MESSAGE_INITIAL_CAP = 100`)**: `sendPrompt` calls
  `resetToInitialWindow` so a brand-new prompt at the bottom collapses the
  window back to 100. While scrolled up (`followBottom = false`) neither cap
  drops anything — yanking context would be jarring.
- **Cancellation**: implicit. Chunks whose `session_id` doesn't match the
  active one are dropped on arrival.

## Chat attachments

- The composer's paperclip (bottom-aligned to the last text line — height
  measured from `onTextLayout`, no hardcoded offsets) opens the system picker;
  selections appear as `AttachmentChip`s in a horizontal row above the toolbar.
- On send, `ChatViewModel` uploads each one sequentially to `shared/uploads/`
  via `SharedApi.upload` (streamed PUT with progress; the server dedupes names
  with ` (n)` and returns the final relpath). Cancel restores the pending
  input. The prompt then goes over the WS with `attachments: [relpaths]`.
- The **backend** composes the native message (images as base64 vision blocks
  + `@`-mentions) — the app never builds prompt text for attachments.
- Rendering is bidirectional: user bubbles show chips for attachments (parsed
  from `@`-mention/`[Image #N]` conventions for live turns, and from
  transcript metadata for history); pasted/attached images from PC sessions
  load through the transcript-images endpoint with auth headers.

## Files (FileExplorerScreen)

File manager over `backend/shared/`:

- **Upload**: FAB (scroll-to-bottom style) → system picker → confirm dialog →
  per-file progress ring (custom ring icon, states uploading/done) in a
  dropdown like the tasks footer, with a real cancel `X` per file.
- **New folder / rename**: dialogs with duplicate validation, autofocus, and
  extension preserved on rename (`RenameDialog` with `errorOf`/`suffix`).
- **Sort** ("More" menu → Ordenar): a second `DropdownMenu` with the four
  `SortField`s (name/date/type/size); the active field shows an up/down arrow,
  re-tapping it flips the direction, tapping another switches field (asc). The
  arrow slot is reserved on every item so the menu width doesn't jump.
  Persisted in `Settings` (`fileSortField`/`fileSortAscending`, default
  date-descending); folders always lead. `ordered` (the sorted list) is what
  the LazyColumn and drag-select index against.
- **Multi-select** (Samsung style): long-press enters selection; drag after
  long-press marks ranges (with haptics; `suppressClick` kills the ghost click
  on release); top bar gets select-all dot + count + close; a bottom toolbar
  (animated in only after the gesture ends — `marking` state) offers
  Move / Copy / Share / Delete plus a "More" `AbovePopupMenu` with
  View / Rename / Save / Save as / Copy path (absolute PC paths via
  `SharedApi.absolutePaths`, one per line).
- **Move/copy**: the selection persists while you navigate to the destination;
  sticky bottom buttons confirm or cancel. Name collisions dedupe with ` (n)`.
- **Rows**: icon, name (ellipsized), date on the left, size or item count
  right-aligned in bold.

## FilePreviewScreen

`previewKindOf(filename)` (MimeTypeMap + fallbacks) picks the renderer:

- **Image** (incl. SVG via Coil's SVG decoder): zoomable/pannable.
- **Html**: WebView with JS/DOM/zoom; `shouldInterceptRequest` injects
  `Backend.authHeaders` for same-server subresources (css/js/img).
- **Markdown**: `MarkdownText`. **Text**: monospace + selection.
- Toolbar menu: Save / Save as / Share / **Delete** — Delete only appears when
  an `onDelete` callback was provided; it confirms, runs the callback, and
  closes the preview.

Previews open through `MainActivity`'s overlay: every screen calls
`onOpenPreview(url, filename, onDelete?)` → `PreviewRequest`. Files passes a
delete+reload callback, chat shared-links derive one via
`SharedApi.relativeFromUrl`, memories delete via `ClaudeApi.deleteMemory`,
skills pass `null`.

## Monitor (monitor/MonitorScreen.kt)

Sidebar item (Activity icon, between Claude and Terminal). Two pages in a
`HorizontalPager` switched by an outlined `SegmentedButton` row (theme colors:
active = primary 0.18 container + primary text, `outlineVariant` borders, no
check icon, slim `contentPadding` — never force a fixed height, it clips the
label):

- **Resources** (most-dynamic first): CPU and GPU side by side (GPU only when
  the server reports one), Memory and VRAM — each with a `Sparkline` (Canvas;
  right-anchored so the line grows leftwards, 90 samples ≈ 3min, 2dp vertical
  inset so 0%/100% stay visible); then Storage bars and an **Information**
  panel (OS brand icon via `terminal/OsIcons` + `os_id`, hostname `•` uptime,
  CPU/GPU names, RAM/VRAM totals, arch), both in rounded group panels (16dp
  inner padding). GPU graph subtitle = name (sans "NVIDIA GeForce ") `•` temp.
- **Logs**: full-page panel (surfaceContainerHigh, monospace rows, ERROR red /
  WARNING yellow) with the thin `verticalScrollIndicator`. Auto-scroll: a
  `snapshotFlow { scroll.maxValue }` + `collectLatest { animateScrollTo }`
  reacts after layout (no delays/guesses); `followLogs` is sampled right
  before each append so user scroll-up pauses following; on page entry a
  `first { maxValue > 0 }` lands at the tail instantly.
- Data arrives over a dedicated **WebSocket** (`SystemApi.stream()` →
  `Backend.systemWsUrl`, OkHttp + `callbackFlow`): the server pushes a
  `system` snapshot every 2s and `logs` entries as they land. The
  `LaunchedEffect(state.activeEnvironmentId)` collects the stream and
  reconnects with a 3s backoff; changing the environment resets everything
  and reconnects to the new server.
- Top bar follows the standard connection pattern: subtitle = active
  environment name, red `StatusDot` + "Servidor no disponible" while down.
  Actions: **restart server** (RotateCw + ConfirmDialog →
  `SystemApi.restart()`; the stream drops and reconnects by itself) and the
  environment selector (Files pattern).

## Claude manager (claude/)

`ClaudeScreen` is the hub (sidebar item with the Claude logo):

- **CLI group**: version row (red alert icon when `CliOutdated`) + official
  Claude Code changelog sheet (`ClaudeChangelogSheet`, also reused by
  Settings), plus inline source controls (system/bundled/custom path +
  Save/Update with progress) — no dialogs.
- **User prompt**: multiline editor for the backend's `USER.md` (clear button
  in the dialog header just empties the field; Save/Cancel confirm).
- **Usage group** (below CLI): plan tier ("Max (20x)") trailing the label and
  a `MetricBar` per limit window from `ClaudeApi.usage()` — session / all
  models / per-model weekly — with "Se restablece en Xh Ym" (or weekday+time
  when >24h) subtitles.
- **Extensions group**: rows with chevrons → `ClaudeDetailScreen(kind)`.

`ClaudeDetailScreen` (one screen, `enum ClaudeKind`):

- **Plugins**: rows `marketplace • version • scope`; dialog with compact
  Switch (enable/disable) in the title, description + `Version x` in an
  `OutlinedPanel`, and Update/Uninstall `ActionButton`s with inline progress.
  Top-bar `CirclePlus` opens the **catalog sheet** (`AppBottomSheet` with
  `dismissible=false`): search field with clear `X`, marketplace picker
  (Store icon dropdown), install confirmation dialog with description+version.
- **Marketplaces**: Update/Remove action buttons per entry; add by repo.
- **Mcp**: list (`type • detail`); add dialog (name + stdio/http/sse +
  command/URL); remove.
- **Skills**: tap opens the SKILL.md in FilePreview.
- **Memories**: project selector (folder names from the server's `name` field,
  same as the sidebar selector; the top-bar subtitle shows the full path);
  global + project memories; tap opens the file in FilePreview with delete
  wired.

After any action the screen reloads and re-resolves the open dialog's plugin
so toggles/versions reflect immediately.

## Version compatibility & updates

- `ChatViewModel.evaluateCompat` compares `versionName` vs `supported_app`,
  `BuildConfig.SUPPORTED_SERVER` vs server `version`, and `cli_version` vs
  `supported_cli` (`data/AppCompat.kt` parses the `>=x.y.z` ranges) →
  `CompatStatus` (AppOutdated / ServerOutdated / CliOutdated) rendered as
  `NoticeCard`s over the composer; plus an update-available notice from
  GitHub.
- Notice actions navigate to Settings with a **highlight** target (`"about"`
  or `"cli"`): the list auto-scrolls and the row flashes twice (0.1 alpha,
  220ms each).
- `GitHubApi` disk cache: app changelog cached per app version, owner profile
  cached forever, Claude Code changelog (raw `CHANGELOG.md` from the official
  repo, 30 sections) cached per CLI version, `latestRelease` fetched on app
  open only + manual "Check for updates" button in Settings. Unauthenticated
  GitHub API allows 60 req/h — the cache keeps usage near zero.

## Markdown rendering (`ui/MarkdownText.kt`)

- CommonMark + GFM (tables, strikethrough, task lists, footnotes, autolink, ins).
- `val root = remember(markdown) { parser.parse(markdown) }` — sync on the
  composition thread. The transcript window cap (100/500) keeps the number of
  composed `MarkdownText` instances small enough that off-main parsing isn't
  worth the recomposition flicker it would introduce.
- Selection is one scope per chat: a single `SelectionContainer` wraps the whole
  message list, so selecting in any message and tapping elsewhere clears it
  consistently. `MarkdownText(selectable = false)` opts out of its own container
  when it's already inside the list's. The new Compose text context menu is on
  (`ComposeFoundationFlags.isNewContextMenuEnabled`), adding Translate / Search /
  Share via the system's PROCESS_TEXT actions.
- **Unified link handling** — `LocalUriHandler` is overridden inside every
  `MarkdownText`: `/api/shared/...` links go to the `onSharedLink` callback
  (chat opens `SharedLinkActionsDialog`: View / Save / Save as / Share);
  **every other link shows the external-link ConfirmDialog before opening the
  browser** — built in, so every markdown surface (chat, previews, changelogs)
  behaves identically with no per-screen wiring.
- Inline code uses `addStringAnnotation(INLINE_CODE_TAG, ...)` instead of
  `SpanStyle.background`; `MdText` then reads the `TextLayoutResult` in
  `Modifier.drawBehind` and paints rounded boxes per line. This keeps Compose's
  selection highlight visible inside inline code.
- Fenced code blocks share `surfaceContainerHigh` with inline code. The header
  shows the language + a copy button that briefly switches to `Lucide.Check`
  tinted with the primary color for ~1s.

## Code edits as diffs

The backend converts `Edit/Write/MultiEdit/NotebookEdit` tools into
`file_change` events carrying `diff_lines: [{kind, text}]` (already classified
backend-side — mobile doesn't re-detect or split). The app renders these as
`FileChangeBlock` — a collapsible header with the file path + `Lucide.FilePen`
icon; the expanded body paints each `DiffLine` using `Palette.green/red/blue/
gray` and their `*Bg` containers (light/dark adapted), plus a `+`/`-` prefix on
`ADD`/`DEL`. The same shape is re-emitted from the resume endpoint so live and
resumed sessions render identically.

## Rewind

Opened from the chat top bar: `RewindSheet` lists checkpoints (one per user
prompt); picking one opens `RewindDialog` with the message preview and a live
dry-run diff (`+N −M • files changed`, loading state) from
`SessionsApi.rewindPreview`, plus the choice of rewinding code+conversation or
conversation only. Confirm calls the backend and reloads the branched session.

## Theme and colors (`ui/theme/`)

- `CConnectTheme` wraps `MaterialExpressiveTheme` with `ExpressiveShapes`,
  `ExpressiveTypography` (bolder weights), `MotionScheme.expressive`, and a
  custom `ColorScheme` that flattens surfaces to pure black/white while keeping
  the user-chosen accent as `primary`/`secondary`/`tertiary`.
- `Palette` is the semantic-but-named-by-color set:
  `red/green/blue/yellow/orange/cyan/purple/gray` plus `redBg/greenBg/blueBg`
  for translucent backgrounds. Two snapshots — `LightPalette` and `DarkPalette`
  — selected via `LocalPalette`. Use `palette.green` (etc.) inside any
  composable.
- Consumers: `diffStyleFor` (diff colors), `permissionStyle` (chip per
  permission mode), the SSH connected dot, anywhere else that needs a semantic
  hue. Brand colors (FA distro tints, accent presets) stay outside the palette
  because they don't have a light/dark counterpart.

## Visual conventions

- `Collapsible(label, text, icon?, running?)` — used for `THINKING` (Lightbulb
  icon) and `SUMMARY`. Layout: `[icon] [label] [flex] [spinner?] [chevron]`; the
  spinner shows while the block is still streaming.
- `ToolBlock` — header (`SquareTerminal` icon + tool name + input preview) toggles
  expand; a running spinner shows until the result lands. Expanded shows the input
  monospace and the result folded in as a code block — no separate result block.
- `InteractionBlock`:
  - Header icon: `Lucide.CircleQuestionMark` for `kind="question"`,
    `Lucide.Shield` for permission prompts.
  - Resolved state shows `CustomIcons.PlayFilled` (filled right-triangle, same
    geometry as `Lucide.Play` but filled instead of stroked) + chosen label or
    free-text answer.
- ` • ` (U+2022) is THE separator for joined facts in a line/subtitle
  (`marketplace • version • scope`, `name • address`, `+N −M • files`).
- Selection dialogs use `DialogSelectItem` — a 20dp outlined circle with a
  10dp primary inner dot when selected.
- Trailing chevron rows (`DetailLink`, SSH hosts) use the standard 24dp icon.
- Keyboard: screens with inputs follow the ChatScreen pattern —
  `padding(padding).consumeWindowInsets(padding)` + `imePadding()`, and
  `WindowInsets.isImeVisible` → `clearFocus()` when the IME closes.
- All user-facing delete actions say **Eliminar** (never "Borrar") in Spanish.

## Scrolling

- `ChatScreen` tracks `followBottom` — toggled off on user drag, back on when
  the scroll settles at the bottom. The main `LaunchedEffect` auto-scrolls only
  when `followBottom` is true.
- A separate `LaunchedEffect` on the last message's id force-scrolls (and
  re-asserts `followBottom = true`) when the last message is an unresolved
  interaction — so the user always sees the prompt they have to act on.

## Quick chat

A parallel mini-conversation that never touches the main turn. Opened from a
sticky button in the toolbar (a dot marks an ongoing one). State lives in
`ChatUiState.sideChat`, bound to the current `sessionId` so switching
conversations drops it. `SidePanel` is a bottom-anchored overlay over the chat,
drag-resizable between a peek height and full (where it docks flush under the
app bar), reusing `ChatMessageItem` and the composer. Answers stream over the
WS `ask` / `ask_text` / `ask_done` events and nothing is written to the session.

## SSH client

Standalone feature, independent of the Claude Code bridge. Entry point is
`TerminalScreen`, reachable from Chat top bar and from Settings → SSH hosts
(returning from there restores the previous screen via the `terminalFromSettings`
flag in `MainActivity`).

- **Transport**: `sshj` over password auth, no `known_hosts` (MVP, uses
  `PromiscuousVerifier`). Renderer is `connectbot/termlib` (libvterm).
- **Connection stability**: `KeepAliveProvider.KEEP_ALIVE` (15s interval) plus
  a `WIFI_MODE_FULL_HIGH_PERF` `WifiLock` held while the session is open keep
  the connection from dropping when the screen suspends. The lock releases in
  `close()`. (Plain SSH can't recover a shell after a real network change —
  this only keeps a live connection alive, it doesn't reconnect state.)
- **Crypto**: Android ships a stripped BouncyCastle; `MainActivity.onCreate`
  swaps it for the full `bcprov-jdk18on` so modern OpenSSH defaults
  (curve25519, ed25519, chacha20-poly1305) work.
- **At-rest storage**: `SshStore` uses `EncryptedSharedPreferences` with a
  Keystore-backed master key — saved passwords are for third-party systems we
  don't control.
- **OS detection**: after auth, `SshConnection.probeOs` runs
  `sh -c 'uname -s; cat /etc/os-release /etc/lsb-release /etc/system-release'`
  on a separate exec channel (invisible to the user's shell). The parsed `ID`
  (or `DARWIN`/`MINGW`/empty → `windows`) is persisted on the profile and
  drives `iconForOs` (Font Awesome Brands) + `colorForOs` (distro brand color).
- **Resize debounce**: termlib fires `onResize` many times during IME
  animations and each `SIGWINCH` makes the remote TUI repaint a partial frame
  into scrollback. `SshConnection.resize` coalesces them with a 50ms delay.

## Build / Signing

- Debug: `./gradlew :app:installDebug`. Release: `./gradlew :app:installRelease`
  / `:app:assembleRelease`. Signing config reads `mobile/key.properties`
  (gitignored) which points at `mobile/keystore.jks`. Output filename is
  `CConnect-<version>-release.apk`.
- `versionCode`/`versionName` and the `SUPPORTED_SERVER` BuildConfig field live
  in `app/build.gradle.kts` — keep them in step with the backend's
  `[tool.cconnect]` table.
- Release minifies via R8. `proguard-rules.pro` keeps `net.schmizz.sshj.**`,
  `com.hierynomus.**`, `org.bouncycastle.**`, `org.connectbot.**` — sshj
  resolves cipher/MAC/KEX factories by FQCN and BouncyCastle providers are
  loaded by name, so stripping them crashes on connect.

## Conventions

1. **Backend is the source of truth.** Mirror its event shapes verbatim — when
   you add a field on one side, add it on the other.
2. **Comments are WHY-only.** No noise, no comments restating what the code does.
3. **No inline fully-qualified names.** Always import — `Icon(Lucide.X, ...)`,
   never `androidx.compose.material3.Icon(...)` inline.
4. **Reusable shared components** (especially in `ui/`) shouldn't be deleted
   even if temporarily unused — they keep porting across screens cheap.
5. **Español neutro** for any user-facing Spanish strings (no regionalisms).
   Material accent color names stay in English (`Accents.kt`).
