# План осовременивания тем, палитр и внешнего вида ForPDA

> Документ создан 2026-06-30 как самодостаточный план глубокой переработки тем на
> полноценный Material 3 + Material You с заделом под Compose-first и фишки
> Android 12-15. Сопровождает (не заменяет) `REFACTOR_PLAN.md` (раздел 4.x) и
> `PERF_DIAGNOSIS.md`.
>
> Все пути и номера строк актуальны на дату составления; при рефакторинге
> сверяться с реальным кодом. Проверка сборки: `./gradlew :app:assembleStableDebug`
> (или `:assembleStableRelease` при наличии `keystore.parallel.properties`).

---

## Контекст — что уже есть (кратко)

Темы уже неплохо подготовлены (это не «legacy», а зрелый код):

- Material 3 (`Theme.Material3.DayNight`) как базовый родитель; `Material 1.12.0`.
- `ThemeMode` (`LIGHT/DARK/AMOLED/SYSTEM/SYSTEM_AMOLED`) и `UiPalette`
  (`SYSTEM/SEPIA_READING/SEPIA_BLUE/MINIMAL_READER`) в
  `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt`.
- Material You частично: `MaterialYouApplier.kt` + `MaterialYouPolicy.kt`
  (режимы `NONE/ACCENT_ONLY/SURFACE`).
- Edge-to-edge: `EdgeToEdge.kt`, вызывается в `MainActivity`/`SettingsActivity`.
- 4 Compose-экрана: `NotesScreen`, `QmsContactsScreen`, `ArticlesListScreen`,
  `FavoritesScreen`.

**Главное архитектурное ограничение** — ~35 custom-attr в `res/values/attrs.xml`
(`default_text_color`, `cards_background`, `icon_toolbar`, `link_color`,
`chrome_plane_background`, `main_toolbar_accent_surface`, …). Они читаются через
`TypedArray.getColorStateList`, который **не умеет** дерефить `?attr/...`
(TYPE_ATTRIBUTE) и падает с `UnsupportedOperationException`. Из-за этого dynamic
color сегодня красит только `colorPrimary`, `colorBackground` и `colorError` — а
фоны экранов, карточки, тулбар, навбар, иконки, текст «не видят» обои. Это
подробно задокументировано в `res/values/styles.xml` (блоки
`ThemeOverlay.ForPDA.MaterialYou*`).

## Цель

Полноценная Material 3 + Material You архитектура: обои влияют на весь UI (а не
только акцент), палитры Unified через M3 color roles, Compose-экраны
синхронизированы с темами, настройки тем — с превью, задел для фишек Android
12-15 (predictive back, expressive colors, splash screen с animated icon,
themed icon).

```mermaid
flowchart LR
  A["~35 custom attr<br/>(TypedArray.getColorStateList crash)"] --> B["M3 color roles<br/>(colorOnSurface, colorSurfaceVariant, colorOutline, ...)")
  B --> C["Full Dynamic Color<br/>(обои на всех поверхностях)"]
  B --> D["ForPdaComposeTheme<br/>(Compose-экраны синхронизированы)"]
  B --> E["Material3 components<br/>(MaterialToolbar, Chip, Slider)"]
  B --> F["Palette settings UX<br/>(превью, Carousel)"]
  C --> G["Android 12-15 features<br/>(predictive back, expressive, splash)"]
```

---

## Этап 1 — Фундамент: миграция custom-attr на M3 color roles (КРИТИЧНО)

Это «разблокатор» всего остального. Без него dynamic color не пойдёт глубже
акцента.

**Маппинг (проверить по `res/values/attrs.xml`, ~35 штук):**

