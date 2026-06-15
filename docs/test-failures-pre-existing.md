# Pre-existing failing unit tests: разбор

> Разбор выполнен **Mon Jun 15 2026**. Все 22 падения воспроизводятся на чистом `main` (без локальных правок) — подтверждено пользователем до начала анализа. В ходе работы **никаких изменений в коде не делалось**: только чтение тестов, прод-файлов и XML-отчётов из `app/build/test-results/testStableDebugUnitTest`.

---

# Объяснение падающих юнит-тестов (продолжение)

Продолжаю с того места, где остановился. Ниже — финальная сводка по оставшимся классам и итоговая таблица.

---

## 4. `ArticleCommentsValidationTest` — 1 падение

`empty parse with real comment node and positive count is parse failure` — `MockKException: no answer found for NewsApi.fetchCommentsPageSource(url, 1)`.

- **Что проверяет:** что при `commentsCount=12` и пустом `parseCommentsFromSource(...)` интерактор `ArticleInteractor.loadComments()` сам запрашивает страницу через `NewsApi.fetchCommentsPageSource(url, 1)` и при пустом ответе возвращает `Error("comments_page_fetch_empty")`.
- **Что значит ошибка:** MockK-стаб для `NewsApi` не сконфигурирован на `fetchCommentsPageSource(...)` — интерсептор MockK бросает `MockKException`, потому что production-код `ArticleInteractor` вызвал метод, который тест не закрыл стабом. Stack-trace указывает на `NewsApi.kt:173 → NewsRepository.kt:156 → ArticleInteractor.fetchAndParseCommentsPage(ArticleInteractor.kt:1677)`.
- **Серьёзность для приложения:** **косметическая/не критично для рантайма** — это падение только в unit-тесте из-за неполного мока. В реальном приложении `NewsApi.fetchCommentsPageSource` — обычный сетевой вызов, никакого сбоя он не вызовет.
- **Наиболее вероятная причина:** **тестовый баг**. Тест не добавил `every { api.fetchCommentsPageSource(...) } returns ...`. В рантайме код работает штатно, тест просто недостаточно полон.

---

## 5. `FavoritesRepositoryTest` — 1 падение

`refresh with inspector hints replaces rows and keeps one row per favorite` — `AssertionError: expected:<false> but was:<true>` (строка 304).

- **Что проверяет:** что после `loadFavorites(forceRefresh = true)` элемент `topicId == 42` помечен как `isNew = false` (потому что HTML от API говорит, что он прочитан), даже когда inspector-событие пришло с `timeStamp > lastTimeStamp` (как будто есть новое сообщение).
- **Что значит ошибка:** `assertEquals(false, items.single { it.topicId == 42 }.isNew)` упал — реальное значение `true`. То есть `FavoriteReadStateMerge` решил, что **inspector важнее HTML** и поднял `isNew=true`, хотя сам сервер отдал `isNew=false`.
- **Серьёзность для приложения:** **влияет на фичу избранного** — в избранном могут показываться «непрочитанные» бейджи у топиков, которые пользователь реально уже прочитал. Не краш, но раздражающий UI-глюк.
- **Наиболее вероятная причина:** **прод-баг в логике мерджа** `FavoriteReadStateMerge`/`FavoritesRepository`: при свежем HTML-флаге `isNew=false` (значит топик прочитан) inspector-event не должен переопределять это в `isNew=true`. Семантика «HTML wins, если он недавний» нарушена.

---

## 6. `HistoryViewModelTest` — 1 падение (на самом деле весь класс)

`initializationError` — `IncompatibleClassChangeError: class HistoryViewModel overrides final method androidx.lifecycle.ViewModel.clear$lifecycle_viewmodel_release()V`.

