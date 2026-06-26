# PROPDA WebView Rendering Stabilization & Performance Optimization Task

## Purpose

This document is a detailed implementation prompt/specification for an AI coding agent.

> **Revision note (project-tuned):** This spec was originally AI-generated and has been verified and corrected against the actual ForPDA codebase. File paths, class names, and method signatures referenced below were confirmed to exist unless explicitly marked as *new*. Where the original draft guessed, the guesses have been replaced with verified facts (see the **Verified project facts** section).

The project is an Android client for the 4PDA forum. The application already contains multiple WebView-based rendering pipelines:

- Forum topic pages / Hybrid Theme Mode
- Search results
- QMS chat
- News/article detail pages
- Static WebView pages such as forum rules and announcements

The goal is to improve stability, rendering reliability, performance, and maintainability of these WebView pipelines.

Do **not** work on Edge-to-edge or Material You polish in this task.

---

# Role

You are a Senior Android Engineer, WebView Specialist, UI Architect, Performance Engineer, Security Auditor, and QA Engineer.

Your work must be based only on actual source code.

Do not guess.  
Do not invent files.  
Do not perform large rewrites.  
Prefer small, safe, testable changes.

If there is not enough evidence in the code, explicitly write:

```text
Not enough evidence.
```

---

# Preconditions (read before starting)

```text
1. The working tree is currently DIRTY: many of the exact files this task touches
   (ExtendedWebView.kt, ThemeRenderSession.kt, ThemeWebController.kt,
   TemplateCssComposer.kt, MainDataStore.kt, ThemeInfiniteScrollController.kt, ...)
   have uncommitted changes.
   => Before starting any phase, ensure these in-progress changes are committed or
      stashed so the agent's changes are isolated and revertible.
2. Material You / highlight / scroll-restore code is "hot" (actively edited) right now.
   This task MUST NOT touch Material You, edge-to-edge, highlight, or scroll-restore
   behavior (see Safety Rules). Keep clear of those zones to avoid merge conflicts.
3. Run one Sprint at a time. After each Sprint: build + run unit tests, then commit.
   Do NOT attempt the whole document in a single pass.
```

---

# Verified project facts

These were confirmed against the real source. Use them; do not re-guess.

## Settings storage (canonical mechanism)

The project uses a DataStore-backed mirror, NOT raw SharedPreferences for new settings.

```text
forpdateam.ru.forpda.model.preferences.MainPreferencesHolder   <- public API surface
forpdateam.ru.forpda.model.datastore.MainDataStore             <- actual storage + in-memory mirror
forpdateam.ru.forpda.common.Preferences                        <- enums / keys (Preferences.Main.*)
```

Established per-setting pattern (follow it exactly for any new setting):

```kotlin
// MainDataStore: observeXxxFlow(): Flow<T>, getXxxImmediate(): T, suspend setXxx(value: T)
// MainPreferencesHolder: re-exposes observeXxxFlow() / getXxx() / suspend setXxx()
```

=> For Phase 7 (Slow WebView Mode) add the new flag through
`MainDataStore` + `MainPreferencesHolder` using `observe…Flow()/get…Immediate()/set…`.
Do NOT introduce a parallel SharedPreferences path. Do NOT invent `MainPreferencesHolder`
methods that follow a different naming convention.

## Existing theme page cache (important — affects Phase 6B & Phase 8)

A page-level memory cache ALREADY exists and overlaps heavily with the proposed
`ThemeHtmlMemoryCache`:

```text
forpdateam.ru.forpda.model.repository.theme.ThemePageMemoryCache
```

It already caches the parsed `ThemePage` (including `html`, `postsFragmentHtml`,
`renderSignature`, scroll-restore fields) with:

```text
Key(topicId, st, hatOpen, pollOpen)
TTL (DEFAULT_TTL_MS = 7 min), LRU (maxEntries = 24, access-ordered LinkedHashMap)
get()/put()/invalidateTopic()/clear()
shouldSkipCache(url)  // skips findpost/getnewpost/pid/p= URLs
copyForCache()        // defensive deep copy
```

