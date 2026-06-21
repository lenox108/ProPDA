# WebView Pipeline Inventory

> **Phase 1 deliverable** for the *PROPDA WebView Rendering Stabilization & Performance Optimization* task (`docs/Task01_GPT.md`).
> This document is a **source-code-based inventory** of every WebView rendering pipeline in the app. No production code was changed to produce it.
> All file paths are relative to `app/src/main/java/forpdateam/ru/forpda/` unless stated otherwise. Line numbers reflect the working tree at the time of writing and may drift after edits.

## Pipelines covered

| Pipeline | Owner enum (proposed) | Fragment | Security profile | Base bridge (`IBase`) |
|---|---|---|---|---|
| Forum topic / Theme | `THEME` | `ui/fragments/theme/ThemeFragmentWeb.kt` | `TRUSTED_LOCAL_TEMPLATE` | Yes (`enableBaseBridge()`) |
| Search results | `SEARCH` | `ui/fragments/search/SearchFragment.kt` | `TRUSTED_STATIC_ARTICLE` | No |
| QMS chat | `QMS` | `ui/fragments/qms/chat/QmsChatFragment.kt` | `TRUSTED_LOCAL_TEMPLATE` | Yes (`enableBaseBridge()`) |
| News / article detail | `NEWS` | `ui/fragments/news/details/ArticleContentFragment.kt` | `TRUSTED_STATIC_ARTICLE` | No |

All four pipelines share the same WebView subclass: **`ExtendedWebView`** (`ui/views/ExtendedWebView.kt`), which extends `NestedWebView` (→ `WebView`) and implements the `IBase` JS bridge interface. Static pages (forum rules, announcements) also use `ExtendedWebView` with `TRUSTED_STATIC_ARTICLE` but have no dynamic render lifecycle and are out of scope for the shared controller for now.


---

## Existing reusable infrastructure

> Later phases MUST extend these instead of duplicating them (Safety Rules 15, 16, 18).

### `WebViewLoadDispatchPolicy` — `common/webview/WebViewLoadDispatchPolicy.kt`
`object` with a `Snapshot` data class and three pure decision functions. Already the foundation for the future shared render controller (Phase 3).

- `Snapshot(pendingTargetId, pendingContentHash, loadDispatched, requestGeneration, domConfirmedGeneration, lastDomConfirmedTargetId, lastRequestedTargetId)`
- `shouldSkipInflightDuplicate(force, targetId, contentHash, snapshot)` — skip only when the *same payload* is actively loading (dispatched, not yet DOM-confirmed).
- `shouldForceEnsureRender(targetId, snapshot)` — HTML available but paint never confirmed → force a render.
- `shouldDeferLoadUntilLayout(width, height)` — never call `loadDataWithBaseURL` while the view is 0×0.
- Test exists: `WebViewLoadDispatchPolicyTest`.
- Already consumed by **Search** (`searchLoadSnapshot()`), **News** (`ArticleRenderInflightPolicy.Snapshot`), and partly **QMS** (`shouldDeferLoadUntilLayout`). Theme has its own equivalent logic not yet routed through this object.

### `ThemePageMemoryCache` — `model/repository/theme/ThemePageMemoryCache.kt`
Short-lived in-memory cache of the **parsed `ThemePage`** (pre-template) including `html`, `postsFragmentHtml`, `renderSignature`, and scroll-restore fields.

- `Key(topicId, st, hatOpen, pollOpen)` — **does NOT include theme/render signature** (Phase 6B gap).
- TTL `DEFAULT_TTL_MS = 7 min`; LRU `maxEntries = 24` via access-ordered `LinkedHashMap`.
- `get()` returns a defensive `copyForCache()` deep copy; `put()` prunes expired + evicts eldest.
- `invalidateTopic(topicId)`, `clear()`, `keyFrom(url, hatOpen, pollOpen)`.
- `shouldSkipCache(url)` — skips `act=findpost`, `view=findpost/getnewpost/getlastpost`, `pid=`, `p=` URLs.
- **Phase 6B / Phase 8 decision:** extend this cache (add signature-aware invalidation), reuse it as the Smart Preload store. Do **not** create a parallel `ThemeHtmlMemoryCache` without passing the decision gate.

