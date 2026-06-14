# PROPDA Final Code Audit Report

Дата: 2026-05-21

Область аудита: финальная проверка кода и релизных рисков перед beta/release. Этот отчет не вносит исправления в код и опирается на текущие документы sanity-проверок, инвентарь JS bridge и точечный осмотр затронутых файлов.

## Critical issues

### C1. Релизная готовность не подтверждена

- Проблема: статически подтвержденного P0 crash/blocker не найдено, но текущий `docs/PROPDA_FINAL_RELEASE_SANITY.md` фиксирует красные focused-тесты Search/media и отсутствие device/emulator QA.
- Почему важно: релизный APK собирается, но без зеленых release-critical тестов и ручной проверки на устройстве нельзя считать beta/full release sign-off завершенным.
- Затронутые файлы: `docs/PROPDA_FINAL_RELEASE_SANITY.md`, `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApiTest.kt`, `app/src/test/java/forpdateam/ru/forpda/presentation/search/SearchViewModelTest.kt`, `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParserImageTest.kt`.
- Рекомендуемое исправление: сначала починить красные тесты Search/media, затем прогнать focused sanity повторно и выполнить device QA по темам, поиску, медиа, mentions, favorites и внешним ссылкам.
- Уровень риска: Critical для релизного sign-off, P1 по runtime impact до подтверждения.
- Сложность реализации: средняя, потому что нужно разбирать реальные parser/ViewModel регрессии и подтверждать поведение на устройстве.

## High priority issues

### H1. Destructive Theme JS bridge не защищен render-token guard

- Проблема: `ThemeJsInterface` экспортирует широкий набор destructive/sensitive методов (`deletePost`, `editPost`, `votePost`, `submitPoll`, `reply`, `quote*`, `reportPost`, reputation actions), но эти вызовы не проверяют токен текущего trusted render, хотя `ThemeRenderGuard` уже существует.
- Почему важно: WebView bridge является главным остаточным security-risk. Если trusted boundary будет нарушена или в шаблон попадет неожиданный HTML/JS, native-действия темы можно инициировать без дополнительного session/render подтверждения.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt`, `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderGuard.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt`, `app/src/main/assets/forum/js/theme.js`.
- Рекомендуемое исправление: добавить render-token в HTML/JS-шаблон темы и требовать его для destructive bridge calls; invalid/blank/stale token должен блокировать native callback и писать безопасный warning без пользовательского действия.
- Уровень риска: High.
- Сложность реализации: средняя-высокая, потому что нужно синхронно изменить Kotlin bridge, генерацию шаблона, JS-вызовы и тесты на stale-token сценарии.

### H2. Focused Search тесты красные

- Проблема: sanity-документ фиксирует падения `SearchApiTest > parse_forumPostPrefersResultEntryIdOverBodyPostLink` с `NullPointerException` в `HtmlToSpannedConverter.convert` через `SearchParser.parse`, а также `SearchViewModelTest > user topic search result opens posts by searched user in selected topic` с пустым `SearchedUser`.
- Почему важно: поиск является release-critical user flow; ошибка может ломать открытие результатов, переход к постам пользователя или парсинг body/link данных.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApi.kt`, `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchParser.kt`, `app/src/main/java/forpdateam/ru/forpda/presentation/search/SearchViewModel.kt`, `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApiTest.kt`, `app/src/test/java/forpdateam/ru/forpda/presentation/search/SearchViewModelTest.kt`.
- Рекомендуемое исправление: воспроизвести focused Search suite, исправить null-path в parser conversion и восстановить propagation searched-user/topic metadata в ViewModel navigation.
- Уровень риска: High.
- Сложность реализации: средняя.

### H3. Focused media/news image тесты красные