=> **Decision required before Phase 6B:** do NOT blindly create a second cache.
First evaluate whether the goals of `ThemeHtmlMemoryCache` are already met by
`ThemePageMemoryCache`. Prefer **extending `ThemePageMemoryCache`** (e.g. add
signature-based invalidation hooks) over adding a parallel HTML cache. Only create
a separate `ThemeHtmlMemoryCache` if there is a concrete need for raw-HTML caching
that `ThemePageMemoryCache` cannot serve, and document that need explicitly.
Phase 8 Smart Preload MUST reuse `ThemePageMemoryCache` as its store, not a new one.

## Other confirmed facts

```text
- WebViewLoadDispatchPolicy is an `object` with: shouldSkipInflightDuplicate(),
  shouldForceEnsureRender(), shouldDeferLoadUntilLayout(), plus a Snapshot data class.
  Test already exists: WebViewLoadDispatchPolicyTest.
- ThemeRenderSession fields (real): topicId, page, renderGenerationId, bridgeToken,
  themeSignature, createdAt. It has create(page, bridgeToken) + logCreated(...) and is
  DEBUG-gated via FpdaDebugLog. There is currently NO ThemeRenderSessionTest.
- ThemeBridgeHandler real API is init()/cleanup() (NOT enableBaseBridge() directly);
  it calls webView.enableBaseBridge()/removeBaseBridge()/add/removeJavascriptInterface()
  and jsInterface.cancel(). Interface names: "IThemePresenter", "IThemeView".
- ExtendedWebView batching is real: pendingJs (StringBuilder), jsFlushRunnable,
  evalJs(script) [batched, 16 ms], evalJs(script, callback) [immediate], flushQueuedJs(),
  clearQueuedJs(), plus a private evalJsImmediate(). clearQueuedJs() is already called on
  destroy paths.
- TemplateCssComposer cache is real: ComposeCacheKey, ComposeCacheEntry, cachedEntry,
  cachedKey, composeCached(); exposes compose() and composeHash(). Test exists:
  TemplateCssComposerCacheTest.
- FpdaDebugLog (forpdateam.ru.forpda.diagnostic) is the existing structured debug logger.
  Reuse it for all new diagnostics instead of android.util.Log.
```

---

# Current Codebase Context

The following components already exist in the current project and must be reused where possible.

## Existing shared WebView load policy

File:

```text
app/src/main/java/forpdateam/ru/forpda/common/webview/WebViewLoadDispatchPolicy.kt
```

Existing responsibilities:

```kotlin
shouldSkipInflightDuplicate()
shouldForceEnsureRender()
shouldDeferLoadUntilLayout()
```

This file is already a good foundation for a shared WebView render controller.

---

## Existing ThemeRenderSession

File:

```text
app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderSession.kt
```

Current fields:

```kotlin
topicId
page
renderGenerationId
bridgeToken
themeSignature
createdAt
```

Important note:

`ThemeRenderSession` currently mirrors multiple independent validity systems:

- ThemeRenderGuard token
- ThemePage.renderGenerationId
- ThemeWebController render generation

Do not remove the old systems immediately. Use this class as a bridge toward a cleaner shared render session model.

---

## Existing ThemeBridgeHandler

File:

```text
app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt
```

Current responsibilities:

```kotlin
addJavascriptInterface()
removeJavascriptInterface()
enableBaseBridge()
removeBaseBridge()
jsInterface.cancel()
```

This is good. Keep this pattern.

---

## Existing ExtendedWebView JS batching

File:

```text
app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt
```

Existing batching logic:

```kotlin
pendingJs
jsFlushRunnable
evalJs()
flushQueuedJs()
clearQueuedJs()
```

Current behavior:

- `evalJs(script: String)` queues JS.
- JS commands are batched with a 16 ms delay.
- `evalJs(script, callback)` still executes immediately via `evaluateJavascript`.
- `clearQueuedJs()` clears pending JS and queued actions.

Reuse this. Do not replace it with a new unrelated batching system.

---

## Existing TemplateCssComposer cache

File:

```text
app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt
```

Current cache structures:

```kotlin
ComposeCacheKey
ComposeCacheEntry
cachedEntry
cachedKey
composeCached()
```

This means CSS override caching is already partially implemented. Extend it only if needed.

---

# Main Problem

The app currently has several WebView pipelines that solve similar lifecycle and rendering problems independently:

