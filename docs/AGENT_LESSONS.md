# Agent Lessons

## 2026-03-14

- `oa-chat/` is a read-only submodule in this repo. Android changes belong in the shell, build logic, or docs, not in the embedded web app.
- Preserve `https://chat.openanonymity.ai/` locally with `WebViewAssetLoader`; this is what keeps root-path routing, cookies, App Links, share URLs, and passkey assumptions aligned with the web app.
- Ticket export needs cash-like semantics. The Android shell polyfills `showSaveFilePicker` so cancel keeps tickets and a successful save clears them only after the native write completes.
