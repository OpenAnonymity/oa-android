# How oa-android Works

Last updated: 2026-03-14

This document is the detailed handoff for how `oa-android` is put together, where it is
intentionally thin, where it is no longer thin, and what is currently worse than running
`oa-chat` by itself in a browser.

Read this before making changes that touch the Android shell, the `oa-chat` submodule
boundary, or background execution.

## Why This Repo Exists

`oa-chat` is the real product. `oa-android` exists because Android still needs a native
wrapper for a few things the web app cannot reliably do on its own:

- package the app as an installable Android app
- preserve the production OA origin locally inside WebView
- handle Android App Links and launcher entry points
- fill browser gaps such as file picking, downloads, and ticket export save semantics
- enable WebView passkeys / WebAuthn
- integrate with Android window insets, system bars, and IME behavior
- keep long-running model streams alive when the user leaves the app

If a feature does not need one of those platform-specific behaviors, it should usually stay
in `oa-chat`.

## Source Of Truth And Repo Boundary

The intended boundary is:

- `oa-chat/` is the canonical web app and should remain the source of truth for UI,
  sessions, messages, streaming presentation, tickets, shares, import/export, and account
  logic.
- `oa-android/app/` is the Android shell. It should own packaging, the WebView host, App
  Links, platform integrations, and the minimum native runtime needed to make `oa-chat`
  work well on Android.

Important nuance:

- In git, `oa-android/oa-chat/` is supposed to be a read-only submodule.
- In local workspace development, the build may fall back to sibling `../oa-chat` when the
  submodule is not initialized yet.
- That fallback is a packaging convenience only. It is not a license to casually fork web
  logic into the Android repo.

## Thin Wrapper Rule

The default rule is:

1. keep product behavior in `oa-chat`
2. keep Android native additions narrow
3. only move logic native when WebView cannot satisfy a required Android behavior

Examples of good native ownership:

- App Links
- `showSaveFilePicker` polyfill backed by SAF
- `<input type="file">` support
- download delegation
- system-bar / IME inset handling
- passkey enablement
- background model transport

Examples of bad native ownership:

- rendering chat messages differently from `oa-chat`
- reimplementing session/message state in Kotlin without a strong Android-specific need
- duplicating model parsing or UI formatting when the existing JS path can still own it

## Repo Shape

Main directories:

- `oa-chat/`: embedded web app checkout or submodule
- `app/`: Android application module
- `buildSrc/`: Gradle-side constants for generated asset paths
- `docs/`: repo-local handoff and architecture notes

Important Android source areas:

- `app/src/main/java/ai/openanonymity/android/MainActivity.kt`
- `app/src/main/java/ai/openanonymity/android/web/`
- `app/src/main/java/ai/openanonymity/android/navigation/`
- `app/src/main/java/ai/openanonymity/android/files/`
- `app/src/main/java/ai/openanonymity/android/bridge/`
- `app/src/main/java/ai/openanonymity/android/background/`

## Build And Packaging Flow

`oa-android` does not check built web assets into git. The packaging flow is:

1. `prepareOaChatDist` resolves the web app source directory.
   - Prefer `oa-android/oa-chat/` when initialized.
   - Fall back to sibling `../oa-chat` in a shared workspace.
2. Run `npm install` in that web repo.
3. Run `npm run build` in that web repo.
4. Copy `dist/` into `build/generated/oaChatAssets/main/assets/oa-chat-dist`.
5. Let the Android app bundle those generated assets into the APK.

Current implementation:

- root task: `build.gradle.kts`
- path contract: `buildSrc/src/main/kotlin/ai/openanonymity/android/build/OaChatBuildLayout.kt`
- app asset wiring: `app/build.gradle.kts`

Implications:

- never commit generated `dist/`
- changes to `oa-chat` often need a rebuild before Android behavior changes show up
- local Android work can be blocked by broken web builds

## Runtime Boot Flow

The app start path is:

1. `MainActivity` creates the shell UI and the `WebView`.
2. `configureWindowInsets()` opts into edge-to-edge and applies system-bar, cutout, and
   IME padding to the root container.
3. `OaWebViewConfigurator` hardens WebView settings.
4. `OaWebViewAssetLoaderFactory` installs a `WebViewAssetLoader` on
   `https://chat.openanonymity.ai/`.
5. `OaUrlRouter` decides which initial URL to load from the launcher or App Link intent.
6. `OaWebViewClient` keeps OA-origin traffic inside the app and sends external URLs out to
   Android.
7. Native bridges attach to the page:
   - save-file picker bridge
   - theme surface sync
   - native inference bridge
