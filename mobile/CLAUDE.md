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
│   └── ChatViewModel.kt         # State + event handlers + history loading
├── data/
│   ├── ChatModels.kt            # Role, ChatMessage, InteractionData, ServerEvent
│   ├── SessionModels.kt
│   ├── QrConnectionPayload.kt   # Parse the QR JSON {url, token}
│   ├── ConnectionProfile.kt     # kind, host, port?, authKind, authToken, ...
│   ├── Settings.kt              # SharedPreferences-backed; syncs Backend
│   └── remote/
│       ├── Backend.kt           # Active connection + computed baseUrl/wsUrl + auth headers
│       ├── Http.kt              # GET/POST/DELETE helpers, applyAuth
│       ├── ChatSocket.kt        # WebSocket client + event parser
│       ├── SessionsApi.kt, SharedApi.kt, CapabilitiesApi.kt
├── settings/
│   ├── SettingsScreen.kt        # All preferences + ConnectionsDialog + ConnectionEditDialog
│   └── QrScanner.kt             # play-services-code-scanner wrapper (no camera permission)
├── files/
│   ├── FileExplorerScreen.kt    # Browse backend/shared/
│   └── FileTransfer.kt          # DownloadManager + OkHttp save-as / share (with auth headers)
└── ui/
    ├── MarkdownText.kt          # CommonMark parser + Compose renderer (selection, inline-code box, copy)
    ├── Dialogs.kt               # CompactDialog, DialogSelectItem, DialogActionItem, SharedLinkActionsDialog
    ├── ScrollIndicator.kt       # Thin custom scrollbars for horizontalScroll
    ├── CustomIcons.kt           # PlayFilled, Stop — filled icons matching Lucide shapes
    └── theme/                   # Accents + sessionColor + dynamic color
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
| `tool_use` | `Role.TOOL` with name + input preview |
| `tool_result` | `Role.TOOL_RESULT` collapsible |
| **`file_change`** | `Role.FILE_CHANGE` block — path header + diff body rendered via `MarkdownText("```diff\n$diff\n```")` |
| `interaction_request` | `Role.INTERACTION` with buttons; on answer the WS receives `interaction_response` and the same message flips to resolved state |
| `todos` | updates top-bar todo list |
| `task` | updates the task progress UI |
| `result` | stores the new `sessionId` |
| `done` / `interrupted` / `error` | UI transitions |

## Markdown rendering (`ui/MarkdownText.kt`)

- CommonMark + GFM (tables, strikethrough, task lists, footnotes, autolink, ins).
- `SelectionContainer` wraps the column so any block is selectable via long-press.
- Inline code uses `addStringAnnotation(INLINE_CODE_TAG, ...)` instead of
  `SpanStyle.background`; `MdText` then reads the `TextLayoutResult` in
  `Modifier.drawBehind` and paints rounded boxes per line. This keeps Compose's
  selection highlight visible inside inline code.
- Fenced code blocks share `surfaceContainerHigh` with inline code. The header
  shows the language + a copy button that briefly switches to `Lucide.Check`
  tinted with the primary color for ~1s.
- Diff blocks parse `+`/`-`/`@@`/`+++`/`---` lines and color them in
  GitHub-like greens/reds/blues; rendered via the same fenced-code path when
  the language is `diff`/`patch` or the content looks like a diff.
- `/api/shared/...` links are intercepted: `LocalUriHandler` is overridden so a
  click opens `SharedLinkActionsDialog` (Save / Save as / Share) instead of the
  browser. The download/save/share paths all carry the active Bearer header.

## Code edits as diffs

The backend converts `Edit/Write/MultiEdit/NotebookEdit` tools into
`file_change` events. The app renders these as `FileChangeBlock` — a collapsible
header with the file path + `Lucide.FilePen` icon; the expanded body reuses the
diff renderer via `MarkdownText("```diff\n$diff\n```")`. The same shape is
re-emitted from the resume endpoint so live and resumed sessions render
identically.

## Selection icons & visual conventions

- `Collapsible(label, text, icon?)` — used for `THINKING` (Lightbulb icon),
  `TOOL_RESULT`, `SUMMARY`. Layout: `[icon] [label] [flex] [chevron]`.
- `ToolBlock` — `SquareTerminal` icon + tool name + preview (same size text,
  gray) + trailing chevron.
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

## Build / Signing

- Debug: `./gradlew :app:installDebug` (debug keystore, auto).
- Release: `./gradlew :app:assembleRelease`. Signing config reads
  `mobile/key.properties` (gitignored) which points at `mobile/keystore.jks`.
  Output filename is `CConnect-<version>-release.apk`.
- `versionCode` and `versionName` live in `app/build.gradle.kts`.

## Conventions

1. **Backend is the source of truth.** Mirror its event shapes verbatim — when
   you add a field on one side, add it on the other.
2. **Comments are WHY-only.** No noise, no comments restating what the code does.
3. **Reusable shared components** (especially in `ui/`) shouldn't be deleted
   even if temporarily unused — they keep porting across screens cheap.
4. **Español neutro** for any user-facing Spanish strings (no regionalisms).
   Material accent color names stay in English (`Accents.kt`).
