# Native Background Streaming Plan

Last updated: 2026-03-14

Status: a narrower MVP is now implemented.

- `oa-chat` keeps request construction, SSE parsing, and UI state.
- Android owns the active OpenRouter HTTP/SSE call in a foreground-service-backed registry.
- Remaining work from this plan is access-issuance handoff, stronger persistence/replay, and parity with the browser-side proxy/libcurl transport path.

## Goal

Make OA model requests continue when the user leaves the app and later returns, while keeping `oa-chat` as the source of truth for UI, routing, and most product behavior.

## Why The Thin Wrapper Fails

Today, access issuance and model streaming run inside WebView JavaScript. That means:

- ticket redemption / access-key acquisition is page-owned work
- OpenRouter streaming is page-owned work
- reasoning/content chunk parsing is page-owned work
- IndexedDB updates happen in the page

Android can keep the app process alive without guaranteeing that page-owned async work continues. A foreground service only helps if the service itself owns the work.

## Non-Goals

- Rebuild the OA product UI natively
- Move sessions, messages, tickets, shares, import/export, or account UX out of `oa-chat`
- Fork `oa-chat` business logic broadly into Kotlin
- Add silent background retries for arbitrary web tasks

## Recommended Architecture

### Core split

- `oa-chat` stays responsible for:
  - all UI
  - session/message structure
  - pending/streaming states and chat UX
  - imports/exports/shares/tickets/account flows at the product layer
  - persistence in IndexedDB for normal app state
- Android native takes responsibility only for:
  - access issuance needed for a send/regenerate request
  - provider/network transport for that request
  - chunk parsing and progress reporting
  - persistence of active background jobs
  - notification / cancellation / resume plumbing

### Process model

Run the inference engine in a dedicated Android service process, for example `:inference`.

Why:

- it decouples active model work from the UI activity/WebView lifecycle
- it makes activity recreation and WebView renderer churn survivable
- it gives a single place for foreground notification, cancellation, and replay

### Service model

Use a started + bound foreground service.

- `startForegroundService()` / foreground notification when the user presses Send or Regenerate
- bind from `MainActivity` for live progress when the app is visible
- keep the service started while work is active, so it survives activity unbind/rebind
- stop the service only when all active jobs are completed, failed, or cancelled

This should use `dataSync` foreground service semantics for the first implementation. That works on API 28+ and matches the immediate, user-initiated network work model. Android 14+ user-initiated data transfer jobs are worth evaluating later, but they are not the first implementation target because the app supports API 28+ and needs one consistent path.

This is an inference from the Android docs rather than an explicit OA requirement.

## Cross-Repo Contract Change

This redesign cannot stay entirely inside `oa-android`. It needs a small upstream contract in `oa-chat`.

### New `oa-chat` transport seam

Introduce a narrow platform transport interface in `oa-chat`, with:

- default web implementation: current browser-owned flow
- Android native implementation: service-backed flow

Suggested surface:

```ts
interface PlatformInferenceTransport {
  requestAccess(request: AccessRequest): Promise<AccessGrant>;
  startCompletion(request: CompletionRequest): Promise<StreamHandle>;
  cancel(jobId: string): Promise<void>;
  getSnapshot(jobId: string): Promise<JobSnapshot | null>;
  subscribe(listener: (event: StreamEvent) => void): () => void;
}
```

The rest of `oa-chat` should stop calling `ticketClient` and provider stream code directly from the chat controller path. Instead it should call this transport seam.

### Why this matters

If Android has to monkeypatch internal `oa-chat` functions from the outside, parity will drift quickly. A first-class seam in `oa-chat` keeps product logic in the web app and lets Android only swap transport ownership.

## Native Job Contract

Each user send/regenerate becomes a native job with a stable `jobId`.

The request sent from `oa-chat` to Android should include:

- `jobId`
- `sessionId`
- `placeholderMessageId`
- backend id
- model id
- sanitized messages payload ready for provider request
- search / reasoning flags
- file payload descriptors or fully prepared multimodal payload
- access info if already available
- enough metadata to reconstruct the final assistant message and token state

Each event emitted back to `oa-chat` should include:

- `jobId`
- `sequence`
- `type`
- event payload

Suggested event types:

- `phase`
- `accessGranted`
- `streamOpen`
- `reasoningChunk`
- `contentChunk`
- `images`
- `usage`
- `citations`
- `completed`
- `cancelled`
- `failed`

## Persistence Strategy

Do not make Room replace IndexedDB for the app.

Instead:

- keep IndexedDB as `oa-chat`’s normal app database
- add a native job store only for active/recent background inference jobs

Suggested native tables:

- `stream_jobs`
  - `job_id`, `session_id`, `placeholder_message_id`, `status`, `backend_id`, `model_id`, timestamps
- `stream_events`
  - append-only event log with `job_id`, `sequence`, `type`, payload blob
- `stream_snapshots`
  - latest accumulated content, reasoning, usage, citations, final state