```text
Theme
Search
QMS
News
```

Common duplicated concerns:

```text
loadDataWithBaseURL
render generation
DOM-ready probes
page-ready probes
JS lifecycle callbacks
evaluateJavascript scheduling
blank screen recovery
bridge registration/removal
queued JS cleanup
stale callback protection
```

Because these are handled separately, fixes made in one area can leave the same bug in another area.

The long-term goal is a shared WebView rendering layer used by Theme/Search/QMS/News, but this must be introduced gradually and safely.

---

# Non-Goals

Do not implement these in this task:

```text
Edge-to-edge UI
Material You polish
full UI redesign
new offline reading system
full rewrite of theme.js
replacement of WebView with RecyclerView
large-scale architecture rewrite
```

---

# Phase 1 — WebView Pipeline Inventory

## Goal

Create a source-code-based inventory of all WebView rendering pipelines.

## Files to inspect

```text
app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt
app/src/main/java/forpdateam/ru/forpda/common/webview/WebViewLoadDispatchPolicy.kt

app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt
app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt
app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt
app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApi.kt
app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderSession.kt

app/src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt
app/src/main/java/forpdateam/ru/forpda/presentation/search/SearchWebRenderPolicy.kt

app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt
app/src/main/java/forpdateam/ru/forpda/presentation/qms/chat/QmsWebRenderPolicy.kt
app/src/main/java/forpdateam/ru/forpda/presentation/qms/chat/QmsWebRenderProbe.kt

app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt
app/src/main/java/forpdateam/ru/forpda/presentation/articles/detail/ArticleOpenSession.kt
app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleWebViewRenderProbe.kt
app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleRenderInflightPolicy.kt
app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/BlankRenderRetryPolicy.kt
```

Also inventory the existing shared/diagnostic infrastructure (already present):

```text
app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt
app/src/main/java/forpdateam/ru/forpda/diagnostic/FpdaDebugLog.kt
app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderGuard.kt
```

## Required analysis

For each WebView pipeline document:

```text
1. Where WebView is created or initialized
2. Which WebViewSecurityProfile is used
3. Where loadDataWithBaseURL is called
4. Where render generation/session/request ID is created
5. Where stale generation/session callbacks are ignored
6. Where addJavascriptInterface is called
7. Where removeJavascriptInterface is called
8. Where clearQueuedJs is called
9. Where DOM-ready/page-ready is handled
10. Where blank-screen recovery exists
11. Where WebView timers are paused/resumed
12. Where pending JS is queued/flushed
```

## Deliverable

Create:

```text
docs/WEBVIEW_PIPELINE_INVENTORY.md
```

Do not change production code in this phase unless a trivial safety issue is found.

Additionally, the inventory MUST include a short subsection
**"Existing reusable infrastructure"** documenting `ThemePageMemoryCache`,
`FpdaDebugLog`, `WebViewLoadDispatchPolicy`, and `ThemeRenderGuard`, so later phases
extend them instead of duplicating them.

---

# Phase 2 — Introduce Shared WebViewRenderSession

## Goal

Add a generic render session model for all WebView pipelines.

## New file

```text
app/src/main/java/forpdateam/ru/forpda/common/webview/WebViewRenderSession.kt
```

## Suggested model

```kotlin
data class WebViewRenderSession(
    val owner: Owner,
    val targetId: Int,
    val contentHash: Int,
    val renderGeneration: Int,
    val bridgeToken: String?,
    val createdAt: Long,
) {
    enum class Owner {
        THEME,
        SEARCH,
        QMS,
        NEWS,
        STATIC
    }
}
```

## Requirements

1. Do not delete `ThemeRenderSession`.
2. Do not delete `ThemeRenderGuard`.
3. Do not delete existing per-feature render generation immediately.
4. The new shared session must initially work as an additional guard/diagnostic layer.
5. Existing behavior must not change during the first integration.
6. Add tests for session equality, freshness, and owner/target matching.

## Suggested helper methods

```kotlin
fun isSameTarget(other: WebViewRenderSession): Boolean
fun isCurrent(active: WebViewRenderSession?): Boolean
fun isStaleComparedTo(active: WebViewRenderSession?): Boolean
```

## Tests

Create:

