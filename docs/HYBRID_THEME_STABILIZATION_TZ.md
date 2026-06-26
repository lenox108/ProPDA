# ТЗ: Стабилизация и оптимизация Hybrid Theme Mode (PROPDA / ForPDA)

> Документ для ИИ-исполнителя. Составлен на основе read-only аудита кодовой базы
> `/Users/j.golt/Documents/Cursor01/ForPDA-master`. Все пути и номера строк проверены
> по реальному коду на дату составления. Номера строк могут смещаться по мере правок —
> всегда подтверждай место по сигнатуре функции/строке кода перед изменением.

## 0. Контекст и стек

- Неофициальный Kotlin-клиент 4pda. Контент темы рендерится в WebView из HTML, собираемого
  шаблонизатором minitemplator + CSS из `app/src/main/assets/forpda/styles/`.
- Проверка сборки/компиляции (release требует отсутствующих ключей — НЕ использовать):
  - Компиляция Kotlin: `./gradlew :app:compileStableDebugKotlin`
  - Юнит-тесты темы: `./gradlew :app:testStableDebugUnitTest`
  - Полная отладочная сборка: `./gradlew :app:assembleStableDebug`
- Базовый пакет: `forpdateam.ru.forpda`.

## 1. ЖЕЛЕЗНЫЕ ПРАВИЛА (читать перед каждой задачей)

1. НИКАКИХ больших переписываний. Только мелкие, проверяемые правки.
2. НЕ удалять существующую логику, пока не доказано, что она мёртвая (найди все usages).
3. Перед изменением компонента — найди ВСЕ вызовы и зависимости (Grep по символу).
4. Каждая правка — атомарная и тестируемая. Один пункт = один коммит.
5. Hybrid Theme Mode должен оставаться полностью рабочим после КАЖДОГО пункта.
6. Любое предположение без подтверждения кодом помечать: `Недостаточно данных`.
7. Стабильность важнее архитектурной красоты. Детерминизм важнее «умного» поведения.
8. Каждый этап завершить и проверить (компиляция + тесты) ПЕРЕД переходом к следующему.
9. Поведенческие изменения, видимые пользователю, согласовывать отдельно (см. раздел 12).

## 2. КАРТА КЛЮЧЕВЫХ ФАЙЛОВ

| Роль | Файл |
|---|---|
| Фрагмент (view-scoped) | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt` (~2892 строк) |
| Контроллер WebView | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt` (~1721) |
| JS-мост (bridge) | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` |
| Базовый мост | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/BaseJsInterface.kt` |
| Регистрация мостов | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt` |
| Типобезопасный JS API | `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApi.kt` |
| Render guard (token) | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderGuard.kt` |
| ViewModel (retained) | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt` (~4943) |
| WebView | `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` |
| Шаблон/рендер | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt` |
| CSS-композер | `app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt`, `TemplateManager.kt` |
| Highlight-логика | `presentation/theme/HighlightResolver.kt`, `TopicHighlightApply.kt`, `HighlightArmingPolicy.kt` |
| Infinite scroll | `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollController.kt` |
| HTML-шаблон | `app/src/main/assets/template_theme.html` |
| JS-модуль темы | `app/src/main/assets/forpda/scripts/modules/theme.js` |
| JS общий | `app/src/main/assets/forpda/scripts/main.js` |

## 3. АРХИТЕКТУРНАЯ ПРОБЛЕМА (корень багов)

Существует ТРИ независимых механизма «валидности рендера», которые нигде не согласованы:

1. `ThemeRenderGuard.token` (Base64-строка, `SecureRandom`) — авторизация деструктивных
   bridge-вызовов (reply/quote/vote/delete/edit/report/poll/reputation).
   Файл: `ThemeRenderGuard.kt`. Проверка в `ThemeJsInterface.runProtected` (строки ~237-250).
2. `ThemePage.renderGenerationId` (int) — жизненный цикл подсветки поста (highlight/fadeout).
   Минтуется в `TopicHighlightApply.nextGeneration()`; bump только если `renderGenerationId == 0`.
3. `ThemeWebController.renderGeneration` (int) — гейт DOM/PAGE lifecycle и missed-lifecycle flush.
   Инкремент на каждом `renderThemePage` (`ThemeWebController.kt` ~строка 290).

