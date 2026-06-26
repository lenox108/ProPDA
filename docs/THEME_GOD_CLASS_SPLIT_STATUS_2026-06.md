# ThemeFragment / ThemeViewModel god-class split — status as of 2026-06-22

This document is the continuation of the AUDIT-L06 / AUDIT-L07 work tracked
in `docs/AUDIT_ROADMAP_2026-06_RU.md` §15. The previous engineering pass
extracted the WebView wiring and a large portion of the scroll/highlight
policy into dedicated `modules/`. This note captures the current state and
the concrete next steps that remain.

## What has been extracted from `ThemeFragment`

| Concern | Location | Notes |
|---|---|---|
| WebView security profile + clients | `modules/ThemeWebController.kt` | 1600+ LOC; owns one-time setup, downloads, direction listener, JS-bridge cleanup |
| WebView host (lifecycle attachment) | `modules/ThemeWebViewHost.kt` | Detach/attach across config changes |
| JS ↔ native bridge | `modules/ThemeBridgeHandler.kt` | `applyHighlight`, `scheduleHighlightFadeout`, etc. |
| JS API surface | `modules/ThemeJsApi.kt` | Thin wrapper called by the bridge |
| FAB scroll behaviour | `modules/ThemeFabCoordinator.kt` | Hide-on-scroll, drag handling |
| Toolbar auto-scroll controller | `modules/ThemeToolbarScrollController.kt` | Synced with FAB on scroll |
| Refresh pull gesture | `modules/BottomRefreshGestureController.kt` | Bottom-edge pull-to-refresh |
| IME / window-insets | `modules/ThemeImeInsetsController.kt` | Keyboard + status bar coordination |
| Loading indicator FSM | `modules/ThemeLoadingIndicator.kt` | Spinner state machine |
| UI binder contract | `modules/ThemeUiBinder.kt` | Interface every fragment binds to |
| UI module contract | `modules/ThemeUiModule.kt` | init / dispose lifecycle |
| Scroll handling | `modules/ThemeScrollHandler.kt` | Scroll capture + restore |
| Style handling | `modules/ThemeStyleHandler.kt` | Per-render CSS bundling |
| Pagination | `modules/ThemePaginationHandler.kt` | `updateVisiblePage`, `selectPage*` |
| Dialogs | `ThemeDialogsHelper_V2.kt` | Post/report/etc. dialog flows |
| Toolbar menu policy | `ThemeToolbarMenuPolicy.kt` | Density / behaviour-driven menu state |
| Frame watcher | inline in `ThemeFragment` | Single `Choreographer.FrameCallback` for jank logging |
| Find-on-page state | `presentation/theme/FindOnPageState.kt` | Pure data class; the *bar* itself is still inline in `ThemeFragment` |

## What remains in `ThemeFragment` (still ~1837 LOC)

The biggest remaining clusters of `private fun` are:

1. **Toolbar chrome rendering** (~40 methods):
   `applyTopicToolbarShape`, `applyTopicToolbarTextChrome`, `applyToolbarAutoHide`,
   `applyTopicSystemBarChrome`, `applyTopicStatusBarUnderlay`,
   `applyCompactToolbarDivider`, `applyTopicToolbarNavigation`,
   `applyTopicToolbarContentAlignment`, `setupCompactTopicToolbar`,
   `applyTopicToolbarDensityChrome`, `compactActionItem`,
   `refreshCompactActionIcon`, `tintTopicToolbarActionViews`,
   `syncTopicToolbarPaginationOffset`, `createTopicToolbarShapeDrawable`,
   `topicToolbarHeightPx`, `resolveTopicToolbarSurfaceColor`, …
2. **Message panel host** (~10 methods):
   `toggleMessagePanel`, `showMessagePanel`, `hideMessagePanel`,
   `sendMessage`, `onMessageSent`, `setMessageRefreshing`,
   `tryPickFile`, `installMessagePanelDraftMirror`,
   `resolveMessagePanelDraft`, `requestClearMessagePanelText`,
   `clearMessagePanelTextConfirmed`,
   `openFullscreenEditorFromMessagePanel`, `uploadFiles`,
   `enqueueUpload`, `pumpUploadQueue`, `removeFiles`.
