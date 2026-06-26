# План пошагового исправления навигации по ссылкам и «цепочки памяти» скролла (ForPDA)

> Документ предназначен для AI-агента-исполнителя, который будет применять
> исправления по одному, **не видя исходного чата**. Каждый пункт самодостаточен:
> указаны точные файлы, строки, что менять, чего **не трогать**, как проверить и
> как откатить. Читай каждый шаг буквально и не выходи за его границы.

## Контекст

- Источник правок — read-only аудит WebView-темы 4PDA (рендер постов в WebView
  через `theme.js`, мост `IThemePresenter`). Жалоба пользователя:
  «при переходе по ссылкам перекидывает в совершенно другие сообщения темы» и
  «теряется скролл/якорь».
- Все находки проверены чтением реального кода; номера строк актуальны на момент
  написания плана. Перед правкой всё равно **перечитай** указанный диапазон —
  файлы крупные (`theme.js` ~3500 строк, `ThemeViewModel.kt` ~4900 строк), и
  строки могли сдвинуться от предыдущих пунктов этого же плана.
- Команда быстрой проверки компиляции Kotlin:
  ```
  ./gradlew :app:compileStableDebugKotlin
  ```
- Команда узкого прогона нужного теста:
  ```
  ./gradlew :app:testStableDebugUnitTest --tests "<FQN>"
  ```
- JS (`theme.js`) юнит-тестами не покрыт — проверка ручная в приложении (сценарии
  указаны в «(f) Проверка» соответствующих пунктов).

### Базовые правила для исполнителя (действуют ВЕЗДЕ)

- Меняй только то, что предписано в конкретном шаге. Никаких «попутных»
  улучшений, переформатирований, переименований, рефакторингов.
- Чётко соблюдай границу «JS / Kotlin / оба»: в каждом пункте указано, какой
  слой меняется. Не трогай второй слой, если он не указан.
- Граница production vs test: добавление/обновление unit-тестов разрешено только
  там, где это явно предписано (пункты A1, и опционально A3/A4). Существующие
  тесты (`LastReadAnchorPolicyTest`, `ThemeTemplateTest`) **не ломать**.
- После каждого пункта выполняй узкую проверку из его подпункта «(f)» прежде чем
  переходить к следующему.
- Никаких коммитов, пока пользователь не попросит отдельно.

## Оглавление