### Replay model

When the app returns:

1. `oa-chat` asks Android for active/recent job snapshots
2. Android replays missing events or sends the latest snapshot
3. `oa-chat` writes the resulting state into IndexedDB and updates the UI
4. `oa-chat` acks the highest applied sequence
5. Android compacts old event logs after completion + ack

This keeps the native store temporary and lets `oa-chat` remain the durable chat-history owner.

## Networking Scope

### What native must own

For background reliability, native must own the full request path for an active job:

- access issuance for the send/regenerate if needed
- provider request execution
- stream reading
- cancellation

If any of those steps remain inside WebView JS, the job is still vulnerable to background interruption.

### Privacy / proxy parity risk

`oa-chat` currently routes sensitive inference traffic through its browser-side proxy path (`networkProxy.js` + libcurl.js/mbedTLS). A native redesign must not silently drop to a less private transport just to gain background execution.

Recommended rule:

- if a backend requires proxy/relay semantics for OA privacy claims, Android must implement a native equivalent before enabling background mode for that backend
- do not silently fall back to direct provider connections in background mode

This is the highest-risk part of the redesign.

## Implementation Phases

### Phase 0: Extract the seam in `oa-chat`

- Add the `PlatformInferenceTransport` abstraction
- Make current web path the default implementation
- Refactor chat send/regenerate flows so transport is injected rather than hardcoded
- Add event replay hooks so `oa-chat` can rebuild a streaming message from sequenced transport events

Deliverable:

- `oa-chat` still behaves the same on web/desktop
- Android can override transport without monkeypatching internal controller code

### Phase 1: Android service skeleton

- Add a separate-process `OaInferenceService`
- Expose IPC via Binder/Messenger or AIDL
- Add a service client in the activity process
- Add Room-backed job/event persistence
- Add foreground notification with cancel/open actions

Deliverable:

- fake jobs can survive activity recreation and app switching
- UI process can reconnect and replay stored progress

### Phase 2: Native direct transport parity for one backend

- Implement one backend end-to-end in native code:
  - access issuance path required for send/regenerate
  - provider streaming request
  - chunk parsing
  - cancellation
  - final usage/model metadata
- Feed events back into `oa-chat` through the transport seam

Deliverable:

- one real model flow survives app switching and resumes correctly in the WebView UI

### Phase 3: Native proxy / privacy parity

- Implement the OA relay/proxy path natively, or extract/spec the protocol so native can interoperate without copying brittle browser internals
- Add parity tests against the current web path for headers, TLS expectations, failure handling, and cancellation semantics

Deliverable:

- background execution without weakening the intended privacy/network path

### Phase 4: Hardening

- handle process death and service restart
- support multiple concurrent jobs
- compact acknowledged event logs
- add notification actions for cancel/reopen
- add low-memory / airplane-mode / lock-screen / screen-off tests

## MVP Recommendation

Because background running is the only goal right now, the MVP should be:

- keep all UI and normal chat persistence in WebView
- move only send/regenerate transport into native
- support one backend first
- support final-result correctness and partial-result replay on return
- avoid broad native copies of unrelated OA product logic

## Tests

### Unit

- request/event contract encoding
- Room job/event persistence
- replay and ack compaction
- cancellation semantics
- notification action routing

### Instrumentation

- send a job, press Home, wait, reopen, confirm stream continued
- same for:
  - waiting-for-access stage
  - reasoning stage
  - content streaming stage
- activity recreation during active job
- service-process survives UI-process death
- notification cancel stops the job and updates the WebView on return

### Manual

- long reasoning model run while switching among apps
- lock screen during run
- network loss / restore
- multiple concurrent chats
- ticket exhaustion / access refresh during background run

## Open Questions

- Should Android use a dedicated native SQLite/Room schema only for active jobs, or also store final assistant messages for crash recovery?
- Should Android use Binder callbacks, `Messenger`, or a local socket/channel for event streaming?
- Is it better to implement native proxy parity directly, or to extract a relay protocol spec first and implement both web and Android against that spec?
- Should Android 14+ use user-initiated data transfer jobs for some long-running transfers later, or keep one foreground-service path across API 28+?

## Recommendation

Proceed with a two-repo change:

1. upstream `oa-chat` transport seam extraction
2. `oa-android` separate-process foreground inference service that owns access + provider streaming for active jobs

That is the narrowest redesign that can actually satisfy the background-running requirement while keeping `oa-chat` as the product/UI owner.

## Sources

- [Services overview](https://developer.android.com/develop/background-work/services)
- [Bound services overview](https://developer.android.com/develop/background-work/services/bound-services)
- [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Data transfer background task options](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options)
- [User-initiated data transfer](https://developer.android.com/about/versions/14/changes/user-initiated-data-transfers)
- [Manage WebView objects](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview)
- [WebView API reference](https://developer.android.com/reference/android/webkit/WebView)