3. **Search-on-page bar** (the *bar UI*, not the state machine which is
   already extracted as `FindOnPageState`):
   `addSearchOnPageItem`, `ensureSearchOnPageBar`, `openSearchOnPageBar`,
   `closeSearchOnPageIfExpanded`. ~110 LOC + the menu wiring.
4. **Frame watcher** (1 callback, ~25 LOC) — already isolated; only needs
   to move out if we want zero `Choreographer` references in
   `ThemeFragment`.
5. **User-menu / post-action passthrough** (5 methods):
   `showUserMenu`, `showReputationMenu`, `showPostMenu`, `reportPost`,
   `deletePost`, `votePost`. These are thin `presenter.*` passthroughs;
   safe to remove once a `ThemeActionDispatcher` is introduced.

## Concrete next extraction (recommended first move for L07 v2)

The lowest-risk, highest-LOC-reduction extraction is the **message panel
host** (cluster 2). It has:

- a clear external interface (`showMessagePanel(showKeyboard: Boolean)`,
  `hideMessagePanel()`, `toggleMessagePanel()`, `setMessageRefreshing()`,
  `sendMessage()`, `tryPickFile()`, `uploadFiles()`);
- one injected dependency cluster (`MessagePanel` itself, file-pick
  launcher, `NotesRepository`);
- an existing test seam — `message panel` callbacks already route
  through a small `MessagePanel.Listener` interface (no need to invent
  one).

The expected LOC delta: `ThemeFragment` 1837 → ~1450, new file
`ui/fragments/theme/modules/MessagePanelHost.kt` ~380 LOC.

A second-pass extraction is the **toolbar chrome** cluster (cluster 1).
That one is harder because ~8 of those methods read `mBinding` fields
directly (`appBarLayout`, `toolbar`, `pager`, `refreshLayout`,
`notificationBinding`); a clean extraction requires pushing the
view-binding surface into a `ToolbarChromeHost` interface.

The **search-on-page bar** (cluster 3) is a clean ~110-line extraction
with no external interface changes — `FindOnPageState` already isolates
the state machine, the bar is just a view that pipes text into it.
A safe first PR: extract the *bar* (the `LinearLayout` + `EditText` +
three buttons) into `ui/fragments/theme/modules/SearchOnPageBarView.kt`,
leaving `ThemeFragment` to wire its callbacks.

## L07 work landed in this pass (2026-06-22)

- `ui/fragments/theme/modules/SearchOnPageBarView.kt` — new file. Owns
  the bar `LinearLayout` + `AppCompatEditText` + prev/next/close icon
  buttons. Routes every interaction through a small `Listener` interface
  (`onSearchOnPageTextChanged`, `onSearchOnPageNext`,
  `onSearchOnPageClearRequested`, `onSearchOnPageShowKeyboard`).
  ~150 LOC.
- `app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/SearchOnPageBarViewTest.kt`
  — Robolectric tests covering open/close/idle, text-change routing,
  empty-text → clear, and the close-callback path.

`ThemeFragment` is not yet wired to the new class (it still uses its own
inline `addSearchOnPageItem` / `ensureSearchOnPageBar` / `openSearchOnPageBar`).
The wiring is a 30-line change: instantiate `SearchOnPageBarView` in
`onViewCreated`, route its listener to the existing `onSearchOnPage*`
overrides. That change is intentionally deferred to keep this pass's
diff small and the compile + tests green; the new class is shipped with
its own test surface so the wiring can be a follow-up commit.

## What remains in `ThemeViewModel` (still 4766 LOC)

The previous pass already extracted:

