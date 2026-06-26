# Theme WebView Navigation / Scroll / Anchor / Highlight Audit — DRAFT

Дата: 2026-06-24
Проект: PROPDA / ForPDA Android client
Статус: черновик диагностики, без исправлений кода

## 1. Область аудита

Проверяется работа открытой темы:

- открытие темы;
- настройка перехода к первому непрочитанному;
- WebView render;
- anchor scroll;
- highlight первого непрочитанного или последнего прочитанного поста;
- переходы по ссылкам внутри приложения;
- back navigation;
- восстановление исходного поста;
- сохранение и восстановление scroll position;
- race-condition guards и их избыточность.

## 2. Ограничения текущей диагностики

В workspace уже есть много незакоммиченных изменений и новых файлов. Поэтому диагностика выполнялась в режиме read-only.

Gradle compile/test команды были заблокированы safe bash policy CodexPro. Требуется отдельная локальная проверка:

```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:testStableDebugUnitTest
```

## 3. Подтверждённая цепочка открытия темы

Основная цепочка unread-open:

```text
TopicOpenTargetResolver
-> TopicUnreadOpenPolicy
-> ThemePage parser anchor fields
-> ThemeFragmentWeb.onPageComplete
-> ThemeScrollCommand.INITIAL_ANCHOR
-> ThemeWebController.buildScrollCommandAction
-> ThemeJsApi.executeScrollCommand
-> theme.js executeThemeScrollCommand
-> scrollToElementWithRetries
```

Дополнительные участники:

- TopicOpenScrollRestorePolicy
- ThemeOpenScrollCoalescePolicy
- HighlightResolver
- TopicHighlightApply
- ThemeWebController.reapplyTopicHighlight
- ThemeBackRestoreUrlPolicy
- ThemeLinkNavigationPolicy
- theme.js refresh/anchor runtime

## 4. Главная архитектурная проблема

Scroll, anchor и highlight управляются несколькими независимыми механизмами:

1. URL navigation: view=getnewpost, view=getlastpost, view=findpost, st, entry hash.
2. Parser anchor resolution: HTML unread marker, redirect hash, canonical link, query post id, fallback entry.
3. Kotlin scroll commands: INITIAL_ANCHOR, REFRESH_RESTORE, ANCHOR, END_ANCHOR_OR_BOTTOM, BOTTOM.
4. JavaScript scroll runtime: resolveThemeInitialAnchorName, scrollToElementWithRetries, restoreThemeRefreshScrollAnchorWithRetries, restoreThemeToBottomAfterRefreshWithRetries.
5. Native/WebView reveal guards: ThemeOpenScrollCoalescePolicy, watchdog, blank verify, DOM probe, scroll stuck reveal.

Вывод: система содержит много защит от race-condition, но часть защит перекрывает друг друга и усложняет поведение.

## 5. Finding F-01 — FIRST_UNREAD может падать в fallback на неверный пост

Severity: High

Файлы:

- TopicUnreadOpenPolicy.kt
- TopicOpenTargetResolver.kt
- HighlightResolver.kt
- ThemeFragmentWeb.kt
- theme.js

Наблюдение:

TopicUnreadOpenPolicy.resolveGetNewPostAnchor использует цепочку fallback-источников. Если HTML unread marker не найден, а redirect hash признан bottom/top/all-read hint, код может перейти к fallback entry.

Это объясняет симптом: при открытии непрочитанной темы с настройкой к первому непрочитанному открывается первое сообщение на последней странице.

Статус: высокая вероятность, но нужна runtime-диагностика по конкретному URL.

Рекомендация:

Добавить diagnostic trace для каждого выхода из resolveGetNewPostAnchor: finalUrl, entryIds, redirectHashId, hasUnreadTarget, reason.

## 6. Finding F-02 — Highlight зависит от корректной страницы

Severity: High

Файлы:

- HighlightResolver.kt
- TopicHighlightApply.kt
- ThemeWebController.kt

Наблюдение:

HighlightResolver применяет FirstUnread только если firstUnreadPostId находится в pagePostIds.

Если нужный пост не находится на текущей странице, resolver падает на LastRead, explicit, fallback или None.

Вывод:

Баг с подсветкой может быть следствием неверно открытой страницы, а не отдельной ошибкой highlight.

## 7. Finding F-03 — Конкуренция Kotlin INITIAL_ANCHOR и JS DOM initial anchor

Severity: Medium/High

Файлы:

- ThemeFragmentWeb.kt
- ThemeWebController.kt
- ThemeOpenScrollCoalescePolicy.kt
- theme.js

Наблюдение:

Kotlin создаёт ThemeScrollCommand.initialAnchor на pageComplete. В theme.js при NORMAL_ACTION также существует legacy DOM-путь через resolveThemeInitialAnchorName и scrollToElementWithRetries.

JS пытается не запускать DOM initial anchor, если уже есть command id, но это зависит от порядка событий.

