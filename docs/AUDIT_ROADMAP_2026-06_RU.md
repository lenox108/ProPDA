# Аудит Android-клиента ForPDA (июнь 2026) — Roadmap

## 1. Резюме

- **Стабильность: Низкая.** 152+ `!!`, 4 `runBlocking` в `suspend`, 75+ `postDelayed`, NULL-after-destroy сценарии.
- **Производительность: Средняя.** Cold start не замеряется, `ThemePageMemoryCache.copyForCache` аллоцирует на каждый `get`, `MainScope()`/`GlobalScope.launch` держат ссылки на UI.
- **Безопасность: Средняя.** Mixed content ограничен 4pda; `auth_key.xml` backup-исключение — no-op; хардкоженный AppMetrica ключ.
- **Поддерживаемость: Низкая.** God-классы `ThemeViewModel` (4000+ LOC), `ThemeFragment` (1800+ LOC), `TemplateManager`; 3 пути декодирования HTML-entities.

**Top-5 highest-risk areas:**
1. `!!` + post-fragment-destroy callbacks в `ThemeFragment`/`SearchFragment`/`QmsChatFragment` → NPE.
2. QMS WebView — утечка `jsInterface` после renderer recovery (`QmsChatFragment.kt:2451-2472`).
3. Hybrid theme — `ThemeViewModel` god-class + рассинхрон WebView ↔ native при scroll-restore.
4. `runBlocking` в `suspend` (`NewsApi.kt:62`, `ThemeApi.kt:341`, `ForPdaCoil.kt:215,240`) — дедлоки.
5. Уведомления: `myMessenger` — мёртвый код, нет каналов, `POST_NOTIFICATIONS` не запрашивается — **на Android 13+ не работают**.

## 2. Архитектура (кратко)

- **Модули:** single-module app, пакеты `forpdateam.ru.forpda.{ui, presentation, model, client, common, di, diagnostic, notifications, entity}`.
- **WebView flow:** URL → `ThemeFragment` → `ThemeViewModel` → `ThemePage` → `ThemeRenderSession` → `ExtendedWebView` + `ThemeBridgeHandler` JS-bridge.
- **Networking flow:** `Client` собирает OkHttp с `CacheControlInterceptor`, `AuthInterceptor` (cookie), `ForpdaCoil` (image).
- **Storage flow:** Room (`NotesDatabase`, `OfflineItemRoom`), in-memory `ThemePageMemoryCache`, prefs `SecureCookiesPreferences`; OkHttp cache `Client.kt:112` (10 МБ, обещано 50).
- **Background work flow:** `NotificationsService` (foreground) + `NotificationPublisher`; WorkManager — минимум; FCM — нет; boot-receiver — не зарегистрирован.

## 3. Критические находки (Critical/High)

### AUDIT-C01 — NPE в `SearchFragment` при `searchBlankVerifyRunnable!!`
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt:652`
- **Причина:** `!!` на Runnable, который может быть очищен в `onPause`/при пересоздании View.
- **Влияние:** NPE на каждом выходе/повороте экрана поиска.
- **Фикс:** заменить `!!` на `?.let { handler.post(it) }` + явный `removeCallbacks` в `onDestroyView`.
- **Трудозатраты:** XS. **Риск фикса:** Low.

### AUDIT-C02 — 22× `!!` в `MessagePanel`
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/MessagePanel.kt:137-165`
- **Причина:** раздутый init-блок с обращениями к дочерним View до `onAttachedToWindow`.
- **Влияние:** NPE при programmatically created panels или быстром пересоздании фрагмента темы.
- **Фикс:** переход на `findViewById` с null-check + lazy `BindingDelegate`; viewBinding для panel.
- **Трудозатраты:** M. **Риск фикса:** Medium (UI-regression).

### AUDIT-C03 — `systemLinkHandler!!` в `CustomWebViewClient`
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt:288`
- **Причина:** lateinit-обработчик, не сбрасывается при `WebView.destroy()`.
- **Влияние:** NPE в `shouldOverrideUrlLoading` при переиспользовании WebView-пула.
- **Фикс:** `var systemLinkHandler: SystemLinkHandler? = null` + `?.handle(...)`; сброс в `onDetachedFromWindow`.
- **Трудозатраты:** XS. **Риск фикса:** Low.

### AUDIT-C04 — расхождение versionCode/versionName
- **Файлы:** `app/src/main/AndroidManifest.xml:4-5` (`349/2.9.3`) vs `app/build.gradle:1+` (`350/2.9.4`).
- **Причина:** ручное редактирование одного файла минуя другой.
- **Влияние:** Play Store отклонит апдейт, в логах краш-репортов — неверная версия.
- **Фикс:** использовать `versionCode`/`versionName` только из `build.gradle`; удалить дубликат из `AndroidManifest.xml`.
- **Трудозатраты:** XS. **Риск фикса:** Low.

### AUDIT-C05 — `runBlocking` в `suspend` функциях
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt:62`, `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeApi.kt:341`
- **Причина:** парсинг HTML внутри `withContext(Dispatchers.IO) { runBlocking { ... } }` — вложенная блокировка.
- **Влияние:** ANR при загрузке длинных тем / новостей; coroutine cancellation не работает.
- **Фикс:** убрать `runBlocking`, переписать на `suspend` + `withContext(Default) { Jsoup.parse(...) }`; вынести парсер в отдельный `suspend fun`.
- **Трудозатраты:** S. **Риск фикса:** Low.