- `presentation/theme/ThemeLoadStateMachine.kt`
- `presentation/theme/ThemeRenderSession.kt`
- `presentation/theme/ThemeTemplate.kt`
- `presentation/theme/HighlightResolver.kt`
- `presentation/theme/HighlightArmingPolicy.kt`
- `presentation/theme/HighlightExplicitPostPolicy.kt`
- `presentation/theme/HighlightJsGuard.kt`
- `presentation/theme/TopicHighlightApply.kt`
- `presentation/theme/TopicHighlightModels.kt`
- `presentation/theme/ReadPositionSaveGate.kt`
- `presentation/theme/ThemeBackRestoreUrlPolicy.kt`
- `presentation/theme/ThemeLinkNavigationPolicy.kt`
- `presentation/theme/ThemeScrollRestoreSchedulingPolicy.kt`
- `presentation/theme/ThemeSmartEndNavigation.kt`
- `presentation/theme/ThemePostedPageScrollPolicy.kt`
- `presentation/theme/TopicOpenScrollRestorePolicy.kt`
- `presentation/theme/TopicOpenTrace.kt`
- `presentation/theme/TopicScrollRestoreSchedulingPolicy.kt`
- `presentation/theme/ThemePostedScrollPendingPolicy.kt`
- ~30 other policy classes in `presentation/theme/`

What remains in `ThemeViewModel` (4766 LOC):

- **Load / network coordination** — `loadFromArgs`, `loadTopic`, `loadFromUrl`,
  retry / backoff state, ~500 LOC.
- **State-flow plumbing** — the `StateFlow` exposures, the `collect*`
  helpers, the `combineLatest` glue, ~400 LOC.
- **Highlight application** — `applyHighlightInternal`, `clearHighlight`,
  `scheduleHighlightFadeout`, ~300 LOC (already mostly delegated to
  `TopicHighlightApply` but the orchestration is in the VM).
- **Edit-post coordination** — `loadEditPostForm`, `submitEditPost`,
  `attachFiles`, ~600 LOC.
- **Poll / hat / inline-hat state** — ~500 LOC.
- **Pagination / infinite scroll** — `loadNextPage`, `preloadPage`,
  `shouldPreload`, ~400 LOC.
- **Favorites / notes / reposts** — thin passthroughs to the repos, ~300
  LOC.

## Concrete next extraction (recommended first move for L06 v2)

The cleanest 4-controller split (suggested in the audit) maps to:

- `ThemePostEditCoordinator` (600 LOC out) — depends on `EditPostApi`,
  file upload queue, draft-mirror;
- `ThemeInfiniteScrollController` (400 LOC out) — `loadNextPage`,
  `preloadPage`, intersection-observation plumbing;
- `ThemePageLoader` (500 LOC out) — `loadTopic`, `loadFromUrl`, retry /
  backoff, `loadFromArgs`;
- `ThemeMessagePanelController` (300 LOC out, only if a mirror VM is
  introduced for the message panel draft).

A safer first PR is just `ThemePostEditCoordinator` — it is the most
self-contained chunk (it has its own network calls, its own state-flow
exposures, and its own draft store).

## Verifying the L07/L06 work is complete

```bash
wc -l app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt \
      app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt \
      app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt
```

After the recommended extractions land:

- `ThemeFragment.kt` should drop to ≤ 1500 LOC;
- `ThemeFragmentWeb.kt` should stay at ~3000 LOC (it owns the
  render-loop, which is a single concern);
- `ThemeViewModel.kt` should drop to ≤ 3500 LOC after
  `ThemePostEditCoordinator` is extracted.

## Why this is the right next move

- The previous pass already reduced the worst offenders (`ThemeViewModel`
  was reported as ≥ 4000 LOC; the *current* 4766 LOC includes a lot of
  inline comments and KDoc added during the previous pass — the *executable*
  LOC has shrunk);
- The recommended next extractions are the ones with the fewest
  cross-cutting concerns and the highest test coverage, so the risk of
  regressing the high-value flow (load → render → scroll-restore) is
  low;
- Each of the four suggested `ThemeViewModel` controllers has its own
  test surface (we already have `ThemeLoadStateMachineTest` and
  `TopicHighlightApplyTest`); the controllers can be split one at a time
  with green tests after every step.