| Custom attr | → M3 role |
|---|---|
| `default_text_color`, `contrast_text_color`, `drawer_item_text_selected` | `colorOnSurface` |
| `second_text_color`, `drawer_item_text` | `colorOnSurfaceVariant` |
| `background_base` | `colorSurface` (или `android:colorBackground`) |
| `background_for_cards`, `cards_background`, `chrome_plane_background` | `colorSurfaceContainer` / `colorSurfaceContainerHigh` (Material 1.12+) |
| `background_for_lists` | `colorSurfaceContainerLow` |
| `divider_line`, `divider_line_bottom_nav` | `colorOutlineVariant` |
| `link_color`, `link_color_contrast` | `colorPrimary` (dynamic-safe) |
| `icon_base`, `icon_toolbar`, `menu_tile_icon`, `icon_base_inverse` | `colorOnSurfaceVariant` / `colorOnSurface` |
| `main_toolbar_accent_surface` | `colorPrimaryContainer` |
| `attachment_overlay_bg`, `msg_panel_tooltip_bg`, `profile_overlay_bg` | `colorScrim` (M3) / кастомный через `colorSurfaceVariant` с альфой |
| `count_background`, `notify_dot_tab` | `colorErrorContainer` / `colorPrimaryContainer` (контекстно) |
| `smart_nav_fab_background`, `smart_nav_fab_icon` | `colorPrimaryContainer` / `colorOnPrimaryContainer` |

**Подход (инкрементальный, чтобы не сломать инфляцию):**

1. Для каждого attr — оставить его в `res/values/attrs.xml`, но в
   `res/values/styles.xml` переопределить его как ссылку на M3-роль:
   `<item name="default_text_color">?attr/colorOnSurface</item>` (один слой
   indirection — `getColorStateList` это переварит, т.к. в M3-стилях у этих
   ролей уже конкретные `ColorStateList`, а не `?attr/...`).
2. Убрать прямые `@color/light_*`/`@color/dark_*` для custom-attr в
   `res/values/styles.xml`, `res/values-night/styles.xml`,
   `res/values/styles_sepia.xml`, `res/values/styles_sepia_blue.xml`,
   `res/values/styles_minimal_reader.xml`,
   `res/values-night/styles_amoled_palettes.xml` — теперь они наследуют
   M3-роли.
3. Палитры (`Sepia/SepiaBlue/MinimalReader`) переопределяют уже M3-роли
   (`colorPrimary`, `colorSurface`, `colorOnSurface`, `colorSurfaceContainer*`),
   а не ~40 custom-attr.
4. **Проверка каждого шага**: smoke-инфляция `fragment_base.xml` (бывшее место
   крэша), `MainActivity`, открытие темы/статьи/QMS.

**Подводные камни:**

- `TypedArray.getColorStateList` падает только на **2 уровнях indirection**.
  Один уровень (`?attr/colorOnSurface` → конкретный `ColorStateList`) —
  безопасен. Не закладывать в custom-attr ссылки на другие custom-attr.
- AMOLED: через `colorSurfaceContainerLowest=#000000` + `colorSurface=#000000`
  вместо отдельной темы `AmoledAppTheme` в `values-night/styles.xml`.
- `ThemeOverlay.ForPDA.MaterialYouSurface` (строки 104-139 `styles.xml`) потеряет
  актуальность после миграции — `link_color` и `textColor*` будут dynamic-safe
  через `colorPrimary`/`colorOnSurface`. Упростить до
  `android:colorBackground → ?attr/colorSurface`.

**Файлы:**

- `app/src/main/res/values/attrs.xml` (пометить legacy как deprecated)
- `app/src/main/res/values/styles.xml` (`DayNightAppTheme`, строки 257-370)
- `app/src/main/res/values-night/styles.xml`
- `app/src/main/res/values/styles_sepia.xml`,
  `app/src/main/res/values/styles_sepia_blue.xml`,
  `app/src/main/res/values/styles_minimal_reader.xml`,
  `app/src/main/res/values-night/styles_amoled_palettes.xml`
- `app/src/main/res/values/colors.xml` (теперь только «палитры исходников», не
  app-attr)

---

## Этап 2 — Compose-тема: `ForPdaComposeTheme`

Сейчас 4 Compose-экрана используют дефолтный фиолетовый MaterialTheme,
игнорируя 4 палитры приложения и Material You.

**Создать `app/src/main/java/forpdateam/ru/forpda/ui/compose/theme/`:**