### `FpdaDebugLog` — `diagnostic/FpdaDebugLog.kt`
DEBUG-only (`BuildConfig.DEBUG`-gated) structured single-line logger built on Timber, with stable tags.

- `log(tag, event, fields)` / `warn(tag, event, fields)`; `newTraceId()`.
- Privacy helpers: `sanitizeUrl()` (strips `auth_key`, `session_id`, `sid`, `pass`, `token`, `key`, `cookie`, `member_id`, …), `classifyHtml()` (length + short hash + coarse markers, never raw markup), `errorClass()`.
- Domain helpers: `logTheme()`, `logQms()`, `logArticle()`, `logSmartButton()`, plus `fieldsWithTrace()`/`fieldsWithGeneration()`.
- Existing tags include `TAG_THEME_RENDER` (alias `TAG_WEBVIEW_RENDER`), `TAG_WEBVIEW_BLANK`, `TAG_ARTICLE_RENDER` (alias `TAG_ARTICLE_WEBVIEW`), `TAG_QMS_WEBVIEW`, etc.
- **Phase 10:** implement `WebViewRenderDiagnostics` on top of this; never use `android.util.Log`.

### `ThemeRenderGuard` — `presentation/theme/ThemeRenderGuard.kt`
Per-render bridge-token guard (Theme only). `newToken()` mints 32 secure-random bytes as URL-safe Base64 (no padding); `isValid(token)` compares against the current token; `invalidate()` clears it. Enforced in `ThemeJsInterface.runProtected()` to reject stale JS-bridge actions. Must not be deleted in this task (Safety Rule 1).

### `ThemeRenderSession` — `presentation/theme/ThemeRenderSession.kt`
Immutable snapshot that mirrors the three independent Theme validity systems (`ThemeRenderGuard.token`, `ThemePage.renderGenerationId`, controller render generation) without replacing them. Fields: `topicId, page, renderGenerationId, bridgeToken, themeSignature, createdAt`. Has `create(page, bridgeToken)` and DEBUG-gated `logCreated(...)`. The shared `WebViewRenderSession` (Phase 2) should generalize this. Must not be deleted (Safety Rule 2). No `ThemeRenderSessionTest` exists yet.


---

## Shared core: `ExtendedWebView` JS batching & lifecycle

File: `ui/views/ExtendedWebView.kt`. All four pipelines depend on this.

### Security profiles (enum `WebViewSecurityProfile`, lines ~42–61)
- `TRUSTED_LOCAL_TEMPLATE` — fully trusted local content (Theme, QMS). JS enabled, `IBase` base bridge permitted.
- `TRUSTED_STATIC_ARTICLE` — locally generated static content (articles, search, rules, announcements). JS enabled, but `IBase` base bridge **forbidden** — only narrow read-only interfaces allowed.
- `UNTRUSTED_EXTERNAL` — JS disabled.
- `init(profile)` (~135–226) configures `WebSettings`: JS on for non-`UNTRUSTED_EXTERNAL`; multi-window + JS-open-windows for trusted; DOM storage on; file access fully disabled; mixed-content `NEVER_ALLOW` in release / `COMPATIBILITY_MODE` in debug.
- `enableBaseBridge()` (~234–240) registers `IBase.JS_BASE_INTERFACE` — **gated to `TRUSTED_LOCAL_TEMPLATE` only** (warns + returns otherwise). `removeBaseBridge()` (~243–245) removes it.

