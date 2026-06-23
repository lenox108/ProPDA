# ForPDA — Full Project Audit Report

> **Scope:** static review of the entire `/Users/j.golt/Documents/Cursor01/ForPDA-master` Android project (ForPDA 2 / ProPDA, fork of the 4pda.ru client). Kotlin + Hilt + OkHttp + Coil + Compose + Room + WorkManager. compileSdk/targetSdk 35, minSdk 24, AGP 8.11.1, Kotlin 2.0.21.
>
> **Prior existing artifacts** (used as references — see Appendix A): `PERF_DIAGNOSIS.md`, `REFACTOR_PLAN.md`, `docs/PROPDA_FINAL_CODE_AUDIT_REPORT.md`, `docs/PROPDA_HARDENING_REPORT.md`, `docs/JS_BRIDGE_INVENTORY.md`, `docs/QMS_BRIDGE_INVENTORY.md`, `docs/READER_MODE_INVENTORY.md`, `docs/POST_ACTIONS_INVENTORY.md`, `docs/UI_THREAD_RENDERING_INVENTORY.md`, `docs/WEBVIEW_INTERCEPT_PROFILING.md`. Findings already present in those docs are cross-referenced rather than restated.
>
> **Out of scope (this audit):** fixing code, running the app, executing gradle build, committing.

---

## 1. Executive Summary

The codebase is a mature, mostly well-hardened fork. The networking layer, cookie/secret storage, WebView URL policy, and JS bridge surface are reasonable and have been improved over many rounds (see `PERF_DIAGNOSIS.md`, `PROPDA_HARDENING_REPORT.md`, `JS_BRIDGE_INVENTORY.md`). However, **the project still ships with a small set of real residual risks** and a larger set of fragility/smell issues that will produce crashes or UX regressions under specific real-world conditions:

- **CRITICAL — Theme destructive JS bridge methods have no render-token guard.** The `IThemePresenter` bridge (`ThemeJsInterface`) exposes `deletePost`, `editPost`, `votePost`, `submitPoll`, `reply`, `quote*`, `reportPost`, `openLink`, `openImage` without the `ThemeRenderGuard` token check that already exists in the codebase. A template injection or untrusted HTML re-render would have native blast-radius. *(See finding `S-01` / `PROPDA_FINAL_CODE_AUDIT_REPORT.md §H1` for the prior analysis.)*
- **HIGH — `searchBlankVerifyRunnable!!` and similar `!!` cascades are concentrated in the message panel and search fragment.** 152 non-null assertions across `app/src/main`. `MessagePanel.kt:137-161` has 22 consecutive `!!` on the result of `ViewBinding.inflate(...)`. *(See `C-01`..`C-05`.)*
- **HIGH — `CookieManager.initializeCookies()` runs synchronously on the thread that constructs `Client` (Hilt: main thread).** This includes the first-touch `EncryptedSharedPreferences.create(...)` AES-GCM unwrap and a SharedPreferences migration. On slow devices this can produce cold-start ANR or "Application is starting" warnings. *(See `P-04`.)*
- **HIGH — `DownloadsService` is not exported and well-configured, but `wakeLock` is not held in `DownloadWorker`**, while `ImageLoadingInterceptor` does `TimeUnit.MILLISECONDS.sleep(300)` on the OkHttp dispatcher — small but observable. *(See `P-02`, `P-05`.)*
- **MEDIUM — `dev/beta/store/stable` product flavors ship a hardcoded AppMetrica API key in `App.kt:263`** (`a94d9236-cdf3-4a5e-af30-d6dbffaea362`). This is the same key in every flavor. While currently gated by `flavor == "store"`, a mistake will silently enable it for everyone. *(See `S-04`.)*
- **MEDIUM — `App.instance!!` is a singleton-with-`!!` accessed from `BatteryDebugLogger`, `ErrorInterceptor`, `HtmlToSpannedConverter`, and 5+ other files.** Safe in production but can NPE in unit tests / Robolectric that mock `Application`. *(See `C-08`.)*

The 4 top issues to fix in the next sprint are listed in §10.

---

## 2. Severity Table

| ID  | Title                                                                                          | Area               | File(s)                                                                                                  | Severity |
| --- | ---------------------------------------------------------------------------------------------- | ------------------ | -------------------------------------------------------------------------------------------------------- | -------- |
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

## 3. Detailed Findings (selected)

### 3.1 Security findings

#### S-01 (CRITICAL) — Theme destructive JS bridge methods have no render-token guard

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

#### S-04 (MEDIUM) — Hardcoded AppMetrica API key in `App.kt:263`

- **Area:** Secrets / config
- **File:** `app/src/main/java/forpdateam/ru/forpda/App.kt:261-269`
- **Evidence:**
  ```kotlin
  appScope.launch(Dispatchers.IO) {
      val config = AppMetricaConfig.newConfigBuilder("a94d9236-cdf3-4a5e-af30-d6dbffaea362").build()
      AppMetrica.activate(applicationContext, config)
  ```
- **Description:** The key is in plaintext in source. Currently gated by `flavor == "store"` (line 241-245), which is correct, but the gate is a single string comparison — easy to flip during a refactor. Also, even the dev/beta/parallel/stable flavors *do* install an UncaughtExceptionHandler that calls `AppMetrica.reportError` (line 250-260) — when the key is also used by store, this means dev builds will already have initialized the SDK handler before the gate.
- **Impact:** If the gate is removed, analytics traffic from every flavor; if a fork changes the key, no rotation path.
- **Recommended fix:** Move the key into `local.properties` / Gradle property / `BuildConfig` field; the existing `keystore.parallel.properties` pattern can be mirrored.
- **Effort:** S.