```text
app/src/test/java/forpdateam/ru/forpda/common/webview/WebViewRenderSessionTest.kt
```

---

# Phase 3 — Introduce Shared WebViewRenderController

## Goal

Create a small shared controller for common WebView render lifecycle operations.

## New file

```text
app/src/main/java/forpdateam/ru/forpda/common/webview/WebViewRenderController.kt
```

## Responsibilities

The controller should manage:

```text
1. Active WebViewRenderSession
2. Render request state
3. Duplicate load protection
4. 0x0 WebView load deferral
5. loadDataWithBaseURL dispatch tracking
6. DOM confirmation
7. Page confirmation
8. Stale callback detection
9. Cleanup on destroy
10. JS queue cleanup
```

## Use existing policy

Reuse:

```text
WebViewLoadDispatchPolicy.kt
```

Do not duplicate its logic.

## Suggested methods

```kotlin
fun beginRender(
    owner: WebViewRenderSession.Owner,
    targetId: Int,
    contentHash: Int,
    bridgeToken: String?,
    force: Boolean = false
): WebViewRenderSession

fun shouldSkipDuplicate(session: WebViewRenderSession, force: Boolean): Boolean

fun shouldDeferUntilLayout(webView: ExtendedWebView): Boolean

fun markLoadDispatched(session: WebViewRenderSession)

fun markDomConfirmed(session: WebViewRenderSession)

fun markPageConfirmed(session: WebViewRenderSession)

fun isCurrent(session: WebViewRenderSession): Boolean

fun cleanup()
```

## Important

Do not make this class too large.  
Do not move feature-specific logic into it.

This controller must only handle generic WebView render lifecycle state.

## Tests

Create:

```text
app/src/test/java/forpdateam/ru/forpda/common/webview/WebViewRenderControllerTest.kt
```

Test scenarios:

```text
duplicate render is skipped only after real load dispatch
render is forced if DOM was never confirmed
0x0 WebView load is deferred
stale session callback is ignored
cleanup invalidates active session
```

---

# Phase 4 — Integrate Shared Controller into Theme Safely

## Goal

Start with Theme because it is the most complex WebView pipeline.

## Files to inspect/change

```text
ThemeFragmentWeb.kt
ThemeWebController.kt
ThemeBridgeHandler.kt
ThemeJsApi.kt
ThemeRenderSession.kt
ThemeRenderGuard.kt
ThemePage.kt
```

## Requirements

1. In `ThemeWebController.renderThemePage()`, create a `WebViewRenderSession`.
2. Use `WebViewRenderController` before `loadDataWithBaseURL`.
3. Continue using existing `renderGeneration`.
4. Continue using existing `ThemeRenderGuard`.
5. Continue using existing `ThemeRenderSession`.
6. Initially only add diagnostics and safety checks.
7. If old and new generation/session systems disagree, log it in debug builds.
8. Do not alter scroll restore behavior in this phase.
9. Do not alter highlight behavior in this phase.

## Expected behavior

No visible behavior should change.

This phase is successful if:

```text
Theme render still works
Back navigation still works
Refresh still works
Rotation still works
No new white screens appear
Debug logs show render sessions correctly
```

---

# Phase 5 — Strengthen JS Batching

## Current state

`ExtendedWebView` already batches JS commands through:

```kotlin
pendingJs
jsFlushRunnable
evalJs()
flushQueuedJs()
clearQueuedJs()
```

## Goal

Improve observability and safety of the existing batching system.

## Files

```text
ExtendedWebView.kt
ThemeJsApi.kt
ThemeWebController.kt
ArticleContentFragment.kt
QmsChatFragment.kt
SearchFragment.kt
```

## Tasks

1. Add maximum batch size protection.
2. Add debug-only metrics:
   - JS command count
   - JS batch flush count
   - average batch size
   - number of commands dropped by `clearQueuedJs`
   - number of commands ignored after WebView destroy
3. Prevent enqueueing JS after destroy.
4. Add an explicit immediate-eval path for commands that must not be delayed.
5. Audit `ThemeJsApi` direct `evaluateJavascript` calls and convert safe fire-and-forget commands to batched `evalJs`.
6. Do not convert commands with callbacks unless correctness is proven.

## New optional file