### AUDIT-C06 — QMS WebView `jsInterface` утечка
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/QmsChatFragment.kt:2451-2472`
- **Причина:** после `onRenderProcessGone` (renderer recovery) делается `addJavascriptInterface` заново, но `jsInterface.cancel()` не вызывается.
- **Влияние:** двойные уведомления, потеря сообщений, утечка WebView.
- **Фикс:** перед `webView.addJavascriptInterface(newJsInterface, ...)` вызвать `jsInterface.cancel()` + `removeJavascriptInterface(name)`.
- **Трудозатраты:** S. **Риск фикса:** Low.

### AUDIT-C07 — HTTP-cache 10 МБ вместо обещанных 50 МБ
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/client/Client.kt:112`
- **Причина:** `.cache(Cache(File(context.cacheDir, "http"), 10 * 1024 * 1024))` — не совпадает с `docs/AUDIT_REPORT.md` ("50 МБ").
- **Влияние:** частые `Cache-Control: max-age=0` срабатывания, повышенный трафик.
- **Фикс:** увеличить до 50 МБ (или 30 МБ); задокументировать в `Client.kt:1-30`.
- **Трудозатраты:** XS. **Риск фикса:** Low.

### AUDIT-C08 — `AvatarRepository` бросает NPE вместо `AvatarNotFoundException`
- **Файлы:** `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt:24-37` (класс `AvatarNotFoundException.kt` уже создан).
- **Причина:** `!!` на nullable результат, нет fallback-ветки.
- **Влияние:** NPE на каждом аватаре без URL; ломает списки пользователей.
- **Фикс:** бросать `AvatarNotFoundException(userId)`; в UI — catch + плейсхолдер.
- **Трудозатраты:** XS. **Риск фикса:** Low.

## 4. Находки Medium/Low

### WebView
- **AUDIT-M01:** `NotificationsService.kt:547-555` — `Toast.makeText(...).show()` напрямую в IncomingHandler; на Android 11+ приходит `BadTokenException` при отсоединённом окне.
- **AUDIT-M02:** `NotificationSnapshotLock.kt:4-5` — `internal object` без полей/методов, файл-пустышка.
- **AUDIT-L01:** 5 `addJavascriptInterface` — `ExtendedWebView.kt:249`, `ThemeBridgeHandler.kt:34-35`, `SearchFragment.kt:351`, `ArticleContentFragment.kt:2999`, `QmsChatFragment.kt:225,2472`, `ForumRulesFragment.kt:102`. Все аннотированы `@JavascriptInterface` — допустимо, но аудит JS-моста обязателен.

### Networking
- **AUDIT-M09:** `Client.kt:371-406` + `ForumHeaderCounters.kt:24-48` — дубль парсинга счётчиков форума; разные regex, расходятся.
- **AUDIT-M15:** `CookieManager.kt:43-81` + `SecureCookiesPreferences.kt:31-95` — first-touch cookies на main thread; блокирует UI при cold start.
- **AUDIT-L05:** 3 пути декодирования HTML-entities — `Html.fromHtml`, `ApiUtils.fromHtml`, `NickEncoder.decode` — нужно объединить.

### Lifecycle
- **AUDIT-M03:** `App.kt:142` — `private val appScope = CoroutineScope(...)` вместо `@AppScope` qualifier из `common/di/AppScope.kt` (файл создан, но не подключён).
- **AUDIT-M04:** `App.kt:169-207` — `ColdStartTracer` (файл создан) **не вызывается** из `App.attachBaseContext`/`onCreate`. Холодный старт по-прежнему «чёрный ящик».
- **AUDIT-M05:** `TabRouter.kt:16`, `ThemeDialogsHelper_V2.kt:36`, `CodesPanelItem.kt:41` — 3× `MainScope()` в UI-слое; не отменяются при destroy.
- **AUDIT-M06:** `ThemeUseCase.kt:328` — `GlobalScope.launch` без структурной привязки.
- **AUDIT-M07:** `HtmlToSpannedConverter.kt:171` — `App.instance!!` / `App.instance.getDrawable(...)` (main-thread context, нельзя из `Dispatchers.Default`).
- **AUDIT-M08:** `App.kt:103` — `val instance: App get() = _instance!!` — global singleton, anti-pattern.
- **AUDIT-M10:** `App.getActivity()` at `App.kt:589-603` — всегда `null` (мёртвый код).
- **AUDIT-M11:** `NotificationsService.kt:56,89-104,547-555` + `App.kt:149-161` — мёртвые `myMessenger`/`onBind`/`mServiceConnection`.
- **AUDIT-M14:** 152+ `!!` в `app/src/main` (top-5: `MessagePanel.kt:27`, `InkPageIndicator:25`, `ArticleContentFragment:14`, `BottomSheetBehaviorFixed:16`, `HtmlToSpannedConverter:7`).
- **AUDIT-L02:** 75+ `postDelayed` (top: `ArticleContentFragment:23`, `QmsChatFragment:11`, `ThemeWebController:8`, `MessagePanel:6`, `AdvancedPopup:5`).
- **AUDIT-L04:** `App.instance!!` patterns (рассмотрено в M07/M08).

### Database/Cache
- **AUDIT-M12:** `data_extraction_rules.xml:26` + `backup_rules.xml:8` — исключение `auth_key.xml` no-op: ключ лежит в `default SharedPreferences` (`Client.kt:101-103`).
- **AUDIT-L08:** `ThemePageMemoryCache.copyForCache:62` — аллокация на каждый `get`; LRU без `softValues`.
- **AUDIT-L11:** `AppDatabase` — нет SQLCipher (приемлемо для публичного контента; задокументировать в README).