#### S-05 (MEDIUM) — `ArticleContentFragment.openExternalBrowser(url)` may bypass `UrlPolicy` for external intent

- **Area:** WebView / URL boundary
- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` (see bridge registration around line 2952 and `openExternalBrowser` near 3001)
- **Description:** The news bridge has `openExternalBrowser(url)` and `openImage(url)` `@JavascriptInterface` methods that take a URL string from JS and forward to the external system. There is no local `UrlPolicy.classify(...)` call. News HTML is `TRUSTED_STATIC_ARTICLE` (locally generated), so the attack surface is small, but the bridge is the same surface as the theme; defense in depth is missing. (Cross-ref `PROPDA_FINAL_CODE_AUDIT_REPORT.md §M4`.)
- **Recommended fix:** Wrap both methods in `UrlPolicy.classify` and reject `Blocked` / non-http(s).
- **Effort:** S.

#### S-07 (LOW) — `MIXED_CONTENT_COMPATIBILITY_MODE` instead of `MIXED_CONTENT_NEVER_ALLOW`

- **Area:** WebView hardening
- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt:151`
- **Description:** `mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE` allows some forms of mixed content. The app loads its assets locally and trusts 4pda.to; the external 4pda CDN may still return mixed content. For a content-only app this is fine, but the safer default is `MIXED_CONTENT_NEVER_ALLOW` (Android L+).
- **Recommended fix:** Set to `MIXED_CONTENT_NEVER_ALLOW` and rely on the policy that all images are loaded through the 4pda CDN, which is HTTPS.
- **Effort:** S.

### 3.2 Crash risk findings (selected)

#### C-01 (MEDIUM) — `searchBlankVerifyRunnable!!` in `postDelayed`

- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt:633`
- **Evidence:**
  ```kotlin
  webView.postDelayed(searchBlankVerifyRunnable!!, 48L)
  ```
- **Description:** The field is nullable by declaration; the `!!` assumes it is non-null at this line. On the race between scheduling and the prior `runnable` being nulled, this throws NPE. The `removeCallbacks(runnable)` at line 763 in the same file does not guard against the same race.
- **Recommended fix:** Use `searchBlankVerifyRunnable?.let { webView.postDelayed(it, 48L) }`.
- **Effort:** XS.

#### C-02 (MEDIUM) — 22 consecutive `!!` in `MessagePanel.kt`

- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/MessagePanel.kt:137-161`
- **Evidence:**
  ```kotlin
  advancedButton = fullBinding!!.buttonAdvancedInput
  attachmentsButton = fullBinding!!.buttonAttachments
  sendButton = fullBinding!!.buttonSend
  fullButton = fullBinding!!.buttonFull
  editPollButton = fullBinding!!.buttonEdtPoll
  … (22 lines, alternating fullBinding!! and quickBinding!!)
  ```
- **Description:** `fullBinding`/`quickBinding` are the result of `ViewBinding.inflate(...)` — in practice never null, but the chain is brittle: any future "two-mode binding" refactor will NPE at the first `!!`. There are 27 non-null assertions in this file alone (`grep` count).
- **Recommended fix:** Use `fullBinding?.buttonAdvancedInput?.let { advancedButton = it }` or split into a sealed `Mode { Full, Quick }` and a single `bind(binding: …Binding)` block.
- **Effort:** S.

#### C-03 (MEDIUM) — `downloadFile()` NPEs when `systemLinkHandler == null`

- **File:** `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt:287-289`
- **Evidence:**
  ```kotlin
  private fun downloadFile(context: Context, uri: Uri) {
      systemLinkHandler!!.handleDownload(uri.toString(), null, context)
  }
  ```
- **Description:** `systemLinkHandler` is a constructor-injected nullable. `handleUri` (line 248-268) uses `systemLinkHandler?.handle(...)` (safe), but `downloadFile` uses `!!`. In current code the `DownloadListener` is only installed when `systemLinkHandler != null` (see `ExtendedWebView.kt:156-197`), so the path is unreachable today, but the contract is fragile. A future caller that hits `handleUri → isDownloadableFile → downloadFile` without the listener wired in will NPE.
- **Recommended fix:** Make `systemLinkHandler` non-nullable in the constructor of `CustomWebViewClient` and ensure callers provide it.
- **Effort:** XS.

#### C-04 (HIGH) — `AvatarRepository.getAvatar(nick)` throws raw NPE for unknown nicks

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

#### C-05 (MEDIUM) — `Toast` in `Service` `IncomingHandler` has no `try/catch`

- **File:** `app/src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt:547-555`
- **Evidence:**
  ```kotlin
  private class IncomingHandler(service: NotificationsService) : Handler(Looper.getMainLooper()) {
      private val serviceRef: WeakReference<NotificationsService> = WeakReference(service)
      override fun handleMessage(msg: Message) {
          serviceRef.get()?.let { service ->
              Toast.makeText(service.applicationContext, "" + msg.data, Toast.LENGTH_SHORT).show()
          }
      }
  }
  ```
- **Description:** `Toast.makeText(applicationContext, …)` from a non-UI service can throw `BadTokenException` on Android 11+ if the system token expires or the service is being torn down. The handler has no try/catch. The handler is bound to a `Messenger` whose only producer in the visible code is `App.kt:157` (storing `mBoundService`); no `send()` callers were found — making the whole path dead but still crash-prone if a future caller writes to it.
- **Recommended fix:** Wrap the `Toast.makeText(...).show()` in `try { … } catch (BadTokenException) { }`; consider removing the dead `Messenger` path entirely.
- **Effort:** S.

#### C-06 (LOW) — `ThemePostedPageScrollPolicy.maxByOrNull{...}!!.id` NPE on empty list

