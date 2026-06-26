# ForPDA — Поэтапный план работ (v4, на основе v3)

> **Назначение:** операционный план-роадмап для стабилизации ForPDA Android-клиента. Основа — `AUDIT_AND_ACTIONS_v3_FINAL_FULL.md`. Исключено: S-04 (AppMetrica), P-04 (CookieManager startup), офлайн-тема (не относится к текущему скопу).
>
> **Структура:** 6 этапов (Sprint 1–6) + Backlog. Внутри каждого этапа — задачи, сгруппированные по **доменам** и **приоритетам**.

---

## Легенда приоритетов

| Код | Смысл | Критерий |
|-----|-------|----------|
| **P0** | Release blocker | Без этого не выпускаем |
| **P1** | Critical quality | Без этого качество прода недопустимо |
| **P2** | Important | Включаем в ближайший спринт, иначе долг копится |
| **P3** | Hygiene | Делаем, когда есть окно |
| **P4** | Backlog | По запросу / при касании смежного кода |

## Домены (группы)

| Код | Домен |
|-----|-------|
| **SEC** | Security / WebView boundary / Secrets |
| **LIF** | Lifecycle / Bridge teardown / Coroutine scopes |
| **RUN** | Runtime / Blocking / Concurrency / Deadlock |
| **PERF** | Performance / WebView intercept / Cache |
| **MEM** | Memory / DOM growth / Retained objects |
| **TST** | Tests / CI gates / Tooling |
| **ARCH** | Architecture / Refactor / God-class |
| **MFT** | Manifest / Build / Config / Deps |
| **UX** | UX / A11y / Visual polish |

---

## Сводная таблица задач (без S-04, P-04, офлайн-темы)