Эти три значения минтуются в разное время, живут в разных объектах и НЕ сверяются между собой.
Цель долгосрочной части ТЗ (Этап 7) — постепенно ввести единый `ThemeRenderSession`,
не ломая текущую логику.

---

# ПОЭТАПНЫЕ ЗАДАЧИ

Порядок ОБЯЗАТЕЛЕН: от безопасных и локальных правок к структурным. Каждый этап:
завершить → скомпилировать → прогнать тесты → проверить чек-лист → коммит → следующий этап.

---

## ЭТАП 1 — Highlight: устранить рестарт таймера затухания (баг B1)

Приоритет: ВЫСОКИЙ. Риск: НИЗКИЙ. Тип: исправление детерминизма.

### Проблема (подтверждено кодом)
`reapplyTopicHighlightAfterScrollSettled()` обнуляет ОБА гварда, включая
`highlightFadeoutScheduledGeneration`:

Файл `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt`,
функция `reapplyTopicHighlightAfterScrollSettled()` (~строки 1392-1396):

```
fun reapplyTopicHighlightAfterScrollSettled() {
    highlightArmedGeneration = 0
    highlightFadeoutScheduledGeneration = 0   // <-- ПРОБЛЕМА
    reapplyTopicHighlight()
}
```

После обнуления `scheduleHighlightFadeoutForPage` (~строка 1469: `if (highlightFadeoutScheduledGeneration == generation) return`)
снова проходит гвард и повторно вызывает `PPDA_scheduleHighlightFadeout`, который в JS
отменяет старый таймер и заводит новый на 2000мс. Итог: подсветка живёт дольше 2с
при каждом settle/reveal. Это противоречит задокументированному контракту в
`ThemeJsApi.scheduleHighlightFadeout` (KDoc ~строки 296-298: «re-calling ... does NOT extend the deadline»)
и намерению теста `ThemeWebControllerFadeoutTest` («Same render must not re-arm the timer»).

Вызывается из ДВУХ мест: `ThemeFragmentWeb.kt` (ProgrammaticScrollEnded, ~строка 2544)
и `onWebViewContentRevealed()` (`ThemeWebController.kt` ~строка 1399-1404).

### Правка
1. Убрать обнуление `highlightFadeoutScheduledGeneration = 0` из
   `reapplyTopicHighlightAfterScrollSettled()`. Оставить только `highlightArmedGeneration = 0`.
   Обоснование: затухание планируется отдельно и идемпотентно по generation; повторный
   apply (визуальная граница) безопасен, а повторный schedule — нет.
2. Проверить, что `scheduleHighlightFadeoutForPage` остаётся идемпотентным по generation.

### Проверка
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeWebControllerFadeoutTest*"` — зелёный.
- Обновить/дополнить `ThemeWebControllerFadeoutTest.kt`: добавить кейс
  «после reapplyTopicHighlightAfterScrollSettled повторный scheduleHighlightFadeoutForPage
  с тем же generation НЕ вызывает повторный jsApi.eval(scheduleHighlightFadeout)».
- Ручная QA: открыть тему с непрочитанным постом → подсветка гаснет ровно ~2с,
  даже если в это время отрабатывает программный скролл/reveal.

### Definition of Done
Подсветка гаснет за фиксированные `HIGHLIGHT_FADEOUT_DELAY_MS` независимо от количества
settle/reveal событий. Тест на не-рестарт таймера проходит.

---

## ЭТАП 2 — Highlight: дедупликация лишних JS-вызовов (P2)

Приоритет: СРЕДНИЙ. Риск: НИЗКИЙ. Тип: производительность, без смены поведения.

### Проблема (подтверждено)
В `ThemeWebController.kt` highlight-путь делает 4 отдельных немедленных
`evaluateJavascript` на рендер, причём `setReadPosObserverEnabled(false)` вызывается ДВАЖДЫ:

- `reapplyTopicHighlight()` ~строки 1439-1440:
  `jsApi.eval(jsApi.setReadPosObserverEnabled(false))` + `jsApi.eval(jsApi.applyHighlight(...))`
- `scheduleHighlightFadeoutForPage()` ~строки 1471-1472:
  `jsApi.eval(jsApi.setReadPosObserverEnabled(false))` + `jsApi.eval(jsApi.scheduleHighlightFadeout(...))`