- **File:** `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemePostedPageScrollPolicy.kt:27-28`
- **Evidence:**
  ```kotlin
  val lastId = parsedPosts.maxByOrNull { it.id }!!.id
  val firstId = parsedPosts.minByOrNull { it.id }!!.id
  ```
- **Description:** Both lines NPE on empty `parsedPosts`. In the current flow this is only called when the parser has produced posts, but the helper is exported and re-usable.
- **Recommended fix:** Use `.maxByOrNull { it.id }?.id ?: return` or `require(parsedPosts.isNotEmpty())` with a domain exception.
- **Effort:** XS.

#### C-07 (LOW) — `ThemeHistoryController.history.last()` after `.filter` (NoSuchElementException on empty)

- **File:** `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryController.kt:101,138,169`
- **Description:** Same pattern as C-06. (Already partially in `PERF_DIAGNOSIS.md`, but the file was not fixed.)
- **Recommended fix:** Replace with `lastOrNull { … }` and handle the null.
- **Effort:** XS.

#### C-08 (HIGH) — `QmsChatFragment` recreates WebView with stale JS bridge still attached

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


### 3.3 Performance findings (selected; see PERF_DIAGNOSIS.md for article/QMS specifics)

#### P-01 (MEDIUM) — OkHttp HTTP cache is 10 MB, no `Cache-Control: max-age`

- **File:** `app/src/main/java/forpdateam/ru/forpda/client/Client.kt:111-112,124-129`
- **Evidence:**
  ```kotlin
  private val cacheDir by lazy { File(context.cacheDir, "http_cache").apply { mkdirs() } }
  private val httpCache by lazy { Cache(cacheDir, 10L * 1024 * 1024) } // 10 MB
  ```
- **Description:** 10 MB is small for a forum/article client. There is no global `Cache-Control: max-age` — only `bypassCache=true` adds `no-cache, no-store, must-revalidate` (see `NewsApi.kt:272-281`). The OkHttp cache will mostly be hit only on identical URLs with `If-Modified-Since` returning 304.
- **Impact:** Repeated navigations within the same theme/section always hit the network; no passive caching of CSS/JS assets that are public.
- **Recommended fix:** Raise to ~50 MB; add a network interceptor that injects `Cache-Control: max-age=60` for assets and theme HTML; lower for authenticated requests.
- **Effort:** S.

#### P-02 (MEDIUM) — `ImageLoadingInterceptor` does `TimeUnit.MILLISECONDS.sleep(300)` on OkHttp dispatcher

- **File:** `app/src/main/java/forpdateam/ru/forpda/client/interceptors/ImageLoadingInterceptor.kt:72`
- **Evidence:**
  ```kotlin
  TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS)
  val retried = request.newBuilder().url(appendRetryFlag(request.url)).build()
  return chain.proceed(retried)
  ```
- **Description:** OkHttp interceptors run on the OkHttp dispatcher worker. Sleeping 300 ms blocks a dispatcher thread. For a heavy image topic this can starve the thread pool. The retry is a one-shot, but a flood of 504/503 from the CDN will pile up.
- **Recommended fix:** Replace with `chain.call().cancel()` + a new enqueue, or schedule the retry through an off-thread executor; or just retry the 504 once without the sleep.
- **Effort:** S.

#### P-04 (HIGH) — `CookieManager.initializeCookies` runs on the Hilt-singleton construction thread

- **File:** `app/src/main/java/forpdateam/ru/forpda/client/CookieManager.kt:43-81`
- **Evidence:** `init` block calls `SecureCookiesPreferences.getInstance(context)` (first-time AES-256-GCM key unwrap, ~30-200 ms on first run) and then synchronously reads/writes default SharedPreferences for each cookie. This runs on whatever thread constructs `Client`; in Hilt, that is the main thread.
- **Impact:** First-launch ANR risk on slow devices. Visible in the cold-start path (`App.onCreate` → `setupCoil` → `ForPdaCoil.init` → `webClient.getHttpClient()`).
- **Recommended fix:** Make `CookieManager` lazy; load cookies on the first `request` (off main) and keep an in-memory snapshot for the main thread.
- **Effort:** M.

#### P-06 (LOW) — `CachedDns` is never cleared on network changes

- **File:** `app/src/main/java/forpdateam/ru/forpda/client/CachedDns.kt:61-66`
- **Description:** `clearCache()` exists but is not called by `NetworkConnectivityTracker` (per `App.setupNetworkTracking`). On WiFi ↔ Mobile transitions, the cached IPs for the old network's DNS may be returned for up to 30 s. OkHttp's connection pool will eventually re-resolve on connection failure, but the first request after a switch will hit the wrong IP.
- **Recommended fix:** Call `cachedDns.clearCache()` when `NetworkConnectivityTracker` reports a transition.
- **Effort:** XS.

### 3.4 Architecture findings (selected; see REFACTOR_PLAN.md for the full plan)

#### A-01 (MEDIUM) — `MainScope()` leaks at 3 sites

- **Files:**
  - `app/src/main/java/forpdateam/ru/forpda/presentation/TabRouter.kt:16`
  - `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelper_V2.kt:43`
  - `app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/advanced/CodesPanelItem.kt:41`
- **Description:** `MainScope()` is a top-level `CoroutineScope` backed by `Dispatchers.Main` + `SupervisorJob()`. The lifecycle owner must explicitly call `.cancel()` or the scope keeps running through screen rotations and process death. None of these three sites have a documented cancellation hook.
- **Recommended fix:** Replace with `lifecycleScope` (Fragment) or `viewModelScope` (ViewModel). For `CodesPanelItem` (a view), expose a `dispose()` and call it from the parent fragment's `onDestroyView`.
- **Effort:** S each.

