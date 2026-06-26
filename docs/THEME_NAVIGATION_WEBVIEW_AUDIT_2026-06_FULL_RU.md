# Theme WebView · Navigation / Scroll / Anchor / Highlight — ПОЛНЫЙ АУДИТ

Дата: 2026-06-24
Проект: PROPDA / ForPDA Android client
Статус: read-only диагностика. Код не менялся. Это единственный файл, который был создан.
Метод: чтение реального кода. Все ссылки `file:line` проверены на текущем рабочем дереве.

> Замечание о сборке: gradle compile/test не запускались (запрещены и медленны). Тесты прочитаны как исходники, а не выполнены. Где нужен runtime — отмечено «Недостаточно данных».

---

## 1. Executive Summary (наиболее вероятные причины)

Расследование подтвердило, что **инфраструктура подсветки (CSS-класс + JS + контракт) корректна и закрыта тестами** — это важный сдвиг относительно черновика. Открытые вопросы F-04/F-05 из черновика **закрыты**: методы `consumeLinkSourceAnchorFor`, `beginScrollCommand`, `getPendingScrollCommand`, `onLinkSourceAnchorCaptured` реально существуют и связаны (см. §3, §6).

Наиболее вероятные корневые причины наблюдаемых проблем:

1. **«Не та» страница, а не «не та» подсветка.** Подсветка зависит от того, что нужный пост физически присутствует в DOM текущей отрендеренной страницы (`HighlightResolver` отбрасывает off-page цель; JS `PPDA_applyHighlight` отвергает `post_not_on_page`). Если unread-резолвер открыл не ту страницу (первый пост на последней странице), подсветке нечего показывать → она «не работает». То есть симптом №9 во многом является следствием симптомов №1/№2. (§4, §9)

2. **Хрупкая интерпретация серверного редиректа `view=getnewpost`.** Цепочка `TopicUnreadOpenPolicy.resolveGetNewPostAnchor` имеет длинный fallback (html-маркер → redirect-hash → highlight= → p= → canonical → page-top-reject → entry-fallback → data-post). На «прочитанной» теме, открытой через getnewpost только из-за настройки LAST_UNREAD, сервер редиректит на нижнюю/верхнюю закладку, и эвристики «bottom hint / top hint / ambiguous» решают судьбу якоря. Любая рассинхронизация серверного read-state и списочного хинта → посадка на первый пост последней страницы. (§4)

3. **Подсветка откладывается до завершения «блокирующего» скролла и зависит от повторного arming.** `HighlightArmingPolicy.shouldDeferUntilScrollSettled` откладывает нативное применение подсветки, пока выполняется INITIAL_ANCHOR/блокирующий скролл. Повторное применение зависит от `onWebViewContentRevealed` / `reapplyTopicHighlightAfterScrollSettled` / завершения scroll-command. Если scroll-command «зависает» и завершается через safety-fallback, окно подсветки (2 c) может стартовать невидимо или вообще не повторно армироваться. (§9, §10)

4. **Кросс-топиковые внутренние ссылки открываются в НОВОЙ вкладке** (`router.tryOpenTopicInNewTab`), поэтому «назад из Темы B в Тему A» опирается на back-стек вкладок/системы, а не на внутренний `ThemeHistoryController`. In-tab `historyController` для Темы A при этом не получает запись о переходе в Тему B → возврат может не вернуть исходный пост/скролл так, как ожидает пользователь. (§5, §6)

5. **Слишком много перекрывающихся guard-ов скролла/раскрытия** (`ThemeOpenScrollCoalescePolicy`, `TopicOpenScrollRestorePolicy`, deferral подсветки, JS retry-лестницы, `__themeScrollCommandId`, `unreadInitialAnchorPending`, `themeAnchorScrollGeneration`, render-watchdog). Они корректны по отдельности, но их взаимодействие во времени — самый вероятный источник «иногда» багов №1/№4/№5/№6/№9. (§10, §12)

Никаких изменений кода не предлагается к немедленному применению. Сначала — runtime-диагностика (логи уже есть в коде, см. §13).

---

## 2. Current Architecture Map

### 2.1 Слои и ключевые файлы (реальные пути)

Presentation / policy (`app/src/main/java/forpdateam/ru/forpda/presentation/theme/`):

- `ThemeViewModel.kt` (4969 строк) — центральный оркестратор: загрузка темы, история, скролл-команды, подсветка, back, source-anchor.
- `TopicOpenTargetResolver.kt` — выбор цели открытия (EXPLICIT_POST / EXPLICIT_PAGE / USER_ACTION / SETTING_* / READ_RESUME / SERVER_UNREAD_FALLBACK / SAFE_FALLBACK) и нормализация URL.
- `TopicUnreadOpenPolicy.kt` — список→URL для read/unread, и `resolveGetNewPostAnchor` (якорь после получения HTML).
- `HighlightResolver.kt` / `TopicHighlightApply.kt` / `HighlightOpenInputsPolicy.kt` / `HighlightArmingPolicy.kt` / `ReadPositionSaveGate.kt` — модель подсветки.
- `ThemeScrollCommand.kt` — типизированная команда скролла (ANCHOR / INITIAL_ANCHOR / END_ANCHOR_OR_BOTTOM / SCROLL_Y / UNREAD / BOTTOM / REFRESH_RESTORE).
- `ThemeHistoryController.kt` — внутренний стек страниц темы + back-снапшоты.
- `ThemeBackRestoreUrlPolicy.kt` — чистый URL возврата `showtopic+st(+#entry)`.
- `TopicOpenScrollRestorePolicy.kt` / `ThemeOpenScrollCoalescePolicy.kt` — guard-ы скролла/раскрытия.
- `ThemeLinkNavigationPolicy.kt` — классификация кликов по ссылкам (навигация / image viewer / download).
- `ThemeUrlPolicy.kt` — канонический парсер URL (`ThemeUrlInfo`).
- `ThemeJsInterface.kt` / `ThemeWebCallbacks.kt` — JS-мост WebView→Kotlin.

UI / WebView (`app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/`):

- `ThemeFragmentWeb.kt` (2876 строк) — фрагмент, lifecycle WebView, back-кнопка.
- `modules/ThemeWebController.kt` (2027 строк) — `WebViewClient`, загрузка HTML, scroll-команды, нативное применение подсветки.
- `modules/ThemeJsApi.kt` — типобезопасные вызовы JS.
- `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` — обёртка WebView (`loadDataWithBaseURL`, `evalJs*`, `endWork`).

Renderer / assets:

- `app/src/main/assets/template_theme.html` — HTML-шаблон + IIFE с `PPDA_applyHighlight` / `PPDA_scheduleHighlightFadeout`.
- `app/src/main/assets/forpda/scripts/modules/theme.js` (3425 строк) — runtime скролла/якорей/ссылок/инфинит-скролла.
- `app/src/main/assets/forpda/styles/light/light_themes.css`, `.../dark/dark_themes.css` — **шипованный CSS (источник правды в runtime, НЕ генерируется из LESS на сборке)**, содержит правило `.post_container.ppda_highlight_post`.
- `app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt` — инжектит `:root{--ppda-accent:…}` под активную палитру.

Data:

- `model/data/remote/api/theme/ThemeApi.kt` — URL-хелперы и getnewpost-эвристики.
- `model/repository/theme/ThemePageMemoryCache.kt` — кэш страниц, ключ `(topicId, st, hatOpen, pollOpen)`.
- `entity/remote/theme/ThemePage.kt` — модель страницы (`anchorPostId`, `scrollY`, `highlightTarget`, `renderGenerationId`, `hasUnreadTarget`, `ambiguousLastUnreadBottomRedirect`, `resumeToLastPageBottom`).

### 2.2 Модель маршрута темы