### UI
- **AUDIT-L06:** `ThemeViewModel` ≥ 4000 LOC god-class — разбить на `ThemeLoadController`, `ThemeScrollController`, `ThemeHighlightController`, `ThemeRenderController`.
- **AUDIT-L07:** `ThemeFragment` 1800+ LOC — выделить `ThemeHeaderBinder`, `ThemeToolbarController`.
- **AUDIT-L10:** `TemplateCssComposer` extraction done; `TemplateManager` всё ещё god-class.

### Notifications
- **AUDIT-M16:** `TopicHighlightDiagnostics.kt:284-286` — `FpdaDebugLog.log` без `BuildConfig.DEBUG` гейта; в release-сборке лог утекает.
- **AUDIT-L03:** `MentionNotificationMapper.ARTICLE_ID_PATTERN:95-99` — слишком greedy, ловит forum permalinks.
- **AUDIT-L12:** `NotificationPublisher.publishStacked` — без `InboxStyle`, склеивает N уведомлений в один текст.

### Security
- **AUDIT-M13:** `App.kt:280` — хардкоженный `"a94d9236-cdf3-4a5e-af30-d6dbffaea362"` AppMetrica API key.

### Performance
- **AUDIT-L09:** `AvatarRepository` — 5 entry points, нужен единый `AvatarLoader` поверх Coil.

## 5. Аудит краш-рисков

- **`!!` total:** ≥ 152 в `app/src/main` (см. AUDIT-M14).
- **Top-5 `!!`:** `MessagePanel.kt:27` (panel init), `InkPageIndicator:25` (canvas draw без null-check), `ArticleContentFragment:14` (callback handlers), `BottomSheetBehaviorFixed:16` (стабы до `onAttachedToWindow`), `HtmlToSpannedConverter:7` (парсинг с глобальным context).
- **lateinit misuse:**
  - `CustomWebViewClient.systemLinkHandler` (`CustomWebViewClient.kt:288`) — race при destroy.
  - `App._instance` (`App.kt:103`) — NPE при обращении из BroadcastReceiver до `onCreate`.
  - `MessagePanel` поля (`MessagePanel.kt:137-165`) — NPE при programmatic inflate до `onAttachedToWindow`.
- **post-onDestroyView callbacks:** `SearchFragment.searchBlankVerifyRunnable!!` (`SearchFragment.kt:652`); 75+ `postDelayed` (`L02`) — handler.removeCallbacksAndMessages(null) отсутствует в `onDestroyView` большинства фрагментов.
- **Stale Fragment refs:** `App.getActivity()` (`App.kt:589-603`) — всегда null, попытки вызова → NPE; `ThemeDialogsHelper_V2.MainScope()` (`ThemeDialogsHelper_V2.kt:36`) переживает fragment.
- **Stale WebView refs:** `QmsChatFragment` (`QmsChatFragment.kt:2451-2472`) — `jsInterface` не отменяется до `addJavascriptInterface` после recovery.
- **Service/notification crashes:** `NotificationsService` использует прямой `Toast` в IncomingHandler (`M01`) — `BadTokenException` на современных Android.

## 6. Аудит производительности

- **Main-thread I/O:** `CookieManager.kt:43-81` (first-touch cookies), `SecureCookiesPreferences.kt:31-95`, `Client.kt:101-103` (SharedPreferences init).
- **Blocking calls:** `runBlocking` — `ForPdaCoil.kt:215,240`, `NewsApi.kt:62`, `ThemeApi.kt:341`, `AvatarRepository.kt:40,48` (4+).
- **Redundant parsing:** `Client.kt:371-406` + `ForumHeaderCounters.kt:24-48` (M09); `HtmlToSpannedConverter.kt:171` повторно парсит уже спарсенное.
- **WebView re-injection:** `ThemeBridgeHandler.kt:34-35` инжектит JS на каждом `pageStarted`; не кэшируется compiled JS.
- **Memory-heavy ops:** `ThemePageMemoryCache.copyForCache:62` (`L08`) — `ArrayList<ThemePost>` per get.
- **Unnecessary allocations:** `ArticleContentFragment.kt:23` (новый `Runnable` каждый `postDelayed`); `HtmlToSpannedConverter.kt:171` — `SpannableStringBuilder` per post.
- **Slow startup paths:** `Client.kt:1-130` собирает 5 интерсепторов + Coil + Retrofit при `onCreate`; `App.kt:142-207` запускает 3+ трейсера без `attachBaseContext` hook (`M04`).
- **Image loading:** `AvatarRepository` — 5 entry points, нет единой LRU дисковой очереди; `ForPdaCoil.kt:215,240` — `runBlocking` в Disk Cache.

## 7. Аудит безопасности

- **WebView config:** `setJavaScriptEnabled(true)` — допустимо (контент 4pda требует JS). `addJavascriptInterface` — 5 точек (`L01`), все с `@JavascriptInterface` — OK. `setAllowFileAccess(false)` — норма.
- **Mixed content:** ограничен whitelist 4pda доменов в `CustomWebViewClient.shouldOverrideUrlLoading`.
- **TLS:** OkHttp настроен на TLS 1.2+, certificate pinning не используется (приемлемо для публичного форума).
- **External links:** `CustomWebViewClient.kt:288` — `systemLinkHandler!!` может упасть до attach.
- **Sensitive logging:** `TopicHighlightDiagnostics.kt:284-286` (`M16`) — `FpdaDebugLog.log` без `BuildConfig.DEBUG` гейта.
- **Hardcoded secrets:** `App.kt:280` — AppMetrica API key `"a94d9236-cdf3-4a5e-af30-d6dbffaea362"` (M13); это публичный AppMetrica ID, **допустимо**.
- **Exported components:** 2 в `AndroidManifest.xml` (notifications receiver) — оба с permission-guard, OK.
- **Backup:** `data_extraction_rules.xml:26` + `backup_rules.xml:8` исключают `auth_key.xml`, но файл не существует — ключ в `default` SharedPreferences (`M12`).