`jsApi.eval` (`ThemeJsApi.kt` ~строки 118-120) бьёт напрямую в `evaluateJavascript`,
минуя batch-очередь `evalJs`/`flushQueuedJs`.

### Правка
1. Убрать дублирующий вызов `setReadPosObserverEnabled(false)` — оставить ровно один
   на цикл рендера (например, только в `scheduleHighlightFadeoutForPage`, т.к. он вызывается
   раньше в `reapplyTopicHighlight`). Подтвердить порядок вызовов перед правкой.
2. Объединить highlight-сниппеты в один eval: собрать строку из
   `setReadPosObserverEnabled(false)` + `applyHighlight(...)` (+ при необходимости
   `scheduleHighlightFadeout`) и выполнить одним `jsApi.eval(...)` / через `webView.evalJs`.
   ВАЖНО: не менять момент и условия применения (генерационные гварды сохранить).
3. Не трогать диагностические события (`TopicHighlightDiagnostics.*`).

### Проверка
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeJsApi*"` — зелёный.
- Логи `js_highlight_applied` / `highlight_fadeout_scheduled` по-прежнему эмитятся ровно один раз на generation.
- Ручная QA: подсветка/затухание визуально без регрессий.

### Definition of Done
На один рендер — не более 1 вызова `setReadPosObserverEnabled`; число немедленных
highlight-eval снижено. Тесты JsApi зелёные.

---

## ЭТАП 3 — Lifecycle: порядок уничтожения WebView (баг L1)

Приоритет: СРЕДНИЙ. Риск: НИЗКИЙ. Тип: корректность teardown.

### Проблема (подтверждено)
В `ThemeFragmentWeb.onDestroyView()` (`ThemeFragmentWeb.kt` ~строки 1692-1760):
`webView.destroy()` (~строка 1745) выполняется ДО `moduleRegistry.disposeAll()` (~строка 1758).
Внутри disposeAll отрабатывает `ThemeBridgeHandler.cleanup()` с `removeJavascriptInterface(...)`
и `ThemeWebController.cleanup()` с `evaluateJavascript("destroyThemeRuntime...")` — но они
уже бьют в уничтоженный WebView. Сейчас это безопасно (обёрнуто try/catch), но
`removeJavascriptInterface` после `destroy()` бессмыслен.

### Правка
1. Перенести снятие мостов и runtime-cleanup ПЕРЕД `webView.destroy()`.
   Целевой порядок в `onDestroyView`:
   (a) `viewRuntimeGeneration++`; `viewHandler.removeCallbacksAndMessages(null)`
   (b) `resetThemeRenderLifecycle(...)`
   (c) `jsInterface.cancel()` + `renderGuard.invalidate()`
   (d) `moduleRegistry.disposeAll()` (снимает мосты, чистит контроллер)
   (e) ТОЛЬКО затем `webView.stopLoading()/loadUrl(about:blank)/clearHistory()/removeAllViews()/destroy()`
2. Не менять состав очищаемых полей — только порядок.
3. Подтвердить, что `ThemeBridgeHandler.cleanup()` и `ThemeWebController.cleanup()`
   корректно отрабатывают, когда WebView ещё жив.

### Проверка
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeWebControllerLifecycle*"` — зелёный.
- Ручная QA (LeakCanary при наличии): открыть/закрыть тему 10 раз, ротация — без утечек/крэшей.
- Логи: после закрытия фрагмента не должно быть «evaluateJavascript on destroyed WebView».

### Definition of Done
Мосты снимаются и runtime чистится до `destroy()`. Lifecycle-тест зелёный, нет регрессий памяти.

---

## ЭТАП 4 — Bridge: единообразный guard для чтения/навигации (S1)

Приоритет: СРЕДНИЙ. Риск: СРЕДНИЙ (поведенческий — согласовать). Тип: безопасность/детерминизм.

### Проблема (подтверждено)
Деструктивные методы в `ThemeJsInterface.kt` защищены `runProtected` (render token).
Но методы чтения/навигации НЕ защищены и идут через голый `runInUiThread`:
`showUserMenu`, `showReputationMenu`, `showPostMenu`, `openLink`, `setHistoryBody`,
`infiniteScroll`, `visiblePageChanged`, `postVisible` и т.п. Устаревший рендер может
дёрнуть меню/навигацию на свежей странице.
Также `rememberLinkSourceAnchor` (~строки 208-211) — СИНХРОННЫЙ и без guard (это намеренно,
т.к. вызывается до posted-callback; НЕ менять синхронность, см. комментарий в коде).

