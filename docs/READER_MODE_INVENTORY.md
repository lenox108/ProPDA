# Reader Mode Inventory

## Existing models
- `Preferences.Main.TopicScrollMode` in `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt` controls topic page navigation behavior with `HYBRID` and `CLASSIC`.
- `Preferences.Main.TopicPostDensity` in `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt` controls topic post layout density with `COMFORTABLE` and `COMPACT`.
- `Preferences.Main.UiPalette` in `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt` includes `MINIMAL_READER`, but this is a color/palette model, not the same model as topic density.
- `ThemeUiEvent.UpdateTopicScrollMode` and `ThemeUiEvent.UpdateTopicPostDensity` in `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt` propagate setting changes to the topic UI.

## Existing modes
- Topic scroll mode:
  - `HYBRID`: infinite/hybrid topic reading with injected scaffold.
  - `CLASSIC`: one page at a time; page movement is through pagination/menu/gestures.
- Topic post density:
  - `COMFORTABLE`: default post layout; maps to `density_comfortable`.
  - `COMPACT`: reduced post/header/block spacing; maps to `density_compact`.
- UI palette:
  - `MINIMAL_READER`: calmer native and WebView colors through theme overrides. It does not currently change post density or navigation mode.

## Where mode is stored
- `app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt`
  - Declares `TOPIC_SCROLL_MODE`, `TOPIC_POST_DENSITY`, and `UI_PALETTE` DataStore keys.
  - Reads legacy SharedPreferences fallback values from `${packageName}_preferences`.
  - Mirrors current values into `main_mirror` for synchronous reads.
  - Defaults invalid or missing `TopicPostDensity` to `COMFORTABLE`.
- `app/src/main/java/forpdateam/ru/forpda/model/preferences/MainPreferencesHolder.kt`
  - Exposes flow accessors, immediate getters, and suspend setters for scroll mode, post density, and UI palette.
- `app/src/main/res/xml/preferences.xml`
  - Defines the `main.topic_post_density` list preference.
- `app/src/main/res/values/arrays.xml`
  - Defines `entries_topic_post_density` and `entry_values_topic_post_density`.
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt`
  - Synchronizes the list preference with `MainPreferencesHolder`, parses lowercase values, updates summaries, and writes changes back to DataStore.

## Where mode affects rendering
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt`
  - Adds `TopicPostDensity` to `renderSignature`.
  - Maps density to `${post_density_class}` through `topicPostDensityClass()`.
  - Injects that class into `template_theme.html`, where topic pages render as `body#topic`.
- `app/src/main/assets/template_theme.html`
  - Applies `${post_density_class}` to `<body id="${body_type}" ...>`.
- `app/src/main/assets/forpda/styles/modules/themes.less`
  - Source LESS for topic layout and `body#topic.density_compact` overrides.
- `app/src/main/assets/forpda/styles/light/light_themes.css`
  - Checked-in generated light CSS containing `body#topic.density_compact` rules.
- `app/src/main/assets/forpda/styles/dark/dark_themes.css`
  - Checked-in generated dark CSS containing `body#topic.density_compact` rules.
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt`
  - Applies native toolbar chrome changes for compact density in `applyTopicToolbarDensityChrome()`.
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`
  - Initializes density chrome and handles `UpdateTopicPostDensity` UI events.
- `app/src/main/java/forpdateam/ru/forpda/ui/TemplateManager.kt`
  - Applies Minimal Reader and Sepia palette WebView overrides through `getThemeOverridesCss()`.

## Safe extension path
- Treat `TopicPostDensity` as the closest existing model for future reader spacing modes.
- Add any future density by extending `Preferences.Main.TopicPostDensity`, then update parsing defaults in `MainDataStore`, preference arrays/strings, summaries in `SettingsFragment`, and `ThemeTemplate.topicPostDensityClass()`.
- Add the matching body class before changing toolbar behavior.
- Put new topic spacing rules in `app/src/main/assets/forpda/styles/modules/themes.less` and keep `light_themes.css` and `dark_themes.css` synchronized if generated CSS remains checked in.
- Keep `MINIMAL_READER` as a palette concern unless a later task explicitly combines palette and density into a broader reader mode model.
- Preserve `COMFORTABLE` as the default and fallback value.

## Risky files
- `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt`: enum additions affect persisted string parsing and preference values.
- `app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt`: storage, legacy fallback, mirror values, and parse defaults must stay consistent.
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt`: list preference values are lowercase in XML but parsed by uppercasing; summaries must cover every mode.
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt`: density changes alter render signatures and full topic HTML generation.
- `app/src/main/assets/template_theme.html`: body class injection affects all topic CSS.
- `app/src/main/assets/forpda/styles/modules/themes.less`: shared topic layout source; broad edits can change post, quote, spoiler, attachment, poll, and pagination rendering.
- `app/src/main/assets/forpda/styles/light/light_themes.css` and `app/src/main/assets/forpda/styles/dark/dark_themes.css`: generated CSS must stay synchronized with source LESS without mass formatting.
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt`: native toolbar density changes can affect system bars, pagination offsets, title sizing, and layout height.
