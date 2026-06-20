- Added a tab system to keep several chats open at once, each with its own session, project, model and environment: a tab bar on desktop and web, a tab switcher on mobile, "open in new tab" from any chat or session, keyboard shortcuts (Ctrl+T, Ctrl+W, Ctrl+Tab) to move between and manage them, and the tabs you had open reopen where you left them when you restart the app — loading only when you switch to them
- Added a full-screen mode on the desktop app, toggled with F11, that restores the previous window state when you leave it
- Added an agent view: when Claude launches a subagent its tool calls are grouped into their own collapsible block with a live progress indicator, and that list is rebuilt when you resume the chat
- Added a formatted plan view for plan mode — a collapsible, outlined block with a one-line title preview when collapsed and Allow / Deny / Do differently actions — shown the same way live and when resuming, keeping the decision you actually made
- Added an "Interrupted" marker so a turn you stop mid-response is clearly flagged
- Fixed the chat list not loading on startup when the backend came up late, and model names in the toolbar showing as raw ids until the next reconnect

> [!NOTE]
> The web version is available at https://cconnect.pages.dev/