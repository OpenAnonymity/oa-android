# Agent Lessons

## 2026-03-14

- `oa-chat/` is a read-only submodule in this repo. Android changes belong in the shell, build logic, or docs, not in the embedded web app.
- Preserve `https://chat.openanonymity.ai/` locally with `WebViewAssetLoader`; this is what keeps root-path routing, cookies, App Links, share URLs, and passkey assumptions aligned with the web app.
- Ticket export needs cash-like semantics. The Android shell polyfills `showSaveFilePicker` so cancel keeps tickets and a successful save clears them only after the native write completes.
- For shared-workspace development, `prepareOaChatDist` can fall back to sibling `../oa-chat` if the nested `oa-chat/` submodule path has not been initialized yet. Keep the submodule contract in git and docs even though the local fallback exists.
- The repo now has a checked-in Gradle wrapper. In constrained shells, use a repo-local `GRADLE_USER_HOME` if the default `~/.gradle` path is not writable.
- For manual QA, prefer a modern Pixel-class AVD with host GPU enabled. A tiny fallback AVD such as `320x640` at `160dpi` is enough for smoke instrumentation but is too low-fidelity to judge real UI quality or ergonomics.
- Target SDK 36 means Android 15/16 edge-to-edge behavior is effectively the default. The activity host must apply system-bar, cutout, and IME insets to the WebView container or the OA web UI will render under the status bar and the bottom composer will not stay above the keyboard.
- When testing in the emulator with host keyboard passthrough enabled, set `show_ime_with_hard_keyboard=1` if you still want the on-screen keyboard to appear. Otherwise WebView text input can look broken even though Android is treating the hardware keyboard as active.
- For the Pixel-class manual-test AVD, keep `hw.keyboard=yes` in `~/.android/avd/oaPixel9Api36.avd/config.ini` and cold-boot after changing it. Without that, the Mac keyboard may not type into the emulator even though the app input path itself is fine.
- In WebView instrumentation, `ActivityScenario.launch(Intent.ACTION_VIEW, url)` is not enough for App Link coverage. Add `CATEGORY_BROWSABLE` and launch `MainActivity` explicitly, then test resolver behavior separately with `PackageManager`.
- `evaluateJavascript(...)` runs as an `about:blank` eval script in Chromium, so module imports like `import('/services/ticketStore.js')` fail there. For browser-module test harnesses, inject a real `<script type="module">` element into the page instead.
- The save-picker bridge now installs the polyfill into the page world after load and exposes a `JavascriptInterface` fallback transport in addition to `WebMessageListener`. That avoids script-world mismatches between the injected polyfill and later `evaluateJavascript` callbacks in Android WebView.
- Do not hardcode the Android shell inset background separately from `oa-chat`. The top and bottom inset areas must follow the web app's effective `bg-background` color, including theme changes, or the shell will drift visually. `ThemeSurfaceBridge` now syncs the computed page background into the Android root and system-bar icon appearance.
- Background streaming became workable once the seam moved down to raw transport instead of UI logic. `oa-chat/chat/api.js` still builds the request and parses SSE, but Android now owns the live HTTP/SSE call and buffers raw lines in a foreground service-backed registry.
- Launcher re-entry on a `singleTask` activity is easy to miss. If `onNewIntent()` always forces a reload, tapping the launcher after Home destroys any in-flight page state even when the same `MainActivity` instance survives. Only deep-link intents should force a navigation.
- The current MVP uses direct native HTTPS for the active model stream. Proxy/libcurl parity is still a follow-up; do not document this as full network-path parity with browser `networkProxy.js`.
