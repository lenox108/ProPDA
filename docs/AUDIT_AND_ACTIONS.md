# ForPDA — Full Project Audit Report and Action Plan

## 1. Title and Scope

- **Project:** ForPDA 2 / ProPDA (Android fork of the 4pda.ru client).
- **Repo:** `/Users/j.golt/Documents/Cursor01/ForPDA-master`.
- **Stack:** Kotlin 2.0.21 + Hilt + OkHttp 4.12.0 + Coil 2.7.0 + Compose + Room + WorkManager. `compileSdk`/`targetSdk` 35, `minSdk` 24, AGP 8.11.1.
- **Scope of this document:** static review of the entire Android project; consolidation of the prior `docs/AUDIT_REPORT.md` and the recommended action plan into a single end-to-end deliverable.
- **Prior artifacts referenced (see §12.D):** `PERF_DIAGNOSIS.md`, `REFACTOR_PLAN.md`, `docs/PROPDA_FINAL_CODE_AUDIT_REPORT.md`, `docs/PROPDA_HARDENING_REPORT.md`, `docs/JS_BRIDGE_INVENTORY.md`, `docs/QMS_BRIDGE_INVENTORY.md`, `docs/READER_MODE_INVENTORY.md`, `docs/POST_ACTIONS_INVENTORY.md`, `docs/UI_THREAD_RENDERING_INVENTORY.md`, `docs/WEBVIEW_INTERCEPT_PROFILING.md`, `scripts/battery-audit.sh`.
- **Out of scope (this audit):** fixing code, running the app, executing gradle build, committing. The numbered actions in §4 are recommendations, not applied changes.

---

## 2. Executive Summary

The codebase is a mature, mostly well-hardened fork. The networking layer, cookie/secret storage, WebView URL policy, and JS bridge surface have been improved over many rounds (see `PERF_DIAGNOSIS.md`, `PROPDA_HARDENING_REPORT.md`, `JS_BRIDGE_INVENTORY.md`). However, the project still ships with a small set of real residual risks and a larger set of fragility/smell issues that will produce crashes or UX regressions under specific real-world conditions. The team must:

- **Fix the render-token guard gap on destructive `ThemeJsInterface` methods** *(S-01, Critical)*. `IThemePresenter` exposes `deletePost`, `editPost`, `votePost`, `submitPoll`, `reply`, `quote*`, `reportPost`, `openLink`, `openImage` without consulting `ThemeRenderGuard`. A template injection or untrusted HTML re-render reaches native destructive actions. *(Cross-ref: `PROPDA_FINAL_CODE_AUDIT_REPORT.md §H1`.)*
- **Eliminate the `!!` crash surface in user-visible flows** *(C-01..C-08, High/Medium)*. 152 non-null assertions across `app/src/main`; 27 in `MessagePanel.kt` alone (22 consecutive on `ViewBinding.inflate` at lines 137–161). `searchBlankVerifyRunnable!!` (`SearchFragment.kt:633`) and `QmsChatFragment` stale-bridge re-registration are the highest-impact items.
- **Move `CookieManager.initializeCookies()` off the main thread** *(P-04, High)*. The Hilt-singleton init block runs the first-touch `EncryptedSharedPreferences.create(...)` AES-GCM unwrap and a SharedPreferences migration on the thread that constructs `Client` — which is the main thread. On slow devices this produces a cold-start ANR.
- **Remove `runBlocking` from the OkHttp / suspending path** *(A-03, High)*. Five sites (`ForPdaCoil.kt:215,240`, `NewsApi.kt:62`, `ThemeApi.kt:341`, `AvatarRepository.kt:40,48`) call `runBlocking(Dispatchers.IO)` from contexts that can be on `Dispatchers.Main`; one deadlock class is enough to drop a release.
- **Harden the manifest, WebView and secrets path** *(M-01, S-04, S-05, S-07, Medium/Low)*. Add `dataExtractionRules`, move the AppMetrica key to `BuildConfig`, route news `openExternalBrowser`/`openImage` through `UrlPolicy`, and switch `mixedContentMode` to `MIXED_CONTENT_NEVER_ALLOW`.

The 4-sprint implementation roadmap is in §11.

---

## 3. Severity Table (51 findings)