### JS batching internals
- `evalJs(script: String)` (512) → `enqueueEvalJs()` — **batched**: appends to `pendingJs` `StringBuilder` under `jsBatchLock`, ensures trailing `;`, posts `jsFlushRunnable` after **16 ms** if not already posted.
- `evalJs(script, resultCallback)` (516) → `syncWithJs { evaluateJavascript(script, callback) }` — **immediate**, callback-based.
- `flushQueuedJs()` (535) — removes the runnable and runs it now.
- `clearQueuedJs()` (540) — removes the runnable, resets `pendingJs` length to 0, clears `jsFlushPosted` and `actionsForWebView`.
- `evalJsImmediate(script)` (549, private) — immediate `evaluateJavascript(script, null)` with a `loadUrl("javascript:…")` fallback on exception.
- `domContentLoaded()` (563, `@JavascriptInterface`) — sets `isJsReady = true`, drains `actionsForWebView`, then dispatches `JsLifeCycleListener.onDomContentComplete(actions)` and `nativeEvents.onNativeDomComplete()`.
- `loadDataWithBaseURL` override (~390) resets `isJsReady=false` and guards `destroyedForReuse`.

### Timer pause/resume (process-global caveat)
- `onPause()` (265) — decrements a process-wide `activeTimerWebViews` counter; only calls `pauseTimers()` when the **last** active WebView pauses (because `pauseTimers()/resumeTimers()` are process-global and would otherwise freeze a visible WebView). Also calls `clearQueuedJs()`.
- `onResume()` (284) — increments the counter; calls `resumeTimers()` when the first WebView resumes.
- `endWork()` (~814) calls `clearQueuedJs()` and loads `about:blank` for teardown.


---

## Pipeline 1 — Theme (forum topic)

Files: `ui/fragments/theme/ThemeFragmentWeb.kt`, `ui/fragments/theme/modules/ThemeWebController.kt`, `ui/fragments/theme/modules/ThemeBridgeHandler.kt`, `ui/fragments/theme/modules/ThemeJsApi.kt`, `presentation/theme/ThemeRenderSession.kt`, `presentation/theme/ThemeRenderGuard.kt`, `presentation/theme/ThemeJsInterface.kt`.

This is the most complex pipeline; it carries **three independent validity systems** simultaneously.

| # | Concern | Location |
|---|---|---|
| 1 | WebView created | `ThemeFragmentWeb.kt:283` (`webView = ExtendedWebView(...)`, `jsApi = ThemeJsApi(webView)`); `init()` called `:323` |
| 1 | WebView initialized | `ThemeWebController.init()` `:140–162` → `webView.init(TRUSTED_LOCAL_TEMPLATE)` `:143`; wires `ThemeWebViewClient`/`ThemeChromeClient` |
| 2 | Security profile | `TRUSTED_LOCAL_TEMPLATE` (`ThemeWebController.kt:143`); base bridge enabled (only profile allowed to) |
| 3 | `loadDataWithBaseURL` | `ThemeWebController.renderThemePage()` `:319` — base `ArticleLinkResolver.THEME_WEBVIEW_BASE_URL`, `page.html` |
| 4 | Render generation | (a) controller `renderGeneration` `:107`, bumped in `renderThemePage()` `:290`, exposed via `getControllerRenderGeneration()` `:436`; (b) bridge token via `ThemeRenderGuard.newToken()` minted in `onPageComplete` `ThemeFragmentWeb.kt:1961`; (c) `ThemeRenderSession.create()` `ThemeFragmentWeb.kt:1964–1967`; (d) per-page `ThemePage.renderGenerationId` + fragment `renderCount` `:206` |
| 5 | Stale callbacks ignored | DOM/page lifecycle claim: `tryClaimDomLifecycle()` `ThemeWebController.kt:425`, `tryClaimPageLifecycle()` `:438`; bail-outs `ThemeFragmentWeb.kt:1767–1773` (domComplete) & `:2104–2113` (pageComplete); bridge-token check `ThemeJsInterface.runProtected()` `:246–259`; view-runtime generation `ThemeFragmentWeb.kt:1280–1296`; `activeRenderKey` checks `ThemeWebController.kt:1346`, `:1003–1020` |
| 6 | `addJavascriptInterface` | `ThemeBridgeHandler.init()` `:30–37` (calls `enableBaseBridge()` then adds `IThemeView`/`IThemePresenter` interfaces) |
| 7 | `removeJavascriptInterface` | `ThemeBridgeHandler.cleanup()` (`removeJavascriptInterface` + `removeBaseBridge()` + `jsInterface.cancel()`) |
| 8 | `clearQueuedJs` | Invoked on destroy/reset paths via controller + `ExtendedWebView.onPause()`/`endWork()` |
| 9 | DOM-ready / page-ready | `onDomContentComplete()` `ThemeFragmentWeb.kt:1767`; `onPageComplete()` `:2104` (mints token + builds `ThemeRenderSession`) |
| 10 | Blank-screen recovery | Theme uses `shouldForceEnsureRender` equivalent + ensure-render path in controller; watchdogs recently converted to coroutines (see last commit) |
| 11 | Timers paused/resumed | Inherited `ExtendedWebView.onPause()/onResume()` via fragment lifecycle (`ThemeFragmentWeb.kt:~1574–1690`) |
| 12 | Pending JS queued/flushed | via `ThemeJsApi` (typed wrappers over `evalJs`) and `ExtendedWebView` batching |