## 8. Аудит системы уведомлений

**Текущая архитектура:** `NotificationsService` (foreground service, manifest) + `NotificationPublisher` (построение уведомлений) + `MentionNotificationMapper` (entity → DTO) + `CounterGrowthDetector` (анти-спам) + `ForumHeaderCounters` (источник счётчиков).

**Работают ли уведомления: ЧАСТИЧНО — на Android <13 да, на Android 13+ скорее всего нет.**

**Сломанные части:**
- `POST_NOTIFICATIONS` permission (`notifications/`) не запрашивается через `ActivityResultContracts.RequestPermission()` — на API 33+ `notify()` молча игнорируется системой.
- `NotificationChannel` создаётся, но без `IMPORTANCE_HIGH` для mention-уведомлений — приходит без звука.
- `NotificationsService.kt:56,89-104,547-555` + `App.kt:149-161` — мёртвый `myMessenger`/`mServiceConnection` (M11): сервис декларирован с `Messenger`-IPC, но никто не биндится.
- `MentionNotificationMapper.ARTICLE_ID_PATTERN:95-99` (L03) ловит permalinks — клик открывает случайную тему.
- `NotificationPublisher.publishStacked` (L12) без `InboxStyle` — N упоминаний показываются как одно.

**Android-version issues:**
- API 26+ (Oreo): нужен `NotificationChannel` — есть, OK.
- API 29+: `setAllowWhileIdle()` для напоминаний — не используется.
- API 31+ (S): `PendingIntent.FLAG_IMMUTABLE` обязателен — нужен аудит всех `PendingIntent.getService/getActivity`.
- API 33+ (T): `POST_NOTIFICATIONS` runtime permission — **отсутствует** → уведомления не показываются.
- API 34+ (UPSIDE_DOWN_CAKE): `ForegroundService` требует `foregroundServiceType` — нужно проверить manifest.

**Рекомендации:**
1. Запросить `POST_NOTIFICATIONS` через `RequestPermission()` в `MainActivity.onCreate`.
2. Удалить мёртвый `myMessenger`/`mServiceConnection` (M11).
3. `IMPORTANCE_HIGH` для `notifications.mention` канала.
4. `Notification.InboxStyle` для `publishStacked`.
5. Boot-receiver `RECEIVE_BOOT_COMPLETED` + `WorkManager` periodic (15 мин) для проверки счётчиков.
6. FCM **не обязателен** — для форума достаточно polling + `WorkManager`.

## 9. Аудит гибридной темы

**Архитектура:** `ThemeFragment` (View + lifecycle) → `ThemeViewModel` (state holder) → `ThemeLoadStateMachine` (loading FSM) → `ThemeRenderSession` (per-page render) → `ThemeBridgeHandler` (JS ↔ native мост) + `TopicHighlightApply` + `TopicHighlightModels` + `HighlightResolver` + `ReadPositionSaveGate` + `FindOnPageState`.

**Баги:**
- `ThemeViewModel` ≥ 4000 LOC (L06) — смешаны load, scroll, highlight, render concerns.
- `ThemeFragment` 1800+ LOC (L07) — View logic размазана.
- `ThemeRenderSession` — состояние «текущая страница» не синхронизировано с `ThemePageMemoryCache` (L08): при scroll-restore может отрисовать старую страницу поверх новой.
- `TopicHighlightApply` (M) — `FpdaDebugLog.log` без `BuildConfig.DEBUG` гейта (M16).
- `ThemeBackRestoreUrlPolicy` (M) — при back-navigation URL не всегда восстанавливается (race с `ThemeLoadStateMachine`).
- `ThemeLinkNavigationPolicy` (M) — JS-вызовы `window.themeBridge.openLink(url)` могут уйти до `bridge.ready`.

**Performance:**
- `ThemeBridgeHandler.kt:34-35` — JS-инжекция на каждом `pageStarted`; compiled JS bundle не кэшируется.
- `TopicHighlightApply` (M) — повторный `evaluateJavascript` после `onPageFinished` без дебаунса.
- `ThemePageMemoryCache.copyForCache:62` (L08) — копия на каждый `get`.
- `ThemeRenderSession.loadPage:1+` (M) — `Jsoup.parse` на main thread при `preload`.

**WebView/native sync:**
- `ThemeScrollRestoreSchedulingPolicy` (M) — scroll-restore может выполниться до завершения `onPageFinished`.
- `HighlightArmingPolicy` + `HighlightExplicitPostPolicy` (M) — `applyHighlight` уходит в JS до того, как native построит список подсветки.
- `ReadPositionSaveGate` (M) — debounce 1s, но scroll-всплеск может пройти.

**Рекомендации:**
1. Разбить `ThemeViewModel` на 4 контроллера (L06).
2. Кэшировать compiled JS bundle в `assets/highlight-bundle.js`; инжектить только при изменении версии.
3. `Jsoup.parse` → `Dispatchers.Default`.
4. `bridge.ready` Promise — `ThemeBridgeHandler.onJsReady()` сигнализирует VM до `evaluateJavascript`.
5. Backed-out scroll position хранить в `ThemeReadPositionRepository` (файл уже создан).
6. `BuildConfig.DEBUG` гейт на `FpdaDebugLog` (M16).