```text
app/src/main/java/forpdateam/ru/forpda/common/webview/WebViewJsBatchMetrics.kt
```

## Tests

Create or update:

```text
app/src/test/java/forpdateam/ru/forpda/common/webview/WebViewJsBatchMetricsTest.kt
```

---

# Phase 6 — CSS and HTML Cache

## Current state

`TemplateCssComposer` already caches CSS overrides.

## Goal

Extend caching safely without stale HTML rendering.

## Part A — CSS cache audit

File:

```text
TemplateCssComposer.kt
```

Tasks:

1. Verify the cache key includes every setting that affects CSS.
2. Ensure cache invalidates on:
   - dark/light mode change
   - palette change
   - font mode change
   - density/reader mode change if it affects CSS
   - avatar-related CSS if applicable
3. Add tests if missing.

Existing related test:

```text
TemplateCssComposerCacheTest
```

Update if needed.

---

## Part B — Theme page memory cache (extend, do not duplicate)

> **Project-tuned:** `ThemePageMemoryCache` ALREADY exists and already caches the parsed
> `ThemePage` including `html`, `postsFragmentHtml`, and `renderSignature`, with TTL + LRU
> + `invalidateTopic()` + `shouldSkipCache(url)`. Creating a separate `ThemeHtmlMemoryCache`
> would duplicate it. **Default action: extend `ThemePageMemoryCache`.**

## Step 0 — Decision gate (do this first)

```text
1. Read ThemePageMemoryCache.kt fully.
2. Decide: can the caching goals below be met by extending ThemePageMemoryCache?
   - If YES (expected): extend it. Do NOT create ThemeHtmlMemoryCache.
   - If NO: write a short justification in the deliverable explaining the concrete
     gap, then (only then) add a new cache that COMPOSES with, not replaces, the
     existing one.
```

## What to add to `ThemePageMemoryCache` (extension path)

The current `Key(topicId, st, hatOpen, pollOpen)` does NOT include the theme signature,
so a palette/density/font change could return a stale-styled page. Address this:

```text
1. Add renderSignature/themeSignature awareness to invalidation:
   - on theme/palette change, density change, font mode change, avatar mode change,
     blacklist change, post edit, new reply: call clear() or invalidateTopic().
2. Verify get() never returns an entry whose renderSignature no longer matches the
   current compose signature (TemplateCssComposer.composeHash() / page.renderSignature).
3. Keep existing rules intact: never cache findpost/getnewpost/pid/p= URLs
   (shouldSkipCache), keep TTL + LRU.
```

## Only-if-needed new file (escape hatch)

```text
app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemeHtmlMemoryCache.kt
```

## Suggested model (only if the decision gate proves a separate cache is required)

```kotlin
data class ThemeHtmlCacheEntry(
    val topicId: Int,
    val page: Int,
    val themeSignature: String,
    val html: String,
    val postsFragmentHtml: String?,
    val createdAt: Long
)
```

## Rules

1. Cache only the last 3–5 pages.
2. Never cache empty HTML.
3. Never cache error pages.
4. Never reuse cache if `themeSignature` changed.
5. Invalidate cache on:
   - theme/palette change
   - density change
   - font mode change
   - avatar mode change
   - blacklist change
   - post edit
   - new reply
   - topic reload with changed content hash
6. Cache should mainly help:
   - back navigation
   - returning from image viewer
   - rotation
   - recently opened topic restore

## Tests

If extending `ThemePageMemoryCache` (default path), add/extend:

```text
app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCacheTest.kt
```

If a separate cache was justified, create:

```text
app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemeHtmlMemoryCacheTest.kt
```

Test (either way):

```text
same key returns cached page/HTML
different signature misses / invalidates cache
empty or error HTML is rejected
LRU eviction works
manual invalidation (invalidateTopic / clear) works
signature change invalidates previously cached entry
```

---

# Phase 7 — Slow WebView Mode

## Goal

Add a compatibility mode for weak devices or problematic Android System WebView versions.

## User-facing name

```text
Compatibility WebView mode
```

or:

```text
Slow WebView mode
```

## Suggested settings location

```text
Settings → Performance
```

or:

```text
Settings → WebView
```

## Suggested preference key

```text
main.webview.compatibility_mode
```

## Storage (verified mechanism — follow exactly)