| ID | Задача | Домен | Severity | Priority | Sprint | Зависит от |
|----|--------|-------|----------|----------|--------|------------|
| S-01 | Theme render-token guard (ThemeJsInterface destructive methods) | SEC | Critical | **P0** | 1 | — |
| S-02 | Theme bridge lifecycle validation (reject calls after view/presenter end) | LIF | High | **P0** | 1 | S-01 |
| C-08 | QMS WebView re-creation: cancel old `jsInterface` scope before destroy/recreate | LIF | High | **P0** | 1 | — |
| A-03 | Remove runtime `runBlocking` (5 sites) → suspend-first | RUN | High | **P0** | 2 | — |
| S-05 | UrlPolicy gate for JS-originated external URL actions in news bridge | SEC | Medium | **P1** | 2 | — |
| C-04 | `AvatarRepository.getAvatar(nick)` — return `String?` / domain exception, no raw NPE | RUN | Medium-High | **P1** | 2 | A-03 |
| D-11 | Enable `unitTests.includeAndroidResources = true` | TST | Info-Medium | **P2** | 2 | — |
| C-01 | `searchBlankVerifyRunnable!!` → nullable safe `postDelayed` | LIF | Medium | **P2** | 2 | — |
| C-02 | 22× `!!` на `ViewBinding.inflate` в `MessagePanel.kt:137-161` | LIF | Medium | **P2** | 2 | — |
| C-03 | `CustomWebViewClient.downloadFile()` — non-null `systemLinkHandler` или fail-fast | LIF | Medium | **P2** | 2 | — |
| T-05 | ANR/startup measurement gate (Perfetto + StrictMode + startup benchmark) | TST | Medium | **P2** | 3 | — |
| P-09 | WebView resource interception pipeline profiling (`shouldInterceptRequest`, decode/encode) | PERF | High | **P1** | 4 | T-05 |
| P-08 | WebView DOM growth controls (long topics, QMS windowing, max message retention) | MEM | Medium | **P2** | 4-5 | P-09 |
| P-02 | Remove `Thread.sleep(300)` из `ImageLoadingInterceptor` retry | PERF | Medium | **P2** | 4 | — |
| P-01 | OkHttp HTTP cache 10→50 MB + `Cache-Control: max-age` | PERF | Medium | **P3** | 4 | P-09 |
| A-01 | 3× `MainScope()` leaks → `lifecycleScope` / `viewModelScope` | LIF | Medium | **P2** | 5 | — |
| A-02 | `GlobalScope.launch` in `ThemeUseCase:326` → injected app scope | LIF | Medium | **P2** | 5 | — |
| A-04 | `ThemeViewModel` decomposition (extract one coordinator) | ARCH | Medium | **P2** | 5-6 | — |
| P-10 | Memory validation program (LeakCanary, heap dumps, retained-object gate) | MEM | Medium | **P2** | 5 | — |
| S-03 | `App.instance!!` getter → DI-инжекция или Hilt-bound `Application` | LIF | Medium | **P2** | 5 | — |
| P-07 | `WebSocketController` `synchronized` + dispatcher parsing → пересмотр | PERF | Low-Medium | **P3** | 5 | — |
| D-03 | `androidx.security:security-crypto:1.1.0-alpha06` → Tink или pin `1.0.0` | MFT | Medium | **P3** | 6 | — |
| D-04 | `minitemplator 1.2` (abandoned) → замена/форк | MFT | Medium | **P3** | 6 | — |
| T-01 | `detekt-baseline.xml` 484 КБ → burn-down (hand-curated set) | TST | Medium | **P3** | 6 | — |
| C-05 | `NotificationsService.IncomingHandler` Toast wrap in try/catch | LIF | Low-Medium | **P3** | 6 | — |
| M-01 | `allowBackup`/`dataExtractionRules` hardening (после верификации backup contents) | MFT | Low-Medium | **P3** | 6 | — |
| S-07 | `MIXED_CONTENT_COMPATIBILITY_MODE` → `MIXED_CONTENT_NEVER_ALLOW` (после QA) | SEC | Low-Medium | **P3** | 6 | — |
| C-06 | `ThemePostedPageScrollPolicy.maxByOrNull{}!!.id` → guard empty list | LIF | Low | **P4** | 6 | — |
| C-07 | `ThemeHistoryController.history.last()` after `.filter` → `lastOrNull` | LIF | Low | **P4** | 6 | — |
| P-06 | `CachedDns.clearCache()` on network change | PERF | Low | **P4** | 6 | — |
| M-03 | Sync `versionCode`/`versionName` (manifest vs gradle) | MFT | Low | **P4** | 6 | — |
| A-05 | `AppDatabase` split (entities 6 доменов) | ARCH | Low | **P4** | Backlog | — |
| D-05 | `sectioned-recyclerview` archived → замена при касании UI | MFT | Low | **P4** | Backlog | — |
| D-07 | `tagsoup 1.2.1` → замена при касании парсеров | MFT | Low | **P4** | Backlog | — |
| D-09 | `cicerone 6.6` JitPack → pin immutable commit | MFT | Low | **P4** | Backlog | — |
| M-02 | `MainActivity` exported deep-link → verify UrlPolicy path | MFT | Low | **P4** | Backlog | — |
| P-03 | `ArticleInteractor` phase-2 always scheduled (PERF_DIAGNOSIS §5.6) | PERF | Low | **P4** | Backlog | — |
| T-04 | ProGuard rule `-keep class App { public <init>(); }` redundant | MFT | Low | **P4** | Backlog | — |
| X-04 | `textZoom=100` first-paint glitch в WebView | UX | Low | **P4** | Backlog | — |
| X-05 | `enableEdgeToEdge` per-activity для Android 15+ | UX | Low | **P4** | Backlog | — |
| A-06 | Parser pattern shared via `IPatternProvider` — добавить parser tests | TST | Info | Optional | Backlog | — |
| C-09 | StrictMode disabled in release — release-safe telemetry | TST | Info | Optional | Backlog | — |
| X-01 | CP1251 login — документировать UX constraint | UX | Info | Optional | Backlog | — |
| X-02 | Logout discoverability в UI | UX | Info | Optional | Backlog | — |
| X-03 | Captcha fallback QA | UX | Info | Optional | Backlog | — |

**Исключены из плана (по запросу):** S-04 (AppMetrica), P-04 (CookieManager startup), офлайн-тема.

**Info "no action":** D-01 (coil), D-02 (okhttp), D-06 (jsoup), D-08 (cicerone), D-10 (hilt), M-04 (WakeUpReceiver), P-05 (Coil cache), S-06 (WebView ctor), S-08 (DownloadsService), T-02 (assembleStoreRelease), T-03 (proguard). В таблице не отражены — зафиксированы как «confirmed safe / no action».