8. `oa-chat` boots as a normal web app, but now inside a local WebView-backed origin.

## The Preserved Origin Contract

One of the most important design choices is that the bundled app still runs on:

- `https://chat.openanonymity.ai/`

That is done locally via `WebViewAssetLoader`, not by hitting the network for the app
bundle itself.

Why this matters:

- `oa-chat` keeps root-path routing
- OA share/session/ticket URLs stay unchanged
- same-site cookie and storage assumptions stay aligned with web
- App Links can target the same URLs the web product already uses
- WebAuthn/passkeys can stay on the expected origin

If this origin contract changes, a large amount of subtle web behavior will drift.

## URL Routing And Navigation

Routing rules are intentionally simple:

- launcher open with no deep link -> load the app root
- `https://chat.openanonymity.ai/...` -> keep inside the WebView unchanged
- non-OA HTTP(S) links -> open externally

Main code:

- router: `navigation/OaUrlRouter.kt`
- external policy: `navigation/ExternalNavigationPolicy.kt`
- WebView interception: `web/OaWebViewClient.kt`

Important detail:

- `MainActivity` uses `singleTask`
- launcher re-entry now preserves the current page if the new intent does not carry a deep
  link
- this is required so Home -> reopen does not accidentally wipe live page state

## Native Features The Wrapper Adds

### File input

`oa-chat` can use normal `<input type="file">`, but Android WebView needs native chooser
plumbing.

- Android side: `files/OaFileChooserController.kt`
- WebView hook: `web/OaWebChromeClient.kt`
- backend: `ActivityResultContracts.OpenMultipleDocuments`

### Downloads

Browser-triggered downloads are delegated to Android:

- Android side: `files/OaDownloadHandler.kt`
- backend: `DownloadManager`

### Ticket export save semantics

Ticket export behaves like cash. Cancel must keep tickets. Successful save is the point
where tickets may be cleared.

That is why the shell polyfills `showSaveFilePicker`:

- bridge: `bridge/SaveFilePickerBridge.kt`
- injected polyfill: `bridge/SaveFilePickerPolyfill.kt`
- protocol helpers: `bridge/SaveFilePickerProtocol.kt`

The native side only reports success after the SAF write completes.

### Passkeys

The wrapper enables WebView WebAuthn support so `oa-chat` can keep the same product-side
account and passkey flows.

- setup: `web/OaWebViewConfigurator.kt`

Important limitation:

- manual prompts are supported
- conditional or silent mediation should not be expected inside WebView

### Insets, keyboard, and surface color

The shell owns what a browser page normally would not:

- status bar overlap avoidance
- navigation bar / gesture area padding
- IME padding so the composer stays visible
- keeping the Android inset background color in sync with the live page background

Main code:

- inset policy: `OaWindowInsets.kt`
- root host: `MainActivity.kt`
- page-to-shell color sync: `bridge/ThemeSurfaceBridge.kt`

## Background Streaming: What Exists Now

This is the main place where `oa-android` is no longer a purely thin shell.

### Why the pure WebView model was insufficient

In plain `oa-chat`, the page owns:

- access issuance
- the provider request
- the live SSE stream
- parsing streamed chunks
- writing UI and IndexedDB updates

When the Android app goes to the background, WebView page-owned async work is not reliable
enough for long model runs. Simply keeping the activity alive was not enough.

### Current MVP split

The current implementation keeps as much ownership in `oa-chat` as possible while moving
the minimum necessary work native.

`oa-chat` still owns:

- send/regenerate UX
- request body construction
- pending-state transitions
- SSE parsing into reasoning/content/image/token updates
- message rendering and IndexedDB writes

Android now owns:

- the active OpenRouter HTTP/SSE network call after the request is ready
- cancellation of that native request
- a foreground notification while jobs are active
- buffering raw SSE lines while the app is backgrounded

### How the current path works

1. `oa-chat/chat/api.js` detects the Android environment.
2. `chat/services/androidNativeInferenceTransport.js` sends a start request through the JS
   bridge.
3. `bridge/NativeInferenceBridge.kt` receives the request via `addJavascriptInterface`.
4. `background/NativeInferenceRegistry.kt` starts an in-memory native job.
5. `background/InferenceForegroundService.kt` keeps a foreground notification alive while
   jobs are active.
6. Native uses OkHttp to run the streaming HTTP request and buffers sequenced raw SSE
   lines.
7. The page polls those raw events back into JS.
8. `oa-chat` feeds those lines through its existing SSE parser and updates the normal chat
   UI/state.

This is a deliberate seam: Android owns transport, but `oa-chat` still owns the semantics
of what the stream means.