| ID   | Title                                                                                          | Area               | File(s)                                                                                                  | Severity |
| ---- | ---------------------------------------------------------------------------------------------- | ------------------ | -------------------------------------------------------------------------------------------------------- | -------- |
| S-01 | Theme destructive JS bridge methods have no render-token guard                                 | WebView/JS         | `presentation/theme/ThemeJsInterface.kt`, `presentation/theme/ThemeRenderGuard.kt`                       | **Critical** |
| S-02 | Destructive theme methods (`editPost`/`deletePost`/`votePost`/`submitPoll`) callable after view destroyed in some races | WebView/JS  | `ui/fragments/theme/ThemeBridgeHandler.kt`, `ThemeFragmentWeb.kt`                                       | High |
| S-03 | `App.instance!!` non-null getter used from many places; NPE-prone in tests                      | Lifecycle          | `App.kt:103`, `common/HtmlToSpannedConverter.kt:171`, `client/interceptors/ErrorInterceptor.kt`          | High |
| S-04 | Hardcoded AppMetrica API key in `App.kt`; not behind BuildConfig                               | Security/Secrets   | `App.kt:263`                                                                                             | Medium |
| S-05 | `ArticleContentFragment.openExternalBrowser(url)` may pass URL to external intent without `UrlPolicy` gate | WebView/Sec | `ui/fragments/news/details/ArticleContentFragment.kt`, `common/webview/UrlPolicy.kt` *(see PROPDA_FINAL_CODE_AUDIT_REPORT.md §M4)* | Medium |
| C-01 | `searchBlankVerifyRunnable!!` non-null assertion in `postDelayed`                              | Crash              | `ui/fragments/search/SearchFragment.kt:633`                                                              | Medium |
| C-02 | 22 consecutive `!!` on `ViewBinding.inflate` results in `MessagePanel.kt`                       | Crash/Smell        | `ui/views/messagepanel/MessagePanel.kt:137-161`                                                          | Medium |
| C-03 | `downloadFile()` NPEs if `systemLinkHandler` not wired (constructor-nullable)                   | Crash              | `common/webview/CustomWebViewClient.kt:288`                                                              | Medium |
| C-04 | `AvatarRepository.getAvatar(nick)` throws raw `NullPointerException` for unknown nicks          | Crash              | `model/repository/avatar/AvatarRepository.kt:24-37`                                                       | High |
| C-05 | `NotificationsService.IncomingHandler` does `Toast` from `Service` `applicationContext` (no try/catch) | Crash        | `notifications/NotificationsService.kt:547-555`                                                          | Medium |
| C-06 | `ThemePostedPageScrollPolicy` uses `maxByOrNull{...}!!id` — empty list NPE                       | Crash              | `presentation/theme/ThemePostedPageScrollPolicy.kt:27-28`                                                | Low |
| C-07 | `ThemeHistoryController.history.last()`/`.first()` after `.filter` — empty list NPE              | Crash              | `presentation/theme/ThemeHistoryController.kt:101,138,169` *(see also PERF_DIAGNOSIS.md)*                 | Low |
| C-08 | `QmsChatFragment` recreates WebView with stale JS bridge still attached                        | Crash/Race         | `ui/fragments/qms/chat/QmsChatFragment.kt:2400-2421`                                                      | High |
| C-09 | StrictMode disabled in release (debug-only) — release ships unobserved                         | Tooling            | `App.kt:220-235`                                                                                          | Info |
| A-01 | `MainScope()` leaks: 3 sites (`TabRouter`, `ThemeDialogsHelper_V2`, `CodesPanelItem`)         | Architecture       | `presentation/TabRouter.kt:16`, `ui/fragments/theme/ThemeDialogsHelper_V2.kt:43`, `ui/views/messagepanel/advanced/CodesPanelItem.kt:41` | Medium |
| A-02 | `GlobalScope.launch` in `ThemeUseCase` — un-cancellable                                           | Architecture       | `model/interactors/theme/ThemeUseCase.kt:326`                                                              | Medium |
| A-03 | `runBlocking` on the OkHttp dispatcher thread: 5 sites                                          | Architecture       | `common/ForPdaCoil.kt:215,240`, `model/data/remote/api/news/NewsApi.kt:62`, `model/data/remote/api/theme/ThemeApi.kt:341`, `model/repository/avatar/AvatarRepository.kt:40,48` | High |
| A-04 | `ThemeViewModel` is a god-class (>4000 lines) — see REFACTOR_PLAN §1.1                          | Architecture       | `presentation/theme/ThemeViewModel.kt`                                                                    | Low (already documented) |
| A-05 | `AppDatabase` is monolithic; entities from 6 different domains in one DB                        | Architecture       | `entity/db/notes/AppDatabase.kt`                                                                          | Low |
| A-06 | All `Parsers` use `pattern.matcher(html).find()` with shared `IPatternProvider`                 | Architecture       | `model/data/storage/IPatternProvider.kt`, 14 parser classes                                              | Info |
| P-01 | OkHttp HTTP cache is 10 MB — too small for an article/QMS-heavy app; no `Cache-Control: max-age` | Performance | `client/Client.kt:112`                                                                                    | Medium |
| P-02 | `ImageLoadingInterceptor` does `Thread.sleep(300)` on the OkHttp dispatcher for 504/503 retry    | Performance        | `client/interceptors/ImageLoadingInterceptor.kt:72`                                                       | Medium |
| P-03 | Article phase-2 always scheduled even when not needed (see PERF_DIAGNOSIS §5.6)                  | Performance        | `model/interactors/news/ArticleInteractor.kt:270`                                                         | Low (already documented) |
| P-04 | `CookieManager.initializeCookies` runs on Client construction thread; first-time AES-GCM unwrap can stall | Performance | `client/CookieManager.kt:43-81`                                                                           | High |
| P-05 | `ForPdaCoil` disk cache is 128 MB; memory cache 25% of process heap                            | Performance        | `common/ForPdaCoil.kt:73-86`                                                                              | Info |
| P-06 | `CachedDns` TTL is 30 s — fine for mobile; no `clearCache()` is called on network changes        | Performance        | `client/CachedDns.kt:20,61`                                                                                | Low |
| P-07 | `WebSocketController` holds sockets in `synchronized(lock)`; message parsing on dispatcher thread | Performance | `client/WebSocketController.kt:21-118`                                                                     | Low |
| S-06 | `WebView` instantiates `ExtendedWebView` via reflection-free path; constructor is fine             | WebView            | `ui/views/ExtendedWebView.kt:67`                                                                           | Info |
| S-07 | `mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE` (allows mixed content)                    | WebView            | `ui/views/ExtendedWebView.kt:151`                                                                          | Low |
| S-08 | `DownloadsService` is correctly typed `dataSync` (Android 14+), but exported=false (good)        | Manifest           | `AndroidManifest.xml:124-127`                                                                              | Info |
| M-01 | `allowBackup="true"` + `fullBackupContent="false"` — should explicitly set `dataExtractionRules`  | Manifest           | `AndroidManifest.xml:49-50`                                                                                | Medium |
| M-02 | `MainActivity` is `exported="true"` and has a `http(s)://4pda.to*` deep-link intent filter         | Manifest           | `AndroidManifest.xml:59-98`                                                                                | Low (intentional) |
| M-03 | `android:versionCode="349"` in manifest is **stale** vs `app/build.gradle:67` `versionCode 350`   | Build              | `AndroidManifest.xml:4`, `app/build.gradle:67`                                                            | Low |
| M-04 | `WakeUpReceiver` is `exported="true"` — protected by `RECEIVE_BOOT_COMPLETED` permission          | Manifest           | `AndroidManifest.xml:157-165`                                                                              | Info |
| X-01 | Login form: `nick` and `password` sent in `Cp1251` (CP1251-encoded) — works for 4pda but is legacy  | Auth/UX          | `model/data/remote/api/auth/AuthApi.kt:42-43`                                                              | Info |
| X-02 | No "logout" in UI — only via `AuthApi.logout`                                                  | UX                 | `presentation/auth/AuthViewModel.kt` (no logout method visible)                                          | Info |
| X-03 | Captcha is loaded via external browser (`GoogleCaptchaFragment`) — full-screen WebView, OK        | UX                 | `ui/fragments/other/GoogleCaptchaFragment.kt`                                                              | Info |
| X-04 | `WebView` font scale defaults to `textZoom = 100`, ignores user fontScale for first paint         | UX                 | `ui/views/ExtendedWebView.kt:154`                                                                          | Low |
| X-05 | No `enableEdgeToEdge` on any activity (although manifest sets `enableOnBackInvokedCallback`)    | UX                 | `AndroidManifest.xml:57`                                                                                   | Low |
| D-01 | `coil 2.7.0` — current; no known CVE                                                          | Dependencies       | `gradle/libs.versions.toml:55`                                                                             | Info |
| D-02 | `okhttp 4.12.0` — current; no known CVE                                                        | Dependencies       | `gradle/libs.versions.toml:52`                                                                             | Info |
| D-03 | `androidx.security:security-crypto:1.1.0-alpha06` — alpha                                     | Dependencies       | `gradle/libs.versions.toml:40`                                                                             | Medium |
| D-04 | `minitemplator 1.2` (JCenter era; `repackaged`) — abandoned upstream                            | Dependencies       | `gradle/libs.versions.toml:72`                                                                             | Medium |
| D-05 | `sectioned-recyclerview 0.5.0` — afollestad, archived (1.2.0 was last non-deprecated)            | Dependencies       | `gradle/libs.versions.toml:83`                                                                             | Low |
| D-06 | `jsoup 1.18.3` — current                                                                       | Dependencies       | `gradle/libs.versions.toml:70`                                                                             | Info |
| D-07 | `tagsoup 1.2.1` — very old; classpath from JCenter; no recent CVE                              | Dependencies       | `gradle/libs.versions.toml:71`                                                                             | Low |
| D-08 | `cicerone 6.6` (forked from terrakok/cicerone)                                                | Dependencies       | `gradle/libs.versions.toml:64`                                                                             | Info |
| D-09 | `cicerone` GitHub source is JitPack; tag `6.6` is a moving tag                                  | Dependencies       | `gradle/libs.versions.toml:64`, `settings.gradle:16`                                                       | Low |
| D-10 | `hilt 2.53.1` + JavaPoet force-pin (workaround for Hilt 2.51+)                                 | Dependencies       | `build.gradle:9-12`                                                                                         | Info |
| D-11 | No `unitTests.includeAndroidResources = true`; many test paths may be broken in CI (already in `test-fixes-plan.md`) | Tests   | `app/build.gradle:211-213`                                                                                  | Info |
| T-01 | `detekt-baseline.xml` is 484 KB — has absorbed hundreds of legacy findings (see `detekt.yml`)    | Tooling            | `detekt-baseline.xml`, `detekt.yml`                                                                          | Medium |
| T-02 | `assembleStoreRelease` is intentionally blocked without `keystore.properties` (good)            | Build              | `app/build.gradle:241-263`                                                                                  | Info |
| T-03 | `proguard-rules.pro` has `-keepnames class forpdateam.ru.forpda.entity.db.** { *; }` (good)      | Build              | `app/proguard-rules.pro:54`                                                                                  | Info |
| T-04 | ProGuard rule `-keep class forpdateam.ru.forpda.App { public <init>(); }` is redundant with Hilt  | Build              | `app/proguard-rules.pro:24-26`                                                                              | Low |

---

## 4. Top 10 Prioritized Actions (ROI-ordered)

Each action references finding IDs from §3. Use this as a planning aid — this audit is not a fix list.

### Action 1 — Add render-token guard to all destructive `ThemeJsInterface` methods

- **Priority:** 1 (highest)
- **Severity addressed:** Critical (`S-01`, `S-02`)
- **Files to change:** `presentation/theme/ThemeJsInterface.kt`, `presentation/theme/ThemeRenderGuard.kt`, `presentation/theme/ThemeBridgeHandler.kt`, `app/src/main/assets/forpda/templates/theme.html` (or equivalent template path), `presentation/theme/ThemeViewModel.kt`, new test files under `app/src/test/.../theme/`.
- **Concrete steps:**
  - Generate a per-render token (e.g. `UUID.randomUUID().toString()`) when a theme page is loaded, store it on the `ThemeViewModel` for the lifetime of that render, and inject it into the theme HTML as `<meta name="forpda-render-token" content="...">`.
  - Add a `verifyRenderToken(candidate: String): Boolean` to `ThemeRenderGuard`; invalidate the token on `WebView.reset()` / fragment `onDestroyView`.
  - Wrap each destructive `@JavascriptInterface` method (`deletePost`, `editPost`, `votePost`, `submitPoll`, `reply`, `quotePost`, `quoteSelectedText`, `reportPost`, `openLink`, `openImage`) to call the guard; reject with a logged no-op if the token is missing or stale.
  - Mirror the existing `NewsApi` poll-token pattern from `ArticleContentFragment` so the cross-bridge contract is consistent.
  - Add unit tests for: missing token, stale token (after WebView reset), wrong token, and a positive happy path.
- **Acceptance criteria:**
  - All 10 destructive methods are guarded; `rg "@JavascriptInterface" ThemeJsInterface.kt` shows the guard call as the first line of each.
  - A forged template that calls `forpda.deletePost(...)` without the meta tag is silently dropped (verified in instrumented test).
  - Unit-test coverage for the guard is ≥ 4 cases and CI-gated.
- **Estimated effort:** M–L
- **Risk of doing it:** Low. The bridge is currently unguarded; adding a guard can only reduce blast radius. Regression risk is limited to false-positives (legitimate calls rejected) — mitigated by per-render token scoping and the `NewsApi` pattern.

### Action 2 — Convert `AvatarRepository` and `ForPdaCoil` sync APIs to `suspend`; remove `runBlocking` from `NewsApi` and `ThemeApi`

- **Priority:** 2
- **Severity addressed:** High (`A-03`, `C-04`)
- **Files to change:** `common/ForPdaCoil.kt`, `model/data/remote/api/news/NewsApi.kt:62`, `model/data/remote/api/theme/ThemeApi.kt:341`, `model/repository/avatar/AvatarRepository.kt`.
- **Concrete steps:**
  - Change `ForPdaCoil.loadBitmapSync` and `ForPdaCoil.loadBitmapSyncForIntercept` to `suspend` variants that internally `withContext(Dispatchers.IO)`. Keep a `loadBitmapBlocking` shim for the WebView intercept on the binder thread; mark it `@WorkerThread` and document that it is unsafe on `Dispatchers.Main`.
  - Replace `runBlocking(Dispatchers.IO) { … }` in `NewsApi.kt:62` and `ThemeApi.kt:341` with `withContext(Dispatchers.IO) { … }` and make the calling functions `suspend`.
  - In `AvatarRepository`, change `getAvatar(nick)` to return `String?`; introduce `AvatarNotFoundException(nick)` for callers that still want exception semantics.
  - Audit and update all callers of `getAvatar(nick)` (notably `NotificationsService.sendNotification` line 263, `getAvatarForWebViewInterceptSync` line 46-55) for the new null-returning contract.
  - Add a Lint / detekt rule (or a CI grep) that fails on new `runBlocking` occurrences in `app/src/main`.
- **Acceptance criteria:**
  - `rg "runBlocking" app/src/main` returns zero matches.
  - `ForPdaCoil`, `NewsApi`, `ThemeApi`, `AvatarRepository` have no `runBlocking` and the coroutine path is `suspend` end-to-end.
  - Unit tests cover: success, null on unknown nick, exception variant.