- **Что проверяет:** вообще-то ничего — это ошибка **инициализации тест-раннера**, JUnit не может даже создать класс. Падает на `Class.getMethods()`.
- **Что значит ошибка:** `BaseViewModel` наследует `androidx.lifecycle.ViewModel`. В современной версии `androidx.lifecycle:lifecycle-viewmodel` метод `clear()` у `ViewModel` стал `final` (на это указывает суффикс `$lifecycle_viewmodel_release`). `HistoryViewModel` пытается `override fun clear() { ... }` — это и запрещено. Стек показывает: `BaseViewModel` объявил `open fun clear()`, `HistoryViewModel` перекрыл, и JVM-верификатор ругается.
- **Серьёзность для приложения:** **критично потенциально**. Если в release-сборке подключена та же версия `androidx.lifecycle`, JVM запретит subclass-override и **приложение упадёт при первой попытке создать `HistoryViewModel`** (открытие экрана истории). Стоит проверить, что на устройстве — падает или нет.
- **Наиболее вероятная причина:** **прод-баг (API misuse)**. В `BaseViewModel` нужно либо убрать `open fun clear()` и использовать `onCleared()`, либо переименовать метод (например, `disposeUi()`), чтобы не конфликтовать с финальным `ViewModel.clear()`. Скорее всего, проблема возникла после обновления `androidx.lifecycle` — раньше метод был `open`, стал `final`.

---

## 7. `QmsChatViewModelLoadTest` — 1 падение

`bg refresh failure with cache emits LoadWarning with cache age` — `kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test coroutine is not completing, there were active child jobs: [DeferredCoroutine{Active}]`. Длительность 60 030 мс — тест истёк по таймауту.

- **Что проверяет:** когда QMS-чат уже закэширован, а фоновый refresh падает с `NETWORK`, ViewModel должна эмитнуть UI-событие `LoadWarning` с `cacheAgeMinutes`.
- **Что значит ошибка:** после `advanceUntilIdle()` в TestDispatcher осталась **незавершённая** `DeferredCoroutine`. То есть какой-то `async { }` запущен в проде `QmsChatViewModel` и **не отменяется/не завершается** в `onCleared` или в момент, когда тест «замерзает». Похоже, в проде у ViewModel-а есть `async`, который не отменяется на `viewModelScope.cancel()` (или запущен на другом диспатчере, не в `Main`).
- **Серьёзность для приложения:** **утечка горутин в QMS-чате**. В рантайме это проявляется как: (1) зависшие «фантомные» фоновые refresh-и QMS-диалога, (2) `QmsChatMemoryCache` может не очищаться корректно при уходе с экрана. Не приводит к видимому крашу, но жрёт ресурсы и может задерживать выходы/возвраты в чат.
- **Наиболее вероятная причина:** **прод-баг (lifecycle/cancellation)**: в `QmsChatViewModel` есть `async`-таск фоновой перезагрузки, который не отменяется в `clear()`/`onCleared()`. Возможно, `scope` — `GlobalScope` или `SupervisorJob` без `cancel`, либо запуск на `Dispatchers.IO` без явной отмены.

---

## 8. `ThemeTemplateTest` — 5 падений

Тесты на `ThemeTemplate` (рендер HTML-шаблона темы форума). Все 5 падают на одной и той же механике: проверка, что в HTML-выводе присутствуют маркеры `blacklisted_post_placeholder`, `class=\"post_container blacklisted_post\"`, `name=\"entryNNN\"` и т.п. Падают:

- `blacklisted forum post renders compact placeholder without post content`
- `blacklisted posts before visible post render compact placeholder not empty anchor`
- `consecutive blacklisted posts keep separate anchors with hidden content` (expected:<3> but was:<0>)
- `hybrid posts fragment keeps interleaved blacklisted stub order`
- `duplicate post ids in page list render single blacklisted stub` (expected:<1> but was:<0>)