**Notes / risks for later phases:**
- Theme has its **own** dispatch logic rather than routing through `WebViewLoadDispatchPolicy`; Phase 4 should route it through the shared controller as an *additional* guard without removing existing systems (Safety Rules 1–3).
- `ThemeRenderSession.themeSignature` comes from `page.renderSignature`; relevant to Phase 6B signature-aware cache invalidation.
- **Do not touch** highlight / scroll-restore code here (Safety Rules 7, 17) — it is actively edited.


---

## Pipeline 2 — Search results

Files: `ui/fragments/search/SearchFragment.kt`, `presentation/search/SearchWebRenderPolicy.kt`.

Uses `ExtendedWebView` (field `:127`); fragment implements `ExtendedWebView.JsLifeCycleListener`. The WebView is **not attached at creation** — it is added to the `SwipeRefreshLayout` only when forum-post results arrive (`:574–577`), which is the documented root of the 0×0 first-open blank screen.

| # | Concern | Location |
|---|---|---|
| 1 | WebView created/init | `onCreateView()` `:224–235` (`init(TRUSTED_STATIC_ARTICLE)`); clients wired lazily in `showData()` `:578–581` |
| 2 | Security profile | `TRUSTED_STATIC_ARTICLE` `:226` → base bridge never enabled |
| 3 | `loadDataWithBaseURL` | `dispatchSearchHtmlLoad()` `:724–730`, base `SearchWebRenderPolicy.SEARCH_BASE_URL` (`https://4pda.to/forum/`) |
| 4 | Render generation | `searchRenderGeneration` `:159`, bumped in `queueSearchHtmlLoad()` `:683`; snapshot via `searchLoadSnapshot()` `:656–663` (reuses `WebViewLoadDispatchPolicy.Snapshot`) |
| 5 | Stale callbacks ignored | `verifySearchRender()` `:784–795` (checks before + inside JS callback); `handleSearchBlankBody()` `:827`; `scheduleSearchLayoutDispatch()` `:733`; `onDomContentComplete()` captures generation `:643–650` |
| 6 | `addJavascriptInterface` | Not found (no JS bridge interface added; static profile). `DialogsHelper` wired via `setDialogsHelper()` `:227` |
| 7 | `removeJavascriptInterface` | Not found (none added) |
| 8 | `clearQueuedJs` | via `ExtendedWebView.onPause()`/teardown; no explicit fragment call found |
| 9 | DOM-ready / page-ready | `onDomContentComplete()` `:643–650` → schedules `verifySearchRender()` after 48 ms |
| 10 | Blank-screen recovery | `verifySearchRender()` + `handleSearchBlankBody()` + `searchBlankRetryCount` `:162`; layout-deferred dispatch via `ViewTreeObserver.OnGlobalLayoutListener` `:733–753` |
| 11 | Timers paused/resumed | inherited `ExtendedWebView.onPause()/onResume()` |
| 12 | Pending JS queued/flushed | uses immediate `webView.evaluateJavascript()` in verify path (`:795`); no batched `evalJs` for content |