- **Estimated effort:** M
- **Risk of doing it:** Medium. Callers in WebView intercept path must be re-audited for thread affinity. A regression would re-introduce the original deadlock class — gate the migration behind a feature flag or staged rollout if the change is large.

### Action 3 — Make `CookieManager.initializeCookies()` lazy / off-main

- **Priority:** 3
- **Severity addressed:** High (`P-04`)
- **Files to change:** `client/CookieManager.kt`, `client/Client.kt` (consumers), `App.kt` (Hilt graph).
- **Concrete steps:**
  - Replace the eager `init { initializeCookies() }` block in `CookieManager` with a `private val cookiesFlow: StateFlow<Map<String, String>>` that loads on first read.
  - Make the first read happen on the first outbound `Interceptor.intercept` (which is always on the OkHttp dispatcher, off the main thread), and keep an in-memory snapshot for subsequent main-thread reads.
  - Add a `runBlocking` *only* on the first read to bridge the cold start, with a strict `Looper.myLooper() != Looper.getMainLooper()` precondition.
  - Move the AES-GCM unwrap from `CookieManager` into a `Lazy<SecureCookiesPreferences>` (or into a `DataModule` provider) so the first decrypt happens on `Dispatchers.IO`.
  - Verify the cold-start path: `App.onCreate` → `setupCoil` → `ForPdaCoil.init` → `webClient.getHttpClient()` → first `request` does not block the main thread.
- **Acceptance criteria:**
  - `StrictMode.ThreadPolicy.detectDiskReads().penaltyLog()` does not flag cookie/encryption I/O on main thread during a fresh launch (verified with `scripts/battery-audit.sh` or a one-off StrictMode run).
  - All cookie reads on the main thread are from the in-memory snapshot, never from disk.
  - The first authenticated request after process death is observably off-main (a `Looper.getMainLooper().thread !== Thread.currentThread()` assert in tests).
- **Estimated effort:** M
- **Risk of doing it:** Medium. The risk is reading stale cookies for the first request after process restart; mitigate by warming the snapshot in `App.onCreate` on `Dispatchers.IO` and awaiting it before the first authenticated call (using a `CountDownLatch` or `Mutex`).

### Action 4 — Fix `QmsChatFragment` WebView re-creation: cancel old `jsInterface` before `destroy()`

- **Priority:** 4
- **Severity addressed:** High (`C-08`)
- **Files to change:** `ui/fragments/qms/chat/QmsChatFragment.kt` (lines 2400–2421), `presentation/qms/QmsChatJsInterface.kt`, `common/webview/BaseJsInterface.kt` (if the `cancel()` is missing — verify).
- **Concrete steps:**
  - Before calling `webView.destroy()`, call `jsInterface?.cancel()` and null out any reference the old `QmsChatJsInterface` holds to the presenter / fragment.
  - Re-order the block so `removeJavascriptInterface(JS_INTERFACE)` runs *before* the new `addJavascriptInterface(jsInterface, JS_INTERFACE)` on the new WebView.
  - Add an integration test that simulates: open QMS → rotate device → re-open QMS. The old bridge's pending coroutine jobs must not fire into the new bridge.
  - Audit the analogous block in `ArticleContentFragment` (lines 1124–2952) — see §6 hotspot #17 — and apply the same pattern.
- **Acceptance criteria:**
  - Rotation test passes 100/100 without a `IllegalStateException` or a `presenter is null` crash.
  - `rg "addJavascriptInterface" QmsChatFragment.kt` is paired with a `removeJavascriptInterface` in the same lifecycle method.
  - No leaked coroutine jobs from `BaseJsInterface`'s `MainScope` survive `onDestroyView` (verified by `LeakCanary` in debug builds).
- **Estimated effort:** S
- **Risk of doing it:** Low. The fix is a teardown ordering change; the only risk is a missing cancellation hook on a non-default bridge, mitigated by the new test.

### Action 5 — Replace `searchBlankVerifyRunnable!!` and the 22 `!!` cascade in `MessagePanel`

- **Priority:** 5
- **Severity addressed:** Medium (`C-01`, `C-02`)
- **Files to change:** `ui/fragments/search/SearchFragment.kt:633` (and the `removeCallbacks` site at 763), `ui/views/messagepanel/MessagePanel.kt:137-161` and related `!!` sites.
- **Concrete steps:**
  - In `SearchFragment`: replace `webView.postDelayed(searchBlankVerifyRunnable!!, 48L)` with `searchBlankVerifyRunnable?.let { webView.postDelayed(it, 48L) }`; the `removeCallbacks` call at line 763 already nulls the runnable and should not crash on a second `removeCallbacks` (verify).
  - In `MessagePanel`: introduce a sealed `Mode { Full, Quick }` and a single `bind(binding: …Binding)` block; replace the 22 `fullBinding!!` / `quickBinding!!` lines with `binding?.let { … }` (or non-null by construction once `Mode` is exhaustive).
  - Sweep the 152 `!!` occurrences across `app/src/main` for an Lint baseline update; this action is a focused first pass.
  - Add a unit test for `MessagePanel` mode binding that constructs a `null` binding and asserts no NPE.
- **Acceptance criteria:**
  - `rg "searchBlankVerifyRunnable!!" SearchFragment.kt` returns zero matches.
  - `rg "!!" MessagePanel.kt` count drops from 27 to ≤ 5 (only those justified by an `inflate` chain).
  - The new unit test passes; a `git blame` shows the new code introduces no new `!!` in this region.
- **Estimated effort:** XS–S
- **Risk of doing it:** Low. The current code is already brittle; replacing `!!` with `?.let` cannot increase crash surface.

### Action 6 — Add `dataExtractionRules` and harden `allowBackup`; switch to `MIXED_CONTENT_NEVER_ALLOW`

- **Priority:** 6
- **Severity addressed:** Medium (`M-01`), Low (`S-07`)
- **Files to change:** `AndroidManifest.xml:49-50`, new `app/src/main/res/xml/data_extraction_rules.xml`, `ui/views/ExtendedWebView.kt:151`.
- **Concrete steps:**
  - Create `data_extraction_rules.xml` with `<cloud-backup>` and `<device-transfer>` blocks that explicitly exclude `secure_cookies_prefs.xml` and `Preferences.Auth.AUTH_KEY` (key `auth_key`).
  - Add `android:dataExtractionRules="@xml/data_extraction_rules"` to the `<application>` element.
  - Verify `app/src/main/res/xml/data_extraction_rules.xml` excludes the encrypted SharedPreferences file by `<exclude domain="sharedpref" path="secure_cookies_prefs.xml"/>`.
  - In `ExtendedWebView.kt:151`, change `WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE` to `WebSettings.MIXED_CONTENT_NEVER_ALLOW`; verify no image asset breaks (the 4pda CDN is HTTPS).
  - Document the new file in `docs/` and add a checklist item to `PROPDA_QC_01_RELEASE_SANITY.md`.
- **Acceptance criteria:**
  - `aapt dump xmltree app/build/outputs/apk/release/app-release.apk AndroidManifest.xml` shows both `fullBackupContent` and `dataExtractionRules` set.
  - A `bmgr backupnow com.forpdateam.ru.forpda` run on a debug build produces a backup that *does not* contain `secure_cookies_prefs.xml` or `auth_key`.
  - All WebView screens render without mixed-content warnings in logcat.
- **Estimated effort:** S
- **Risk of doing it:** Low. The CDN is HTTPS; if any test thread hits a mixed asset, the failure mode is a missing image (visible in QA), not a crash.

### Action 7 — Move the AppMetrica API key to `BuildConfig` / Gradle property

- **Priority:** 7
- **Severity addressed:** Medium (`S-04`)
- **Files to change:** `app/build.gradle` (add `buildConfigField` for `APPMETRICA_API_KEY`), `App.kt:241-269`, `.gitignore` (verify `local.properties` is already ignored), `keystore.parallel.properties` (do **not** store the key here — use `local.properties` instead).
- **Concrete steps:**
  - In `app/build.gradle`, add `buildConfigField("String", "APPMETRICA_API_KEY", "\"${project.findProperty("APPMETRICA_API_KEY") ?: ""}\"")` per flavor.
  - In `App.kt`, replace the literal `"a94d9236-…"` with `BuildConfig.APPMETRICA_API_KEY`.
  - Add a `preBuild` task that fails the build if `APPMETRICA_API_KEY` is empty *and* the flavor is `store`.
  - Update the README with the new property name.
- **Acceptance criteria:**
  - `rg "a94d9236" app/src` returns zero matches.
  - `assembleStoreRelease` fails fast when the property is missing; `assembleDevRelease` succeeds with an empty key (analytics disabled).
  - The key is read from `local.properties` at build time and is not in any tracked file.
- **Estimated effort:** S
- **Risk of doing it:** Low. The existing `flavor == "store"` gate is preserved; the only behavior change is that the key is parameterized.

### Action 8 — Run the focused Search/media tests in green

- **Priority:** 8
- **Severity addressed:** Test coverage gap (cross-references `PROPDA_FINAL_CODE_AUDIT_REPORT.md §H2/H3` and `docs/test-fixes-plan.md`)
- **Files to change:** `app/src/test/.../search/`, `app/src/test/.../media/`, `app/build.gradle:211-213` (enable `unitTests.includeAndroidResources` per `D-11`).
- **Concrete steps:**
  - Pick the highest-value test paths from `PROPDA_FINAL_CODE_AUDIT_REPORT.md §H2/H3` (Search/media) and the `docs/test-fixes-plan.md` backlog.
  - Enable `unitTests.includeAndroidResources = true` in `app/build.gradle` to fix `D-11` first; this unblocks a class of Robolectric resource lookups.
  - Migrate tests that still depend on `App.instance!!` to a Hilt test rule or a test-only `Application` subclass (addresses `S-03`).
  - Add `TestResults` JUnit XML parsing to CI; fail the build on a new test failure.
  - Track a coverage floor for `ThemeViewModel`, `AvatarRepository`, and `CookieManager` (target 60% lines, 40% branches as a starting gate).