- `Theme.kt` — `@Composable fun ForPdaTheme(content)`:
  - Читает `LocalContext` →
    `MaterialColors.getColorStateListOrNull(context, android.R.attr.colorBackground)`
    (Material 1.12+) для построения `lightColorScheme`/`darkColorScheme` **из
    текущей AppCompat-темы** (мост View→Compose).
  - На Android 12+ и палитре `SYSTEM` с включённым Material You —
    `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`.
  - Определяет `isSystemInDarkTheme()` через `LocalConfiguration` + `UiMode`
    (не через `MainDataStore`, чтобы не гонять Flow при рекомпозиции).
- `Type.kt` — `Typography` из M3 + текущий `FontController.fontMode`
  (Roboto/Inter/SourceSans3/OpenSans).
- `Shape.kt` — `Shapes` со скруглениями 10/16/28dp (как в
  `ShapeAppearance.ForPDA.*` в `styles.xml` строки 674-697).

**Подключить:**

- В каждом `*ComposeFragment.kt` обернуть `setContent { ForPdaTheme { XxxScreen(...) } }`.
- Хосты: `NotesComposeFragment`, `QmsContactsComposeFragment`,
  `ArticlesListComposeFragment`, `FavoritesComposeFragment`.

**Файлы:** новый `ui/compose/theme/{Theme,Type,Shape}.kt`, 4 `*ComposeFragment.kt`.

---

## Этап 3 — Material You v2: расширенная динамика

После Этапа 1 dynamic color будет работать на всех поверхностях. Дополнительно:

- **Удалить обходные пути** в `MaterialYouApplier.kt`: KDoc про
  `Theme.AppCompat.Empty` и `UnsupportedOperationException` больше не актуален
  — `HarmonizedColors.applyToContextIfAvailable()` станет безопасным. Вернуться
  к каноническому вызову, удалить `ThemeOverlay.ForPDA.HarmonizedError`.
- **MaterialYouPolicy**: добавить `Mode.EXPRESSIVE` (Material 1.13+ для Android
  14+) — на Android 14+ опционально более насыщенные производные цвета.
  Расширить enum в `MaterialYouPolicy.kt`.
- **Contrast levels** (Android 14+, Material 1.12
  `DynamicColorsOptions.setContrastLevel()`): новый тумблер «Высокий контраст»
  в настройках (доступно только SDK 34+).
- **`values-v31/themes.xml`**: новый файл с
  `ThemeOverlay.ForPDA.DynamicColorRoot`, который применяется на Android 12+
  через `android:theme` в `application` (как альтернатива программному apply).
  Сейчас `res/values-v33/themes.xml` пустой.

---

## Этап 4 — Material 3 компоненты

- **MaterialToolbar**: заменить `androidx.appcompat.widget.Toolbar`
  (`res/layout/fragment_base.xml`, `:61`) на `MaterialToolbar`. После Этапа 1
  крэши `Toolbar.<init>` уйдут. Получим M3-styled app bar с поддержкой
  `centerAligned` и collapsed/expanded title styling.
- **Chip / ChipGroup / FilterChip**: заменить `Spinner`-фильтры на `FilterChip`
  (например, в `fragment_base.xml:143` фильтры списков, в фильтре новостей).
- **Slider** в настройках: `WEBVIEW_FONT_SIZE` через Material `Slider` с live
  preview вместо `ListPreference` или `SeekBar`.
- **SearchBar** (M3): опционально для нового экрана поиска.
- **NavigationBar / NavigationRail** для sw600dp: сейчас своя реализация bottom
  drawer; для планшетов добавить `NavigationRail` в `res/values-sw600dp/`.

**Файлы:** `fragment_base.xml`, `res/xml/preferences.xml`, item-layouts
фильтров, новый `Widget.ForPDA.Chip.*` стиль.

---

## Этап 5 — UX настроек тем/палитр с превью

Сейчас темы/палитры — это `ListPreference` в `res/xml/preferences.xml`. На
Android 13+ системные настройки используют визуальные карточки с превью цветов.