The flag MUST go through the existing DataStore mirror, using the established
`observeXxxFlow()` / `getXxxImmediate()` / suspend `setXxx()` pattern.

Confirmed files to edit (these all exist):

```text
app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt            // add key/enum under Preferences.Main if needed
app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt // observeCompatibilityModeFlow()/getCompatibilityModeImmediate()/setCompatibilityMode()
app/src/main/java/forpdateam/ru/forpda/model/preferences/MainPreferencesHolder.kt // re-expose the three accessors
app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt
app/src/main/res/xml/preferences.xml
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml   // EN strings live here too — keep both in sync
app/src/main/res/values/arrays.xml       // only if a list/enum UI is used
```

Do NOT add a parallel SharedPreferences read/write path. Mirror the exact naming
style already used in `MainDataStore`/`MainPreferencesHolder` (e.g. boolean flag
`compatibilityMode`).

## Behavior when enabled

Slow WebView Mode must reduce aggressive rendering behavior:

```text
1. Disable Smart Preload
2. Increase JS batching/debounce delay
3. Reduce DOM probe frequency
4. Reduce scroll restore retry count
5. Disable aggressive highlight reapply
6. Disable highlight CSS transitions if safe
7. Disable speculative render
8. Reduce unnecessary WebView animations
9. Prefer stability over speed
```

## Important

Slow Mode must not remove functionality.  
It must only reduce aggressive background work.

## Suggested policy class

New file:

```text
app/src/main/java/forpdateam/ru/forpda/common/webview/SlowWebViewModePolicy.kt
```

Suggested methods:

```kotlin
data class SlowWebViewModeConfig(
    val enabled: Boolean,
    val jsBatchDelayMs: Long,
    val maxScrollRestoreRetries: Int,
    val allowSmartPreload: Boolean,
    val allowAggressiveHighlightReapply: Boolean,
    val allowSpeculativeRender: Boolean,
)
```

## Tests

Create:

```text
app/src/test/java/forpdateam/ru/forpda/common/webview/SlowWebViewModePolicyTest.kt
```

---

# Phase 8 — Smart Preload of Next Topic Page

> **Project-tuned (risk):** This is the HIGHEST-RISK phase and is furthest from the
> "do not break" goal (races, extra traffic, stale content). Do it LAST, only after
> Theme integration (Phase 4) and caching (Phase 6) are proven stable. It MUST ship
> behind a kill switch (default OFF) and MUST be disabled when Slow WebView Mode is on.
> Consider splitting this into its own separate task/PR.

## Goal

Improve perceived speed of infinite scroll by preloading the next page before the user reaches the end.

## Files to inspect/change

```text
ThemeInfiniteScrollController.kt
ThemeWebController.kt
ThemeViewModel.kt
ThemeUseCase.kt
ThemePageMemoryCache.kt   // REUSE this as the preload store — do NOT add a new cache
```

> Preload results MUST be stored in the existing `ThemePageMemoryCache` (it already
> deep-copies via `copyForCache()` and has TTL/LRU/invalidation). Do not introduce a
> separate preload cache.

## Behavior

When the user reaches approximately 70–80% of the current topic page:

```text
1. Check if there is a next page
2. Check if no preload is already running
3. Check Slow WebView Mode is disabled
4. Check battery saver / low power conditions if available
5. Check network conditions if available
6. Start preloading only one next page
7. Store result in memory cache
8. When the user reaches the bottom, use the preloaded result
```

## Restrictions

```text
Preload only one page ahead
Do not preload in Slow WebView Mode
Do not preload during active refresh
Do not preload while another topic is being opened
Do not preload after topic switch
Do not preload when server returned an error
Do not preload on repeated failures
Do not preload if current page is already last page
```

## Suggested policy file

```text
app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeSmartPreloadPolicy.kt
```

## Suggested tests

```text
app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeSmartPreloadPolicyTest.kt
```

Test:

```text
preload starts at threshold
preload does not start below threshold
preload disabled in Slow WebView Mode
preload blocked during refresh
preload blocked when no next page
stale topic preload result is ignored
```

---

# Phase 9 — Gradual Migration of Search/QMS/News

## Goal

After Theme integration is stable, migrate the other WebView pipelines one by one.

