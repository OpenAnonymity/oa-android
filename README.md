# oa-android

`oa-android/` is a thin Android shell around the embedded `oa-chat` web app. Product logic stays in the `oa-chat/` submodule; Android owns packaging, app links, WebView hardening, file-handling gaps, downloads, and ticket-export save semantics.

## Read First

1. `AGENTS.md`
2. `docs/HOW_IT_WORKS.md`
3. `docs/ARCHITECTURE.md`
4. `docs/AGENT_LESSONS.md`
5. `docs/NATIVE_BACKGROUND_STREAMING_PLAN.md` when working on background execution

## Repo Shape

- `oa-chat/`: read-only git submodule for the canonical web app
- `app/`: Android application module
- `buildSrc/`: shared Gradle-side constants for the generated asset pipeline
- `docs/`: repo-local implementation and handoff notes

## Build Requirements

- JDK 17
- Android SDK Platform 36
- Android Build Tools / cmdline tools that match AGP 8.13.x
- Node.js and npm available on `PATH`
- Initialized `oa-chat` submodule

## First-Time Setup

```bash
git submodule update --init --recursive
cd oa-chat
npm install
cd ..
```

If the nested `oa-chat/` submodule has not been initialized yet but you are working inside the shared OA workspace, the Gradle build can also fall back to the sibling checkout at `../oa-chat`.

## Main Commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The root `prepareOaChatDist` task runs `npm install` + `npm run build` inside `oa-chat/` and copies `dist/` into generated Android assets. `:app:preBuild` depends on it, so normal app builds bundle the web app automatically.

## Architectural Contract

- Keep `oa-chat/` untouched from this repo.
- Serve bundled assets on `https://chat.openanonymity.ai/` with `WebViewAssetLoader`.
- Preserve OA web URLs unchanged when routing App Links into the local WebView.
- Keep native bridges narrow and generic.

For the detailed explanation of where the wrapper stays thin, where it no longer does, and
what is currently worse than plain `oa-chat`, read `docs/HOW_IT_WORKS.md`.

## Background Streaming MVP

Android now keeps active model streams alive across Home/app switching by moving the
active OpenRouter HTTP/SSE transport into a native foreground service while keeping
request construction, SSE parsing, UI, and normal chat state in `oa-chat`.

Current contract:

- access issuance still happens in `oa-chat` before the native stream starts
- once a stream starts, Android owns the network call and buffers SSE lines until the
  page polls them again
- reopening the app from the launcher no longer force-reloads the WebView if there is
  no deep link, so the in-flight page state survives launcher re-entry
- this MVP currently uses native direct HTTPS for the active model stream rather than
  the browser-side proxy/libcurl path

Remaining redesign work is tracked in `docs/NATIVE_BACKGROUND_STREAMING_PLAN.md`.

## Verification

- JVM unit tests cover routing, asset resolution, MIME/path handling, external-link policy, save-picker result protocol, and build-layout wiring.
- Instrumentation tests cover asset loading, storage persistence, file input, App Link routing, ticket-export save/cancel semantics, and background streaming across Home/launcher resume via a native mock stream.
