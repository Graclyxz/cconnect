- Added a message queue: keep sending messages while Claude is still working and they're delivered in order, each appearing in its place in the conversation as it's picked up, and anything still queued is restored when you reload or resume the chat
- Added a status band in the chat that appears while a turn is retrying or reconnecting after a connection hiccup
- Changed interruptions and errors to stay in the chat: stopping a turn or an API error is now kept and shown again when you resume
- Changed the compaction indicator to an inline progress band that matches the rest of the chat
- Fixed the QR scanner not opening when adding or editing a connection

> [!NOTE]
> The web version is available at https://cconnect.pages.dev/