---


## Sprint 1 — JS Bridge Security & Lifecycle (release blocker)

**Цель:** закрыть критический WebView trust boundary. Без этого не выпускаем.

**Exit criteria:**
- [ ] Все деструктивные `ThemeJsInterface` методы требуют валидный render-token
- [ ] Stale / missing / wrong / blank token → лог-но-оп (не crash)
- [ ] QMS WebView re-create не теряет bridge-скоуп и не оставляет stale coroutine
- [ ] Rotation / reopen / process-restore тесты зелёные

### Задачи

| ID | Задача | Домен | Priority | Файлы | Acceptance |
|----|--------|-------|----------|-------|------------|
| **S-01** | Theme render-token guard | SEC | **P0** | `presentation/theme/ThemeJsInterface.kt`, `presentation/theme/ThemeRenderGuard.kt` | `deletePost`/`editPost`/`votePost`/`submitPoll`/`reply`/`quote*`/`reportPost`/`openLink`/`openImage` требуют токен. Unit-тесты: null, blank, wrong, stale, valid. Instrumented smoke: forged template call отбрасывается. |
| **S-02** | Theme bridge lifecycle validation | LIF | **P0** | `ui/fragments/theme/ThemeBridgeHandler.kt`, `ThemeFragmentWeb.kt` | Reject вызовов после `onDestroyView`/presenter end. Связано с S-01 (одна feature). Rotation/reopen тесты зелёные. |
| **C-08** | QMS WebView re-create: cancel old `jsInterface` scope | LIF | **P0** | `ui/fragments/qms/chat/QmsChatFragment.kt:2400-2421` | `jsInterface?.cancel()` (BaseJsInterface уже имеет) перед `destroy()`. Reentrance guard на `addJavascriptInterface`. |

**Делать вместе:** S-01 + S-02 + C-08 = один atomic PR. Без S-02 S-01 бесполезен.

**Не делать в Sprint 1:** рефактор ThemeViewModel, новые JS-методы, редизайн шаблонов, Compose-миграция.

---

## Sprint 2 — Runtime Correctness & Focused Tests

**Цель:** убрать runtime-blocking / deadlock класс, починить crash-цепочку, сделать тесты работоспособными.

**Exit criteria:**
- [ ] Нет production-runtime `runBlocking` кроме задокументированных shim (с assert `Looper.myLooper() != Looper.getMainLooper()`)
- [ ] CI grep-guard на новые `runBlocking` в `app/src/main`
- [ ] `AvatarRepository` контракт без raw NPE
- [ ] Search/media focused тесты зелёные
- [ ] Targeted crash-paths (C-01/C-02/C-03) закрыты

### Задачи

| ID | Задача | Домен | Priority | Файлы | Acceptance |
|----|--------|-------|----------|-------|------------|
| **A-03** | Remove runtime `runBlocking` (5 sites) | RUN | **P0** | `common/ForPdaCoil.kt:215,240`, `model/data/remote/api/news/NewsApi.kt:62`, `model/data/remote/api/theme/ThemeApi.kt:341`, `model/repository/avatar/AvatarRepository.kt:40,48` | Suspend-first API. `ForPdaCoil` blocking shim остаётся с precondition-assert. CI guard: `rg "runBlocking" app/src/main` без runtime-path матчей. |
| **S-05** | UrlPolicy gate для JS-originated external URL | SEC | **P1** | `ui/fragments/news/details/ArticleContentFragment.kt` (`openExternalBrowser`, `openImage`) | Оба метода обёрнуты в `UrlPolicy.classify(...)`; не-http(s) / `Blocked` отбрасываются. Регрессионный тест. |
| **C-04** | `AvatarRepository.getAvatar(nick)` без raw NPE | RUN | **P1** | `model/repository/avatar/AvatarRepository.kt:24-37` | `String?` + `AvatarNotFoundException(nick)` вместо `throw NullPointerException`. `NotificationsService` обновить обработчик. |
| **D-11** | Enable `unitTests.includeAndroidResources = true` | TST | **P2** | `app/build.gradle:211-213` | Тесты, зависящие от resources, проходят локально и в CI. |
| **C-01** | `searchBlankVerifyRunnable!!` → safe `postDelayed` | LIF | **P2** | `ui/fragments/search/SearchFragment.kt:633` | `runnable?.let { webView.postDelayed(it, 48L) }`. Аналогично в `removeCallbacks` пути. |
| **C-02** | 22× `!!` на `ViewBinding.inflate` в `MessagePanel` | LIF | **P2** | `ui/views/messagepanel/MessagePanel.kt:137-161` | Заменить на `?.let { ... = it }` или sealed `Mode { Full, Quick }`. Цель: ноль `!!` в этом файле. |
| **C-03** | `CustomWebViewClient.downloadFile()` non-null `systemLinkHandler` | LIF | **P2** | `common/webview/CustomWebViewClient.kt:288` | `systemLinkHandler` non-nullable в конструкторе. Caller обязан передавать. Fail-fast при отсутствии. |