Маршрут описывается `TopicOpenContext` (`TopicOpenTargetResolver.kt:29-63`): `rawUrl`, `setting` (FIRST_PAGE/LAST_UNREAD), `sourceScreen`, `userAction`, списочные хинты (`unreadUrlFromList`, `unreadPostIdFromList`, `listTopicMarkedUnread`, `lastReadUrlFromList`), кэш (`cachedLastPage`, `cachedScrollPosition`). Производное: `topicId`/`explicitPostId`/`explicitPageSt` через `ThemeUrlPolicy.parse`. Результат — `TopicOpenResolution(url, targetType, resolvedPageSt, resolvedPostId, reason, suppressScrollRestore)`.

### 2.3 Рендер и состояние

- WebView **переиспользуется** между темами; контент грузится через `loadDataWithBaseURL` (`ThemeWebController.kt:416`), не `loadUrl`. `loadUrl("about:blank")` — только для сброса/teardown.
- Подсветка хранится на `ThemePage.highlightTarget` + `renderGenerationId` и применяется нативно через `reapplyTopicHighlight` (`ThemeWebController.kt:1617`), который вызывает JS `PPDA_applyHighlight(postId, type, generation)`.
- История — `ThemeHistoryController` (стек `ThemePage` + `backSnapshots` по ключу `topicId+st`).
- Source-anchor (клик по ссылке) хранится в `ThemeViewModel.lastLinkSourceAnchor` / `pendingHistorySourceAnchor` с TTL `SOURCE_ANCHOR_TTL_MS = 15000` (`ThemeViewModel.kt:88`).

---

## 3. Topic Opening Flow (реальный пошаговый поток)

Полная цепочка «тап по теме → скролл/подсветка»:

1. **Вход в VM**: `ThemeViewModel.loadUrl(...)` (`ThemeViewModel.kt:2081`). Вычисляет `incomingTopicId`, при смене темы — `resetTransientStateForNewTopic` (`:2095`), обнуляет `currentPage`/`activeLoadedTopicId` (`:2100-2101`).
2. **Резолв цели**: `resolveTopicOpenUrl` (`ThemeViewModel.kt:792`) строит `TopicOpenContext` и вызывает `TopicOpenTargetResolver.resolve(context)` (`:810`). Там же:
   - `pendingParserListUnreadHint = TopicUnreadOpenPolicy.parserTrustsGetNewPostUnread(...)` (`:811`);
   - `activeOpenSessionKind = resolveOpenSessionKindAtResolve(...)` (`:816`);
   - `activeNavigationTarget` (Unread/Explicit/…) и флаги `suppressScrollRestore` (`:818-835`).
3. **Выбор URL** (`TopicOpenTargetResolver.resolve`, `TopicOpenTargetResolver.kt:77-202`): по приоритету EXPLICIT_POST → EXPLICIT_PAGE → userAction → setting(FIRST_PAGE | LAST_UNREAD). LAST_UNREAD делегирует в `TopicUnreadOpenPolicy.resolveListOpen` (`:174`), затем нормализует в `view=getnewpost` (`normalizeLastUnreadNavigationUrl`, `:260`).
4. **Сетевой грузчик**: `loadData(resolution.url, ThemeLoadAction.Normal)` (`ThemeViewModel.kt:2163` → `:1186`). Отменяет in-flight, ротация `openTrace` (генерация для отбраковки stale-результатов), запуск `themeUseCase.loadTheme(...)` с `openFromUnreadListHint` (`:1384-1385`).
5. **Парсинг ответа**: парсер заполняет `ThemePage` (posts, pagination, `anchorPostId`, `hasUnreadTarget`, `ambiguousLastUnreadBottomRedirect`). Для getnewpost-якоря — `TopicUnreadOpenPolicy.resolveGetNewPostAnchor` (`TopicUnreadOpenPolicy.kt:504`).
6. **`onLoadData`** (`ThemeViewModel.kt:~1466+`): применяет realign/reload-политики (`realignOffPageGetNewPostAnchor` `:349`, `offPageReadResumeFindPostReloadId` `:331`, `resolveReadResumeBottomRedirect` `:371`), вычисляет подсветочные входы и стампит подсветку через `applyHighlightForCurrentPage` (`:3695`).
7. **Рендер HTML**: `ThemeWebController` грузит `page.html` через `webView.loadDataWithBaseURL(...)` (`ThemeWebController.kt:416`), предварительно `webView.clearQueuedJs()` (`:392`).
8. **DOM ready**: `onDomRendered` (`ThemeWebController.kt:~1520+`) проверяет, что в DOM есть ожидаемые посты (`hasExpectedPosts`, `:1537-1548`). Только тогда:
   - `reapplyTopicHighlight()` (`:1550`);
   - `flushPendingScrollCommand()` (`:1552`);
   - `revealThemeContentAfterDomRendered()` (`:1554`);
   - end/posted-page скроллы (`:1556-1560`).
9. **Скролл**: `ThemeScrollCommand` собирается в VM (`beginScrollCommand` `:300`) и исполняется в JS `executeThemeScrollCommand` (`theme.js:922`) → `scrollToElementWithRetries` (`theme.js:2034`) → `doScroll` (`theme.js:2244`). Завершение — `maybeCompleteThemeScrollCommand` → `IThemePresenter.onScrollCommandComplete` (`theme.js:897-919`).
10. **Подсветка**: `reapplyTopicHighlight` (`ThemeWebController.kt:1617`) резолвит цель, проверяет guard-ы (disposed/page/target/generation/postId), `HighlightArmingPolicy.shouldDeferUntilScrollSettled` и `shouldArmForCurrentTarget`, и шлёт JS `applyHighlight` + `scheduleHighlightFadeout` (`:1742-1746`). JS `PPDA_applyHighlight` (`template_theme.html:870`) проверяет on-page и красит `.post_container.ppda_highlight_post`.

Ключевые объекты состояния по пути: `openTrace`/`OpenTrace` (генерация загрузки), `activeNavigationTarget`, `pendingParserListUnreadHint`, `pendingScrollCommand`, `page.highlightTarget`/`renderGenerationId`, `highlightArmedGeneration`/`highlightArmedPostId`, `ReadPositionSaveGate`.

---

## 4. First Unread / Last Read Flow (и почему unread иногда открывает первый пост последней страницы)

### 4.1 Список → URL (навигация)

`TopicUnreadOpenPolicy.resolveListOpen` (`TopicUnreadOpenPolicy.kt:421`) под LAST_UNREAD:

- строка помечена unread (`listTopicMarkedUnread`) → `view=getnewpost`, `suppressScrollRestore=true` (`:442-450`);
- строка прочитана (нет unread-хинтов) → **`view=getlastpost`** (`READ_RESUME`, reason `list_read_use_getlastpost`, `:457-468`) — это правильное поведение «возобновить с серверной закладки»;
- «прочитанная» строка, но всё же нужно искать unread → `getnewpost` поверх `lastReadUrlFromList` (`:469-487`).

### 4.2 Якорь после HTML (`resolveGetNewPostAnchor`, `TopicUnreadOpenPolicy.kt:504-630`)

Цепочка приоритетов:

1. **HTML unread-маркер** (`findUnreadPostEntryIdForGetNewPost`) → `hasUnreadTarget=true`, reason `html_unread_marker` (`:505-511`). Самый надёжный.
2. **all-read bottom redirect** на последней странице (`isLikelyAllReadGetNewPostBottomRedirect`): если `listUnreadHint` → `anchor=null`, `ambiguousBottomRedirect=true`; иначе → `entry$hash`, `hasUnreadTarget=false` (`:517-529`).
3. **redirect hash** (если не top/bottom hint и не отвергнут) → `redirect_hash`, `hasUnreadTarget=true` (`:531-543`).
4. **highlight=** / **p=/pid=** / **canonical** (`:545-579`).
5. **page-top redirect без unread** → `anchor=null`, `ambiguousBottomRedirect=listUnreadHint`, reason `page_top_redirect_no_unread` (`:589-596`).
6. **entry-fallback** (`resolveEntryFallback`, `:679-705`): при `bottomHashRejected`/`listUnreadHint` берёт `entryIds[1]`, `contentEntries.first()` и т.п. с `hasUnreadTarget=true`.
7. **data-post атрибут** (`:620-627`), иначе `no_anchor`.

### 4.3 Почему «первый пост последней страницы»