## Order

```text
1. Search
2. QMS
3. News
```

Do not migrate all at once.

---

## Search migration

Files:

```text
SearchFragment.kt
SearchWebRenderPolicy.kt
```

Tasks:

```text
1. Add WebViewRenderSession
2. Add WebViewRenderController
3. Keep existing searchRenderGeneration initially
4. Compare old generation and new session in debug logs
5. Ensure back navigation keeps results
6. Ensure blank screen recovery still works
```

---

## QMS migration

Files:

```text
QmsChatFragment.kt
QmsWebRenderPolicy.kt
QmsWebRenderProbe.kt
```

Tasks:

```text
1. Add WebViewRenderSession
2. Add WebViewRenderController
3. Keep existing QMS generation initially
4. Ensure bridge cleanup is safe
5. Ensure DOM-ready fallback remains safe
6. Ensure messages render after pause/resume
```

---

## News migration

Files:

```text
ArticleContentFragment.kt
ArticleOpenSession.kt
ArticleWebViewRenderProbe.kt
```

Tasks:

```text
1. Add WebViewRenderSession
2. Add WebViewRenderController
3. Keep ArticleOpenSession initially
4. Avoid converting callback-based evaluateJavascript calls unless proven safe
5. Ensure article body appears reliably
6. Ensure comments binding still works
```

---

# Phase 10 — Diagnostics and Metrics

## Goal

Make rendering problems measurable.

## New file

```text
app/src/main/java/forpdateam/ru/forpda/diagnostic/WebViewRenderDiagnostics.kt
```

> **Project-tuned:** Implement this on top of the existing `FpdaDebugLog`
> (`forpdateam.ru.forpda.diagnostic.FpdaDebugLog`) and gate everything behind
> `BuildConfig.DEBUG`, mirroring `ThemeRenderSession.logCreated()`. Do NOT use raw
> `android.util.Log`. Reuse an existing `FpdaDebugLog` TAG or add one new tag.

## Metrics to log in debug builds

```text
render_requested
load_dispatched
load_deferred_zero_size
duplicate_render_skipped
dom_confirmed
page_confirmed
render_forced_after_missed_dom
stale_callback_ignored
bridge_attached
bridge_removed
queued_js_flushed
queued_js_cleared
html_cache_hit
html_cache_miss
smart_preload_started
smart_preload_hit
smart_preload_miss
slow_webview_mode_enabled
```

## Log fields

```text
owner
targetId
contentHash
generation
bridgeTokenPresent
loadMs
domConfirmedMs
pageConfirmedMs
cacheHit
jsBatchCount
staleCallbackCount
```

## Do not log

```text
raw HTML
cookies
auth tokens
personal messages
private QMS content
full URLs with sensitive query parameters
```

---

# Phase 11 — Testing Plan

## Required unit tests

Create or update:

```text
WebViewRenderSessionTest
WebViewRenderControllerTest
WebViewLoadDispatchPolicyTest        // already exists — keep green
WebViewJsBatchMetricsTest
ThemePageMemoryCacheTest             // extend (preferred) — ThemeHtmlMemoryCacheTest only if separate cache was justified
SlowWebViewModePolicyTest
ThemeSmartPreloadPolicyTest
ThemeRenderSessionTest
ThemeJsInterfaceTest
ThemeUseCaseTest
ThemeTemplateTest
SearchWebRenderPolicyTest
QmsWebRenderPolicyTest
ArticleOpenSessionTest
```

## Manual QA checklist

Test on at least:

```text
Android 8
Android 10
Android 12
Android 13
Android 14
Android 15
```

Scenarios:

```text
Open a normal topic
Open a large topic
Open unread topic
Open already-read topic
Back navigation inside topic
Back navigation from search results
Refresh topic in the middle
Refresh topic at bottom
Rotate while topic is loading
Rotate after topic loaded
Open image viewer and return
Fast switch between 5 topics
Infinite scroll down
Infinite scroll up
Slow WebView Mode ON
Slow WebView Mode OFF
Search results open/back
QMS open/close/pause/resume
News article open/back/comments binding
App background/foreground
Process death restore if feasible
```

---

# Implementation Order

## Sprint 1 — Inventory and foundation