**Рекомендуемый порядок:** A-03 → C-04 (использует avatar pipeline) → C-01/C-02/C-03 (лёгкие) → S-05 → D-11 (последним, чтобы тесты уже имели смысл).

---

## Sprint 3 — ANR/Startup Measurement Gate

**Цель:** создать измерительную инфраструктуру до любых startup-оптимизаций. P-04 (CookieManager) **намеренно пропущен** по запросу — этот спринт оставляем для других startup-задач (если появятся) или для T-05 как страховки под будущее.

**Exit criteria:**
- [ ] Perfetto-скрипт для cold start коммитится, артефакты сохраняются в `docs/perf/`
- [ ] StrictMode disk-read логгинг включён в debug
- [ ] Startup benchmark воспроизводимый (одинаковые цифры на одинаковом устройстве)
- [ ] Документ `docs/STARTUP_BASELINE.md` со снимком «до»

### Задачи

| ID | Задача | Домен | Priority | Файлы | Acceptance |
|----|--------|-------|----------|-------|------------|
| **T-05** | ANR/startup measurement gate | TST | **P2** | `scripts/battery-audit.sh` (расширить), новые Perfetto-скрипты, `docs/STARTUP_BASELINE.md` | Скрипт запускает cold start N раз, собирает traces, считает p50/p95. Baseline зафиксирован. StrictMode disk read + network на main thread детектится. |

**Зачем отдельный спринт:** P-04 был пропущен, но T-05 страхует под любую будущую startup-оптимизацию (мы её не делаем сейчас, но измерения оставляем — иначе следующий человек начнёт гадать).

---

## Sprint 4 — WebView Performance & Image Pipeline

**Цель:** уменьшить user-visible jank в тяжёлых темах. Сначала профилирование, потом оптимизация.

**Exit criteria:**
- [ ] Отчёт `docs/perf/WEBVIEW_INTERCEPT_REPORT.md` с top-N затрат в `shouldInterceptRequest`
- [ ] Image-heavy topic open/scroll измерен до/после
- [ ] Hot-path synchronous decode/encode удалён или обоснован
- [ ] `Thread.sleep(300)` в interceptor убран
- [ ] DOM growth в long QMS session измерен (хотя бы baseline)

### Задачи

| ID | Задача | Домен | Priority | Файлы | Acceptance |
|----|--------|-------|----------|-------|------------|
| **P-09** | WebView resource interception profiling | PERF | **P1** | `common/webview/CustomWebViewClient.kt`, `common/ForPdaCoil.kt`, `model/repository/avatar/AvatarRepository.kt`, image interceptors | Debug-only тайминги в `shouldInterceptRequest`. Avatar/image cache lookup, bitmap decode/encode, stream creation — измерены. Предпочтение cached bytes/streams. |
| **P-08** | WebView DOM growth controls | MEM | **P2** | `ThemeFragmentWeb`, `QmsChatFragment`, templates/assets | Baseline измерение. QMS windowing или max message retention стратегия. Threshold зафиксирован. |
| **P-02** | Убрать `Thread.sleep(300)` из `ImageLoadingInterceptor` | PERF | **P2** | `client/interceptors/ImageLoadingInterceptor.kt:72` | Заменить на `chain.call().cancel() + new enqueue` или просто убрать sleep. Retry 504/503 остаётся, но без блокировки dispatcher. |
| **P-01** | OkHttp HTTP cache 10→50 MB + `Cache-Control: max-age` | PERF | **P3** | `client/Client.kt:112`, добавить interceptor | Поднять кэш до 50 MB. Inject `Cache-Control: max-age=60` для assets/theme HTML (auth-requests не трогать). |