### Правка (поэтапно, осторожно)
1. Сначала ТОЛЬКО навигация по постам с postId, где stale-рендер реально опасен:
   обернуть `showUserMenu`, `showReputationMenu`, `showPostMenu` в `runProtected`
   (потребует добавить параметр `renderToken: String?` в сигнатуру JS-метода И в вызов в `theme.js`).
   ВНИМАНИЕ: менять сигнатуры на стороне JS (`theme.js`) синхронно — найти все вызовы Grep.
2. `openLink`/`infiniteScroll`/`visiblePageChanged`/`postVisible`/`setHistoryBody` —
   НЕ оборачивать в этом этапе (высокий риск регрессий навигации/истории). Пометить как
   `отложено` с обоснованием.
3. НЕ трогать `rememberLinkSourceAnchor` (синхронность критична).

### Проверка
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeJsInterfaceTest*"` — зелёный.
- Дополнить `ThemeJsInterfaceTest.kt`: вызов меню с невалидным/пустым токеном игнорируется,
  с валидным — проходит.
- Ручная QA: меню поста/пользователя/репутации работают при обычном открытии; после смены темы
  старые callbacks не срабатывают.

### Definition of Done
Меню-методы с postId защищены токеном; остальное явно отложено с обоснованием. Тесты зелёные.

> ВАЖНО: этот этап меняет контракт JS↔native. Если есть сомнение в полном покрытии вызовов
> в `theme.js` — остановиться и согласовать перед мерджем (правило 9).

---

## ЭТАП 5 — Performance: кэш composed CSS (P1)

Приоритет: СРЕДНИЙ. Риск: НИЗКИЙ. Тип: производительность.

### Проблема (подтверждено)
`TemplateManager.getThemeOverridesCss()` (`TemplateManager.kt` ~строки 66-71) на КАЖДЫЙ вызов
зовёт `cssComposer.compose()` (`TemplateCssComposer.kt` ~строки 33-47), который заново строит
сотни строк CSS. Вызывается 5-10 раз за один hybrid-refresh, в т.ч. внутри
`ThemeTemplate.renderSignature()` (`ThemeTemplate.kt` ~строки 174-182) — только чтобы
посчитать hash для ключа кэша фрагментов. Композиция нигде не мемоизируется.

### Правка
1. В `TemplateCssComposer` (или `TemplateManager`) добавить мемоизацию `compose()` по сигнатуре:
   ключ = (`getThemeType()`/isNight, активная палитра AMOLED/Sepia/SepiaBlue/Minimal,
   `FontController.getCurrentFontMode(...)`). Кэшировать строку + её hashCode.
2. Инвалидировать кэш при смене темы/палитры/шрифта. Найти, где меняются эти настройки
   (`observeThemeTypeFlow`, смена палитры/шрифта в настройках) и сбрасывать кэш там, либо
   пересчитывать ключ каждый раз и сравнивать (cache-by-key — безопаснее, без явной инвалидизации).
3. `renderSignature()` должен использовать закэшированный hashCode, а не пере-компоновать.

### Проверка
- `./gradlew :app:testStableDebugUnitTest --tests "*ThemeTemplate*"` — зелёный.
- Добавить тест: при неизменных (theme,palette,font) повторный `getThemeOverridesCss()`
  возвращает идентичную строку и НЕ пере-компонует (можно через spy/счётчик вызовов реальной композиции).
- Ручная QA: смена светлая/тёмная/палитра/шрифт корректно обновляет CSS темы.

### Definition of Done
`compose()` выполняется не чаще одного раза на уникальную (theme,palette,font) сигнатуру
в пределах сессии. Визуальных регрессий нет.

> Опционально (по согласованию): заменить SHA-256 per-post в ключе кэша фрагментов
> (`ThemeTemplate.kt` ~строки 706-729) на более дешёвый ключ (id + длина тела + ratingHash).
> Это отдельная подзадача с замером — НЕ делать без бенчмарка.

---

