# HYBRID_THEME_STABILIZATION_SPEC_EN
## PROPDA / ForPDA Hybrid Theme Mode Stabilization & Optimization Specification

> Engineering specification for AI coding agents and senior Android engineers.
> Based on a read-only audit of the existing codebase.
> All file names, paths, and bug references originate from the audited implementation.

---

# 0. Context and Technology Stack

The application is an unofficial Android client for the 4PDA forum.

Topic content is rendered inside a WebView using:
- MiniTemplator-generated HTML
- CSS assets from `app/src/main/assets/forpda/styles/`
- JavaScript runtime from `app/src/main/assets/forpda/scripts/`

Base package:

`forpdateam.ru.forpda`

Build verification commands:

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:testStableDebugUnitTest
./gradlew :app:assembleStableDebug
```

Do NOT use release builds.

---

# 1. Non-Negotiable Rules

1. No large-scale rewrites.
2. No architecture replacement.
3. No deletion of existing logic until all usages are verified.
4. Before changing any symbol, find all usages and dependencies.
5. Every stage must be atomic and independently testable.
6. Hybrid Theme Mode must remain functional after every commit.
7. Unsupported assumptions must be marked:

   `Not enough evidence.`

8. Stability has higher priority than architectural elegance.
9. Determinism has higher priority than smart behavior.
10. Compile and validate after every stage.
11. User-visible behavior changes require approval.
12. One stage = one commit.
13. Do not merge or push automatically.

---

# 2. Key File Map

| Role | File |
|--------|--------|
| Theme Fragment | ThemeFragmentWeb.kt |
| WebView Controller | ThemeWebController.kt |
| JS Bridge | ThemeJsInterface.kt |
| Base Bridge | BaseJsInterface.kt |
| Bridge Registration | ThemeBridgeHandler.kt |
| Typed JS API | ThemeJsApi.kt |
| Render Guard | ThemeRenderGuard.kt |
| ViewModel | ThemeViewModel.kt |
| WebView | ExtendedWebView.kt |
| Template Renderer | ThemeTemplate.kt |
| CSS Composer | TemplateCssComposer.kt |
| Template Manager | TemplateManager.kt |
| Highlight Logic | HighlightResolver.kt |
| Infinite Scroll | ThemeInfiniteScrollController.kt |
| HTML Template | template_theme.html |
| Theme Runtime JS | theme.js |
| Shared Runtime JS | main.js |

---

# 3. Critical Architectural Problem

Three independent render-validity systems currently exist:

## A. ThemeRenderGuard.token

Purpose:
- authorization of destructive bridge actions

## B. ThemePage.renderGenerationId

Purpose:
- highlight lifecycle
- fadeout lifecycle

## C. ThemeWebController.renderGeneration

Purpose:
- DOM lifecycle synchronization
- render sequencing

These systems are created independently and are not synchronized.

Long-term objective:

Introduce a unified:

```kotlin
ThemeRenderSession
```

without breaking existing behavior.

---

# 4. Implementation Roadmap

# Stage 1 — Highlight Fadeout Determinism

Priority: HIGH

Goal:
Prevent highlight fadeout timer rearming.

Problem:
`reapplyTopicHighlightAfterScrollSettled()` resets
`highlightFadeoutScheduledGeneration`.

Result:
The fadeout timer can be extended repeatedly.

Required Change:
- Remove fadeout-generation reset.
- Preserve generation-based idempotency.

Acceptance Criteria:
- Highlight lifetime is fixed.
- Reapply does not extend timeout.
- Unit tests remain green.

Commit:

theme(stabilize): stage 1 highlight fadeout determinism

---

# Stage 2 — Highlight JS Deduplication

Priority: MEDIUM

Goal:
Reduce unnecessary JavaScript execution.

Problems:
- duplicate observer disabling
- multiple immediate evaluateJavascript calls

Required Change:
- keep one observer-disable operation
- consolidate highlight-related JS execution
- preserve diagnostics

Acceptance Criteria:
- visual behavior unchanged
- fewer JS executions
- diagnostics unchanged

Commit:

theme(stabilize): stage 2 highlight js deduplication

---

# Stage 3 — Lifecycle Teardown Ordering

Priority: MEDIUM

Goal:
Perform cleanup before WebView destruction.

Current Issue:
WebView destroy happens before bridge cleanup.

Required Order:

1. invalidate lifecycle generation
2. cancel handlers
3. invalidate render guards
4. dispose modules
5. unregister JS bridges
6. cleanup runtime
7. destroy WebView

Acceptance Criteria:
- no cleanup on destroyed WebView
- no post-destroy evaluateJavascript calls
- lifecycle tests pass

Commit:

theme(stabilize): stage 3 lifecycle teardown ordering

---

# Stage 4 — Bridge Token Protection

Priority: MEDIUM

Goal:
Protect navigation/menu actions from stale renders.

Protect:

- showUserMenu
- showReputationMenu
- showPostMenu

Do NOT modify:

- openLink
- infiniteScroll
- visiblePageChanged
- postVisible
- setHistoryBody
- rememberLinkSourceAnchor

Acceptance Criteria:
- stale render cannot open menus
- valid render continues to work

Commit:

theme(stabilize): stage 4 bridge token protection

---

# Stage 5 — CSS Composition Cache

Priority: MEDIUM

Goal:
Prevent CSS recomposition on every render.

Cache Key:

- theme type
- night mode
- palette
- font mode

Requirements:

- cache CSS string
- cache hash value
- invalidate automatically when key changes

Acceptance Criteria:

- compose() runs once per unique configuration
- theme switching remains correct

Commit:

theme(stabilize): stage 5 css composition cache

---

# Stage 6 — Deterministic Scroll Restore

Priority: HIGH

Risk: HIGH

Goal:
Guarantee exactly one scroll restoration path.

## Stage 6.1

Diagnostics only.
No behavior changes.

## Stage 6.2

Remove duplicate restore paths.

DOM restore must not compete with Kotlin restore commands.

## Stage 6.3

Generation cleanup.

Prevent competing anchor restoration chains.

Acceptance Criteria:

- one restore path per render
- no landing jumps
- no anchor races
- scroll tests pass

Commits:

theme(stabilize): stage 6.1 scroll diagnostics

theme(stabilize): stage 6.2 scroll restore unification

theme(stabilize): stage 6.3 scroll generation cleanup

---

# Stage 7 — ThemeRenderSession

Priority: LONG TERM

Goal:
Introduce a single render-session abstraction.

New file:

ThemeRenderSession.kt

```kotlin
data class ThemeRenderSession(
    val topicId: Int,
    val page: Int,
    val renderGenerationId: Int,
    val bridgeToken: String,
    val themeSignature: String,
    val createdAt: Long
)
```

Requirements:

- session initially mirrors existing systems
- existing systems remain active
- no behavior changes

Acceptance Criteria:

- session creation centralized
- debug logging available
- existing functionality unchanged

Commit:

theme(stabilize): stage 7 introduce render session

---

# 5. Protected Systems

Do not refactor without explicit justification:

- ThemeInfiniteScrollController contamination protection
- Off-main HTML/CSS composition in ThemeUseCase
- Lifecycle JS batching in ExtendedWebView
- Existing token-protected destructive bridge actions
- Kotlin-owned highlight target resolution

---

# 6. Validation Strategy

After every stage:

## Compile

```bash
./gradlew :app:compileStableDebugKotlin
```

## Run Relevant Tests

```bash
./gradlew :app:testStableDebugUnitTest
```

## Manual Verification

Verify:

- Hybrid Theme Mode
- Light Theme
- Dark Theme
- AMOLED Palette
- Sepia Palette
- Rotation
- Process Restore
- Refresh
- Infinite Scroll
- External Links
- Image Viewer

---

# 7. QA Matrix

Required Devices:

Android 8–15

Required Scenarios:

- open topic normally
- open via findpost
- unread-post landing
- return via back stack
- refresh near bottom
- refresh in middle of topic
- infinite-scroll prepend
- large topics
- process death recovery
- repeated open/close cycles
- rotation stress testing

---

# 8. Bug Mapping

| ID | Description | Stage |
|----|-------------|--------|
| B1 | Highlight fadeout timer restart | 1 |
| P2 | Duplicate highlight JS execution | 2 |
| L1 | WebView destroy before bridge cleanup | 3 |
| S1 | Navigation/menu calls without token guard | 4 |
| P1 | CSS recomposition every render | 5 |
| B2 | Non-deterministic scroll restore | 6 |
| B3 | Fragmented render validity systems | 7 |
| B4 | Infinite scroll contamination | Audit only |

---

# 9. Definition of Overall Success

The specification is complete when:

- Highlight fadeout is deterministic.
- Scroll restoration is deterministic.
- Lifecycle teardown is safe.
- Menu actions are protected from stale renders.
- CSS composition is cached.
- No regressions are introduced.
- Unit tests pass.
- Hybrid Theme Mode remains fully functional.
