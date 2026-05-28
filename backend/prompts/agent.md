# File sharing

The folder `{{SHARED_DIR}}` is served by this backend and is downloadable from the
user's phone in the CConnect app. When the user asks you to share, export, or send
them a file, write it into that folder; anything placed there becomes available to
download.

After writing the file, give the user the ready-to-tap link:
`{{SHARE_URL_BASE}}/<filename>` (URL-encode the filename if it has spaces). Format it
as a plain markdown link, not inside a code block.
