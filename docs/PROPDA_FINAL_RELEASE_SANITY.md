# PROPDA Final Release Sanity

Date: 2026-05-21

Scope: FIX-13 final verification before beta/release after the stabilization fixes. This pass did not change app code, Gradle configuration, or UI behavior.

## Checks Run

- PASS: `./gradlew :app:compileStableDebugKotlin --no-daemon`
  - Result: BUILD SUCCESSFUL in 7s.
  - Notes: Kotlin compile completed with no errors. Gradle reported deprecated features incompatible with Gradle 9.0 and generated a problems report.
- PASS: `./gradlew :app:assembleStableRelease --no-daemon`
  - Result: BUILD SUCCESSFUL in 2m 49s.
  - Notes: Stable release APK packaging completed. Kotlin/Android deprecation and unused-code warnings remain.
- FAIL: focused mentions/favorites/url/theme/search/QMS unit sanity run:
  - Command: `./gradlew :app:testStableDebugUnitTest --tests 'forpdateam.ru.forpda.presentation.mentions.MentionsViewModelTest' --tests 'forpdateam.ru.forpda.model.repository.mentions.MentionsRepositoryTest' --tests 'forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepositoryTest' --tests 'forpdateam.ru.forpda.ui.fragments.favorites.FavoritesAdapterIdentityTest' --tests 'forpdateam.ru.forpda.common.webview.UrlPolicyTest' --tests 'forpdateam.ru.forpda.presentation.LinkHandlerUrlPolicyTest' --tests 'forpdateam.ru.forpda.presentation.theme.ThemeUrlPolicyTest' --tests 'forpdateam.ru.forpda.model.interactors.theme.ThemeUseCaseTest' --tests 'forpdateam.ru.forpda.presentation.search.SearchViewModelTest' --tests 'forpdateam.ru.forpda.model.data.remote.api.search.SearchApiTest' --tests 'forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModelTest' --no-daemon`
  - Result: 62 tests completed, 2 failed.
- FAIL: additional favorites/media/theme/error focused unit sanity run:
  - Command: `./gradlew :app:testStableDebugUnitTest --tests 'forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesParserTest' --tests 'forpdateam.ru.forpda.model.data.remote.api.news.ArticleParserImageTest' --tests 'forpdateam.ru.forpda.model.data.remote.api.theme.ThemeParserSnapshotTest' --tests 'forpdateam.ru.forpda.presentation.theme.ThemeRenderGuardTest' --tests 'forpdateam.ru.forpda.model.data.remote.api.checker.CheckerParserTest' --no-daemon`
  - Result: 30 tests completed, 16 failed.
- PASS: `adb devices`
  - Result: command succeeded, but no device or emulator was listed.

## Pass

- Stable debug Kotlin compile passes.
- Stable release assemble passes and produces the release APK.
- Mentions read-state focused coverage passed within the first focused run:
  - `forpdateam.ru.forpda.presentation.mentions.MentionsViewModelTest`
  - `forpdateam.ru.forpda.model.repository.mentions.MentionsRepositoryTest`
- Favorites refresh/identity focused coverage passed within the focused runs:
  - `forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepositoryTest`
  - `forpdateam.ru.forpda.ui.fragments.favorites.FavoritesAdapterIdentityTest`
  - `forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesParserTest`
- URL/link policy coverage passed within the first focused run:
  - `forpdateam.ru.forpda.common.webview.UrlPolicyTest`
  - `forpdateam.ru.forpda.presentation.LinkHandlerUrlPolicyTest`
  - `forpdateam.ru.forpda.presentation.theme.ThemeUrlPolicyTest`
- QMS contacts basic ViewModel coverage passed:
  - `forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModelTest`
- Theme use case/render guard/parser snapshot coverage selected for this pass did not report failures in the focused runs.
- Error/checker parser coverage selected for this pass did not report failures in the focused runs.

## Fail / Not Verified

- FAIL: Search parser/navigation focused tests are red:
  - `SearchApiTest > parse_forumPostPrefersResultEntryIdOverBodyPostLink`: `NullPointerException` in `HtmlToSpannedConverter.convert`, reached through `SearchParser.parse`.
  - `SearchViewModelTest > user topic search result opens posts by searched user in selected topic`: expected `SearchedUser`, actual empty string.
- FAIL: Media/news image focused tests are red:
  - `ArticleParserImageTest`: 16 failures, all reported as `NullPointerException`, covering fallback article image parsing and poll/news article parsing cases.
- NOT VERIFIED: runtime topic opening, no blank topic, answers/read-state UX, Smart Button, Reader Modes, images in WebView/image viewer, snackbar/insets, runtime error states, QMS open, search open, and profile open.
  - Reason: no attached Android device or emulator was available.
- NOT VERIFIED: logcat/runtime crash-loop behavior, WebView console health, gesture/3-button navigation insets, keyboard snackbar overlap, and media-heavy topic scrolling.

## Known Issues

- Focused release-critical search tests are failing and need triage before treating search/profile/topic result navigation as release-ready.
- Focused media/news parser tests are failing with `NullPointerException`; this leaves article image/poll parsing risk open.
- Manual runtime QA remains unavailable in this environment because `adb devices` returned no connected target.
- Gradle deprecation warnings remain and should be tracked before a future Gradle 9 upgrade, but they did not block the current build.
- Kotlin/Android compile warnings remain for deprecated APIs, unused parameters, unnecessary safe calls, and opt-in annotations. These warnings did not block compile or release assemble.
- Previous release documents still record broader unresolved red tests in topic template/search/relocation areas; this pass did not retest all of those broader failures.

## Release Blockers

- P0 blockers: none confirmed by compile/build checks in this pass.
- P1 blockers before beta/full release confidence:
  - Search focused tests are red.
  - Media/news image focused tests are red.
  - Runtime sanity cannot be signed off without a device/emulator pass.

## Beta Readiness Verdict

NOT READY for full beta sign-off from this environment.

The release APK builds, and there are no confirmed P0 compile/build blockers. However, focused release-critical tests are still red in search and media parsing, and required manual runtime checks were not possible without an attached device or emulator. Beta can only proceed as a risk-accepted build if these known issues are explicitly accepted and device QA is completed separately.