Симптом №2 объясняется **взаимодействием 4.1/4.2 с серверным редиректом**:

- На реально **прочитанной** теме, открытой через `getnewpost` только из-за настройки LAST_UNREAD (не из-за genuine `+N`), сервер делает редирект на нижнюю/верхнюю закладку. Если это последняя страница, срабатывает ветка `all_read_bottom_redirect` (`:517`). Здесь судьба зависит от `listUnreadHint`:
  - `listUnreadHint=true` → `anchor=null` + `ambiguousBottomRedirect=true` → дальше downstream `resolveReadResumeBottomRedirect` (`:371`) пытается выровнять на redirect-hash, иначе пользователь остаётся на «шапке» страницы/первом посте.
- Если HTML-маркера нет, redirect отвергнут как bottom/top hint, и `resolveEntryFallback` под `listUnreadHint` возвращает **первую/вторую запись страницы** с `hasUnreadTarget=true` (`:687-692`). Когда загруженная «страница» — это последняя страница темы (сервер так редиректнул), «первая запись страницы» и есть «первый пост последней страницы». Это и есть наблюдаемый баг.
- Дополнительно: off-page якорь (когда нужный пост не на загруженном окне) обрабатывается `realignOffPageGetNewPostAnchor` (`:349`) → выравнивает на redirect-hash или **первый пост на странице** (`:354`). Если redirect-hash не на странице — снова «первый пост страницы».

Вывод: корневая проблема — **доверие к серверному getnewpost-редиректу + список-хинтам при рассинхронизации read-state**, а не баг подсветки. Подсветка затем честно показывает то, что выбрал резолвер (или ничего, если пост off-page).

### 4.4 Связь с подсветкой

`HighlightOpenInputsPolicy.resolveOpenInputs` (`HighlightOpenInputsPolicy.kt:55`): `firstUnreadPostId` берётся из `page.anchorPostId` **только если `page.hasUnreadTarget`** (`:60-64`). Значит, если getnewpost-резолвер выставил `hasUnreadTarget=false` (all-read/ambiguous), подсветка идёт по ветке last-read/последний-пост-страницы (`HighlightResolver` приоритет 2/4). Поэтому «непрочитанная» тема, ошибочно посчитанная all-read, не получит FirstUnread-подсветку.

---

## 5. URL Parsing and Internal Link Routing

### 5.1 Канонический парсер `ThemeUrlPolicy.parse` (`ThemeUrlPolicy.kt:17-41`)

`ThemeUrlInfo(topicId, postId, page, isFindPost, normalizedUrl)`.

- `topicId` ← `showtopic=` / `t=` / lofi-паттерн.
- `postId` ← `p=` / `pid=` / `anchor=entryN` / `#entryN` (`postIdFromAnchor`, `:104-107`).
- `page` ← сырой `st=` (это **0-based offset**, не номер страницы).
- `isFindPost` ← `view=findpost`/`act=findpost` **ИЛИ** (есть postId, нет view, act не в `NON_FINDPOST_ACTS`) (`:30`).

Примеры (проверено):

| URL | topicId | postId | page(st) | isFindPost |
|---|---|---|---|---|
| `…?s=&showtopic=1121483&view=findpost&p=143876380` | 1121483 | 143876380 | null | true |
| `…?showtopic=239158` | 239158 | null | null | false |

**`st`→страница:** `ThemePage.st` (`ThemePage.kt:75-79`) = `(pagination.current-1)*perPage` (0-based). Комментарий там прямо предупреждает: иначе при возврате назад URL получит `st` на страницу больше реального.

### 5.2 Хелперы `ThemeApi` (`ThemeApi.kt:708-1180`)

Регэкспы `topicUrlPostIdP`, `…Pid`, `…Highlight`, `…EntryFragment`, `…St`, `…TopicId` (`:708-716`). Все используют `findAll(...).lastOrNull()` (берут последнее вхождение). Эвристики getnewpost-редиректа:

- `isLikelyLastReadPageTopHint` (`:1093`) — postId == первая запись/шапка → «верхний» хинт.
- `isLikelyLastReadPageBottomHint` (`:1104`) — postId == последняя запись → «нижний» хинт.
- `isLikelyAllReadGetNewPostBottomRedirect` (`:1113`) — делегирует к bottom-hint.
- `findUnreadPostEntryIdForGetNewPost` (`:1139+`) — скан HTML на `class=…unread…`.

### 5.3 Неоднозначности парсинга

1. **`p=`/`pid=` без `view`** считается findpost (`ThemeUrlPolicy.kt:30`), но в `ThemeApi`/`TopicOpenTargetResolver` голый `p=` трактуется как **last-read хинт списка**, а не как явный пост (`extractLastReadStylePostIdFromTopicUrl` doc `:1082`; `isExplicitPost` `TopicOpenTargetResolver.kt:316-323`). Два слоя по-разному классифицируют один URL → источник неоднозначности (Finding H-03).
2. **`ArticleLinkResolver`**: `/forum/index.php?p=…` без forum-nav-параметров переписывается в `/index.php?p=…` (как комментарий новости), т.е. одинокий `p=` может уйти не в форум.
3. Серверный `getnewpost` редирект сам по себе многозначен (top/bottom/first-unread) — §4.

### 5.4 Маршрутизация внутренних ссылок (клик в WebView)

Два механизма на один клик:

- **JS-захват source-anchor** (синхронный): `rememberThemeLinkSourceAnchor` на capture-фазе `pointerdown/touchstart/mousedown/click` (`theme.js:308-312`) → `IThemePresenter.rememberLinkSourceAnchor(JSON)` (`theme.js:1343-1345`) → `ThemeJsInterface.rememberLinkSourceAnchor` **синхронно** (`ThemeJsInterface.kt:207-211`) → `onLinkSourceAnchorCaptured` (`ThemeViewModel.kt:3466`).
- **Нативный перехват навигации**: `shouldOverrideUrlLoading` → `handleUri` (`ThemeWebController.kt:1377`). Для site-URI: `consumeLinkSourceAnchorFor(resolved)` (`:1385`), `presenter.handleNewUrl(...)`.

`handleNewUrl` → `handleForumNavigation` (`ThemeViewModel.kt:4205`):

- **другой topicId** → `router.tryOpenTopicInNewTab(url, "in_topic_link")` (`:4221`) — **НОВАЯ вкладка**;
- **тот же topic, findpost** → `handleFindPostNavigation` (`:4236`): если пост на странице — `currentPage.addAnchor(finalAnchor)` + `ScrollToAnchor` (`:4244-4246`), иначе `loadUrl(...)`.

**Риск двойной навигации (Finding R-02):** есть третий путь в `onPageStarted` (`ThemeWebController.kt:1432-1440`), который тоже зовёт `handleNewUrl` для `showtopic=`/`act=findpost`, защищённый только строковым `lastHandledUrl`. Плюс JS-мост `openLink` (`ThemeJsInterface.kt:193`). Три входа навигации, защита — `handleUri` возвращает `true` + строковый де-дуп.

---

## 6. Back Navigation Analysis

### 6.1 Механика

Нет `OnBackPressedDispatcher`/predictive-back в этих файлах. Back идёт: `ThemeFragmentWeb.onBackPressed()` (`ThemeFragmentWeb.kt:968`) → `ThemeViewModel.onBackPressed()` (`ThemeViewModel.kt:4326`):

1. Если у `currentPage.anchors.size > 1` — снять последний якорь и `ScrollToAnchor` (`:4331-4337`). **Это путь возврата для in-same-topic findpost-ссылок** (якорь был добавлен в `:4244`).
2. Иначе, если `TopicBackBehavior.HISTORY` и `historyController.canGoBack()` — `backPage()` (`:4338-4340`).

`backPage()` (`ThemeViewModel.kt:2877`): снимает страницу со стека, `currentPage = prev`. Если `prev.html` пуст → строит чистый URL `buildBackRestoreUrl` = `showtopic+st(+#entry<anchorPostId>)` (`:2887-2896`, политика `ThemeBackRestoreUrlPolicy.kt`) и грузит `ThemeLoadAction.Back`. Иначе ремапит кэшированную страницу (`:2897-2911`).