**Notes:** Search already routes load decisions through `WebViewLoadDispatchPolicy` (good Phase 9 starting point). Back navigation preserves results because the HTML/WebView state is retained in the fragment. Phase 9 migration order puts Search **first**.


---

## Pipeline 3 — QMS chat

Files: `ui/fragments/qms/chat/QmsChatFragment.kt`, `presentation/qms/chat/QmsWebRenderPolicy.kt`, `presentation/qms/chat/QmsWebRenderProbe.kt`.

Uses `ExtendedWebView` (field `:121`); fragment implements `ExtendedWebView.JsLifeCycleListener` `:98`. Unlike Search/News, QMS **enables the base bridge** so `domContentLoaded()` fires.

| # | Concern | Location |
|---|---|---|
| 1 | WebView created/init | `onCreateView()` `:192–198` (`init(TRUSTED_LOCAL_TEMPLATE)` + `enableBaseBridge()`); added at `:207`; **re-created** on renderer crash in `recoverQmsWebViewAfterRendererGone()` `:2413–2432` |
| 2 | Security profile | `TRUSTED_LOCAL_TEMPLATE` `:196` & `:2415`; base bridge enabled `:197` |
| 3 | `loadDataWithBaseURL` | `dispatchQmsShellLoad()` `:975`, base `https://4pda.to/forum/`, shell HTML from `qmsChatTemplate.generateHtmlBase()` `:942`; deferral via `WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout` `:952–975` |
| 4 | Render generation | `qmsLoadGeneration` + `qmsLoadChatKey`, bumped in `prepareQmsChatSwitchWithoutReload()` `:848–850`, `loadBaseWebContainer()` `:901–903`, recovery `:2381–2382`; DOM probe token `qmsDomReadyProbeToken` `:1621`; queued JS bound via `PendingQmsJs(generation, chatKey, js)` `:2726–2730` |
| 5 | Stale callbacks ignored | central `isCurrentQmsLoad(generation)` `:1325–1336`; used at probe start `:1587–1596`, probe callback `:1635–1656`, exec runnables `:1447/1463/1480/1483`, render-verify `:1981`, `onPageFinished` `:2663/2678/2686`; probe-chain via `QmsWebRenderPolicy.isCurrentDomReadyProbe()` `:1626–1652`; watchdog `:1022` |
| 6 | `addJavascriptInterface` | `onViewCreated()` `:222` (`IChat`, `JS_INTERFACE` `:2623`); recovery `:2426` |
| 7 | `removeJavascriptInterface` | `onDestroyView()` `:334` (+ `removeBaseBridge()` `:335`); recovery `:2404–2405` |
| 8 | `clearQueuedJs` | `prepareQmsChatSwitchWithoutReload()` `:873`; `loadBaseWebContainer()` `:929`; plus auto on `ExtendedWebView.onPause()`/`endWork()` |
| 9 | DOM-ready / page-ready | `onDomContentComplete()` `:1578–1584` → `verifyQmsDomReady()` `:1586–1623` → `runQmsDomReadyProbe()` `:1625–1690` (uses `QmsWebRenderProbe.domReadyProbeScript()`, backoff `QmsWebRenderPolicy.domReadyDelayMs(attempt)`, `MAX_DOM_READY_ATTEMPTS=10`); success → `completeQmsDomReady()` `:1706` → `markQmsDomReadyAndFlush()` `:1726–1774`; `onPageFinished()` `:2649–2689` posts delayed verify (32 ms) |
| 10 | Blank-screen recovery | renderer-gone recovery (`onRenderProcessGone` → `recoverQmsWebViewAfterRendererGone()` `:2381–2432`); DOM-probe retry loop is the soft-recovery path |
| 11 | Timers paused/resumed | inherited `ExtendedWebView.onPause()/onResume()`; messages re-render after resume via the DOM-ready probe loop + queued-JS flush |
| 12 | Pending JS queued/flushed | `PendingQmsJs` queue (`:2726`); flushed in `markQmsDomReadyAndFlush()`; `execQmsJsAfterLayout`/`execQmsJsWithInjectFeedback` runnables |

