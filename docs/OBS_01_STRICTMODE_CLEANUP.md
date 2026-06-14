# OBS-01 StrictMode Cleanup

## Scope

StrictMode setup and previously documented diagnostics were reviewed for small, safe lifecycle and blocking-work fixes. Runtime Logcat capture was not available because `adb devices` returned no attached Android device or emulator.

## StrictMode Setup

- `app/src/main/java/forpdateam/ru/forpda/App.kt` enables StrictMode only for `BuildConfig.DEBUG`.
- Thread policy uses `detectAll()` with `penaltyLog()`.
- VM policy uses `detectLeakedClosableObjects()` with `penaltyLog()`.
- No `penaltyDeath()` use was found in the reviewed StrictMode setup.

## Findings

| Category | Finding | Status |
| --- | --- | --- |
| Safe quick fix | `App` registered a process lifecycle observer and a Doze `BroadcastReceiver` without keeping explicit handles for a full cleanup path. | Fixed now |
| Safe/no-op guard | Repeated `setupDozeReceiver()` calls could register another receiver if initialization is ever retried. | Fixed now |
| Needs architecture/runtime evidence | Startup still does AppMetrica, WorkManager scheduling, preference reads, WebView/Coil setup, and notification channel setup on the startup path. These may trigger StrictMode disk/binder stacks, but changing them safely needs actual Logcat traces. | Deferred |
| Needs runtime evidence | Synchronous preference mirror getters and WebView/image blocking bridges remain possible jank sources. They require stack traces before changing because they are behavior-sensitive. | Deferred |
| False/noisy for this pass | Disk cache work found in `EditPostDiskCache` runs from an IO coroutine and was not changed. | No change |

## Fixed Now

- Added explicit cleanup for app-level lifecycle resources in `App.kt`.
- Stored the Doze receiver instance, guarded against duplicate registration, and unregister it during application cleanup.
- Stored the process lifecycle observer instance and remove it during cleanup.
- Stopped the network tracker, cleared the receiver reference, cancelled the app scope, and cleared the singleton reference from one cleanup path.

## Deferred

- Re-run with a connected device/emulator and capture `StrictMode`, `StrictMode policy violation`, `DiskReadViolation`, `DiskWriteViolation`, `NetworkViolation`, and leaked closable stacks.
- Use those stacks to decide whether startup preference reads, WorkManager scheduling, AppMetrica activation, WebView user-agent probing, or notification setup can be safely moved off the UI thread.
- Re-check attachment selection and image-heavy topic flows under Logcat before changing blocking bridge code.