`ThemeHistoryController` хранит `ThemePage` (с `scrollY`, `anchorPostId`, `scrollRatio`) + `backSnapshots` по ключу `topicId+st` (`ThemeHistoryController.kt:28,293`).

### 6.2 Source-anchor для возврата

При клике по ссылке `onLinkSourceAnchorCaptured` (`ThemeViewModel.kt:3466`) сохраняет точный кликнутый пост в `lastLinkSourceAnchor`/`pendingHistorySourceAnchor` и **сразу записывает его в исходную страницу** через `applyLinkSourceAnchorSnapshot` (`:3489`, `:3596-3601`: пишет `target.anchorPostId = anchor.postId`, `scrollY`, `ratio`). `consumeLinkSourceAnchorFor` (`:3497`) намеренно **не** обнуляет `pendingHistorySourceAnchor`, чтобы трейлинговый async DOM-захват исходной страницы не перезаписал authoritative кликнутый пост (`:3509-3515`). TTL 15 c, scope по `topicId+st` (`sourceAnchorAppliesTo`, `:3582`).

Это значит: **для возврата В ПРЕДЕЛАХ той же темы** исходный пост сохраняется корректно.

### 6.3 Почему back теряет исходный пост / скролл

1. **Кросс-топик → новая вкладка (Finding B-01).** При ссылке на другой topicId `handleForumNavigation` открывает Тему B в новой вкладке (`ThemeViewModel.kt:4221`). Внутренний `historyController` Темы A **не получает push о переходе** — переход «Тема A → Тема B» не лежит в in-tab истории Темы A. Возврат тогда обслуживается back-стеком вкладок/системы, и какая страница/пост Темы A восстановится — зависит от состояния вкладки, а не от `pendingHistorySourceAnchor` (который живёт в VM Темы A и имеет TTL 15 c). Это прямой кандидат на симптомы №4/№5.
2. **Блокировка html перед уходом.** Перед загрузкой новой темы `updateHistoryLastHtml("")` обнуляет html (комментарий `backPage`, `:2882`), поэтому back делает сетевой reload по чистому URL. Если `anchorPostId` к этому моменту не authoritative (например, source-anchor истёк по TTL или не применился из-за рассинхронизации st), `buildBackRestoreUrl` отдаёт URL без `#entry` → возврат на верх страницы (симптом №5/№6).
3. **TTL source-anchor 15 c.** При медленной сети/долгом чтении Темы B возврат может произойти позже TTL → `consumeLinkSourceAnchorFor`/`sourceAnchorAppliesTo` вернут null, точный пост потерян (`:3500-3507`, `:3584-3589`).
4. **scrollY vs ratio.** Снапшот хранит `scrollY`+`scrollRatio`+`wasNearBottom`, но при reload верстка может отличаться (изображения/шрифты), и восстановление по `#entry` зависит от `doScroll` (см. §8). При отсутствии `#entry` падение на ratio/savedY менее точно.

### 6.4 Что в back работает (подтверждено тестами)

`ThemeBackRestoreUrlPolicyTest` (`ThemeBackNavigationTest.kt:28-72`) пинит: anchor предпочитается над url-hash, fallback на url-hash, и findpost-параметры стрипаются. `ThemeBackScrollRestorePolicyTest` (`:75-112`) пинит: на `ThemeLoadAction.Back` scroll-restore **никогда не подавляется** (даже при stale unread-флагах) и saved-scroll разрешён. То есть политика возврата в пределах темы корректна — проблема в кросс-топиковом и TTL/верстка кейсах.

---

## 7. WebView Rendering and Lifecycle Analysis

### 7.1 Загрузка/рендер

- Контент: `webView.loadDataWithBaseURL(THEME_WEBVIEW_BASE_URL, page.html, "text/html", "utf-8", null)` (`ThemeWebController.kt:416`). Перед этим — `webView.clearQueuedJs()` (`:392`).
- `ExtendedWebView.loadDataWithBaseURL` override (`ExtendedWebView.kt:418-423`) сбрасывает `isJsReady=false`, `bottomChromePaddingValue`. `window` JS пересоздаётся при каждом `loadDataWithBaseURL` → JS-глобалы (`__themeScrollCommandId`, `unreadInitialAnchorPending`, `themeAnchorScrollGeneration`, `PPDA_HIGHLIGHT`) обнуляются вместе со страницей. Это **ограничивает** кросс-топиковый JS-bleed.
- `loadUrl` (`:425-430`) — только для `about:blank`/reset.

### 7.2 Переиспользование/очистка

- WebView **переиспользуется** между темами (один `ExtendedWebView`); `destroy()` только в `onDestroyView` (`ThemeFragmentWeb.kt:1732-1740`: `stopLoading`+`about:blank`+`clearHistory`+`removeAllViews`+`destroy`).
- Между темами: `ThemeFragmentWeb.kt:1202-1205` делает `stopLoading()`+`loadUrl("about:blank")` — **без `clearCache`**.
- `ExtendedWebView.endWork()` (`:883-901`) — per-reuse teardown (`about:blank`, `clearHistory`, `clearMatches`).
- **Нет `clearCache`/`clearView`** нигде в этих файлах.

### 7.3 Защита от stale-состояния

- **Render token / generation**: `ThemeRenderGuard` + `renderToken` в JS-мостах (`ThemeJsInterface.runProtected`, `:221-234`) отбрасывает действия с невалидным токеном.
- **activeRenderKey**: коллбэк DOM-probe проверяет `if (activeRenderKey != renderKey) return` (`ThemeWebController.kt:1531`).
- **renderGenerationId**: подсветка и кэш используют генерацию для отбраковки stale-коллбэков (`ThemePage.renderGenerationId`, `ThemePageMemoryCache.get(... expectedRenderGeneration)`).
- **renderSignature**: история обнуляет html при смене сигнатуры (`ThemeHistoryController.updatePageHistoryHtml:271-278`, `setHistoryBody:325-333`).

### 7.4 Риски lifecycle

1. `__themeScrollCommandId` — глобал на `window`. В норме обнуляется reload-ом, но `maybeCompleteThemeScrollCommand` (`theme.js:897`) завершает команду по **текущему** id; если scroll-команда новой темы стартовала до того, как старая завершилась, а reload произошёл между — теоретически возможна потеря completion-коллбэка (бенинно, но влияет на reveal/highlight timing). (Finding R-04)
2. Отсутствие `clearCache` означает, что устаревший styled-CSS/изображения могут переживать смену темы — но `renderSignature`/cache-signature это закрывают на уровне страниц.
3. Reveal зависит от множества watchdog-путей (`ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason`, `:59`), которые могут раскрыть контент при stale `contentHeight` от прошлой темы (комментарий `:119-121`).

---

## 8. Scroll / Anchor / Viewport Analysis

### 8.1 Команды и исполнение

VM: `beginScrollCommand` (`ThemeViewModel.kt:300`) сохраняет `pendingScrollCommand`; `getPendingScrollCommand` (`:491`); завершение `completeScrollCommand` (`:348`). Контроллер шлёт `jsApi.executeScrollCommand(command)` → `executeThemeScrollCommand(payload)` (`theme.js:922`).

`executeThemeScrollCommand` (`theme.js:922-1015`), switch по `kind`:

- `REFRESH_RESTORE` → `restoreThemeToBottomAfterRefreshWithRetries()` или `restoreThemeRefreshScrollAnchorWithRetries()` (`:940-951`).
- `BOTTOM` → `scrollToThemeBottomWithRetries()` (`:952-959`).
- `ANCHOR` → `scrollToElementWithRetries(anchorPostId)` (без `requireFinalRetry`) (`:960-966`).
- `END_ANCHOR_OR_BOTTOM` → `endScrollPending` + `scrollToThemeBottomWithRetries(5)` (`:967-979`).
- `INITIAL_ANCHOR` → коалесценс если `unreadInitialAnchorPending`, иначе resolve + `scrollToElementWithRetries(initialAnchor, true)` (`:980-1012`).

