# План рефакторинга и модернизации ForPDA (ProPDA / ForPDA 2)

> Документ предназначен для исполнителя-ИИ, который будет выполнять изменения. Он самодостаточен: содержит точные пути, номера строк, имена классов, текущие версии и пошаговые инструкции. Все факты проверены по реальному коду репозитория `/Users/j.golt/Documents/Cursor01/ForPDA-master` на дату составления.
>
> **Главное правило проверки сборки во всём документе:** используйте wrapper и вариант `stable`:
>
> ```bash
> ./gradlew :app:assembleStableRelease
> ```
>
> Обычный `assembleRelease`/`*StoreRelease` падает **намеренно** из-за отсутствующих ключей. См. `app/build.gradle:239-261` (блок `gradle.taskGraph.whenReady`): сборка `stable` требует `keystore.parallel.properties` (иначе исключение на `:247-251`), а официальный `storeRelease` требует RELEASE-ключей (исключение на `:256-260`). Вариант `stable` при отсутствии `keystore.parallel.properties` падает тоже — поэтому для **локальной** проверки сборки без ключей надёжнее всего использовать debug-вариант `./gradlew :app:assembleStableDebug` ИЛИ просто компиляцию `./gradlew :app:compileStableReleaseKotlin`. **Важно:** в задании указано `:app:assembleStableRelease` — используйте его, но если он упадёт на отсутствии `keystore.parallel.properties`, переключитесь на `:app:assembleStableDebug` для проверки компиляции (это не меняет смысла проверки — нас интересует успешная компиляция Kotlin/KSP, а не подпись).

---

## Оглавление