**Рекомендуемый порядок:** P-09 (профиль) → P-08 (baseline) → P-02 (дешёвый фикс) → P-01 (после того, как ясно, что не мешает измерениям).

**Зависимость:** P-08 и P-01 требуют, чтобы T-05 из Sprint 3 уже был в репозитории (для сравнения).

---

## Sprint 5 — Memory, Lifecycle & Coroutine Scopes

**Цель:** починить утечки, навести порядок в coroutine scopes, начать распутывание ThemeViewModel.

**Exit criteria:**
- [ ] LeakCanary включён в debug, ретеншн-чек проходит
- [ ] Нет retained Theme/QMS WebView после закрытия экранов
- [ ] Нет retained Fragment после rotation/back
- [ ] Нет `MainScope()` / `GlobalScope.launch` в production runtime
- [ ] Heap snapshot после 5–10 навигаций по тяжёлым темам — в `docs/perf/`
- [ ] Один ThemeViewModel coordinator извлечён с тестами

### Задачи

| ID | Задача | Домен | Priority | Файлы | Acceptance |
|----|--------|-------|----------|-------|------------|
| **A-01** | 3× `MainScope()` leaks → lifecycle-aware scopes | LIF | **P2** | `presentation/TabRouter.kt:16`, `ui/fragments/theme/ThemeDialogsHelper_V2.kt:43`, `ui/views/messagepanel/advanced/CodesPanelItem.kt:41` | `lifecycleScope` (Fragment) / `viewModelScope` (ViewModel). Для `CodesPanelItem` (view) — `dispose()` + явный вызов из родительского fragment. |
| **A-02** | `GlobalScope.launch` in `ThemeUseCase` → injected app scope | LIF | **P2** | `model/interactors/theme/ThemeUseCase.kt:326` | Inject `@Singleton` `appScope` (или use case-owned scope с `cancel()`). |
| **S-03** | `App.instance!!` getter → DI injection | LIF | **P2** | `App.kt:103`, `common/HtmlToSpannedConverter.kt:171`, `client/interceptors/ErrorInterceptor.kt` | Убрать `App.instance!!`. В Hilt-bound `Application` через `@ApplicationContext`. Исправить 6+ caller'ов. |
| **P-10** | Memory validation program | MEM | **P2** | LeakCanary config, `docs/MEMORY_QA_CHECKLIST.md` | LeakCanary в debug. Heap dump после 5-10 навигаций по тяжёлым темам. Документ с порогами. |
| **A-04** | ThemeViewModel decomposition (extract one coordinator) | ARCH | **P2** | `presentation/theme/ThemeViewModel.kt` | Извлечь один coordinator (lifecycle / action / render boundary) за текущим public API. Регрессионные тесты. |
| **P-07** | `WebSocketController` `synchronized` + dispatcher parsing | PERF | **P3** | `client/WebSocketController.kt:21-118` | Пересмотр только если WebSocket parsing показывает jank в профиле. Иначе отложить. |

**Зависимости:** A-01 и A-02 — независимые, делать параллельно. A-04 — отдельная задача, требует собственного spike перед PR. P-10 — самостоятельная.

---

## Sprint 6 — Architecture & Hygiene

**Цель:** техдолг, который не блокирует прод, но копится. Делаем после стабилизации.

**Exit criteria:**
- [ ] `androidx.security:security-crypto` либо pin `1.0.0`, либо мигрирован на Tink
- [ ] `minitemplator 1.2` либо заменён, либо форк зафиксирован в нашем репо
- [ ] `detekt-baseline.xml` ужат до hand-curated set (3-5 правил)
- [ ] Notifications service `Toast` обёрнут в try/catch
- [ ] `versionCode`/`versionName` синхронизированы manifest ↔ gradle
- [ ] Документ по backup hardening: фактическое содержимое проверено

### Задачи