Риск:

Два независимых initial scroll пути могут конкурировать.

Рекомендация:

Сделать один владелец initial anchor scroll. Предпочтительно Kotlin ThemeScrollCommand. JS DOM initial anchor оставить fallback-only.

## 8. Finding F-04 — Back/source-anchor цепочка требует проверки

Severity: Critical until verified

Файлы:

- ThemeJsInterface.kt
- ThemeWebCallbacks.kt
- ThemeWebController.kt
- theme.js

Наблюдение:

ThemeJsInterface.rememberLinkSourceAnchor вызывает onLinkSourceAnchorCaptured. В ThemeWebCallbacks дефолтная реализация пустая. Поиск пока не показал явный override, который сохраняет payload.

ThemeWebController при переходе по ссылке ожидает source anchor через consumeLinkSourceAnchorFor.

Риск:

Если source-anchor реально не сохраняется, back navigation не имеет точного исходного postId и падает на менее точный restore по viewport, ratio или savedY.

Это может объяснять потерю исходного поста при возврате назад.

Статус: нужно подтвердить компиляцией и дополнительным поиском.

## 9. Finding F-05 — Возможный разрыв контракта ThemeWebController и ThemeViewModel

Severity: Critical until verified

ThemeWebController вызывает методы ThemeViewModel:

- consumeLinkSourceAnchorFor
- beginScrollCommand
- getPendingScrollCommand

Поиск по ThemeViewModel.kt пока не нашёл реализацию этих методов.

Возможные причины:

1. ограничение search tool;
2. методы находятся в другом слое;
3. текущая ветка не компилируется;
4. часть изменений применена не полностью.

До compile verification нельзя начинать исправления навигации.

## 10. Finding F-06 — Слишком много race-condition guards

Severity: Medium

Кандидаты на отдельную инвентаризацию:

- ThemeOpenScrollCoalescePolicy
- TopicOpenScrollRestorePolicy
- ThemeUnreadHybridAnchorGuardPolicy
- ThemeMissedPageLifecyclePolicy
- ThemeRenderCompletePolicy
- ThemeRenderSettledPolicy
- JS themeAnchorScrollGeneration
- JS __themeScrollCommandId
- JS unreadInitialAnchorPending
- JS retry arrays for restore/anchor/bottom

Рекомендация:

Создать отдельную задачу Theme Navigation Race Guard Simplification.

Цель:

1. составить таблицу всех guard-ов;
2. указать, какой баг каждый закрывает;
3. покрыть текущее поведение тестами;
4. удалить дубли;
5. оставить один state-machine для render/scroll lifecycle.

## 11. Предварительный план исправлений

### Phase 0 — Verification Gate

- выполнить compileStableDebugKotlin;
- выполнить theme-related unit tests;
- подтвердить наличие или отсутствие методов ThemeViewModel;
- не начинать рефакторинг до зелёной компиляции.

### Phase 1 — Diagnostics only

Добавить расширенные логи без изменения поведения:

- TopicOpenTargetResolver.resolve;
- TopicUnreadOpenPolicy.resolveGetNewPostAnchor;
- HighlightResolver.resolve;
- ThemeFragmentWeb pageComplete;
- executeThemeScrollCommand;
- rememberLinkSourceAnchor / consumeLinkSourceAnchorFor.

### Phase 2 — FIRST_UNREAD stabilization

- запретить опасный fallback для genuine unread open без явного reason;
- при off-page unread target делать controlled reload или повторный getnewpost resolution;
- не позволять saved scroll restore перебивать FIRST_UNREAD.

### Phase 3 — Highlight stabilization

- логировать unread_off_page как warning;
- не делать last-post fallback для unread-open, пока не доказано all-read;
- проверять наличие target post в DOM перед JS highlight.

### Phase 4 — Back navigation stabilization

- сделать source-anchor сохранение обязательным;
- логировать source_anchor_missing;
- хранить native snapshot для возврата назад независимо от JS TTL.

### Phase 5 — Race guard simplification

- выбрать одного владельца initial anchor scroll;
- оставить Kotlin ThemeScrollCommand как основной путь;
- JS DOM initial anchor перевести в fallback-only;
- удалить дублирующие reveal guard paths после тестового покрытия.

## 12. Что нельзя исправлять сразу

Пока нельзя сразу:

- удалять JS retry-механизмы;
- переписывать ThemeViewModel;
- упрощать ThemeOpenScrollCoalescePolicy без тестов;
- менять fallback в resolveGetNewPostAnchor без runtime-логов;
- трогать infinite scroll contamination guards.

## 13. Следующие действия

1. Выполнить compile verification.
2. Если compile падает на missing methods, сначала восстановить контракт ThemeViewModel/ThemeWebController.
3. Если compile зелёный, добавить runtime diagnostics.
4. После подтверждения runtime ветки бага согласовать первый safe fix.
