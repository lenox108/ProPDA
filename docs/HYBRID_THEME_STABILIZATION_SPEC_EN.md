# SPEC: Hybrid Theme Mode Stabilization & Optimization (PROPDA / ForPDA)

> Implementation spec for an AI executor. Based on a read-only audit of
> `/Users/j.golt/Documents/Cursor01/ForPDA-master`. All file paths and line numbers were
> verified against the real code at authoring time. Line numbers drift as edits land —
> ALWAYS re-confirm the location by the function signature / code line before editing.

## 0. Context & stack

- Unofficial Kotlin client for the 4pda forum. Topic content is rendered inside a WebView from
  HTML built by the minitemplator engine + CSS under `app/src/main/assets/forpda/styles/`.
- Build / compile verification (release needs missing keystores — DO NOT use release):
  - Compile Kotlin: `./gradlew :app:compileStableDebugKotlin`
  - Theme unit tests: `./gradlew :app:testStableDebugUnitTest`
  - Full debug build: `./gradlew :app:assembleStableDebug`
- Base package: `forpdateam.ru.forpda`.

## 1. HARD RULES (read before every task)

1. NO large rewrites. Small, verifiable changes only.
2. DO NOT remove existing logic until you have PROVEN it is dead (find all usages first).
3. Before changing a component, locate ALL call sites and dependencies (Grep by symbol).
4. Every change is atomic and testable. One item = one commit.
5. Hybrid Theme Mode must stay fully functional after EVERY item.
6. Any assumption not backed by code must be marked: `Not enough evidence`.
7. Stability over architectural beauty. Determinism over clever behavior.
8. Finish and verify each stage (compile + tests) BEFORE moving to the next.
9. User-visible behavioral changes must be confirmed separately (see Section 12 / per-stage notes).

## 2. KEY FILE MAP

| Role | File |
|---|---|
| Fragment (view-scoped) | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt` (~2892 lines) |
| WebView controller | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt` (~1721) |
| JS bridge | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` |
| Base bridge | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/BaseJsInterface.kt` |
| Bridge registration | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt` |
| Type-safe JS API | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApi.kt` |
| Render guard (token) | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderGuard.kt` |
| ViewModel (retained) | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt` (~4943) |
| WebView | `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` |
| Template / render | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt` |
| CSS composer | `app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt`, `TemplateManager.kt` |
| Highlight logic | `presentation/theme/HighlightResolver.kt`, `TopicHighlightApply.kt`, `HighlightArmingPolicy.kt` |
| Infinite scroll | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollController.kt` |
| HTML template | `app/src/main/assets/template_theme.html` |
| Theme JS module | `app/src/main/assets/forpda/scripts/modules/theme.js` |
| Shared JS | `app/src/main/assets/forpda/scripts/main.js` |

## 3. ROOT ARCHITECTURAL PROBLEM (cause of the bugs)

There are THREE independent "render validity" mechanisms that are never reconciled:

1. `ThemeRenderGuard.token` (Base64 string, `SecureRandom`) — authorizes destructive bridge
   calls (reply/quote/vote/delete/edit/report/poll/reputation).
   File: `ThemeRenderGuard.kt`. Checked in `ThemeJsInterface.runProtected` (lines ~237-250).
2. `ThemePage.renderGenerationId` (int) — post-highlight lifecycle (highlight/fadeout).
   Minted in `TopicHighlightApply.nextGeneration()`; bumped only when `renderGenerationId == 0`.
3. `ThemeWebController.renderGeneration` (int) — gates DOM/PAGE lifecycle and missed-lifecycle flush.
   Incremented on each `renderThemePage` (`ThemeWebController.kt` ~line 290).

These three values are minted at different times, live in different objects, and are never
compared. The long-term part of this spec (Stage 7) gradually introduces a single
`ThemeRenderSession` without breaking current logic.

> IMPORTANT: also note the retained `ViewModel` (`presenter: ThemeViewModel by viewModels()`,
> `ThemeFragment.kt:273`) OUTLIVES the view-scoped WebView/bridge. So lifecycle guards must be
> airtight. This is why Stages 1 and 3 matter.

---

# STAGED TASKS

Order is MANDATORY: from safe/local fixes to structural ones. For each stage:
finish -> compile -> run tests -> check the checklist -> commit -> next stage.

---

## STAGE 1 — Highlight: stop the fadeout-timer restart (bug B1)

Priority: HIGH. Risk: LOW. Type: determinism fix.

