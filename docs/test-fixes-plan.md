# План пошагового исправления падающих unit-тестов (ForPDA / ProPDA)

> Документ предназначен для AI-агента-исполнителя, который будет применять
> исправления по одному. Каждый пункт самодостаточен: указаны точные файлы,
> строки, символы, что менять, чего **не трогать**, как проверить и как
> откатить. Читай каждый шаг буквально и не выходи за его границы.

## Контекст и текущий статус

- Команда полного прогона юнит-тестов:
  ```
  ./gradlew :app:testStableDebugUnitTest --continue
  ```
- Текущее состояние: **1609 тестов, 21 падение в 7 классах**.
- Две правки уже внесены ранее и **в этот список НЕ входят** (упоминаются
  только для контекста — не повторяй и не откатывай их):
  1. Добавлены строковые ресурсы `app_update_*`.
  2. Метод `BaseViewModel.clear()` переименован в `onUserClear()`. Эта правка
     уже починила `HistoryViewModelTest`, поэтому его нет в списке ниже.
- Все 7 классов из этого документа проверены чтением реального кода и узкими
  прогонами тестов. Где исходная диагностика была неточной — это явно отмечено
  в разделе «Первопричина».

### Базовые правила для исполнителя (действуют ВЕЗДЕ)

- Меняй только то, что предписано в конкретном шаге. Никаких «попутных»
  улучшений, переформатирований, переименований.
- Для «только-тестовых» фиксов (пункты 1, 2, 3, 5) **категорически запрещено**
  трогать любой production-код (`src/main/...`). Меняется только файл теста.
- Для production-фиксов (пункты 4, 6) делай **минимальное** изменение,
  удовлетворяющее задокументированному контракту тестов. Не переписывай парсер
  (`ArticleParser.kt` — ~4000 строк) и не рефактори соседние методы.
- Пункт 7 — продуктовое решение: **НЕ меняй код**, пока пользователь не выбрал
  политику. Сначала задай вопрос.
- После каждого фикса прогоняй узкую команду из подпункта «(f) Проверка» и
  убеждайся, что именно этот класс зелёный, прежде чем переходить к следующему.

## Оглавление