**Notes:** QMS has the most elaborate DOM-ready probe machinery and is a strong reference for the shared controller's DOM-confirmation contract. Phase 9 migrates QMS **second**. Privacy: never log private QMS message content (Safety Rule 12).


---

## Pipeline 4 — News / article detail

Files: `ui/fragments/news/details/ArticleContentFragment.kt` (~3448 lines), `presentation/articles/detail/ArticleOpenSession.kt`, `ui/fragments/news/details/ArticleWebViewRenderProbe.kt`, `ui/fragments/news/details/ArticleRenderInflightPolicy.kt`, `ui/fragments/news/details/BlankRenderRetryPolicy.kt`.

Uses `ExtendedWebView` with `TRUSTED_STATIC_ARTICLE` — so the `IBase` base bridge is **never** enabled and `domContentLoaded()` never fires. News therefore proves DOM-readiness by injecting a request-ID echo script and exposing a dedicated `INews` interface.

| # | Concern | Location |
|---|---|---|
| 1 | WebView created/init | `onCreateView()` `:181–216` (`init(TRUSTED_STATIC_ARTICLE)` + `prepareForContentLoad()`); re-init in `resetWebViewForArticleReload()` `:1033` |
| 2 | Security profile | `TRUSTED_STATIC_ARTICLE` `:186`/`:1033` (import `:52`) — no base bridge |
| 3 | `loadDataWithBaseURL` | main render `renderArticle()` `loadAction` `:935–942` (base `TRUSTED_ARTICLE_BASE_URL` = `ArticleLinkResolver.ARTICLE_WEBVIEW_BASE_URL`); error placeholder `showArticleErrorPlaceholder()` `:965–971` |
| 4 | Render generation | (a) `articleRequestId` `:140`, `++` per load `:918–919`, injected into HTML so JS echoes via `INews.onArticleDomContentLoaded(requestId)`; `renderProbeToken` `:143`; (b) `ArticleOpenSession.markWebViewLoadStart(requestId)` `:921` / `ArticleOpenSession.kt:116–119`; (c) `ArticleRenderInflightPolicy.Snapshot` (`articleRequestId`+`domContentLoadedRequestId`) `renderInflightSnapshot()` `:445–454`, used at `:834`/`:457` |
| 5 | Stale callbacks ignored | central `confirmArticleRenderFromWebView()` `:1306–1318` (`requestId != articleRequestId` → `StateRaceTrace stale_ignored`); repeated in `probeArticleBodyState` `:1325/1050`, `finalizeArticleRenderConfirmation` `:1419`, `runArticleRenderProbe` `:1585/1590`, `scheduleUnpaintedBodyRecovery` `:1124/1128/1169`, `verifyRenderedContentOrRetry` `:1047–1050`, `handleBlankArticleBodyDetected` `:1182`, probe token `:1572`; comments use separate `isInjectGenerationCurrent()` `:2064` / `commentsBindEpoch` `:628` |
| 6 | `addJavascriptInterface` | `attachNewsBridge()` `:2859–2865` (`INews`, `JS_INTERFACE` `:3291`); called from `onCreateView` `:214`, `renderArticle` `:882`, reset `:1036`, comments bind `:466`, `onPageFinished` `:3062` |
| 7 | `removeJavascriptInterface` | `detachNewsBridge()` `:2867+` |
| 8 | `clearQueuedJs` | via `ExtendedWebView` lifecycle; reset path `resetWebViewForArticleReload()` |
| 9 | DOM-ready / page-ready | injected request-ID script → `onArticleDomContentLoaded()` → `confirmArticleRenderFromWebView()`; probe via `ArticleWebViewRenderProbe` (`runArticleRenderProbe` `:1585`); `onPageFinished` `:3062` |
| 10 | Blank-screen recovery | `BlankRenderRetryPolicy` + `scheduleUnpaintedBodyRecovery()` `:1124`, `handleBlankArticleBodyDetected()` `:1182`, `verifyRenderedContentOrRetry()` `:1047`, `resetWebViewForArticleReload()` `:1033` |
| 11 | Timers paused/resumed | `webView.onResume()` before load `:935`; inherited `onPause()/onResume()` |
| 12 | Pending JS queued/flushed | comments injection uses **callback-based** `evaluateJavascript` (generation-guarded) — these must NOT be converted to batched `evalJs` (Safety Rule 11) |