Завершение → `maybeCompleteThemeScrollCommand(success, reason)` (`:897-920`) → `IThemePresenter.onScrollCommandComplete`.

### 8.2 Геометрия (`doScroll`, `theme.js:2244-2285`)

```text
postTopAbs = rect.top + pageYOffset
y = postTopAbs - topChromePadding        // вычитается ТОЛЬКО верхний reserve (тулбар)
maxY = scrollHeight - innerHeight
scrollTo(0, clamp(y, 0, maxY))
```

`doScroll` также добавляет legacy-класс `active` на контейнер поста (`:2278-2284`) — **отдельный от подсветки** механизм (§9.4).

Ретраи: `scrollToElementWithRetries` (`theme.js:2034-2097`) с лестницей `SCROLL_ANCHOR_RETRY_DELAYS_MS = [1,120,400,900]` (`:9`); ранний выход при `requireFinalRetry && nearTarget && successfulScrolls>0` (`:2063-2069`). «Near top» = `rect.top in [-8, slack]` (`isThemeAnchorNearViewportTop`, `:1062-1070`). Каждый attempt в `setTimeout`→`requestAnimationFrame` (`scheduleThemeScrollAttempt`, `:1072`).

### 8.3 Конкурирующий JS DOM-initial-anchor

Legacy DOM-путь — слушатель `nativeEvents.DOM` (`theme.js:2575-2607`). Для `NORMAL_ACTION` он **явно заглушён**, если активна Kotlin-команда:

```js
if (window.loadAction == NORMAL_ACTION) {
  if (window.__themeScrollCommandId || themeInfiniteScroll.unreadInitialAnchorPending) { return; }
  ... resolveThemeInitialAnchorName() ... scrollToElementWithRetries(domInitialAnchor, true);
}
```
(`:2592-2603`). Вторичный де-дуп по `themeAnchorRetryPendingName`/near-top (`:2598`).

**Вердикт по F-03 черновика:** конкуренции при штатном порядке нет — guard корректен. Но guard **зависит от порядка событий**: если `DOM` событие пришло до того, как Kotlin успел выставить `__themeScrollCommandId` (т.е. до `executeThemeScrollCommand`), JS запустит свой DOM-anchor. Это узкое окно — реальный кандидат на «иногда не тот якорь» (симптом №1). (Finding S-01)

### 8.4 Скролл до раскладки/изображений

Ретраи скролла и пересчёт картинок — **независимые таймеры**: `prepareThemeMediaImage` ставит `load/error`-листенеры (`theme.js:1770-1771`) и `scheduleThemeMediaImageLoadedRecheck` на `[16,80,240,600,1200,2000,4000]` мс (`:1776`); аспект резервируется заранее (`applyThemeMediaAspectRatio`, `:1812`). Тем не менее attempt скролла на `[1,120,400,900]` может сработать до догрузки высокого изображения → цель «уезжает» после скролла (симптомы №1/№6). (Finding S-02)

---

## 9. Post Highlight Audit (почему подсветка не работает)

### 9.1 Инфраструктура КОРРЕКТНА (опровержение «класс — no-op»)

Историческая первопричина из черновика («класс есть, CSS нет») **исправлена и закрыта тестом**:

- JS красит `.post_container` классом **`ppda_highlight_post`** (`template_theme.html:840`), а не `post-highlight`/`.active`.
- CSS-правило существует в **шипованных** `light_themes.css:1567-1591` и `dark_themes.css` (проверено): `box-shadow: inset … var(--ppda-accent, var(--surface-accent, #2177af)) … !important;` + fading-вариант (`:1592-1595`). LESS-источник — `themes.less:1478-1503`.
- Селектор JS `.post_container[data-post-id]` совпадает с реальной разметкой постов (`template_theme.html:317`).
- `--ppda-accent` инжектится безусловно под активную палитру (`TemplateCssComposer.kt:241-248`), включая sepia/amoled/minimal/hybrid.
- `TopicHighlightCssContractTest.kt` пинит: JS тоглит оба класса; CSS содержит видимый `box-shadow`, `inset`, `!important`, `--ppda-accent`, без `background`/`outline`/`color-mix(`; сигнатура `PPDA_scheduleHighlightFadeout(generationId, delayMs)`.

**Вывод:** если на устройстве в WebConsole печатается `PPDA_HL applied … boxShadow=[…]` с непустым box-shadow — подсветка визуально присутствует. Если её «не видно», причина выше по цепочке.

### 9.2 JS-гард `PPDA_applyHighlight` (`template_theme.html:870-901`)

Отказы (каждый логируется в console `PPDA_HL reject …`):

1. `callbackGen < ppdaHighlight.generationId` → **stale generation** (`:873`).
2. `postId==0 || type=="none"` → **empty** (`:880`).
3. пост не среди `.post_container[data-post-id]` → **`post_not_on_page`** (`:890`). ← главный практический отказ.

То есть: правильный класс ляжет только если нужный `postId` **физически в DOM текущей страницы** и генерация не stale.

### 9.3 Нативная цепочка `reapplyTopicHighlight` (`ThemeWebController.kt:1617-1789`) и её гард-лестница

Ранние выходы (логируются `highlightArmSkipped`): `disposed_or_no_view` (`:1625`), `no_current_page` (`:1647`), `no_highlight_target`/`highlight_target_none` (`:1656-1676`), `topic_id_non_positive` (`:1678`), `generation_non_positive` (`:1690`), `post_id_non_positive` (`:1702`).

Ключевая логика:

- `deferApply = HighlightArmingPolicy.shouldDeferUntilScrollSettled(hasBlockingScrollPending)` (`:1715`) — **если выполняется блокирующий скролл, нативный apply ОТКЛАДЫВАЕТСЯ** (`HighlightArmingPolicy.kt:12-13`). Причина: не красить пост, который ещё off-screen (док `HighlightArmingPolicy.kt:3-9`).
- `shouldApply = !deferApply && shouldArmForCurrentTarget(...)` (`:1717`).
- `reapplyTopicHighlight` вызывается из `onDomRendered` **только при `hasExpectedPosts`** (`ThemeWebController.kt:1549-1551`).
- Повторное арминг после скролла: `onWebViewContentRevealed` → `reapplyTopicHighlightAfterScrollSettled` (`:1601-1607`, `:1589-1599`) сбрасывает armed-guard и повторно зовёт `reapplyTopicHighlight`.

### 9.4 Конкурирующая legacy-подсветка `active`