- **Acceptance criteria:**
  - `./gradlew :app:testStoreDebugUnitTest` is green; `./gradlew :app:testDevDebugUnitTest` is green.
  - Coverage report shows ≥ 60% line coverage in `ThemeViewModel`, `AvatarRepository`, `CookieManager`.
  - The CI job uploads JUnit XML and fails on any new failure.
- **Estimated effort:** M
- **Risk of doing it:** Medium. The risk is that enabling more tests reveals pre-existing failures from `D-11`; mitigate by triaging `docs/test-failures-pre-existing.md` and quarantining the long-tail failures.

### Action 9 — Cut `detekt-baseline.xml` to a small, hand-curated set

- **Priority:** 9
- **Severity addressed:** Medium (`T-01`)
- **Files to change:** `detekt-baseline.xml`, `detekt.yml`, possibly many source files for the underlying fixes.
- **Concrete steps:**
  - Run `./gradlew detektBaseline` with the current `detekt.yml`, then manually re-baseline only on the rules the team wants to enforce.
  - Enable high-signal rules: `ComplexMethod`, `LongMethod`, `LongParameterList`, `NestedBlockDepth`, `TooManyFunctions`. Disable the current blanket disables (`UndocumentedPublic*`, `MagicNumber`, etc.) one at a time.
  - Fix or `@Suppress` the new findings; do not re-baseline as a way to hide them.
  - Add a CI gate: `./gradlew detekt` must pass with `excludeCorrectable: false` and a baseline that is checked in as a small file (< 20 KB).
- **Acceptance criteria:**
  - `detekt-baseline.xml` is < 20 KB and contains only justified `@Suppress`-equivalent entries.
  - `./gradlew detekt` runs in CI and gates merges.
  - The high-signal rule set catches ≥ 5 real issues in the next 30 days (track in `docs/AI_REGRESSION_CHECKLIST.md`).
- **Estimated effort:** L (one-off)
- **Risk of doing it:** Medium. Disabling blanket disables will surface thousands of warnings; the team must commit to a multi-week fix-up or risk the CI gate becoming a blocker. Plan a 2-week ramp.

### Action 10 — Cache `OfflineArticleSource` URL validation and clear `CachedDns` on network change

