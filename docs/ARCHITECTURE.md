# oa-android Architecture

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
- `<input type="file">` support backed by SAF `OpenMultipleDocuments`
- `DownloadManager` delegation for browser-triggered downloads
- App Link handling and external-intent delegation
- WebView hardening and WebAuthn enablement for same-origin passkeys

## Generated Asset Pipeline

`prepareOaChatDist` is the only build step that touches the submodule:

1. run `npm install` in `oa-chat/`
2. run `npm run build` in `oa-chat/`
3. copy `oa-chat/dist/` into `build/generated/oaChatAssets/main/assets/oa-chat-dist`

The generated `dist/` bundle is never checked into git.

## Storage Model

- WebView DOM storage and IndexedDB stay app-local.
- The shell preserves the `https://chat.openanonymity.ai/` origin so `oa-chat` sees the same host/path structure it expects on the web.
- Browser-to-app migration still uses OA's export/import flows; Chrome storage is not auto-migrated.

## Passkeys

Phase-2 parity is enabled through WebView's native WebAuthn support on the same `chat.openanonymity.ai` origin. Manual passkey prompts are supported; conditional or silent mediation is not expected inside WebView.
