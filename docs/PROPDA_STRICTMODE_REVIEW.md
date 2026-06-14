# PROPDA StrictMode Review

Task: AUDIT-FIX-09

## Scope

StrictMode diagnostics were reviewed for debug builds only. The runtime navigation pass requested for topic, long topic, image-heavy topic, Answers, Favorites, Search, and background/foreground transitions could not be executed because no Android device or emulator was attached.

## StrictMode Configuration

- `app/src/main/java/forpdateam/ru/forpda/App.kt`
- `setupStrictMode()` is called during application startup.
- The setup is guarded by `BuildConfig.DEBUG`.
- Thread policy uses `detectAll()` with `penaltyLog()`.
- VM policy uses `detectLeakedClosableObjects()` with `penaltyLog()`.
- No `penaltyDeath()` usage was found in the searched source scope.
- StrictMode should log violations in debug builds and should not crash the app from StrictMode.

## Runtime Logcat Capture

No runtime StrictMode violations were collected in this pass.

Reason: `adb devices` returned no attached device/emulator, so the app could not be launched and the requested navigation scenarios could not be exercised. Findings below are static risk classifications only; they are not fabricated Logcat events.

## Findings

| Priority | Severity | Violation found | Affected files | Suspected cause | User impact | Fix recommendation | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P0 | Info | No runtime Logcat capture available | N/A | No attached Android device/emulator | Unknown; runtime evidence unavailable | Re-run this audit with a debug device and capture `StrictMode`/`StrictMode policy violation` lines while exercising the requested screens | Deferred |
| P1 | Medium | Potential main-thread disk/network-adjacent startup work | `app/src/main/java/forpdateam/ru/forpda/App.kt`, `app/src/main/java/forpdateam/ru/forpda/client/Client.kt`, `app/src/main/java/forpdateam/ru/forpda/di/AppModule.kt` | Application startup initializes preferences, AppMetrica, WorkManager scheduling, WebView user-agent probing, and client cache/preferences paths. Some of these APIs may touch disk or binder-backed services before the first screen is ready. | Cold-start jank or StrictMode disk-read/write logs on debug builds | Capture runtime stack traces first. Move confirmed disk work to `Dispatchers.IO` or lazy initialization only when it is safe for startup behavior. Keep StrictMode enabled and do not suppress policies. | Deferred |
| P1 | Medium | Potential main-thread preferences reads in immediate getters | `app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt`, `app/src/main/java/forpdateam/ru/forpda/model/datastore/TopicDataStore.kt`, `app/src/main/java/forpdateam/ru/forpda/model/datastore/OtherDataStore.kt` | Immediate fallback getters and DataStore flows read legacy `SharedPreferences` values. If called from UI paths before preferences are memory-warm, StrictMode may report disk reads. | UI hitches when opening topics/settings/search if legacy fallback is hit on the main thread | Prefer pre-warmed mirrored values or call suspend/DataStore paths from background work. Confirm with Logcat stacks before changing behavior because these getters may be intentionally synchronous for UI defaults. | Deferred |
| P2 | Low | Potential blocking bridge/intercept work | `app/src/main/java/forpdateam/ru/forpda/common/ForPdaCoil.kt`, `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt` | Synchronous wrappers use `runBlocking(Dispatchers.IO)` for image/WebView intercept paths. This avoids main-thread IO by dispatcher, but still blocks the caller thread and can amplify image-heavy topic latency. | Slower image-heavy topic rendering or WebView resource stalls; not necessarily a StrictMode violation if caller is not main | Keep the blocking bridge only where required by WebView/Coil APIs. Consider cache-first paths and bounded timeouts for any confirmed slow stacks. | Deferred |
| P2 | Low | Potential content resolver/file IO around downloads and attachments | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/downloads/DownloadsFragment.kt`, `app/src/main/java/forpdateam/ru/forpda/common/FilePickHelper.kt` | Download deletion already routes MediaProvider/file deletes through `Dispatchers.IO`. File picking opens streams and must be verified from the upload call path. | If any caller runs file opening on the main thread, attachment upload may jank or log StrictMode disk reads | No change to the guarded download path. Re-check attachment upload with Logcat and move confirmed stream-open work off main if needed. | Deferred |
| P2 | Low | Potential lifecycle/leak risk not covered by current VM policy | `app/src/main/java/forpdateam/ru/forpda/App.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/views/MessagePanel.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/views/SmartNavigationMenu.kt` | App-wide and view-owned coroutine scopes require explicit cancellation. Current VM policy only enables `detectLeakedClosableObjects()`, so Activity/service registration leaks may not be logged. | Possible retained work after view/app lifecycle transitions; StrictMode may not detect it with the current narrow VM policy | Audit lifecycle cancellation separately. If adding more VM detectors, keep them debug-only and penaltyLog-only, then fix reported leaks instead of suppressing them. | Deferred |

## Fixed Now

No code fixes were applied.

Rationale: without a connected device, no concrete StrictMode stack trace was available. The static findings above point at plausible risk areas, but changing startup, preferences, WebView, or bridge code without runtime evidence would exceed the small-safe-fix scope of this task.

## Deferred Runtime Checklist

Re-run with a device/emulator attached:

- Launch stable debug build.
- Clear Logcat, then filter for `StrictMode`, `StrictMode policy violation`, `DiskReadViolation`, `DiskWriteViolation`, `NetworkViolation`, and leaked closable messages.
- Open a normal topic, long topic, and image-heavy topic.
- Open Answers, Favorites, and Search.
- Send the app to background and return to foreground.
- Save full stacks for each unique violation.
- Classify each unique stack by user impact and fix only the root cause.

## Acceptance Notes

- StrictMode remains debug-only in `App.kt`.
- StrictMode uses `penaltyLog()` and does not use `penaltyDeath()`.
- No release StrictMode enablement was added.
- No broad refactors, new dependencies, or violation suppressions were added.
