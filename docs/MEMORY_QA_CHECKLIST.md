# Memory QA Checklist (P-10)

> Program for catching Activity/Fragment/View leaks **before** they hit
> production. This is the living counter-part of the audit findings
> A-01/A-02/A-04 — every new lifecycle-sensitive change should pass
> through this checklist.

## 1. Tooling (in order of cost)

| Tool | When | Setup cost | Notes |
|------|------|-----------|-------|
| `leakcanary-android` (debug only) | Every debug build | Low (Gradle dep) | Auto-detects Activity/Fragment/View leaks. Add to `app/build.gradle` `debugImplementation` and `leakCanary { enabled = true }` is the default. |
| Android Studio Profiler → **Memory** | Manual QA, before each release | None | Capture heap dump, look for retained objects after navigating back. |
| `dumpsys meminfo <package>` | Automated, in CI smoke | None | Compare PSS / Java heap across runs. |
| StrictMode `detectLeaks()` | Already enabled in `App.setupStrictMode()` | None | Catch SQLite/Cursor leaks. |
| Macrobenchmark + `MemoryUsageMetric` | Pre-release, on a real device | Medium | Stable, repeatable, requires Macrobenchmark module. |

## 2. LeakCanary setup (target state)

Add to `app/build.gradle`:

```gradle
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

The first debug build will auto-install the `LeakCanary` `ContentProvider`
and start monitoring Activity/Fragment lifecycles.

For the WebView and `JavaScript Interface` objects (see Stage 1), LeakCanary
needs a `ReachabilityWatcher` chain — see [LeakCanary + WebView docs](https://square.github.io/leakcanary/recipes/#how-to-fix-a-leak).
**Action**: monitor `ThemeFragmentWeb`, `QmsChatFragment`, `WebView` in
`onDestroy()` and confirm they are GCed.

## 3. Manual QA loop (run before each release)

1. Install the **debug** APK with LeakCanary on a low-RAM device
   (Android 7–9, 1–2 GB RAM).
2. Reproduce the following 5 navigation flows and click **back** at the end
   of each:
   - Theme → scroll 200 posts → tap post menu → close → back
   - QMS chat → type message → close WebView
   - Search → apply filter → back
   - Profile → open QMS list → back → back
   - News list → open article → back
3. Wait 5 s, then trigger GC from LeakCanary notification.
4. **Gate**: zero `Leaking: false` reports. If any leak appears, file a
   bug referencing the flow.

## 4. Heap dump gate (release candidate)

Take a heap dump from Android Studio Profiler after the 5-flow loop above.

- `ThemeFragment` instances in heap: **0**
- `QmsChatFragment` instances in heap: **0**
- `WebView` instances in heap: **0**
- `MainScope`/`viewScope` `Job` retained > 10 s after back: **0**
- `ThemeUseCase.appScope` active jobs after 10 s idle: **0**

## 5. CI smoke check (low cost)

Add a Gradle task that runs on each PR:

```bash
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=forpdateam.ru.forpda.leak.LeakGateTest
```

Where `LeakGateTest` repeats the 5 flows above and asserts no leak
detection callback fires within 30 s. (To be added when the team has
bandwidth — out of scope for the current PR.)

## 6. Open questions

- Do we want a **headless** LeakCanary run on Firebase Test Lab? Pros:
  catches leaks on devices we don't own. Cons: 5–10 min extra per run.
- Should `Application.ApplicationExitInfo` callbacks be sampled to detect
  leaks in production via total PSS trends? (Post-v1 idea.)

## 7. Status (June 2026)

- LeakCanary dep: **not yet added** — pending product approval (debug-only
  APK size impact ~2 MB).
- Manual QA loop: **documented only** — team to adopt on next release
  candidate.
- CI gate: **planned, not implemented**.