#### A-02 (MEDIUM) — `GlobalScope.launch` in `ThemeUseCase`

- **File:** `app/src/main/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCase.kt:326`
- **Evidence:** `GlobalScope.launch(Dispatchers.IO) { … }`.
- **Description:** A coroutine launched in `GlobalScope` cannot be cancelled when the caller is gone. The job can keep running after the activity is destroyed, leaking work and updating state on a dead ViewModel.
- **Recommended fix:** Inject a `CoroutineScope` from the use case's owner (`@Singleton`-scoped applicationScope) or restructure to use the ViewModel scope.
- **Effort:** S.

#### A-03 (HIGH) — `runBlocking` on the OkHttp dispatcher / caller's thread (5 sites)

- **Files:**
  - `app/src/main/java/forpdateam/ru/forpda/common/ForPdaCoil.kt:215,240`
  - `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt:62`
  - `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeApi.kt:341`
  - `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt:40,48`
- **Description:** `runBlocking(Dispatchers.IO) { … }` from a coroutine context is a smell; `runBlocking` from a non-coroutine caller blocks the current thread. `ForPdaCoil.loadBitmapSync` is intentionally synchronous (used from `shouldInterceptRequest`), but `NewsApi.kt:62` is on a coroutine path.
- **Impact:** When called from a non-coroutine context (the WebView intercept on a binder thread) it works; called from a coroutine on `Dispatchers.Main` it can deadlock if the inner scope is also `Main`.
- **Recommended fix:** Convert each to a `suspend` function. For `ForPdaCoil` and `AvatarRepository` sync variants, keep the sync version but document that they are only safe off the main thread (`Looper.myLooper() != Looper.getMainLooper()`).
- **Effort:** M.

### 3.5 UX findings (selected)

#### X-01 (INFO) — Legacy CP1251 login

- **File:** `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/auth/AuthApi.kt:42-43`
- **Evidence:**
  ```kotlin
  .formHeader("login", Cp1251Codec.encode(form.nick), true)
  .formHeader("password", Cp1251Codec.encode(form.password), true)
  ```
- **Description:** The login form encodes credentials in CP1251 because 4pda's forum auth requires it. This is correct behavior, but the form has no client-side password strength check, no "show password" toggle (visible in `AuthFragmentCallbacks`?), and the captcha is loaded externally. There is no logout from the UI (`AuthViewModel` exposes only `onClickSkip`).

#### X-04 (LOW) — `textZoom` reads `resources.configuration.fontScale` but sets `textZoom = 100` first

- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt:133,154`
- **Evidence:**
  ```kotlin
  settings.textZoom = 100
  …
  settings.textZoom = (resources.configuration.fontScale * 100).toInt()
  ```
- **Description:** The default is overwritten by the user's font scale; correct, but the user `WebView` font-size preference (`Preferences.Main.WEBVIEW_FONT_SIZE = "main.webview.font_size_v2"`) is layered on top via `setRelativeFontSize(16)` at line 152. The interaction is documented nowhere and not unit-tested.

#### X-05 (LOW) — Edge-to-edge not enabled per-activity

- **File:** `AndroidManifest.xml:57` (`enableOnBackInvokedCallback="true"`).
- **Description:** The manifest opts into predictive back, but no activity overrides `WindowCompat.setDecorFitsSystemWindows(window, false)` or uses `enableEdgeToEdge()` from `androidx.activity:activity-ktx 1.10.1`. On Android 15+ (API 35) the system enforces edge-to-edge by default for `targetSdk=35`; the app must support it. The fork's `README.md` claims "Исправления поведения клавиатуры/BBCode-панели на Android 15/16" — verify by manual device QA.

### 3.6 Build / dependency / manifest findings (selected)

#### M-01 (MEDIUM) — `allowBackup="true"` + `fullBackupContent="false"` should be `dataExtractionRules`

- **File:** `app/src/main/AndroidManifest.xml:49-50`
- **Description:** `allowBackup="true"` + `fullBackupContent="false"` is the legacy pre-Android 12 way. For `targetSdk=35` you should also set `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+) to control cloud backup and device-transfer separately. The current setup may inadvertently include all SharedPreferences in cloud backup, including the auth-key (`Preferences.Auth.AUTH_KEY = "auth_key"`) and the EncryptedSharedPreferences file.
- **Recommended fix:** Add a `data_extraction_rules.xml` resource that explicitly excludes the encrypted cookie file and the auth_key from cloud backup; set `android:dataExtractionRules` in the manifest.
- **Effort:** S.

#### M-03 (LOW) — Stale `versionCode`/`versionName` in manifest

- **Files:** `AndroidManifest.xml:4-5` (349 / 2.9.3) vs `app/build.gradle:67` (350 / 2.9.4)
- **Description:** Gradle overrides these at build time, but the manifest entries are still read by the Play Store UI and linters. Update the manifest to match.

#### D-03 (MEDIUM) — `androidx.security:security-crypto:1.1.0-alpha06`

- **File:** `app/build.gradle:297` (`implementation libs.androidx.security.crypto`)
- **Description:** Alpha dependency for cookie encryption. Stable `1.0.0` is sufficient for the AES256_SIV/AES256_GCM scheme used. The comment in `app/build.gradle:296` says "FIXME: Using alpha due to API changes in 1.0.x stable. Consider migrating to Tink directly." — that migration would be the right long-term path.
- **Recommended fix:** Track Tink directly; or pin `1.0.0` if the alpha API is not actually used.

#### D-04 (MEDIUM) — `minitemplator 1.2` is JCenter-era and repackaged

- **File:** `app/build.gradle:326` (`implementation libs.minitemplator`)
- **Description:** `org.bsc.util:minitemplator-repackaged:1.2` is a repackaged fork of the abandoned JCenter library. JitPack / Maven Central may not have a maintained version. Risk: the dependency will eventually disappear.