- [Рекомендуемый порядок выполнения и риски](#рекомендуемый-порядок-выполнения-и-риски)
- [A1 — buildFinalAnchor/extractScrollElement: кривой/пустой якорь (CRITICAL, Kotlin)](#a1)
- [A2 — resolveThemeAnchorElement берёт первый дубль якоря (HIGH, JS)](#a2)
- [A3 — handleForumNavigation: строковое сравнение showtopic (MEDIUM, Kotlin)](#a3)
- [A4 — getPostById без учёта topicId (MEDIUM, Kotlin)](#a4)
- [B1 — гонка native scrollY vs async DOM-anchor (HIGH, Kotlin)](#b1)
- [B2 — restore по anchor-offset до финального layout (MEDIUM, JS)](#b2)
- [B3 — source-anchor TTL 5s протухает на медленной сети (MEDIUM, JS+Kotlin)](#b3)
- [B4 — пустые "" якоря в стеке anchors (LOW, Kotlin)](#b4)
- [C — doScroll availableViewport: регрессий нет (анализ, действий не требуется)](#c)
- [E — SecureCookiesPreferences fallback в незашифрованные prefs (продуктовое решение)](#e)
- [Definition of done](#definition-of-done)

## Рекомендуемый порядок выполнения и риски

Выполняй строго в этом порядке:

| № | Код | Слой | Риск | Почему такой порядок / зависимости |
|---|-----|------|------|------------------------------------|
| 1 | A1  | Kotlin | CRITICAL | Самая вероятная причина «перекидывает не туда»; чистая функция, легко тестируется. **Чинит и часть B4** (перестаёт класть пустые якоря). |
| 2 | A2  | JS | HIGH | Вторая вероятная причина; точечная замена резолвера якоря. Не зависит от A1. |
| 3 | B1  | Kotlin | HIGH | Главная причина «теряется скролл»; приоритет anchor+offset над сырым scrollY. |
| 4 | A3  | Kotlin | MEDIUM | Перезагрузка той же темы вместо локального скролла. Опц. тест. |
| 5 | A4  | Kotlin | MEDIUM | Скролл «в пустоту» при остаточных страницах другой темы. Опц. тест. |
| 6 | B2  | JS | MEDIUM | Доводит точность восстановления после догрузки картинок/шрифтов. |
| 7 | B3  | JS+Kotlin | MEDIUM | Расширяет окно жизни source-anchor на медленной сети. |
| 8 | B4  | Kotlin | LOW | Защита стека якорей от пустых значений (частично закрыт A1). |
| 9 | C   | — | — | Только анализ: регрессий нет, **действий не требуется**. |
| 10| E   | Kotlin | решение | Продуктовый trade-off безопасности — **сначала вопрос пользователю**. |

Логика порядка: сначала две самые вероятные причины жалобы (A1 — CRITICAL,
A2 — HIGH) и главная причина потери скролла (B1 — HIGH). Затем MEDIUM-навигация
(A3, A4) и доводка памяти (B2, B3). В конце — низкорисковый B4, констатация по C
и продуктовое решение E (без правок до ответа человека).

Зависимости: **A1 закрывает источник пустых якорей**, которые иначе попадают в
стек `anchors` (B4); поэтому A1 делать до B4. Остальные пункты независимы.

## A1 — buildFinalAnchor / extractScrollElement: кривой или пустой якорь {#a1}

Файл (только Kotlin): `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
Слой: **только Kotlin**. Риск: **CRITICAL**.

### (a) Симптом + наблюдаемое поведение
Пользователь тапает по внутренней ссылке на пост, который уже загружен на
текущих страницах темы (`#entryNNN`, `showtopic=…&p=NNN`, `view=findpost&p=NNN`).
Вместо плавного скролла к нужному посту его «перекидывает» к другому сообщению
ИЛИ вообще никуда не скроллит (остаётся на месте). Иногда после этого «Назад»
тоже не работает (см. зависимость с B4).

### (b) Файлы и точные строки
- `ThemeViewModel.kt:4110-4124` — `handleFindPostNavigation` (вызывает
  `extractScrollElement` и `buildFinalAnchor`, кладёт результат в `addAnchor` и
  эмитит `ScrollToAnchor`).
- `ThemeViewModel.kt:4139-4146` — `extractScrollElement`.
- `ThemeViewModel.kt:4148-4151` — `buildFinalAnchor` (баг).
- `ThemeApi.kt:704` — паттерн `elemToScrollPattern =
  Pattern.compile("(?:anchor=|#)([^&\\n\\=\\?\\.\\#]*)")` (допускает пустую группу).

### (c) Первопричина (проверено по коду)
1. Паттерн `(?:anchor=|#)([^…]*)` матчит и `anchor=`, и **любой** `#`, а группа
   `([…]*)` допускает **0 символов**. При URL с «голым»/служебным `#`
   (например `...&p=NNN#`) `matcher.group(1)` = `""` (пустая строка, не `null`).
2. В `buildFinalAnchor` (строки 4148-4151):
   ```
   return (if (elem == null) "entry" else "") + (if (elem != null) elem else postId)
   ```
   При `elem == ""` (не `null`): первая часть = `""` (т.к. `elem != null`),
   вторая часть = `elem` = `""`. Итог — **пустая строка** → `ScrollToAnchor("")`
   → `ThemeFragmentWeb.scrollToAnchor` выходит по `anchor.isNullOrBlank()`
   (`ThemeFragmentWeb.kt:303`): скролла нет, пользователь «не туда».
3. Когда `elem` непустой, но НЕ начинается с `entry` (например `anchor=`
   принёс числовой токен), `buildFinalAnchor` **не добавляет** префикс `entry`
   (предполагает, что `elem` — уже валидное `name`). Тогда JS ищет
   `[name="<число>"]`, не находит → fallback/скролл к чужому элементу.
4. `while (matcher.find())` берёт **последний** матч: при наличии и `anchor=…`,
   и `#entryNNN` может быть выбран не тот фрагмент.

### (d) Пошаговые действия (что именно изменить)
Цель: `buildFinalAnchor` всегда возвращает либо валидное `name` (спойлер-якорь
вида `spoiler-…` / явный `entry…`), либо `entry<postId>` как безопасный
fallback; пустую строку не возвращать никогда.

Замени тело `buildFinalAnchor` (строки 4148-4151).

Было:
```kotlin
    private fun buildFinalAnchor(elem: String?, postId: String): String {
        if (BuildConfig.DEBUG) Timber.d(" scroll to $postId : $elem")
        return (if (elem == null) "entry" else "") + (if (elem != null) elem else postId)
    }
```

Стало:
```kotlin
    private fun buildFinalAnchor(elem: String?, postId: String): String {
        if (BuildConfig.DEBUG) Timber.d(" scroll to $postId : $elem")
        val trimmed = elem?.trim().orEmpty()
        // Валидный якорь из URL используем как есть, только если он непустой и
        // не «голый» числовой токен (последнее — это id поста без префикса entry).
        if (trimmed.isNotEmpty() && !trimmed.all { it.isDigit() }) {
            return trimmed
        }
        // Иначе безопасный fallback на пост: entry<postId>. postId уже очищен в
        // extractPostId до цифр; если он пуст — возвращаем "" осознанно, чтобы
        // вызывающий код не скроллил в произвольное место.
        val numericPostId = postId.trim()
        return if (numericPostId.isNotEmpty()) "entry$numericPostId" else ""
    }
```

Ничего больше в файле не меняй. `extractScrollElement` и
`elemToScrollPattern` оставь как есть — нормализацию делаем в `buildFinalAnchor`.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй `elemToScrollPattern` (`ThemeApi.kt:704`) — он используется и в других
  местах; правка локализована в `buildFinalAnchor`.
- Не меняй `extractScrollElement`, `extractPostId`, `handleFindPostNavigation`.
- Не трогай JS-резолвер якоря (это пункт A2, отдельно).
- Не меняй контракт спойлер-якорей `spoiler-…` / `hide-…` (они содержат дефис и
  буквы, поэтому проходят как «не all-digits» и возвращаются как есть).

### (f) Проверка
1. Компиляция:
   ```
   ./gradlew :app:compileStableDebugKotlin
   ```
2. Рекомендуется добавить unit-тест на чистую функцию (см. ниже «Тест A1»).
   Прогон:
   ```
   ./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.presentation.theme.BuildFinalAnchorTest"
   ```
3. Ручной сценарий в приложении: открой тему, где в одном из постов есть ссылка
   вида `#entryNNN`/`...&p=NNN` на другой пост ЭТОЙ ЖЕ страницы. Тап по ссылке →
   плавный скролл именно к посту NNN, а не к другому/верху. Повтори для ссылки с
   хвостовым `#`.

#### Тест A1 (рекомендуемый, чистая функция)
`buildFinalAnchor` сейчас `private`. Минимально-инвазивный способ протестировать
без рефакторинга — извлечь логику не нужно; вместо этого сделай функцию
видимой для теста, пометив её `@VisibleForTesting internal`:

Было: `private fun buildFinalAnchor(elem: String?, postId: String): String {`
Стало: `@VisibleForTesting internal fun buildFinalAnchor(elem: String?, postId: String): String {`
(импорт `androidx.annotation.VisibleForTesting` уже может присутствовать; если
нет — добавь его.)

Затем создай тест
`app/src/test/java/forpdateam/ru/forpda/presentation/theme/BuildFinalAnchorTest.kt`,
проверяющий контракт без инстанса WebView: если конструктор `ThemeViewModel`
тяжёлый, НЕ создавай его — вместо этого вынеси логику в этот же тест как
зеркало невозможно; поэтому ЕСЛИ инстанцирование `ThemeViewModel` в тесте
нетривиально, **пропусти тест** и ограничься ручной проверкой (это допустимо).
Если же инстанс получить просто (есть фабрика/моки в соседних тестах темы) —
зафиксируй кейсы:
- `buildFinalAnchor(null, "123") == "entry123"`
- `buildFinalAnchor("", "123") == "entry123"`  (раньше было `""` — баг)
- `buildFinalAnchor("123", "123") == "entry123"` (числовой токен → entry, а не "123")
- `buildFinalAnchor("entry123", "123") == "entry123"`
- `buildFinalAnchor("spoiler-123-1", "123") == "spoiler-123-1"`
- `buildFinalAnchor(null, "") == ""`

### (g) Откат
Верни тело `buildFinalAnchor` к исходному (строки 4148-4151) и сними
`@VisibleForTesting`/удали тест-файл, если добавлял.

## A2 — resolveThemeAnchorElement берёт первый дубль якоря {#a2}

Файл (только JS): `app/src/main/assets/forpda/scripts/modules/theme.js`
Слой: **только JS**. Риск: **HIGH**.

### (a) Симптом + наблюдаемое поведение
В темах с бесконечной прокруткой (подгрузка соседних страниц), где один и тот же
пост попал в DOM дважды (наложение страниц), скролл к посту по ссылке/якорю уводит
к ПЕРВОМУ экземпляру `<a name="entryNNN">`, который может относиться к удалённому
дублю/верхней копии. Визуально — «перекинуло» к другому/неправильному месту того
же поста.

### (b) Файлы и точные строки
- `theme.js:1200-1211` — `resolveThemeAnchorElement(name)`; строки 1207 и 1210
  используют `document.querySelector('[name="entry…"]' / '[name="…"]')` —
  возвращают ПЕРВЫЙ матч.
- `theme.js:1313-1321` — `findRealThemePostById(postId)` — уже корректно
  фильтрует «реальные» посты (`isRealThemePost`, `:not(.topic_hat_*)`).
- `theme.js:2636-2654` — `dedupeThemePostContainers`: удаляет дубли
  `.post_container[data-post-id]`, но НЕ дубли `<a name="entryNNN">`.

### (c) Первопричина (проверено по коду)
`dedupeThemePostContainers` (2636-2654) чистит только контейнеры постов, а
anchor-узлы `<a name="entryNNN">` (в мобильной разметке 4PDA стоят отдельным
элементом перед контейнером — см. `theme.js:834-841`) не дедуплицируются.
`resolveThemeAnchorElement` (1210) на «голом» имени `entryNNN` отдаёт
`document.querySelector('[name="entryNNN"]')` — первый по DOM, который может
быть копией из удалённого/перекрывающегося диапазона страниц. Параллельный
путь `findRealThemePostById` (1313) выбирает именно «реальный» пост, но
`resolveThemeAnchorElement` им не пользуется для чистых `entry`-имён.

### (d) Пошаговые действия (что именно изменить)
Цель: для чистого имени `entryNNN` сперва пытаться найти «реальный» пост через
`findRealThemePostById`, и только при отсутствии — падать на исходный
`querySelector`. Спойлерную ветку (`anchorData`) НЕ менять.

Замени тело `resolveThemeAnchorElement` (строки 1200-1211).

Было:
```javascript
function resolveThemeAnchorElement(name) {
    if (typeof name !== 'string' || !name.length) return null;
    var anchorData = /([^-]*)-([\d]*)-(\d+)/g.exec(name);
    if (anchorData) {
        anchorData[1] = anchorData[1].toLowerCase();
        if (anchorData[1] === "spoiler") anchorData[1] = "spoil";
        if (anchorData[1] === "hide") anchorData[1] = "hidden";
        var entry = document.querySelector('[name="entry' + anchorData[2] + '"]');
        return entry ? entry.querySelectorAll(".post-block." + anchorData[1])[Number(anchorData[3]) - 1] : null;
    }
    return document.querySelector('[name="' + name + '"]');
}
```

Стало:
```javascript
function resolveThemeAnchorElement(name) {
    if (typeof name !== 'string' || !name.length) return null;
    var anchorData = /([^-]*)-([\d]*)-(\d+)/g.exec(name);
    if (anchorData) {
        anchorData[1] = anchorData[1].toLowerCase();
        if (anchorData[1] === "spoiler") anchorData[1] = "spoil";
        if (anchorData[1] === "hide") anchorData[1] = "hidden";
        var entry = document.querySelector('[name="entry' + anchorData[2] + '"]');
        return entry ? entry.querySelectorAll(".post-block." + anchorData[1])[Number(anchorData[3]) - 1] : null;
    }
    // Для чистого имени entryNNN предпочитаем «реальный» пост (без дублей и
    // topic_hat), чтобы при наложении страниц не уехать к копии из удалённого
    // диапазона. Падаем на querySelector только если реальный пост не найден.
    var entryMatch = /^entry(\d+)$/i.exec(name);
    if (entryMatch && typeof findRealThemePostById === "function") {
        var realPost = findRealThemePostById(entryMatch[1]);
        if (realPost) return realPost;
    }
    return document.querySelector('[name="' + name + '"]');
}
```

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй спойлерную ветку (`anchorData`) — она ищет вложенный `.post-block`.
- Не меняй `findRealThemePostById` (1313-1321) и `dedupeThemePostContainers`.
- Не меняй `doScroll`/`scrollToElement` — резолвинг локализован здесь.
- Не трогай Kotlin-слой.

### (f) Проверка
JS юнит-тестами не покрыт — проверка ручная:
1. Открой длинную тему, проскролль так, чтобы сработала подгрузка соседних
   страниц (наложение). Тапни по внутренней ссылке на пост, видимый в
   перекрытии. Скролл должен попасть в «живой» (нижний, реальный) экземпляр
   поста, а не в верхнюю копию.
2. Проверь, что обычный скролл по `#entryNNN` (тема без дублей) не сломался.
3. Проверь спойлер-якорь (ссылка вида `spoiler-NNN-1`) — открывается и
   скроллит к нужному спойлеру.

### (g) Откат
Верни тело `resolveThemeAnchorElement` к исходному (строки 1200-1211).

## A3 — handleForumNavigation: строковое сравнение showtopic {#a3}

Файл (только Kotlin): `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
Слой: **только Kotlin**. Риск: **MEDIUM**.

### (a) Симптом + наблюдаемое поведение
Тап по ссылке, ведущей на ТЕКУЩУЮ тему (но с другим представлением topic id в
URL — `?t=`, lofi `?tNNN`, либо текущий URL без `showtopic`, т.к. он
findpost/getnewpost), вызывает ПОЛНУЮ перезагрузку темы вместо локального
скролла. Пользователь теряет позицию — «перекинуло» из-за перезагрузки.

### (b) Файлы и точные строки
- `ThemeViewModel.kt:4085-4108` — `handleForumNavigation`.
- `ThemeViewModel.kt:4096` — сравнение
  `showTopicParam != Uri.parse(themeUrl).getQueryParameter("showtopic")`.
- `ThemeUrlPolicy.kt:17-41` — `ThemeUrlPolicy.parse(...)` (даёт нормализованный
  `topicId` из `showtopic`/`t`/lofi).

### (c) Первопричина (проверено по коду)
Сравнение идёт буквально по query-параметру `showtopic` обоих URL. Если текущий
`themeUrl` несёт тему в другом виде (`?t=NNN`, lofi `?tNNN-...`) или вовсе без
`showtopic` (после findpost/getnewpost-редиректа), то
`Uri.parse(themeUrl).getQueryParameter("showtopic")` вернёт `null`/другое
значение, и условие `showTopicParam != …` станет истинным даже для той же темы →
`loadUrl(...)` (перезагрузка) вместо передачи в findpost-скролл ниже.

### (d) Пошаговые действия (что именно изменить)
Цель: сравнивать НОРМАЛИЗОВАННЫЙ topicId ссылки и текущего URL через
`ThemeUrlPolicy.parse`, а не строки `showtopic`.

Замени условие на строке 4096.

Было:
```kotlin
        val showTopicParam = uri.getQueryParameter("showtopic")
        if (BuildConfig.DEBUG) Timber.d("param showtopic: $showTopicParam")

        if (showTopicParam != null && showTopicParam != Uri.parse(themeUrl).getQueryParameter("showtopic")) {
            setTopicOpenIntent(TopicOpenIntentClassifier.freshIntentForSource(lastOpenSourceScreen))
            loadUrl(url, sourceScreen = "in_topic_link")
            return true
        }
```

Стало:
```kotlin
        val showTopicParam = uri.getQueryParameter("showtopic")
        if (BuildConfig.DEBUG) Timber.d("param showtopic: $showTopicParam")

        val linkTopicId = ThemeUrlPolicy.parse(url)?.topicId
        val currentTopicId = ThemeUrlPolicy.parse(themeUrl)?.topicId
            ?: currentPage?.id?.takeIf { it > 0 }
        if (linkTopicId != null && currentTopicId != null && linkTopicId != currentTopicId) {
            setTopicOpenIntent(TopicOpenIntentClassifier.freshIntentForSource(lastOpenSourceScreen))
            loadUrl(url, sourceScreen = "in_topic_link")
            return true
        }
```

Проверь, что импорт `ThemeUrlPolicy` уже есть (он в том же пакете
`forpdateam.ru.forpda.presentation.theme`, поэтому импорт не требуется).
`currentPage` доступен в классе (используется рядом, например строка 4118).

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй `isFindPostNavigation`/`handleFindPostNavigation` ниже по методу —
  они отрабатывают, когда тема та же.
- Не меняй `ThemeUrlPolicy.parse`.
- Сохрани прежнее поведение «другая тема → loadUrl»: только уточняем сравнение.

### (f) Проверка
1. Компиляция: `./gradlew :app:compileStableDebugKotlin`.
2. (Опц.) если решишь добавить тест на `handleForumNavigation`, он потребует
   инстанса ViewModel и моков — это нетривиально; тест НЕ обязателен.
3. Ручной сценарий: открой тему через «непрочитанное»/findpost (URL без
   `showtopic`). Внутри темы тапни ссылку на пост этой же темы со `showtopic=` в
   href → должен быть локальный скролл (findpost-ветка), без перезагрузки темы и
   без потери позиции. И наоборот: ссылка на ДРУГУЮ тему по-прежнему открывает
   новую тему.

### (g) Откат
Верни условие строки 4096 к исходному (строковое сравнение `showtopic`).

## A4 — getPostById без учёта topicId {#a4}

Файл (только Kotlin): `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
Слой: **только Kotlin**. Риск: **MEDIUM**.

### (a) Симптом + наблюдаемое поведение
При остатке загруженных страниц прошлой темы в том же табе (`loadedPages`) ссылка
на пост может ошибочно «найти» пост из другой темы → `ScrollToAnchor` вместо
корректной загрузки нужной темы; скролл уходит «в пустоту»/не туда.

### (b) Файлы и точные строки
- `ThemeViewModel.kt:3644-3652` — `getPostById(postId: Int)`.
- Использование в навигации: `ThemeViewModel.kt:4114`
  (`getPostById(...) != null` решает «скроллить vs грузить»).

### (c) Первопричина (проверено по коду)
`getPostById` (3644-3649) сначала ищет по ВСЕМ `loadedPages.values` без фильтра
по topicId, и только fallback-ветки (3650-3652) ограничены `currentPage`. Id
постов на 4PDA глобально уникальны, поэтому коллизия маловероятна, но при
остаточных страницах другой темы найденный пост может не принадлежать текущей
теме, и навигация выберет неверную ветку.

### (d) Пошаговые действия (что именно изменить)
Цель: при поиске по `loadedPages` учитывать только страницы текущей темы.
Минимальная правка — добавить фильтр `page.id == currentPage?.id` в первую ветку.

Было:
```kotlin
    private fun getPostById(postId: Int): IBaseForumPost? =
            loadedPages.values
                    .asSequence()
                    .flatMap { page -> sequenceOf(page.topicHatPost) + page.posts.asSequence() }
                    .filterNotNull()
                    .firstOrNull { it.id == postId }
                    ?: currentPage?.topicHatPost?.takeIf { it.id == postId }
                    ?: currentPage?.posts?.firstOrNull { it.id == postId }
                    ?: topicHatPost?.takeIf { topicHatTopicId == currentPage?.id && it.id == postId }
```

Стало:
```kotlin
    private fun getPostById(postId: Int): IBaseForumPost? {
        val currentTopicId = currentPage?.id
        return loadedPages.values
                    .asSequence()
                    .filter { currentTopicId == null || it.id == currentTopicId }
                    .flatMap { page -> sequenceOf(page.topicHatPost) + page.posts.asSequence() }
                    .filterNotNull()
                    .firstOrNull { it.id == postId }
                    ?: currentPage?.topicHatPost?.takeIf { it.id == postId }
                    ?: currentPage?.posts?.firstOrNull { it.id == postId }
                    ?: topicHatPost?.takeIf { topicHatTopicId == currentPage?.id && it.id == postId }
    }
```

ВНИМАНИЕ: здесь `it.id` у `ThemePage` — это topicId страницы (а `page.posts[i].id`
— id поста). Перед правкой ПЕРЕЧИТАЙ `ThemePage` и убедись, что `page.id` —
именно topicId (см. использование `currentPage?.id` как topicId в этом же файле,
напр. строки 3652, 4118). Если поле называется иначе — используй верное имя
topicId страницы. Не угадывай.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй fallback-ветки (3650-3652) — они уже привязаны к `currentPage`.
- Не меняй сигнатуру `getPostById` (остаётся `IBaseForumPost?`).
- Не меняй вызовы `getPostById` в других местах.

### (f) Проверка
1. Компиляция: `./gradlew :app:compileStableDebugKotlin`.
2. Ручной сценарий: в одном табе открой тему A, затем перейди в тему B (тот же
   таб). Вернись/открой тему B и тапни ссылку на пост темы B. Скролл — к
   правильному посту; ссылка на пост, которого нет в текущей теме, идёт через
   загрузку (`loadUrl`), а не «в пустоту».

### (g) Откат
Верни `getPostById` к исходному виду (выражение без `filter` и без обёртки в
блок `{ return … }`).

## B1 — гонка native scrollY vs async DOM-anchor при скрытии вкладки {#b1}

Файл (только Kotlin): `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt`
Слой: **только Kotlin**. Риск: **HIGH**.

### (a) Симптом + наблюдаемое поведение
Открыл ссылку (новая дочерняя вкладка) или свернул тему — при возврате позиция
скролла уезжает (особенно на постах с картинками/тяжёлым контентом). «Теряется
скролл/якорь».

### (b) Файлы и точные строки
- `ThemeWebController.kt:1051-1101` — `saveScrollYOnHide()`.
- `ThemeWebController.kt:1055` — синхронный захват `webView.scrollY`.
- `ThemeWebController.kt:1069-1071` — РАННИЙ снапшот только со `scrollY/ratio`.
- `ThemeWebController.kt:1072-1097` — асинхронный `evaluateJavascript(
  captureThemeRefreshScrollAnchor)`, дописывающий `postId/offsetTop`.

### (c) Первопричина (проверено по коду)
Сначала пишется снапшот без DOM-якоря (1070): `updatePageHistoryHtml(target, "",
scrollY, scrollRatio=ratio, wasNearBottom=...)`. Точный снапшот с
`postId/offsetTop` приходит асинхронно через `evaluateJavascript` (1072-1097) и
применяется ТОЛЬКО если вкладка ещё жива и `currentPage?.id == targetPageId`
(1075-1080). Если вкладку прячут/пересоздают быстро или страница успела
смениться — второй снапшот теряется, и восстановление идёт по абсолютному
`scrollY`. Ленивые картинки/шрифты сдвигают layout → `scrollY` устаревает →
позиция уезжает.

ВАЖНО: убрать асинхронность здесь нельзя (JS по природе async). Минимальная
безопасная правка — НЕ ухудшать снапшот: гарантировать, что ранний снапшот
несёт максимум информации (ratio + wasNearBottom уже есть), и что при наличии
DOM-якоря он применяется. Сама async-логика и условие совпадения страницы —
корректны; правка усиливает ранний снапшот «ближайшим видимым постом», чтобы
даже без второго колбэка было к чему привязаться.

### (d) Пошаговые действия (что именно изменить)
ПЕРЕД правкой перечитай `theme.js` функцию `captureThemeRefreshScrollAnchor`
(`theme.js:1526-1539`) и `findThemeViewportAnchorPost` (`theme.js:1272-1293`) —
убедись, что в JS есть синхронный способ получить id «центрального» видимого
поста. Он есть.

Правка состоит в том, чтобы ранний (синхронный) путь тоже получал anchor-post id
синхронным `evaluateJavascript`-независимым вызовом. Так как из Kotlin синхронно
дернуть DOM нельзя, безопасная минимальная мера — НЕ перезаписывать уже
сохранённый точный снапшот «пустым» ранним. Сейчас порядок такой: ранний
снапшот (1070) → затем поздний (1095). Если поздний не пришёл, остаётся ранний —
это ОК. Проблема не в перезаписи, а в том, что при потере позднего нет
DOM-якоря.

Поэтому правка: в позднем колбэке (1094-1095) если `postId` пуст (JS не нашёл
якорь), НЕ затирай ранний снапшот вызовом с пустым `postId`. Замени блок
строк 1094-1096.

Было:
```kotlin
                if (currentPage != null) {
                    presenter.updatePageHistoryHtml(currentPage, "", scrollY, postId, offsetTop, domRatio, domWasNearBottom)
                }
```

Стало:
```kotlin
                if (currentPage != null) {
                    // Не затираем ранний снапшот пустым DOM-якорём: если JS не
                    // нашёл пост-якорь (postId пуст), сохраняем только уточнённые
                    // ratio/wasNearBottom, оставив anchorPostId из раннего прохода.
                    val safePostId = postId?.takeIf { it.isNotBlank() }
                    presenter.updatePageHistoryHtml(
                            currentPage,
                            "",
                            scrollY,
                            safePostId,
                            if (safePostId != null) offsetTop else null,
                            domRatio,
                            domWasNearBottom
                    )
                }
```

ПЕРЕД правкой перечитай сигнатуру `updatePageHistoryHtml`
(`ThemeViewModel.kt:3315-3367`): параметры `anchorPostId: String? = null,
anchorOffsetTop: Double? = null` — `null` означает «не менять/нет якоря». Это
поведение нужно подтвердить чтением `historyController.updatePageHistoryHtml`.
Если `null` там ТРАКТУЕТСЯ как «стереть якорь», а не «оставить прежний» — тогда
этот фикс не применяй и вместо него лишь добавь ранний синхронный
`anchorPostId` через `findThemeViewportAnchorPost` (потребует доп. JS-хелпера —
тогда это уже JS+Kotlin; в таком случае оформи как отдельную задачу и СПРОСИ).
Не делай вслепую.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй проверку совпадения страницы (1077) и guard `disposed`/`isAdded`
  (1074-1075) — они корректны.
- Не убирай ранний снапшот (1070) — он нужен как нижняя граница.
- Не трогай JS-капчер, кроме чтения.

### (f) Проверка
1. Компиляция: `./gradlew :app:compileStableDebugKotlin`.
2. Ручной сценарий: открой тему, проскролль на середину поста с картинками.
   Тапни ссылку (откроется дочерняя вкладка) → вернись назад. Позиция должна
   восстановиться к тому же посту/месту, а не уехать вверх/вниз после догрузки
   картинок. Повтори со сворачиванием/разворачиванием вкладки темы.

### (g) Откат
Верни блок строк 1094-1096 к исходному (один вызов `updatePageHistoryHtml` с
`postId`, `offsetTop`).

## B2 — восстановление по anchor-offset до финального layout {#b2}

Файл (только JS): `app/src/main/assets/forpda/scripts/modules/theme.js`
Слой: **только JS**. Риск: **MEDIUM**.

### (a) Симптом + наблюдаемое поведение
После refresh/back позиция сначала встаёт верно, затем «доезжает»/сдвигается,
когда догрузились картинки/шрифты, потому что последний retry отработал по ещё
не финальной вёрстке. Лёгкая, но заметная «потеря якоря».

### (b) Файлы и точные строки
- `theme.js:1595-1625` — `restoreThemeRefreshScrollAnchorWithRetries()`
  (расписание retry-ев фиксированными задержками).
- `theme.js:1607-1611` — массивы `delays` (фиксированные мс).
- `theme.js:1541-1586` — `restoreThemeRefreshScrollAnchorOnce(...)` (метод
  `anchor-offset`, строки 1551-1564).

### (c) Первопричина (проверено по коду)
Restore идёт по фиксированным задержкам `[1,80,180,420,900,1400(,2200)]`
(1607-1611). На медленной сети картинки догружаются после последнего retry, и
финальная позиция не корректируется. Метод `anchor-offset` (1551-1564) считает
`targetY` по `getBoundingClientRect()` на момент retry — если вёрстка ещё
«плывёт», точка устаревает.

### (d) Пошаговые действия (что именно изменить)
Цель — добавить ОДИН отложенный «дозакрепляющий» retry, привязанный к событию
загрузки (`window load` / стабилизация `scrollHeight`), не меняя существующее
расписание. Минимально-инвазивно: после планирования штатных retry-ев в
`restoreThemeRefreshScrollAnchorWithRetries` добавь подписку на `load`/таймер
стабилизации, который ещё раз вызовет `restoreThemeRefreshScrollAnchorOnce` для
того же `scrollGeneration`.

ПЕРЕД правкой перечитай строки 1595-1625 целиком и найди место сразу после
завершения цикла `for` по `delays` (около строки 1620-1622), но ВНУТРИ функции.
Вставь туда:

```javascript
    // Дозакрепление после полной загрузки ресурсов (картинки/шрифты двигают layout
    // уже после последнего фиксированного retry). Срабатывает один раз.
    (function () {
        var settleGeneration = scrollGeneration;
        var didSettle = false;
        var settleOnce = function () {
            if (didSettle) return;
            didSettle = true;
            if (!isThemeAnchorScrollCurrent(settleGeneration)) return;
            themeRuntimeRequestAnimationFrame(function () {
                if (!isThemeAnchorScrollCurrent(settleGeneration)) return;
                restoreThemeRefreshScrollAnchorOnce(settleGeneration, "settle+load", true);
            });
        };
        if (document.readyState === "complete") {
            themeRuntimeSetTimeout(settleOnce, 1600);
        } else {
            window.addEventListener("load", function () {
                themeRuntimeSetTimeout(settleOnce, 200);
            }, { once: true });
            // Страховка, если событие load уже пропущено/не придёт.
            themeRuntimeSetTimeout(settleOnce, 2600);
        }
    })();
```

Проверь, что используемые имена существуют в файле: `scrollGeneration` (объявлен
в начале функции, строка ~1605), `isThemeAnchorScrollCurrent`,
`themeRuntimeRequestAnimationFrame`, `themeRuntimeSetTimeout`,
`restoreThemeRefreshScrollAnchorOnce`. Если `restoreThemeRefreshScrollAnchorOnce`
с `BOTTOM`-режимом обрабатывается отдельно (строки 1601-1604 — ранний `return`),
этот блок до него не дойдёт (он после), значит он применяется только к
не-BOTTOM ветке — это корректно.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй существующие массивы `delays` и сам цикл retry.
- Не трогай BOTTOM-ветку (1601-1604) и
  `restoreThemeToBottomAfterRefreshWithRetries`.
- Не меняй `restoreThemeRefreshScrollAnchorOnce` (только вызываем его повторно).
- Не трогай Kotlin-слой.

### (f) Проверка
JS — ручная:
1. На медленном соединении (throttle) открой длинную тему с картинками,
   сделай pull-to-refresh из середины. Позиция должна остаться на том же посте
   после полной догрузки картинок (без «доезда»).
2. Проверь back-restore: вернись в тему — позиция стабильна после загрузки media.
3. Убедись, что END/bottom-навигация не изменилась (там свой путь).

### (g) Откат
Удали добавленный IIFE-блок дозакрепления из
`restoreThemeRefreshScrollAnchorWithRetries`.

## B3 — source-anchor TTL 5s протухает на медленной сети {#b3}

Файлы (JS + Kotlin):
- `app/src/main/assets/forpda/scripts/modules/theme.js`
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
Слой: **JS + Kotlin** (симметрично оба порога). Риск: **MEDIUM**.

### (a) Симптом + наблюдаемое поведение
Если страница/переход грузится дольше 5 секунд, привязка к посту-источнику
клика теряется → при возврате восстанавливается менее точная позиция. Усиливает
«потерю якоря» на медленной сети.

### (b) Файлы и точные строки
- `theme.js:1511` — `if (data.ageMs > 5000) { … return ""; }` в
  `captureThemeLinkSourceAnchor`.
- `ThemeViewModel.kt:3416-3426` — `consumeLinkSourceAnchorFor(targetUrl)`;
  строка 3419 — `if (ageMs > 5000L) { … return null }`.

### (c) Первопричина (проверено по коду)
Жёсткий TTL 5000 мс в обоих местах. На медленной загрузке source-anchor
помечается «stale» и отбрасывается, хотя пользователь реально только что
кликнул. Оба порога независимы и должны меняться синхронно.

### (d) Пошаговые действия (что именно изменить)
Цель — поднять TTL до 15000 мс (15 секунд) симметрично в JS и Kotlin. Это
консервативное расширение окна; больший срок не нужен (старый anchor всё равно
заменяется новым кликом).

JS — `theme.js:1511`.
Было:
```javascript
        if (data.ageMs > 5000) {
```
Стало:
```javascript
        if (data.ageMs > 15000) {
```

Kotlin — `ThemeViewModel.kt:3419`.
Было:
```kotlin
        if (ageMs > 5000L) {
```
Стало:
```kotlin
        if (ageMs > 15000L) {
```

Если хочешь, замени «магическое число» на именованную константу — НО только если
рядом уже есть аналогичные константы; иначе оставь литерал, чтобы не плодить
стиль. Минимальная правка — литерал.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй формат `JSON.stringify(data)` и поля snapshot.
- Не меняй логику сравнения `pendingHistorySourceAnchor == anchor`
  (`ThemeViewModel.kt:3428`).
- Не трогай другие TTL/таймеры (highlight TTL 6000 — это другое, не его).

### (f) Проверка
1. Компиляция Kotlin: `./gradlew :app:compileStableDebugKotlin`.
2. Ручной сценарий (throttle сети): кликни ссылку в теме, дождись медленной
   загрузки (>5 c, <15 c), вернись назад → позиция привязана к посту-источнику
   клика (а не к грубому fallback).

### (g) Откат
Верни оба порога к `5000` (JS) и `5000L` (Kotlin).

## B4 — пустые "" якоря в стеке anchors {#b4}

Файл (только Kotlin): `app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt`
(альтернатива — `ThemeViewModel.kt`). Слой: **только Kotlin**. Риск: **LOW**.
**Зависимость:** после A1 источник пустых якорей в основном устранён; B4 —
страховка на остальные пути добавления якоря.

### (a) Симптом + наблюдаемое поведение
В стек `anchors` попадает пустая строка `""` (например из кривого
`buildFinalAnchor` до A1, либо иной путь). Тогда «Назад» «срабатывает»
(снимает пустой якорь), но никуда не скроллит — ощущение зависшей навигации.

### (b) Файлы и точные строки
- `ThemePage.kt:66-72` — `addAnchor`/`removeAnchor`.
- Использование: `ThemeViewModel.kt:4118` (`currentPage?.addAnchor(finalAnchor)`),
  `ThemeViewModel.kt:4205-4211` (back снимает по одному и эмитит
  `ScrollToAnchor(it.anchor.orEmpty())`).

### (c) Первопричина (проверено по коду)
`addAnchor` (66-68) добавляет любое значение, включая `""`. `onBackPressed`
(4205-4211) при `anchors.size > 1` снимает якорь и эмитит скролл, даже если он
пустой — `scrollToAnchor` затем тихо выходит на пустой строке.

### (d) Пошаговые действия (что именно изменить)
Цель — не класть пустые/бланковые якоря в стек. Минимальная правка в `addAnchor`.

Было:
```kotlin
    fun addAnchor(anchor: String): Boolean {
        return anchors.add(anchor)
    }
```

Стало:
```kotlin
    fun addAnchor(anchor: String): Boolean {
        if (anchor.isBlank()) return false
        return anchors.add(anchor)
    }
```

Это не меняет контракт возврата (`Boolean`); вызывающий код в 4118 игнорирует
результат, что безопасно.

### (e) ЧТО НЕ ТРОГАТЬ
- Не меняй `removeAnchor`, `anchor` getter, `st` getter.
- Не меняй `onBackPressed` (после фильтра пустые якоря туда не попадут).
- Не меняй сигнатуру `addAnchor`.

### (f) Проверка
1. Компиляция: `./gradlew :app:compileStableDebugKotlin`.
2. (Опц.) короткий unit-тест на `ThemePage.addAnchor`:
   `addAnchor("") == false && anchors.isEmpty()`,
   `addAnchor("entry1") == true`. Если в проекте есть тесты `ThemePage` —
   добавь туда; иначе тест необязателен.
3. Ручной сценарий: серия переходов по внутренним ссылкам, затем «Назад»
   несколько раз — каждый back реально скроллит, нет «пустых» шагов.

### (g) Откат
Убери строку `if (anchor.isBlank()) return false` из `addAnchor`.

## C — doScroll availableViewport: регрессий нет, действий не требуется {#c}

Слой: анализ JS. Действий **не требуется**.

### Что проверено
Свежая правка `viewport -> availableViewport` в «ветке высокого поста»
`doScroll` (`theme.js:2342-2393`, ключевые строки 2362-2376):
- `viewport = window.innerHeight` (2362) — используется для `maxY`
  (2366: `maxY = scrollHeight - viewport`) и как база.
- `availableViewport = max(0, viewport - bottomReserve)` (2363-2365), где
  `bottomReserve = bottomChromePadding + messagePanelPadding`.
- Для высокого поста (`postHeight > availableViewport`, 2368):
  `y = postTopAbs + postHeight - availableViewport` (2372) — низ поста ставится
  НАД нижним chrome, чтобы панель действий поста была видна.
- Итог клампится: `window.scrollTo(0, max(0, min(maxY, y)))` (2376).

### Почему регрессий нет
1. `maxY` намеренно считается по ПОЛНОМУ `viewport` (2366), т.е. это реальный
   максимум документа (включая нижний спейсер `bottom_chrome_spacer`, который
   входит в `document.documentElement.scrollHeight`). Клам `min(maxY, y)`
   корректно ограничивает увеличенный из-за `availableViewport` `y`.
2. `doScroll` вызывается ТОЛЬКО из ветки `NORMAL_ACTION || explicitAnchor`
   (`scrollToElement`, `theme.js:2097-2106`, вызов на 2104). Ветки
   BACK/REFRESH (2060-2087), END (2088-2096) `doScroll` НЕ используют:
   - END → `scrollToEndAnchorOrBottomWithRetries` (2257) и
     `scrollToThemeBottomOnce` (3396), где `viewport = window.innerHeight`
     (3398) — полный, без `availableViewport`.
   - BACK/REFRESH → `restoreThemeRefreshScrollAnchorWithRetries` /
     `window.scrollTo(0, loadScrollY)` / `scrollIntoView()` — `availableViewport`
     там не фигурирует.
   Значит правка изолирована в явном/обычном скролле к посту и не затрагивает
   END/BACK/REFRESH.
3. Нижний спейсер учитывается корректно: он часть `scrollHeight` → `maxY`
   достаточно велик, чтобы выровнять низ высокого поста над chrome без выхода
   за конец документа.

### Вывод
Правка `availableViewport` корректна и согласована с `maxY`/`scrollHeight`/
нижним спейсером; END/BACK/REFRESH/EXPLICIT-anchor ветки не затронуты.
**Регрессий нет — изменения не требуются.** (Если в будущем `doScroll` начнут
вызывать из END/BACK, переоценить.)

## E — SecureCookiesPreferences fallback кладёт приватные куки в незашифрованные prefs {#e}

Файлы: `app/src/main/java/forpdateam/ru/forpda/common/SecureCookiesPreferences.kt`,
`app/src/main/java/forpdateam/ru/forpda/client/CookieManager.kt`
Слой: Kotlin. Тип: **ПРОДУКТОВОЕ РЕШЕНИЕ (trade-off безопасности)**.
**ВАЖНО: НЕ МЕНЯЙ КОД, ПОКА ПОЛЬЗОВАТЕЛЬ НЕ ВЫБРАЛ ПОЛИТИКУ. СНАЧАЛА СПРОСИ.**

### (a) Симптом + наблюдаемое поведение
На Android 7.0–7.1 (и кривых прошивках) при сбое AndroidKeyStore
`EncryptedSharedPreferences` не поднимается, и хранилище откатывается на ОБЫЧНЫЕ
(незашифрованные) `SharedPreferences`. Приватные auth-куки (`cookie_member_id`,
`cookie_pass_hash`, `cookie_session_id` и т.д.) в этом режиме лежат в открытом
виде. Для пользователя поведение незаметно (вход работает), но это снижение
уровня защиты.

### (b) Файлы и точные строки
- `SecureCookiesPreferences.kt:44-66` — `createStorage` (try → Encrypted,
  catch → `PreferenceManager.getDefaultSharedPreferences` fallback).
- `SecureCookiesPreferences.kt:35-36` — флаг `isEncrypted`.
- `SecureCookiesPreferences.kt:63` — `Timber.e(e, "Failed to init
  EncryptedSharedPreferences; falling back to plain prefs")`.
- `CookieManager.kt:43-56` — try/catch вокруг `initializeCookies()`.
- `CookieManager.kt:92-125` — `saveFromResponse` (пишет `cookie_*` через
  `securePrefs.putString`, который в fallback-режиме — открытые prefs).

### (c) Первопричина / суть trade-off (проверено по коду)
Fallback намеренный: без него процесс падает на старте на сломанном keystore
(комментарии 16-19, 57-65). Цена — приватные куки в открытых prefs на затронутых
устройствах. Singleton потокобезопасен (double-checked locking,
`SecureCookiesPreferences.kt:136-145`); `CookieManager` использует
`ConcurrentHashMap`/`AtomicLong` — гонок хранилища нет. Проблема — только
конфиденциальность данных в fallback-режиме, и это решение продукта.

### (d) Возможные варианты (ОБСУДИТЬ, не применять без ответа)
Шаг 1 (обязательный). Задай пользователю вопрос дословно:
> «На Android 7 при сбое keystore auth-куки сохраняются в НЕзашифрованные prefs
> (fallback, иначе краш на старте). Что делаем: (A) осознанно принять trade-off
> и ничего не менять; (B) в fallback-режиме НЕ восстанавливать/не персистить
> приватные куки (юзер останется разлогинен на таких устройствах, но секреты не
> лягут в открытом виде); (C) добавить лёгкое смягчение — обфускация значений и
> снижение детализации логов, без полноценного шифрования? Какой вариант?»

Не предпринимай изменений до ответа.

Шаг 2 — по выбору:
- Вариант A (принять trade-off): код не меняем. Опционально убедись, что лог на
  строке 63 НЕ печатает значения куки (он не печатает — только исключение) —
  оставить как есть.
- Вариант B (не персистить секреты в fallback): в `saveFromResponse`
  (`CookieManager.kt:104-109`) и при восстановлении (`initializeCookies`,
  `CookieManager.kt:58-89`) пропускать приватные ключи, когда
  `securePrefs` не зашифрован. Потребуется прокинуть флаг `isEncrypted` наружу
  (сейчас он `private`, `SecureCookiesPreferences.kt:35`). Это расширение API —
  делать только при выборе B и минимально.
- Вариант C (смягчение): добавить лёгкую обфускацию значения перед `putString`
  в fallback-режиме. Это НЕ настоящая защита; описать пользователю честно.

### (e) ЧТО НЕ ТРОГАТЬ
- Ничего не меняй до ответа пользователя.
- Не убирай сам fallback (иначе вернётся краш на старте на Android 7).
- Не меняй singleton-логику и потокобезопасность.
- Не логируй значения куки ни в одном из вариантов.

### (f) Проверка
- Вариант A: проверять нечего (без правок).
- Варианты B/C: `./gradlew :app:compileStableDebugKotlin`; ручной smoke —
  логин/перезапуск на обычном устройстве не сломан; на устройстве с fallback
  (или эмуляция исключения) поведение соответствует выбранному варианту.

### (g) Откат
- Вариант A: нет правок.
- Варианты B/C: `git checkout --` затронутых файлов
  (`SecureCookiesPreferences.kt`, `CookieManager.kt`).

## Definition of done

- Применены пункты A1, A2, B1 (обязательные, высокий приоритет), затем A3, A4,
  B2, B3, B4 (по согласованию). Пункт C — без правок (только зафиксирован
  анализ). Пункт E — только после выбора политики пользователем.
- Зелёная компиляция Kotlin после каждого Kotlin-пункта и в конце:
  ```
  ./gradlew :app:compileStableDebugKotlin
  ```
- Релевантные unit-тесты (там, где добавлялись/затрагивались) зелёные:
  ```
  ./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.presentation.theme.BuildFinalAnchorTest"
  ./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.presentation.theme.LastReadAnchorPolicyTest"
  ./gradlew :app:testStableDebugUnitTest --tests "forpdateam.ru.forpda.presentation.theme.ThemeTemplateTest"
  ```
  `LastReadAnchorPolicyTest` и `ThemeTemplateTest` — регрессионная страховка
  (правки их не должны затрагивать; должны остаться зелёными).
- JS-правки (A2, B2) проверены вручную по сценариям из «(f)» — автотестов для
  `theme.js` нет.
- Ручные сценарии навигации/скролла пройдены:
  1. Ссылка на пост текущей страницы → плавный скролл именно к нему (A1).
  2. Ссылка на пост в зоне наложения страниц → к реальному (нижнему) экземпляру
     (A2).
  3. Ссылка на ту же тему через URL без `showtopic` → локальный скролл без
     перезагрузки (A3).
  4. Переход между темами в одном табе → нет скролла «в пустоту» (A4).
  5. Открытие дочерней вкладки/сворачивание и возврат → позиция стабильна после
     догрузки картинок (B1, B2).
  6. Медленная сеть: source-anchor не «протухает» в окне до 15 c (B3).
  7. Серия back-переходов → каждый реально скроллит (B4).
- Границы соблюдены: JS-пункты не трогают Kotlin и наоборот (кроме B3 —
  симметрично оба слоя); никаких рефакторингов; production vs test разделены.
- Коммитов нет, пока пользователь не попросит отдельно.
