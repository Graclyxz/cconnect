# CLAUDE.md — CConnect Mobile

Android app (Jetpack Compose, Kotlin 2.2.20, Compose BOM 2025.09) that drives
the CConnect backend via REST + WebSocket. Talks to a Claude Code instance
running on a PC, locally over Tailscale or publicly over a Tailscale Funnel.

Package: `com.jahirtrap.cconnect`. Min SDK 26, target SDK 36.

---

## Architecture

```
ChatScreen (UI) ──> ChatViewModel ──> ChatSocket (OkHttp WS) ──> backend /api/chat/ws
                                  └─> SessionsApi / SharedApi / CapabilitiesApi (Http) ──> backend /api/...
```

- Single ViewModel per screen; state via `StateFlow<ChatUiState>`.
- Networking via OkHttp 4 (`data/remote/Http.kt`). The `Backend` object holds
  the active connection (kind, host, port, authKind, auth fields). The active
  connection is mirrored from `Settings.activeConnection` on app start and on
  every switch.
- All requests pass through `Http.applyAuth(builder)` which appends the headers
  built by `Backend.authHeaders` (Bearer, Basic, or custom).

## Project Structure

```
mobile/app/src/main/java/com/jahirtrap/cconnect/
├── MainActivity.kt
├── chat/
│   ├── ChatScreen.kt            # Top-level screen + drawer + scroll logic
│   ├── ChatBlocks.kt            # ChatMessageItem and per-role renderers
│   ├── ChatViewModel.kt         # State + event handlers + transcript window
│   └── PermissionUi.kt          # permissionStyle() → icon + Palette color per mode
├── data/
│   ├── ChatModels.kt            # Role, ChatMessage, InteractionData, ServerEvent
│   ├── SessionModels.kt
│   ├── QrConnectionPayload.kt   # Parse the QR JSON {url, token}
│   ├── ConnectionProfile.kt     # kind, host, port?, authKind, authToken, ...
│   ├── Settings.kt              # SharedPreferences-backed; syncs Backend
│   ├── SshProfile.kt            # id, name, host, port, user, password, os?
│   ├── SshStore.kt              # EncryptedSharedPreferences-backed list of SshProfile
│   └── remote/
│       ├── Backend.kt           # Active connection + computed baseUrl/wsUrl + auth headers
│       ├── Http.kt              # GET/POST/DELETE helpers, applyAuth
│       ├── ChatSocket.kt        # WebSocket client + event parser
│       ├── SessionsApi.kt, SharedApi.kt, CapabilitiesApi.kt, SettingsApi.kt, CliApi.kt
├── settings/
│   ├── SettingsScreen.kt        # All preferences + ConnectionsDialog + ConnectionEditDialog
│   └── QrScanner.kt             # play-services-code-scanner wrapper (no camera permission)
├── files/
│   ├── FileExplorerScreen.kt    # Browse backend/shared/
│   └── FileTransfer.kt          # DownloadManager + OkHttp save-as / share (with auth headers)
├── terminal/
│   ├── TerminalScreen.kt        # SSH host list + edit dialog + termlib-backed session
│   ├── SshConnection.kt         # sshj wrapper: connect, PTY shell, debounced resize, OS probe
│   └── OsIcons.kt               # iconForOs / colorForOs: FA Brands + distro brand colors
└── ui/
    ├── MarkdownText.kt          # CommonMark parser + Compose renderer (selection, inline-code box, copy)
    ├── Dialogs.kt               # CompactDialog, DialogSelectItem, DialogActionItem, SharedLinkActionsDialog
    ├── ScrollIndicator.kt       # Thin custom scrollbars for horizontalScroll
    ├── CustomIcons.kt           # PlayFilled, Stop — filled icons matching Lucide shapes
    ├── SecretTextField.kt       # OutlinedTextField with show/hide toggle for tokens/passwords
    ├── TooltipIconButton.kt     # IconButton wrapped with M3 PlainTooltip + custom anchor provider
    └── theme/
        ├── Theme.kt             # CConnectTheme: MaterialExpressiveTheme + ExpressiveShapes + Palette provider
        ├── Palette.kt           # data class Palette + Light/Dark sets + LocalPalette + palette getter
        ├── Accents.kt           # User-selectable accent presets
        └── SessionColors.kt     # Per-conversation color picker
```