### Problem (code-confirmed)
`reapplyTopicHighlightAfterScrollSettled()` resets BOTH guards, including
`highlightFadeoutScheduledGeneration`:

File `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt`,
function `reapplyTopicHighlightAfterScrollSettled()` (~lines 1392-1396):

```
fun reapplyTopicHighlightAfterScrollSettled() {
    highlightArmedGeneration = 0
    highlightFadeoutScheduledGeneration = 0   // <-- PROBLEM
    reapplyTopicHighlight()
}
```

After the reset, `scheduleHighlightFadeoutForPage` (~line 1469:
`if (highlightFadeoutScheduledGeneration == generation) return`) passes its guard again and
re-calls `PPDA_scheduleHighlightFadeout`, which (in JS) cancels the old timer and arms a fresh
2000ms one. Result: the highlight stays visible longer than 2s on every settle/reveal. This
contradicts the documented contract in `ThemeJsApi.scheduleHighlightFadeout` (KDoc ~lines 296-298:
"re-calling ... does NOT extend the deadline") and the intent of `ThemeWebControllerFadeoutTest`
("Same render must not re-arm the timer").

Called from TWO places: `ThemeFragmentWeb.kt` (ProgrammaticScrollEnded, ~line 2544) and
`onWebViewContentRevealed()` (`ThemeWebController.kt` ~lines 1399-1404).

### Change
1. Remove `highlightFadeoutScheduledGeneration = 0` from
   `reapplyTopicHighlightAfterScrollSettled()`. Keep only `highlightArmedGeneration = 0`.
   Rationale: fadeout is scheduled separately and is idempotent per generation; re-applying the
   visual border is safe, re-scheduling the timer is not.
2. Confirm `scheduleHighlightFadeoutForPage` remains idempotent per generation.

### Verification
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeWebControllerFadeoutTest*"` is green.
- Update/extend `ThemeWebControllerFadeoutTest.kt`: add a case asserting that after
  `reapplyTopicHighlightAfterScrollSettled`, a second `scheduleHighlightFadeoutForPage` with the
  same generation does NOT trigger another `jsApi.eval(scheduleHighlightFadeout)`.
- Manual QA: open a topic with an unread post -> highlight fades after exactly ~2s, even while a
  programmatic scroll / reveal happens.

### Definition of Done
Highlight fades after a fixed `HIGHLIGHT_FADEOUT_DELAY_MS` regardless of how many settle/reveal
events occur. The no-restart test passes.

---

## STAGE 2 — Highlight: dedupe redundant JS calls (P2)

Priority: MEDIUM. Risk: LOW. Type: performance, no behavior change.

### Problem (confirmed)
In `ThemeWebController.kt` the highlight path issues 4 separate immediate `evaluateJavascript`
calls per render, and `setReadPosObserverEnabled(false)` is called TWICE:

- `reapplyTopicHighlight()` ~lines 1439-1440:
  `jsApi.eval(jsApi.setReadPosObserverEnabled(false))` + `jsApi.eval(jsApi.applyHighlight(...))`
- `scheduleHighlightFadeoutForPage()` ~lines 1471-1472:
  `jsApi.eval(jsApi.setReadPosObserverEnabled(false))` + `jsApi.eval(jsApi.scheduleHighlightFadeout(...))`

`jsApi.eval` (`ThemeJsApi.kt` ~lines 118-120) hits `evaluateJavascript` directly, bypassing the
`evalJs` / `flushQueuedJs` batch queue.

### Change
1. Remove the duplicate `setReadPosObserverEnabled(false)` — keep exactly one per render cycle
   (e.g. only in `scheduleHighlightFadeoutForPage`, since it runs earlier in `reapplyTopicHighlight`).
   Confirm the call order before editing.
2. Combine the highlight snippets into a single eval: build one string from
   `setReadPosObserverEnabled(false)` + `applyHighlight(...)` (+ `scheduleHighlightFadeout` if
   appropriate) and run via one `jsApi.eval(...)` / `webView.evalJs`.
   IMPORTANT: do NOT change WHEN/under what conditions the highlight applies (keep generation guards).
3. Do NOT touch the diagnostic events (`TopicHighlightDiagnostics.*`).

### Verification
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeJsApi*"` is green.
- `js_highlight_applied` / `highlight_fadeout_scheduled` logs still emit exactly once per generation.
- Manual QA: highlight/fadeout visually unchanged.

### Definition of Done
At most 1 `setReadPosObserverEnabled` call per render; fewer immediate highlight evals. JsApi tests green.

