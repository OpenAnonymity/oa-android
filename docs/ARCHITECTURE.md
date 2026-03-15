# oa-android Architecture

For the fuller handoff, including what is new, different, and worse than plain `oa-chat`,
read `docs/HOW_IT_WORKS.md` first.

## Role

`oa-android` packages `oa-chat` inside a hardened Android WebView while preserving the web app's production origin and routing assumptions.

## Runtime Contract

- Bundled assets are served locally on `https://chat.openanonymity.ai/` via `WebViewAssetLoader`.
- `oa-chat` keeps root-path routing, same-site cookies, storage keys, share URLs, and ticket URLs unchanged.
- App Links for `https://chat.openanonymity.ai/` load the incoming URL into the WebView unchanged.
- External HTTP(S) links leave the app unless they stay on the OA chat origin.

## Native Surface

The shell intentionally stays narrow:

- `showSaveFilePicker` document-start polyfill backed by SAF `CreateDocument`
- Page-world save-picker polyfill installation plus a minimal native JS-interface fallback so the save bridge remains callable from the page context in Android WebView
- `<input type="file">` support backed by SAF `OpenMultipleDocuments`
- `DownloadManager` delegation for browser-triggered downloads
- App Link handling and external-intent delegation
- WebView hardening and WebAuthn enablement for same-origin passkeys
- Activity-level system-bar, cutout, and IME inset handling so the bundled web UI is not obscured on modern Android edge-to-edge layouts
- Theme-surface syncing so the Android inset background and system-bar icon appearance track the live `oa-chat` page background instead of a separate native palette
- Native inference bridge plus a foreground service that own active OpenRouter HTTP/SSE streams after access issuance, buffer raw SSE lines while the app is backgrounded, and let `oa-chat` keep its existing parser and UI state model

## Background Streaming MVP

- Access issuance still starts in `oa-chat`, but once `chat/api.js` starts an Android-native stream job the foreground service owns the live network call and raw SSE buffering.
- `oa-chat` still builds the request body and still parses SSE lines into reasoning/content/images/tokens, which keeps the visible product behavior aligned with web/desktop.
- Launcher re-entry without a deep link intentionally preserves the current WebView page instead of force-loading the root URL again. That matters because a `singleTask` launcher reopen would otherwise wipe the in-flight JS state even if the activity survived.
- The current native transport path is direct HTTPS. It does not yet mirror the browser-side proxy/libcurl relay path used by `networkProxy.js`.
- The current service is still in the main app process and keeps active jobs in memory only. Process death still loses active work.

See `docs/NATIVE_BACKGROUND_STREAMING_PLAN.md` for the recommended redesign.

## Generated Asset Pipeline

`prepareOaChatDist` is the only build step that touches the submodule:

1. run `npm install` in `oa-chat/`
2. run `npm run build` in `oa-chat/`
3. copy `oa-chat/dist/` into `build/generated/oaChatAssets/main/assets/oa-chat-dist`

The generated `dist/` bundle is never checked into git.

For local workspace development, the task can also resolve the sibling repo at `../oa-chat` when the nested `oa-chat/` submodule path has not been initialized yet.

## Storage Model

- WebView DOM storage and IndexedDB stay app-local.
- The shell preserves the `https://chat.openanonymity.ai/` origin so `oa-chat` sees the same host/path structure it expects on the web.
- Browser-to-app migration still uses OA's export/import flows; Chrome storage is not auto-migrated.

## Passkeys

Phase-2 parity is enabled through WebView's native WebAuthn support on the same `chat.openanonymity.ai` origin. Manual passkey prompts are supported; conditional or silent mediation is not expected inside WebView.
