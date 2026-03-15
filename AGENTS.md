# oa-android Agent Guide

`oa-android` is a wrapper repo, not a fork of `oa-chat`.

## Read Order

1. `README.md`
2. `docs/HOW_IT_WORKS.md`
3. `docs/ARCHITECTURE.md`
4. `docs/AGENT_LESSONS.md`
5. Relevant files under `app/`

## Core Rules

- Never edit `oa-chat/` from this repo. It is a read-only submodule.
- For local workspace development, the build can fall back to sibling `../oa-chat` if `oa-android/oa-chat/` is not initialized yet. Treat that only as a packaging convenience, not a repo-boundary change.
- Keep product logic and UX in the web app unless Android must fill a platform gap.
- Preserve the bundled origin as `https://chat.openanonymity.ai/`.
- Prefer small native surfaces: App Links, WebView settings, file chooser, download delegation, save-file bridge, and passkey enablement.
- Background streaming is the main exception to the "thin wrapper" rule. Read `docs/HOW_IT_WORKS.md` and `docs/NATIVE_BACKGROUND_STREAMING_PLAN.md` before expanding it further.
- If a change reveals a reusable Android-specific lesson or gotcha, update `docs/AGENT_LESSONS.md`.
- If the runtime contract changes, update `docs/ARCHITECTURE.md` in the same change.

## Key Paths

- `build.gradle.kts`: root build logic and `prepareOaChatDist`
- `buildSrc/src/main/kotlin/ai/openanonymity/android/build/OaChatBuildLayout.kt`: generated asset path contract
- `app/build.gradle.kts`: Android module config and generated assets wiring
- `app/src/main/java/ai/openanonymity/android/`: app shell, WebView, bridge, and native integration code
- `app/src/test/java/`: JVM unit tests
- `app/src/androidTest/java/`: instrumentation coverage

## Verification Expectations

Run the smallest relevant checks in this repo and record what did not run in your handoff. Prefer:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```
