# PROPDA RC-01 Beta Release Readiness

Date: 2026-05-21
Scope: beta release readiness assessment after UI modernization, stabilization, hardening, and performance passes.

## Verdict

NOT READY

Recommended go/no-go decision: NO-GO for beta until release-critical focused tests are fixed or explicitly accepted, and minimum runtime QA is completed on a device or emulator.

## Build Results

- PASS: `./gradlew :app:compileStableDebugKotlin --no-daemon`
  - Result: BUILD SUCCESSFUL in 8s.
  - Notes: Gradle reported deprecated features incompatible with Gradle 9.0 and generated a problems report. No Kotlin compile errors.
- PASS: `./gradlew :app:assembleStableRelease --no-daemon`
  - Result: BUILD SUCCESSFUL in 1m 55s.
  - Notes: Release Kotlin compile emitted warnings for unused parameters and deprecated Android/WebView APIs; `lintVitalStableRelease`, R8 minification, resource optimization, and APK packaging completed.
- FAIL: focused release-critical unit tests:
  - Command: `./gradlew :app:testStableDebugUnitTest --no-daemon --tests 'forpdateam.ru.forpda.model.data.remote.api.search.SearchApiTest' --tests 'forpdateam.ru.forpda.presentation.search.SearchViewModelTest' --tests 'forpdateam.ru.forpda.presentation.theme.ThemeTemplateTest' --tests 'forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApiRelocationExtractionTest'`
  - Result: 25 tests completed, 12 failed.

## APK

- Path: `app/build/outputs/apk/stable/release/ProPDA-2.8.4-stableRelease.apk`
- Modified: 2026-05-21 11:30:50 +0400
- Size: 6,982,434 bytes

## Git Working Tree Summary

The working tree is intentionally dirty and was not reverted. Important dirty categories observed:

- CI/build/release configuration: `.github/workflows/android-ci.yml`, `.gitignore`, `app/build.gradle`, `app/proguard-rules.pro`, Android manifests.
- Web assets/UI modernization: `app/src/main/assets/forpda` scripts, styles, templates, icons, patterns, and generated visual/archive artifacts.
- Kotlin migration and app code changes: Java deletions with Kotlin replacements across application, client, common, WebView, presentation, and feature packages.
- Release/update/hardening additions: app update classes, network/client interceptors, helpers, lifecycle/runtime cleanup, snackbar/helper utilities.
- Documentation and QC artifacts: changelogs, update notes, QC/hardening/performance reports, and this RC-01 readiness report.

## Release Blockers

- Focused release-critical tests are red in topic rendering, search navigation, and relocated topic parsing areas.
  - `SearchApiTest > parse_forumPostPrefersResultEntryIdOverBodyPostLink`: `NullPointerException`.
  - `ThemeApiRelocationExtractionTest > extractsHrefWithForumPrefixAndEntities`: `AssertionError`.
  - `SearchViewModelTest > user topic search result opens posts by searched user in selected topic`: `ComparisonFailure`.
  - `ThemeTemplateTest`: 9 failures with `MiniTemplator.VariableNotDefinedException` covering rating controls, topic hat rating state, and poll rendering.
- Runtime/manual QA remains unavailable in the current pass; previous QC noted no attached device or emulator.
- Existing broader full unit status is not clean: `docs/AI_SPEC_COMPLETION_STATUS.md` records full `testStableDebugUnitTest` failing with 395 tests completed and 40 failed, and `docs/PROPDA_QC_01_RELEASE_SANITY.md` records the broader touched-area focused run failing 200 tests with 12 failures.

## Known Non-Blocking Issues

- Gradle deprecation warnings indicate future Gradle 9.0 incompatibility, but they do not block the current release build.
- Release compile warnings remain for unused parameters and deprecated Android/WebView APIs.
- `theme.js` and Theme WebView runtime still carry documented technical debt around global mutable state, legacy listeners, scroll restore, and WebView lifecycle complexity.
- Performance improvements were assessed statically/build-wise; manual profiling was not available.

## Manual QA Required Before Beta

- Install and launch the generated stable release APK on at least one real device or emulator.
- Verify startup, login/session retention, logout/login, main navigation, profile, back stack, and crash-loop absence.
- Verify topic screen runtime: open topics, direct post links/findpost, pagination, refresh, scroll restore, unread jump, reader modes, smart quotes, long press, reply/quote/full quote/edit flows, spoilers, polls, rating controls, code blocks, images, and image viewer return.
- Verify QMS: contacts, theme list, chat open, send flow, attachments, and media rendering.
- Verify favorites: list open, manual refresh, updated-topic state, counters, add/remove, and duplicate row/counter behavior.
- Verify search: forum/topic/user result opening, selected-topic user search, and result navigation.
- Verify WebView/runtime lifecycle: rotate, background/foreground, rapid navigation, topic reopen, child topic/back, no duplicate attach crash, no runaway timers, no visible scroll jank in long/media-heavy topics, and clean logcat for expected flows.
- Verify link/download policy: internal topic links in app, external HTTPS externally, blocked `javascript:`, `file:`, and `data:` links, image view/download.
- Verify snackbar/insets with gesture navigation, 3-button navigation, and keyboard.

## Notes

- No code fixes were applied during RC-01 because the observed blockers are test/runtime readiness issues and no small, obvious, high-confidence release-blocking fix was identified within RC-01 scope.
- RC-02 changelog/release notes and RC-03 beta tester checklist were not started.
