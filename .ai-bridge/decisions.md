# Decisions

Append-only record of architectural / process decisions that should remain
stable across sessions and agent handoffs. Format: one dated section per
decision, with ticket ID, status, evidence pointer, and a short rationale.

---

## 2026-06-22 — L01 closed: JS-bridge security audit shipped

- **Ticket:** AUDIT-L01 (docs-only).
- **Status:** ✅ Closed.
- **Evidence:** `docs/JS_BRIDGE_SECURITY_AUDIT_2026-06.md` (196 lines).
  Covers all 5 active `addJavascriptInterface` registration sites:
  `ExtendedWebView.kt:249` (base `IBase`), `ThemeBridgeHandler.kt:34-35`
  (theme), `SearchFragment.kt:351` (search), `ArticleContentFragment.kt:2999`
  (news), `QmsChatFragment.kt:225,2472` (QMS), `ForumRulesFragment.kt:102`
  (rules). Contains a cross-cutting concern table, a per-site findings +
  recommendations table, and a followups section.
- **Net finding:** 1 Medium-High — `QmsChatFragment` re-registers the JS
  bridge on a brand-new WebView without an explicit `webView.init(profile)`
  call on the re-created instance (profile is inherited via the constructor
  default, but the contract is not asserted). Logged below as a backlog
  followup.
- **Rationale:** docs-only closure per ticket scope; no production code
  changes. The remaining Medium-High finding is non-blocking and is
  explicitly tracked in `docs/BACKLOG_DEFERRED.md`.

## 2026-06-22 — L10 closed: TemplateManager god-class decomposition

- **Ticket:** AUDIT-L10.
- **Status:** ✅ Closed.
- **Evidence:** `app/src/main/java/forpdateam/ru/forpda/ui/TemplateManager.kt`
  is now a thin facade (80 lines). Four helper classes extracted and
  annotated with the "Extracted from TemplateManager as part of the
  god-class decomposition" provenance marker:
  - `TemplateCssComposer.kt` — CSS composition
  - `TemplateAssetLoader.kt` — `MiniTemplator` asset loading
  - `TemplatePaletteResolver.kt` — sepia / blue / amoled palette decisions
  - `TemplateStaticStrings.kt` — localized static-string map
- **Compatibility:** Public API of `TemplateManager` is preserved; callers
  (`ThemeUseCase`, `QmsChatTemplate`, `ArticleTemplate`, etc.) are
  unaffected — no call-site changes were required.
- **Rationale:** matches the §1.1 split plan in `REFACTOR_PLAN.md` /
  `docs/AUDIT_ROADMAP_2026-06_RU.md` (UI → ARCH god-class group).

## 2026-06-22 — L08 analytical document shipped (awaiting user approval)

- **Ticket:** AUDIT-L08.
- **Status:** ⏳ Awaiting user approval.
- **Evidence:** `docs/perf/L08_THEME_PAGE_MEMORY_CACHE_ANALYSIS.md` (924 lines, 8 sections).
- **Recommended approach:** **Variant A (zero-alloc read) + Variant C (render-generation
  handshake)**, both S effort. Variant A deletes the per-`get()` `copyForCache()` allocation
  (~280 B/hit) and returns the same `ThemePage` reference on read. Variant C adds a
  `get(key, expectedSignature, expectedRenderGeneration)` overload so the cache evicts
  entries whose `renderGenerationId` does not match the active `ThemeRenderSession` —
  closing the scroll-restore desync flagged at `docs/AUDIT_ROADMAP_2026-06_RU.md:199`.
- **Compatibility with L06 (ThemeRenderSession):** fully aligned — Variant C *is* the
  natural handshake that L06 needs but did not implement. No god-class refactor required.
- **Compatibility with P-08 (page windowing, `docs/PLAN.md:51`):** orthogonal. P-08 is
  a separate L-effort ticket, deferred.
- **Rejected:** Variant B (LruCache — negative ROI), Variant D (page windowing — out of scope).
- **Rationale:** Phase-1 of L08 (SoftReference migration) is already in the code; the
  allocation half of the ticket is the only remaining work on the hot path, and the
  handshake with `ThemeRenderSession` is the only remaining correctness gap. Both fit
  in a single ~3-commit PR (see §5.1 of the document).