- **Что проверяют:** что посты авторов из blacklist-а пользователя рендерятся как компактный плейсхолдер «Сообщение скрыто» с правильными якорями, а не пустой HTML.
- **Что значит ошибка:** в финальном HTML вообще нет `class="post_container blacklisted_post"` (`countOccurrences(...) == 0`). Это значит, что `ThemeTemplate` **не разворачивает блок `blacklisted_stub_open`** в шаблоне для авторов из blacklist-а. Скорее всего, прод-код либо не считывает `topicPreferencesHolder.getForumBlacklist()` при формировании карты постов, либо не подставляет `blacklisted_post_class` и `blacklisted_stub_open`/`blacklisted_stub_close`-блоки.
- **Серьёзность для приложения:** **влияет на фичу**. Пользователи, у которых настроен blacklist авторов, **увидят сообщения от заблокированных авторов как обычные** (без скрытия/плейсхолдера). Это регрессия в функции сокрытия постов.
- **Наиболее вероятная причина:** **прод-баг в `ThemeTemplate`**: либо обращение к blacklist-у потеряно при рефакторе рендера, либо `MiniTemplator`-блок `blacklisted_stub_open`/`blacklisted_stub_close` не активируется, потому что не выполнено условие в `addBlockOpt`. Тестов с реальным шаблоном нетронутым — скорее всего, прод-логика разошлась с тем, что тесты предполагают.

---

## Сводная картина

| Класс | Падает | Тип ошибки | Серьёзность для приложения | Вероятная причина |
|---|---|---|---|---|
| `QuoteTitleTransformTest` | 1 | `ComparisonFailure` (regex) | Косметика | Тестовый баг (regex слишком строгая) |
| `ArticleParserImageTest` | 4 | `AssertionError` на HTML | Фича (рендер статей) | Прод-баг `ArticleParser` |
| `NewsApiCommentActionsTest` | 8 | `NoSuchElementException` (пустой children) | Фича (модерация/лайки комментариев) | Прод-баг: десктопный HTML не мёржится в `commentsSource` |
| `ArticleCommentsValidationTest` | 1 | `MockKException` (нет стаба) | Нет влияния на рантайм | Тестовый баг (неполный мок) |
| `FavoritesRepositoryTest` | 1 | `AssertionError` на `isNew` | Фича (бейджи непрочитанного в избранном) | Прод-баг: `FavoriteReadStateMerge` отдаёт приоритет inspector-у вместо HTML |
| `HistoryViewModelTest` | 1 (весь класс) | `IncompatibleClassChangeError` (`clear()` final) | **Потенциальный краш** при открытии экрана истории | Прод-баг: override финального `ViewModel.clear()` |
| `QmsChatViewModelLoadTest` | 1 | `UncompletedCoroutinesError` (60s таймаут) | Утечка горутин в QMS | Прод-баг: `async` не отменяется в `clear()`/`onCleared()` |
| `ThemeTemplateTest` | 5 | `AssertionError` (нет `blacklisted_post`) | Фича (сокрытие постов blacklist) | Прод-баг в `ThemeTemplate`: не активируется `blacklisted_stub` |

---

## Итог

- **Потенциально опасно для рантайма (1):** `HistoryViewModelTest` — `IncompatibleClassChangeError`. Если в прод-сборке используется та же версия `androidx.lifecycle`, экран истории может крашить приложение. Стоит проверить вручную на устройстве.
- **Реальные прод-баги, влияющие на функциональность (4 класса):** `ArticleParserImageTest`, `NewsApiCommentActionsTest`, `FavoritesRepositoryTest`, `ThemeTemplateTest`. Это не падения, а несоответствия логики рендера/мерджа. Пользователи увидят: артефакты в статьях, пропавшие кнопки модерации, лишние «непрочитанные» бейджи, видимые посты из blacklist.
- **Утечка горутин (1):** `QmsChatViewModelLoadTest` — не виден пользователю напрямую, но утечка ресурсов в QMS-чате.
- **Тестовые баги (2):** `QuoteTitleTransformTest`, `ArticleCommentsValidationTest` — падают из-за неполных/слишком строгих тестов, прод-код не затронут.
