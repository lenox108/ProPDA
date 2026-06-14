# PROPDA QC-01 Release Sanity

Date: 2026-05-21
Scope: post-modernization regression and release sanity check for theme/topic screen, favorites, QMS, search, profile, app startup, navigation, WebView runtime, reader modes, smart quotes, gestures, media rendering, snackbar/insets, and favorites refresh.

## Build Results

- PASS: `./gradlew :app:compileStableDebugKotlin --no-daemon`
  - Result: BUILD SUCCESSFUL in 7s.
  - Notes: Gradle reported deprecated features incompatible with Gradle 9.0 and generated a problems report. No Kotlin compile errors.
- PASS: WebView JavaScript syntax check with `node --check` over `app/src/main/assets/forpda/scripts/*.js` and `app/src/main/assets/forpda/scripts/modules/*.js`.
- PASS: focused favorites unit tests:
  - `forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesParserTest`
  - `forpdateam.ru.forpda.ui.fragments.favorites.FavoritesAdapterIdentityTest`
- PASS: focused QMS/WebView/text policy tests:
  - `forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModelTest`
  - `forpdateam.ru.forpda.common.webview.UrlPolicyTest`
  - `forpdateam.ru.forpda.presentation.LinkHandlerUrlPolicyTest`
  - `forpdateam.ru.forpda.common.ForumPostTextTest`
  - `forpdateam.ru.forpda.common.EditPostBodyNormalizeTest`
- FAIL: broader focused touched-area unit test run completed 200 tests with 12 failures.

## Manual Checklist Results

Manual runtime testing was not available: `adb devices` reported no connected device or emulator. Items below are NOT RUNTIME VERIFIED unless otherwise stated.

- Theme/topic screen: NOT RUNTIME VERIFIED. Compile passed and JS syntax passed, but focused theme tests contain failures listed below. Runtime items not verified: topic opens, posts visible, no blank screen, scrolling, back restore, unread jump, pagination, Smart Quotes, Reader Modes, long press, reply/quote/full quote/edit, images render/open, spoilers, and code block readability.
- Favorites: NOT RUNTIME VERIFIED. Static/unit evidence is positive: focused favorites parser and adapter identity tests passed. Runtime items not verified: list opens, manual refresh, fresh updated-topic state, duplicate counters, and duplicate rows.
- Insets/messages: NOT RUNTIME VERIFIED. Snackbar/message behavior with 3-button navigation, gesture navigation, and keyboard coverage requires device verification.
- QMS: NOT RUNTIME VERIFIED. QMS contacts ViewModel focused test passed. Runtime items not verified: contacts open, chat opens, send flow, attachments, and media.
- Search/profile/navigation: NOT RUNTIME VERIFIED. Search focused run has failures listed below. Main tabs, profile, back navigation, and crash-loop checks require device verification.
- Runtime/WebView: NOT RUNTIME VERIFIED. WebView JS syntax passed. Device-only items not verified: duplicate WebView attach crash, JS console errors, runaway timers, scroll jumps, and visible scroll jank in long topic.

## Known Failures

Broader focused touched-area test command:

```text
./gradlew :app:testStableDebugUnitTest --no-daemon \
  --tests 'forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesParserTest' \
  --tests 'forpdateam.ru.forpda.ui.fragments.favorites.FavoritesAdapterIdentityTest' \
  --tests 'forpdateam.ru.forpda.presentation.theme.*' \
  --tests 'forpdateam.ru.forpda.model.data.remote.api.theme.*' \
  --tests 'forpdateam.ru.forpda.model.interactors.theme.*' \
  --tests 'forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModelTest' \
  --tests 'forpdateam.ru.forpda.model.data.remote.api.search.SearchApiTest' \
  --tests 'forpdateam.ru.forpda.presentation.search.SearchViewModelTest' \
  --tests 'forpdateam.ru.forpda.common.webview.UrlPolicyTest' \
  --tests 'forpdateam.ru.forpda.presentation.LinkHandlerUrlPolicyTest' \
  --tests 'forpdateam.ru.forpda.common.ForumPostTextTest' \
  --tests 'forpdateam.ru.forpda.common.EditPostBodyNormalizeTest'
```

Result: 200 tests completed, 12 failed.

Failures:

- `SearchApiTest > parse_forumPostPrefersResultEntryIdOverBodyPostLink`: `NullPointerException` at `SearchApiTest.kt:57`.
- `ThemeApiRelocationExtractionTest > extractsHrefWithForumPrefixAndEntities`: `AssertionError` at `ThemeApiRelocationExtractionTest.kt:75`.
- `SearchViewModelTest > user topic search result opens posts by searched user in selected topic`: `ComparisonFailure` at `SearchViewModelTest.kt:214`.
- `ThemeTemplateTest > topic hat uses first post rating value without first post buttons`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:91`.
- `ThemeTemplateTest > readonly result poll still renders results fallback`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:319`.
- `ThemeTemplateTest > non-own post with known rating shows rating buttons`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:71`.
- `ThemeTemplateTest > topic hat uses cached first page rating without buttons`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:175`.
- `ThemeTemplateTest > poll with no parsed buttons still renders results fallback`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:290`.
- `ThemeTemplateTest > unknown current user id keeps rating buttons visible`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:81`.
- `ThemeTemplateTest > topic hat uses resolved first post voted rating state`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:131`.
- `ThemeTemplateTest > own post hides rating buttons`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:60`.
- `ThemeTemplateTest > voteable poll renders enabled options and results target`: `MiniTemplator.VariableNotDefinedException` at `ThemeTemplateTest.kt:257`.

## New Regressions Found

- No app code was changed during QC-01.
- Potential release-blocking regression evidence exists in theme template tests: multiple `ThemeTemplateTest` cases fail with `MiniTemplator.VariableNotDefinedException`, which may indicate template/data contract drift affecting topic rendering, post rating controls, and poll rendering.
- Potential search regression evidence exists in `SearchApiTest` and `SearchViewModelTest` failures.
- Potential moved-topic/relocation regression evidence exists in `ThemeApiRelocationExtractionTest`.

## Critical Blockers

- Runtime checklist could not be executed because no device/emulator was attached.
- Focused touched-area tests are red with 12 failures in theme/search/relocation areas.
- Given the failing topic/search tests and lack of runtime verification, this QC pass cannot mark the release as ready.

## Release Readiness Verdict

NOT READY

Reason: required compile passes and several focused checks pass, but broader focused regression tests fail in theme/search/relocation areas and runtime behavior was not device-verified.