**Создать Preference-фрагмент «Внешний вид» с Compose:**

- Новый `AppearanceSettingsFragment` (Compose через `ComposeView` в
  `PreferenceFragmentCompat`, или отдельный экран по образцу
  `NotesComposeFragment`).
- Карточки палитр с превью: каждая палитра — мини-превью с цветами (background,
  surface, primary, onSurface).
- Кнопка-чип для Material You toggle с понятным описанием и иконкой обоев.
- Слайдер Contrast (Этап 3).
- Предпросмотр текста/карточки прямо в карточке настройки.

**Файлы:** новый `ui/compose/screens/AppearanceSettingsScreen.kt`,
host-fragment, регистрация в `res/xml/preferences.xml` или новый пункт меню.

---

## Этап 6 — Задел на будущее: Android 12-15 features

- **Predictive back** (§4.3 `REFACTOR_PLAN.md`):
  `android:enableOnBackInvokedCallback="true"` в `AndroidManifest.xml`;
  мигрировать любые `onBackPressed()` на `OnBackPressedDispatcher` +
  `OnBackPressedCallback`.
- **Splash screen с animated icon**: `androidx.core:core-splashscreen` (Android
  13+). Заменить `@drawable/bg_splash_light` (`styles.xml:250-252`) на
  `SplashScreen` API с animated icon.
- **Themed icons (Android 13+)**: `mipmap-anydpi-v26/ic_launcher_monochrome.xml`
  уже есть — добавить `<activity-alias>` с `android:icon` для разных
  launcher-тем (или положиться на системный themed icon).
- **Per-app language preferences** (Android 13+, `AppLocales`): сейчас
  локализация в `res/values-en/strings.xml` — добавить системную опцию.
- **Edge-to-edge для Android 15 targetSdk 35**: уже включён через
  `EdgeToEdge.kt`; убедиться что insets корректны для всех фрагментов (тулбар,
  FAB, reply panel с IME).
- **Material 3 Expressive (Material 1.14+, когда выйдет)**: задел — `colorRoles`
  через `ColorRoles.createExpressive()`, отражение в
  `MaterialYouPolicy.Mode.EXPRESSIVE`.

---

## Порядок выполнения и приоритеты

| # | Этап | Риск | Зависимости |
|---|---|---|---|
| 1 | **Этап 1**: custom-attr → M3 roles | высокий (точечно, по одному attr) | — |
| 2 | **Этап 2**: Compose-тема | низкий | Этап 1 (для корректной палитры) |
| 3 | **Этап 3**: Material You v2 (упрощение, expressive, contrast) | средний | Этап 1 |
| 4 | **Этап 4**: M3-компоненты | средний | Этап 1 (MaterialToolbar после миграции) |
| 5 | **Этап 5**: UX настроек с превью | низкий | Этап 2, 3 |
| 6 | **Этап 6**: Android 12-15 features | низкий | — |

Каждый этап — отдельные коммиты. После каждого:
`./gradlew :app:assembleStableDebug` + `./gradlew :app:testStableDebugUnitTest`
+ smoke на устройстве (открыть тему/статью/QMS, сменить палитру, включить
Material You, сменить обои).

---

## Проверка и откат

- `./gradlew :app:assembleStableDebug` (или `:assembleStableRelease` при
  наличии ключей).
- `./gradlew :app:testStableDebugUnitTest` — есть тесты `MaterialYouApplierTest`,
  `FragmentBaseTextViewInflateTest`.
- Smoke на Android 12+ и 15: смена обоев → все поверхности перекрашиваются;
  смена палитры → Compose-экраны следуют; включение/выключение Material You →
  видимое изменение.
- Откат: поэтапно (каждый этап — отдельный набор коммитов). Этап 1 — самый
  рискованный, делать в отдельной ветке с возможностью `git revert` по attr.

---

## Замечание о приоритетах

Это большой план. Этап 1 даёт максимальный ROI (dynamic color заработает
полностью), Этап 2 — самое дешёвое и быстрое (Compose-экраны
синхронизируются).