L08 — analytical document at docs/perf/L08_THEME_PAGE_MEMORY_CACHE_ANALYSIS.md, recommended approach: A+C, status: awaiting user approval

## 2026-06-22 — L08 implemented: Variant A (zero-alloc read) + Variant C (render-generation handshake)

- **Ticket:** AUDIT-L08.
- **Status:** ✅ Closed (PR-ready; awaiting manual QA + integration into `ThemeRepository`).
- **Evidence:**
  - `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt`
    — `copyForCache()` extension deleted (was line 156-190); `get(...)` no longer allocates
    a defensive copy on read; new 3-arg overload `get(key, expectedSignature, expectedRenderGeneration)`
    evicts entries whose `renderGenerationId` does not match the active `ThemeRenderSession`;
    `Entry` now records `renderGenerationId: Int` at `put()` time.
  - `app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCacheTest.kt`
    — updated `putThenGet_returnsCopyAndExpiresAfterTtl` to `assertSame(page, hit)` (no
    longer asserts `hit !== page`); added `putThenGet_returnsSameReferenceAcrossRepeatedReads`,
    `get_returnsUnmodifiedCollectionsByReference`, `get_withMatchingRenderGeneration_returnsHit`,
    `get_withStaleRenderGeneration_missesAndEvicts`, `get_withNullRenderGeneration_skipsCheck`,
    `put_capturesRenderGenerationIdFromPage`. Total: **17 tests, 0 failures**.
- **Public-API impact:** backward-compatible. The 0-arg and 1-arg `get` overloads are
  preserved as delegates to the new 3-arg form. The only behavioural change for existing
  callers (`ThemeRepository.getTheme`) is that the returned `ThemePage` is now the
  *same* instance that was `put()` — no defensive copy. Audited callers (`ThemeRepository`,
  `ThemeUseCase`, `ThemeInfiniteScrollController`) only read or write scalar fields on
  the returned page; they never re-`put` it, so the new contract is safe.
- **Test/build status:** `./gradlew :app:testStoreDebugUnitTest --tests "*ThemePageMemoryCacheTest*"`
  → 17/17 green. `./gradlew :app:testStoreDebugUnitTest` → all suites green, 0 failures.
  `./gradlew :app:assembleStoreDebug` → BUILD SUCCESSFUL.
- **Out of scope (per user request):** no `ThemeRepository.activeRenderSessionProvider`
  wiring, no `ThemeRenderSession` refactoring, no `ThemeViewModel` change, no DI module
  change. The 3-arg `get` is wired at the cache layer only; the integration into
  `ThemeRepository.getTheme` is a followup that the user will run as a separate commit
  (per analysis §5.1 Stage 2, §6 Open question 1).
- **Known pre-existing test issue (not caused by this work):**
  `app/src/test/java/forpdateam/ru/forpda/notifications/NotificationsServiceForegroundNotificationTest.kt:93`
  references an unresolved `smallIconResId` and blocks `compileStoreDebugUnitTestKotlin`
  for the *whole* test module. The file is untracked in git and predates this change.
  Logged in the followup list below.
- **Followups for the next sprint:**
  1. Wire `ThemeRepository.activeRenderSessionProvider` (analysis §5.1 Stage 2, Open
     question 1) — locate the `@Module` for `ThemeRepository` and bind the provider.
  2. In `ThemeViewModel`, set the provider to the active `ThemeRenderSession` after
     `ThemeRenderSession.create(...)` returns.
  3. Fix the broken `NotificationsServiceForegroundNotificationTest.kt` (pre-existing,
     untracked).
  4. (Optional) Run the `ThemePageMemoryCacheAllocBenchmark` from analysis §5.3 to
     confirm < 100 KB / 10 000 gets on a Robolectric run.
  5. (P-08) WebView DOM growth controls — page windowing, deferred per analysis §4.
- **Rationale:** minimal change with maximum payoff. Both variants are S effort and
  fit in two focused edits to one file. Public API is preserved. The two-layer defence
  (signature + render-generation) gives belt-and-suspenders correctness for the
  scroll-restore case.

L08 — implemented in `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt`; Variant A (zero-alloc read) + Variant C (render-generation handshake); 17/17 cache tests green, full unit test suite green, assembleStoreDebug successful; integration into `ThemeRepository` deferred to followup.

