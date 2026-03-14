# oa-android

`oa-android/` is a thin Android shell around the embedded `oa-chat` web app. Product logic stays in the `oa-chat/` submodule; Android owns packaging, app links, WebView hardening, file-handling gaps, downloads, and ticket-export save semantics.

## Read First

1. `AGENTS.md`
2. `docs/ARCHITECTURE.md`
3. `docs/AGENT_LESSONS.md`

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

## Main Commands

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
gradle :app:connectedDebugAndroidTest
```

The root `prepareOaChatDist` task runs `npm install` + `npm run build` inside `oa-chat/` and copies `dist/` into generated Android assets. `:app:preBuild` depends on it, so normal app builds bundle the web app automatically.

## Architectural Contract

- Keep `oa-chat/` untouched from this repo.
- Serve bundled assets on `https://chat.openanonymity.ai/` with `WebViewAssetLoader`.
- Preserve OA web URLs unchanged when routing App Links into the local WebView.
- Keep native bridges narrow and generic.

## Verification

- JVM unit tests cover routing, asset resolution, MIME/path handling, external-link policy, save-picker result protocol, and build-layout wiring.
- Instrumentation tests cover asset loading, storage persistence, file input, App Link routing, and ticket-export save/cancel semantics.