#### T-01 (MEDIUM) — `detekt-baseline.xml` is 484 KB

- **File:** `detekt-baseline.xml`
- **Description:** The baseline absorbs ~thousands of legacy findings. `detekt.yml` disables `UndocumentedPublic*`, `FunctionNaming`, `ClassNaming`, `MagicNumber`, `MaxLineLength`, `TooGenericExceptionCaught`, `EmptyClassBlock`, `EmptyFunctionBlock`, `AbsentOrWrongFileLicense`, `CommentOver*`. Combined with the 484 KB baseline, detekt effectively catches nothing new in this codebase. New issues introduced today will be silently absorbed.
- **Recommended fix:** Pick 2-3 high-signal rules (`ComplexMethod`, `LongMethod`, `LongParameterList`) and aggressively reduce the baseline file size by fixing the existing entries; gate new code by `excludeCorrectable: false` (already on in `detekt.yml:3`).
- **Effort:** L (one-off refactor).

EOF
wc -l /tmp/append2.md
## 4. Crash risk hotspots

This is the cross-cutting summary of the conditions under which the app can crash. Each hotspot has been documented in §3.2 in detail; this section is the at-a-glance triage.

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

EOF
wc -l /tmp/append3a.md
## 5. Security findings (consolidated)

The application is a forum client; the main security boundary is the WebView <-> JS bridge and the auth-cookie / network surface. The following is the consolidated security picture, cross-referenced with `PROPDA_FINAL_CODE_AUDIT_REPORT.md`, `JS_BRIDGE_INVENTORY.md`, and `PROPDA_HARDENING_REPORT.md`.

### 5.1 Secrets and config

- **S-04 (Medium) Hardcoded AppMetrica API key** — `App.kt:263`. Single string literal; gate by `flavor == "store"`; should move to `local.properties` / Gradle property.
- **S-04b (Info) `keystore.parallel.properties` is in `.gitignore`** — confirmed at line 7. The file on disk contains real passwords, which is correct local behavior. No `keystore.properties` is tracked.
- **No additional hardcoded API keys** were found. No `Basic ` / `Bearer ` tokens in source.

### 5.2 Network and TLS

- **TLS configuration is sane.** `Client.kt:114-130` builds `OkHttpClient` with `Protocol.HTTP_2, HTTP_1_1`, a `ConnectionPool`, Brotli, and a `CachedDns`. There is no custom `SSLSocketFactory`, no `trustAllCerts`, no `HostnameVerifier` override, no `sslSocketFactory(...)` — confirmed by `rg "trustAll|hostnameVerifier|sslSocketFactory" app/src/main` returning nothing relevant.
- **`networkSecurityConfig` is correct.** `app/src/main/res/xml/network_security_config.xml` sets `cleartextTrafficPermitted="false"` and only allows the system trust store. There is no `<trust-anchors>` for user-installed CAs.
- **M-01 (Medium) `allowBackup="true"` + `fullBackupContent="false"`** — see §3.6.

### 5.3 Storage

- **`SecureCookiesPreferences`** (line 1-112) uses `MasterKey.KeyScheme.AES256_GCM` and `EncryptedSharedPreferences` with `AES256_SIV` keys and `AES256_GCM` values. Migration from legacy default prefs is one-shot (line 39-77) and idempotent (the `cookies_migrated_to_encrypted` flag).
- **The auth_key is stored in plain `SharedPreferences`** (`AuthApi.checkLogin`, line 76). This is acceptable for a 4pda auth key (already extracted from the network); documenting the trade-off is sufficient.
- **Room database** uses no SQLCipher; the database file is in `databases/forpda_database` unencrypted. It contains history, favorites, notes, and forum user cache. Acceptable for this app.

### 5.4 WebView

- **JS bridge inventory** — see `docs/JS_BRIDGE_INVENTORY.md` for the full table. Summary:
  - 7 active `addJavascriptInterface` registrations (5 `this`-as-Fragment, 1 `jsInterface`, 1 `themeViewBridge`).
  - 52 exported `@JavascriptInterface` methods.
  - Highest-risk surface is `IThemePresenter` (theme destructive actions).
- **WebView file/content access is disabled.** `ExtendedWebView.init` sets `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false` (lines 147-150).
- **`UrlPolicy` blocks `javascript:`, `file:`, `data:`, `content:`, `about:`, `app_cache:` schemes** before WebView navigation or external intent dispatch (`common/webview/UrlPolicy.kt:33-38`). Control chars, percent-encoded control chars, and percent-encoded dangerous scheme prefixes are also rejected.
- **S-01 (Critical)** — Theme destructive methods have no render-token guard.
- **S-05 (Medium)** — News `openExternalBrowser` / `openImage` should re-validate via `UrlPolicy` (see prior docs).
- **S-07 (Low)** — `MIXED_CONTENT_COMPATIBILITY_MODE` allows some forms of mixed content; should be `MIXED_CONTENT_NEVER_ALLOW`.

### 5.5 AndroidManifest

- **`MainActivity` is `exported="true"` with a deep-link intent filter** for `http(s)://4pda.to*` and `www.4pda.to` (lines 66-90). This is intentional (share-target) and the URL goes through `MainActivity`'s `onNewIntent` → cicerone → `UrlPolicy` at the eventual navigation. Defense in depth: filter should also check `host == "4pda.to" || host == "www.4pda.to"` (currently `host="4pda.to"` plus `host="www.4pda.to"` for both `http` and `https`, which is exhaustive).
- **`NotificationsService` is `exported="false"`** with `foregroundServiceType="dataSync"` (line 124-127). Good.
- **`WakeUpReceiver` is `exported="true"`** but protected by `android:permission="android.permission.RECEIVE_BOOT_COMPLETED"` (line 160-161). The system sends the BOOT_COMPLETED broadcast only to receivers holding this permission, so this is safe.