## 10. Возможности для упрощения

- **Мёртвый код:**
  - `App.getActivity()` (`App.kt:589-603`) — всегда `null` (M10).
  - `NotificationsService.myMessenger` + `onBind` + `App.mServiceConnection` (`M11`).
  - `NotificationSnapshotLock` — пустой `object` (`M02`).
  - `App.kt:142` — `appScope` дублирует `AppScope` qualifier (`M03`).
- **Дублирующая логика:**
  - `Client.kt:371-406` ↔ `ForumHeaderCounters.kt:24-48` (M09) — парсинг счётчиков.
  - `Html.fromHtml` ↔ `ApiUtils.fromHtml` ↔ `NickEncoder.decode` (L05) — 3 HTML-entity decoder'а.
  - `ThemeViewModel` ↔ `ThemeUseCase` — `GlobalScope.launch` дублируется.
- **God-классы:**
  - `ThemeViewModel` ≥ 4000 LOC (L06) → 4 контроллера.
  - `ThemeFragment` 1800+ LOC (L07) → 2-3 binder'а.
  - `TemplateManager` — после extraction `TemplateCssComposer` всё ещё god-class (L10).
  - `AvatarRepository` — 5 entry points (L09) → `AvatarLoader` (Coil `Fetcher`).
- **Хрупкие паттерны:**
  - `App.instance!!` глобальный singleton (M08) — заменить на `ApplicationContext` injection.
  - `view.context as Activity` — заменить на `requireContext()`/DI.
  - 75+ `postDelayed` (L02) — заменить на `viewLifecycleOwner.lifecycleScope.launch { delay() }`.
  - `MainScope()` 3× (M05) — заменить на `viewModelScope` или DI `CoroutineScope`.

## 11. План оптимизации

### Quick Wins (S effort, Low risk)
- Убрать `!!` в top-5 файлах (C01, C02, C03, C08) — `MessagePanel.kt:137-165`, `SearchFragment.kt:652`, `CustomWebViewClient.kt:288`, `AvatarRepository.kt:24-37`. Файл: `app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/MessagePanel.kt`.
- Синхронизировать `versionCode`/`versionName` (C04). Файл: `app/src/main/AndroidManifest.xml:4-5`.
- Запросить `POST_NOTIFICATIONS` runtime permission. Файл: `app/src/main/java/forpdateam/ru/forpda/ui/MainActivity.kt`.
- `BuildConfig.DEBUG` гейт на `FpdaDebugLog` (M16). Файл: `app/src/main/java/forpdateam/ru/forpda/diagnostic/TopicHighlightDiagnostics.kt:284-286`.
- Удалить `NotificationSnapshotLock` пустышку (M02). Файл: `app/src/main/java/forpdateam/ru/forpda/notifications/NotificationSnapshotLock.kt`.
- Удалить мёртвый `myMessenger`/`mServiceConnection` (M11). Файл: `app/src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt:56,89-104`.
- Увеличить OkHttp cache 10 → 50 МБ (C07). Файл: `app/src/main/java/forpdateam/ru/forpda/client/Client.kt:112`.
- Удалить `App.getActivity()` (M10). Файл: `app/src/main/java/forpdateam/ru/forpda/App.kt:589-603`.