- Проблема: `ArticleParserImageTest` имеет 16 `NullPointerException` failures по fallback article image parsing и poll/news article parsing cases.
- Почему важно: новости, изображения и poll-контент часто видны пользователю сразу; parser NPE может проявиться как пустой экран, потеря картинки или crash в article flow.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParser.kt`, `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParserImageTest.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt`.
- Рекомендуемое исправление: локализовать общий null source в parser fixtures/conversion, вернуть fallback image extraction и poll/news parsing tests в green state.
- Уровень риска: High.
- Сложность реализации: средняя.

### H4. Theme WebView lifecycle остается хрупким

- Проблема: theme flow распределен между `ThemeFragmentWeb`, `ThemeWebController`, `ThemeJsInterface`, шаблонами и `theme.js`; lifecycle, queued JS, pagination, history body, render guard и bridge callbacks пересекаются в одном hot path.
- Почему важно: переписывать этот слой перед beta опасно, но без targeted QA возможны blank topic, stale callbacks, двойные actions, неконсистентный history/render state и WebView-only regressions.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt`, `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`, `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt`, `app/src/main/assets/forum/js/theme.js`.
- Рекомендуемое исправление: не делать широкий rewrite; добавить узкие guards/tests вокруг render-token, DOM ready/page loaded ordering, pagination/history restore и выполнить ручную QA-матрицу на устройстве.
- Уровень риска: High.
- Сложность реализации: высокая для полного исправления, средняя для targeted guard/QA.

## Medium priority issues

### M1. Favorites badge/count edge cases и release-noisy logs

- Проблема: в `FavoritesRepository` остаются шумные `Log.e("testtabnotify", ...)`, а badge/count logic зависит от inspector/cache/read event combinations.
- Почему важно: error-level logs засоряют release diagnostics, а счетчик избранного легко расходится при read/unread событиях, live tab notifications и cached inspector hints.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/model/repository/faviorites/FavoritesRepository.kt`, `app/src/test/java/forpdateam/ru/forpda/model/repository/faviorites/FavoritesRepositoryTest.kt`, `app/src/test/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesAdapterIdentityTest.kt`.
- Рекомендуемое исправление: убрать или перевести debug logs в gated `Timber.d`, добавить tests для inspector unread hints, cached read events, read event decrement и live-tab combinations.
- Уровень риска: Medium.
- Сложность реализации: низкая-средняя.

### M2. Mentions read-state overlay может расходиться после process death/offline

- Проблема: read-state stabilization для mentions опирается на локальное in-memory/cache overlay поведение, границы которого после process death, offline mode и delayed sync не полностью подтверждены.
- Почему важно: пользователь может видеть повторно непрочитанные mentions или потерять визуальное подтверждение прочтения после рестарта/сети, даже если текущие focused tests зеленые.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/presentation/mentions/MentionsViewModel.kt`, `app/src/main/java/forpdateam/ru/forpda/model/repository/mentions/MentionsRepository.kt`, `app/src/test/java/forpdateam/ru/forpda/presentation/mentions/MentionsViewModelTest.kt`, `app/src/test/java/forpdateam/ru/forpda/model/repository/mentions/MentionsRepositoryTest.kt`.
- Рекомендуемое исправление: документировать expected boundaries и добавить tests на restart/offline/delayed server refresh сценарии.
- Уровень риска: Medium.
- Сложность реализации: средняя.

### M3. WebView request path/avatar intercept может вызывать jank

- Проблема: avatar/image request interception и repository/cache lookup в WebView path могут выполнять синхронную работу на чувствительном пути загрузки страницы.
- Почему важно: на тяжелых темах это проявится как spinner jank, задержки скролла, поздняя загрузка аватаров или dropped frames.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt`, `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt`, `app/src/main/java/forpdateam/ru/forpda/client/ForPdaCoil.kt`, theme/news WebView fragments.
- Рекомендуемое исправление: профилировать media-heavy topic на устройстве, измерить blocking work в intercept path, кэшировать aggressively и переносить тяжелые операции с критического WebView request path.
- Уровень риска: Medium.
- Сложность реализации: средняя-высокая.

### M4. ArticleContentFragment external browser path требует явной UrlPolicy проверки

- Проблема: `ArticleContentFragment.openExternalBrowser(url)` передает URL в `ExternalBrowserLauncher.open` через `openExternalBrowserOnly`; в осмотренном коде не видно локальной `UrlPolicy.classify` проверки на этом bridge method.
- Почему важно: news bridge пересекает URL/content boundary. Даже если WebView загружает trusted static article HTML, external intent должен иметь тот же allow/block contract, что WebView navigation и theme `openLink`.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt`, `app/src/main/java/forpdateam/ru/forpda/common/webview/UrlPolicy.kt`, `app/src/main/java/forpdateam/ru/forpda/presentation/SystemLinkHandler.kt`.
- Рекомендуемое исправление: перед `ExternalBrowserLauncher.open` классифицировать URL через `UrlPolicy`, блокировать unsafe schemes и покрыть `openExternalBrowser` unit/instrumentation test или narrow Robolectric-style test.
- Уровень риска: Medium с security оттенком; High, если bridge может получить untrusted URL.
- Сложность реализации: низкая-средняя.