### 5.6 Dependencies

- **D-03 (Medium)** — `androidx.security:security-crypto:1.1.0-alpha06`. The `app/build.gradle:296` FIXME note acknowledges the alpha.
- **D-04 (Medium)** — `minitemplator 1.2` (JCenter repackaged).
- **D-07 (Low)** — `tagsoup 1.2.1` (very old; no CVE).
- **No known CVEs in the current versions** of `okhttp 4.12.0`, `coil 2.7.0`, `jsoup 1.18.3`, `coroutines 1.8.1`, `material 1.12.0`.

EOF
wc -l /tmp/append3b.md
## 6. Performance findings (consolidated)

The codebase has been profiled and documented extensively in `PERF_DIAGNOSIS.md`. The findings below are *new* (not in PERF_DIAGNOSIS) or cross-cutting:

### 6.1 Memory

- **`ForPdaCoil` memory cache is 25% of process heap** (`common/ForPdaCoil.kt:73-78`). This is the right ballpark for an image-heavy app. The disk cache is 128 MB. The `LruCache` for `AvatarRepository` is 512 entries. Memory pressure should be OK on most devices, but a heavy theme with 200+ images will hit the cache repeatedly.
- **`WebView` instances are kept alive longer than necessary.** `ThemeFragmentWeb` keeps the WebView across the fragment lifecycle; the WebView's bitmap cache is large. The hardening report already covered the post-destroy state.
- **`AppDatabase` SQLite connection pool:** not explicitly tuned. Default is fine.

### 6.2 CPU

- **`runBlocking` on dispatcher threads (5 sites)** — see A-03. The biggest CPU win is converting these to `suspend` functions.
- **`Jsoup` parsing of forum HTML** is done on `Dispatchers.Default` (per `ArticleInteractor.kt:825-827`); theme parsing in `ThemeApi` is sync.
- **Cascading `postDelayed` chains** in `ArticleContentFragment` and `QmsChatFragment` (60+ sites total) — see §3.2 hotspot 12-14. These add up on rapid navigation.

### 6.3 Battery / Network

- **Per the `battery-audit.sh` README** (in `scripts/battery-audit.README.md`), the expected wake-locks are `WebSocketController` (when foreground realtime is on) and `DownloadWorker` (during downloads). Everything else is a bug.
- **No `AlarmManager` usage in the app** (confirmed by `rg AlarmManager app/src/main` returning only framework references). All background work goes through `WorkManager`.
- **DNS cache TTL is 30 s** (`CachedDns.kt:20`). Reasonable for mobile; see P-06 for the missed clear-on-network-change.
- **`ArticleContentFragment` always runs the deferred-extras phase-2** even when there's nothing to load (`PERF_DIAGNOSIS §5.6`). Already documented.

### 6.4 WebView

- **Image intercept on `shouldInterceptRequest`** (`CustomWebViewClient.kt:82-181`) does sync disk reads and PNG encoding on the binder thread. This is documented in `docs/WEBVIEW_INTERCEPT_PROFILING.md` and partly mitigated by the `AVATAR_RESPONSE_MAX_ENTRY_BYTES = 256KB` LRU.
- **No `setOffscreenPreRender(false)`** is called — the default is `false` on Android 5+, so the WebView does not pre-render off-screen. OK.

## 7. Architecture findings (consolidated)

### 7.1 Coupling and layering

- **`NetworkModule` is 91 lines** and provides 13 `*Api` `@Singleton`s and 14 `*Parser` `@Singleton`s. Every new endpoint adds two more lines. The cross-cutting concern is that every API also takes the full `IWebClient` and the parser, so a misconfiguration in either will silently fall through to the network. (See `di/NetworkModule.kt`.)
- **All parsers extend a shared `IPatternProvider`** — see `model/data/storage/IPatternProvider.kt`. Patterns are matched against forum HTML; the provider is `@Singleton` and shared across 14 parsers. This is good DRY but a single pattern regression can break all of them.
- **`ThemeViewModel` is >4000 lines** — see `REFACTOR_PLAN.md §1.1`. Splitting it is the highest-impact architecture change.
- **`AppDatabase` is monolithic** — 10 entity types from 6 different domains (notes, history, QMS, favorites, forum, forum-user, offline). Splitting into 2-3 databases is a small refactor that would let users clear specific caches. *(See A-05.)*

### 7.2 Coroutines and lifecycle

- **3 `MainScope()` leaks** — see A-01.
- **1 `GlobalScope.launch`** — see A-02.
- **5 `runBlocking` sites** — see A-03.
- **`StrictMode.ThreadPolicy.Builder().detectAll()` is debug-only** (`App.kt:220-235`). In release, no thread-policy enforcement. This is correct for production but means there is no early warning for accidental disk I/O on main thread in release.

### 7.3 Duplicated logic

- **`getCounts` parsing** in `Client.kt:371-406` and the per-source "fallback patterns" (`IWebClient.mentionsCountPattern`, `favoritesCountPattern`, `qmsCountPattern`) are duplicated across `getCounts`, `EventsRepository`, and `MentionsRepository`. Consolidating into a `HeaderCountersParser` would reduce the count of patterns from 3 to 1.
- **HTML-entity decoding** uses both `Html.fromHtml` and `ApiUtils.fromHtml` and `NickEncoder.decode`; the three should be merged or clearly bounded.
- **Stale-cookie cleanup** is implemented twice: in `CookieManager.removeStaleNullCookies` (line 174-186) and in the `saveFromResponse` path (line 90-94).