## ЭТАП 6 — Scroll restore: единый владелец решения (баг B2)

Приоритет: ВЫСОКИЙ по эффекту, но ВЫСОКИЙ риск. Тип: детерминизм. Делать ОСТОРОЖНО и поэтапно.

### Проблема (подтверждено)
Приоритет восстановления скролла размазан по ~12 точкам в Kotlin и JS. Конкурируют ДВА
JS-пути восстановления на одном NORMAL-открытии:
- `executeThemeScrollCommand("INITIAL_ANCHOR")` (армится из Kotlin)
- легаси-листенер `nativeEvents.DOM` (`theme.js` ~строки 2622-2654)
Гвард `if (window.__themeScrollCommandId) return;` зависит от порядка событий.
Плюс `scrollGeneration` не инвалидирует coalesced INITIAL_ANCHOR (передаётся `keepGeneration=true`),
а `ratio`-restore документированно небезопасен после infinite-prepend.

Целевой приоритет (как описано, но сейчас НЕ единый): explicit anchor → saved anchor →
bottom → ratio → no-op (JS `restoreThemeRefreshScrollAnchorOnce`, `theme.js` ~строки 1412-1477).

### Правка (минимальные безопасные шаги; НЕ переписывать restore целиком)
1. ШАГ 6.1 (диагностика, без смены поведения): добавить под debug-флагом единый лог
   «какой restore-путь сработал и почему» в обеих JS-ветках, чтобы зафиксировать реальные
   гонки на устройствах. Собрать данные перед изменением логики.
2. ШАГ 6.2 (устранение двойного пути): на NORMAL-открытии гарантировать, что выполняется
   РОВНО ОДИН armed command. Сделать легаси `nativeEvents.DOM`-restore (`theme.js` ~2639-2651)
   строго подчинённым: если Kotlin армировал команду (флаг `__themeScrollCommandId` или явный
   признак «команда будет»), DOM-листенер НЕ должен делать собственный `scrollToElementWithRetries`.
   Установить признак ДО возможного DOM-события (в lifecycle batch до DOM-ready).
3. ШАГ 6.3 (generation): для coalesced INITIAL_ANCHOR не передавать `keepGeneration=true`,
   либо инвалидировать конкурирующую refresh-цепочку через `cancelThemeAnchorScrollRetries()`
   в начале INITIAL_ANCHOR. Подтвердить Grep, что это не ломает refresh/back restore.

Каждый шаг — отдельный коммит и отдельная проверка. НЕ объединять.

### Проверка
- `./gradlew :app:testStableDebugUnitTest --tests "*Scroll*" --tests "*Anchor*"` — зелёный
  (`ThemeScrollAnchorControllerTest`, `ThemeRefreshScrollRestorePolicyTest`,
  `ThemeOpenScrollCoalescePolicyTest`, `ThemeDomLoadAnchorPolicyTest`).
- Ручная QA-матрица (КРИТИЧНО, проверять на каждом шаге):
  - открытие по ссылке на пост (findpost) → лендинг ровно на пост;
  - открытие с непрочитанным → первый непрочитанный;
  - возврат назад (back) → сохранённая позиция;
  - refresh у низа страницы → низ;
  - refresh в середине → тот же якорь;
  - infinite-scroll prepend → позиция не «уезжает».

### Definition of Done
На каждый рендер выполняется один детерминированный restore-путь; двойной DOM-restore устранён;
QA-матрица проходит без «прыжков». Если хоть один сценарий регрессирует — откатить шаг.

---

## ЭТАП 7 — Долгосрочно: ввести ThemeRenderSession (постепенно)

Приоритет: НИЗКИЙ/долгосрочный. Риск: НИЗКИЙ при правильном поэтапном вводе. НЕ переписывать приложение.

### Цель
Единый объект-источник валидности рендера. Со временем все проверки валидности
(token, generationId, lifecycle generation) должны опираться на него.

### Шаги
1. ШАГ 7.1: создать data-класс (новый файл
   `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderSession.kt`):

```
data class ThemeRenderSession(
    val topicId: Int,
    val page: Int,
    val renderGenerationId: Int,
    val bridgeToken: String,
    val themeSignature: String,
    val createdAt: Long
)
```