## Low priority polish

### L1. Крупные классы усложняют сопровождение

- Проблема: `ThemeViewModel`, `ThemeFragmentWeb` и `ThemeFragment` остаются большими классами с несколькими ответственностями.
- Почему важно: каждый hotfix в теме несет риск collateral regression, сложнее ревьюить lifecycle и state transitions.
- Затронутые файлы: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`, `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt`.
- Рекомендуемое исправление: после релиза постепенно выносить bridge policy, pagination/history state, render lifecycle и UI action routing в небольшие модули с тестами.
- Уровень риска: Low для beta, Medium для дальнейшей скорости разработки.
- Сложность реализации: высокая, поэтому не делать перед релизом.

### L2. Остаточные fullscreen/immersive references нужно ограничить ожидаемыми зонами

- Проблема: minimap references удалены, но immersive/fullscreen behavior может оставаться в image viewer/fullscreen editor flows.
- Почему важно: если immersive flags применяются шире ожидаемого, возможны regressions со статусбаром, gesture navigation и IME.
- Затронутые файлы: image viewer/fullscreen editor related classes, theme/news media entry points.
- Рекомендуемое исправление: проверить search по immersive/fullscreen flags, подтвердить, что они ограничены image viewer/fullscreen editor, и добавить ручной QA шаг.
- Уровень риска: Low.
- Сложность реализации: низкая.

## Performance bottlenecks

### P1. Theme/WebView rendering и spinner/loading jank

- Проблема: тяжелая тема совмещает HTML render, JS bridge callbacks, avatar/media intercept, history restore и UI loading state.
- Почему важно: даже без crash пользователь может получить blank topic, долгий spinner, рывки скролла или поздние действия Smart Button.
- Затронутые файлы: `ThemeFragmentWeb.kt`, `ThemeWebController.kt`, `ThemeViewModel.kt`, `CustomWebViewClient.kt`, `AvatarRepository.kt`, `ForPdaCoil.kt`, `theme.js`.
- Рекомендуемое исправление: сделать device profiling на длинной теме с картинками, собрать logcat/WebView console, проверить DOM ready/page loaded timing и request interception latency.
- Уровень риска: Medium-High для UX.
- Сложность реализации: средняя.

### P2. Parser/converter null-paths в Search/media

- Проблема: текущие NPE в Search/media тестах указывают на parser/converter paths, которые не выдерживают некоторые fixture HTML variants.
- Почему важно: parser failures могут быть data-dependent и всплыть только на определенных темах, новостях или результатах поиска.
- Затронутые файлы: Search parser/API/ViewModel и Article parser/tests.
- Рекомендуемое исправление: исправлять root cause через parser contract и fixtures, не через broad try/catch suppression.
- Уровень риска: High для affected flows.
- Сложность реализации: средняя.

## Security risks

### S1. Главный остаточный риск: trusted JS bridge surface

- Проблема: URL policy уже заметно усилен, но самый широкий trusted boundary остается в theme `IThemePresenter`.
- Почему важно: destructive native actions из JS должны требовать актуального render/session proof, иначе компрометация шаблона или unexpected injected call имеет слишком большой blast radius.
- Затронутые файлы: `ThemeJsInterface.kt`, `ThemeRenderGuard.kt`, `ThemeBridgeHandler.kt`, `ThemeFragmentWeb.kt`, `theme.js`, `docs/JS_BRIDGE_INVENTORY.md`.
- Рекомендуемое исправление: token-guard destructive calls, добавить negative tests для missing/stale/wrong token, сохранить URL policy checks для link methods.
- Уровень риска: High.
- Сложность реализации: средняя-высокая.

### S2. External URL dispatch должен везде проходить через UrlPolicy

- Проблема: большинство navigation/download paths уже используют `UrlPolicy`, но news `openExternalBrowser` требует явного подтверждения или исправления.
- Почему важно: единый URL policy снижает риск arbitrary scheme/intent dispatch из HTML/JS.
- Затронутые файлы: `ArticleContentFragment.kt`, `UrlPolicy.kt`, `SystemLinkHandler.kt`, `ExtendedWebView.kt`, `CustomWebViewClient.kt`.
- Рекомендуемое исправление: закрыть оставшиеся bridge/external-browser entry points через `UrlPolicy.classify`; заблокированные URL логировать без открытия intent.
- Уровень риска: Medium.
- Сложность реализации: низкая-средняя.

## UI/UX suggestions

### U1. Обязательная device QA матрица

- Проблема: текущая sanity-проверка не имела доступного device/emulator, поэтому не проверены runtime topic opening, read-state UX, images, snackbar/insets, QMS/search/profile open и WebView console health.
- Почему важно: основные риски текущей ветки находятся в WebView/UI lifecycle и не покрываются только compile/assemble.
- Затронутые файлы: theme, mentions, favorites, news/media, search, QMS UI flows.
- Рекомендуемое исправление: выполнить smoke на физическом устройстве или emulator: тема с длинной страницей и картинками, quote/reply/edit menu без destructive submit, search result open, mentions read, favorites badge, image viewer, external links, IME/snackbar, gesture и 3-button navigation.
- Уровень риска: High для release sign-off.
- Сложность реализации: низкая-средняя, требует устройства.

### U2. Accessibility и scaling polish

- Проблема: icon-only actions, TalkBack labels, large font scaling, snackbar/IME overlap и loading spinner behavior не подтверждены.
- Почему важно: эти проблемы редко ловятся unit-тестами, но заметно ухудшают beta feedback и доступность.
- Затронутые файлы: theme/news/search/favorites layouts and fragments, snackbar/insets helpers.
- Рекомендуемое исправление: добавить ручную QA checklist для TalkBack, font scale 1.3-1.5, landscape/IME, snackbar overlap и touch target sizes.
- Уровень риска: Medium.
- Сложность реализации: низкая для QA, средняя для исправлений.

## Recommended implementation order

1. Починить красные focused-тесты Search: `SearchApiTest` и `SearchViewModelTest`.
2. Починить красные focused-тесты media/news: `ArticleParserImageTest`.
3. Добавить render-token guard для destructive methods в `ThemeJsInterface` и соответствующие JS/template/tests changes.
4. Убрать release-noisy `Log.e("testtabnotify", ...)` и расширить Favorites tests по inspector/cache/read events.
5. Проверить и при необходимости закрыть `ArticleContentFragment.openExternalBrowser` через `UrlPolicy`.
6. Выполнить device QA matrix для theme/mentions/favorites/search/news/linking/IME/insets.
7. После beta отдельно планировать постепенный split крупных theme классов и performance profiling.

## What NOT to do

1. Не делать широкий rewrite `ThemeFragmentWeb`, `ThemeWebController`, `ThemeViewModel` или `theme.js` прямо перед beta.
2. Не маскировать NPE в Search/media parser broad `try/catch` без восстановления expected parser output и тестов.
3. Не расширять JS bridge surface и не добавлять новые `@JavascriptInterface` методы без security review.
4. Не обходить `UrlPolicy` для новых external browser/download/link entry points.
5. Не считать compile/assemble success релизным sign-off без зеленых focused tests и device QA.
6. Не оставлять release шум в `Log.e` для диагностических debug-сообщений.

## Files most likely needing attention

- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderGuard.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt`
- `app/src/main/assets/forum/js/theme.js`
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApi.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchParser.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/search/SearchViewModel.kt`
- `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApiTest.kt`
- `app/src/test/java/forpdateam/ru/forpda/presentation/search/SearchViewModelTest.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParser.kt`
- `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParserImageTest.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/repository/faviorites/FavoritesRepository.kt`
- `app/src/test/java/forpdateam/ru/forpda/model/repository/faviorites/FavoritesRepositoryTest.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/mentions/MentionsViewModel.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/repository/mentions/MentionsRepository.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt`
- `app/src/main/java/forpdateam/ru/forpda/client/ForPdaCoil.kt`
- `app/src/main/java/forpdateam/ru/forpda/common/webview/UrlPolicy.kt`