| ID | Задача | Домен | Priority | Файлы | Acceptance |
|----|--------|-------|----------|-------|------------|
| **D-03** | `androidx.security:security-crypto:1.1.0-alpha06` → stable | MFT | **P3** | `gradle/libs.versions.toml:40`, `app/build.gradle` | Pin `1.0.0` если alpha API не используется, либо spike на Tink migration. |
| **D-04** | `minitemplator 1.2` (abandoned) | MFT | **P3** | `gradle/libs.versions.toml:72` | Заменить на поддерживаемый форк или скопировать исходники в наш `third_party/`. |
| **T-01** | `detekt-baseline.xml` 484 КБ → burn-down | TST | **P3** | `detekt-baseline.xml`, `detekt.yml` | Оставить 2-3 high-signal правила (`ComplexMethod`, `LongMethod`, `LongParameterList`). Baseline ужат до <50 КБ. |
| **C-05** | `NotificationsService.IncomingHandler` Toast wrap | LIF | **P3** | `notifications/NotificationsService.kt:547-555` | `try { Toast... } catch (BadTokenException) { }`. Опционально: убрать мёртвый `Messenger` path. |
| **M-01** | Backup hardening | MFT | **P3** | `AndroidManifest.xml:49-50` | Сначала проверить, что реально попадает в backup (`bmgr backupnow`). Потом — `data_extraction_rules.xml` с исключениями EncryptedSharedPreferences + auth_key. |
| **S-07** | `MIXED_CONTENT_COMPATIBILITY_MODE` → `NEVER_ALLOW` | SEC | **P3** | `ui/views/ExtendedWebView.kt:151` | Сменить. QA: проверить CDN/images не регрессируют. |
| **C-06** | `ThemePostedPageScrollPolicy` empty-list guard | LIF | **P4** | `presentation/theme/ThemePostedPageScrollPolicy.kt:27-28` | `maxByOrNull{...}?.id ?: return`. |
| **C-07** | `ThemeHistoryController.history.lastOrNull` | LIF | **P4** | `presentation/theme/ThemeHistoryController.kt:101,138,169` | `lastOrNull { ... }` + handle null. |
| **P-06** | `CachedDns.clearCache()` on network change | PERF | **P4** | `client/CachedDns.kt:20,61` | Подписаться на `NetworkConnectivityTracker`. |
| **M-03** | Sync `versionCode`/`versionName` | MFT | **P4** | `AndroidManifest.xml:4-5`, `app/build.gradle:67` | Обновить manifest до 350/2.9.4. |
| **T-04** | ProGuard rule `App` constructor redundant | MFT | **P4** | `app/proguard-rules.pro:24-26` | Удалить строку. Hilt уже держит. |
| **C-09** | StrictMode release-safe telemetry | TST | Optional | `App.kt:220-235` | Crashlytics/Breadcrumb hooks вместо StrictMode в release. |

---

## Backlog (P4 / Optional)

Делаем **только при касании** смежного кода или по явному запросу. Не планируем в спринт.

| ID | Задача | Домен | Trigger для включения |
|----|--------|-------|------------------------|
| A-05 | `AppDatabase` split на 2-3 доменных БД | ARCH | Запрос на «очистить отдельный кэш» (notes/history/favorites) |
| A-06 | Parser tests на `IPatternProvider` shared patterns | TST | Любой баг в парсере темы/новостей |
| D-05 | `sectioned-recyclerview` archived замена | MFT | Касание соответствующего экрана |
| D-07 | `tagsoup 1.2.1` замена | MFT | Касание парсеров |
| D-09 | `cicerone 6.6` JitPack tag pin | MFT | Любой release со сдвигом тегов |
| M-02 | `MainActivity` deep-link verify UrlPolicy | MFT | Любое изменение intent-filter |
| P-03 | `ArticleInteractor` phase-2 schedule (PERF_DIAGNOSIS §5.6) | PERF | Если device QA покажет долгий open of large article |
| T-04 | ProGuard `App` rule redundant | MFT | Любой ProGuard sweep |
| X-04 | `textZoom=100` first-paint glitch | UX | Если пользователи жалуются на «прыжок» шрифта |
| X-05 | `enableEdgeToEdge` per-activity для Android 15+ | UX | При device QA на Android 15+ с проблемами insets |
| X-01 | CP1251 login — документировать UX constraint | UX | Внутренняя документация форка |
| X-02 | Logout discoverability | UX | Если UX-тесты покажут неочевидность |
| X-03 | Captcha fallback QA | UX | Если жалобы на captcha loading |