2. ШАГ 7.2: создавать сессию в одной точке начала рендера (там же, где минтуется token
   на `onDomContentComplete`, `ThemeFragmentWeb.kt` ~строка 1956) и хранить «текущую сессию».
   НЕ удалять существующие `ThemeRenderGuard`/`renderGenerationId` — на этом шаге сессия
   только ДУБЛИРУЕТ их значения для наблюдения (лог совпадения/расхождения под debug).
3. ШАГ 7.3 (позже, отдельным ТЗ): переключить `runProtected` и highlight-гварды на чтение
   из `ThemeRenderSession`, затем удалить дубли. Только после доказанной стабильности.

### Definition of Done (для Этапа 7 в рамках этого ТЗ)
Класс введён, сессия создаётся и логируется, существующая логика НЕ изменена.
Расхождения token/generation видны в debug-логах для будущей консолидации.

---

# СПРАВОЧНО: что УЖЕ защищено (не трогать без причины)

- Infinite scroll контаминация в основном предотвращена:
  `ThemeInfiniteScrollController.kt` — фильтр по topicId (строки ~95-99), проверка
  `session != infiniteSession || loaded.id != topicId` (строка 139), dedup-окно
  `markRequestAllowed` (1500мс), `cancelAll()` инкрементирует session, проверка
  `loadedPages[...]?.id == topicId`. ВЫВОД: отдельный этап не нужен; при правках scroll/lifecycle
  не сломать эти гварды. Если нужен аудит — только подтверждающий, без рефакторинга.
- Композиция HTML/CSS уже выполняется off-main: `ThemeUseCase.mapEntity` —
  `withContext(Dispatchers.Default)` (`ThemeUseCase.kt` ~строка 194). НЕ переносить на main.
- Lifecycle-JS уже батчится в один `evalJs`: `ExtendedWebView.kt` ~строки 585-591. Сохранить.
- Деструктивные bridge-вызовы уже под token-guard. JS-аргументы экранируются `JSONObject.quote`.
- Highlight single source of truth = `ThemePage.highlightTarget` + `renderGenerationId`
  (`TopicHighlightApply.kt`). Решение о цели — 100% Kotlin (`HighlightResolver.resolve`).
  НЕ переносить решение в JS.

---

# ПОРЯДОК РАБОТЫ И КОММИТЫ

- Один этап (а внутри Этапа 6 — один ШАГ) = один коммит.
- Формат сообщения коммита: `theme(stabilize): <этап N> <краткое что/зачем>`.
- Перед каждым коммитом: `./gradlew :app:compileStableDebugKotlin` и релевантные unit-тесты зелёные.
- НЕ пушить и НЕ мерджить без явного запроса владельца.
- Видимые пользователю поведенческие изменения (Этап 4 расширение, Этап 6) — согласовать.

# ОБЩИЙ ЧЕК-ЛИСТ РУЧНОГО QA (после Этапов 1-6)

Android 8-15; светлая/тёмная/Material You/AMOLED/Sepia; ротация; восстановление процесса;
back stack; refresh; infinite scroll; большие темы; внешние ссылки; просмотр изображений; поиск.
Особое внимание: подсветка гаснет за ~2с (Этап 1), скролл-лендинг детерминирован (Этап 6),
нет утечек при открытии/закрытии темы (Этап 3).

# КАРТА БАГОВ → ЭТАП

| ID | Баг | Файл:строки | Этап |
|---|---|---|---|
| B1 | Рестарт таймера затухания highlight | ThemeWebController.kt:1392-1396, :1469 | 1 |
| P2 | Лишние/двойные highlight-eval | ThemeWebController.kt:1439-1440,1471-1472 | 2 |
| L1 | destroy() до снятия мостов | ThemeFragmentWeb.kt:1745 vs :1758 | 3 |
| S1 | Чтение/меню без token-guard | ThemeJsInterface.kt (showUserMenu и др.) | 4 |
| P1 | CSS пере-компонуется каждый рендер | TemplateManager.kt:66-71, TemplateCssComposer.kt:33-47 | 5 |
| B2 | Двойной/недетерминированный restore | theme.js:2622-2654, 1412-1477 | 6 |
| B3 | Фрагментация generation/token | три системы (раздел 3) | 7 |
| B4 | Infinite contamination | ThemeInfiniteScrollController.kt | уже защищено (аудит-only) |