- [Рекомендуемый порядок выполнения и риски](#рекомендуемый-порядок-выполнения-и-риски)
- [Пункт 1 — QuoteTitleTransformTest (тест) — риск: низкий](#пункт-1--quotetitletransformtest)
- [Пункт 2 — ArticleCommentsValidationTest (тест) — риск: низкий](#пункт-2--articlecommentsvalidationtest)
- [Пункт 3 — QmsChatViewModelLoadTest (тест) — риск: низкий](#пункт-3--qmschatviewmodelloadtest)
- [Пункт 4 — NewsApiCommentActionsTest (production) — риск: высокий](#пункт-4--newsapicommentactionstest)
- [Пункт 5 — ThemeTemplateTest (тест) — риск: средний](#пункт-5--themetemplatetest)
- [Пункт 6 — ArticleParserImageTest (production) — риск: высокий](#пункт-6--articleparserimagetest)
- [Пункт 7 — FavoritesRepositoryTest (продуктовое решение) — риск: средний](#пункт-7--favoritesrepositorytest)
- [Definition of done](#definition-of-done)

## Рекомендуемый порядок выполнения и риски

Выполняй строго в этом порядке:

| № | Класс | Тип | Риск | Почему такой порядок |
|---|-------|-----|------|----------------------|
| 1 | `QuoteTitleTransformTest` | только тест | низкий | Тривиальная правка regex в тесте |
| 2 | `ArticleCommentsValidationTest` | только тест | низкий | Добавить один мок |
| 3 | `QmsChatViewModelLoadTest` | только тест | низкий | Чинит зависание и **экономит ~60 c** в CI |
| 4 | `NewsApiCommentActionsTest` (8) | production | высокий | Реальный баг парса/мерджа комментариев |
| 5 | `ThemeTemplateTest` (5) | только тест | средний | Ослабить слишком буквальные ассерты |
| 6 | `ArticleParserImageTest` (4) | production | высокий | Реальные регрессии рендера статьи |
| 7 | `FavoritesRepositoryTest` (1) | решение | средний | Требует выбора политики пользователем |

Логика порядка: сначала три «только-тестовых» фикса (1, 2, 3) — самый низкий
риск, причём пункт 3 убирает 60-секундное зависание прогона. Затем реальные
production-баги (4, 6) и тестовый пункт 5. В самом конце — пункт 7, который
требует решения человека.

## Пункт 1 — QuoteTitleTransformTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/common/QuoteTitleTransformTest.kt`
Тип: **только тест**. Риск: низкий.

### (a) Симптом + точное сообщение об ошибке
- Падает метод `returnsEmptyDateWhenOnlyNickPresent` (строки 37–42).
- Сообщение: `org.junit.ComparisonFailure: expected:<user> but was:<>`.
- Ассерт `assertEquals("user", nick)` на строке 40 получает пустой `nick`.

### (b) Файлы и строки
- `QuoteTitleTransformTest.kt:12-13` — поле `titleRegexp`.
- `QuoteTitleTransformTest.kt:15-21` — функция `parseQuoteTitle`.
- `QuoteTitleTransformTest.kt:39` — вход `"user @ "`.

### (c) Первопричина (проверено)
Тест — это «зеркало» JS-логики `blocks.js → transformQuotes()`. Собственная
mirror-регулярка теста требует обязательный разделитель `\s@\s` (пробел–`@`–
пробел). Вход `"user @ "` нормализуется в `parseQuoteTitle` строкой
`text.replace(Regex("""\s+"""), " ").trim()` → `"user @"` (хвостовой пробел
срезается `trim()`). После этого паттерн `([\s\S]*?)\s@\s(...)?` уже не
находит `@` с двух сторон в окружении пробелов, `find` возвращает `null`
(строка 17), и функция отдаёт `"" to null` → `nick` пустой.
Реальный prod-контракт (JS) допускает «висящий» `@` без даты: ник = `"user"`,
дата = пусто. Значит ошибка в mirror-регулярке/нормализации самого теста, а не
в продукте.

### (d) Пошаговые действия (что именно изменить)
Цель: при входе `"user @ "` функция должна вернуть `nick = "user"`, `date = null`,
не сломав два уже зелёных кейса (`"@antisk115 @ 12.06.26, 19:40"` и
`"antisk115 @ 12.06.26, 19:40"`).

Сделай разделитель `@` устойчивым к отсутствию пробела/даты справа. Замени
строку 13 (тело `titleRegexp`) так, чтобы пробелы вокруг `@` были
необязательными, а блок даты оставался опциональным:

```kotlin
private val titleRegexp =
        Regex("""([\s\S]*?)\s*@\s*((?:\d+\.\d+\.\d+|[\wа-яА-ЯёЁ][\wа-яА-ЯёЁ._-]*)(?:,\s*\d+:\d+)?)?""")
```

Если после этой правки `nick` для входа `"user @ "` приходит как `"user "`
(с хвостовым пробелом), дополнительно убедись, что в строке 18 ник
`.trim()`-ится (он уже `.trim()`-ится в текущем коде). Этого достаточно.

Никаких других изменений в файле не делай.

### (e) ЧТО НЕ ТРОГАТЬ
- Не трогай НИКАКОЙ production-файл (это контрактный тест, зеркало JS).
- Не меняй методы `stripsLeadingAtFromNickAfterSnapbackIcon` и
  `parsesPlainNickWithoutSnapbackPrefix` — они должны остаться зелёными.
- Не меняй сигнатуру `parseQuoteTitle` и тип возврата `Pair<String, String?>`.

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.common.QuoteTitleTransformTest"
```
Ожидание: 3 теста, 0 падений.

### (g) Откат
Верни строку 13 к исходному значению:
```kotlin
Regex("""([\s\S]*?)\s@\s((?:\d+\.\d+\.\d+|[\wа-яА-ЯёЁ][\wа-яА-ЯёЁ._-]*)(?:,\s*\d+:\d+)?)?""")
```

## Пункт 2 — ArticleCommentsValidationTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/model/interactors/news/ArticleCommentsValidationTest.kt`
Тип: **только тест**. Риск: низкий.

### (a) Симптом + точное сообщение об ошибке
- Падает ровно один метод:
  `` `empty parse with real comment node and positive count is parse failure` ``
  (строки 53–103).
- Сообщение вида:
  `io.mockk.MockKException: no answer found for: NewsApi(#…).fetchCommentsPageSource(…, 1)`.

### (b) Файлы и строки
- Блок мока этого теста: `ArticleCommentsValidationTest.kt:73-86` — создаётся
  `val api = mockk<NewsApi> { … }` **без** `relaxed = true`.
- Вызов, который падает: prod дергает `api.fetchCommentsPageSource(url, 1)`
  на fallback-пути «пустой парс при положительном счётчике».

### (c) Первопричина (проверено)
Это единственный тест в классе, где `NewsApi` мокается **строгим** способом
`mockk<NewsApi> { … }` (строка 73) — без `relaxed = true`. Все остальные тесты
класса используют `mockk<NewsApi>(relaxed = true)` и поэтому не падают на
незамоканных вызовах. Production-логика на пути «есть узлы-комментарии, но парс
дал пустое дерево при `commentsCount=12`» легитимно вызывает
`fetchCommentsPageSource(url, 1)` как fallback. В строгом моке для этого вызова
ответа нет → `MockKException`.

Сигнатура метода (см. `NewsApi.kt:164`):
`fun fetchCommentsPageSource(articleUrl: String, commentPage: Int): String?`
— возвращает **`String?`** (nullable). В этом тесте достаточно вернуть `null`,
чтобы fallback-fetch «не нашёл» страницу и итог остался
`CommentLoadResult.Error("comments_page_fetch_empty")`, как и проверяет тест
(строки 98–102).

### (d) Пошаговые действия (что именно изменить)
В блоке мока этого теста добавь ОДНУ строку в стиле остальных стабов (`every`,
т.к. метод не `suspend` и в этом классе используется именно `every`).
Добавь её сразу после строки 85
(`every { parseCommentsFromSource(any(), any(), paginated = true) } returns emptyTree`),
внутри того же блока `mockk<NewsApi> { … }`:

```kotlin
every { fetchCommentsPageSource(any(), any()) } returns null
```

Обоснование выбора `every` (а не `coEvery`): в этом классе остальные стабы для
`fetchCommentsPageSource` оформлены через `every { … }` (например строки 140,
189, 245, 998). Метод не помечен `suspend`, поэтому `every` корректен.

Обоснование `returns null`: тест ожидает финальный
`CommentLoadResult.Error` с сообщением `comments_page_fetch_empty`. `null`
сохраняет этот исход. Не возвращай непустой HTML — это изменит ветку и сломает
ассерт.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй production-код (`NewsApi.kt`, `ArticleInteractor`, и т. д.).
- Не превращай `mockk<NewsApi>` в `relaxed = true` — это замаскирует другие
  возможные пропуски и изменит поведение теста.
- Не трогай остальные стабы и ассерты этого метода.

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.model.interactors.news.ArticleCommentsValidationTest"
```
Ожидание: все тесты класса зелёные, в т. ч. упавший метод.

### (g) Откат
Удали добавленную строку
`every { fetchCommentsPageSource(any(), any()) } returns null`.

## Пункт 3 — QmsChatViewModelLoadTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/presentation/qms/chat/QmsChatViewModelLoadTest.kt`
Production-файл для контрольного чтения:
`app/src/main/java/forpdateam/ru/forpda/presentation/qms/chat/QmsChatViewModel.kt`
Тип: **только тест** (основная правка). Риск: низкий. Экономит ~60 c прогона.

### (a) Симптом + точное сообщение об ошибке
- Падает метод:
  `` `bg refresh failure with cache emits LoadWarning with cache age` `` (строки 342–368).
- Ошибка: `kotlinx.coroutines.test.UncompletedCoroutinesError` после ~60 c
  ожидания: «… after waiting for 60000 ms … there were active child jobs:
  [DeferredCoroutine{Active}]».
- Этот один тест «съедает» ~60 c всего прогона.

### (b) Файлы и строки
- `QmsChatViewModelLoadTest.kt:343` — объявление теста `= runTest {` (важно:
  **без** аргумента `dispatcher`, в отличие от остальных тестов класса,
  которые пишут `runTest(dispatcher)`).
- `QmsChatViewModelLoadTest.kt:358-360` — проблемный блок:
  ```kotlin
  val events = async {
      vm.uiEvents.take(6).toList()
  }
  ```
- `QmsChatViewModelLoadTest.kt:364` — фактически проверяется только **один**
  `LoadWarning`: `…filterIsInstance<QmsChatUiEvent.LoadWarning>().single()`.

### (c) Первопричина (проверено)
`async { vm.uiEvents.take(6).toList() }` ждёт **6** событий. На сценарии
«кэш-хит + фейл фонового обновления» ViewModel эмитит меньше 6 событий, поэтому
`take(6)` никогда не завершается, и `DeferredCoroutine` остаётся `Active` —
`runTest` не может «осесть» и падает таймаутом 60 c.

Подтверждение по prod-коду `QmsChatViewModel.kt` (этот тест вызывает
`onChatIdentityChanged()` напрямую, НЕ `start()`, поэтому событий
font/style/title нет):
- путь кэш-фоллбэка `applyLoadFailure` эмитит `ShowChat` (строка 568),
  `emitInitialMessages` → `ResetAndShowMessages` (строки 195–201) и `LoadWarning`
  (строка 570);
- затем фейл фонового обновления (`Failure`) эмитит ещё один `LoadWarning`
  (строки 500–505).
Итого ~4 события, а тест ждёт 6 → `take(6)` зависает.

Также проверено, что это **не** реальная утечка корутины в продукте:
- ViewModel использует `scope` из `BaseViewModel` (`BaseViewModel.kt:44`),
  отменяемый в `onUserClear()`;
- `QmsChatViewModel.onCleared()` (`QmsChatViewModel.kt:155-157`) корректен.
Зависает именно тестовый `async`, а не prod-корутина. Поэтому фикс —
**только в тесте**, опциональной prod-правки не требуется.

### (d) Пошаговые действия (что именно изменить)
Сделай так, чтобы собиралось ровно столько событий, сколько реально эмитится,
и чтобы тестовая корутина гарантированно завершалась.

Вариант (предпочтительный): собирать события без фиксированного `take(6)`, а
ограниченно по времени/планировщику, и затем извлекать единственный
`LoadWarning`. Замени блок строк 358–367 на:

```kotlin
        val collected = mutableListOf<QmsChatUiEvent>()
        val events = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiEvents.toList(collected)
        }
        vm.onChatIdentityChanged()
        advanceUntilIdle()
        events.cancel()

        val warning = collected.filterIsInstance<QmsChatUiEvent.LoadWarning>().single()
```

Добавь нужные импорты в начало файла (если их ещё нет):
```kotlin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.toList
```
(`kotlinx.coroutines.flow.toList` уже импортирован — строка 27; `backgroundScope`
и `testScheduler` доступны из `runTest`-скоупа; `UnconfinedTestDispatcher` нужно
добавить.)

Запасной вариант (если не хочешь менять способ сбора): просто замени `take(6)`
на `take(4)`. Перед фиксацией такого варианта обязательно убедись локальным
прогоном, что реально эмитится именно 4 события на этом сценарии (см. (f)); если
число иное — поставь ровно его. Этот вариант хрупкий, поэтому предпочтителен
первый.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй `QmsChatViewModel.kt` и другой production-код: реальной утечки нет.
- Не меняй остальные тесты класса (они используют `runTest(dispatcher)` и
  зелёные).
- Не увеличивай таймаут `runTest` — это не лечит причину, а лишь маскирует.

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.presentation.qms.chat.QmsChatViewModelLoadTest"
```
Ожидание: все тесты класса зелёные, прогон класса завершается за единицы секунд
(без 60-секундного зависания).

### (g) Откат
Верни блок строк 358–367 к исходному:
```kotlin
        val events = async {
            vm.uiEvents.take(6).toList()
        }
        vm.onChatIdentityChanged()
        advanceUntilIdle()

        val warning = events.await().filterIsInstance<QmsChatUiEvent.LoadWarning>().single()
```
и убери лишние импорты.

## Пункт 4 — NewsApiCommentActionsTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApiCommentActionsTest.kt`
Production-файлы:
`app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt`,
`app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParser.kt`
Тип: **PRODUCTION-баг**. Риск: высокий.

### (a) Симптом + точное сообщение об ошибке
Падает **8** методов. У всех корень — пустые `children` у дерева комментариев:
- `java.util.NoSuchElementException: List is empty.` — там, где вызывается
  `.children.single()`.
- `java.util.NoSuchElementException: Collection contains no element matching the predicate.`
  — там, где вызывается `.children.first { it.id == 10 }`.

Список 8 падающих методов (имена точные, прогон подтверждён):
1. `getDetails_whenAuthorizedAndMobileLacksRep_fetchesDesktopCommentsForReputationActions` (`.children.single()`, строка 291)
2. `getDetails_whenAuthorizedAndDesktopHasOwnEdit_mergesEditAction` (строка 323)
3. `getDetails_whenMobileHasRepButNoOwnEdit_stillFetchesDesktopComments` (строка 379)
4. `parseComments_whenOwnCommentMissingEdit_appliesFallbackEditAndDelete` (строка 402)
5. `getDetails_whenRuntimeLoadHasNoMobileAuthorId_butDesktopHasEdit_exposesEditToUiDecision` (строка 492)
6. `getDetails_whenOtherCommentHasEdit_butOwnMissing_stillProbesDesktopOrUsesFallback` (`.first { it.id == 10 }`)
7. `getDetails_whenOtherCommentHasEdit_butOwnMissing_stillProbesDesktopOrAppliesFallback` (`.first { it.id == 10 }`)
8. `getDetails_whenMobileHasBareOwnEditLink_stillProbesDesktopComments` (строка 669)

### (b) Файлы и строки
- Тест-хелпер `loadDetailsWithDesktopComments`: `NewsApiCommentActionsTest.kt:81-84`
  — делает `fetchArticleDetails(url)` затем `enrichDesktopExtras(fetch)`.
- HTML-фикстура `detailsHtml(commentHtml)`: `NewsApiCommentActionsTest.kt:917-925`
  — комментарии заданы как
  `<ul class="comment-list"><li><div id="comment-N" class="comment"> … </div></li></ul>`.
- Provider стаб `DetailsPatternProviderStub`: `NewsApiCommentActionsTest.kt:86-108`
  (используется во всех 8 падающих тестах).
- Production-вход парса дерева: `NewsApi.parseComments(article, …)` —
  `NewsApi.kt:1142` (ветка non-paginated: строки 1182–1214).
- Базовый парс источника: `ArticleParser.parseComments(karmaMap, source)` —
  `ArticleParser.kt:2273-2299`; tag-fallback `parseCommentsFromTags`
  вызывается на `ArticleParser.kt:2293`.
- Заполнение `commentsSource` при разборе статьи: `ArticleParser.parseArticle`
  — `ArticleParser.kt:507`; извлечение источника комментариев из страницы:
  `extractCommentsSourceFromPage` — `ArticleParser.kt:743`.

### (c) Первопричина (что проверено и где исходная диагностика уточнена)
Исходная диагностика указывала на «merge desktop-комментариев» как корень.
**Уточнение по факту чтения и прогонов:** падает в т. ч. метод
`parseComments_whenOwnCommentMissingEdit_appliesFallbackEditAndDelete` (строка
402), который **не использует desktop-источник вообще** (передаётся только
mobile-HTML, см. строки 388–402). Раз дерево пустое и в чисто mobile-кейсе —
проблема **до** этапа desktop-merge: либо `commentsSource` не извлекается из
mobile-HTML в `parseArticle`, либо базовый разбор узлов
`<li><div id="comment-N" class="comment">` в `ArticleParser.parseComments` /
`parseCommentsFromTags` перестал давать узлы для именно такой разметки.

То есть корень — в **базовом извлечении/парсе комментариев**, а desktop-merge
лишь наследует пустое дерево. Это снижает область правки: чинить нужно путь
`parseArticle → commentsSource` и/или `parseComments(karmaMap, source)`,
а НЕ логику merge.

### (d) Пошаговые действия — методика расследования (НЕ слепой патч)
Действуй по шагам и подтверждай гипотезу логами/инспекцией перед правкой.

Шаг 1. Зафиксируй контракт по тестам. Прочитай 8 методов и выпиши ожидаемое:
- ожидается ровно один корневой комментарий с `id` из фикстуры
  (`comment-10`/`comment-11`), у которого после merge есть нужные `actions`
  (`reputationPlus/reputationMinus`, `edit`, `delete`), либо ровно один узел
  `id == 10`. Действия (reputation/edit/delete) — вторичны; первично, чтобы
  `children` были непусты.

Шаг 2. Локализуй точку обнуления. Временно (только для диагностики, потом
убрать) в тесте `parseComments_whenOwnCommentMissingEdit_appliesFallbackEditAndDelete`
после строки 401 (`val article = api.loadDetailsWithDesktopComments(456)`)
проверь в отладке/через временный `println`:
- `article.commentsSource` — пуст он или содержит `<li><div id="comment-10"…`?
  * Если **пуст** → корень в `parseArticle`/`extractCommentsSourceFromPage`
    (`ArticleParser.kt:507`, `:743`): mobile-HTML из `detailsHtml` (строки
    917–925) не распознаётся как страница с комментариями, источник не
    извлекается. Тогда правка — в извлечении `commentsSource`.
  * Если **НЕ пуст** → корень в `ArticleParser.parseComments(karmaMap, source)`
    (`:2273`): DOM-walk `recurseComments` и tag-fallback `parseCommentsFromTags`
    не распознают узел `<li><div id="comment-N" class="comment">`. Тогда правка —
    в распознавании узла комментария.

Шаг 3. Подтверди на уровне парсера. Если источник не пуст, вызови напрямую
`ArticleParser(DetailsPatternProviderStub()).parseComments(SparseArray(), article.commentsSource)`
и посмотри `.children.size`. Сравни с `hasCommentMarkup(source)`
(`ArticleParser.kt:2356`) — если `hasCommentMarkup` = true, а `children` пусты,
сработает ветка `parse_root_missing` (`:2282`) и tag-fallback; проверь, почему
`parseCommentsFromTags` (`:2293`) не извлекает узлы из данной разметки.

Шаг 4. Сделай **минимальную** правку под найденную причину:
- Если не извлекается `commentsSource`: поправь распознавание блока
  комментариев в `extractCommentsSourceFromPage` / соответствующем месте
  `parseArticle`, чтобы `<ul class="comment-list">…<li><div id="comment-N"
  class="comment">` из mobile-HTML давал непустой `commentsSource`.
- Если не парсятся узлы: поправь условие распознавания узла комментария
  (id-паттерн `comment-(\d+)` и класс `comment`) в `parseComments` /
  `parseCommentsFromTags`, чтобы такая разметка снова давала узлы.
В обоих случаях правка должна быть точечной (одно условие/regex), а не
переписыванием метода.

Шаг 5. Убери временный диагностический код из теста. **Тест не модифицируется**
в финале — это production-баг.

### (e) ЧТО НЕ ТРОГАТЬ
- **Не переписывай** `ArticleParser.kt` целиком и не рефактори соседние методы
  парса — только минимальное условие, восстанавливающее распознавание.
- Не меняй сам тест и его фикстуры (`detailsHtml`, `DetailsPatternProviderStub`):
  это контракт, баг в продукте.
- Не трогай логику `mergeCommentDesktopActions` / desktop-merge, если Шаг 2
  показал, что дерево пусто уже на mobile-этапе (а это так для метода №4).
- Не меняй пагинационные методы (`parseCommentsFromSource`, `parseCommentsBatch`)
  — соответствующие тесты пагинации зелёные, их поведение ломать нельзя.

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.model.data.remote.api.news.NewsApiCommentActionsTest"
```
Ожидание: 30 тестов, 0 падений (было 8 падений). Дополнительно прогони
смежные классы, чтобы не словить регресс парса:
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.model.data.remote.api.news.ArticleParserImageTest" --tests "forpdateam.ru.forpda.model.interactors.news.ArticleCommentsValidationTest"
```

### (g) Откат
Через git верни изменённые строки `NewsApi.kt`/`ArticleParser.kt` к исходному
состоянию (`git checkout -- <файл>` или ручной revert точечной правки). Тест в
этом пункте не менялся, откатывать в нём нечего.

## Пункт 5 — ThemeTemplateTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplateTest.kt`
Production-файлы для чтения (НЕ для правки по умолчанию):
`app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt`,
`app/src/main/assets/template_theme.html`
Тип: **только тест** (по итогам дизамбигуации — см. (c)). Риск: средний.

### (a) Симптом + точное сообщение об ошибке
Падают **5** «blacklist»-тестов. Сообщения вида
`java.lang.AssertionError: expected:<3> but was:<0>` и
`expected:<1> but was:<0>` — то есть подсчёт `countOccurrences(...)` для
подстроки blacklist-разметки даёт 0.

Затронутые тесты (используют `realTopicTemplate()`):
1. `blacklisted forum post renders compact placeholder without post content` (строки 124–153) — ассерт строки 140.
2. `blacklisted posts before visible post render compact placeholder not empty anchor` (строки 323–351).
3. `consecutive blacklisted posts keep separate anchors with hidden content` (строки 353–377) — ассерты строк 372–373.
4. `interleaved blacklisted and visible posts preserve publication order in html` (строки 379–440).
5. `duplicate post ids in page list render single blacklisted stub` (строки 541–570) — ассерт строки 567.

### (b) Файлы и строки
- Буквальные ассерты в тесте:
  - `class="post_container blacklisted_post"` — строки 140, 320, 372, 483, 535, 536, 567.
  - `class="blacklisted_post_content" aria-hidden="true" hidden` — строки 144, 349, 373.
- Реальный шаблон `template_theme.html:317`:
  ```html
  <div name="entry${post_id}" class="post_container ${blacklisted_post_class} ${user_online} ${hat_state_class}" …>
  ```
- Блок stub в шаблоне: `template_theme.html:318-321` (там `blacklisted_post_content`
  присутствует ровно как в ассерте).
- Production-рендер blacklist: `ThemeTemplate.kt:265-298`. Ключевое:
  - `ThemeTemplate.kt:275` — `setVariableOpt("blacklisted_post_class", if (isBlacklisted) "blacklisted_post" else "")`;
  - `ThemeTemplate.kt:277-287` — добавляются блоки `blacklisted_stub_open`,
    `blacklisted_post_body`, `blacklisted_post_footer`, `blacklisted_stub_close`;
  - `ThemeTemplate.kt:263` — `setVariableOpt("hat_state_class", "")` (пусто);
  - `user_online` для обычного поста тоже пусто.

### (c) Первопричина — дизамбигуация (ОБЯЗАТЕЛЬНО прочитать перед правкой)
Ранее были две версии: (1) blacklist-рендер в продукте сломан; (2) ассерты
теста слишком буквальны. **Проверено по коду — верна версия (2): фича работает,
а тесты слишком буквальны.**

Доказательство:
- `ThemeTemplate.kt:275` реально выставляет `blacklisted_post_class =
  "blacklisted_post"`, и stub-блоки реально добавляются (строки 277–287).
- Но в шаблоне (`template_theme.html:317`) класс собирается как
  `class="post_container ${blacklisted_post_class} ${user_online} ${hat_state_class}"`.
  При непустом `blacklisted_post_class` и пустых `user_online`/`hat_state_class`
  результат в рантайме —
  `class="post_container blacklisted_post  "` (две лишние позиции/пробелы перед
  закрывающей кавычкой), а **не** `class="post_container blacklisted_post"`.
- Поэтому буквальная подстрока с закрывающей кавычкой сразу после
  `blacklisted_post` не находится → `countOccurrences == 0` → `expected:<3>
  but was:<0>`.
- При этом подстрока `class="blacklisted_post_content" aria-hidden="true" hidden`
  в шаблоне присутствует точно (`template_theme.html:320`), но тесты, где она
  стоит рядом с подсчётом `post_container blacklisted_post`, всё равно падают на
  первой непройденной ассерции.

Вывод: **branch (b)** — чинить ТЕСТ, не `ThemeTemplate.kt`. Сделать сопоставление
токенным/регэксп-устойчивым к лишним пробелам и доп. классам — как уже сделано в
зелёных `realTopicTemplate()`-тестах (например, `orderedPostIdsInPostsList`,
строки 1209–1215, использует regex по наличию класса `post_container`, а не
точную подстроку).

### (d) Пошаговые действия (что именно изменить — только в тесте)
Цель: заменить буквальные `contains`/`countOccurrences` по
`class="post_container blacklisted_post"` на токенную проверку наличия классов
`post_container` И `blacklisted_post` в одном теге `<div … class="…">`,
устойчивую к доп. классам и пробелам.

Шаг 1. Добавь в класс теста private-хелпер (рядом с `countOccurrences`,
после строки 1199), считающий теги поста с обоими классами:
```kotlin
    private fun String.countBlacklistedPostContainers(): Int {
        val pattern = Regex(
                """<div\b[^>]*\bclass="[^"]*\bpost_container\b[^"]*\bblacklisted_post\b[^"]*"[^>]*>""",
                RegexOption.IGNORE_CASE,
        )
        return pattern.findAll(this).count()
    }
```
Если в тесте нужно проверить конкретный `data-post-id`, используй вариант с
группой по id (как в `orderedPostIdsInPostsList`).

Шаг 2. Замени буквальные ассерты на токенные:
- `assertTrue(html.contains("class=\"post_container blacklisted_post\""))` →
  `assertTrue(html.countBlacklistedPostContainers() >= 1)`.
- `assertEquals(3, html.countOccurrences("class=\"post_container blacklisted_post\""))` →
  `assertEquals(3, html.countBlacklistedPostContainers())`.
- Для ассертов с `data-post-id` (строки 483, 535, 536) сделай аналогичный regex,
  дополнительно требующий `data-post-id="<нужный>"` в том же теге, и сравнивай
  счётчик/наличие.

Шаг 3. Ассерты по `class="blacklisted_post_content" aria-hidden="true" hidden`
**оставь как есть** — эта подстрока в реальном шаблоне присутствует буквально
(`template_theme.html:320`); они падали только из-за более ранней непройденной
ассерции в том же тесте.

Шаг 4. Прочее в этих тестах (`name="entryNNN"`, `toggleBlacklistedPost('NNN'`,
порядок постов через `assertPostsListOrder`, наличие `Hidden body`) — не трогай.

### (e) ЧТО НЕ ТРОГАТЬ
- **НЕ меняй `ThemeTemplate.kt`** — фича работает корректно. Любая правка
  рендера здесь сломает реальное поведение и/или другие зелёные тесты.
- **НЕ меняй `template_theme.html`** — порядок классов
  `${blacklisted_post_class} ${user_online} ${hat_state_class}` намеренный.
- Не ослабляй ассерты `blacklisted_post_content` и порядок постов.
- Не трогай не-blacklist тесты класса (rating buttons, top hat, poll, density,
  post count) — они зелёные.

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.presentation.theme.ThemeTemplateTest"
```
Ожидание: все тесты класса зелёные (5 ранее падавших — тоже).

### (g) Откат
Удали добавленный хелпер `countBlacklistedPostContainers` и верни 5
изменённых ассертов к исходным буквальным `contains`/`countOccurrences`.

### Ветка (a) — если бы фича реально не рендерила stub (НЕ наш случай)
Этот сценарий **здесь не применяется** (см. (c)), приведён только как
ориентир. Признак: при отладке `blacklisted_post_class` пуст или блоки
`blacklisted_stub_open/close` не добавляются. Тогда правка была бы в
`ThemeTemplate.kt:275-287`, а тесты — без изменений. Поскольку по факту переменная
выставляется и блоки добавляются — **используй ветку (b)** и тесты не меняй
ThemeTemplate.kt.

## Пункт 6 — ArticleParserImageTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParserImageTest.kt`
Production-файл:
`app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParser.kt`
Тип: **PRODUCTION-баг**. Риск: высокий.

### (a) Симптом + точное сообщение об ошибке
Падают **4** метода с `java.lang.AssertionError` (часто без текстового
сообщения, т. к. это голые `assertTrue`/`assertFalse`):
1. `parseArticleRuntime_doesNotDuplicateHeaderMetadataInsideRenderedBody` (строки 455–521).
2. `parseArticleV3_preservesLeadBeforeFirstImage` (строки 218–254).
3. `parseArticle_rebuildsPollFromDataSitePollPayload` (строки 790–841).
4. `parseArticleRuntime_preservesArticleAnonsBeforeFirstImageInRenderedHtml` (строки 400–453).

### (b) Файлы и строки — что именно проверяет каждый тест
- Тест 1 (`doesNotDuplicateHeaderMetadata…`, строки 511–520):
  `assertFalse(parsedHtml.contains("article-meta-comment"))` (518),
  `assertFalse(parsedHtml.contains("<time class=\"article-meta-time\">24.05.26</time>"))` (519),
  `assertFalse(renderedHtml.contains("article-meta-comment"))` (520), а также
  порядок lead → image → body (511–517). Контракт: метаданные шапки
  (`article-meta-comment`, `article-meta-time`) НЕ дублируются внутри тела.
- Тест 2 (`preservesLeadBeforeFirstImage`, строки 246–253): `leadIndex >= 0`,
  `imageIndex > leadIndex`, `bodyIndex > imageIndex`, lead встречается ровно один
  раз (`assertEquals(leadIndex, parsedHtml.lastIndexOf(lead))`),
  `assertEquals(null, article.imgUrl)`. Контракт: lead-абзац
  (`.article__lead`) сохраняется ПЕРЕД первой картинкой, не дублируется, и не
  становится `imgUrl`.
- Тест 3 (`rebuildsPollFromDataSitePollPayload`, строки 821–840): из
  `data-site-poll`-payload (строка 808) должен собраться нормализованный
  результат опроса: `poll-ajax-frame-news`, тексты вариантов с заменой
  `&nbsp;`→пробел («У меня компьютер светится…»), проценты
  `13% <span class="num_votes">386</span>`, `Проголосовало 2932 чел.`, и при
  этом БЕЗ `<form`, `answer[]`, без «Новая версия Gemini…» и без
  `news-poll-fallback`.
- Тест 4 (`preservesArticleAnonsBeforeFirstImage…`, строки 445–452): аналог
  теста 2, но lead берётся из `.article-anons > p` (строка 422); проверяется
  порядок lead → image → body, единственность lead (451) и
  `assertFalse(renderedHtml.contains("article-meta-comment"))` (452).

### (c) Первопричина (что проверено и где уточнено)
Это реальные регрессии рендера статьи в `ArticleParser.parseArticle`
(`ArticleParser.kt:507`). Прогон подтвердил именно эти 4 метода. По коду
определены ответственные участки (правки должны быть локализованы в них):
- Lead/anons-перед-картинкой (тесты 2 и 4): обработка lead-маркеров —
  `ArticleParser.kt:177` (`leadClassMarkers = listOf("lead","intro","announce",
  "subtitle","article__lead","content__lead")`). Нужно убедиться, что lead из
  `.article__lead` и из `.article-anons` сохраняется перед первой картинкой,
  не дублируется и не уходит в `imgUrl`.
- Дедуп метаданных шапки (тест 1): удаление `article-meta-comment` /
  `article-meta-time` из тела — регэксп `article-meta-comment` на
  `ArticleParser.kt:285-290`. Нужно, чтобы дублированные в `articleBody`
  метаданные (фикстура: строки 478–483) вычищались.
- Пересборка опроса из `data-site-poll` (тест 3): обработка payload —
  `ArticleParser.kt:77` (regex `data-site-poll`), сборка нормализованного блока
  — окрестности `ArticleParser.kt:1451`, `:1598`, `:2063-2095`
  (`poll-ajax-frame-news`, `news-poll-normalized`/`news-poll-fallback`).
  Уточнение: тест требует именно **нормализованный результат** (проценты, число
  голосов), а НЕ fallback-вариант (`news-poll-fallback` запрещён ассертом
  строки 836) и НЕ vote-форму (`<form`/`answer[]` запрещены строками 832–833).

Замечание: исходная диагностика называла ориентиры строк теста (`:117`, `:249`,
`:821`). Фактически: helper `assertFalse` — `:116-118`; lead-тест 2 — `:218-254`
(ассерты `:246-253`); poll-тест — `:790-841` (ассерты `:821-840`). Имена методов
выше — каноничны, ориентируйся на них.

### (d) Пошаговые действия — методика расследования (per-test, НЕ слепой патч)
Для каждого падающего метода:

Шаг A. Получи фактический вывод. Временно (для диагностики) в начале метода
после получения `parsedHtml`/`renderedHtml` выведи его (`println` или отладчик)
и сравни «ожидается vs получено» по конкретным ассертам из (b). Так поймёшь,
что именно не так: дубль, неверный порядок, отсутствие блока, лишний `<form`.

Шаг B. Сопоставь с кодом-владельцем (см. (c)) и найди конкретную ветку:
- Тесты 2/4: пройди путь обработки lead до вставки первой картинки; проверь,
  что узлы `.article__lead` / `.article-anons` извлекаются и помещаются перед
  картинкой ровно один раз; проверь, что lead-текст не присваивается `imgUrl`.
- Тест 1: проверь, что регэксп на `:285-290` действительно удаляет
  `article-meta-comment` и связанный `<time class="article-meta-time">…</time>`
  из тела `articleBody`, когда они продублированы.
- Тест 3: проследи, какая ветка срабатывает на `data-site-poll` — должна быть
  «нормализованный результат», а не fallback/vote-form. Проверь форматирование
  процентов (`13%`, `4%`), число голосов (`Проголосовало 2932 чел.`), замену
  `&nbsp;`.

Шаг C. Сделай **минимальную** точечную правку под найденную ветку. Каждая правка
должна затрагивать только свой участок (lead-вставка / дедуп-метаданные /
poll-нормализация). Не объединяй их в общий рефактор.

Шаг D. Убери диагностический `println`. Тесты НЕ меняются — это production-баги.

Шаг E. После правок прогоняй класс целиком (см. (f)) — правки lead/poll/meta
могут влиять друг на друга через общий `parseArticle`; убедись, что не появилось
новых падений среди уже зелёных методов класса (их там 33).

### (e) ЧТО НЕ ТРОГАТЬ
- **Не переписывай** `ArticleParser.kt` (~4000 строк) и общую структуру
  `parseArticle` — только точечные ветки lead/meta/poll.
- Не меняй тесты и их фикстуры/стабы (`ArticlesPatternProviderStub`,
  `RuntimeArticlesPatternProviderStub`) — это контракт.
- Не меняй уже зелёные poll-тесты (`parseArticle_rebuildsUnvotedDataSitePollPayloadAsVoteForm`,
  `extractNormalizedPollBlock_readsVotedDataSitePollFromVoteResponse`,
  `parseArticle_dataSitePollWithVoteCookieRendersResultsNotVoteForm`) — их
  поведение должно сохраниться.
- Не трогай шаблон `template_news.html`, если причина — в парсере (а она в нём).

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.model.data.remote.api.news.ArticleParserImageTest"
```
Ожидание: 37 тестов, 0 падений (было 4). Затем смежные:
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.model.data.remote.api.news.NewsApiCommentActionsTest"
```

### (g) Откат
`git checkout -- app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParser.kt`
(или ручной revert точечных правок). Тест не менялся.

## Пункт 7 — FavoritesRepositoryTest

Файл теста: `app/src/test/java/forpdateam/ru/forpda/model/repository/faviorites/FavoritesRepositoryTest.kt`
Production-файл:
`app/src/main/java/forpdateam/ru/forpda/model/repository/faviorites/FavoriteReadStateMerge.kt`
Тип: **ПРОДУКТОВОЕ РЕШЕНИЕ**. Риск: средний.
**ВАЖНО: НЕ МЕНЯЙ КОД, ПОКА ПОЛЬЗОВАТЕЛЬ НЕ ВЫБРАЛ ПОЛИТИКУ. СНАЧАЛА СПРОСИ.**

### (a) Симптом + точное сообщение об ошибке
- Падает ровно один метод (подтверждено узким прогоном):
  `` `refresh with inspector hints replaces rows and keeps one row per favorite` ``
  (строки 269–307).
- Сообщение: `java.lang.AssertionError` на строке **304**
  (`assertEquals(false, items.single { it.topicId == 42 }.isNew)`), фактически
  `expected:<false> but was:<true>`.
- (Уточнение к исходной диагностике: она указывала на «timestamps say unread»-
  тест; реально по прогону падает именно `refresh with inspector hints…`,
  ассерт на строке 304.)

### (b) Файлы и строки
- Сценарий теста (строки 269–307): в кэше топик 42 `isNew=true`; сеть
  возвращает топик 42 `isNew=false` (прочитан по свежему HTML); инспектор
  присылает событие для топика 42 с `timeStamp=200, lastTimeStamp=100`
  (новее → `inspectorUnread=true`). Тест ожидает `isNew=false` (строка 304).
- Логика мерджа: `FavoriteReadStateMerge.merge(...)` —
  `FavoriteReadStateMerge.kt:21-137`. Ветка `inspectorPresent && inspectorUnread`
  — `FavoriteReadStateMerge.kt:38-72`.
- Конкретно для этого кейса срабатывает приоритет «кэш был непрочитан» →
  `FavoriteReadStateMerge.kt:57-67`: т. к. `htmlSaysUnread=false`,
  `network==READ`, но `cachedUnread=true`, возвращается
  `Result(UNREAD, …, "preserve_cached_unread_over_stale_html")` → `isNew=true`.

### (c) Первопричина (проверено) — это конфликт ПОЛИТИК, а не явный баг
Текущий продукт даёт приоритет инспектору/кэш-непрочитанности: если кэш был
unread и HTML без явного `+N`, строка остаётся UNREAD до открытия топика
пользователем (см. комментарий в коде, `FavoriteReadStateMerge.kt:58-59`). Тест
же кодирует противоположную политику: «свежее HTML-чтение побеждает». Обе
позиции осмысленны:
- **Политика A — «инспектор/кэш-unread побеждает» (текущее поведение продукта).**
  Плюс: не «съедаем» непрочитанное до фактического открытия топика. Минус: тест
  падает; пользователь может видеть unread, хотя сервер уже отдал read.
- **Политика B — «свежее HTML-чтение побеждает» (ожидание теста).** Плюс: тест
  зелёный, UI быстрее отражает прочитанность. Минус: при «ленивом» HTML без `+N`
  можно преждевременно потушить реально непрочитанную тему.

### (d) Пошаговые действия — СНАЧАЛА ВОПРОС, ПОТОМ ОДНА ИЗ ВЕТОК
Шаг 1 (обязательный). Останови работу и задай пользователю вопрос дословно:
> «В пункте 7 (FavoritesRepositoryTest) конфликт политик чтения избранного. Что
> считать источником истины, когда инспектор/кэш говорят „непрочитано“, а свежий
> HTML — „прочитано“ (без явного +N)? Вариант A — оставить приоритет
> инспектора/кэша (текущее поведение, тест придётся обновить). Вариант B —
> „свежее HTML-чтение побеждает“ (меняем merge-логику под тест). Какой выбрать?»

Не предпринимай изменений до ответа.

Шаг 2A. Если выбран **Вариант B («свежее HTML-чтение побеждает»)** — правка в
ПРОДУКТЕ. В `FavoriteReadStateMerge.kt`, ветка `inspectorUnread`
(строки 57–67): расширь условие, при котором возвращается READ, чтобы оно
срабатывало и когда кэш был unread, а HTML/сеть дают READ без `+N` и без
`hasNewerContentThanCache`. Практически — в блоке `if (!htmlSaysUnread &&
network == FavoriteReadState.READ && !hasNewerContentThanCache)` (строка 57)
убери/ослабь подветку `if (cachedUnread)` (строки 60–67), чтобы возвращалось
`Result(FavoriteReadState.READ, 0, "html_read_over_stale_inspector")`. Делай
минимально и проверь, что не ломаются другие тесты класса (их там много и они
проверяют ровно эти политики).

Шаг 2B. Если выбран **Вариант A («инспектор/кэш-unread побеждает», текущее
поведение)** — правка в ТЕСТЕ, продукт не трогаем. В тесте
`refresh with inspector hints replaces rows and keeps one row per favorite`
(строки 269–307) приведи ожидание к фактическому поведению: на строке 304
замени `assertEquals(false, …isNew)` на `assertEquals(true, …isNew)` и при
необходимости синхронизируй ассерт `unreadPostCount` на строке 305 с реальным
значением (прогон покажет фактическое). Меняй только этот метод.

### (e) ЧТО НЕ ТРОГАТЬ
- **Ничего не меняй до ответа пользователя.** Это явное требование.
- При Варианте A — не трогай `FavoriteReadStateMerge.kt` и другие тесты класса.
- При Варианте B — не переписывай весь `merge`; меняй только подветку
  `inspectorUnread` и обязательно прогони ВЕСЬ класс на регресс (там десятки
  тестов на эти же политики, например строки 237–267, 356–381, 384–411,
  443–500, 559–601, 603–684).
- Не меняй сигнатуру `FavoriteReadStateMerge.merge(...)` и `applyTo(...)`.

### (f) Проверка
```
./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepositoryTest"
```
Ожидание: все тесты класса зелёные. При Варианте B особенно внимательно
убедись, что не покраснели тесты, кодирующие «inspector/cache-unread wins».

### (g) Откат
- Вариант A: верни строку 304 (и при необходимости 305) теста к исходному.
- Вариант B: `git checkout -- app/src/main/java/forpdateam/ru/forpda/model/repository/faviorites/FavoriteReadStateMerge.kt`.

## Definition of done

- Полный прогон зелёный:
  ```
  ./gradlew :app:testStableDebugUnitTest --continue
  ```
  Ожидание после выполнения пунктов 1–6 и принятого решения по пункту 7:
  **0 падений** (1609 тестов).
- Порядок не нарушать: 1 → 2 → 3 → 4 → 5 → 6 → 7. После каждого пункта —
  узкий прогон из его подпункта «(f)».
- Проверка компиляции (т. к. `assembleStableRelease` / `assembleRelease`
  требуют keystores и не годятся для быстрой проверки):
  ```
  ./gradlew :app:compileStableDebugKotlin
  ```
  а тесты — только через unit-test-таску:
  ```
  ./gradlew :app:testStableDebugUnitTest --continue
  ```
- Границы: для «только-тестовых» пунктов (1, 2, 3, 5) production-код НЕ изменён;
  для production-пунктов (4, 6) изменения точечные, без переписывания парсеров;
  пункт 7 выполнен только после явного выбора политики пользователем.
- Никаких коммитов не делать (если пользователь не попросит отдельно).
