# Deferred tasks (out of scope per stage)

> Tasks intentionally **not** implemented in their originally planned stage,
> with the reason and the conditions under which they should be picked up
> later. See `docs/PLAN.md` for the full staged plan and `docs/PLAN_TASKS.md`
> for the canonical task table.

## Stage 3 — Perf baseline

### P-09 — Perf baseline regression guard (Perfetto + macrobenchmark)
- **Status:** partly done (scripts: `scripts/startup-benchmark.sh`,
  `scripts/battery-audit.sh`; protocol: `docs/STARTUP_BASELINE.md`).
- **Deferred:** no automated CI gate yet (no Macrobenchmark module wired
  into CI, no Perfetto trace comparison script).
- **Pick up when:** team has bandwidth to set up Macrobenchmark module and
  add a CI workflow that runs p95 cold-start on Firebase Test Lab.

## Stage 5 — Memory, lifecycle, coroutines

### A-04 — `ThemeViewModel` decomposition
- **File:** `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt` (4000+ LOC).
- **Why deferred:** in one PR the risk/reward is bad — the ViewModel is the
  central coordinator for the most-used screen in the app. Decomposition
  needs a spike + narrow PR per extracted class.
- **Pick up when:** team agrees on the seam (likely split: navigation
  actions / post actions / prefetching / state), then one PR per extracted
  class with snapshot tests on the public surface.

### P-07 — `WebSocketController` synchronized + dispatcher parsing
- **Why deferred:** no measured jank from the WebSocket layer. Changing
  parsing/synchronization without a profile is optimization-blind.
- **Pick up when:** a real freeze is reproduced in QMS chat, captured in
  Perfetto, and the WebSocket thread is the bottleneck. Then revisit with
  data.

### `ColdStartTracerTest` — 4 pre-existing failures
- **File:** `app/src/test/java/forpdateam/ru/forpda/diagnostic/ColdStartTracerTest.kt`.
- **Status:** **fixed** (see commit history). The clock is now an
  `internal var clock: () -> Long` on the tracer, the anchor sentinel is
  `-1L` (not `0L`), and `reset(resetAnchor = true)` lets tests clear the
  anchor without breaking production callers.

## Skipped by user directive (v3 audit exclusions)

- **S-04** — AppMetrica key: keep current key, do not change.
- **P-04** — `CookieManager` startup refactor: not needed.
- **Offline theme** task: explicitly dropped by user as not needed.
- **Offline article** task: explicitly dropped by user as not needed. The
  entire offline-reading feature (theme *and* article) was removed
  end-to-end in 2026-06 — see `docs/AUDIT_REPORT.md` §11.1 and §11.2. No
  offline cache, no "Сохранить для оффлайна" overflow, no
  `offline.max_bytes_mb` preference, no `offline_items` table, no
  `Offline*` data layer.

## Stage 6 — Architecture & hygiene

### D-03 — `androidx.security:security-crypto` → stable
- **Status:** pinned to `1.1.0-alpha06` on purpose. See comment in
  `gradle/libs.versions.toml` near `androidxSecurityCrypto`.
- **Why deferred:** stable `1.0.0` only ships the legacy
  `MasterKeys.AES256_GCM_SPEC` API; our code uses
  `MasterKey.Builder` + `setKeyScheme(AES256_GCM)` from alpha. Migration
  to stable requires rewriting `SecureCookiesPreferences` and a key
  rotation.
- **Planned exit:** Tink migration (the androidx-security team's own
  recommended replacement).
- **Pick up when:** team has bandwidth for a key-rotation round-trip +
  Tink spike.

### D-04 — `minitemplator 1.2` (abandoned) → fork or vendor
- **Status:** dependency left in place. See comment in
  `gradle/libs.versions.toml` near `minitemplator`.
- **Why deferred:** replacement of the templating engine requires
  rewriting every `*.mtl` file (HTML rendering for Theme/QMS/News).
  Vendor-the-source is a few-hour job; a real templating engine swap
  is a sprint.
- **Pick up when:** there is a planned UI change that touches the
  template layer — the right time to vendor or replace.

### T-01 — `detekt-baseline.xml` 484 КБ → burn-down to <50 КБ
- **Status:** config (`detekt.yml`) already only enables 3 high-signal
  rules (`ComplexMethod`, `LongMethod`, `LongParameterList`).
- **Why deferred:** regenerating the baseline requires a full
  `./gradlew detektBaseline` pass (5+ min) and would touch ~500 LOC of
  baseline entries. Not appropriate to do in an unrelated PR.
- **Pick up when:** next detekt config sweep or a major Kotlin
  upgrade — then regenerate the baseline in one focused PR.

## How to use this file

When the team is ready to pick up one of these, copy the relevant section
into a new `docs/PLAN_<TASK_ID>.md` with:
1. Spike result (file:line measurements, profile screenshots).
2. Scope of the PR (one class at a time, public surface snapshot test).
3. Rollout plan (canary / staged release).

Link the new spike from this file under the original task, and update
status to "in progress".

## Carried over from L01 (JS-bridge security audit, closed 2026-06-22)

### L01-MH — `QmsChatFragment` lacks explicit `webView.init(profile)` on re-created WebView
- **Source:** `docs/JS_BRIDGE_SECURITY_AUDIT_2026-06.md` §2.6 (only
  Medium-High finding from the audit).
- **File:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt`
  (re-creation path around the already-closed C-08 fix, lines 2400–2421).
- **Why deferred:** QMS re-init currently inherits `TRUSTED_LOCAL_TEMPLATE`
  via the `ExtendedWebView` constructor default, so the bridge is still
  scoped correctly. The audit recommends an explicit `webView.init(profile)`
  assertion for defense in depth. Non-blocking; pairs naturally with C-08
  (`jsInterface.cancel()` before re-attach).
- **Pick up when:** next QMS / WebView-lifecycle sprint, together with a
  regression test that asserts the security profile after re-creation.