## What Is New Compared To oa-chat

Relative to the plain web repo, `oa-android` adds:

- a generated-asset build pipeline around `oa-chat/dist`
- a local `https://chat.openanonymity.ai/` WebView hosting model
- App Link handling on the OA production URL shape
- native file-picker, save-picker, and download integrations
- WebView-specific hardening and passkey enablement
- Android-specific inset, IME, and system-bar handling
- a native background-stream transport seam for active model runs
- Android-specific JVM and instrumentation test coverage

## What Is Different From oa-chat

These are architectural differences, not necessarily regressions:

- `oa-chat` can treat the browser as both UI host and network runtime; `oa-android` splits
  those roles between WebView JS and Kotlin.
- Launcher/deep-link behavior matters in Android because `MainActivity` can receive new
  intents without process restart.
- A local asset server now sits in front of the web app bundle.
- The app has native lifecycle and notification behavior that does not exist in web-only
  deployments.
- Some browser APIs are polyfilled or delegated natively rather than provided by Chromium
  directly.

## What Is Worse Than oa-chat Today

This repo is still intentionally thin overall, but it is objectively more fragile than just
shipping `oa-chat` on the web.

### More moving parts

There are now at least three layers to reason about:

- `oa-chat` JS behavior
- WebView/runtime behavior
- Android native wrapper behavior

Many bugs only appear at the boundaries between those layers.

### Harder debugging

A failure can now come from:

- the web app
- WebView quirks
- JS bridge protocol mismatch
- Android lifecycle behavior
- native networking
- foreground-service policy

That is slower to debug than a pure web repro.

### Build complexity

The Android app depends on:

- a healthy `oa-chat` checkout
- Node/npm
- Gradle/AGP
- Android SDK/emulator state

This is more failure-prone than working in `oa-chat` alone.

### Background streaming is still only an MVP

Current limitations:

- the foreground service is still in the main app process, not a dedicated process
- active jobs live in memory only
- process death still loses active streams
- access issuance still starts in WebView JS
- the current native transport is direct HTTPS, not `networkProxy.js` / libcurl parity
- the native transport path is currently OpenRouter-specific

This means `oa-android` background behavior is better than a plain WebView page, but not
yet the strongest possible Android implementation.

### Parity drift risk

Every time Android takes over part of the flow, parity can drift away from `oa-chat`.

The current seam is relatively safe because:

- JS still builds the request
- JS still parses the stream
- JS still owns visible chat state

If native starts owning more product semantics, that risk increases quickly.

## Rule Of Thumb For Future Changes

Ask this before adding Kotlin:

1. Is this an Android platform gap or lifecycle problem?
2. Can `oa-chat` remain the product/UI owner if Android only exposes a narrow service?
3. Can the native side return raw capability or transport events rather than product state?

If the answer to those is yes, the change probably belongs in `oa-android`.

If the change requires Android to understand chat semantics, session structure, or message
formatting, push back and look for a smaller seam first.

## TODOs And Next Steps

High-priority follow-ups:

- move the background inference engine into a dedicated service process so UI-process churn
  is less dangerous
- persist active background jobs and event logs natively instead of keeping them only in
  memory
- move access issuance into the native background path so the "requesting key" phase also
  survives app switching reliably
- implement privacy/path parity with the browser-side proxy/libcurl transport or extract a
  shared protocol so Android does not weaken the intended network path
- add replay/snapshot recovery so the app can rebuild a partially completed stream after
  recreation or process death
- add notification actions for cancel and reopen
- harden multi-job concurrency and cleanup

Medium-priority follow-ups:

- test more aggressively on real devices, not only emulators
- verify battery / doze / lock-screen behavior for long model runs
- audit whether any other browser API assumptions in `oa-chat` still need Android-native
  support
- keep the boundary doc current whenever new native code is added so future agents do not
  casually duplicate product logic in Kotlin

## Testing And Verification

Primary commands:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Manual Android-specific checks should include:

- launcher open, Home, resume, and deep-link entry
- file attachments and native chooser behavior
- ticket export cancel vs successful save semantics
- external links opening outside the app
- passkey prompts
- long-running stream while leaving the app and returning

For UI quality, prefer a modern Pixel-class AVD. Tiny smoke-test emulators are not good
enough to judge layout, keyboard behavior, or system-bar integration.

## Read This With

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/AGENT_LESSONS.md`
- `docs/NATIVE_BACKGROUND_STREAMING_PLAN.md`

If you are touching upstream web behavior too, also read:

- `oa-chat/docs/APP_STATE.md`