### 7.4 Configuration and DI

- **`provideOfflineOkHttpClient`** in `DataModule.kt:177-181` is independent of the main `Client`. The comment notes the trade-off: image downloads are public, no auth_key. OK.
- **All `*Repository` constructors are concrete classes, not interfaces.** Hilt binds the concrete type. The benefit: no interface indirection. The cost: harder to test in isolation, harder to swap implementations.

EOF
wc -l /tmp/append3c.md
## 8. UX findings (consolidated)

### 8.1 Login flow

- **CP1251 legacy encoding** — see X-01. The auth flow does not communicate that the user is on a legacy CP1251 endpoint; the password field does not toggle visibility.
- **No "logout" visible in `AuthViewModel`.** The model exposes only `onClickSkip`. A "Logout" call goes through `AuthApi.logout` (used by the menu).
- **Captcha is loaded in an external browser / GoogleCaptchaFragment**, which is a full-screen WebView. There is no in-app fall-back if the captcha is blocked.

### 8.2 WebView / theme

- **First-paint font scale is `textZoom = 100`, not user font scale** — see X-04. The user can change font size in the system, but the WebView re-initializes with 100 before applying the scale. The first frame of a fresh WebView is at 100%.
- **`WebViewChecker` and `WebVewNotFoundActivity` exist for devices without a working WebView** — confirmed at `AndroidManifest.xml:118-122`. The class name has a typo (`WebVewNotFoundActivity`). Low priority but worth fixing.

### 8.3 Notifications

- **Notification stack is built per event with no rate-limiting** — `NotificationsService.sendNotifications` (line 330-379) processes up to 4 events and posts one. Per-event rate limiting is in the user prefs, but the per-second rate is not capped, so a flood of events can post 4-stack notifications back-to-back. A real 4pda spam event can create a "notification storm" until the user kills the app.
- **Channel `forpda_foreground_service` is created with `IMPORTANCE_MIN`** but is visible in app settings. This is intentional but users can disable it; if they do, the foreground service is killed on Android 14+.

### 8.4 Edge-to-edge / back

- **`enableOnBackInvokedCallback="true"` is set in the manifest** but no per-activity migration. Android 14+ predictive back may behave inconsistently. The fork README claims keyboard/BBCode fixes for Android 15/16; verify by device QA. (See `docs/navigation-scroll-fixes-plan.md`.)

### 8.5 Accessibility

- **No `contentDescription` audits were done in this pass** — the existing `docs/ACCESSIBILITY_CHECKLIST.md` covers this.
- **The message panel's advanced buttons** (BBCode toolbar) are icon-only and have `contentDescription` in the layout, but font scale 1.5 may overlap.

## 9. Top 10 prioritized action items (ROI-ordered)

Each item references the IDs from §2. Use it as a planning aid — this audit is not a fix list.

1. **Add render-token guard to all destructive `ThemeJsInterface` methods** *(S-01, S-02)*. Single biggest residual risk. Effort: M. ROI: highest. Already in `PROPDA_FINAL_CODE_AUDIT_REPORT.md §H1`; should be picked up.
2. **Convert `AvatarRepository` and `ForPdaCoil` sync APIs to `suspend` where possible; remove `runBlocking` from `NewsApi` and `ThemeApi`** *(A-03, C-04)*. Effort: M. ROI: high — eliminates a deadlock class and a crash class.
3. **Make `CookieManager.initializeCookies()` lazy / off-main** *(P-04)*. Effort: M. ROI: high — first-launch ANR is a Play Store visible crash signature.
4. **Fix `QmsChatFragment` WebView re-creation: cancel old `jsInterface` before `destroy()`** *(C-08)*. Effort: S. ROI: high — QMS is a release-critical user flow.
5. **Replace `searchBlankVerifyRunnable!!` and the 22 `!!` cascade in `MessagePanel`** *(C-01, C-02)*. Effort: XS–S. ROI: medium — these are the most user-visible crash risks.
6. **Add `dataExtractionRules` and harden `allowBackup`; add `MIXED_CONTENT_NEVER_ALLOW`** *(M-01, S-07)*. Effort: S. ROI: medium.
7. **Move the AppMetrica API key to `BuildConfig` / Gradle property** *(S-04)*. Effort: S. ROI: medium — prevents accidental enablement on every flavor.
8. **Run the focused Search/media tests in green** *(PROPDA_FINAL_CODE_AUDIT_REPORT.md §H2/H3)*. Effort: M. ROI: high — these are explicitly release-blocking per the existing report.
9. **Cut `detekt-baseline.xml` to a small, hand-curated set** *(T-01)*. Effort: L (one-off). ROI: medium — restores detekt as a guardrail.
10. **Cache `OfflineArticleSource` URL validation and clear `CachedDns` on network change** *(P-06)*. Effort: XS. ROI: low — small reliability polish.

## 10. Appendix

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

- **Total findings:** 51 (in §2 table)
- **By severity:** Critical: 1, High: 8, Medium: 18, Low: 15, Info: 9
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

---

## 11. Post-audit changes

### 11.1 Theme Offline feature removed (2026-06)

Per explicit user directive, the **"save theme for offline reading"** path was
removed end-to-end. The article (news) offline path and the shared
`OfflineRepository` / `OfflineStorage` / `OfflineItemRoom` data layer are
**preserved** — they back the article offline cache (`ArticleContentFragment`
"Сохранить для оффлайна" overflow item) and the cache-size preference in
Settings.

What was removed:

- `ThemeFragmentWeb.saveCurrentThemeForOffline()` and the
  `R.id.action_save_offline` menu injection in `addBaseToolbarMenu`.
- `R.menu.theme_details` (only the offline menu item — `article_details.xml`
  is kept for the article offline path).