---

## Сводный ритм спринтов

| Sprint | Фокус | Кол-во задач | Критический выход |
|--------|-------|--------------|-------------------|
| **1** | JS Bridge Security & Lifecycle | 3 (S-01, S-02, C-08) | Render-token guard работает, QMS bridge safe |
| **2** | Runtime Correctness & Tests | 7 (A-03, S-05, C-04, D-11, C-01, C-02, C-03) | Нет `runBlocking` в runtime, тесты зелёные |
| **3** | ANR/Startup Measurement Gate | 1 (T-05) | Измерительная инфраструктура |
| **4** | WebView Perf & Image Pipeline | 4 (P-09, P-08, P-02, P-01) | Hot-path измерен и оптимизирован |
| **5** | Memory, Lifecycle & Coroutines | 6 (A-01, A-02, S-03, P-10, A-04, P-07) | Нет утечек, scopes почищены, ThemeViewModel начат |
| **6** | Architecture & Hygiene | 12 (D-03, D-04, T-01, C-05, M-01, S-07, C-06, C-07, P-06, M-03, T-04, C-09) | Техдолг ужат |
| **Backlog** | По запросу | 13 | — |

**Итого в активных спринтах:** 33 задачи.

---

## Правила работы (AI-Agent rules)

Эти правила идут прямо в Cursor / Claude Code.

### Всегда

- Читать target-файлы до правки.
- Один atomic change за раз.
- Тесты обновлять/добавлять с каждым behavior change.
- Запускать самый узкий доступный тест первым.
- Сохранять public API, если задача не требует API-изменения явно.
- Документировать behavior change в соответствующем `docs/*CHECKLIST.md`.
- Feature flag / staged rollout для рискованных WebView/runtime изменений.

### Никогда

- Не переписывать ThemeViewModel целиком.
- Не переписывать WebView renderer.
- Не мигрировать экраны на Compose в рамках этого плана.
- Не обходить UrlPolicy.
- Не добавлять `@JavascriptInterface` методы без review.
- Не добавлять `runBlocking` в production runtime.
- Не прятать parser errors за broad `try/catch` без сохранения expected output.
- Не считать compile-success за release sign-off.

---

## Release Gate Checklist

Release candidate приемлем только если:

- [ ] **S-01** render-token guard работает и покрыт тестами
- [ ] **S-02** theme lifecycle validation работает
- [ ] **C-08** QMS WebView re-create без потерь
- [ ] **A-03** нет runtime-path `runBlocking`
- [ ] Search focused тесты зелёные
- [ ] Media/news parser focused тесты зелёные
- [ ] UrlPolicy тесты зелёные
- [ ] **T-05** Perfetto/StrictMode baseline зафиксирован
- [ ] LeakCanary / memory audit без retained WebView
- [ ] Device QA matrix пройден
- [ ] Нет открытых Critical или High bridge/lifecycle findings

---

## Device QA Matrix (минимум)

**Устройства:** low-end Android / low-RAM emulator, mid-range, flagship или современный emulator.

**Сценарии:** long topic с изображениями, image-heavy topic, quote/reply/edit без destructive submit, search → result, mentions read/unread, favorites badge/mark-read, QMS long session, QMS rotate/reopen, external links, downloads, login, captcha, IME/snackbar overlap, gesture и 3-button nav, font scale 1.3–1.5, process death restore.

---

## Итог

Доминирующая цепочка рисков: **WebView → JS Bridge → Lifecycle → Runtime blocking → Image pipeline → Memory**.

**Операционный принцип:**
1. Стабилизировать security и lifecycle первыми (Sprint 1).
2. Убрать runtime-blocking параллельно (Sprint 2).
3. Измерять startup и WebView perf перед оптимизацией (Sprint 3–4).
4. Оптимизировать только после измерений (Sprint 4).
5. Память и coroutine scopes — после того, как hot-path стабилен (Sprint 5).
6. Техдолг и гигиена — в конце (Sprint 6).
7. Архитектурный рефакторинг — gradual, по одному coordinator за спринт (Sprint 5+).
