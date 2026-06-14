# UI Thread Rendering Inventory

## Quote pipeline
- file: `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeParser.kt`
- responsibility: parses the forum response into `ThemePage` and `ThemePost` models. Post body HTML is kept in `ThemePost.body`; quote markup is not rebuilt here.

- file: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt`
- responsibility: forms the final topic HTML from `ThemePage.posts`; each post body is inserted into `template_theme.html` through the `${body}` variable.

- file: `app/src/main/assets/template_theme.html`
- responsibility: defines the WebView document for a topic. Post content is rendered inside `<div class="post_body emoticons">${body}</div>`. The template also loads `blocks.js`, `modules/theme.js`, and `${style_type}_themes.css`.

- file: `app/src/main/assets/forpda/scripts/blocks.js`
- responsibility: transforms quote blocks after render. `transformQuotes()` processes `.post-block.quote`, rewrites `.block-title` into avatar/name/date markup, and marks quotes as `transformed`. `blocksOpenClose()` handles spoilers and code blocks, not quotes.

- file: `app/src/main/assets/forpda/scripts/modules/theme.js`
- responsibility: theme-level WebView interactions, scroll/anchor restore, overlay state, post action helpers, poll and hat controls. It is not the primary quote transformer, but it is a safe place to call render-time JS if a template-level hook is needed.

## CSS/theme pipeline
- file: `app/src/main/assets/forpda/styles/modules/themes.less`
- responsibility: source LESS for topic post layout, `.post_body`, `.post_container`, `.post-block.quote`, spoiler/code blocks, attachments, bottom pagination, and `body#topic.density_compact` overrides.

- file: `app/src/main/assets/forpda/styles/light/light_themes.css`
- responsibility: generated light CSS loaded by `template_theme.html` when `style_type` is `light`.

- file: `app/src/main/assets/forpda/styles/dark/dark_themes.css`
- responsibility: generated dark CSS loaded by `template_theme.html` when `style_type` is `dark`.

- file: `app/src/main/assets/forpda/styles/light/config_light.less`
- responsibility: light theme variables used by topic LESS, including `@quote_border_color`, `@quote_surface`, attachment colors, text colors, and surfaces.

- file: `app/src/main/assets/forpda/styles/dark/config_dark.less`
- responsibility: dark theme variables used by topic LESS, including `@quote_border_color`, `@quote_surface`, attachment colors, text colors, and surfaces.

- file: `app/src/main/java/forpdateam/ru/forpda/ui/TemplateManager.kt`
- responsibility: resolves `style_type` from `DayNightHelper`, loads `template_theme.html`, and injects inline palette/layout overrides through `theme_overrides_css`.

- file: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeStyleHandler.kt`
- responsibility: sets native WebView background, padding, and relative font size.

- file: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebViewHost.kt`
- responsibility: attaches/configures the topic `ExtendedWebView` and sets the native background used behind the rendered page.

## Reader/density settings
- file: `app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt`
- responsibility: declares `TopicPostDensity` as `COMFORTABLE` and `COMPACT`; also declares UI palette values including `MINIMAL_READER`.

- file: `app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt`
- responsibility: stores and reads `TOPIC_POST_DENSITY`, mirrors it to SharedPreferences, and defaults invalid or missing values to `COMFORTABLE`.

- file: `app/src/main/java/forpdateam/ru/forpda/model/preferences/MainPreferencesHolder.kt`
- responsibility: exposes synchronous and flow accessors for topic scroll mode, topic post density, theme mode, and UI palette.

- file: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt`
- responsibility: maps `TopicPostDensity` to the body class `density_compact` or `density_comfortable` via `topicPostDensityClass()`, and includes the density name in `renderSignature`.

- file: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
- responsibility: observes topic post density changes and emits UI events so the currently open topic can update.

- file: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`
- responsibility: applies density-related native chrome changes through `updateTopicPostDensityChrome(...)` and reacts to `UpdateTopicPostDensity` events.

- file: `app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt`
- responsibility: binds the `TOPIC_POST_DENSITY` list preference to `MainPreferencesHolder` and updates its summary.

- file: `app/src/main/res/values/strings.xml`
- responsibility: Russian labels/summaries for `TopicPostDensity` and `Minimal Reader UI`.

- file: `app/src/main/res/values-en/strings.xml`
- responsibility: English labels/summaries for `TopicPostDensity` and `Minimal Reader UI`.

- file: `app/src/main/res/values/styles_minimal_reader.xml`
- responsibility: Minimal Reader UI palette for native colors and WebView theme attributes.

- file: `app/src/main/res/values-night/styles_amoled_palettes.xml`
- responsibility: AMOLED-specific Minimal Reader UI palette overrides.

## Safe insertion points
1. Add Smart Quotes initialization to `app/src/main/assets/forpda/scripts/blocks.js` near `transformQuotes()`, guarded so already-processed quotes are skipped.
2. Add Smart Quotes CSS classes to `app/src/main/assets/forpda/styles/modules/themes.less` near existing `.post-block.quote` styles, then keep generated `light_themes.css` and `dark_themes.css` in sync if the project expects checked-in compiled CSS.
3. If a template-level call is needed, place it in `app/src/main/assets/template_theme.html` after the existing script imports or in the existing render lifecycle in `app/src/main/assets/forpda/scripts/modules/theme.js`.
4. For a simple feature flag, use a single JS-side default in `blocks.js` first; use Kotlin settings only if a later task explicitly requires integrating with the existing settings pipeline.
5. For reader/density extensions, start from `Preferences.Main.TopicPostDensity`, `MainDataStore`, `ThemeTemplate.topicPostDensityClass()`, and the density CSS in `themes.less`.

## Do not touch
- Gradle, SDK, signing, packaging, CI, or dependency files.
- Auth/session/cookies, networking, Room schema, or parser internals for Smart Quotes.
- Navigation and toolbar lifecycle while working on quote collapse.
- QMS, article/news rendering, search templates, and forum rules templates unless a later task explicitly includes them.
- `patch.diff` and unrelated post-patch documentation.
- Existing destructive JS bridge contracts in `ThemeJsInterface`.