- [0. Контекст: приложение и стек](#0-контекст-приложение-и-стек)
- [Рекомендуемый порядок выполнения и риски](#рекомендуемый-порядок-выполнения-и-риски)
- [SECTION 1 — Архитектурный долг](#section-1--архитектурный-долг)
  - [1.1 Декомпозиция god-классов](#11-декомпозиция-god-классов)
  - [1.2 Удаление мёртвой зависимости Realm](#12-удаление-мёртвой-зависимости-realm)
  - [1.3 Миграция kapt → KSP (Hilt, Room)](#13-миграция-kapt--ksp-hilt-room)
  - [1.4 Kotlin 1.9.25 → 2.x + Compose Compiler plugin](#14-kotlin-1925--2x--compose-compiler-plugin)
  - [1.5 Version catalog (gradle/libs.versions.toml)](#15-version-catalog-gradlelibsversionstoml)
- [SECTION 2 — Хрупкость парсинга](#section-2--хрупкость-парсинга)
  - [2.1 Замена regex/custom-DOM на Jsoup](#21-замена-regexcustom-dom-на-jsoup)
  - [2.2 Тесты парсеров на реальных HTML-фикстурах](#22-тесты-парсеров-на-реальных-html-фикстурах)
- [SECTION 3 — Производительность чтения](#section-3--производительность-чтения)
  - [3.1 Упрощение WebView render pipeline](#31-упрощение-webview-render-pipeline)
  - [3.2 Частичная миграция нативных списков на Compose](#32-частичная-миграция-нативных-списков-на-compose)
- [SECTION 4 — Современный UX](#section-4--современный-ux)
  - [4.1 Material You / Dynamic Color](#41-material-you--dynamic-color)
  - [4.2 Edge-to-edge](#42-edge-to-edge)
  - [4.3 Predictive back gesture](#43-predictive-back-gesture)
- [SECTION 5 — Оффлайн-чтение](#section-5--оффлайн-чтение)
  - [5.1 Сохранение тем/статей для чтения оффлайн](#51-сохранение-темстатей-для-чтения-оффлайн)
- [Приложение A — Список проверенных фактов и исправления](#приложение-a--список-проверенных-фактов-и-исправления)

---

## 0. Контекст: приложение и стек

**Приложение.** Неофициальный Kotlin-клиент российского форума 4pda.to (приложение «ProPDA / ForPDA 2»). 100% Kotlin. Контент тем/статей/QMS рендерится в WebView из HTML, который строится из распарсенных моделей через шаблонизатор (minitemplator) + CSS из `app/src/main/assets/forpda/styles/`. Парсинг страниц — собственный (regex + кастомный DOM), не Jsoup.

**Текущий стек (проверено по реальным файлам):**

| Параметр | Значение | Источник (проверено) |
|---|---|---|
| Kotlin | `1.9.25` | `build.gradle:8,14`; `app/build.gradle:7` |
| AGP (Android Gradle Plugin) | `8.11.1` | `build.gradle:7` |
| Gradle wrapper | `8.13` | `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl=...gradle-8.13-bin.zip`) |
| compileSdk / targetSdk | `35` / `35` | `app/build.gradle:40,68` |
| minSdk | `24` | `app/build.gradle:67` |
| Java / jvmTarget | `17` | `app/build.gradle:185-190` |
| Hilt | `2.51.1` через **kapt** | `build.gradle:9`; `app/build.gradle:9,337-338`, плюс `kapt 'androidx.hilt:hilt-compiler:1.2.0'` на `:340` |
| Room | `2.6.1` через **kapt** | `app/build.gradle:297-299` (`kapt "androidx.room:room-compiler:2.6.1"`) |
| OkHttp | `4.12.0` (+ `okhttp-brotli`) | `app/build.gradle:315-316` |
| Coil | `2.7.0` | `app/build.gradle:318` |
| Cicerone | `6.6` | `app/build.gradle:329` |
| Compose BOM | `2024.12.01` | `app/build.gradle:301` |
| kotlinCompilerExtensionVersion | `1.5.15` | `app/build.gradle:180-182` |
| Serialization | kotlinx-serialization-json `1.6.3`, plugin `1.9.25` | `app/build.gradle:7,320` |
| Coroutines | `1.8.1` | `app/build.gradle:319` |
| Detekt | `1.23.4` | `app/build.gradle:10,359` |
| Тесты | JUnit4 `4.13.2`, Robolectric `4.14.1`, MockK `1.13.13`, Turbine `1.1.0`, coroutines-test `1.8.1`, room-testing `2.6.1` | `app/build.gradle:342-348` |
| Realm | classpath `io.realm:realm-gradle-plugin:10.19.0` (**мёртвая, не применяется**) | `build.gradle:10` |
| Версия приложения | `versionCode 350`, `versionName 2.9.4` (в build.gradle); в `AndroidManifest.xml` — `versionCode 349`, `versionName 2.9.3` (перекрываются build.gradle) | `app/build.gradle:47-50`; `AndroidManifest.xml:4-5` |

**Структура UI.** Один пакет приложения `forpdateam.ru.forpda`. Навигация — Cicerone (`presentation/Screen.kt` — sealed-классы экранов). Активити — **ровно 4** (см. §4.2). Один `@Composable`-экран — `ui/compose/screens/NotesScreen.kt`, хост — `ui/fragments/notes/NotesComposeFragment.kt`.

**Все числа из исходного задания, которые оказались НЕВЕРНЫМИ**, перечислены в [Приложении A](#приложение-a--список-проверенных-фактов-и-исправления). Ключевые: путь `ArticleInteractor.kt`, количество Activity (4, не 6), названия палитр (одна — `CLASSIC_4PDA`, не «MINIMAL»), номер строки keystore-исключения, и **уже существующий, но не подключённый `gradle/libs.versions.toml`**.

---

## Рекомендуемый порядок выполнения и риски

Зависимости между пакетами и порядок (от безопасного фундамента к рискованному):

| # | Пакет | Риск | Зависит от | Примечание |
|---|---|---|---|---|
| 1 | **1.2** Удаление Realm | 🟢 low | — | Чистое удаление мёртвого кода; делать первым. |
| 2 | **1.5** Version catalog | 🟢 low | — | Можно инкрементально; уже есть устаревший toml — переписать и подключить. |
| 3 | **2.2** Тесты парсеров (фикстуры) | 🟢 low | — | Готовит «золотые» тесты ДО любых изменений парсеров. Делать перед 2.1 и перед 1.1-декомпозицией парсеров. |
| 4 | **1.1** Декомпозиция god-классов | 🟡 medium | желательно после 2.2 для парсеров | Механический рефакторинг без смены поведения; делать по одному классу. |
| 5 | **1.3** kapt → KSP | 🟡 medium | 1.2, 1.5 (желательно) | Строго ДО 1.4. |
| 6 | **1.4** Kotlin 2.x + Compose Compiler plugin | 🔴 high | **строго после 1.3** | Самый рискованный пакет. K2-строгость, совместимость библиотек. |
| 7 | **2.1** Jsoup-миграция парсеров | 🟡 medium | **после 2.2** | По одному парсеру, со старым как fallback за флагом. |
| 8 | **3.1** Упрощение WebView pipeline | 🟡 medium | независимо; согласовать с TOP-10 в `PERF_DIAGNOSIS.md` | Сначала измерить, потом упрощать. |
| 9 | **4.2** Edge-to-edge | 🟡 medium | — | Обязательно при targetSdk 35 на Android 15. |
| 10 | **4.1** Material You / Dynamic Color | 🟢 low | — | Только нативный UI; WebView-CSS не трогать (см. границу). |
| 11 | **4.3** Predictive back | 🟢 low | — | Opt-in + миграция OnBackPressed. |
| 12 | **3.2** Списки на Compose | 🟡 medium | желательно после 1.4 (не строго) | По одному списочному экрану. |
| 13 | **5.1** Оффлайн-чтение | 🔴 high | опирается на Room (1.3), кэши статей | Большая фича; поэтапно (data → save → list → render → images → limits). |

**Логика порядка:** 1.2 и 1.5 — безопасный фундамент; 2.2 (тесты) фиксируют поведение парсеров до 2.1 и до декомпозиции; 1.3 строго до 1.4 (KSP должен быть включён до перехода на Kotlin 2.x, иначе двойная миграция); 4.x можно делать параллельно после фундамента; 5.1 — последней, опираясь на уже отрефакторенный Room/DI.

---

## SECTION 1 — Архитектурный долг

### 1.1 Декомпозиция god-классов

**(a) Цель / зачем.** Снизить когнитивную нагрузку и риск регрессий в крупнейших файлах. Это **чисто механический рефакторинг без изменения поведения**: выносим связные группы ответственности в отдельные классы (как уже сделано в проекте). Никаких изменений логики, сигнатур публичного API экранов или порядка вызовов.

**(b) Файлы и текущие размеры (проверено `wc -l`):**

| Файл | Строк |
|---|---|
| `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt` | **4766** |
| `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/ArticleParser.kt` | **4162** |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` | **3471** |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt` | **2839** |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt` | **2763** |
| `app/src/main/java/forpdateam/ru/forpda/model/interactors/news/ArticleInteractor.kt` | **2470** ⚠️ путь содержит `/news/` |
| `app/src/main/java/forpdateam/ru/forpda/ui/TemplateManager.kt` | **2006** |

**Существующий паттерн извлечения (использовать ОБЯЗАТЕЛЬНО, не изобретать новый):**

- Под `presentation/theme/` уже вынесено ~50 классов в стиле `*Policy` / `*Coordinator` / `*Controller` / `*Handler` / `*StateMachine`. Примеры (проверено `ls`): `ThemeLoadStateMachine.kt`, `ThemePaginationController.kt`, `ThemeScrollAnchorController.kt`, `ThemePostActionHandler.kt`, `ThemeRenderGuard.kt`, `ThemeUrlPolicy.kt`, `ThemeInfiniteScrollController.kt`, `ThemeHistoryController.kt`, `TopicOpenIntentClassifier.kt`, `ThemeJsInterface.kt`.
- Под `ui/fragments/theme/modules/` уже вынесены UI-модули фрагмента (проверено `ls`): `ThemeWebController.kt`, `ThemeWebViewHost.kt`, `ThemeScrollHandler.kt`, `ThemeToolbarScrollController.kt`, `ThemeImeInsetsController.kt`, `ThemeFabCoordinator.kt`, `ThemeBridgeHandler.kt`, `ThemeUiBinder.kt`, `ThemeLoadingIndicator.kt`, `ThemePaginationHandler.kt`, `ThemeStyleHandler.kt`, `ThemeJsApi.kt`, `ThemeUiModule.kt`, `BottomRefreshGestureController.kt`.

**(c) Пошаговая стратегия (инкрементально, по одному классу, по одной ответственности):**

Для каждого god-класса повторять цикл:
1. Зафиксировать «золотые» тесты (для парсеров — §2.2; для ViewModel — существующие тесты в `app/src/test/...`).
2. Найти связную группу методов/полей (одна ответственность). Завести `private val xxx = XxxController(...)` в god-классе.
3. Перенести тело методов в новый класс, оставив в god-классе тонкие делегирующие обёртки (то же имя метода → `xxx.doIt(...)`). Сохранять сигнатуры.
4. Скомпилировать (`./gradlew :app:compileStableDebugKotlin`), прогнать тесты.
5. Закоммитить ОДНУ ответственность. Повторить.

**Приоритет и предлагаемые новые классы:**

1. **`ArticleParser.kt` (4162)** — делать ПЕРВЫМ среди парсеров и только после §2.2 (golden-тесты). Разбить по логическим секциям статьи. Предлагаемые классы (новый пакет `model/data/remote/api/news/parser/`):
   - `ArticleHeaderParser` (заголовок, дата, автор, теги),
   - `ArticleBodyParser` (тело статьи, спойлеры, медиа),
   - `ArticlePollParser` (опросы/poll-блоки),
   - `ArticleCommentParser` (дерево комментариев),
   - `ArticleAttachmentsParser` (вложения/галерея).
   `ArticleParser` остаётся фасадом, делегирующим в под-парсеры.
2. **`ThemeViewModel.kt` (4766)** — продолжить существующий паттерн `presentation/theme/*`. Кандидаты на вынос (если ещё внутри ViewModel): `ThemePostInteractionCoordinator`, `ThemeReplyFlowController`, `ThemePollController`, `ThemeModerationActionHandler`. Сверяться со списком уже существующих классов, чтобы не дублировать.
3. **`ArticleContentFragment.kt` (3471)** — по образцу `ui/fragments/theme/modules/`. Кандидаты (новый пакет `ui/fragments/news/details/modules/`): `ArticleWebViewHost`, `ArticleToolbarController`, `ArticleCommentsUiBinder`, `ArticleReplyPanelController`, `ArticleInsetsController`.
4. **`TemplateManager.kt` (2006)** — выделить `TemplatePaletteResolver` (логика палитр, см. `:54-66`), `TemplateAssetLoader`, `TemplateCssComposer`.
5. **`ThemeFragmentWeb.kt` (2839)**, **`QmsChatFragment.kt` (2763)** — аналогично, по образцу modules.
6. **`ArticleInteractor.kt` (2470)** — вынести `ArticleNetworkLoader`, `ArticleCacheCoordinator`, `ArticleDeferredExtrasScheduler` (deferred-логика уже частично описана в `PERF_DIAGNOSIS.md`, методы `:508-628`).

**(d) Подводные камни / риски.**
- Не менять порядок вызовов и побочные эффекты (особенно в WebView pipeline — гонки рендеринга чувствительны к порядку, см. §3.1).
- Приватные поля, к которым обращаются несколько методов, передавать в новый класс через конструктор или callback, а не дублировать состояние.
- В Kotlin внутренние `inner`/extension-функции god-класса при переносе теряют доступ к `this` — заменять явной передачей зависимостей.
- Не объединять рефакторинг с любым другим пакетом (особенно 1.4) в одном коммите.

**(e) Проверка.** `./gradlew :app:assembleStableRelease` (или `:app:assembleStableDebug` локально) + `./gradlew :app:testStableDebugUnitTest`. Поведение должно быть идентичным; визуально проверить открытие статьи, темы, QMS-диалога.

**(f) Откат.** Каждая ответственность — отдельный коммит → `git revert <commit>` точечно. Так как делегаты сохраняют сигнатуры, откат не задевает остальной код.

---

### 1.2 Удаление мёртвой зависимости Realm

**(a) Цель / зачем.** В `build.gradle:10` есть classpath-плагин `io.realm:realm-gradle-plugin:10.19.0`, но плагин **нигде не применяется** и Realm не используется. Это мёртвый вес: тянет артефакты в build-classpath и сбивает с толку.

**(b) Доказательство «мёртвости» (проверено grep):**
- `build.gradle:10` — единственный реальный реф на плагин (`classpath 'io.realm:realm-gradle-plugin:10.19.0'`).
- **Нигде нет** `apply plugin: 'realm'` / `id 'io.realm'` в применённом виде (`app/build.gradle plugins{}` — `:4-11`, Realm отсутствует).
- **Нет** ни одного `RealmObject`, `import io.realm.*` в исходниках `app/src/main/java`.
- Остальные совпадения «realm» — **ложные**:
  - `app/src/main/java/forpdateam/ru/forpda/common/MimeTypeUtil.kt` — строка `"application/vnd.rn-realmedia"` (MIME, не Realm DB).
  - `app/proguard-rules.pro` — правила `-keep ... io.realm.RealmObject` и `-dontwarn io.realm.**` (мёртвые правила, можно удалить).
  - `app/src/main/java/forpdateam/ru/forpda/presentation/forum/ForumViewModel.kt` — слово «Realm» только в **комментариях** (исторический комментарий про старый кэш).
  - `gradle/libs.versions.toml` — есть `realm = "10.19.0"` и plugin `realm` (тоже мёртвые, удалить в рамках §1.5).

**(c) Шаги:**
1. В `build.gradle` удалить строку 10:
   ```groovy
   classpath 'io.realm:realm-gradle-plugin:10.19.0'
   ```
2. В `app/proguard-rules.pro` удалить блок Realm-правил (строки с `io.realm.annotations.RealmModule`, `io.realm.internal.Keep`, `-dontwarn io.realm.**`, `* extends io.realm.RealmObject`).
3. (Опционально, относится к §1.5) В `gradle/libs.versions.toml` удалить `realm = "10.19.0"` (`:14`) и плагин `realm = { id = "io.realm", ... }` (`:159`).
4. Привести в порядок исторический комментарий в `ForumViewModel.kt`, если он вводит в заблуждение (не обязательно).

**(d) Подводные камни.** Убедиться, что ProGuard-правила Realm действительно не нужны (они не нужны — Realm-классов нет). Не трогать строку MIME в `MimeTypeUtil.kt`.

**(e) Проверка.** `./gradlew :app:assembleStableRelease` (release включает R8/ProGuard — проверит, что удаление правил не ломает shrink). Дополнительно: `./gradlew :app:dependencies | grep -i realm` — должно быть пусто.

**(f) Откат.** Одна-две строки → `git revert` коммита или ручное восстановление.

---

### 1.3 Миграция kapt → KSP (Hilt, Room)

**(a) Цель / зачем.** kapt медленный (генерирует Java-stubs) и не развивается. KSP быстрее и официально поддержан Hilt и Room. Это обязательный шаг ПЕРЕД переходом на Kotlin 2.x (§1.4): на Kotlin 2.x kapt работает в legacy-режиме, а KSP — нативно.

**(b) Текущие kapt-использования (проверено `app/build.gradle`):**
- `app/build.gradle:8` — `id 'kotlin-kapt'` (применение плагина).
- `app/build.gradle:299` — `kapt "androidx.room:room-compiler:2.6.1"`.
- `app/build.gradle:338` — `kapt 'com.google.dagger:hilt-android-compiler:2.51.1'`.
- `app/build.gradle:340` — `kapt 'androidx.hilt:hilt-compiler:1.2.0'` (Hilt-WorkManager).
- **databinding не используется** — в `buildFeatures` только `viewBinding`, `buildConfig`, `compose` (`:174-178`). Аннотационных процессоров для databinding нет.

Итого ровно **3** kapt-зависимости + плагин kapt. Других kapt-процессоров нет (проверено: `kapt` встречается только в этих строках).

**(c) Точные изменения.**

KSP — отдельный плагин, версия которого привязана к версии Kotlin: формат `<kotlin>-<ksp>`. Для Kotlin **1.9.25** используйте `com.google.devtools.ksp` версии **`1.9.25-1.0.20`** (последняя KSP1 под 1.9.25). ВАЖНО: пока остаёмся на Kotlin 1.9.25 (Kotlin 2.x — это §1.4), поэтому здесь берём KSP под 1.9.25.

1. **Объявить плагин KSP в classpath или через plugins block.** Так как проект использует legacy-плагины через `buildscript{}` в корневом `build.gradle`, проще добавить KSP в `app/build.gradle` `plugins{}` с явной версией:
   ```groovy
   // app/build.gradle, блок plugins { ... } (:4-11)
   plugins {
       id 'com.android.application'
       id 'org.jetbrains.kotlin.android'
       id 'org.jetbrains.kotlin.plugin.serialization' version '1.9.25'
       id 'com.google.devtools.ksp' version '1.9.25-1.0.20'   // заменяет kotlin-kapt
       id 'dagger.hilt.android.plugin'
       id 'io.gitlab.arturbosch.detekt' version '1.23.4'
   }
   ```
   Удалить строку `id 'kotlin-kapt'` (`:8`).

   > Примечание: `settings.gradle` уже содержит `pluginManagement { repositories { ... gradlePluginPortal() } }`, поэтому плагин с версией в `plugins{}` резолвится без правок repositories.

2. **Заменить `kapt(...)` на `ksp(...)`** в `dependencies`:
   ```groovy
   // было (:299)
   kapt "androidx.room:room-compiler:2.6.1"
   // стало
   ksp "androidx.room:room-compiler:2.6.1"

   // было (:338)
   kapt 'com.google.dagger:hilt-android-compiler:2.51.1'
   // стало
   ksp 'com.google.dagger:hilt-android-compiler:2.51.1'

   // было (:340)
   kapt 'androidx.hilt:hilt-compiler:1.2.0'
   // стало
   ksp 'androidx.hilt:hilt-compiler:1.2.0'
   ```

3. **Перенести аргументы Room из kapt в ksp.** Сейчас Room экспортирует схему: `exportSchema = true` (`AppDatabase` в `entity/db/notes/NotesDatabase.kt:33`). Если в `app/build.gradle` ЕСТЬ блок `kapt { arguments { arg("room.schemaLocation", ...) } }` — заменить на:
   ```groovy
   ksp {
       arg("room.schemaLocation", "$projectDir/schemas")
       arg("room.incremental", "true")
   }
   ```
   > ⚠️ Проверить: в текущем `app/build.gradle` блока `kapt {}` с `room.schemaLocation` НЕ обнаружено (kapt используется только как процессор). Если schemaLocation нигде не задан, а `exportSchema=true`, Room выдаёт предупреждение, но не ошибку. Рекомендуется добавить `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, чтобы схемы версии 6 экспортировались в `app/schemas/` (пригодится для §5.1 и Room-миграций). Создать каталог `app/schemas/` и добавить его в VCS.

4. Hilt-плагин (`dagger.hilt.android.plugin`) остаётся — он работает поверх KSP без изменений (Hilt 2.51.1 поддерживает KSP).

**(d) Подводные камни.**
- **Hilt + KSP:** требуется Hilt ≥ 2.48; у нас 2.51.1 — ОК. Никаких доп. флагов не нужно.
- **Room schemaLocation** переезжает из `kapt{arguments{...}}` в `ksp{arg(...)}` — другой синтаксис (`arg("k","v")`, не `argument`).
- Если где-то использовался `kapt.correctErrorTypes` или `kapt.useBuildCache` в `gradle.properties` — эти флаги для KSP не нужны; оставить их безвредно, но лучше удалить связанные с kapt.
- KSP не генерирует Java-stubs → если какой-то Java-код полагался на kapt-генерируемые классы в Java (здесь проект 100% Kotlin — не проблема).
- Hilt-WorkManager (`androidx.hilt:hilt-compiler:1.2.0`) тоже на KSP с 1.1.0+ — ОК.

**(e) Проверка.**
```bash
./gradlew clean :app:assembleStableRelease
```
Что проверять: сборка проходит; в `app/build/generated/ksp/` появляются сгенерированные Hilt/Room классы (`*_HiltModules`, `*_Impl` для DAO). Прогнать `./gradlew :app:testStableDebugUnitTest` (Room-testing использует сгенерированные DAO). Убедиться, что `app/build/generated/source/kapt/` больше не создаётся.

**(f) Откат.** Вернуть `id 'kotlin-kapt'`, заменить `ksp(...)` обратно на `kapt(...)`, убрать `id 'com.google.devtools.ksp'` и блок `ksp{}`. Один коммит → `git revert`.

---

### 1.4 Kotlin 1.9.25 → 2.x + Compose Compiler plugin

> 🔴 **Самый высокорисковый пакет. Выполнять СТРОГО ПОСЛЕ §1.3 (KSP) и желательно после §1.5 (catalog).** Делать в отдельной ветке.

**(a) Цель / зачем.** С Kotlin 2.0+ Compose-компилятор поставляется как Gradle-плагин `org.jetbrains.kotlin.plugin.compose`, а ручной `kotlinCompilerExtensionVersion` больше не нужен и не поддерживается. Переход открывает K2-компилятор (быстрее, строже), новые возможности языка.

**(b) Текущее состояние (проверено):**
- Kotlin `1.9.25` (`build.gradle:8,14`, `app/build.gradle:7`).
- Compose BOM `2024.12.01` (`app/build.gradle:301`).
- `composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }` (`app/build.gradle:180-182`).
- Плагин serialization с версией `1.9.25` (`app/build.gradle:7`) — версию нужно синхронно поднять.
- KSP-плагин (после §1.3) `1.9.25-1.0.20` — тоже синхронно поднять.

**(c) Точные изменения.** Целевая версия: **Kotlin `2.0.21`** (стабильная, проверенная связка с Compose; можно `2.1.x`, но `2.0.21` — наименее рискованный шаг с текущей `1.9.25`). Соответствующая версия KSP: **`2.0.21-1.0.28`**.

1. **Корневой `build.gradle`:**
   ```groovy
   // build.gradle:8
   classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'
   // build.gradle:14
   ext.kotlin_version = '2.0.21'
   ```
2. **`app/build.gradle` plugins:** добавить Compose Compiler plugin, поднять версии serialization и KSP:
   ```groovy
   plugins {
       id 'com.android.application'
       id 'org.jetbrains.kotlin.android'
       id 'org.jetbrains.kotlin.plugin.serialization' version '2.0.21'
       id 'org.jetbrains.kotlin.plugin.compose' version '2.0.21'   // НОВЫЙ
       id 'com.google.devtools.ksp' version '2.0.21-1.0.28'        // поднять с 1.9.25-1.0.20
       id 'dagger.hilt.android.plugin'
       id 'io.gitlab.arturbosch.detekt' version '1.23.4'
   }
   ```
3. **Удалить ручной compiler extension** — блок `composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }` (`app/build.gradle:180-182`) полностью убрать.
4. (Опционально) Включить метрики Compose-компилятора через расширение `composeCompiler { ... }`, если нужно профилирование стабильности.
5. Проверить Compose BOM: `2024.12.01` совместим с Compose Compiler `2.0.21` — ОК, можно оставить или поднять до свежего BOM позже отдельным шагом.

**(d) Ожидаемые поломки и подводные камни.**
- **K2-строгость:** более строгая проверка nullability/smart-cast, неоднозначных перегрузок, `when`-исчерпывающести. Ошибки компиляции придётся править точечно. Учитывая объём god-классов (§1.1), ожидать несколько десятков мест.
- **kapt vs KSP:** к моменту 1.4 kapt уже убран (§1.3) — это снимает класс проблем «kapt на Kotlin 2.x» (kapt на K2 идёт через legacy fallback). Если §1.3 не сделан — НЕ начинать 1.4.
- **Совместимость библиотек:** Detekt `1.23.4` собран под Kotlin 1.9 — может выдавать предупреждения на Kotlin 2.x. Это не блокер сборки приложения (detekt — отдельная задача), но `./gradlew detekt` может потребовать обновления detekt до версии с поддержкой Kotlin 2.x (например, `1.23.7+`). Зафиксировать в `detekt-baseline.xml` при необходимости.
- **Serialization-плагин** должен совпадать по версии с Kotlin — синхронно поднят в шаге 2.
- **Compose-стабильность:** новый компилятор может изменить решения о рекомпозиции; визуально проверить `NotesScreen`.
- Coil 2.7.0, Hilt 2.51.1, Room 2.6.1 — совместимы с Kotlin 2.0.21.

**(e) Проверка.**
```bash
./gradlew clean :app:assembleStableRelease
./gradlew :app:testStableDebugUnitTest
```
Проверять: компиляция всех модулей; Compose-экран `NotesScreen` рендерится; нет ошибок «Compose Compiler / Kotlin version mismatch». Прогнать smoke-тест приложения (открыть статью, тему, QMS, заметки).

**(f) Откат.** Вернуть Kotlin `1.9.25` во всех 4 местах (classpath, ext, serialization, KSP), вернуть `composeOptions{kotlinCompilerExtensionVersion="1.5.15"}`, удалить `org.jetbrains.kotlin.plugin.compose`. Поскольку пакет в отдельной ветке — откат = не мёржить ветку.

---

### 1.5 Version catalog (gradle/libs.versions.toml)

**(a) Цель / зачем.** Единый источник версий зависимостей, типобезопасные ссылки `libs.xxx`.

> ⚠️ **ВАЖНОЕ ОТКРЫТИЕ (исправление к заданию):** файл `gradle/libs.versions.toml` **УЖЕ СУЩЕСТВУЕТ** (201 строка), но **НЕ ПОДКЛЮЧЁН** — модульные build-файлы (`build.gradle`, `app/build.gradle`) по-прежнему используют жёстко прописанные координаты. Более того, существующий toml **устарел и расходится** с реальными версиями:
> - `coil = "2.6.0"` в toml, но в `app/build.gradle:318` реально **2.7.0**.
> - `androidx-core = "1.15.0"` в toml, но реально `1.13.1` (`app/build.gradle:274`, к тому же есть `resolutionStrategy force 'androidx.core:core:1.13.1'` на `:263-266`).
> - `minSdk = "21"` в toml, но реально `24` (`app/build.gradle:67`).
> - `versionMinor = "5"` в toml, реально `"9"`.
> - В toml отсутствуют: Room, Compose BOM, datastore, security-crypto, work, brotli, desugar, hilt-work корректно, KSP/compose-плагины, serialization, detekt и др.
> - В toml ЕСТЬ библиотеки, которых НЕТ в `app/build.gradle` (spectrum, simple-tooltip, circular-progress, circle-imageview) — это «хотелки», а не реальные зависимости.
>
> **Вывод:** существующий toml нельзя использовать как есть. Его нужно **переписать с нуля** на основе реальных зависимостей и затем **подключить**.

**(b) Инвентаризация реальных зависимостей** — источник истины: `app/build.gradle:269-360` и `build.gradle:6-14`. Полный список координат и версий приведён в таблице стека (§0) и в `app/build.gradle`.

**(c) Шаги (инкрементально):**

1. **Переписать `gradle/libs.versions.toml`** под реальные версии. Минимальный корректный скелет (привести в соответствие со ВСЕМИ зависимостями `app/build.gradle`):
   ```toml
   [versions]
   agp = "8.11.1"
   kotlin = "1.9.25"            # после §1.4 → "2.0.21"
   ksp = "1.9.25-1.0.20"        # после §1.4 → "2.0.21-1.0.28"
   hilt = "2.51.1"
   hiltWork = "1.2.0"
   room = "2.6.1"
   okhttp = "4.12.0"
   coil = "2.7.0"
   coroutines = "1.8.1"
   serialization = "1.6.3"
   cicerone = "6.6"
   composeBom = "2024.12.01"
   androidxCore = "1.13.1"
   lifecycle = "2.8.7"
   work = "2.10.2"
   material = "1.12.0"
   detekt = "1.23.4"
   # ... (добавить ВСЕ остальные из app/build.gradle:274-348)

   [libraries]
   androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidxCore" }
   room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
   room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
   room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
   hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
   hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
   okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
   coil = { module = "io.coil-kt:coil", version.ref = "coil" }
   compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
   # ... (полный набор)

   [plugins]
   android-application = { id = "com.android.application", version.ref = "agp" }
   kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
   ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
   hilt = { id = "dagger.hilt.android.plugin", version.ref = "hilt" }
   kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
   # после §1.4: kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
   ```
   Удалить из toml мёртвые/несуществующие записи (realm, spectrum, simple-tooltip, circular-progress, circle-imageview), если соответствующих зависимостей в `app/build.gradle` нет.

2. **Подключение каталога.** Gradle 8.13 **автоматически** обнаруживает `gradle/libs.versions.toml` и создаёт accessor `libs` — явная регистрация в `settings.gradle` НЕ требуется (в текущем `settings.gradle` блока `dependencyResolutionManagement { versionCatalogs {} }` нет, и он не нужен для дефолтного имени `libs`).

3. **Миграция build-файлов — инкрементально.** Можно переводить зависимости группами, оставляя остальные хардкодом:
   ```groovy
   // было
   implementation "androidx.room:room-runtime:2.6.1"
   // стало
   implementation libs.room.runtime
   ```
   Плагины (после миграции на новый синтаксис plugins-блока):
   ```groovy
   plugins {
       alias(libs.plugins.android.application)
       alias(libs.plugins.kotlin.android)
       // ...
   }
   ```
   Начать с самостоятельной группы (например, Room и Hilt), проверить сборку, затем расширять.

**(d) Подводные камни.**
- Не сломать `resolutionStrategy force 'androidx.core:core:1.13.1'` (`app/build.gradle:263-266`) — версия в каталоге должна совпадать (1.13.1), иначе конфликт.
- Compose BOM-зависимости без версии (`implementation 'androidx.compose.ui:ui'`) — в каталоге описывать как `module` без version (версия из BOM-платформы).
- Имена ключей с дефисами в toml ↔ точки в accessor (`androidx-core-ktx` → `libs.androidx.core.ktx`).

**(e) Проверка.** После каждой группы: `./gradlew :app:assembleStableRelease`. Сверить `./gradlew :app:dependencies` до/после — дерево версий не должно измениться.

**(f) Откат.** Так как миграция группами, каждый коммит точечно откатывается `git revert`. Хардкод-координаты всегда можно вернуть.

---

## SECTION 2 — Хрупкость парсинга

### 2.1 Замена regex/custom-DOM на Jsoup

**(a) Цель / зачем.** Сейчас HTML парсится **собственным regex-движком** и кастомным DOM. Это хрупко: ломается на изменениях разметки 4pda, тяжело поддерживать. Jsoup — устойчивый HTML5-парсер с CSS-селекторами.

**(b) Что есть сейчас (проверено):**
- Кастомный DOM-движок: `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/regex/parser/` — файлы `Parser.kt` (234 строки), `Document.kt`, `Node.kt`. `Parser.kt:21-36` строит DOM регулярками (`mainPattern`, `attributePattern`), `:54` `fun parse(html): Document`, навигация `findNode`/`findChildNodes`/`ownText` (`:197-233`).
- `PatternProvider` — `app/src/main/java/forpdateam/ru/forpda/model/system/PatternProvider.kt` — провайдер скомпилированных `Pattern` для парсеров (инжектится через DI: `di/NetworkModule.kt`, `di/DataModule.kt`, `di/AppModule.kt`).
- Базовый класс: `app/src/main/java/forpdateam/ru/forpda/model/data/remote/parser/BaseParser.kt`.
- **Полный список *Parser.kt** (проверено): `AppUpdateParser`, `DevDbParser`, `TopicsParser`, `AttachmentsParser`, `MentionsParser`, `QmsParser`, `EditPostParser`, `ThemeParser`, `ThemePageMetadataParser`, `AuthParser`, `ArticleParser` (4162 стр.), `ProfileParser`, `ForumParser`, `ReputationParser`, `FavoritesParser`, `SearchParser`. Плюс `BaseParser`, `BbcodePreviewRenderer`.
- **Jsoup сейчас НЕ подключён** (проверено: нет в `app/build.gradle` и в toml).

**(c) Инкрементальный план миграции:**

1. **Добавить Jsoup:**
   ```groovy
   // app/build.gradle dependencies
   implementation 'org.jsoup:jsoup:1.18.3'
   ```
   (и запись в каталог §1.5).
2. **Зафиксировать §2.2 (golden-тесты) ПЕРЕД миграцией** — это обязательное условие.
3. **Пилот — самый маленький парсер.** Рекомендуется **`MentionsParser.kt`** или **`ReputationParser.kt`** как первый кандидат (небольшие, изолированная модель, легко покрыть фикстурой). Если они окажутся слишком завязаны — взять `AttachmentsParser.kt`. **Рекомендуемый пилот: `ReputationParser.kt`** (компактная страница, простая модель).
4. **Маппинг regex → Jsoup CSS:**
   - `findNode(node,"div","class","foo")` → `doc.selectFirst("div.foo")`;
   - `findChildNodes(node,"a",null,...)` → `el.select("a")`;
   - `ownText(node)` → `el.ownText()`; полный текст → `el.text()`; атрибут → `el.attr("href")`.
5. **Fallback за флагом.** Ввести булев флаг (например, в `BuildConfig` или в `PatternProvider`/новом `ParserFlags`): `USE_JSOUP_<PARSER>`. Новый Jsoup-путь и старый regex-путь сосуществуют; по умолчанию старый, пока golden-тесты на Jsoup-пути не зелёные. После стабилизации — удалить regex-ветку этого парсера.
6. **Порядок миграции (от простого к сложному):**
   `ReputationParser` → `MentionsParser` → `AttachmentsParser` → `SearchParser` → `FavoritesParser` → `ProfileParser` → `TopicsParser` → `DevDbParser` → `EditPostParser` → `AuthParser` → `ForumParser` → `ThemePageMetadataParser` → `ThemeParser` → `QmsParser` → **`ArticleParser` последним** (после его декомпозиции §1.1).
7. После миграции всех парсеров — удалить `regex/parser/{Parser,Document,Node}.kt` и неиспользуемые `Pattern` из `PatternProvider`.

**(d) Подводные камни.**
- 4pda отдаёт «грязный» HTML — Jsoup его нормализует, но селекторы должны опираться на устойчивые классы/id, а не на порядок. Сверять с фикстурами.
- Кодировка: страницы могут быть в windows-1251/utf-8 — Jsoup.parse принимать с правильным charset (как уже делает текущий слой загрузки).
- Производительность: Jsoup на большой статье (ArticleParser) тяжелее regex — измерять (§3.1 теги `FPDA_ARTICLE_PARSE`).
- DI: `PatternProvider` инжектится в парсеры — при удалении regex-пути не сломать конструкторы (оставить параметр или удалить из DI согласованно).

**(e) Проверка.** `./gradlew :app:testStableDebugUnitTest` — golden-тесты (§2.2) на Jsoup-пути должны давать те же модели, что regex-путь. Затем `./gradlew :app:assembleStableRelease`.

**(f) Откат.** Флаг fallback позволяет мгновенно вернуться на regex-путь без отката кода. На уровне VCS — revert коммита парсера.

---

### 2.2 Тесты парсеров на реальных HTML-фикстурах

**(a) Цель / зачем.** Зафиксировать текущее поведение парсеров «золотыми» тестами на реальных сохранённых HTML-страницах, чтобы §2.1 (Jsoup) и §1.1 (декомпозиция `ArticleParser`) можно было верифицировать на идентичность. Делать ПЕРЕД 2.1 и перед декомпозицией парсеров.

**(b) Что есть сейчас (проверено):**
- Тестовый стек уже в проекте: JUnit4, Robolectric `4.14.1`, MockK `1.13.13`, Turbine `1.1.0`, coroutines-test `1.8.1` (`app/build.gradle:342-348`). `testOptions { unitTests.returnDefaultValues = true }` (`:209-211`).
- Уже есть тесты парсеров, например `app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/qms/QmsParserChatTest.kt`.
- Каталога `app/src/test/resources/` пока НЕТ (проверено).

**(c) Шаги:**
1. **Создать каталог фикстур:** `app/src/test/resources/fixtures/` с подпапками по доменам: `article/`, `theme/`, `qms/`, `reputation/`, `mentions/`, `forum/`, `topics/`, ...
2. **Собрать репрезентативные страницы.** Способы захвата:
   - В debug-сборке уже есть логирование тела ответа (`NewsApi.fetchArticleDetails` эмитит `article_response`, см. `PERF_DIAGNOSIS.md` A.5 п.4) — взять реальный HTML оттуда.
   - Либо `curl`/браузер: сохранить мобильную и desktop-версии страницы статьи, страницу темы (с пагинацией, спойлерами, опросом), QMS-диалог, профиль, репутацию. Имена: `article/article_with_poll.html`, `article/article_basic.html`, `theme/theme_page_with_spoilers.html` и т.д.
   - Удалить из фикстур приватные данные (куки, токены, имена в QMS) — заменить на синтетические.
3. **Загрузка фикстуры в тесте:**
   ```kotlin
   private fun loadFixture(path: String): String =
       requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$path"))
           .bufferedReader(Charsets.UTF_8).use { it.readText() }
   ```
4. **Написать golden-тесты** для каждого мигрируемого парсера, ассертить поля модели:
   ```kotlin
   class ReputationParserGoldenTest {
       private val parser = ReputationParser(/* PatternProvider mock/real */)

       @Test fun `parses reputation list`() {
           val html = loadFixture("reputation/reputation_basic.html")
           val model = parser.parse(html)
           assertEquals(42, model.items.size)
           assertEquals("user123", model.items.first().nick)
           // ... все значимые поля
       }
   }
   ```
   Для парсеров, зависящих от `PatternProvider`, создать реальный `PatternProvider` (он самодостаточен) или мок через MockK.
5. **Привязка к 2.1:** один и тот же фикстур-тест прогоняется и на regex-пути, и на Jsoup-пути (параметризовать флагом) — результаты обязаны совпасть.

**(d) Подводные камни.**
- HTML может содержать абсолютные ссылки на 4pda — не уходить в сеть; парсеры должны работать чисто на строке.
- Robolectric нужен только если парсер тянет Android-классы (`android.text.Html`, `Uri`). Если парсер чистый JVM — можно без Robolectric (быстрее).
- Не коммитить гигантские страницы целиком, если достаточно репрезентативного фрагмента; но для статьи нужна полная — это ОК.

**(e) Проверка.** `./gradlew :app:testStableDebugUnitTest`. Все новые golden-тесты зелёные на текущем (regex) пути ДО начала §2.1.

**(f) Откат.** Тесты и ресурсы — аддитивны, удаление безопасно. Откат не влияет на прод-код.

---

## SECTION 3 — Производительность чтения

### 3.1 Упрощение WebView render pipeline

**(a) Цель / зачем.** Холодное открытие темы/статьи проходит через многоступенчатую машину рендера с DOM-пробами и watchdog'ами. Цель — измерить cold-open и упростить pipeline без потери надёжности рендера. **Обязательно сверяться с `PERF_DIAGNOSIS.md` (корень репозитория) и его сводным TOP-10** (раздел C).

**(b) Файлы pipeline (проверено):**
- `app/src/main/java/forpdateam/ru/forpda/ui/TemplateManager.kt` (2006 стр.) — сборка HTML-шаблона + палитры (`:54-66`).
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt` (2839 стр.) — WebView-фрагмент темы.
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` (3471 стр.) — рендер статьи (`renderArticle` → `loadDataWithBaseURL`, см. `PERF_DIAGNOSIS.md` A.1 `:922,951`).
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt` (2763 стр.) — QMS, DOM-пробы `runQmsDomReadyProbe` (`:1640-1705`), watchdog'и, recovery (`tryQmsRenderAutoRecovery :2170-2199`).
- WebView-классы: найти `ExtendedWebView`/`NestedWebView`, `CustomWebViewClient`/`CustomWebChromeClient` под `ui/views/` (см. ниже команду поиска).
- Render-политики под `presentation/theme/`: `ThemeRenderGuard`, `ThemeRenderCompletePolicy`, `ThemeRenderSettledPolicy`, `ThemeDomLoadAnchorPolicy`, `ThemeStuckLoadRecoveryPolicy`; для QMS — `QmsWebRenderPolicy` (`DOM_READY_RETRY_MS=100`, `MAX_DOM_READY_ATTEMPTS=28`, см. `PERF_DIAGNOSIS.md` B.5 п.5).

  Команда для точной локализации классов WebView:
  ```bash
  rg -ln "ExtendedWebView|NestedWebView|CustomWebViewClient|CustomWebChromeClient" app/src/main/java
  ```

**(c) Текущий cold-open flow (выжимка из `PERF_DIAGNOSIS.md`):**
- **Статья (A.1):** клик → `ArticlesListViewModel.onItemClick` → навигация → `ArticleInteractor.loadArticleLocked` (строго последовательный: awaitWarm → cache lookup → network GET мобильной страницы → `articleTemplate.mapEntity` на `Dispatchers.Default` → возможный 2-й GET при «не renderable» → put в cache → `_data.value` → WebView `loadDataWithBaseURL`) → phase-2 deferred extras (ещё desktop-GET'ы для комментариев и опроса).
- **QMS (B.1):** open → либо reuse shell без перезагрузки, либо полный `loadBaseWebContainer` → `loadDataWithBaseURL` → инъекция сообщений → `runQmsDomReadyProbe` (до 28 проб × 100мс) → verify-render → success/`completeQmsRenderSuccess`, при неудаче — watchdog auto-recovery с полным reload shell (1-2 сек).

**(d) Сложность probe/watchdog (что упрощать):**
- Линейные DOM-ready пробы (28×100мс = до 2.8с активного опроса) — `PERF_DIAGNOSIS.md` B.5 п.5: заменить на экспоненциальный backoff (100,200,400,800,1600мс).
- 6× `postDelayed` для scroll-to-bottom (`QmsChatFragment.scheduleScrollQmsToBottom :1180-1196`) — B.5 п.6: сократить до 1-2 итераций.
- Watchdog auto-recovery, уходящий в полный reload shell (`:2170-2199`) — B.5 п.7: ограничить число попыток.
- Последовательные сетевые GET'ы фазы-2 (`NewsApi.loadDesktopExtrasIfMissing :200-224`) — A.5 п.1 / C п.3: параллелить.
- Refetch-retry на фазе-1 (`ArticleInteractor :843-854`) — A.5 п.2 / C п.4: сузить до 5xx/IOException.

**(e) Шаги:**
1. **Сначала измерить.** Включить DEBUG-теги таймингов (уже реализованы, см. `PERF_DIAGNOSIS.md` раздел 4): `FPDA_ARTICLE_OPEN` (`ArticleOpenSession.emitSummary`, поля `totalOpenDurationMs`, `firstContentVisibleMs`, `templateBuildMs` и т.д.), `FPDA_QMS_OPEN`/`FPDA_QMS_WEBVIEW`. Снять 5-10 cold-open замеров на реальном устройстве (предварительно `adb shell pm clear` или kill процесса для «холода»).
   ```bash
   adb logcat -s FPDA_ARTICLE_OPEN FPDA_ARTICLE_RENDER FPDA_QMS_OPEN FPDA_QMS_WEBVIEW
   ```
2. **Реализовать низкорисковые пункты TOP-10** по порядку выигрыша (раздел C `PERF_DIAGNOSIS.md`), по одному, с замером до/после:
   - C1: SWR-TTL для QMS (`QmsChatViewModel.kt:300`).
   - C3: параллелить desktop-extras (`NewsApi.kt:200-224`).
   - C4: сузить refetch-retry (`ArticleInteractor.kt:843-854`).
   - C6: early-return при memory-хите (`ArticleInteractor.kt:442-481`).
   - C7: backoff для DOM-проб (`QmsChatFragment.kt:1640-1705` + `QmsWebRenderPolicy.kt:34`).
   - C8: сократить scroll-to-bottom итерации.
3. **Упростить watchdog-машину** после того как базовые таймсейверы внедрены и измерены: свести количество перекрывающихся политик рендера, где это не меняет видимое поведение. НЕ удалять recovery полностью — это страховка от blank-WebView (`FPDA_WEBVIEW_BLANK`).
4. Документировать каждое изменение замером (мс).

**(f) Подводные камни.**
- WebView-рендеринг чувствителен к гонкам: убирая пробу/watchdog, легко получить blank-экран на медленных устройствах. Каждое упрощение — за DEBUG-замером и smoke-тестом на реальном устройстве (особенно холодный старт + быстрый back/forward между темами).
- Изменения порядка инъекции JS могут ломать скролл-восстановление (`ThemeScrollAnchorController`, `ScrollAnchor`).
- Согласовывать с §1.1 (если параллельно идёт декомпозиция этих же фрагментов — координировать, не конфликтовать).

**(g) Проверка.** `./gradlew :app:assembleStableRelease`; на устройстве — сравнить `totalOpenDurationMs`/`firstContentVisibleMs` до/после (должно уменьшиться или не вырасти); ручной smoke: открыть 10 статей/тем/диалогов холодным стартом, проверить отсутствие blank-WebView и корректность скролла.

**(h) Откат.** Каждый пункт TOP-10 — отдельный коммит → `git revert`. Флагов не требуется, но для рискованных упрощений watchdog можно завести debug-флаг переключения старого/нового пути.

---

### 3.2 Частичная миграция нативных списков на Compose

**(a) Цель / зачем.** Постепенно переводить **нативные списочные экраны** (RecyclerView) на Compose `LazyColumn`, оставляя контент постов в WebView. Это снижает boilerplate адаптеров и упрощает state-биндинг.

**(b) Что есть сейчас (проверено):**
- **Ровно ОДИН** `@Composable` в проекте: `app/src/main/java/forpdateam/ru/forpda/ui/compose/screens/NotesScreen.kt`.
- Хост-паттерн (эталон для интеропа) — `app/src/main/java/forpdateam/ru/forpda/ui/fragments/notes/NotesComposeFragment.kt`:
  ```kotlin
  @AndroidEntryPoint
  class NotesComposeFragment : RecyclerFragment() {
      private val viewModel: NotesViewModel by viewModels()
      override fun onCreateView(...): View {
          super.onCreateView(inflater, container, savedInstanceState)
          return ComposeView(requireContext()).apply {
              setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
              setContent { NotesScreen(viewModel = viewModel, onNavigateToLink = { ... }) }
          }
      }
  }
  ```
- `NotesScreen` уже использует `collectAsStateWithLifecycle()` поверх `viewModel.uiState: StateFlow` и `viewModel.effects` — то есть **паттерн StateFlow → Compose уже работает**.
- Навигация — Cicerone, экраны в `presentation/Screen.kt` (sealed). Списки: `ArticleList` (`:88`), `Topics` (`:188`), `QmsContacts` (`:125`), `Favorites` (`:72`), `Forum` (`:76`), `History` (`:80`), `Mentions` (`:84`).

**(c) Рекомендуемая первая цель и план:**

- **Первая цель — список новостей (`ArticleList`)** ИЛИ **`QmsContacts`**. Рекомендуется **`QmsContacts`** как первый: список контактов проще (нет сложной пагинации/префетча как у новостей), есть Room-кэш (`QmsContactRoom`/`QmsContactDao`), и ViewModel уже отдаёт состояние. Если у `QmsContacts` нет StateFlow-ViewModel — взять `ArticleList` (у него `ArticlesListViewModel`).

- **Интероп с Cicerone/ViewModel (по образцу Notes):**
  1. Создать `QmsContactsScreen.kt` в `ui/compose/screens/` — `@Composable`, принимает существующую ViewModel и лямбды навигации.
  2. Создать `QmsContactsComposeFragment` по образцу `NotesComposeFragment` (extends базовый `RecyclerFragment`, `@AndroidEntryPoint`, `by viewModels()`, `ComposeView` + `DisposeOnViewTreeLifecycleDestroyed`).
  3. Навигацию наружу (клик по контакту → открыть QMS-чат) пробрасывать лямбдой, внутри которой вызывается существующий router/Cicerone (как `onNavigateToLink` в Notes).
  4. В Cicerone-`Screen` (`QmsContacts`) переключить создание фрагмента на новый Compose-фрагмент (или временно завести флаг для A/B).
  5. Использовать `collectAsStateWithLifecycle()` для `uiState` и обработку one-shot эффектов как в `NotesScreen` (`effects`).

- **Инкрементально:** мигрировать по одному списку. Порядок: `QmsContacts` → `ArticleList` (новости) → `Topics` → `Favorites`/`History`/`Mentions`. **Контент постов/статей/QMS-сообщений остаётся в WebView** — мигрируются только списочные экраны.

**(d) Подводные камни.**
- **Зависимость от §1.4:** Compose Compiler plugin (Kotlin 2.x) желателен, но НЕ строго обязателен — текущая конфигурация (`kotlinCompilerExtensionVersion 1.5.15` + Kotlin 1.9.25) уже компилирует Compose (`NotesScreen` работает). Можно начинать до 1.4; после 1.4 убрать ручной extension.
- Сохранение скролл-позиции при возврате через Cicerone — `LazyListState` через `rememberSaveable`/`rememberLazyListState`.
- Пагинация (новости) — обработать подгрузку при достижении конца списка (`derivedStateOf` на `lastVisibleItemIndex`), не сломав существующий prefetch.
- Тема/палитра: Compose-экраны должны уважать текущую тему приложения (Material3 `MaterialTheme`); согласовать с §4.1.

**(e) Проверка.** `./gradlew :app:assembleStableRelease` + `./gradlew :app:testStableDebugUnitTest`; на устройстве — открыть мигрированный список, проверить клики/навигацию/скролл/обновление, возврат назад (Cicerone back).

**(f) Откат.** Старый RecyclerView-фрагмент не удалять до стабилизации Compose-версии; переключение экрана в Cicerone-`Screen` — одна строка. Откат = вернуть старый фрагмент.

---

## SECTION 4 — Современный UX

### 4.1 Material You / Dynamic Color (Android 12+)

**(a) Цель / зачем.** На Android 12+ подтягивать акцент из обоев системы (Dynamic Color) для **нативного UI** приложения, интегрировав с существующими палитрами.

**(b) Что есть сейчас (проверено):**
- 5 палитр в `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt:61`:
  ```kotlin
  enum class UiPalette { SYSTEM, CLASSIC_4PDA, SEPIA_READING, SEPIA_BLUE, MINIMAL_READER }
  ```
  ⚠️ Исправление к заданию: 5 палитр — `SYSTEM`, **`CLASSIC_4PDA`**, `SEPIA_READING`, `SEPIA_BLUE`, `MINIMAL_READER`. Четвёртая называется `CLASSIC_4PDA`, а не «MINIMAL» (в `arrays.xml` строковый ключ — `minimal_reader`, но enum-значение для классической темы — `CLASSIC_4PDA`).
- `app/src/main/res/values/arrays.xml:29-40` — `entries_ui_palette` / `entry_values_ui_palette` (значения: `SYSTEM`, `CLASSIC_4PDA`, `SEPIA_READING`, `SEPIA_BLUE`, `MINIMAL_READER`).
- Применение палитр: `TemplateManager.kt:54-66`, `UiThemeStyles.kt`, `UiThemeStyles.effectivePalette(...)`.
- Тема приложения: `AndroidManifest.xml:57` — `android:theme="@style/DayNightAppTheme"`, splash — `@style/SplashTheme`.
- Material библиотека `com.google.android.material:material:1.12.0` (`app/build.gradle:313`) — поддерживает `DynamicColors`.

**(c) Граница «нативный UI ↔ WebView CSS» (КРИТИЧНО):**
- Dynamic Color применяется **только к нативному UI** (тулбары, навигация, нативные списки, Compose-экраны §3.2).
- **Контент в WebView темизируется ОТДЕЛЬНО** через CSS из `app/src/main/assets/forpda/styles/{light,dark}/...` и `modules/*.less` (см. git-status — эти файлы менялись). Палитры `SEPIA_*`/`MINIMAL_READER`/`CLASSIC_4PDA` влияют на CSS-вывод (`TemplateManager`). **Dynamic Color НЕ должен переопределять WebView-CSS** — иначе чтение «поедет». Граница: акцент из обоев → нативные акценты (FAB, кнопки, чипы), но фон/типографика контента остаётся под управлением палитры чтения.

**(d) Шаги:**
1. Добавить опцию в настройки: «Использовать цвета системы (Material You)» — новый ключ в `Preferences` (boolean, только Android 12+). По умолчанию — выкл (чтобы не ломать существующий UX).
2. В `App` (Application, `AndroidManifest.xml:48` `android:name=".App"`) подключить динамические цвета условно:
   ```kotlin
   import com.google.android.material.color.DynamicColors
   // в App.onCreate(), если включена опция и Build.VERSION.SDK_INT >= 31:
   DynamicColors.applyToActivitiesIfAvailable(this)
   ```
   (Material 1.12.0 содержит `DynamicColors`.) Применять ТОЛЬКО когда выбрана палитра `SYSTEM` или включён отдельный тумблер Material You — чтобы не конфликтовать с `SEPIA_*`.
3. Согласовать с `UiThemeStyles.effectivePalette(...)`: при включённом Dynamic Color нативная тема берёт системный акцент, но `effectivePalette` для WebView продолжает возвращать выбранную палитру чтения.
4. Для Compose-экранов (§3.2): использовать `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` на Android 12+ внутри `MaterialTheme`, с фолбэком на статическую схему.

**(e) Подводные камни.**
- НЕ применять Dynamic Color к WebView-контенту — только нативный слой.
- `DynamicColors.applyToActivitiesIfAvailable` пересоздаёт тему активити — проверить, что splash (`SplashTheme`) и `DayNightPreferenceTheme` (Settings) не ломаются.
- Палитры чтения (`SEPIA_*`) должны иметь приоритет для контента; Material You — для «хрома» приложения.
- Тёмная/светлая тема (`DayNightAppTheme`) должна продолжать работать (атрибут `?attr/...` в стилях).

**(f) Проверка.** `./gradlew :app:assembleStableRelease`; на Android 12+ устройстве: сменить обои → проверить, что акцент нативного UI поменялся, а WebView-контент (статья/тема) остался в выбранной палитре чтения. На Android < 12 — опция скрыта/неактивна, поведение без изменений.

**(g) Откат.** Опция по умолчанию выключена; удаление вызова `DynamicColors.applyToActivitiesIfAvailable` и ключа настройки → возврат к текущему поведению.

---

### 4.2 Edge-to-edge

**(a) Цель / зачем.** При `targetSdk 35` на Android 15 edge-to-edge включается **принудительно системой**. Без явной обработки insets контент уходит под системные бары/вырезы. Нужно явно включить edge-to-edge и корректно обработать вставки.

**(b) Активити (проверено — их РОВНО 4, а не 6, как в задании):**
| Класс | Файл | Манифест |
|---|---|---|
| `MainActivity` | `app/src/main/java/forpdateam/ru/forpda/ui/activities/MainActivity.kt` | `AndroidManifest.xml:59-98` (LAUNCHER, deep links 4pda.to) |
| `ImageViewerActivity` | `app/.../ui/activities/imageviewer/ImageViewerActivity.kt` | `:99-105` (NoActionBar) |
| `SettingsActivity` | `app/.../ui/activities/SettingsActivity.kt` | `:107-116` (PreferenceTheme) |
| `WebVewNotFoundActivity` | `app/.../ui/activities/WebVewNotFoundActivity.kt` | `:118-122` (NoActionBar) |

(`PaginationHelper.kt` в grep по «Activity» — ложное совпадение, это не Activity.)

`MainActivity` хостит фрагменты (Cicerone), `windowSoftInputMode="adjustResize"` (`:65`). targetSdk 35 (`app/build.gradle:68`).

**(c) Шаги:**
1. **Включить edge-to-edge в каждой из 4 активити.** В `onCreate` ДО `super.onCreate`/`setContentView`:
   ```kotlin
   import androidx.activity.enableEdgeToEdge
   // или, без activity-ktx-расширения:
   import androidx.core.view.WindowCompat
   override fun onCreate(savedInstanceState: Bundle?) {
       enableEdgeToEdge()                                   // androidx.activity 1.8+ (у нас 1.10.1)
       // эквивалент: WindowCompat.setDecorFitsSystemWindows(window, false)
       super.onCreate(savedInstanceState)
       ...
   }
   ```
   `androidx.activity:activity-ktx:1.10.1` (`app/build.gradle:275`) содержит `enableEdgeToEdge()`.
2. **Обработать insets** во всех экранах:
   - Тулбары (AppBar/Toolbar) — применить top-inset как padding/margin: `ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets -> val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.updatePadding(top = bars.top); insets }`.
   - Bottom-навигация / нижние панели — bottom-inset (+ ime для панели ответа).
   - **Панель ответа** (reply panel в темах/QMS) — учитывать `WindowInsetsCompat.Type.ime()` совместно с systemBars; есть существующий `ThemeImeInsetsController.kt` (`ui/fragments/theme/modules/`) — переиспользовать/расширить его подход.
   - **WebView** — контент WebView должен иметь нижний/верхний отступ под бары там, где он на весь экран, либо CSS `env(safe-area-inset-*)` (для WebView предпочтительно нативный padding контейнера, т.к. CSS safe-area в WebView без `viewport-fit=cover` не работает — проверить `<meta viewport>` в шаблоне `TemplateManager`).
   - `ImageViewerActivity` — полноэкранный, бары прозрачные; убедиться, что зум/жесты не конфликтуют с системными жестами (gesture insets).
3. **Прозрачность системных баров.** При edge-to-edge на Android 15 цвет баров игнорируется системой; убрать устаревшие `statusBarColor`/`navigationBarColor` из стилей (`res/values/styles*.xml`), при необходимости задать `isAppearanceLightStatusBars` через `WindowInsetsControllerCompat` в зависимости от темы (day/night).
4. **Аудит всех фрагментов**, рисующих у краёв (тулбары, FAB, нижние листы `BottomSheetDialog.kt`, пагинация) — добавить inset-обработку.

**(d) Подводные камни.**
- `adjustResize` + edge-to-edge + IME: на Android 15 поведение клавиатуры изменилось — тестировать панель ответа в темах/QMS и поле поиска.
- Двойные отступы: не применять inset дважды (и на контейнере, и на child).
- `fitsSystemWindows="true"` в XML-лэйаутах может конфликтовать — заменить на программную обработку insets.
- WebView со скроллом: нижний бар не должен перекрывать последний пост — добавить bottom padding контейнеру WebView.

**(e) Проверка.** `./gradlew :app:assembleStableRelease`; на эмуляторе/устройстве Android 15 (API 35) и Android 12: проверить, что контент не уходит под бары, тулбар/нижняя навигация/панель ответа корректны, клавиатура не перекрывает поле ввода, ImageViewer полноэкранный без обрезки жестами.

**(f) Откат.** Удалить `enableEdgeToEdge()` и inset-листенеры. Так как targetSdk 35 форсит edge-to-edge, полноценный откат потребовал бы понизить targetSdk — не делать; вместо этого фиксить insets. На уровне VCS — revert по активити.

---

### 4.3 Predictive back gesture (Android 14+)

**(a) Цель / зачем.** Включить предиктивный жест «назад» (анимация-превью предыдущего экрана). Требует opt-in и миграции кастомной обработки «назад» на современные коллбэки.

**(b) Что есть сейчас (проверено):**
- В `AndroidManifest.xml` атрибут `android:enableOnBackInvokedCallback` **отсутствует** в `<application>` (`:47-58`) — не включён.
- Кастомной обработки «назад» через `enableOnBackInvokedCallback`/`OnBackInvokedCallback` в коде **нет** (проверено grep). Используется Cicerone для навигации назад (`MainActivity` + router).
- Найти текущие обработчики «назад»:
  ```bash
  rg -n "onBackPressed|OnBackPressedCallback|addCallback|handleOnBackPressed|onBackPressedDispatcher" app/src/main/java
  ```

**(c) Шаги:**
1. **Opt-in в манифесте** — в теге `<application>` (`AndroidManifest.xml:47-58`):
   ```xml
   <application
       android:name=".App"
       ...
       android:enableOnBackInvokedCallback="true">
   ```
2. **Мигрировать любую `onBackPressed()`-логику** на `OnBackPressedDispatcher` + `OnBackPressedCallback` (AndroidX, уже доступно через `androidx.activity:activity-ktx:1.10.1` и `fragment-ktx:1.8.6`):
   ```kotlin
   requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
       override fun handleOnBackPressed() { /* Cicerone back / закрыть панель / снять выделение */ }
   })
   ```
   Это автоматически совместимо с predictive back (AndroidX мостит к `OnBackInvokedCallback`).
3. **Cicerone-навигация:** убедиться, что back через `router.exit()` вызывается из коллбэка/диспетчера, а не из переопределённого `Activity.onBackPressed()` (deprecated на API 33+). Если в `MainActivity` есть override `onBackPressed()` — перенести в `onBackPressedDispatcher.addCallback`.
4. Для экранов с временным перехватом «назад» (открытая панель ответа, поиск, выделение в списке) — `callback.isEnabled = true/false` динамически, чтобы predictive-анимация показывалась только когда реально уйдём с экрана.

**(d) Подводные камни.**
- Если останется хоть один `Activity.onBackPressed()` override без миграции — predictive back для него не сработает корректно.
- Коллбэки с `isEnabled=true` всегда перехватывают жест и отключают предиктивную анимацию выхода — включать только когда действительно нужно перехватить.
- Порядок добавления коллбэков (LIFO) — вложенные фрагменты vs activity.
- Тестировать на Android 14+ с включённой системной опцией predictive back (Developer options на 14, по умолчанию на 15).

**(e) Проверка.** `./gradlew :app:assembleStableRelease`; на Android 14/15: жест «назад» от края показывает превью предыдущего экрана; Cicerone-навигация назад работает корректно во вложенных стеках; панель ответа/поиск закрываются по «назад» без выхода из приложения.

**(f) Откат.** Убрать `android:enableOnBackInvokedCallback="true"`. Миграция на `OnBackPressedCallback` полезна сама по себе и откатывать её не требуется.

---

## SECTION 5 — Оффлайн-чтение

### 5.1 Сохранение тем/статей для чтения оффлайн

**(a) Цель / зачем.** Дать пользователю «Сохранить для оффлайна»: тема/статья (распарсенная модель + готовый HTML + изображения) сохраняется на диск и читается без сети в существующем WebView. 🔴 Большая фича — делать поэтапно.

**(b) Текущая инфраструктура (проверено):**
- **Room:** единая БД `AppDatabase` (файл `app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesDatabase.kt`), `version = 6`, `exportSchema = true` (`:20-34`). Сущности: `NoteItemRoom`, `NoteFolderRoom`, `HistoryItemRoom`, `QmsContactRoom`, `QmsThemeRoom`, `QmsThemesRoom`, `FavItemRoom`, `ForumItemFlatRoom`, `ForumUserRoom`. DAO — одноимённые. Инстанс создаётся в `di/DataModule.kt`.
- Сущности/DAO под `app/src/main/java/forpdateam/ru/forpda/entity/db/{notes,qms,forum,history,favorites}/...`.
- **Кэши-обёртки Room** под `app/src/main/java/forpdateam/ru/forpda/model/data/cache/*` (`HistoryCacheRoom`, `FavoritesCacheRoom`, `ForumCacheRoom`, `ForumUsersCacheRoom`, `NotesCacheRoom`, `QmsCacheRoom`) — паттерн для нового кэша.
- **Дисковый кэш статей:** `app/src/main/java/forpdateam/ru/forpda/model/interactors/news/ArticleDiskCache.kt` — JSON-кэш статей (`maxEntries=24`, `maxAgeMs=24ч`, см. `PERF_DIAGNOSIS.md` A.3). Memory-кэш — `ArticleMemoryCache.kt`. Prefetch — `ArticlePrefetchService.kt`.
- **Загрузка/рендер статьи:** `ArticleInteractor.kt` (`model/interactors/news/`) → `articleTemplate.mapEntity` → `ArticleContentFragment.renderArticle` → WebView `loadDataWithBaseURL` (см. `PERF_DIAGNOSIS.md` A.1).
- **Загрузка/рендер темы:** `ThemeViewModel` + `ThemeFragmentWeb` + `TemplateManager` (HTML+CSS) → WebView.
- **Картинки:** Coil `2.7.0` (`app/build.gradle:318`) + OkHttp `4.12.0` (есть свой дисковый кэш OkHttp, если настроен; Coil имеет свой image-disk-cache).

**(c) Дизайн данных.** Что персистить (на одну сохранённую запись):
1. **Распарсенную модель** (Article/Theme entity) — для повторного маппинга/поиска/метаданных.
2. **Готовый HTML-вывод шаблона** (результат `TemplateManager`/`articleTemplate.mapEntity`) — чтобы рендерить мгновенно без парсинга/сети.
3. **Референсные изображения** — скачать и сохранить локально, переписать URL → `file://`/`content://` (или отдать через `WebViewAssetLoader`).
4. **Метаданные** — тип (article/theme), исходный URL/id, заголовок, дата сохранения, размер на диске, статус (полностью сохранено / частично).

**Новые Room-сущности (расширение `AppDatabase` до version 7 с миграцией):**
```kotlin
@Entity(tableName = "offline_items")
data class OfflineItemRoom(
    @PrimaryKey val id: String,        // "article:<id>" / "theme:<topicId>:<page>"
    val type: String,                  // ARTICLE | THEME
    val sourceUrl: String,
    val title: String,
    val savedAtMs: Long,
    val sizeBytes: Long,
    val status: String,                // COMPLETE | PARTIAL | FAILED
    val htmlPath: String,              // путь к сохранённому HTML на диске
    val modelJson: String              // сериализованная модель (kotlinx.serialization)
)
```
> HTML и картинки хранить НЕ в Room, а в файловой системе (`context.filesDir/offline/<id>/index.html`, `.../images/...`), Room хранит только метаданные/пути и (опционально) JSON-модель. Это согласуется с тем, что `ArticleDiskCache` уже хранит JSON на диске, а не в Room.

DAO: `OfflineItemDao` (insert/delete/getAll/getById/sumSize). Зарегистрировать в `AppDatabase.entities` + abstract `offlineItemDao()`, поднять `version` 6→7, добавить `Migration(6,7)` с `CREATE TABLE offline_items (...)` в `di/DataModule.kt` (где собирается `Room.databaseBuilder`).

**(d) Поэтапный план реализации:**

**Фаза 1 — Data layer.**
- Создать `OfflineItemRoom` + `OfflineItemDao`, расширить `AppDatabase` (version 7 + Migration(6,7)).
- Создать `OfflineStorage` (файловый менеджер: `filesDir/offline/<id>/`, запись/чтение HTML, директория картинок, подсчёт размера, удаление).
- Создать `OfflineRepository` (фасад над DAO + OfflineStorage), зарегистрировать в DI (`di/DataModule.kt`).
- Проверка: `./gradlew :app:testStableDebugUnitTest` с `room-testing` (Migration-тест 6→7).

**Фаза 2 — Save action.**
- Добавить пункт меню «Сохранить для оффлайна» в `ArticleContentFragment` (статья) и в теме (`ThemeFragmentWeb`/тулбар темы). Иконка/строка в `res/menu/*` и `res/values/strings.xml`.
- При нажатии: взять уже распарсенную модель и уже собранный HTML (на момент открытия они есть: для статьи — результат `articleTemplate.mapEntity`; для темы — вывод `TemplateManager`). Сохранить модель (JSON) + HTML в `OfflineStorage`, запись в Room со `status=PARTIAL` до завершения скачивания картинок.
- Использовать WorkManager (`androidx.work:work-runtime:2.10.2`, уже есть) для фоновой докачки картинок, чтобы переживать сворачивание приложения. Есть прецедент Worker'ов (`DownloadWorker.kt`, `EventsCheckWorker.kt`).

**Фаза 3 — Offline list screen.**
- Новый экран «Сохранённое/Оффлайн». Добавить `Screen.OfflineList` в `presentation/Screen.kt` (sealed), фрагмент со списком `OfflineItemRoom` (можно сразу на Compose — см. §3.2, по образцу `NotesComposeFragment`/`NotesScreen`).
- ViewModel `OfflineListViewModel` отдаёт `StateFlow` списка из `OfflineRepository` (Room `Flow`).
- Точка входа в меню/навигации (drawer/`OtherMenu` — `Screen.OtherMenu :192`).

**Фаза 4 — Offline rendering.**
- Детектор оффлайна: использовать `ACCESS_NETWORK_STATE` (разрешение уже есть, `AndroidManifest.xml:11`) / `ConnectivityManager`. При открытии сохранённого элемента из оффлайн-списка ИЛИ при отсутствии сети для обычного открытия — подменять источник на сохранённый HTML.
- Внедрить в pipeline: в `ArticleInteractor.loadArticleLocked`/`tryLoadCachedArticle` (`PERF_DIAGNOSIS.md` A.1 `:242,442-481`) добавить ветку «offline lookup» ПЕРЕД сетью; для темы — аналогично в `ThemeViewModel`. Возвращать сохранённый HTML в тот же `loadDataWithBaseURL`-путь (рендер-код не меняется).
- baseURL для `loadDataWithBaseURL` указать на локальную директорию/`WebViewAssetLoader`, чтобы относительные пути картинок резолвились в `file://`.

**Фаза 5 — Image caching.**
- При сохранении пройти по HTML, собрать `<img src>`, скачать через OkHttp (тот же клиент из `di/NetworkModule.kt`) в `offline/<id>/images/`, переписать src → относительный путь.
- Альтернатива: переиспользовать Coil disk cache, но для надёжного оффлайна лучше собственная копия в директории элемента (Coil-кэш вытесняется).
- По завершении — `status=COMPLETE`, обновить `sizeBytes`.

**Фаза 6 — Storage limits / eviction.**
- Лимит на общий размер (настройка, дефолт напр. 200 МБ) и/или по количеству. LRU-вытеснение по `savedAtMs`/последнему открытию (по образцу `ArticleDiskCache` maxEntries/maxAge).
- Экран управления: показать суммарный размер (`OfflineItemDao.sumSize()`), кнопка «Удалить всё», удаление отдельного элемента (Room + `OfflineStorage.delete(id)`).

**(e) Конкретные файлы для интеграции (резюме):**
- Room: `entity/db/notes/NotesDatabase.kt` (AppDatabase), `di/DataModule.kt` (builder + Migration).
- Статья: `model/interactors/news/ArticleInteractor.kt`, `ArticleDiskCache.kt`, `ui/fragments/news/details/ArticleContentFragment.kt`.
- Тема: `presentation/theme/ThemeViewModel.kt`, `ui/fragments/theme/ThemeFragmentWeb.kt`, `ui/TemplateManager.kt`.
- Навигация/меню: `presentation/Screen.kt`, `res/menu/*`, `res/values/strings.xml`.
- Сеть/картинки: `di/NetworkModule.kt` (OkHttp), `androidx.work` Worker по образцу `downloads/DownloadWorker.kt`.

**(f) Подводные камни.**
- **WebView baseURL и `file://`**: для оффлайн-рендера относительные пути должны резолвиться локально. Предпочтительно `WebViewAssetLoader` (безопаснее `file://`-доступа). Проверить CSP/`viewport` в шаблоне `TemplateManager`.
- **CSS/JS-ассеты**: оффлайн-HTML ссылается на `app/src/main/assets/forpda/styles/...` — они есть в APK, грузятся через `file:///android_asset/` — сохранять не нужно, но baseURL должен это позволять.
- **Размер**: статьи с галереями могут быть тяжёлыми — обязательны лимиты (Фаза 6).
- **Миграция Room 6→7**: не использовать `fallbackToDestructiveMigration` (потеряются заметки/история/избранное!). Только явная `Migration(6,7)`.
- **Согласование с §1.3 (KSP)**: новые DAO компилируются KSP — делать 5.1 после 1.3.
- **Версионирование схемы**: после version 7 — закоммитить `app/schemas/7.json` (если включён `room.schemaLocation`, §1.3).

**(g) Проверка.**
```bash
./gradlew :app:assembleStableRelease
./gradlew :app:testStableDebugUnitTest   # включая Migration(6,7) тест
```
На устройстве: сохранить статью и тему → включить авиарежим → открыть из оффлайн-списка → контент и картинки отображаются; проверить лимиты/удаление; проверить, что онлайн-поведение и заметки/история (Room) не пострадали.

**(h) Откат.** Фича аддитивная и за UI-входом. Откат: убрать пункт меню/экран; Room-миграцию 6→7 при откате НЕ откатывать деструктивно (оставить таблицу пустой/неиспользуемой) — понижать версию БД нельзя. На уровне VCS — revert по фазам (каждая фаза — отдельный коммит).

---

## Приложение A — Список проверенных фактов и исправления

Факты из исходного задания, которые при проверке по реальному коду оказались **НЕВЕРНЫМИ** или неточными:

1. **Путь `ArticleInteractor.kt`.** В задании: `model/interactors ArticleInteractor.kt`. Реально: `app/src/main/java/forpdateam/ru/forpda/model/interactors/news/ArticleInteractor.kt` (в подпакете `/news/`). Размер 2470 — подтверждён.

2. **Количество Activity.** В задании (§4.2): «audit all **6** Activities». Реально — **4** Activity: `MainActivity`, `ImageViewerActivity`, `SettingsActivity`, `WebVewNotFoundActivity` (подтверждено и по `*.kt`, и по `AndroidManifest.xml` — 4 тега `<activity>`).

3. **Названия палитр.** В задании: «5 palettes (SYSTEM/MINIMAL_READER/SEPIA_READING/SEPIA_BLUE + theme modes)». Реально enum (`common/Preferences.kt:61`): `SYSTEM, CLASSIC_4PDA, SEPIA_READING, SEPIA_BLUE, MINIMAL_READER`. Палитр действительно 5, но четвёртая называется **`CLASSIC_4PDA`** (в задании её фактически пропустили, упомянув «MINIMAL» отдельно). `MINIMAL_READER` присутствует.

4. **Путь `Preferences.kt`.** В задании подразумевается `Preferences.kt` рядом с палитрами; реально файл — `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt` (пакет `common`, не `model`).

5. **`gradle/libs.versions.toml` уже существует.** В задании (§1.5): «Introduce a Gradle version catalog». Реально файл **уже есть** (201 строка), но **не подключён** к build-файлам и **устарел** (расхождения: `coil 2.6.0` vs реальная `2.7.0`; `androidx-core 1.15.0` vs `1.13.1`; `minSdk 21` vs `24`; `versionMinor 5` vs `9`; отсутствуют Room/Compose/datastore/work и др.; есть несуществующие в проекте библиотеки). Пакет 1.5 превращается из «создать» в «переписать с нуля и подключить».

6. **Номер строки keystore-исключения.** В задании: «plain assembleRelease fails by design at app/build.gradle:258». Реально логика в блоке `gradle.taskGraph.whenReady` (`:239-261`): для `stable` исключение бросается на `:247-251` (нет `keystore.parallel.properties`), для официального `storeRelease` — на `:256-260` (нет RELEASE-ключей). Строка 258 — внутри текста сообщения второго исключения. Также: вариант **`assembleStableRelease` без `keystore.parallel.properties` тоже упадёт** (см. условие `needsParallelStable`), поэтому для проверки компиляции без ключей в документе рекомендован `:app:assembleStableDebug`/`compileStableReleaseKotlin`.

7. **`AppDatabase` хранится в файле `NotesDatabase.kt`.** В задании: «AppDatabase, version 6». Класс действительно называется `AppDatabase`, `version = 6` — подтверждено, но физический файл — `entity/db/notes/NotesDatabase.kt` (единственная Room-БД проекта; «кэши» `*CacheRoom.kt` — обёртки над DAO той же БД, отдельных `@Database` у них нет).

8. **Версия в манифесте vs build.gradle.** `AndroidManifest.xml:4-5` указывает `versionCode 349 / versionName 2.9.3`, тогда как `app/build.gradle:47-50` задаёт `versionCode 350 / versionName 2.9.4` (значения build.gradle имеют приоритет). Незначительно, но отмечено.

9. **Gradle wrapper.** Не указан в задании; проверено — `8.13` (`gradle/wrapper/gradle-wrapper.properties`). Совместим с AGP 8.11.1.

**Подтверждённые без изменений факты задания:** Kotlin 1.9.25, AGP 8.11.1, compile/target SDK 35, minSdk 24, Hilt 2.51.1 via kapt, Room 2.6.1, OkHttp 4.12.0, Coil 2.7.0, Cicerone 6.6, Compose BOM 2024.12.01, kotlinCompilerExtensionVersion 1.5.15, 100% Kotlin, ровно один `@Composable` (`NotesScreen.kt`), god-классы и их размеры (4766/4162/3471/2839/2763/2470/2006), Realm-плагин мёртв, кастомный regex/DOM-парсер (`regex/parser/{Parser,Document,Node}.kt`), `PatternProvider`, отсутствие Jsoup, отсутствие edge-to-edge/predictive-back, наличие `PERF_DIAGNOSIS.md` с TOP-10.