```text
1. Create docs/WEBVIEW_PIPELINE_INVENTORY.md
2. Add WebViewRenderSession
3. Add WebViewRenderDiagnostics
4. Add unit tests for session and diagnostics helpers
```

## Sprint 2 — Shared controller

```text
1. Add WebViewRenderController
2. Reuse WebViewLoadDispatchPolicy
3. Add tests for duplicate load, deferred load, DOM confirmation, cleanup
```

## Sprint 3 — Theme safe integration

```text
1. Integrate controller into ThemeWebController
2. Keep old ThemeRenderGuard and renderGeneration
3. Add debug comparison logs
4. Verify topic open/back/refresh/rotation
```

## Sprint 4 — JS batching hardening

```text
1. Add metrics and limits to ExtendedWebView batching
2. Prevent JS enqueue after destroy
3. Audit ThemeJsApi fire-and-forget commands
4. Do not convert callback-based JS unless proven safe
```

## Sprint 5 — Cache

```text
1. Audit TemplateCssComposer cache key (TemplateCssComposerCacheTest exists)
2. Run the Phase 6B decision gate: extend ThemePageMemoryCache (default) instead of
   adding ThemeHtmlMemoryCache
3. Add signature-based invalidation rules to the existing cache
4. Add/extend ThemePageMemoryCacheTest
```

## Sprint 6 — Slow WebView Mode

```text
1. Add setting and DataStore support
2. Add SlowWebViewModePolicy
3. Apply policy to preload, JS debounce, scroll retries, highlight reapply
4. Add tests
```

## Sprint 7 — Smart Preload

```text
1. Add ThemeSmartPreloadPolicy
2. Integrate with ThemeInfiniteScrollController
3. Cache preload result
4. Ignore stale preload result after topic switch
5. Add tests
```

## Sprint 8 — Search/QMS/News migration

```text
1. Migrate Search
2. Migrate QMS
3. Migrate News
4. Keep old per-feature generation systems until the new shared system is proven stable
```

---

# Safety Rules

Do not violate these rules:

```text
1. Do not delete ThemeRenderGuard immediately.
2. Do not delete ThemeRenderSession immediately.
3. Do not delete existing renderGeneration fields immediately.
4. Do not rewrite theme.js entirely.
5. Do not touch Edge-to-edge.
6. Do not touch Material You polish.
7. Do not change topic scroll restore and highlight behavior in the same commit.
8. Do not enable Smart Preload without a kill switch.
9. Do not preload while Slow WebView Mode is enabled.
10. Do not cache HTML without renderSignature/themeSignature validation.
11. Do not batch JS calls that require immediate callback-based results.
12. Do not log private QMS content or raw HTML.
13. Do not migrate Theme/Search/QMS/News all at once.
14. Do not start any phase on a dirty working tree — commit/stash first, work per Sprint.
15. Do not create ThemeHtmlMemoryCache without passing the Phase 6B decision gate;
    prefer extending the existing ThemePageMemoryCache.
16. Do not add a parallel SharedPreferences path for the Slow WebView Mode flag —
    use MainDataStore/MainPreferencesHolder (observe/get/set) like every other setting.
17. Do not touch highlight or scroll-restore code (currently being actively edited).
18. Use FpdaDebugLog + BuildConfig.DEBUG for all new diagnostics; never android.util.Log.
```

---

# Acceptance Criteria

The implementation is successful only if:

```text
1. Topic pages open reliably.
2. Search results are preserved after back navigation.
3. QMS still renders correctly after pause/resume.
4. News article pages still render body and comments correctly.
5. WebView blank-screen recovery still works.
6. Stale JS callbacks are ignored.
7. JS bridge is removed on destroy.
8. Queued JS is cleared on destroy/new render.
9. Slow WebView Mode reduces aggressive behavior.
10. Smart Preload improves infinite scroll without stale topic contamination.
11. CSS/HTML cache never renders stale theme content.
12. Existing tests pass.
13. New tests cover the new policies/controllers.
```

---

# Final Deliverables

The AI agent must provide:

```text
1. Summary of inspected files
2. List of implemented changes
3. Files changed
4. New tests added
5. Manual QA checklist result
6. Known limitations
7. Any code paths marked Not enough evidence
8. Build/test command results
```