**Notes / callback-based JS that must stay immediate:** comments binding/injection paths use callback-based `evaluateJavascript` keyed on `commentsBindEpoch`/inject generation. Phase 5/9 must not batch these. Phase 9 migrates News **last**.


---

## Cross-pipeline comparison (duplicated concerns)

| Concern | Theme | Search | QMS | News |
|---|---|---|---|---|
| WebView class | `ExtendedWebView` | `ExtendedWebView` | `ExtendedWebView` | `ExtendedWebView` |
| Security profile | `TRUSTED_LOCAL_TEMPLATE` | `TRUSTED_STATIC_ARTICLE` | `TRUSTED_LOCAL_TEMPLATE` | `TRUSTED_STATIC_ARTICLE` |
| Base bridge (`IBase`) | Yes | No | Yes | No |
| DOM-ready source | `domContentLoaded()` bridge | injected JS + 48 ms verify | `domContentLoaded()` + probe loop | injected request-ID echo + probe |
| Render generation field | `renderGeneration` + token + session + page id | `searchRenderGeneration` | `qmsLoadGeneration`+`qmsLoadChatKey` | `articleRequestId` (+ session + inflight) |
| Uses `WebViewLoadDispatchPolicy` | partial / own logic | yes | yes (deferral) | yes (via inflight policy) |
| Stale-callback guard | lifecycle claim + token + view gen | generation compare | `isCurrentQmsLoad()` | `requestId` compare |
| Blank-screen recovery | ensure-render + watchdogs | blank verify + retry | renderer-gone recovery + probe | `BlankRenderRetryPolicy` + reset |
| Bridge add/remove pairing | `ThemeBridgeHandler` init/cleanup | none | onView/onDestroy + recovery | attach/detach helpers |

**Common duplicated logic** (the long-term consolidation target — Phases 2–4, 9): `loadDataWithBaseURL` dispatch, render-generation creation, DOM/page-ready probing, stale-callback rejection, `evaluateJavascript` scheduling, blank-screen recovery, bridge registration/removal, queued-JS cleanup. Each pipeline re-implements these independently, so a fix in one does not propagate to the others — exactly the motivation for the shared `WebViewRenderSession` (Phase 2) and `WebViewRenderController` (Phase 3).

## "Not enough evidence" / open items

- Theme `clearQueuedJs` explicit call sites: covered via `ExtendedWebView.onPause()`/`endWork()` and controller reset; exact fragment-level call lines were not exhaustively enumerated.
- Search/News exact `removeJavascriptInterface` line numbers for News (`detachNewsBridge` body) truncated in source scan — method confirmed to exist at `:2867`.
- These do not affect Phase 1 conclusions; verify precise lines when editing those files in later phases.

## Recommendations feeding later phases

1. **Phase 2 (`WebViewRenderSession`)** — generalize `ThemeRenderSession`; add `Owner { THEME, SEARCH, QMS, NEWS, STATIC }`; keep existing per-feature systems.
2. **Phase 3 (`WebViewRenderController`)** — wrap `WebViewLoadDispatchPolicy`; do not move feature-specific logic in.
3. **Phase 4 (Theme integration)** — additive diagnostics only; never touch highlight/scroll-restore (Safety Rules 7, 17).
4. **Phase 5 (JS batching)** — add metrics + post-destroy enqueue guard to `ExtendedWebView`; do NOT batch News callback-based comment injection (Safety Rule 11).
5. **Phase 6B (cache)** — extend `ThemePageMemoryCache` with signature-aware invalidation; its `Key` currently lacks `renderSignature`.
6. **Phase 8 (Smart Preload)** — reuse `ThemePageMemoryCache` as the store; kill switch default OFF; disabled in Slow Mode.
7. **Phase 10 (diagnostics)** — build on `FpdaDebugLog` only.