- **Priority:** 10
- **Severity addressed:** Low (`P-06`)
- **Files to change:** `client/CachedDns.kt`, `app/src/main/java/forpdateam/ru/forpda/App.kt` (`setupNetworkTracking`).
- **Concrete steps:**
  - In `App.setupNetworkTracking`, register a `ConnectivityManager.NetworkCallback` that calls `cachedDns.clearCache()` on `onAvailable` for a different network than the last seen.
  - Audit the call sites that read from `CachedDns` for the empty-result edge case (see §6 hotspot #18: `cache[hostname] = emptyList()` is cached for 30 s on NXDOMAIN).
  - In `OfflineArticleSource`, add a `LruCache<String, ValidationResult>(256)` for URL validation results to avoid re-running `UrlPolicy.classify` on every reload.
- **Acceptance criteria:**
  - WiFi ↔ mobile transitions clear the DNS cache (verified with a `Log.d` tag in debug builds).
  - `OfflineArticleSource` shows no `UrlPolicy.classify` re-computation for the same URL within a session.
  - The empty-result edge case in `CachedDns.kt:42` is either handled or explicitly documented.
- **Estimated effort:** XS
- **Risk of doing it:** Low. The only behavior change is that the first request after a network switch may take a few ms longer (DNS re-resolve), which is the correct behavior.

---

## 5. Detailed Findings — Critical and High

This section restates the full evidence for the 1 Critical + 6 High findings. Medium / Low / Info findings are summarized in one-line form at the end of this section. (The prior `docs/AUDIT_REPORT.md §10.E` reported 8 High; re-counting the §3 table yields 6 — S-02, S-03, C-04, C-08, A-03, P-04 — and this document uses the table-derived count.)

### <a id="s-01"></a>S-01 (CRITICAL) — Theme destructive JS bridge methods have no render-token guard

- **Area:** WebView / JS bridge
- **File:** `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt`
- **Evidence (representative):**
  ```kotlin
  @JavascriptInterface fun deletePost(postId: String) { … }
  @JavascriptInterface fun editPost(postId: String) { … }
  @JavascriptInterface fun votePost(postId: String, type: String) { … }
  @JavascriptInterface fun submitPoll(action: String, method: String, encodedForm: String) { … }
  @JavascriptInterface fun reply(postId: String) { … }
  @JavascriptInterface fun quotePost(text: String, postId: String) { … }
  @JavascriptInterface fun reportPost(postId: String) { … }
  @JavascriptInterface fun openLink(url: String) { … }
  ```
- **Description:** `ThemeRenderGuard` already exists in the codebase but is *not* consulted by these methods. JS bridge surface for the theme is `IThemePresenter`, registered with `securityProfile == TRUSTED_LOCAL_TEMPLATE` (`ThemeBridgeHandler.kt:34-35`). If a future template change, a CSS-driven XSS, or an `articleCache.put` of a poisoned HTML payload is loaded into a theme WebView, destructive native actions are reachable from JS without a session/render proof. The `NewsApi` poll token check (ArticleContentFragment) shows the pattern is feasible.
- **Impact:** Native destructive actions reachable from a compromised trusted template.
- **Recommended fix:** Inject a per-render token into the theme HTML (`<meta name="forpda-render-token" content="...">`) and require it on every destructive `@JavascriptInterface` method. Invalidate token when the WebView is reset / fragment is destroyed. Add unit tests for missing/stale/wrong token scenarios. (See `PROPDA_FINAL_CODE_AUDIT_REPORT.md §H1` for the prior analysis — this is still the #1 risk.)
- **Effort:** M–L (Kotlin bridge + JS template + tests).

### <a id="s-02"></a>S-02 (HIGH) — Destructive theme methods callable after view destroyed in some races

- **Area:** WebView / JS bridge
- **Files:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeBridgeHandler.kt`, `ThemeFragmentWeb.kt`
- **Description:** The theme bridge is registered with the WebView (`addJavascriptInterface`), and the `IThemePresenter` reference is held in `ThemeViewModel`. When the fragment is destroyed (rotation, popping the back-stack, process restore), the JS bridge can still receive callbacks for in-flight template scripts. The bridge is currently coupled to fragment lifetime only via the WebView's `removeJavascriptInterface` call, which is not always reached before the JS callback fires. The `ThemeRenderGuard` is the correct mitigation, but as a defense-in-depth measure the bridge must also reject calls when `view == null` or `presenter == null`.
- **Recommended fix:** In `ThemeBridgeHandler`, gate every destructive call on `presenter?.view != null`; combine with the per-render token from S-01.
- **Effort:** S.

### <a id="s-03"></a>S-03 (HIGH) — `App.instance!!` non-null getter used from many places; NPE-prone in tests

- **Area:** Lifecycle / DI
- **Files:** `App.kt:103`, `common/HtmlToSpannedConverter.kt:171`, `client/interceptors/ErrorInterceptor.kt`, `BatteryDebugLogger`, and 4+ more (per `S-02` evidence; see `§9.2`).
- **Evidence (representative):**
  ```kotlin
  // App.kt
  val ctx: Context = App.instance!!
  // common/HtmlToSpannedConverter.kt:171
  val res = App.instance!!.resources
  // client/interceptors/ErrorInterceptor.kt
  App.instance!!.packageName
  ```
- **Description:** `App.instance` is a nullable singleton getter. Safe in production (the application instance is non-null after `onCreate`), but any Robolectric / unit test that creates a fresh `Application` or resets `_instance` will NPE on the first call. The pattern also makes it impossible to swap in a Hilt-injected `Context` for tests.
- **Impact:** Tests that exercise `HtmlToSpannedConverter` or `ErrorInterceptor` fail intermittently; the AppMetrica `UncaughtExceptionHandler` is wired through `App.instance!!` and can mask real test failures.
- **Recommended fix:** Inject `@ApplicationContext context: Context` (Hilt) into the call sites. The `HtmlToSpannedConverter` is a static utility; convert to a class and inject. The `ErrorInterceptor` is a Hilt singleton; add a `Context` parameter to its constructor. `App.instance` may remain as a debug-only fallback.
- **Effort:** M (touches many small files).

### <a id="c-04"></a>C-04 (HIGH) — `AvatarRepository.getAvatar(nick)` throws raw NPE for unknown nicks

- **File:** `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt:24-37`
- **Evidence:**
  ```kotlin
  suspend fun getAvatar(nick: String): String =
      withContext(Dispatchers.IO) {
          fetchAvatarByNick(nick) ?: throw NullPointerException("No avatar/user by nick: $nick")
      }
  ```
- **Description:** Throwing a raw NPE is a code smell (use a domain exception) and a reliability risk: `NotificationsService.sendNotification` calls `avatarRepository.getAvatar(event.userId, event.userNick)` at line 263, and the user nick can be empty for QMS events from unknown users. The `runCatching { … }.onFailure { sendNotification(event, null) }` at line 262-275 does catch it, but the WebView intercept path (`getAvatarForWebViewInterceptSync`, line 46-55) silently returns `null` and the avatar is just absent.
- **Recommended fix:** Return `String?`; let callers decide. If NPE is needed, throw `AvatarNotFoundException(nick)`.
- **Effort:** XS.

### <a id="c-08"></a>C-08 (HIGH) — `QmsChatFragment` recreates WebView with stale JS bridge still attached

- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt:2400-2421`
- **Evidence:**
  ```kotlin
  if (::webView.isInitialized) {
      unregisterForContextMenu(webView)
      webView.removeJavascriptInterface(JS_INTERFACE)
      webView.removeBaseBridge()
      webView.setJsLifeCycleListener(null)
      chatBinding.qmsChatContainer.removeView(webView)
      webView.destroy()
  }
  webView = ExtendedWebView(requireContext()).also { … it.init(TRUSTED_LOCAL_TEMPLATE); it.enableBaseBridge() }
  webView.setJsLifeCycleListener(this)
  webView.addJavascriptInterface(jsInterface, JS_INTERFACE)
  ```
- **Description:** `jsInterface` is **re-registered with the same `JS_INTERFACE` name** on a brand-new WebView. `BaseJsInterface` (a parent of `QmsChatJsInterface`) extends a `MainScope`; old launch jobs from the previous bridge can fire callbacks into the *old* `jsInterface` after the new one is registered. The old bridge's `presenter` reference is now stale, leading to either `IllegalStateException` (fragment gone) or a race that drops a sent QMS message.
- **Recommended fix:** Call `jsInterface?.cancel()` (BaseJsInterface already exposes `cancel()`) before replacing the WebView; null out any reference the old `QmsChatJsInterface` holds to the presenter.
- **Effort:** S.

### <a id="a-03"></a>A-03 (HIGH) — `runBlocking` on the OkHttp dispatcher / caller's thread (5 sites)

- **Files:**
  - `app/src/main/java/forpdateam/ru/forpda/common/ForPdaCoil.kt:215,240`
  - `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt:62`
  - `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeApi.kt:341`
  - `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt:40,48`
- **Description:** `runBlocking(Dispatchers.IO) { … }` from a coroutine context is a smell; `runBlocking` from a non-coroutine caller blocks the current thread. `ForPdaCoil.loadBitmapSync` is intentionally synchronous (used from `shouldInterceptRequest`), but `NewsApi.kt:62` is on a coroutine path.
- **Impact:** When called from a non-coroutine context (the WebView intercept on a binder thread) it works; called from a coroutine on `Dispatchers.Main` it can deadlock if the inner scope is also `Main`.
- **Recommended fix:** Convert each to a `suspend` function. For `ForPdaCoil` and `AvatarRepository` sync variants, keep the sync version but document that they are only safe off the main thread (`Looper.myLooper() != Looper.getMainLooper()`).
- **Effort:** M.

### <a id="p-04"></a>P-04 (HIGH) — `CookieManager.initializeCookies` runs on the Hilt-singleton construction thread

- **File:** `app/src/main/java/forpdateam/ru/forpda/client/CookieManager.kt:43-81`
- **Evidence:** `init` block calls `SecureCookiesPreferences.getInstance(context)` (first-time AES-256-GCM key unwrap, ~30-200 ms on first run) and then synchronously reads/writes default SharedPreferences for each cookie. This runs on whatever thread constructs `Client`; in Hilt, that is the main thread.
- **Impact:** First-launch ANR risk on slow devices. Visible in the cold-start path (`App.onCreate` → `setupCoil` → `ForPdaCoil.init` → `webClient.getHttpClient()`).
- **Recommended fix:** Make `CookieManager` lazy; load cookies on the first `request` (off main) and keep an in-memory snapshot for the main thread.
- **Effort:** M.
### Summary of Medium / Low / Info findings (one line each)

The 44 remaining findings are summarized below. Anchor IDs use the form `#finding-id` so they can be linked from the Top 10 actions.

**Security**

- **S-04 (Medium)** — Hardcoded AppMetrica API key in `App.kt:263`; gate is a single string comparison. Move key to `local.properties` / `BuildConfig` (see `BuildConfig.APPMETRICA_API_KEY`).
- **S-05 (Medium)** — `ArticleContentFragment.openExternalBrowser(url)` and `openImage(url)` `@JavascriptInterface` methods bypass `UrlPolicy.classify`; defense in depth missing. (Cross-ref `PROPDA_FINAL_CODE_AUDIT_REPORT.md §M4`.)
- **S-06 (Info)** — `ExtendedWebView` constructor path is reflection-free; no action required.
- **S-07 (Low)** — `mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE` should be `MIXED_CONTENT_NEVER_ALLOW` (`ExtendedWebView.kt:151`).
- **S-08 (Info)** — `DownloadsService` is correctly `dataSync` and `exported=false`; OK.

**Crash risk**

- **C-01 (Medium)** — `searchBlankVerifyRunnable!!` in `postDelayed` (`SearchFragment.kt:633`). Use `?.let { webView.postDelayed(it, 48L) }`.
- **C-02 (Medium)** — 22 consecutive `!!` on `ViewBinding.inflate` results (`MessagePanel.kt:137-161`); 27 `!!` total in the file. Refactor with sealed `Mode { Full, Quick }`.
- **C-03 (Medium)** — `downloadFile()` NPEs if `systemLinkHandler` is null (`CustomWebViewClient.kt:288`). Make the constructor non-nullable.
- **C-05 (Medium)** — `NotificationsService.IncomingHandler` does `Toast` from a `Service` without try/catch (`NotificationsService.kt:547-555`); `BadTokenException` risk on Android 11+. Wrap and consider removing the dead `Messenger` path.
- **C-06 (Low)** — `ThemePostedPageScrollPolicy.maxByOrNull{...}!!id` and `minByOrNull{...}!!id` NPE on empty list (`ThemePostedPageScrollPolicy.kt:27-28`).
- **C-07 (Low)** — `ThemeHistoryController.history.last()`/`.first()` after `.filter` (`ThemeHistoryController.kt:101,138,169`); same pattern as C-06.
- **C-09 (Info)** — `StrictMode.ThreadPolicy.detectAll()` is debug-only (`App.kt:220-235`); release ships unobserved. Intentional but documented.

**Architecture**

- **A-01 (Medium)** — `MainScope()` leaks at 3 sites: `TabRouter.kt:16`, `ThemeDialogsHelper_V2.kt:43`, `CodesPanelItem.kt:41`. Replace with `lifecycleScope` / `viewModelScope` or expose `dispose()`.
- **A-02 (Medium)** — `GlobalScope.launch(Dispatchers.IO)` in `ThemeUseCase.kt:326`; un-cancellable. Use an injected application-scoped scope.
- **A-04 (Low)** — `ThemeViewModel` is a god-class (>4000 lines); see `REFACTOR_PLAN.md §1.1`. Already documented.
- **A-05 (Low)** — `AppDatabase` is monolithic; 10 entity types from 6 domains. Splitting is a small refactor.
- **A-06 (Info)** — All parsers share `IPatternProvider`; a single pattern regression can break 14 parsers. No action required.

**Performance**

- **P-01 (Medium)** — OkHttp HTTP cache is 10 MB (`Client.kt:111-112,124-129`); no `Cache-Control: max-age`. Raise to ~50 MB; add an interceptor.
- **P-02 (Medium)** — `ImageLoadingInterceptor` does `Thread.sleep(300)` on the OkHttp dispatcher for 504/503 retry (`ImageLoadingInterceptor.kt:72`). Replace with off-thread retry.
- **P-03 (Low)** — Article phase-2 always scheduled (`ArticleInteractor.kt:270`); see `PERF_DIAGNOSIS §5.6`. Already documented.
- **P-05 (Info)** — `ForPdaCoil` disk cache 128 MB; memory cache 25% of heap. OK.
- **P-06 (Low)** — `CachedDns` TTL is 30 s; `clearCache()` not called on network changes (`CachedDns.kt:20,61`).
- **P-07 (Low)** — `WebSocketController` parses messages on dispatcher thread (`WebSocketController.kt:21-118`).

**Build / dependency / manifest**

- **M-01 (Medium)** — `allowBackup="true"` + `fullBackupContent="false"` (`AndroidManifest.xml:49-50`); should be `dataExtractionRules` excluding the encrypted cookie file and the auth_key. See Action 6.
- **M-02 (Low)** — `MainActivity` `exported="true"` with `http(s)://4pda.to*` deep-link (`AndroidManifest.xml:59-98`); intentional share-target.
- **M-03 (Low)** — Stale `versionCode=349` in manifest vs `350` in `app/build.gradle:67`. Update manifest.
- **M-04 (Info)** — `WakeUpReceiver` `exported="true"` is permission-gated by `RECEIVE_BOOT_COMPLETED`; safe.
- **D-01..D-10 (Info/Low/Medium)** — Dependency hygiene. `D-03` (`androidx.security:security-crypto:1.1.0-alpha06`) and `D-04` (`minitemplator 1.2` JCenter repackaged) are Medium; `D-05`, `D-07`, `D-09` are Low; the rest are Info (no known CVE).
- **D-11 (Info)** — `unitTests.includeAndroidResources = true` is missing; see `docs/test-fixes-plan.md`.
- **T-01 (Medium)** — `detekt-baseline.xml` is 484 KB; combined with the blanket disables in `detekt.yml`, detekt catches nothing new. See Action 9.
- **T-02 (Info)** — `assembleStoreRelease` is intentionally blocked without `keystore.properties`; good.
- **T-03 (Info)** — ProGuard `-keepnames class forpdateam.ru.forpda.entity.db.** { *; }` is correct.
- **T-04 (Low)** — ProGuard rule `-keep class forpdateam.ru.forpda.App { public <init>(); }` (`proguard-rules.pro:24-26`) is redundant with Hilt; remove.

**UX**

- **X-01 (Info)** — `AuthApi.kt:42-43` sends `nick`/`password` as CP1251; required by 4pda's forum auth. No client-side password strength check; no "show password" toggle.
- **X-02 (Info)** — No `logout` in `AuthViewModel`; only `onClickSkip` visible. `AuthApi.logout` is called from the menu.
- **X-03 (Info)** — Captcha is loaded via `GoogleCaptchaFragment` (full-screen WebView); OK.
- **X-04 (Low)** — `textZoom = 100` is the first-paint default (`ExtendedWebView.kt:133,154`); the user's font scale is applied after. First frame is at 100%.
- **X-05 (Low)** — No per-activity edge-to-edge; the manifest opts into predictive back (`AndroidManifest.xml:57`) but no `enableEdgeToEdge()` call. Required for Android 15+ (`targetSdk=35`).

---

## 6. Crash Risk Hotspots

This is the cross-cutting summary of the conditions under which the app can crash. Each hotspot has been documented in §5 in detail; this section is the at-a-glance triage.

| #  | Hotspot                                                                 | File:line                                          | Trigger                                                                                            | Likely outcome                          |
| -- | ----------------------------------------------------------------------- | -------------------------------------------------- | -------------------------------------------------------------------------------------------------- | --------------------------------------- |
| 1  | `ThemeRenderGuard` not integrated with destructive bridge methods        | `presentation/theme/ThemeJsInterface.kt`           | Template injection / XSS / out-of-band re-render                                                   | Native destructive action / data loss   |
| 2  | `QmsChatFragment` WebView re-creation with stale bridge                 | `ui/fragments/qms/chat/QmsChatFragment.kt:2400-2421` | Screen rotation, process restore, fast re-open                                                       | Lost QMS message or `IllegalStateException` |
| 3  | `CookieManager.initializeCookies` on main thread                         | `client/CookieManager.kt:43-81`                    | First launch on slow device                                                                         | Cold-start ANR                          |
| 4  | `AvatarRepository.getAvatar(nick)` throws NPE on unknown nick            | `model/repository/avatar/AvatarRepository.kt:24-37` | QMS event from unknown user                                                                          | Crash on notification path (mitigated by runCatching) |
| 5  | `MessagePanel` 22× `!!` cascade on `ViewBinding.inflate`                 | `ui/views/messagepanel/MessagePanel.kt:137-161`    | Refactor that returns null in either binding                                                         | NPE on first message panel open         |
| 6  | `searchBlankVerifyRunnable!!` in `postDelayed`                           | `ui/fragments/search/SearchFragment.kt:633`        | Race between scheduling and prior runnable being nulled                                              | NPE in search render                    |
| 7  | `CustomWebViewClient.downloadFile()` `systemLinkHandler!!`                | `common/webview/CustomWebViewClient.kt:288`        | Future code path that invokes handleUri without DownloadListener set                                  | NPE                                     |
| 8  | `NotificationsService.IncomingHandler` `Toast` from `Service`            | `notifications/NotificationsService.kt:547-555`    | `BadTokenException` on Android 11+                                                                  | Crash (currently unreachable)           |
| 9  | `ThemePostedPageScrollPolicy.maxByOrNull{}!!.id`                          | `presentation/theme/ThemePostedPageScrollPolicy.kt:27-28` | Helper called on empty list                                                                        | NPE                                     |
| 10 | `ThemeHistoryController.history.last()` after `.filter`                   | `presentation/theme/ThemeHistoryController.kt:101,138,169` | Filter returns empty list                                                                          | `NoSuchElementException`                 |
| 11 | `App.instance!!` getter                                                    | `App.kt:103` + 6+ callers                          | Robolectric test that resets `_instance`                                                            | NPE in unit tests                       |
| 12 | `view.postDelayed(..., 350)` in `EditPostFragment` with no cancel          | `ui/fragments/editpost/EditPostFragment.kt:471`   | Activity destroyed before delay fires; runnable captures detached `view`                             | Leaked Runnable; `IllegalStateException` on view access |
| 13 | `messageField.postDelayed(…, …)` in `MessagePanel` (10+ sites)            | `ui/views/messagepanel/MessagePanel.kt:332,608,613,618` | Activity destroyed before delay fires; handler.removeCallbacksAndMessages(null) does help on fullPanel but not on messageField  | Stale `Editable` mutation |
| 14 | `ArticleContentFragment.pendingExpandWatchdogRunnable!!`                  | `ui/fragments/news/details/ArticleContentFragment.kt:2111` | Race during fast article open/close                                                                | NPE                                     |
| 15 | `runBlocking(Dispatchers.IO)` from `Dispatchers.Main` (5 sites)           | `common/ForPdaCoil.kt:215,240`, `NewsApi.kt:62`, `ThemeApi.kt:341`, `AvatarRepository.kt:40,48` | Suspending coroutine launched on main and immediately runBlocking                                  | Potential deadlock under load           |
| 16 | `SecureCookiesPreferences` `instance` is `@Volatile` not `@GuardedBy`     | `common/SecureCookiesPreferences.kt:101-110`       | `getInstance` from multiple threads in tight loop                                                   | Benign DCL (already correct)            |
| 17 | `ArticleContentFragment` registers `this` as JS interface, then on reset re-registers before bridge is removed | `ui/fragments/news/details/ArticleContentFragment.kt:1124-2952` | Article reload while previous WebView still attached                                               | Mixed bridge / lost callback            |
| 18 | `forpdateam.ru.forpda.client.CachedDns` keeps `cache[hostname]` forever if `addresses.isEmpty()` | `client/CachedDns.kt:42` | NXDOMAIN response gets cached for 30 s with empty list                                              | Stale empty result                      |
| 19 | `WebSocketController.connect()` generates a new id `1000..16384` on every reconnect — high collision in long sessions | `client/WebSocketController.kt:77` | Long session, frequent reconnects                                                                  | `currentId` collisions in `webSockets` list |
| 20 | `ImageLoadingInterceptor.closeQuietly()` catches `Throwable`               | `client/interceptors/ImageLoadingInterceptor.kt:96-101` | `Response.close()` fails mid-retry; logged at no level                                              | Lost error context                      |

---

## 7. Security Findings (consolidated)

The application is a forum client; the main security boundary is the WebView <-> JS bridge and the auth-cookie / network surface. The following is the consolidated security picture, cross-referenced with `PROPDA_FINAL_CODE_AUDIT_REPORT.md`, `JS_BRIDGE_INVENTORY.md`, and `PROPDA_HARDENING_REPORT.md`.

**Secrets and config**

- **S-04 (Medium) Hardcoded AppMetrica API key** — `App.kt:263`. Single string literal; gate by `flavor == "store"`; should move to `local.properties` / Gradle property (see Action 7).
- **`keystore.parallel.properties` is in `.gitignore`** (line 7). The file on disk contains real passwords, which is correct local behavior. No `keystore.properties` is tracked.
- No additional hardcoded API keys were found. No `Basic ` / `Bearer ` tokens in source.

**Network and TLS**

- TLS configuration is sane. `Client.kt:114-130` builds `OkHttpClient` with `Protocol.HTTP_2, HTTP_1_1`, a `ConnectionPool`, Brotli, and a `CachedDns`. There is no custom `SSLSocketFactory`, no `trustAllCerts`, no `HostnameVerifier` override, no `sslSocketFactory(...)`.
- `networkSecurityConfig` is correct. `app/src/main/res/xml/network_security_config.xml` sets `cleartextTrafficPermitted="false"` and only allows the system trust store. No `<trust-anchors>` for user-installed CAs.
- **M-01 (Medium)** — `allowBackup="true"` + `fullBackupContent="false"`; see Action 6.

**Storage**

- `SecureCookiesPreferences` (line 1-112) uses `MasterKey.KeyScheme.AES256_GCM` and `EncryptedSharedPreferences` with `AES256_SIV` keys and `AES256_GCM` values. Migration from legacy default prefs is one-shot (line 39-77) and idempotent (the `cookies_migrated_to_encrypted` flag).
- The auth_key is stored in plain `SharedPreferences` (`AuthApi.checkLogin`, line 76). Acceptable for a 4pda auth key; documenting the trade-off is sufficient.
- Room database uses no SQLCipher; the database file is in `databases/forpda_database` unencrypted. Acceptable for this app.

**WebView**

- JS bridge inventory — see `docs/JS_BRIDGE_INVENTORY.md`: 7 active `addJavascriptInterface` registrations (5 `this`-as-Fragment, 1 `jsInterface`, 1 `themeViewBridge`); 52 exported `@JavascriptInterface` methods. Highest-risk surface is `IThemePresenter` (theme destructive actions).
- WebView file/content access is disabled. `ExtendedWebView.init` sets `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false` (lines 147-150).
- `UrlPolicy` blocks `javascript:`, `file:`, `data:`, `content:`, `about:`, `app_cache:` schemes before WebView navigation or external intent dispatch (`common/webview/UrlPolicy.kt:33-38`). Control chars, percent-encoded control chars, and percent-encoded dangerous scheme prefixes are also rejected.
- **S-01 (Critical)** — Theme destructive methods have no render-token guard (Action 1).
- **S-05 (Medium)** — News `openExternalBrowser` / `openImage` should re-validate via `UrlPolicy`.
- **S-07 (Low)** — `MIXED_CONTENT_COMPATIBILITY_MODE` allows some forms of mixed content; should be `MIXED_CONTENT_NEVER_ALLOW` (Action 6).

**AndroidManifest**

- `MainActivity` is `exported="true"` with a deep-link intent filter for `http(s)://4pda.to*` and `www.4pda.to` (lines 66-90). Intentional (share-target) and the URL goes through `MainActivity`'s `onNewIntent` → cicerone → `UrlPolicy` at the eventual navigation. Filter exhaustively covers `4pda.to` and `www.4pda.to` for both `http` and `https`.
- `NotificationsService` is `exported="false"` with `foregroundServiceType="dataSync"` (line 124-127). Good.
- `WakeUpReceiver` is `exported="true"` but protected by `android:permission="android.permission.RECEIVE_BOOT_COMPLETED"` (line 160-161). The system sends `BOOT_COMPLETED` only to receivers holding this permission, so this is safe.

**Dependencies**

- **D-03 (Medium)** — `androidx.security:security-crypto:1.1.0-alpha06`. The `app/build.gradle:296` FIXME note acknowledges the alpha.
- **D-04 (Medium)** — `minitemplator 1.2` (JCenter repackaged).
- **D-07 (Low)** — `tagsoup 1.2.1` (very old; no CVE).
- No known CVEs in `okhttp 4.12.0`, `coil 2.7.0`, `jsoup 1.18.3`, `coroutines 1.8.1`, `material 1.12.0`.

---

## 8. Performance Findings (consolidated)

The codebase has been profiled and documented extensively in `PERF_DIAGNOSIS.md`. The findings below are *new* (not in PERF_DIAGNOSIS) or cross-cutting:

**Memory**

- `ForPdaCoil` memory cache is 25% of process heap (`common/ForPdaCoil.kt:73-78`). Right ballpark for an image-heavy app. The disk cache is 128 MB. The `LruCache` for `AvatarRepository` is 512 entries. Memory pressure should be OK on most devices, but a heavy theme with 200+ images will hit the cache repeatedly.
- `WebView` instances are kept alive longer than necessary. `ThemeFragmentWeb` keeps the WebView across the fragment lifecycle; the WebView's bitmap cache is large. The hardening report already covered the post-destroy state.
- `AppDatabase` SQLite connection pool: not explicitly tuned. Default is fine.

**CPU**

- `runBlocking` on dispatcher threads (5 sites) — see A-03 / Action 2. The biggest CPU win is converting these to `suspend` functions.
- `Jsoup` parsing of forum HTML is done on `Dispatchers.Default` (per `ArticleInteractor.kt:825-827`); theme parsing in `ThemeApi` is sync.
- Cascading `postDelayed` chains in `ArticleContentFragment` and `QmsChatFragment` (60+ sites total) — see §6 hotspots 12-14. These add up on rapid navigation.

**Battery / Network**

- Per the `scripts/battery-audit.sh` README, the expected wake-locks are `WebSocketController` (when foreground realtime is on) and `DownloadWorker` (during downloads). Everything else is a bug.
- No `AlarmManager` usage in the app. All background work goes through `WorkManager`.
- DNS cache TTL is 30 s (`CachedDns.kt:20`). Reasonable for mobile; see P-06 / Action 10 for the missed clear-on-network-change.
- `ArticleContentFragment` always runs the deferred-extras phase-2 even when there's nothing to load (`PERF_DIAGNOSIS §5.6`). Already documented.

**WebView**

- Image intercept on `shouldInterceptRequest` (`CustomWebViewClient.kt:82-181`) does sync disk reads and PNG encoding on the binder thread. Documented in `docs/WEBVIEW_INTERCEPT_PROFILING.md` and partly mitigated by the `AVATAR_RESPONSE_MAX_ENTRY_BYTES = 256KB` LRU.
- No `setOffscreenPreRender(false)` is called — the default is `false` on Android 5+, so the WebView does not pre-render off-screen. OK.

---

## 9. Architecture Findings (consolidated)

**Coupling and layering**

- `NetworkModule` is 91 lines and provides 13 `*Api` `@Singleton`s and 14 `*Parser` `@Singleton`s. Every new endpoint adds two more lines. The cross-cutting concern is that every API also takes the full `IWebClient` and the parser, so a misconfiguration in either will silently fall through to the network. (See `di/NetworkModule.kt`.)
- All parsers extend a shared `IPatternProvider` — see `model/data/storage/IPatternProvider.kt`. Patterns are matched against forum HTML; the provider is `@Singleton` and shared across 14 parsers. Good DRY but a single pattern regression can break all of them.
- `ThemeViewModel` is >4000 lines — see `REFACTOR_PLAN.md §1.1`. Splitting it is the highest-impact architecture change.
- `AppDatabase` is monolithic — 10 entity types from 6 different domains. Splitting into 2-3 databases is a small refactor that would let users clear specific caches.

**Coroutines and lifecycle**

- 3 `MainScope()` leaks — see A-01.
- 1 `GlobalScope.launch` — see A-02.
- 5 `runBlocking` sites — see A-03 / Action 2.
- `StrictMode.ThreadPolicy.Builder().detectAll()` is debug-only (`App.kt:220-235`). In release, no thread-policy enforcement. Correct for production but means there is no early warning for accidental disk I/O on main thread in release.

**Duplicated logic**

- `getCounts` parsing in `Client.kt:371-406` and the per-source "fallback patterns" (`IWebClient.mentionsCountPattern`, `favoritesCountPattern`, `qmsCountPattern`) are duplicated across `getCounts`, `EventsRepository`, and `MentionsRepository`. Consolidating into a `HeaderCountersParser` would reduce the count of patterns from 3 to 1.
- HTML-entity decoding uses both `Html.fromHtml` and `ApiUtils.fromHtml` and `NickEncoder.decode`; the three should be merged or clearly bounded.
- Stale-cookie cleanup is implemented twice: in `CookieManager.removeStaleNullCookies` (line 174-186) and in the `saveFromResponse` path (line 90-94).

**Configuration and DI**

- `provideOfflineOkHttpClient` in `DataModule.kt:177-181` is independent of the main `Client`. The comment notes the trade-off: image downloads are public, no auth_key. OK.
- All `*Repository` constructors are concrete classes, not interfaces. Hilt binds the concrete type. The benefit: no interface indirection. The cost: harder to test in isolation, harder to swap implementations.

---

## 10. UX Findings (consolidated)

**Login flow**

- CP1251 legacy encoding — see X-01. The auth flow does not communicate that the user is on a legacy CP1251 endpoint; the password field does not toggle visibility.
- No "logout" visible in `AuthViewModel`. The model exposes only `onClickSkip`. A "Logout" call goes through `AuthApi.logout` (used by the menu).
- Captcha is loaded in an external browser / `GoogleCaptchaFragment`, which is a full-screen WebView. There is no in-app fall-back if the captcha is blocked.

**WebView / theme**

- First-paint font scale is `textZoom = 100`, not user font scale — see X-04. The user can change font size in the system, but the WebView re-initializes with 100 before applying the scale. The first frame of a fresh WebView is at 100%.
- `WebViewChecker` and `WebVewNotFoundActivity` exist for devices without a working WebView — confirmed at `AndroidManifest.xml:118-122`. The class name has a typo (`WebVewNotFoundActivity`). Low priority but worth fixing.

**Notifications**

- Notification stack is built per event with no rate-limiting — `NotificationsService.sendNotifications` (line 330-379) processes up to 4 events and posts one. Per-event rate limiting is in the user prefs, but the per-second rate is not capped, so a flood of events can post 4-stack notifications back-to-back. A real 4pda spam event can create a "notification storm" until the user kills the app.
- Channel `forpda_foreground_service` is created with `IMPORTANCE_MIN` but is visible in app settings. This is intentional but users can disable it; if they do, the foreground service is killed on Android 14+.

**Edge-to-edge / back**

- `enableOnBackInvokedCallback="true"` is set in the manifest but no per-activity migration. Android 14+ predictive back may behave inconsistently. The fork README claims keyboard/BBCode fixes for Android 15/16; verify by device QA. (See `docs/navigation-scroll-fixes-plan.md`.)

**Accessibility**

- No `contentDescription` audits were done in this pass — the existing `docs/ACCESSIBILITY_CHECKLIST.md` covers this.
- The message panel's advanced buttons (BBCode toolbar) are icon-only and have `contentDescription` in the layout, but font scale 1.5 may overlap.

---

## 11. Implementation Roadmap (4 sprints)

The 10 prioritized actions are grouped into four 1-week sprints. The "this week" sprint focuses on the highest-blast-radius items with the lowest effort; later sprints absorb the broader cleanups.

### Sprint 1 — This week (Actions 1, 4, 5, 10)

- **Scope:** Add the render-token guard on `ThemeJsInterface` (Action 1); fix `QmsChatFragment` WebView re-creation (Action 4); replace `searchBlankVerifyRunnable!!` and the 22 `!!` cascade in `MessagePanel` (Action 5); clear `CachedDns` on network change and cache `OfflineArticleSource` URL validation (Action 10).
- **Acceptance criteria:**
  - `rg "@JavascriptInterface" ThemeJsInterface.kt` shows every destructive method gated by `ThemeRenderGuard`; unit tests for missing/stale/wrong token pass.
  - `rg "searchBlankVerifyRunnable!!" SearchFragment.kt` returns zero; `rg "!!" MessagePanel.kt` ≤ 5.
  - `QmsChatFragment` rotation test (open QMS → rotate device → re-open) passes 100/100 without an `IllegalStateException` or `presenter is null` crash.
  - `CachedDns.clearCache()` is invoked from a `ConnectivityManager.NetworkCallback` in `App.setupNetworkTracking`; `OfflineArticleSource` has a `LruCache<String, ValidationResult>(256)` for `UrlPolicy.classify` results.
  - CI gate: `./gradlew :app:testStoreDebugUnitTest` is green.

### Sprint 2 — A-03 runBlocking + P-04 cookie off-main (Actions 2, 3)

- **Scope:** Convert `AvatarRepository`, `ForPdaCoil`, `NewsApi`, `ThemeApi` to remove `runBlocking` (Action 2); make `CookieManager.initializeCookies` lazy and off the main thread (Action 3).
- **Acceptance criteria:**
  - `rg "runBlocking" app/src/main` returns zero matches.
  - `ForPdaCoil`, `NewsApi`, `ThemeApi`, `AvatarRepository` end-to-end are `suspend` functions; the only `loadBitmapBlocking` shim is `@WorkerThread` and explicitly refuses to run on `Looper.getMainLooper()`.
  - `AvatarRepository.getAvatar(nick)` returns `String?`; the `AvatarNotFoundException` variant is opt-in via a separate method.
  - `CookieManager` first-touch is on `Dispatchers.IO`; main thread never reads from disk (verified with `StrictMode.detectDiskReads().penaltyLog()` on a fresh launch).
  - Cold-start path `App.onCreate` → first authenticated request is observably off-main.
  - All call sites audited; CI grep gate fails on new `runBlocking` in `app/src/main`.

### Sprint 3 — Manifest hardening + secrets (Actions 6, 7)

- **Scope:** Add `dataExtractionRules` excluding the encrypted cookie file and `auth_key`; switch `mixedContentMode` to `MIXED_CONTENT_NEVER_ALLOW` (Action 6); move the AppMetrica API key to `BuildConfig` (Action 7).
- **Acceptance criteria:**
  - `AndroidManifest.xml` has both `fullBackupContent` and `dataExtractionRules`; `aapt dump xmltree` confirms the rules exclude `secure_cookies_prefs.xml` and `auth_key`.
  - `bmgr backupnow` on a debug build produces a backup that does not contain the encrypted cookie file or `auth_key`.
  - `rg "a94d9236" app/src` returns zero matches; `BuildConfig.APPMETRICA_API_KEY` is the only source of the key.
  - `assembleStoreRelease` fails fast when `APPMETRICA_API_KEY` is missing.
  - All WebView screens render without mixed-content warnings in logcat; `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.

### Sprint 4 — Tests + detekt cleanup (Actions 8, 9)

- **Scope:** Run the focused Search/media tests in green (Action 8); cut `detekt-baseline.xml` to a small, hand-curated set (Action 9).
- **Acceptance criteria:**
  - `./gradlew :app:testStoreDebugUnitTest` and `./gradlew :app:testDevDebugUnitTest` are green; coverage report shows ≥ 60% line coverage in `ThemeViewModel`, `AvatarRepository`, `CookieManager`.
  - `unitTests.includeAndroidResources = true` is enabled; `D-11` is closed.
  - `detekt-baseline.xml` is < 20 KB; high-signal rules (`ComplexMethod`, `LongMethod`, `LongParameterList`, `NestedBlockDepth`, `TooManyFunctions`) are enabled.
  - `./gradlew detekt` runs in CI and gates merges; the high-signal rule set catches ≥ 5 real issues in the 30 days after rollout.
  - The team has committed to a 2-week ramp for the detekt rollout; long-tail failures are tracked in `docs/AI_REGRESSION_CHECKLIST.md` rather than re-baselined.

---

## 12. Appendix

### A. Methodology

- Read top-level build files (`build.gradle`, `settings.gradle`, `app/build.gradle`, `gradle.properties`, `gradle/libs.versions.toml`).
- Read prior existing artifacts: `PERF_DIAGNOSIS.md`, `REFACTOR_PLAN.md`, `docs/PROPDA_FINAL_CODE_AUDIT_REPORT.md`, `docs/PROPDA_HARDENING_REPORT.md`, `docs/JS_BRIDGE_INVENTORY.md`, `docs/QMS_BRIDGE_INVENTORY.md`, `docs/READER_MODE_INVENTORY.md`, `docs/POST_ACTIONS_INVENTORY.md`, `docs/UI_THREAD_RENDERING_INVENTORY.md`, `docs/WEBVIEW_INTERCEPT_PROFILING.md`. Findings already in those are cross-referenced, not duplicated.
- Read full source for: `client/Client.kt`, `client/CookieManager.kt`, `client/WebSocketController.kt`, `client/interceptors/AuthInterceptor.kt`, `client/interceptors/ErrorInterceptor.kt`, `client/interceptors/ImageLoadingInterceptor.kt`, `client/interceptors/RedirectFragmentInterceptor.kt`, `client/CachedDns.kt`, `di/NetworkModule.kt`, `di/DataModule.kt`, `common/webview/CustomWebViewClient.kt`, `common/webview/UrlPolicy.kt`, `common/webview/DialogsHelper.kt`, `common/SecureCookiesPreferences.kt`, `common/Preferences.kt`, `common/SharedPreferencesFlow.kt`, `common/ForPdaCoil.kt`, `common/receivers/WakeUpReceiver.kt`, `ui/views/ExtendedWebView.kt`, `App.kt`, `notifications/NotificationsService.kt`, `notifications/EventsCheckWorker.kt`, `presentation/auth/AuthViewModel.kt`, `model/repository/avatar/AvatarRepository.kt`, `model/data/remote/api/auth/AuthApi.kt`, `entity/db/notes/AppDatabase.kt`, `AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`, `app/proguard-rules.pro`, `app/build.gradle`.
- Code-targeted greps via the `Grep` tool with patterns: `!!`, `addJavascriptInterface`, `setJavaScriptEnabled`, `setAllowFileAccess`, `setAllowContentAccess`, `setMixedContentMode`, `setAllowFileAccessFromFileURLs`, `setAllowUniversalAccessFromFileURLs`, `GlobalScope`, `MainScope()`, `runBlocking`, `runOnUiThread`, `loadUrl(`, `loadDataWithBaseURL(`, `Html.fromHtml`, `MD5`, `MessageDigest.getInstance`, `loadLibrary`, `Runtime.exec`, `postDelayed`, `removeCallbacks`, `exported.*true`, `usesCleartextTraffic`, `networkSecurityConfig`, `a94d9236` (AppMetrica key).
- No gradle build was run (per constraints). No destructive commands.

### B. Files reviewed (alphabetical, partial)

- `app/build.gradle` (370 lines)
- `app/proguard-rules.pro` (100 lines)
- `app/src/main/AndroidManifest.xml` (169 lines)
- `app/src/main/java/forpdateam/ru/forpda/App.kt` (425+ lines)
- `app/src/main/java/forpdateam/ru/forpda/client/CachedDns.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/Client.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/CookieManager.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/WebSocketController.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/interceptors/AuthInterceptor.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/interceptors/ErrorInterceptor.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/interceptors/ImageLoadingInterceptor.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/interceptors/RedirectFragmentInterceptor.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/ForPdaCoil.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/SecureCookiesPreferences.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/SharedPreferencesFlow.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/receivers/WakeUpReceiver.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/webview/DialogsHelper.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/webview/UrlPolicy.kt`
- `app/src/main/java/forpdateam/ru/forpda/di/DataModule.kt`
- `app/src/main/java/forpdateam/ru/forpda/di/NetworkModule.kt`
- `app/src/main/java/forpdateam/ru/forpda/entity/db/notes/AppDatabase.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/auth/AuthApi.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt`
- `app/src/main/java/forpdateam/ru/forpda/notifications/EventsCheckWorker.kt`
- `app/src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/auth/AuthViewModel.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` (partial, 300 lines)
- `app/src/main/res/xml/network_security_config.xml`
- `gradle/libs.versions.toml`
- `detekt.yml`, `detekt-baseline.xml` (size only)
- `keystore.parallel.properties` (read for secret-leak audit; confirmed gitignored)

### C. Tools used

- `Read` (file inspection)
- `Grep` (pattern scans via ripgrep)
- `Glob` (file discovery)
- `Shell` (limited to `ls`, `git ls-files`, `wc`)
- `explore` subagent (initial broad sweep — partial output recovered; the second pass resumed directly)
- No `WebFetch`, no external tools.

### D. Cross-references to prior artifacts

- `PROPDA_FINAL_CODE_AUDIT_REPORT.md` — covers release-blocking tests, ThemeRenderGuard, parser NPE in tests, theme WebView lifecycle, Mentions state overlay, ArticleContentFragment external browser path. This audit references §H1, §H2, §H3, §H4, §M1, §M3, §M4, §S1, §S2, §U1, §U2 from that document.
- `PERF_DIAGNOSIS.md` — article/QMS pipeline bottlenecks, low-risk micro-optimizations. This audit's §3.3 / §6 references §5.6 (phase-2 schedule), §5.10 (double awaitWarm), §5.5 (refetch retry).
- `REFACTOR_PLAN.md` — Kotlin 2.0 migration, Jsoup migration, god-class split, version catalog cleanup. This audit's §7 references §1.1 (ThemeViewModel split) and §1.2 (dead Realm).
- `PROPDA_HARDENING_REPORT.md` — ThemeFragmentWeb lifecycle, JS runtime, scroll restore. This audit does not duplicate those findings.
- `JS_BRIDGE_INVENTORY.md` / `QMS_BRIDGE_INVENTORY.md` / `READER_MODE_INVENTORY.md` / `POST_ACTIONS_INVENTORY.md` / `UI_THREAD_RENDERING_INVENTORY.md` / `WEBVIEW_INTERCEPT_PROFILING.md` — per-bridge/per-component inventories. This audit cites them for context.

### E. Counts

- **Total findings:** 51 (in §3 table)
- **By severity (per §3 table):** Critical: 1, High: 6, Medium: 18, Low: 15, Info: 11. (The original `docs/AUDIT_REPORT.md §10.E` reported 1 / 8 / 18 / 15 / 9; re-counting the table yields 6 High and 11 Info. This document uses the table-derived count.)
- **`!!` non-null assertions in `app/src/main`:** 152 across 37 files
- **Empty / `_ -> {}` catch blocks:** 14
- **Catch blocks total:** ~250 (rough estimate from grep)
- **Active `addJavascriptInterface` registrations:** 7 (per `JS_BRIDGE_INVENTORY.md`)
- **`@JavascriptInterface` exported methods:** 52
- **`postDelayed` call sites:** ~30
- **`MainScope()` instances:** 3
- **`GlobalScope.launch` instances:** 1
- **`runBlocking` instances:** 7 (5 in `app/src/main`, 2 in test sources)
- **MD5 use sites:** 1 (`AttachmentsApi.kt:93` for upload checksum, not a security issue)
- **SHA-256 use sites:** 4 (all for diagnostics, not crypto secrets)
- **Hardcoded secrets found in source:** 1 (AppMetrica key in `App.kt:263`)
- **Exported manifest components:** 2 (`MainActivity`, `WakeUpReceiver` — both intentional and properly permission-gated)