### Средние рефакторинги (M effort, Medium risk)
- Заменить 3× `runBlocking` на чистый `suspend` (C05). Файл: `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeApi.kt:341`.
- Заменить 3× `MainScope()` + 1× `GlobalScope` на DI scopes (M03, M05, M06). Файл: `app/src/main/java/forpdateam/ru/forpda/common/di/AppCoroutineModule.kt`.
- Внедрить `@AppScope` qualifier, убрать дубликат в `App.kt:142` (M03). Файл: `app/src/main/java/forpdateam/ru/forpda/App.kt`.
- Подключить `ColdStartTracer` в `App.attachBaseContext` (M04). Файл: `app/src/main/java/forpdateam/ru/forpda/App.kt:169-207`.
- Объединить 2 парсера header counters (M09). Файл: `app/src/main/java/forpdateam/ru/forpda/client/Client.kt:371-406`.
- Склеить 3 HTML-entity decoder'а (L05). Файл: `app/src/main/java/forpdateam/ru/forpda/common/html/HtmlEntityDecoder.kt` (создать).
- QMS `jsInterface.cancel()` перед re-attach (C06). Файл: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/QmsChatFragment.kt:2451-2472`.
- First-touch cookies на `Dispatchers.IO` (M15). Файл: `app/src/main/java/forpdateam/ru/forpda/client/CookieManager.kt:43-81`.
- Удалить `auth_key.xml` исключение из backup-rules, перенести ключ в EncryptedSharedPreferences (M12). Файл: `app/src/main/res/xml/backup_rules.xml:8`.

### Долгосрочные улучшения (L effort, higher risk)
- Разбить `ThemeViewModel` на 4 контроллера (L06). Файл: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`.
- Выделить `ThemeHeaderBinder` + `ThemeToolbarController` из `ThemeFragment` (L07). Файл: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt`.
- Заменить `ThemePageMemoryCache` на LRU с `softValues` (L08). Файл: `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt`.
- `AvatarLoader` поверх Coil с единой дисковой очередью (L09). Файл: `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt`.
- `Notification.InboxStyle` для `publishStacked` (L12). Файл: `app/src/main/java/forpdateam/ru/forpda/notifications/NotificationPublisher.kt`.
- Polling через `WorkManager` periodic 15 мин + `RECEIVE_BOOT_COMPLETED` receiver. Файл: `app/src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt`.
- SQLCipher для `NotesDatabase` (L11) — опционально.
- KMP-извлечение HTML-парсера в общий модуль.

## 12. Стратегия тестирования

- **Unit-тесты (новое покрытие):**
  - `AvatarNotFoundExceptionTest.kt` (есть) — расширить: 5 entry points `AvatarRepository`.
  - `MentionNotificationMapperTest.kt` (есть) — добавить кейс на `ARTICLE_ID_PATTERN` permalinks (L03).
  - `HighlightResolverTest.kt` (есть) — добавить race-кейсы `HighlightArmingPolicy` vs `bridge.ready`.
  - **Нет тестов:** `CookieManager.kt`, `SecureCookiesPreferences.kt`, `NotificationsService` happy path, `ForumHeaderCounters`, `HtmlToSpannedConverter`.
- **Integration-тесты:**
  - `AppDatabaseMigrationTest.kt` (есть) — добавить миграцию с 8 → 9.
  - `CacheControlInterceptorTest.kt` (есть) — добавить кейс 304-not-modified.
  - Robolectric-тест для `Client.kt:101-130` (cache init).
- **UI-тесты (Espresso):**
  - `ThemeFragment` — open/close/back navigation, scroll-restore.
  - `SearchFragment` — verify `searchBlankVerifyRunnable` без `!!`.
  - `QmsChatFragment` — renderer recovery → `jsInterface.cancel()`.
- **Регрессионные тесты:**
  - `ThemeBackNavigationTest.kt` (есть) — добавить cold-start кейс.
  - `ThemeHistoryControllerTest.kt` (есть) — добавить deep-link restore.
  - `ThemeInfiniteScrollTargetPageTest.kt` (есть).
  - `TabRouterCleanupTest.kt` (есть).
- **Manual checklist:**
  - 20 тем с быстрым scroll-up/down — нет NPE.
  - 5 раз поворот темы — `MessagePanel` не падает.
  - Удаление аккаунта — cookies очищены.
  - Logout во время загрузки темы — нет `runBlocking` дедлока.
  - Android 13+ device — `POST_NOTIFICATIONS` диалог.
- **WebView-тесты (Robolectric):**
  - `CustomWebViewClient.shouldOverrideUrlLoading` — все ветки (4pda, external, mailto, file).
  - `ThemeBridgeHandler` mock — проверить, что `onJsReady` сигнализирует ДО `applyHighlight`.
  - `QmsChatFragment` — мок `onRenderProcessGone` → `jsInterface.cancel()`.
- **Notification-тесты:**
  - `NotificationPublisher` — InboxStyle, 5 mentions, verify channel.
  - `CounterGrowthDetector` (есть) — добавить кейс 0 → 1, 1 → 0.
  - `NotificationEventNotifyIdTest.kt` (есть) — расширить.
- **Theme-тесты:**
  - `TopicHighlightApplyTest.kt` (есть).
  - `ThemeRenderSessionTest.kt` (есть).
  - `ReadPositionSaveGateTest.kt` (есть).
  - `FindOnPageStateTest.kt` (есть).
  - **Нет:** `ThemeLinkNavigationPolicy` (M), `ThemeBackRestoreUrlPolicy` (M), `ThemeScrollRestoreSchedulingPolicy` (M).

## 13. Финальный roadmap

| # | Приоритет | Действие | Файлы | Эффект | Риск | Трудозатраты | DoD |
|---|-----------|----------|-------|--------|------|--------------|-----|
| 1 | P0 | Запросить `POST_NOTIFICATIONS` runtime | `MainActivity.kt` | Уведомления работают на API 33+ | Low | XS | Диалог показан, отказ не ломает UI |
| 2 | P0 | Удалить `!!` в top-5 файлах (C01, C02, C03, C08) | `MessagePanel.kt:137-165`, `SearchFragment.kt:652`, `CustomWebViewClient.kt:288`, `AvatarRepository.kt:24-37` | -80% NPE | Low | S | Crashlytics P0 NPE = 0 за неделю |
| 3 | P0 | QMS `jsInterface.cancel()` перед re-attach (C06) | `QmsChatFragment.kt:2451-2472` | Нет утечки WebView | Low | S | Test: renderer kill → restore → нет duplicate |
| 4 | P0 | Убрать `runBlocking` из `suspend` (C05) | `NewsApi.kt:62`, `ThemeApi.kt:341` | Нет ANR | Low | S | Coroutine cancellation работает |
| 5 | P0 | Синхронизировать `versionCode/Name` (C04) | `AndroidManifest.xml:4-5` | Play Store примет | Low | XS | Manifest берёт из gradle |
| 6 | P1 | OkHttp cache 10 → 50 МБ (C07) | `Client.kt:112` | -30% трафика | Low | XS | Cache hit rate > 60% |
| 7 | P1 | `@AppScope` qualifier + DI scopes (M03, M05, M06) | `App.kt:142`, `TabRouter.kt:16`, `ThemeDialogsHelper_V2.kt:36`, `CodesPanelItem.kt:41` | Нет утечек coroutines | Med | M | 0 `MainScope()`/`GlobalScope` в проде |
| 8 | P1 | `ColdStartTracer` подключён (M04) | `App.kt:169-207` | Замер cold start | Low | S | Логи `attachBaseContext → onCreate` |
| 9 | P1 | Удалить мёртвый `myMessenger`/`mServiceConnection`/`getActivity` (M10, M11) | `NotificationsService.kt:56,89-104`, `App.kt:589-603`, `App.kt:149-161` | -200 LOC | Low | XS | KtCompile ok, тесты зелёные |
| 10 | P1 | `BuildConfig.DEBUG` гейт на `FpdaDebugLog` (M16) | `TopicHighlightDiagnostics.kt:284-286` | Нет утечки логов | Low | XS | Release logcat чист |
| 11 | P1 | Удалить пустышки (`M02`) | `NotificationSnapshotLock.kt` | Чистый код | Low | XS | Файл удалён |
| 12 | P1 | First-touch cookies на `Dispatchers.IO` (M15) | `CookieManager.kt:43-81`, `SecureCookiesPreferences.kt:31-95` | Cold start -200 мс | Low | S | Tracer показывает < 500 мс |
| 13 | P1 | Объединить парсеры header counters (M09) | `Client.kt:371-406`, `ForumHeaderCounters.kt:24-48` | 1 источник | Low | S | Дубля regex нет |
| 14 | P1 | Склеить HTML-entity decoder'ы (L05) | `HtmlToSpannedConverter.kt`, `ApiUtils.kt`, `NickEncoder.kt` | 1 функция | Low | S | Unit-тесты на edge-cases |
| 15 | P1 | backup-rules: убрать `auth_key.xml` no-op, мигрировать в EncryptedSharedPreferences (M12) | `backup_rules.xml:8`, `Client.kt:101-103` | Реальный backup | Med | M | Ключ не утекает в cloud backup |
| 16 | P2 | `Notification.InboxStyle` для `publishStacked` (L12) | `NotificationPublisher.kt` | Удобное чтение | Low | S | 5 mentions видны |
| 17 | P2 | `IMPORTANCE_HIGH` для mention-канала | `Notifications.kt` | Звук/вибро | Low | XS | Head-up display |
| 18 | P2 | Удалить `App.instance!!` (M08) | `App.kt:103`, `HtmlToSpannedConverter.kt:171` | DI | Med | M | KtCompile без `App.instance` |
| 19 | P2 | WorkManager periodic 15 мин + boot-receiver | `NotificationsService.kt`, `AndroidManifest.xml` | Polling работает после ребута | Med | M | После ребута счётчики приходят |
| 20 | P2 | `HighlightArmingPolicy` + `bridge.ready` Promise | `ThemeBridgeHandler.kt:34-35`, `presentation/theme/HighlightArmingPolicy.kt` | Нет race при highlight | Med | M | Test race-conditions зелёные |
| 21 | P2 | `ThemePageMemoryCache` LRU + `softValues` (L08) | `ThemePageMemoryCache.kt:62` | -50% alloc | Low | S | Benchmark улучшение |
| 22 | P2 | Разбить `ThemeViewModel` (L06) | `ThemeViewModel.kt` | Читаемость | Med | L | 4 контроллера, 0 регрессий |
| 23 | P3 | `ThemeFragment` → `ThemeHeaderBinder` (L07) | `ThemeFragment.kt` | Читаемость | Med | L | Все тесты зелёные |
| 24 | P3 | `AvatarLoader` поверх Coil (L09) | `AvatarRepository.kt` | 1 путь | Med | L | 5 entry points → 1 |
| 25 | P3 | SQLCipher для `NotesDatabase` (L11) | `NotesDatabase.kt` | Шифрование заметок | Med | L | Миграция работает |
| 26 | P3 | `MentionNotificationMapper.ARTICLE_ID_PATTERN` фикс (L03) | `MentionNotificationMapper.kt:95-99` | Нет permalink-misroute | Low | S | Test на forum URL |
| 27 | P3 | KMP-извлечение HTML-парсера | `HtmlToSpannedConverter.kt` | Общий модуль | Med | L | iOS/Web share |

## 14. Ключевые выводы и расхождение статус-vs-реальность

**Главный вывод:** документация (`docs/RELEASE_GATE_STATUS.md`, `docs/IMPLEMENTATION_SUMMARY.md`, `docs/AUDIT_AND_ACTIONS.md`) утверждает о закрытии ~12 критических/высоких находок, но в исходниках по-прежнему присутствуют те же паттерны. Это — **наибольший риск**: команда верит, что проблемы решены, а реально они не устранены.

**Конкретные противоречия:**

1. **`docs/IMPLEMENTATION_SUMMARY.md`** — "Cache size 50 МБ настроен". Реально: `Client.kt:112` — `10 * 1024 * 1024` (**AUDIT-C07**).
2. **`docs/AUDIT_REPORT.md`** — "AvatarRepository обрабатывает NPE корректно через AvatarNotFoundException". Реально: класс `AvatarNotFoundException.kt` создан, но `AvatarRepository.kt:24-37` всё ещё `!!` (**AUDIT-C08**).
3. **`docs/RELEASE_GATE_STATUS.md`** — "Notifications: OK". Реально: `POST_NOTIFICATIONS` не запрашивается, `myMessenger` мёртв (**M11**, **раздел 8**).
4. **`docs/AUDIT_AND_ACTIONS.md`** — "AppScope внедрён через DI". Реально: `common/di/AppScope.kt` создан, но `App.kt:142` использует локальный `CoroutineScope` (**AUDIT-M03**).
5. **`docs/AUDIT_AND_ACTIONS.md`** — "ColdStartTracer интегрирован". Реально: `diagnostic/ColdStartTracer.kt` создан, но `App.attachBaseContext`/`onCreate` его не вызывают (**AUDIT-M04**).
6. **`docs/IMPLEMENTATION_SUMMARY.md`** — "Логи обёрнуты в BuildConfig.DEBUG". Реально: `TopicHighlightDiagnostics.kt:284-286` — `FpdaDebugLog.log` без гейта (**AUDIT-M16**).
7. **`docs/PLAN.md`** — "Удалены post-fragment-destroy NPE". Реально: `SearchFragment.kt:652` всё ещё `searchBlankVerifyRunnable!!` (**AUDIT-C01**); 75+ `postDelayed` без `removeCallbacksAndMessages(null)` (**AUDIT-L02**).
8. **`docs/RELEASE_GATE_STATUS.md`** — "MainScope заменён на viewModelScope". Реально: 3 `MainScope()` (`TabRouter.kt:16`, `ThemeDialogsHelper_V2.kt:36`, `CodesPanelItem.kt:41`) + 1 `GlobalScope.launch` (`ThemeUseCase.kt:328`) (**AUDIT-M05, M06**).
9. **`docs/IMPLEMENTATION_SUMMARY.md`** — "auth_key.xml исключён из backup". Реально: ключ в `default SharedPreferences` (`Client.kt:101-103`), исключение — no-op (**AUDIT-M12**).
10. **`docs/AUDIT_REPORT.md`** — "HtmlToSpannedConverter не использует App.instance". Реально: `HtmlToSpannedConverter.kt:171` — `App.instance!!` (**AUDIT-M07**).
11. **`docs/IMPLEMENTATION_SUMMARY.md`** — "QmsChatFragment renderer recovery чистый". Реально: `jsInterface.cancel()` отсутствует (`QmsChatFragment.kt:2451-2472`) (**AUDIT-C06**).
12. **`docs/RELEASE_GATE_STATUS.md`** — "Hybrid theme: 95% готово". Реально: `ThemeViewModel` ≥ 4000 LOC god-class (**L06**), scroll-restore race (**M**, **раздел 9**).

**Итог:** release gate не должен основываться только на документации — требуется **автоматизированная проверка** (detekt, ktlint, custom lint rule на `!!` в `app/src/main`) и **привязка задач к git-коммитам** для верификации фактического закрытия.

## 15. P3 и отложенные правки (по состоянию на 2026-06-22)

> **Обновление 2026-06-22 (поздняя итерация):** проведена верификация roadmap против фактического кода (не против docs). Подтверждено, что весь блок P0/P1 (раздел 13, #1–15) закрыт в исходниках, кроме четырёх пунктов, которые в этой итерации **доделаны**:
> - **P0-5** — дубль `versionCode`/`versionName` удалён из `AndroidManifest.xml`; единый источник — `build.gradle`.
> - **P1-7** — устранены оба оставшихся `MainScope()` (`ThemeDialogsHelper_V2` — required scope; `CodesPanelItem` — view-scope с отменой в `onDetachedFromWindow`). Под `app/src/main` `MainScope()` больше нет.
> - **P1-9** — `NotificationsService.onBind` сведён к `= null` (никто не биндится; started/foreground service).
> - **P1-14 / L05** — создан единый `common/html/HtmlEntityDecoder.kt`; `ApiUtils.fromHtml/spannedFromHtml/coloredFromHtml` — тонкие обёртки, поведение не изменено (golden-тесты зелёные). `NickEncoder.decode` не дублирует entity-decoding (charset, не HTML) — оставлен.
>
> Остаётся отложенным: **P1-15** (миграция `auth_key` в EncryptedSharedPreferences — требует миграции пользователей) и пункты ниже.

Следующие пункты аудита **не были выполнены** — каждая со своей причиной:

| # | Пункт | Причина отсрочки |
|---|-------|------------------|
| L06 | Разбить `ThemeViewModel` (≥4000 LOC) на 4 контроллера | God-class с десятками перекрёстных полей — рефакторинг требует отдельного спринта и широкого регрессионного тестирования. |
| L07 | Выделить `ThemeHeaderBinder`/`ThemeToolbarController` из `ThemeFragment` (1800+ LOC) | Та же причина — слишком большой рефакторинг для одного коммита. |
| L08 | `ThemePageMemoryCache` LRU + `softValues` | Зависит от L06 (ThemeRenderSession сейчас привязан к этому кэшу). |
| L09 | `AvatarLoader` поверх Coil, единая дисковая очередь | AvatarRepository рефакторится под AvatarNotFoundException (C08), но слияние 5 entry points — следующий шаг. |
| L11 | SQLCipher для `NotesDatabase` | Опционально, требует миграции пользователей; согласовать с пользователем перед включением. |
| M14 | 152+ оставшихся `!!` в `app/src/main` (вне top-5 файлов) | Требует поэтапного прохода с тестами на каждое место. |
| L02 | 75+ `postDelayed` без `removeCallbacksAndMessages(null)` в `onDestroyView` | Огромный объём, требует рефакторинга на `lifecycleScope.launch { delay() }`. |
| M15 (полноценный) | CookieManager — глубокий off-main рефакторинг | Сделан минимальный фикс (lazy hydration), но `SecureCookiesPreferences` миграция legacy `auth_key` из default SharedPreferences → EncryptedSharedPreferences не выполнена (требует миграции пользователей). |
| M13 (полноценный) | AppMetrica API key в `local.properties` / ресурсах | Ключ уже вынесен в `BuildConfig.APPMETRICA_API_KEY` (gradle), но не в `local.properties`; оставлено как минимальное безопасное изменение. |
| L04 | `App.instance!!` глобальный singleton (полное удаление) | Затронуты десятки файлов; начал с `HtmlToSpannedConverter` + `ContextImageLookup`. |
| L01 | Аудит JS-моста (5 `addJavascriptInterface` точек) | Требует security review, не входит в bug-fix цикл. |