## Connection model

`ConnectionProfile`:

- `kind` ∈ `"http" | "https"`.
- `port: Int?` — nullable. For `https` it's always `null` (implicit 443); the
  edit dialog hides the port field. For `http` it defaults to 8723.
- `authKind` ∈ `"none" | "bearer" | "basic" | "header"`. `Backend.authHeaders`
  flattens the active auth into a `List<Pair<String,String>>` consumed
  uniformly by `Http`, `ChatSocket`, and `FileTransfer`.

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
4. The `ConnectionEditDialog` opens prefilled with `focusName = true` so the
   name field gets focus immediately for the user to type a custom name.
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
| `interaction_request` | `Role.INTERACTION` with buttons; on answer the WS receives `interaction_response` and the same message flips to resolved state |
| `todos` | updates top-bar todo list |
| `task` | updates the task progress UI |
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
- Inline code uses `addStringAnnotation(INLINE_CODE_TAG, ...)` instead of
  `SpanStyle.background`; `MdText` then reads the `TextLayoutResult` in
  `Modifier.drawBehind` and paints rounded boxes per line. This keeps Compose's
  selection highlight visible inside inline code.
- Fenced code blocks share `surfaceContainerHigh` with inline code. The header
  shows the language + a copy button that briefly switches to `Lucide.Check`
  tinted with the primary color for ~1s.
- `/api/shared/...` links are intercepted: `LocalUriHandler` is overridden so a
  click opens `SharedLinkActionsDialog` (Save / Save as / Share) instead of the
  browser. The download/save/share paths all carry the active Bearer header.

## Code edits as diffs

The backend converts `Edit/Write/MultiEdit/NotebookEdit` tools into
`file_change` events carrying `diff_lines: [{kind, text}]` (already classified
backend-side — mobile doesn't re-detect or split). The app renders these as
`FileChangeBlock` — a collapsible header with the file path + `Lucide.FilePen`
icon; the expanded body paints each `DiffLine` using `Palette.green/red/blue/
gray` and their `*Bg` containers (light/dark adapted), plus a `+`/`-` prefix on
`ADD`/`DEL`. The same shape is re-emitted from the resume endpoint so live and
resumed sessions render identically.

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

## Selection icons & visual conventions

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
- Custom-response send icon: `Lucide.SendHorizontal`.
- Connections / language / theme dialogs use `DialogSelectItem` — a 20dp
  outlined circle with a 10dp primary-color inner dot when selected. Connection
  rows add `subtitle = profile.address` and edit/delete IconButtons (36dp) as
  the trailing slot.

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

- Debug: `./gradlew :app:installDebug` (debug keystore, auto).
- Release: `./gradlew :app:assembleRelease`. Signing config reads
  `mobile/key.properties` (gitignored) which points at `mobile/keystore.jks`.
  Output filename is `CConnect-<version>-release.apk`.
- `versionCode` and `versionName` live in `app/build.gradle.kts`.
- Release minifies via R8. `proguard-rules.pro` keeps `net.schmizz.sshj.**`,
  `com.hierynomus.**`, `org.bouncycastle.**`, `org.connectbot.**` — sshj
  resolves cipher/MAC/KEX factories by FQCN and BouncyCastle providers are
  loaded by name, so stripping them crashes on connect.

## Conventions

1. **Backend is the source of truth.** Mirror its event shapes verbatim — when
   you add a field on one side, add it on the other.
2. **Comments are WHY-only.** No noise, no comments restating what the code does.
3. **Reusable shared components** (especially in `ui/`) shouldn't be deleted
   even if temporarily unused — they keep porting across screens cheap.
4. **Español neutro** for any user-facing Spanish strings (no regionalisms).
   Material accent color names stay in English (`Accents.kt`).