`doScroll` добавляет класс `active` на контейнер поста (`theme.js:2278-2284`). Это **остаток старого механизма** (черновик HighlightResolver doc прямо называет «previous attempt wired the visual flash to doScroll()'s `.active`»). Класс `active` ≠ `ppda_highlight_post`; если в CSS остался стиль на `.active`, возможна визуальная путаница, но это не основной механизм. (Finding H-04, low)

### 9.5 Конкретные причины, почему подсветка «не работает»

1. **`post_not_on_page`** — нужный пост (first-unread/last-read) не на отрендеренной странице, потому что unread-резолвер открыл не ту страницу (§4). → JS отвергает, ничего не красится. Это связь №9↔№1/№2.
2. **`hasUnreadTarget=false` на самом деле непрочитанной теме** → `HighlightOpenInputsPolicy` не даёт `firstUnreadPostId`, резолвер уходит в last-read/последний-пост-страницы → подсветка не на том посте или на последнем посте (выглядит «не работает»).
3. **Deferral + зависший scroll-complete**: `deferApply=true`, скролл не присылает `onScrollCommandComplete` вовремя, повторный arming зависит от reveal/settled-путей. `scheduleHighlightFadeout` мог уже стартовать (2 c) и снять класс до того, как пользователь его увидел; или нативный apply так и не пришёл. (Finding H-01)
4. **Stale generation**: если `renderGenerationId` обновился (новый рендер), а JS-коллбэк пришёл от старого, `PPDA_applyHighlight` отвергнет (`:873`). `TopicHighlightApply.applyToPage` намеренно НЕ бампит генерацию при том же target (`TopicHighlightApply.kt:77-80`), чтобы рефреш не ломал коллбэки — но при смене target/страницы это окно есть. (Finding H-02)
5. **ReadPositionSaveGate**: во время окна подсветки (3 c) и блокирующего скролла подавляется сохранение read-position (`ReadPositionSaveGate.kt:48-54`), чтобы IntersectionObserver не перетёр `lastViewedPostId`. Если окно закрывается рано/поздно — last-read цель может «дрейфовать», что косвенно влияет на выбор подсветки при следующем открытии.

**Главный вывод §9:** подсветка не сломана по существу — она **не получает корректную on-page цель в корректный момент времени**. Чинить надо §4 (страница/якорь) и §9.3 (тайминг арминга), а не CSS/JS подсветки.

---

## 10. Race Conditions and State Corruption

| ID | Триггер | Файлы | Доказательство | Эффект | Рекомендуемый guard |
|---|---|---|---|---|---|
| R-01 | Результат загрузки Темы A применяется после открытия Темы B | `ThemeViewModel.kt` (`OpenTrace`/`openTrace`, `loadData:1198-1265`) | Есть ротация `openTrace` и отмена in-flight job в `loadData`; cache-ключ топико-скоупнут | В норме закрыт; проверить, что **все** async-коллбэки (scroll/highlight/DOM-probe) сверяют trace/renderKey | Единый trace-guard на входе каждого коллбэка |
| R-02 | Один клик → несколько навигаций | `ThemeWebController.kt:1377,1432-1440`; `ThemeJsInterface.kt:193` | 3 входа (`handleUri`, `onPageStarted`, `openLink`), защита — строковый `lastHandledUrl` + return true | Возможна двойная загрузка/двойной push истории → back «через одну» | Флаг «navigation dispatched for this gesture», а не строковый де-дуп |
| R-03 | JS DOM-initial-anchor стартует до Kotlin INITIAL_ANCHOR | `theme.js:2592-2603` | guard `__themeScrollCommandId` зависит от порядка событий | «Иногда не тот якорь» (№1) | Один владелец initial scroll (Kotlin), JS — fallback-only |
| R-04 | `__themeScrollCommandId` теряет completion при reload между темами | `theme.js:897-900,935` | глобал на `window`, перезаписывается новым id | Потеря `onScrollCommandComplete` → reveal/highlight зависают | Скоупить command id по renderKey/generation |
| R-05 | Source-anchor истёк по TTL при возврате | `ThemeViewModel.kt:3500-3507,3584-3589` | TTL 15 c | Back теряет точный исходный пост (№5) | Хранить нативный back-снапшот независимо от JS TTL |
| R-06 | Трейлинговый async DOM-захват исходной страницы перетирает кликнутый пост | `ThemeViewModel.kt:3509-3515` | Комментарий о фиксе; решено через `pendingHistorySourceAnchor` | Уже закрыт; держать под тестом | — |
| R-07 | Deferred highlight + ранний fadeout | `ThemeWebController.kt:1715,1746`; `template_theme.html:903-938` | `deferApply` откладывает apply, fadeout-таймер мог стартовать | Подсветка невидима/мигает (№9) | Стартовать fadeout только ПОСЛЕ фактического apply |
| R-08 | Stale `contentHeight` от прошлой темы раскрывает контент рано | `ThemeOpenScrollCoalescePolicy.kt:119-121` | Комментарий о safety-fallback reveal | Реверс к scrollY=0/мигание (№6/№9) | Сбрасывать contentHeight на teardown темы |

**State corruption:** прямого «Тема A получает посты Темы B» не найдено — кэш топико-скоупнут (`ThemePageMemoryCache.Key`, `:43-48`), history дедуплицирует по `topicId+st+anchor`, render-guard/generation/signature отбраковывают stale. Основной риск — **временной**, а не структурный.

---

## 11. Findings

> F-04/F-05 черновика ЗАКРЫТЫ: `consumeLinkSourceAnchorFor` (`ThemeViewModel.kt:3497`), `beginScrollCommand` (`:300`), `getPendingScrollCommand` (`:491`), `onLinkSourceAnchorCaptured` (`:3466`) реально существуют и связаны. Контракт ThemeWebController↔ThemeViewModel цел.

### U-01 — getnewpost открывает первый пост последней страницы (unread)
- Severity: **Critical**
- Файлы: `TopicUnreadOpenPolicy.kt:504-705` (`resolveGetNewPostAnchor`, `resolveEntryFallback`, `realignOffPageGetNewPostAnchor:349`, `resolveReadResumeBottomRedirect:371`); `HighlightOpenInputsPolicy.kt:60-64`.
- Evidence: ветки `all_read_bottom_redirect` (`:517-529`) и `resolveEntryFallback` под `listUnreadHint` возвращают `entryIds.first()`/`[1]` с `hasUnreadTarget=true` (`:687-692`); off-page realign падает на `posts.first` (`:354`).
- Root cause: доверие к серверному getnewpost-редиректу и list-хинту при рассинхронизации серверного read-state; «первая запись страницы» = первый пост последней страницы, когда сервер редиректнул на последнюю.
- Impact: симптом №2 (и каскадом №9 — нечего подсвечивать на нужной странице).
- Repro: прочитанная/частично-прочитанная тема в избранном, LAST_UNREAD, открыть; сервер редиректит на низ → посадка на первый пост последней страницы.
- Fix (предложение, НЕ применять): на genuine-unread открытии запретить fallback на `entryIds.first()` без подтверждённого on-page first-unread; при off-page unread делать controlled reload `view=findpost&p=<unreadId>`; диагностический trace на каждый выход резолвера.
- Effort: M. Risk: M (много граничных кейсов, нужны runtime-логи).

### U-02 — `hasUnreadTarget` ложно false для непрочитанной темы
- Severity: **High**
- Файлы: `TopicUnreadOpenPolicy.kt:517-596`; `HighlightOpenInputsPolicy.kt:60`.
- Evidence: `page_top_redirect_no_unread`/`all_read_bottom_redirect` ставят `hasUnreadTarget=false`; тогда `firstUnreadPostId=null`.
- Root cause: эвристики top/bottom-hint отвергают валидный redirect как «closed».
- Impact: FirstUnread-подсветка не выбирается (№9), скролл уходит в last-read.
- Fix: логировать `unread_off_page`/`page_top_redirect_no_unread` как warning; не делать last-post fallback для unread-открытия, пока all-read не доказан HTML-маркерами.
- Effort: S-M. Risk: M.

### S-01 — JS DOM-initial-anchor конкурирует с Kotlin INITIAL_ANCHOR (порядок событий)
- Severity: **High**
- Файлы: `theme.js:2592-2603`; `ThemeWebController.kt` (отправка `executeScrollCommand`).
- Evidence: guard `if (window.__themeScrollCommandId || unreadInitialAnchorPending) return;` срабатывает, только если Kotlin успел выставить id до `DOM`-события.
- Root cause: два владельца initial scroll, синхронизация по гонке.
- Impact: «иногда не тот якорь» (№1).
- Fix: сделать Kotlin ThemeScrollCommand единственным владельцем; JS DOM-anchor — только fallback при отсутствии команды через N мс.
- Effort: M. Risk: M.

### S-02 — Скролл до догрузки изображений/шрифтов
- Severity: **Medium**
- Файлы: `theme.js:9` (`[1,120,400,900]`), `:1770-1818`, `doScroll:2244-2285`.
- Evidence: независимые таймеры скролла и image-recheck.
- Impact: цель «уезжает» после скролла (№1/№6); особенно «длинный пост с картинками».
- Fix: финальный re-anchor после `images settled`/последнего recheck; либо завязать `requireFinalRetry` на стабилизацию `scrollHeight`.
- Effort: S-M. Risk: L.

### B-01 — Кросс-топик ссылка → новая вкладка, in-tab история не знает о переходе
- Severity: **High**
- Файлы: `ThemeViewModel.kt:4216-4226`; `ThemeHistoryController.kt`.
- Evidence: `router.tryOpenTopicInNewTab(url, "in_topic_link")`.
- Root cause: back из Темы B обслуживается back-стеком вкладок, а не `historyController` Темы A; source-anchor живёт в VM Темы A с TTL.
- Impact: симптомы №4/№5 (back не возвращает к исходному посту).
- Fix: либо открывать кросс-топик ссылку in-tab с push в `historyController` (и сохранять source-anchor как back-снапшот), либо при возврате восстанавливать сохранённый снапшот Темы A независимо от TTL.
- Effort: M. Risk: M-H (затрагивает табовую навигацию).

### B-02 — Source-anchor TTL 15 c теряет точный пост при медленной сети
- Severity: **Medium**
- Файлы: `ThemeViewModel.kt:88,3500-3507,3584-3589`.
- Evidence: `SOURCE_ANCHOR_TTL_MS=15000`; expiry обнуляет pending-поля.
- Impact: №5 при долгом чтении целевой темы.
- Fix: персистить нативный back-снапшот (уже есть `captureBackSnapshot`) и не зависеть от JS-TTL для построения back-URL.
- Effort: S. Risk: L.

### H-01 — Deferred highlight + ранний/несинхронный fadeout
- Severity: **High**
- Файлы: `ThemeWebController.kt:1715-1764`; `HighlightArmingPolicy.kt:12`; `template_theme.html:903-938`.
- Evidence: `deferApply` откладывает `applyHighlight`, но `scheduleHighlightFadeout` ставится в том же `buildString`/может стартовать; повторный arming зависит от reveal/settled.
- Root cause: окно подсветки (2 c) и фактический apply развязаны во времени.
- Impact: подсветка невидима/мелькает (№9).
- Fix: арм fadeout только после подтверждённого `js_highlight_applied`; при отложенном apply — отложить и fadeout.
- Effort: S-M. Risk: M.

### H-02 — Stale renderGenerationId отбрасывает валидный коллбэк
- Severity: **Medium**
- Файлы: `TopicHighlightApply.kt:77-80`; `template_theme.html:873`.
- Evidence: генерация не бампится при том же target, но при смене target/страницы коллбэк старой генерации отвергается JS.
- Impact: иногда подсветка не появляется после быстрых ре-рендеров.
- Fix: явная инвалидция armed-state при смене страницы; тест на последовательность generation.
- Effort: S. Risk: L.

### H-03 — Двойная классификация `p=` (findpost vs last-read hint)
- Severity: **Medium**
- Файлы: `ThemeUrlPolicy.kt:30`; `TopicOpenTargetResolver.kt:316-352`; `ThemeApi.kt:1082-1087`.
- Evidence: `ThemeUrlPolicy` считает голый `p=` → findpost; `TopicOpenTargetResolver`/`ThemeApi` трактуют как last-read хинт.
- Impact: неоднозначная посадка по прямым/закладочным ссылкам (№1/№3).
- Fix: единый источник классификации `p=`; явно различать «findpost intent» и «list last-read hint».
- Effort: S-M. Risk: M.

### R-02 — Тройной путь навигации по клику (двойная навигация)
- Severity: **Medium**
- Файлы: `ThemeWebController.kt:1377,1432-1440`; `ThemeJsInterface.kt:193`.
- Evidence: `handleUri` + `onPageStarted` + `openLink`, защита строковым `lastHandledUrl`.
- Impact: двойная загрузка/двойной push → back «через одну» (№4/№7).
- Fix: один gesture-scoped флаг диспетчеризации.
- Effort: S. Risk: M.

### H-04 — Legacy `active` класс из `doScroll`
- Severity: **Low/Info**
- Файлы: `theme.js:2278-2284`.
- Evidence: `doScroll` всё ещё ставит `active` на контейнер.
- Impact: потенциальная визуальная путаница, дубль логики.
- Fix: удалить после подтверждения отсутствия CSS-зависимостей от `.active`.
- Effort: S. Risk: L.

### G-01 — Избыточность guard-ов скролла/раскрытия
- Severity: **Medium (debt)**
- Файлы: `ThemeOpenScrollCoalescePolicy.kt`, `TopicOpenScrollRestorePolicy.kt`, `HighlightArmingPolicy.kt`, `ThemeViewModel` render-watchdog, JS retry-лестницы.
- Impact: трудно отлаживаемые «иногда» баги; высокий риск регрессий при правках.
- Fix: после покрытия тестами свести к одному state-machine render/scroll lifecycle.
- Effort: L. Risk: H (только после тестов).

---

## 12. Optimization and Simplification Opportunities

### Quick Wins
- Включить уже существующие диагностики на debug-сборке и собрать реальные логи по проблемным URL (`ReadStateTrace`, `TopicHighlightDiagnostics`, `THEME_HISTORY_TAG`, `PPDA_HL` в WebConsole) — без правок поведения.
- `H-02`: явная инвалидция `highlightArmedGeneration/PostId` при смене страницы.
- `B-02`: строить back-URL из нативного `TopicBackSnapshot`, не из JS-TTL anchor.
- Убрать legacy `active` из `doScroll` (`H-04`) после grep по CSS на `.active`.

### Medium Refactors
- `S-01`/`R-03`: один владелец initial anchor scroll (Kotlin), JS DOM-anchor → fallback-only с таймаутом.
- `H-01`/`R-07`: связать `scheduleHighlightFadeout` с фактическим `applyHighlight` (fadeout только после apply).
- `R-02`: gesture-scoped флаг диспетчеризации навигации вместо строкового `lastHandledUrl`.
- `H-03`: единая классификация `p=`.

### Long-Term Cleanup
- `U-01`/`U-02`: пересмотреть всю getnewpost-anchor-цепочку — заменить длинный fallback на явный state-machine с runtime-подтверждением on-page first-unread (controlled reload при off-page).
- `B-01`: унифицировать внутреннюю историю темы и табовую навигацию (или явный back-снапшот при уходе в новую вкладку).
- `G-01`: единый render/scroll lifecycle state-machine, инвентаризация всех guard-ов (таблица «guard → какой баг закрывает → тест»).

### Производительность
- Дубли reveal-watchdog-путей (`ThemeOpenScrollCoalescePolicy.isSafetyFallbackRevealReason`) и независимые JS retry-таймеры — кандидаты на консолидацию (меньше повторных DOM-сканов/скроллов).
- `reapplyTopicHighlight` повторно резолвит подсветку (`applyHighlightForCurrentPage`) на каждый reveal/settled — кэшировать результат на генерацию.

---

## 13. Recommended Fix Strategy (безопасный порядок; НЕ реализовывать)

**Phase 0 — Verification Gate.** Запустить локально `:app:compileStableDebugKotlin` и theme-тесты. (Контракт VM↔Controller уже подтверждён чтением — F-04/F-05 закрыты, но компиляцию ветки всё равно проверить.)

**Phase 1 — Diagnostics only (без изменения поведения).** Собрать логи по проблемным сценариям: `resolveGetNewPostAnchor` (reason/entryIds/redirectHash/hasUnreadTarget), `HighlightResolver`/`PPDA_HL reject …`, `onScrollCommandComplete`, `THEME_HISTORY` push/pop/back. Цель — подтвердить, какая ветка реально срабатывает для №1/№2/№9.

**Phase 2 — Стабилизация FIRST_UNREAD (U-01/U-02).** Запретить опасный `entryIds.first()` fallback для genuine-unread без on-page подтверждения; при off-page — controlled reload `findpost&p=<unreadId>`; не давать last-read/ saved-scroll перебивать подтверждённый first-unread.

**Phase 3 — Стабилизация подсветки (H-01/H-02).** Арм fadeout только после `js_highlight_applied`; инвалидция armed-state при смене страницы; warning на `unread_off_page`.

**Phase 4 — Стабилизация back (B-01/B-02/R-02).** Нативный back-снапшот независимо от TTL; решить судьбу кросс-топик новой вкладки; gesture-scoped навигация.

**Phase 5 — Упрощение guard-ов (S-01/R-03/R-04/G-01).** Один владелец initial scroll; скоупинг command-id; затем консолидация lifecycle — **только** после тестового покрытия текущего поведения.

---

## 14. Test Plan

### 14.1 Существующее покрытие (прочитано, НЕ запускалось)

- `HighlightResolverTest.kt` — приоритеты резолвера: FirstUnread > LastRead(on-page) > Explicit(on-page) > last-post-fallback > None; детерминизм; `unread_off_page` reason; нулевые id трактуются как absent.
- `TopicHighlightApplyTest.kt` — стамп target на `ThemePage`, бамп generation, сохранение generation при ре-apply того же target.
- `TopicHighlightCssContractTest.kt` — JS-класс↔CSS-правило контракт (box-shadow/inset/!important/--ppda-accent, без background/outline/color-mix; сигнатура fadeout).
- `ThemeBackNavigationTest.kt` (`ThemeBackRestoreUrlPolicyTest`, `ThemeBackScrollRestorePolicyTest`) — back-URL: anchor > url-hash, strip findpost; back никогда не подавляет scroll-restore.
- Прочее в дереве: `HighlightArmingPolicyTest`, `HighlightOpenInputsPolicyTest`, `HighlightJsGuardTest`, `ReadPositionSaveGateTest`, `SourceAnchorTtlTest`, `ThemeRenderSessionTest`, `TopicScrollRestoreSchedulingPolicyTest`, `TopicUnreadOpenPolicyTest`, `ThemeParserGetNewPostAnchorTest`, `ThemeApiFindUnreadGetNewPostTest`, `ThemeJsApiApplyHighlightTest`, `ThemeJsApiScheduleHighlightFadeoutTest`, `ThemeWebControllerFadeoutTest`, `ThemeWebControllerArmFlagOrderingTest`, `ThemePageMemoryCacheTest`, `TemplateCssComposerHighlightAccentTest`.
- **Покрыто:** резолвер/apply/CSS-контракт/back-URL/source-anchor TTL/getnewpost-anchor (unit). **НЕ покрыто:** реальный тайминг WebView (deferral↔fadeout↔reveal), кросс-топик новая вкладка + back, двойная навигация, JS DOM-anchor гонка, скролл-после-картинок.

### 14.2 Ручные кейсы (ожидаемый результат)

| # | Сценарий | Ожидание |
|---|---|---|
| 1 | Непрочитанная тема, «к первому непрочитанному» вкл. | Открыть на странице с первым непрочитанным; подсветка FirstUnread (ring 2 c) на нём; скролл к нему |
| 2 | Полностью прочитанная тема | READ_RESUME на последнем прочитанном; подсветка LastRead на нём (не на последнем посте насильно) |
| 3 | Прямая ссылка на пост (`view=findpost&p=…`) | Точная страница+пост; Explicit-подсветка; скролл к посту |
| 4 | Прямая ссылка на тему (`?showtopic=…`) | Поведение по настройке (FIRST_PAGE/LAST_UNREAD); старое состояние не влияет |
| 5 | Внутренняя ссылка на тему (тот же topic) | Переход к посту; back возвращает к исходному посту (anchor stack) |
| 6 | Внутренняя ссылка на пост (тот же topic) | Скролл к посту; back снимает якорь, возврат к исходной позиции |
| 7 | Тема A → ссылка в Тему B → back | Возврат в Тему A на тот же пост и scroll (сейчас риск B-01) |
| 8 | Быстрые повторные клики по ссылке | Одна навигация, не двойная; back консистентен (риск R-02) |
| 9 | Медленная сеть | Подсветка/скролл не теряются; back-anchor не истекает (риск B-02) |
| 10 | Поворот экрана во время загрузки | Та же цель/подсветка после реколлекта |
| 11 | Восстановление процесса | Тема/страница/позиция восстановлены корректно |
| 12 | Reload WebView (pull-to-refresh) | Позиция и подсветка сохранены (generation не сброшен зря) |
| 13 | Длинный пост | Якорь точный после settle; подсветка на правильном посте |
| 14 | Короткий пост (короткая последняя страница) | Нет залипания на scrollY=0; reveal не раньше anchor settle |
| 15 | Пост с картинками | Якорь не «уезжает» после догрузки изображений (риск S-02) |
| 16 | Light / Dark / Hybrid тема | Ring подсветки виден во всех палитрах (`--ppda-accent`) |
| 17 | Android back-кнопка | Поведение по `TopicBackBehavior` (anchor → history → выход) |
| 18 | Жест back (свайп) | Совпадает с кнопкой; нет двойного срабатывания |

### 14.3 Рекомендуемые новые автотесты
- Гонка JS DOM-anchor vs Kotlin INITIAL_ANCHOR (S-01): порядок установки `__themeScrollCommandId`.
- Deferral↔fadeout (H-01): fadeout не армится до `applyHighlight`.
- Кросс-топик новая вкладка + back (B-01): возврат к исходному посту.
- getnewpost off-page → controlled reload (U-01) вместо `entryIds.first()`.

---

## 15. Final Verdict

1. **Почему «не тот» якорь при открытии.** Две причины: (а) гонка JS DOM-initial-anchor против Kotlin INITIAL_ANCHOR при раннем `DOM`-событии (`theme.js:2592-2603`, S-01); (б) скролл выполняется до догрузки изображений/шрифтов, цель «уезжает» (`theme.js` retry-лестница vs image-recheck, S-02). Плюс двойная классификация `p=` (H-03).

2. **Почему unread открывает первый пост последней страницы.** `TopicUnreadOpenPolicy.resolveGetNewPostAnchor`/`resolveEntryFallback` при отсутствии HTML-unread-маркера и отвергнутом redirect доверяют серверному getnewpost-редиректу и list-хинту, возвращая `entryIds.first()`/`[1]` с `hasUnreadTarget=true` (`:687-692`), а off-page realign падает на `posts.first` (`:354`). Когда сервер редиректнул на последнюю страницу, это и есть «первый пост последней страницы» (U-01).

3. **Почему back теряет исходный пост.** Кросс-топик ссылка открывает Тему B в **новой вкладке** (`ThemeViewModel.kt:4221`), in-tab `historyController` Темы A не получает переход; source-anchor живёт в VM с TTL 15 c (B-01/B-02). В пределах одной темы back корректен (anchor stack / back-снапшот).

4. **Почему не восстанавливается scroll.** При уходе html обнуляется (`backPage`/`updateHistoryLastHtml("")`), back делает reload по чистому URL; если `anchorPostId` не authoritative (истёк TTL/рассинхрон st) — URL без `#entry` → верх страницы; при reload верстка отличается → восстановление по ratio/savedY неточно (B-02, S-02, R-08).

5. **Почему не работает подсветка.** Инфраструктура (класс `ppda_highlight_post`, CSS в `*_themes.css`, `--ppda-accent`, JS-контракт) **корректна и под тестом**. Подсветка не появляется, потому что: (а) нужный пост **не на отрендеренной странице** (следствие №1/№2) → JS `post_not_on_page`; (б) `hasUnreadTarget` ложно false → нет FirstUnread-входа (U-02); (в) нативный apply отложен `shouldDeferUntilScrollSettled`, а fadeout (2 c) развязан во времени → невидимо/мелькает (H-01/H-02).

6. **Что чинить первым.** U-01 (getnewpost-страница/якорь) — он же снимает большую часть №9. Параллельно — Phase 1 диагностика (логи уже в коде) для подтверждения веток. Затем H-01 (тайминг подсветки) и B-01/B-02 (back).

7. **Что НЕЛЬЗЯ трогать до подтверждения корня.** Не упрощать/удалять без тестов и runtime-логов: цепочку `resolveGetNewPostAnchor` (fallback), JS retry-механизмы (`scrollToElementWithRetries`/restore-with-retries), `ThemeOpenScrollCoalescePolicy`/`TopicOpenScrollRestorePolicy`, render-watchdog/reveal-пути, контракт source-anchor (`pendingHistorySourceAnchor`/TTL), infinite-scroll contamination guards, кэш-ключи и render-generation/signature handshake. CSS/JS подсветки менять НЕ нужно — корень не там.

---

*Конец отчёта. Изменения кода не вносились. Создан только этот файл.*












