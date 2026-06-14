# PROPDA Audit Fix Final Sanity

Date: 2026-05-21

Task: AUDIT-FIX-10

## Scope

Final release sanity after AUDIT-FIX-01 through AUDIT-FIX-09. This pass verifies the focused audit fixes, compile status, and remaining release risks. No runtime, Gradle, dependency, or UI redesign changes were made.

The working tree was already dirty before this pass; unrelated source changes were not reverted.

## Completed Audit Fixes Verified

- Search parser/navigation sanity is now green in the required focused Search run.
- Article/news image and poll parser sanity is now green in the required `ArticleParserImageTest` run.
- Mentions read-state and badge sanity is covered by the required `*Mentions*` focused run and passed.
- Favorites force-refresh, cached read event, live notification badge, inspector hints, and stale-cache read-state sanity are covered by the required `*Favorites*` focused run and passed.
- External URL policy sanity is statically present through `UrlPolicy.classify(...)` usage in WebView/navigation/external-dispatch paths and through focused URL policy tests. The broader URL/theme add-on run was attempted; URL policy tests were not reported as failing.
- Topic open sanity is statically covered by theme URL parsing and topic/render read lifecycle tests. No device/emulator runtime topic-open pass was available.
- Answers badge/read-state sanity was checked through the same rendered-topic/read lifecycle path used to mark visible topic posts read, and through mentions unread snapshot tests. No manual Answers screen runtime pass was available.
- WebView/Theme no-blank-topic sanity is partially covered by render-token guard, DOM/render lifecycle, and theme URL tests. A separate `ThemeTemplateTest` cluster remains red and is documented below.
- AUDIT-FIX-09 StrictMode review remains documentation-only: no attached device/emulator was available for Logcat capture.

## Required Test Results

PASS: `./gradlew :app:compileStableDebugKotlin --no-daemon`

- Result: `BUILD SUCCESSFUL in 5s`
- Exit marker: `compileStableDebugKotlin exit=0`

PASS: `./gradlew :app:testStableDebugUnitTest --tests '*SearchApiTest*' --tests '*SearchViewModelTest*' --no-daemon`

- Result: `BUILD SUCCESSFUL in 8s`
- Exit marker: `Search focused tests exit=0`

PASS: `./gradlew :app:testStableDebugUnitTest --tests '*ArticleParserImageTest*' --no-daemon`

- Result: `BUILD SUCCESSFUL in 7s`
- Exit marker: `ArticleParserImageTest exit=0`

PASS: `./gradlew :app:testStableDebugUnitTest --tests '*Mentions*' --no-daemon`

- Result: `BUILD SUCCESSFUL in 7s`
- Exit marker: `Mentions focused tests exit=0`

PASS: `./gradlew :app:testStableDebugUnitTest --tests '*Favorites*' --no-daemon`

- Result: `BUILD SUCCESSFUL in 8s`
- Exit marker: `Favorites focused tests exit=0`

## Additional Sanity Attempt

FAIL: `./gradlew :app:testStableDebugUnitTest --tests '*ThemeUrlPolicyTest*' --tests '*ThemeUseCaseTest*' --tests '*ThemeRenderGuardTest*' --tests '*ThemeJsInterfaceTest*' --tests '*UrlPolicyTest*' --tests '*LinkHandlerUrlPolicyTest*' --tests '*ThemeTemplateTest*' --tests '*ThemeApiMovedTopicProbeTest*' --tests '*ThemeApiFindUnreadGetNewPostTest*' --no-daemon`

- Result: `53 tests completed, 9 failed`
- Failed cluster: `ThemeTemplateTest`
- Failure type: `biz.source_code.miniTemplator.MiniTemplator$VariableNotDefinedException`
- Failed cases:
  - `own post hides rating buttons`
  - `non-own post with known rating shows rating buttons`
  - `unknown current user id keeps rating buttons visible`
  - `topic hat uses first post rating value without first post buttons`
  - `topic hat uses resolved first post voted rating state`
  - `topic hat uses cached first page rating without buttons`
  - `voteable poll renders enabled options and results target`
  - `poll with no parsed buttons still renders results fallback`
  - `readonly result poll still renders results fallback`

This broader add-on run was not one of the required commands, but it is relevant to the WebView/Theme no-blank-topic sanity area. No code fix was applied because this task allows only tiny release-blocker fixes, and the failure cluster needs template/test contract triage rather than an obvious one-line release fix.

## Manual / Runtime Checks

Runtime manual/device checks were not executed.

Reason: no Android device or emulator was available in this environment during the final sanity pass. The following remain documentation-only until a device/emulator QA pass is completed:

- Topic open sanity on normal, moved, unread, and image-heavy topics.
- Answers badge/read-state visual sanity.
- Favorites refresh visual sanity after pull-to-refresh/background return.
- External URL policy sanity from actual WebView/news/theme UI clicks.
- WebView/Theme no blank topic sanity with Logcat/WebView console observation.
- StrictMode runtime Logcat capture from AUDIT-FIX-09.

## Known Remaining Issues

- `ThemeTemplateTest` still has a red cluster with `MiniTemplator.VariableNotDefinedException`. This is the main known automated sanity gap after the required AUDIT-FIX-10 checks passed.
- Runtime/manual QA is still unavailable, so topic open, Answers UI, Favorites UI refresh, external URL UI dispatch, and WebView blank-topic behavior cannot be fully signed off from this environment.
- Existing Gradle deprecation warnings remain and Gradle reports deprecated features incompatible with Gradle 9.0. These warnings did not block compile or focused tests.
- Broader full unit status was not re-run in this task; only required focused runs and one additional targeted sanity run were executed.

## Release Blockers

- P0 blockers: none confirmed by the required compile and focused audit-fix checks.
- P1 blockers / release sign-off blockers:
  - `ThemeTemplateTest` red cluster in the additional WebView/Theme sanity run.
  - Missing device/emulator runtime QA for release-critical UI flows.

## Beta Readiness Verdict

READY WITH KNOWN ISSUES

Rationale: all required AUDIT-FIX-10 compile and focused audit-fix test commands passed, and the previously documented Search and ArticleParserImage focused failures are now green. However, beta sign-off still needs explicit risk acceptance for the red `ThemeTemplateTest` cluster and for the missing runtime/device QA pass.