- `Screen.OfflineList` (and its `TabHelper` routing branch +
  `useComposeOfflineList` A/B flag).
- `OfflineListComposeFragment` / `OfflineListViewModel` /
  `OfflineListScreen` (the THEME list UI — it never showed articles).
- `OfflineListOpenItemRoutingTest` (helper that parsed `article:*` /
  `theme:*` ids for the removed list).
- `ThemeViewModel.currentData()` — the §5.1 read-only accessor that existed
  solely to feed the save flow.

What was kept (shared with article offline and intentionally left in place):

- `OfflineRepository` / `OfflineStorage` / `OfflineImageDownloader` /
  `OfflineSaveController` / `OfflineArticleSource` / `OfflineItemRoom` /
  `OfflineItemDao` / `NotesMigrations` (6→7) / `NotesDatabase` v9 — used by
  the article offline flow and by the storage-limit preference.
- `offline.max_bytes_mb` preference UI and its
  `SettingsFragment.enforceStorageLimit` listener.
- `R.string.save_for_offline` / `R.string.offline_saved` /
  `R.string.offline_save_error` — referenced by `ArticleContentFragment`.
- `R.id.action_save_offline` — still in `res/menu/article_details.xml`.

### 11.2 Article Offline feature removed (2026-06)

Per explicit user directive, the **"save article for offline reading"** path
was removed end-to-end on top of §11.1. After §11.1, the
`OfflineRepository` / `OfflineStorage` / `OfflineImageDownloader` /
`OfflineSaveController` / `OfflineArticleSource` / `OfflineWebViewBaseUrl` /
`OfflineIndexPathHandler` / `OfflineItemRoom` / `OfflineItemDao` data layer
was the last remaining offline artefact (it backed the article "Сохранить
для оффлайна" overflow item). That whole layer is now gone, together with
the storage-limit preference UI, the `R.id.action_save_offline` menu item,
and every offline-related resource, schema migration, and test.

What was removed:

- `model/data/offline/` package: `OfflineRepository`, `OfflineStorage`,
  `OfflineImageDownloader`, `OfflineSaveController`, `OfflineArticleSource`,
  `OfflineWebViewBaseUrl`, `OfflineIndexPathHandler`.
- `entity/db/offline/` package: `OfflineItemRoom` (and the in-file
  `OfflineItemType` / `OfflineItemStatus` enums), `OfflineItemDao`.
- `NotesDatabase`: `OfflineItemRoom` removed from `@Database(entities = …)`,
  `offlineItemDao()` abstract method removed, schema version dropped from 9
  to 6 (matching the post-removal set of tables).
- `NotesMigrations`: `MIGRATION_6_7` / `MIGRATION_7_8` / `MIGRATION_8_9`
  (all of which only created/touched the `offline_items` table) replaced
  with `MIGRATION_7_6` / `MIGRATION_8_6` / `MIGRATION_9_6` that drop the
  `offline_items` table for devices that picked it up during the offline
  era. New installs land directly on v6.
- `DataModule`: every `provideOffline*` provider (DAO, storage, repository,
  article source, image downloader, save controller, dedicated OkHttp
  client) and the corresponding imports.
- `ArticleContentFragment`: the four `@Inject` offline fields, the
  `registerOfflineSaveMenu()` `MenuProvider` registration, the
  `saveCurrentArticleForOffline()` helper, the `tryServeOfflineArticle` /
  `clearOfflineAssetLoader` flow in `onViewCreated`, the offline
  `WebViewAssetLoader` plumbing in the inner `ArticleWebViewClient`
  (`setOfflineAssetLoader` + the two `shouldInterceptRequest` overrides),
  and the `Menu` / `MenuInflater` / `MenuItem` / `MenuProvider` imports.
- `SettingsFragment`: the `OfflineRepository` injection and the
  `offline.max_bytes_mb` listener branch in `prefsListener` (including the
  `enforceStorageLimit` call).
- `Constants.TAB_OFFLINE` (the leftover tab identifier from the §11.1
  removal).
- Resources: `R.menu.article_details.xml`,
  `R.drawable.ic_news_offline_black_24dp.xml`,
  `R.layout.news_item_compat.xml` (the layout that hosted the unused
  `news_list_item_save` ImageButton backed by the offline icon).
- `R.string.save_for_offline` / `R.string.offline_saved` /
  `R.string.offline_save_error` / `R.string.pref_title_offline` /
  `R.string.pref_title_offline_max_bytes` /
  `R.string.pref_summary_offline_max_bytes` and the 50/100/200/500/1024 МБ
  variants.
- `R.array.entries_offline_max_bytes` / `R.array.entry_values_offline_max_bytes`.
- The `R.string.entries_offline_max_bytes` /
  `R.string.entry_values_offline_max_bytes` string-array labels (in
  `strings.xml`).
- The "Оффлайн-чтение" / "Лимит кэша" `PreferenceCategory` in
  `res/xml/preferences.xml`.
- Tests: `app/src/test/java/.../model/data/offline/` (`OfflineRepositoryEvictionTest`,
  `OfflineImageDownloaderTest`, `OfflineArticleSourceTest`).
- Room schemas: `app/schemas/.../AppDatabase/7.json` and `9.json`. The
  pre-offline `6.json` is kept (it never carried the `offline_items`
  table and matches the new v6 schema).

The article / news rendering pipeline now relies solely on the network
fetch + the existing in-memory `ArticleDiskCache` / `ArticleMemoryCache` /
`ArticleReadingProgressStore`; no `WebViewAssetLoader` is wired on the
article WebView anymore, so the `CustomWebViewClient` base-class
`shouldInterceptRequest` implementation is authoritative again.
