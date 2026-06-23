# Hybrid Theme Mode Audit Context

Generated: 2026-06-20T06:46:29.431Z
Workspace: /Users/j.golt/Documents/Cursor01/ForPDA-master
Workspace ID: ws_ca0602d51035b59a06b67e0c
Write mode: workspace
Bash mode: safe
Tool mode: standard

Purpose: paste this bundle into a high-context ChatGPT model when that model cannot call the CodexPro MCP tools directly.
Instruction for ChatGPT: use this as repository context, produce a narrow Codex execution plan, and avoid inventing files or runtime facts not shown here.

> **Post-bundle note (2026-06-23, two-pass):** the entire **offline** feature is
> gone. Pass 1 removed the **Theme Offline** UI (`Screen.OfflineList`,
> `OfflineListComposeFragment`, `OfflineListViewModel`, `OfflineListScreen`,
> `R.menu.theme_details`, the `action_save_offline` overflow item in
> `ThemeFragmentWeb`, `ThemeViewModel.currentData()`, the
> `useComposeOfflineList` A/B flag in `TabHelper`) but kept the shared
> `OfflineRepository` / `OfflineStorage` / `OfflineSaveController` /
> `OfflineItemRoom` data layer so the article ("Сохранить для оффлайна")
> path could still use it. Pass 2 — documented in `docs/AUDIT_REPORT.md`
> §11.2 — now also removes the article offline path and the data layer
> itself: `model/data/offline/` and `entity/db/offline/` packages,
> `MIGRATION_6_7` / `MIGRATION_7_8` / `MIGRATION_8_9` (replaced by
> `MIGRATION_7_6` / `MIGRATION_8_6` / `MIGRATION_9_6` that drop the
> `offline_items` table), `NotesDatabase` v9 → v6, the `R.menu.article_details`
> overflow item, the `offline.max_bytes_mb` preference category in Settings,
> every `R.string.*offline*` / `R.string.save_for_offline*` and matching
> string-array, the `R.drawable.ic_news_offline_black_24dp` icon and the
> dead `R.layout.news_item_compat.xml` layout, the `Constants.TAB_OFFLINE`
> tab identifier, all `provideOffline*` providers in `DataModule`, the
> offline WebView plumbing in `ArticleContentFragment` (including the
> `WebViewAssetLoader` overrides on the inner `ArticleWebViewClient`),
> and the entire `app/src/test/java/.../model/data/offline/` test
> package.

## Repository Tree

.
├── Ключи для forpda/
│   ├── debug.jks
│   ├── forpda-parallel.jks
│   └── keystore.parallel.properties
├── app/
│   ├── schemas/
│   │   └── forpdateam.ru.forpda.entity.db.notes.AppDatabase/
│   │       ├── 6.json
│   │       ├── 7.json
│   │       └── 8.json
│   ├── src/
│   │   ├── beta/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── dev/
│   │   │   └── AndroidManifest.xml
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   ├── java/
│   │   │   ├── res/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── ic_launchero-web.png
│   │   ├── stable/
│   │   │   └── AndroidManifest.xml
│   │   ├── store/
│   │   │   └── res/
│   │   └── test/
│   │       ├── java/
│   │       └── resources/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── version.properties
├── docs/
│   ├── perf/
│   │   └── baselines/
│   │       └── README.md
│   ├── ACCESSIBILITY_CHECKLIST.md
│   ├── AI_REGRESSION_CHECKLIST.md
│   ├── AI_SPEC_COMPLETION_STATUS.md
│   ├── AUDIT_AND_ACTIONS.md
│   ├── AUDIT_REPORT.md
│   ├── BACKLOG_DEFERRED.md
│   ├── CAPTCHA_FALLBACK.md
│   ├── CI_CHECKS.md
│   ├── CP1251_NOTES.md
│   ├── DEBUG_LOGGING.md
│   ├── IMPLEMENTATION_SUMMARY.md
│   ├── JS_BRIDGE_INVENTORY.md
│   ├── LARGE_SCREEN_CHECKLIST.md
│   ├── MEMORY_QA_CHECKLIST.md
│   ├── navigation-scroll-fixes-plan.md
│   ├── OBS_01_STRICTMODE_CLEANUP.md
│   ├── PERFORMANCE_CHECKLIST.md
│   ├── PLAN.md
│   ├── POST_ACTIONS_INVENTORY.md
│   ├── PROPDA_AUDIT_FIX_FINAL_SANITY.md
│   ├── PROPDA_BLANK_TOPIC_DIAGNOSIS.md
│   ├── PROPDA_DEVICE_QA_MATRIX.md
│   ├── PROPDA_FINAL_CODE_AUDIT_REPORT.md
│   ├── PROPDA_FINAL_RELEASE_SANITY.md
│   ├── PROPDA_HARDENING_REPORT.md
│   ├── PROPDA_QC_01_RELEASE_SANITY.md
│   ├── PROPDA_QC_02_RUNTIME_CLEANUP.md
│   ├── PROPDA_QC_03_PERFORMANCE.md
│   ├── PROPDA_RC_01_BETA_READINESS.md
│   ├── PROPDA_STRICTMODE_REVIEW.md
│   ├── QMS_BRIDGE_INVENTORY.md
│   ├── READER_MODE_INVENTORY.md
│   ├── RELEASE_GATE_STATUS.md
│   ├── STARTUP_BASELINE.md
│   ├── test-failures-pre-existing.md
│   ├── test-fixes-plan.md
│   ├── topic-highlight-qa.md
│   ├── UI_THREAD_FEATURE_FLAGS.md
│   ├── UI_THREAD_REGRESSION_CHECKLIST.md
│   ├── UI_THREAD_RENDERING_INVENTORY.md
│   ├── UX_STATE_CHECKLIST.md
│   ├── VERSION_CATALOG_CLEANUP_PLAN.md
│   ├── VERSION_CATALOG_CLEANUP.md
│   └── WEBVIEW_INTERCEPT_PROFILING.md
├── gh_res/
│   ├── icon_4pda.png
│   ├── logo.png
│   ├── res.md
│   ├── screen1.png
│   ├── screen2.png
│   └── screen3.png
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── res/
│   ├── drawable/
│   │   └── ic_launcher_background.xml
│   ├── drawable-night/
│   │   └── ic_launcher_background.xml
│   ├── drawable-night-nodpi/
│   │   └── ic_launcher_foreground.png
│   ├── drawable-nodpi/
│   │   └── ic_launcher_foreground.png
│   ├── mipmap-anydpi-v26/
│   │   ├── ic_launcher_round.xml
│   │   └── ic_launcher.xml
│   ├── mipmap-hdpi/
│   │   ├── ic_launcher_dark.png
│   │   ├── ic_launcher_light.png
│   │   └── ic_launcher.png
│   ├── mipmap-mdpi/
│   │   ├── ic_launcher_dark.png
│   │   ├── ic_launcher_light.png
│   │   └── ic_launcher.png
│   ├── mipmap-xhdpi/
│   │   ├── ic_launcher_dark.png
│   │   ├── ic_launcher_light.png
│   │   └── ic_launcher.png
│   ├── mipmap-xxhdpi/
│   │   ├── ic_launcher_dark.png
│   │   ├── ic_launcher_light.png
│   │   └── ic_launcher.png
│   └── mipmap-xxxhdpi/
│       ├── ic_launcher_dark.png
│       ├── ic_launcher_light.png
│       └── ic_launcher.png
├── scripts/
│   ├── battery-audit.README.md
│   ├── battery-audit.sh
│   ├── create-parallel-keystore.sh
│   ├── startup-benchmark.README.md
│   └── startup-benchmark.sh
├── signing/
│   ├── debug.jks
│   └── forpda-parallel.jks
├── update_helper/
│   ├── scripts/
│   │   └── main.js
│   ├── styles/
│   │   ├── main.css
│   │   └── main.less
│   └── index.html
├── Снимок экрана — 2026-05-05 в 15.54.31.jpg
├── ANDROID_RES_DARK_FIXED.zip
├── ANDROID_RES_FINAL_WITH_NEW_DARK_ICON.zip
├── build.gradle
├── detekt-baseline.xml
├── detekt.yml
├── fix_favorites_latest_sort_followup.diff
├── fix_spoiler_animation_smooth.diff
├── fix_spoiler_palette_stripes.diff
├── ForPDA-master.zip
├── ForPDA-source-2026-06-14.zip
├── Gemini_Generated_Image_ge3zdtge3zdtge3z.png
├── Gemini_Generated_Image_mcso5wmcso5wmcso.png
├── gradle.properties
├── gradlew
├── gradlew.bat
├── icon_dark_129.png
├── icon_white_129.png
├── keystore.parallel.properties
├── keystore.parallel.properties.example
├── LICENSE
├── local.properties
├── perf_02_long_session_webview.diff
├── PERF_DIAGNOSIS.md
├── README.md
├── REFACTOR_PLAN.md
├── settings.gradle
├── thumb_down_final.png
└── thumb_up_final.png

## Git Status

```text
## cursor/add-datastore-settings...origin/cursor/add-datastore-settings [ahead 66]
 M app/build.gradle
 M app/proguard-rules.pro
 M app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/7.json
 M app/src/main/AndroidManifest.xml
 M app/src/main/assets/forpda/scripts/modules/theme.js
 M app/src/main/assets/forpda/styles/dark/dark_themes.css
 M app/src/main/assets/forpda/styles/light/light_themes.css
 D app/src/main/assets/forpda/styles/modules/themes.less
 M app/src/main/assets/template_theme.html
 M app/src/main/java/forpdateam/ru/forpda/App.kt
 M app/src/main/java/forpdateam/ru/forpda/client/Client.kt
 M app/src/main/java/forpdateam/ru/forpda/client/interceptors/ImageLoadingInterceptor.kt
 M app/src/main/java/forpdateam/ru/forpda/common/HtmlToSpannedConverter.kt
 M app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt
 M app/src/main/java/forpdateam/ru/forpda/common/receivers/WakeUpReceiver.kt
 M app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt
 M app/src/main/java/forpdateam/ru/forpda/di/DataModule.kt
 M app/src/main/java/forpdateam/ru/forpda/diagnostic/FpdaDebugLog.kt
 M app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesDatabase.kt
 M app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesMigrations.kt
 D app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemDao.kt
 D app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemRoom.kt
 M app/src/main/java/forpdateam/ru/forpda/entity/remote/events/NotificationEvent.kt
 M app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineArticleSource.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineImageDownloader.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineIndexPathHandler.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineRepository.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineSaveController.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineStorage.kt
 D app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineWebViewBaseUrl.kt
 M app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/Constants.kt
 M app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt
 M app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchParser.kt
 M app/src/main/java/forpdateam/ru/forpda/model/datastore/ListsDataStore.kt
 M app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt
 M app/src/main/java/forpdateam/ru/forpda/model/datastore/NotificationDataStore.kt
 M app/src/main/java/forpdateam/ru/forpda/model/datastore/TopicDataStore.kt
 M app/src/main/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCase.kt
 M app/src/main/java/forpdateam/ru/forpda/model/preferences/NotificationPreferencesHolder.kt
 M app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt
 M app/src/main/java/forpdateam/ru/forpda/model/repository/events/EventsRepository.kt
 M app/src/main/java/forpdateam/ru/forpda/notifications/EventsCheckWorker.kt
 M app/src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/Screen.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/TabRouter.kt
 D app/src/main/java/forpdateam/ru/forpda/presentation/offline/OfflineListViewModel.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryController.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollController.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeLoadStateMachine.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeOpenScrollCoalescePolicy.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemePostedPageScrollPolicy.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeWebCallbacks.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicNavigationModels.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicOpenScrollRestorePolicy.kt
 M app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicPrependedHatPolicy.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/activities/MainActivity.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/activities/SettingsActivity.kt
 D app/src/main/java/forpdateam/ru/forpda/ui/compose/screens/OfflineListScreen.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt
 D app/src/main/java/forpdateam/ru/forpda/ui/fragments/offline/OfflineListComposeFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/AnnounceFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/ForumRulesFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelper_V2.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApi.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/navigation/TabHelper.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/navigation/TabNavigator.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/navigation/TabNavigatorThemeSwitchPolicy.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/MessagePanel.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/advanced/CodesPanelItem.kt
 M app/src/main/java/forpdateam/ru/forpda/ui/views/pagination/PaginationHelper.kt
 M app/src/main/res/menu/article_details.xml
 M app/src/main/res/menu/theme_details.xml
 M app/src/main/res/menu/theme_search_menu.xml
 M app/src/main/res/values-en/strings.xml
 M app/src/main/res/values/arrays.xml
 M app/src/main/res/values/strings.xml
 M app/src/main/res/values/styles.xml
 M app/src/main/res/xml/preferences.xml
 M app/src/main/res/xml/preferences_notifications.xml
 M app/src/test/java/forpdateam/ru/forpda/entity/db/notes/AppDatabaseMigrationTest.kt
 D app/src/test/java/forpdateam/ru/forpda/model/data/offline/OfflineArticleSourceTest.kt
 D app/src/test/java/forpdateam/ru/forpda/model/data/offline/OfflineImageDownloaderTest.kt
 D app/src/test/java/forpdateam/ru/forpda/model/data/offline/OfflineRepositoryEvictionTest.kt
 M app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApiTest.kt
 M app/src/test/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCaseTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryControllerSnapshotTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterfaceTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeOpenScrollCoalescePolicyTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemePostedPageScrollPolicyTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplateTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicBackSnapshotTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicOpenTargetMapperTest.kt
 M app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicPrependedHatPolicyTest.kt
 D app/src/test/java/forpdateam/ru/forpda/ui/fragments/offline/OfflineListOpenItemRoutingTest.kt
 M app/src/test/java/forpdateam/ru/forpda/ui/navigation/TabNavigatorThemeSwitchPolicyTest.kt
 M docs/READER_MODE_INVENTORY.md
 M docs/UI_THREAD_RENDERING_INVENTORY.md
 M gradle/libs.versions.toml
?? .ai-bridge/
?? app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/8.json
?? app/src/main/java/forpdateam/ru/forpda/client/interceptors/CacheControlInterceptor.kt
?? app/src/main/java/forpdateam/ru/forpda/common/di/
?? app/src/main/java/forpdateam/ru/forpda/diagnostic/ColdStartTracer.kt
?? app/src/main/java/forpdateam/ru/forpda/diagnostic/TopicHighlightDiagnostics.kt
?? app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarNotFoundException.kt
?? app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemeReadPositionRepository.kt
?? app/src/main/java/forpdateam/ru/forpda/notifications/CounterGrowthDetector.kt
?? app/src/main/java/forpdateam/ru/forpda/notifications/EventsCheckScheduler.kt
?? app/src/main/java/forpdateam/ru/forpda/notifications/ForumHeaderCounters.kt
?? app/src/main/java/forpdateam/ru/forpda/notifications/MentionNotificationMapper.kt
?? app/src/main/java/forpdateam/ru/forpda/notifications/NotificationPublisher.kt
?? app/src/main/java/forpdateam/ru/forpda/notifications/NotificationSnapshotLock.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/FindOnPageState.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/HighlightArmingPolicy.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/HighlightExplicitPostPolicy.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/HighlightResolver.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/MaterialYouPolicy.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/ReadPositionSaveGate.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeBackRestoreUrlPolicy.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeLinkNavigationPolicy.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicHighlightApply.kt
?? app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicHighlightModels.kt
?? app/src/main/java/forpdateam/ru/forpda/ui/MaterialYouApplier.kt
?? app/src/test/java/forpdateam/ru/forpda/client/CachedDnsClearTest.kt
?? app/src/test/java/forpdateam/ru/forpda/client/interceptors/CacheControlInterceptorTest.kt
?? app/src/test/java/forpdateam/ru/forpda/diagnostic/ColdStartTracerTest.kt
?? app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchParserGoldenTest.kt
?? app/src/test/java/forpdateam/ru/forpda/model/repository/avatar/
?? app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemeReadPositionRepositoryTest.kt
?? app/src/test/java/forpdateam/ru/forpda/model/system/
?? app/src/test/java/forpdateam/ru/forpda/notifications/
?? app/src/test/java/forpdateam/ru/forpda/presentation/TabRouterCleanupTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/FindOnPageStateTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightArmingPolicyTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightExplicitPostPolicyTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightJsGuardTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightResolverTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/MaterialYouPolicyTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/ReadPositionSaveGateTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/SourceAnchorTtlTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeBackNavigationTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryControllerTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollTargetPageTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeLinkNavigationPolicyTest.kt
?? app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicHighlightApplyTest.kt
?? app/src/test/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesPaginationScrollTest.kt
?? app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelperV2FragmentLifecycleTest.kt
?? app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelperV2ScopeTest.kt
?? app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApiApplyHighlightTest.kt
?? app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApiScheduleHighlightFadeoutTest.kt
?? app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebControllerFadeoutTest.kt
?? docs/AUDIT_AND_ACTIONS.md
?? docs/AUDIT_REPORT.md
?? docs/BACKLOG_DEFERRED.md
?? docs/CAPTCHA_FALLBACK.md
?? docs/CP1251_NOTES.md
?? docs/IMPLEMENTATION_SUMMARY.md
?? docs/MEMORY_QA_CHECKLIST.md
?? docs/PLAN.md
?? docs/RELEASE_GATE_STATUS.md
?? docs/STARTUP_BASELINE.md
?? docs/navigation-scroll-fixes-plan.md
?? docs/perf/
?? docs/test-fixes-plan.md
?? docs/topic-highlight-qa.md
?? scripts/battery-audit.README.md
?? scripts/battery-audit.sh
?? scripts/startup-benchmark.README.md
?? scripts/startup-benchmark.sh
```

## Recent Commits

```text
5a8deb7 (HEAD -> cursor/add-datastore-settings) [battery] Convert ThemeFragmentWeb watchdogs to coroutines
6356c30 [battery] Move AppMetrica init off the main thread
ca1ed11 [battery] Promote NotificationsService to foreground (dataSync)
06be677 [battery] Make FavoritesCacheRoom.saveFavorites atomic
b87cf3b [battery] Halve Coil image disk cache to 128MB
81f22d2 [battery] Reduce OkHttp main client timeouts to 20s/30s
1f77d04 [battery] Drop WakeUpReceiver singleton executor
786deb2 [battery] Debounce FavoritesAdapter prefs setters
```

## Selected Files

Changed files detected: app/build.gradle, app/proguard-rules.pro, app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/7.json, app/src/main/AndroidManifest.xml, app/src/main/assets/forpda/scripts/modules/theme.js, app/src/main/assets/forpda/styles/dark/dark_themes.css, app/src/main/assets/forpda/styles/light/light_themes.css, app/src/main/assets/forpda/styles/modules/themes.less, app/src/main/assets/template_theme.html, app/src/main/java/forpdateam/ru/forpda/App.kt, app/src/main/java/forpdateam/ru/forpda/client/Client.kt, app/src/main/java/forpdateam/ru/forpda/client/interceptors/ImageLoadingInterceptor.kt, app/src/main/java/forpdateam/ru/forpda/common/HtmlToSpannedConverter.kt, app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt, app/src/main/java/forpdateam/ru/forpda/common/receivers/WakeUpReceiver.kt, app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt, app/src/main/java/forpdateam/ru/forpda/di/DataModule.kt, app/src/main/java/forpdateam/ru/forpda/diagnostic/FpdaDebugLog.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesDatabase.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesMigrations.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemDao.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemRoom.kt, app/src/main/java/forpdateam/ru/forpda/entity/remote/events/NotificationEvent.kt, app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineArticleSource.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineImageDownloader.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineIndexPathHandler.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineRepository.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineSaveController.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineStorage.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineWebViewBaseUrl.kt, app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/Constants.kt, app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt, app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchParser.kt, app/src/main/java/forpdateam/ru/forpda/model/datastore/ListsDataStore.kt, app/src/main/java/forpdateam/ru/forpda/model/datastore/MainDataStore.kt, app/src/main/java/forpdateam/ru/forpda/model/datastore/NotificationDataStore.kt, app/src/main/java/forpdateam/ru/forpda/model/datastore/TopicDataStore.kt, app/src/main/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCase.kt, app/src/main/java/forpdateam/ru/forpda/model/preferences/NotificationPreferencesHolder.kt, app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt, app/src/main/java/forpdateam/ru/forpda/model/repository/events/EventsRepository.kt, app/src/main/java/forpdateam/ru/forpda/notifications/EventsCheckWorker.kt, app/src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt, app/src/main/java/forpdateam/ru/forpda/presentation/Screen.kt, app/src/main/java/forpdateam/ru/forpda/presentation/TabRouter.kt, app/src/main/java/forpdateam/ru/forpda/presentation/offline/OfflineListViewModel.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryController.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollController.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeLoadStateMachine.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeOpenScrollCoalescePolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemePostedPageScrollPolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeWebCallbacks.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicNavigationModels.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicOpenScrollRestorePolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicPrependedHatPolicy.kt, app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt, app/src/main/java/forpdateam/ru/forpda/ui/activities/MainActivity.kt, app/src/main/java/forpdateam/ru/forpda/ui/activities/SettingsActivity.kt, app/src/main/java/forpdateam/ru/forpda/ui/compose/screens/OfflineListScreen.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/offline/OfflineListComposeFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/AnnounceFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/ForumRulesFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelper_V2.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApi.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt, app/src/main/java/forpdateam/ru/forpda/ui/navigation/TabHelper.kt, app/src/main/java/forpdateam/ru/forpda/ui/navigation/TabNavigator.kt, app/src/main/java/forpdateam/ru/forpda/ui/navigation/TabNavigatorThemeSwitchPolicy.kt, app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt, app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/MessagePanel.kt, app/src/main/java/forpdateam/ru/forpda/ui/views/messagepanel/advanced/CodesPanelItem.kt, app/src/main/java/forpdateam/ru/forpda/ui/views/pagination/PaginationHelper.kt, app/src/main/res/menu/article_details.xml, app/src/main/res/menu/theme_details.xml, app/src/main/res/menu/theme_search_menu.xml, app/src/main/res/values-en/strings.xml, app/src/main/res/values/arrays.xml, app/src/main/res/values/strings.xml, app/src/main/res/values/styles.xml, app/src/main/res/xml/preferences.xml, app/src/main/res/xml/preferences_notifications.xml, app/src/test/java/forpdateam/ru/forpda/entity/db/notes/AppDatabaseMigrationTest.kt, app/src/test/java/forpdateam/ru/forpda/model/data/offline/OfflineArticleSourceTest.kt, app/src/test/java/forpdateam/ru/forpda/model/data/offline/OfflineImageDownloaderTest.kt, app/src/test/java/forpdateam/ru/forpda/model/data/offline/OfflineRepositoryEvictionTest.kt, app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchApiTest.kt, app/src/test/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCaseTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryControllerSnapshotTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterfaceTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeOpenScrollCoalescePolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemePostedPageScrollPolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplateTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicBackSnapshotTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicOpenTargetMapperTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicPrependedHatPolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/offline/OfflineListOpenItemRoutingTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/navigation/TabNavigatorThemeSwitchPolicyTest.kt, docs/READER_MODE_INVENTORY.md, docs/UI_THREAD_RENDERING_INVENTORY.md, gradle/libs.versions.toml, .ai-bridge/, app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/8.json, app/src/main/java/forpdateam/ru/forpda/client/interceptors/CacheControlInterceptor.kt, app/src/main/java/forpdateam/ru/forpda/common/di/, app/src/main/java/forpdateam/ru/forpda/diagnostic/ColdStartTracer.kt, app/src/main/java/forpdateam/ru/forpda/diagnostic/TopicHighlightDiagnostics.kt, app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarNotFoundException.kt, app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemeReadPositionRepository.kt, app/src/main/java/forpdateam/ru/forpda/notifications/CounterGrowthDetector.kt, app/src/main/java/forpdateam/ru/forpda/notifications/EventsCheckScheduler.kt, app/src/main/java/forpdateam/ru/forpda/notifications/ForumHeaderCounters.kt, app/src/main/java/forpdateam/ru/forpda/notifications/MentionNotificationMapper.kt, app/src/main/java/forpdateam/ru/forpda/notifications/NotificationPublisher.kt, app/src/main/java/forpdateam/ru/forpda/notifications/NotificationSnapshotLock.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/FindOnPageState.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/HighlightArmingPolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/HighlightExplicitPostPolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/HighlightResolver.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/MaterialYouPolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ReadPositionSaveGate.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeBackRestoreUrlPolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeLinkNavigationPolicy.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicHighlightApply.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/TopicHighlightModels.kt, app/src/main/java/forpdateam/ru/forpda/ui/MaterialYouApplier.kt, app/src/test/java/forpdateam/ru/forpda/client/CachedDnsClearTest.kt, app/src/test/java/forpdateam/ru/forpda/client/interceptors/CacheControlInterceptorTest.kt, app/src/test/java/forpdateam/ru/forpda/diagnostic/ColdStartTracerTest.kt, app/src/test/java/forpdateam/ru/forpda/model/data/remote/api/search/SearchParserGoldenTest.kt, app/src/test/java/forpdateam/ru/forpda/model/repository/avatar/, app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemeReadPositionRepositoryTest.kt, app/src/test/java/forpdateam/ru/forpda/model/system/, app/src/test/java/forpdateam/ru/forpda/notifications/, app/src/test/java/forpdateam/ru/forpda/presentation/TabRouterCleanupTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/FindOnPageStateTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightArmingPolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightExplicitPostPolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightJsGuardTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/HighlightResolverTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/MaterialYouPolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ReadPositionSaveGateTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/SourceAnchorTtlTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeBackNavigationTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeHistoryControllerTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollTargetPageTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/ThemeLinkNavigationPolicyTest.kt, app/src/test/java/forpdateam/ru/forpda/presentation/theme/TopicHighlightApplyTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesPaginationScrollTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelperV2FragmentLifecycleTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeDialogsHelperV2ScopeTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApiApplyHighlightTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApiScheduleHighlightFadeoutTest.kt, app/src/test/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebControllerFadeoutTest.kt, docs/AUDIT_AND_ACTIONS.md, docs/AUDIT_REPORT.md, docs/BACKLOG_DEFERRED.md, docs/CAPTCHA_FALLBACK.md, docs/CP1251_NOTES.md, docs/IMPLEMENTATION_SUMMARY.md, docs/MEMORY_QA_CHECKLIST.md, docs/PLAN.md, docs/RELEASE_GATE_STATUS.md, docs/STARTUP_BASELINE.md, docs/navigation-scroll-fixes-plan.md, docs/perf/, docs/test-fixes-plan.md, docs/topic-highlight-qa.md, scripts/battery-audit.README.md, scripts/battery-audit.sh, scripts/startup-benchmark.README.md, scripts/startup-benchmark.sh
Explicit selected paths: app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt, app/src/main/res/xml/preferences.xml, app/src/main/java/forpdateam/ru/forpda/ui/fragments/settings/SettingsFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt, app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeJsApi.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt, app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt, app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt, app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt, app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt, app/src/main/assets/template_theme.html, app/src/main/assets/forpda/scripts/modules/theme.js, app/src/main/assets/forpda/styles/dark/dark_themes.css, app/src/main/assets/forpda/styles/light/light_themes.css, app/src/main/java/forpdateam/ru/forpda/ui/activities/MainActivity.kt, app/src/main/java/forpdateam/ru/forpda/ui/TemplateCssComposer.kt
Extra globs: none
Files included below: README.md, .ai-bridge/, app/build.gradle, app/proguard-rules.pro, app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/7.json, app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/8.json, app/src/main/AndroidManifest.xml, app/src/main/assets/forpda/scripts/modules/theme.js, app/src/main/assets/forpda/styles/dark/dark_themes.css, app/src/main/assets/forpda/styles/light/light_themes.css, app/src/main/assets/forpda/styles/modules/themes.less, app/src/main/assets/template_theme.html, app/src/main/java/forpdateam/ru/forpda/App.kt, app/src/main/java/forpdateam/ru/forpda/client/Client.kt, app/src/main/java/forpdateam/ru/forpda/client/interceptors/CacheControlInterceptor.kt, app/src/main/java/forpdateam/ru/forpda/client/interceptors/ImageLoadingInterceptor.kt, app/src/main/java/forpdateam/ru/forpda/common/di/, app/src/main/java/forpdateam/ru/forpda/common/HtmlToSpannedConverter.kt, app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt, app/src/main/java/forpdateam/ru/forpda/common/receivers/WakeUpReceiver.kt, app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt, app/src/main/java/forpdateam/ru/forpda/di/DataModule.kt, app/src/main/java/forpdateam/ru/forpda/diagnostic/ColdStartTracer.kt, app/src/main/java/forpdateam/ru/forpda/diagnostic/FpdaDebugLog.kt, app/src/main/java/forpdateam/ru/forpda/diagnostic/TopicHighlightDiagnostics.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesDatabase.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesMigrations.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemDao.kt, app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemRoom.kt, app/src/main/java/forpdateam/ru/forpda/entity/remote/events/NotificationEvent.kt, app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineArticleSource.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineImageDownloader.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineIndexPathHandler.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineRepository.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineSaveController.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineStorage.kt, app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineWebViewBaseUrl.kt, app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/Constants.kt, app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt

## File Contents

### README.md

Bytes: 6987
SHA-256: d3bf8bd923d5635aeeb0c17acde9165c2b721d07d38aadbf7b9c26088bfe9419
Lines: 1-94 of 94

```markdown
 1 | ![API](https://img.shields.io/badge/API-19%2B-blue.svg?style=flat)
 2 | # ForPDA (fork) #
 3 | 
 4 | Этот репозиторий — **форк** оригинального клиента ForPDA.
 5 | 
 6 | ## Что изменено в этом форке
 7 | - Исправления поведения **клавиатуры/BBCode-панели** на Android 15/16 (edge-to-edge, OnePlus/OEM).
 8 | - Убрана опция **«Проверить обновления»** из настроек.
 9 | 
10 | ## Лицензия и авторство
11 | Проект остаётся под **GPL v3**. Автор оригинального проекта указан ниже в секции лицензии.
12 | 
13 | **ForPDA** – это простой и удобный клиент для сайта [4pda.ru](http://4pda.ru/)
14 | 
15 | <a href="http://4pda.ru/forum/index.php?showtopic=820313" target="_blank"><img src="https://raw.githubusercontent.com/RadiationX/ForPDA/master/gh_res/logo.png" height="192px" alt="Логотип ForPDA" /></a>
16 | 
17 | <a href="https://play.google.com/store/apps/details?id=ru.forpdateam.forpda"><img alt="Get it on Google Play" src="https://play.google.com/intl/ru_ru/badges/images/apps/ru-play-badge.png" height="48px"/></a>
18 | <a href="http://4pda.ru/forum/index.php?showtopic=820313" target="_blank"><img src="https://raw.githubusercontent.com/RadiationX/ForPDA/master/gh_res/icon_4pda.png" height="48px" alt="Тема на форуме 4PDA" /></a>
19 | 
20 | ##
21 | **Скриншоты:**
22 | 
23 | ![](https://raw.githubusercontent.com/RadiationX/ForPDA/master/gh_res/screen1.png)![](https://raw.githubusercontent.com/RadiationX/ForPDA/master/gh_res/screen2.png)![](https://raw.githubusercontent.com/RadiationX/ForPDA/master/gh_res/screen3.png)
24 | ##
25 | 
26 | Вы можете просматривать информацию с [сайта](http://4pda.ru/) в удобном виде, писать и редактировать сообщения на [форуме](http://4pda.ru/forum/index.php?act=idx), искать нужную вам информацию, скачивать файлы, общаться с другими [пользователями](http://4pda.ru/forum/index.php?act=Members) в чате [QMS](http://4pda.ru/forum/index.php?act=qms&code=no) и многое другое! 
27 | 
28 | **Основные возможности**
29 | 
30 | - Просмотр новостей сайта
31 | - Возможность оставлять комментарии на сайте
32 | - Просмотр форумов и списков их тем
33 | - Поиск по сайту и форуму, с возможностью настроить параметры поиска
34 | - Возможность создавать/редактировать/удалять сообщения на форуме
35 | - Возможность редактировать темы на форуме
36 | - Возможность скачивать и загружать файлы на форум
37 | - Простой и удобный доступ к избранному
38 | - Доступ к каталогу устройств [DevDB](http://4pda.ru/devdb)
39 | - Доступ к [QMS](http://4pda.ru/forum/index.php?act=qms&code=no) (создание/удаление диалогов, а также управление черным списком)
40 | - Доступ профилю пользователей
41 | - Просмотр упоминаний
42 | - История посещённых тем
43 | - Заметки и форумный блокнот
44 | 
45 | **Некоторые особенности**
46 | 
47 | - Простой и понятный интерфейс в стиле Material Design
48 | - Две темы оформления (светлая и темная)
49 | - Отсутствие лишнего функционала и настроек
50 | - Команда разработчиков стремится к идеалам современных приложений
51 | 
52 | ##
53 | ## Сборка проекта
54 | Проект разрабатывается с помощью [Android Studio](https://developer.android.com/studio/index.html) и использует Gradle для сборки. Для корректной сборки нужно установить JDK 8, обновить SDK до версии 25, и Gradle до версии 3.3
55 | 
56 |     // Top-level build file where you can add configuration options common to all sub-projects/modules.
57 |     //...
58 |     
59 |     dependencies {
60 |     classpath 'com.android.tools.build:gradle:2.3.3'
61 |     // Other plugins
62 |     
63 |     // NOTE: Do not place your application dependencies here; they belong
64 |     // in the individual module build.gradle files
65 |     }
66 |     //...
67 | 
68 | Сборка призводится командой Build -> Build APK (в Android Studio). Результирующий APK находится в `%PROJECT_DIR%/apk/`
69 | 
70 | ## Для разработчиков стилей
71 | На данный момент приложение не поддерживает пользовательские стили, но вы можете отредактировать стандартные стили приложения. Стандартные стили находятся в папке `/assets/forpda/styles/`  модуля `app`.
72 | Тестовые html для всех основных разделов форума уже включены в проект. Смотрите папку `/assets/forpda/`  модуля `app`.
73 | 
74 | Для удобного редактирования стилей вам необходимо уметь работать с [LESS](http://lesscss.org/)
75 | Основной код лежит в `../modules/`, для компиляции нужно использовать соответствующие файлы из папок `../light/` и `../dark/`.
76 | 
77 | Также имеются конфигурационные файлы (`config_*.less`), в которых можно удобно изменять нужные цвета. После изменения конфигурационных файлов, обязательно нужно скомпилировать все модули стилей.
78 | 
79 | **Файлы javascript трогать не нужно, т.к. их работа тесно связана с java кодом клиента, и любые изменения в критичных местах, могут повлиять на работу клиента.**
80 | 
81 | Разработка стилей делалась в [Brackets](http://brackets.io/) с модулями "Emmet", "LESS AutoCompile" и "LESSHints".
82 | 
83 | ## Лицензия
84 | Исходный код распостраняется под лицензией GPL v3
85 | 
86 | > Copyright (C) 2016-2018  Evgeniy Nizamiev [(radiationx@yandex.ru)](mailto:radiationx@yandex.ru)
87 | > 
88 | > This program is free software; you can redistribute it and/or modify
89 | > it under the terms of the GNU General Public License as published by
90 | > the Free Software Foundation; either version 3 of the License.
91 | 
92 | 
93 | Составитель справки: [Snow Volf](https://github.com/SnowVolf)
94 | 
```

### app/build.gradle

Bytes: 13856
SHA-256: ba21f2b5e20120fe62c1871d8fcdba698fad8143f9c32111a88f90a2807c7eb9
Lines: 1-374 of 374

```text
  1 | import java.text.DateFormat
  2 | import java.text.SimpleDateFormat
  3 | 
  4 | plugins {
  5 |     id 'com.android.application'
  6 |     id 'org.jetbrains.kotlin.android'
  7 |     id 'org.jetbrains.kotlin.plugin.serialization' version '2.0.21'
  8 |     alias libs.plugins.ksp
  9 |     alias libs.plugins.kotlin.compose
 10 |     alias libs.plugins.hilt.android
 11 |     id 'io.gitlab.arturbosch.detekt' version '1.23.4'
 12 | }
 13 | 
 14 | static def getDateTime() {
 15 |     DateFormat df = new SimpleDateFormat("dd MMMMM yyyy")
 16 |     return df.format(new Date()) + " г."
 17 | }
 18 | 
 19 | def keystorePropertiesFile = rootProject.file("keystore.properties")
 20 | def keystoreProperties = new Properties()
 21 | if (keystorePropertiesFile.exists()) {
 22 |     keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
 23 | }
 24 | 
 25 | def parallelKeystorePropertiesFile = rootProject.file("keystore.parallel.properties")
 26 | def parallelKeystoreProperties = new Properties()
 27 | if (parallelKeystorePropertiesFile.exists()) {
 28 |     parallelKeystoreProperties.load(new FileInputStream(parallelKeystorePropertiesFile))
 29 | }
 30 | 
 31 | def strictLint = System.getenv("CI") == "true" || project.findProperty("forpda.strictLint") == "true"
 32 | def hasUpstreamReleaseKeystore = keystorePropertiesFile.exists()
 33 |         && keystoreProperties['RELEASE_STORE_FILE'] != null
 34 |         && !keystoreProperties['RELEASE_STORE_FILE'].toString().trim().isEmpty()
 35 |         && keystoreProperties['RELEASE_STORE_PASSWORD'] != null
 36 |         && keystoreProperties['RELEASE_KEY_ALIAS'] != null
 37 |         && keystoreProperties['RELEASE_KEY_PASSWORD'] != null
 38 | 
 39 | android {
 40 |     namespace 'forpdateam.ru.forpda'
 41 |     compileSdk 35
 42 | 
 43 |     buildFeatures {
 44 |         buildConfig = true
 45 |         viewBinding = true
 46 |     }
 47 | 
 48 |     def versionNumber = 351
 49 |     def versionMajor = "2"
 50 |     def versionMinor = "9"
 51 |     def versionPatch = "5"
 52 |     def versionBuild = 1
 53 |     def versionPropsFile = file('version.properties')
 54 |     if (versionPropsFile.canRead()) {
 55 |         Properties versionProps = new Properties()
 56 |         versionProps.load(new FileInputStream(versionPropsFile))
 57 |         def vb = versionProps['VERSION_BUILD']
 58 |         if (vb != null) {
 59 |             versionBuild = vb.toString().toInteger()
 60 |         }
 61 |     }
 62 | 
 63 |     defaultConfig {
 64 |         // По умолчанию — stable (ForPDA 2, ru.forpdateam.forpda.parallel). Оригинальный id Play: flavor store.
 65 |         applicationId "ru.forpdateam.forpda.parallel"
 66 |         versionCode versionNumber
 67 |         versionName "${versionMajor}.${versionMinor}.${versionPatch}"
 68 |         minSdk 24
 69 |         targetSdk 35
 70 |         vectorDrawables.useSupportLibrary = true
 71 |         buildConfigField "String", 'BUILD_DATE', '"' + getDateTime() + '"'
 72 |         ndk {
 73 |             abiFilters 'armeabi-v7a', 'arm64-v8a'
 74 |         }
 75 |         // Только нужные локали — меньше таблиц ресурсов в APK/AAB.
 76 |         resourceConfigurations += ['ru', 'en']
 77 |     }
 78 | 
 79 |     signingConfigs {
 80 |         if (parallelKeystorePropertiesFile.exists()) {
 81 |             parallel {
 82 |                 def storeRel = parallelKeystoreProperties['STORE_FILE']
 83 |                 if (storeRel == null || storeRel.toString().trim().isEmpty()) {
 84 |                     throw new GradleException("keystore.parallel.properties: задайте STORE_FILE (путь к .jks от корня репозитория)")
 85 |                 }
 86 |                 def store = rootProject.file(storeRel.toString().trim())
 87 |                 if (!store.isFile()) {
 88 |                     throw new GradleException("keystore.parallel: файл не найден: ${store.absolutePath}")
 89 |                 }
 90 |                 storeFile store
 91 |                 storePassword parallelKeystoreProperties['STORE_PASSWORD']
 92 |                 keyAlias parallelKeystoreProperties['KEY_ALIAS']
 93 |                 keyPassword parallelKeystoreProperties['KEY_PASSWORD']
 94 |             }
 95 |         }
 96 |         if (keystorePropertiesFile.exists()) {
 97 |             upstreamDebug {
 98 |                 storeFile rootProject.file(keystoreProperties['DEBUG_STORE_FILE'])
 99 |                 storePassword keystoreProperties['DEBUG_STORE_PASSWORD']
100 |                 keyAlias keystoreProperties['DEBUG_KEY_ALIAS']
101 |                 keyPassword keystoreProperties['DEBUG_KEY_PASSWORD']
102 |             }
103 |             if (hasUpstreamReleaseKeystore) {
104 |                 upstreamRelease {
105 |                     storeFile rootProject.file(keystoreProperties['RELEASE_STORE_FILE'].toString().trim())
106 |                     storePassword keystoreProperties['RELEASE_STORE_PASSWORD']
107 |                     keyAlias keystoreProperties['RELEASE_KEY_ALIAS']
108 |                     keyPassword keystoreProperties['RELEASE_KEY_PASSWORD']
109 |                 }
110 |             }
111 |         }
112 |     }
113 | 
114 |     packagingOptions {
115 |         jniLibs {
116 |             useLegacyPackaging true
117 |         }
118 |     }
119 | 
120 |     buildTypes {
121 |         debug {
122 |         }
123 |         release {
124 |             minifyEnabled true
125 |             shrinkResources true
126 |             proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
127 |         }
128 |     }
129 | 
130 |     flavorDimensions += "channel"
131 |     productFlavors {
132 |         stable {
133 |             dimension "channel"
134 |             // Только stable: всегда ключ ForPDA 2 (parallel). Без keystore.parallel.properties
135 |             // сборка stable APK/AAB блокируется (см. taskGraph.whenReady ниже).
136 |             if (parallelKeystorePropertiesFile.exists()) {
137 |                 signingConfig signingConfigs.parallel
138 |             } else {
139 |                 signingConfig signingConfigs.debug
140 |             }
141 |         }
142 |         store {
143 |             dimension "channel"
144 |             applicationId 'ru.forpdateam.forpda'
145 |             versionName "${versionMajor}.${versionMinor}.${versionPatch}"
146 |             // Оригинальный пакет Play — тот же keystore.properties (debug/release как в проекте).
147 |             if (hasUpstreamReleaseKeystore) {
148 |                 signingConfig signingConfigs.upstreamRelease
149 |             } else {
150 |                 signingConfig signingConfigs.debug
151 |             }
152 |         }
153 |         beta {
154 |             dimension "channel"
155 |             applicationId 'ru.forpdateam.forpda.beta'
156 |             versionName "${versionMajor}.${versionMinor}.${versionPatch} (${versionBuild}) beta"
157 |             if (hasUpstreamReleaseKeystore) {
158 |                 signingConfig signingConfigs.upstreamRelease
159 |             } else {
160 |                 signingConfig signingConfigs.debug
161 |             }
162 |         }
163 |         dev {
164 |             dimension "channel"
165 |             applicationId 'ru.forpdateam.forpda.debug'
166 |             versionName "${versionMajor}.${versionMinor}.${versionPatch} (${versionBuild}) dev"
167 |             if (hasUpstreamReleaseKeystore) {
168 |                 signingConfig signingConfigs.upstreamRelease
169 |             } else {
170 |                 signingConfig signingConfigs.debug
171 |             }
172 |         }
173 |     }
174 | 
175 |     buildFeatures {
176 |         viewBinding = true
177 |         buildConfig = true
178 |         compose = true
179 |     }
180 | 
181 |     composeOptions {
182 |         // Kotlin 2.0+: compose compiler version is provided by
183 |         // org.jetbrains.kotlin.plugin.compose (see plugins block).
184 |     }
185 | 
186 |     compileOptions {
187 |         sourceCompatibility JavaVersion.VERSION_17
188 |         targetCompatibility JavaVersion.VERSION_17
189 |         coreLibraryDesugaringEnabled true
190 |     }
191 |     kotlinOptions {
192 |         jvmTarget = '17'
193 |     }
194 | 
195 |     packaging {
196 |         resources {
197 |             excludes += [
198 |                     'META-INF/DEPENDENCIES.txt',
199 |                     'META-INF/LICENSE.txt',
200 |                     'META-INF/NOTICE.txt',
201 |                     'META-INF/NOTICE',
202 |                     'META-INF/LICENSE',
203 |                     'META-INF/DEPENDENCIES',
204 |                     'META-INF/notice.txt',
205 |                     'META-INF/license.txt',
206 |                     'META-INF/dependencies.txt'
207 |             ]
208 |         }
209 |     }
210 | 
211 |     testOptions {
212 |         unitTests.returnDefaultValues = true
213 |         // Allow JVM unit tests to load Android resources (strings, dimensions, layouts).
214 |         // Without this, Robolectric/ResourceLoader paths in tests that touch R.* or
215 |         // resources.getString() would silently return null/0.
216 |         unitTests.includeAndroidResources = true
217 |     }
218 | 
219 |     lint {
220 |         // RestrictedApi disabled for AppMetrica and other libraries' internal APIs
221 |         disable 'RestrictedApi'
222 |         checkReleaseBuilds = true
223 |         // CI/strict builds must fail on lint errors; local builds keep quick iteration.
224 |         abortOnError = strictLint
225 |         // Check all issues including new Android SDK deprecations
226 |         checkDependencies = true
227 |         xmlReport = true
228 |         htmlReport = true
229 |     }
230 | 
231 |     // AAB в Play: отдельные APK по ABI — меньше размер загрузки для пользователя.
232 |     bundle {
233 |         abi {
234 |             enableSplit = true
235 |         }
236 |     }
237 | 
238 |     applicationVariants.configureEach { variant ->
239 |         variant.outputs.configureEach { output ->
240 |             output.outputFileName = "ProPDA-${versionMajor}.${versionMinor}.${versionPatch}-${variant.name}.apk"
241 |         }
242 |     }
243 | }
244 | 
245 | gradle.taskGraph.whenReady { graph ->
246 |     def needsParallelStable = graph.allTasks.any { task ->
247 |         def n = task.name
248 |         if (!n.contains("Stable")) {
249 |             return false
250 |         }
251 |         return n.startsWith("assemble") || n.startsWith("bundle") || n.startsWith("package") || n.startsWith("install") || n.startsWith("publish")
252 |     }
253 |     if (needsParallelStable && !parallelKeystorePropertiesFile.exists()) {
254 |         throw new GradleException(
255 |                 "Сборка варианта stable требует keystore.parallel.properties в корне репозитория " +
256 |                         "(подпись ru.forpdateam.forpda.parallel). См. keystore.parallel.properties.example")
257 |     }
258 |     def needsOfficialStoreRelease = graph.allTasks.any { task ->
259 |         def n = task.name
260 |         return n.contains("StoreRelease") && (n.startsWith("assemble") || n.startsWith("bundle") || n.startsWith("package") || n.startsWith("publish"))
261 |     }
262 |     if (needsOfficialStoreRelease && !hasUpstreamReleaseKeystore) {
263 |         throw new GradleException(
264 |                 "Сборка официального store-варианта требует RELEASE_* ключи в keystore.properties; " +
265 |                         "debug-подпись для storeRelease запрещена.")
266 |     }
267 | }
268 | 
269 | configurations.all {
270 |     resolutionStrategy {
271 |         force 'androidx.core:core:1.13.1'
272 |     }
273 | }
274 | 
275 | dependencies {
276 |     implementation fileTree(include: ['*.jar'], dir: 'libs')
277 | 
278 |     coreLibraryDesugaring libs.desugar.jdk.libs
279 | 
280 |     implementation libs.androidx.core.ktx
281 |     implementation libs.androidx.activity.ktx
282 |     implementation libs.androidx.fragment.ktx
283 |     implementation libs.androidx.webkit
284 |     implementation libs.androidx.lifecycle.viewmodel.ktx
285 |     implementation libs.androidx.lifecycle.runtime.ktx
286 |     implementation libs.androidx.lifecycle.process
287 |     implementation libs.androidx.appcompat
288 |     implementation libs.androidx.constraintlayout
289 |     implementation libs.androidx.coordinatorlayout
290 |     implementation libs.androidx.recyclerview
291 |     implementation libs.androidx.cardview
292 |     implementation libs.androidx.preference.ktx
293 |     implementation libs.androidx.documentfile
294 |     implementation libs.androidx.palette.ktx
295 |     implementation libs.androidx.swiperefreshlayout
296 |     implementation libs.androidx.viewpager
297 |     implementation libs.androidx.viewpager2
298 |     implementation libs.androidx.profileinstaller
299 |     implementation libs.androidx.work.runtime
300 |     // FIXME: Using alpha due to API changes in 1.0.x stable. Consider migrating to Tink directly.
301 |     implementation libs.androidx.security.crypto
302 | 
303 |     implementation libs.androidx.datastore.preferences
304 |     implementation libs.androidx.room.runtime
305 |     implementation libs.androidx.room.ktx
306 |     ksp libs.androidx.room.compiler
307 | 
308 |     implementation platform(libs.androidx.compose.bom)
309 |     implementation libs.androidx.compose.ui
310 |     implementation libs.androidx.compose.ui.graphics
311 |     implementation libs.androidx.compose.ui.tooling.preview
312 |     implementation libs.androidx.compose.material3
313 |     implementation libs.androidx.compose.material.icons.extended
314 |     implementation libs.androidx.activity.compose
315 |     implementation libs.androidx.lifecycle.viewmodel.compose
316 |     implementation libs.androidx.lifecycle.runtime.compose
317 |     debugImplementation libs.androidx.compose.ui.tooling
318 |     debugImplementation libs.androidx.compose.ui.test.manifest
319 | 
320 |     implementation libs.google.material
321 | 
322 |     implementation libs.okhttp
323 |     implementation libs.okhttp.brotli
324 | 
325 |     implementation libs.coil
326 |     implementation libs.kotlinx.coroutines.android
327 |     implementation libs.kotlinx.serialization.json
328 |     implementation libs.jsoup
329 |     implementation libs.tagsoup
330 |     implementation libs.minitemplator
331 |     implementation libs.sectionedRecyclerview
332 | 
333 |     implementation libs.photoView
334 | 
335 |     implementation libs.kotlin.stdlib
336 | 
337 |     implementation libs.navigation.cicerone
338 |     implementation libs.adapterdelegates
339 | 
340 |     implementation libs.appmetrica
341 | 
342 |     implementation libs.roundedImageview
343 |     implementation libs.timber
344 | 
345 |     implementation libs.hilt.android
346 |     ksp libs.hilt.compiler
347 |     implementation libs.hilt.work
348 |     ksp libs.hilt.work.compiler
349 | 
350 |     testImplementation libs.junit
351 |     testImplementation libs.robolectric
352 |     testImplementation libs.androidx.room.testing
353 |     testImplementation libs.mockk
354 |     testImplementation libs.kotlinx.coroutines.test
355 |     testImplementation libs.turbine
356 |     testImplementation libs.androidx.test.core
357 | }
358 | 
359 | ksp {
360 |     arg("room.schemaLocation", "$projectDir/schemas")
361 |     arg("room.incremental", "true")
362 | }
363 | 
364 | detekt {
365 |     buildUponDefaultConfig = true
366 |     allRules = false
367 |     config = files("$rootDir/detekt.yml")
368 |     baseline = file("$rootDir/detekt-baseline.xml")
369 | }
370 | 
371 | dependencies {
372 |     detektPlugins libs.detekt.formatting
373 | }
374 | 
```

### app/proguard-rules.pro

Bytes: 3384
SHA-256: 4d71edad080cd647b0f2ac9993fff5e3ac967749aa4737ee60d5b0ee3fb6790f
Lines: 1-99 of 99

```text
 1 | -optimizationpasses 5
 2 | -dontskipnonpubliclibraryclassmembers
 3 | -allowaccessmodification
 4 | # Prevent crashes on API < 30 where getWindowInsetsController() doesn't exist.
 5 | -keepclassmembers class android.view.View {
 6 |     public android.view.WindowInsetsController getWindowInsetsController();
 7 | }
 8 | -keepclassmembers class android.webkit.WebView {
 9 |     public android.view.WindowInsetsController getWindowInsetsController();
10 | }
11 | -repackageclasses ''
12 | -adaptclassstrings
13 | 
14 | 
15 | -dontnote **
16 | -dontwarn forpdateam.ru.forpda.**
17 | 
18 | # =============================================================================
19 | # Приложение forpdateam.ru.forpda — точечные правила (без -keep всего пакета).
20 | # R8 может удалять неиспользуемый код и обфусцировать имена, кроме перечисленного.
21 | # =============================================================================
22 | 
23 | # Note: `forpdateam.ru.forpda.App` (Hilt `@HiltAndroidApp`) is automatically
24 | # kept by Hilt's generated ProGuard rules. A manual `-keep ... <init>()` is
25 | # redundant and was removed (T-04).
26 | 
27 | # WebView: методы, вызываемые из JS по имени
28 | -keepclassmembers class * {
29 |     @android.webkit.JavascriptInterface <methods>;
30 | }
31 | 
32 | # News article WebView bridge (INews) — must survive R8 in stableRelease
33 | -keep class forpdateam.ru.forpda.ui.fragments.news.details.ArticleCommentsNativeBar { *; }
34 | -keep class forpdateam.ru.forpda.ui.fragments.news.details.ArticleContentFragment {
35 |     @android.webkit.JavascriptInterface <methods>;
36 | }
37 | 
38 | # Kotlin: метаданные (sealed, inline, reflection в библиотеках)
39 | -keepattributes kotlin.Metadata
40 | -keep class kotlin.Metadata { *; }
41 | 
42 | # Cicerone: Screen.getKey() = simpleName вложенных классов — стабильные ключи навигации
43 | -keepnames class forpdateam.ru.forpda.presentation.Screen$* {
44 |     *;
45 | }
46 | 
47 | # Parcelable
48 | -keepclassmembers class * implements android.os.Parcelable {
49 |     public static final ** CREATOR;
50 | }
51 | 
52 | # Realm: модели БД (дополнительно к правилу * extends RealmObject ниже)
53 | -keep class forpdateam.ru.forpda.entity.db.** { *; }
54 | 
55 | -keepattributes SourceFile,LineNumberTable
56 | 
57 | # okio
58 | -keep class sun.misc.Unsafe { *; }
59 | -dontwarn java.nio.file.*
60 | -dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
61 | -dontwarn okio.**
62 | -dontnote okio.**
63 | 
64 | 
65 | # OkHttp - optimized: keepnames instead of keep
66 | -keepattributes Signature
67 | -keepattributes *Annotation*
68 | -keepnames class okhttp3.** { *; }
69 | -keepnames interface okhttp3.** { *; }
70 | -dontwarn okhttp3.**
71 | 
72 | -keep public class androidx.browser.customtabs.CustomTabsService
73 | 
74 | # AppMetrica
75 | -keep class io.appmetrica.** { *; }
76 | -dontwarn io.appmetrica.**
77 | 
78 | # WorkManager - keepnames for shrinking
79 | -keepnames class androidx.work.** { *; }
80 | -dontwarn androidx.work.**
81 | 
82 | # Coil - optimized: keepnames instead of keep
83 | -keepnames class coil.** { *; }
84 | -keepnames interface coil.** { *; }
85 | -dontwarn coil.**
86 | 
87 | # Cicerone - keepnames for shrinking
88 | -keepnames class com.github.terrakok.cicerone.** { *; }
89 | -dontwarn com.github.terrakok.cicerone.**
90 | 
91 | -keep class **.R
92 | -keep class **.R$* {
93 |     <fields>;
94 | }
95 | 
96 | 
97 | # В search fragment юзается с рефлексией, поэтому нужно исключить
98 | -keep public class androidx.swiperefreshlayout.widget.SwipeRefreshLayout { *; }
99 | 
```

### app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/7.json

Bytes: 15495
SHA-256: 6e518e350f50db808d7416124848473c45ebf3fd5658610c0ff5cde7dbb236c2
Lines: 1-530 of 530

```json
  1 | {
  2 |   "formatVersion": 1,
  3 |   "database": {
  4 |     "version": 7,
  5 |     "identityHash": "852f71bb74bcd224cddc939316e0fcd8",
  6 |     "entities": [
  7 |       {
  8 |         "tableName": "notes",
  9 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `link` TEXT NOT NULL, `content` TEXT NOT NULL, `folderId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
 10 |         "fields": [
 11 |           {
 12 |             "fieldPath": "id",
 13 |             "columnName": "id",
 14 |             "affinity": "INTEGER",
 15 |             "notNull": true
 16 |           },
 17 |           {
 18 |             "fieldPath": "title",
 19 |             "columnName": "title",
 20 |             "affinity": "TEXT",
 21 |             "notNull": true
 22 |           },
 23 |           {
 24 |             "fieldPath": "link",
 25 |             "columnName": "link",
 26 |             "affinity": "TEXT",
 27 |             "notNull": true
 28 |           },
 29 |           {
 30 |             "fieldPath": "content",
 31 |             "columnName": "content",
 32 |             "affinity": "TEXT",
 33 |             "notNull": true
 34 |           },
 35 |           {
 36 |             "fieldPath": "folderId",
 37 |             "columnName": "folderId",
 38 |             "affinity": "INTEGER",
 39 |             "notNull": false
 40 |           },
 41 |           {
 42 |             "fieldPath": "createdAt",
 43 |             "columnName": "createdAt",
 44 |             "affinity": "INTEGER",
 45 |             "notNull": true
 46 |           },
 47 |           {
 48 |             "fieldPath": "updatedAt",
 49 |             "columnName": "updatedAt",
 50 |             "affinity": "INTEGER",
 51 |             "notNull": true
 52 |           },
 53 |           {
 54 |             "fieldPath": "sortOrder",
 55 |             "columnName": "sortOrder",
 56 |             "affinity": "INTEGER",
 57 |             "notNull": true
 58 |           }
 59 |         ],
 60 |         "primaryKey": {
 61 |           "autoGenerate": false,
 62 |           "columnNames": [
 63 |             "id"
 64 |           ]
 65 |         },
 66 |         "indices": [],
 67 |         "foreignKeys": []
 68 |       },
 69 |       {
 70 |         "tableName": "note_folders",
 71 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
 72 |         "fields": [
 73 |           {
 74 |             "fieldPath": "id",
 75 |             "columnName": "id",
 76 |             "affinity": "INTEGER",
 77 |             "notNull": true
 78 |           },
 79 |           {
 80 |             "fieldPath": "name",
 81 |             "columnName": "name",
 82 |             "affinity": "TEXT",
 83 |             "notNull": true
 84 |           },
 85 |           {
 86 |             "fieldPath": "sortOrder",
 87 |             "columnName": "sortOrder",
 88 |             "affinity": "INTEGER",
 89 |             "notNull": true
 90 |           },
 91 |           {
 92 |             "fieldPath": "createdAt",
 93 |             "columnName": "createdAt",
 94 |             "affinity": "INTEGER",
 95 |             "notNull": true
 96 |           },
 97 |           {
 98 |             "fieldPath": "updatedAt",
 99 |             "columnName": "updatedAt",
100 |             "affinity": "INTEGER",
101 |             "notNull": true
102 |           }
103 |         ],
104 |         "primaryKey": {
105 |           "autoGenerate": true,
106 |           "columnNames": [
107 |             "id"
108 |           ]
109 |         },
110 |         "indices": [],
111 |         "foreignKeys": []
112 |       },
113 |       {
114 |         "tableName": "history",
115 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `url` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `unixTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
116 |         "fields": [
117 |           {
118 |             "fieldPath": "id",
119 |             "columnName": "id",
120 |             "affinity": "INTEGER",
121 |             "notNull": true
122 |           },
123 |           {
124 |             "fieldPath": "url",
125 |             "columnName": "url",
126 |             "affinity": "TEXT",
127 |             "notNull": true
128 |           },
129 |           {
130 |             "fieldPath": "date",
131 |             "columnName": "date",
132 |             "affinity": "TEXT",
133 |             "notNull": true
134 |           },
135 |           {
136 |             "fieldPath": "title",
137 |             "columnName": "title",
138 |             "affinity": "TEXT",
139 |             "notNull": true
140 |           },
141 |           {
142 |             "fieldPath": "unixTime",
143 |             "columnName": "unixTime",
144 |             "affinity": "INTEGER",
145 |             "notNull": true
146 |           }
147 |         ],
148 |         "primaryKey": {
149 |           "autoGenerate": false,
150 |           "columnNames": [
151 |             "id"
152 |           ]
153 |         },
154 |         "indices": [],
155 |         "foreignKeys": []
156 |       },
157 |       {
158 |         "tableName": "qms_contacts",
159 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`nick` TEXT NOT NULL, `id` INTEGER NOT NULL, `count` INTEGER NOT NULL, `avatar` TEXT, PRIMARY KEY(`nick`))",
160 |         "fields": [
161 |           {
162 |             "fieldPath": "nick",
163 |             "columnName": "nick",
164 |             "affinity": "TEXT",
165 |             "notNull": true
166 |           },
167 |           {
168 |             "fieldPath": "id",
169 |             "columnName": "id",
170 |             "affinity": "INTEGER",
171 |             "notNull": true
172 |           },
173 |           {
174 |             "fieldPath": "count",
175 |             "columnName": "count",
176 |             "affinity": "INTEGER",
177 |             "notNull": true
178 |           },
179 |           {
180 |             "fieldPath": "avatar",
181 |             "columnName": "avatar",
182 |             "affinity": "TEXT",
183 |             "notNull": false
184 |           }
185 |         ],
186 |         "primaryKey": {
187 |           "autoGenerate": false,
188 |           "columnNames": [
189 |             "nick"
190 |           ]
191 |         },
192 |         "indices": [],
193 |         "foreignKeys": []
194 |       },
195 |       {
196 |         "tableName": "qms_themes",
197 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `countMessages` INTEGER NOT NULL, `countNew` INTEGER NOT NULL, `name` TEXT, `date` TEXT, PRIMARY KEY(`id`))",
198 |         "fields": [
199 |           {
200 |             "fieldPath": "id",
201 |             "columnName": "id",
202 |             "affinity": "INTEGER",
203 |             "notNull": true
204 |           },
205 |           {
206 |             "fieldPath": "userId",
207 |             "columnName": "userId",
208 |             "affinity": "INTEGER",
209 |             "notNull": true
210 |           },
211 |           {
212 |             "fieldPath": "countMessages",
213 |             "columnName": "countMessages",
214 |             "affinity": "INTEGER",
215 |             "notNull": true
216 |           },
217 |           {
218 |             "fieldPath": "countNew",
219 |             "columnName": "countNew",
220 |             "affinity": "INTEGER",
221 |             "notNull": true
222 |           },
223 |           {
224 |             "fieldPath": "name",
225 |             "columnName": "name",
226 |             "affinity": "TEXT",
227 |             "notNull": false
228 |           },
229 |           {
230 |             "fieldPath": "date",
231 |             "columnName": "date",
232 |             "affinity": "TEXT",
233 |             "notNull": false
234 |           }
235 |         ],
236 |         "primaryKey": {
237 |           "autoGenerate": false,
238 |           "columnNames": [
239 |             "id"
240 |           ]
241 |         },
242 |         "indices": [],
243 |         "foreignKeys": []
244 |       },
245 |       {
246 |         "tableName": "qms_themes_list",
247 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`userId` INTEGER NOT NULL, `nick` TEXT, PRIMARY KEY(`userId`))",
248 |         "fields": [
249 |           {
250 |             "fieldPath": "userId",
251 |             "columnName": "userId",
252 |             "affinity": "INTEGER",
253 |             "notNull": true
254 |           },
255 |           {
256 |             "fieldPath": "nick",
257 |             "columnName": "nick",
258 |             "affinity": "TEXT",
259 |             "notNull": false
260 |           }
261 |         ],
262 |         "primaryKey": {
263 |           "autoGenerate": false,
264 |           "columnNames": [
265 |             "userId"
266 |           ]
267 |         },
268 |         "indices": [],
269 |         "foreignKeys": []
270 |       },
271 |       {
272 |         "tableName": "favorites",
273 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`favId` INTEGER NOT NULL, `topicId` INTEGER NOT NULL, `forumId` INTEGER NOT NULL, `authorId` INTEGER NOT NULL, `lastUserId` INTEGER NOT NULL, `stParam` INTEGER NOT NULL, `pages` INTEGER NOT NULL, `curatorId` INTEGER NOT NULL, `trackType` TEXT, `infoColor` TEXT, `topicTitle` TEXT, `forumTitle` TEXT, `authorUserNick` TEXT, `lastUserNick` TEXT, `date` TEXT, `desc` TEXT, `curatorNick` TEXT, `subType` TEXT, `isPin` INTEGER NOT NULL, `isForum` INTEGER NOT NULL, `isNew` INTEGER NOT NULL, `readState` INTEGER NOT NULL, `isPoll` INTEGER NOT NULL, `isClosed` INTEGER NOT NULL, `unreadPostCount` INTEGER NOT NULL, `localReadPostId` INTEGER NOT NULL, `localReadPostDateMillis` INTEGER NOT NULL, PRIMARY KEY(`favId`))",
274 |         "fields": [
275 |           {
276 |             "fieldPath": "favId",
277 |             "columnName": "favId",
278 |             "affinity": "INTEGER",
279 |             "notNull": true
280 |           },
281 |           {
282 |             "fieldPath": "topicId",
283 |             "columnName": "topicId",
284 |             "affinity": "INTEGER",
285 |             "notNull": true
286 |           },
287 |           {
288 |             "fieldPath": "forumId",
289 |             "columnName": "forumId",
290 |             "affinity": "INTEGER",
291 |             "notNull": true
292 |           },
293 |           {
294 |             "fieldPath": "authorId",
295 |             "columnName": "authorId",
296 |             "affinity": "INTEGER",
297 |             "notNull": true
298 |           },
299 |           {
300 |             "fieldPath": "lastUserId",
301 |             "columnName": "lastUserId",
302 |             "affinity": "INTEGER",
303 |             "notNull": true
304 |           },
305 |           {
306 |             "fieldPath": "stParam",
307 |             "columnName": "stParam",
308 |             "affinity": "INTEGER",
309 |             "notNull": true
310 |           },
311 |           {
312 |             "fieldPath": "pages",
313 |             "columnName": "pages",
314 |             "affinity": "INTEGER",
315 |             "notNull": true
316 |           },
317 |           {
318 |             "fieldPath": "curatorId",
319 |             "columnName": "curatorId",
320 |             "affinity": "INTEGER",
321 |             "notNull": true
322 |           },
323 |           {
324 |             "fieldPath": "trackType",
325 |             "columnName": "trackType",
326 |             "affinity": "TEXT",
327 |             "notNull": false
328 |           },
329 |           {
330 |             "fieldPath": "infoColor",
331 |             "columnName": "infoColor",
332 |             "affinity": "TEXT",
333 |             "notNull": false
334 |           },
335 |           {
336 |             "fieldPath": "topicTitle",
337 |             "columnName": "topicTitle",
338 |             "affinity": "TEXT",
339 |             "notNull": false
340 |           },
341 |           {
342 |             "fieldPath": "forumTitle",
343 |             "columnName": "forumTitle",
344 |             "affinity": "TEXT",
345 |             "notNull": false
346 |           },
347 |           {
348 |             "fieldPath": "authorUserNick",
349 |             "columnName": "authorUserNick",
350 |             "affinity": "TEXT",
351 |             "notNull": false
352 |           },
353 |           {
354 |             "fieldPath": "lastUserNick",
355 |             "columnName": "lastUserNick",
356 |             "affinity": "TEXT",
357 |             "notNull": false
358 |           },
359 |           {
360 |             "fieldPath": "date",
361 |             "columnName": "date",
362 |             "affinity": "TEXT",
363 |             "notNull": false
364 |           },
365 |           {
366 |             "fieldPath": "desc",
367 |             "columnName": "desc",
368 |             "affinity": "TEXT",
369 |             "notNull": false
370 |           },
371 |           {
372 |             "fieldPath": "curatorNick",
373 |             "columnName": "curatorNick",
374 |             "affinity": "TEXT",
375 |             "notNull": false
376 |           },
377 |           {
378 |             "fieldPath": "subType",
379 |             "columnName": "subType",
380 |             "affinity": "TEXT",
381 |             "notNull": false
382 |           },
383 |           {
384 |             "fieldPath": "isPin",
385 |             "columnName": "isPin",
386 |             "affinity": "INTEGER",
387 |             "notNull": true
388 |           },
389 |           {
390 |             "fieldPath": "isForum",
391 |             "columnName": "isForum",
392 |             "affinity": "INTEGER",
393 |             "notNull": true
394 |           },
395 |           {
396 |             "fieldPath": "isNew",
397 |             "columnName": "isNew",
398 |             "affinity": "INTEGER",
399 |             "notNull": true
400 |           },
401 |           {
402 |             "fieldPath": "readState",
403 |             "columnName": "readState",
404 |             "affinity": "INTEGER",
405 |             "notNull": true
406 |           },
407 |           {
408 |             "fieldPath": "isPoll",
409 |             "columnName": "isPoll",
410 |             "affinity": "INTEGER",
411 |             "notNull": true
412 |           },
413 |           {
414 |             "fieldPath": "isClosed",
415 |             "columnName": "isClosed",
416 |             "affinity": "INTEGER",
417 |             "notNull": true
418 |           },
419 |           {
420 |             "fieldPath": "unreadPostCount",
421 |             "columnName": "unreadPostCount",
422 |             "affinity": "INTEGER",
423 |             "notNull": true
424 |           },
425 |           {
426 |             "fieldPath": "localReadPostId",
427 |             "columnName": "localReadPostId",
428 |             "affinity": "INTEGER",
429 |             "notNull": true
430 |           },
431 |           {
432 |             "fieldPath": "localReadPostDateMillis",
433 |             "columnName": "localReadPostDateMillis",
434 |             "affinity": "INTEGER",
435 |             "notNull": true
436 |           }
437 |         ],
438 |         "primaryKey": {
439 |           "autoGenerate": false,
440 |           "columnNames": [
441 |             "favId"
442 |           ]
443 |         },
444 |         "indices": [],
445 |         "foreignKeys": []
446 |       },
447 |       {
448 |         "tableName": "forum_items_flat",
449 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `parentId` INTEGER NOT NULL, `level` INTEGER NOT NULL, `title` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
450 |         "fields": [
451 |           {
452 |             "fieldPath": "id",
453 |             "columnName": "id",
454 |             "affinity": "INTEGER",
455 |             "notNull": true
456 |           },
457 |           {
458 |             "fieldPath": "parentId",
459 |             "columnName": "parentId",
460 |             "affinity": "INTEGER",
461 |             "notNull": true
462 |           },
463 |           {
464 |             "fieldPath": "level",
465 |             "columnName": "level",
466 |             "affinity": "INTEGER",
467 |             "notNull": true
468 |           },
469 |           {
470 |             "fieldPath": "title",
471 |             "columnName": "title",
472 |             "affinity": "TEXT",
473 |             "notNull": false
474 |           },
475 |           {
476 |             "fieldPath": "position",
477 |             "columnName": "position",
478 |             "affinity": "INTEGER",
479 |             "notNull": true
480 |           }
481 |         ],
482 |         "primaryKey": {
483 |           "autoGenerate": false,
484 |           "columnNames": [
485 |             "id"
486 |           ]
487 |         },
488 |         "indices": [],
489 |         "foreignKeys": []
490 |       },
491 |       {
492 |         "tableName": "forum_users",
493 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `nick` TEXT, `avatar` TEXT, PRIMARY KEY(`id`))",
494 |         "fields": [
495 |           {
496 |             "fieldPath": "id",
497 |             "columnName": "id",
498 |             "affinity": "INTEGER",
499 |             "notNull": true
500 |           },
501 |           {
502 |             "fieldPath": "nick",
503 |             "columnName": "nick",
504 |             "affinity": "TEXT",
505 |             "notNull": false
506 |           },
507 |           {
508 |             "fieldPath": "avatar",
509 |             "columnName": "avatar",
510 |             "affinity": "TEXT",
511 |             "notNull": false
512 |           }
513 |         ],
514 |         "primaryKey": {
515 |           "autoGenerate": false,
516 |           "columnNames": [
517 |             "id"
518 |           ]
519 |         },
520 |         "indices": [],
521 |         "foreignKeys": []
522 |       }
523 |     ],
524 |     "views": [],
525 |     "setupQueries": [
526 |       "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
527 |       "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '852f71bb74bcd224cddc939316e0fcd8')"
528 |     ]
529 |   }
530 | }
```

### app/schemas/forpdateam.ru.forpda.entity.db.notes.AppDatabase/8.json

Bytes: 15495
SHA-256: 182fe54682c7194784b55a6f67119e3ace4bf57b37416bd761690fd526bbd484
Lines: 1-530 of 530

```json
  1 | {
  2 |   "formatVersion": 1,
  3 |   "database": {
  4 |     "version": 8,
  5 |     "identityHash": "852f71bb74bcd224cddc939316e0fcd8",
  6 |     "entities": [
  7 |       {
  8 |         "tableName": "notes",
  9 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `link` TEXT NOT NULL, `content` TEXT NOT NULL, `folderId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
 10 |         "fields": [
 11 |           {
 12 |             "fieldPath": "id",
 13 |             "columnName": "id",
 14 |             "affinity": "INTEGER",
 15 |             "notNull": true
 16 |           },
 17 |           {
 18 |             "fieldPath": "title",
 19 |             "columnName": "title",
 20 |             "affinity": "TEXT",
 21 |             "notNull": true
 22 |           },
 23 |           {
 24 |             "fieldPath": "link",
 25 |             "columnName": "link",
 26 |             "affinity": "TEXT",
 27 |             "notNull": true
 28 |           },
 29 |           {
 30 |             "fieldPath": "content",
 31 |             "columnName": "content",
 32 |             "affinity": "TEXT",
 33 |             "notNull": true
 34 |           },
 35 |           {
 36 |             "fieldPath": "folderId",
 37 |             "columnName": "folderId",
 38 |             "affinity": "INTEGER",
 39 |             "notNull": false
 40 |           },
 41 |           {
 42 |             "fieldPath": "createdAt",
 43 |             "columnName": "createdAt",
 44 |             "affinity": "INTEGER",
 45 |             "notNull": true
 46 |           },
 47 |           {
 48 |             "fieldPath": "updatedAt",
 49 |             "columnName": "updatedAt",
 50 |             "affinity": "INTEGER",
 51 |             "notNull": true
 52 |           },
 53 |           {
 54 |             "fieldPath": "sortOrder",
 55 |             "columnName": "sortOrder",
 56 |             "affinity": "INTEGER",
 57 |             "notNull": true
 58 |           }
 59 |         ],
 60 |         "primaryKey": {
 61 |           "autoGenerate": false,
 62 |           "columnNames": [
 63 |             "id"
 64 |           ]
 65 |         },
 66 |         "indices": [],
 67 |         "foreignKeys": []
 68 |       },
 69 |       {
 70 |         "tableName": "note_folders",
 71 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
 72 |         "fields": [
 73 |           {
 74 |             "fieldPath": "id",
 75 |             "columnName": "id",
 76 |             "affinity": "INTEGER",
 77 |             "notNull": true
 78 |           },
 79 |           {
 80 |             "fieldPath": "name",
 81 |             "columnName": "name",
 82 |             "affinity": "TEXT",
 83 |             "notNull": true
 84 |           },
 85 |           {
 86 |             "fieldPath": "sortOrder",
 87 |             "columnName": "sortOrder",
 88 |             "affinity": "INTEGER",
 89 |             "notNull": true
 90 |           },
 91 |           {
 92 |             "fieldPath": "createdAt",
 93 |             "columnName": "createdAt",
 94 |             "affinity": "INTEGER",
 95 |             "notNull": true
 96 |           },
 97 |           {
 98 |             "fieldPath": "updatedAt",
 99 |             "columnName": "updatedAt",
100 |             "affinity": "INTEGER",
101 |             "notNull": true
102 |           }
103 |         ],
104 |         "primaryKey": {
105 |           "autoGenerate": true,
106 |           "columnNames": [
107 |             "id"
108 |           ]
109 |         },
110 |         "indices": [],
111 |         "foreignKeys": []
112 |       },
113 |       {
114 |         "tableName": "history",
115 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `url` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `unixTime` INTEGER NOT NULL, PRIMARY KEY(`id`))",
116 |         "fields": [
117 |           {
118 |             "fieldPath": "id",
119 |             "columnName": "id",
120 |             "affinity": "INTEGER",
121 |             "notNull": true
122 |           },
123 |           {
124 |             "fieldPath": "url",
125 |             "columnName": "url",
126 |             "affinity": "TEXT",
127 |             "notNull": true
128 |           },
129 |           {
130 |             "fieldPath": "date",
131 |             "columnName": "date",
132 |             "affinity": "TEXT",
133 |             "notNull": true
134 |           },
135 |           {
136 |             "fieldPath": "title",
137 |             "columnName": "title",
138 |             "affinity": "TEXT",
139 |             "notNull": true
140 |           },
141 |           {
142 |             "fieldPath": "unixTime",
143 |             "columnName": "unixTime",
144 |             "affinity": "INTEGER",
145 |             "notNull": true
146 |           }
147 |         ],
148 |         "primaryKey": {
149 |           "autoGenerate": false,
150 |           "columnNames": [
151 |             "id"
152 |           ]
153 |         },
154 |         "indices": [],
155 |         "foreignKeys": []
156 |       },
157 |       {
158 |         "tableName": "qms_contacts",
159 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`nick` TEXT NOT NULL, `id` INTEGER NOT NULL, `count` INTEGER NOT NULL, `avatar` TEXT, PRIMARY KEY(`nick`))",
160 |         "fields": [
161 |           {
162 |             "fieldPath": "nick",
163 |             "columnName": "nick",
164 |             "affinity": "TEXT",
165 |             "notNull": true
166 |           },
167 |           {
168 |             "fieldPath": "id",
169 |             "columnName": "id",
170 |             "affinity": "INTEGER",
171 |             "notNull": true
172 |           },
173 |           {
174 |             "fieldPath": "count",
175 |             "columnName": "count",
176 |             "affinity": "INTEGER",
177 |             "notNull": true
178 |           },
179 |           {
180 |             "fieldPath": "avatar",
181 |             "columnName": "avatar",
182 |             "affinity": "TEXT",
183 |             "notNull": false
184 |           }
185 |         ],
186 |         "primaryKey": {
187 |           "autoGenerate": false,
188 |           "columnNames": [
189 |             "nick"
190 |           ]
191 |         },
192 |         "indices": [],
193 |         "foreignKeys": []
194 |       },
195 |       {
196 |         "tableName": "qms_themes",
197 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `countMessages` INTEGER NOT NULL, `countNew` INTEGER NOT NULL, `name` TEXT, `date` TEXT, PRIMARY KEY(`id`))",
198 |         "fields": [
199 |           {
200 |             "fieldPath": "id",
201 |             "columnName": "id",
202 |             "affinity": "INTEGER",
203 |             "notNull": true
204 |           },
205 |           {
206 |             "fieldPath": "userId",
207 |             "columnName": "userId",
208 |             "affinity": "INTEGER",
209 |             "notNull": true
210 |           },
211 |           {
212 |             "fieldPath": "countMessages",
213 |             "columnName": "countMessages",
214 |             "affinity": "INTEGER",
215 |             "notNull": true
216 |           },
217 |           {
218 |             "fieldPath": "countNew",
219 |             "columnName": "countNew",
220 |             "affinity": "INTEGER",
221 |             "notNull": true
222 |           },
223 |           {
224 |             "fieldPath": "name",
225 |             "columnName": "name",
226 |             "affinity": "TEXT",
227 |             "notNull": false
228 |           },
229 |           {
230 |             "fieldPath": "date",
231 |             "columnName": "date",
232 |             "affinity": "TEXT",
233 |             "notNull": false
234 |           }
235 |         ],
236 |         "primaryKey": {
237 |           "autoGenerate": false,
238 |           "columnNames": [
239 |             "id"
240 |           ]
241 |         },
242 |         "indices": [],
243 |         "foreignKeys": []
244 |       },
245 |       {
246 |         "tableName": "qms_themes_list",
247 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`userId` INTEGER NOT NULL, `nick` TEXT, PRIMARY KEY(`userId`))",
248 |         "fields": [
249 |           {
250 |             "fieldPath": "userId",
251 |             "columnName": "userId",
252 |             "affinity": "INTEGER",
253 |             "notNull": true
254 |           },
255 |           {
256 |             "fieldPath": "nick",
257 |             "columnName": "nick",
258 |             "affinity": "TEXT",
259 |             "notNull": false
260 |           }
261 |         ],
262 |         "primaryKey": {
263 |           "autoGenerate": false,
264 |           "columnNames": [
265 |             "userId"
266 |           ]
267 |         },
268 |         "indices": [],
269 |         "foreignKeys": []
270 |       },
271 |       {
272 |         "tableName": "favorites",
273 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`favId` INTEGER NOT NULL, `topicId` INTEGER NOT NULL, `forumId` INTEGER NOT NULL, `authorId` INTEGER NOT NULL, `lastUserId` INTEGER NOT NULL, `stParam` INTEGER NOT NULL, `pages` INTEGER NOT NULL, `curatorId` INTEGER NOT NULL, `trackType` TEXT, `infoColor` TEXT, `topicTitle` TEXT, `forumTitle` TEXT, `authorUserNick` TEXT, `lastUserNick` TEXT, `date` TEXT, `desc` TEXT, `curatorNick` TEXT, `subType` TEXT, `isPin` INTEGER NOT NULL, `isForum` INTEGER NOT NULL, `isNew` INTEGER NOT NULL, `readState` INTEGER NOT NULL, `isPoll` INTEGER NOT NULL, `isClosed` INTEGER NOT NULL, `unreadPostCount` INTEGER NOT NULL, `localReadPostId` INTEGER NOT NULL, `localReadPostDateMillis` INTEGER NOT NULL, PRIMARY KEY(`favId`))",
274 |         "fields": [
275 |           {
276 |             "fieldPath": "favId",
277 |             "columnName": "favId",
278 |             "affinity": "INTEGER",
279 |             "notNull": true
280 |           },
281 |           {
282 |             "fieldPath": "topicId",
283 |             "columnName": "topicId",
284 |             "affinity": "INTEGER",
285 |             "notNull": true
286 |           },
287 |           {
288 |             "fieldPath": "forumId",
289 |             "columnName": "forumId",
290 |             "affinity": "INTEGER",
291 |             "notNull": true
292 |           },
293 |           {
294 |             "fieldPath": "authorId",
295 |             "columnName": "authorId",
296 |             "affinity": "INTEGER",
297 |             "notNull": true
298 |           },
299 |           {
300 |             "fieldPath": "lastUserId",
301 |             "columnName": "lastUserId",
302 |             "affinity": "INTEGER",
303 |             "notNull": true
304 |           },
305 |           {
306 |             "fieldPath": "stParam",
307 |             "columnName": "stParam",
308 |             "affinity": "INTEGER",
309 |             "notNull": true
310 |           },
311 |           {
312 |             "fieldPath": "pages",
313 |             "columnName": "pages",
314 |             "affinity": "INTEGER",
315 |             "notNull": true
316 |           },
317 |           {
318 |             "fieldPath": "curatorId",
319 |             "columnName": "curatorId",
320 |             "affinity": "INTEGER",
321 |             "notNull": true
322 |           },
323 |           {
324 |             "fieldPath": "trackType",
325 |             "columnName": "trackType",
326 |             "affinity": "TEXT",
327 |             "notNull": false
328 |           },
329 |           {
330 |             "fieldPath": "infoColor",
331 |             "columnName": "infoColor",
332 |             "affinity": "TEXT",
333 |             "notNull": false
334 |           },
335 |           {
336 |             "fieldPath": "topicTitle",
337 |             "columnName": "topicTitle",
338 |             "affinity": "TEXT",
339 |             "notNull": false
340 |           },
341 |           {
342 |             "fieldPath": "forumTitle",
343 |             "columnName": "forumTitle",
344 |             "affinity": "TEXT",
345 |             "notNull": false
346 |           },
347 |           {
348 |             "fieldPath": "authorUserNick",
349 |             "columnName": "authorUserNick",
350 |             "affinity": "TEXT",
351 |             "notNull": false
352 |           },
353 |           {
354 |             "fieldPath": "lastUserNick",
355 |             "columnName": "lastUserNick",
356 |             "affinity": "TEXT",
357 |             "notNull": false
358 |           },
359 |           {
360 |             "fieldPath": "date",
361 |             "columnName": "date",
362 |             "affinity": "TEXT",
363 |             "notNull": false
364 |           },
365 |           {
366 |             "fieldPath": "desc",
367 |             "columnName": "desc",
368 |             "affinity": "TEXT",
369 |             "notNull": false
370 |           },
371 |           {
372 |             "fieldPath": "curatorNick",
373 |             "columnName": "curatorNick",
374 |             "affinity": "TEXT",
375 |             "notNull": false
376 |           },
377 |           {
378 |             "fieldPath": "subType",
379 |             "columnName": "subType",
380 |             "affinity": "TEXT",
381 |             "notNull": false
382 |           },
383 |           {
384 |             "fieldPath": "isPin",
385 |             "columnName": "isPin",
386 |             "affinity": "INTEGER",
387 |             "notNull": true
388 |           },
389 |           {
390 |             "fieldPath": "isForum",
391 |             "columnName": "isForum",
392 |             "affinity": "INTEGER",
393 |             "notNull": true
394 |           },
395 |           {
396 |             "fieldPath": "isNew",
397 |             "columnName": "isNew",
398 |             "affinity": "INTEGER",
399 |             "notNull": true
400 |           },
401 |           {
402 |             "fieldPath": "readState",
403 |             "columnName": "readState",
404 |             "affinity": "INTEGER",
405 |             "notNull": true
406 |           },
407 |           {
408 |             "fieldPath": "isPoll",
409 |             "columnName": "isPoll",
410 |             "affinity": "INTEGER",
411 |             "notNull": true
412 |           },
413 |           {
414 |             "fieldPath": "isClosed",
415 |             "columnName": "isClosed",
416 |             "affinity": "INTEGER",
417 |             "notNull": true
418 |           },
419 |           {
420 |             "fieldPath": "unreadPostCount",
421 |             "columnName": "unreadPostCount",
422 |             "affinity": "INTEGER",
423 |             "notNull": true
424 |           },
425 |           {
426 |             "fieldPath": "localReadPostId",
427 |             "columnName": "localReadPostId",
428 |             "affinity": "INTEGER",
429 |             "notNull": true
430 |           },
431 |           {
432 |             "fieldPath": "localReadPostDateMillis",
433 |             "columnName": "localReadPostDateMillis",
434 |             "affinity": "INTEGER",
435 |             "notNull": true
436 |           }
437 |         ],
438 |         "primaryKey": {
439 |           "autoGenerate": false,
440 |           "columnNames": [
441 |             "favId"
442 |           ]
443 |         },
444 |         "indices": [],
445 |         "foreignKeys": []
446 |       },
447 |       {
448 |         "tableName": "forum_items_flat",
449 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `parentId` INTEGER NOT NULL, `level` INTEGER NOT NULL, `title` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
450 |         "fields": [
451 |           {
452 |             "fieldPath": "id",
453 |             "columnName": "id",
454 |             "affinity": "INTEGER",
455 |             "notNull": true
456 |           },
457 |           {
458 |             "fieldPath": "parentId",
459 |             "columnName": "parentId",
460 |             "affinity": "INTEGER",
461 |             "notNull": true
462 |           },
463 |           {
464 |             "fieldPath": "level",
465 |             "columnName": "level",
466 |             "affinity": "INTEGER",
467 |             "notNull": true
468 |           },
469 |           {
470 |             "fieldPath": "title",
471 |             "columnName": "title",
472 |             "affinity": "TEXT",
473 |             "notNull": false
474 |           },
475 |           {
476 |             "fieldPath": "position",
477 |             "columnName": "position",
478 |             "affinity": "INTEGER",
479 |             "notNull": true
480 |           }
481 |         ],
482 |         "primaryKey": {
483 |           "autoGenerate": false,
484 |           "columnNames": [
485 |             "id"
486 |           ]
487 |         },
488 |         "indices": [],
489 |         "foreignKeys": []
490 |       },
491 |       {
492 |         "tableName": "forum_users",
493 |         "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER NOT NULL, `nick` TEXT, `avatar` TEXT, PRIMARY KEY(`id`))",
494 |         "fields": [
495 |           {
496 |             "fieldPath": "id",
497 |             "columnName": "id",
498 |             "affinity": "INTEGER",
499 |             "notNull": true
500 |           },
501 |           {
502 |             "fieldPath": "nick",
503 |             "columnName": "nick",
504 |             "affinity": "TEXT",
505 |             "notNull": false
506 |           },
507 |           {
508 |             "fieldPath": "avatar",
509 |             "columnName": "avatar",
510 |             "affinity": "TEXT",
511 |             "notNull": false
512 |           }
513 |         ],
514 |         "primaryKey": {
515 |           "autoGenerate": false,
516 |           "columnNames": [
517 |             "id"
518 |           ]
519 |         },
520 |         "indices": [],
521 |         "foreignKeys": []
522 |       }
523 |     ],
524 |     "views": [],
525 |     "setupQueries": [
526 |       "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
527 |       "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '852f71bb74bcd224cddc939316e0fcd8')"
528 |     ]
529 |   }
530 | }
```

### app/src/main/AndroidManifest.xml

Bytes: 6761
SHA-256: 156b1016b9fcab627ad02401d99c9a2153f7fa5d84692bc38b4100c29cb6200f
Lines: 1-170 of 170

```text
  1 | <?xml version="1.0" encoding="utf-8"?>
  2 | <manifest xmlns:android="http://schemas.android.com/apk/res/android"
  3 |     xmlns:tools="http://schemas.android.com/tools"
  4 |     android:versionCode="351"
  5 |     android:versionName="2.9.5">
  6 | 
  7 |     <uses-feature
  8 |         android:name="android.hardware.touchscreen"
  9 |         android:required="false" />
 10 | 
 11 |     <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 12 |     <uses-permission android:name="android.permission.INTERNET" />
 13 |     <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 14 |     <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 15 |     <uses-permission
 16 |         android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"
 17 |         tools:targetApi="34" />
 18 |     <uses-permission
 19 |         android:name="android.permission.WRITE_EXTERNAL_STORAGE"
 20 |         android:maxSdkVersion="29" />
 21 |     <uses-permission android:name="android.permission.VIBRATE" />
 22 |     <uses-permission
 23 |         android:name="android.permission.POST_NOTIFICATIONS"
 24 |         tools:targetApi="33" />
 25 | 
 26 |     <queries>
 27 |         <package android:name="com.android.chrome" />
 28 |         <package android:name="org.mozilla.firefox" />
 29 |         <package android:name="com.microsoft.emmx" />
 30 |         <package android:name="com.sec.android.app.sbrowser" />
 31 |         <package android:name="com.opera.browser" />
 32 |         <package android:name="com.opera.mini.native" />
 33 |         <package android:name="com.yandex.browser" />
 34 | 
 35 |         <intent>
 36 |             <action android:name="android.intent.action.VIEW" />
 37 |             <category android:name="android.intent.category.BROWSABLE" />
 38 |             <data android:scheme="http" />
 39 |         </intent>
 40 |         <intent>
 41 |             <action android:name="android.intent.action.VIEW" />
 42 |             <category android:name="android.intent.category.BROWSABLE" />
 43 |             <data android:scheme="https" />
 44 |         </intent>
 45 |     </queries>
 46 | 
 47 |     <application
 48 |         android:name=".App"
 49 |         android:allowBackup="true"
 50 |         android:dataExtractionRules="@xml/data_extraction_rules"
 51 |         android:fullBackupContent="@xml/backup_rules"
 52 |         android:extractNativeLibs="true"
 53 |         android:icon="@mipmap/ic_launcher"
 54 |         android:label="@string/app_name"
 55 |         android:roundIcon="@mipmap/ic_launcher"
 56 |         android:supportsRtl="true"
 57 |         android:theme="@style/DayNightAppTheme"
 58 |         android:enableOnBackInvokedCallback="true"
 59 |         android:networkSecurityConfig="@xml/network_security_config">
 60 |         <activity
 61 |             android:name=".ui.activities.MainActivity"
 62 |             android:configChanges="keyboardHidden|screenSize|orientation"
 63 |             android:exported="true"
 64 |             android:launchMode="singleTask"
 65 |             android:theme="@style/SplashTheme"
 66 |             android:windowSoftInputMode="adjustResize">
 67 |             <intent-filter>
 68 |                 <action android:name="android.intent.action.MAIN" />
 69 | 
 70 |                 <category android:name="android.intent.category.LAUNCHER" />
 71 |             </intent-filter>
 72 |             <intent-filter>
 73 |                 <action android:name="android.intent.action.VIEW" />
 74 | 
 75 |                 <category android:name="android.intent.category.DEFAULT" />
 76 |                 <category android:name="android.intent.category.BROWSABLE" />
 77 | 
 78 |                 <data
 79 |                     android:host="4pda.to"
 80 |                     android:scheme="http" />
 81 |                 <data
 82 |                     android:host="www.4pda.to"
 83 |                     android:scheme="http" />
 84 |                 <data
 85 |                     android:host="4pda.to"
 86 |                     android:scheme="https" />
 87 |                 <data
 88 |                     android:host="www.4pda.to"
 89 |                     android:scheme="https" />
 90 | 
 91 |             </intent-filter>
 92 | 
 93 |             <meta-data
 94 |                 android:name="android.max_aspect"
 95 |                 android:value="2.1" />
 96 |             <meta-data
 97 |                 android:name="android.app.shortcuts"
 98 |                 android:resource="@xml/shortcuts" />
 99 |         </activity>
100 |         <activity
101 |             android:name=".ui.activities.imageviewer.ImageViewerActivity"
102 |             android:configChanges="orientation|keyboardHidden|screenSize"
103 |             android:exported="false"
104 |             android:launchMode="singleTask"
105 |             android:theme="@style/DayNightAppTheme.NoActionBar"
106 |             android:windowSoftInputMode="adjustResize" />
107 | 
108 |         <activity
109 |             android:name=".ui.activities.SettingsActivity"
110 |             android:exported="false"
111 |             android:label="@string/activity_title_settings"
112 |             android:theme="@style/DayNightPreferenceTheme"
113 |             android:windowSoftInputMode="adjustResize">
114 |             <meta-data
115 |                 android:name="android.max_aspect"
116 |                 android:value="2.1" />
117 |         </activity>
118 | 
119 |         <activity
120 |             android:name=".ui.activities.WebVewNotFoundActivity"
121 |             android:exported="false"
122 |             android:launchMode="singleTop"
123 |             android:theme="@style/DayNightAppTheme.NoActionBar" />
124 | 
125 |         <service
126 |             android:name=".notifications.NotificationsService"
127 |             android:exported="false"
128 |             android:foregroundServiceType="dataSync" />
129 | 
130 |         <!-- WorkManager foreground downloads require explicit type on Android 14+ -->
131 |         <service
132 |             android:name="androidx.work.impl.foreground.SystemForegroundService"
133 |             android:exported="false"
134 |             android:foregroundServiceType="dataSync"
135 |             tools:node="merge" />
136 | 
137 |         <provider
138 |             android:name="androidx.startup.InitializationProvider"
139 |             android:authorities="${applicationId}.androidx-startup"
140 |             android:exported="false"
141 |             tools:node="merge">
142 |             <meta-data
143 |                 android:name="androidx.work.WorkManagerInitializer"
144 |                 android:value="androidx.startup"
145 |                 tools:node="remove" />
146 |         </provider>
147 | 
148 |         <provider
149 |             android:name="androidx.core.content.FileProvider"
150 |             android:authorities="${applicationId}.fileprovider"
151 |             android:exported="false"
152 |             android:grantUriPermissions="true">
153 |             <meta-data
154 |                 android:name="android.support.FILE_PROVIDER_PATHS"
155 |                 android:resource="@xml/file_paths" />
156 |         </provider>
157 | 
158 |         <receiver
159 |             android:name=".common.receivers.WakeUpReceiver"
160 |             android:enabled="true"
161 |             android:exported="true"
162 |             android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
163 |             <intent-filter>
164 |                 <action android:name="android.intent.action.BOOT_COMPLETED" />
165 |             </intent-filter>
166 |         </receiver>
167 |     </application>
168 | 
169 | </manifest>
170 | 
```

### app/src/main/assets/forpda/styles/dark/dark_themes.css

Bytes: 109365
SHA-256: 05c9c9ce4ae226a32f0330d3ddd479ae87a6e7b335fce26ef86ff4e912d5515f
Lines: 1-3539 of 3539

```css
   1 | /* Generated by less 2.5.1 */
   2 | /*LIGHT*/
   3 | /*DARK*/
   4 | /*OTHERS*/
   5 | /*Curves*/
   6 | /*Fonts*/
   7 | /*Color scheme*/
   8 | /* NEW COLORS */
   9 | /*Elements colors*/
  10 | /*Post blocks*/
  11 | .noselect {
  12 |   -webkit-touch-callout: none;
  13 |   -webkit-user-select: none;
  14 |   -khtml-user-select: none;
  15 |   -moz-user-select: none;
  16 |   -ms-user-select: none;
  17 |   user-select: none;
  18 | }
  19 | body {
  20 |   background: #121212;
  21 |   /* Theme accent palette for the topic post-highlight frame, defined at body
  22 |    * scope so it cascades to `.post_container::after`. These follow the dark
  23 |    * theme's own chrome accent (the post action / link color #E0E0E0) so the
  24 |    * frame is on-brand and changes with the selected palette; a per-theme
  25 |    * override of `--fpda-theme-accent` (or the chrome icon color) re-points all
  26 |    * three. Each highlight rule still carries a hardcoded hex as the ultimate
  27 |    * fallback (var chain: theme accent -> chrome icon color -> hex). */
  28 |   --fpda-theme-accent: var(--topic-action-icon-color, #E0E0E0);
  29 |   --fpda-accent-primary: var(--fpda-theme-accent, #E0E0E0);
  30 |   --fpda-accent-muted: var(--fpda-theme-accent, #9aa0a6);
  31 |   --fpda-accent-neutral: var(--fpda-theme-accent, #b0b6bb);
  32 | }
  33 | .rep-action-symbols {
  34 |   position: absolute;
  35 |   width: 0;
  36 |   height: 0;
  37 |   overflow: hidden;
  38 | }
  39 | .hat_content.close {
  40 |   display: none;
  41 | }
  42 | #search .panel.bottom:empty + #bottomMargin {
  43 |   display: none;
  44 | }
  45 | #search .hat_content.close {
  46 |   display: block;
  47 |   max-height: 22.5rem;
  48 |   overflow: hidden;
  49 |   position: relative;
  50 | }
  51 | #search .hat_content.close.over_height:after {
  52 |   content: "";
  53 |   position: absolute;
  54 |   left: 0;
  55 |   bottom: 0;
  56 |   right: 0;
  57 |   height: 6rem;
  58 |   box-shadow: inset 0 -6rem 3rem -3rem #212121;
  59 |   z-index: 100;
  60 | }
  61 | #search .hat_content.open {
  62 |   display: block;
  63 |   max-height: none;
  64 |   height: auto;
  65 |   min-height: 0;
  66 |   overflow: visible;
  67 |   position: relative;
  68 | }
  69 | #search .hat_content.open:after {
  70 |   content: none !important;
  71 |   display: none !important;
  72 | }
  73 | #search .post_container.open > .hat_content {
  74 |   max-height: none !important;
  75 |   height: auto !important;
  76 |   overflow: visible !important;
  77 | }
  78 | #search .post_container.open .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body {
  79 |   max-height: none !important;
  80 |   overflow: visible !important;
  81 | }
  82 | #search .post_container.open .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body:after {
  83 |   content: none !important;
  84 |   display: none !important;
  85 | }
  86 | #search .search_jump_to_post {
  87 |   margin-top: 0.35rem;
  88 |   margin-bottom: 0.65rem;
  89 |   margin-left: 0.5rem;
  90 |   margin-right: 0.5rem;
  91 |   padding: 0.4rem 0.85rem 0.55rem;
  92 |   display: flex;
  93 |   align-items: center;
  94 |   justify-content: space-between;
  95 |   gap: 0.5rem;
  96 |   flex-wrap: wrap;
  97 | }
  98 | #search a.search_post_btn {
  99 |   display: inline-block;
 100 |   padding: 0.35rem 0.75rem;
 101 |   border-radius: 6px;
 102 |   font-size: 0.75rem;
 103 |   font-weight: 600;
 104 |   letter-spacing: 0.02em;
 105 |   text-decoration: none;
 106 |   text-transform: none;
 107 |   line-height: 1.25;
 108 |   border: 1px solid rgba(255, 255, 255, 0.14);
 109 |   background: rgba(255, 255, 255, 0.06);
 110 |   color: #90caf9;
 111 |   -webkit-tap-highlight-color: rgba(255, 255, 255, 0.08);
 112 | }
 113 | #search a.search_post_btn:active {
 114 |   opacity: 0.9;
 115 |   background: rgba(255, 255, 255, 0.1);
 116 | }
 117 | #search .search_post_id_hint {
 118 |   font-size: 0.72rem;
 119 |   opacity: 0.5;
 120 |   color: #b0b0b0;
 121 | }
 122 | button,
 123 | .btn {
 124 |   display: block;
 125 |   padding: 0.875em 1em;
 126 |   font-size: 1em;
 127 |   background: none;
 128 |   border: none;
 129 |   font-weight: bold;
 130 |   text-transform: uppercase;
 131 |   -webkit-tap-highlight-color: rgba(0, 0, 0, 0);
 132 |   outline: none!important;
 133 |   text-align: center;
 134 |   color: #E0E0E0;
 135 | }
 136 | button > span,
 137 | .btn > span,
 138 | button > b,
 139 | .btn > b {
 140 |   font-size: 0.875em;
 141 | }
 142 | button > *,
 143 | .btn > * {
 144 |   display: inline;
 145 |   vertical-align: top;
 146 | }
 147 | button:active,
 148 | .btn:active {
 149 |   background: rgba(255, 255, 255, 0.1);
 150 | }
 151 | @keyframes pollRangeAnimation {
 152 |   0% {
 153 |     -webkit-transform: scaleX(0.1) translateZ(0);
 154 |     transform: scaleX(0.1) translateZ(0);
 155 |   }
 156 |   100% {
 157 |     -webkit-transform: scaleX(1) translateZ(0);
 158 |     transform: scaleX(1) translateZ(0);
 159 |   }
 160 | }
 161 | @keyframes pollRangeValueAnimation {
 162 |   0% {
 163 |     visibility: visible;
 164 |     opacity: 0;
 165 |   }
 166 |   100% {
 167 |     visibility: visible;
 168 |     opacity: 1;
 169 |   }
 170 | }
 171 | @keyframes radAnim1 {
 172 |   0% {
 173 |     -webkit-transform: scale(1);
 174 |     transform: scale(1);
 175 |   }
 176 |   50% {
 177 |     -webkit-transform: scale(0.95);
 178 |     transform: scale(0.95);
 179 |   }
 180 |   100% {
 181 |     -webkit-transform: scale(1);
 182 |     transform: scale(1);
 183 |   }
 184 | }
 185 | .poll {
 186 |   background: #1E1E1E;
 187 |   margin: 0.5em 0;
 188 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
 189 |   -webkit-border-radius: 0.875rem;
 190 |   border-radius: 0.875rem;
 191 |   overflow: hidden;
 192 | }
 193 | .poll.poll_overlay_host {
 194 |   position: fixed;
 195 |   top: 0;
 196 |   left: 0.5em;
 197 |   right: 0.5em;
 198 |   z-index: 21;
 199 |   --theme-poll-max-height: calc(100vh - var(--theme-bottom-chrome-padding, 0px) - 1rem);
 200 |   margin: 0.5em 0;
 201 |   box-sizing: border-box;
 202 | }
 203 | .poll.poll_overlay_host.open {
 204 |   display: flex;
 205 |   flex-direction: column;
 206 |   max-height: var(--theme-poll-max-height);
 207 |   border-bottom: 0.0625rem solid rgba(255, 255, 255, 0.05);
 208 |   overscroll-behavior: contain;
 209 |   pointer-events: auto;
 210 | }
 211 | .poll.poll_overlay_host.open > .body {
 212 |   flex: 1 1 auto;
 213 |   min-height: 0;
 214 |   max-height: calc(var(--theme-poll-max-height) - 3rem);
 215 |   padding-bottom: 0.5rem;
 216 |   overflow-y: auto;
 217 |   overflow-x: hidden;
 218 |   -webkit-overflow-scrolling: touch;
 219 |   overscroll-behavior: contain;
 220 |   touch-action: pan-y;
 221 | }
 222 | .poll.poll_overlay_host.close {
 223 |   display: none;
 224 |   pointer-events: none;
 225 | }
 226 | .poll.poll_entry.close {
 227 |   display: block;
 228 | }
 229 | .poll.poll_entry.close > .body {
 230 |   display: none;
 231 | }
 232 | .poll > .title {
 233 |   -webkit-touch-callout: none;
 234 |   -webkit-user-select: none;
 235 |   -khtml-user-select: none;
 236 |   -moz-user-select: none;
 237 |   -ms-user-select: none;
 238 |   user-select: none;
 239 |   display: flex;
 240 |   align-items: center;
 241 |   justify-content: space-between;
 242 |   gap: 0.5rem;
 243 |   box-sizing: border-box;
 244 |   min-height: 3rem;
 245 |   color: #E0E0E0;
 246 |   position: relative;
 247 |   text-align: left;
 248 |   line-height: 1.25rem;
 249 |   padding-right: 0.5rem;
 250 | }
 251 | .poll > .title > span {
 252 |   flex: 1 1 auto;
 253 |   min-width: 0;
 254 |   overflow: hidden;
 255 |   text-overflow: ellipsis;
 256 |   white-space: nowrap;
 257 |   color: inherit;
 258 |   line-height: 1.25rem;
 259 | }
 260 | .poll > .title .icon {
 261 |   display: flex;
 262 |   align-items: center;
 263 |   justify-content: center;
 264 |   flex: 0 0 2.5rem;
 265 |   height: 2.5rem;
 266 |   width: 2.5rem;
 267 |   float: none;
 268 |   right: auto;
 269 |   top: auto;
 270 |   position: relative;
 271 |   -webkit-transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 272 |   transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 273 |   -webkit-transform-origin: center;
 274 |   transform-origin: center;
 275 | }
 276 | .poll > .title .icon:after {
 277 |   content: "";
 278 |   position: absolute;
 279 |   left: 50%;
 280 |   top: 50%;
 281 |   margin-left: -0.25em;
 282 |   margin-top: -0.25em;
 283 |   border-left: 0.125rem solid rgba(255, 255, 255, 0.5);
 284 |   border-bottom: 0.125rem solid rgba(255, 255, 255, 0.5);
 285 |   box-sizing: border-box;
 286 |   height: 0.5em;
 287 |   width: 0.5em;
 288 |   border-color: #E0E0E0;
 289 |   -webkit-transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 290 |   transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 291 |   -webkit-transform-origin: center;
 292 |   transform-origin: center;
 293 | }
 294 | .poll > .title .icon:before {
 295 |   content: "";
 296 |   position: absolute;
 297 |   left: 50%;
 298 |   top: 50%;
 299 |   margin-left: -1em;
 300 |   margin-top: -1em;
 301 |   height: 2em;
 302 |   width: 2em;
 303 |   background: #ffffff;
 304 |   -webkit-border-radius: 100%;
 305 |   border-radius: 100%;
 306 |   -webkit-transform: scale(0.5) translateZ(0);
 307 |   transform: scale(0.5) translateZ(0);
 308 |   opacity: 0;
 309 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 310 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 311 | }
 312 | .poll > .title:active .icon:before {
 313 |   opacity: 0.05;
 314 |   -webkit-transform: scale(1) translateZ(0);
 315 |   transform: scale(1) translateZ(0);
 316 | }
 317 | .poll > .body {
 318 |   -webkit-hyphens: auto;
 319 |   -ms-hyphens: auto;
 320 |   hyphens: auto;
 321 |   word-wrap: break-word;
 322 | }
 323 | .poll > .body .questions .question {
 324 |   padding-bottom: 0.5em;
 325 | }
 326 | .poll > .body .questions .question > .title {
 327 |   font-weight: bold;
 328 |   padding: 1em 1em 0em 1em;
 329 |   line-height: 1.5em;
 330 | }
 331 | .poll > .body .questions .question:first-child > .title {
 332 |   padding-top: 0.5em;
 333 | }
 334 | .poll > .body .questions .question > .items {
 335 |   padding: 0 1em;
 336 | }
 337 | .poll > .body .questions .question > .items .item {
 338 |   display: block;
 339 | }
 340 | .poll > .body .questions .question > .items .item.default {
 341 |   padding: 0.875em 0;
 342 |   position: relative;
 343 |   cursor: pointer;
 344 |   min-height: 1.25em;
 345 |   touch-action: manipulation;
 346 | }
 347 | .poll > .body .questions .question > .items .item.default input {
 348 |   position: absolute;
 349 |   left: 0;
 350 |   top: 50%;
 351 |   width: 1.25em;
 352 |   height: 1.25em;
 353 |   margin: -0.625em 0 0 0;
 354 |   opacity: 0;
 355 |   z-index: 2;
 356 | }
 357 | .poll > .body .questions .question > .items .item.default input[type="radio"] ~ .icon {
 358 |   -webkit-border-radius: 100%;
 359 |   border-radius: 100%;
 360 |   -webkit-mask-image: none;
 361 |   mask-image: none;
 362 |   -webkit-mask-position: center center;
 363 |   mask-position: center center;
 364 |   -webkit-mask-size: 1.25em 1.25em;
 365 |   mask-size: 1.25em 1.25em;
 366 |   -webkit-mask-origin: border-box;
 367 |   mask-origin: border-box;
 368 |   -webkit-mask-clip: border-box;
 369 |   mask-clip: border-box;
 370 | }
 371 | .poll > .body .questions .question > .items .item.default input[type="radio"] ~ .icon:after,
 372 | .poll > .body .questions .question > .items .item.default input[type="radio"] ~ .icon:before {
 373 |   -webkit-border-radius: 100%;
 374 |   border-radius: 100%;
 375 | }
 376 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon {
 377 |   -webkit-border-radius: 0.125em;
 378 |   border-radius: 0.125em;
 379 |   height: 1.125em;
 380 |   width: 1.125em;
 381 |   margin-top: -0.5625em;
 382 |   left: 0.0625em;
 383 | }
 384 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add {
 385 |   position: absolute;
 386 |   height: 100%;
 387 |   width: 100%;
 388 |   top: 0;
 389 |   left: 0;
 390 |   -webkit-transform: rotate(-45deg);
 391 |   transform: rotate(-45deg);
 392 |   z-index: 1;
 393 | }
 394 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:after,
 395 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:before {
 396 |   content: "";
 397 |   position: absolute;
 398 |   box-sizing: border-box;
 399 |   border: 0.125rem solid rgba(255, 255, 255, 0.5);
 400 |   border-color: #212121;
 401 |   border-top: none;
 402 |   border-right: none;
 403 |   -webkit-transform-origin: left bottom;
 404 |   transform-origin: left bottom;
 405 |   opacity: 0;
 406 |   -webkit-transition: 0.088s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.11s opacity cubic-bezier(0.4, 0, 0.2, 1);
 407 |   transition: 0.088s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.11s opacity cubic-bezier(0.4, 0, 0.2, 1);
 408 | }
 409 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:after {
 410 |   top: 0.4375em;
 411 |   left: 0.125em;
 412 |   height: 0.125em;
 413 |   width: 0.8125em;
 414 |   -webkit-transform: scaleX(0.15);
 415 |   transform: scaleX(0.15);
 416 | }
 417 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:before {
 418 |   left: 0.125em;
 419 |   top: 0.125em;
 420 |   height: 0.4375em;
 421 |   width: 0.125em;
 422 |   -webkit-transform: scaleY(0.35);
 423 |   transform: scaleY(0.35);
 424 | }
 425 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon:after,
 426 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon:before {
 427 |   -webkit-border-radius: 0;
 428 |   border-radius: 0;
 429 | }
 430 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon:before {
 431 |   height: 1.25em;
 432 |   width: 1.25em;
 433 |   margin-top: -0.625em;
 434 |   margin-left: -0.625em;
 435 |   border: 0.375em solid #ffffff;
 436 |   -webkit-transform: scale(2);
 437 |   transform: scale(2);
 438 | }
 439 | .poll > .body .questions .question > .items .item.default input:checked + .icon {
 440 |   border-color: #E0E0E0;
 441 |   -webkit-transition: 0s 0s border-color cubic-bezier(0.4, 0, 0.2, 1);
 442 |   transition: 0s 0s border-color cubic-bezier(0.4, 0, 0.2, 1);
 443 |   /*-webkit-animation: radAnim1 @dur @curveStandard;
 444 |                                         animation: radAnim1 @dur @curveStandard;*/
 445 | }
 446 | .poll > .body .questions .question > .items .item.default input:checked + .icon:after {
 447 |   -webkit-transform: scale(0.1);
 448 |   transform: scale(0.1);
 449 |   -webkit-transition: 0.176s 0.11s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.088s opacity cubic-bezier(0.4, 0, 0.2, 1);
 450 |   transition: 0.176s 0.11s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.088s opacity cubic-bezier(0.4, 0, 0.2, 1);
 451 |   opacity: 1;
 452 | }
 453 | .poll > .body .questions .question > .items .item.default input:checked + .icon:before {
 454 |   -webkit-transform: scale(1);
 455 |   transform: scale(1);
 456 |   -webkit-transition: 0s 0.198s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0s opacity cubic-bezier(0.4, 0, 0.2, 1);
 457 |   transition: 0s 0.198s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0s opacity cubic-bezier(0.4, 0, 0.2, 1);
 458 |   opacity: 1;
 459 |   border-width: 0.3125em;
 460 | }
 461 | .poll > .body .questions .question > .items .item.default input:checked[type="checkbox"] + .icon .add:after,
 462 | .poll > .body .questions .question > .items .item.default input:checked[type="checkbox"] + .icon .add:before {
 463 |   opacity: 1;
 464 |   -webkit-transition: 0.176s 0.198s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s opacity cubic-bezier(0.4, 0, 0.2, 1);
 465 |   transition: 0.176s 0.198s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s opacity cubic-bezier(0.4, 0, 0.2, 1);
 466 |   -webkit-transform: scaleX(1) scaleY(1);
 467 |   transform: scaleX(1) scaleY(1);
 468 | }
 469 | .poll > .body .questions .question > .items .item.default input:checked[type="checkbox"] + .icon:before {
 470 |   background: #ffffff;
 471 | }
 472 | .poll > .body .questions .question > .items .item.default > .icon {
 473 |   display: block;
 474 |   position: absolute;
 475 |   height: 1.25em;
 476 |   width: 1.25em;
 477 |   background: #242424;
 478 |   left: 0;
 479 |   top: 50%;
 480 |   margin-top: -0.625em;
 481 |   border: 0.125rem solid rgba(255, 255, 255, 0.5);
 482 |   overflow: hidden;
 483 |   -webkit-transform: translateZ(0);
 484 |   transform: translateZ(0);
 485 |   -webkit-transition: 0s 0.352s border-color cubic-bezier(0.4, 0, 0.2, 1);
 486 |   transition: 0s 0.352s border-color cubic-bezier(0.4, 0, 0.2, 1);
 487 |   outline: 2px solid transparent;
 488 | }
 489 | .poll > .body .questions .question > .items .item.default > .icon:after,
 490 | .poll > .body .questions .question > .items .item.default > .icon:before {
 491 |   content: "";
 492 |   position: absolute;
 493 |   top: 50%;
 494 |   left: 50%;
 495 |   opacity: 0;
 496 |   box-sizing: border-box;
 497 | }
 498 | .poll > .body .questions .question > .items .item.default > .icon:after {
 499 |   height: 1.25em;
 500 |   width: 1.25em;
 501 |   margin-top: -0.625em;
 502 |   margin-left: -0.625em;
 503 |   border: 0.375em solid #ffffff;
 504 |   opacity: 0;
 505 |   -webkit-transform: scale(2.25);
 506 |   transform: scale(2.25);
 507 |   -webkit-transition: 0.176s 0.088s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.352s opacity cubic-bezier(0.4, 0, 0.2, 1);
 508 |   transition: 0.176s 0.088s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.352s opacity cubic-bezier(0.4, 0, 0.2, 1);
 509 | }
 510 | .poll > .body .questions .question > .items .item.default > .icon:before {
 511 |   height: 0.625em;
 512 |   width: 0.625em;
 513 |   margin-top: -0.3125em;
 514 |   margin-left: -0.3125em;
 515 |   border: 0.1875em solid #ffffff;
 516 |   -webkit-transform: scale(4);
 517 |   transform: scale(4);
 518 |   -webkit-transition: 0s 0.132s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.132s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.44s opacity cubic-bezier(0.4, 0, 0.2, 1);
 519 |   transition: 0s 0.132s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.132s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.44s opacity cubic-bezier(0.4, 0, 0.2, 1);
 520 | }
 521 | .poll > .body .questions .question > .items .item.default > .title {
 522 |   display: inline-block;
 523 |   padding-left: 2em;
 524 |   min-height: 1.25em;
 525 | }
 526 | .poll > .body .questions .question > .items .item.default:active:before {
 527 |   opacity: 0.05;
 528 |   -webkit-transform: scale(1) translateZ(0);
 529 |   transform: scale(1) translateZ(0);
 530 | }
 531 | .poll > .body .questions .question > .items .item.default:before {
 532 |   content: "";
 533 |   position: absolute;
 534 |   left: 0%;
 535 |   top: 50%;
 536 |   margin-left: -0.625em;
 537 |   margin-top: -1.25em;
 538 |   height: 2.5em;
 539 |   width: 2.5em;
 540 |   background: rgba(255, 255, 255, 0.1);
 541 |   -webkit-border-radius: 100%;
 542 |   border-radius: 100%;
 543 |   -webkit-transform: scale(0.5) translateZ(0);
 544 |   transform: scale(0.5) translateZ(0);
 545 |   opacity: 0;
 546 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 547 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 548 | }
 549 | .poll > .body .questions .question > .items .item.readonly {
 550 |   cursor: default;
 551 |   opacity: 0.72;
 552 |   pointer-events: none;
 553 | }
 554 | .poll > .body .questions .question > .items .item.readonly input {
 555 |   display: none;
 556 | }
 557 | .poll > .body .questions .question > .items .item.readonly > .icon,
 558 | .poll > .body .questions .question > .items .item.readonly:before {
 559 |   display: none;
 560 | }
 561 | .poll > .body .questions .question > .items .item.readonly > .title {
 562 |   padding-left: 0;
 563 | }
 564 | .poll > .body .questions .question > .items .item.result {
 565 |   padding-top: 0.5em;
 566 |   margin-bottom: 0.5em;
 567 |   position: relative;
 568 | }
 569 | .poll > .body .questions .question > .items .item.result > .title {
 570 |   position: relative;
 571 |   z-index: 1;
 572 |   display: inline-block;
 573 |   background: #242424;
 574 |   -webkit-box-shadow: 0.75em 0 0.5em -0.25em #242424;
 575 |   box-shadow: 0.75em 0 0.5em -0.25em #242424;
 576 |   line-height: 1.5em;
 577 | }
 578 | .poll > .body .questions .question > .items .item.result .range_bar {
 579 |   background: rgba(255, 255, 255, 0.1);
 580 |   box-sizing: border-box;
 581 |   position: relative;
 582 |   min-height: 1.5rem;
 583 |   padding: 0.375rem 0.75rem;
 584 |   -webkit-border-radius: 2px;
 585 |   border-radius: 2px;
 586 | }
 587 | .poll > .body .questions .question > .items .item.result .range_bar .range {
 588 |   position: absolute;
 589 |   left: 0;
 590 |   top: 0;
 591 |   height: 100%;
 592 |   width: auto;
 593 |   background: rgba(255, 255, 255, 0.1);
 594 |   -webkit-border-radius: 2px;
 595 |   border-radius: 2px;
 596 |   -webkit-transform-origin: left center;
 597 |   transform-origin: left center;
 598 | }
 599 | .poll > .body .questions .question > .items .item.result .range_bar .value {
 600 |   float: right;
 601 |   font-size: 0.75em;
 602 |   font-weight: bold;
 603 |   -webkit-transform: translateZ(0);
 604 |   transform: translateZ(0);
 605 |   vertical-align: bottom;
 606 |   color: #ffffff;
 607 | }
 608 | .poll > .body .questions .question > .items .item.result .range_bar .value .num_votes:before {
 609 |   content: " (";
 610 | }
 611 | .poll > .body .questions .question > .items .item.result .range_bar .value .num_votes:after {
 612 |   content: ")";
 613 | }
 614 | .poll > .body .questions .question > .items .item.result .range_bar .title {
 615 |   position: relative;
 616 |   font-size: 0.875em;
 617 |   display: block;
 618 |   overflow: hidden;
 619 |   padding-right: 0.5rem;
 620 |   color: #ffffff;
 621 |   font-weight: bold;
 622 | }
 623 | .poll > .body .questions .question > .items .item.result .range_bar:after {
 624 |   content: "";
 625 |   display: table;
 626 |   clear: both;
 627 | }
 628 | .poll > .body .votes_info {
 629 |   text-align: right;
 630 |   color: #98989F;
 631 |   padding: 0.5em 1em;
 632 | }
 633 | .poll > .body .votes_info span {
 634 |   font-size: 0.875em;
 635 | }
 636 | .poll > .body .poll_status {
 637 |   text-align: right;
 638 |   color: #b2b2b2;
 639 |   padding: 0 1em 0.5em;
 640 | }
 641 | .poll > .body .poll_status span {
 642 |   font-size: 0.875em;
 643 | }
 644 | .poll > .body .buttons {
 645 |   -webkit-touch-callout: none;
 646 |   -webkit-user-select: none;
 647 |   -khtml-user-select: none;
 648 |   -moz-user-select: none;
 649 |   -ms-user-select: none;
 650 |   user-select: none;
 651 |   display: flex;
 652 |   flex-wrap: wrap;
 653 |   gap: 0.25rem;
 654 |   justify-content: flex-end;
 655 |   align-items: center;
 656 |   position: sticky;
 657 |   bottom: 0;
 658 |   z-index: 2;
 659 |   padding: 0.25rem 0.5rem 0.5rem;
 660 |   background: #212121;
 661 |   border-top: 0.0625rem solid rgba(255, 255, 255, 0.12);
 662 | }
 663 | .poll > .body .buttons > * {
 664 |   float: none;
 665 |   display: inline-flex;
 666 |   align-items: center;
 667 |   justify-content: center;
 668 |   min-height: 2.5rem;
 669 |   box-sizing: border-box;
 670 |   padding: 0.625rem 0.75rem;
 671 |   text-align: center;
 672 |   color: #E0E0E0;
 673 |   background: rgba(255, 255, 255, 0.1);
 674 |   -webkit-border-radius: 0.875rem;
 675 |   border-radius: 0.875rem;
 676 | }
 677 | .poll > .body .buttons > * > span {
 678 |   display: inline;
 679 |   color: inherit;
 680 |   font-size: 0.875rem;
 681 |   line-height: 1.25rem;
 682 | }
 683 | .poll > .body .buttons .vote,
 684 | .poll > .body .buttons .show_results,
 685 | .poll > .body .buttons .show_poll {
 686 |   min-width: 6rem;
 687 | }
 688 | .poll > .body .buttons .vote {
 689 |   background: #E0E0E0;
 690 |   color: #212121;
 691 | }
 692 | .poll > .body .buttons:after {
 693 |   content: "";
 694 |   display: table;
 695 |   clear: both;
 696 | }
 697 | .poll.open > .title .icon {
 698 |   -webkit-transform: rotateX(180deg) translateZ(0);
 699 |   transform: rotateX(180deg) translateZ(0);
 700 | }
 701 | .poll.open:not(.once-opened) .range {
 702 |   -webkit-animation: pollRangeAnimation 0.375s cubic-bezier(0.4, 0, 0.2, 1);
 703 |   animation: pollRangeAnimation 0.375s cubic-bezier(0.4, 0, 0.2, 1);
 704 |   -webkit-animation-fill-mode: forwards;
 705 |   animation-fill-mode: forwards;
 706 | }
 707 | .post_body {
 708 |   padding: 1em;
 709 |   position: relative;
 710 |   z-index: 1;
 711 |   -webkit-hyphens: auto;
 712 |   -ms-hyphens: auto;
 713 |   hyphens: auto;
 714 |   word-wrap: break-word;
 715 |   -webkit-user-select: text;
 716 |   user-select: text;
 717 |   -webkit-touch-callout: default;
 718 | }
 719 | .post_body a.anchor {
 720 |   display: block;
 721 |   position: relative;
 722 |   height: 1.5rem;
 723 |   margin-top: 0.5rem;
 724 | }
 725 | .post_body a.anchor:before {
 726 |   content: "";
 727 |   position: absolute;
 728 |   left: 0;
 729 |   top: 0;
 730 |   height: 1.5rem;
 731 |   width: 1.5rem;
 732 |   background-image: url("../../res/dark/anchor.svg");
 733 |   background-position: center;
 734 |   background-repeat: no-repeat;
 735 |   background-size: 1.5rem 1.5rem;
 736 | }
 737 | .post_body a.anchor:after {
 738 |   content: "";
 739 |   position: absolute;
 740 |   top: 0.75rem;
 741 |   left: 2rem;
 742 |   right: 0.5rem;
 743 |   border-top: 0.0625rem dashed #b2b2b2;
 744 | }
 745 | .post_body a.anchor:active {
 746 |   background: rgba(255, 255, 255, 0.1);
 747 | }
 748 | .post_body .postcolor {
 749 |   overflow: auto!important;
 750 |   height: auto!important;
 751 |   margin: -1em;
 752 |   padding: 1em;
 753 | }
 754 | .post_body img {
 755 |   max-width: 100%;
 756 |   height: auto!important;
 757 | }
 758 | .post_body img.linked-image,
 759 | .post_body img.attach {
 760 |   display: inline-block;
 761 |   vertical-align: middle;
 762 |   max-height: 75vh;
 763 |   contain: paint;
 764 |   object-fit: contain;
 765 |   background-color: rgba(3, 169, 244, 0.1);
 766 |   border-radius: 0.25rem;
 767 |   box-sizing: border-box;
 768 | }
 769 | .post_body img.theme-media-has-ratio {
 770 |   aspect-ratio: auto;
 771 | }
 772 | .post_body img.theme-media-pending {
 773 |   min-width: 3rem;
 774 |   min-height: 3rem;
 775 |   opacity: 1;
 776 | }
 777 | .post_body img.theme-media-loaded {
 778 |   opacity: 1;
 779 |   -webkit-transition: opacity 120ms cubic-bezier(0, 0, 0.2, 1);
 780 |   transition: opacity 120ms cubic-bezier(0, 0, 0.2, 1);
 781 | }
 782 | .post_body .edit {
 783 |   display: block;
 784 |   text-align: inherit;
 785 |   padding: 0;
 786 |   margin-left: 0;
 787 |   vertical-align: baseline;
 788 |   font-size: 0.875em;
 789 |   line-height: 1.35;
 790 |   white-space: normal;
 791 |   overflow-wrap: anywhere;
 792 |   word-break: break-word;
 793 |   max-width: 100%;
 794 |   color: #b2b2b2;
 795 | }
 796 | .post_body strong .edit {
 797 |   text-align: left;
 798 | }
 799 | .post_body .post-edit-reason {
 800 |   font-size: 0.875em;
 801 |   text-align: right;
 802 |   border-top: none;
 803 |   padding: 0 0.571em 0 0.571em;
 804 |   margin-top: -2px;
 805 |   color: #b2b2b2;
 806 | }
 807 | .post_body .ipb-attach.attach-file {
 808 |   position: relative;
 809 | }
 810 | .post_body .ipb-attach.attach-file img {
 811 |   display: none;
 812 | }
 813 | .post_body .ipb-attach.attach-file:before {
 814 |   content: "";
 815 |   display: inline-block;
 816 |   height: 1.25em;
 817 |   width: 1.25em;
 818 |   vertical-align: text-bottom;
 819 |   /*background-image: url(../../res/file.svg);
 820 |             background-position: center;
 821 |             background-repeat: no-repeat;
 822 |             background-size: 20/16em 20/16em;
 823 |             
 824 |             -webkit-filter: blur(1px);
 825 |             filter: blur(1px);*/
 826 |   background: url(../../res/file-outline.svg) no-repeat center center;
 827 | }
 828 | @-webkit-keyframes highlight {
 829 |   0% {
 830 |     opacity: 0;
 831 |   }
 832 |   20% {
 833 |     opacity: 0.15;
 834 |   }
 835 |   100% {
 836 |     opacity: 0;
 837 |   }
 838 | }
 839 | @keyframes highlight {
 840 |   0% {
 841 |     opacity: 0;
 842 |   }
 843 |   20% {
 844 |     opacity: 0.15;
 845 |   }
 846 |   100% {
 847 |     opacity: 0;
 848 |   }
 849 | }
 850 | body.circle_avatar .post_header .avatar .img {
 851 |   -webkit-border-radius: 3.5em;
 852 |   border-radius: 3.5em;
 853 | }
 854 | body.circle_avatar .post_header .avatar > .letter {
 855 |   -webkit-border-radius: 3.5em;
 856 |   border-radius: 3.5em;
 857 | }
 858 | body.square_avatar .post-block.quote > .block-title .avatar {
 859 |   -webkit-border-radius: 0px;
 860 |   border-radius: 0px;
 861 | }
 862 | body.square_avatar .post-block.quote > .block-title .avatar .image {
 863 |   -webkit-border-radius: 0px;
 864 |   border-radius: 0px;
 865 | }
 866 | body.hide_avatar .post_header .avatar .img {
 867 |   display: none;
 868 | }
 869 | body.hide_avatar .post_header .avatar .letter {
 870 |   display: block;
 871 | }
 872 | body.hide_avatar .post-block.quote > .block-title .avatar .image {
 873 |   display: none;
 874 | }
 875 | .post_container {
 876 |   background: #1E1E1E;
 877 |   margin: 0.5em 0;
 878 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
 879 |   position: relative;
 880 |   -webkit-border-radius: 0.875rem;
 881 |   border-radius: 0.875rem;
 882 |   overflow: hidden;
 883 | }
 884 | .post_container.blacklisted_post {
 885 |   box-shadow: none;
 886 |   margin-top: var(--topic-post-spacing, 0.5em);
 887 |   margin-bottom: var(--topic-post-spacing, 0.5em);
 888 |   min-height: 0;
 889 |   padding: 0;
 890 | }
 891 | .post_container.blacklisted_post.revealed {
 892 |   background: #1E1E1E;
 893 |   margin: 0.5em 0;
 894 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
 895 |   position: relative;
 896 |   -webkit-border-radius: 0.875rem;
 897 |   border-radius: 0.875rem;
 898 |   overflow: hidden;
 899 |   min-height: auto;
 900 |   padding: 0;
 901 | }
 902 | .post_container.blacklisted_post .blacklisted_post_placeholder {
 903 |   display: -webkit-box;
 904 |   display: -webkit-flex;
 905 |   display: flex;
 906 |   -webkit-box-pack: center;
 907 |   -webkit-justify-content: center;
 908 |   justify-content: center;
 909 |   -webkit-box-align: center;
 910 |   -webkit-align-items: center;
 911 |   align-items: center;
 912 |   box-sizing: border-box;
 913 |   width: 100%;
 914 |   padding: 0.25rem 0.5rem;
 915 |   margin: 0.25rem 0;
 916 |   border: 0;
 917 |   background: transparent;
 918 |   font: inherit;
 919 |   font-size: 0.75rem;
 920 |   line-height: 1.25;
 921 |   color: #b2b2b2;
 922 |   opacity: 0.78;
 923 |   text-decoration: none;
 924 |   text-align: center;
 925 |   user-select: none;
 926 |   cursor: pointer;
 927 |   -webkit-tap-highlight-color: transparent;
 928 | }
 929 | .post_container.blacklisted_post .blacklisted_post_placeholder .blacklisted_post_placeholder_text {
 930 |   color: inherit !important;
 931 |   text-decoration: none !important;
 932 | }
 933 | .post_container.blacklisted_post .blacklisted_post_placeholder.aec,
 934 | .post_container.blacklisted_post .blacklisted_post_placeholder:link,
 935 | .post_container.blacklisted_post .blacklisted_post_placeholder:visited,
 936 | .post_container.blacklisted_post .blacklisted_post_placeholder:hover,
 937 | .post_container.blacklisted_post .blacklisted_post_placeholder:active,
 938 | .post_container.blacklisted_post .blacklisted_post_placeholder:focus {
 939 |   color: #b2b2b2 !important;
 940 |   text-decoration: none !important;
 941 |   background: transparent;
 942 |   outline: 0;
 943 | }
 944 | .post_container.blacklisted_post .blacklisted_post_placeholder[hidden] {
 945 |   display: none !important;
 946 | }
 947 | .post_container.blacklisted_post .blacklisted_post_content[hidden] {
 948 |   display: none !important;
 949 | }
 950 | .post_container.blacklisted_post.revealed .blacklisted_post_content {
 951 |   display: block;
 952 | }
 953 | .post_container .hat_button {
 954 |   -webkit-touch-callout: none;
 955 |   -webkit-user-select: none;
 956 |   -khtml-user-select: none;
 957 |   -moz-user-select: none;
 958 |   -ms-user-select: none;
 959 |   user-select: none;
 960 |   width: 100%;
 961 |   color: #E0E0E0;
 962 |   position: relative;
 963 |   text-align: center;
 964 | }
 965 | .post_container .hat_button .icon {
 966 |   height: 3em;
 967 |   width: 2.5em;
 968 |   float: right;
 969 |   margin: -0.875em -1em;
 970 |   position: relative;
 971 |   -webkit-transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 972 |   transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 973 |   -webkit-transform-origin: center;
 974 |   transform-origin: center;
 975 | }
 976 | .post_container .hat_button .icon:after {
 977 |   content: "";
 978 |   position: absolute;
 979 |   left: 50%;
 980 |   top: 50%;
 981 |   margin-left: -0.25em;
 982 |   margin-top: -0.25em;
 983 |   border-left: 0.125rem solid rgba(255, 255, 255, 0.5);
 984 |   border-bottom: 0.125rem solid rgba(255, 255, 255, 0.5);
 985 |   box-sizing: border-box;
 986 |   height: 0.5em;
 987 |   width: 0.5em;
 988 |   border-color: #E0E0E0;
 989 |   -webkit-transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 990 |   transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 991 |   -webkit-transform-origin: center;
 992 |   transform-origin: center;
 993 | }
 994 | .post_container .hat_button .icon:before {
 995 |   content: "";
 996 |   position: absolute;
 997 |   left: 50%;
 998 |   top: 50%;
 999 |   margin-left: -1em;
1000 |   margin-top: -1em;
1001 |   height: 2em;
1002 |   width: 2em;
1003 |   background: #ffffff;
1004 |   -webkit-border-radius: 100%;
1005 |   border-radius: 100%;
1006 |   -webkit-transform: scale(0.5) translateZ(0);
1007 |   transform: scale(0.5) translateZ(0);
1008 |   opacity: 0;
1009 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1010 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1011 | }
1012 | .post_container .hat_button:active .icon:before {
1013 |   opacity: 0.05;
1014 |   -webkit-transform: scale(1) translateZ(0);
1015 |   transform: scale(1) translateZ(0);
1016 | }
1017 | .post_container .hat_button + .hat_content {
1018 |   padding-top: 0.5em;
1019 | }
1020 | .post_container.open .hat_button .icon {
1021 |   -webkit-transform: rotateX(180deg) translateZ(0);
1022 |   transform: rotateX(180deg) translateZ(0);
1023 | }
1024 | .post_container.online .post_header .inf.nick .online_dot {
1025 |   display: inline-block;
1026 | }
1027 | .post_container .post_title {
1028 |   text-align: center;
1029 |   text-decoration: underline;
1030 |   color: red;
1031 |   padding: 1rem;
1032 |   padding-bottom: 0;
1033 | }
1034 | .post_container .post_title a {
1035 |   font-weight: bold;
1036 |   display: block;
1037 |   padding-bottom: 1rem;
1038 |   border-bottom: 0.0625rem solid rgba(255, 255, 255, 0.05);
1039 | }
1040 | .post_container .post_header {
1041 |   padding: 0.75em 1em 0 1em;
1042 | }
1043 | .post_container .post_header .header_wrapper {
1044 |   position: relative;
1045 |   --post-header-action-right: -1rem;
1046 |   --post-header-action-width: 2.5rem;
1047 |   --post-header-inline-end-reserve: 2.5rem;
1048 |   --post-header-menu-axis-overhang: 2.25rem;
1049 |   min-height: 3.5em;
1050 |   display: flex;
1051 |   flex-direction: column;
1052 |   justify-content: center;
1053 |   padding-left: 4.5em;
1054 |   padding-right: var(--post-header-inline-end-reserve);
1055 |   box-sizing: border-box;
1056 | }
1057 | .post_container .post_header .avatar {
1058 |   -webkit-touch-callout: none;
1059 |   -webkit-user-select: none;
1060 |   -khtml-user-select: none;
1061 |   -moz-user-select: none;
1062 |   -ms-user-select: none;
1063 |   user-select: none;
1064 |   width: 3.5em;
1065 |   height: 3.5em;
1066 |   position: absolute;
1067 |   left: 0;
1068 |   top: 0;
1069 | }
1070 | .post_container .post_header .avatar .img {
1071 |   position: relative;
1072 |   height: 3.5em;
1073 |   width: 3.5em;
1074 |   background-repeat: no-repeat;
1075 |   background-size: cover;
1076 |   background-position: center;
1077 | }
1078 | .post_container .post_header .avatar.none_avatar > .letter {
1079 |   display: block;
1080 | }
1081 | .post_container .post_header .avatar > .letter {
1082 |   color: #E0E0E0;
1083 |   position: absolute;
1084 |   text-align: center;
1085 |   width: 1.75em;
1086 |   height: 1.75em;
1087 |   line-height: 1.75em;
1088 |   text-transform: uppercase;
1089 |   font-size: 2em;
1090 |   font-weight: bold;
1091 |   top: 0;
1092 |   left: 0;
1093 |   background-color: #595959;
1094 |   display: none;
1095 | }
1096 | .post_container .post_header .avatar .reputation {
1097 |   position: absolute;
1098 |   background: #4d4d4d;
1099 |   z-index: 1;
1100 |   height: 1.125em;
1101 |   line-height: 1.125em;
1102 |   min-width: 1.125em;
1103 |   text-align: center;
1104 |   vertical-align: text-top;
1105 |   padding: 0 0.375em;
1106 |   -webkit-border-radius: 1.25em;
1107 |   border-radius: 1.25em;
1108 |   right: -0.25em;
1109 |   bottom: 0.25em;
1110 |   right: 0;
1111 |   bottom: 0;
1112 |   box-shadow: 0 0 0 0.125em #212121;
1113 | }
1114 | .post_container .post_header .avatar .reputation > span {
1115 |   color: #E0E0E0;
1116 |   font-size: 0.625em;
1117 |   font-weight: bold;
1118 |   vertical-align: middle;
1119 |   /* &:before {
1120 |                         content: "Реп (";
1121 |                     }
1122 |                     &:after {
1123 |                         content: ")";
1124 |                     }*/
1125 | }
1126 | .post_container .post_header .avatar .reputation:after {
1127 |   content: "";
1128 |   position: absolute;
1129 |   height: 2.25rem;
1130 |   min-width: 2.25rem;
1131 |   top: 0;
1132 |   left: 0;
1133 |   right: 0;
1134 | }
1135 | .post_container .post_header .avatar.disable .img {
1136 |   width: 0;
1137 | }
1138 | .post_container .post_header .avatar.disable ~ .inf {
1139 |   left: auto;
1140 | }
1141 | .post_container .post_header .avatar.circle .img {
1142 |   -webkit-border-radius: 4em;
1143 |   border-radius: 4em;
1144 | }
1145 | .post_container .post_header .inf {
1146 |   position: relative;
1147 |   left: auto;
1148 |   max-width: 100%;
1149 |   margin-top: 0;
1150 | }
1151 | .post_container .post_header .inf.nick {
1152 |   display: flex;
1153 |   align-items: center;
1154 |   min-width: 0;
1155 |   top: auto;
1156 |   padding-top: 0;
1157 |   padding-right: 0.25rem;
1158 |   color: #E0E0E0;
1159 |   font-weight: bold;
1160 |   overflow: hidden;
1161 |   white-space: nowrap;
1162 | }
1163 | .post_container .post_header .inf.nick > .aec {
1164 |   display: block;
1165 |   min-width: 0;
1166 |   overflow: hidden;
1167 |   text-overflow: ellipsis;
1168 |   white-space: nowrap;
1169 | }
1170 | .post_container .post_header .inf.nick .online_dot {
1171 |   position: relative;
1172 |   flex: 0 0 auto;
1173 |   margin-left: 0.375em;
1174 |   height: 0.5em;
1175 |   width: 0.5em;
1176 |   background-color: #12b557;
1177 |   display: none;
1178 |   -webkit-border-radius: 100%;
1179 |   border-radius: 100%;
1180 | }
1181 | .post_container .post_header .inf.nick .online_dot:after {
1182 |   content: "";
1183 |   position: absolute;
1184 |   height: 2.25em;
1185 |   width: 2.25em;
1186 |   left: 0;
1187 |   top: -0.875em;
1188 | }
1189 | .post_container .post_header .inf.nick.online .online_dot {
1190 |   display: inline-block;
1191 | }
1192 | .post_container .post_header .inf.post_meta {
1193 |   display: flex;
1194 |   align-items: baseline;
1195 |   gap: 0.5rem;
1196 |   position: static;
1197 |   min-width: 0;
1198 |   min-height: 1.125rem;
1199 |   top: auto;
1200 |   margin-top: 0.25rem;
1201 |   width: calc(100% + var(--post-header-menu-axis-overhang));
1202 |   max-width: none;
1203 |   margin-right: 0;
1204 |   padding-right: 0;
1205 |   color: #b2b2b2;
1206 |   font-weight: normal;
1207 |   line-height: 1.25;
1208 | }
1209 | .post_container .post_header .inf.post_meta .group_text {
1210 |   display: block;
1211 |   flex: 1 1 auto;
1212 |   margin-right: 0;
1213 |   min-width: 0;
1214 |   overflow: hidden;
1215 |   text-overflow: ellipsis;
1216 |   white-space: nowrap;
1217 | }
1218 | .post_container .post_header .inf.post_meta .group_text > span {
1219 |   font-size: 0.875rem;
1220 | }
1221 | .post_container .post_header .inf.post_meta .date {
1222 |   flex: 0 0 auto;
1223 |   margin-left: auto;
1224 |   min-width: 0;
1225 |   max-width: 9rem;
1226 |   overflow: hidden;
1227 |   text-overflow: ellipsis;
1228 |   white-space: nowrap;
1229 |   color: #b2b2b2;
1230 |   text-align: right;
1231 | }
1232 | .post_container .post_header .inf.post_meta .date > span {
1233 |   font-size: 0.875rem;
1234 | }
1235 | .post_container .post_header .inf.user_post_count {
1236 |   display: flex;
1237 |   align-items: center;
1238 |   gap: 0.25rem;
1239 |   width: max-content;
1240 |   max-width: 100%;
1241 |   min-width: 0;
1242 |   margin-top: 0.1875rem;
1243 |   color: #b2b2b2;
1244 |   opacity: 0.78;
1245 |   line-height: 1.2;
1246 |   font-weight: normal;
1247 | }
1248 | .post_container .post_header .inf.user_post_count .user_post_count_icon {
1249 |   flex: 0 0 auto;
1250 |   width: 0.75rem;
1251 |   height: 0.75rem;
1252 |   color: currentColor;
1253 |   opacity: 0.78;
1254 | }
1255 | .post_container .post_header .inf.user_post_count > span {
1256 |   display: block;
1257 |   min-width: 0;
1258 |   overflow: hidden;
1259 |   text-overflow: ellipsis;
1260 |   white-space: nowrap;
1261 |   font-size: 0.75rem;
1262 | }
1263 | .post_container .post_header .inf.user_post_count.user_post_count_placeholder {
1264 |   min-width: 3rem;
1265 |   color: transparent !important;
1266 |   opacity: 0 !important;
1267 |   pointer-events: none;
1268 | }
1269 | .post_container .post_header .inf.post_meta + .user_post_count {
1270 |   display: -webkit-box !important;
1271 |   display: -webkit-flex !important;
1272 |   display: flex !important;
1273 |   visibility: visible !important;
1274 |   height: auto !important;
1275 |   min-height: 0.875rem !important;
1276 |   overflow: visible !important;
1277 | }
1278 | .post_container .post_header .inf.menu {
1279 |   -webkit-touch-callout: none;
1280 |   -webkit-user-select: none;
1281 |   -khtml-user-select: none;
1282 |   -moz-user-select: none;
1283 |   -ms-user-select: none;
1284 |   user-select: none;
1285 |   left: auto;
1286 |   position: absolute;
1287 |   right: -1em;
1288 |   right: var(--post-header-action-right);
1289 |   top: -0.75em;
1290 |   height: 3em;
1291 |   width: var(--post-header-action-width);
1292 |   background-image: url("../../res/dark/dots-vertical.svg");
1293 |   background-repeat: no-repeat;
1294 |   background-size: 1.5rem 1.5rem;
1295 |   background-position: center;
1296 |   margin: 0;
1297 | }
1298 | .post_container .post_header .inf.menu > span {
1299 |   display: none;
1300 | }
1301 | .post_container .post_header .inf.menu:before {
1302 |   content: "";
1303 |   position: absolute;
1304 |   left: 50%;
1305 |   top: 50%;
1306 |   margin-left: -1em;
1307 |   margin-top: -1em;
1308 |   height: 2em;
1309 |   width: 2em;
1310 |   background: #ffffff;
1311 |   -webkit-border-radius: 100%;
1312 |   border-radius: 100%;
1313 |   -webkit-transform: scale(0.5) translateZ(0);
1314 |   transform: scale(0.5) translateZ(0);
1315 |   opacity: 0;
1316 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1317 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1318 | }
1319 | .post_container .post_header .inf.menu:active {
1320 |   background-color: transparent;
1321 | }
1322 | .post_container .post_header .inf.menu:active:before {
1323 |   opacity: 0.05;
1324 |   -webkit-transform: scale(1) translateZ(0);
1325 |   transform: scale(1) translateZ(0);
1326 | }
1327 | .post_container .post_header .inf.date {
1328 |   top: 1.25em;
1329 |   color: #b2b2b2;
1330 | }
1331 | .post_container .post_header .inf.date > span {
1332 |   font-size: 0.875em;
1333 | }
1334 | .post_container .post_header .inf.number {
1335 |   display: none;
1336 | }
1337 | .post_container .post_header .inf.number > span {
1338 |   font-size: 0.875em;
1339 | }
1340 | .post_container .post_footer {
1341 |   -webkit-touch-callout: none;
1342 |   -webkit-user-select: none;
1343 |   -khtml-user-select: none;
1344 |   -moz-user-select: none;
1345 |   -ms-user-select: none;
1346 |   user-select: none;
1347 |   padding: 0 1em 0.75em 1em;
1348 | }
1349 | .post_container .post_footer .btn {
1350 |   color: rgba(255, 255, 255, 0.92);
1351 | }
1352 | .post_container .post_footer .post_rating_row {
1353 |   display: -webkit-box;
1354 |   display: -webkit-flex;
1355 |   display: flex;
1356 |   -webkit-box-pack: start;
1357 |   -webkit-justify-content: flex-start;
1358 |   justify-content: flex-start;
1359 |   margin-top: 0.375em;
1360 |   color: rgba(255, 255, 255, 0.68);
1361 |   font-size: 0.8125rem;
1362 | }
1363 | .post_container .post_footer .post_rating {
1364 |   display: -webkit-inline-box;
1365 |   display: -webkit-inline-flex;
1366 |   display: inline-flex;
1367 |   -webkit-box-align: center;
1368 |   -webkit-align-items: center;
1369 |   align-items: center;
1370 |   -webkit-box-pack: center;
1371 |   -webkit-justify-content: center;
1372 |   justify-content: center;
1373 |   min-width: 1.25em;
1374 |   height: 2.5em;
1375 |   padding: 0;
1376 |   border: 0;
1377 |   -webkit-border-radius: 0;
1378 |   border-radius: 0;
1379 |   background: transparent;
1380 |   box-shadow: none;
1381 |   outline: 0;
1382 |   color: rgba(255, 255, 255, 0.92);
1383 |   font-size: 0.95rem;
1384 |   font-weight: 700;
1385 |   line-height: 1;
1386 | }
1387 | .post_container .post_footer .post_rating.post_rating_hidden {
1388 |   display: none !important;
1389 | }
1390 | .post_container .post_footer .post_actions_row {
1391 |   display: -webkit-box;
1392 |   display: -webkit-flex;
1393 |   display: flex;
1394 |   -webkit-box-align: center;
1395 |   -webkit-align-items: center;
1396 |   align-items: center;
1397 |   gap: var(--post-action-gap, 0.375rem);
1398 |   -webkit-flex-wrap: nowrap;
1399 |   flex-wrap: nowrap;
1400 |   width: 100%;
1401 |   box-sizing: border-box;
1402 |   --post-action-button-size: 3rem;
1403 |   --post-action-icon-size: 1.8rem;
1404 |   --post-rep-action-icon-size: var(--post-action-icon-size);
1405 |   --post-action-stroke-width: 1.85;
1406 |   --post-action-light-stroke-width: 1.2;
1407 |   --post-action-radius: 0;
1408 |   --topic-action-icon-color: #E0E0E0;
1409 |   --post-action-icon-color: var(--topic-action-icon-color);
1410 |   --topic-action-icon-active-color: var(--topic-action-icon-color);
1411 |   --post-action-icon-active-color: var(--topic-action-icon-active-color);
1412 | }
1413 | .post_container .post_footer .post_actions_row .btn.rep_up,
1414 | .post_container .post_footer .post_actions_row .btn.rep_down,
1415 | .post_container .post_footer .post_actions_row .btn.reply,
1416 | .post_container .post_footer .post_actions_row .btn.quote {
1417 |   display: -webkit-inline-box;
1418 |   display: -webkit-inline-flex;
1419 |   display: inline-flex;
1420 |   -webkit-box-align: center;
1421 |   -webkit-align-items: center;
1422 |   align-items: center;
1423 |   -webkit-box-pack: center;
1424 |   -webkit-justify-content: center;
1425 |   justify-content: center;
1426 |   box-sizing: border-box;
1427 |   margin: 0;
1428 |   padding: 0;
1429 |   width: var(--post-action-button-size);
1430 |   height: var(--post-action-button-size);
1431 |   min-width: var(--post-action-button-size);
1432 |   min-height: var(--post-action-button-size);
1433 |   border: 0;
1434 |   background: transparent;
1435 |   background-color: transparent;
1436 |   background-image: none;
1437 |   border-color: transparent;
1438 |   box-shadow: none;
1439 |   outline: 0;
1440 |   -webkit-filter: none;
1441 |   filter: none;
1442 |   -webkit-tap-highlight-color: transparent;
1443 |   color: var(--post-action-icon-color);
1444 |   -webkit-border-radius: var(--post-action-radius);
1445 |   border-radius: var(--post-action-radius);
1446 | }
1447 | .post_container .post_footer .post_actions_row .btn.rep_up,
1448 | .post_container .post_footer .post_actions_row .btn.rep_down {
1449 |   overflow: visible;
1450 |   color: var(--post-action-icon-color);
1451 | }
1452 | .post_container .post_footer .post_actions_row .btn.reply:focus,
1453 | .post_container .post_footer .post_actions_row .btn.quote:focus,
1454 | .post_container .post_footer .post_actions_row .btn.rep_up:focus,
1455 | .post_container .post_footer .post_actions_row .btn.rep_down:focus,
1456 | .post_container .post_footer .post_actions_row .btn.reply:visited,
1457 | .post_container .post_footer .post_actions_row .btn.quote:visited,
1458 | .post_container .post_footer .post_actions_row .btn.rep_up:visited,
1459 | .post_container .post_footer .post_actions_row .btn.rep_down:visited,
1460 | .post_container .post_footer .post_actions_row .btn.reply:hover,
1461 | .post_container .post_footer .post_actions_row .btn.quote:hover,
1462 | .post_container .post_footer .post_actions_row .btn.rep_up:hover,
1463 | .post_container .post_footer .post_actions_row .btn.rep_down:hover {
1464 |   background: transparent;
1465 |   background-color: transparent;
1466 |   background-image: none;
1467 |   border: 0;
1468 |   border-color: transparent;
1469 |   box-shadow: none;
1470 |   outline: 0;
1471 |   -webkit-filter: none;
1472 |   filter: none;
1473 |   color: var(--post-action-icon-color);
1474 | }
1475 | .post_container .post_footer .post_actions_row .btn.reply {
1476 |   margin-left: auto;
1477 | }
1478 | .post_container .post_footer .post_actions_row .btn.quote:first-child,
1479 | .post_container .post_footer .post_actions_row .btn.rep_up + .btn.quote,
1480 | .post_container .post_footer .post_actions_row .post_rating + .btn.quote,
1481 | .post_container .post_footer .post_actions_row .btn.rep_down + .btn.quote {
1482 |   margin-left: auto;
1483 | }
1484 | .post_container .post_footer .post_actions_row .btn.reply:active,
1485 | .post_container .post_footer .post_actions_row .btn.quote:active,
1486 | .post_container .post_footer .post_actions_row .btn.rep_up:active,
1487 | .post_container .post_footer .post_actions_row .btn.rep_down:active {
1488 |   background: transparent;
1489 |   background-color: transparent;
1490 |   background-image: none;
1491 |   border: 0;
1492 |   border-color: transparent;
1493 |   box-shadow: none;
1494 |   outline: 0;
1495 |   -webkit-filter: none;
1496 |   filter: none;
1497 | }
1498 | .post_container .post_footer .post_actions_row .btn.rep_up > span,
1499 | .post_container .post_footer .post_actions_row .btn.rep_down > span,
1500 | .post_container .post_footer .post_actions_row .btn.reply > span,
1501 | .post_container .post_footer .post_actions_row .btn.quote > span {
1502 |   display: none;
1503 | }
1504 | .post_container .post_footer .post_actions_row .btn > .post-action-icon {
1505 |   display: block;
1506 |   flex: 0 0 auto;
1507 |   color: var(--post-action-icon-color);
1508 |   fill: none;
1509 |   stroke: currentColor;
1510 |   width: var(--post-action-icon-size);
1511 |   height: var(--post-action-icon-size);
1512 |   overflow: hidden;
1513 |   background: transparent;
1514 |   background-color: transparent;
1515 |   background-image: none;
1516 |   box-shadow: none;
1517 |   outline: 0;
1518 |   -webkit-filter: none;
1519 |   filter: none;
1520 |   opacity: 1;
1521 |   mix-blend-mode: normal;
1522 | }
1523 | .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
1524 | .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
1525 |   fill: none;
1526 |   stroke: currentColor;
1527 |   stroke-width: var(--post-action-stroke-width);
1528 |   stroke-linecap: round;
1529 |   stroke-linejoin: round;
1530 |   stroke-opacity: 1;
1531 |   vector-effect: non-scaling-stroke;
1532 | }
1533 | .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
1534 | .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
1535 | .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
1536 | .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
1537 |   stroke-width: var(--post-action-light-stroke-width);
1538 |   stroke-opacity: 1;
1539 | }
1540 | .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
1541 | .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon {
1542 |   display: block;
1543 |   flex: 0 0 auto;
1544 |   color: var(--post-action-icon-color);
1545 |   fill: currentColor;
1546 |   stroke: none;
1547 |   width: var(--post-rep-action-icon-size);
1548 |   height: var(--post-rep-action-icon-size);
1549 |   overflow: visible;
1550 |   background: transparent;
1551 |   background-color: transparent;
1552 |   background-image: none;
1553 |   box-shadow: none;
1554 |   outline: 0;
1555 |   -webkit-filter: none;
1556 |   filter: none;
1557 |   opacity: 1;
1558 |   mix-blend-mode: normal;
1559 | }
1560 | .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon *,
1561 | .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon * {
1562 |   fill-opacity: 1;
1563 | }
1564 | .post_container .post_footer .btn.vote {
1565 |   float: left;
1566 | }
1567 | .post_container .post_footer .btn.edit {
1568 |   float: right;
1569 |   margin-right: 0;
1570 |   clear: both;
1571 | }
1572 | .post_container .post_footer:after {
1573 |   content: "";
1574 |   display: table;
1575 |   clear: both;
1576 | }
1577 | .post_container:before {
1578 |   content: "";
1579 |   position: absolute;
1580 |   left: 0;
1581 |   top: 0;
1582 |   height: 100%;
1583 |   width: 100%;
1584 |   background: rgba(var(--fpda-unread-bg-rgb, 255 255 255), 0.25);
1585 |   z-index: 10;
1586 |   opacity: 0;
1587 |   pointer-events: none;
1588 | }
1589 | .post_container.active:before {
1590 |   -webkit-animation: highlight 1s;
1591 |   animation: highlight 1s;
1592 | }
1593 | .post_container:after {
1594 |   content: "";
1595 |   display: table;
1596 |   clear: both;
1597 | }
1598 | .topic_hat_entry.top_hat_entry {
1599 |   margin-top: 0;
1600 |   margin-bottom: 0;
1601 | }
1602 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry) {
1603 |   border-bottom-left-radius: 0;
1604 |   border-bottom-right-radius: 0;
1605 | }
1606 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry) .inline_hat_button {
1607 |   border-bottom: none;
1608 | }
1609 | .topic_hat_entry.top_hat_entry + .poll.poll_entry {
1610 |   margin-top: 0;
1611 |   border-top-left-radius: 0;
1612 |   border-top-right-radius: 0;
1613 | }
1614 | .topic_hat_entry.top_hat_entry + .poll.poll_entry > .title {
1615 |   border-top: 0.0625rem solid rgba(255, 255, 255, 0.05);
1616 | }
1617 | .topic_hat_entry.top_hat_entry .inline_hat_button {
1618 |   border-bottom: 0.0625rem solid rgba(255, 255, 255, 0.05);
1619 | }
1620 | .topic_hat_entry.top_hat_entry.close .inline_hat_content {
1621 |   display: none;
1622 | }
1623 | .topic_hat_entry.top_hat_entry.open .inline_hat_button {
1624 |   border-bottom: 0.0625rem solid rgba(255, 255, 255, 0.05);
1625 | }
1626 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry).open .inline_hat_button,
1627 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry).close .inline_hat_button {
1628 |   border-bottom: none;
1629 | }
1630 | .topic_hat_entry.top_hat_entry .inline_hat_content {
1631 |   padding-top: 0;
1632 | }
1633 | body#topic .post_container.topic_hat_fixed > .hat_button,
1634 | body#topic .post_container.topic_hat_entry > .hat_button,
1635 | body#topic .topic_hat_entry > .inline_hat_button,
1636 | body#topic .poll > .title,
1637 | body#topic .poll.poll_entry > .title {
1638 |   display: flex;
1639 |   align-items: center;
1640 |   justify-content: space-between;
1641 |   gap: 0.5rem;
1642 |   box-sizing: border-box;
1643 |   min-height: var(--topic-collapsible-header-min-height);
1644 |   color: var(--topic-collapsible-header-color);
1645 |   line-height: 1.125rem;
1646 |   padding: var(--topic-collapsible-header-padding-y) 0.5rem var(--topic-collapsible-header-padding-y) 1em;
1647 |   font-size: 0.8125rem;
1648 |   font-weight: 600;
1649 |   letter-spacing: 0.01em;
1650 |   text-transform: uppercase;
1651 |   text-align: left;
1652 | }
1653 | body#topic .post_container.topic_hat_fixed > .hat_button > span,
1654 | body#topic .post_container.topic_hat_entry > .hat_button > span,
1655 | body#topic .topic_hat_entry > .inline_hat_button > span,
1656 | body#topic .poll > .title > span,
1657 | body#topic .poll.poll_entry > .title > span {
1658 |   flex: 1 1 auto;
1659 |   min-width: 0;
1660 |   overflow: hidden;
1661 |   text-overflow: ellipsis;
1662 |   white-space: nowrap;
1663 |   color: inherit;
1664 |   line-height: 1.125rem;
1665 |   font-size: inherit;
1666 |   font-weight: inherit;
1667 |   letter-spacing: inherit;
1668 |   text-transform: inherit;
1669 | }
1670 | body#topic .post_container.topic_hat_fixed > .hat_button > .icon,
1671 | body#topic .post_container.topic_hat_entry > .hat_button > .icon,
1672 | body#topic .topic_hat_entry > .inline_hat_button > .icon,
1673 | body#topic .poll > .title > .icon,
1674 | body#topic .poll.poll_entry > .title > .icon {
1675 |   display: flex;
1676 |   align-items: center;
1677 |   justify-content: center;
1678 |   flex: 0 0 var(--topic-collapsible-header-icon-size);
1679 |   float: none;
1680 |   width: var(--topic-collapsible-header-icon-size);
1681 |   height: var(--topic-collapsible-header-icon-size);
1682 |   margin: 0 0 0 0.5rem;
1683 |   position: relative;
1684 |   right: auto;
1685 |   top: auto;
1686 |   color: var(--topic-collapsible-header-icon-color);
1687 | }
1688 | body#topic .post_container.topic_hat_fixed > .hat_button > .icon:after,
1689 | body#topic .post_container.topic_hat_entry > .hat_button > .icon:after,
1690 | body#topic .topic_hat_entry > .inline_hat_button > .icon:after,
1691 | body#topic .poll > .title > .icon:after,
1692 | body#topic .poll.poll_entry > .title > .icon:after {
1693 |   border-color: currentColor;
1694 | }
1695 | body#topic .post_container.topic_hat_fixed > .hat_button > .icon:before,
1696 | body#topic .post_container.topic_hat_entry > .hat_button > .icon:before,
1697 | body#topic .topic_hat_entry > .inline_hat_button > .icon:before,
1698 | body#topic .poll > .title > .icon:before,
1699 | body#topic .poll.poll_entry > .title > .icon:before {
1700 |   background: currentColor;
1701 | }
1702 | body#topic {
1703 |   --topic-collapsible-header-color: #ffffff;
1704 |   --topic-collapsible-header-icon-color: var(--topic-collapsible-header-color);
1705 |   --topic-collapsible-header-min-height: 2rem;
1706 |   --topic-collapsible-header-padding-y: 0.3125rem;
1707 |   --topic-collapsible-header-icon-size: 1.5rem;
1708 |   --topic-body-font-size: 1em;
1709 |   --topic-body-line-height: 1.48;
1710 |   --topic-quote-font-size: 1em;
1711 |   --topic-code-font-size: 0.75rem;
1712 |   --topic-code-line-height: 1.375rem;
1713 |   --topic-paragraph-spacing: 0.72em;
1714 |   --topic-inline-block-spacing: 0.625rem;
1715 |   --topic-edit-info-spacing: 0.375rem;
1716 |   --topic-post-spacing: 0.5em;
1717 |   --topic-quote-spacing: 0.75rem;
1718 | }
1719 | body#topic .post_container {
1720 |   margin-top: var(--topic-post-spacing);
1721 |   margin-bottom: var(--topic-post-spacing);
1722 | }
1723 | body#topic .post_container.blacklisted_post {
1724 |   margin-top: var(--topic-post-spacing);
1725 |   margin-bottom: var(--topic-post-spacing);
1726 |   box-shadow: none;
1727 |   min-height: 0;
1728 |   padding: 0;
1729 | }
1730 | body#topic .post_container.blacklisted_post.revealed {
1731 |   background: #1E1E1E;
1732 |   margin-top: var(--topic-post-spacing);
1733 |   margin-bottom: var(--topic-post-spacing);
1734 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
1735 |   position: relative;
1736 |   -webkit-border-radius: 0.875rem;
1737 |   border-radius: 0.875rem;
1738 |   overflow: hidden;
1739 |   min-height: auto;
1740 |   padding: 0;
1741 | }
1742 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder {
1743 |   display: -webkit-box;
1744 |   display: -webkit-flex;
1745 |   display: flex;
1746 |   -webkit-box-pack: center;
1747 |   -webkit-justify-content: center;
1748 |   justify-content: center;
1749 |   -webkit-box-align: center;
1750 |   -webkit-align-items: center;
1751 |   align-items: center;
1752 |   box-sizing: border-box;
1753 |   width: 100%;
1754 |   padding: 0.25rem 0.5rem;
1755 |   margin: 0.25rem 0;
1756 |   border: 0;
1757 |   background: transparent;
1758 |   font: inherit;
1759 |   font-size: 0.75rem;
1760 |   line-height: 1.25;
1761 |   color: #b2b2b2;
1762 |   opacity: 0.78;
1763 |   text-decoration: none;
1764 |   text-align: center;
1765 |   user-select: none;
1766 |   cursor: pointer;
1767 |   -webkit-tap-highlight-color: transparent;
1768 | }
1769 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder .blacklisted_post_placeholder_text {
1770 |   color: inherit !important;
1771 |   text-decoration: none !important;
1772 | }
1773 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder.aec,
1774 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:link,
1775 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:visited,
1776 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:hover,
1777 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:active,
1778 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:focus {
1779 |   color: #b2b2b2 !important;
1780 |   text-decoration: none !important;
1781 |   background: transparent;
1782 |   outline: 0;
1783 | }
1784 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder[hidden] {
1785 |   display: none !important;
1786 | }
1787 | body#topic .post_container.blacklisted_post .blacklisted_post_content[hidden] {
1788 |   display: none !important;
1789 | }
1790 | body#topic .post_container.blacklisted_post.revealed .blacklisted_post_content {
1791 |   display: block;
1792 | }
1793 | body#topic .posts_list .theme_page_separator + .theme_page_container > .post_container.blacklisted_post:first-child {
1794 |   margin-top: 0;
1795 | }
1796 | body#topic .posts_list .theme_page_separator + .theme_page_container > .post_container:first-child:not(.blacklisted_post) {
1797 |   margin-top: 0.125rem;
1798 | }
1799 | body#topic .posts_list > .topic_hat_entry.top_hat_entry:first-child {
1800 |   margin-top: 0;
1801 | }
1802 | body#topic .poll.poll_entry {
1803 |   margin-top: 0;
1804 |   margin-bottom: var(--topic-post-spacing);
1805 | }
1806 | body#topic .post_body {
1807 |   font-size: var(--topic-body-font-size);
1808 |   line-height: var(--topic-body-line-height);
1809 |   color: #ffffff;
1810 | }
1811 | body#topic .post_body p {
1812 |   margin-top: 0;
1813 |   margin-bottom: var(--topic-paragraph-spacing);
1814 | }
1815 | body#topic .post_body p:last-child {
1816 |   margin-bottom: 0;
1817 | }
1818 | body#topic .post_body h1,
1819 | body#topic .post_body h2,
1820 | body#topic .post_body h3,
1821 | body#topic .post_body h4 {
1822 |   margin: 1em 0 0.45em;
1823 |   color: #ffffff;
1824 |   line-height: 1.25;
1825 | }
1826 | body#topic .post_body h1:first-child,
1827 | body#topic .post_body h2:first-child,
1828 | body#topic .post_body h3:first-child,
1829 | body#topic .post_body h4:first-child {
1830 |   margin-top: 0;
1831 | }
1832 | body#topic .post_body ul,
1833 | body#topic .post_body ol {
1834 |   margin-top: var(--topic-paragraph-spacing);
1835 |   margin-bottom: var(--topic-paragraph-spacing);
1836 |   padding-left: 1.35em;
1837 | }
1838 | body#topic .post_body li + li {
1839 |   margin-top: 0.25em;
1840 | }
1841 | body#topic .post_body > .post-block,
1842 | body#topic .post_body .attach_block,
1843 | body#topic .post_body img.linked-image,
1844 | body#topic .post_body img.attach {
1845 |   margin-top: var(--topic-inline-block-spacing);
1846 |   margin-bottom: var(--topic-inline-block-spacing);
1847 | }
1848 | body#topic .post_body img.linked-image,
1849 | body#topic .post_body img.attach {
1850 |   display: block;
1851 | }
1852 | body#topic .post_body .edit,
1853 | body#topic .post_body strong .edit {
1854 |   display: block;
1855 |   text-align: inherit;
1856 |   padding: 0;
1857 |   margin-left: 0;
1858 |   margin-top: 0;
1859 |   margin-bottom: 0;
1860 |   vertical-align: baseline;
1861 |   line-height: 1.35;
1862 |   white-space: normal;
1863 |   overflow-wrap: anywhere;
1864 |   word-break: break-word;
1865 |   max-width: 100%;
1866 | }
1867 | body#topic .post_body .post-edit-reason {
1868 |   display: block;
1869 |   margin-top: 0;
1870 |   margin-bottom: 0;
1871 |   line-height: 1.35;
1872 |   padding-top: 0;
1873 |   padding-bottom: 0;
1874 | }
1875 | body#topic .post-block.quote {
1876 |   margin-top: var(--topic-quote-spacing);
1877 |   margin-bottom: var(--topic-quote-spacing);
1878 |   border-left: 0;
1879 |   box-sizing: border-box;
1880 |   font-size: var(--topic-quote-font-size);
1881 |   line-height: 1.44;
1882 |   overflow: hidden;
1883 |   touch-action: pan-y;
1884 |   padding-left: 0;
1885 |   background: var(--surface-control, rgba(255, 255, 255, 0.1));
1886 |   -webkit-border-radius: 0.75rem;
1887 |   border-radius: 0.75rem;
1888 | }
1889 | body#topic .post-block.quote:before {
1890 |   content: none;
1891 | }
1892 | body#topic .post-block.quote > .block-title {
1893 |   padding: 0.625rem 0.75rem 0.3125rem 0.75rem;
1894 |   color: #ffffff !important;
1895 |   font-size: 0.875rem;
1896 |   line-height: 1.25rem;
1897 | }
1898 | body#topic .post-block.quote > .block-title .title .date {
1899 |   color: #b2b2b2;
1900 |   line-height: 1rem;
1901 | }
1902 | body#topic .post-block.quote > .block-body {
1903 |   padding: 0.25rem 0.75rem 0.75rem 0.75rem;
1904 | }
1905 | body#topic .post-block.quote > .block-body > .post-block.quote {
1906 |   margin-top: 0.5rem;
1907 |   margin-bottom: 0.25rem;
1908 |   margin-left: 0;
1909 | }
1910 | body#topic .post-block.code > .block-body .lines {
1911 |   font-size: var(--topic-code-font-size);
1912 |   line-height: var(--topic-code-line-height);
1913 |   background: linear-gradient(rgba(130, 130, 130, 0.1) var(--topic-code-line-height), transparent var(--topic-code-line-height));
1914 |   background-size: 100% calc(var(--topic-code-line-height) * 2);
1915 |   -webkit-overflow-scrolling: touch;
1916 |   padding-right: 0;
1917 | }
1918 | body#topic .post-block.code > .block-body .lines > div {
1919 |   min-height: var(--topic-code-line-height);
1920 |   padding-right: 0.75rem;
1921 | }
1922 | body#topic .post-block.code > .block-body .lines > div:before {
1923 |   line-height: var(--topic-code-line-height);
1924 | }
1925 | body#topic .post-block.code {
1926 |   margin-top: var(--topic-inline-block-spacing);
1927 |   margin-bottom: var(--topic-inline-block-spacing);
1928 |   contain: paint;
1929 | }
1930 | body#topic .post-block.code > .block-title {
1931 |   padding-top: 0.5rem;
1932 |   padding-bottom: 0.5rem;
1933 |   line-height: 1.25rem;
1934 | }
1935 | body#topic .post-block.code > .block-body {
1936 |   padding-top: 0.375rem;
1937 |   padding-bottom: 0.5rem;
1938 |   overflow: hidden;
1939 | }
1940 | body#topic .post-block.spoil {
1941 |   margin-top: var(--topic-inline-block-spacing);
1942 |   margin-bottom: var(--topic-inline-block-spacing);
1943 | }
1944 | body#topic .post-block.spoil > .block-title {
1945 |   display: -webkit-box;
1946 |   display: -webkit-flex;
1947 |   display: flex;
1948 |   -webkit-box-align: center;
1949 |   -webkit-align-items: center;
1950 |   align-items: center;
1951 |   box-sizing: border-box;
1952 |   line-height: 1.25rem;
1953 |   padding-right: 5rem;
1954 | }
1955 | body#topic .post-block.spoil > .block-title > .block-controls {
1956 |   display: flex;
1957 |   align-items: center;
1958 |   justify-content: flex-end;
1959 |   height: 2.5rem;
1960 |   right: 2.5rem;
1961 |   top: 50%;
1962 |   -webkit-transform: translateY(-50%);
1963 |   transform: translateY(-50%);
1964 | }
1965 | body#topic .post-block.spoil > .block-title > .block-controls i {
1966 |   float: none;
1967 |   flex: 0 0 2.25rem;
1968 | }
1969 | body#topic .post-block.spoil > .block-title .icon {
1970 |   display: flex;
1971 |   align-items: center;
1972 |   justify-content: center;
1973 |   height: 2.5rem;
1974 |   width: 2.5rem;
1975 |   top: 50%;
1976 |   -webkit-transform: translateY(-50%);
1977 |   transform: translateY(-50%);
1978 | }
1979 | body#topic .post-block.spoil.open > .block-title .icon {
1980 |   -webkit-transform: translateY(-50%) rotateX(180deg) translateZ(0);
1981 |   transform: translateY(-50%) rotateX(180deg) translateZ(0);
1982 | }
1983 | body#topic .post-block.spoil.open > .block-body {
1984 |   padding-top: 0.5rem;
1985 | }
1986 | body#topic .post-block.spoil > .block-body > .post-block.spoil {
1987 |   margin-top: 0.5rem;
1988 |   margin-bottom: 0.5rem;
1989 | }
1990 | body#topic.density_comfortable .post_body .post-block.quote,
1991 | body#topic.density_comfortable .post_body .post-block.spoil {
1992 |   margin-bottom: 0.375rem;
1993 | }
1994 | body#topic.density_comfortable .post_body .post-block.quote + br,
1995 | body#topic.density_comfortable .post_body .post-block.spoil + br,
1996 | body#topic.density_comfortable .post_body .post-block.quote + br + br,
1997 | body#topic.density_comfortable .post_body .post-block.spoil + br + br {
1998 |   display: none;
1999 | }
2000 | body#topic.density_comfortable .post_body .post-block.quote + p,
2001 | body#topic.density_comfortable .post_body .post-block.spoil + p,
2002 | body#topic.density_comfortable .post_body .post-block.quote + br + p,
2003 | body#topic.density_comfortable .post_body .post-block.spoil + br + p,
2004 | body#topic.density_comfortable .post_body .post-block.quote + br + br + p,
2005 | body#topic.density_comfortable .post_body .post-block.spoil + br + br + p {
2006 |   margin-top: 0;
2007 | }
2008 | body#topic.density_comfortable {
2009 |   --topic-collapsible-header-min-height: 2rem;
2010 |   --topic-collapsible-header-padding-y: 0.3125rem;
2011 |   --topic-collapsible-header-icon-size: 1.5rem;
2012 | }
2013 | body#topic.density_comfortable .post_container .post_footer {
2014 |   padding: 0 0.75rem 0.5rem 0.75rem;
2015 | }
2016 | body#topic.density_comfortable .post_container .post_footer .post_actions_row {
2017 |   --post-action-gap: 0.25rem;
2018 |   --post-action-button-size: 2.1rem;
2019 |   --post-action-icon-size: 1.2rem;
2020 |   --post-rep-action-icon-size: var(--post-action-icon-size);
2021 |   --post-action-radius: 0;
2022 | }
2023 | body#topic .post_container .post_header .header_wrapper {
2024 |   position: relative !important;
2025 |   --post-header-action-right: -1rem !important;
2026 |   --post-header-action-width: 2.5rem !important;
2027 |   --post-header-inline-end-reserve: 2.5rem !important;
2028 |   --post-header-menu-axis-overhang: 2.25rem !important;
2029 |   padding-right: var(--post-header-inline-end-reserve) !important;
2030 |   box-sizing: border-box !important;
2031 | }
2032 | body#topic .post_container .post_header .header_wrapper > .inf.post_meta {
2033 |   display: -webkit-box !important;
2034 |   display: -webkit-flex !important;
2035 |   display: flex !important;
2036 |   -webkit-box-align: baseline !important;
2037 |   -webkit-align-items: baseline !important;
2038 |   align-items: baseline !important;
2039 |   gap: 0.5rem !important;
2040 |   min-width: 0 !important;
2041 |   width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
2042 |   max-width: none !important;
2043 |   margin-right: 0 !important;
2044 |   padding-right: 0 !important;
2045 |   box-sizing: border-box !important;
2046 | }
2047 | body#topic .post_container .post_header .header_wrapper > .inf.post_meta > .group_text {
2048 |   -webkit-box-flex: 1 !important;
2049 |   -webkit-flex: 1 1 auto !important;
2050 |   flex: 1 1 auto !important;
2051 |   min-width: 0 !important;
2052 |   overflow: hidden !important;
2053 |   text-overflow: ellipsis !important;
2054 |   white-space: nowrap !important;
2055 | }
2056 | body#topic .post_container .post_header .header_wrapper > .inf.post_meta > .date {
2057 |   -webkit-box-flex: 0 !important;
2058 |   -webkit-flex: 0 0 auto !important;
2059 |   flex: 0 0 auto !important;
2060 |   margin-left: auto !important;
2061 |   max-width: 9rem !important;
2062 |   overflow: hidden !important;
2063 |   text-align: right !important;
2064 |   text-overflow: ellipsis !important;
2065 |   white-space: nowrap !important;
2066 | }
2067 | body#topic .post_container .post_header .header_wrapper > .inf.user_post_count {
2068 |   display: -webkit-box !important;
2069 |   display: -webkit-flex !important;
2070 |   display: flex !important;
2071 |   -webkit-box-align: center !important;
2072 |   -webkit-align-items: center !important;
2073 |   align-items: center !important;
2074 |   visibility: visible !important;
2075 |   height: auto !important;
2076 |   min-height: 0.875rem !important;
2077 |   overflow: visible !important;
2078 |   color: #b2b2b2 !important;
2079 | }
2080 | body#topic .post-block.quote.smart-quote-collapsible {
2081 |   overflow: hidden;
2082 | }
2083 | body#topic .post-block.quote.smart-quote-collapsible > .block-body {
2084 |   -webkit-transition: max-height 0.2s cubic-bezier(0.4, 0, 0.2, 1);
2085 |   transition: max-height 0.2s cubic-bezier(0.4, 0, 0.2, 1);
2086 | }
2087 | body#topic .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body {
2088 |   max-height: 11.25rem;
2089 |   overflow: hidden;
2090 | }
2091 | body#topic .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body:after {
2092 |   content: "";
2093 |   position: absolute;
2094 |   left: 0;
2095 |   right: 0;
2096 |   bottom: 0;
2097 |   height: 3rem;
2098 |   pointer-events: none;
2099 |   box-shadow: inset 0 -3rem 2rem -1.5rem rgba(255, 255, 255, 0.1);
2100 | }
2101 | body#topic .post-block.quote.smart-quote-collapsible.smart-quote-expanded > .block-body {
2102 |   max-height: none;
2103 | }
2104 | body#topic .post-block.quote.smart-quote-collapsible > .smart-quote-toggle {
2105 |   -webkit-touch-callout: none;
2106 |   -webkit-user-select: none;
2107 |   -khtml-user-select: none;
2108 |   -moz-user-select: none;
2109 |   -ms-user-select: none;
2110 |   user-select: none;
2111 |   display: block;
2112 |   padding: 0.5rem 0.75rem;
2113 |   color: #E0E0E0;
2114 |   font-size: 0.875rem;
2115 |   font-weight: bold;
2116 |   text-align: center;
2117 | }
2118 | body#topic .post-block.quote.smart-quote-collapsible > .smart-quote-toggle:active {
2119 |   background: rgba(255, 255, 255, 0.16);
2120 | }
2121 | body#search .post_container .post_header .header_wrapper {
2122 |   position: relative !important;
2123 |   --post-header-action-right: -1rem !important;
2124 |   --post-header-action-width: 2.5rem !important;
2125 |   --post-header-inline-end-reserve: 2.5rem !important;
2126 |   --post-header-menu-axis-overhang: 2.25rem !important;
2127 |   padding-right: var(--post-header-inline-end-reserve) !important;
2128 |   box-sizing: border-box !important;
2129 | }
2130 | body#search .post_container .post_header .header_wrapper > .inf.post_meta {
2131 |   display: -webkit-box !important;
2132 |   display: -webkit-flex !important;
2133 |   display: flex !important;
2134 |   -webkit-box-align: baseline !important;
2135 |   -webkit-align-items: baseline !important;
2136 |   align-items: baseline !important;
2137 |   gap: 0.5rem !important;
2138 |   min-width: 0 !important;
2139 |   width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
2140 |   max-width: none !important;
2141 |   margin-right: 0 !important;
2142 |   padding-right: 0 !important;
2143 |   box-sizing: border-box !important;
2144 | }
2145 | body#search .post_container .post_header .header_wrapper > .inf.post_meta > .group_text {
2146 |   -webkit-box-flex: 1 !important;
2147 |   -webkit-flex: 1 1 auto !important;
2148 |   flex: 1 1 auto !important;
2149 |   min-width: 0 !important;
2150 |   overflow: hidden !important;
2151 |   text-overflow: ellipsis !important;
2152 |   white-space: nowrap !important;
2153 | }
2154 | body#search .post_container .post_header .header_wrapper > .inf.post_meta > .date {
2155 |   -webkit-box-flex: 0 !important;
2156 |   -webkit-flex: 0 0 auto !important;
2157 |   flex: 0 0 auto !important;
2158 |   margin-left: auto !important;
2159 |   max-width: 9rem !important;
2160 |   overflow: hidden !important;
2161 |   text-align: right !important;
2162 |   text-overflow: ellipsis !important;
2163 |   white-space: nowrap !important;
2164 | }
2165 | body#topic.density_compact .post_container .post_header .header_wrapper {
2166 |   --post-header-action-right: -0.5rem !important;
2167 |   --post-header-action-width: 2.125rem !important;
2168 |   --post-header-inline-end-reserve: 2.375rem !important;
2169 |   --post-header-menu-axis-overhang: 1.8125rem !important;
2170 |   padding-right: var(--post-header-inline-end-reserve) !important;
2171 | }
2172 | body#topic.density_compact .post_container .post_header .header_wrapper > .inf.post_meta {
2173 |   width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
2174 |   margin-right: 0 !important;
2175 | }
2176 | body#topic.density_compact .post_container .post_header .header_wrapper > .inf.post_meta > .date {
2177 |   max-width: 7.75rem !important;
2178 | }
2179 | body#topic.density_compact {
2180 |   --topic-collapsible-header-min-height: 2rem;
2181 |   --topic-collapsible-header-padding-y: 0.25rem;
2182 |   --topic-collapsible-header-icon-size: 1.5rem;
2183 |   --topic-body-line-height: 1.38;
2184 |   --topic-code-line-height: 1.1875rem;
2185 |   --topic-paragraph-spacing: 0.38em;
2186 |   --topic-inline-block-spacing: 0.3125rem;
2187 |   --topic-edit-info-spacing: 0.25rem;
2188 |   --topic-post-spacing: 0.3125rem;
2189 |   --topic-quote-spacing: 0.3125rem;
2190 | }
2191 | body#topic.density_compact .post_container {
2192 |   margin: var(--topic-post-spacing) 0;
2193 | }
2194 | body#topic.density_compact .post_container .hat_button {
2195 |   min-height: 2rem;
2196 |   line-height: 2rem;
2197 |   padding-top: 0;
2198 |   padding-bottom: 0;
2199 | }
2200 | body#topic.density_compact .post_container .hat_button + .hat_content {
2201 |   padding-top: 0.25rem;
2202 | }
2203 | body#topic.density_compact .post_container .hat_button .icon {
2204 |   height: 1.5rem;
2205 |   margin-top: 0;
2206 |   margin-bottom: 0;
2207 | }
2208 | body#topic.density_compact .topic_hat_entry.post_container > .hat_button .icon {
2209 |   margin-top: 0;
2210 |   margin-bottom: 0;
2211 | }
2212 | body#topic.density_compact .post_container .post_header {
2213 |   padding: 0.5rem 0.75rem 0 0.75rem;
2214 | }
2215 | body#topic.density_compact .post_container .post_header .header_wrapper {
2216 |   --post-header-action-right: -0.5rem;
2217 |   --post-header-action-width: 2.125rem;
2218 |   --post-header-inline-end-reserve: 2.375rem;
2219 |   --post-header-menu-axis-overhang: 1.8125rem;
2220 |   min-height: 3.625rem;
2221 |   display: flex;
2222 |   flex-direction: column;
2223 |   justify-content: center;
2224 |   padding-left: 4.25em;
2225 |   padding-right: var(--post-header-inline-end-reserve);
2226 |   box-sizing: border-box;
2227 | }
2228 | body#topic.density_compact .post_container .post_header .avatar {
2229 |   position: absolute;
2230 |   left: 0;
2231 |   top: 0;
2232 | }
2233 | body#topic.density_compact .post_container .post_header .inf {
2234 |   position: relative;
2235 |   left: auto;
2236 |   max-width: none;
2237 |   margin-top: 0;
2238 |   line-height: 1.25;
2239 | }
2240 | body#topic.density_compact .post_container .post_header .inf.nick {
2241 |   display: flex;
2242 |   top: auto;
2243 |   padding-top: 0;
2244 |   padding-right: 0.25rem;
2245 | }
2246 | body#topic.density_compact .post_container .post_header .inf.post_meta {
2247 |   margin-top: 0.1875rem;
2248 |   width: calc(100% + var(--post-header-menu-axis-overhang));
2249 |   margin-right: 0;
2250 |   padding-right: 0;
2251 |   min-height: 1rem;
2252 | }
2253 | body#topic.density_compact .post_container .post_header .inf.post_meta .group_text {
2254 |   margin-right: 0;
2255 | }
2256 | body#topic.density_compact .post_container .post_header .inf.post_meta .date {
2257 |   max-width: 7.75rem;
2258 | }
2259 | body#topic.density_compact .post_container .post_header .inf.post_meta + .user_post_count {
2260 |   margin-top: 0.125rem;
2261 | }
2262 | body#topic.density_compact .post_container .post_header .inf.post_meta .group_text > span,
2263 | body#topic.density_compact .post_container .post_header .inf.post_meta .date > span {
2264 |   font-size: 0.75rem;
2265 | }
2266 | body#topic.density_compact .post_container .post_header .inf.user_post_count {
2267 |   gap: 0.1875rem;
2268 | }
2269 | body#topic.density_compact .post_container .post_header .inf.user_post_count > span {
2270 |   font-size: 0.6875rem;
2271 | }
2272 | body#topic.density_compact .post_container .post_header .inf.user_post_count .user_post_count_icon {
2273 |   width: 0.6875rem;
2274 |   height: 0.6875rem;
2275 | }
2276 | body#topic.density_compact .post_container .post_header .inf.menu {
2277 |   position: absolute;
2278 |   right: var(--post-header-action-right);
2279 |   top: -0.375rem;
2280 |   height: 2.5rem;
2281 |   width: var(--post-header-action-width);
2282 |   background-size: 1.25rem 1.25rem;
2283 | }
2284 | body#topic.density_compact .post_container .post_header .inf.number {
2285 |   display: none;
2286 | }
2287 | body#topic.density_compact .post_container .post_body {
2288 |   padding: 0.5rem 0.75rem;
2289 | }
2290 | body#topic.density_compact .post_container .post_body .postcolor {
2291 |   margin: -0.5rem -0.75rem;
2292 |   padding: 0.5rem 0.75rem;
2293 | }
2294 | body#topic.density_compact .post_container .post_body a.anchor {
2295 |   height: 1.25rem;
2296 |   margin-top: 0.25rem;
2297 | }
2298 | body#topic.density_compact .post_container .post_body .edit {
2299 |   padding: var(--topic-edit-info-spacing) 0;
2300 |   line-height: 1.125rem;
2301 | }
2302 | body#topic.density_compact .post_container .post_body .post-edit-reason {
2303 |   padding: 0;
2304 |   margin-top: 0;
2305 |   line-height: 1.125rem;
2306 | }
2307 | body#topic.density_compact .post_container .post_footer {
2308 |   padding: 0 0.75rem 0.3125rem 0.75rem;
2309 | }
2310 | body#topic.density_compact .post_container .post_footer .post_actions_row {
2311 |   --post-action-gap: 0.171875rem;
2312 |   --post-action-button-size: 2.042184375rem;
2313 |   --post-action-icon-size: 1.195425rem;
2314 |   --post-rep-action-icon-size: var(--post-action-icon-size);
2315 |   --post-action-radius: 0;
2316 |   margin: 0 0.171875rem 0.125rem 0.171875rem;
2317 | }
2318 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.reply,
2319 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.quote,
2320 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_up,
2321 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_down {
2322 |   width: var(--post-action-button-size);
2323 |   height: var(--post-action-button-size);
2324 |   min-height: var(--post-action-button-size);
2325 |   min-width: var(--post-action-button-size);
2326 |   padding: 0;
2327 |   border: 0;
2328 |   background: transparent;
2329 |   background-color: transparent;
2330 |   background-image: none;
2331 |   box-shadow: none;
2332 |   outline: 0;
2333 |   -webkit-filter: none;
2334 |   filter: none;
2335 |   -webkit-border-radius: var(--post-action-radius);
2336 |   border-radius: var(--post-action-radius);
2337 | }
2338 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
2339 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.quote > .post-action-icon,
2340 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_up > .post-action-icon,
2341 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_down > .post-action-icon {
2342 |   width: var(--post-action-icon-size);
2343 |   height: var(--post-action-icon-size);
2344 | }
2345 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
2346 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon {
2347 |   width: var(--post-rep-action-icon-size);
2348 |   height: var(--post-rep-action-icon-size);
2349 | }
2350 | body#topic.density_compact .post_container .post_footer .post_rating {
2351 |   padding: 0.125rem 0.375rem;
2352 |   font-size: 0.75rem;
2353 | }
2354 | body#topic.density_compact .topic_hat_fixed.post_container .post_header,
2355 | body#topic.density_compact .topic_hat_entry.post_container .post_header {
2356 |   padding-top: 0.5rem;
2357 | }
2358 | body#topic.density_compact .post-block {
2359 |   margin-top: 0.5rem;
2360 |   margin-bottom: 0.5rem;
2361 | }
2362 | body#topic.density_compact .post-block > .block-title {
2363 |   padding: 0.4375rem 0.75rem;
2364 |   line-height: 1.125rem;
2365 | }
2366 | body#topic.density_compact .post-block > .block-body {
2367 |   padding: 0.375rem 0.75rem 0.625rem 0.75rem;
2368 | }
2369 | body#topic.density_compact .post-block.spoil > .block-title {
2370 |   min-height: 1.125rem;
2371 |   padding-right: 4.5rem;
2372 | }
2373 | body#topic.density_compact .post-block.spoil.close {
2374 |   margin-bottom: 0.375rem;
2375 | }
2376 | body#topic.density_compact .post-block.spoil.close + br,
2377 | body#topic.density_compact .post-block.spoil.close + br + br {
2378 |   display: none;
2379 | }
2380 | body#topic.density_compact .post-block.spoil.close + .edit,
2381 | body#topic.density_compact .post-block.spoil.close + .post-edit-reason,
2382 | body#topic.density_compact .post-block.spoil.close + strong .edit,
2383 | body#topic.density_compact .post-block.spoil.close + br + .edit,
2384 | body#topic.density_compact .post-block.spoil.close + br + .post-edit-reason,
2385 | body#topic.density_compact .post-block.spoil.close + br + strong .edit,
2386 | body#topic.density_compact .post-block.spoil.close + br + br + .edit,
2387 | body#topic.density_compact .post-block.spoil.close + br + br + .post-edit-reason,
2388 | body#topic.density_compact .post-block.spoil.close + br + br + strong .edit {
2389 |   margin-top: 0;
2390 |   padding-top: 0;
2391 | }
2392 | body#topic.density_compact .post-block.spoil > .block-title > .block-controls {
2393 |   height: 2.25rem;
2394 |   right: 2.25rem;
2395 | }
2396 | body#topic.density_compact .post-block.spoil > .block-title .icon {
2397 |   height: 2.25rem;
2398 |   width: 2.25rem;
2399 | }
2400 | body#topic.density_compact .post-block.spoil.open > .block-title .icon {
2401 |   -webkit-transform: translateY(-50%) rotateX(180deg) translateZ(0);
2402 |   transform: translateY(-50%) rotateX(180deg) translateZ(0);
2403 | }
2404 | body#topic.density_compact .post-block.spoil > .block-body > .btns_container > .spoil_close {
2405 |   margin-top: 0.625rem;
2406 |   margin-left: -0.375rem;
2407 |   margin-right: -0.375rem;
2408 |   margin-bottom: -0.375rem;
2409 |   padding: 0.375rem;
2410 | }
2411 | body#topic.density_compact .post-block.quote {
2412 |   margin-top: var(--topic-quote-spacing);
2413 |   margin-bottom: var(--topic-quote-spacing);
2414 | }
2415 | body#topic.density_compact .post-block.quote + br,
2416 | body#topic.density_compact .post-block.quote + br + br {
2417 |   display: none;
2418 | }
2419 | body#topic.density_compact .post-block.quote + p,
2420 | body#topic.density_compact .post-block.quote + br + p,
2421 | body#topic.density_compact .post-block.quote + br + br + p {
2422 |   margin-top: 0;
2423 | }
2424 | body#topic.density_compact .post-block.quote > .block-title {
2425 |   padding-top: 0.375rem;
2426 |   padding-bottom: 0.375rem;
2427 | }
2428 | body#topic.density_compact .post-block.quote > .block-body {
2429 |   padding-top: 0.25rem;
2430 |   padding-bottom: 0.5rem;
2431 | }
2432 | body#topic.density_compact .post-block.code > .block-title {
2433 |   padding-top: 0.375rem;
2434 |   padding-bottom: 0.375rem;
2435 | }
2436 | body#topic.density_compact .post-block.code > .block-body {
2437 |   padding: 0.25rem 0 0.375rem 2.5rem;
2438 | }
2439 | body#topic.density_compact .post-block.code > .block-body .lines {
2440 |   padding-right: 0.5rem;
2441 | }
2442 | body#topic.density_compact .post-block.code > .block-body .lines > div:before {
2443 |   width: 2.5rem;
2444 | }
2445 | body#topic.density_compact .attach_block {
2446 |   padding-left: 3rem;
2447 |   padding-top: 0.1875rem;
2448 |   padding-bottom: 0.1875rem;
2449 |   padding-right: 0.25rem;
2450 |   min-height: 2.625rem;
2451 | }
2452 | body#topic.density_compact .attach_block .icon {
2453 |   top: 0.1875rem;
2454 |   left: 0.1875rem;
2455 |   height: 2.25rem;
2456 |   width: 2.25rem;
2457 | }
2458 | body#topic.density_compact .attach_block .icon:after {
2459 |   height: 2.25rem;
2460 |   width: 2.25rem;
2461 | }
2462 | body#topic.density_compact .post_container.topic_hat_fixed > .hat_button,
2463 | body#topic.density_compact .post_container.topic_hat_entry > .hat_button,
2464 | body#topic.density_compact .poll > .title,
2465 | body#topic.density_compact .poll.poll_entry > .title {
2466 |   min-height: 2rem;
2467 |   padding-top: 0.25rem;
2468 |   padding-bottom: 0.25rem;
2469 | }
2470 | body#topic.density_compact .post_container.topic_hat_fixed > .hat_button > span,
2471 | body#topic.density_compact .post_container.topic_hat_entry > .hat_button > span,
2472 | body#topic.density_compact .poll > .title > span,
2473 | body#topic.density_compact .poll.poll_entry > .title > span {
2474 |   font-size: 0.8125rem;
2475 | }
2476 | body#topic.density_compact .post_container.topic_hat_fixed > .hat_button > .icon,
2477 | body#topic.density_compact .post_container.topic_hat_entry > .hat_button > .icon,
2478 | body#topic.density_compact .poll > .title > .icon,
2479 | body#topic.density_compact .poll.poll_entry > .title > .icon {
2480 |   flex-basis: 1.5rem;
2481 |   width: 1.5rem;
2482 |   height: 1.5rem;
2483 | }
2484 | body#topic.density_compact .poll.poll_entry {
2485 |   margin: 0.375rem 0;
2486 | }
2487 | body#topic.density_compact .poll.poll_entry > .body .questions .question > .title {
2488 |   padding-top: 0.5rem;
2489 |   padding-bottom: 0.375rem;
2490 | }
2491 | body#topic.density_compact .poll.poll_entry > .body .questions .question > .items {
2492 |   padding-top: 0.25rem;
2493 |   padding-bottom: 0.5rem;
2494 | }
2495 | body#topic.density_super_compact {
2496 |   --topic-collapsible-header-min-height: 1.875rem;
2497 |   --topic-collapsible-header-padding-y: 0.25rem;
2498 |   --topic-collapsible-header-icon-size: 1.375rem;
2499 |   --topic-body-line-height: 1.32;
2500 |   --topic-code-line-height: 1.125rem;
2501 |   --topic-paragraph-spacing: 0.28em;
2502 |   --topic-inline-block-spacing: 0.25rem;
2503 |   --topic-edit-info-spacing: 0.125rem;
2504 |   --topic-post-spacing: 0.1875rem;
2505 |   --topic-quote-spacing: 0.25rem;
2506 | }
2507 | body#topic.density_super_compact .post_container .hat_button {
2508 |   min-height: 2.125rem;
2509 |   line-height: 2.125rem;
2510 | }
2511 | body#topic.density_super_compact .post_container .hat_button + .hat_content {
2512 |   padding-top: 0.125rem;
2513 | }
2514 | body#topic.density_super_compact .post_container .hat_button .icon {
2515 |   height: 2.125rem;
2516 |   background-size: 1.125rem 1.125rem;
2517 | }
2518 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button,
2519 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button {
2520 |   display: -webkit-box;
2521 |   display: -webkit-flex;
2522 |   display: flex;
2523 |   -webkit-box-align: center;
2524 |   -webkit-align-items: center;
2525 |   align-items: center;
2526 |   -webkit-box-pack: justify;
2527 |   -webkit-justify-content: space-between;
2528 |   justify-content: space-between;
2529 |   box-sizing: border-box;
2530 |   min-height: 2.5rem;
2531 |   padding: 0.375rem 0.75rem;
2532 |   line-height: 1.2;
2533 |   text-align: left;
2534 | }
2535 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > span,
2536 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > span {
2537 |   -webkit-box-flex: 1;
2538 |   -webkit-flex: 1 1 auto;
2539 |   flex: 1 1 auto;
2540 |   min-width: 0;
2541 |   overflow: hidden;
2542 |   text-overflow: ellipsis;
2543 |   white-space: nowrap;
2544 |   font-size: 0.75rem;
2545 |   line-height: 1.2;
2546 | }
2547 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > .icon,
2548 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > .icon {
2549 |   -webkit-box-flex: 0;
2550 |   -webkit-flex: 0 0 2rem;
2551 |   flex: 0 0 2rem;
2552 |   float: none;
2553 |   width: 2rem;
2554 |   height: 2rem;
2555 |   margin: 0 0 0 0.5rem;
2556 | }
2557 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > .icon:after,
2558 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > .icon:after {
2559 |   -webkit-transform: rotate(-45deg);
2560 |   transform: rotate(-45deg);
2561 | }
2562 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button + .hat_content {
2563 |   padding-top: 0.125rem;
2564 | }
2565 | body#topic.density_super_compact .post_container .post_header {
2566 |   padding: 0.375rem 0.625rem 0 0.625rem;
2567 | }
2568 | body#topic.density_super_compact .post_container .post_header .header_wrapper {
2569 |   min-height: 2.75rem;
2570 |   padding-left: 2.75rem;
2571 | }
2572 | body#topic.density_super_compact .post_container .post_header .avatar,
2573 | body#topic.density_super_compact .post_container .post_header .avatar .img,
2574 | body#topic.density_super_compact .post_container .post_header .avatar .letter {
2575 |   width: 2.25rem;
2576 |   height: 2.25rem;
2577 | }
2578 | body#topic.density_super_compact .post_container .post_header .avatar .letter {
2579 |   line-height: 2.25rem;
2580 | }
2581 | body#topic.density_super_compact .post_container .post_header .avatar .reputation {
2582 |   min-width: 1.125rem;
2583 |   height: 1.125rem;
2584 |   line-height: 1.125rem;
2585 | }
2586 | body#topic.density_super_compact .post_container .post_header .avatar .reputation > span {
2587 |   font-size: 0.625rem;
2588 | }
2589 | body#topic.density_super_compact .post_container .post_header .inf {
2590 |   line-height: 1.18;
2591 | }
2592 | body#topic.density_super_compact .post_container .post_header .inf.nick {
2593 |   font-size: 0.875rem;
2594 | }
2595 | body#topic.density_super_compact .post_container .post_header .inf.post_meta {
2596 |   margin-top: 0.0625rem;
2597 |   min-height: 0.875rem;
2598 | }
2599 | body#topic.density_super_compact .post_container .post_header .inf.post_meta .group_text > span,
2600 | body#topic.density_super_compact .post_container .post_header .inf.post_meta .date > span {
2601 |   font-size: 0.6875rem;
2602 | }
2603 | body#topic.density_super_compact .post_container .post_header .inf.post_meta .date {
2604 |   max-width: 7rem;
2605 | }
2606 | body#topic.density_super_compact .post_container .post_header .inf.post_meta + .user_post_count {
2607 |   display: none !important;
2608 | }
2609 | body#topic.density_super_compact .post_container .post_header .inf.menu {
2610 |   top: -0.4375rem;
2611 |   height: 2.125rem;
2612 |   background-size: 1.125rem 1.125rem;
2613 | }
2614 | body#topic.density_super_compact .post_container .post_body {
2615 |   padding: 0.375rem 0.625rem;
2616 | }
2617 | body#topic.density_super_compact .post_container .post_body .postcolor {
2618 |   margin: -0.375rem -0.625rem;
2619 |   padding: 0.375rem 0.625rem;
2620 | }
2621 | body#topic.density_super_compact .post_container .post_body .edit,
2622 | body#topic.density_super_compact .post_container .post_body strong .edit {
2623 |   display: block;
2624 |   position: static;
2625 |   clear: both;
2626 |   font-size: 0.6875rem;
2627 |   line-height: 1.35;
2628 |   margin-top: 0;
2629 |   margin-bottom: 0;
2630 |   margin-left: 0;
2631 |   vertical-align: baseline;
2632 |   white-space: normal;
2633 |   overflow-wrap: anywhere;
2634 |   word-break: break-word;
2635 |   max-width: 100%;
2636 | }
2637 | body#topic.density_super_compact .post_container .post_body .post-edit-reason {
2638 |   display: block;
2639 |   position: static;
2640 |   clear: both;
2641 |   font-size: 0.6875rem;
2642 |   line-height: 1rem;
2643 |   margin-top: 0.125rem;
2644 |   margin-bottom: 0.125rem;
2645 | }
2646 | body#topic.density_super_compact .post_container .post_body p + .edit,
2647 | body#topic.density_super_compact .post_container .post_body p + .post-edit-reason,
2648 | body#topic.density_super_compact .post_container .post_body p + strong .edit,
2649 | body#topic.density_super_compact .post_container .post_body br + .edit,
2650 | body#topic.density_super_compact .post_container .post_body br + .post-edit-reason,
2651 | body#topic.density_super_compact .post_container .post_body br + strong .edit,
2652 | body#topic.density_super_compact .post_container .post_body br + br + .edit,
2653 | body#topic.density_super_compact .post_container .post_body br + br + .post-edit-reason,
2654 | body#topic.density_super_compact .post_container .post_body br + br + strong .edit {
2655 |   padding-top: 0;
2656 | }
2657 | body#topic.density_super_compact .post_container .post_body br:has(+ .edit),
2658 | body#topic.density_super_compact .post_container .post_body br:has(+ .post-edit-reason),
2659 | body#topic.density_super_compact .post_container .post_body br:has(+ strong .edit),
2660 | body#topic.density_super_compact .post_container .post_body br:has(+ br + .edit),
2661 | body#topic.density_super_compact .post_container .post_body br:has(+ br + .post-edit-reason),
2662 | body#topic.density_super_compact .post_container .post_body br:has(+ br + strong .edit) {
2663 |   display: none;
2664 | }
2665 | body#topic.density_super_compact .post_container .post_body br + .edit,
2666 | body#topic.density_super_compact .post_container .post_body br + .post-edit-reason,
2667 | body#topic.density_super_compact .post_container .post_body br + strong .edit,
2668 | body#topic.density_super_compact .post_container .post_body br + br + .edit,
2669 | body#topic.density_super_compact .post_container .post_body br + br + .post-edit-reason,
2670 | body#topic.density_super_compact .post_container .post_body br + br + strong .edit {
2671 |   margin-top: 0.125rem;
2672 | }
2673 | body#topic.density_super_compact .post_container .post_footer {
2674 |   padding: 0 0.625rem 0.3125rem 0.625rem;
2675 | }
2676 | body#topic.density_super_compact .post_container .post_footer .post_actions_row {
2677 |   --post-action-gap: 0.125rem;
2678 |   --post-action-button-size: 1.45805625rem;
2679 |   --post-action-icon-size: 0.929510859375rem;
2680 |   --post-rep-action-icon-size: var(--post-action-icon-size);
2681 |   --post-action-radius: 0;
2682 |   margin: 0 0.125rem 0.125rem 0.125rem;
2683 | }
2684 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.reply,
2685 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.quote,
2686 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_up,
2687 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_down {
2688 |   width: var(--post-action-button-size);
2689 |   height: var(--post-action-button-size);
2690 |   min-height: var(--post-action-button-size);
2691 |   min-width: var(--post-action-button-size);
2692 |   font-size: 0.75rem;
2693 |   -webkit-border-radius: var(--post-action-radius);
2694 |   border-radius: var(--post-action-radius);
2695 | }
2696 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
2697 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.quote > .post-action-icon,
2698 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_up > .post-action-icon,
2699 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_down > .post-action-icon {
2700 |   width: var(--post-action-icon-size);
2701 |   height: var(--post-action-icon-size);
2702 | }
2703 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
2704 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon {
2705 |   width: var(--post-rep-action-icon-size);
2706 |   height: var(--post-rep-action-icon-size);
2707 | }
2708 | body#topic.density_super_compact .post_container .post_footer .post_rating {
2709 |   padding: 0.0625rem 0.3125rem;
2710 |   font-size: 0.6875rem;
2711 | }
2712 | body#topic.density_super_compact .post-block {
2713 |   margin-top: 0.3125rem;
2714 |   margin-bottom: 0.3125rem;
2715 | }
2716 | body#topic.density_super_compact .post-block > .block-title {
2717 |   padding: 0.3125rem 0.625rem;
2718 |   line-height: 1rem;
2719 | }
2720 | body#topic.density_super_compact .post-block > .block-body {
2721 |   padding: 0.25rem 0.625rem 0.375rem 0.625rem;
2722 | }
2723 | body#topic.density_super_compact .post-block.spoil.close {
2724 |   margin-bottom: 0.25rem;
2725 | }
2726 | body#topic.density_super_compact .post-block.spoil > .block-title {
2727 |   min-height: 1rem;
2728 |   padding-right: 3.75rem;
2729 | }
2730 | body#topic.density_super_compact .post-block.spoil > .block-title > .block-controls {
2731 |   height: 1.875rem;
2732 |   right: 1.875rem;
2733 | }
2734 | body#topic.density_super_compact .post-block.spoil > .block-title .icon {
2735 |   height: 1.875rem;
2736 |   width: 1.875rem;
2737 |   top: 50%;
2738 |   -webkit-transform: translateY(-50%);
2739 |   transform: translateY(-50%);
2740 | }
2741 | body#topic.density_super_compact .post-block.spoil.open > .block-title .icon {
2742 |   -webkit-transform: translateY(-50%) rotateX(180deg) translateZ(0);
2743 |   transform: translateY(-50%) rotateX(180deg) translateZ(0);
2744 | }
2745 | body#topic.density_super_compact .post-block.spoil > .block-body > .btns_container > .spoil_close {
2746 |   margin-top: 0.375rem;
2747 |   padding: 0.3125rem;
2748 | }
2749 | body#topic.density_super_compact .post-block.quote {
2750 |   margin-bottom: 0.125rem;
2751 | }
2752 | body#topic.density_super_compact .post-block.quote + br,
2753 | body#topic.density_super_compact .post-block.quote + br + br {
2754 |   display: none;
2755 | }
2756 | body#topic.density_super_compact .post-block.quote + p,
2757 | body#topic.density_super_compact .post-block.quote + br + p,
2758 | body#topic.density_super_compact .post-block.quote + br + br + p {
2759 |   margin-top: 0;
2760 | }
2761 | body#topic.density_super_compact .post-block.quote > .block-title {
2762 |   padding-top: 0.25rem;
2763 |   padding-bottom: 0.25rem;
2764 | }
2765 | body#topic.density_super_compact .post-block.quote > .block-body {
2766 |   padding-top: 0.1875rem;
2767 |   padding-bottom: 0.3125rem;
2768 | }
2769 | body#topic.density_super_compact .post-block.code > .block-body {
2770 |   padding: 0.1875rem 0 0.3125rem 2.125rem;
2771 | }
2772 | body#topic.density_super_compact .post-block.code > .block-body .lines {
2773 |   padding-right: 0.375rem;
2774 | }
2775 | body#topic.density_super_compact .post-block.code > .block-body .lines > div:before {
2776 |   width: 2.125rem;
2777 | }
2778 | body#topic.density_super_compact .attach_block {
2779 |   min-height: 2.25rem;
2780 |   padding-left: 2.625rem;
2781 |   padding-top: 0.125rem;
2782 |   padding-bottom: 0.125rem;
2783 | }
2784 | body#topic.density_super_compact .attach_block .icon {
2785 |   top: 0.125rem;
2786 |   left: 0.125rem;
2787 |   height: 2rem;
2788 |   width: 2rem;
2789 | }
2790 | body#topic.density_super_compact .attach_block .icon:after {
2791 |   height: 2rem;
2792 |   width: 2rem;
2793 | }
2794 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button,
2795 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button,
2796 | body#topic.density_super_compact .poll > .title,
2797 | body#topic.density_super_compact .poll.poll_entry > .title {
2798 |   min-height: 1.875rem;
2799 |   padding-top: 0.25rem;
2800 |   padding-bottom: 0.25rem;
2801 | }
2802 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > span,
2803 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > span,
2804 | body#topic.density_super_compact .poll > .title > span,
2805 | body#topic.density_super_compact .poll.poll_entry > .title > span {
2806 |   font-size: 0.75rem;
2807 |   line-height: 1rem;
2808 | }
2809 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > .icon,
2810 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > .icon,
2811 | body#topic.density_super_compact .poll > .title > .icon,
2812 | body#topic.density_super_compact .poll.poll_entry > .title > .icon {
2813 |   flex-basis: 1.375rem;
2814 |   width: 1.375rem;
2815 |   height: 1.375rem;
2816 | }
2817 | body#topic.density_super_compact .poll.poll_entry {
2818 |   margin: 0.25rem 0;
2819 | }
2820 | body#topic.density_super_compact .poll.poll_entry > .body .questions .question > .title {
2821 |   padding-top: 0.375rem;
2822 |   padding-bottom: 0.25rem;
2823 | }
2824 | body#topic.density_super_compact .poll.poll_entry > .body .questions .question > .items {
2825 |   padding-top: 0.125rem;
2826 |   padding-bottom: 0.375rem;
2827 | }
2828 | body#topic {
2829 |   --topic-paragraph-gap: 0.5rem;
2830 |   --topic-block-gap: 0.625rem;
2831 |   --topic-inline-block-gap: 0.5rem;
2832 |   --topic-quote-gap: 0.5rem;
2833 |   --topic-body-padding-x: 1rem;
2834 |   --topic-body-padding-y: 0.875rem;
2835 |   --topic-block-title-padding-x: 0.75rem;
2836 |   --topic-block-title-padding-y: 0.5rem;
2837 |   --topic-block-body-padding-x: 0.75rem;
2838 |   --topic-block-body-padding-top: 0.375rem;
2839 |   --topic-block-body-padding-bottom: 0.5rem;
2840 |   --topic-quote-title-padding-top: 0.5rem;
2841 |   --topic-quote-title-padding-bottom: 0.25rem;
2842 |   --topic-quote-body-padding-top: 0.25rem;
2843 |   --topic-quote-body-padding-bottom: 0.5rem;
2844 |   --topic-spoiler-body-open-padding-top: 0.375rem;
2845 |   --topic-actions-top-gap: 0.25rem;
2846 |   --topic-paragraph-spacing: var(--topic-paragraph-gap);
2847 |   --topic-inline-block-spacing: var(--topic-inline-block-gap);
2848 |   --topic-quote-spacing: var(--topic-quote-gap);
2849 | }
2850 | body#topic .post_body {
2851 |   padding: var(--topic-body-padding-y) var(--topic-body-padding-x);
2852 | }
2853 | body#topic .post_body p {
2854 |   margin-top: 0;
2855 |   margin-bottom: var(--topic-paragraph-gap);
2856 | }
2857 | body#topic .post_body ul,
2858 | body#topic .post_body ol {
2859 |   margin-top: var(--topic-paragraph-gap);
2860 |   margin-bottom: var(--topic-paragraph-gap);
2861 | }
2862 | body#topic .post_body > .post-block,
2863 | body#topic .post_body .attach_block,
2864 | body#topic .post_body img.linked-image,
2865 | body#topic .post_body img.attach {
2866 |   margin-top: var(--topic-inline-block-gap);
2867 |   margin-bottom: var(--topic-inline-block-gap);
2868 | }
2869 | body#topic .post_body > .post-block + br,
2870 | body#topic .post_body > .post-block + br + br,
2871 | body#topic .post_body .attach_block + br,
2872 | body#topic .post_body .attach_block + br + br,
2873 | body#topic .post_body img.linked-image + br,
2874 | body#topic .post_body img.linked-image + br + br,
2875 | body#topic .post_body img.attach + br,
2876 | body#topic .post_body img.attach + br + br {
2877 |   display: none;
2878 | }
2879 | body#topic .post_body > .post-block + p,
2880 | body#topic .post_body > .post-block + br + p,
2881 | body#topic .post_body > .post-block + br + br + p,
2882 | body#topic .post_body .attach_block + p,
2883 | body#topic .post_body .attach_block + br + p,
2884 | body#topic .post_body .attach_block + br + br + p,
2885 | body#topic .post_body img.linked-image + p,
2886 | body#topic .post_body img.linked-image + br + p,
2887 | body#topic .post_body img.linked-image + br + br + p,
2888 | body#topic .post_body img.attach + p,
2889 | body#topic .post_body img.attach + br + p,
2890 | body#topic .post_body img.attach + br + br + p {
2891 |   margin-top: 0;
2892 | }
2893 | body#topic .post_body > .post-block + .post-block,
2894 | body#topic .post_body > .post-block + .attach_block,
2895 | body#topic .post_body .attach_block + .post-block,
2896 | body#topic .post_body .attach_block + .attach_block {
2897 |   margin-top: var(--topic-block-gap);
2898 | }
2899 | body#topic .post_body p + .edit,
2900 | body#topic .post_body p + .post-edit-reason,
2901 | body#topic .post_body p + strong .edit,
2902 | body#topic .post_body > .post-block + .edit,
2903 | body#topic .post_body > .post-block + .post-edit-reason,
2904 | body#topic .post_body > .post-block + strong .edit,
2905 | body#topic .post_body .attach_block + .edit,
2906 | body#topic .post_body .attach_block + .post-edit-reason,
2907 | body#topic .post_body .attach_block + strong .edit {
2908 |   padding-top: var(--topic-edit-info-spacing);
2909 | }
2910 | body#topic .post_body .signature,
2911 | body#topic .post_body .post-signature,
2912 | body#topic .post_body .post_signature {
2913 |   margin-top: var(--topic-block-gap);
2914 |   padding-top: var(--topic-paragraph-gap);
2915 | }
2916 | body#topic .post-block {
2917 |   margin-top: var(--topic-block-gap);
2918 |   margin-bottom: var(--topic-block-gap);
2919 | }
2920 | body#topic .post-block > .block-title {
2921 |   padding: var(--topic-block-title-padding-y) var(--topic-block-title-padding-x);
2922 | }
2923 | body#topic .post-block > .block-body {
2924 |   padding: var(--topic-block-body-padding-top) var(--topic-block-body-padding-x) var(--topic-block-body-padding-bottom) var(--topic-block-body-padding-x);
2925 | }
2926 | body#topic .post-block.quote {
2927 |   margin-top: var(--topic-quote-gap);
2928 |   margin-bottom: var(--topic-quote-gap);
2929 |   border-left: 0;
2930 |   box-sizing: border-box;
2931 |   overflow: hidden;
2932 |   touch-action: pan-y;
2933 |   padding-left: 0.25rem;
2934 | }
2935 | body#topic .post-block.quote:before {
2936 |   content: "";
2937 |   position: absolute;
2938 |   top: 0;
2939 |   bottom: 0;
2940 |   left: 0;
2941 |   width: 0.25rem;
2942 |   background: #E0E0E0;
2943 |   pointer-events: none;
2944 | }
2945 | body#topic .post-block.quote > .block-title {
2946 |   padding-top: var(--topic-quote-title-padding-top);
2947 |   padding-bottom: var(--topic-quote-title-padding-bottom);
2948 | }
2949 | body#topic .post-block.quote > .block-body {
2950 |   padding-top: var(--topic-quote-body-padding-top);
2951 |   padding-bottom: var(--topic-quote-body-padding-bottom);
2952 | }
2953 | body#topic .post-block.quote > .block-body > .post-block.quote {
2954 |   margin-top: var(--topic-quote-gap);
2955 |   margin-bottom: var(--topic-inline-block-gap);
2956 | }
2957 | body#topic .post-block.code > .block-title {
2958 |   padding-top: var(--topic-block-title-padding-y);
2959 |   padding-bottom: var(--topic-block-title-padding-y);
2960 | }
2961 | body#topic .post-block.code > .block-body {
2962 |   padding-top: var(--topic-block-body-padding-top);
2963 |   padding-bottom: var(--topic-block-body-padding-bottom);
2964 | }
2965 | body#topic .post-block.spoil {
2966 |   margin-top: var(--topic-inline-block-gap);
2967 |   margin-bottom: var(--topic-inline-block-gap);
2968 | }
2969 | body#topic .post-block.spoil.open > .block-body {
2970 |   padding-top: var(--topic-spoiler-body-open-padding-top);
2971 | }
2972 | body#topic .post-block.spoil > .block-body > .post-block.spoil {
2973 |   margin-top: var(--topic-block-gap);
2974 |   margin-bottom: var(--topic-block-gap);
2975 | }
2976 | body#topic.density_compact {
2977 |   --topic-paragraph-gap: 0.3125rem;
2978 |   --topic-block-gap: 0.375rem;
2979 |   --topic-inline-block-gap: 0.3125rem;
2980 |   --topic-quote-gap: 0.3125rem;
2981 |   --topic-body-padding-x: 0.75rem;
2982 |   --topic-body-padding-y: 0.5rem;
2983 |   --topic-block-title-padding-y: 0.375rem;
2984 |   --topic-block-body-padding-top: 0.3125rem;
2985 |   --topic-block-body-padding-bottom: 0.4375rem;
2986 |   --topic-quote-title-padding-top: 0.3125rem;
2987 |   --topic-quote-title-padding-bottom: 0.3125rem;
2988 |   --topic-quote-body-padding-top: 0.1875rem;
2989 |   --topic-quote-body-padding-bottom: 0.375rem;
2990 |   --topic-spoiler-body-open-padding-top: 0.3125rem;
2991 |   --topic-actions-top-gap: 0.125rem;
2992 | }
2993 | body#topic.density_compact .post_container .post_body,
2994 | body#topic.density_super_compact .post_container .post_body {
2995 |   padding: var(--topic-body-padding-y) var(--topic-body-padding-x);
2996 | }
2997 | body#topic.density_compact .post_container .post_body .postcolor,
2998 | body#topic.density_super_compact .post_container .post_body .postcolor {
2999 |   margin: calc(0rem - var(--topic-body-padding-y)) calc(0rem - var(--topic-body-padding-x));
3000 |   padding: var(--topic-body-padding-y) var(--topic-body-padding-x);
3001 | }
3002 | body#topic.density_compact .post_container .post_footer .post_actions_row {
3003 |   margin-top: var(--topic-actions-top-gap);
3004 | }
3005 | body#topic.density_compact .post-block,
3006 | body#topic.density_super_compact .post-block {
3007 |   margin-top: var(--topic-block-gap);
3008 |   margin-bottom: var(--topic-block-gap);
3009 | }
3010 | body#topic.density_compact .post-block > .block-title,
3011 | body#topic.density_super_compact .post-block > .block-title {
3012 |   padding: var(--topic-block-title-padding-y) var(--topic-block-title-padding-x);
3013 | }
3014 | body#topic.density_compact .post-block > .block-body,
3015 | body#topic.density_super_compact .post-block > .block-body {
3016 |   padding: var(--topic-block-body-padding-top) var(--topic-block-body-padding-x) var(--topic-block-body-padding-bottom) var(--topic-block-body-padding-x);
3017 | }
3018 | body#topic.density_compact .post-block.spoil.close,
3019 | body#topic.density_super_compact .post-block.spoil.close {
3020 |   margin-bottom: var(--topic-inline-block-gap);
3021 | }
3022 | body#topic.density_compact .post-block.quote,
3023 | body#topic.density_super_compact .post-block.quote {
3024 |   margin-top: var(--topic-quote-gap);
3025 |   margin-bottom: var(--topic-quote-gap);
3026 | }
3027 | body#topic.density_compact .post-block.quote > .block-title,
3028 | body#topic.density_super_compact .post-block.quote > .block-title {
3029 |   padding-top: var(--topic-quote-title-padding-top);
3030 |   padding-bottom: var(--topic-quote-title-padding-bottom);
3031 | }
3032 | body#topic.density_compact .post-block.quote > .block-body,
3033 | body#topic.density_super_compact .post-block.quote > .block-body {
3034 |   padding-top: var(--topic-quote-body-padding-top);
3035 |   padding-bottom: var(--topic-quote-body-padding-bottom);
3036 | }
3037 | body#topic.density_compact .post-block.code > .block-title {
3038 |   padding-top: var(--topic-block-title-padding-y);
3039 |   padding-bottom: var(--topic-block-title-padding-y);
3040 | }
3041 | body#topic.density_compact .post-block.code > .block-body {
3042 |   padding-top: var(--topic-block-body-padding-top);
3043 |   padding-bottom: var(--topic-block-body-padding-bottom);
3044 | }
3045 | body#topic.density_super_compact {
3046 |   --topic-paragraph-gap: 0.1875rem;
3047 |   --topic-block-gap: 0.25rem;
3048 |   --topic-inline-block-gap: 0.1875rem;
3049 |   --topic-quote-gap: 0.1875rem;
3050 |   --topic-body-padding-x: 0.625rem;
3051 |   --topic-body-padding-y: 0.375rem;
3052 |   --topic-block-title-padding-x: 0.625rem;
3053 |   --topic-block-title-padding-y: 0.25rem;
3054 |   --topic-block-body-padding-x: 0.625rem;
3055 |   --topic-block-body-padding-top: 0.1875rem;
3056 |   --topic-block-body-padding-bottom: 0.3125rem;
3057 |   --topic-quote-title-padding-top: 0.1875rem;
3058 |   --topic-quote-title-padding-bottom: 0.1875rem;
3059 |   --topic-quote-body-padding-top: 0.125rem;
3060 |   --topic-quote-body-padding-bottom: 0.25rem;
3061 |   --topic-spoiler-body-open-padding-top: 0.1875rem;
3062 |   --topic-actions-top-gap: 0.0625rem;
3063 | }
3064 | body#topic.density_super_compact .post_container .post_footer .post_actions_row {
3065 |   margin-top: var(--topic-actions-top-gap);
3066 | }
3067 | body#topic.density_super_compact .post-block.code > .block-body {
3068 |   padding-top: var(--topic-block-body-padding-top);
3069 |   padding-bottom: var(--topic-block-body-padding-bottom);
3070 | }
3071 | .theme_bottom_pagination {
3072 |   -webkit-touch-callout: none;
3073 |   -webkit-user-select: none;
3074 |   -khtml-user-select: none;
3075 |   -moz-user-select: none;
3076 |   -ms-user-select: none;
3077 |   user-select: none;
3078 |   margin: 0;
3079 |   padding: 0;
3080 |   background: transparent;
3081 | }
3082 | .theme_bottom_pagination .theme_bottom_pagination_row {
3083 |   display: flex;
3084 |   align-items: center;
3085 |   justify-content: stretch;
3086 |   min-height: 2.25rem;
3087 | }
3088 | .theme_bottom_pagination button {
3089 |   -webkit-touch-callout: none;
3090 |   -webkit-user-select: none;
3091 |   -khtml-user-select: none;
3092 |   -moz-user-select: none;
3093 |   -ms-user-select: none;
3094 |   user-select: none;
3095 |   border: 0;
3096 |   border-radius: 0;
3097 |   flex: 1 1 0;
3098 |   min-width: 0;
3099 |   height: 2.25rem;
3100 |   padding: 0;
3101 |   color: #ffffff;
3102 |   background: transparent;
3103 |   font: inherit;
3104 |   font-weight: 700;
3105 |   line-height: 2.25rem;
3106 |   text-align: center;
3107 |   outline: none;
3108 |   -webkit-tap-highlight-color: transparent;
3109 | }
3110 | .theme_bottom_pagination button.theme_bottom_pagination_current {
3111 |   flex: 1.45 1 0;
3112 |   color: #ffffff;
3113 |   background: transparent;
3114 | }
3115 | .theme_bottom_pagination button.disabled {
3116 |   color: #ffffff;
3117 |   opacity: 0.39;
3118 | }
3119 | .theme_bottom_pagination button:not(.disabled):active {
3120 |   background: rgba(255, 255, 255, 0.05);
3121 | }
3122 | .theme_bottom_pagination button > span {
3123 |   font-size: 1.5rem;
3124 |   line-height: 2.25rem;
3125 | }
3126 | .theme_bottom_pagination button.theme_bottom_pagination_current > span {
3127 |   font-size: 0.875rem;
3128 |   font-weight: 700;
3129 |   line-height: 2.25rem;
3130 |   white-space: nowrap;
3131 |   overflow: visible;
3132 |   text-overflow: clip;
3133 | }
3134 | .posts_list .theme_page_container {
3135 |   margin: 0;
3136 |   padding: 0;
3137 | }
3138 | .posts_list .theme_page_separator {
3139 |   color: #b2b2b2;
3140 |   font-size: 0.875rem;
3141 |   font-weight: bold;
3142 |   line-height: 1.35;
3143 |   margin: 0.5rem 0.25rem;
3144 |   padding: 0.25rem 1rem;
3145 |   text-align: center;
3146 |   display: flex;
3147 |   align-items: center;
3148 |   justify-content: center;
3149 | }
3150 | .posts_list.search-results .topic_title_post {
3151 |   border-bottom: 0.0625rem solid rgba(255, 255, 255, 0.05);
3152 |   font-weight: bold;
3153 | }
3154 | .posts_list.search-results .post_container .post_header {
3155 |   padding: 0.5em 1em;
3156 | }
3157 | .posts_list.search-results .post_container .post_header .s_inf.nick {
3158 |   font-weight: bold;
3159 |   color: #E0E0E0;
3160 | }
3161 | .posts_list.search-results .post_container .post_header .s_inf.nick.online {
3162 |   color: #12b557;
3163 | }
3164 | .posts_list.search-results .post_container .post_header .s_inf.date {
3165 |   float: right;
3166 | }
3167 | .posts_list.search-results .post_container .post_header .s_inf.date > span {
3168 |   font-size: 0.875em;
3169 | }
3170 | .posts_list.search-results .post_container .s_post_footer {
3171 |   border-top: 0.0625rem solid rgba(255, 255, 255, 0.05);
3172 |   padding: 0.75em 1em;
3173 |   line-height: 1.5em;
3174 | }
3175 | .posts_list.search-results .bad-search-result {
3176 |   background: #1E1E1E;
3177 |   margin: 0.5em 0;
3178 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
3179 |   padding: 1em;
3180 |   margin: 0;
3181 | }
3182 | .posts_list.search-results .bad-search-result h3 {
3183 |   margin: 0;
3184 |   padding-bottom: 0.5em;
3185 |   color: #E0E0E0;
3186 | }
3187 | .posts_list.search-results .bad-search-result span {
3188 |   color: #b2b2b2;
3189 | }
3190 | .posts_list.search-results ~ #bottomMargin {
3191 |   height: 5.5em;
3192 | }
3193 | .posts_list.search-results .post_body {
3194 |   padding: 1em;
3195 | }
3196 | .navigation {
3197 |   background: #1e1e1e;
3198 |   margin: 0.5em 0.25rem 0 0.25rem;
3199 |   box-shadow: none;
3200 |   border: none;
3201 |   -webkit-border-radius: 0.875rem;
3202 |   border-radius: 0.875rem;
3203 |   display: flex;
3204 |   flex-flow: wrap;
3205 |   overflow: hidden;
3206 |   min-height: 3rem;
3207 | }
3208 | #padding_for_message_panel {
3209 |   display: none;
3210 |   height: 0;
3211 |   margin: 0;
3212 |   padding: 0;
3213 |   background: transparent;
3214 | }
3215 | #bottom_chrome_spacer {
3216 |   height: 0;
3217 |   margin: 0;
3218 |   padding: 0;
3219 |   background: transparent;
3220 |   pointer-events: none;
3221 | }
3222 | #theme_top_chrome_spacer {
3223 |   height: var(--theme-top-chrome-padding, 0px);
3224 |   margin: 0;
3225 |   padding: 0;
3226 |   background: transparent;
3227 |   pointer-events: none;
3228 | }
3229 | body#topic.topic_hat_overlay_open {
3230 |   overflow: hidden;
3231 | }
3232 | body.topic_hat_overlay_open #theme_top_chrome_spacer {
3233 |   display: none;
3234 | }
3235 | .navigation.disabled {
3236 |   display: none;
3237 | }
3238 | .navigation .button {
3239 |   -webkit-touch-callout: none;
3240 |   -webkit-user-select: none;
3241 |   -khtml-user-select: none;
3242 |   -moz-user-select: none;
3243 |   -ms-user-select: none;
3244 |   user-select: none;
3245 |   height: 3rem;
3246 |   line-height: 3rem;
3247 |   display: block;
3248 |   flex: 1 1 0px;
3249 |   box-sizing: border-box;
3250 |   -webkit-border-radius: 0.75rem;
3251 |   border-radius: 0.75rem;
3252 |   position: relative;
3253 |   white-space: nowrap;
3254 |   text-align: center;
3255 |   text-transform: uppercase;
3256 |   color: #E0E0E0;
3257 |   font-size: 0.875rem;
3258 |   font-weight: 700;
3259 | }
3260 | .navigation .button > .icon {
3261 |   height: 100%;
3262 |   width: 100%;
3263 |   display: block;
3264 |   margin: 0 auto;
3265 |   background-position: center;
3266 |   background-size: 1.5rem;
3267 |   background-repeat: no-repeat;
3268 | }
3269 | .navigation .button > b {
3270 |   display: inline-block;
3271 | }
3272 | .navigation .button:not(.disabled):active {
3273 |   background: rgba(255, 255, 255, 0.1);
3274 | }
3275 | .navigation .button:before {
3276 |   content: "";
3277 |   position: absolute;
3278 |   left: 0;
3279 |   top: 0;
3280 |   right: 0;
3281 |   bottom: 0;
3282 |   margin: -0.375rem -0.5rem;
3283 |   /*background: red;
3284 |                 opacity: 0.05;*/
3285 | }
3286 | .navigation .button.disabled > .icon {
3287 |   opacity: 0.31;
3288 | }
3289 | .navigation .button.hidden {
3290 |   display: none;
3291 | }
3292 | .navigation .button.page > .icon {
3293 |   display: none;
3294 | }
3295 | .navigation .button.first > .icon {
3296 |   background-image: url("../../res/dark/chevron-double-left.svg");
3297 | }
3298 | .navigation .button.prev > .icon {
3299 |   background-image: url("../../res/dark/chevron-left.svg");
3300 | }
3301 | .navigation .button.next > .icon {
3302 |   background-image: url("../../res/dark/chevron-right.svg");
3303 | }
3304 | .navigation .button.last > .icon {
3305 |   background-image: url("../../res/dark/chevron-double-right.svg");
3306 | }
3307 | @media all and (max-width: 20rem) {
3308 |   .post_container .post_header .avatar .reputation:after {
3309 |     height: 1.125rem;
3310 |     min-width: 1.125rem;
3311 |   }
3312 |   .post_container .post_header .inf.nick .online_dot:after {
3313 |     height: 1em;
3314 |     width: 1em;
3315 |     left: 0;
3316 |     top: -0.25rem;
3317 |   }
3318 | }
3319 | @media all and (max-width: 14rem) {
3320 |   .poll > .title {
3321 |     padding-left: 0.5rem;
3322 |     /*>span{
3323 |             display: inline;
3324 |         }*/
3325 |   }
3326 |   .poll > .body .questions .question > .title {
3327 |     padding-left: 0.5rem;
3328 |     padding-right: 0.5rem;
3329 |   }
3330 |   .poll > .body .questions .question > .items {
3331 |     padding-left: 0.5rem;
3332 |     padding-right: 0.5rem;
3333 |   }
3334 |   .post_body {
3335 |     padding-left: 0.5rem;
3336 |     padding-right: 0.5rem;
3337 |   }
3338 |   .post_body .postcolor {
3339 |     margin: -0.5em;
3340 |     padding: 1em;
3341 |   }
3342 |   .post_container .hat_button {
3343 |     padding-left: 0.5rem;
3344 |   }
3345 |   .post_container .post_header {
3346 |     padding-left: 0.5rem;
3347 |     padding-right: 0.5rem;
3348 |   }
3349 |   .post_container .post_header .inf.nick {
3350 |     position: relative;
3351 |     top: auto;
3352 |     left: auto;
3353 |     display: block;
3354 |   }
3355 |   .post_container .post_header .inf.menu {
3356 |     right: -0.5rem;
3357 |   }
3358 |   /*.post_container .post_header .header_wrapper{
3359 |         border-bottom: @border1;
3360 |         padding-bottom: 8/16rem;
3361 |     }*/
3362 | }
3363 | .topic_page_counter {
3364 |   background: #1E1E1E;
3365 |   margin: 0.5em 0;
3366 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
3367 |   -webkit-border-radius: 0.875rem;
3368 |   border-radius: 0.875rem;
3369 |   color: #b3b3b3;
3370 |   font-weight: bold;
3371 |   padding: 0.75em 1em;
3372 | }
3373 | .topic_hat_fixed.top_hat_overlay_host {
3374 |   background: #1E1E1E;
3375 |   margin: 0;
3376 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
3377 |   overflow: hidden;
3378 |   position: fixed;
3379 |   top: 0;
3380 |   left: 0;
3381 |   right: 0;
3382 |   z-index: 21;
3383 |   box-sizing: border-box;
3384 |   -webkit-border-radius: 0 0 0.875rem 0.875rem;
3385 |   border-radius: 0 0 0.875rem 0.875rem;
3386 |   -webkit-transform: translate3d(0, 0, 0);
3387 |   transform: translate3d(0, 0, 0);
3388 |   -webkit-backface-visibility: hidden;
3389 |   backface-visibility: hidden;
3390 |   --theme-hat-max-height: calc(100vh - var(--theme-top-chrome-padding, 0px) - var(--theme-bottom-chrome-padding, 0px) - 1rem);
3391 | }
3392 | .topic_hat_fixed.top_hat_overlay_host.close {
3393 |   display: none;
3394 |   pointer-events: none;
3395 | }
3396 | .topic_hat_fixed.top_hat_overlay_host.open {
3397 |   display: -webkit-box;
3398 |   display: -webkit-flex;
3399 |   display: flex;
3400 |   -webkit-box-orient: vertical;
3401 |   -webkit-box-direction: normal;
3402 |   -webkit-flex-direction: column;
3403 |   flex-direction: column;
3404 |   max-height: var(--theme-hat-max-height);
3405 |   pointer-events: auto;
3406 |   opacity: 1;
3407 |   -webkit-transform: translate3d(0, 0, 0);
3408 |   transform: translate3d(0, 0, 0);
3409 | }
3410 | .topic_hat_fixed.top_hat_overlay_host.open.theme_hat_overlay_preparing {
3411 |   -webkit-animation: none;
3412 |   animation: none;
3413 |   -webkit-transition: none;
3414 |   transition: none;
3415 | }
3416 | .topic_hat_fixed.top_hat_overlay_host.open.theme_hat_overlay_enter {
3417 |   -webkit-animation: theme_hat_overlay_in 0.28s cubic-bezier(0.4, 0, 0.2, 1) forwards;
3418 |   animation: theme_hat_overlay_in 0.28s cubic-bezier(0.4, 0, 0.2, 1) forwards;
3419 | }
3420 | .topic_hat_fixed.top_hat_overlay_host.open {
3421 |   border-bottom: 0.0625rem solid rgba(255, 255, 255, 0.05);
3422 |   overscroll-behavior: contain;
3423 | }
3424 | .topic_hat_fixed.top_hat_overlay_host.open .hat_content {
3425 |   -webkit-box-flex: 1;
3426 |   -webkit-flex: 1 1 auto;
3427 |   flex: 1 1 auto;
3428 |   min-height: 0;
3429 |   max-height: var(--theme-hat-max-height);
3430 |   padding-top: var(--theme-top-chrome-padding, 0px);
3431 |   padding-bottom: 0.5rem;
3432 |   overflow-y: auto;
3433 |   overflow-x: hidden;
3434 |   -webkit-overflow-scrolling: touch;
3435 |   overscroll-behavior: contain;
3436 |   touch-action: pan-y;
3437 |   background: inherit;
3438 | }
3439 | .topic_hat_fixed.top_hat_overlay_host.initial_open {
3440 |   position: fixed;
3441 | }
3442 | .topic_hat_fixed.top_hat_overlay_host.initial_open .hat_content {
3443 |   overflow-y: auto;
3444 | }
3445 | @-webkit-keyframes theme_hat_overlay_in {
3446 |   from {
3447 |     opacity: 0;
3448 |     -webkit-transform: translate3d(0, -4px, 0);
3449 |     transform: translate3d(0, -4px, 0);
3450 |   }
3451 |   to {
3452 |     opacity: 1;
3453 |     -webkit-transform: translate3d(0, 0, 0);
3454 |     transform: translate3d(0, 0, 0);
3455 |   }
3456 | }
3457 | @keyframes theme_hat_overlay_in {
3458 |   from {
3459 |     opacity: 0;
3460 |     -webkit-transform: translate3d(0, -4px, 0);
3461 |     transform: translate3d(0, -4px, 0);
3462 |   }
3463 |   to {
3464 |     opacity: 1;
3465 |     -webkit-transform: translate3d(0, 0, 0);
3466 |     transform: translate3d(0, 0, 0);
3467 |   }
3468 | }
3469 | @media all and (min-width: 11.5rem) {
3470 |   .navigation .button:not(.hidden) + .button {
3471 |     margin-left: 0;
3472 |   }
3473 | }
3474 | @media all and (max-width: 11.5rem) {
3475 |   .navigation {
3476 |     display: block;
3477 |   }
3478 |   .navigation .button {
3479 |     float: left;
3480 |     width: 50%;
3481 |   }
3482 |   .navigation .button.page {
3483 |     width: 100%;
3484 |   }
3485 | }
3486 | /* -----------------------------------------------------------------------
3487 |  * Topic post highlight — TRANSIENT ~2-second frame on the whole post block.
3488 |  *
3489 |  * Uses an absolutely-positioned `::after` inset border so all four sides stay
3490 |  * visible. Outer box-shadow was clipped at the left/right viewport edges because
3491 |  * topic posts span full width (only vertical margin). `.post_container` is
3492 |  * `overflow: hidden`; the overlay is inset 1px so the 2px border sits inside
3493 |  * the rounded clip rect. Only overlay `opacity` fades (`post-highlight-fading`);
3494 |  * post content stays fully opaque. Replaces the clearfix `::after` while active.
3495 |  * -----------------------------------------------------------------------
3496 |  */
3497 | .post_container.post-highlight-first-unread,
3498 | .post_container.post-highlight-last-read,
3499 | .post_container.post-highlight-explicit {
3500 |   position: relative;
3501 |   box-sizing: border-box;
3502 | }
3503 | .post_container.post-highlight-first-unread::after,
3504 | .post_container.post-highlight-last-read::after,
3505 | .post_container.post-highlight-explicit::after {
3506 |   content: "";
3507 |   position: absolute;
3508 |   top: 1px;
3509 |   right: 1px;
3510 |   bottom: 1px;
3511 |   left: 1px;
3512 |   border-radius: 0.8125rem;
3513 |   z-index: 100;
3514 |   pointer-events: none;
3515 |   border: 2px solid transparent;
3516 |   opacity: 1;
3517 |   transition: opacity 250ms ease-out;
3518 |   display: block;
3519 | }
3520 | .post_container.post-highlight-first-unread::after {
3521 |   border-color: var(--fpda-highlight-first-unread-accent, var(--fpda-accent-primary, #ff7043));
3522 | }
3523 | .post_container.post-highlight-last-read::after {
3524 |   border-color: var(--fpda-highlight-last-read-accent, var(--fpda-accent-muted, #9aa0a6));
3525 | }
3526 | .post_container.post-highlight-explicit::after {
3527 |   border-color: var(--fpda-highlight-explicit-accent, var(--fpda-accent-neutral, #4fc3f7));
3528 | }
3529 | .post_container.post-highlight-fading::after {
3530 |   opacity: 0;
3531 | }
3532 | @media (prefers-reduced-motion: reduce) {
3533 |   .post_container.post-highlight-first-unread::after,
3534 |   .post_container.post-highlight-last-read::after,
3535 |   .post_container.post-highlight-explicit::after {
3536 |     transition: none;
3537 |   }
3538 | }
3539 | 
```

### app/src/main/assets/forpda/styles/light/light_themes.css

Bytes: 108957
SHA-256: 2daa26ebb95ad8da1f45853aa8fd5098221c3eb578030f1372ee30b603bb618a
Lines: 1-3534 of 3534

```css
   1 | /* Generated by less 2.5.1 */
   2 | /*LIGHT*/
   3 | /*DARK*/
   4 | /*OTHERS*/
   5 | /*Curves*/
   6 | /*Fonts*/
   7 | /*Color scheme*/
   8 | /* NEW COLORS */
   9 | /*Elements colors*/
  10 | /*Post blocks*/
  11 | .noselect {
  12 |   -webkit-touch-callout: none;
  13 |   -webkit-user-select: none;
  14 |   -khtml-user-select: none;
  15 |   -moz-user-select: none;
  16 |   -ms-user-select: none;
  17 |   user-select: none;
  18 | }
  19 | body {
  20 |   background: #f2f2f7;
  21 |   /* Theme accent palette for the topic post-highlight frame, defined at body
  22 |    * scope so it cascades to `.post_container::after`. Follows the light theme's
  23 |    * own chrome accent (the post action icon color #616161); a per-theme
  24 |    * override of `--fpda-theme-accent` (or the chrome icon color) re-points all
  25 |    * three. Each highlight rule still carries a hardcoded hex as the ultimate
  26 |    * fallback (var chain: theme accent -> chrome icon color -> hex). */
  27 |   --fpda-theme-accent: var(--topic-action-icon-color, #616161);
  28 |   --fpda-accent-primary: var(--fpda-theme-accent, #e64a19);
  29 |   --fpda-accent-muted: var(--fpda-theme-accent, #8a8f94);
  30 |   --fpda-accent-neutral: var(--fpda-theme-accent, #0288d1);
  31 | }
  32 | .rep-action-symbols {
  33 |   position: absolute;
  34 |   width: 0;
  35 |   height: 0;
  36 |   overflow: hidden;
  37 | }
  38 | .hat_content.close {
  39 |   display: none;
  40 | }
  41 | #search .panel.bottom:empty + #bottomMargin {
  42 |   display: none;
  43 | }
  44 | #search .hat_content.close {
  45 |   display: block;
  46 |   max-height: 22.5rem;
  47 |   overflow: hidden;
  48 |   position: relative;
  49 | }
  50 | #search .hat_content.close.over_height:after {
  51 |   content: "";
  52 |   position: absolute;
  53 |   left: 0;
  54 |   bottom: 0;
  55 |   right: 0;
  56 |   height: 6rem;
  57 |   box-shadow: inset 0 -6rem 3rem -3rem #ffffff;
  58 |   z-index: 100;
  59 | }
  60 | #search .hat_content.open {
  61 |   display: block;
  62 |   max-height: none;
  63 |   height: auto;
  64 |   min-height: 0;
  65 |   overflow: visible;
  66 |   position: relative;
  67 | }
  68 | #search .hat_content.open:after {
  69 |   content: none !important;
  70 |   display: none !important;
  71 | }
  72 | #search .post_container.open > .hat_content {
  73 |   max-height: none !important;
  74 |   height: auto !important;
  75 |   overflow: visible !important;
  76 | }
  77 | #search .post_container.open .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body {
  78 |   max-height: none !important;
  79 |   overflow: visible !important;
  80 | }
  81 | #search .post_container.open .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body:after {
  82 |   content: none !important;
  83 |   display: none !important;
  84 | }
  85 | #search .search_jump_to_post {
  86 |   margin-top: 0.35rem;
  87 |   margin-bottom: 0.65rem;
  88 |   margin-left: 0.5rem;
  89 |   margin-right: 0.5rem;
  90 |   padding: 0.4rem 0.85rem 0.55rem;
  91 |   display: flex;
  92 |   align-items: center;
  93 |   justify-content: space-between;
  94 |   gap: 0.5rem;
  95 |   flex-wrap: wrap;
  96 | }
  97 | #search a.search_post_btn {
  98 |   display: inline-block;
  99 |   padding: 0.35rem 0.75rem;
 100 |   border-radius: 6px;
 101 |   font-size: 0.75rem;
 102 |   font-weight: 600;
 103 |   letter-spacing: 0.02em;
 104 |   text-decoration: none;
 105 |   text-transform: none;
 106 |   line-height: 1.25;
 107 |   border: 1px solid rgba(0, 0, 0, 0.12);
 108 |   background: rgba(0, 0, 0, 0.045);
 109 |   color: #1565c0;
 110 |   -webkit-tap-highlight-color: rgba(0, 0, 0, 0.06);
 111 | }
 112 | #search a.search_post_btn:active {
 113 |   opacity: 0.88;
 114 |   background: rgba(0, 0, 0, 0.08);
 115 | }
 116 | #search .search_post_id_hint {
 117 |   font-size: 0.72rem;
 118 |   opacity: 0.55;
 119 |   color: #666666;
 120 | }
 121 | button,
 122 | .btn {
 123 |   display: block;
 124 |   padding: 0.875em 1em;
 125 |   font-size: 1em;
 126 |   background: none;
 127 |   border: none;
 128 |   font-weight: bold;
 129 |   text-transform: uppercase;
 130 |   -webkit-tap-highlight-color: rgba(0, 0, 0, 0);
 131 |   outline: none!important;
 132 |   text-align: center;
 133 |   color: #616161;
 134 | }
 135 | button > span,
 136 | .btn > span,
 137 | button > b,
 138 | .btn > b {
 139 |   font-size: 0.875em;
 140 | }
 141 | button > *,
 142 | .btn > * {
 143 |   display: inline;
 144 |   vertical-align: top;
 145 | }
 146 | button:active,
 147 | .btn:active {
 148 |   background: rgba(0, 0, 0, 0.1);
 149 | }
 150 | @-webkit-keyframes pollRangeAnimation {
 151 |   0% {
 152 |     -webkit-transform: scaleX(0.1) translateZ(0);
 153 |     transform: scaleX(0.1) translateZ(0);
 154 |   }
 155 |   100% {
 156 |     -webkit-transform: scaleX(1) translateZ(0);
 157 |     transform: scaleX(1) translateZ(0);
 158 |   }
 159 | }
 160 | @-webkit-keyframes pollRangeValueAnimation {
 161 |   0% {
 162 |     visibility: visible;
 163 |     opacity: 0;
 164 |   }
 165 |   100% {
 166 |     visibility: visible;
 167 |     opacity: 1;
 168 |   }
 169 | }
 170 | @-webkit-keyframes radAnim1 {
 171 |   0% {
 172 |     -webkit-transform: scale(1);
 173 |     transform: scale(1);
 174 |   }
 175 |   50% {
 176 |     -webkit-transform: scale(0.95);
 177 |     transform: scale(0.95);
 178 |   }
 179 |   100% {
 180 |     -webkit-transform: scale(1);
 181 |     transform: scale(1);
 182 |   }
 183 | }
 184 | .poll {
 185 |   background: #EEF7F6;
 186 |   margin: 0.5em 0;
 187 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
 188 |   -webkit-border-radius: 0.875rem;
 189 |   border-radius: 0.875rem;
 190 |   overflow: hidden;
 191 | }
 192 | .poll.poll_overlay_host {
 193 |   position: fixed;
 194 |   top: 0;
 195 |   left: 0.5em;
 196 |   right: 0.5em;
 197 |   z-index: 21;
 198 |   --theme-poll-max-height: calc(100vh - var(--theme-bottom-chrome-padding, 0px) - 1rem);
 199 |   margin: 0.5em 0;
 200 |   box-sizing: border-box;
 201 | }
 202 | .poll.poll_overlay_host.open {
 203 |   display: flex;
 204 |   flex-direction: column;
 205 |   max-height: var(--theme-poll-max-height);
 206 |   border-bottom: 0.0625rem solid rgba(0, 0, 0, 0.05);
 207 |   overscroll-behavior: contain;
 208 |   pointer-events: auto;
 209 | }
 210 | .poll.poll_overlay_host.open > .body {
 211 |   flex: 1 1 auto;
 212 |   min-height: 0;
 213 |   max-height: calc(var(--theme-poll-max-height) - 3rem);
 214 |   padding-bottom: 0.5rem;
 215 |   overflow-y: auto;
 216 |   overflow-x: hidden;
 217 |   -webkit-overflow-scrolling: touch;
 218 |   overscroll-behavior: contain;
 219 |   touch-action: pan-y;
 220 | }
 221 | .poll.poll_overlay_host.close {
 222 |   display: none;
 223 |   pointer-events: none;
 224 | }
 225 | .poll.poll_entry.close {
 226 |   display: block;
 227 | }
 228 | .poll.poll_entry.close > .body {
 229 |   display: none;
 230 | }
 231 | .poll > .title {
 232 |   -webkit-touch-callout: none;
 233 |   -webkit-user-select: none;
 234 |   -khtml-user-select: none;
 235 |   -moz-user-select: none;
 236 |   -ms-user-select: none;
 237 |   user-select: none;
 238 |   display: flex;
 239 |   align-items: center;
 240 |   justify-content: space-between;
 241 |   gap: 0.5rem;
 242 |   box-sizing: border-box;
 243 |   min-height: 3rem;
 244 |   color: #616161;
 245 |   position: relative;
 246 |   text-align: left;
 247 |   line-height: 1.25rem;
 248 |   padding-right: 0.5rem;
 249 | }
 250 | .poll > .title > span {
 251 |   flex: 1 1 auto;
 252 |   min-width: 0;
 253 |   overflow: hidden;
 254 |   text-overflow: ellipsis;
 255 |   white-space: nowrap;
 256 |   color: inherit;
 257 |   line-height: 1.25rem;
 258 | }
 259 | .poll > .title .icon {
 260 |   display: flex;
 261 |   align-items: center;
 262 |   justify-content: center;
 263 |   flex: 0 0 2.5rem;
 264 |   height: 2.5rem;
 265 |   width: 2.5rem;
 266 |   float: none;
 267 |   right: auto;
 268 |   top: auto;
 269 |   position: relative;
 270 |   -webkit-transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 271 |   transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 272 |   -webkit-transform-origin: center;
 273 |   transform-origin: center;
 274 | }
 275 | .poll > .title .icon:after {
 276 |   content: "";
 277 |   position: absolute;
 278 |   left: 50%;
 279 |   top: 50%;
 280 |   margin-left: -0.25em;
 281 |   margin-top: -0.25em;
 282 |   border-left: 0.125rem solid rgba(0, 0, 0, 0.5);
 283 |   border-bottom: 0.125rem solid rgba(0, 0, 0, 0.5);
 284 |   box-sizing: border-box;
 285 |   height: 0.5em;
 286 |   width: 0.5em;
 287 |   border-color: #616161;
 288 |   -webkit-transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 289 |   transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 290 |   -webkit-transform-origin: center;
 291 |   transform-origin: center;
 292 | }
 293 | .poll > .title .icon:before {
 294 |   content: "";
 295 |   position: absolute;
 296 |   left: 50%;
 297 |   top: 50%;
 298 |   margin-left: -1em;
 299 |   margin-top: -1em;
 300 |   height: 2em;
 301 |   width: 2em;
 302 |   background: #000000;
 303 |   -webkit-border-radius: 100%;
 304 |   border-radius: 100%;
 305 |   -webkit-transform: scale(0.5) translateZ(0);
 306 |   transform: scale(0.5) translateZ(0);
 307 |   opacity: 0;
 308 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 309 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 310 | }
 311 | .poll > .title:active .icon:before {
 312 |   opacity: 0.05;
 313 |   -webkit-transform: scale(1) translateZ(0);
 314 |   transform: scale(1) translateZ(0);
 315 | }
 316 | .poll > .body {
 317 |   webkit-hyphens: auto;
 318 |   -ms-hyphens: auto;
 319 |   hyphens: auto;
 320 |   word-wrap: break-word;
 321 | }
 322 | .poll > .body .questions .question {
 323 |   padding-bottom: 0.5em;
 324 | }
 325 | .poll > .body .questions .question > .title {
 326 |   font-weight: bold;
 327 |   padding: 1em 1em 0em 1em;
 328 |   line-height: 1.5em;
 329 | }
 330 | .poll > .body .questions .question:first-child > .title {
 331 |   padding-top: 0.5em;
 332 | }
 333 | .poll > .body .questions .question > .items {
 334 |   padding: 0 1em;
 335 | }
 336 | .poll > .body .questions .question > .items .item {
 337 |   display: block;
 338 | }
 339 | .poll > .body .questions .question > .items .item.default {
 340 |   padding: 0.875em 0;
 341 |   position: relative;
 342 |   cursor: pointer;
 343 |   min-height: 1.25em;
 344 |   touch-action: manipulation;
 345 | }
 346 | .poll > .body .questions .question > .items .item.default input {
 347 |   position: absolute;
 348 |   left: 0;
 349 |   top: 50%;
 350 |   width: 1.25em;
 351 |   height: 1.25em;
 352 |   margin: -0.625em 0 0 0;
 353 |   opacity: 0;
 354 |   z-index: 2;
 355 | }
 356 | .poll > .body .questions .question > .items .item.default input[type="radio"] ~ .icon {
 357 |   -webkit-border-radius: 100%;
 358 |   border-radius: 100%;
 359 |   -webkit-mask-image: none;
 360 |   mask-image: none;
 361 |   -webkit-mask-position: center center;
 362 |   -webkit-mask-size: 1.25em 1.25em;
 363 |   -webkit-mask-origin: border-box;
 364 |   -webkit-mask-clip: border-box;
 365 | }
 366 | .poll > .body .questions .question > .items .item.default input[type="radio"] ~ .icon:after,
 367 | .poll > .body .questions .question > .items .item.default input[type="radio"] ~ .icon:before {
 368 |   -webkit-border-radius: 100%;
 369 |   border-radius: 100%;
 370 | }
 371 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon {
 372 |   -webkit-border-radius: 0.125em;
 373 |   border-radius: 0.125em;
 374 |   height: 1.125em;
 375 |   width: 1.125em;
 376 |   margin-top: -0.5625em;
 377 |   left: 0.0625em;
 378 | }
 379 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add {
 380 |   position: absolute;
 381 |   height: 100%;
 382 |   width: 100%;
 383 |   top: 0;
 384 |   left: 0;
 385 |   -webkit-transform: rotate(-45deg);
 386 |   transform: rotate(-45deg);
 387 |   z-index: 1;
 388 | }
 389 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:after,
 390 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:before {
 391 |   content: "";
 392 |   position: absolute;
 393 |   box-sizing: border-box;
 394 |   border: 0.125rem solid rgba(0, 0, 0, 0.5);
 395 |   border-color: #ffffff;
 396 |   border-top: none;
 397 |   border-right: none;
 398 |   -webkit-transform-origin: left bottom;
 399 |   transform-origin: left bottom;
 400 |   opacity: 0;
 401 |   -webkit-transition: 0.088s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.11s opacity cubic-bezier(0.4, 0, 0.2, 1);
 402 |   transition: 0.088s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.11s opacity cubic-bezier(0.4, 0, 0.2, 1);
 403 | }
 404 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:after {
 405 |   top: 0.4375em;
 406 |   left: 0.125em;
 407 |   height: 0.125em;
 408 |   width: 0.8125em;
 409 |   -webkit-transform: scaleX(0.15);
 410 |   transform: scaleX(0.15);
 411 | }
 412 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon .add:before {
 413 |   left: 0.125em;
 414 |   top: 0.125em;
 415 |   height: 0.4375em;
 416 |   width: 0.125em;
 417 |   -webkit-transform: scaleY(0.35);
 418 |   transform: scaleY(0.35);
 419 | }
 420 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon:after,
 421 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon:before {
 422 |   -webkit-border-radius: 0;
 423 |   border-radius: 0;
 424 | }
 425 | .poll > .body .questions .question > .items .item.default input[type="checkbox"] ~ .icon:before {
 426 |   height: 1.25em;
 427 |   width: 1.25em;
 428 |   margin-top: -0.625em;
 429 |   margin-left: -0.625em;
 430 |   border: 0.375em solid #000000;
 431 |   -webkit-transform: scale(2);
 432 |   transform: scale(2);
 433 | }
 434 | .poll > .body .questions .question > .items .item.default input:checked + .icon {
 435 |   border-color: #616161;
 436 |   -webkit-transition: 0s 0s border-color cubic-bezier(0.4, 0, 0.2, 1);
 437 |   transition: 0s 0s border-color cubic-bezier(0.4, 0, 0.2, 1);
 438 |   /*-webkit-animation: radAnim1 @dur @curveStandard;
 439 |                                         animation: radAnim1 @dur @curveStandard;*/
 440 | }
 441 | .poll > .body .questions .question > .items .item.default input:checked + .icon:after {
 442 |   -webkit-transform: scale(0.1);
 443 |   transform: scale(0.1);
 444 |   -webkit-transition: 0.176s 0.11s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.088s opacity cubic-bezier(0.4, 0, 0.2, 1);
 445 |   transition: 0.176s 0.11s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.088s opacity cubic-bezier(0.4, 0, 0.2, 1);
 446 |   opacity: 1;
 447 | }
 448 | .poll > .body .questions .question > .items .item.default input:checked + .icon:before {
 449 |   -webkit-transform: scale(1);
 450 |   transform: scale(1);
 451 |   -webkit-transition: 0s 0.198s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0s opacity cubic-bezier(0.4, 0, 0.2, 1);
 452 |   transition: 0s 0.198s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0s opacity cubic-bezier(0.4, 0, 0.2, 1);
 453 |   opacity: 1;
 454 |   border-width: 0.3125em;
 455 | }
 456 | .poll > .body .questions .question > .items .item.default input:checked[type="checkbox"] + .icon .add:after,
 457 | .poll > .body .questions .question > .items .item.default input:checked[type="checkbox"] + .icon .add:before {
 458 |   opacity: 1;
 459 |   -webkit-transition: 0.176s 0.198s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s opacity cubic-bezier(0.4, 0, 0.2, 1);
 460 |   transition: 0.176s 0.198s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.198s opacity cubic-bezier(0.4, 0, 0.2, 1);
 461 |   -webkit-transform: scaleX(1) scaleY(1);
 462 |   transform: scaleX(1) scaleY(1);
 463 | }
 464 | .poll > .body .questions .question > .items .item.default input:checked[type="checkbox"] + .icon:before {
 465 |   background: #000000;
 466 | }
 467 | .poll > .body .questions .question > .items .item.default > .icon {
 468 |   display: block;
 469 |   position: absolute;
 470 |   height: 1.25em;
 471 |   width: 1.25em;
 472 |   background: #ffffff;
 473 |   left: 0;
 474 |   top: 50%;
 475 |   margin-top: -0.625em;
 476 |   border: 0.125rem solid rgba(0, 0, 0, 0.5);
 477 |   overflow: hidden;
 478 |   -webkit-transform: translateZ(0);
 479 |   transform: translateZ(0);
 480 |   -webkit-transition: 0s 0.352s border-color cubic-bezier(0.4, 0, 0.2, 1);
 481 |   transition: 0s 0.352s border-color cubic-bezier(0.4, 0, 0.2, 1);
 482 |   outline: 2px solid transparent;
 483 | }
 484 | .poll > .body .questions .question > .items .item.default > .icon:after,
 485 | .poll > .body .questions .question > .items .item.default > .icon:before {
 486 |   content: "";
 487 |   position: absolute;
 488 |   top: 50%;
 489 |   left: 50%;
 490 |   opacity: 0;
 491 |   box-sizing: border-box;
 492 | }
 493 | .poll > .body .questions .question > .items .item.default > .icon:after {
 494 |   height: 1.25em;
 495 |   width: 1.25em;
 496 |   margin-top: -0.625em;
 497 |   margin-left: -0.625em;
 498 |   border: 0.375em solid #000000;
 499 |   opacity: 0;
 500 |   -webkit-transform: scale(2.25);
 501 |   transform: scale(2.25);
 502 |   -webkit-transition: 0.176s 0.088s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.352s opacity cubic-bezier(0.4, 0, 0.2, 1);
 503 |   transition: 0.176s 0.088s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.352s opacity cubic-bezier(0.4, 0, 0.2, 1);
 504 | }
 505 | .poll > .body .questions .question > .items .item.default > .icon:before {
 506 |   height: 0.625em;
 507 |   width: 0.625em;
 508 |   margin-top: -0.3125em;
 509 |   margin-left: -0.3125em;
 510 |   border: 0.1875em solid #000000;
 511 |   -webkit-transform: scale(4);
 512 |   transform: scale(4);
 513 |   -webkit-transition: 0s 0.132s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.132s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s -webkit-transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.44s opacity cubic-bezier(0.4, 0, 0.2, 1);
 514 |   transition: 0s 0.132s background cubic-bezier(0.4, 0, 0.2, 1), 0s 0.132s border-width cubic-bezier(0.4, 0, 0.2, 1), 0.44s 0s transform cubic-bezier(0.4, 0, 0.2, 1), 0s 0.44s opacity cubic-bezier(0.4, 0, 0.2, 1);
 515 | }
 516 | .poll > .body .questions .question > .items .item.default > .title {
 517 |   display: inline-block;
 518 |   padding-left: 2em;
 519 |   min-height: 1.25em;
 520 | }
 521 | .poll > .body .questions .question > .items .item.default:active:before {
 522 |   opacity: 0.05;
 523 |   -webkit-transform: scale(1) translateZ(0);
 524 |   transform: scale(1) translateZ(0);
 525 | }
 526 | .poll > .body .questions .question > .items .item.default:before {
 527 |   content: "";
 528 |   position: absolute;
 529 |   left: 0%;
 530 |   top: 50%;
 531 |   margin-left: -0.625em;
 532 |   margin-top: -1.25em;
 533 |   height: 2.5em;
 534 |   width: 2.5em;
 535 |   background: rgba(0, 0, 0, 0.1);
 536 |   -webkit-border-radius: 100%;
 537 |   border-radius: 100%;
 538 |   -webkit-transform: scale(0.5) translateZ(0);
 539 |   transform: scale(0.5) translateZ(0);
 540 |   opacity: 0;
 541 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 542 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
 543 | }
 544 | .poll > .body .questions .question > .items .item.readonly {
 545 |   cursor: default;
 546 |   opacity: 0.72;
 547 |   pointer-events: none;
 548 | }
 549 | .poll > .body .questions .question > .items .item.readonly input {
 550 |   display: none;
 551 | }
 552 | .poll > .body .questions .question > .items .item.readonly > .icon,
 553 | .poll > .body .questions .question > .items .item.readonly:before {
 554 |   display: none;
 555 | }
 556 | .poll > .body .questions .question > .items .item.readonly > .title {
 557 |   padding-left: 0;
 558 | }
 559 | .poll > .body .questions .question > .items .item.result {
 560 |   padding-top: 0.5em;
 561 |   margin-bottom: 0.5em;
 562 |   position: relative;
 563 | }
 564 | .poll > .body .questions .question > .items .item.result > .title {
 565 |   position: relative;
 566 |   z-index: 1;
 567 |   display: inline-block;
 568 |   background: #ffffff;
 569 |   -webkit-box-shadow: 0.75em 0 0.5em -0.25em #ffffff;
 570 |   box-shadow: 0.75em 0 0.5em -0.25em #ffffff;
 571 |   line-height: 1.5em;
 572 | }
 573 | .poll > .body .questions .question > .items .item.result .range_bar {
 574 |   background: rgba(0, 0, 0, 0.1);
 575 |   box-sizing: border-box;
 576 |   position: relative;
 577 |   min-height: 1.5rem;
 578 |   padding: 0.375rem 0.75rem;
 579 |   -webkit-border-radius: 2px;
 580 |   border-radius: 2px;
 581 | }
 582 | .poll > .body .questions .question > .items .item.result .range_bar .range {
 583 |   position: absolute;
 584 |   left: 0;
 585 |   top: 0;
 586 |   height: 100%;
 587 |   width: auto;
 588 |   background: rgba(0, 0, 0, 0.1);
 589 |   -webkit-border-radius: 2px;
 590 |   border-radius: 2px;
 591 |   -webkit-transform-origin: left center;
 592 |   transform-origin: left center;
 593 | }
 594 | .poll > .body .questions .question > .items .item.result .range_bar .value {
 595 |   float: right;
 596 |   display: inline-block;
 597 |   font-size: 0.75em;
 598 |   font-weight: bold;
 599 |   -webkit-transform: translateZ(0);
 600 |   transform: translateZ(0);
 601 |   vertical-align: bottom;
 602 |   color: #000000;
 603 | }
 604 | .poll > .body .questions .question > .items .item.result .range_bar .value .num_votes:before {
 605 |   content: " (";
 606 | }
 607 | .poll > .body .questions .question > .items .item.result .range_bar .value .num_votes:after {
 608 |   content: ")";
 609 | }
 610 | .poll > .body .questions .question > .items .item.result .range_bar .title {
 611 |   position: relative;
 612 |   font-size: 0.875em;
 613 |   display: block;
 614 |   overflow: hidden;
 615 |   padding-right: 0.5rem;
 616 |   color: #000000;
 617 |   font-weight: bold;
 618 | }
 619 | .poll > .body .questions .question > .items .item.result .range_bar:after {
 620 |   content: "";
 621 |   display: table;
 622 |   clear: both;
 623 | }
 624 | .poll > .body .votes_info {
 625 |   text-align: right;
 626 |   color: #636366;
 627 |   padding: 0.5em 1em;
 628 | }
 629 | .poll > .body .votes_info span {
 630 |   font-size: 0.875em;
 631 | }
 632 | .poll > .body .poll_status {
 633 |   text-align: right;
 634 |   color: #757575;
 635 |   padding: 0 1em 0.5em;
 636 | }
 637 | .poll > .body .poll_status span {
 638 |   font-size: 0.875em;
 639 | }
 640 | .poll > .body .buttons {
 641 |   -webkit-touch-callout: none;
 642 |   -webkit-user-select: none;
 643 |   -khtml-user-select: none;
 644 |   -moz-user-select: none;
 645 |   -ms-user-select: none;
 646 |   user-select: none;
 647 |   display: flex;
 648 |   flex-wrap: wrap;
 649 |   gap: 0.25rem;
 650 |   justify-content: flex-end;
 651 |   align-items: center;
 652 |   position: sticky;
 653 |   bottom: 0;
 654 |   z-index: 2;
 655 |   padding: 0.25rem 0.5rem 0.5rem;
 656 |   background: #ffffff;
 657 |   border-top: 0.0625rem solid rgba(0, 0, 0, 0.12);
 658 | }
 659 | .poll > .body .buttons > * {
 660 |   float: none;
 661 |   display: inline-flex;
 662 |   align-items: center;
 663 |   justify-content: center;
 664 |   min-height: 2.5rem;
 665 |   box-sizing: border-box;
 666 |   padding: 0.625rem 0.75rem;
 667 |   text-align: center;
 668 |   color: #616161;
 669 |   background: rgba(0, 0, 0, 0.1);
 670 |   -webkit-border-radius: 0.875rem;
 671 |   border-radius: 0.875rem;
 672 | }
 673 | .poll > .body .buttons > * > span {
 674 |   display: inline;
 675 |   color: inherit;
 676 |   font-size: 0.875rem;
 677 |   line-height: 1.25rem;
 678 | }
 679 | .poll > .body .buttons .vote,
 680 | .poll > .body .buttons .show_results,
 681 | .poll > .body .buttons .show_poll {
 682 |   min-width: 6rem;
 683 | }
 684 | .poll > .body .buttons .vote {
 685 |   background: #616161;
 686 |   color: #ffffff;
 687 | }
 688 | .poll > .body .buttons:after {
 689 |   content: "";
 690 |   display: table;
 691 |   clear: both;
 692 | }
 693 | .poll.open > .title .icon {
 694 |   -webkit-transform: rotateX(180deg) translateZ(0);
 695 |   transform: rotateX(180deg) translateZ(0);
 696 | }
 697 | .poll.open:not(.once-opened) .range {
 698 |   -webkit-animation: pollRangeAnimation 0.375s cubic-bezier(0.4, 0, 0.2, 1);
 699 |   -webkit-animation-fill-mode: forwards;
 700 | }
 701 | .post_body {
 702 |   padding: 1em;
 703 |   position: relative;
 704 |   z-index: 1;
 705 |   -webkit-hyphens: auto;
 706 |   -ms-hyphens: auto;
 707 |   hyphens: auto;
 708 |   word-wrap: break-word;
 709 |   -webkit-user-select: text;
 710 |   user-select: text;
 711 |   -webkit-touch-callout: default;
 712 | }
 713 | .post_body a.anchor {
 714 |   display: block;
 715 |   position: relative;
 716 |   height: 1.5rem;
 717 |   margin-top: 0.5rem;
 718 | }
 719 | .post_body a.anchor:before {
 720 |   content: "";
 721 |   position: absolute;
 722 |   left: 0;
 723 |   top: 0;
 724 |   height: 1.5rem;
 725 |   width: 1.5rem;
 726 |   background-image: url("../../res/light/anchor.svg");
 727 |   background-position: center;
 728 |   background-repeat: no-repeat;
 729 |   background-size: 1.5rem 1.5rem;
 730 | }
 731 | .post_body a.anchor:after {
 732 |   content: "";
 733 |   position: absolute;
 734 |   top: 0.75rem;
 735 |   left: 2rem;
 736 |   right: 0.5rem;
 737 |   border-top: 0.0625rem dashed #757575;
 738 | }
 739 | .post_body a.anchor:active {
 740 |   background: rgba(0, 0, 0, 0.1);
 741 | }
 742 | .post_body .postcolor {
 743 |   overflow: auto!important;
 744 |   height: auto!important;
 745 |   margin: -1em;
 746 |   padding: 1em;
 747 | }
 748 | .post_body img {
 749 |   max-width: 100%;
 750 |   height: auto!important;
 751 | }
 752 | .post_body img.linked-image,
 753 | .post_body img.attach {
 754 |   display: inline-block;
 755 |   vertical-align: middle;
 756 |   max-height: 75vh;
 757 |   contain: paint;
 758 |   object-fit: contain;
 759 |   background-color: rgba(0, 150, 136, 0.1);
 760 |   border-radius: 0.25rem;
 761 |   box-sizing: border-box;
 762 | }
 763 | .post_body img.theme-media-has-ratio {
 764 |   aspect-ratio: auto;
 765 | }
 766 | .post_body img.theme-media-pending {
 767 |   min-width: 3rem;
 768 |   min-height: 3rem;
 769 |   opacity: 1;
 770 | }
 771 | .post_body img.theme-media-loaded {
 772 |   opacity: 1;
 773 |   -webkit-transition: opacity 120ms cubic-bezier(0, 0, 0.2, 1);
 774 |   transition: opacity 120ms cubic-bezier(0, 0, 0.2, 1);
 775 | }
 776 | .post_body .edit {
 777 |   display: block;
 778 |   text-align: inherit;
 779 |   padding: 0;
 780 |   margin-left: 0;
 781 |   vertical-align: baseline;
 782 |   font-size: 0.875em;
 783 |   line-height: 1.35;
 784 |   white-space: normal;
 785 |   overflow-wrap: anywhere;
 786 |   word-break: break-word;
 787 |   max-width: 100%;
 788 |   color: #757575;
 789 | }
 790 | .post_body strong .edit {
 791 |   text-align: left;
 792 | }
 793 | .post_body .post-edit-reason {
 794 |   font-size: 0.875em;
 795 |   text-align: right;
 796 |   border-top: none;
 797 |   padding: 0 0.571em 0 0.571em;
 798 |   margin-top: -2px;
 799 |   color: #757575;
 800 | }
 801 | .post_body .ipb-attach.attach-file {
 802 |   position: relative;
 803 | }
 804 | .post_body .ipb-attach.attach-file img {
 805 |   display: none;
 806 | }
 807 | .post_body .ipb-attach.attach-file:before {
 808 |   content: "";
 809 |   display: inline-block;
 810 |   height: 1.25em;
 811 |   width: 1.25em;
 812 |   vertical-align: text-bottom;
 813 |   /*background-image: url(../../res/file.svg);
 814 |             background-position: center;
 815 |             background-repeat: no-repeat;
 816 |             background-size: 20/16em 20/16em;
 817 |             
 818 |             -webkit-filter: blur(1px);
 819 |             filter: blur(1px);*/
 820 |   background: url(../../res/file-outline.svg) no-repeat center center;
 821 | }
 822 | @-webkit-keyframes highlight {
 823 |   0% {
 824 |     opacity: 0;
 825 |   }
 826 |   20% {
 827 |     opacity: 0.25;
 828 |   }
 829 |   100% {
 830 |     opacity: 0;
 831 |   }
 832 | }
 833 | @keyframes highlight {
 834 |   0% {
 835 |     opacity: 0;
 836 |   }
 837 |   20% {
 838 |     opacity: 0.25;
 839 |   }
 840 |   100% {
 841 |     opacity: 0;
 842 |   }
 843 | }
 844 | body.circle_avatar .post_header .avatar .img {
 845 |   -webkit-border-radius: 3.5em;
 846 |   border-radius: 3.5em;
 847 | }
 848 | body.circle_avatar .post_header .avatar > .letter {
 849 |   -webkit-border-radius: 3.5em;
 850 |   border-radius: 3.5em;
 851 | }
 852 | body.square_avatar .post-block.quote > .block-title .avatar {
 853 |   -webkit-border-radius: 0px;
 854 |   border-radius: 0px;
 855 | }
 856 | body.square_avatar .post-block.quote > .block-title .avatar .image {
 857 |   -webkit-border-radius: 0px;
 858 |   border-radius: 0px;
 859 | }
 860 | body.hide_avatar .post_header .avatar .img {
 861 |   display: none;
 862 | }
 863 | body.hide_avatar .post_header .avatar .letter {
 864 |   display: block;
 865 | }
 866 | body.hide_avatar .post-block.quote > .block-title .avatar .image {
 867 |   display: none;
 868 | }
 869 | .post_container {
 870 |   background: #ffffff;
 871 |   margin: 0.5em 0;
 872 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
 873 |   position: relative;
 874 |   -webkit-border-radius: 0.875rem;
 875 |   border-radius: 0.875rem;
 876 |   overflow: hidden;
 877 | }
 878 | .post_container.blacklisted_post {
 879 |   box-shadow: none;
 880 |   margin-top: var(--topic-post-spacing, 0.5em);
 881 |   margin-bottom: var(--topic-post-spacing, 0.5em);
 882 |   min-height: 0;
 883 |   padding: 0;
 884 | }
 885 | .post_container.blacklisted_post.revealed {
 886 |   background: #ffffff;
 887 |   margin: 0.5em 0;
 888 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
 889 |   position: relative;
 890 |   -webkit-border-radius: 0.875rem;
 891 |   border-radius: 0.875rem;
 892 |   overflow: hidden;
 893 |   min-height: auto;
 894 |   padding: 0;
 895 | }
 896 | .post_container.blacklisted_post .blacklisted_post_placeholder {
 897 |   display: -webkit-box;
 898 |   display: -webkit-flex;
 899 |   display: flex;
 900 |   -webkit-box-pack: center;
 901 |   -webkit-justify-content: center;
 902 |   justify-content: center;
 903 |   -webkit-box-align: center;
 904 |   -webkit-align-items: center;
 905 |   align-items: center;
 906 |   box-sizing: border-box;
 907 |   width: 100%;
 908 |   padding: 0.25rem 0.5rem;
 909 |   margin: 0.25rem 0;
 910 |   border: 0;
 911 |   background: transparent;
 912 |   font: inherit;
 913 |   font-size: 0.75rem;
 914 |   line-height: 1.25;
 915 |   color: #757575;
 916 |   opacity: 0.78;
 917 |   text-decoration: none;
 918 |   text-align: center;
 919 |   user-select: none;
 920 |   cursor: pointer;
 921 |   -webkit-tap-highlight-color: transparent;
 922 | }
 923 | .post_container.blacklisted_post .blacklisted_post_placeholder .blacklisted_post_placeholder_text {
 924 |   color: inherit !important;
 925 |   text-decoration: none !important;
 926 | }
 927 | .post_container.blacklisted_post .blacklisted_post_placeholder.aec,
 928 | .post_container.blacklisted_post .blacklisted_post_placeholder:link,
 929 | .post_container.blacklisted_post .blacklisted_post_placeholder:visited,
 930 | .post_container.blacklisted_post .blacklisted_post_placeholder:hover,
 931 | .post_container.blacklisted_post .blacklisted_post_placeholder:active,
 932 | .post_container.blacklisted_post .blacklisted_post_placeholder:focus {
 933 |   color: #757575 !important;
 934 |   text-decoration: none !important;
 935 |   background: transparent;
 936 |   outline: 0;
 937 | }
 938 | .post_container.blacklisted_post .blacklisted_post_placeholder[hidden] {
 939 |   display: none !important;
 940 | }
 941 | .post_container.blacklisted_post .blacklisted_post_content[hidden] {
 942 |   display: none !important;
 943 | }
 944 | .post_container.blacklisted_post.revealed .blacklisted_post_content {
 945 |   display: block;
 946 | }
 947 | .post_container .hat_button {
 948 |   -webkit-touch-callout: none;
 949 |   -webkit-user-select: none;
 950 |   -khtml-user-select: none;
 951 |   -moz-user-select: none;
 952 |   -ms-user-select: none;
 953 |   user-select: none;
 954 |   width: 100%;
 955 |   color: #616161;
 956 |   position: relative;
 957 |   text-align: center;
 958 | }
 959 | .post_container .hat_button .icon {
 960 |   height: 3em;
 961 |   width: 2.5em;
 962 |   float: right;
 963 |   margin: -0.875em -1em;
 964 |   position: relative;
 965 |   -webkit-transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 966 |   transition: 0.225s cubic-bezier(0.4, 0, 0.2, 1);
 967 |   -webkit-transform-origin: center;
 968 |   transform-origin: center;
 969 | }
 970 | .post_container .hat_button .icon:after {
 971 |   content: "";
 972 |   position: absolute;
 973 |   left: 50%;
 974 |   top: 50%;
 975 |   margin-left: -0.25em;
 976 |   margin-top: -0.25em;
 977 |   border-left: 0.125rem solid rgba(0, 0, 0, 0.5);
 978 |   border-bottom: 0.125rem solid rgba(0, 0, 0, 0.5);
 979 |   box-sizing: border-box;
 980 |   height: 0.5em;
 981 |   width: 0.5em;
 982 |   border-color: #616161;
 983 |   -webkit-transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 984 |   transform: rotate(-45deg) translateY(-0.0625em) translateX(0.0625em);
 985 |   -webkit-transform-origin: center;
 986 |   transform-origin: center;
 987 | }
 988 | .post_container .hat_button .icon:before {
 989 |   content: "";
 990 |   position: absolute;
 991 |   left: 50%;
 992 |   top: 50%;
 993 |   margin-left: -1em;
 994 |   margin-top: -1em;
 995 |   height: 2em;
 996 |   width: 2em;
 997 |   background: #000000;
 998 |   -webkit-border-radius: 100%;
 999 |   border-radius: 100%;
1000 |   -webkit-transform: scale(0.5) translateZ(0);
1001 |   transform: scale(0.5) translateZ(0);
1002 |   opacity: 0;
1003 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1004 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1005 | }
1006 | .post_container .hat_button:active .icon:before {
1007 |   opacity: 0.05;
1008 |   -webkit-transform: scale(1) translateZ(0);
1009 |   transform: scale(1) translateZ(0);
1010 | }
1011 | .post_container .hat_button + .hat_content {
1012 |   padding-top: 0.5em;
1013 | }
1014 | .post_container.open .hat_button .icon {
1015 |   -webkit-transform: rotateX(180deg) translateZ(0);
1016 |   transform: rotateX(180deg) translateZ(0);
1017 | }
1018 | .post_container.online .post_header .inf.nick .online_dot {
1019 |   display: inline-block;
1020 | }
1021 | .post_container .post_title {
1022 |   text-align: center;
1023 |   text-decoration: underline;
1024 |   color: red;
1025 |   padding: 1rem;
1026 |   padding-bottom: 0;
1027 | }
1028 | .post_container .post_title a {
1029 |   font-weight: bold;
1030 |   display: block;
1031 |   padding-bottom: 1rem;
1032 |   border-bottom: 0.0625rem solid rgba(0, 0, 0, 0.05);
1033 | }
1034 | .post_container .post_header {
1035 |   padding: 0.75em 1em 0 1em;
1036 | }
1037 | .post_container .post_header .header_wrapper {
1038 |   position: relative;
1039 |   --post-header-action-right: -1rem;
1040 |   --post-header-action-width: 2.5rem;
1041 |   --post-header-inline-end-reserve: 2.5rem;
1042 |   --post-header-menu-axis-overhang: 2.25rem;
1043 |   min-height: 3.5em;
1044 |   display: flex;
1045 |   flex-direction: column;
1046 |   justify-content: center;
1047 |   padding-left: 4.5em;
1048 |   padding-right: var(--post-header-inline-end-reserve);
1049 |   box-sizing: border-box;
1050 | }
1051 | .post_container .post_header .avatar {
1052 |   -webkit-touch-callout: none;
1053 |   -webkit-user-select: none;
1054 |   -khtml-user-select: none;
1055 |   -moz-user-select: none;
1056 |   -ms-user-select: none;
1057 |   user-select: none;
1058 |   width: 3.5em;
1059 |   height: 3.5em;
1060 |   position: absolute;
1061 |   left: 0;
1062 |   top: 0;
1063 | }
1064 | .post_container .post_header .avatar .img {
1065 |   position: relative;
1066 |   height: 3.5em;
1067 |   width: 3.5em;
1068 |   background-repeat: no-repeat;
1069 |   background-size: cover;
1070 |   background-position: center;
1071 | }
1072 | .post_container .post_header .avatar.none_avatar > .letter {
1073 |   display: block;
1074 | }
1075 | .post_container .post_header .avatar > .letter {
1076 |   color: #ffffff;
1077 |   position: absolute;
1078 |   text-align: center;
1079 |   width: 1.75em;
1080 |   height: 1.75em;
1081 |   line-height: 1.75em;
1082 |   text-transform: uppercase;
1083 |   font-size: 2em;
1084 |   font-weight: bold;
1085 |   top: 0;
1086 |   left: 0;
1087 |   background-color: #bfbfbf;
1088 |   display: none;
1089 | }
1090 | .post_container .post_header .avatar .reputation {
1091 |   position: absolute;
1092 |   background: #808080;
1093 |   z-index: 1;
1094 |   height: 1.125em;
1095 |   line-height: 1.125em;
1096 |   min-width: 1.125em;
1097 |   text-align: center;
1098 |   vertical-align: text-top;
1099 |   padding: 0 0.375em;
1100 |   -webkit-border-radius: 1.25em;
1101 |   border-radius: 1.25em;
1102 |   right: -0.25em;
1103 |   bottom: 0.25em;
1104 |   right: 0;
1105 |   bottom: 0;
1106 |   box-shadow: 0 0 0 0.125em #ffffff;
1107 | }
1108 | .post_container .post_header .avatar .reputation > span {
1109 |   color: #ffffff;
1110 |   font-size: 0.625em;
1111 |   font-weight: bold;
1112 |   vertical-align: middle;
1113 |   /* &:before {
1114 |                         content: "Реп (";
1115 |                     }
1116 |                     &:after {
1117 |                         content: ")";
1118 |                     }*/
1119 | }
1120 | .post_container .post_header .avatar .reputation:after {
1121 |   content: "";
1122 |   position: absolute;
1123 |   height: 2.25rem;
1124 |   min-width: 2.25rem;
1125 |   top: 0;
1126 |   left: 0;
1127 |   right: 0;
1128 | }
1129 | .post_container .post_header .avatar.disable .img {
1130 |   width: 0;
1131 | }
1132 | .post_container .post_header .avatar.disable ~ .inf {
1133 |   left: auto;
1134 | }
1135 | .post_container .post_header .avatar.circle .img {
1136 |   -webkit-border-radius: 4em;
1137 |   border-radius: 4em;
1138 | }
1139 | .post_container .post_header .inf {
1140 |   position: relative;
1141 |   left: auto;
1142 |   max-width: 100%;
1143 |   margin-top: 0;
1144 | }
1145 | .post_container .post_header .inf.nick {
1146 |   display: flex;
1147 |   align-items: center;
1148 |   min-width: 0;
1149 |   top: auto;
1150 |   padding-top: 0;
1151 |   padding-right: 0.25rem;
1152 |   color: #212121;
1153 |   font-weight: bold;
1154 |   overflow: hidden;
1155 |   white-space: nowrap;
1156 | }
1157 | .post_container .post_header .inf.nick > .aec {
1158 |   display: block;
1159 |   min-width: 0;
1160 |   overflow: hidden;
1161 |   text-overflow: ellipsis;
1162 |   white-space: nowrap;
1163 | }
1164 | .post_container .post_header .inf.nick .online_dot {
1165 |   position: relative;
1166 |   flex: 0 0 auto;
1167 |   margin-left: 0.375em;
1168 |   height: 0.5em;
1169 |   width: 0.5em;
1170 |   background-color: #12b557;
1171 |   display: none;
1172 |   -webkit-border-radius: 100%;
1173 |   border-radius: 100%;
1174 | }
1175 | .post_container .post_header .inf.nick .online_dot:after {
1176 |   content: "";
1177 |   position: absolute;
1178 |   height: 2.25em;
1179 |   width: 2.25em;
1180 |   left: 0;
1181 |   top: -0.875em;
1182 | }
1183 | .post_container .post_header .inf.nick.online .online_dot {
1184 |   display: inline-block;
1185 | }
1186 | .post_container .post_header .inf.post_meta {
1187 |   display: flex;
1188 |   align-items: baseline;
1189 |   gap: 0.5rem;
1190 |   position: static;
1191 |   min-width: 0;
1192 |   min-height: 1.125rem;
1193 |   top: auto;
1194 |   margin-top: 0.25rem;
1195 |   width: calc(100% + var(--post-header-menu-axis-overhang));
1196 |   max-width: none;
1197 |   margin-right: 0;
1198 |   padding-right: 0;
1199 |   color: #757575;
1200 |   font-weight: normal;
1201 |   line-height: 1.25;
1202 | }
1203 | .post_container .post_header .inf.post_meta .group_text {
1204 |   display: block;
1205 |   flex: 1 1 auto;
1206 |   margin-right: 0;
1207 |   min-width: 0;
1208 |   overflow: hidden;
1209 |   text-overflow: ellipsis;
1210 |   white-space: nowrap;
1211 | }
1212 | .post_container .post_header .inf.post_meta .group_text > span {
1213 |   font-size: 0.875rem;
1214 | }
1215 | .post_container .post_header .inf.post_meta .date {
1216 |   flex: 0 0 auto;
1217 |   margin-left: auto;
1218 |   min-width: 0;
1219 |   max-width: 9rem;
1220 |   overflow: hidden;
1221 |   text-overflow: ellipsis;
1222 |   white-space: nowrap;
1223 |   color: #757575;
1224 |   text-align: right;
1225 | }
1226 | .post_container .post_header .inf.post_meta .date > span {
1227 |   font-size: 0.875rem;
1228 | }
1229 | .post_container .post_header .inf.user_post_count {
1230 |   display: flex;
1231 |   align-items: center;
1232 |   gap: 0.25rem;
1233 |   width: max-content;
1234 |   max-width: 100%;
1235 |   min-width: 0;
1236 |   margin-top: 0.1875rem;
1237 |   color: #757575;
1238 |   opacity: 0.78;
1239 |   line-height: 1.2;
1240 |   font-weight: normal;
1241 | }
1242 | .post_container .post_header .inf.user_post_count .user_post_count_icon {
1243 |   flex: 0 0 auto;
1244 |   width: 0.75rem;
1245 |   height: 0.75rem;
1246 |   color: currentColor;
1247 |   opacity: 0.78;
1248 | }
1249 | .post_container .post_header .inf.user_post_count > span {
1250 |   display: block;
1251 |   min-width: 0;
1252 |   overflow: hidden;
1253 |   text-overflow: ellipsis;
1254 |   white-space: nowrap;
1255 |   font-size: 0.75rem;
1256 | }
1257 | .post_container .post_header .inf.user_post_count.user_post_count_placeholder {
1258 |   min-width: 3rem;
1259 |   color: transparent !important;
1260 |   opacity: 0 !important;
1261 |   pointer-events: none;
1262 | }
1263 | .post_container .post_header .inf.post_meta + .user_post_count {
1264 |   display: -webkit-box !important;
1265 |   display: -webkit-flex !important;
1266 |   display: flex !important;
1267 |   visibility: visible !important;
1268 |   height: auto !important;
1269 |   min-height: 0.875rem !important;
1270 |   overflow: visible !important;
1271 | }
1272 | .post_container .post_header .inf.menu {
1273 |   -webkit-touch-callout: none;
1274 |   -webkit-user-select: none;
1275 |   -khtml-user-select: none;
1276 |   -moz-user-select: none;
1277 |   -ms-user-select: none;
1278 |   user-select: none;
1279 |   left: auto;
1280 |   position: absolute;
1281 |   right: -1em;
1282 |   right: var(--post-header-action-right);
1283 |   top: -0.75em;
1284 |   height: 3em;
1285 |   width: var(--post-header-action-width);
1286 |   background-image: url("../../res/light/dots-vertical.svg");
1287 |   background-repeat: no-repeat;
1288 |   background-size: 1.5rem 1.5rem;
1289 |   background-position: center;
1290 |   margin: 0;
1291 | }
1292 | .post_container .post_header .inf.menu > span {
1293 |   display: none;
1294 | }
1295 | .post_container .post_header .inf.menu:before {
1296 |   content: "";
1297 |   position: absolute;
1298 |   left: 50%;
1299 |   top: 50%;
1300 |   margin-left: -1em;
1301 |   margin-top: -1em;
1302 |   height: 2em;
1303 |   width: 2em;
1304 |   background: #000000;
1305 |   -webkit-border-radius: 100%;
1306 |   border-radius: 100%;
1307 |   -webkit-transform: scale(0.5) translateZ(0);
1308 |   transform: scale(0.5) translateZ(0);
1309 |   opacity: 0;
1310 |   -webkit-transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1311 |   transition: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
1312 | }
1313 | .post_container .post_header .inf.menu:active {
1314 |   background-color: transparent;
1315 | }
1316 | .post_container .post_header .inf.menu:active:before {
1317 |   opacity: 0.05;
1318 |   -webkit-transform: scale(1) translateZ(0);
1319 |   transform: scale(1) translateZ(0);
1320 | }
1321 | .post_container .post_header .inf.date {
1322 |   top: 1.25em;
1323 |   color: #757575;
1324 | }
1325 | .post_container .post_header .inf.date > span {
1326 |   font-size: 0.875em;
1327 | }
1328 | .post_container .post_header .inf.number {
1329 |   display: none;
1330 | }
1331 | .post_container .post_header .inf.number > span {
1332 |   font-size: 0.875em;
1333 | }
1334 | .post_container .post_footer {
1335 |   -webkit-touch-callout: none;
1336 |   -webkit-user-select: none;
1337 |   -khtml-user-select: none;
1338 |   -moz-user-select: none;
1339 |   -ms-user-select: none;
1340 |   user-select: none;
1341 |   padding: 0 1em 0.75em 1em;
1342 | }
1343 | .post_container .post_footer .btn {
1344 |   color: rgba(0, 0, 0, 0.87);
1345 | }
1346 | .post_container .post_footer .post_rating_row {
1347 |   display: -webkit-box;
1348 |   display: -webkit-flex;
1349 |   display: flex;
1350 |   -webkit-box-pack: start;
1351 |   -webkit-justify-content: flex-start;
1352 |   justify-content: flex-start;
1353 |   margin-top: 0.375em;
1354 |   color: #757575;
1355 |   font-size: 0.8125rem;
1356 | }
1357 | .post_container .post_footer .post_rating {
1358 |   display: -webkit-inline-box;
1359 |   display: -webkit-inline-flex;
1360 |   display: inline-flex;
1361 |   -webkit-box-align: center;
1362 |   -webkit-align-items: center;
1363 |   align-items: center;
1364 |   -webkit-box-pack: center;
1365 |   -webkit-justify-content: center;
1366 |   justify-content: center;
1367 |   min-width: 1.25em;
1368 |   height: 2.5em;
1369 |   padding: 0;
1370 |   border: 0;
1371 |   -webkit-border-radius: 0;
1372 |   border-radius: 0;
1373 |   background: transparent;
1374 |   box-shadow: none;
1375 |   outline: 0;
1376 |   color: rgba(0, 0, 0, 0.87);
1377 |   font-size: 0.95rem;
1378 |   font-weight: 700;
1379 |   line-height: 1;
1380 | }
1381 | .post_container .post_footer .post_rating.post_rating_hidden {
1382 |   display: none !important;
1383 | }
1384 | .post_container .post_footer .post_actions_row {
1385 |   display: -webkit-box;
1386 |   display: -webkit-flex;
1387 |   display: flex;
1388 |   -webkit-box-align: center;
1389 |   -webkit-align-items: center;
1390 |   align-items: center;
1391 |   gap: var(--post-action-gap, 0.375rem);
1392 |   -webkit-flex-wrap: nowrap;
1393 |   flex-wrap: nowrap;
1394 |   width: 100%;
1395 |   box-sizing: border-box;
1396 |   --post-action-button-size: 3rem;
1397 |   --post-action-icon-size: 1.8rem;
1398 |   --post-rep-action-icon-size: var(--post-action-icon-size);
1399 |   --post-action-stroke-width: 1.85;
1400 |   --post-action-light-stroke-width: 1.2;
1401 |   --post-action-radius: 0;
1402 |   --topic-action-icon-color: #616161;
1403 |   --post-action-icon-color: var(--topic-action-icon-color);
1404 |   --topic-action-icon-active-color: var(--topic-action-icon-color);
1405 |   --post-action-icon-active-color: var(--topic-action-icon-active-color);
1406 | }
1407 | .post_container .post_footer .post_actions_row .btn.rep_up,
1408 | .post_container .post_footer .post_actions_row .btn.rep_down,
1409 | .post_container .post_footer .post_actions_row .btn.reply,
1410 | .post_container .post_footer .post_actions_row .btn.quote {
1411 |   display: -webkit-inline-box;
1412 |   display: -webkit-inline-flex;
1413 |   display: inline-flex;
1414 |   -webkit-box-align: center;
1415 |   -webkit-align-items: center;
1416 |   align-items: center;
1417 |   -webkit-box-pack: center;
1418 |   -webkit-justify-content: center;
1419 |   justify-content: center;
1420 |   box-sizing: border-box;
1421 |   margin: 0;
1422 |   padding: 0;
1423 |   width: var(--post-action-button-size);
1424 |   height: var(--post-action-button-size);
1425 |   min-width: var(--post-action-button-size);
1426 |   min-height: var(--post-action-button-size);
1427 |   border: 0;
1428 |   background: transparent;
1429 |   background-color: transparent;
1430 |   background-image: none;
1431 |   border-color: transparent;
1432 |   box-shadow: none;
1433 |   outline: 0;
1434 |   -webkit-filter: none;
1435 |   filter: none;
1436 |   -webkit-tap-highlight-color: transparent;
1437 |   color: var(--post-action-icon-color);
1438 |   -webkit-border-radius: var(--post-action-radius);
1439 |   border-radius: var(--post-action-radius);
1440 | }
1441 | .post_container .post_footer .post_actions_row .btn.rep_up,
1442 | .post_container .post_footer .post_actions_row .btn.rep_down {
1443 |   overflow: visible;
1444 |   color: var(--post-action-icon-color);
1445 | }
1446 | .post_container .post_footer .post_actions_row .btn.reply:focus,
1447 | .post_container .post_footer .post_actions_row .btn.quote:focus,
1448 | .post_container .post_footer .post_actions_row .btn.rep_up:focus,
1449 | .post_container .post_footer .post_actions_row .btn.rep_down:focus,
1450 | .post_container .post_footer .post_actions_row .btn.reply:visited,
1451 | .post_container .post_footer .post_actions_row .btn.quote:visited,
1452 | .post_container .post_footer .post_actions_row .btn.rep_up:visited,
1453 | .post_container .post_footer .post_actions_row .btn.rep_down:visited,
1454 | .post_container .post_footer .post_actions_row .btn.reply:hover,
1455 | .post_container .post_footer .post_actions_row .btn.quote:hover,
1456 | .post_container .post_footer .post_actions_row .btn.rep_up:hover,
1457 | .post_container .post_footer .post_actions_row .btn.rep_down:hover {
1458 |   background: transparent;
1459 |   background-color: transparent;
1460 |   background-image: none;
1461 |   border: 0;
1462 |   border-color: transparent;
1463 |   box-shadow: none;
1464 |   outline: 0;
1465 |   -webkit-filter: none;
1466 |   filter: none;
1467 |   color: var(--post-action-icon-color);
1468 | }
1469 | .post_container .post_footer .post_actions_row .btn.reply {
1470 |   margin-left: auto;
1471 | }
1472 | .post_container .post_footer .post_actions_row .btn.quote:first-child,
1473 | .post_container .post_footer .post_actions_row .btn.rep_up + .btn.quote,
1474 | .post_container .post_footer .post_actions_row .post_rating + .btn.quote,
1475 | .post_container .post_footer .post_actions_row .btn.rep_down + .btn.quote {
1476 |   margin-left: auto;
1477 | }
1478 | .post_container .post_footer .post_actions_row .btn.reply:active,
1479 | .post_container .post_footer .post_actions_row .btn.quote:active,
1480 | .post_container .post_footer .post_actions_row .btn.rep_up:active,
1481 | .post_container .post_footer .post_actions_row .btn.rep_down:active {
1482 |   background: transparent;
1483 |   background-color: transparent;
1484 |   background-image: none;
1485 |   border: 0;
1486 |   border-color: transparent;
1487 |   box-shadow: none;
1488 |   outline: 0;
1489 |   -webkit-filter: none;
1490 |   filter: none;
1491 | }
1492 | .post_container .post_footer .post_actions_row .btn.rep_up > span,
1493 | .post_container .post_footer .post_actions_row .btn.rep_down > span,
1494 | .post_container .post_footer .post_actions_row .btn.reply > span,
1495 | .post_container .post_footer .post_actions_row .btn.quote > span {
1496 |   display: none;
1497 | }
1498 | .post_container .post_footer .post_actions_row .btn > .post-action-icon {
1499 |   display: block;
1500 |   flex: 0 0 auto;
1501 |   color: var(--post-action-icon-color);
1502 |   fill: none;
1503 |   stroke: currentColor;
1504 |   width: var(--post-action-icon-size);
1505 |   height: var(--post-action-icon-size);
1506 |   overflow: hidden;
1507 |   background: transparent;
1508 |   background-color: transparent;
1509 |   background-image: none;
1510 |   box-shadow: none;
1511 |   outline: 0;
1512 |   -webkit-filter: none;
1513 |   filter: none;
1514 |   opacity: 1;
1515 |   mix-blend-mode: normal;
1516 | }
1517 | .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
1518 | .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
1519 |   fill: none;
1520 |   stroke: currentColor;
1521 |   stroke-width: var(--post-action-stroke-width);
1522 |   stroke-linecap: round;
1523 |   stroke-linejoin: round;
1524 |   stroke-opacity: 1;
1525 |   vector-effect: non-scaling-stroke;
1526 | }
1527 | .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
1528 | .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
1529 | .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
1530 | .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
1531 |   stroke-width: var(--post-action-light-stroke-width);
1532 |   stroke-opacity: 1;
1533 | }
1534 | .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
1535 | .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon {
1536 |   display: block;
1537 |   flex: 0 0 auto;
1538 |   color: var(--post-action-icon-color);
1539 |   fill: currentColor;
1540 |   stroke: none;
1541 |   width: var(--post-rep-action-icon-size);
1542 |   height: var(--post-rep-action-icon-size);
1543 |   overflow: visible;
1544 |   background: transparent;
1545 |   background-color: transparent;
1546 |   background-image: none;
1547 |   box-shadow: none;
1548 |   outline: 0;
1549 |   -webkit-filter: none;
1550 |   filter: none;
1551 |   opacity: 1;
1552 |   mix-blend-mode: normal;
1553 | }
1554 | .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon *,
1555 | .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon * {
1556 |   fill-opacity: 1;
1557 | }
1558 | .post_container .post_footer .btn.vote {
1559 |   float: left;
1560 | }
1561 | .post_container .post_footer .btn.edit {
1562 |   float: right;
1563 |   margin-right: 0;
1564 |   clear: both;
1565 | }
1566 | .post_container .post_footer:after {
1567 |   content: "";
1568 |   display: table;
1569 |   clear: both;
1570 | }
1571 | .post_container:before {
1572 |   content: "";
1573 |   position: absolute;
1574 |   left: 0;
1575 |   top: 0;
1576 |   height: 100%;
1577 |   width: 100%;
1578 |   background: rgba(var(--fpda-unread-bg-rgb, 0 0 0), 0.25);
1579 |   z-index: 10;
1580 |   opacity: 0;
1581 |   pointer-events: none;
1582 | }
1583 | .post_container.active:before {
1584 |   -webkit-animation: highlight 1s;
1585 |   animation: highlight 1s;
1586 | }
1587 | .post_container:after {
1588 |   content: "";
1589 |   display: table;
1590 |   clear: both;
1591 | }
1592 | .topic_hat_entry.top_hat_entry {
1593 |   margin-top: 0;
1594 |   margin-bottom: 0;
1595 | }
1596 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry) {
1597 |   border-bottom-left-radius: 0;
1598 |   border-bottom-right-radius: 0;
1599 | }
1600 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry) .inline_hat_button {
1601 |   border-bottom: none;
1602 | }
1603 | .topic_hat_entry.top_hat_entry + .poll.poll_entry {
1604 |   margin-top: 0;
1605 |   border-top-left-radius: 0;
1606 |   border-top-right-radius: 0;
1607 | }
1608 | .topic_hat_entry.top_hat_entry + .poll.poll_entry > .title {
1609 |   border-top: 0.0625rem solid rgba(0, 0, 0, 0.05);
1610 | }
1611 | .topic_hat_entry.top_hat_entry .inline_hat_button {
1612 |   border-bottom: 0.0625rem solid rgba(0, 0, 0, 0.05);
1613 | }
1614 | .topic_hat_entry.top_hat_entry.close .inline_hat_content {
1615 |   display: none;
1616 | }
1617 | .topic_hat_entry.top_hat_entry.open .inline_hat_button {
1618 |   border-bottom: 0.0625rem solid rgba(0, 0, 0, 0.05);
1619 | }
1620 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry).open .inline_hat_button,
1621 | .topic_hat_entry.top_hat_entry:has(+ .poll.poll_entry).close .inline_hat_button {
1622 |   border-bottom: none;
1623 | }
1624 | .topic_hat_entry.top_hat_entry .inline_hat_content {
1625 |   padding-top: 0;
1626 | }
1627 | body#topic .post_container.topic_hat_fixed > .hat_button,
1628 | body#topic .post_container.topic_hat_entry > .hat_button,
1629 | body#topic .topic_hat_entry > .inline_hat_button,
1630 | body#topic .poll > .title,
1631 | body#topic .poll.poll_entry > .title {
1632 |   display: flex;
1633 |   align-items: center;
1634 |   justify-content: space-between;
1635 |   gap: 0.5rem;
1636 |   box-sizing: border-box;
1637 |   min-height: var(--topic-collapsible-header-min-height);
1638 |   color: var(--topic-collapsible-header-color);
1639 |   line-height: 1.125rem;
1640 |   padding: var(--topic-collapsible-header-padding-y) 0.5rem var(--topic-collapsible-header-padding-y) 1em;
1641 |   font-size: 0.8125rem;
1642 |   font-weight: 600;
1643 |   letter-spacing: 0.01em;
1644 |   text-transform: uppercase;
1645 |   text-align: left;
1646 | }
1647 | body#topic .post_container.topic_hat_fixed > .hat_button > span,
1648 | body#topic .post_container.topic_hat_entry > .hat_button > span,
1649 | body#topic .topic_hat_entry > .inline_hat_button > span,
1650 | body#topic .poll > .title > span,
1651 | body#topic .poll.poll_entry > .title > span {
1652 |   flex: 1 1 auto;
1653 |   min-width: 0;
1654 |   overflow: hidden;
1655 |   text-overflow: ellipsis;
1656 |   white-space: nowrap;
1657 |   color: inherit;
1658 |   line-height: 1.125rem;
1659 |   font-size: inherit;
1660 |   font-weight: inherit;
1661 |   letter-spacing: inherit;
1662 |   text-transform: inherit;
1663 | }
1664 | body#topic .post_container.topic_hat_fixed > .hat_button > .icon,
1665 | body#topic .post_container.topic_hat_entry > .hat_button > .icon,
1666 | body#topic .topic_hat_entry > .inline_hat_button > .icon,
1667 | body#topic .poll > .title > .icon,
1668 | body#topic .poll.poll_entry > .title > .icon {
1669 |   display: flex;
1670 |   align-items: center;
1671 |   justify-content: center;
1672 |   flex: 0 0 var(--topic-collapsible-header-icon-size);
1673 |   float: none;
1674 |   width: var(--topic-collapsible-header-icon-size);
1675 |   height: var(--topic-collapsible-header-icon-size);
1676 |   margin: 0 0 0 0.5rem;
1677 |   position: relative;
1678 |   right: auto;
1679 |   top: auto;
1680 |   color: var(--topic-collapsible-header-icon-color);
1681 | }
1682 | body#topic .post_container.topic_hat_fixed > .hat_button > .icon:after,
1683 | body#topic .post_container.topic_hat_entry > .hat_button > .icon:after,
1684 | body#topic .topic_hat_entry > .inline_hat_button > .icon:after,
1685 | body#topic .poll > .title > .icon:after,
1686 | body#topic .poll.poll_entry > .title > .icon:after {
1687 |   border-color: currentColor;
1688 | }
1689 | body#topic .post_container.topic_hat_fixed > .hat_button > .icon:before,
1690 | body#topic .post_container.topic_hat_entry > .hat_button > .icon:before,
1691 | body#topic .topic_hat_entry > .inline_hat_button > .icon:before,
1692 | body#topic .poll > .title > .icon:before,
1693 | body#topic .poll.poll_entry > .title > .icon:before {
1694 |   background: currentColor;
1695 | }
1696 | body#topic {
1697 |   --topic-collapsible-header-color: #212121;
1698 |   --topic-collapsible-header-icon-color: var(--topic-collapsible-header-color);
1699 |   --topic-collapsible-header-min-height: 2rem;
1700 |   --topic-collapsible-header-padding-y: 0.3125rem;
1701 |   --topic-collapsible-header-icon-size: 1.5rem;
1702 |   --topic-body-font-size: 1em;
1703 |   --topic-body-line-height: 1.48;
1704 |   --topic-quote-font-size: 1em;
1705 |   --topic-code-font-size: 0.75rem;
1706 |   --topic-code-line-height: 1.375rem;
1707 |   --topic-paragraph-spacing: 0.72em;
1708 |   --topic-inline-block-spacing: 0.625rem;
1709 |   --topic-edit-info-spacing: 0.375rem;
1710 |   --topic-post-spacing: 0.5em;
1711 |   --topic-quote-spacing: 0.75rem;
1712 | }
1713 | body#topic .post_container {
1714 |   margin-top: var(--topic-post-spacing);
1715 |   margin-bottom: var(--topic-post-spacing);
1716 | }
1717 | body#topic .post_container.blacklisted_post {
1718 |   margin-top: var(--topic-post-spacing);
1719 |   margin-bottom: var(--topic-post-spacing);
1720 |   box-shadow: none;
1721 |   min-height: 0;
1722 |   padding: 0;
1723 | }
1724 | body#topic .post_container.blacklisted_post.revealed {
1725 |   background: #ffffff;
1726 |   margin-top: var(--topic-post-spacing);
1727 |   margin-bottom: var(--topic-post-spacing);
1728 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
1729 |   position: relative;
1730 |   -webkit-border-radius: 0.875rem;
1731 |   border-radius: 0.875rem;
1732 |   overflow: hidden;
1733 |   min-height: auto;
1734 |   padding: 0;
1735 | }
1736 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder {
1737 |   display: -webkit-box;
1738 |   display: -webkit-flex;
1739 |   display: flex;
1740 |   -webkit-box-pack: center;
1741 |   -webkit-justify-content: center;
1742 |   justify-content: center;
1743 |   -webkit-box-align: center;
1744 |   -webkit-align-items: center;
1745 |   align-items: center;
1746 |   box-sizing: border-box;
1747 |   width: 100%;
1748 |   padding: 0.25rem 0.5rem;
1749 |   margin: 0.25rem 0;
1750 |   border: 0;
1751 |   background: transparent;
1752 |   font: inherit;
1753 |   font-size: 0.75rem;
1754 |   line-height: 1.25;
1755 |   color: #757575;
1756 |   opacity: 0.78;
1757 |   text-decoration: none;
1758 |   text-align: center;
1759 |   user-select: none;
1760 |   cursor: pointer;
1761 |   -webkit-tap-highlight-color: transparent;
1762 | }
1763 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder .blacklisted_post_placeholder_text {
1764 |   color: inherit !important;
1765 |   text-decoration: none !important;
1766 | }
1767 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder.aec,
1768 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:link,
1769 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:visited,
1770 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:hover,
1771 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:active,
1772 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder:focus {
1773 |   color: #757575 !important;
1774 |   text-decoration: none !important;
1775 |   background: transparent;
1776 |   outline: 0;
1777 | }
1778 | body#topic .post_container.blacklisted_post .blacklisted_post_placeholder[hidden] {
1779 |   display: none !important;
1780 | }
1781 | body#topic .post_container.blacklisted_post .blacklisted_post_content[hidden] {
1782 |   display: none !important;
1783 | }
1784 | body#topic .post_container.blacklisted_post.revealed .blacklisted_post_content {
1785 |   display: block;
1786 | }
1787 | body#topic .posts_list .theme_page_separator + .theme_page_container > .post_container.blacklisted_post:first-child {
1788 |   margin-top: 0;
1789 | }
1790 | body#topic .posts_list .theme_page_separator + .theme_page_container > .post_container:first-child:not(.blacklisted_post) {
1791 |   margin-top: 0.125rem;
1792 | }
1793 | body#topic .posts_list > .topic_hat_entry.top_hat_entry:first-child {
1794 |   margin-top: 0;
1795 | }
1796 | body#topic .poll.poll_entry {
1797 |   margin-top: 0;
1798 |   margin-bottom: var(--topic-post-spacing);
1799 | }
1800 | body#topic .post_body {
1801 |   font-size: var(--topic-body-font-size);
1802 |   line-height: var(--topic-body-line-height);
1803 |   color: #212121;
1804 | }
1805 | body#topic .post_body p {
1806 |   margin-top: 0;
1807 |   margin-bottom: var(--topic-paragraph-spacing);
1808 | }
1809 | body#topic .post_body p:last-child {
1810 |   margin-bottom: 0;
1811 | }
1812 | body#topic .post_body h1,
1813 | body#topic .post_body h2,
1814 | body#topic .post_body h3,
1815 | body#topic .post_body h4 {
1816 |   margin: 1em 0 0.45em;
1817 |   color: #212121;
1818 |   line-height: 1.25;
1819 | }
1820 | body#topic .post_body h1:first-child,
1821 | body#topic .post_body h2:first-child,
1822 | body#topic .post_body h3:first-child,
1823 | body#topic .post_body h4:first-child {
1824 |   margin-top: 0;
1825 | }
1826 | body#topic .post_body ul,
1827 | body#topic .post_body ol {
1828 |   margin-top: var(--topic-paragraph-spacing);
1829 |   margin-bottom: var(--topic-paragraph-spacing);
1830 |   padding-left: 1.35em;
1831 | }
1832 | body#topic .post_body li + li {
1833 |   margin-top: 0.25em;
1834 | }
1835 | body#topic .post_body > .post-block,
1836 | body#topic .post_body .attach_block,
1837 | body#topic .post_body img.linked-image,
1838 | body#topic .post_body img.attach {
1839 |   margin-top: var(--topic-inline-block-spacing);
1840 |   margin-bottom: var(--topic-inline-block-spacing);
1841 | }
1842 | body#topic .post_body img.linked-image,
1843 | body#topic .post_body img.attach {
1844 |   display: block;
1845 | }
1846 | body#topic .post_body .edit,
1847 | body#topic .post_body strong .edit {
1848 |   display: block;
1849 |   text-align: inherit;
1850 |   padding: 0;
1851 |   margin-left: 0;
1852 |   margin-top: 0;
1853 |   margin-bottom: 0;
1854 |   vertical-align: baseline;
1855 |   line-height: 1.35;
1856 |   white-space: normal;
1857 |   overflow-wrap: anywhere;
1858 |   word-break: break-word;
1859 |   max-width: 100%;
1860 | }
1861 | body#topic .post_body .post-edit-reason {
1862 |   display: block;
1863 |   margin-top: 0;
1864 |   margin-bottom: 0;
1865 |   line-height: 1.35;
1866 |   padding-top: 0;
1867 |   padding-bottom: 0;
1868 | }
1869 | body#topic .post-block.quote {
1870 |   margin-top: var(--topic-quote-spacing);
1871 |   margin-bottom: var(--topic-quote-spacing);
1872 |   border-left: 0;
1873 |   box-sizing: border-box;
1874 |   font-size: var(--topic-quote-font-size);
1875 |   line-height: 1.44;
1876 |   overflow: hidden;
1877 |   touch-action: pan-y;
1878 |   padding-left: 0;
1879 |   background: var(--surface-control, rgba(0, 0, 0, 0.1));
1880 |   -webkit-border-radius: 0.75rem;
1881 |   border-radius: 0.75rem;
1882 | }
1883 | body#topic .post-block.quote:before {
1884 |   content: none;
1885 | }
1886 | body#topic .post-block.quote > .block-title {
1887 |   padding: 0.625rem 0.75rem 0.3125rem 0.75rem;
1888 |   color: #212121 !important;
1889 |   font-size: 0.875rem;
1890 |   line-height: 1.25rem;
1891 | }
1892 | body#topic .post-block.quote > .block-title .title .date {
1893 |   color: #757575;
1894 |   line-height: 1rem;
1895 | }
1896 | body#topic .post-block.quote > .block-body {
1897 |   padding: 0.25rem 0.75rem 0.75rem 0.75rem;
1898 | }
1899 | body#topic .post-block.quote > .block-body > .post-block.quote {
1900 |   margin-top: 0.5rem;
1901 |   margin-bottom: 0.25rem;
1902 |   margin-left: 0;
1903 | }
1904 | body#topic .post-block.code > .block-body .lines {
1905 |   font-size: var(--topic-code-font-size);
1906 |   line-height: var(--topic-code-line-height);
1907 |   background: linear-gradient(rgba(130, 130, 130, 0.1) var(--topic-code-line-height), transparent var(--topic-code-line-height));
1908 |   background-size: 100% calc(var(--topic-code-line-height) * 2);
1909 |   -webkit-overflow-scrolling: touch;
1910 |   padding-right: 0;
1911 | }
1912 | body#topic .post-block.code > .block-body .lines > div {
1913 |   min-height: var(--topic-code-line-height);
1914 |   padding-right: 0.75rem;
1915 | }
1916 | body#topic .post-block.code > .block-body .lines > div:before {
1917 |   line-height: var(--topic-code-line-height);
1918 | }
1919 | body#topic .post-block.code {
1920 |   margin-top: var(--topic-inline-block-spacing);
1921 |   margin-bottom: var(--topic-inline-block-spacing);
1922 |   contain: paint;
1923 | }
1924 | body#topic .post-block.code > .block-title {
1925 |   padding-top: 0.5rem;
1926 |   padding-bottom: 0.5rem;
1927 |   line-height: 1.25rem;
1928 | }
1929 | body#topic .post-block.code > .block-body {
1930 |   padding-top: 0.375rem;
1931 |   padding-bottom: 0.5rem;
1932 |   overflow: hidden;
1933 | }
1934 | body#topic .post-block.spoil {
1935 |   margin-top: var(--topic-inline-block-spacing);
1936 |   margin-bottom: var(--topic-inline-block-spacing);
1937 | }
1938 | body#topic .post-block.spoil > .block-title {
1939 |   display: -webkit-box;
1940 |   display: -webkit-flex;
1941 |   display: flex;
1942 |   -webkit-box-align: center;
1943 |   -webkit-align-items: center;
1944 |   align-items: center;
1945 |   box-sizing: border-box;
1946 |   line-height: 1.25rem;
1947 |   padding-right: 5rem;
1948 | }
1949 | body#topic .post-block.spoil > .block-title > .block-controls {
1950 |   display: flex;
1951 |   align-items: center;
1952 |   justify-content: flex-end;
1953 |   height: 2.5rem;
1954 |   right: 2.5rem;
1955 |   top: 50%;
1956 |   -webkit-transform: translateY(-50%);
1957 |   transform: translateY(-50%);
1958 | }
1959 | body#topic .post-block.spoil > .block-title > .block-controls i {
1960 |   float: none;
1961 |   flex: 0 0 2.25rem;
1962 | }
1963 | body#topic .post-block.spoil > .block-title .icon {
1964 |   display: flex;
1965 |   align-items: center;
1966 |   justify-content: center;
1967 |   height: 2.5rem;
1968 |   width: 2.5rem;
1969 |   top: 50%;
1970 |   -webkit-transform: translateY(-50%);
1971 |   transform: translateY(-50%);
1972 | }
1973 | body#topic .post-block.spoil.open > .block-title .icon {
1974 |   -webkit-transform: translateY(-50%) rotateX(180deg) translateZ(0);
1975 |   transform: translateY(-50%) rotateX(180deg) translateZ(0);
1976 | }
1977 | body#topic .post-block.spoil.open > .block-body {
1978 |   padding-top: 0.5rem;
1979 | }
1980 | body#topic .post-block.spoil > .block-body > .post-block.spoil {
1981 |   margin-top: 0.5rem;
1982 |   margin-bottom: 0.5rem;
1983 | }
1984 | body#topic.density_comfortable .post_body .post-block.quote,
1985 | body#topic.density_comfortable .post_body .post-block.spoil {
1986 |   margin-bottom: 0.375rem;
1987 | }
1988 | body#topic.density_comfortable .post_body .post-block.quote + br,
1989 | body#topic.density_comfortable .post_body .post-block.spoil + br,
1990 | body#topic.density_comfortable .post_body .post-block.quote + br + br,
1991 | body#topic.density_comfortable .post_body .post-block.spoil + br + br {
1992 |   display: none;
1993 | }
1994 | body#topic.density_comfortable .post_body .post-block.quote + p,
1995 | body#topic.density_comfortable .post_body .post-block.spoil + p,
1996 | body#topic.density_comfortable .post_body .post-block.quote + br + p,
1997 | body#topic.density_comfortable .post_body .post-block.spoil + br + p,
1998 | body#topic.density_comfortable .post_body .post-block.quote + br + br + p,
1999 | body#topic.density_comfortable .post_body .post-block.spoil + br + br + p {
2000 |   margin-top: 0;
2001 | }
2002 | body#topic.density_comfortable {
2003 |   --topic-collapsible-header-min-height: 2rem;
2004 |   --topic-collapsible-header-padding-y: 0.3125rem;
2005 |   --topic-collapsible-header-icon-size: 1.5rem;
2006 | }
2007 | body#topic.density_comfortable .post_container .post_footer {
2008 |   padding: 0 0.75rem 0.5rem 0.75rem;
2009 | }
2010 | body#topic.density_comfortable .post_container .post_footer .post_actions_row {
2011 |   --post-action-gap: 0.25rem;
2012 |   --post-action-button-size: 2.1rem;
2013 |   --post-action-icon-size: 1.2rem;
2014 |   --post-rep-action-icon-size: var(--post-action-icon-size);
2015 |   --post-action-radius: 0;
2016 | }
2017 | body#topic .post_container .post_header .header_wrapper {
2018 |   position: relative !important;
2019 |   --post-header-action-right: -1rem !important;
2020 |   --post-header-action-width: 2.5rem !important;
2021 |   --post-header-inline-end-reserve: 2.5rem !important;
2022 |   --post-header-menu-axis-overhang: 2.25rem !important;
2023 |   padding-right: var(--post-header-inline-end-reserve) !important;
2024 |   box-sizing: border-box !important;
2025 | }
2026 | body#topic .post_container .post_header .header_wrapper > .inf.post_meta {
2027 |   display: -webkit-box !important;
2028 |   display: -webkit-flex !important;
2029 |   display: flex !important;
2030 |   -webkit-box-align: baseline !important;
2031 |   -webkit-align-items: baseline !important;
2032 |   align-items: baseline !important;
2033 |   gap: 0.5rem !important;
2034 |   min-width: 0 !important;
2035 |   width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
2036 |   max-width: none !important;
2037 |   margin-right: 0 !important;
2038 |   padding-right: 0 !important;
2039 |   box-sizing: border-box !important;
2040 | }
2041 | body#topic .post_container .post_header .header_wrapper > .inf.post_meta > .group_text {
2042 |   -webkit-box-flex: 1 !important;
2043 |   -webkit-flex: 1 1 auto !important;
2044 |   flex: 1 1 auto !important;
2045 |   min-width: 0 !important;
2046 |   overflow: hidden !important;
2047 |   text-overflow: ellipsis !important;
2048 |   white-space: nowrap !important;
2049 | }
2050 | body#topic .post_container .post_header .header_wrapper > .inf.post_meta > .date {
2051 |   -webkit-box-flex: 0 !important;
2052 |   -webkit-flex: 0 0 auto !important;
2053 |   flex: 0 0 auto !important;
2054 |   margin-left: auto !important;
2055 |   max-width: 9rem !important;
2056 |   overflow: hidden !important;
2057 |   text-align: right !important;
2058 |   text-overflow: ellipsis !important;
2059 |   white-space: nowrap !important;
2060 | }
2061 | body#topic .post_container .post_header .header_wrapper > .inf.user_post_count {
2062 |   display: -webkit-box !important;
2063 |   display: -webkit-flex !important;
2064 |   display: flex !important;
2065 |   -webkit-box-align: center !important;
2066 |   -webkit-align-items: center !important;
2067 |   align-items: center !important;
2068 |   visibility: visible !important;
2069 |   height: auto !important;
2070 |   min-height: 0.875rem !important;
2071 |   overflow: visible !important;
2072 |   color: #757575 !important;
2073 | }
2074 | body#topic .post-block.quote.smart-quote-collapsible {
2075 |   overflow: hidden;
2076 | }
2077 | body#topic .post-block.quote.smart-quote-collapsible > .block-body {
2078 |   -webkit-transition: max-height 0.2s cubic-bezier(0.4, 0, 0.2, 1);
2079 |   transition: max-height 0.2s cubic-bezier(0.4, 0, 0.2, 1);
2080 | }
2081 | body#topic .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body {
2082 |   max-height: 11.25rem;
2083 |   overflow: hidden;
2084 | }
2085 | body#topic .post-block.quote.smart-quote-collapsible.smart-quote-collapsed > .block-body:after {
2086 |   content: "";
2087 |   position: absolute;
2088 |   left: 0;
2089 |   right: 0;
2090 |   bottom: 0;
2091 |   height: 3rem;
2092 |   pointer-events: none;
2093 |   box-shadow: inset 0 -3rem 2rem -1.5rem rgba(117, 117, 117, 0.1);
2094 | }
2095 | body#topic .post-block.quote.smart-quote-collapsible.smart-quote-expanded > .block-body {
2096 |   max-height: none;
2097 | }
2098 | body#topic .post-block.quote.smart-quote-collapsible > .smart-quote-toggle {
2099 |   -webkit-touch-callout: none;
2100 |   -webkit-user-select: none;
2101 |   -khtml-user-select: none;
2102 |   -moz-user-select: none;
2103 |   -ms-user-select: none;
2104 |   user-select: none;
2105 |   display: block;
2106 |   padding: 0.5rem 0.75rem;
2107 |   color: #616161;
2108 |   font-size: 0.875rem;
2109 |   font-weight: bold;
2110 |   text-align: center;
2111 | }
2112 | body#topic .post-block.quote.smart-quote-collapsible > .smart-quote-toggle:active {
2113 |   background: rgba(117, 117, 117, 0.16);
2114 | }
2115 | body#search .post_container .post_header .header_wrapper {
2116 |   position: relative !important;
2117 |   --post-header-action-right: -1rem !important;
2118 |   --post-header-action-width: 2.5rem !important;
2119 |   --post-header-inline-end-reserve: 2.5rem !important;
2120 |   --post-header-menu-axis-overhang: 2.25rem !important;
2121 |   padding-right: var(--post-header-inline-end-reserve) !important;
2122 |   box-sizing: border-box !important;
2123 | }
2124 | body#search .post_container .post_header .header_wrapper > .inf.post_meta {
2125 |   display: -webkit-box !important;
2126 |   display: -webkit-flex !important;
2127 |   display: flex !important;
2128 |   -webkit-box-align: baseline !important;
2129 |   -webkit-align-items: baseline !important;
2130 |   align-items: baseline !important;
2131 |   gap: 0.5rem !important;
2132 |   min-width: 0 !important;
2133 |   width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
2134 |   max-width: none !important;
2135 |   margin-right: 0 !important;
2136 |   padding-right: 0 !important;
2137 |   box-sizing: border-box !important;
2138 | }
2139 | body#search .post_container .post_header .header_wrapper > .inf.post_meta > .group_text {
2140 |   -webkit-box-flex: 1 !important;
2141 |   -webkit-flex: 1 1 auto !important;
2142 |   flex: 1 1 auto !important;
2143 |   min-width: 0 !important;
2144 |   overflow: hidden !important;
2145 |   text-overflow: ellipsis !important;
2146 |   white-space: nowrap !important;
2147 | }
2148 | body#search .post_container .post_header .header_wrapper > .inf.post_meta > .date {
2149 |   -webkit-box-flex: 0 !important;
2150 |   -webkit-flex: 0 0 auto !important;
2151 |   flex: 0 0 auto !important;
2152 |   margin-left: auto !important;
2153 |   max-width: 9rem !important;
2154 |   overflow: hidden !important;
2155 |   text-align: right !important;
2156 |   text-overflow: ellipsis !important;
2157 |   white-space: nowrap !important;
2158 | }
2159 | body#topic.density_compact .post_container .post_header .header_wrapper {
2160 |   --post-header-action-right: -0.5rem !important;
2161 |   --post-header-action-width: 2.125rem !important;
2162 |   --post-header-inline-end-reserve: 2.375rem !important;
2163 |   --post-header-menu-axis-overhang: 1.8125rem !important;
2164 |   padding-right: var(--post-header-inline-end-reserve) !important;
2165 | }
2166 | body#topic.density_compact .post_container .post_header .header_wrapper > .inf.post_meta {
2167 |   width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
2168 |   margin-right: 0 !important;
2169 | }
2170 | body#topic.density_compact .post_container .post_header .header_wrapper > .inf.post_meta > .date {
2171 |   max-width: 7.75rem !important;
2172 | }
2173 | body#topic.density_compact {
2174 |   --topic-collapsible-header-min-height: 2rem;
2175 |   --topic-collapsible-header-padding-y: 0.25rem;
2176 |   --topic-collapsible-header-icon-size: 1.5rem;
2177 |   --topic-body-line-height: 1.38;
2178 |   --topic-code-line-height: 1.1875rem;
2179 |   --topic-paragraph-spacing: 0.38em;
2180 |   --topic-inline-block-spacing: 0.3125rem;
2181 |   --topic-edit-info-spacing: 0.25rem;
2182 |   --topic-post-spacing: 0.3125rem;
2183 |   --topic-quote-spacing: 0.3125rem;
2184 | }
2185 | body#topic.density_compact .post_container {
2186 |   margin: var(--topic-post-spacing) 0;
2187 | }
2188 | body#topic.density_compact .post_container .hat_button {
2189 |   min-height: 2rem;
2190 |   line-height: 2rem;
2191 |   padding-top: 0;
2192 |   padding-bottom: 0;
2193 | }
2194 | body#topic.density_compact .post_container .hat_button + .hat_content {
2195 |   padding-top: 0.25rem;
2196 | }
2197 | body#topic.density_compact .post_container .hat_button .icon {
2198 |   height: 1.5rem;
2199 |   margin-top: 0;
2200 |   margin-bottom: 0;
2201 | }
2202 | body#topic.density_compact .topic_hat_entry.post_container > .hat_button .icon {
2203 |   margin-top: 0;
2204 |   margin-bottom: 0;
2205 | }
2206 | body#topic.density_compact .post_container .post_header {
2207 |   padding: 0.5rem 0.75rem 0 0.75rem;
2208 | }
2209 | body#topic.density_compact .post_container .post_header .header_wrapper {
2210 |   --post-header-action-right: -0.5rem;
2211 |   --post-header-action-width: 2.125rem;
2212 |   --post-header-inline-end-reserve: 2.375rem;
2213 |   --post-header-menu-axis-overhang: 1.8125rem;
2214 |   min-height: 3.625rem;
2215 |   display: flex;
2216 |   flex-direction: column;
2217 |   justify-content: center;
2218 |   padding-left: 4.25em;
2219 |   padding-right: var(--post-header-inline-end-reserve);
2220 |   box-sizing: border-box;
2221 | }
2222 | body#topic.density_compact .post_container .post_header .avatar {
2223 |   position: absolute;
2224 |   left: 0;
2225 |   top: 0;
2226 | }
2227 | body#topic.density_compact .post_container .post_header .inf {
2228 |   position: relative;
2229 |   left: auto;
2230 |   max-width: none;
2231 |   margin-top: 0;
2232 |   line-height: 1.25;
2233 | }
2234 | body#topic.density_compact .post_container .post_header .inf.nick {
2235 |   display: flex;
2236 |   top: auto;
2237 |   padding-top: 0;
2238 |   padding-right: 0.25rem;
2239 | }
2240 | body#topic.density_compact .post_container .post_header .inf.post_meta {
2241 |   margin-top: 0.1875rem;
2242 |   width: calc(100% + var(--post-header-menu-axis-overhang));
2243 |   margin-right: 0;
2244 |   padding-right: 0;
2245 |   min-height: 1rem;
2246 | }
2247 | body#topic.density_compact .post_container .post_header .inf.post_meta .group_text {
2248 |   margin-right: 0;
2249 | }
2250 | body#topic.density_compact .post_container .post_header .inf.post_meta .date {
2251 |   max-width: 7.75rem;
2252 | }
2253 | body#topic.density_compact .post_container .post_header .inf.post_meta + .user_post_count {
2254 |   margin-top: 0.125rem;
2255 | }
2256 | body#topic.density_compact .post_container .post_header .inf.post_meta .group_text > span,
2257 | body#topic.density_compact .post_container .post_header .inf.post_meta .date > span {
2258 |   font-size: 0.75rem;
2259 | }
2260 | body#topic.density_compact .post_container .post_header .inf.user_post_count {
2261 |   gap: 0.1875rem;
2262 | }
2263 | body#topic.density_compact .post_container .post_header .inf.user_post_count > span {
2264 |   font-size: 0.6875rem;
2265 | }
2266 | body#topic.density_compact .post_container .post_header .inf.user_post_count .user_post_count_icon {
2267 |   width: 0.6875rem;
2268 |   height: 0.6875rem;
2269 | }
2270 | body#topic.density_compact .post_container .post_header .inf.menu {
2271 |   position: absolute;
2272 |   right: var(--post-header-action-right);
2273 |   top: -0.375rem;
2274 |   height: 2.5rem;
2275 |   width: var(--post-header-action-width);
2276 |   background-size: 1.25rem 1.25rem;
2277 | }
2278 | body#topic.density_compact .post_container .post_header .inf.number {
2279 |   display: none;
2280 | }
2281 | body#topic.density_compact .post_container .post_body {
2282 |   padding: 0.5rem 0.75rem;
2283 | }
2284 | body#topic.density_compact .post_container .post_body .postcolor {
2285 |   margin: -0.5rem -0.75rem;
2286 |   padding: 0.5rem 0.75rem;
2287 | }
2288 | body#topic.density_compact .post_container .post_body a.anchor {
2289 |   height: 1.25rem;
2290 |   margin-top: 0.25rem;
2291 | }
2292 | body#topic.density_compact .post_container .post_body .edit {
2293 |   padding: var(--topic-edit-info-spacing) 0;
2294 |   line-height: 1.125rem;
2295 | }
2296 | body#topic.density_compact .post_container .post_body .post-edit-reason {
2297 |   padding: 0;
2298 |   margin-top: 0;
2299 |   line-height: 1.125rem;
2300 | }
2301 | body#topic.density_compact .post_container .post_footer {
2302 |   padding: 0 0.75rem 0.3125rem 0.75rem;
2303 | }
2304 | body#topic.density_compact .post_container .post_footer .post_actions_row {
2305 |   --post-action-gap: 0.171875rem;
2306 |   --post-action-button-size: 2.042184375rem;
2307 |   --post-action-icon-size: 1.195425rem;
2308 |   --post-rep-action-icon-size: var(--post-action-icon-size);
2309 |   --post-action-radius: 0;
2310 |   margin: 0 0.171875rem 0.125rem 0.171875rem;
2311 | }
2312 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.reply,
2313 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.quote,
2314 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_up,
2315 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_down {
2316 |   width: var(--post-action-button-size);
2317 |   height: var(--post-action-button-size);
2318 |   min-height: var(--post-action-button-size);
2319 |   min-width: var(--post-action-button-size);
2320 |   padding: 0;
2321 |   border: 0;
2322 |   background: transparent;
2323 |   background-color: transparent;
2324 |   background-image: none;
2325 |   box-shadow: none;
2326 |   outline: 0;
2327 |   -webkit-filter: none;
2328 |   filter: none;
2329 |   -webkit-border-radius: var(--post-action-radius);
2330 |   border-radius: var(--post-action-radius);
2331 | }
2332 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
2333 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.quote > .post-action-icon,
2334 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_up > .post-action-icon,
2335 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_down > .post-action-icon {
2336 |   width: var(--post-action-icon-size);
2337 |   height: var(--post-action-icon-size);
2338 | }
2339 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
2340 | body#topic.density_compact .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon {
2341 |   width: var(--post-rep-action-icon-size);
2342 |   height: var(--post-rep-action-icon-size);
2343 | }
2344 | body#topic.density_compact .post_container .post_footer .post_rating {
2345 |   padding: 0.125rem 0.375rem;
2346 |   font-size: 0.75rem;
2347 | }
2348 | body#topic.density_compact .topic_hat_fixed.post_container .post_header,
2349 | body#topic.density_compact .topic_hat_entry.post_container .post_header {
2350 |   padding-top: 0.5rem;
2351 | }
2352 | body#topic.density_compact .post-block {
2353 |   margin-top: 0.5rem;
2354 |   margin-bottom: 0.5rem;
2355 | }
2356 | body#topic.density_compact .post-block > .block-title {
2357 |   padding: 0.4375rem 0.75rem;
2358 |   line-height: 1.125rem;
2359 | }
2360 | body#topic.density_compact .post-block > .block-body {
2361 |   padding: 0.375rem 0.75rem 0.625rem 0.75rem;
2362 | }
2363 | body#topic.density_compact .post-block.spoil > .block-title {
2364 |   min-height: 1.125rem;
2365 |   padding-right: 4.5rem;
2366 | }
2367 | body#topic.density_compact .post-block.spoil.close {
2368 |   margin-bottom: 0.375rem;
2369 | }
2370 | body#topic.density_compact .post-block.spoil.close + br,
2371 | body#topic.density_compact .post-block.spoil.close + br + br {
2372 |   display: none;
2373 | }
2374 | body#topic.density_compact .post-block.spoil.close + .edit,
2375 | body#topic.density_compact .post-block.spoil.close + .post-edit-reason,
2376 | body#topic.density_compact .post-block.spoil.close + strong .edit,
2377 | body#topic.density_compact .post-block.spoil.close + br + .edit,
2378 | body#topic.density_compact .post-block.spoil.close + br + .post-edit-reason,
2379 | body#topic.density_compact .post-block.spoil.close + br + strong .edit,
2380 | body#topic.density_compact .post-block.spoil.close + br + br + .edit,
2381 | body#topic.density_compact .post-block.spoil.close + br + br + .post-edit-reason,
2382 | body#topic.density_compact .post-block.spoil.close + br + br + strong .edit {
2383 |   margin-top: 0;
2384 |   padding-top: 0;
2385 | }
2386 | body#topic.density_compact .post-block.spoil > .block-title > .block-controls {
2387 |   height: 2.25rem;
2388 |   right: 2.25rem;
2389 | }
2390 | body#topic.density_compact .post-block.spoil > .block-title .icon {
2391 |   height: 2.25rem;
2392 |   width: 2.25rem;
2393 | }
2394 | body#topic.density_compact .post-block.spoil.open > .block-title .icon {
2395 |   -webkit-transform: translateY(-50%) rotateX(180deg) translateZ(0);
2396 |   transform: translateY(-50%) rotateX(180deg) translateZ(0);
2397 | }
2398 | body#topic.density_compact .post-block.spoil > .block-body > .btns_container > .spoil_close {
2399 |   margin-top: 0.625rem;
2400 |   margin-left: -0.375rem;
2401 |   margin-right: -0.375rem;
2402 |   margin-bottom: -0.375rem;
2403 |   padding: 0.375rem;
2404 | }
2405 | body#topic.density_compact .post-block.quote {
2406 |   margin-top: var(--topic-quote-spacing);
2407 |   margin-bottom: var(--topic-quote-spacing);
2408 | }
2409 | body#topic.density_compact .post-block.quote + br,
2410 | body#topic.density_compact .post-block.quote + br + br {
2411 |   display: none;
2412 | }
2413 | body#topic.density_compact .post-block.quote + p,
2414 | body#topic.density_compact .post-block.quote + br + p,
2415 | body#topic.density_compact .post-block.quote + br + br + p {
2416 |   margin-top: 0;
2417 | }
2418 | body#topic.density_compact .post-block.quote > .block-title {
2419 |   padding-top: 0.375rem;
2420 |   padding-bottom: 0.375rem;
2421 | }
2422 | body#topic.density_compact .post-block.quote > .block-body {
2423 |   padding-top: 0.25rem;
2424 |   padding-bottom: 0.5rem;
2425 | }
2426 | body#topic.density_compact .post-block.code > .block-title {
2427 |   padding-top: 0.375rem;
2428 |   padding-bottom: 0.375rem;
2429 | }
2430 | body#topic.density_compact .post-block.code > .block-body {
2431 |   padding: 0.25rem 0 0.375rem 2.5rem;
2432 | }
2433 | body#topic.density_compact .post-block.code > .block-body .lines {
2434 |   padding-right: 0.5rem;
2435 | }
2436 | body#topic.density_compact .post-block.code > .block-body .lines > div:before {
2437 |   width: 2.5rem;
2438 | }
2439 | body#topic.density_compact .attach_block {
2440 |   padding-left: 3rem;
2441 |   padding-top: 0.1875rem;
2442 |   padding-bottom: 0.1875rem;
2443 |   padding-right: 0.25rem;
2444 |   min-height: 2.625rem;
2445 | }
2446 | body#topic.density_compact .attach_block .icon {
2447 |   top: 0.1875rem;
2448 |   left: 0.1875rem;
2449 |   height: 2.25rem;
2450 |   width: 2.25rem;
2451 | }
2452 | body#topic.density_compact .attach_block .icon:after {
2453 |   height: 2.25rem;
2454 |   width: 2.25rem;
2455 | }
2456 | body#topic.density_compact .post_container.topic_hat_fixed > .hat_button,
2457 | body#topic.density_compact .post_container.topic_hat_entry > .hat_button,
2458 | body#topic.density_compact .poll > .title,
2459 | body#topic.density_compact .poll.poll_entry > .title {
2460 |   min-height: 2rem;
2461 |   padding-top: 0.25rem;
2462 |   padding-bottom: 0.25rem;
2463 | }
2464 | body#topic.density_compact .post_container.topic_hat_fixed > .hat_button > span,
2465 | body#topic.density_compact .post_container.topic_hat_entry > .hat_button > span,
2466 | body#topic.density_compact .poll > .title > span,
2467 | body#topic.density_compact .poll.poll_entry > .title > span {
2468 |   font-size: 0.8125rem;
2469 | }
2470 | body#topic.density_compact .post_container.topic_hat_fixed > .hat_button > .icon,
2471 | body#topic.density_compact .post_container.topic_hat_entry > .hat_button > .icon,
2472 | body#topic.density_compact .poll > .title > .icon,
2473 | body#topic.density_compact .poll.poll_entry > .title > .icon {
2474 |   flex-basis: 1.5rem;
2475 |   width: 1.5rem;
2476 |   height: 1.5rem;
2477 | }
2478 | body#topic.density_compact .poll.poll_entry {
2479 |   margin: 0.375rem 0;
2480 | }
2481 | body#topic.density_compact .poll.poll_entry > .body .questions .question > .title {
2482 |   padding-top: 0.5rem;
2483 |   padding-bottom: 0.375rem;
2484 | }
2485 | body#topic.density_compact .poll.poll_entry > .body .questions .question > .items {
2486 |   padding-top: 0.25rem;
2487 |   padding-bottom: 0.5rem;
2488 | }
2489 | body#topic.density_super_compact {
2490 |   --topic-collapsible-header-min-height: 1.875rem;
2491 |   --topic-collapsible-header-padding-y: 0.25rem;
2492 |   --topic-collapsible-header-icon-size: 1.375rem;
2493 |   --topic-body-line-height: 1.32;
2494 |   --topic-code-line-height: 1.125rem;
2495 |   --topic-paragraph-spacing: 0.28em;
2496 |   --topic-inline-block-spacing: 0.25rem;
2497 |   --topic-edit-info-spacing: 0.125rem;
2498 |   --topic-post-spacing: 0.1875rem;
2499 |   --topic-quote-spacing: 0.25rem;
2500 | }
2501 | body#topic.density_super_compact .post_container .hat_button {
2502 |   min-height: 2.125rem;
2503 |   line-height: 2.125rem;
2504 | }
2505 | body#topic.density_super_compact .post_container .hat_button + .hat_content {
2506 |   padding-top: 0.125rem;
2507 | }
2508 | body#topic.density_super_compact .post_container .hat_button .icon {
2509 |   height: 2.125rem;
2510 |   background-size: 1.125rem 1.125rem;
2511 | }
2512 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button,
2513 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button {
2514 |   display: -webkit-box;
2515 |   display: -webkit-flex;
2516 |   display: flex;
2517 |   -webkit-box-align: center;
2518 |   -webkit-align-items: center;
2519 |   align-items: center;
2520 |   -webkit-box-pack: justify;
2521 |   -webkit-justify-content: space-between;
2522 |   justify-content: space-between;
2523 |   box-sizing: border-box;
2524 |   min-height: 2.5rem;
2525 |   padding: 0.375rem 0.75rem;
2526 |   line-height: 1.2;
2527 |   text-align: left;
2528 | }
2529 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > span,
2530 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > span {
2531 |   -webkit-box-flex: 1;
2532 |   -webkit-flex: 1 1 auto;
2533 |   flex: 1 1 auto;
2534 |   min-width: 0;
2535 |   overflow: hidden;
2536 |   text-overflow: ellipsis;
2537 |   white-space: nowrap;
2538 |   font-size: 0.75rem;
2539 |   line-height: 1.2;
2540 | }
2541 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > .icon,
2542 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > .icon {
2543 |   -webkit-box-flex: 0;
2544 |   -webkit-flex: 0 0 2rem;
2545 |   flex: 0 0 2rem;
2546 |   float: none;
2547 |   width: 2rem;
2548 |   height: 2rem;
2549 |   margin: 0 0 0 0.5rem;
2550 | }
2551 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > .icon:after,
2552 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > .icon:after {
2553 |   -webkit-transform: rotate(-45deg);
2554 |   transform: rotate(-45deg);
2555 | }
2556 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button + .hat_content {
2557 |   padding-top: 0.125rem;
2558 | }
2559 | body#topic.density_super_compact .post_container .post_header {
2560 |   padding: 0.375rem 0.625rem 0 0.625rem;
2561 | }
2562 | body#topic.density_super_compact .post_container .post_header .header_wrapper {
2563 |   min-height: 2.75rem;
2564 |   padding-left: 2.75rem;
2565 | }
2566 | body#topic.density_super_compact .post_container .post_header .avatar,
2567 | body#topic.density_super_compact .post_container .post_header .avatar .img,
2568 | body#topic.density_super_compact .post_container .post_header .avatar .letter {
2569 |   width: 2.25rem;
2570 |   height: 2.25rem;
2571 | }
2572 | body#topic.density_super_compact .post_container .post_header .avatar .letter {
2573 |   line-height: 2.25rem;
2574 | }
2575 | body#topic.density_super_compact .post_container .post_header .avatar .reputation {
2576 |   min-width: 1.125rem;
2577 |   height: 1.125rem;
2578 |   line-height: 1.125rem;
2579 | }
2580 | body#topic.density_super_compact .post_container .post_header .avatar .reputation > span {
2581 |   font-size: 0.625rem;
2582 | }
2583 | body#topic.density_super_compact .post_container .post_header .inf {
2584 |   line-height: 1.18;
2585 | }
2586 | body#topic.density_super_compact .post_container .post_header .inf.nick {
2587 |   font-size: 0.875rem;
2588 | }
2589 | body#topic.density_super_compact .post_container .post_header .inf.post_meta {
2590 |   margin-top: 0.0625rem;
2591 |   min-height: 0.875rem;
2592 | }
2593 | body#topic.density_super_compact .post_container .post_header .inf.post_meta .group_text > span,
2594 | body#topic.density_super_compact .post_container .post_header .inf.post_meta .date > span {
2595 |   font-size: 0.6875rem;
2596 | }
2597 | body#topic.density_super_compact .post_container .post_header .inf.post_meta .date {
2598 |   max-width: 7rem;
2599 | }
2600 | body#topic.density_super_compact .post_container .post_header .inf.post_meta + .user_post_count {
2601 |   display: none !important;
2602 | }
2603 | body#topic.density_super_compact .post_container .post_header .inf.menu {
2604 |   top: -0.4375rem;
2605 |   height: 2.125rem;
2606 |   background-size: 1.125rem 1.125rem;
2607 | }
2608 | body#topic.density_super_compact .post_container .post_body {
2609 |   padding: 0.375rem 0.625rem;
2610 | }
2611 | body#topic.density_super_compact .post_container .post_body .postcolor {
2612 |   margin: -0.375rem -0.625rem;
2613 |   padding: 0.375rem 0.625rem;
2614 | }
2615 | body#topic.density_super_compact .post_container .post_body .edit,
2616 | body#topic.density_super_compact .post_container .post_body strong .edit {
2617 |   display: block;
2618 |   position: static;
2619 |   clear: both;
2620 |   font-size: 0.6875rem;
2621 |   line-height: 1.35;
2622 |   margin-top: 0;
2623 |   margin-bottom: 0;
2624 |   margin-left: 0;
2625 |   vertical-align: baseline;
2626 |   white-space: normal;
2627 |   overflow-wrap: anywhere;
2628 |   word-break: break-word;
2629 |   max-width: 100%;
2630 | }
2631 | body#topic.density_super_compact .post_container .post_body .post-edit-reason {
2632 |   display: block;
2633 |   position: static;
2634 |   clear: both;
2635 |   font-size: 0.6875rem;
2636 |   line-height: 1rem;
2637 |   margin-top: 0.125rem;
2638 |   margin-bottom: 0.125rem;
2639 | }
2640 | body#topic.density_super_compact .post_container .post_body p + .edit,
2641 | body#topic.density_super_compact .post_container .post_body p + .post-edit-reason,
2642 | body#topic.density_super_compact .post_container .post_body p + strong .edit,
2643 | body#topic.density_super_compact .post_container .post_body br + .edit,
2644 | body#topic.density_super_compact .post_container .post_body br + .post-edit-reason,
2645 | body#topic.density_super_compact .post_container .post_body br + strong .edit,
2646 | body#topic.density_super_compact .post_container .post_body br + br + .edit,
2647 | body#topic.density_super_compact .post_container .post_body br + br + .post-edit-reason,
2648 | body#topic.density_super_compact .post_container .post_body br + br + strong .edit {
2649 |   padding-top: 0;
2650 | }
2651 | body#topic.density_super_compact .post_container .post_body br:has(+ .edit),
2652 | body#topic.density_super_compact .post_container .post_body br:has(+ .post-edit-reason),
2653 | body#topic.density_super_compact .post_container .post_body br:has(+ strong .edit),
2654 | body#topic.density_super_compact .post_container .post_body br:has(+ br + .edit),
2655 | body#topic.density_super_compact .post_container .post_body br:has(+ br + .post-edit-reason),
2656 | body#topic.density_super_compact .post_container .post_body br:has(+ br + strong .edit) {
2657 |   display: none;
2658 | }
2659 | body#topic.density_super_compact .post_container .post_body br + .edit,
2660 | body#topic.density_super_compact .post_container .post_body br + .post-edit-reason,
2661 | body#topic.density_super_compact .post_container .post_body br + strong .edit,
2662 | body#topic.density_super_compact .post_container .post_body br + br + .edit,
2663 | body#topic.density_super_compact .post_container .post_body br + br + .post-edit-reason,
2664 | body#topic.density_super_compact .post_container .post_body br + br + strong .edit {
2665 |   margin-top: 0.125rem;
2666 | }
2667 | body#topic.density_super_compact .post_container .post_footer {
2668 |   padding: 0 0.625rem 0.3125rem 0.625rem;
2669 | }
2670 | body#topic.density_super_compact .post_container .post_footer .post_actions_row {
2671 |   --post-action-gap: 0.125rem;
2672 |   --post-action-button-size: 1.45805625rem;
2673 |   --post-action-icon-size: 0.929510859375rem;
2674 |   --post-rep-action-icon-size: var(--post-action-icon-size);
2675 |   --post-action-radius: 0;
2676 |   margin: 0 0.125rem 0.125rem 0.125rem;
2677 | }
2678 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.reply,
2679 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.quote,
2680 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_up,
2681 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_down {
2682 |   width: var(--post-action-button-size);
2683 |   height: var(--post-action-button-size);
2684 |   min-height: var(--post-action-button-size);
2685 |   min-width: var(--post-action-button-size);
2686 |   font-size: 0.75rem;
2687 |   -webkit-border-radius: var(--post-action-radius);
2688 |   border-radius: var(--post-action-radius);
2689 | }
2690 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
2691 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.quote > .post-action-icon,
2692 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_up > .post-action-icon,
2693 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_down > .post-action-icon {
2694 |   width: var(--post-action-icon-size);
2695 |   height: var(--post-action-icon-size);
2696 | }
2697 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
2698 | body#topic.density_super_compact .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon {
2699 |   width: var(--post-rep-action-icon-size);
2700 |   height: var(--post-rep-action-icon-size);
2701 | }
2702 | body#topic.density_super_compact .post_container .post_footer .post_rating {
2703 |   padding: 0.0625rem 0.3125rem;
2704 |   font-size: 0.6875rem;
2705 | }
2706 | body#topic.density_super_compact .post-block {
2707 |   margin-top: 0.3125rem;
2708 |   margin-bottom: 0.3125rem;
2709 | }
2710 | body#topic.density_super_compact .post-block > .block-title {
2711 |   padding: 0.3125rem 0.625rem;
2712 |   line-height: 1rem;
2713 | }
2714 | body#topic.density_super_compact .post-block > .block-body {
2715 |   padding: 0.25rem 0.625rem 0.375rem 0.625rem;
2716 | }
2717 | body#topic.density_super_compact .post-block.spoil.close {
2718 |   margin-bottom: 0.25rem;
2719 | }
2720 | body#topic.density_super_compact .post-block.spoil > .block-title {
2721 |   min-height: 1rem;
2722 |   padding-right: 3.75rem;
2723 | }
2724 | body#topic.density_super_compact .post-block.spoil > .block-title > .block-controls {
2725 |   height: 1.875rem;
2726 |   right: 1.875rem;
2727 | }
2728 | body#topic.density_super_compact .post-block.spoil > .block-title .icon {
2729 |   height: 1.875rem;
2730 |   width: 1.875rem;
2731 |   top: 50%;
2732 |   -webkit-transform: translateY(-50%);
2733 |   transform: translateY(-50%);
2734 | }
2735 | body#topic.density_super_compact .post-block.spoil.open > .block-title .icon {
2736 |   -webkit-transform: translateY(-50%) rotateX(180deg) translateZ(0);
2737 |   transform: translateY(-50%) rotateX(180deg) translateZ(0);
2738 | }
2739 | body#topic.density_super_compact .post-block.spoil > .block-body > .btns_container > .spoil_close {
2740 |   margin-top: 0.375rem;
2741 |   padding: 0.3125rem;
2742 | }
2743 | body#topic.density_super_compact .post-block.quote {
2744 |   margin-bottom: 0.125rem;
2745 | }
2746 | body#topic.density_super_compact .post-block.quote + br,
2747 | body#topic.density_super_compact .post-block.quote + br + br {
2748 |   display: none;
2749 | }
2750 | body#topic.density_super_compact .post-block.quote + p,
2751 | body#topic.density_super_compact .post-block.quote + br + p,
2752 | body#topic.density_super_compact .post-block.quote + br + br + p {
2753 |   margin-top: 0;
2754 | }
2755 | body#topic.density_super_compact .post-block.quote > .block-title {
2756 |   padding-top: 0.25rem;
2757 |   padding-bottom: 0.25rem;
2758 | }
2759 | body#topic.density_super_compact .post-block.quote > .block-body {
2760 |   padding-top: 0.1875rem;
2761 |   padding-bottom: 0.3125rem;
2762 | }
2763 | body#topic.density_super_compact .post-block.code > .block-body {
2764 |   padding: 0.1875rem 0 0.3125rem 2.125rem;
2765 | }
2766 | body#topic.density_super_compact .post-block.code > .block-body .lines {
2767 |   padding-right: 0.375rem;
2768 | }
2769 | body#topic.density_super_compact .post-block.code > .block-body .lines > div:before {
2770 |   width: 2.125rem;
2771 | }
2772 | body#topic.density_super_compact .attach_block {
2773 |   min-height: 2.25rem;
2774 |   padding-left: 2.625rem;
2775 |   padding-top: 0.125rem;
2776 |   padding-bottom: 0.125rem;
2777 | }
2778 | body#topic.density_super_compact .attach_block .icon {
2779 |   top: 0.125rem;
2780 |   left: 0.125rem;
2781 |   height: 2rem;
2782 |   width: 2rem;
2783 | }
2784 | body#topic.density_super_compact .attach_block .icon:after {
2785 |   height: 2rem;
2786 |   width: 2rem;
2787 | }
2788 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button,
2789 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button,
2790 | body#topic.density_super_compact .poll > .title,
2791 | body#topic.density_super_compact .poll.poll_entry > .title {
2792 |   min-height: 1.875rem;
2793 |   padding-top: 0.25rem;
2794 |   padding-bottom: 0.25rem;
2795 | }
2796 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > span,
2797 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > span,
2798 | body#topic.density_super_compact .poll > .title > span,
2799 | body#topic.density_super_compact .poll.poll_entry > .title > span {
2800 |   font-size: 0.75rem;
2801 |   line-height: 1rem;
2802 | }
2803 | body#topic.density_super_compact .post_container.topic_hat_fixed > .hat_button > .icon,
2804 | body#topic.density_super_compact .post_container.topic_hat_entry > .hat_button > .icon,
2805 | body#topic.density_super_compact .poll > .title > .icon,
2806 | body#topic.density_super_compact .poll.poll_entry > .title > .icon {
2807 |   flex-basis: 1.375rem;
2808 |   width: 1.375rem;
2809 |   height: 1.375rem;
2810 | }
2811 | body#topic.density_super_compact .poll.poll_entry {
2812 |   margin: 0.25rem 0;
2813 | }
2814 | body#topic.density_super_compact .poll.poll_entry > .body .questions .question > .title {
2815 |   padding-top: 0.375rem;
2816 |   padding-bottom: 0.25rem;
2817 | }
2818 | body#topic.density_super_compact .poll.poll_entry > .body .questions .question > .items {
2819 |   padding-top: 0.125rem;
2820 |   padding-bottom: 0.375rem;
2821 | }
2822 | body#topic {
2823 |   --topic-paragraph-gap: 0.5rem;
2824 |   --topic-block-gap: 0.625rem;
2825 |   --topic-inline-block-gap: 0.5rem;
2826 |   --topic-quote-gap: 0.5rem;
2827 |   --topic-body-padding-x: 1rem;
2828 |   --topic-body-padding-y: 0.875rem;
2829 |   --topic-block-title-padding-x: 0.75rem;
2830 |   --topic-block-title-padding-y: 0.5rem;
2831 |   --topic-block-body-padding-x: 0.75rem;
2832 |   --topic-block-body-padding-top: 0.375rem;
2833 |   --topic-block-body-padding-bottom: 0.5rem;
2834 |   --topic-quote-title-padding-top: 0.5rem;
2835 |   --topic-quote-title-padding-bottom: 0.25rem;
2836 |   --topic-quote-body-padding-top: 0.25rem;
2837 |   --topic-quote-body-padding-bottom: 0.5rem;
2838 |   --topic-spoiler-body-open-padding-top: 0.375rem;
2839 |   --topic-actions-top-gap: 0.25rem;
2840 |   --topic-paragraph-spacing: var(--topic-paragraph-gap);
2841 |   --topic-inline-block-spacing: var(--topic-inline-block-gap);
2842 |   --topic-quote-spacing: var(--topic-quote-gap);
2843 | }
2844 | body#topic .post_body {
2845 |   padding: var(--topic-body-padding-y) var(--topic-body-padding-x);
2846 | }
2847 | body#topic .post_body p {
2848 |   margin-top: 0;
2849 |   margin-bottom: var(--topic-paragraph-gap);
2850 | }
2851 | body#topic .post_body ul,
2852 | body#topic .post_body ol {
2853 |   margin-top: var(--topic-paragraph-gap);
2854 |   margin-bottom: var(--topic-paragraph-gap);
2855 | }
2856 | body#topic .post_body > .post-block,
2857 | body#topic .post_body .attach_block,
2858 | body#topic .post_body img.linked-image,
2859 | body#topic .post_body img.attach {
2860 |   margin-top: var(--topic-inline-block-gap);
2861 |   margin-bottom: var(--topic-inline-block-gap);
2862 | }
2863 | body#topic .post_body > .post-block + br,
2864 | body#topic .post_body > .post-block + br + br,
2865 | body#topic .post_body .attach_block + br,
2866 | body#topic .post_body .attach_block + br + br,
2867 | body#topic .post_body img.linked-image + br,
2868 | body#topic .post_body img.linked-image + br + br,
2869 | body#topic .post_body img.attach + br,
2870 | body#topic .post_body img.attach + br + br {
2871 |   display: none;
2872 | }
2873 | body#topic .post_body > .post-block + p,
2874 | body#topic .post_body > .post-block + br + p,
2875 | body#topic .post_body > .post-block + br + br + p,
2876 | body#topic .post_body .attach_block + p,
2877 | body#topic .post_body .attach_block + br + p,
2878 | body#topic .post_body .attach_block + br + br + p,
2879 | body#topic .post_body img.linked-image + p,
2880 | body#topic .post_body img.linked-image + br + p,
2881 | body#topic .post_body img.linked-image + br + br + p,
2882 | body#topic .post_body img.attach + p,
2883 | body#topic .post_body img.attach + br + p,
2884 | body#topic .post_body img.attach + br + br + p {
2885 |   margin-top: 0;
2886 | }
2887 | body#topic .post_body > .post-block + .post-block,
2888 | body#topic .post_body > .post-block + .attach_block,
2889 | body#topic .post_body .attach_block + .post-block,
2890 | body#topic .post_body .attach_block + .attach_block {
2891 |   margin-top: var(--topic-block-gap);
2892 | }
2893 | body#topic .post_body p + .edit,
2894 | body#topic .post_body p + .post-edit-reason,
2895 | body#topic .post_body p + strong .edit,
2896 | body#topic .post_body > .post-block + .edit,
2897 | body#topic .post_body > .post-block + .post-edit-reason,
2898 | body#topic .post_body > .post-block + strong .edit,
2899 | body#topic .post_body .attach_block + .edit,
2900 | body#topic .post_body .attach_block + .post-edit-reason,
2901 | body#topic .post_body .attach_block + strong .edit {
2902 |   padding-top: var(--topic-edit-info-spacing);
2903 | }
2904 | body#topic .post_body .signature,
2905 | body#topic .post_body .post-signature,
2906 | body#topic .post_body .post_signature {
2907 |   margin-top: var(--topic-block-gap);
2908 |   padding-top: var(--topic-paragraph-gap);
2909 | }
2910 | body#topic .post-block {
2911 |   margin-top: var(--topic-block-gap);
2912 |   margin-bottom: var(--topic-block-gap);
2913 | }
2914 | body#topic .post-block > .block-title {
2915 |   padding: var(--topic-block-title-padding-y) var(--topic-block-title-padding-x);
2916 | }
2917 | body#topic .post-block > .block-body {
2918 |   padding: var(--topic-block-body-padding-top) var(--topic-block-body-padding-x) var(--topic-block-body-padding-bottom) var(--topic-block-body-padding-x);
2919 | }
2920 | body#topic .post-block.quote {
2921 |   margin-top: var(--topic-quote-gap);
2922 |   margin-bottom: var(--topic-quote-gap);
2923 |   border-left: 0;
2924 |   box-sizing: border-box;
2925 |   overflow: hidden;
2926 |   touch-action: pan-y;
2927 |   padding-left: 0.25rem;
2928 | }
2929 | body#topic .post-block.quote:before {
2930 |   content: "";
2931 |   position: absolute;
2932 |   top: 0;
2933 |   bottom: 0;
2934 |   left: 0;
2935 |   width: 0.25rem;
2936 |   background: #616161;
2937 |   pointer-events: none;
2938 | }
2939 | body#topic .post-block.quote > .block-title {
2940 |   padding-top: var(--topic-quote-title-padding-top);
2941 |   padding-bottom: var(--topic-quote-title-padding-bottom);
2942 | }
2943 | body#topic .post-block.quote > .block-body {
2944 |   padding-top: var(--topic-quote-body-padding-top);
2945 |   padding-bottom: var(--topic-quote-body-padding-bottom);
2946 | }
2947 | body#topic .post-block.quote > .block-body > .post-block.quote {
2948 |   margin-top: var(--topic-quote-gap);
2949 |   margin-bottom: var(--topic-inline-block-gap);
2950 | }
2951 | body#topic .post-block.code > .block-title {
2952 |   padding-top: var(--topic-block-title-padding-y);
2953 |   padding-bottom: var(--topic-block-title-padding-y);
2954 | }
2955 | body#topic .post-block.code > .block-body {
2956 |   padding-top: var(--topic-block-body-padding-top);
2957 |   padding-bottom: var(--topic-block-body-padding-bottom);
2958 | }
2959 | body#topic .post-block.spoil {
2960 |   margin-top: var(--topic-inline-block-gap);
2961 |   margin-bottom: var(--topic-inline-block-gap);
2962 | }
2963 | body#topic .post-block.spoil.open > .block-body {
2964 |   padding-top: var(--topic-spoiler-body-open-padding-top);
2965 | }
2966 | body#topic .post-block.spoil > .block-body > .post-block.spoil {
2967 |   margin-top: var(--topic-block-gap);
2968 |   margin-bottom: var(--topic-block-gap);
2969 | }
2970 | body#topic.density_compact {
2971 |   --topic-paragraph-gap: 0.3125rem;
2972 |   --topic-block-gap: 0.375rem;
2973 |   --topic-inline-block-gap: 0.3125rem;
2974 |   --topic-quote-gap: 0.3125rem;
2975 |   --topic-body-padding-x: 0.75rem;
2976 |   --topic-body-padding-y: 0.5rem;
2977 |   --topic-block-title-padding-y: 0.375rem;
2978 |   --topic-block-body-padding-top: 0.3125rem;
2979 |   --topic-block-body-padding-bottom: 0.4375rem;
2980 |   --topic-quote-title-padding-top: 0.3125rem;
2981 |   --topic-quote-title-padding-bottom: 0.3125rem;
2982 |   --topic-quote-body-padding-top: 0.1875rem;
2983 |   --topic-quote-body-padding-bottom: 0.375rem;
2984 |   --topic-spoiler-body-open-padding-top: 0.3125rem;
2985 |   --topic-actions-top-gap: 0.125rem;
2986 | }
2987 | body#topic.density_compact .post_container .post_body,
2988 | body#topic.density_super_compact .post_container .post_body {
2989 |   padding: var(--topic-body-padding-y) var(--topic-body-padding-x);
2990 | }
2991 | body#topic.density_compact .post_container .post_body .postcolor,
2992 | body#topic.density_super_compact .post_container .post_body .postcolor {
2993 |   margin: calc(0rem - var(--topic-body-padding-y)) calc(0rem - var(--topic-body-padding-x));
2994 |   padding: var(--topic-body-padding-y) var(--topic-body-padding-x);
2995 | }
2996 | body#topic.density_compact .post_container .post_footer .post_actions_row {
2997 |   margin-top: var(--topic-actions-top-gap);
2998 | }
2999 | body#topic.density_compact .post-block,
3000 | body#topic.density_super_compact .post-block {
3001 |   margin-top: var(--topic-block-gap);
3002 |   margin-bottom: var(--topic-block-gap);
3003 | }
3004 | body#topic.density_compact .post-block > .block-title,
3005 | body#topic.density_super_compact .post-block > .block-title {
3006 |   padding: var(--topic-block-title-padding-y) var(--topic-block-title-padding-x);
3007 | }
3008 | body#topic.density_compact .post-block > .block-body,
3009 | body#topic.density_super_compact .post-block > .block-body {
3010 |   padding: var(--topic-block-body-padding-top) var(--topic-block-body-padding-x) var(--topic-block-body-padding-bottom) var(--topic-block-body-padding-x);
3011 | }
3012 | body#topic.density_compact .post-block.spoil.close,
3013 | body#topic.density_super_compact .post-block.spoil.close {
3014 |   margin-bottom: var(--topic-inline-block-gap);
3015 | }
3016 | body#topic.density_compact .post-block.quote,
3017 | body#topic.density_super_compact .post-block.quote {
3018 |   margin-top: var(--topic-quote-gap);
3019 |   margin-bottom: var(--topic-quote-gap);
3020 | }
3021 | body#topic.density_compact .post-block.quote > .block-title,
3022 | body#topic.density_super_compact .post-block.quote > .block-title {
3023 |   padding-top: var(--topic-quote-title-padding-top);
3024 |   padding-bottom: var(--topic-quote-title-padding-bottom);
3025 | }
3026 | body#topic.density_compact .post-block.quote > .block-body,
3027 | body#topic.density_super_compact .post-block.quote > .block-body {
3028 |   padding-top: var(--topic-quote-body-padding-top);
3029 |   padding-bottom: var(--topic-quote-body-padding-bottom);
3030 | }
3031 | body#topic.density_compact .post-block.code > .block-title {
3032 |   padding-top: var(--topic-block-title-padding-y);
3033 |   padding-bottom: var(--topic-block-title-padding-y);
3034 | }
3035 | body#topic.density_compact .post-block.code > .block-body {
3036 |   padding-top: var(--topic-block-body-padding-top);
3037 |   padding-bottom: var(--topic-block-body-padding-bottom);
3038 | }
3039 | body#topic.density_super_compact {
3040 |   --topic-paragraph-gap: 0.1875rem;
3041 |   --topic-block-gap: 0.25rem;
3042 |   --topic-inline-block-gap: 0.1875rem;
3043 |   --topic-quote-gap: 0.1875rem;
3044 |   --topic-body-padding-x: 0.625rem;
3045 |   --topic-body-padding-y: 0.375rem;
3046 |   --topic-block-title-padding-x: 0.625rem;
3047 |   --topic-block-title-padding-y: 0.25rem;
3048 |   --topic-block-body-padding-x: 0.625rem;
3049 |   --topic-block-body-padding-top: 0.1875rem;
3050 |   --topic-block-body-padding-bottom: 0.3125rem;
3051 |   --topic-quote-title-padding-top: 0.1875rem;
3052 |   --topic-quote-title-padding-bottom: 0.1875rem;
3053 |   --topic-quote-body-padding-top: 0.125rem;
3054 |   --topic-quote-body-padding-bottom: 0.25rem;
3055 |   --topic-spoiler-body-open-padding-top: 0.1875rem;
3056 |   --topic-actions-top-gap: 0.0625rem;
3057 | }
3058 | body#topic.density_super_compact .post_container .post_footer .post_actions_row {
3059 |   margin-top: var(--topic-actions-top-gap);
3060 | }
3061 | body#topic.density_super_compact .post-block.code > .block-body {
3062 |   padding-top: var(--topic-block-body-padding-top);
3063 |   padding-bottom: var(--topic-block-body-padding-bottom);
3064 | }
3065 | .theme_bottom_pagination {
3066 |   -webkit-touch-callout: none;
3067 |   -webkit-user-select: none;
3068 |   -khtml-user-select: none;
3069 |   -moz-user-select: none;
3070 |   -ms-user-select: none;
3071 |   user-select: none;
3072 |   margin: 0;
3073 |   padding: 0;
3074 |   background: transparent;
3075 | }
3076 | .theme_bottom_pagination .theme_bottom_pagination_row {
3077 |   display: flex;
3078 |   align-items: center;
3079 |   justify-content: stretch;
3080 |   min-height: 2.25rem;
3081 | }
3082 | .theme_bottom_pagination button {
3083 |   -webkit-touch-callout: none;
3084 |   -webkit-user-select: none;
3085 |   -khtml-user-select: none;
3086 |   -moz-user-select: none;
3087 |   -ms-user-select: none;
3088 |   user-select: none;
3089 |   border: 0;
3090 |   border-radius: 0;
3091 |   flex: 1 1 0;
3092 |   min-width: 0;
3093 |   height: 2.25rem;
3094 |   padding: 0;
3095 |   color: #000000;
3096 |   background: transparent;
3097 |   font: inherit;
3098 |   font-weight: 700;
3099 |   line-height: 2.25rem;
3100 |   text-align: center;
3101 |   outline: none;
3102 |   -webkit-tap-highlight-color: transparent;
3103 | }
3104 | .theme_bottom_pagination button.theme_bottom_pagination_current {
3105 |   flex: 1.45 1 0;
3106 |   color: #000000;
3107 |   background: transparent;
3108 | }
3109 | .theme_bottom_pagination button.disabled {
3110 |   color: #000000;
3111 |   opacity: 0.39;
3112 | }
3113 | .theme_bottom_pagination button:not(.disabled):active {
3114 |   background: rgba(0, 0, 0, 0.05);
3115 | }
3116 | .theme_bottom_pagination button > span {
3117 |   font-size: 1.5rem;
3118 |   line-height: 2.25rem;
3119 | }
3120 | .theme_bottom_pagination button.theme_bottom_pagination_current > span {
3121 |   font-size: 0.875rem;
3122 |   font-weight: 700;
3123 |   line-height: 2.25rem;
3124 |   white-space: nowrap;
3125 |   overflow: visible;
3126 |   text-overflow: clip;
3127 | }
3128 | .posts_list .theme_page_container {
3129 |   margin: 0;
3130 |   padding: 0;
3131 | }
3132 | .posts_list .theme_page_separator {
3133 |   color: #757575;
3134 |   font-size: 0.875rem;
3135 |   font-weight: bold;
3136 |   line-height: 1.35;
3137 |   margin: 0.5rem 0.25rem;
3138 |   padding: 0.25rem 1rem;
3139 |   text-align: center;
3140 |   display: flex;
3141 |   align-items: center;
3142 |   justify-content: center;
3143 | }
3144 | .posts_list.search-results .topic_title_post {
3145 |   border-bottom: 0.0625rem solid rgba(0, 0, 0, 0.05);
3146 |   font-weight: bold;
3147 | }
3148 | .posts_list.search-results .post_container .post_header {
3149 |   padding: 0.5em 1em;
3150 | }
3151 | .posts_list.search-results .post_container .post_header .s_inf.nick {
3152 |   font-weight: bold;
3153 |   color: #212121;
3154 | }
3155 | .posts_list.search-results .post_container .post_header .s_inf.nick.online {
3156 |   color: #12b557;
3157 | }
3158 | .posts_list.search-results .post_container .post_header .s_inf.date {
3159 |   float: right;
3160 | }
3161 | .posts_list.search-results .post_container .post_header .s_inf.date > span {
3162 |   font-size: 0.875em;
3163 | }
3164 | .posts_list.search-results .post_container .s_post_footer {
3165 |   border-top: 0.0625rem solid rgba(0, 0, 0, 0.05);
3166 |   padding: 0.75em 1em;
3167 |   line-height: 1.5em;
3168 | }
3169 | .posts_list.search-results .bad-search-result {
3170 |   background: #ffffff;
3171 |   margin: 0.5em 0;
3172 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
3173 |   padding: 1em;
3174 |   margin: 0;
3175 | }
3176 | .posts_list.search-results .bad-search-result h3 {
3177 |   margin: 0;
3178 |   padding-bottom: 0.5em;
3179 |   color: #212121;
3180 | }
3181 | .posts_list.search-results .bad-search-result span {
3182 |   color: #757575;
3183 | }
3184 | .posts_list.search-results ~ #bottomMargin {
3185 |   height: 5.5em;
3186 | }
3187 | .posts_list.search-results .post_body {
3188 |   padding: 1em;
3189 | }
3190 | .navigation {
3191 |   background: #ffffff;
3192 |   margin: 0.5em 0.25rem 0 0.25rem;
3193 |   box-shadow: none;
3194 |   border: none;
3195 |   -webkit-border-radius: 0.875rem;
3196 |   border-radius: 0.875rem;
3197 |   display: flex;
3198 |   flex-flow: wrap;
3199 |   overflow: hidden;
3200 |   min-height: 3rem;
3201 | }
3202 | #padding_for_message_panel {
3203 |   display: none;
3204 |   height: 0;
3205 |   margin: 0;
3206 |   padding: 0;
3207 |   background: transparent;
3208 | }
3209 | #bottom_chrome_spacer {
3210 |   height: 0;
3211 |   margin: 0;
3212 |   padding: 0;
3213 |   background: transparent;
3214 |   pointer-events: none;
3215 | }
3216 | #theme_top_chrome_spacer {
3217 |   height: var(--theme-top-chrome-padding, 0px);
3218 |   margin: 0;
3219 |   padding: 0;
3220 |   background: transparent;
3221 |   pointer-events: none;
3222 | }
3223 | body#topic.topic_hat_overlay_open {
3224 |   overflow: hidden;
3225 | }
3226 | body.topic_hat_overlay_open #theme_top_chrome_spacer {
3227 |   display: none;
3228 | }
3229 | .navigation.disabled {
3230 |   display: none;
3231 | }
3232 | .navigation .button {
3233 |   -webkit-touch-callout: none;
3234 |   -webkit-user-select: none;
3235 |   -khtml-user-select: none;
3236 |   -moz-user-select: none;
3237 |   -ms-user-select: none;
3238 |   user-select: none;
3239 |   height: 3rem;
3240 |   line-height: 3rem;
3241 |   display: block;
3242 |   flex: 1 1 0px;
3243 |   box-sizing: border-box;
3244 |   -webkit-border-radius: 0.75rem;
3245 |   border-radius: 0.75rem;
3246 |   position: relative;
3247 |   vertical-align: middle;
3248 |   white-space: nowrap;
3249 |   text-align: center;
3250 |   text-transform: uppercase;
3251 |   color: #616161;
3252 |   font-size: 0.875rem;
3253 |   font-weight: 700;
3254 | }
3255 | .navigation .button > .icon {
3256 |   height: 100%;
3257 |   width: 100%;
3258 |   display: block;
3259 |   margin: 0 auto;
3260 |   background-position: center;
3261 |   background-size: 1.5rem;
3262 |   background-repeat: no-repeat;
3263 | }
3264 | .navigation .button > b {
3265 |   display: inline-block;
3266 | }
3267 | .navigation .button:not(.disabled):active {
3268 |   background: rgba(0, 0, 0, 0.1);
3269 | }
3270 | .navigation .button:before {
3271 |   content: "";
3272 |   position: absolute;
3273 |   left: 0;
3274 |   top: 0;
3275 |   right: 0;
3276 |   bottom: 0;
3277 |   margin: -0.375rem -0.5rem;
3278 |   /*background: red;
3279 |                 opacity: 0.05;*/
3280 | }
3281 | .navigation .button.disabled > .icon {
3282 |   opacity: 0.31;
3283 | }
3284 | .navigation .button.hidden {
3285 |   display: none;
3286 | }
3287 | .navigation .button.page > .icon {
3288 |   display: none;
3289 | }
3290 | .navigation .button.first > .icon {
3291 |   background-image: url("../../res/light/chevron-double-left.svg");
3292 | }
3293 | .navigation .button.prev > .icon {
3294 |   background-image: url("../../res/light/chevron-left.svg");
3295 | }
3296 | .navigation .button.next > .icon {
3297 |   background-image: url("../../res/light/chevron-right.svg");
3298 | }
3299 | .navigation .button.last > .icon {
3300 |   background-image: url("../../res/light/chevron-double-right.svg");
3301 | }
3302 | @media all and (max-width: 20rem) {
3303 |   .post_container .post_header .avatar .reputation:after {
3304 |     height: 1.125rem;
3305 |     min-width: 1.125rem;
3306 |   }
3307 |   .post_container .post_header .inf.nick .online_dot:after {
3308 |     height: 1em;
3309 |     width: 1em;
3310 |     left: 0;
3311 |     top: -0.25rem;
3312 |   }
3313 | }
3314 | @media all and (max-width: 14rem) {
3315 |   .poll > .title {
3316 |     padding-left: 0.5rem;
3317 |     /*>span{
3318 |             display: inline;
3319 |         }*/
3320 |   }
3321 |   .poll > .body .questions .question > .title {
3322 |     padding-left: 0.5rem;
3323 |     padding-right: 0.5rem;
3324 |   }
3325 |   .poll > .body .questions .question > .items {
3326 |     padding-left: 0.5rem;
3327 |     padding-right: 0.5rem;
3328 |   }
3329 |   .post_body {
3330 |     padding-left: 0.5rem;
3331 |     padding-right: 0.5rem;
3332 |   }
3333 |   .post_body .postcolor {
3334 |     margin: -0.5em;
3335 |     padding: 1em;
3336 |   }
3337 |   .post_container .hat_button {
3338 |     padding-left: 0.5rem;
3339 |   }
3340 |   .post_container .post_header {
3341 |     padding-left: 0.5rem;
3342 |     padding-right: 0.5rem;
3343 |   }
3344 |   .post_container .post_header .inf.nick {
3345 |     position: relative;
3346 |     top: auto;
3347 |     left: auto;
3348 |     display: block;
3349 |   }
3350 |   .post_container .post_header .inf.menu {
3351 |     right: -0.5rem;
3352 |   }
3353 |   /*.post_container .post_header .header_wrapper{
3354 |         border-bottom: @border1;
3355 |         padding-bottom: 8/16rem;
3356 |     }*/
3357 | }
3358 | .topic_page_counter {
3359 |   background: #ffffff;
3360 |   margin: 0.5em 0;
3361 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
3362 |   -webkit-border-radius: 0.875rem;
3363 |   border-radius: 0.875rem;
3364 |   color: #666666;
3365 |   font-weight: bold;
3366 |   padding: 0.75em 1em;
3367 | }
3368 | .topic_hat_fixed.top_hat_overlay_host {
3369 |   background: #ffffff;
3370 |   margin: 0;
3371 |   box-shadow: 0rem 0.0625rem 0.0625rem rgba(0, 0, 0, 0.12), 0rem 0rem 0.0625rem rgba(0, 0, 0, 0.12);
3372 |   overflow: hidden;
3373 |   position: fixed;
3374 |   top: 0;
3375 |   left: 0;
3376 |   right: 0;
3377 |   z-index: 21;
3378 |   box-sizing: border-box;
3379 |   -webkit-border-radius: 0 0 0.875rem 0.875rem;
3380 |   border-radius: 0 0 0.875rem 0.875rem;
3381 |   -webkit-transform: translate3d(0, 0, 0);
3382 |   transform: translate3d(0, 0, 0);
3383 |   -webkit-backface-visibility: hidden;
3384 |   backface-visibility: hidden;
3385 |   --theme-hat-max-height: calc(100vh - var(--theme-top-chrome-padding, 0px) - var(--theme-bottom-chrome-padding, 0px) - 1rem);
3386 | }
3387 | .topic_hat_fixed.top_hat_overlay_host.close {
3388 |   display: none;
3389 |   pointer-events: none;
3390 | }
3391 | .topic_hat_fixed.top_hat_overlay_host.open {
3392 |   display: -webkit-box;
3393 |   display: -webkit-flex;
3394 |   display: flex;
3395 |   -webkit-box-orient: vertical;
3396 |   -webkit-box-direction: normal;
3397 |   -webkit-flex-direction: column;
3398 |   flex-direction: column;
3399 |   max-height: var(--theme-hat-max-height);
3400 |   pointer-events: auto;
3401 |   opacity: 1;
3402 |   -webkit-transform: translate3d(0, 0, 0);
3403 |   transform: translate3d(0, 0, 0);
3404 | }
3405 | .topic_hat_fixed.top_hat_overlay_host.open.theme_hat_overlay_preparing {
3406 |   -webkit-animation: none;
3407 |   animation: none;
3408 |   -webkit-transition: none;
3409 |   transition: none;
3410 | }
3411 | .topic_hat_fixed.top_hat_overlay_host.open.theme_hat_overlay_enter {
3412 |   -webkit-animation: theme_hat_overlay_in 0.28s cubic-bezier(0.4, 0, 0.2, 1) forwards;
3413 |   animation: theme_hat_overlay_in 0.28s cubic-bezier(0.4, 0, 0.2, 1) forwards;
3414 | }
3415 | .topic_hat_fixed.top_hat_overlay_host.open {
3416 |   border-bottom: 0.0625rem solid rgba(0, 0, 0, 0.05);
3417 |   overscroll-behavior: contain;
3418 | }
3419 | .topic_hat_fixed.top_hat_overlay_host.open .hat_content {
3420 |   -webkit-box-flex: 1;
3421 |   -webkit-flex: 1 1 auto;
3422 |   flex: 1 1 auto;
3423 |   min-height: 0;
3424 |   max-height: var(--theme-hat-max-height);
3425 |   padding-top: var(--theme-top-chrome-padding, 0px);
3426 |   padding-bottom: 0.5rem;
3427 |   overflow-y: auto;
3428 |   overflow-x: hidden;
3429 |   -webkit-overflow-scrolling: touch;
3430 |   overscroll-behavior: contain;
3431 |   touch-action: pan-y;
3432 |   background: inherit;
3433 | }
3434 | .topic_hat_fixed.top_hat_overlay_host.initial_open {
3435 |   position: fixed;
3436 | }
3437 | .topic_hat_fixed.top_hat_overlay_host.initial_open .hat_content {
3438 |   overflow-y: auto;
3439 | }
3440 | @-webkit-keyframes theme_hat_overlay_in {
3441 |   from {
3442 |     opacity: 0;
3443 |     -webkit-transform: translate3d(0, -4px, 0);
3444 |     transform: translate3d(0, -4px, 0);
3445 |   }
3446 |   to {
3447 |     opacity: 1;
3448 |     -webkit-transform: translate3d(0, 0, 0);
3449 |     transform: translate3d(0, 0, 0);
3450 |   }
3451 | }
3452 | @keyframes theme_hat_overlay_in {
3453 |   from {
3454 |     opacity: 0;
3455 |     -webkit-transform: translate3d(0, -4px, 0);
3456 |     transform: translate3d(0, -4px, 0);
3457 |   }
3458 |   to {
3459 |     opacity: 1;
3460 |     -webkit-transform: translate3d(0, 0, 0);
3461 |     transform: translate3d(0, 0, 0);
3462 |   }
3463 | }
3464 | @media all and (min-width: 11.5rem) {
3465 |   .navigation .button:not(.hidden) + .button {
3466 |     margin-left: 0;
3467 |   }
3468 | }
3469 | @media all and (max-width: 11.5rem) {
3470 |   .navigation {
3471 |     display: block;
3472 |   }
3473 |   .navigation .button {
3474 |     float: left;
3475 |     width: 50%;
3476 |   }
3477 |   .navigation .button.page {
3478 |     width: 100%;
3479 |   }
3480 | }
3481 | /* -----------------------------------------------------------------------
3482 |  * Topic post highlight — TRANSIENT ~2-second frame on the whole post block.
3483 |  *
3484 |  * Uses an absolutely-positioned `::after` inset border so all four sides stay
3485 |  * visible. Outer box-shadow was clipped at the left/right viewport edges because
3486 |  * topic posts span full width (only vertical margin). `.post_container` is
3487 |  * `overflow: hidden`; the overlay is inset 1px so the 2px border sits inside
3488 |  * the rounded clip rect. Only overlay `opacity` fades (`post-highlight-fading`);
3489 |  * post content stays fully opaque. Replaces the clearfix `::after` while active.
3490 |  * -----------------------------------------------------------------------
3491 |  */
3492 | .post_container.post-highlight-first-unread,
3493 | .post_container.post-highlight-last-read,
3494 | .post_container.post-highlight-explicit {
3495 |   position: relative;
3496 |   box-sizing: border-box;
3497 | }
3498 | .post_container.post-highlight-first-unread::after,
3499 | .post_container.post-highlight-last-read::after,
3500 | .post_container.post-highlight-explicit::after {
3501 |   content: "";
3502 |   position: absolute;
3503 |   top: 1px;
3504 |   right: 1px;
3505 |   bottom: 1px;
3506 |   left: 1px;
3507 |   border-radius: 0.8125rem;
3508 |   z-index: 100;
3509 |   pointer-events: none;
3510 |   border: 2px solid transparent;
3511 |   opacity: 1;
3512 |   transition: opacity 250ms ease-out;
3513 |   display: block;
3514 | }
3515 | .post_container.post-highlight-first-unread::after {
3516 |   border-color: var(--fpda-highlight-first-unread-accent, var(--fpda-accent-primary, #e64a19));
3517 | }
3518 | .post_container.post-highlight-last-read::after {
3519 |   border-color: var(--fpda-highlight-last-read-accent, var(--fpda-accent-muted, #8a8f94));
3520 | }
3521 | .post_container.post-highlight-explicit::after {
3522 |   border-color: var(--fpda-highlight-explicit-accent, var(--fpda-accent-neutral, #0288d1));
3523 | }
3524 | .post_container.post-highlight-fading::after {
3525 |   opacity: 0;
3526 | }
3527 | @media (prefers-reduced-motion: reduce) {
3528 |   .post_container.post-highlight-first-unread::after,
3529 |   .post_container.post-highlight-last-read::after,
3530 |   .post_container.post-highlight-explicit::after {
3531 |     transition: none;
3532 |   }
3533 | }
3534 | 
```

### app/src/main/assets/template_theme.html

Bytes: 80269
SHA-256: 985122f3868f0d7ca8646bf6e8129faa1d3779d257eecd9c62dca09d83d6509e
Lines: 1-1123 of 1123

```html
   1 | <!doctype html>
   2 | <html>
   3 | 
   4 | <head>
   5 |     <title>${topic_title}</title>
   6 |     <script type="text/javascript">
   7 |         var PageInfo = {
   8 |             url: "${topic_url}",
   9 |             title: "${topic_title}",
  10 |             description: "${topic_description}",
  11 |             elemToScroll: "${elem_to_scroll}",
  12 |             bodyType: "${body_type}",
  13 | 
  14 |             enableAvatars: ${enable_avatars_bool},
  15 |             avatarType: "${avatar_type}",
  16 | 
  17 |             allPagesCount: ${all_pages_int},
  18 |             postsOnPageCount: ${posts_on_page_int},
  19 |             currentPage: ${current_page_int},
  20 |             scrollMode: "${topic_scroll_mode}",
  21 |             hybridScroll: ${topic_hybrid_scroll_bool},
  22 |             debug: ${debug_bool},
  23 | 
  24 |             // Topic post highlight. ppdaHighlight.postId === 0 means "no highlight".
  25 |             // ppdaHighlight.type is one of "first-unread" | "last-read" | "explicit" | "none".
  26 |             // ppdaHighlight.generationId guards against stale re-applies.
  27 |             ppdaHighlight: {
  28 |                 postId: ${ppda_highlight_post_id_int},
  29 |                 type: "${ppda_highlight_type}",
  30 |                 generationId: ${ppda_render_generation_id_int}
  31 |             },
  32 | 
  33 |             inFavorite: ${in_favorite_bool},
  34 |             authorized: ${authorized_bool},
  35 |             isCurator: ${is_curator_bool},
  36 |             memberId: ${member_id_int},
  37 |             topicId: ${topic_id_int},
  38 |             topicHatPostId: ${topic_hat_post_id_int},
  39 |             forumBlacklist: {
  40 |                 single: "${res_s_forum_blacklist_post_hidden}",
  41 |                 pluralTemplate: "${res_s_forum_blacklist_posts_hidden}"
  42 |             }
  43 |         }
  44 | 
  45 |         var bottomChromePadding = 0;
  46 |         var messagePanelPadding = 0;
  47 |         var topChromePadding = ${top_chrome_padding_css_px};
  48 | 
  49 |         function getExpectedBottomSpacerHeight() {
  50 |             return Math.max(0, bottomChromePadding + messagePanelPadding);
  51 |         }
  52 | 
  53 |         function applyBottomSpacer() {
  54 |             var element = document.getElementById("bottom_chrome_spacer");
  55 |             var value = getExpectedBottomSpacerHeight();
  56 |             document.documentElement.style.setProperty("--theme-bottom-chrome-padding", value + "px");
  57 |             if (typeof updateThemeHatOverlayLayout === "function") {
  58 |                 updateThemeHatOverlayLayout();
  59 |             }
  60 |             if (typeof updateThemePollOverlayLayout === "function") {
  61 |                 updateThemePollOverlayLayout();
  62 |             }
  63 |             if (element) {
  64 |                 element.style.height = value + "px";
  65 |             }
  66 |             console.log("[ThemeInsets] applyBottomSpacer bottomChrome=" + bottomChromePadding + " messagePanel=" + messagePanelPadding + " spacer=" + value + " viewportH=" + (window.innerHeight || 0) + " documentH=" + Math.max(document.documentElement.scrollHeight || 0, document.body ? document.body.scrollHeight || 0 : 0));
  67 |         }
  68 | 
  69 |         function setPaddingBottom(newPadding) {
  70 |             messagePanelPadding = Math.max(0, Number(newPadding) || 0);
  71 |             applyBottomSpacer();
  72 | 
  73 |             var legacy = document.getElementById("padding_for_message_panel");
  74 |             if (!legacy) return;
  75 |             legacy.style.paddingBottom = "0";
  76 |             legacy.style.height = "0";
  77 |             legacy.style.margin = "0";
  78 |             legacy.style.display = "none";
  79 |         }
  80 | 
  81 |         function setBottomChromePadding(newPadding) {
  82 |             bottomChromePadding = Math.max(0, Number(newPadding) || 0);
  83 |             applyBottomSpacer();
  84 |         }
  85 | 
  86 |         function setTopChromePadding(newPadding) {
  87 |             topChromePadding = Math.max(0, Number(newPadding) || 0);
  88 |             document.documentElement.style.setProperty("--theme-top-chrome-padding", topChromePadding + "px");
  89 |             if (typeof updateThemeHatOverlayLayout === "function") {
  90 |                 updateThemeHatOverlayLayout();
  91 |             }
  92 |             if (typeof updateThemePollOverlayLayout === "function") {
  93 |                 updateThemePollOverlayLayout();
  94 |             }
  95 |         }
  96 | 
  97 |         document.documentElement.style.setProperty("--theme-top-chrome-padding", topChromePadding + "px");
  98 | 
  99 |         // -----------------------------------------------------------------------
 100 |         // Topic post highlight.
 101 |         //
 102 |         // The Kotlin renderer computes which post should be highlighted
 103 |         // (last-read for an already-read topic, first-unread for an unread
 104 |         // topic, explicit for a deep link) and stamps the resolved post id +
 105 |         // type into the template. The highlighted post's <div> already carries
 106 |         // the `post-highlight-{type}` class in the static HTML.
 107 |         //
 108 |         // This JS is the *fallback* path. It is invoked from native once the
 109 |         // WebView is interactive, and it can also be re-invoked from native
 110 |         // when the highlight is re-resolved without a fresh template render
 111 |         // (e.g. unread target switched in from a list click). The function
 112 |         // accepts a generation id; older callbacks are ignored.
 113 |         //
 114 |         // Signature: window.PPDA_applyHighlight(postId, type, generationId)
 115 |         //   - postId: number (0 means "clear highlight")
 116 |         //   - type:   "first-unread" | "last-read" | "explicit" | "none"
 117 |         //   - generationId: monotonically increasing per logical open/refresh
 118 |         // -----------------------------------------------------------------------
 119 |         window.PPDA_applyHighlight = function (postId, type, generationId) {
 120 |             try {
 121 |                 var cur = (PageInfo && PageInfo.ppdaHighlight) || {};
 122 |                 if (typeof generationId === "number" && typeof cur.generationId === "number"
 123 |                         && generationId < cur.generationId) {
 124 |                     if (typeof IThemePresenter !== "undefined" && IThemePresenter.reportStaleHighlight) {
 125 |                         try { IThemePresenter.reportStaleHighlight(generationId, cur.generationId, postId); } catch (_) {}
 126 |                     }
 127 |                     return false;
 128 |                 }
 129 |                 if (cur && typeof cur.generationId === "number" && generationId > cur.generationId) {
 130 |                     cur.generationId = generationId;
 131 |                 }
 132 |                 // A new (greater) generation restarts the visible window: any
 133 |                 // pending fadeout from the previous render is cleared and a
 134 |                 // fresh 2-second deadline is armed via the native side
 135 |                 // (PPDA_scheduleHighlightFadeout) — but if the caller is asking
 136 |                 // us to re-apply within the same render (equal generation), the
 137 |                 // existing deadline MUST still win: it must NOT be extended.
 138 |                 if (cur && typeof cur.generationId === "number" && generationId === cur.generationId
 139 |                         && window.__ppdaHighlightFadedGeneration === cur.generationId) {
 140 |                     // This render has already been faded out (or is mid-fade).
 141 |                     // Re-asserting the class would race the CSS transition.
 142 |                     return false;
 143 |                 }
 144 |                 var HIGHLIGHT_CLASSES = ["post-highlight-first-unread", "post-highlight-last-read", "post-highlight-explicit"];
 145 |                 var nodes = document.querySelectorAll(".post_container.post-highlight-first-unread, .post_container.post-highlight-last-read, .post_container.post-highlight-explicit");
 146 |                 for (var i = 0; i < nodes.length; i++) {
 147 |                     var n = nodes[i];
 148 |                     n.classList.remove("post-highlight-first-unread");
 149 |                     n.classList.remove("post-highlight-last-read");
 150 |                     n.classList.remove("post-highlight-explicit");
 151 |                     n.classList.remove("post-highlight-fading");
 152 |                 }
 153 |                 if (!postId || postId <= 0 || !type || type === "none") {
 154 |                     return true;
 155 |                 }
 156 |                 var klass = type === "first-unread" ? "post-highlight-first-unread"
 157 |                         : type === "last-read" ? "post-highlight-last-read"
 158 |                         : type === "explicit" ? "post-highlight-explicit"
 159 |                         : null;
 160 |                 if (!klass) return true;
 161 |                 var el = document.getElementById("post-" + postId) ||
 162 |                         document.querySelector('[data-post-id="' + postId + '"]');
 163 |                 if (!el) return false;
 164 |                 el.classList.add(klass);
 165 |                 // If open-scroll landed on a different post (getnewpost bottom vs
 166 |                 // last-read highlight), bring the highlighted block into view so the
 167 |                 // ~2s outline is actually visible before fade-out arms.
 168 |                 try {
 169 |                     var rect = el.getBoundingClientRect();
 170 |                     var vh = window.innerHeight || document.documentElement.clientHeight || 0;
 171 |                     var chromeTop = 0;
 172 |                     var chromeBottom = 0;
 173 |                     if (typeof bottomChromePadding === "number") chromeBottom += bottomChromePadding;
 174 |                     if (typeof messagePanelPadding === "number") chromeBottom += messagePanelPadding;
 175 |                     if (typeof topChromePadding === "number") chromeTop += topChromePadding;
 176 |                     var visibleTop = chromeTop;
 177 |                     var visibleBottom = Math.max(visibleTop + 1, vh - chromeBottom);
 178 |                     var margin = 8;
 179 |                     var fullyVisible = rect.top >= visibleTop + margin &&
 180 |                             rect.bottom <= visibleBottom - margin;
 181 |                     if (!fullyVisible && typeof el.scrollIntoView === "function") {
 182 |                         el.scrollIntoView({ block: "center", inline: "nearest", behavior: "auto" });
 183 |                     }
 184 |                 } catch (_) {}
 185 |                 return true;
 186 |             } catch (e) {
 187 |                 return false;
 188 |             }
 189 |         };
 190 | 
 191 |         // -----------------------------------------------------------------------
 192 |         // Transient highlight fade-out.
 193 |         //
 194 |         // The static `post-highlight-*` class remains in the DOM (it's the
 195 |         // source of truth for theme/layout), but the *visible* state must
 196 |         // transition to off after a short window. Native calls
 197 |         // PPDA_scheduleHighlightFadeout(generationId, delayMs) once per
 198 |         // render: this arms a JS-side setTimeout that adds the
 199 |         // `post-highlight-fading` class after `delayMs` (the CSS animates
 200 |         // opacity to 0 over ~300ms and clears the background/accent) and
 201 |         // then strips both the fading class and the highlight class. When
 202 |         // the highlight class is finally removed we report back to native
 203 |         // via IThemePresenter.highlightFadeoutCompleted(generationId) so the
 204 |         // `highlight_fadeout_completed` diagnostic can be emitted.
 205 |         //
 206 |         // Scrolling within the same page does NOT restart this timer (the
 207 |         // native bridge only calls us on a fresh render event). A page
 208 |         // navigation does — native bumps `PageInfo.ppdaHighlight.generationId`
 209 |         // and re-arms a fresh timer.
 210 |         //
 211 |         // Stale re-applies (PPDA_applyHighlight with an equal generation
 212 |         // after fade has begun) are rejected by the guard inside
 213 |         // PPDA_applyHighlight so the CSS transition is not clobbered.
 214 |         // -----------------------------------------------------------------------
 215 |         window.PPDA_scheduleHighlightFadeout = function (generationId, delayMs) {
 216 |             try {
 217 |                 if (typeof generationId !== "number" || generationId <= 0) return;
 218 |                 if (typeof delayMs !== "number" || delayMs < 0) delayMs = 0;
 219 |                 if (window.__ppdaHighlightFadeoutTimer) {
 220 |                     clearTimeout(window.__ppdaHighlightFadeoutTimer);
 221 |                     window.__ppdaHighlightFadeoutTimer = null;
 222 |                 }
 223 |                 window.__ppdaHighlightFadeoutTargetGen = generationId;
 224 |                 window.__ppdaHighlightFadeoutDeadlineAt = Date.now() + delayMs;
 225 |                 window.__ppdaHighlightFadeoutTimer = setTimeout(function () {
 226 |                     window.__ppdaHighlightFadeoutTimer = null;
 227 |                     try {
 228 |                         var nodes = document.querySelectorAll(".post_container.post-highlight-first-unread, .post_container.post-highlight-last-read, .post_container.post-highlight-explicit");
 229 |                         // We fade an `::after` overlay FRAME (its `opacity`), not
 230 |                         // full-post opacity. The pseudo-element's `transitionend`
 231 |                         // fires on its host node (`ev.target === n`) with
 232 |                         // `propertyName === "opacity"`. `transitionend` is not
 233 |                         // 100% reliable across WebView versions, so each node uses
 234 |                         // a race: whichever of `transitionend` or a setTimeout
 235 |                         // fallback fires first removes the classes, guarded by a
 236 |                         // per-node "removed once" flag so cleanup runs exactly
 237 |                         // once. A per-node closure (forEach) avoids the classic
 238 |                         // shared-`var` loop-capture bug.
 239 |                         Array.prototype.forEach.call(nodes, function (n) {
 240 |                             // Trigger the CSS overlay fade.
 241 |                             n.classList.add("post-highlight-fading");
 242 |                             var done = false;
 243 |                             var removeClasses = function () {
 244 |                                 if (done) return;
 245 |                                 done = true;
 246 |                                 try { n.removeEventListener("transitionend", onEnd); } catch (_) {}
 247 |                                 if (fallback) { try { clearTimeout(fallback); } catch (_) {} }
 248 |                                 n.classList.remove("post-highlight-fading");
 249 |                                 n.classList.remove("post-highlight-first-unread");
 250 |                                 n.classList.remove("post-highlight-last-read");
 251 |                                 n.classList.remove("post-highlight-explicit");
 252 |                                 if (typeof window.PPDA_enableReadPosObserver === "function") {
 253 |                                     try { window.PPDA_enableReadPosObserver(true); } catch (_) {}
 254 |                                 }
 255 |                             };
 256 |                             var onEnd = function (ev) {
 257 |                                 // React only to the overlay's own opacity
 258 |                                 // transition on this very node (ignore bubbled
 259 |                                 // child transitions). `box-shadow` is tolerated
 260 |                                 // for backward-compat with older renders.
 261 |                                 if (ev && ev.target !== n) return;
 262 |                                 if (ev && ev.propertyName
 263 |                                         && ev.propertyName !== "opacity"
 264 |                                         && ev.propertyName !== "box-shadow") return;
 265 |                                 try { removeClasses(); } catch (_) {}
 266 |                             };
 267 |                             n.addEventListener("transitionend", onEnd);
 268 |                             // Defensive fallback: the CSS overlay transition is
 269 |                             // 250ms; 350ms gives it a small margin. If
 270 |                             // `transitionend` never fires (reduced-motion, some
 271 |                             // WebView builds that skip pseudo-element
 272 |                             // transitionend), this strips the classes so the
 273 |                             // highlight never sticks.
 274 |                             var fallback = setTimeout(function () {
 275 |                                 try { removeClasses(); } catch (_) {}
 276 |                             }, 350);
 277 |                         });
 278 |                         window.__ppdaHighlightFadedGeneration = generationId;
 279 |                         if (typeof IThemePresenter !== "undefined" && IThemePresenter.highlightFadeoutCompleted) {
 280 |                             try { IThemePresenter.highlightFadeoutCompleted(generationId); } catch (_) {}
 281 |                         }
 282 |                     } catch (_) {}
 283 |                 }, delayMs);
 284 |             } catch (_) {}
 285 |         };
 286 | 
 287 |         // -----------------------------------------------------------------------
 288 |         // Read-position observer.
 289 |         //
 290 |         // Tracks the bottommost post intersecting the viewport (the deepest
 291 |         // post the user has scrolled into view) and reports its id to native
 292 |         // via `IThemePresenter.postVisible` so the read-position repository
 293 |         // can persist `lastViewedPostId` / `lastViewedPage`. When the viewport
 294 |         // is at the page bottom, the chronologically last post is reported
 295 |         // instead — the previous topmost-only observer saved the penultimate
 296 |         // post when the user had read to the end.
 297 |         //
 298 |         // The observer is gated on a flag (`ppdaReadPosObserverEnabled`) which
 299 |         // native can flip to false on refresh / before unload.
 300 |         // -----------------------------------------------------------------------
 301 |         (function () {
 302 |             try {
 303 |                 if (typeof window === "undefined" || typeof document === "undefined") return;
 304 |                 if (typeof window.IntersectionObserver === "undefined") return;
 305 |                 var lastReported = 0;
 306 |                 var scrollSettleTimer = null;
 307 |                 var BOTTOM_TOLERANCE_PX = 64;
 308 | 
 309 |                 function lastPostNode() {
 310 |                     var nodes = document.querySelectorAll("[data-post-id]");
 311 |                     return nodes.length ? nodes[nodes.length - 1] : null;
 312 |                 }
 313 | 
 314 |                 function isAtScrollBottom() {
 315 |                     var doc = document.documentElement;
 316 |                     var scrollY = window.scrollY || window.pageYOffset || 0;
 317 |                     var viewport = window.innerHeight || doc.clientHeight || 0;
 318 |                     var scrollHeight = Math.max(doc.scrollHeight, doc.offsetHeight, doc.clientHeight);
 319 |                     return scrollY + viewport >= scrollHeight - BOTTOM_TOLERANCE_PX;
 320 |                 }
 321 | 
 322 |                 function reportPostId(postId) {
 323 |                     if (!postId || postId === lastReported) return;
 324 |                     lastReported = postId;
 325 |                     if (typeof IThemePresenter !== "undefined" && IThemePresenter.postVisible) {
 326 |                         try { IThemePresenter.postVisible(String(postId)); } catch (_) {}
 327 |                     }
 328 |                 }
 329 | 
 330 |                 function reportReadPositionFromViewport() {
 331 |                     if (isAtScrollBottom()) {
 332 |                         var last = lastPostNode();
 333 |                         if (last) {
 334 |                             var lastId = parseInt(last.getAttribute("data-post-id") || "0", 10);
 335 |                             if (lastId) {
 336 |                                 reportPostId(lastId);
 337 |                                 return;
 338 |                             }
 339 |                         }
 340 |                     }
 341 |                     var nodes = document.querySelectorAll("[data-post-id]");
 342 |                     var best = null;
 343 |                     var bestBottom = -Infinity;
 344 |                     for (var i = 0; i < nodes.length; i++) {
 345 |                         var node = nodes[i];
 346 |                         var rect = node.getBoundingClientRect();
 347 |                         var vh = window.innerHeight || document.documentElement.clientHeight || 0;
 348 |                         if (rect.bottom <= 0 || rect.top >= vh) continue;
 349 |                         if (rect.bottom > bestBottom) {
 350 |                             bestBottom = rect.bottom;
 351 |                             best = node;
 352 |                         }
 353 |                     }
 354 |                     if (!best) return;
 355 |                     var bestId = parseInt(best.getAttribute("data-post-id") || "0", 10);
 356 |                     if (bestId) reportPostId(bestId);
 357 |                 }
 358 | 
 359 |                 window.PPDA_enableReadPosObserver = function (enabled) {
 360 |                     if (typeof enabled !== "boolean") enabled = true;
 361 |                     var obs = window.__ppdaReadPosObs;
 362 |                     if (enabled && !obs) {
 363 |                         obs = new IntersectionObserver(function () {
 364 |                             try { reportReadPositionFromViewport(); } catch (_) {}
 365 |                         }, { rootMargin: "0px", threshold: 0 });
 366 |                         window.__ppdaReadPosObs = obs;
 367 |                         var nodes = document.querySelectorAll("[data-post-id]");
 368 |                         for (var i = 0; i < nodes.length; i++) obs.observe(nodes[i]);
 369 |                         if (!window.__ppdaReadPosScrollBound) {
 370 |                             window.__ppdaReadPosScrollBound = true;
 371 |                             window.addEventListener("scroll", function () {
 372 |                                 if (!window.__ppdaReadPosObs) return;
 373 |                                 if (scrollSettleTimer) clearTimeout(scrollSettleTimer);
 374 |                                 scrollSettleTimer = setTimeout(function () {
 375 |                                     scrollSettleTimer = null;
 376 |                                     try { reportReadPositionFromViewport(); } catch (_) {}
 377 |                                 }, 150);
 378 |                             }, { passive: true });
 379 |                         }
 380 |                     } else if (!enabled && obs) {
 381 |                         try { obs.disconnect(); } catch (_) {}
 382 |                         window.__ppdaReadPosObs = null;
 383 |                         if (scrollSettleTimer) {
 384 |                             clearTimeout(scrollSettleTimer);
 385 |                             scrollSettleTimer = null;
 386 |                         }
 387 |                     }
 388 |                 };
 389 |                 if (document.readyState === "complete" || document.readyState === "interactive") {
 390 |                     window.PPDA_enableReadPosObserver(true);
 391 |                 } else {
 392 |                     document.addEventListener("DOMContentLoaded", function () {
 393 |                         window.PPDA_enableReadPosObserver(true);
 394 |                     }, { once: true });
 395 |                 }
 396 |             } catch (e) {}
 397 |         })();
 398 |     </script>
 399 |     <script type="text/javascript" src="file:///android_asset/forpda/scripts/main.js"></script>
 400 |     <script type="text/javascript" src="file:///android_asset/forpda/scripts/modules/posts_functions.js"></script>
 401 |     <script type="text/javascript" src="file:///android_asset/forpda/scripts/modules/theme.js"></script>
 402 |     <script type="text/javascript" src="file:///android_asset/forpda/scripts/attach_transformer.js"></script>
 403 |     <script type="text/javascript" src="file:///android_asset/forpda/scripts/blocks.js"></script>
 404 |     <link rel="stylesheet" type="text/css" href="file:///android_asset/forpda/styles/md_colors.css">
 405 |     <link rel="stylesheet" type="text/css" href="file:///android_asset/fonts/roboto/import_mono.css">
 406 |     <link rel="stylesheet" type="text/css" href="file:///android_asset/fonts/fontello/import.css">
 407 |     <link rel="stylesheet" type="text/css" href="file:///android_asset/forpda/styles/${style_type}/${style_type}_main.css">
 408 |     <link rel="stylesheet" type="text/css" href="file:///android_asset/forpda/styles/${style_type}/${style_type}_themes.css">
 409 |     ${theme_overrides_css}
 410 |     <script type="text/javascript" src="file:///android_asset/forpda/scripts/z_emoticons.js"></script>
 411 | </head>
 412 | 
 413 | <body id="${body_type}" class="${enable_avatars} ${avatar_type} ${post_density_class}">
 414 |     <svg class="rep-action-symbols" aria-hidden="true" focusable="false">
 415 |         <symbol id="rep-thumb-up-final" viewBox="0 0 128 128"><path d="M76 3h9v1H76zM74 4h12v1H74zM73 5h15v1H73zM71 6h18v1H71zM71 7h19v1H71zM70 8h21v1H70zM70 9h8v1H70zM82 9h10v1H82zM69 10h8v1H69zM84 10h8v1H84zM69 11h7v1H69zM85 11h8v1H85zM68 12h7v1H68zM85 12h8v1H85zM68 13h7v1H68zM86 13h7v1H86zM68 14h7v1H68zM87 14h7v1H87zM67 15h8v1H67zM87 15h7v1H87zM67 16h7v1H67zM88 16h7v1H88zM67 17h7v1H67zM88 17h7v1H88zM67 18h7v1H67zM88 18h7v1H88zM67 19h6v1H67zM88 19h7v1H88zM66 20h7v1H66zM88 20h7v1H88zM66 21h7v1H66zM88 21h7v1H88zM65 22h8v1H65zM88 22h7v1H88zM65 23h7v1H65zM88 23h7v1H88zM64 24h8v1H64zM88 24h7v1H88zM64 25h7v1H64zM88 25h7v1H88zM63 26h8v1H63zM88 26h7v1H88zM62 27h8v1H62zM88 27h6v1H88zM62 28h8v1H62zM87 28h7v1H87zM61 29h8v1H61zM87 29h7v1H87zM60 30h8v1H60zM87 30h7v1H87zM59 31h9v1H59zM87 31h6v1H87zM58 32h9v1H58zM87 32h6v1H87zM57 33h9v1H57zM86 33h7v1H86zM56 34h9v1H56zM86 34h7v1H86zM55 35h9v1H55zM85 35h8v1H85zM53 36h10v1H53zM85 36h7v1H85zM53 37h9v1H53zM85 37h7v1H85zM52 38h9v1H52zM84 38h7v1H84zM50 39h10v1H50zM84 39h7v1H84zM50 40h9v1H50zM84 40h7v1H84zM48 41h10v1H48zM83 41h7v1H83zM47 42h10v1H47zM83 42h7v1H83zM46 43h10v1H46zM82 43h8v1H82zM45 44h10v1H45zM82 44h7v1H82zM44 45h10v1H44zM81 45h8v1H81zM43 46h10v1H43zM81 46h7v1H81zM42 47h10v1H42zM81 47h6v1H81zM14 48h5v1H14zM41 48h10v1H41zM80 48h7v1H80zM8 49h23v1H8zM40 49h9v1H40zM79 49h8v1H79zM7 50h25v1H7zM39 50h10v1H39zM79 50h9v1H79zM6 51h27v1H6zM38 51h10v1H38zM78 51h39v1H78zM5 52h29v1H5zM38 52h9v1H38zM78 52h42v1H78zM4 53h42v1H4zM78 53h44v1H78zM3 54h42v1H3zM78 54h45v1H78zM3 55h9v1H3zM28 55h16v1H28zM78 55h46v1H78zM3 56h7v1H3zM29 56h14v1H29zM79 56h46v1H79zM3 57h6v1H3zM29 57h13v1H29zM115 57h10v1H115zM2 58h7v1H2zM30 58h11v1H30zM117 58h9v1H117zM2 59h7v1H2zM30 59h11v1H30zM118 59h8v1H118zM2 60h7v1H2zM30 60h10v1H30zM119 60h8v1H119zM2 61h7v1H2zM30 61h9v1H30zM120 61h8v1H120zM2 62h7v1H2zM30 62h8v1H30zM120 62h8v1H120zM2 63h7v1H2zM30 63h8v1H30zM120 63h8v1H120zM2 64h7v1H2zM30 64h8v1H30zM120 64h8v1H120zM2 65h7v1H2zM30 65h7v1H30zM120 65h8v1H120zM2 66h7v1H2zM30 66h7v1H30zM120 66h8v1H120zM2 67h7v1H2zM30 67h7v1H30zM119 67h8v1H119zM2 68h7v1H2zM30 68h7v1H30zM119 68h7v1H119zM2 69h7v1H2zM30 69h7v1H30zM118 69h8v1H118zM2 70h7v1H2zM30 70h7v1H30zM117 70h8v1H117zM2 71h7v1H2zM30 71h7v1H30zM101 71h24v1H101zM2 72h7v1H2zM30 72h7v1H30zM100 72h24v1H100zM2 73h7v1H2zM30 73h7v1H30zM99 73h25v1H99zM2 74h7v1H2zM30 74h7v1H30zM99 74h25v1H99zM2 75h7v1H2zM30 75h7v1H30zM99 75h25v1H99zM2 76h7v1H2zM30 76h7v1H30zM100 76h25v1H100zM2 77h7v1H2zM30 77h7v1H30zM102 77h23v1H102zM2 78h7v1H2zM30 78h7v1H30zM118 78h8v1H118zM2 79h7v1H2zM30 79h7v1H30zM119 79h7v1H119zM2 80h7v1H2zM30 80h7v1H30zM119 80h7v1H119zM2 81h7v1H2zM30 81h7v1H30zM120 81h7v1H120zM2 82h7v1H2zM30 82h7v1H30zM120 82h7v1H120zM2 83h7v1H2zM30 83h7v1H30zM120 83h7v1H120zM2 84h7v1H2zM30 84h7v1H30zM120 84h7v1H120zM2 85h7v1H2zM30 85h7v1H30zM120 85h6v1H120zM2 86h7v1H2zM30 86h7v1H30zM119 86h7v1H119zM2 87h7v1H2zM30 87h7v1H30zM119 87h7v1H119zM2 88h7v1H2zM30 88h7v1H30zM118 88h7v1H118zM2 89h7v1H2zM30 89h7v1H30zM116 89h9v1H116zM2 90h7v1H2zM30 90h7v1H30zM102 90h23v1H102zM2 91h7v1H2zM30 91h7v1H30zM100 91h24v1H100zM2 92h7v1H2zM30 92h7v1H30zM99 92h24v1H99zM2 93h7v1H2zM30 93h7v1H30zM99 93h23v1H99zM2 94h7v1H2zM30 94h7v1H30zM99 94h23v1H99zM2 95h7v1H2zM30 95h7v1H30zM100 95h22v1H100zM2 96h7v1H2zM30 96h7v1H30zM102 96h20v1H102zM2 97h7v1H2zM30 97h7v1H30zM115 97h8v1H115zM2 98h7v1H2zM30 98h7v1H30zM116 98h7v1H116zM2 99h7v1H2zM30 99h7v1H30zM116 99h7v1H116zM2 100h7v1H2zM30 100h7v1H30zM117 100h6v1H117zM2 101h7v1H2zM30 101h7v1H30zM116 101h7v1H116zM2 102h7v1H2zM30 102h7v1H30zM116 102h7v1H116zM2 103h7v1H2zM30 103h7v1H30zM115 103h8v1H115zM2 104h7v1H2zM30 104h7v1H30zM114 104h8v1H114zM2 105h7v1H2zM30 105h7v1H30zM113 105h9v1H113zM2 106h7v1H2zM30 106h7v1H30zM102 106h20v1H102zM2 107h7v1H2zM30 107h7v1H30zM100 107h21v1H100zM2 108h7v1H2zM30 108h7v1H30zM99 108h21v1H99zM2 109h7v1H2zM30 109h7v1H30zM99 109h20v1H99zM2 110h7v1H2zM30 110h7v1H30zM99 110h19v1H99zM2 111h7v1H2zM30 111h7v1H30zM99 111h18v1H99zM2 112h7v1H2zM30 112h7v1H30zM102 112h14v1H102zM2 113h7v1H2zM30 113h8v1H30zM109 113h7v1H109zM3 114h7v1H3zM29 114h10v1H29zM109 114h7v1H109zM3 115h8v1H3zM28 115h12v1H28zM109 115h7v1H109zM3 116h9v1H3zM27 116h14v1H27zM109 116h7v1H109zM4 117h39v1H4zM108 117h8v1H108zM4 118h41v1H4zM107 118h8v1H107zM5 119h43v1H5zM105 119h10v1H105zM6 120h26v1H6zM36 120h78v1H36zM7 121h24v1H7zM37 121h76v1H37zM10 122h19v1H10zM38 122h75v1H38zM41 123h70v1H41zM43 124h67v1H43zM48 125h59v1H48zM55 126h43v1H55zM101 126h1v1H101z"></path></symbol>
 416 |         <symbol id="rep-thumb-down-final" viewBox="0 0 128 128"><path d="M22 3h63v1H22zM19 4h69v1H19zM101 4h18v1H101zM18 5h73v1H18zM99 5h22v1H99zM16 6h76v1H16zM97 6h26v1H97zM15 7h79v1H15zM96 7h28v1H96zM14 8h110v1H14zM13 9h11v1H13zM83 9h42v1H83zM12 10h10v1H12zM85 10h18v1H85zM116 10h9v1H116zM12 11h9v1H12zM88 11h14v1H88zM118 11h7v1H118zM12 12h7v1H12zM90 12h11v1H90zM119 12h7v1H119zM11 13h7v1H11zM91 13h10v1H91zM119 13h7v1H119zM11 14h7v1H11zM92 14h8v1H92zM119 14h7v1H119zM10 15h7v1H10zM93 15h7v1H93zM120 15h6v1H120zM10 16h7v1H10zM93 16h7v1H93zM120 16h6v1H120zM10 17h7v1H10zM94 17h6v1H94zM120 17h6v1H120zM10 18h7v1H10zM94 18h6v1H94zM119 18h7v1H119zM10 19h7v1H10zM94 19h6v1H94zM119 19h7v1H119zM10 20h7v1H10zM94 20h6v1H94zM119 20h7v1H119zM10 21h7v1H10zM94 21h6v1H94zM119 21h7v1H119zM11 22h7v1H11zM94 22h6v1H94zM119 22h7v1H119zM11 23h9v1H11zM94 23h6v1H94zM119 23h7v1H119zM11 24h18v1H11zM94 24h6v1H94zM119 24h7v1H119zM9 25h22v1H9zM94 25h6v1H94zM119 25h7v1H119zM8 26h23v1H8zM94 26h6v1H94zM119 26h7v1H119zM7 27h24v1H7zM94 27h6v1H94zM119 27h7v1H119zM6 28h25v1H6zM94 28h6v1H94zM119 28h7v1H119zM5 29h25v1H5zM94 29h6v1H94zM119 29h7v1H119zM5 30h11v1H5zM94 30h6v1H94zM119 30h7v1H119zM4 31h8v1H4zM94 31h6v1H94zM119 31h7v1H119zM4 32h7v1H4zM94 32h6v1H94zM119 32h7v1H119zM3 33h8v1H3zM94 33h6v1H94zM119 33h7v1H119zM3 34h7v1H3zM94 34h6v1H94zM119 34h7v1H119zM3 35h6v1H3zM94 35h6v1H94zM119 35h7v1H119zM3 36h6v1H3zM94 36h6v1H94zM119 36h7v1H119zM3 37h6v1H3zM94 37h6v1H94zM119 37h7v1H119zM3 38h6v1H3zM94 38h6v1H94zM119 38h7v1H119zM3 39h7v1H3zM94 39h6v1H94zM119 39h7v1H119zM3 40h7v1H3zM94 40h6v1H94zM119 40h7v1H119zM3 41h8v1H3zM94 41h6v1H94zM119 41h7v1H119zM4 42h8v1H4zM94 42h6v1H94zM119 42h7v1H119zM4 43h22v1H4zM94 43h6v1H94zM119 43h7v1H119zM5 44h22v1H5zM94 44h6v1H94zM119 44h7v1H119zM5 45h23v1H5zM94 45h6v1H94zM119 45h7v1H119zM5 46h24v1H5zM94 46h6v1H94zM119 46h7v1H119zM4 47h24v1H4zM94 47h6v1H94zM119 47h7v1H119zM3 48h24v1H3zM94 48h6v1H94zM119 48h7v1H119zM3 49h11v1H3zM15 49h10v1H15zM94 49h6v1H94zM119 49h7v1H119zM3 50h8v1H3zM94 50h6v1H94zM119 50h7v1H119zM2 51h7v1H2zM94 51h6v1H94zM119 51h7v1H119zM2 52h7v1H2zM94 52h6v1H94zM119 52h7v1H119zM1 53h7v1H1zM94 53h6v1H94zM119 53h7v1H119zM0 54h8v1H0zM94 54h6v1H94zM119 54h7v1H119zM0 55h8v1H0zM94 55h6v1H94zM119 55h7v1H119zM0 56h8v1H0zM94 56h6v1H94zM119 56h7v1H119zM1 57h7v1H1zM94 57h6v1H94zM119 57h7v1H119zM2 58h7v1H2zM94 58h6v1H94zM119 58h7v1H119zM2 59h8v1H2zM94 59h6v1H94zM119 59h7v1H119zM3 60h10v1H3zM16 60h2v1H16zM19 60h3v1H19zM94 60h6v1H94zM119 60h7v1H119zM3 61h21v1H3zM94 61h6v1H94zM119 61h7v1H119zM4 62h21v1H4zM94 62h6v1H94zM119 62h7v1H119zM4 63h21v1H4zM94 63h6v1H94zM119 63h7v1H119zM4 64h21v1H4zM94 64h6v1H94zM119 64h7v1H119zM4 65h20v1H4zM93 65h7v1H93zM119 65h7v1H119zM4 66h19v1H4zM93 66h7v1H93zM119 66h7v1H119zM4 67h9v1H4zM93 67h7v1H93zM120 67h6v1H120zM3 68h8v1H3zM92 68h8v1H92zM120 68h6v1H120zM3 69h7v1H3zM92 69h9v1H92zM119 69h7v1H119zM3 70h6v1H3zM91 70h10v1H91zM119 70h7v1H119zM3 71h6v1H3zM90 71h11v1H90zM119 71h7v1H119zM3 72h6v1H3zM89 72h13v1H89zM118 72h7v1H118zM3 73h6v1H3zM89 73h14v1H89zM117 73h8v1H117zM3 74h7v1H3zM87 74h38v1H87zM3 75h7v1H3zM87 75h37v1H87zM3 76h8v1H3zM86 76h37v1H86zM4 77h8v1H4zM85 77h9v1H85zM98 77h24v1H98zM4 78h9v1H4zM84 78h9v1H84zM99 78h22v1H99zM5 79h12v1H5zM46 79h2v1H46zM83 79h9v1H83zM101 79h18v1H101zM6 80h44v1H6zM82 80h9v1H82zM6 81h45v1H6zM81 81h9v1H81zM8 82h43v1H8zM80 82h9v1H80zM9 83h42v1H9zM79 83h10v1H79zM11 84h40v1H11zM78 84h10v1H78zM15 85h36v1H15zM77 85h9v1H77zM43 86h8v1H43zM76 86h10v1H76zM43 87h7v1H43zM75 87h10v1H75zM43 88h7v1H43zM74 88h10v1H74zM42 89h8v1H42zM73 89h10v1H73zM42 90h7v1H42zM72 90h10v1H72zM42 91h7v1H42zM71 91h10v1H71zM41 92h7v1H41zM70 92h10v1H70zM41 93h7v1H41zM69 93h10v1H69zM41 94h7v1H41zM68 94h10v1H68zM41 95h6v1H41zM67 95h10v1H67zM40 96h7v1H40zM66 96h10v1H66zM40 97h7v1H40zM65 97h10v1H65zM40 98h7v1H40zM65 98h9v1H65zM39 99h7v1H39zM64 99h9v1H64zM39 100h7v1H39zM63 100h9v1H63zM39 101h7v1H39zM62 101h9v1H62zM39 102h6v1H39zM62 102h8v1H62zM39 103h6v1H39zM61 103h8v1H61zM39 104h6v1H39zM60 104h8v1H60zM39 105h6v1H39zM60 105h8v1H60zM38 106h7v1H38zM59 106h8v1H59zM38 107h7v1H38zM59 107h7v1H59zM38 108h7v1H38zM59 108h7v1H59zM38 109h7v1H38zM58 109h7v1H58zM38 110h7v1H38zM58 110h7v1H58zM38 111h7v1H38zM57 111h8v1H57zM39 112h6v1H39zM57 112h7v1H57zM39 113h6v1H39zM57 113h7v1H57zM39 114h7v1H39zM57 114h6v1H57zM39 115h7v1H39zM57 115h6v1H57zM39 116h8v1H39zM56 116h7v1H56zM40 117h8v1H40zM55 117h8v1H55zM40 118h8v1H40zM54 118h8v1H54zM41 119h9v1H41zM53 119h9v1H53zM41 120h20v1H41zM42 121h19v1H42zM43 122h17v1H43zM44 123h15v1H44zM46 124h11v1H46zM49 125h5v1H49z"></path></symbol>
 417 |     </svg>
 418 |     <div id="theme_top_chrome_spacer" aria-hidden="true"></div>
 419 |     <!-- $BeginBlock top_hat -->
 420 |     <div class="topic_hat_fixed post_container top_hat_overlay_host ${user_online} ${top_hat_state_class}" data-post-id="${post_id}" data-display-date="${date}" data-user-id="${user_id}" data-nick="${nick}">
 421 |         <div class="hat_content ${top_hat_state_class}">
 422 |             <div class="post_header">
 423 |                 <div tabindex="-1" class="accessibility_anchor"></div>
 424 |                 <div class="header_wrapper">
 425 |                     <div class="avatar ${none_avatar} aec">
 426 |                         <b class="letter" onclick="IThemePresenter.showUserMenu('${post_id}')">${nick_letter}</b>
 427 |                         <div class="img" style="background-image:url(${avatar});" onclick="IThemePresenter.showUserMenu('${post_id}')" role="button" aria-label="${res_s_avatar}"></div>
 428 |                         <div class="reputation" onclick="IThemePresenter.showReputationMenu('${post_id}')" role="button" aria-label="${res_s_reputation} ${reputation}"><span>${reputation}</span></div>
 429 |                     </div>
 430 |                     <span class="inf nick">
 431 |                 <span class="aec" onclick="IThemePresenter.showUserMenu('${post_id}')"><b>${nick}</b></span>
 432 |                         <span class="online_dot" aria-hidden="true"></span>
 433 |                     </span>
 434 |                     <span class="inf post_meta">
 435 |                         <span class="group_text aec" style="color:${group_color};" onclick="IThemePresenter.toast('${res_s_group}: ${group}')" role="button" aria-label="${res_s_group} ${group}"><span>${group}</span></span>
 436 |                         <span class="date"><span>${date}</span></span>
 437 |                     </span>
 438 |                     ${user_post_count_html}
 439 |                     <span class="inf number"><span>#${number}</span></span>
 440 |                     <a class="btn inf menu aec" href="javascript:void(0)" onclick="IThemePresenter.showPostMenu('${post_id}'); return false;" role="button" aria-label="${res_s_menu}"><span>${res_s_menu}</span></a>
 441 |                 </div>
 442 |             </div>
 443 |             <div class="post_body emoticons">${body}</div>
 444 |             <div class="post_footer">
 445 |                 <!-- $BeginBlock top_hat_report_block -->
 446 |                 <!--<a class="btn report" onclick="IThemePresenter.reportPost(${post_id})"><span>${res_s_report}</span></a>-->
 447 |                 <!-- $EndBlock top_hat_report_block -->
 448 |                 <!-- $BeginBlock top_hat_reply_quote_row -->
 449 |                 <div class="post_actions_row">
 450 |                 <!-- $BeginBlock top_hat_rep_up_block -->
 451 |                 <a class="btn rep_up aec" data-post-id="${post_id}" onclick="votePostFromEl(this, true)" role="button" aria-label="${res_s_vote_post_good}" title="${res_s_vote_post_good}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-up-final" xlink:href="#rep-thumb-up-final"></use></svg><span>+</span></a>
 452 |                 <!-- $EndBlock top_hat_rep_up_block -->
 453 |                 <span class="post_rating ${post_rating_state} ${post_rating_hidden_class}" aria-label="${res_s_post_rating} ${post_rating}"><b>${post_rating}</b></span>
 454 |                 <!-- $BeginBlock top_hat_rep_down_block -->
 455 |                 <a class="btn rep_down aec" data-post-id="${post_id}" onclick="votePostFromEl(this, false)" role="button" aria-label="${res_s_vote_post_bad}" title="${res_s_vote_post_bad}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-down-final" xlink:href="#rep-thumb-down-final"></use></svg><span>-</span></a>
 456 |                 <!-- $EndBlock top_hat_rep_down_block -->
 457 |                 <a class="btn reply aec" onclick="replyThemePost('${post_id}')" role="button" aria-label="${res_s_reply}" title="${res_s_reply}"><svg class="post-action-icon post-action-stroke-icon post-action-reply-icon" aria-hidden="true" viewBox="3.25 3.25 17.5 17.5"><path d="M12 4.5a7.5 7.5 0 017.5 7.5 7.5 7.5 0 01-7.5 7.5 7.3 7.3 0 01-3.1-.68L5 20l1.18-3.75A7.46 7.46 0 014.5 12 7.5 7.5 0 0112 4.5z"/></svg><span>${res_s_reply}</span></a>
 458 |                 <a class="btn quote aec" onclick="quoteFullThemePost('${post_id}', this)" role="button" aria-label="${res_s_quote}" title="${res_s_quote}"><svg class="post-action-icon post-action-stroke-icon post-action-quote-icon quote-action-icon" aria-hidden="true" viewBox="2 2 20 20"><path d="M3.5 6h17"/><path d="M17 2.75L20.5 6 17 9.25"/><path d="M3.5 6v5.5H6"/><path d="M20.5 18h-17"/><path d="M7 14.75L3.5 18 7 21.25"/><path d="M20.5 18v-5.5H18"/></svg><span>${res_s_quote}</span></a>
 459 |                 </div>
 460 |                 <!-- $EndBlock top_hat_reply_quote_row -->
 461 |                 <!-- $BeginBlock top_hat_delete_block -->
 462 |                 <!--<a class="btn delete" onclick="IThemePresenter.deletePost('${post_id}')"><span>${res_s_delete}</span></a>-->
 463 |                 <!-- $EndBlock top_hat_delete_block -->
 464 |                 <!-- $BeginBlock top_hat_edit_block -->
 465 |                 <!--<a class="btn edit" onclick="IThemePresenter.editPost('${post_id}')"><span>${res_s_edit}</span></a>-->
 466 |                 <!-- $EndBlock top_hat_edit_block -->
 467 |             </div>
 468 |         </div>
 469 |     </div>
 470 |     <!-- $EndBlock top_hat -->
 471 |     <!-- $BeginBlock poll_overlay_block -->
 472 |     <div id="theme_poll_overlay_host" class="poll ${poll_type} poll_overlay_host ${poll_overlay_state_class}">
 473 |         <a class="btn title aec" onclick="toggleButton(this, 'hat_content', 'poll')" role="button" aria-label="${res_s_poll_title}: ${poll_title}"><span>${poll_title}</span><i class="icon"></i></a>
 474 |         <form action="${poll_form_action}" method="${poll_form_method}" class="hat_content body ${poll_overlay_state_class}" onsubmit="return submitThemePoll(this)">
 475 |             <div class="questions">
 476 |                 <!-- $BeginBlock poll_overlay_question_block -->
 477 |                 <div class="question">
 478 |                     <div class="title">${question_title}</div>
 479 |                     <div class="items">
 480 |                         <!-- $BeginBlock poll_overlay_default_item -->
 481 |                         <label class="item ${poll_type} aec">
 482 |                             <input type="${question_item_type}" name="${question_item_name}" value="${question_item_value}" ${question_item_disabled}><i class="icon"><i class="add"></i></i><span class="title"><span>${question_item_title}</span></span>
 483 |                         </label>
 484 |                         <!-- $EndBlock poll_overlay_default_item -->
 485 |                         <!-- $BeginBlock poll_overlay_result_item -->
 486 |                         <div class="item ${poll_type}">
 487 |                             <div class="range_bar">
 488 |                                 <div class="range" style="min-width:${question_item_percent}%;"></div>
 489 |                                 <span class="value">${question_item_percent}%<span class="num_votes"><span>${question_item_votes}</span></span>
 490 |                                 </span>
 491 |                                 <span class="title"><span>${question_item_title}</span></span>
 492 |                             </div>
 493 |                         </div>
 494 |                         <!-- $EndBlock poll_overlay_result_item -->
 495 |                     </div>
 496 |                 </div>
 497 |                 <!-- $EndBlock poll_overlay_question_block -->
 498 |             </div>
 499 |             <div class="votes_info"><span>${res_s_poll_all_votes_count}: ${poll_votes_count}</span></div>
 500 |             <!-- $BeginBlock poll_overlay_unavailable_status -->
 501 |             <div class="poll_status"><span>${res_s_poll_unavailable}</span></div>
 502 |             <!-- $EndBlock poll_overlay_unavailable_status -->
 503 |             <!-- $BeginBlock poll_overlay_buttons -->
 504 |             <div class="buttons">
 505 |                 <!-- $BeginBlock poll_overlay_vote_button -->
 506 |                 <button type="submit" class="btn vote aec" role="button" aria-label="${res_s_poll_vote_btn}"><span>${res_s_poll_vote_btn}</span></button>
 507 |                 <!-- $EndBlock poll_overlay_vote_button -->
 508 |                 <!-- $BeginBlock poll_overlay_show_results_button --><a class="btn show_results aec" onclick="IThemePresenter.showPollResults('${poll_results_url}')" role="button" aria-label="${res_s_poll_results_btn}"><span>${res_s_poll_results_btn}</span></a>
 509 |                 <!-- $EndBlock poll_overlay_show_results_button -->
 510 |                 <!-- $BeginBlock poll_overlay_show_poll_button --><a class="btn show_poll aec" onclick="IThemePresenter.showPoll()" role="button" aria-label="${res_s_poll_show_btn}"><span>${res_s_poll_show_btn}</span></a>
 511 |                 <!-- $EndBlock poll_overlay_show_poll_button -->
 512 |             </div>
 513 |             <!-- $EndBlock poll_overlay_buttons -->
 514 |             <!-- $BeginBlock poll_overlay_hidden_input -->
 515 |             <input type="hidden" name="${poll_hidden_name}" value="${poll_hidden_value}">
 516 |             <!-- $EndBlock poll_overlay_hidden_input -->
 517 |         </form>
 518 |     </div>
 519 |     <!-- $EndBlock poll_overlay_block -->
 520 |     <div class="posts_list">
 521 |         <!-- theme_posts_list_start -->
 522 |         <!-- $BeginBlock top_hat_entry -->
 523 |         <div name="entry${post_id}" id="post-${post_id}" class="topic_hat_entry post_container top_hat_entry ${inline_hat_state_class} ${user_online}${post_highlight_class}" data-topic-id="${topic_id_int}" data-post-id="${post_id}" data-display-date="${date}" data-user-id="${user_id}" data-nick="${nick}" data-render-generation="${ppda_render_generation_id_int}">
 524 |             <a class="btn hat_button inline_hat_button aec" onclick="toggleInlineTopicHat(this, '${topic_id_int}')" role="button" aria-label="${res_s_hat}"><span>${res_s_hat}</span><i class="icon"></i></a>
 525 |             <div class="hat_content inline_hat_content ${inline_hat_state_class}">
 526 |                 <div class="post_header">
 527 |                     <div tabindex="-1" class="accessibility_anchor"></div>
 528 |                     <div class="header_wrapper">
 529 |                         <div class="avatar ${none_avatar} aec">
 530 |                             <b class="letter" onclick="IThemePresenter.showUserMenu('${post_id}')">${nick_letter}</b>
 531 |                             <div class="img" style="background-image:url(${avatar});" onclick="IThemePresenter.showUserMenu('${post_id}')" role="button" aria-label="${res_s_avatar}"></div>
 532 |                             <div class="reputation" onclick="IThemePresenter.showReputationMenu('${post_id}')" role="button" aria-label="${res_s_reputation} ${reputation}"><span>${reputation}</span></div>
 533 |                         </div>
 534 |                         <span class="inf nick">
 535 |                     <span class="aec" onclick="IThemePresenter.showUserMenu('${post_id}')"><b>${nick}</b></span>
 536 |                             <span class="online_dot" aria-hidden="true"></span>
 537 |                         </span>
 538 |                         <span class="inf post_meta">
 539 |                             <span class="group_text aec" style="color:${group_color};" onclick="IThemePresenter.toast('${res_s_group}: ${group}')" role="button" aria-label="${res_s_group} ${group}"><span>${group}</span></span>
 540 |                             <span class="date"><span>${date}</span></span>
 541 |                         </span>
 542 |                         ${user_post_count_html}
 543 |                         <span class="inf number"><span>#${number}</span></span>
 544 |                         <a class="btn inf menu aec" href="javascript:void(0)" onclick="IThemePresenter.showPostMenu('${post_id}'); return false;" role="button" aria-label="${res_s_menu}"><span>${res_s_menu}</span></a>
 545 |                     </div>
 546 |                 </div>
 547 |                 <div class="post_body emoticons">${body}</div>
 548 |                 <div class="post_footer">
 549 |                     <!-- $BeginBlock top_hat_entry_report_block -->
 550 |                     <!--<a class="btn report" onclick="IThemePresenter.reportPost(${post_id})"><span>${res_s_report}</span></a>-->
 551 |                     <!-- $EndBlock top_hat_entry_report_block -->
 552 |                     <!-- $BeginBlock top_hat_entry_reply_quote_row -->
 553 |                     <div class="post_actions_row">
 554 |                     <!-- $BeginBlock top_hat_entry_rep_up_block -->
 555 |                     <a class="btn rep_up aec" data-post-id="${post_id}" onclick="votePostFromEl(this, true)" role="button" aria-label="${res_s_vote_post_good}" title="${res_s_vote_post_good}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-up-final" xlink:href="#rep-thumb-up-final"></use></svg><span>+</span></a>
 556 |                     <!-- $EndBlock top_hat_entry_rep_up_block -->
 557 |                     <span class="post_rating ${post_rating_state} ${post_rating_hidden_class}" aria-label="${res_s_post_rating} ${post_rating}"><b>${post_rating}</b></span>
 558 |                     <!-- $BeginBlock top_hat_entry_rep_down_block -->
 559 |                     <a class="btn rep_down aec" data-post-id="${post_id}" onclick="votePostFromEl(this, false)" role="button" aria-label="${res_s_vote_post_bad}" title="${res_s_vote_post_bad}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-down-final" xlink:href="#rep-thumb-down-final"></use></svg><span>-</span></a>
 560 |                     <!-- $EndBlock top_hat_entry_rep_down_block -->
 561 |                     <a class="btn reply aec" onclick="replyThemePost('${post_id}')" role="button" aria-label="${res_s_reply}" title="${res_s_reply}"><svg class="post-action-icon post-action-stroke-icon post-action-reply-icon" aria-hidden="true" viewBox="3.25 3.25 17.5 17.5"><path d="M12 4.5a7.5 7.5 0 017.5 7.5 7.5 7.5 0 01-7.5 7.5 7.3 7.3 0 01-3.1-.68L5 20l1.18-3.75A7.46 7.46 0 014.5 12 7.5 7.5 0 0112 4.5z"/></svg><span>${res_s_reply}</span></a>
 562 |                     <a class="btn quote aec" onclick="quoteFullThemePost('${post_id}', this)" role="button" aria-label="${res_s_quote}" title="${res_s_quote}"><svg class="post-action-icon post-action-stroke-icon post-action-quote-icon quote-action-icon" aria-hidden="true" viewBox="2 2 20 20"><path d="M3.5 6h17"/><path d="M17 2.75L20.5 6 17 9.25"/><path d="M3.5 6v5.5H6"/><path d="M20.5 18h-17"/><path d="M7 14.75L3.5 18 7 21.25"/><path d="M20.5 18v-5.5H18"/></svg><span>${res_s_quote}</span></a>
 563 |                     </div>
 564 |                     <!-- $EndBlock top_hat_entry_reply_quote_row -->
 565 |                     <!-- $BeginBlock top_hat_entry_delete_block -->
 566 |                     <!--<a class="btn delete" onclick="IThemePresenter.deletePost('${post_id}')"><span>${res_s_delete}</span></a>-->
 567 |                     <!-- $EndBlock top_hat_entry_delete_block -->
 568 |                     <!-- $BeginBlock top_hat_entry_edit_block -->
 569 |                     <!--<a class="btn edit" onclick="IThemePresenter.editPost('${post_id}')"><span>${res_s_edit}</span></a>-->
 570 |                     <!-- $EndBlock top_hat_entry_edit_block -->
 571 |                 </div>
 572 |             </div>
 573 |         </div>
 574 |         <!-- $EndBlock top_hat_entry -->
 575 |         <!-- $BeginBlock poll_block -->
 576 |         <div class="poll ${poll_type} poll_entry ${poll_state_class}">
 577 |             <a class="btn title aec" onclick="toggleButton(this, 'hat_content', 'poll')" role="button" aria-label="${res_s_poll_title}: ${poll_title}"><span>${poll_title}</span><i class="icon"></i></a>
 578 |             <form action="${poll_form_action}" method="${poll_form_method}" class="hat_content body ${poll_state_class}" onsubmit="return submitThemePoll(this)">
 579 |                 <div class="questions">
 580 |                     <!-- $BeginBlock poll_question_block -->
 581 |                     <div class="question">
 582 |                         <div class="title">${question_title}</div>
 583 |                         <div class="items">
 584 |                             <!-- $BeginBlock poll_default_item -->
 585 |                             <label class="item ${poll_type} aec">
 586 |                                 <input type="${question_item_type}" name="${question_item_name}" value="${question_item_value}" ${question_item_disabled}><i class="icon"><i class="add"></i></i><span class="title"><span>${question_item_title}</span></span>
 587 |                             </label>
 588 |                             <!-- $EndBlock poll_default_item -->
 589 |                             <!-- $BeginBlock poll_result_item -->
 590 |                             <div class="item ${poll_type}">
 591 |                                 <div class="range_bar">
 592 |                                     <div class="range" style="min-width:${question_item_percent}%;"></div>
 593 |                                     <span class="value">${question_item_percent}%<span class="num_votes"><span>${question_item_votes}</span></span>
 594 |                                     </span>
 595 |                                     <span class="title"><span>${question_item_title}</span></span>
 596 |                                 </div>
 597 |                             </div>
 598 |                             <!-- $EndBlock poll_result_item -->
 599 |                         </div>
 600 |                     </div>
 601 |                     <!-- $EndBlock poll_question_block -->
 602 |                 </div>
 603 |                 <div class="votes_info"><span>${res_s_poll_all_votes_count}: ${poll_votes_count}</span></div>
 604 |                 <!-- $BeginBlock poll_unavailable_status -->
 605 |                 <div class="poll_status"><span>${res_s_poll_unavailable}</span></div>
 606 |                 <!-- $EndBlock poll_unavailable_status -->
 607 |                 <!-- $BeginBlock poll_buttons -->
 608 |                 <div class="buttons">
 609 |                     <!-- $BeginBlock poll_vote_button -->
 610 |                     <button type="submit" class="btn vote aec" role="button" aria-label="${res_s_poll_vote_btn}"><span>${res_s_poll_vote_btn}</span></button>
 611 |                     <!-- $EndBlock poll_vote_button -->
 612 |                     <!-- $BeginBlock poll_show_results_button --><a class="btn show_results aec" onclick="IThemePresenter.showPollResults('${poll_results_url}')" role="button" aria-label="${res_s_poll_results_btn}"><span>${res_s_poll_results_btn}</span></a>
 613 |                     <!-- $EndBlock poll_show_results_button -->
 614 |                     <!-- $BeginBlock poll_show_poll_button --><a class="btn show_poll aec" onclick="IThemePresenter.showPoll()" role="button" aria-label="${res_s_poll_show_btn}"><span>${res_s_poll_show_btn}</span></a>
 615 |                     <!-- $EndBlock poll_show_poll_button -->
 616 |                 </div>
 617 |                 <!-- $EndBlock poll_buttons -->
 618 |                 <!-- $BeginBlock poll_hidden_input -->
 619 |                 <input type="hidden" name="${poll_hidden_name}" value="${poll_hidden_value}">
 620 |                 <!-- $EndBlock poll_hidden_input -->
 621 |             </form>
 622 |         </div>
 623 |         <!-- $EndBlock poll_block -->
 624 |         <!-- $BeginBlock post -->
 625 |         <div name="entry${post_id}" id="post-${post_id}" class="post_container${blacklisted_post_class}${user_online}${hat_state_class}${post_highlight_class}" data-post-id="${post_id}" data-display-date="${date}" data-user-id="${user_id}" data-nick="${nick}" data-render-generation="${ppda_render_generation_id_int}">
 626 |             <!-- $BeginBlock blacklisted_stub_open -->
 627 |             <button type="button" class="blacklisted_post_placeholder" onclick="toggleBlacklistedPost('${post_id}', this); return false;" role="button" aria-expanded="false" aria-label="${res_s_forum_blacklist_post_hidden}"><span class="blacklisted_post_placeholder_text">${res_s_forum_blacklist_post_hidden}</span></button>
 628 |             <div class="blacklisted_post_content" aria-hidden="true" hidden>
 629 |             <!-- $EndBlock blacklisted_stub_open -->
 630 |             <!-- $BeginBlock hat_button --><a class="btn hat_button aec" onclick="toggleButton(this, 'hat_content', 'hat')" role="button" aria-label="${res_s_hat}"><span>${res_s_hat}</span><i class="icon"></i></a>
 631 |             <!-- $EndBlock hat_button -->
 632 |             <!-- $BeginBlock hat_content_start -->
 633 |             <div class="hat_content ${hat_state_class}">
 634 |                 <!-- $EndBlock hat_content_start -->
 635 |                 <div class="post_header">
 636 |                     <div tabindex="-1" class="accessibility_anchor"></div>
 637 |                     <div class="header_wrapper">
 638 |                         <div class="avatar ${none_avatar} aec">
 639 |                             <b class="letter" onclick="IThemePresenter.showUserMenu('${post_id}')">${nick_letter}</b>
 640 |                             <div class="img" style="background-image:url(${avatar});" onclick="IThemePresenter.showUserMenu('${post_id}')" role="button" aria-label="${res_s_avatar}"></div>
 641 |                             <div class="reputation" onclick="IThemePresenter.showReputationMenu('${post_id}')" role="button" aria-label="${res_s_reputation} ${reputation}"><span>${reputation}</span></div>
 642 |                         </div>
 643 |                         <span class="inf nick">
 644 |                     <span class="aec" onclick="IThemePresenter.showUserMenu('${post_id}')"><b>${nick}</b></span>
 645 |                             <span class="online_dot" aria-hidden="true"></span>
 646 |                         </span>
 647 |                         <span class="inf post_meta">
 648 |                             <span class="group_text aec" style="color:${group_color};" onclick="IThemePresenter.toast('${res_s_group}: ${group}')" role="button" aria-label="${res_s_group} ${group}"><span>${group}</span></span>
 649 |                             <span class="date"><span>${date}</span></span>
 650 |                         </span>
 651 |                         ${user_post_count_html}
 652 |                         <span class="inf number"><span>#${number}</span></span>
 653 |                         <a class="btn inf menu aec" href="javascript:void(0)" onclick="IThemePresenter.showPostMenu('${post_id}'); return false;" role="button" aria-label="${res_s_menu}"><span>${res_s_menu}</span></a>
 654 |                     </div>
 655 |                 </div>
 656 |                 <!-- $BeginBlock blacklisted_post_body -->
 657 |                 <div class="post_body emoticons">${body}</div>
 658 |                 <!-- $EndBlock blacklisted_post_body -->
 659 |                 <!-- $BeginBlock blacklisted_post_footer -->
 660 |                 <div class="post_footer">
 661 |                     <!-- $BeginBlock blacklisted_report_block -->
 662 |                     <!--<a class="btn report" onclick="IThemePresenter.reportPost(${post_id})"><span>${res_s_report}</span></a>-->
 663 |                     <!-- $EndBlock blacklisted_report_block -->
 664 |                     <!-- $BeginBlock blacklisted_reply_quote_row -->
 665 |                     <div class="post_actions_row">
 666 |                     <!-- $BeginBlock blacklisted_rep_up_block -->
 667 |                     <a class="btn rep_up aec" data-post-id="${post_id}" onclick="votePostFromEl(this, true)" role="button" aria-label="${res_s_vote_post_good}" title="${res_s_vote_post_good}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-up-final" xlink:href="#rep-thumb-up-final"></use></svg><span>+</span></a>
 668 |                     <!-- $EndBlock blacklisted_rep_up_block -->
 669 |                     <span class="post_rating ${post_rating_state} ${post_rating_hidden_class}" aria-label="${res_s_post_rating} ${post_rating}"><b>${post_rating}</b></span>
 670 |                     <!-- $BeginBlock blacklisted_rep_down_block -->
 671 |                     <a class="btn rep_down aec" data-post-id="${post_id}" onclick="votePostFromEl(this, false)" role="button" aria-label="${res_s_vote_post_bad}" title="${res_s_vote_post_bad}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-down-final" xlink:href="#rep-thumb-down-final"></use></svg><span>-</span></a>
 672 |                     <!-- $EndBlock blacklisted_rep_down_block -->
 673 |                     <a class="btn reply aec" onclick="replyThemePost('${post_id}')" role="button" aria-label="${res_s_reply}" title="${res_s_reply}"><svg class="post-action-icon post-action-stroke-icon post-action-reply-icon" aria-hidden="true" viewBox="3.25 3.25 17.5 17.5"><path d="M12 4.5a7.5 7.5 0 017.5 7.5 7.5 7.5 0 01-7.5 7.5 7.3 7.3 0 01-3.1-.68L5 20l1.18-3.75A7.46 7.46 0 014.5 12 7.5 7.5 0 0112 4.5z"/></svg><span>${res_s_reply}</span></a>
 674 |                     <a class="btn quote aec" onclick="quoteFullThemePost('${post_id}', this)" role="button" aria-label="${res_s_quote}" title="${res_s_quote}"><svg class="post-action-icon post-action-stroke-icon post-action-quote-icon quote-action-icon" aria-hidden="true" viewBox="2 2 20 20"><path d="M3.5 6h17"/><path d="M17 2.75L20.5 6 17 9.25"/><path d="M3.5 6v5.5H6"/><path d="M20.5 18h-17"/><path d="M7 14.75L3.5 18 7 21.25"/><path d="M20.5 18v-5.5H18"/></svg><span>${res_s_quote}</span></a>
 675 |                     </div>
 676 |                     <!-- $EndBlock blacklisted_reply_quote_row -->
 677 |                     <!-- $BeginBlock blacklisted_delete_block -->
 678 |                     <!--<a class="btn delete" onclick="IThemePresenter.deletePost('${post_id}')"><span>${res_s_delete}</span></a>-->
 679 |                     <!-- $EndBlock blacklisted_delete_block -->
 680 |                     <!-- $BeginBlock blacklisted_edit_block -->
 681 |                     <!--<a class="btn edit" onclick="IThemePresenter.editPost('${post_id}')"><span>${res_s_edit}</span></a>-->
 682 |                     <!-- $EndBlock blacklisted_edit_block -->
 683 |                 </div>
 684 |                 <!-- $EndBlock blacklisted_post_footer -->
 685 |                 <!-- $BeginBlock visible_post_body -->
 686 |                 <div class="post_body emoticons">${body}</div>
 687 |                 <!-- $EndBlock visible_post_body -->
 688 |                 <!-- $BeginBlock visible_post_footer -->
 689 |                 <div class="post_footer">
 690 |                     <!-- $BeginBlock report_block -->
 691 |                     <!--<a class="btn report" onclick="IThemePresenter.reportPost(${post_id})"><span>${res_s_report}</span></a>-->
 692 |                     <!-- $EndBlock report_block -->
 693 |                     <!-- $BeginBlock reply_quote_row -->
 694 |                     <div class="post_actions_row">
 695 |                     <!-- $BeginBlock rep_up_block -->
 696 |                     <a class="btn rep_up aec" data-post-id="${post_id}" onclick="votePostFromEl(this, true)" role="button" aria-label="${res_s_vote_post_good}" title="${res_s_vote_post_good}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-up-final" xlink:href="#rep-thumb-up-final"></use></svg><span>+</span></a>
 697 |                     <!-- $EndBlock rep_up_block -->
 698 |                     <span class="post_rating ${post_rating_state} ${post_rating_hidden_class}" aria-label="${res_s_post_rating} ${post_rating}"><b>${post_rating}</b></span>
 699 |                     <!-- $BeginBlock rep_down_block -->
 700 |                     <a class="btn rep_down aec" data-post-id="${post_id}" onclick="votePostFromEl(this, false)" role="button" aria-label="${res_s_vote_post_bad}" title="${res_s_vote_post_bad}"><svg class="rep-action-icon" aria-hidden="true" viewBox="0 0 128 128"><use href="#rep-thumb-down-final" xlink:href="#rep-thumb-down-final"></use></svg><span>-</span></a>
 701 |                     <!-- $EndBlock rep_down_block -->
 702 |                     <a class="btn reply aec" onclick="replyThemePost('${post_id}')" role="button" aria-label="${res_s_reply}" title="${res_s_reply}"><svg class="post-action-icon post-action-stroke-icon post-action-reply-icon" aria-hidden="true" viewBox="3.25 3.25 17.5 17.5"><path d="M12 4.5a7.5 7.5 0 017.5 7.5 7.5 7.5 0 01-7.5 7.5 7.3 7.3 0 01-3.1-.68L5 20l1.18-3.75A7.46 7.46 0 014.5 12 7.5 7.5 0 0112 4.5z"/></svg><span>${res_s_reply}</span></a>
 703 |                     <a class="btn quote aec" onclick="quoteFullThemePost('${post_id}', this)" role="button" aria-label="${res_s_quote}" title="${res_s_quote}"><svg class="post-action-icon post-action-stroke-icon post-action-quote-icon quote-action-icon" aria-hidden="true" viewBox="2 2 20 20"><path d="M3.5 6h17"/><path d="M17 2.75L20.5 6 17 9.25"/><path d="M3.5 6v5.5H6"/><path d="M20.5 18h-17"/><path d="M7 14.75L3.5 18 7 21.25"/><path d="M20.5 18v-5.5H18"/></svg><span>${res_s_quote}</span></a>
 704 |                     </div>
 705 |                     <!-- $EndBlock reply_quote_row -->
 706 |                     <!-- $BeginBlock delete_block -->
 707 |                     <!--<a class="btn delete" onclick="IThemePresenter.deletePost('${post_id}')"><span>${res_s_delete}</span></a>-->
 708 |                     <!-- $EndBlock delete_block -->
 709 |                     <!-- $BeginBlock edit_block -->
 710 |                     <!--<a class="btn edit" onclick="IThemePresenter.editPost('${post_id}')"><span>${res_s_edit}</span></a>-->
 711 |                     <!-- $EndBlock edit_block -->
 712 |                 </div>
 713 |                 <!-- $EndBlock visible_post_footer -->
 714 |                 <!-- $BeginBlock blacklisted_stub_close -->
 715 |             </div>
 716 |             <!-- $EndBlock blacklisted_stub_close -->
 717 |                 <!-- $BeginBlock hat_content_end -->
 718 |             </div>
 719 |             <!-- $EndBlock hat_content_end -->
 720 |         </div>
 721 |         <!-- $EndBlock post -->
 722 |         <!-- theme_posts_list_end -->
 723 |     </div>
 724 |     <!-- $BeginBlock bottom_pagination -->
 725 |     <nav class="theme_bottom_pagination" aria-label="${res_s_select_desc}">
 726 |         <div class="theme_bottom_pagination_row">
 727 |             <!-- $BeginBlock bottom_pagination_first_enabled -->
 728 |             <button type="button" class="theme_bottom_pagination_btn aec" onclick="IThemePresenter.firstPage()" aria-label="${res_s_first}"><span>«</span></button>
 729 |             <!-- $EndBlock bottom_pagination_first_enabled -->
 730 |             <!-- $BeginBlock bottom_pagination_first_disabled -->
 731 |             <button type="button" class="theme_bottom_pagination_btn disabled" disabled aria-disabled="true" aria-label="${res_s_first}"><span>«</span></button>
 732 |             <!-- $EndBlock bottom_pagination_first_disabled -->
 733 |             <!-- $BeginBlock bottom_pagination_prev_enabled -->
 734 |             <button type="button" class="theme_bottom_pagination_btn aec" onclick="IThemePresenter.prevPage()" aria-label="${res_s_prev}"><span>‹</span></button>
 735 |             <!-- $EndBlock bottom_pagination_prev_enabled -->
 736 |             <!-- $BeginBlock bottom_pagination_prev_disabled -->
 737 |             <button type="button" class="theme_bottom_pagination_btn disabled" disabled aria-disabled="true" aria-label="${res_s_prev}"><span>‹</span></button>
 738 |             <!-- $EndBlock bottom_pagination_prev_disabled -->
 739 |             <button type="button" class="theme_bottom_pagination_current aec" onclick="IThemePresenter.selectPage()" oncontextmenu="return themeBottomPaginationInput(event)" aria-label="${bottom_pagination_accessibility_label}" title="${bottom_pagination_accessibility_label}"><span>${bottom_pagination_label}</span></button>
 740 |             <!-- $BeginBlock bottom_pagination_next_enabled -->
 741 |             <button type="button" class="theme_bottom_pagination_btn aec" onclick="IThemePresenter.nextPage()" aria-label="${res_s_next}"><span>›</span></button>
 742 |             <!-- $EndBlock bottom_pagination_next_enabled -->
 743 |             <!-- $BeginBlock bottom_pagination_next_disabled -->
 744 |             <button type="button" class="theme_bottom_pagination_btn disabled" disabled aria-disabled="true" aria-label="${res_s_next}"><span>›</span></button>
 745 |             <!-- $EndBlock bottom_pagination_next_disabled -->
 746 |             <!-- $BeginBlock bottom_pagination_last_enabled -->
 747 |             <button type="button" class="theme_bottom_pagination_btn aec" onclick="IThemePresenter.lastPage()" aria-label="${res_s_last}"><span>»</span></button>
 748 |             <!-- $EndBlock bottom_pagination_last_enabled -->
 749 |             <!-- $BeginBlock bottom_pagination_last_disabled -->
 750 |             <button type="button" class="theme_bottom_pagination_btn disabled" disabled aria-disabled="true" aria-label="${res_s_last}"><span>»</span></button>
 751 |             <!-- $EndBlock bottom_pagination_last_disabled -->
 752 |         </div>
 753 |     </nav>
 754 |     <!-- $EndBlock bottom_pagination -->
 755 |     <div id="bottom_chrome_spacer" aria-hidden="true"></div>
 756 |     <script type="text/javascript">
 757 |         jsEmoticons.parseAll("file:///android_asset/smiles/")
 758 | 
 759 |         function themeBottomPaginationInput(event) {
 760 |             if (event) {
 761 |                 event.preventDefault();
 762 |                 event.stopPropagation();
 763 |             }
 764 |             IThemePresenter.selectPageInput();
 765 |             return false;
 766 |         }
 767 | 
 768 |         function suppressThemeBottomPaginationSelection(event) {
 769 |             var target = event && event.target;
 770 |             if (!target || !target.closest || !target.closest(".theme_bottom_pagination")) {
 771 |                 return true;
 772 |             }
 773 |             event.preventDefault();
 774 |             return false;
 775 |         }
 776 | 
 777 |         document.addEventListener("selectstart", suppressThemeBottomPaginationSelection, true);
 778 |         document.addEventListener("dragstart", suppressThemeBottomPaginationSelection, true);
 779 | 
 780 |         var topicHeaderInlineObserver = null;
 781 |         var topicHeaderInlineFallbackBound = false;
 782 |         var topicHeaderInlineFallbackRaf = null;
 783 |         var topicHeaderInlineVisibleOnScreen = false;
 784 | 
 785 |         function findInlineTopicHeaderBlock() {
 786 |             return document.querySelector(".topic_hat_entry.top_hat_entry");
 787 |         }
 788 | 
 789 |         function findTopicHeaderOverlayBlock() {
 790 |             return document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
 791 |         }
 792 | 
 793 |         function isTopicHeaderOverlayVisible() {
 794 |             var block = findTopicHeaderOverlayBlock();
 795 |             if (!block) return false;
 796 |             var body = block.querySelector(".hat_content");
 797 |             return !block.classList.contains("initial_open") &&
 798 |                 block.classList.contains("open") && !block.classList.contains("close") &&
 799 |                 (!body || (body.classList.contains("open") && !body.classList.contains("close")));
 800 |         }
 801 | 
 802 |         function computeInlineTopicHeaderVisibleOnScreen() {
 803 |             var block = findInlineTopicHeaderBlock();
 804 |             if (!block || typeof block.getBoundingClientRect !== "function") return false;
 805 |             var rect = block.getBoundingClientRect();
 806 |             var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
 807 |             var viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
 808 |             return rect.width > 0 && rect.height > 0 &&
 809 |                 rect.bottom > 0 && rect.right > 0 &&
 810 |                 rect.top < viewportHeight && rect.left < viewportWidth;
 811 |         }
 812 | 
 813 |         function onTopicHeaderViewportVisibilityChanged(visible) {
 814 |             topicHeaderInlineVisibleOnScreen = visible === true;
 815 |         }
 816 | 
 817 |         function updateInlineTopicHeaderVisibleOnScreen(visible) {
 818 |             var next = visible === true;
 819 |             if (topicHeaderInlineVisibleOnScreen === next) return;
 820 |             onTopicHeaderViewportVisibilityChanged(next);
 821 |         }
 822 | 
 823 |         function scheduleInlineTopicHeaderFallbackCheck() {
 824 |             if (topicHeaderInlineFallbackRaf !== null) return;
 825 |             topicHeaderInlineFallbackRaf = requestAnimationFrame(function () {
 826 |                 topicHeaderInlineFallbackRaf = null;
 827 |                 updateInlineTopicHeaderVisibleOnScreen(computeInlineTopicHeaderVisibleOnScreen());
 828 |             });
 829 |         }
 830 | 
 831 |         function installInlineTopicHeaderVisibilityObserver() {
 832 |             destroyInlineTopicHeaderVisibilityObserver();
 833 |             var block = findInlineTopicHeaderBlock();
 834 |             topicHeaderInlineVisibleOnScreen = computeInlineTopicHeaderVisibleOnScreen();
 835 |             if (!block) return;
 836 |             if (typeof IntersectionObserver === "function") {
 837 |                 topicHeaderInlineObserver = new IntersectionObserver(function (entries) {
 838 |                     if (!entries || !entries.length) return;
 839 |                     updateInlineTopicHeaderVisibleOnScreen(entries[0].isIntersecting === true);
 840 |                 }, {threshold: 0});
 841 |                 topicHeaderInlineObserver.observe(block);
 842 |                 return;
 843 |             }
 844 |             window.addEventListener("scroll", scheduleInlineTopicHeaderFallbackCheck, {passive: true});
 845 |             window.addEventListener("resize", scheduleInlineTopicHeaderFallbackCheck);
 846 |             topicHeaderInlineFallbackBound = true;
 847 |         }
 848 | 
 849 |         function destroyInlineTopicHeaderVisibilityObserver() {
 850 |             if (topicHeaderInlineObserver) {
 851 |                 topicHeaderInlineObserver.disconnect();
 852 |                 topicHeaderInlineObserver = null;
 853 |             }
 854 |             if (topicHeaderInlineFallbackBound) {
 855 |                 window.removeEventListener("scroll", scheduleInlineTopicHeaderFallbackCheck);
 856 |                 window.removeEventListener("resize", scheduleInlineTopicHeaderFallbackCheck);
 857 |                 topicHeaderInlineFallbackBound = false;
 858 |             }
 859 |             if (topicHeaderInlineFallbackRaf !== null) {
 860 |                 cancelAnimationFrame(topicHeaderInlineFallbackRaf);
 861 |                 topicHeaderInlineFallbackRaf = null;
 862 |             }
 863 |         }
 864 | 
 865 |         function isTopicHeaderVisibleOnScreen() {
 866 |             return computeInlineTopicHeaderVisibleOnScreen();
 867 |         }
 868 | 
 869 |         function showTopicHeaderOverlay() {
 870 |             return toggleThemeHatFromFixed(true);
 871 |         }
 872 | 
 873 |         function openTopicHeaderOverlayFromToolbar() {
 874 |             if (typeof suppressThemeInfiniteScrollFor === "function") {
 875 |                 suppressThemeInfiniteScrollFor(1200);
 876 |             }
 877 |             closeInlineTopicHatForToolbarOverlay();
 878 |             return toggleThemeHatFromFixed(true);
 879 |         }
 880 | 
 881 |         function hideTopicHeaderOverlay() {
 882 |             return toggleThemeHatFromFixed(false);
 883 |         }
 884 | 
 885 |         function closeInlineTopicHatForToolbarOverlay() {
 886 |             var block = findInlineTopicHeaderBlock();
 887 |             if (!block) return false;
 888 |             var body = block.querySelector(".inline_hat_content");
 889 |             var isOpen = block.classList.contains("open") ||
 890 |                 (body && body.classList.contains("open") && !body.classList.contains("close"));
 891 |             if (!isOpen) return false;
 892 |             block.classList.add("once-opened");
 893 |             block.classList.remove("open");
 894 |             block.classList.add("close");
 895 |             if (body) {
 896 |                 body.classList.remove("open");
 897 |                 body.classList.add("close");
 898 |             }
 899 |             var topicId = block.getAttribute("data-topic-id") || "";
 900 |             if (topicId && typeof IThemePresenter !== "undefined" &&
 901 |                 typeof IThemePresenter.setInlineHatOpen === "function") {
 902 |                 IThemePresenter.setInlineHatOpen(String(topicId), "false", "false");
 903 |             }
 904 |             return true;
 905 |         }
 906 | 
 907 |         function ensureTopicHatOverlayRendered() {
 908 |             var block = findTopicHeaderOverlayBlock();
 909 |             if (block) {
 910 |                 return showTopicHeaderOverlay();
 911 |             }
 912 |             if (typeof IThemePresenter !== "undefined" &&
 913 |                 typeof IThemePresenter.requestHatOverlayInjection === "function") {
 914 |                 IThemePresenter.requestHatOverlayInjection();
 915 |                 return false;
 916 |             }
 917 |             return false;
 918 |         }
 919 | 
 920 |         function onToolbarHeaderButtonClick() {
 921 |             if (typeof suppressThemeInfiniteScrollFor === "function") {
 922 |                 suppressThemeInfiniteScrollFor(1200);
 923 |             }
 924 |             if (isTopicHeaderOverlayVisible()) {
 925 |                 return hideTopicHeaderOverlay();
 926 |             }
 927 |             closeInlineTopicHatForToolbarOverlay();
 928 |             if (toggleThemeHatFromFixed(true)) {
 929 |                 return true;
 930 |             }
 931 |             return ensureTopicHatOverlayRendered();
 932 |         }
 933 | 
 934 |         function onToolbarHeaderButtonClickWithResult() {
 935 |             try {
 936 |                 return onToolbarHeaderButtonClick() === true;
 937 |             } catch (e) {
 938 |                 return false;
 939 |             }
 940 |         }
 941 | 
 942 |         function openTopicHeaderOverlayFromToolbarWithResult() {
 943 |             try {
 944 |                 if (typeof openTopicHeaderOverlayFromToolbar === "function") {
 945 |                     return openTopicHeaderOverlayFromToolbar() === true;
 946 |                 }
 947 |                 if (typeof showTopicHeaderOverlay === "function") {
 948 |                     return showTopicHeaderOverlay() === true;
 949 |                 }
 950 |                 if (typeof toggleThemeHatFromFixed === "function") {
 951 |                     return toggleThemeHatFromFixed(true) === true;
 952 |                 }
 953 |             } catch (e) {
 954 |                 return false;
 955 |             }
 956 |             return false;
 957 |         }
 958 | 
 959 |         function handleTopicHeaderToolbarClick() {
 960 |             return onToolbarHeaderButtonClick();
 961 |         }
 962 | 
 963 |         function toggleThemePollFromToolbar() {
 964 |             var poll = document.getElementById("theme_poll_overlay_host") || document.querySelector(".poll.poll_overlay_host");
 965 |             if (!poll) return false;
 966 |             if (typeof suppressThemeInfiniteScrollFor === "function") {
 967 |                 suppressThemeInfiniteScrollFor(1200);
 968 |             }
 969 |             var body = poll.querySelector(".hat_content");
 970 |             if (typeof cancelThemeAnchorScrollRetries === "function") {
 971 |                 cancelThemeAnchorScrollRetries();
 972 |             }
 973 |             var isClosed = poll.classList.contains("close") || (body && body.classList.contains("close"));
 974 |             if (isClosed) {
 975 |                 poll.classList.remove("close");
 976 |                 poll.classList.add("open");
 977 |                 poll.style.pointerEvents = "";
 978 |                 if (body) {
 979 |                     body.classList.remove("close");
 980 |                     body.classList.add("open");
 981 |                 }
 982 |                 document.body.classList.add("theme_poll_overlay_open");
 983 |                 if (typeof updateThemePollOverlayLayout === "function") {
 984 |                     updateThemePollOverlayLayout();
 985 |                 }
 986 |                 IThemePresenter.setPollOpen("true");
 987 |             } else {
 988 |                 poll.classList.add("once-opened");
 989 |                 poll.classList.remove("open");
 990 |                 poll.classList.add("close");
 991 |                 if (body) {
 992 |                     body.classList.remove("open");
 993 |                     body.classList.add("close");
 994 |                 }
 995 |                 document.body.classList.remove("theme_poll_overlay_open");
 996 |                 IThemePresenter.setPollOpen("false");
 997 |             }
 998 |             if (typeof scheduleThemeInfiniteScrollBootstrap === "function") {
 999 |                 scheduleThemeInfiniteScrollBootstrap(80);
1000 |             }
1001 |             return true;
1002 |         }
1003 | 
1004 |         function toggleInlineTopicHat(button, topicId) {
1005 |             var block = button;
1006 |             while (block && block.classList && !block.classList.contains("topic_hat_entry")) {
1007 |                 block = block.parentElement;
1008 |             }
1009 |             if (!block) return false;
1010 |             var body = block.querySelector(".inline_hat_content");
1011 |             if (!body) return false;
1012 |             var isClosed = block.classList.contains("close") || body.classList.contains("close");
1013 |             if (isClosed) {
1014 |                 block.classList.remove("close");
1015 |                 block.classList.add("open");
1016 |                 body.classList.remove("close");
1017 |                 body.classList.add("open");
1018 |                 IThemePresenter.setInlineHatOpen(String(topicId || block.getAttribute("data-topic-id") || ""), "true");
1019 |             } else {
1020 |                 block.classList.add("once-opened");
1021 |                 block.classList.remove("open");
1022 |                 block.classList.add("close");
1023 |                 body.classList.remove("open");
1024 |                 body.classList.add("close");
1025 |                 IThemePresenter.setInlineHatOpen(String(topicId || block.getAttribute("data-topic-id") || ""), "false");
1026 |             }
1027 |             if (typeof scheduleThemeInfiniteScrollBootstrap === "function") {
1028 |                 scheduleThemeInfiniteScrollBootstrap(80);
1029 |             }
1030 |             return true;
1031 |         }
1032 | 
1033 |         function toggleThemeHatFromFixed(forceOpen) {
1034 |             var block = document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
1035 |             if (!block) return false;
1036 |             if (typeof suppressThemeInfiniteScrollFor === "function") {
1037 |                 suppressThemeInfiniteScrollFor(1200);
1038 |             }
1039 |             var body = block.querySelector(".hat_content");
1040 |             if (!body) return false;
1041 |             var isClosed = block.classList.contains("close") || body.classList.contains("close");
1042 |             var shouldOpen = typeof forceOpen === "boolean" ? forceOpen : isClosed;
1043 |             if (shouldOpen) {
1044 |                 if (typeof openThemeHatOverlayHost === "function") {
1045 |                     openThemeHatOverlayHost(block, body);
1046 |                 } else {
1047 |                     block.classList.remove("initial_open");
1048 |                     block.classList.remove("close");
1049 |                     block.classList.add("open");
1050 |                     block.style.pointerEvents = "";
1051 |                     body.classList.remove("initial_open");
1052 |                     body.classList.remove("close");
1053 |                     body.classList.add("open");
1054 |                     if (typeof updateThemeHatOverlayLayout === "function") {
1055 |                         updateThemeHatOverlayLayout();
1056 |                     }
1057 |                     document.body.classList.add("topic_hat_overlay_open");
1058 |                     IThemePresenter.setHatOpen("true");
1059 |                 }
1060 |             } else {
1061 |                 if (typeof closeThemeHatOverlayHost === "function") {
1062 |                     closeThemeHatOverlayHost(block, body);
1063 |                 } else {
1064 |                     block.classList.remove("initial_open");
1065 |                     block.classList.remove("open");
1066 |                     block.classList.add("close");
1067 |                     body.classList.remove("initial_open");
1068 |                     body.classList.remove("open");
1069 |                     body.classList.add("close");
1070 |                     if (typeof updateThemeHatOverlayLayout === "function") {
1071 |                         updateThemeHatOverlayLayout();
1072 |                     }
1073 |                     document.body.classList.remove("topic_hat_overlay_open");
1074 |                     IThemePresenter.setHatOpen("false");
1075 |                 }
1076 |             }
1077 |             if (typeof scheduleThemeInfiniteScrollBootstrap === "function") {
1078 |                 scheduleThemeInfiniteScrollBootstrap(80);
1079 |             }
1080 |             return true;
1081 |         }
1082 | 
1083 |         function closeThemeToolbarOverlaysForNavigation(notifyNative) {
1084 |             var hat = document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
1085 |             if (hat) {
1086 |                 var hatBody = hat.querySelector(".hat_content");
1087 |                 hat.classList.remove("open");
1088 |                 hat.classList.add("close");
1089 |                 hat.style.pointerEvents = "none";
1090 |                 if (hatBody) {
1091 |                     hatBody.classList.remove("initial_open");
1092 |                     hatBody.classList.remove("open");
1093 |                     hatBody.classList.add("close");
1094 |                 }
1095 |                 document.body.classList.remove("topic_hat_overlay_open");
1096 |                 if (notifyNative !== false) {
1097 |                     IThemePresenter.setHatOpen("false");
1098 |                 }
1099 |             }
1100 | 
1101 |             var poll = document.getElementById("theme_poll_overlay_host") || document.querySelector(".poll.poll_overlay_host");
1102 |             if (poll) {
1103 |                 var pollBody = poll.querySelector(".hat_content");
1104 |                 poll.classList.remove("open");
1105 |                 poll.classList.add("close");
1106 |                 poll.style.pointerEvents = "none";
1107 |                 if (pollBody) {
1108 |                     pollBody.classList.remove("open");
1109 |                     pollBody.classList.add("close");
1110 |                 }
1111 |                 document.body.classList.remove("theme_poll_overlay_open");
1112 |                 if (notifyNative !== false) {
1113 |                     IThemePresenter.setPollOpen("false");
1114 |                 }
1115 |             }
1116 |         }
1117 | 
1118 |         installInlineTopicHeaderVisibilityObserver();
1119 |     </script>
1120 | </body>
1121 | 
1122 | </html>
1123 | 
```

### app/src/main/java/forpdateam/ru/forpda/App.kt

Bytes: 26322
SHA-256: 4158f4766bb536b44b383ce64bd7c4000082eaa2398958b00224deaffec34695
Lines: 1-617 of 617

```text
  1 | package forpdateam.ru.forpda
  2 | 
  3 | import android.Manifest
  4 | import android.app.Activity
  5 | import android.app.Application
  6 | import android.content.BroadcastReceiver
  7 | import android.content.Context
  8 | import android.content.Intent
  9 | import android.content.IntentFilter
 10 | import forpdateam.ru.forpda.diagnostic.ColdStartTracer
 11 | import android.content.SharedPreferences
 12 | import android.content.pm.PackageManager
 13 | import android.content.res.Configuration
 14 | import android.content.res.TypedArray
 15 | import android.graphics.Color
 16 | import android.graphics.drawable.Drawable
 17 | import android.graphics.drawable.VectorDrawable
 18 | import android.net.ConnectivityManager
 19 | import android.net.NetworkCapabilities
 20 | import android.os.Build
 21 | import android.os.PowerManager
 22 | import android.os.StrictMode
 23 | import android.util.DisplayMetrics
 24 | import android.util.TypedValue
 25 | import android.webkit.WebSettings
 26 | import androidx.annotation.AttrRes
 27 | import androidx.annotation.ColorInt
 28 | import androidx.annotation.DrawableRes
 29 | import androidx.annotation.RequiresApi
 30 | import androidx.appcompat.content.res.AppCompatResources
 31 | import androidx.core.app.ActivityCompat
 32 | import androidx.core.content.getSystemService
 33 | import androidx.lifecycle.DefaultLifecycleObserver
 34 | import androidx.lifecycle.LifecycleOwner
 35 | import androidx.lifecycle.ProcessLifecycleOwner
 36 | import androidx.preference.PreferenceManager
 37 | import timber.log.Timber
 38 | import forpdateam.ru.forpda.common.dpToPx
 39 | import forpdateam.ru.forpda.common.getColorFromAttr
 40 | import forpdateam.ru.forpda.common.getDrawableAttr
 41 | import forpdateam.ru.forpda.common.getDrawableResAttr
 42 | import forpdateam.ru.forpda.common.getToolBarHeight
 43 | import forpdateam.ru.forpda.common.getVecDrawable
 44 | import forpdateam.ru.forpda.common.DayNightHelper
 45 | import forpdateam.ru.forpda.common.ForPdaCoil
 46 | import forpdateam.ru.forpda.common.LocaleHelper
 47 | import forpdateam.ru.forpda.common.NetworkConnectivityTracker
 48 | import forpdateam.ru.forpda.common.Preferences
 49 | import forpdateam.ru.forpda.notifications.EventsCheckScheduler
 50 | import forpdateam.ru.forpda.notifications.NotificationsService
 51 | import forpdateam.ru.forpda.ui.fragments.TabFragment
 52 | import io.appmetrica.analytics.AppMetrica
 53 | import io.appmetrica.analytics.AppMetricaConfig
 54 | import kotlinx.coroutines.CoroutineScope
 55 | import kotlinx.coroutines.Dispatchers
 56 | import kotlinx.coroutines.SupervisorJob
 57 | import kotlinx.coroutines.cancel
 58 | import kotlinx.coroutines.delay
 59 | import kotlinx.coroutines.flow.collect
 60 | import kotlinx.coroutines.launch
 61 | import kotlinx.coroutines.withContext
 62 | import kotlinx.coroutines.flow.MutableStateFlow
 63 | import kotlinx.coroutines.flow.StateFlow
 64 | import kotlinx.coroutines.flow.asStateFlow
 65 | import java.util.concurrent.TimeUnit
 66 | import java.util.concurrent.atomic.AtomicBoolean
 67 | import java.util.concurrent.atomic.AtomicReference
 68 | import dagger.hilt.android.HiltAndroidApp
 69 | import forpdateam.ru.forpda.appupdates.AppUpdateScheduler
 70 | import forpdateam.ru.forpda.common.BatteryDebugLogger
 71 | import forpdateam.ru.forpda.model.NetworkStateProvider
 72 | import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
 73 | import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
 74 | import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
 75 | import forpdateam.ru.forpda.model.data.remote.IWebClient
 76 | import forpdateam.ru.forpda.model.repository.events.EventsRepository
 77 | import forpdateam.ru.forpda.ui.TemplateManager
 78 | import javax.inject.Inject
 79 | 
 80 | /**
 81 |  * Главный класс приложения ForPDA.
 82 |  * 
 83 |  * Улучшения по сравнению с Java-версией:
 84 |  * - Thread-safe singleton через companion object с @Volatile
 85 |  * - StateFlow вместо Observable для networkForbidden
 86 |  * - Lazy initialization зависимостей
 87 |  * - Статические поля размеров (px2, px4...) теперь делегаты - вычисляются динамически
 88 |  * - Использование Kotlin-идиоматичных конструкций
 89 |  * - Lifecycle observer для автоматической очистки ресурсов
 90 |  * - Null-safety благодаря Kotlin
 91 |  */
 92 | @HiltAndroidApp
 93 | class App : Application(), androidx.work.Configuration.Provider {
 94 | 
 95 |     // region Companion Object - Thread-safe Singleton
 96 |     companion object {
 97 |         @Volatile
 98 |         private var _instance: App? = null
 99 | 
100 |         val instance: App get() = _instance!!
101 |         private const val VERSION_HISTORY_STARTUP_DELAY_MS = 1_500L
102 |         private const val EXPEDITED_DEBOUNCE_MS = 120_000L
103 |     }
104 |     // endregion
105 | 
106 |     // region DI
107 |     @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
108 |     @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
109 |     @Inject lateinit var notificationPreferencesHolder: NotificationPreferencesHolder
110 |     @Inject lateinit var webClient: IWebClient
111 |     @Inject lateinit var templateManager: TemplateManager
112 |     @Inject lateinit var networkState: NetworkStateProvider
113 |     @Inject lateinit var appUpdateScheduler: AppUpdateScheduler
114 |     @Inject lateinit var eventsRepository: EventsRepository
115 |     // endregion
116 | 
117 |     // region Properties
118 |     private val preferencesLazy = lazy { PreferenceManager.getDefaultSharedPreferences(this) }
119 |     private val webViewFound = AtomicReference<Boolean?>(null)
120 | 
121 |     @javax.inject.Inject
122 |     lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory
123 | 
124 |     override val workManagerConfiguration: androidx.work.Configuration
125 |         get() = androidx.work.Configuration.Builder()
126 |                 .setWorkerFactory(workerFactory)
127 |                 .build()
128 | 
129 |     // StateFlow вместо SimpleObservable для networkForbidden
130 |     private val _networkForbidden = MutableStateFlow(false)
131 |     val networkForbidden: StateFlow<Boolean> = _networkForbidden.asStateFlow()
132 |     
133 |     private var networkConnectivityTracker: NetworkConnectivityTracker? = null
134 |     private var dozeReceiver: BroadcastReceiver? = null
135 |     private val permissionCallbacks = mutableListOf<Runnable>()
136 |     private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
137 |     private val appLifecycleObserver = AppLifecycleObserver()
138 | 
139 |     private val isInitialized = AtomicBoolean(false)
140 | 
141 |     /** ProcessLifecycle: true while any activity is started (visible or not). */
142 |     @Volatile
143 |     var isAppInForeground: Boolean = false
144 |         private set
145 | 
146 |     @Volatile
147 |     private var lastExpeditedEventsCheckMs = 0L
148 | 
149 |     // Loaded once on first onConfigurationChanged; never changes after the
150 |     // first call. The content is a fixed list of string resources used by the
151 |     // minitemplator templates — none of them depend on the new Configuration
152 |     // that triggered the update.
153 |     private val localizedStringMap: Map<String, String> by lazy { buildLocalizedStringMap() }
154 |     // endregion
155 | 
156 |     // region Lifecycle
157 |     init {
158 |         _instance = this
159 |     }
160 | 
161 |     override fun attachBaseContext(base: Context) {
162 |         super.attachBaseContext(LocaleHelper.onAttach(base))
163 |         ColdStartTracer.markProcessStart()
164 |     }
165 | 
166 |     override fun onCreate() {
167 |         super.onCreate()
168 |         ColdStartTracer.mark("attachBaseContext_done")
169 | 
170 |         if (isInitialized.getAndSet(true)) {
171 |             return // Предотвращаем двойную инициализацию
172 |         }
173 | 
174 |         _instance = this
175 |         val startTime = System.currentTimeMillis()
176 | 
177 |         if (BuildConfig.DEBUG) {
178 |             Timber.plant(Timber.DebugTree())
179 |         }
180 | 
181 |         setupStrictMode()
182 |         ColdStartTracer.mark("strictMode")
183 |         setupAppMetrica()
184 |         ColdStartTracer.mark("appMetrica")
185 |         setupThemeObserver()
186 |         setupVersionHistory()
187 |         setupCoil()
188 |         ColdStartTracer.mark("coil")
189 |         NotificationsService.createEventChannels(this)
190 |         setupDozeReceiver()
191 |         setupNetworkTracking()
192 |         setupBackgroundEventsCheck()
193 |         setupAppUpdateCheck()
194 |         // Material You теперь применяется per-Activity (см. MaterialYouApplier),
195 |         // т.к. активити вызывают setTheme() в onCreate и стирали бы глобальный оверлей.
196 |         ColdStartTracer.mark("init_done")
197 | 
198 |         // Lifecycle observer для очистки ресурсов
199 |         ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
200 |         BatteryDebugLogger.logState("App", "created")
201 |         
202 |         if (BuildConfig.DEBUG) {
203 |             Timber.d("TIME APP FINAL ${System.currentTimeMillis() - startTime}ms")
204 |         }
205 |     }
206 |     
207 |     override fun onConfigurationChanged(newConfig: Configuration) {
208 |         super.onConfigurationChanged(newConfig)
209 |         updateResources()
210 |     }
211 | 
212 |     override fun onTerminate() {
213 |         cleanupApplicationResources()
214 |         super.onTerminate()
215 |     }
216 |     // endregion
217 | 
218 |     // region Initialization Methods
219 |     private fun setupStrictMode() {
220 |         if (BuildConfig.DEBUG) {
221 |             StrictMode.setThreadPolicy(
222 |                 StrictMode.ThreadPolicy.Builder()
223 |                     .detectAll()
224 |                     .penaltyLog()
225 |                     .build()
226 |             )
227 |             StrictMode.setVmPolicy(
228 |                 StrictMode.VmPolicy.Builder()
229 |                     .detectLeakedClosableObjects()
230 |                     .penaltyLog()
231 |                     .build()
232 |             )
233 |         }
234 |     }
235 |     
236 |     private fun setupAppMetrica() {
237 |         // Аналитика включена только в store-флейворе (тот, что публикуется на Google Play).
238 |         // В dev/beta/parallel/stable — отключаем, чтобы лишний исходящий трафик
239 |         // и periodic-пакеты AppMetrica не жгли батарею.
240 |         val flavor = BuildConfig.FLAVOR
241 |         if (flavor != "store") {
242 |             if (BuildConfig.DEBUG) Timber.d("AppMetrica disabled: flavor=$flavor")
243 |             return
244 |         }
245 |         // Тяжёлая инициализация AppMetrica (I/O по сети, чтение конфига) уходим
246 |         // с главного потока, чтобы cold start не блокировался аналитикой.
247 |         // Установка UncaughtExceptionHandler должна быть как можно раньше — её
248 |         // оставляем на main (она дешёвая и нужна в первые мгновения).
249 |         val defaultUncaught = Thread.getDefaultUncaughtExceptionHandler()
250 |         Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
251 |             try {
252 |                 ex?.let {
253 |                     AppMetrica.reportError("uncaught:${thread.name}:${it.message}", it)
254 |                 }
255 |             } catch (_: Throwable) {
256 |                 // ignore
257 |             }
258 |             defaultUncaught?.uncaughtException(thread, ex)
259 |         }
260 |         ioSafe("AppMetrica async init") {
261 |             val config = AppMetricaConfig.newConfigBuilder("a94d9236-cdf3-4a5e-af30-d6dbffaea362").build()
262 |             AppMetrica.activate(applicationContext, config)
263 |             AppMetrica.enableActivityAutoTracking(this@App)
264 |         }
265 |     }
266 | 
267 |     private fun ioSafe(tag: String, block: suspend () -> Unit) =
268 |         appScope.launch(Dispatchers.IO) {
269 |             runCatching { block() }.onFailure { Timber.w(it, tag) }
270 |         }
271 |     
272 |     
273 |     private fun setupThemeObserver() {
274 |         appScope.launch {
275 |             mainPreferencesHolder
276 |                 .observeThemeModeFlow()
277 |                 .collect { DayNightHelper.applyTheme(it) }
278 |         }
279 |     }
280 |     
281 |     private fun setupVersionHistory() {
282 |         appScope.launch {
283 |             delay(VERSION_HISTORY_STARTUP_DELAY_MS)
284 |             // The IO block uses Timber.e (more serious) and reports errors to
285 |             // AppMetrica outside debug builds — that bespoke error path is why
286 |             // it is not folded into ioSafe(). Keep the call site local; the
287 |             // try/reporting structure must stay visible.
288 |             withContext(Dispatchers.IO) {
289 |                 runCatching {
290 |                     val inputHistory = otherPreferencesHolder.getAppVersionsHistory()
291 |                     val history = inputHistory.split(";").filter { it.isNotEmpty() }.toMutableList()
292 | 
293 |                     var lastVNum = 0
294 |                     var disorder = false
295 |                     for (version in history) {
296 |                         val vNum = version.toIntOrNull() ?: continue
297 |                         if (vNum < lastVNum) {
298 |                             disorder = true
299 |                         }
300 |                         lastVNum = vNum
301 |                     }
302 | 
303 |                     val currentVersion = BuildConfig.VERSION_CODE
304 |                     if (lastVNum < currentVersion) {
305 |                         history.add(currentVersion.toString())
306 |                         otherPreferencesHolder.setAppVersionsHistory(history.joinToString(";"))
307 |                     }
308 | 
309 |                     check(!disorder) { "Нарушение порядка версий!" }
310 |                 }.onFailure { ex ->
311 |                     Timber.e(ex, "Version history error")
312 |                     if (!BuildConfig.DEBUG) {
313 |                         runCatching { AppMetrica.reportError("VERSIONS_HISTORY", ex) }
314 |                     }
315 |                 }
316 |             }
317 |         }
318 |     }
319 |     
320 |     private fun setupCoil() {
321 |         ForPdaCoil.init(this, webClient)
322 |     }
323 |     
324 |     private fun updateResources() {
325 |         if (BuildConfig.DEBUG) {
326 |             Timber.d("updateResources")
327 |         }
328 |         // The string map is fully deterministic (no dependency on the new
329 |         // Configuration that triggered this call): build it once on the first
330 |         // onConfigurationChanged and reuse the cached snapshot thereafter.
331 |         templateManager.setStaticStrings(localizedStringMap)
332 |     }
333 | 
334 |     private fun buildLocalizedStringMap(): Map<String, String> = mapOf(
335 |         "res_s_poll_title" to getString(R.string.res_s_poll_title),
336 |         "res_s_poll_all_votes_count" to getString(R.string.res_s_poll_all_votes_count),
337 |         "res_s_poll_vote_btn" to getString(R.string.res_s_poll_vote_btn),
338 |         "res_s_poll_results_btn" to getString(R.string.res_s_poll_results_btn),
339 |         "res_s_poll_show_btn" to getString(R.string.res_s_poll_show_btn),
340 |         "res_s_poll_unavailable" to getString(R.string.res_s_poll_unavailable),
341 |         "res_s_search_post_btn" to getString(R.string.res_s_search_post_btn),
342 |         "res_s_hat" to getString(R.string.res_s_hat),
343 |         "res_s_avatar" to getString(R.string.res_s_avatar),
344 |         "res_s_reputation" to getString(R.string.res_s_reputation),
345 |         "res_s_post_rating" to getString(R.string.res_s_post_rating),
346 |         "res_s_group" to getString(R.string.res_s_group),
347 |         "res_s_menu" to getString(R.string.res_s_menu),
348 |         "res_s_report" to getString(R.string.res_s_report),
349 |         "res_s_reply" to getString(R.string.res_s_reply),
350 |         "res_s_quote" to getString(R.string.res_s_quote),
351 |         "res_s_vote_good" to getString(R.string.res_s_vote_good),
352 |         "res_s_vote_bad" to getString(R.string.res_s_vote_bad),
353 |         "res_s_vote_post_good" to getString(R.string.vote_post_good),
354 |         "res_s_vote_post_bad" to getString(R.string.vote_post_bad),
355 |         "res_s_delete" to getString(R.string.res_s_delete),
356 |         "res_s_edit" to getString(R.string.res_s_edit),
357 |         "res_s_first" to getString(R.string.res_s_first),
358 |         "res_s_prev" to getString(R.string.res_s_prev),
359 |         "res_s_select_desc" to getString(R.string.res_s_select_desc),
360 |         "res_s_select" to getString(R.string.res_s_select),
361 |         "res_s_next" to getString(R.string.res_s_next),
362 |         "res_s_last" to getString(R.string.res_s_last),
363 |         "res_s_comments" to getString(R.string.res_s_comments),
364 |         "res_s_comments_do_swipe" to getString(R.string.res_s_comments_do_swipe),
365 |         "res_s_forum_blacklist_post_hidden" to getString(R.string.forum_blacklist_post_hidden),
366 |         "res_s_forum_blacklist_posts_hidden" to getString(R.string.forum_blacklist_posts_hidden),
367 |         "news_show_comments" to getString(R.string.news_show_comments),
368 |         "news_inline_comments_description" to getString(R.string.news_inline_comments_description),
369 |         "retry" to getString(R.string.retry),
370 |     )
371 |     
372 |     private fun setupDozeReceiver() {
373 |         if (dozeReceiver != null) return
374 |         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
375 |             val receiver = object : BroadcastReceiver() {
376 |                 @RequiresApi(Build.VERSION_CODES.M)
377 |                 override fun onReceive(context: Context?, intent: Intent?) {
378 |                     Timber.d("DOZE ON RECEIVE $intent")
379 | 
380 |                     val pm = context?.getSystemService<PowerManager>() ?: return
381 | 
382 |                     if (pm.isDeviceIdleMode) {
383 |                         Timber.d("DOZE MODE ENABLYA")
384 |                     } else {
385 |                         Timber.d("DOZE MODE DISABLYA")
386 |                         BatteryDebugLogger.logState("App", "deviceIdleExit", "schedule background check soon")
387 |                         scheduleBackgroundEventsCheckSoon()
388 |                     }
389 |                 }
390 |             }
391 | 
392 |             val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
393 |             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
394 |                 registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
395 |             } else {
396 |                 registerReceiver(receiver, filter)
397 |             }
398 |             dozeReceiver = receiver
399 |         }
400 |     }
401 |     
402 |     
403 |     private fun setupNetworkTracking() {
404 |         networkConnectivityTracker?.stop()
405 |         networkConnectivityTracker = NetworkConnectivityTracker(this, networkState).apply {
406 |             start()
407 |         }
408 |     }
409 | 
410 |     /**
411 |      * Планирует фоновый OneTimeWorkRequest для опроса событий (когда сервис недоступен).
412 |      * Реактивно реагирует на изменение настроек: главный переключатель, фон, каналы.
413 |      */
414 |     private fun setupBackgroundEventsCheck() {
415 |         // Перепланировать при старте под текущие настройки.
416 |         rescheduleEventsCheckWorker()
417 |         appScope.launch {
418 |             kotlinx.coroutines.flow.combine(
419 |                     notificationPreferencesHolder.mainEnabledFlow(),
420 |                     notificationPreferencesHolder.bgCheckEnabledFlow(),
421 |                     notificationPreferencesHolder.favEnabledFlow(),
422 |                     notificationPreferencesHolder.qmsEnabledFlow(),
423 |                     notificationPreferencesHolder.mentionsEnabledFlow(),
424 |             ) { _, _, _, _, _ -> }.collect {
425 |                 rescheduleEventsCheckWorker()
426 |             }
427 |         }
428 |     }
429 | 
430 |     private fun rescheduleEventsCheckWorker(initialDelayMs: Long? = null) {
431 |         if (!canScheduleBackgroundEventsCheck()) {
432 |             EventsCheckScheduler.cancelAll(this)
433 |             if (!notificationPreferencesHolder.wantsPushNotifications()) {
434 |                 eventsRepository.cancelAll()
435 |                 Timber.d("EventsCheckWorker: cancelled (notifications off)")
436 |                 BatteryDebugLogger.logState("EventsCheckWorker", "cancelled", "notifications off")
437 |             } else {
438 |                 Timber.d("EventsCheckWorker: cancelled (background check disabled)")
439 |                 BatteryDebugLogger.logState("EventsCheckWorker", "cancelled", "bgCheck disabled")
440 |             }
441 |             return
442 |         }
443 |         val delayMs = initialDelayMs ?: NotificationsService.computeAdaptiveIntervalMs(this, isAppInForeground)
444 |         EventsCheckScheduler.schedulePeriodic(this, delayMs)
445 |     }
446 | 
447 |     fun canScheduleBackgroundEventsCheck(): Boolean {
448 |         return notificationPreferencesHolder.wantsPushNotifications() &&
449 |                 notificationPreferencesHolder.getBgCheckEnabled()
450 |     }
451 | 
452 |     /**
453 |      * Expedited WorkManager при росте счётчиков в шапке (процесс жив, приложение в фоне).
454 |      */
455 |     fun scheduleExpeditedEventsCheckIfNeeded() {
456 |         if (!canScheduleBackgroundEventsCheck()) return
457 |         if (isAppInForeground) return
458 |         if (eventsRepository.isForegroundRealtimeActive()) return
459 |         val now = System.currentTimeMillis()
460 |         if (now - lastExpeditedEventsCheckMs < EXPEDITED_DEBOUNCE_MS) return
461 |         lastExpeditedEventsCheckMs = now
462 |         EventsCheckScheduler.scheduleExpedited(this)
463 |     }
464 | 
465 |     /**
466 |      * Планирует ближайший фоновый опрос (например, после выхода из Doze).
467 |      */
468 |     fun scheduleBackgroundEventsCheckSoon(delayMs: Long = 30_000L) {
469 |         if (!canScheduleBackgroundEventsCheck()) return
470 |         rescheduleEventsCheckWorker(initialDelayMs = delayMs.coerceAtLeast(5_000L))
471 |     }
472 | 
473 |     private fun setupAppUpdateCheck() {
474 |         appUpdateScheduler.reschedule()
475 |     }
476 | 
477 |     // endregion
478 | 
479 |     // region Public Methods
480 |     /**
481 |      * Проверяет, доступен ли WebView.
482 |      */
483 |     fun isWebViewFound(context: Context): Boolean {
484 |         return webViewFound.get() ?: try {
485 |             WebSettings.getDefaultUserAgent(context)
486 |             webViewFound.set(true)
487 |             true
488 |         } catch (_: Exception) {
489 |             webViewFound.set(false)
490 |             false
491 |         }
492 |     }
493 |     
494 |     /**
495 |      * Уведомляет слушателей о запрете сети.
496 |      */
497 |     fun notifyForbidden(isForbidden: Boolean) {
498 |         _networkForbidden.value = isForbidden
499 |     }
500 |     
501 |     /**
502 |      * Проверяет разрешение на хранение и выполняет callback если оно есть.
503 |      */
504 |     fun checkStoragePermission(runnable: Runnable?, activity: Activity?) {
505 |         if (runnable == null || activity == null) return
506 |         
507 |         // WRITE_EXTERNAL_STORAGE требуется только для записи в public Downloads до Android 10 (API 29).
508 |         // Начиная с Android 10 используем MediaStore и разрешение не нужно.
509 |         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
510 |             Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
511 |             if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
512 |                 != PackageManager.PERMISSION_GRANTED) {
513 |                 ActivityCompat.requestPermissions(
514 |                     activity,
515 |                     arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
516 |                     TabFragment.REQUEST_STORAGE
517 |                 )
518 |                 permissionCallbacks.add(runnable)
519 |                 return
520 |             }
521 |         }
522 |         runnable.run()
523 |     }
524 |     
525 |     /**
526 |      * Вызывается при результате запроса разрешений.
527 |      * PLS CALL THIS IN ALL ACTIVITIES
528 |      */
529 |     fun onRequestPermissionsResult(
530 |         requestCode: Int,
531 |         permissions: Array<out String>,
532 |         grantResults: IntArray
533 |     ) {
534 |         permissions.indices.forEach { i ->
535 |             if (permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE && 
536 |                 grantResults[i] == PackageManager.PERMISSION_GRANTED) {
537 |                 permissionCallbacks.forEach { runnable ->
538 |                     try {
539 |                         runnable.run()
540 |                     } catch (_: Exception) {
541 |                         // ignore
542 |                     }
543 |                 }
544 |                 return@forEach
545 |             }
546 |         }
547 |         permissionCallbacks.clear()
548 |     }
549 |     
550 |     /**
551 |      * Получает текущую активность без использования reflection.
552 |      * Рекомендуется передавать Activity явно через параметры.
553 |      */
554 |     fun getActivity(): Activity? {
555 |         // Используем ActivityManager для получения текущей активити
556 |         // Это более безопасный способ чем reflection
557 |         return try {
558 |             val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
559 |             val appTasks = activityManager?.appTasks
560 |             appTasks?.firstOrNull()?.taskInfo?.topActivity?.let {
561 |                 // Возвращаем null так как мы не можем получить instance Activity
562 |                 // Этот метод deprecated, используйте явную передачу Activity
563 |                 null
564 |             }
565 |         } catch (_: Exception) {
566 |             null
567 |         }
568 |     }
569 |     // endregion
570 | 
571 |     // region Private Methods
572 |     private fun getPreferencesInternal(): SharedPreferences = preferencesLazy.value
573 | 
574 |     private fun cleanupApplicationResources() {
575 |         networkConnectivityTracker?.stop()
576 |         networkConnectivityTracker = null
577 |         dozeReceiver?.let { receiver ->
578 |             runCatching { unregisterReceiver(receiver) }
579 |                 .onFailure { Timber.w(it, "Doze receiver unregister failed") }
580 |         }
581 |         dozeReceiver = null
582 |         ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
583 |         appScope.cancel()
584 |         // Production call sites of App.instance are being migrated to Hilt
585 |         // @ApplicationContext injection (see S-03 in docs/BACKLOG_DEFERRED.md).
586 |         // Until that lands, keep the field live in release builds so the
587 |         // remaining callers do not NPE. In debug, allow the nulling so tests
588 |         // can reset the application between scenarios.
589 |         if (BuildConfig.DEBUG) {
590 |             _instance = null
591 |         }
592 |     }
593 |     // endregion
594 | 
595 |     // region Lifecycle Observer
596 |     private inner class AppLifecycleObserver : DefaultLifecycleObserver {
597 |         override fun onStop(owner: LifecycleOwner) {
598 |             isAppInForeground = false
599 |             BatteryDebugLogger.logState("AppLifecycle", "background")
600 |             eventsRepository.setForegroundRealtimeEnabled(false, "process_stop")
601 |             NotificationsService.stopIfRunning(this@App)
602 |         }
603 | 
604 |         override fun onStart(owner: LifecycleOwner) {
605 |             isAppInForeground = true
606 |             BatteryDebugLogger.logState("AppLifecycle", "foreground")
607 |             NotificationsService.dismissLegacyForegroundStub(this@App)
608 |             eventsRepository.setForegroundRealtimeEnabled(true, "process_start")
609 |         }
610 |         
611 |         override fun onDestroy(owner: LifecycleOwner) {
612 |             cleanupApplicationResources()
613 |         }
614 |     }
615 |     // endregion
616 | }
617 | 
```

### app/src/main/java/forpdateam/ru/forpda/client/Client.kt

Bytes: 20294
SHA-256: ec5847706b74ad87ef2cd8db8dc37a999904888518a1dd48a184e809dd79c09f
Lines: 1-466 of 466

```text
  1 | package forpdateam.ru.forpda.client
  2 | 
  3 | import android.content.Context
  4 | import android.net.ConnectivityManager
  5 | import android.net.Network
  6 | import android.net.NetworkCapabilities
  7 | import android.net.NetworkRequest as AndroidNetworkRequest
  8 | import androidx.preference.PreferenceManager
  9 | import timber.log.Timber
 10 | import forpdateam.ru.forpda.BuildConfig
 11 | import forpdateam.ru.forpda.common.PrivateHeaders
 12 | import forpdateam.ru.forpda.entity.common.AuthData
 13 | import forpdateam.ru.forpda.entity.common.MessageCounters
 14 | import forpdateam.ru.forpda.notifications.CounterGrowthDetector
 15 | import forpdateam.ru.forpda.model.AuthHolder
 16 | import forpdateam.ru.forpda.model.CountersHolder
 17 | import forpdateam.ru.forpda.model.data.remote.IWebClient
 18 | import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
 19 | import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
 20 | import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
 21 | import forpdateam.ru.forpda.client.interceptors.AuthInterceptor
 22 | import forpdateam.ru.forpda.client.interceptors.CacheControlInterceptor
 23 | import forpdateam.ru.forpda.client.interceptors.ErrorInterceptor
 24 | import forpdateam.ru.forpda.client.interceptors.ImageLoadingInterceptor
 25 | import forpdateam.ru.forpda.client.interceptors.RedirectFragmentInterceptor
 26 | import okhttp3.Cache
 27 | import okhttp3.Cookie
 28 | import okhttp3.CookieJar
 29 | import okhttp3.ConnectionPool
 30 | import okhttp3.FormBody
 31 | import okhttp3.HttpUrl
 32 | import okhttp3.MediaType.Companion.toMediaTypeOrNull
 33 | import okhttp3.MultipartBody
 34 | import okhttp3.OkHttpClient
 35 | import okhttp3.Protocol
 36 | import okhttp3.Request
 37 | import okhttp3.RequestBody.Companion.toRequestBody
 38 | import okhttp3.Response
 39 | import okhttp3.WebSocket
 40 | import okhttp3.WebSocketListener
 41 | import okhttp3.brotli.BrotliInterceptor
 42 | import java.io.File
 43 | import java.io.IOException
 44 | import java.util.concurrent.TimeUnit
 45 | import java.util.concurrent.atomic.AtomicReference
 46 | 
 47 | /**
 48 |  * HTTP клиент для работы с API 4pda.
 49 |  * 
 50 |  * Улучшения по сравнению с Java-версией:
 51 |  * - Kotlin null-safety
 52 |  * - Lazy инициализация OkHttp клиентов
 53 |  * - Упрощённая работа с Cookie через MutableMap
 54 |  * - Использование OkHttp 4.x API (MediaType.Companion и т.д.)
 55 |  * - Улучшенная читаемость через when/if expressions
 56 |  */
 57 | class Client(
 58 |     private val context: Context,
 59 |     private val authHolder: AuthHolder,
 60 |     private val countersHolder: CountersHolder
 61 | ) : IWebClient {
 62 | 
 63 |     /**
 64 |      * Проверяет доступность сети перед запросом.
 65 |      * @throws IOException если нет подключения к интернету
 66 |      */
 67 |     /**
 68 |      * Must match [forpdateam.ru.forpda.model.system.AppNetworkState]: INTERNET only.
 69 |      * Requiring [NetworkCapabilities.NET_CAPABILITY_VALIDATED] caused false "no network"
 70 |      * errors on Wi‑Fi while HTTP would still work (validation lags or captive portals).
 71 |      */
 72 |     private fun checkNetworkAvailable() {
 73 |         val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
 74 |         val isAvailable = connectivityManager?.let { cm ->
 75 |             val network = cm.activeNetwork ?: return@let false
 76 |             val capabilities = cm.getNetworkCapabilities(network) ?: return@let false
 77 |             capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
 78 |         } ?: false
 79 | 
 80 |         if (!isAvailable) {
 81 |             // Do not block HTTP: ConnectivityManager often lags behind real reachability on Wi‑Fi/VPN.
 82 |             Timber.w("ConnectivityManager reports no INTERNET; proceeding with request anyway")
 83 |         }
 84 |     }
 85 | 
 86 |     companion object {
 87 |         private val LOG_TAG = Client::class.java.simpleName
 88 | 
 89 |         /** Актуальный мобильный Chrome на Android — ближе к WebView и к типичному браузеру пользователя. */
 90 |         private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
 91 |         private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
 92 |         private const val MOBILE_COOKIE_NAME = "ngx_mb"
 93 |         private const val DESKTOP_MOBILE_COOKIE_VALUE = "0"
 94 |         private const val EVENT_WS_URL = "wss://app.4pda.to/ws/"
 95 |     }
 96 | 
 97 |     // region Properties
 98 |     private val cookieManager = CookieManager(context, authHolder)
 99 |     private val authKey: AtomicReference<String> = AtomicReference("0")
100 |     // endregion
101 | 
102 |     // region Initialization
103 |     init {
104 |         // Загружаем auth_key
105 |         val preferences = PreferenceManager.getDefaultSharedPreferences(context)
106 |         authKey.set(preferences.getString("auth_key", "0") ?: "0")
107 |     }
108 |     // endregion
109 | 
110 |     // region OkHttp Clients (Lazy initialization)
111 |     private val cookieJar: CookieJar get() = cookieManager.cookieJar
112 | 
113 |     private val cachedDns = CachedDns()
114 | 
115 |     init {
116 |         // P-06: when the active network changes (WiFi ↔ Mobile, VPN, etc.)
117 |         // the cached DNS entries for the previous interface may no longer
118 |         // be reachable. Register a NetworkCallback that drops the cache on
119 |         // every change. Registered on the application context and never
120 |         // unregistered — leaks are bounded by the singleton process lifetime.
121 |         val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
122 |         if (connectivityManager != null) {
123 |             val request = AndroidNetworkRequest.Builder()
124 |                 .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
125 |                 .build()
126 |             val callback = object : ConnectivityManager.NetworkCallback() {
127 |                 override fun onAvailable(network: Network) {
128 |                     cachedDns.clearCache()
129 |                     if (BuildConfig.DEBUG) Timber.d("Network available: cleared DNS cache")
130 |                 }
131 |                 override fun onLost(network: Network) {
132 |                     cachedDns.clearCache()
133 |                     if (BuildConfig.DEBUG) Timber.d("Network lost: cleared DNS cache")
134 |                 }
135 |                 override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
136 |                     // Validation status flips when an internet-validated link
137 |                     // becomes a captive-portal/unvalidated one. Cheap to clear.
138 |                     if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
139 |                         cachedDns.clearCache()
140 |                     }
141 |                 }
142 |             }
143 |             runCatching { connectivityManager.registerNetworkCallback(request, callback) }
144 |                 .onFailure {
145 |                     // Some sandboxed/emulator contexts disallow the callback API;
146 |                     // fall back to no-op. CachedDns will still expire entries
147 |                     // after CACHE_TTL_MS.
148 |                     if (BuildConfig.DEBUG) {
149 |                         Timber.w(it, "registerNetworkCallback failed; DNS cache won't auto-clear on network change")
150 |                     }
151 |                 }
152 |         }
153 |     }
154 | 
155 |     private val cacheDir by lazy { File(context.cacheDir, "http_cache").apply { mkdirs() } }
156 |     // 50 MB: matches the article/QMS-heavy nature of the app. 10 MB was too small
157 |     // for repeated navigation within the same theme/section; OkHttp would mostly
158 |     // hit the network even on identical URLs.
159 |     private val httpCache by lazy { Cache(cacheDir, 50L * 1024 * 1024) }
160 | 
161 |     private val client: OkHttpClient by lazy {
162 |         OkHttpClient.Builder()
163 |             .connectTimeout(20, TimeUnit.SECONDS)
164 |             .writeTimeout(20, TimeUnit.SECONDS)
165 |             .readTimeout(30, TimeUnit.SECONDS)
166 |             .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
167 |             .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
168 |             .dns(cachedDns)
169 |             .cookieJar(cookieJar)
170 |             .cache(httpCache)
171 |             .addInterceptor(AuthInterceptor())
172 |             .addInterceptor(ImageLoadingInterceptor { url -> cookieJar.loadForRequest(url).isNotEmpty() })
173 |             .addInterceptor(ErrorInterceptor())
174 |             .addInterceptor(BrotliInterceptor)
175 |             .addNetworkInterceptor(RedirectFragmentInterceptor())
176 |             .addNetworkInterceptor(CacheControlInterceptor())
177 |             .build()
178 |     }
179 | 
180 |     private val desktopClient: OkHttpClient by lazy {
181 |         client.newBuilder()
182 |             .connectTimeout(20, TimeUnit.SECONDS)
183 |             .writeTimeout(20, TimeUnit.SECONDS)
184 |             .readTimeout(20, TimeUnit.SECONDS)
185 |             .cookieJar(object : CookieJar {
186 |                 override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
187 |                     cookieJar.saveFromResponse(url, cookies)
188 |                 }
189 | 
190 |                 override fun loadForRequest(url: HttpUrl): List<Cookie> {
191 |                     val cookies = cookieJar.loadForRequest(url)
192 |                         .filterNot { it.name.equals(MOBILE_COOKIE_NAME, ignoreCase = true) }
193 |                         .toMutableList()
194 |                     if (url.host.contains("4pda", ignoreCase = true)) {
195 |                         cookies += Cookie.Builder()
196 |                             .name(MOBILE_COOKIE_NAME)
197 |                             .value(DESKTOP_MOBILE_COOKIE_VALUE)
198 |                             // OkHttp 4+ rejects domains with a leading dot (IllegalArgumentException)
199 |                             .domain("4pda.to")
200 |                             .path("/")
201 |                             .build()
202 |                     }
203 |                     return cookies
204 |                 }
205 |             })
206 |             .build()
207 |     }
208 | 
209 |     private val webSocketClient: OkHttpClient by lazy {
210 |         OkHttpClient.Builder()
211 |             .connectTimeout(30, TimeUnit.SECONDS)
212 |             .writeTimeout(30, TimeUnit.SECONDS)
213 |             .readTimeout(30, TimeUnit.SECONDS)
214 |             .pingInterval(45, TimeUnit.SECONDS)
215 |             .retryOnConnectionFailure(true)
216 |             .cookieJar(cookieJar)
217 |             .addInterceptor(AuthInterceptor())
218 |             .build()
219 |     }
220 |     // endregion
221 | 
222 |     // region IWebClient Implementation
223 |     override fun getAuthKey(): String = authKey.get()
224 | 
225 |     override fun getClientCookies(): Map<String, Cookie> = cookieManager.getCookies()
226 | 
227 |     override fun clearCookies() {
228 |         cookieManager.clearCookies()
229 |     }
230 | 
231 |     /** Общий клиент для HTTP и загрузки изображений (Coil), чтобы cookies совпадали. */
232 |     fun getHttpClient(): OkHttpClient = client
233 | 
234 |     @Throws(Exception::class)
235 |     override fun get(url: String): NetworkResponse {
236 |         return request(NetworkRequest.Builder().url(url).build())
237 |     }
238 | 
239 |     @Throws(Exception::class)
240 |     override fun request(request: NetworkRequest): NetworkResponse {
241 |         return request(request, client, null)
242 |     }
243 | 
244 |     @Throws(Exception::class)
245 |     override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse {
246 |         return request(request, client, progressListener)
247 |     }
248 | 
249 |     @Throws(Exception::class)
250 |     override fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse {
251 |         val desktopRequest = if (request.headers?.keys?.any { it.equals("User-Agent", ignoreCase = true) } == true) {
252 |             request
253 |         } else {
254 |             NetworkRequest.Builder()
255 |                 .copyFrom(request)
256 |                 .addHeader("User-Agent", DESKTOP_USER_AGENT)
257 |                 .build()
258 |         }
259 |         return request(desktopRequest, desktopClient, null)
260 |     }
261 | 
262 |     @Throws(Exception::class)
263 |     fun request(
264 |         request: NetworkRequest,
265 |         client: OkHttpClient,
266 |         uploadProgressListener: IWebClient.ProgressListener?
267 |     ): NetworkResponse {
268 |         // Проверяем сеть перед запросом — быстрая проверка без ожидания таймаута
269 |         checkNetworkAvailable()
270 |         
271 |         val redirectFragment = RedirectFragmentInterceptor.State()
272 |         val requestBuilder = prepareRequest(request, uploadProgressListener)
273 |             .tag(RedirectFragmentInterceptor.State::class.java, redirectFragment)
274 |         val response = NetworkResponse(request.url)
275 |         var okHttpResponse: Response? = null
276 |         
277 |         try {
278 |             okHttpResponse = client.newCall(requestBuilder.build()).execute()
279 | 
280 |             response.code = okHttpResponse.code
281 |             response.message = okHttpResponse.message
282 |             response.redirect = okHttpResponse.request.url.toString()
283 |             response.locationHeader = okHttpResponse.header("Location")
284 |             response.redirectFragment = redirectFragment.lastFragment.get()
285 | 
286 |             if (!request.isWithoutBody) {
287 |                 val bodyString = okHttpResponse.body?.string() ?: ""
288 |                 response.body = bodyString
289 |                 if (!request.skipCounterUpdate) {
290 |                     getCounts(bodyString)
291 |                 }
292 |                 // Для тем, которые были перенесены/удалены, сервер нередко отдаёт 404 с HTML-заглушкой,
293 |                 // которая может совпасть с паттерном форум-ошибки. В этом случае нам важнее вернуть HTML наверх,
294 |                 // чтобы ThemeApi смог извлечь канонический showtopic и повторить запрос.
295 |                 if (okHttpResponse.code != 404) {
296 |                     checkForumErrors(bodyString)
297 |                 }
298 |             }
299 | 
300 |             if (BuildConfig.DEBUG) {
301 |                 Timber.d("Response: $response")
302 |             }
303 |         } finally {
304 |             okHttpResponse?.close()
305 |         }
306 |         
307 |         return response
308 |     }
309 | 
310 |     override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket {
311 |         val request = Request.Builder()
312 |             .url(EVENT_WS_URL)
313 |             .build()
314 |         return webSocketClient.newWebSocket(request, webSocketListener)
315 |     }
316 |     // endregion
317 | 
318 |     // region Request Preparation
319 |     private fun prepareRequest(
320 |         request: NetworkRequest,
321 |         uploadProgressListener: IWebClient.ProgressListener?
322 |     ): Request.Builder {
323 |         var url = request.url
324 |         
325 |         // Исправляем протокол
326 |         if (url.startsWith("//")) {
327 |             url = "https:$url"
328 |         }
329 |         
330 |         if (BuildConfig.DEBUG) {
331 |             Timber.d("Request url ${request.url}")
332 |         }
333 | 
334 |         val requestBuilder = Request.Builder()
335 |             .url(url)
336 | 
337 |         // Добавляем пользовательские заголовки
338 |         request.headers?.forEach { (key, value) ->
339 |             if (BuildConfig.DEBUG) {
340 |                 val logValue = if (PrivateHeaders.LIST.contains(key)) "private" else value
341 |                 Timber.d("Header $key : $logValue")
342 |             }
343 |             requestBuilder.header(key, value)
344 |         }
345 | 
346 |         // Обрабатываем форму или файл
347 |         if (request.rawBody != null) {
348 |             requestBuilder.post(
349 |                 request.rawBody.toRequestBody(request.rawBodyContentType.toMediaTypeOrNull())
350 |             )
351 |         } else if (request.formHeaders != null || request.file != null) {
352 |             if (BuildConfig.DEBUG) {
353 |                 Timber.d("Multipart ${request.isMultipartForm}")
354 |                 request.formHeaders?.forEach { (key, value) ->
355 |                     val logValue = if (PrivateHeaders.LIST.contains(key)) "private" else value
356 |                     Timber.d("Form header $key : $logValue")
357 |                 }
358 |                 request.file?.let {
359 |                     Timber.d("Form file $it")
360 |                 }
361 |             }
362 | 
363 |             if (!request.isMultipartForm) {
364 |                 // Обычная форма
365 |                 request.formHeaders?.let { formHeaders ->
366 |                     val formBuilder = FormBody.Builder()
367 |                     formHeaders.forEach { (key, value) ->
368 |                         if (request.encodedFormHeaders?.contains(key) == true) {
369 |                             formBuilder.addEncoded(key, value)
370 |                         } else {
371 |                             formBuilder.add(key, value)
372 |                         }
373 |                     }
374 |                     requestBuilder.post(formBuilder.build())
375 |                 }
376 |             } else {
377 |                 // Multipart форма
378 |                 val multipartBuilder = MultipartBody.Builder()
379 |                     .setType(MultipartBody.FORM)
380 | 
381 |                 request.formHeaders?.forEach { (key, value) ->
382 |                     multipartBuilder.addFormDataPart(key, value)
383 |                 }
384 | 
385 |                 request.file?.let { file ->
386 |                     val mediaType = file.mimeType.toMediaTypeOrNull()
387 |                     val requestBody = file.openStream().toRequestBody(mediaType, file.fileSize)
388 |                     multipartBuilder.addFormDataPart(
389 |                         file.requestName ?: "file",
390 |                         file.fileName,
391 |                         requestBody
392 |                     )
393 |                 }
394 | 
395 |                 val multipartBody = multipartBuilder.build()
396 |                 val body = if (uploadProgressListener != null) {
397 |                     ProgressRequestBody(multipartBody, uploadProgressListener)
398 |                 } else {
399 |                     multipartBody
400 |                 }
401 |                 requestBuilder.post(body)
402 |             }
403 |         }
404 | 
405 |         return requestBuilder
406 |     }
407 |     // endregion
408 | 
409 |     // region Response Processing
410 |     @Throws(Exception::class)
411 |     private fun checkForumErrors(res: String) {
412 |         val errorMatcher = IWebClient.errorPattern.matcher(res)
413 |         if (errorMatcher.find()) {
414 |             val errorText = errorMatcher.group(1)?.let { ApiUtils.fromHtml(it) } ?: ""
415 |             throw OnlyShowException(errorText)
416 |         }
417 |     }
418 | 
419 |     private fun getCounts(res: String) {
420 |         fun findFirstInt(pattern: java.util.regex.Pattern): Int? {
421 |             val m = pattern.matcher(res)
422 |             if (!m.find()) return null
423 |             // Берём первое не-null значение из групп (некоторые fallback-паттерны используют альтернативы).
424 |             for (i in 1..m.groupCount()) {
425 |                 val v = m.group(i)?.toIntOrNull()
426 |                 if (v != null) return v
427 |             }
428 |             return null
429 |         }
430 | 
431 |         val counters = countersHolder.get()
432 |         val before = MessageCounters().apply {
433 |             qms = counters.qms
434 |             favorites = counters.favorites
435 |             mentions = counters.mentions
436 |         }
437 |         var changed = false
438 | 
439 |         // Старый «единый» паттерн — самый точный, если совпал.
440 |         val countsMatcher = IWebClient.countsPattern.matcher(res)
441 |         if (countsMatcher.find()) {
442 |             runCatching {
443 |                 countsMatcher.group(1)?.toIntOrNull()?.also { counters.mentions = it; changed = true }
444 |                 countsMatcher.group(2)?.toIntOrNull()?.also { counters.favorites = it; changed = true }
445 |                 countsMatcher.group(3)?.toIntOrNull()?.also { counters.qms = it; changed = true }
446 |             }.onFailure {
447 |                 if (BuildConfig.DEBUG) Timber.d("getCounts parse failed (legacy)")
448 |             }
449 |         } else {
450 |             // Fallback: шапка форума регулярно меняется, поэтому извлекаем счётчики по отдельности.
451 |             findFirstInt(IWebClient.mentionsCountPattern)?.also { counters.mentions = it; changed = true }
452 |             findFirstInt(IWebClient.favoritesCountPattern)?.also { counters.favorites = it; changed = true }
453 |             findFirstInt(IWebClient.qmsCountPattern)?.also { counters.qms = it; changed = true }
454 |         }
455 | 
456 |         if (changed) {
457 |             countersHolder.set(counters, source = "index_header")
458 |             if (CounterGrowthDetector.hasGrowth(before, counters)) {
459 |                 (context.applicationContext as? forpdateam.ru.forpda.App)
460 |                         ?.scheduleExpeditedEventsCheckIfNeeded()
461 |             }
462 |         }
463 |     }
464 |     // endregion
465 | }
466 | 
```

### app/src/main/java/forpdateam/ru/forpda/client/interceptors/CacheControlInterceptor.kt

Bytes: 3282
SHA-256: dc39c92debc0c0f1da8ad8d88d86810d96515cffc17cff793843c6c31549e667
Lines: 1-79 of 79

```text
 1 | package forpdateam.ru.forpda.client.interceptors
 2 | 
 3 | import okhttp3.Interceptor
 4 | import okhttp3.Response
 5 | 
 6 | /**
 7 |  * Network-side interceptor that assigns a sensible `Cache-Control: max-age=N` to
 8 |  * responses for public assets (CSS, JS, fonts) when the server did not set one.
 9 |  *
10 |  * Why this exists:
11 |  * - The 4pda backend often returns `Cache-Control: no-cache` or omits it for static
12 |  *   assets. OkHttp's cache respects server headers strictly, so an OMIT means
13 |  *   the response is cached but immediately revalidated on the next request.
14 |  * - For theme CSS/JS, this causes repeated network round-trips for assets that
15 |  *   are versioned by URL and effectively immutable within a release.
16 |  *
17 |  * Rules:
18 |  * 1. Only network-side, only GETs, only for paths ending in a known asset suffix.
19 |  * 2. Never overrides an explicit `Cache-Control` from the server.
20 |  * 3. Never applies to requests that carry an `Authorization` header or the auth_key
21 |  *    query parameter — those are personalised and must not be cached.
22 |  *
23 |  * The default max-age is conservative (5 minutes). Bump via [assetMaxAgeSeconds]
24 |  * only if a measured profile shows the headroom is needed.
25 |  */
26 | class CacheControlInterceptor(
27 |     private val assetMaxAgeSeconds: Int = 300,
28 | ) : Interceptor {
29 | 
30 |     override fun intercept(chain: Interceptor.Chain): Response {
31 |         val request = chain.request()
32 |         if (request.method != "GET") return chain.proceed(request)
33 |         if (!isAssetPath(request.url.encodedPath)) return chain.proceed(request)
34 |         if (isPersonalisedRequest(request)) return chain.proceed(request)
35 | 
36 |         val response = chain.proceed(request)
37 |         if (response.header(CACHE_CONTROL) != null) return response
38 |         if (response.header(EXPIRES) != null) return response
39 | 
40 |         val maxAge = "max-age=$assetMaxAgeSeconds"
41 |         return response.newBuilder()
42 |             .removeHeader("Pragma")
43 |             .header(CACHE_CONTROL, maxAge)
44 |             .build()
45 |     }
46 | 
47 |     private fun isAssetPath(path: String): Boolean {
48 |         val lower = path.lowercase()
49 |         return lower.endsWith(".css") ||
50 |             lower.endsWith(".js") ||
51 |             lower.endsWith(".mjs") ||
52 |             lower.endsWith(".woff") ||
53 |             lower.endsWith(".woff2") ||
54 |             lower.endsWith(".ttf") ||
55 |             lower.endsWith(".otf") ||
56 |             lower.endsWith(".eot") ||
57 |             lower.endsWith(".svg") ||
58 |             lower.endsWith(".ico")
59 |     }
60 | 
61 |     private fun isPersonalisedRequest(request: okhttp3.Request): Boolean {
62 |         if (request.header("Authorization") != null) return true
63 |         // Forum auth_key query param: 4pda uses ?auth_key=… for personalised theme pages.
64 |         if (request.url.queryParameter("auth_key") != null) return true
65 |         // 4pda's "logged in" cookie is `member_id`; if it is present the request
66 |         // may return user-specific content (theme CSS variables, per-user JS, etc.)
67 |         // and must not be cached.
68 |         val cookie = request.header("Cookie")
69 |         if (cookie != null && cookie.contains(AUTH_COOKIE_MARKER)) return true
70 |         return false
71 |     }
72 | 
73 |     companion object {
74 |         private const val CACHE_CONTROL = "Cache-Control"
75 |         private const val EXPIRES = "Expires"
76 |         private const val AUTH_COOKIE_MARKER = "member_id="
77 |     }
78 | }
79 | 
```

### app/src/main/java/forpdateam/ru/forpda/client/interceptors/ImageLoadingInterceptor.kt

Bytes: 6401
SHA-256: 70ee00a404265a7802a660ba8e7daa6872fe447e7d0ecc354d59710a4263ef4a
Lines: 1-159 of 159

```text
  1 | package forpdateam.ru.forpda.client.interceptors
  2 | 
  3 | import forpdateam.ru.forpda.BuildConfig
  4 | import forpdateam.ru.forpda.client.OkHttpResponseException
  5 | import okhttp3.HttpUrl
  6 | import okhttp3.Interceptor
  7 | import okhttp3.Response
  8 | import timber.log.Timber
  9 | import java.io.IOException
 10 | import java.util.Locale
 11 | 
 12 | /**
 13 |  * Adds browser-like metadata for 4PDA image hosts used by Coil/ImageViewer.
 14 |  *
 15 |  * Signed CDN URLs must stay untouched: this interceptor only adds headers and logs sanitized metadata.
 16 |  */
 17 | class ImageLoadingInterceptor(
 18 |     private val hasCookiesForRequest: (HttpUrl) -> Boolean
 19 | ) : Interceptor {
 20 | 
 21 |     override fun intercept(chain: Interceptor.Chain): Response {
 22 |         val original = chain.request()
 23 |         val isImageRequest = isFourPdaImageRequest(original.url)
 24 |         val request = if (isImageRequest && original.header("Referer").isNullOrBlank()) {
 25 |             // 4pda.ws (and subdomains) host the news/media CDN; their hotlink
 26 |             // check expects the bare forum root, not the /forum/ subpath. The
 27 |             // forum image host expects /forum/.
 28 |             val referer = if (isFourPdaWsHost(original.url)) {
 29 |                 FORUM_ROOT_REFERER
 30 |             } else {
 31 |                 DEFAULT_REFERER
 32 |             }
 33 |             original.newBuilder()
 34 |                 .header("Referer", referer)
 35 |                 .build()
 36 |         } else {
 37 |             original
 38 |         }
 39 | 
 40 |         if (BuildConfig.DEBUG && isImageRequest) {
 41 |             Timber.tag(TAG).d(
 42 |                 "imageLoadStart host=%s path=%s cookiesPresent=%s refererPresent=%s",
 43 |                 original.url.host,
 44 |                 original.url.encodedPath,
 45 |                 hasCookiesForRequest(original.url),
 46 |                 !request.header("Referer").isNullOrBlank()
 47 |             )
 48 |         }
 49 | 
 50 |         return try {
 51 |             val response = chain.proceed(request)
 52 |             if (BuildConfig.DEBUG && isImageRequest) {
 53 |                 val finalUrl = response.request.url
 54 |                 Timber.tag(TAG).d(
 55 |                     "imageLoadResponse host=%s path=%s redirectHost=%s redirectPath=%s code=%d cookiesPresent=%s refererPresent=%s",
 56 |                     original.url.host,
 57 |                     original.url.encodedPath,
 58 |                     if (finalUrl != original.url) finalUrl.host else "",
 59 |                     if (finalUrl != original.url) finalUrl.encodedPath else "",
 60 |                     response.code,
 61 |                     hasCookiesForRequest(finalUrl),
 62 |                     !response.request.header("Referer").isNullOrBlank()
 63 |                 )
 64 |             }
 65 |             // Retry once on 504/503 for image hosts: OkHttp's "only-if-cached" cache miss
 66 |             // returns 504 (and some CDNs return 503 on transient edge failures) without
 67 |             // ever hitting the network. The retry is immediate: Thread.sleep on the OkHttp
 68 |             // dispatcher blocked a worker thread for the full delay. Most edge failures
 69 |             // resolve on the very next request anyway; if they don't, the UI is going
 70 |             // to give up at the same point it would have after a sleep.
 71 |             // The RETRY_FLAG query param keeps the retry count to exactly 1.
 72 |             if (isImageRequest && response.code in 503..504 && !response.request.url.queryParameterNames.contains(RETRY_FLAG)) {
 73 |                 response.closeQuietly()
 74 |                 if (BuildConfig.DEBUG) {
 75 |                     Timber.tag(TAG).d(
 76 |                         "image_retry status=%d attempt=2 url=%s",
 77 |                         response.code,
 78 |                         sanitizeUrlForLog(original.url)
 79 |                     )
 80 |                 }
 81 |                 val retried = request.newBuilder()
 82 |                     .url(appendRetryFlag(request.url))
 83 |                     .build()
 84 |                 return chain.proceed(retried)
 85 |             }
 86 |             response
 87 |         } catch (e: IOException) {
 88 |             if (BuildConfig.DEBUG && isImageRequest) {
 89 |                 val httpCode = (e as? OkHttpResponseException)?.code
 90 |                 Timber.tag(TAG).w(
 91 |                     "imageLoadFailure host=%s path=%s type=%s code=%s cookiesPresent=%s refererPresent=%s",
 92 |                     original.url.host,
 93 |                     original.url.encodedPath,
 94 |                     e::class.java.simpleName,
 95 |                     httpCode?.toString().orEmpty(),
 96 |                     hasCookiesForRequest(original.url),
 97 |                     !request.header("Referer").isNullOrBlank()
 98 |                 )
 99 |             }
100 |             throw e
101 |         }
102 |     }
103 | 
104 |     private fun Response.closeQuietly() {
105 |         try {
106 |             close()
107 |         } catch (_: Throwable) {
108 |         }
109 |     }
110 | 
111 |     private fun appendRetryFlag(url: HttpUrl): HttpUrl =
112 |             url.newBuilder().setQueryParameter(RETRY_FLAG, "1").build()
113 | 
114 |     private fun sanitizeUrlForLog(url: HttpUrl): String {
115 |         val raw = url.toString()
116 |         return if (raw.length <= 200) raw else raw.substring(0, 200) + "…"
117 |     }
118 | 
119 |     companion object {
120 |         private const val TAG = "ImageViewer"
121 |         private const val DEFAULT_REFERER = "https://4pda.to/forum/"
122 |         private const val FORUM_ROOT_REFERER = "https://4pda.to/"
123 |         private const val RETRY_FLAG = "forpda_retry"
124 | 
125 |         fun isFourPdaImageRequest(url: HttpUrl): Boolean {
126 |             val host = url.host.lowercase(Locale.ROOT)
127 |             val path = url.encodedPath.lowercase(Locale.ROOT)
128 |             val isImagePath = path.hasImageExtension()
129 |             if (!isImagePath) return false
130 | 
131 |             return host == "4pda.to" && (
132 |                 path.startsWith("/s/") ||
133 |                     path.startsWith("/forum/dl/post/") ||
134 |                     path.startsWith("/wp-content/uploads/")
135 |                 ) ||
136 |                 host == "s.4pda.to" ||
137 |                 host == "4pda.ws" ||
138 |                 host.endsWith(".4pda.ws")
139 |         }
140 | 
141 |         fun isFourPdaWsHost(url: HttpUrl): Boolean {
142 |             val host = url.host.lowercase(Locale.ROOT)
143 |             return host == "4pda.ws" || host.endsWith(".4pda.ws")
144 |         }
145 | 
146 |         // Set lookup beats list-of-strings.endsWith for image suffix probes.
147 |         private val IMAGE_EXTENSIONS: Set<String> = setOf(
148 |             ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
149 |         )
150 | 
151 |         private fun String.hasImageExtension(): Boolean {
152 |             for (ext in IMAGE_EXTENSIONS) {
153 |                 if (this.endsWith(ext)) return true
154 |             }
155 |             return false
156 |         }
157 |     }
158 | }
159 | 
```

### app/src/main/java/forpdateam/ru/forpda/common/HtmlToSpannedConverter.kt

Bytes: 18620
SHA-256: ea4d3e29b8db9c4f275114a64b1d1ca494d9bab475bbffeaf9765717ad30a1d1
Lines: 1-270 of 270

```text
  1 | package forpdateam.ru.forpda.common
  2 | 
  3 | import android.graphics.Color
  4 | import android.graphics.Typeface
  5 | import android.graphics.drawable.Drawable
  6 | import android.text.*
  7 | import android.text.style.*
  8 | import android.content.res.Resources
  9 | import org.xml.sax.*
 10 | import java.io.StringReader
 11 | import java.util.Locale
 12 | import java.util.regex.Pattern
 13 | import forpdateam.ru.forpda.common.Html.Companion.FROM_HTML_OPTION_USE_CSS_COLORS
 14 | 
 15 | internal class HtmlToSpannedConverter(
 16 |     private val mSource: String,
 17 |     private val mImageGetter: Html.ImageGetter?,
 18 |     private val mTagHandler: Html.TagHandler?,
 19 |     private val mReader: XMLReader,
 20 |     private val mFlags: Int
 21 | ) : ContentHandler {
 22 | 
 23 |     companion object {
 24 |         private val HEADING_SIZES = floatArrayOf(1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f)
 25 |         // Patterns are immutable once compiled; eager init in the companion
 26 |         // is thread-safe and removes the per-call null-check race in the old
 27 |         // getXxxPattern() helpers. Use Kotlin raw strings so the regex's \s,
 28 |         // \A, \S, \b escape sequences are not treated as Kotlin escape sequences.
 29 |         private val sTextAlignPattern: Pattern = Pattern.compile("""(?:\s+|\A)text-align\s*:\s*(\S*)\b""", Pattern.CASE_INSENSITIVE)
 30 |         private val sForegroundColorPattern: Pattern = Pattern.compile("""(?:\s+|\A)color\s*:\s*(\S*)\b""", Pattern.CASE_INSENSITIVE)
 31 |         private val sBackgroundColorPattern: Pattern = Pattern.compile("""(?:\s+|\A)background(?:-color)?\s*:\s*(\S*)\b""", Pattern.CASE_INSENSITIVE)
 32 |         private val sTextDecorationPattern: Pattern = Pattern.compile("""(?:\s+|\A)text-decoration\s*:\s*(\S*)\b""", Pattern.CASE_INSENSITIVE)
 33 |         private val sFontFamilyPattern: Pattern = Pattern.compile("""(?:\s+|\A)font-family\s*:\s*(\S*)\b""", Pattern.CASE_INSENSITIVE)
 34 | 
 35 |         private fun getTextAlignPattern(): Pattern = sTextAlignPattern
 36 |         private fun getForegroundColorPattern(): Pattern = sForegroundColorPattern
 37 |         private fun getBackgroundColorPattern(): Pattern = sBackgroundColorPattern
 38 |         private fun getTextDecorationPattern(): Pattern = sTextDecorationPattern
 39 |         private fun getFontFamilyPattern(): Pattern = sFontFamilyPattern
 40 |     }
 41 | 
 42 |     private val mSpannableStringBuilder = SpannableStringBuilder()
 43 | 
 44 |     fun convert(): Spanned {
 45 |         mReader.contentHandler = this
 46 |         try { mReader.parse(InputSource(StringReader(mSource))) }
 47 |         catch (e: java.io.IOException) { throw RuntimeException(e) }
 48 |         catch (e: SAXException) { throw RuntimeException(e) }
 49 |         val paragraphSpans = mSpannableStringBuilder.getSpans(0, mSpannableStringBuilder.length, ParagraphStyle::class.java)
 50 |         for (span in paragraphSpans) {
 51 |             var start = mSpannableStringBuilder.getSpanStart(span)
 52 |             var end = mSpannableStringBuilder.getSpanEnd(span)
 53 |             if (end - 2 >= 0 && mSpannableStringBuilder[end - 1] == '\n' && mSpannableStringBuilder[end - 2] == '\n') end--
 54 |             if (end == start) mSpannableStringBuilder.removeSpan(span)
 55 |             else mSpannableStringBuilder.setSpan(span, start, end, Spannable.SPAN_PARAGRAPH)
 56 |         }
 57 |         return mSpannableStringBuilder
 58 |     }
 59 | 
 60 |     private fun handleStartTag(tag: String, attributes: Attributes) {
 61 |         when {
 62 |             tag.equals("br", ignoreCase = true) -> {}
 63 |             tag.equals("p", ignoreCase = true) -> { startBlockElement(mSpannableStringBuilder, attributes, getMarginParagraph()); startCssStyle(mSpannableStringBuilder, attributes) }
 64 |             tag.equals("ul", ignoreCase = true) -> startBlockElement(mSpannableStringBuilder, attributes, getMarginList())
 65 |             tag.equals("li", ignoreCase = true) -> startLi(mSpannableStringBuilder, attributes, getMarginListItem())
 66 |             tag.equals("div", ignoreCase = true) -> startBlockElement(mSpannableStringBuilder, attributes, getMarginDiv())
 67 |             tag.equals("span", ignoreCase = true) -> startCssStyle(mSpannableStringBuilder, attributes)
 68 |             tag.equals("strong", ignoreCase = true) || tag.equals("b", ignoreCase = true) -> start(mSpannableStringBuilder, Bold())
 69 |             tag.equals("em", ignoreCase = true) || tag.equals("cite", ignoreCase = true) || tag.equals("dfn", ignoreCase = true) || tag.equals("i", ignoreCase = true) -> start(mSpannableStringBuilder, Italic())
 70 |             tag.equals("big", ignoreCase = true) -> start(mSpannableStringBuilder, Big())
 71 |             tag.equals("small", ignoreCase = true) -> start(mSpannableStringBuilder, Small())
 72 |             tag.equals("font", ignoreCase = true) -> startFont(mSpannableStringBuilder, attributes)
 73 |             tag.equals("blockquote", ignoreCase = true) -> startBlockquote(mSpannableStringBuilder, attributes)
 74 |             tag.equals("tt", ignoreCase = true) -> start(mSpannableStringBuilder, Monospace())
 75 |             tag.equals("a", ignoreCase = true) -> startA(mSpannableStringBuilder, attributes)
 76 |             tag.equals("u", ignoreCase = true) -> start(mSpannableStringBuilder, Underline())
 77 |             tag.equals("del", ignoreCase = true) || tag.equals("s", ignoreCase = true) || tag.equals("strike", ignoreCase = true) -> start(mSpannableStringBuilder, Strikethrough())
 78 |             tag.equals("sup", ignoreCase = true) -> start(mSpannableStringBuilder, Super())
 79 |             tag.equals("sub", ignoreCase = true) -> start(mSpannableStringBuilder, Sub())
 80 |             tag.length == 2 && tag[0].lowercaseChar() == 'h' && tag[1] in '1'..'6' -> startHeading(mSpannableStringBuilder, attributes, tag[1] - '1')
 81 |             tag.equals("img", ignoreCase = true) -> startImg(mSpannableStringBuilder, attributes, mImageGetter)
 82 |             mTagHandler != null -> mTagHandler!!.handleTag(true, tag, mSpannableStringBuilder, mReader)
 83 |         }
 84 |     }
 85 | 
 86 |     private fun handleEndTag(tag: String) {
 87 |         when {
 88 |             tag.equals("br", ignoreCase = true) -> handleBr(mSpannableStringBuilder)
 89 |             tag.equals("p", ignoreCase = true) -> { endCssStyle(mSpannableStringBuilder); endBlockElement(mSpannableStringBuilder) }
 90 |             tag.equals("ul", ignoreCase = true) || tag.equals("div", ignoreCase = true) -> endBlockElement(mSpannableStringBuilder)
 91 |             tag.equals("li", ignoreCase = true) -> { endCssStyle(mSpannableStringBuilder); endBlockElement(mSpannableStringBuilder); end(mSpannableStringBuilder, Bullet::class.java, BulletSpan()) }
 92 |             tag.equals("span", ignoreCase = true) -> endCssStyle(mSpannableStringBuilder)
 93 |             tag.equals("strong", ignoreCase = true) || tag.equals("b", ignoreCase = true) -> end(mSpannableStringBuilder, Bold::class.java, StyleSpan(Typeface.BOLD))
 94 |             tag.equals("em", ignoreCase = true) || tag.equals("cite", ignoreCase = true) || tag.equals("dfn", ignoreCase = true) || tag.equals("i", ignoreCase = true) -> end(mSpannableStringBuilder, Italic::class.java, StyleSpan(Typeface.ITALIC))
 95 |             tag.equals("big", ignoreCase = true) -> end(mSpannableStringBuilder, Big::class.java, RelativeSizeSpan(1.25f))
 96 |             tag.equals("small", ignoreCase = true) -> end(mSpannableStringBuilder, Small::class.java, RelativeSizeSpan(0.875f))
 97 |             tag.equals("font", ignoreCase = true) -> endFont(mSpannableStringBuilder)
 98 |             tag.equals("blockquote", ignoreCase = true) -> { endBlockElement(mSpannableStringBuilder); end(mSpannableStringBuilder, Blockquote::class.java, QuoteSpan()) }
 99 |             tag.equals("tt", ignoreCase = true) -> end(mSpannableStringBuilder, Monospace::class.java, TypefaceSpan("monospace"))
100 |             tag.equals("a", ignoreCase = true) -> endA(mSpannableStringBuilder)
101 |             tag.equals("u", ignoreCase = true) -> end(mSpannableStringBuilder, Underline::class.java, UnderlineSpan())
102 |             tag.equals("del", ignoreCase = true) || tag.equals("s", ignoreCase = true) || tag.equals("strike", ignoreCase = true) -> end(mSpannableStringBuilder, Strikethrough::class.java, StrikethroughSpan())
103 |             tag.equals("sup", ignoreCase = true) -> end(mSpannableStringBuilder, Super::class.java, SuperscriptSpan())
104 |             tag.equals("sub", ignoreCase = true) -> end(mSpannableStringBuilder, Sub::class.java, SubscriptSpan())
105 |             tag.length == 2 && tag[0].lowercaseChar() == 'h' && tag[1] in '1'..'6' -> endHeading(mSpannableStringBuilder)
106 |             mTagHandler != null -> mTagHandler!!.handleTag(false, tag, mSpannableStringBuilder, mReader)
107 |         }
108 |     }
109 | 
110 |     private fun getMarginParagraph() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
111 |     private fun getMarginHeading() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING)
112 |     private fun getMarginListItem() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM)
113 |     private fun getMarginList() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST)
114 |     private fun getMarginDiv() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV)
115 |     private fun getMarginBlockquote() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE)
116 |     private fun getMargin(flag: Int) = if ((flag and mFlags) != 0) 1 else 2
117 | 
118 |     private fun appendNewlines(text: Editable, minNewline: Int) {
119 |         val len = text.length; if (len == 0) return
120 |         var existingNewlines = 0; var i = len - 1
121 |         while (i >= 0 && text[i] == '\n') { existingNewlines++; i-- }
122 |         for (j in existingNewlines until minNewline) text.append("\n")
123 |     }
124 |     private fun startBlockElement(text: Editable, attributes: Attributes, margin: Int) {
125 |         if (margin > 0) { appendNewlines(text, margin); start(text, Newline(margin)) }
126 |         val style = attributes.getValue("", "style")
127 |         if (style != null) { val m = getTextAlignPattern().matcher(style); if (m.find()) { val alignment = m.group(1); when { alignment.equals("start", ignoreCase = true) -> start(text, Alignment(Layout.Alignment.ALIGN_NORMAL)); alignment.equals("center", ignoreCase = true) -> start(text, Alignment(Layout.Alignment.ALIGN_CENTER)); alignment.equals("end", ignoreCase = true) -> start(text, Alignment(Layout.Alignment.ALIGN_OPPOSITE)) } } }
128 |     }
129 |     private fun endBlockElement(text: Editable) {
130 |         val n = getLast(text, Newline::class.java); if (n != null) { appendNewlines(text, n.mNumNewlines); text.removeSpan(n) }
131 |         val a = getLast(text, Alignment::class.java); if (a != null) setSpanFromMark(text, a, AlignmentSpan.Standard(a.mAlignment))
132 |     }
133 |     private fun handleBr(text: Editable) { text.append('\n') }
134 |     private fun startLi(text: Editable, attributes: Attributes, marginListItem: Int) { startBlockElement(text, attributes, marginListItem); start(text, Bullet()); startCssStyle(text, attributes) }
135 |     private fun endLi(text: Editable) { endCssStyle(text); endBlockElement(text); end(text, Bullet::class.java, BulletSpan()) }
136 |     private fun startBlockquote(text: Editable, attributes: Attributes) { startBlockElement(text, attributes, getMarginBlockquote()); start(text, Blockquote()) }
137 |     private fun startHeading(text: Editable, attributes: Attributes, level: Int) { startBlockElement(text, attributes, getMarginHeading()); start(text, Heading(level)) }
138 |     private fun endHeading(text: Editable) { val h = getLast(text, Heading::class.java); if (h != null) setSpanFromMark(text, h, RelativeSizeSpan(HEADING_SIZES[h.mLevel]), StyleSpan(Typeface.BOLD)); endBlockElement(text) }
139 |     private fun <T> getLast(text: Spanned, kind: Class<T>): T? { val objs = text.getSpans(0, text.length, kind); return if (objs.isEmpty()) null else objs[objs.size - 1] }
140 |     private fun setSpanFromMark(text: Spannable, mark: Any, vararg spans: Any) { val where = text.getSpanStart(mark); text.removeSpan(mark); val len = text.length; if (where != len) for (span in spans) text.setSpan(span, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
141 |     private fun start(text: Editable, mark: Any) { val len = text.length; text.setSpan(mark, len, len, Spannable.SPAN_INCLUSIVE_EXCLUSIVE) }
142 |     private fun <T> end(text: Editable, kind: Class<T>, repl: Any) { val obj = getLast(text, kind); if (obj != null) setSpanFromMark(text, obj, repl) }
143 |     private fun startCssStyle(text: Editable, attributes: Attributes) {
144 |         val style = attributes.getValue("", "style") ?: return
145 |         var m = getForegroundColorPattern().matcher(style); if (m.find()) { val c = getHtmlColor(m.group(1)); if (c != -1) start(text, Foreground(c or 0xFF000000.toInt())) }
146 |         m = getBackgroundColorPattern().matcher(style); if (m.find()) { val c = getHtmlColor(m.group(1)); if (c != -1) start(text, Background(c or 0xFF000000.toInt())) }
147 |         m = getTextDecorationPattern().matcher(style); if (m.find()) { if (m.group(1).equals("line-through", ignoreCase = true)) start(text, Strikethrough()) }
148 |         m = getFontFamilyPattern().matcher(style); if (m.find()) start(text, Font(m.group(1)))
149 |     }
150 |     private fun endCssStyle(text: Editable) {
151 |         val font = getLast(text, Font::class.java); if (font != null && font.mFace.equals("fontello", ignoreCase = true)) setSpanFromMark(text, font, AssetsTypefaceSpan(null, "fontello/fontello.ttf"))
152 |         val s = getLast(text, Strikethrough::class.java); if (s != null) setSpanFromMark(text, s, StrikethroughSpan())
153 |         val b = getLast(text, Background::class.java); if (b != null) setSpanFromMark(text, b, BackgroundColorSpan(b.mBackgroundColor))
154 |         val f = getLast(text, Foreground::class.java); if (f != null) setSpanFromMark(text, f, ForegroundColorSpan(f.mForegroundColor))
155 |     }
156 |     private fun startImg(text: Editable, attributes: Attributes, img: Html.ImageGetter?) {
157 |         val src = attributes.getValue("", "src")
158 |         val d: Drawable? = img?.getDrawable(src)
159 |         if (d == null) return
160 |         val len = text.length; text.append("\uFFFC"); text.setSpan(ImageSpan(d, src), len, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
161 |     }
162 |     private fun startFont(text: Editable, attributes: Attributes) {
163 |         val color = attributes.getValue("", "color"); val face = attributes.getValue("", "face")
164 |         if (!TextUtils.isEmpty(color)) { val c = getHtmlColor(color); if (c != -1) start(text, Foreground(c or 0xFF000000.toInt())) }
165 |         if (!TextUtils.isEmpty(face)) start(text, Font(face))
166 |     }
167 |     private fun endFont(text: Editable) {
168 |         val font = getLast(text, Font::class.java); if (font != null) setSpanFromMark(text, font, TypefaceSpan(font.mFace))
169 |         val fg = getLast(text, Foreground::class.java); if (fg != null) setSpanFromMark(text, fg, ForegroundColorSpan(fg.mForegroundColor))
170 |     }
171 |     private fun startA(text: Editable, attributes: Attributes) { start(text, Href(attributes.getValue("", "href"))) }
172 |     private fun endA(text: Editable) { val h = getLast(text, Href::class.java); if (h?.mHref != null) setSpanFromMark(text, h, URLSpan(h.mHref)) }
173 |     private fun getHtmlColor(color: String): Int {
174 |         if ((mFlags and Html.FROM_HTML_OPTION_USE_CSS_COLORS) == Html.FROM_HTML_OPTION_USE_CSS_COLORS) {
175 |             var i: Int? = Html.getColorMap()[color.lowercase(Locale.ROOT)]; if (i != null) return i; i = null
176 |             try { i = Color.parseColor(color) } catch (_: Exception) {}
177 |             if (i != null) return i
178 |         }
179 |         return Color.TRANSPARENT
180 |     }
181 | 
182 |     override fun setDocumentLocator(locator: Locator?) {}
183 |     override fun startDocument() {}
184 |     override fun endDocument() {}
185 |     override fun startPrefixMapping(prefix: String?, uri: String?) {}
186 |     override fun endPrefixMapping(prefix: String?) {}
187 |     override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) { handleStartTag(localName, attributes) }
188 |     override fun endElement(uri: String?, localName: String, qName: String?) { handleEndTag(localName) }
189 |     override fun characters(ch: CharArray, start: Int, length: Int) {
190 |         appendPreservingWhitespace(ch, start, start + length)
191 |     }
192 | 
193 |     /**
194 |      * Walk `ch[from until until]` once, collapsing runs of whitespace (' ' or '\n') into a
195 |      * single space and writing directly into [mSpannableStringBuilder] (no per-chunk
196 |      * [StringBuilder] allocation). Matches the legacy behavior:
197 |      *  - leading whitespace is kept as a single space iff the builder is non-empty and
198 |      *    doesn't already end in whitespace; otherwise dropped;
199 |      *  - an all-whitespace chunk behaves the same way (single space iff the builder is
200 |      *    non-empty and doesn't end in whitespace);
201 |      *  - trailing whitespace collapses to a single space iff the previous output char is
202 |      *    non-whitespace; otherwise dropped.
203 |      */
204 |     private fun appendPreservingWhitespace(ch: CharArray, from: Int, until: Int) {
205 |         if (from >= until) return
206 |         val builder = mSpannableStringBuilder
207 |         var i = from
208 |         while (i < until && isHtmlWhitespace(ch[i])) i++
209 |         val hasLeadingWs = i > from
210 |         if (i >= until) {
211 |             appendBoundarySpaceIfNeeded(builder)
212 |             return
213 |         }
214 |         if (hasLeadingWs) {
215 |             appendBoundarySpaceIfNeeded(builder)
216 |         }
217 |         builder.append(ch[i])
218 |         i++
219 |         while (i < until) {
220 |             val c = ch[i]
221 |             if (isHtmlWhitespace(c)) {
222 |                 while (i < until && isHtmlWhitespace(ch[i])) i++
223 |                 if (i >= until) {
224 |                     appendBoundarySpaceIfNeeded(builder)
225 |                     return
226 |                 }
227 |                 appendBoundarySpaceIfNeeded(builder)
228 |                 builder.append(ch[i])
229 |                 i++
230 |             } else {
231 |                 builder.append(c)
232 |                 i++
233 |             }
234 |         }
235 |     }
236 | 
237 |     private fun isHtmlWhitespace(c: Char): Boolean = c == ' ' || c == '\n'
238 | 
239 |     private fun appendBoundarySpaceIfNeeded(builder: SpannableStringBuilder) {
240 |         val lastIdx = builder.length - 1
241 |         if (lastIdx < 0) return
242 |         val last = builder[lastIdx]
243 |         if (last != ' ' && last != '\n') {
244 |             builder.append(' ')
245 |         }
246 |     }
247 |     override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {}
248 |     override fun processingInstruction(target: String?, data: String?) {}
249 |     override fun skippedEntity(name: String?) {}
250 | 
251 |     private class Bold
252 |     private class Italic
253 |     private class Underline
254 |     private class Strikethrough
255 |     private class Big
256 |     private class Small
257 |     private class Monospace
258 |     private class Blockquote
259 |     private class Super
260 |     private class Sub
261 |     private class Bullet
262 |     private class Font(val mFace: String)
263 |     private class Href(val mHref: String?)
264 |     private class Foreground(val mForegroundColor: Int)
265 |     private class Background(val mBackgroundColor: Int)
266 |     private class Heading(val mLevel: Int)
267 |     private class Newline(val mNumNewlines: Int)
268 |     private class Alignment(val mAlignment: Layout.Alignment)
269 | }
270 | 
```

### app/src/main/java/forpdateam/ru/forpda/common/Preferences.kt

Bytes: 6385
SHA-256: 0f12f12656ee3ce7837e26dd247a2d3658936b8edda18368a3c8bb70e09e8d8b
Lines: 1-146 of 146

```text
  1 | package forpdateam.ru.forpda.common
  2 | 
  3 | /**
  4 |  * Created by radiationx on 28.05.17.
  5 |  */
  6 | object Preferences {
  7 | 
  8 |     object Auth {
  9 |         const val USER_ID = "member_id"
 10 |         const val AUTH_KEY = "auth_key"
 11 |         const val COOKIE_MEMBER_ID = "cookie_member_id"
 12 |         const val COOKIE_PASS_HASH = "cookie_pass_hash"
 13 |         const val COOKIE_SESSION_ID = "cookie_session_id"
 14 |         const val COOKIE_ANONYMOUS = "cookie_anonymous"
 15 |         const val COOKIE_CF_CLEARANCE = "cookie_cf_clearance"
 16 |     }
 17 | 
 18 |     object Other {
 19 |         const val APP_FIRST_START = "main.is_first_start"
 20 |         const val APP_VERSIONS_HISTORY = "app.versions.history"
 21 |         const val SEARCH_SETTINGS = "search_settings_v2"
 22 |         const val MESSAGE_PANEL_BBCODES_SORT = "message_panel.bb_codes.sorted"
 23 |         const val SHOW_REPORT_WARNING = "show_report_warning"
 24 |         const val TOOLTIP_SEARCH_SETTINGS = "search.tooltip.settings"
 25 |         const val TOOLTIP_THEME_LONG_CLICK_SEND = "theme.tooltip.long_click_send"
 26 |         const val TOOLTIP_MESSAGE_PANEL_SORTING = "message_panel.tooltip.user_sorting"
 27 |         const val SMART_NAV_LONG_PRESS_HINT_DISABLED = "smart_nav_long_press_hint_disabled"
 28 |     }
 29 | 
 30 |     object Main {
 31 |         private const val PREFIX = "main."
 32 |         const val WEBVIEW_FONT_SIZE = PREFIX + "webview.font_size_v2"
 33 |         const val IS_SYSTEM_DOWNLOADER = PREFIX + "is_system_downloader"
 34 |         const val DOWNLOAD_METHOD = PREFIX + "download_method"
 35 |         const val DOWNLOAD_FOLDER_URI = PREFIX + "download_folder_uri"
 36 |         const val IS_EDITOR_MONOSPACE = "message_panel.is_monospace"
 37 |         const val IS_EDITOR_DEFAULT_HIDDEN = "message_panel.is_default_hidden"
 38 |         const val SCROLL_BUTTON_ENABLE = PREFIX + "scroll_button.enable"
 39 |         const val TOPIC_PAGINATION_PANEL_ENABLE = PREFIX + "topic_pagination_panel.enable"
 40 |         const val TOPIC_SCROLL_MODE = PREFIX + "topic_scroll_mode"
 41 |         const val TOPIC_POST_DENSITY = PREFIX + "topic_post_density"
 42 |         const val TOPIC_TOOLBAR_BEHAVIOR = PREFIX + "topic_toolbar_behavior"
 43 |         const val TOPIC_PAGE_SWIPE_ENABLE = PREFIX + "topic_page_swipe.enable"
 44 |         const val TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE = PREFIX + "topic_bottom_refresh_gesture.enable"
 45 |         const val TOPIC_BACK_BEHAVIOR = PREFIX + "topic_back_behavior"
 46 |         const val TOPIC_OPEN_TARGET = PREFIX + "topic_open_target"
 47 |         const val TOPIC_HEADER_INITIAL_STATE = PREFIX + "topic_header_initial_state"
 48 |         const val SHOW_BOTTOM_ARROW = PREFIX + "show_bottom_arrow"
 49 |         const val BOTTOM_NAV_COLUMNS = PREFIX + "bottom_nav_columns"
 50 |         const val UI_PALETTE = PREFIX + "ui.palette"
 51 |         const val APP_FONT_MODE = PREFIX + "app_font_mode"
 52 |         const val USE_SYSTEM_FONT = PREFIX + "use_system_font"
 53 |         const val STARTUP_SCREEN = PREFIX + "startup_screen"
 54 |         const val USE_MATERIAL_YOU = PREFIX + "use_material_you"
 55 | 
 56 |         object Theme {
 57 |             private const val PREFIX = Main.PREFIX + "theme."
 58 |             const val MODE = PREFIX + "mode"
 59 |         }
 60 | 
 61 |         enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM, SYSTEM_AMOLED }
 62 |         enum class UiPalette { SYSTEM, CLASSIC_4PDA, SEPIA_READING, SEPIA_BLUE, MINIMAL_READER }
 63 |         enum class DownloadMethod { SYSTEM, EXTERNAL_MANAGER, BROWSER, ASK }
 64 |         enum class TopicScrollMode { HYBRID, CLASSIC }
 65 |         enum class TopicPostDensity { COMFORTABLE, COMPACT, SUPER_COMPACT }
 66 |         enum class TopicToolbarBehavior { PINNED, HIDE_ON_SCROLL }
 67 |         enum class TopicBackBehavior { HISTORY, ORIGIN }
 68 |         enum class TopicOpenTarget { FIRST_PAGE, LAST_UNREAD }
 69 |         enum class TopicHeaderInitialState { EXPANDED, COLLAPSED }
 70 |         enum class StartupScreen { NEWS, FAVORITES, FORUM, REPLIES, QMS }
 71 |     }
 72 | 
 73 |     object Lists {
 74 |         private const val PREFIX = "lists."
 75 | 
 76 |         object Topic {
 77 |             private const val PREFIX = Lists.PREFIX + "topic."
 78 |             const val UNREAD_TOP = PREFIX + "unread_top"
 79 |             const val SHOW_DOT = PREFIX + "show_dot"
 80 |         }
 81 | 
 82 |         object Favorites {
 83 |             private const val PREFIX = Lists.PREFIX + "favorites."
 84 |             const val LOAD_ALL = PREFIX + "load_all"
 85 |             const val SHOW_UNREAD_BADGE = PREFIX + "show_unread_badge"
 86 |             const val SORTING_KEY = PREFIX + "sorting_key"
 87 |             const val SORTING_ORDER = PREFIX + "sorting_order"
 88 |         }
 89 | 
 90 |         object News {
 91 |             private const val PREFIX = Lists.PREFIX + "news."
 92 |             const val CATEGORY = PREFIX + "category"
 93 |         }
 94 |     }
 95 | 
 96 |     object Theme {
 97 |         private const val PREFIX = "theme."
 98 |         const val SHOW_AVATARS = PREFIX + "show_avatars"
 99 |         const val CIRCLE_AVATARS = PREFIX + "circle_avatars"
100 |         const val ANCHOR_HISTORY = PREFIX + "anchor_history"
101 |         const val HAT_OPENED = PREFIX + "hat_opened"
102 |         const val FORUM_BLACKLIST = PREFIX + "forum_blacklist"
103 |     }
104 | 
105 |     object Notifications {
106 |         private const val PREFIX = "notifications."
107 | 
108 |         object Data {
109 |             private const val PREFIX = Notifications.PREFIX + "data."
110 |             const val QMS_EVENTS = PREFIX + "qms_events"
111 |             const val FAVORITES_EVENTS = PREFIX + "favorites_events"
112 |         }
113 | 
114 |         object MainNotif {
115 |             private const val PREFIX = Notifications.PREFIX + "main."
116 |             const val ENABLED = PREFIX + "enabled"
117 |             const val SOUND_ENABLED = PREFIX + "sound_enabled"
118 |             const val VIBRATION_ENABLED = PREFIX + "vibration_enabled"
119 |             const val INDICATOR_ENABLED = PREFIX + "indicator_enabled"
120 |             const val AVATARS_ENABLED = PREFIX + "avatars_enabled"
121 |         }
122 | 
123 |         object FavoritesNotif {
124 |             private const val PREFIX = Notifications.PREFIX + "fav."
125 |             const val ENABLED = PREFIX + "enabled"
126 |             const val ONLY_IMPORTANT = PREFIX + "only_important"
127 |             const val LIVE_TAB = PREFIX + "live_tab"
128 |         }
129 | 
130 |         object Qms {
131 |             private const val PREFIX = Notifications.PREFIX + "qms."
132 |             const val ENABLED = PREFIX + "enabled"
133 |         }
134 | 
135 |         object Mentions {
136 |             private const val PREFIX = Notifications.PREFIX + "mentions."
137 |             const val ENABLED = PREFIX + "enabled"
138 |         }
139 | 
140 |         object Downloads {
141 |             private const val PREFIX = Notifications.PREFIX + "downloads."
142 |             const val ENABLED = PREFIX + "enabled"
143 |         }
144 |     }
145 | }
146 | 
```

### app/src/main/java/forpdateam/ru/forpda/common/receivers/WakeUpReceiver.kt

Bytes: 1251
SHA-256: b6df2e9b69d1d7a8715708c9a125613c5006b8b99ebc565d39a93fcf92cafaef
Lines: 1-34 of 34

```text
 1 | package forpdateam.ru.forpda.common.receivers
 2 | 
 3 | import android.content.BroadcastReceiver
 4 | import android.content.Context
 5 | import android.content.Intent
 6 | import timber.log.Timber
 7 | 
 8 | /**
 9 |  * Приёмник события BOOT_COMPLETED.
10 |  *
11 |  * Сам по себе не запускает ни сервис, ни WorkManager: WorkManager сохраняет
12 |  * периодические задачи между перезагрузками автоматически, а
13 |  * App.setupBackgroundEventsCheck() при следующем запуске процесса
14 |  * перепланирует воркер под актуальные настройки.
15 |  *
16 |  * Дополнительных действий здесь не требуется.
17 |  */
18 | class WakeUpReceiver : BroadcastReceiver() {
19 | 
20 |     override fun onReceive(context: Context, intent: Intent?) {
21 |         val pending = goAsync()
22 |         try {
23 |             val action = intent?.action
24 |             if (Intent.ACTION_BOOT_COMPLETED == action) {
25 |                 Timber.d("Boot completed: notification worker state restored by WorkManager")
26 |             }
27 |         } catch (t: Throwable) {
28 |             Timber.e(t, "WakeUpReceiver failed")
29 |         } finally {
30 |             pending.finish()
31 |         }
32 |     }
33 | }
34 | 
```

### app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt

Bytes: 14136
SHA-256: 26321d81488ecfee2f7b053dcc67d8128cb3d08511c15cc5658b5e431a9abfdc
Lines: 1-363 of 363

```text
  1 | package forpdateam.ru.forpda.common.webview
  2 | 
  3 | import android.annotation.TargetApi
  4 | import android.content.Context
  5 | import android.graphics.Bitmap
  6 | import android.graphics.BitmapFactory
  7 | import android.net.Uri
  8 | import android.net.http.SslError
  9 | import android.os.Build
 10 | import android.os.SystemClock
 11 | import android.util.Base64
 12 | import android.util.LruCache
 13 | import forpdateam.ru.forpda.BuildConfig
 14 | import timber.log.Timber
 15 | import android.webkit.SslErrorHandler
 16 | import android.webkit.WebResourceError
 17 | import android.webkit.WebResourceRequest
 18 | import android.webkit.WebResourceResponse
 19 | import android.webkit.WebView
 20 | import android.webkit.WebViewClient
 21 | import forpdateam.ru.forpda.common.ForPdaCoil
 22 | import forpdateam.ru.forpda.common.SiteUrls
 23 | import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
 24 | import forpdateam.ru.forpda.presentation.ILinkHandler
 25 | import forpdateam.ru.forpda.presentation.ISystemLinkHandler
 26 | import java.io.ByteArrayInputStream
 27 | import java.io.ByteArrayOutputStream
 28 | import java.net.URLDecoder
 29 | import java.util.regex.Pattern
 30 | /**
 31 |  * Created by radiationx on 12.09.17.
 32 |  */
 33 | open class CustomWebViewClient(
 34 |     private val avatarRepository: AvatarRepository? = null,
 35 |     private val linkHandler: ILinkHandler? = null,
 36 |     private val systemLinkHandler: ISystemLinkHandler? = null
 37 | ) : WebViewClient() {
 38 | 
 39 |     companion object {
 40 |         private const val LOG_TAG = "CustomWebViewClient"
 41 |         private const val TYPE_NICK = "nick"
 42 |         private const val TYPE_URL = "url"
 43 |         private const val AVATAR_RESPONSE_CACHE_BYTES = 2 * 1024 * 1024
 44 |         private const val AVATAR_RESPONSE_MAX_ENTRY_BYTES = 256 * 1024
 45 |         private const val AVATAR_INTERCEPT_SLOW_LOG_MS = 16L
 46 |         private data class AvatarCachedResponse(val mimeType: String, val bytes: ByteArray) {
 47 |             override fun equals(other: Any?): Boolean {
 48 |                 if (this === other) return true
 49 |                 if (other !is AvatarCachedResponse) return false
 50 |                 return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
 51 |             }
 52 | 
 53 |             override fun hashCode(): Int {
 54 |                 var result = mimeType.hashCode()
 55 |                 result = 31 * result + bytes.contentHashCode()
 56 |                 return result
 57 |             }
 58 |         }
 59 |         private val avatarResponseCache = object : LruCache<String, AvatarCachedResponse>(AVATAR_RESPONSE_CACHE_BYTES) {
 60 |             override fun sizeOf(key: String, value: AvatarCachedResponse): Int = value.bytes.size
 61 |         }
 62 |         private val DOWNLOAD_PATTERN: Pattern = Pattern.compile(
 63 |             ".*\\.(apk|zip|rar|7z|tar|gz|bz2|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|csv|mp3|mp4|avi|mkv|mov|wmv|flv|wav|ogg|exe|dmg|iso|img|torrent|bin|patch)(\\?.*)?\$",
 64 |             Pattern.CASE_INSENSITIVE
 65 |         )
 66 |         private val P4PDA_DOWNLOAD_PATTERN: Pattern = Pattern.compile(
 67 |             "https?://.*4pda\\.to/.*(?:dl/|download|attach|upload)[^\\.]*(?:\\.(?!jpg|jpeg|png|gif|bmp|webp)[a-z0-9]+)?\$",
 68 |             Pattern.CASE_INSENSITIVE
 69 |         )
 70 |     }
 71 | 
 72 |     private val cachePattern: Pattern = Pattern.compile("app_cache:avatars\\?(url|nick)=([\\s\\S]*)")
 73 | 
 74 |     private fun initDependencies(context: Context) {
 75 |         // Dependencies now provided via constructor; no lazy init needed
 76 |     }
 77 | 
 78 |     override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
 79 |         return interceptAvatarCache(view, request.url?.toString()) ?: super.shouldInterceptRequest(view, request)
 80 |     }
 81 | 
 82 |     override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
 83 |         // Deprecated Android N- overload: kept for binary compat. The WebResourceRequest
 84 |         // overload is the live path on Android N+; the actual avatar-cache logic lives
 85 |         // in interceptAvatarCache() to avoid drift between the two call sites.
 86 |         initDependencies(view.context)
 87 |         return interceptAvatarCache(view, url) ?: super.shouldInterceptRequest(view, url)
 88 |     }
 89 | 
 90 |     private fun interceptAvatarCache(view: WebView, url: String?): WebResourceResponse? {
 91 |         if (url.isNullOrEmpty()) return null
 92 |         val matcher = cachePattern.matcher(url)
 93 |         if (!matcher.find()) return null
 94 |         return try {
 95 |             val startedAt = SystemClock.elapsedRealtime()
 96 |             val type = matcher.group(1).orEmpty()
 97 |             var value = matcher.group(2).orEmpty()
 98 |             value = URLDecoder.decode(value, "UTF-8")
 99 | 
100 |             val resolveStartedAt = SystemClock.elapsedRealtime()
101 |             val avatarUrl = when (type) {
102 |                 TYPE_NICK -> avatarRepository?.getAvatarForWebViewInterceptSync(value)
103 |                 TYPE_URL -> value
104 |                 else -> null
105 |             }
106 |             val resolvedAt = SystemClock.elapsedRealtime()
107 | 
108 |             val resolvedAvatarUrl = avatarUrl ?: run {
109 |                 logAvatarInterceptTiming(
110 |                     type = type,
111 |                     resolveMs = resolvedAt - resolveStartedAt,
112 |                     loadMs = 0,
113 |                     encodeMs = 0,
114 |                     totalMs = resolvedAt - startedAt,
115 |                     bytes = 0,
116 |                     cacheHit = false,
117 |                     skipped = true,
118 |                     source = "unresolved"
119 |                 )
120 |                 null
121 |             } ?: return null
122 |             avatarResponseCache.get(resolvedAvatarUrl)?.let { cached ->
123 |                 logAvatarInterceptTiming(
124 |                     type = type,
125 |                     resolveMs = resolvedAt - resolveStartedAt,
126 |                     loadMs = 0,
127 |                     encodeMs = 0,
128 |                     totalMs = SystemClock.elapsedRealtime() - startedAt,
129 |                     bytes = cached.bytes.size,
130 |                     cacheHit = true,
131 |                     skipped = false,
132 |                     source = "responseCache"
133 |                 )
134 |                 return avatarResponse(cached.bytes, cached.mimeType)
135 |             }
136 |             val cachedImageBytes = ForPdaCoil.loadCachedImageBytesSync(view.context, resolvedAvatarUrl)
137 |             val loadedAt = SystemClock.elapsedRealtime()
138 |             if (cachedImageBytes != null) {
139 |                 logAvatarInterceptTiming(
140 |                     type = type,
141 |                     resolveMs = resolvedAt - resolveStartedAt,
142 |                     loadMs = loadedAt - resolvedAt,
143 |                     encodeMs = 0,
144 |                     totalMs = loadedAt - startedAt,
145 |                     bytes = cachedImageBytes.bytes.size,
146 |                     cacheHit = false,
147 |                     skipped = false,
148 |                     source = "diskBytes"
149 |                 )
150 |                 if (cachedImageBytes.bytes.size <= AVATAR_RESPONSE_MAX_ENTRY_BYTES) {
151 |                     avatarResponseCache.put(
152 |                         resolvedAvatarUrl,
153 |                         AvatarCachedResponse(cachedImageBytes.mimeType, cachedImageBytes.bytes)
154 |                     )
155 |                 }
156 |                 return avatarResponse(cachedImageBytes.bytes, cachedImageBytes.mimeType)
157 |             }
158 |             val bitmap = ForPdaCoil.loadBitmapSync(view.context, resolvedAvatarUrl, allowNetwork = false)
159 |             val bitmapLoadedAt = SystemClock.elapsedRealtime()
160 |             val avatarBytes = convertToPngBytes(bitmap)
161 |             val encodedAt = SystemClock.elapsedRealtime()
162 |             logAvatarInterceptTiming(
163 |                 type = type,
164 |                 resolveMs = resolvedAt - resolveStartedAt,
165 |                 loadMs = bitmapLoadedAt - loadedAt,
166 |                 encodeMs = encodedAt - bitmapLoadedAt,
167 |                 totalMs = encodedAt - startedAt,
168 |                 bytes = avatarBytes?.size ?: 0,
169 |                 cacheHit = false,
170 |                 skipped = avatarBytes == null,
171 |                 source = "bitmapEncode"
172 |             )
173 |             if (avatarBytes == null) {
174 |                 return null
175 |             }
176 |             if (avatarBytes.size <= AVATAR_RESPONSE_MAX_ENTRY_BYTES) {
177 |                 avatarResponseCache.put(
178 |                     resolvedAvatarUrl,
179 |                     AvatarCachedResponse("image/png", avatarBytes)
180 |                 )
181 |             }
182 |             avatarResponse(avatarBytes, "image/png")
183 |         } catch (e: Exception) {
184 |             Timber.e(e, "Avatar intercept error")
185 |             null
186 |         }
187 |     }
188 | 
189 |     private fun avatarResponse(bytes: ByteArray, mimeType: String): WebResourceResponse {
190 |         return WebResourceResponse(
191 |             mimeType,
192 |             null,
193 |             ByteArrayInputStream(bytes)
194 |         )
195 |     }
196 | 
197 |     private fun logAvatarInterceptTiming(
198 |         type: String,
199 |         resolveMs: Long,
200 |         loadMs: Long,
201 |         encodeMs: Long,
202 |         totalMs: Long,
203 |         bytes: Int,
204 |         cacheHit: Boolean,
205 |         skipped: Boolean,
206 |         source: String
207 |     ) {
208 |         if (totalMs < AVATAR_INTERCEPT_SLOW_LOG_MS && encodeMs == 0L) return
209 |         if (!BuildConfig.DEBUG) return
210 |         Timber.d(
211 |             "$LOG_TAG avatarIntercept type=%s source=%s resolveMs=%d loadMs=%d encodeMs=%d totalMs=%d bytes=%d cacheHit=%s skipped=%s",
212 |             type,
213 |             source,
214 |             resolveMs,
215 |             loadMs,
216 |             encodeMs,
217 |             totalMs,
218 |             bytes,
219 |             cacheHit,
220 |             skipped
221 |         )
222 |     }
223 | 
224 |     fun convert(base64Str: String): Bitmap {
225 |         val decodedBytes = Base64.decode(
226 |             base64Str.substring(base64Str.indexOf(",") + 1),
227 |             Base64.DEFAULT
228 |         )
229 |         return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
230 |     }
231 | 
232 |     fun convert(bitmap: Bitmap?): String? {
233 |         return convertToPngBytes(bitmap)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
234 |     }
235 | 
236 |     fun convertToPngBytes(bitmap: Bitmap?): ByteArray? {
237 |         if (bitmap == null) return null
238 |         val outputStream = ByteArrayOutputStream()
239 |         bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
240 |         return outputStream.toByteArray()
241 |     }
242 | 
243 |     override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
244 |         initDependencies(view.context)
245 |         return handleUri(view, Uri.parse(url))
246 |     }
247 | 
248 |     @TargetApi(Build.VERSION_CODES.N)
249 |     override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
250 |         initDependencies(view.context)
251 |         return handleUri(view, request.url)
252 |     }
253 | 
254 |     open fun handleUri(view: WebView, uri: Uri): Boolean {
255 |         return when (val decision = UrlPolicy.classify(uri.toString())) {
256 |             UrlDecision.Blocked -> {
257 |                 Timber.w("Blocked unsafe WebView URL")
258 |                 true
259 |             }
260 |             is UrlDecision.External -> {
261 |                 systemLinkHandler?.handle(decision.normalizedUrl)
262 |                 true
263 |             }
264 |             is UrlDecision.Internal -> {
265 |                 val safeUri = Uri.parse(decision.normalizedUrl)
266 |                 if (isDownloadableFile(safeUri)) {
267 |                     downloadFile(view.context, safeUri)
268 |                     true
269 |                 } else {
270 |                     false
271 |                 }
272 |             }
273 |         }
274 |     }
275 | 
276 |     protected fun shouldOpenExternally(uri: Uri): Boolean {
277 |         return UrlPolicy.classify(uri.toString()) is UrlDecision.External
278 |     }
279 | 
280 |     private fun isDownloadableFile(uri: Uri): Boolean {
281 |         val url = uri.toString()
282 |         if (cachePattern.matcher(url).find()) return false
283 |         if (!SiteUrls.isSiteUri(uri)) return false
284 |         val lowerUrl = url.lowercase()
285 |         if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
286 |             lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
287 |             lowerUrl.endsWith(".bmp") || lowerUrl.endsWith(".webp")) return false
288 |         val hasExtension = DOWNLOAD_PATTERN.matcher(url).matches()
289 |         val is4pdaDownload = P4PDA_DOWNLOAD_PATTERN.matcher(url).matches()
290 |         return hasExtension || is4pdaDownload
291 |     }
292 | 
293 |     private fun downloadFile(context: Context, uri: Uri) {
294 |         // systemLinkHandler is constructor-nullable (tests and minimal WebView contexts).
295 |         // If it is null, the caller (DownloadListener) must not have been wired — silently drop.
296 |         val handler = systemLinkHandler ?: return
297 |         handler.handleDownload(uri.toString(), null, context)
298 |     }
299 | 
300 |     override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
301 |         super.onReceivedSslError(view, handler, error)
302 |     }
303 | 
304 |     @TargetApi(Build.VERSION_CODES.M)
305 |     override fun onReceivedError(
306 |             view: WebView,
307 |             request: WebResourceRequest,
308 |             error: WebResourceError
309 |     ) {
310 |         if (request.isForMainFrame) {
311 |             onMainFrameLoadError(
312 |                     view,
313 |                     request,
314 |                     error.errorCode,
315 |                     error.description?.toString()
316 |             )
317 |         }
318 |         super.onReceivedError(view, request, error)
319 |     }
320 | 
321 |     @Suppress("DEPRECATION")
322 |     override fun onReceivedError(
323 |             view: WebView,
324 |             errorCode: Int,
325 |             description: String?,
326 |             failingUrl: String?
327 |     ) {
328 |         if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
329 |             onMainFrameLoadError(view, null, errorCode, description)
330 |         }
331 |         super.onReceivedError(view, errorCode, description, failingUrl)
332 |     }
333 | 
334 |     @TargetApi(Build.VERSION_CODES.M)
335 |     override fun onReceivedHttpError(
336 |             view: WebView,
337 |             request: WebResourceRequest,
338 |             errorResponse: WebResourceResponse
339 |     ) {
340 |         if (request.isForMainFrame) {
341 |             onMainFrameHttpError(view, request, errorResponse.statusCode)
342 |         }
343 |         super.onReceivedHttpError(view, request, errorResponse)
344 |     }
345 | 
346 |     /** Main document failed to load (network/DNS/SSL, etc.). Subresources are ignored. */
347 |     protected open fun onMainFrameLoadError(
348 |             view: WebView,
349 |             request: WebResourceRequest?,
350 |             errorCode: Int,
351 |             description: String?
352 |     ) {
353 |     }
354 | 
355 |     /** HTTP 4xx/5xx on the main document. Subresources are ignored. */
356 |     protected open fun onMainFrameHttpError(
357 |             view: WebView,
358 |             request: WebResourceRequest?,
359 |             statusCode: Int
360 |     ) {
361 |     }
362 | }
363 | 
```

### app/src/main/java/forpdateam/ru/forpda/di/DataModule.kt

Bytes: 12932
SHA-256: 8687185e2efc5f714637ce941d53577545ed10ba1e385662db4d07b395afb968
Lines: 1-283 of 283

```text
  1 | package forpdateam.ru.forpda.di
  2 | 
  3 | import android.app.Application
  4 | import android.content.Context
  5 | import android.content.SharedPreferences
  6 | import dagger.Module
  7 | import dagger.Provides
  8 | import dagger.hilt.InstallIn
  9 | import dagger.hilt.android.qualifiers.ApplicationContext
 10 | import dagger.hilt.components.SingletonComponent
 11 | import forpdateam.ru.forpda.model.AuthHolder
 12 | import forpdateam.ru.forpda.model.CountersHolder
 13 | import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
 14 | import forpdateam.ru.forpda.model.data.cache.forum.ForumCacheRoom
 15 | import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
 16 | import forpdateam.ru.forpda.model.data.cache.history.HistoryCacheRoom
 17 | import forpdateam.ru.forpda.model.data.cache.notes.NotesCacheRoom
 18 | import forpdateam.ru.forpda.model.data.cache.qms.QmsCacheRoom
 19 | import forpdateam.ru.forpda.entity.db.notes.NoteFolderDao
 20 | import forpdateam.ru.forpda.entity.db.notes.NoteItemDao
 21 | import forpdateam.ru.forpda.entity.db.notes.AppDatabase
 22 | import forpdateam.ru.forpda.entity.db.notes.NotesMigrations
 23 | import forpdateam.ru.forpda.entity.db.history.HistoryItemDao
 24 | import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
 25 | import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatDao
 26 | import forpdateam.ru.forpda.entity.db.ForumUserDao
 27 | import forpdateam.ru.forpda.entity.db.qms.QmsContactDao
 28 | import forpdateam.ru.forpda.entity.db.qms.QmsThemeDao
 29 | import forpdateam.ru.forpda.entity.db.qms.QmsThemesDao
 30 | import androidx.room.Room
 31 | import forpdateam.ru.forpda.model.data.providers.UserSourceProvider
 32 | import forpdateam.ru.forpda.model.data.remote.IWebClient
 33 | import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
 34 | import forpdateam.ru.forpda.model.data.remote.api.auth.AuthApi
 35 | import forpdateam.ru.forpda.model.data.remote.api.devdb.DevDbApi
 36 | import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi
 37 | import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
 38 | import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
 39 | import forpdateam.ru.forpda.model.data.remote.api.forum.ForumApi
 40 | import forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsApi
 41 | import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
 42 | import forpdateam.ru.forpda.model.data.remote.api.profile.ProfileApi
 43 | import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
 44 | import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
 45 | import forpdateam.ru.forpda.model.data.remote.api.search.SearchApi
 46 | import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
 47 | import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
 48 | import forpdateam.ru.forpda.model.data.storage.ExternalStorageProvider
 49 | import forpdateam.ru.forpda.model.data.storage.IPatternProvider
 50 | import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
 51 | import forpdateam.ru.forpda.model.interactors.other.MenuRepository
 52 | import forpdateam.ru.forpda.model.preferences.*
 53 | import forpdateam.ru.forpda.model.repository.auth.AuthRepository
 54 | import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
 55 | import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
 56 | import forpdateam.ru.forpda.model.repository.events.EventsRepository
 57 | import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
 58 | import forpdateam.ru.forpda.model.repository.forum.ForumRepository
 59 | import forpdateam.ru.forpda.model.repository.history.HistoryRepository
 60 | import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
 61 | import forpdateam.ru.forpda.model.repository.news.NewsRepository
 62 | import forpdateam.ru.forpda.model.interactors.news.ArticleDiskCache
 63 | import forpdateam.ru.forpda.model.interactors.news.ArticleMemoryCache
 64 | import forpdateam.ru.forpda.model.interactors.news.ArticlePrefetchService
 65 | import forpdateam.ru.forpda.model.interactors.theme.ThemePrefetchService
 66 | import forpdateam.ru.forpda.model.interactors.news.ArticleReadingProgressStore
 67 | import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
 68 | import forpdateam.ru.forpda.model.repository.note.NotesRepository
 69 | import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
 70 | import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
 71 | import forpdateam.ru.forpda.model.repository.qms.QmsRepository
 72 | import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
 73 | import forpdateam.ru.forpda.model.repository.search.ForumSectionTitleIndex
 74 | import forpdateam.ru.forpda.model.repository.search.SearchRepository
 75 | import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
 76 | import forpdateam.ru.forpda.model.repository.topics.TopicsRepository
 77 | import forpdateam.ru.forpda.model.NetworkStateProvider
 78 | import forpdateam.ru.forpda.entity.app.profile.IUserHolder
 79 | import javax.inject.Singleton
 80 | 
 81 | @Module
 82 | @InstallIn(SingletonComponent::class)
 83 | object DataModule {
 84 | 
 85 |     // region Caches
 86 |     @Provides @Singleton fun provideUserSource(qmsApi: QmsApi) = UserSourceProvider(qmsApi)
 87 |     @Provides @Singleton
 88 |     fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
 89 |         return Room.databaseBuilder(
 90 |             context,
 91 |             AppDatabase::class.java,
 92 |             "forpda_database"
 93 |         ).addMigrations(
 94 |             NotesMigrations.MIGRATION_1_2,
 95 |             NotesMigrations.MIGRATION_2_3,
 96 |             NotesMigrations.MIGRATION_3_4,
 97 |             NotesMigrations.MIGRATION_4_5,
 98 |             NotesMigrations.MIGRATION_5_6,
 99 |             NotesMigrations.MIGRATION_6_7,
100 |             NotesMigrations.MIGRATION_7_8
101 |         )
102 |             .build()
103 |     }
104 | 
105 |     @Provides @Singleton
106 |     fun provideNoteItemDao(database: AppDatabase): NoteItemDao = database.noteItemDao()
107 | 
108 |     @Provides @Singleton
109 |     fun provideNoteFolderDao(database: AppDatabase): NoteFolderDao = database.noteFolderDao()
110 | 
111 |     @Provides @Singleton
112 |     fun provideHistoryItemDao(database: AppDatabase): HistoryItemDao = database.historyItemDao()
113 | 
114 |     @Provides @Singleton
115 |     fun provideQmsContactDao(database: AppDatabase): QmsContactDao = database.qmsContactDao()
116 | 
117 |     @Provides @Singleton
118 |     fun provideQmsThemeDao(database: AppDatabase): QmsThemeDao = database.qmsThemeDao()
119 | 
120 |     @Provides @Singleton
121 |     fun provideQmsThemesDao(database: AppDatabase): QmsThemesDao = database.qmsThemesDao()
122 | 
123 |     @Provides @Singleton
124 |     fun provideFavItemDao(database: AppDatabase): FavItemDao = database.favItemDao()
125 | 
126 |     @Provides @Singleton
127 |     fun provideForumItemFlatDao(database: AppDatabase): ForumItemFlatDao = database.forumItemFlatDao()
128 | 
129 |     @Provides @Singleton
130 |     fun provideForumUserDao(database: AppDatabase): ForumUserDao = database.forumUserDao()
131 | 
132 |     @Provides @Singleton fun provideNotesCacheRoom(noteItemDao: NoteItemDao, noteFolderDao: NoteFolderDao) =
133 |             NotesCacheRoom(noteItemDao, noteFolderDao)
134 |     @Provides @Singleton fun provideHistoryCacheRoom(historyItemDao: HistoryItemDao) = HistoryCacheRoom(historyItemDao)
135 |     @Provides @Singleton fun provideFavoritesCacheRoom(favItemDao: FavItemDao) = FavoritesCacheRoom(favItemDao)
136 |     @Provides @Singleton fun provideForumCacheRoom(forumItemFlatDao: ForumItemFlatDao) = ForumCacheRoom(forumItemFlatDao)
137 |     @Provides @Singleton fun provideForumUsersCacheRoom(forumUserDao: ForumUserDao, userSource: UserSourceProvider) = ForumUsersCacheRoom(forumUserDao, userSource)
138 |     @Provides @Singleton fun provideQmsCacheRoom(qmsContactDao: QmsContactDao, qmsThemeDao: QmsThemeDao, qmsThemesDao: QmsThemesDao) = QmsCacheRoom(qmsContactDao, qmsThemeDao, qmsThemesDao)
139 |     // endregion
140 | 
141 |     // region Repositories
142 |     @Provides @Singleton
143 |     fun provideAvatarRepository(fucRoom: ForumUsersCacheRoom) = AvatarRepository(fucRoom)
144 | 
145 |     @Provides @Singleton
146 |     fun provideFavoritesRepository(
147 |             api: FavoritesApi,
148 |             cacheRoom: FavoritesCacheRoom,
149 |             authHolder: AuthHolder,
150 |             countersHolder: CountersHolder,
151 |             listsPrefs: ListsPreferencesHolder,
152 |             notifPrefs: NotificationPreferencesHolder,
153 |             eventsApi: NotificationEventsApi
154 |     ) = FavoritesRepository(api, cacheRoom, authHolder, countersHolder, listsPrefs, notifPrefs, eventsApi)
155 | 
156 |     @Provides @Singleton
157 |     fun provideHistoryRepository(cacheRoom: HistoryCacheRoom) =
158 |             HistoryRepository(cacheRoom)
159 | 
160 |     @Provides @Singleton
161 |     fun provideMentionsRepository(api: MentionsApi, preferences: SharedPreferences) =
162 |             MentionsRepository(api, preferences)
163 | 
164 |     @Provides @Singleton
165 |     fun provideAuthRepository(
166 |             api: AuthApi,
167 |             authHolder: AuthHolder,
168 |             countersHolder: CountersHolder,
169 |             userHolder: IUserHolder
170 |     ) = AuthRepository(api, authHolder, countersHolder, userHolder)
171 | 
172 |     @Provides @Singleton
173 |     fun provideProfileRepository(
174 |             api: ProfileApi,
175 |             userHolder: IUserHolder,
176 |             authHolder: AuthHolder,
177 |             fuc: ForumUsersCacheRoom
178 |     ) = ProfileRepository(api, userHolder, authHolder, fuc)
179 | 
180 |     @Provides @Singleton
181 |     fun provideReputationRepository(api: ReputationApi) =
182 |             ReputationRepository(api)
183 | 
184 |     @Provides @Singleton
185 |     fun provideForumRepository(api: ForumApi, cacheRoom: ForumCacheRoom) =
186 |             ForumRepository(api, cacheRoom)
187 | 
188 |     @Provides @Singleton
189 |     fun provideForumSectionTitleIndex() = ForumSectionTitleIndex()
190 | 
191 |     @Provides @Singleton
192 |     fun provideTopicsRepository(api: TopicsApi, forumSectionTitleIndex: ForumSectionTitleIndex) =
193 |             TopicsRepository(api, forumSectionTitleIndex)
194 | 
195 |     @Provides @Singleton
196 |     fun provideThemeRepository(api: ThemeApi, hcRoom: HistoryCacheRoom, fucRoom: ForumUsersCacheRoom) =
197 |             ThemeRepository(api, hcRoom, fucRoom)
198 | 
199 |     @Provides @Singleton
200 |     fun provideThemePrefetchService(themeRepository: ThemeRepository) =
201 |             ThemePrefetchService(themeRepository)
202 | 
203 |     @Provides @Singleton
204 |     fun provideQmsRepository(
205 |             api: QmsApi,
206 |             attachmentsApi: AttachmentsApi,
207 |             cacheRoom: QmsCacheRoom,
208 |             fucRoom: ForumUsersCacheRoom,
209 |             countersHolder: CountersHolder
210 |     ) = QmsRepository(api, attachmentsApi, cacheRoom, fucRoom, countersHolder)
211 | 
212 |     @Provides @Singleton
213 |     fun provideSearchRepository(api: SearchApi, fucRoom: ForumUsersCacheRoom, forumSectionTitleIndex: ForumSectionTitleIndex) =
214 |             SearchRepository(api, fucRoom, forumSectionTitleIndex)
215 | 
216 |     @Provides @Singleton
217 |     fun provideNewsRepository(api: NewsApi, fucRoom: ForumUsersCacheRoom) =
218 |             NewsRepository(api, fucRoom)
219 | 
220 |     @Provides @Singleton
221 |     fun provideArticleDiskCache(@ApplicationContext context: Context) =
222 |             ArticleDiskCache(context)
223 | 
224 |     @Provides @Singleton
225 |     fun provideArticleMemoryCache() = ArticleMemoryCache()
226 | 
227 |     @Provides @Singleton
228 |     fun provideArticleReadingProgressStore(@ApplicationContext context: Context) =
229 |             ArticleReadingProgressStore(context)
230 | 
231 |     @Provides @Singleton
232 |     fun provideArticlePrefetchService(
233 |             newsRepository: NewsRepository,
234 |             articleTemplate: ArticleTemplate,
235 |             diskCache: ArticleDiskCache,
236 |             memoryCache: ArticleMemoryCache
237 |     ) = ArticlePrefetchService(newsRepository, articleTemplate, diskCache, memoryCache)
238 | 
239 |     @Provides @Singleton
240 |     fun provideDevDbRepository(api: DevDbApi) =
241 |             DevDbRepository(api)
242 | 
243 |     @Provides @Singleton
244 |     fun provideEditPostRepository(@ApplicationContext context: Context, api: EditPostApi, attachmentsApi: AttachmentsApi, fucRoom: ForumUsersCacheRoom) =
245 |             PostEditorRepository(context, api, attachmentsApi, fucRoom)
246 | 
247 |     @Provides @Singleton
248 |     fun provideNotesRepository(cacheRoom: NotesCacheRoom, es: ExternalStorageProvider) =
249 |             NotesRepository(cacheRoom, es)
250 | 
251 |     @Provides @Singleton
252 |     fun provideEventsRepository(
253 |             @ApplicationContext context: Context,
254 |             application: Application,
255 |             wc: IWebClient,
256 |             api: NotificationEventsApi,
257 |             networkState: NetworkStateProvider,
258 |             authHolder: AuthHolder,
259 |             countersHolder: CountersHolder,
260 |             notifPrefs: NotificationPreferencesHolder,
261 |             mentionsRepository: MentionsRepository
262 |     ) = EventsRepository(context, application, wc, api, networkState, authHolder, countersHolder, notifPrefs, mentionsRepository)
263 | 
264 |     @Provides @Singleton
265 |     fun provideMenuRepository(
266 |             preferences: SharedPreferences,
267 |             authHolder: AuthHolder,
268 |             countersHolder: CountersHolder,
269 |             listsPreferencesHolder: ListsPreferencesHolder
270 |     ) = MenuRepository(preferences, authHolder, countersHolder, listsPreferencesHolder)
271 | 
272 |     // endregion
273 | 
274 |     // region Interactors
275 |     @Provides @Singleton
276 |     fun provideQmsInteractor(
277 |             qmsRepository: QmsRepository,
278 |             eventsRepository: EventsRepository,
279 |             qmsApi: QmsApi
280 |     ) = QmsInteractor(qmsRepository, eventsRepository, qmsApi)
281 |     // endregion
282 | }
283 | 
```

### app/src/main/java/forpdateam/ru/forpda/diagnostic/ColdStartTracer.kt

Bytes: 4349
SHA-256: 7e86ec415e1800f07dfd76bb72f2d2cad5de61f775168df200b90780c42a3f79
Lines: 1-121 of 121

```text
  1 | package forpdateam.ru.forpda.diagnostic
  2 | 
  3 | import android.os.SystemClock
  4 | import timber.log.Timber
  5 | 
  6 | /**
  7 |  * Lightweight in-process cold-start tracer.
  8 |  *
  9 |  * Why this exists:
 10 |  * - StrictMode is enabled only in debug builds (see [forpdateam.ru.forpda.App.setupStrictMode]).
 11 |  *   We have no release-side signal for "how long did cold start take on real devices".
 12 |  * - Sending data to AppMetrica requires explicit consent and is gated on the `store` flavor.
 13 |  *   We want a privacy-free, always-on fallback that does **not** leave the process.
 14 |  *
 15 |  * Usage:
 16 |  * 1. Call [mark] from the earliest possible hook (e.g. the very first line of
 17 |  *    `Application.attachBaseContext` or `Application.onCreate`) with a phase name.
 18 |  * 2. Call [snapshot] later (e.g. in `MainActivity.onResume`) to log the full trace.
 19 |  * 3. Call [reset] only between cold starts; do not call it from `onResume`.
 20 |  *
 21 |  * This class is intentionally a singleton with no synchronization beyond a
 22 |  * volatile read of the mark list. The clock source is [SystemClock.elapsedRealtime],
 23 |  * which is monotonic and survives deep sleep.
 24 |  */
 25 | object ColdStartTracer {
 26 | 
 27 |     private const val MAX_MARKS = 16
 28 |     private const val LOG_TAG = "ColdStart"
 29 | 
 30 |     private data class Mark(val name: String, val elapsedRealtimeMs: Long)
 31 | 
 32 |     @Volatile
 33 |     private var processStartElapsedMs: Long = -1L
 34 | 
 35 |     @Volatile
 36 |     private var marks: List<Mark> = emptyList()
 37 | 
 38 |     /**
 39 |      * Indirection over [SystemClock.elapsedRealtime] so unit tests can swap a
 40 |      * non-zero monotonic clock. Robolectric / `unitTests.returnDefaultValues`
 41 |      * make the real clock return `0L`, which collides with the
 42 |      * "anchor not yet set" sentinel below; routing through this var lets
 43 |      * tests advance the clock without poking at internals.
 44 |      */
 45 |     internal var clock: () -> Long = { SystemClock.elapsedRealtime() }
 46 | 
 47 |     /**
 48 |      * Records the process-start anchor. Call exactly once, as early as possible
 49 |      * in the process lifecycle (best-effort: from `Application.attachBaseContext`).
 50 |      *
 51 |      * Note: we do not skip when the clock reports `0L` — that is a legal
 52 |      * monotonic value (the first millisecond of uptime). A sentinel of
 53 |      * `-1L` (or any negative value) is used to mean "not yet set" so the
 54 |      * guard no longer collides with a real `0L` reading.
 55 |      */
 56 |     fun markProcessStart() {
 57 |         val now = clock()
 58 |         if (processStartElapsedMs < 0L) {
 59 |             processStartElapsedMs = now
 60 |         }
 61 |     }
 62 | 
 63 |     /**
 64 |      * Records a named checkpoint relative to [markProcessStart].
 65 |      * Silently drops marks past [MAX_MARKS] to bound memory.
 66 |      */
 67 |     fun mark(name: String) {
 68 |         if (processStartElapsedMs < 0L) {
 69 |             // markProcessStart() was not called; do not invent a baseline.
 70 |             return
 71 |         }
 72 |         val now = clock()
 73 |         synchronized(this) {
 74 |             if (marks.size >= MAX_MARKS) return
 75 |             marks = marks + Mark(name, now)
 76 |         }
 77 |     }
 78 | 
 79 |     /**
 80 |      * Returns a human-readable summary suitable for logcat. Never returns null.
 81 |      */
 82 |     fun snapshot(): String {
 83 |         val start = processStartElapsedMs
 84 |         if (start < 0L) return "$LOG_TAG: no process-start anchor recorded"
 85 |         val copy = synchronized(this) { marks }
 86 |         if (copy.isEmpty()) {
 87 |             val total = clock() - start
 88 |             return "$LOG_TAG: total=${total}ms (no marks)"
 89 |         }
 90 |         val sb = StringBuilder(LOG_TAG).append(": total=")
 91 |             .append(clock() - start).append("ms [")
 92 |         copy.forEachIndexed { i, m ->
 93 |             if (i > 0) sb.append(", ")
 94 |             sb.append(m.name).append('=').append(m.elapsedRealtimeMs - start).append("ms")
 95 |         }
 96 |         return sb.append(']').toString()
 97 |     }
 98 | 
 99 |     /**
100 |      * Emits the current snapshot via [Timber]. Safe to call from any thread.
101 |      */
102 |     fun logSnapshot() {
103 |         Timber.tag(LOG_TAG).i(snapshot())
104 |     }
105 | 
106 |     /**
107 |      * Clears marks between cold starts. Does **not** reset the process-start
108 |      * anchor in production. Tests that need a clean slate can pass
109 |      * `resetAnchor = true` (e.g. via [setUp] / [org.junit.Before]).
110 |      */
111 |     @JvmOverloads
112 |     fun reset(resetAnchor: Boolean = false) {
113 |         synchronized(this) {
114 |             marks = emptyList()
115 |             if (resetAnchor) {
116 |                 processStartElapsedMs = -1L
117 |             }
118 |         }
119 |     }
120 | }
121 | 
```

### app/src/main/java/forpdateam/ru/forpda/diagnostic/FpdaDebugLog.kt

Bytes: 8114
SHA-256: e37d2a911920093c47b18c5b8180a99c365a41057e8783fee57f46c2115e64b1
Lines: 1-213 of 213

```text
  1 | package forpdateam.ru.forpda.diagnostic
  2 | 
  3 | import android.net.Uri
  4 | import forpdateam.ru.forpda.BuildConfig
  5 | import timber.log.Timber
  6 | import java.security.MessageDigest
  7 | import java.util.Locale
  8 | import java.util.UUID
  9 | 
 10 | /**
 11 |  * DEBUG-only structured single-line logs with stable logcat tags.
 12 |  *
 13 |  * Privacy: never log cookies, tokens, passwords, full HTML, or private message bodies.
 14 |  */
 15 | object FpdaDebugLog {
 16 | 
 17 |     const val TAG_THEME_OPEN = "FPDA_THEME_OPEN"
 18 |     /** Backward-compatible alias for [TAG_THEME_OPEN] in logcat filters. */
 19 |     const val TAG_TOPIC_OPEN = TAG_THEME_OPEN
 20 |     const val TAG_THEME_LOAD = "FPDA_THEME_LOAD"
 21 |     const val TAG_THEME_RENDER = "FPDA_THEME_RENDER"
 22 |     /** Backward-compatible alias for [TAG_THEME_RENDER] in logcat filters. */
 23 |     const val TAG_WEBVIEW_RENDER = TAG_THEME_RENDER
 24 |     const val TAG_TOPIC_SCROLL = "FPDA_TOPIC_SCROLL"
 25 |     const val TAG_TOPIC_READSTATE = "FPDA_TOPIC_READSTATE"
 26 |     const val TAG_ARTICLE_OPEN = "FPDA_ARTICLE_OPEN"
 27 |     const val TAG_ARTICLE_PARSE = "FPDA_ARTICLE_PARSE"
 28 |     const val TAG_ARTICLE_CACHE = "FPDA_ARTICLE_CACHE"
 29 |     const val TAG_COMMENTS_SECTION = "FPDA_COMMENTS_SECTION"
 30 |     const val TAG_STATE_RACE = "FPDA_STATE_RACE"
 31 |     const val TAG_NAV_BACKSTACK = "FPDA_NAV_BACKSTACK"
 32 |     const val TAG_TOPIC_SWITCH = "FPDA_TOPIC_SWITCH"
 33 |     const val TAG_QMS_WEBVIEW = "FPDA_QMS_WEBVIEW"
 34 |     const val TAG_QMS_CHAT = "FPDA_QMS_CHAT"
 35 |     const val TAG_QMS_OPEN = "FPDA_QMS_OPEN"
 36 |     const val TAG_QMS_NETWORK = "FPDA_QMS_NETWORK"
 37 |     const val TAG_QMS_PARSE = "FPDA_QMS_PARSE"
 38 |     const val TAG_QMS_STATE = "FPDA_QMS_STATE"
 39 |     const val TAG_QMS_CACHE = "FPDA_QMS_CACHE"
 40 |     const val TAG_COMMENT_ACTION = "FPDA_COMMENT_ACTION"
 41 |     const val TAG_ARTICLE_DEFERRED = "FPDA_ARTICLE_DEFERRED"
 42 |     const val TAG_ARTICLE_POLL = "FPDA_ARTICLE_POLL"
 43 |     const val TAG_ARTICLE_RENDER = "FPDA_ARTICLE_RENDER"
 44 |     /** Backward-compatible alias for article WebView load phases. */
 45 |     const val TAG_ARTICLE_WEBVIEW = TAG_ARTICLE_RENDER
 46 |     const val TAG_SMART_BUTTON = "FPDA_SMART_BUTTON"
 47 |     const val TAG_WEBVIEW_BLANK = "FPDA_WEBVIEW_BLANK"
 48 |     const val TAG_FAVORITES_UNREAD = "FPDA_FAVORITES_UNREAD"
 49 |     const val TAG_THEME_POST_READ_STATE = "FPDA_THEME_POST_READ_STATE"
 50 |     const val TAG_TOPIC_HIGHLIGHT = "PPDA_TOPIC_HIGHLIGHT"
 51 | 
 52 |     private val sensitiveQueryKeys = setOf(
 53 |             "auth_key",
 54 |             "session_id",
 55 |             "sid",
 56 |             "pass",
 57 |             "password",
 58 |             "token",
 59 |             "key",
 60 |             "cookie",
 61 |             "member_id"
 62 |     )
 63 | 
 64 |     fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(8)
 65 | 
 66 |     fun log(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
 67 |         if (!BuildConfig.DEBUG) return
 68 |         val parts = buildList {
 69 |             add("event=$event")
 70 |             fields.forEach { (key, value) ->
 71 |                 if (value != null) add("$key=$value")
 72 |             }
 73 |         }
 74 |         Timber.tag(tag).i(parts.joinToString(separator = " "))
 75 |     }
 76 | 
 77 |     fun warn(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
 78 |         if (!BuildConfig.DEBUG) return
 79 |         val parts = buildList {
 80 |             add("event=$event")
 81 |             fields.forEach { (key, value) ->
 82 |                 if (value != null) add("$key=$value")
 83 |             }
 84 |         }
 85 |         Timber.tag(tag).w(parts.joinToString(separator = " "))
 86 |     }
 87 | 
 88 |     fun sanitizeUrl(url: String?): String? {
 89 |         if (url.isNullOrBlank()) return url
 90 |         return runCatching {
 91 |             val uri = Uri.parse(url.trim())
 92 |             val builder = uri.buildUpon().clearQuery()
 93 |             uri.queryParameterNames
 94 |                     .filter { name -> sensitiveQueryKeys.none { name.equals(it, ignoreCase = true) } }
 95 |                     .sorted()
 96 |                     .forEach { name ->
 97 |                         uri.getQueryParameters(name).forEach { value ->
 98 |                             builder.appendQueryParameter(name, value)
 99 |                         }
100 |                     }
101 |             val fragment = uri.encodedFragment?.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()
102 |             (builder.build().toString() + fragment).take(512)
103 |         }.getOrElse {
104 |             url.trim().take(512)
105 |         }
106 |     }
107 | 
108 |     fun errorClass(t: Throwable?): String? =
109 |             t?.let { it::class.java.simpleName.ifEmpty { it::class.java.name } }
110 | 
111 |     /**
112 |      * Safe HTML diagnostics: length + short hash + coarse markers — never log raw markup/snippets.
113 |      */
114 |     fun classifyHtml(html: String?): Map<String, Any?> {
115 |         val body = html.orEmpty()
116 |         if (body.isEmpty()) {
117 |             return mapOf("htmlLen" to 0, "htmlHash" to "empty")
118 |         }
119 |         val sample = body.take(8192)
120 |         return mapOf(
121 |                 "htmlLen" to body.length,
122 |                 "htmlHash" to sha256Hex(sample),
123 |                 "hasForm" to body.contains("<form", ignoreCase = true),
124 |                 "hasArticle" to body.contains("<article", ignoreCase = true),
125 |                 "hasCommentList" to (
126 |                         body.contains("comment-list", ignoreCase = true) ||
127 |                                 body.contains("comments-list", ignoreCase = true)
128 |                         ),
129 |                 "hasMessList" to body.contains("mess_list", ignoreCase = true)
130 |         )
131 |     }
132 | 
133 |     private fun sha256Hex(text: String): String {
134 |         val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
135 |         return digest.joinToString(separator = "") { byte ->
136 |             "%02x".format(Locale.US, byte)
137 |         }.take(16)
138 |     }
139 | 
140 |     enum class QmsArea { OPEN, NETWORK, PARSE, STATE, CACHE, WEBVIEW, CHAT }
141 | 
142 |     enum class ThemeArea { OPEN, LOAD, RENDER, SCROLL, SMART_BUTTON }
143 | 
144 |     enum class ArticleArea { OPEN, PARSE, POLL, RENDER, CACHE, WEBVIEW }
145 | 
146 |     fun logQms(
147 |             area: QmsArea,
148 |             event: String,
149 |             fields: Map<String, Any?> = emptyMap(),
150 |             warn: Boolean = false
151 |     ) {
152 |         val tag = when (area) {
153 |             QmsArea.OPEN -> TAG_QMS_OPEN
154 |             QmsArea.NETWORK -> TAG_QMS_NETWORK
155 |             QmsArea.PARSE -> TAG_QMS_PARSE
156 |             QmsArea.STATE -> TAG_QMS_STATE
157 |             QmsArea.CACHE -> TAG_QMS_CACHE
158 |             QmsArea.WEBVIEW -> TAG_QMS_WEBVIEW
159 |             QmsArea.CHAT -> TAG_QMS_CHAT
160 |         }
161 |         if (warn) warn(tag, event, fields) else log(tag, event, fields)
162 |     }
163 | 
164 |     fun logTheme(
165 |             area: ThemeArea,
166 |             event: String,
167 |             fields: Map<String, Any?> = emptyMap(),
168 |             warn: Boolean = false
169 |     ) {
170 |         val tag = when (area) {
171 |             ThemeArea.OPEN -> TAG_THEME_OPEN
172 |             ThemeArea.LOAD -> TAG_THEME_LOAD
173 |             ThemeArea.RENDER -> TAG_THEME_RENDER
174 |             ThemeArea.SCROLL -> TAG_TOPIC_SCROLL
175 |             ThemeArea.SMART_BUTTON -> TAG_SMART_BUTTON
176 |         }
177 |         if (warn) warn(tag, event, fields) else log(tag, event, fields)
178 |     }
179 | 
180 |     fun logSmartButton(
181 |             event: String,
182 |             fields: Map<String, Any?> = emptyMap(),
183 |             warn: Boolean = false
184 |     ) {
185 |         logTheme(ThemeArea.SMART_BUTTON, event, fields, warn)
186 |     }
187 | 
188 |     fun logArticle(
189 |             area: ArticleArea,
190 |             event: String,
191 |             fields: Map<String, Any?> = emptyMap(),
192 |             warn: Boolean = false
193 |     ) {
194 |         val tag = when (area) {
195 |             ArticleArea.OPEN -> TAG_ARTICLE_OPEN
196 |             ArticleArea.PARSE -> TAG_ARTICLE_PARSE
197 |             ArticleArea.POLL -> TAG_ARTICLE_POLL
198 |             ArticleArea.RENDER, ArticleArea.WEBVIEW -> TAG_ARTICLE_RENDER
199 |             ArticleArea.CACHE -> TAG_ARTICLE_CACHE
200 |         }
201 |         if (warn) warn(tag, event, fields) else log(tag, event, fields)
202 |     }
203 | 
204 |     fun fieldsWithTrace(traceId: String?, fields: Map<String, Any?> = emptyMap()): Map<String, Any?> =
205 |             if (traceId.isNullOrBlank()) fields else fields + ("traceId" to traceId)
206 | 
207 |     fun fieldsWithGeneration(
208 |             generationId: Int?,
209 |             fields: Map<String, Any?> = emptyMap()
210 |     ): Map<String, Any?> =
211 |             if (generationId == null) fields else fields + ("generationId" to generationId)
212 | }
213 | 
```

### app/src/main/java/forpdateam/ru/forpda/diagnostic/TopicHighlightDiagnostics.kt

Bytes: 9130
SHA-256: 13e6f5be0e98e6cfa6997ad99cab20963d1dd5e91d81421312362dab88c24ad1
Lines: 1-288 of 288

```text
  1 | package forpdateam.ru.forpda.diagnostic
  2 | 
  3 | import forpdateam.ru.forpda.presentation.theme.HighlightType
  4 | 
  5 | /**
  6 |  * DEBUG-only structured tracing for the topic post highlight pipeline.
  7 |  *
  8 |  * Filter logcat: `adb logcat -s PPDA_TOPIC_HIGHLIGHT`
  9 |  *
 10 |  * Events follow the user-facing QA checklist in
 11 |  * `docs/topic-highlight-qa.md`. If a highlight is expected but not visible, these
 12 |  * events show the resolver decision, the render outcome, and any stale-callback
 13 |  * suppression.
 14 |  */
 15 | object TopicHighlightDiagnostics {
 16 | 
 17 |     fun highlightResolveStarted(
 18 |             topicId: Long,
 19 |             hasUnread: Boolean,
 20 |             lastViewed: Boolean,
 21 |             explicit: Boolean,
 22 |             firstUnreadPostId: Long?,
 23 |             lastViewedPostId: Long?,
 24 |             explicitPostId: Long?,
 25 |             pagePostCount: Int
 26 |     ) {
 27 |         log(
 28 |                 "highlight_resolve_started",
 29 |                 linkedMapOf(
 30 |                         "topicId" to topicId,
 31 |                         "hasUnread" to hasUnread,
 32 |                         "lastViewed" to lastViewed,
 33 |                         "explicit" to explicit,
 34 |                         "firstUnreadPostId" to firstUnreadPostId,
 35 |                         "lastViewedPostId" to lastViewedPostId,
 36 |                         "explicitPostId" to explicitPostId,
 37 |                         "pagePostCount" to pagePostCount
 38 |                 )
 39 |         )
 40 |     }
 41 | 
 42 |     fun highlightTargetResolved(
 43 |             topicId: Long,
 44 |             type: HighlightType,
 45 |             postId: Long,
 46 |             reason: String
 47 |     ) {
 48 |         log(
 49 |                 "highlight_target_resolved",
 50 |                 linkedMapOf(
 51 |                         "topicId" to topicId,
 52 |                         "highlightType" to type.jsName,
 53 |                         "highlightPostId" to postId,
 54 |                         "reason" to reason
 55 |                 )
 56 |         )
 57 |     }
 58 | 
 59 |     fun highlightTargetMissing(topicId: Long, reason: String) {
 60 |         log(
 61 |                 "highlight_target_missing",
 62 |                 linkedMapOf(
 63 |                         "topicId" to topicId,
 64 |                         "reason" to reason
 65 |                 )
 66 |         )
 67 |     }
 68 | 
 69 |     fun renderHighlightApplied(
 70 |             topicId: Long,
 71 |             page: Int,
 72 |             renderGenerationId: Int,
 73 |             mode: String,
 74 |             highlightType: String,
 75 |             appliedSuccessfully: Boolean,
 76 |             postAnchorExists: Boolean
 77 |     ) {
 78 |         log(
 79 |                 "render_highlight_applied",
 80 |                 linkedMapOf(
 81 |                         "topicId" to topicId,
 82 |                         "page" to page,
 83 |                         "renderGenerationId" to renderGenerationId,
 84 |                         "mode" to mode,
 85 |                         "highlightType" to highlightType,
 86 |                         "appliedSuccessfully" to appliedSuccessfully,
 87 |                         "postAnchorExists" to postAnchorExists
 88 |                 )
 89 |         )
 90 |     }
 91 | 
 92 |     fun jsHighlightApplied(
 93 |             topicId: Long,
 94 |             page: Int,
 95 |             renderGenerationId: Int,
 96 |             highlightType: String,
 97 |             postAnchorExists: Boolean
 98 |     ) {
 99 |         log(
100 |                 "js_highlight_applied",
101 |                 linkedMapOf(
102 |                         "topicId" to topicId,
103 |                         "page" to page,
104 |                         "renderGenerationId" to renderGenerationId,
105 |                         "highlightType" to highlightType,
106 |                         "postAnchorExists" to postAnchorExists
107 |                 )
108 |         )
109 |     }
110 | 
111 |     fun nativeHighlightBound(
112 |             topicId: Long,
113 |             page: Int,
114 |             renderGenerationId: Int,
115 |             highlightType: String,
116 |             postId: Long
117 |     ) {
118 |         log(
119 |                 "native_highlight_bound",
120 |                 linkedMapOf(
121 |                         "topicId" to topicId,
122 |                         "page" to page,
123 |                         "renderGenerationId" to renderGenerationId,
124 |                         "highlightType" to highlightType,
125 |                         "postId" to postId
126 |                 )
127 |         )
128 |     }
129 | 
130 |     fun highlightFailedPostNotFound(
131 |             topicId: Long,
132 |             page: Int,
133 |             renderGenerationId: Int,
134 |             highlightType: String,
135 |             expectedPostId: Long,
136 |             failureReason: String
137 |     ) {
138 |         log(
139 |                 "highlight_failed_post_not_found",
140 |                 linkedMapOf(
141 |                         "topicId" to topicId,
142 |                         "page" to page,
143 |                         "renderGenerationId" to renderGenerationId,
144 |                         "highlightType" to highlightType,
145 |                         "expectedPostId" to expectedPostId,
146 |                         "failureReason" to failureReason
147 |                 )
148 |         )
149 |     }
150 | 
151 |     fun staleHighlightIgnored(
152 |             topicId: Long,
153 |             page: Int,
154 |             renderGenerationId: Int,
155 |             callbackGenerationId: Int,
156 |             expectedPostId: Long
157 |     ) {
158 |         log(
159 |                 "stale_highlight_ignored",
160 |                 linkedMapOf(
161 |                         "topicId" to topicId,
162 |                         "page" to page,
163 |                         "renderGenerationId" to renderGenerationId,
164 |                         "callbackGenerationId" to callbackGenerationId,
165 |                         "expectedPostId" to expectedPostId
166 |                 )
167 |         )
168 |     }
169 | 
170 |     /**
171 |      * Native has just armed a JS-side `setTimeout(..., delayMs)` that will
172 |      * trigger the highlight fade-out (add `post-highlight-fading`, then
173 |      * strip the base class on `transitionend`). Emitted from
174 |      * `ThemeWebController.reapplyTopicHighlight` once per render — never
175 |      * on scroll, only on a new render event (topic open / page change /
176 |      * refresh). The `delayMs` is what the JS bridge actually received.
177 |      */
178 |     fun highlightFadeoutScheduled(
179 |             topicId: Long,
180 |             page: Int,
181 |             renderGenerationId: Int,
182 |             delayMs: Int,
183 |             highlightType: String,
184 |             postId: Long
185 |     ) {
186 |         log(
187 |                 "highlight_fadeout_scheduled",
188 |                 linkedMapOf(
189 |                         "topicId" to topicId,
190 |                         "page" to page,
191 |                         "renderGenerationId" to renderGenerationId,
192 |                         "delayMs" to delayMs,
193 |                         "highlightType" to highlightType,
194 |                         "postId" to postId
195 |                 )
196 |         )
197 |     }
198 | 
199 |     /**
200 |      * JS reports back that the highlight class has been fully removed from
201 |      * the DOM for a given render generation (the `transitionend` handler
202 |      * in `PPDA_scheduleHighlightFadeout` fired, or the defensive 600ms
203 |      * fallback ran). The `renderGenerationId` echoes back the same id the
204 |      * native side armed the timer with, so a future render with a fresh
205 |      * generation will not be confused with this completion.
206 |      */
207 |     fun highlightFadeoutCompleted(
208 |             topicId: Long,
209 |             page: Int,
210 |             renderGenerationId: Int
211 |     ) {
212 |         log(
213 |                 "highlight_fadeout_completed",
214 |                 linkedMapOf(
215 |                         "topicId" to topicId,
216 |                         "page" to page,
217 |                         "renderGenerationId" to renderGenerationId
218 |                 )
219 |         )
220 |     }
221 | 
222 |     fun readPositionLoaded(
223 |             topicId: Long,
224 |             lastViewedPostId: Long,
225 |             lastViewedPage: Int
226 |     ) {
227 |         log(
228 |                 "read_position_loaded",
229 |                 linkedMapOf(
230 |                         "topicId" to topicId,
231 |                         "lastViewedPostId" to lastViewedPostId,
232 |                         "lastViewedPage" to lastViewedPage
233 |                 )
234 |         )
235 |     }
236 | 
237 |     fun readPositionSaved(
238 |             topicId: Long,
239 |             lastViewedPostId: Long,
240 |             lastViewedPage: Int
241 |     ) {
242 |         log(
243 |                 "read_position_saved",
244 |                 linkedMapOf(
245 |                         "topicId" to topicId,
246 |                         "lastViewedPostId" to lastViewedPostId,
247 |                         "lastViewedPage" to lastViewedPage
248 |                 )
249 |         )
250 |     }
251 | 
252 |     fun readPositionSaveSuppressed(
253 |             topicId: Long,
254 |             postId: Long,
255 |             reason: String,
256 |     ) {
257 |         log(
258 |                 "read_position_save_suppressed",
259 |                 linkedMapOf(
260 |                         "topicId" to topicId,
261 |                         "postId" to postId,
262 |                         "reason" to reason,
263 |                 )
264 |         )
265 |     }
266 | 
267 |     fun unreadTargetLoaded(
268 |             topicId: Long,
269 |             firstUnreadPostId: Long?,
270 |             unreadPage: Int?,
271 |             unreadUrl: String?
272 |     ) {
273 |         log(
274 |                 "unread_target_loaded",
275 |                 linkedMapOf(
276 |                         "topicId" to topicId,
277 |                         "firstUnreadPostId" to firstUnreadPostId,
278 |                         "unreadPage" to unreadPage,
279 |                         "unreadUrl" to unreadUrl
280 |                 )
281 |         )
282 |     }
283 | 
284 |     private fun log(event: String, fields: Map<String, Any?>) {
285 |         FpdaDebugLog.log("PPDA_TOPIC_HIGHLIGHT", event, fields)
286 |     }
287 | }
288 | 
```

### app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesDatabase.kt

Bytes: 1734
SHA-256: c4f74736fdbf95b6750ca66605a9a1a3e0bfef4ed1c26254ce47120e4ecee01e
Lines: 1-46 of 46

```text
 1 | package forpdateam.ru.forpda.entity.db.notes
 2 | 
 3 | import androidx.room.Database
 4 | import androidx.room.RoomDatabase
 5 | import forpdateam.ru.forpda.entity.db.ForumUserDao
 6 | import forpdateam.ru.forpda.entity.db.ForumUserRoom
 7 | import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
 8 | import forpdateam.ru.forpda.entity.db.favorites.FavItemRoom
 9 | import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatDao
10 | import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatRoom
11 | import forpdateam.ru.forpda.entity.db.history.HistoryItemDao
12 | import forpdateam.ru.forpda.entity.db.history.HistoryItemRoom
13 | import forpdateam.ru.forpda.entity.db.qms.QmsContactDao
14 | import forpdateam.ru.forpda.entity.db.qms.QmsContactRoom
15 | import forpdateam.ru.forpda.entity.db.qms.QmsThemeDao
16 | import forpdateam.ru.forpda.entity.db.qms.QmsThemeRoom
17 | import forpdateam.ru.forpda.entity.db.qms.QmsThemesDao
18 | import forpdateam.ru.forpda.entity.db.qms.QmsThemesRoom
19 | 
20 | @Database(
21 |     entities = [
22 |         NoteItemRoom::class,
23 |         NoteFolderRoom::class,
24 |         HistoryItemRoom::class,
25 |         QmsContactRoom::class,
26 |         QmsThemeRoom::class,
27 |         QmsThemesRoom::class,
28 |         FavItemRoom::class,
29 |         ForumItemFlatRoom::class,
30 |         ForumUserRoom::class,
31 |     ],
32 |     version = 8,
33 |     exportSchema = true
34 | )
35 | abstract class AppDatabase : RoomDatabase() {
36 |     abstract fun noteItemDao(): NoteItemDao
37 |     abstract fun noteFolderDao(): NoteFolderDao
38 |     abstract fun historyItemDao(): HistoryItemDao
39 |     abstract fun qmsContactDao(): QmsContactDao
40 |     abstract fun qmsThemeDao(): QmsThemeDao
41 |     abstract fun qmsThemesDao(): QmsThemesDao
42 |     abstract fun favItemDao(): FavItemDao
43 |     abstract fun forumItemFlatDao(): ForumItemFlatDao
44 |     abstract fun forumUserDao(): ForumUserDao
45 | }
46 | 
```

### app/src/main/java/forpdateam/ru/forpda/entity/db/notes/NotesMigrations.kt

Bytes: 4643
SHA-256: 04b118f6382b24ef237de1737d865cc70bc1445d3612ab3c363bb9c6ce880163
Lines: 1-101 of 101

```text
  1 | package forpdateam.ru.forpda.entity.db.notes
  2 | 
  3 | import androidx.room.migration.Migration
  4 | import androidx.sqlite.db.SupportSQLiteDatabase
  5 | import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
  6 | 
  7 | object NotesMigrations {
  8 |     val MIGRATION_1_2 = object : Migration(1, 2) {
  9 |         override fun migrate(db: SupportSQLiteDatabase) {
 10 |             // Migration from version 1 to 2
 11 |             // Version 1 had basic tables without the folder system
 12 |             // This migration ensures all tables exist with their initial schema
 13 |             // Note: this is a no-op migration for safety, as the exact schema of v1 is unknown
 14 |             // In production, if users are on v1, they will need to be handled via fallback or manual migration
 15 |             // For now, this serves as a placeholder to prevent crashes
 16 |         }
 17 |     }
 18 | 
 19 |     val MIGRATION_2_3 = object : Migration(2, 3) {
 20 |         override fun migrate(db: SupportSQLiteDatabase) {
 21 |             val now = System.currentTimeMillis()
 22 |             db.execSQL(
 23 |                 """
 24 |                 CREATE TABLE IF NOT EXISTS note_folders (
 25 |                     id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 26 |                     name TEXT NOT NULL,
 27 |                     sortOrder INTEGER NOT NULL,
 28 |                     createdAt INTEGER NOT NULL,
 29 |                     updatedAt INTEGER NOT NULL
 30 |                 )
 31 |                 """.trimIndent()
 32 |             )
 33 |             db.execSQL("ALTER TABLE notes ADD COLUMN folderId INTEGER")
 34 |             db.execSQL("ALTER TABLE notes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT $now")
 35 |             db.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $now")
 36 |             db.execSQL("ALTER TABLE notes ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
 37 |         }
 38 |     }
 39 | 
 40 |     val MIGRATION_3_4 = object : Migration(3, 4) {
 41 |         override fun migrate(db: SupportSQLiteDatabase) {
 42 |             db.execSQL("ALTER TABLE forum_items_flat ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
 43 |         }
 44 |     }
 45 | 
 46 |     val MIGRATION_4_5 = object : Migration(4, 5) {
 47 |         override fun migrate(db: SupportSQLiteDatabase) {
 48 |             db.execSQL("ALTER TABLE favorites ADD COLUMN localReadPostId INTEGER NOT NULL DEFAULT 0")
 49 |             db.execSQL("ALTER TABLE favorites ADD COLUMN localReadPostDateMillis INTEGER NOT NULL DEFAULT 0")
 50 |         }
 51 |     }
 52 | 
 53 |     /**
 54 |      * Adds tri-state readState and converts poisoned isNew=0 rows to UNKNOWN so refresh can re-detect unread.
 55 |      */
 56 |     val MIGRATION_5_6 = object : Migration(5, 6) {
 57 |         override fun migrate(db: SupportSQLiteDatabase) {
 58 |             db.execSQL(
 59 |                     "ALTER TABLE favorites ADD COLUMN readState INTEGER NOT NULL DEFAULT ${FavoriteReadState.STORAGE_UNKNOWN}"
 60 |             )
 61 |             db.execSQL(
 62 |                     """
 63 |                     UPDATE favorites
 64 |                     SET readState = CASE
 65 |                         WHEN isNew = 1 THEN ${FavoriteReadState.STORAGE_UNREAD}
 66 |                         ELSE ${FavoriteReadState.STORAGE_UNKNOWN}
 67 |                     END
 68 |                     """.trimIndent()
 69 |             )
 70 |         }
 71 |     }
 72 | 
 73 |     /**
 74 |      * Offline-reading feature was removed. Older builds (DB v6) and the short-lived offline build
 75 |      * (DB v7 with an `offline_items` table) must both keep opening so favorites/notes/history are
 76 |      * preserved. This migration only drops the now-unused `offline_items` table if present; it never
 77 |      * touches user data (favorites, notes, history, qms). Keeping the 6->7 path registered prevents
 78 |      * Room from throwing "A migration from 6 to 7 was required but not found".
 79 |      */
 80 |     val MIGRATION_6_7 = object : Migration(6, 7) {
 81 |         override fun migrate(db: SupportSQLiteDatabase) {
 82 |             db.execSQL("DROP TABLE IF EXISTS offline_items")
 83 |         }
 84 |     }
 85 | 
 86 |     /**
 87 |      * The offline-reading feature was removed, which changed the set of `@Entity` classes and thus
 88 |      * Room's computed identity hash. Databases that already reached v7 on a previous (offline) APK
 89 |      * still store the old identity hash in `room_master_table`, so Room throws
 90 |      * "Room cannot verify the data integrity" when opening v7 with the new schema. Bumping to v8 and
 91 |      * running this migration lets Room rewrite the identity hash to the current schema's value while
 92 |      * preserving user data. Like 6->7, this only drops the unused `offline_items` table if it lingers
 93 |      * and never touches favorites, notes, history, or qms.
 94 |      */
 95 |     val MIGRATION_7_8 = object : Migration(7, 8) {
 96 |         override fun migrate(db: SupportSQLiteDatabase) {
 97 |             db.execSQL("DROP TABLE IF EXISTS offline_items")
 98 |         }
 99 |     }
100 | }
101 | 
```

### app/src/main/java/forpdateam/ru/forpda/entity/remote/events/NotificationEvent.kt

Bytes: 2656
SHA-256: 43b914de2cc6ec5581241131708291364fd474c5aad8047db26f028f0e6f4d36
Lines: 1-115 of 115

```text
  1 | package forpdateam.ru.forpda.entity.remote.events
  2 | 
  3 | /**
  4 |  * Created by radiationx on 29.07.17.
  5 |  */
  6 | 
  7 | data class NotificationEvent @JvmOverloads constructor(
  8 |         var type: Type,
  9 |         var source: Source,
 10 | 
 11 |         var messageId: Int = 0,
 12 | 
 13 |         var sourceId: Int = 0,
 14 |         var userId: Int = 0,
 15 | 
 16 |         var timeStamp: Long = 0,
 17 |         var lastTimeStamp: Long = 0,
 18 | 
 19 |         var msgCount: Int = 0,
 20 |         var isImportant: Boolean = false,
 21 | 
 22 |         var sourceTitle: String = "",
 23 |         var userNick: String = "",
 24 | 
 25 |         var sourceEventText: String? = null
 26 | ) {
 27 | 
 28 | 
 29 |     /*
 30 |     * short
 31 |     * */
 32 | 
 33 |     val isNew: Boolean
 34 |         get() = NotificationEvent.isNew(type)
 35 | 
 36 |     val isRead: Boolean
 37 |         get() = NotificationEvent.isRead(type)
 38 | 
 39 |     val isMention: Boolean
 40 |         get() = NotificationEvent.isMention(type)
 41 | 
 42 | 
 43 |     enum class Type(val value: Int) {
 44 |         NEW(2),
 45 |         READ(4),
 46 |         MENTION(8),
 47 |         HAT_EDITED(16)
 48 |     }
 49 | 
 50 |     enum class Source(val value: Int) {
 51 |         THEME(32),
 52 |         SITE(64),
 53 |         QMS(128)
 54 |     }
 55 | 
 56 |     fun fromTheme(): Boolean {
 57 |         return NotificationEvent.fromTheme(source)
 58 |     }
 59 | 
 60 |     fun fromSite(): Boolean {
 61 |         return NotificationEvent.fromSite(source)
 62 |     }
 63 | 
 64 |     fun fromQms(): Boolean {
 65 |         return NotificationEvent.fromQms(source)
 66 |     }
 67 | 
 68 |     @JvmOverloads
 69 |     fun notifyId(type: Type? = this.type): Int {
 70 |         val actualType = type ?: this.type
 71 |         var hash = 17
 72 |         hash = 31 * hash + source.ordinal
 73 |         hash = 31 * hash + actualType.ordinal
 74 |         hash = 31 * hash + sourceId
 75 |         hash = 31 * hash + messageId
 76 |         hash = 31 * hash + userId
 77 |         return hash and 0x7FFFFFFF
 78 |     }
 79 | 
 80 |     companion object {
 81 |         const val SRC_EVENT_NEW = 1
 82 |         const val SRC_EVENT_READ = 2
 83 |         const val SRC_EVENT_MENTION = 3
 84 |         const val SRC_EVENT_HAT_EDITED = 4
 85 |         const val SRC_TYPE_SITE = "s"
 86 |         const val SRC_TYPE_THEME = "t"
 87 |         const val SRC_TYPE_QMS = "q"
 88 | 
 89 | 
 90 |         fun isNew(type: Type?): Boolean {
 91 |             return type != null && type == Type.NEW
 92 |         }
 93 | 
 94 |         fun isRead(type: Type?): Boolean {
 95 |             return type != null && type == Type.READ
 96 |         }
 97 | 
 98 |         fun isMention(type: Type?): Boolean {
 99 |             return type != null && type == Type.MENTION
100 |         }
101 | 
102 |         fun fromTheme(source: Source?): Boolean {
103 |             return source != null && source == Source.THEME
104 |         }
105 | 
106 |         fun fromSite(source: Source?): Boolean {
107 |             return source != null && source == Source.SITE
108 |         }
109 | 
110 |         fun fromQms(source: Source?): Boolean {
111 |             return source != null && source == Source.QMS
112 |         }
113 |     }
114 | }
115 | 
```

### app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt

Bytes: 3710
SHA-256: 4c9f8d5da86ded4ab3c6af9088b5ff7ef0512aad320d5408af1dea250cc21e4f
Lines: 1-94 of 94

```text
 1 | package forpdateam.ru.forpda.entity.remote.theme
 2 | 
 3 | import forpdateam.ru.forpda.presentation.theme.HighlightTarget
 4 | import java.util.ArrayList
 5 | 
 6 | import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
 7 | 
 8 | /**
 9 |  * Created by radiationx on 04.08.16.
10 |  */
11 | class ThemePage {
12 |     val anchors = mutableListOf<String>()
13 |     var title: String? = null
14 |     var desc: String? = null
15 |     var html: String? = null
16 |     var url: String? = null
17 |     var id = 0
18 |     var forumId = 0
19 |     var favId = 0
20 |     /*public boolean isCurator() {
21 |         return curator;
22 |     }
23 | 
24 |     public void setCurator(boolean curator) {
25 |         this.curator = curator;
26 |     }*/
27 | 
28 |     var scrollY = 0
29 |     var anchorPostId: String? = null
30 |     var anchorOffsetTop: Double? = null
31 |     var scrollRatio: Double? = null
32 |     var wasNearBottom: Boolean = false
33 |     var refreshRestoreId: String? = null
34 |     var refreshRestoreMode: String? = null
35 |     var refreshRestoreSource: String? = null
36 |     var renderSignature: String? = null
37 |     var postsFragmentHtml: String? = null
38 |     /**
39 |      * Resolved highlight target for this page (or null when no highlight applies).
40 |      * See [forpdateam.ru.forpda.presentation.theme.HighlightResolver] for the
41 |      * resolution priority. The template applies a per-post class based on this
42 |      * target; the JS fallback ([window.PPDA_applyHighlight]) reads it via the
43 |      * `ppdaHighlight` JS-side variable when the page becomes interactive.
44 |      */
45 |     var highlightTarget: HighlightTarget? = null
46 |     /**
47 |      * Monotonically increasing id, bumped on every "open" or "refresh" of the
48 |      * topic. The template embeds it as `data-render-generation`; the JS fallback
49 |      * uses it to suppress stale callback-driven re-applies.
50 |      */
51 |     var renderGenerationId: Int = 0
52 |     var isInFavorite = false
53 |     var isCurator = false
54 |     var canQuote = false
55 |     var isHatOpen = false
56 |     var isInlineHatOpen = false
57 |     var isPollOpen = false
58 |     var hasUnreadTarget = false
59 |     var ambiguousLastUnreadBottomRedirect = false
60 |     /**
61 |      * Read-resume / all-read bottom redirect resolved its anchor to the LAST post of the last page.
62 |      * The soft anchor scroll must then land on the BOTTOM of the page (like END navigation) instead
63 |      * of the top of that final post, otherwise the user stays mid-page on a tall final post.
64 |      */
65 |     var resumeToLastPageBottom = false
66 |     var openSessionKind: String? = null
67 |     /** True when this open was initiated via findpost (not scroll anchor). */
68 |     var openedViaFindPostLink: Boolean = false
69 |     /** Original request URL for highlight resolver (may differ from [url] after redirect). */
70 |     var resolverRequestUrl: String? = null
71 |     var topicHatPost: ThemePost? = null
72 |     val posts = ArrayList<ThemePost>()
73 |     var pagination = Pagination()
74 |     var poll: Poll? = null
75 | 
76 |     val anchor: String?
77 |         get() = if (anchors.isEmpty()) null else anchors[anchors.size - 1]
78 | 
79 |     val st: Int
80 |         // pagination.current — 1-индексированный номер страницы; на 4PDA st — это 0-based offset
81 |         // (страница 1 → st=0, страница 2 → st=perPage, …). Иначе при возврате назад URL получает st
82 |         // на одну страницу больше реального; для последних страниц сервер клампит до последней.
83 |         get() = (pagination.current - 1).coerceAtLeast(0) * pagination.perPage
84 | 
85 |     fun addAnchor(anchor: String): Boolean {
86 |         if (anchor.isBlank()) return false
87 |         return anchors.add(anchor)
88 |     }
89 | 
90 |     fun removeAnchor(): String? {
91 |         return if (anchors.isEmpty()) null else anchors.removeAt(anchors.size - 1)
92 |     }
93 | }
94 | 
```

### app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/Constants.kt

Bytes: 8129
SHA-256: 8945954f4f9aec00d4d93bb7d85dcb86afe8516dc658e877517779b2858c068c
Lines: 1-149 of 149

```text
  1 | package forpdateam.ru.forpda.model.data.remote.api.news
  2 | 
  3 | /**
  4 |  * Created by isanechek on 11/10/16.
  5 |  */
  6 | object Constants {
  7 |     const val CNBN = "Cannot Be Null"
  8 |     const val FORPDA_WRITE_EXTERNAL_STORAGE_RERMISSION = 1
  9 | 
 10 |     const val ERROR_LOAD_DATA = "error_load_data"
 11 |     const val ERROR_UPDATE_DATA = "error_update_data"
 12 |     const val ERROR_LOAD_MORE_DATA = "error_load_more_data"
 13 |     const val ERROR_LOAD_MORE_NEW_DATA = "error_load_more_new_data"
 14 |     const val ERROR_NO_INTERNET = "error_no_internet"
 15 |     const val ERROR_OTHER_ERRORS = "error_other_errors"
 16 | 
 17 |     const val NEWS_CATEGORY_ROOT = "root"
 18 |     const val NEWS_URL_ROOT = "https://4pda.to/"
 19 |     const val NEWS_CATEGORY_ALL = "all_news"
 20 |     const val NEWS_URL_ALL = "https://4pda.to/news/"
 21 |     const val NEWS_CATEGORY_TECH = "tech_news"
 22 |     const val NEWS_SUBCATEGORY_TECH_SMARTPHONES = "tech_smartphones_news"
 23 |     const val NEWS_URL_TECH_SMARTPHONES = "https://4pda.to/tag/smartphones/"
 24 |     const val NEWS_SUBCATEGORY_TECH_LAPTOPS = "tech_laptops_news"
 25 |     const val NEWS_URL_TECH_LAPTOPS = "https://4pda.to/tag/laptops/"
 26 |     const val NEWS_SUBCATEGORY_TECH_AUDIO = "tech_audio_news"
 27 |     const val NEWS_URL_TECH_AUDIO = "https://4pda.to/tag/audio/"
 28 |     const val NEWS_SUBCATEGORY_TECH_MONITORS = "tech_monitors_news"
 29 |     const val NEWS_URL_TECH_MONITORS = "https://4pda.to/tag/monitors/"
 30 |     const val NEWS_SUBCATEGORY_TECH_APPLIANCES = "tech_appliances_news"
 31 |     const val NEWS_URL_TECH_APPLIANCES = "https://4pda.to/tag/appliances/"
 32 |     const val NEWS_SUBCATEGORY_TECH_PC = "tech_pc_news"
 33 |     const val NEWS_URL_TECH_PC = "https://4pda.to/tag/pc/"
 34 |     const val NEWS_CATEGORY_ARTICLES = "articles_news"
 35 |     const val NEWS_URL_ARTICLES = "https://4pda.to/articles/"
 36 |     const val NEWS_CATEGORY_REVIEWS = "reviews_news"
 37 |     const val NEWS_URL_REVIEWS = "https://4pda.to/reviews/"
 38 |     const val NEWS_CATEGORY_SOFTWARE = "software_news"
 39 |     const val NEWS_URL_SOFTWARE = "https://4pda.to/software/"
 40 |     const val NEWS_CATEGORY_GAMES = "games_news"
 41 |     const val NEWS_URL_GAMES = "https://4pda.to/games/"
 42 | 
 43 |     const val NEWS_SUBCATEGORY_DEVSTORY_GAMES = "ds_games_news"
 44 |     const val NEWS_URL_DEVSTORY_GAMES = "https://4pda.to/games/tag/devstory/"
 45 |     const val NEWS_SUBCATEGORY_WP7_GAME = "wp_game_news"
 46 |     const val NEWS_URL_WP7_GAME = "https://4pda.to/games/tag/games-for-windows-phone-7/"
 47 |     const val NEWS_SUBCATEGORY_IOS_GAME = "ios_game_news"
 48 |     const val NEWS_URL_IOS_GAME = "https://4pda.to/games/tag/games-for-ios/"
 49 |     const val NEWS_SUBCATEGORY_ANDROID_GAME = "android_game_news"
 50 |     const val NEWS_URL_ANDROID_GAME = "https://4pda.to/games/tag/games-for-android/"
 51 | 
 52 |     const val NEWS_SUBCATEGORY_DEVSTORY_SOFTWARE = "ds_software_news"
 53 |     const val NEWS_URL_DEVSTORY_SOFTWARE = "https://4pda.to/software/tag/devstory/"
 54 |     const val NEWS_SUBCATEGORY_WP7_SOFTWARE = "software_wp7-news"
 55 |     const val NEWS_URL_WP7_SOFTWARE = "https://4pda.to/software/tag/programs-for-windows-phone-7/"
 56 |     const val NEWS_SUBCATEGORY_IOS_SOFTWARE = "software_ios_news"
 57 |     const val NEWS_URL_IOS_SOFTWARE = "https://4pda.to/software/tag/programs-for-ios/"
 58 |     const val NEWS_SUBCATEGORY_ANDROID_SOFTWARE = "software_android_news"
 59 |     const val NEWS_URL_ANDROID_SOFTWARE = "https://4pda.to/software/tag/programs-for-android/"
 60 | 
 61 |     const val NEWS_SUBCATEGORY_SMARTPHONES_REVIEWS = "s_r_news"
 62 |     const val NEWS_URL_SMARTPHONES_REVIEWS = "https://4pda.to/reviews/tag/smartphones/"
 63 |     const val NEWS_SUBCATEGORY_TABLETS_REVIEWS = "t_r_news"
 64 |     const val NEWS_URL_TABLETS_REVIEWS = "https://4pda.to/reviews/tag/tablets/"
 65 |     const val NEWS_SUBCATEGORY_SMART_WATCH_REVIEWS = "sw_r_news"
 66 |     const val NEWS_URL_SMART_WATCH_REVIEWS = "https://4pda.to/reviews/smart-watches/"
 67 |     const val NEWS_SUBCATEGORY_ACCESSORIES_REVIEWS = "a_r_news"
 68 |     const val NEWS_URL_ACCESSORIES_REVIEWS = "https://4pda.to/reviews/tag/accessories/"
 69 |     const val NEWS_SUBCATEGORY_NOTEBOOKS_REVIEWS = "n_r_news"
 70 |     const val NEWS_URL_NOTEBOOKS_REVIEWS = "https://4pda.to/reviews/tag/notebooks/"
 71 |     const val NEWS_SUBCATEGORY_ACOUSTICS_REVIEWS = "ac_r_news"
 72 |     const val NEWS_URL_ACOUSTICS_REVIEWS = "https://4pda.to/reviews/tag/acoustics/"
 73 | 
 74 |     const val NEWS_SUBCATEGORY_HOW_TO_ANDROID = "h_t_a_news"
 75 |     const val NEWS_URL_HOW_TO_ANDROID = "https://4pda.to/tag/how-to-android/?utm_source=slider1"
 76 |     const val NEWS_SUBCATEGORY_HOW_TO_IOS = "h_t_i_news"
 77 |     const val NEWS_URL_HOW_TO_IOS = "https://4pda.to/tag/how-to-ios/?utm_source=slider1"
 78 |     const val NEWS_SUBCATEGORY_HOW_TO_WP = "h_t_w_news"
 79 |     const val NEWS_URL_HOW_TO_WP = "https://4pda.to/tag/how-to-wp/?utm_source=slider1"
 80 |     const val NEWS_SUBCATEGORY_HOW_TO_INTERVIEW = "interview_news"
 81 |     const val NEWS_URL_HOW_TO_INTERVIEW = "https://4pda.to/articles/tag/interview/"
 82 | 
 83 |     const val TAB_ALL = "news"
 84 |     const val TAB_ARTICLE = "article"
 85 |     const val TAB_REVIEWS = "reviews"
 86 |     const val TAB_SOFTWARE = "software"
 87 |     const val TAB_GAMES = "games"
 88 | 
 89 |     const val NEWS_LOAD_DATA_TASK = "news.load.data"
 90 |     const val NEWS_UPDATE_BACKGROUND_TASK = "update.background"
 91 |     const val COUNT_NEW_NEWS_ITEMS = "count.items"
 92 |     const val DETAILS_COVER = "count.items"
 93 |     const val NEWS_ERROR_LOAD_OR_UPDATE_TASK = "news.load.update.errro"
 94 | 
 95 |     const val D_TITLE = "d.news.title"
 96 |     const val D_DATE = "d.news.date"
 97 |     const val D_USERNAME = "d.news.username"
 98 |     const val D_URL = "d.news.url"
 99 |     const val D_IMG = "d.news.img"
100 |     const val D_ID = "d.news.id"
101 | 
102 |     val NEWS_CATEGORY_URLS: Map<String, String> = mapOf(
103 |             NEWS_CATEGORY_ROOT to NEWS_URL_ROOT,
104 |             NEWS_CATEGORY_ALL to NEWS_URL_ALL,
105 |             NEWS_CATEGORY_TECH to NEWS_URL_TECH_SMARTPHONES,
106 |             NEWS_SUBCATEGORY_TECH_SMARTPHONES to NEWS_URL_TECH_SMARTPHONES,
107 |             NEWS_SUBCATEGORY_TECH_LAPTOPS to NEWS_URL_TECH_LAPTOPS,
108 |             NEWS_SUBCATEGORY_TECH_AUDIO to NEWS_URL_TECH_AUDIO,
109 |             NEWS_SUBCATEGORY_TECH_MONITORS to NEWS_URL_TECH_MONITORS,
110 |             NEWS_SUBCATEGORY_TECH_APPLIANCES to NEWS_URL_TECH_APPLIANCES,
111 |             NEWS_SUBCATEGORY_TECH_PC to NEWS_URL_TECH_PC,
112 |             NEWS_CATEGORY_ARTICLES to NEWS_URL_ARTICLES,
113 |             NEWS_CATEGORY_REVIEWS to NEWS_URL_REVIEWS,
114 |             NEWS_CATEGORY_SOFTWARE to NEWS_URL_SOFTWARE,
115 |             NEWS_CATEGORY_GAMES to NEWS_URL_GAMES,
116 |             NEWS_SUBCATEGORY_DEVSTORY_GAMES to NEWS_URL_DEVSTORY_GAMES,
117 |             NEWS_SUBCATEGORY_WP7_GAME to NEWS_URL_WP7_GAME,
118 |             NEWS_SUBCATEGORY_IOS_GAME to NEWS_URL_IOS_GAME,
119 |             NEWS_SUBCATEGORY_ANDROID_GAME to NEWS_URL_ANDROID_GAME,
120 |             NEWS_SUBCATEGORY_DEVSTORY_SOFTWARE to NEWS_URL_DEVSTORY_SOFTWARE,
121 |             NEWS_SUBCATEGORY_WP7_SOFTWARE to NEWS_URL_WP7_SOFTWARE,
122 |             NEWS_SUBCATEGORY_IOS_SOFTWARE to NEWS_URL_IOS_SOFTWARE,
123 |             NEWS_SUBCATEGORY_ANDROID_SOFTWARE to NEWS_URL_ANDROID_SOFTWARE,
124 |             NEWS_SUBCATEGORY_SMARTPHONES_REVIEWS to NEWS_URL_SMARTPHONES_REVIEWS,
125 |             NEWS_SUBCATEGORY_TABLETS_REVIEWS to NEWS_URL_TABLETS_REVIEWS,
126 |             NEWS_SUBCATEGORY_SMART_WATCH_REVIEWS to NEWS_URL_SMART_WATCH_REVIEWS,
127 |             NEWS_SUBCATEGORY_ACCESSORIES_REVIEWS to NEWS_URL_ACCESSORIES_REVIEWS,
128 |             NEWS_SUBCATEGORY_NOTEBOOKS_REVIEWS to NEWS_URL_NOTEBOOKS_REVIEWS,
129 |             NEWS_SUBCATEGORY_ACOUSTICS_REVIEWS to NEWS_URL_ACOUSTICS_REVIEWS,
130 |             NEWS_SUBCATEGORY_HOW_TO_ANDROID to NEWS_URL_HOW_TO_ANDROID,
131 |             NEWS_SUBCATEGORY_HOW_TO_IOS to NEWS_URL_HOW_TO_IOS,
132 |             NEWS_SUBCATEGORY_HOW_TO_WP to NEWS_URL_HOW_TO_WP,
133 |             NEWS_SUBCATEGORY_HOW_TO_INTERVIEW to NEWS_URL_HOW_TO_INTERVIEW
134 |     )
135 | 
136 |     fun isSelectableNewsCategory(category: String): Boolean =
137 |             NEWS_CATEGORY_URLS.containsKey(category)
138 | 
139 |     fun normalizeNewsCategory(category: String?): String =
140 |             when {
141 |                 category == NEWS_CATEGORY_ROOT -> NEWS_CATEGORY_ALL
142 |                 category != null && isSelectableNewsCategory(category) -> category
143 |                 else -> NEWS_CATEGORY_ALL
144 |             }
145 | 
146 |     fun getNewsCategoryUrl(category: String?): String =
147 |             NEWS_CATEGORY_URLS[category] ?: NEWS_URL_ALL
148 | }
149 | 
```

### app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/news/NewsApi.kt

Bytes: 63762
SHA-256: 602562e4a57d1fd79ad541279804c29310e170086319bf51f53f7ff96c9b68ea
Lines: 1-1408 of 1408

```text
   1 | package forpdateam.ru.forpda.model.data.remote.api.news
   2 | 
   3 | import android.util.SparseArray
   4 | import forpdateam.ru.forpda.BuildConfig
   5 | import forpdateam.ru.forpda.diagnostic.ArticleParseTrace
   6 | import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
   7 | import forpdateam.ru.forpda.common.Cp1251Codec
   8 | import forpdateam.ru.forpda.entity.remote.news.Comment
   9 | import forpdateam.ru.forpda.entity.remote.news.CommentKarmaVoteResult
  10 | import forpdateam.ru.forpda.entity.remote.news.DetailsPage
  11 | import forpdateam.ru.forpda.entity.remote.news.NewsItem
  12 | import forpdateam.ru.forpda.model.data.remote.IWebClient
  13 | import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
  14 | import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
  15 | import forpdateam.ru.forpda.model.data.remote.api.news.Constants
  16 | import kotlinx.coroutines.Dispatchers
  17 | import kotlinx.coroutines.async
  18 | import kotlinx.coroutines.awaitAll
  19 | import kotlinx.coroutines.coroutineScope
  20 | import kotlinx.coroutines.runBlocking
  21 | import kotlinx.coroutines.sync.Semaphore
  22 | import kotlinx.coroutines.sync.withPermit
  23 | import timber.log.Timber
  24 | import java.net.URLEncoder
  25 | import java.util.Locale
  26 | 
  27 | data class CommentEditContext(
  28 |         val commentsSource: String? = null,
  29 |         val articleHtml: String? = null,
  30 |         val articleUrl: String? = null,
  31 |         val articleId: Int = 0,
  32 | )
  33 | 
  34 | data class ArticleFetchResult(
  35 |         val page: DetailsPage,
  36 |         val rawBody: String,
  37 |         val response: NetworkResponse,
  38 |         val originalUrl: String,
  39 |         val probeUrl: String,
  40 |         /** Parser body HTML before [ArticleTemplate] mapping; used for phase-2 poll merge. */
  41 |         val parsedBodyHtml: String = page.html.orEmpty()
  42 | )
  43 | 
  44 | /**
  45 |  * Created by radiationx on 31.07.16.
  46 |  */
  47 | class NewsApi(
  48 |         private val webClient: IWebClient,
  49 |         private val articleParser: ArticleParser
  50 | ) {
  51 | 
  52 |     fun getNews(category: String, pageNumber: Int): List<NewsItem> {
  53 |         if (category == Constants.NEWS_CATEGORY_TECH) {
  54 |             return getTechNews(pageNumber)
  55 |         }
  56 |         val url = getLink(category, pageNumber)
  57 |         val response = webClient.get(url)
  58 |         return articleParser.parseArticles(response.body)
  59 |     }
  60 | 
  61 |     private fun getTechNews(pageNumber: Int): List<NewsItem> {
  62 |         // Pin dispatcher to IO: never run on the caller's dispatcher. Avoids deadlock
  63 |         // when a coroutine on Dispatchers.Main (or any limited dispatcher) calls this.
  64 |         return runBlocking(Dispatchers.IO) {
  65 |             val gate = Semaphore(TECH_NEWS_CONCURRENCY)
  66 |             TECH_URLS
  67 |                     .map { url ->
  68 |                         async {
  69 |                             gate.withPermit {
  70 |                                 articleParser.parseArticles(webClient.get(getPageLink(url, pageNumber)).body)
  71 |                             }
  72 |                         }
  73 |                     }
  74 |                     .awaitAll()
  75 |                     .flatten()
  76 |                     .distinctBy { it.id }
  77 |         }
  78 |     }
  79 | 
  80 |     fun getDetails(id: Int): DetailsPage =
  81 |             fetchArticleDetails("https://4pda.to/index.php?p=$id").page
  82 | 
  83 |     fun getDetails(url: String): DetailsPage = fetchArticleDetails(url).page
  84 | 
  85 |     fun fetchArticleDetails(
  86 |             url: String,
  87 |             phase: ArticleParsePhase = ArticleParsePhase.FIRST_RENDER,
  88 |             bypassCache: Boolean = false
  89 |     ): ArticleFetchResult {
  90 |         val response = webClient.request(buildArticleRequest(url, bypassCache))
  91 |         val body = response.body
  92 |         if (BuildConfig.DEBUG) {
  93 |             // Debug-only: trace the raw response characteristics without logging sensitive data.
  94 |             // For successful 2xx/3xx skip the SHA-256 classifyHtml (it costs O(8KB) per fetch);
  95 |             // log only endpoint+code+len. For 4xx/5xx/redirects keep the full classifier
  96 |             // since those are the paths we actually want fingerprints for.
  97 |             val needsFullClassify = response.code !in 200..399
  98 |             FpdaDebugLog.log(
  99 |                     FpdaDebugLog.TAG_COMMENTS_SECTION,
 100 |                     "article_response",
 101 |                     buildMap {
 102 |                         put("endpoint", FpdaDebugLog.sanitizeUrl(response.url.ifBlank { url }))
 103 |                         put("code", response.code)
 104 |                         if (needsFullClassify) {
 105 |                             putAll(FpdaDebugLog.classifyHtml(body))
 106 |                         } else {
 107 |                             put("htmlLen", body.length)
 108 |                         }
 109 |                     }
 110 |             )
 111 |         }
 112 |         rejectUnexpectedArticleBody(body)
 113 |         syncPollVoteCookies()
 114 |         val article = articleParser.parseArticle(body, phase)
 115 |         article.url = response.redirectWithFragment
 116 |         val probeUrl = response.redirect.takeIf { it.isNotBlank() } ?: url
 117 |         if (BuildConfig.DEBUG && article.html.isNullOrBlank()) {
 118 |             ArticleParseTrace.log(
 119 |                     event = "network_empty_body",
 120 |                     articleId = article.id.takeIf { it > 0 },
 121 |                     bodyLen = body.length,
 122 |                     reason = "empty_after_parse"
 123 |             )
 124 |         }
 125 |         return ArticleFetchResult(
 126 |                 page = article,
 127 |                 rawBody = body,
 128 |                 response = response,
 129 |                 originalUrl = url,
 130 |                 probeUrl = probeUrl,
 131 |                 parsedBodyHtml = article.html.orEmpty()
 132 |         )
 133 |     }
 134 | 
 135 |     /** Phase-2: optional second network fetch for desktop comments/poll (not on first-render path). */
 136 |     suspend fun enrichDesktopExtras(fetch: ArticleFetchResult): DetailsPage =
 137 |             loadDesktopExtrasIfMissing(fetch.originalUrl, fetch.response, fetch.page, fetch.rawBody)
 138 | 
 139 |     fun enrichArticleMetadata(page: DetailsPage, rawBody: String) {
 140 |         articleParser.enrichArticleMetadata(page, rawBody)
 141 |     }
 142 | 
 143 |     /**
 144 |      * Fetch the desktop article page (desktop UA, no mobile cookie) and extract its server-rendered
 145 |      * comment list. The phase-1 mobile page ships an EMPTY `<ul class="comment-list">` shell even for
 146 |      * articles with hundreds of comments (they are lazy-loaded by the mobile site's JS), so this is
 147 |      * the reliable way to obtain real comment nodes when the own comment count is positive.
 148 |      *
 149 |      * Returns the comment-list HTML only when it carries actual comment NODES (not another empty
 150 |      * shell); otherwise null so callers can keep the article's own count and surface a retry.
 151 |      */
 152 |     fun loadDesktopCommentsSource(url: String): String? {
 153 |         if (url.isBlank()) return null
 154 |         val body = loadDesktopArticleBody(url, bypassCache = false)?.takeIf { it.isNotBlank() }
 155 |                 ?: return null
 156 |         val source = articleParser.extractCommentsSourceFromPage(body)
 157 |                 ?.takeIf { it.contains("comment", ignoreCase = true) }
 158 |                 ?: return null
 159 |         return source.takeIf { articleParser.hasCommentNodeMarkup(it) }
 160 |     }
 161 | 
 162 |     /**
 163 |      * Fetches one WordPress comment page (`cp` / comment-page-N) and returns the comment-list HTML.
 164 |      * Used for paginated inline comments — one network round-trip per batch (~20 nodes).
 165 |      */
 166 |     fun fetchCommentsPageSource(articleUrl: String, commentPage: Int): String? {
 167 |         if (articleUrl.isBlank() || commentPage <= 0) return null
 168 |         val pageUrl = forpdateam.ru.forpda.presentation.articles.detail.comments
 169 |                 .ArticleCommentsPagination.withCommentPage(articleUrl, commentPage)
 170 |         val body = loadDesktopArticleBody(pageUrl, bypassCache = false)?.takeIf { it.isNotBlank() }
 171 |                 ?: return null
 172 |         val source = articleParser.extractCommentsSourceFromPage(body)
 173 |                 ?.takeIf { it.contains("comment", ignoreCase = true) }
 174 |                 ?: return null
 175 |         return source.takeIf { articleParser.hasCommentNodeMarkup(it) }
 176 |     }
 177 | 
 178 |     fun parseCommentsFromSource(
 179 |             article: DetailsPage,
 180 |             source: String?,
 181 |             paginated: Boolean = false,
 182 |             commentPage: Int = 1,
 183 |     ): Comment {
 184 |         val snapshot = DetailsPage().apply {
 185 |             id = article.id
 186 |             commentsCount = article.commentsCount
 187 |             karmaMap = article.karmaMap
 188 |             commentsSource = source
 189 |             desktopCommentsSource = article.desktopCommentsSource
 190 |             url = article.url
 191 |         }
 192 |         return parseComments(snapshot, paginated = paginated, commentPage = commentPage)
 193 |     }
 194 | 
 195 |     private fun rejectUnexpectedArticleBody(body: String) {
 196 |         when (ArticleHtmlValidator.classifyRawHtml(body)) {
 197 |             ArticleHtmlValidator.PageKind.LOGIN ->
 198 |                     throw IllegalStateException("login_page")
 199 |             ArticleHtmlValidator.PageKind.ERROR,
 200 |             ArticleHtmlValidator.PageKind.CAPTCHA ->
 201 |                     throw IllegalStateException("error_page")
 202 |             else -> Unit
 203 |         }
 204 |         if (body.length < ArticleHtmlValidator.MIN_RAW_HTML_LEN &&
 205 |                 !ArticleHtmlValidator.looksLikeArticlePage(body)) {
 206 |             throw IllegalStateException("unexpected_html")
 207 |         }
 208 |     }
 209 | 
 210 |     private suspend fun loadDesktopExtrasIfMissing(
 211 |             originalUrl: String,
 212 |             primaryResponse: NetworkResponse,
 213 |             article: DetailsPage,
 214 |             @Suppress("UNUSED_PARAMETER") cachedPrimaryBody: String? = null
 215 |     ): DetailsPage = coroutineScope {
 216 |         val probeUrl = primaryResponse.redirect.takeIf { it.isNotBlank() } ?: originalUrl
 217 |         val probeComments = shouldProbeDesktopComments(originalUrl, probeUrl, article)
 218 |         val articleHasRenderablePoll = articleParser.hasNormalizedNewsPollBlock(article.html) ||
 219 |                 articleParser.hasFallbackNewsPollBlock(article.html)
 220 |         val probePoll = !articleHasRenderablePoll && shouldProbeDesktopPoll(originalUrl, probeUrl, article)
 221 |         if (!probeComments && !probePoll) return@coroutineScope article
 222 | 
 223 |         if (probePoll) {
 224 |             syncPollVoteCookies()
 225 |         }
 226 | 
 227 |         val startedAt = System.currentTimeMillis()
 228 |         // Comments and poll need the same desktop body; fetch it once and
 229 |         // share the result. Previously each branch scheduled its own
 230 |         // loadDesktopArticleBody(probeUrl, ...) which doubled the network
 231 |         // round-trip and any side effects (cache writes, log emissions).
 232 |         val bodyDeferred = async(Dispatchers.IO) {
 233 |             loadDesktopArticleBody(probeUrl, bypassCache = false)
 234 |         }
 235 | 
 236 |         var commentsError: Throwable? = null
 237 |         var pollError: Throwable? = null
 238 |         val body = runCatching { bodyDeferred.await() }.getOrElse { e ->
 239 |             logDeferredExtrasError("desktop_body", e)
 240 |             commentsError = e
 241 |             pollError = e
 242 |             null
 243 |         }
 244 |         if (body != null) {
 245 |             if (probeComments) {
 246 |                 article.desktopCommentsSource = articleParser.extractCommentsSourceFromPage(body)
 247 |                         ?.takeIf { it.contains("comment", ignoreCase = true) }
 248 |                         ?: run {
 249 |                             commentsError = IllegalStateException("no comments source in body")
 250 |                             null
 251 |                         }
 252 |             }
 253 |             if (probePoll) {
 254 |                 articleParser.appendPollFromResponse(article, body)
 255 |             }
 256 |         }
 257 |         if (BuildConfig.DEBUG) {
 258 |             FpdaDebugLog.log(
 259 |                     FpdaDebugLog.TAG_COMMENTS_SECTION,
 260 |                     "parallel_extras",
 261 |                     mapOf(
 262 |                             "parallelExtrasMs" to (System.currentTimeMillis() - startedAt),
 263 |                             "probeComments" to probeComments,
 264 |                             "probePoll" to probePoll,
 265 |                             "commentsError" to commentsError?.let { it::class.java.simpleName },
 266 |                             "pollError" to pollError?.let { it::class.java.simpleName }
 267 |                     )
 268 |             )
 269 |         }
 270 |         article
 271 |     }
 272 | 
 273 |     private fun logDeferredExtrasError(stage: String, error: Throwable) {
 274 |         Timber.d(
 275 |                 "NewsApi deferred extras stage=%s error=%s",
 276 |                 stage,
 277 |                 error::class.java.simpleName
 278 |         )
 279 |     }
 280 | 
 281 |     private fun syncPollVoteCookies() {
 282 |         articleParser.syncPollVoteCookies(webClient.getClientCookies())
 283 |     }
 284 | 
 285 |     private fun loadDesktopArticleBody(url: String, bypassCache: Boolean = false): String? =
 286 |             runCatching {
 287 |                 webClient.requestWithoutMobileCookie(
 288 |                         NetworkRequest.Builder()
 289 |                                 .copyFrom(buildArticleRequest(url, bypassCache))
 290 |                                 .addHeader("User-Agent", DESKTOP_USER_AGENT)
 291 |                                 .build()
 292 |                 ).body
 293 |             }.getOrNull()
 294 | 
 295 |     private fun shouldProbeDesktopComments(originalUrl: String, probeUrl: String, article: DetailsPage): Boolean {
 296 |         if (!is4pdaArticleUrl(originalUrl, probeUrl, article)) return false
 297 |         val commentsSource = article.commentsSource
 298 |         if (commentsSource.isNullOrBlank()) return false
 299 |         if (!isAuthorized() || !commentsSource.contains("comment", ignoreCase = true)) return false
 300 |         val userId = currentUserId()
 301 |         if (articleParser.commentsSourceNeedsDesktopProbe(commentsSource, userId)) {
 302 |             return true
 303 |         }
 304 |         if (userId > 0 &&
 305 |                 commentsSource.contains("comment-list", ignoreCase = true) &&
 306 |                 !commentsSource.contains("act=rep", ignoreCase = true)) {
 307 |             return true
 308 |         }
 309 |         if (userId > 0 &&
 310 |                 commentsSource.contains("act=rep", ignoreCase = true) &&
 311 |                 !commentsSource.contains("editcomment", ignoreCase = true)) {
 312 |             return true
 313 |         }
 314 |         if (commentsSource.contains("comment-list", ignoreCase = true) &&
 315 |                 commentsSource.contains("id=\"comment-", ignoreCase = true) &&
 316 |                 !commentsSource.contains("showuser=", ignoreCase = true)) {
 317 |             return true
 318 |         }
 319 |         if (userId > 0 &&
 320 |                 !commentsSource.contains("showuser=$userId", ignoreCase = true) &&
 321 |                 commentsSource.contains("editcomment", ignoreCase = true)) {
 322 |             return false
 323 |         }
 324 |         return userId > 0
 325 |     }
 326 | 
 327 |     private fun buildArticleRequest(url: String, bypassCache: Boolean): NetworkRequest {
 328 |         val builder = NetworkRequest.Builder()
 329 |                 .url(url)
 330 |                 .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
 331 |         if (bypassCache) {
 332 |             builder.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
 333 |             builder.addHeader("Pragma", "no-cache")
 334 |         }
 335 |         return builder.build()
 336 |     }
 337 | 
 338 |     private fun shouldProbeDesktopPoll(originalUrl: String, probeUrl: String, article: DetailsPage): Boolean {
 339 |         if (!is4pdaArticleUrl(originalUrl, probeUrl, article)) return false
 340 |         val hasPollCandidate = articleParser.hasFallbackNewsPollBlock(article.html) ||
 341 |                 articleParser.hasRawTemplatePollMarker(article.html) ||
 342 |                 article.id == KNOWN_DESKTOP_POLL_ARTICLE_ID ||
 343 |                 originalUrl.contains("p=$KNOWN_DESKTOP_POLL_ARTICLE_ID", ignoreCase = true) ||
 344 |                 probeUrl.contains("p=$KNOWN_DESKTOP_POLL_ARTICLE_ID", ignoreCase = true) ||
 345 |                 article.title.orEmpty().contains("опрос", ignoreCase = true) ||
 346 |                 article.html.orEmpty().contains("опрос", ignoreCase = true) ||
 347 |                 article.html.orEmpty().contains("голос", ignoreCase = true)
 348 |         if (!hasPollCandidate) {
 349 |             return false
 350 |         }
 351 |         return originalUrl.contains("index.php?p=", ignoreCase = true) ||
 352 |                 probeUrl.contains("index.php?p=", ignoreCase = true) ||
 353 |                 articleSlugUrlRegex.containsMatchIn(originalUrl) ||
 354 |                 articleSlugUrlRegex.containsMatchIn(probeUrl) ||
 355 |                 article.id > 0
 356 |     }
 357 | 
 358 |     private fun is4pdaArticleUrl(originalUrl: String, probeUrl: String, article: DetailsPage): Boolean {
 359 |         if (!originalUrl.contains("4pda.to", ignoreCase = true) &&
 360 |                 !probeUrl.contains("4pda.to", ignoreCase = true)) {
 361 |             return false
 362 |         }
 363 |         return originalUrl.contains("index.php?p=", ignoreCase = true) ||
 364 |                 probeUrl.contains("index.php?p=", ignoreCase = true) ||
 365 |                 articleSlugUrlRegex.containsMatchIn(originalUrl) ||
 366 |                 articleSlugUrlRegex.containsMatchIn(probeUrl) ||
 367 |                 article.id > 0
 368 |     }
 369 | 
 370 |     private fun isAuthorized(): Boolean =
 371 |             currentUserId() > 0 || webClient.getAuthKey().takeIf { it.isNotBlank() && it != "0" } != null
 372 | 
 373 |     private fun currentUserId(): Int =
 374 |             webClient.getClientCookies().values.firstNotNullOfOrNull { cookie ->
 375 |                 cookie.value.toIntOrNull()?.takeIf {
 376 |                     cookie.name.equals("member_id", ignoreCase = true) && it > 0
 377 |                 }
 378 |             }.orZero()
 379 | 
 380 |     private companion object {
 381 |         val articleSlugUrlRegex = Regex("""/\d{4}/\d{2}/\d{2}/\d+/""")
 382 |         const val KNOWN_DESKTOP_POLL_ARTICLE_ID = 456521
 383 |         const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
 384 |         const val TECH_NEWS_CONCURRENCY = 3
 385 |         val TECH_URLS = listOf(
 386 |                 Constants.NEWS_URL_TECH_SMARTPHONES,
 387 |                 Constants.NEWS_URL_TECH_LAPTOPS,
 388 |                 Constants.NEWS_URL_TECH_AUDIO,
 389 |                 Constants.NEWS_URL_TECH_MONITORS,
 390 |                 Constants.NEWS_URL_TECH_APPLIANCES,
 391 |                 Constants.NEWS_URL_TECH_PC
 392 |         )
 393 |     }
 394 | 
 395 |     fun sendPoll(from: String, pollId: Int, answersId: IntArray): DetailsPage {
 396 |         val pollHtml = votePoll(from, pollId, answersId)
 397 |         syncPollVoteCookies()
 398 |         return articleParser.parseArticle(pollHtml)
 399 |     }
 400 | 
 401 |     fun votePoll(from: String, pollId: Int, answersId: IntArray): String {
 402 |         require(pollId > 0) { "Invalid poll id" }
 403 |         require(answersId.isNotEmpty()) { "No poll answer selected" }
 404 |         val url = "https://4pda.to/pages/poll/?act=vote&poll_id=$pollId"
 405 |         val body = buildString {
 406 |             append("from=")
 407 |             append(URLEncoder.encode(from.ifBlank { "/pages/poll/?poll_id=$pollId" }, "UTF-8"))
 408 |             answersId.forEach {
 409 |                 append("&answer%5B%5D=")
 410 |                 append(URLEncoder.encode(it.toString(), "UTF-8"))
 411 |             }
 412 |         }
 413 |         val request = NetworkRequest.Builder()
 414 |                 .url(url)
 415 |                 .xhrHeader()
 416 |                 .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
 417 |                 .rawBody(body)
 418 |                 .build()
 419 | 
 420 |         val response = webClient.request(request)
 421 |         syncPollVoteCookies()
 422 |         val pollBlock = articleParser.extractNormalizedPollBlock(response.body, pollId.toString())
 423 |                 ?: articleParser.extractNormalizedPollBlock(response.body)
 424 |         return pollBlock ?: throw IllegalStateException("Unable to read updated poll")
 425 |     }
 426 | 
 427 |     fun voteComment(action: Comment.Action): CommentKarmaVoteResult {
 428 |         val body = requestCommentAction(action)
 429 |         ensureCommentActionAccepted(body)
 430 |         return parseKarmaVoteResult(action, body)
 431 |     }
 432 | 
 433 |     fun likeComment(articleId: Int, commentId: Int): Boolean =
 434 |             voteComment(buildCommentVoteAction(articleId, commentId, Comment.Karma.LIKED)).likedByMe
 435 | 
 436 |     fun unlikeComment(articleId: Int, commentId: Int): Boolean =
 437 |             voteComment(buildCommentVoteAction(articleId, commentId, Comment.Karma.NOT_LIKED)).likedByMe
 438 | 
 439 |     fun executeCommentAction(action: Comment.Action, extraFields: Map<String, String> = emptyMap()): Boolean {
 440 |         if ((action.type == Comment.Action.Type.REPUTATION_PLUS || action.type == Comment.Action.Type.REPUTATION_MINUS) &&
 441 |                 action.method.equals(Comment.Action.METHOD_GET, ignoreCase = true)) {
 442 |             return executeReputationAction(action, extraFields)
 443 |         }
 444 |         ensureCommentActionAccepted(requestCommentAction(action, extraFields))
 445 |         return true
 446 |     }
 447 | 
 448 |     private fun requestCommentAction(
 449 |             action: Comment.Action,
 450 |             extraFields: Map<String, String> = emptyMap(),
 451 |     ): String {
 452 |         val url = action.url?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Empty action url")
 453 |         if (url.startsWith("#") || url.startsWith("javascript:", ignoreCase = true)) {
 454 |             throw IllegalArgumentException("Action has no network endpoint")
 455 |         }
 456 |         val fields = LinkedHashMap<String, String>().apply {
 457 |             putAll(action.fields)
 458 |             putAll(extraFields)
 459 |         }
 460 |         val method = action.method.uppercase(Locale.US)
 461 |         val request = if (method == Comment.Action.METHOD_POST || fields.isNotEmpty()) {
 462 |             NetworkRequest.Builder()
 463 |                     .url(url)
 464 |                     .xhrHeader()
 465 |                     .formHeaders(fields)
 466 |                     .build()
 467 |         } else {
 468 |             NetworkRequest.Builder()
 469 |                     .url(url)
 470 |                     .xhrHeader()
 471 |                     .build()
 472 |         }
 473 |         val response = webClient.request(request)
 474 |         return response.body
 475 |     }
 476 | 
 477 |     private fun parseKarmaVoteResult(action: Comment.Action, body: String): CommentKarmaVoteResult {
 478 |         val commentId = karmaActionCommentId(action)
 479 |         val vote = karmaActionVote(action)
 480 |         val parsedMap = articleParser.parseKarmaMap(body)
 481 |         val karma = when {
 482 |             commentId > 0 && parsedMap.get(commentId) != null -> parsedMap.get(commentId)!!.copy()
 483 |             parsedMap.size() == 1 -> parsedMap.valueAt(0).copy()
 484 |             else -> inferKarmaAfterVote(vote)
 485 |         }
 486 |         val resolvedCommentId = commentId.takeIf { it > 0 }
 487 |                 ?: if (parsedMap.size() == 1) parsedMap.keyAt(0) else 0
 488 |         return CommentKarmaVoteResult(resolvedCommentId, karma)
 489 |     }
 490 | 
 491 |     private fun inferKarmaAfterVote(vote: Int): Comment.Karma =
 492 |             Comment.Karma().apply {
 493 |                 status = when (vote) {
 494 |                     Comment.Karma.LIKED -> Comment.Karma.LIKED
 495 |                     Comment.Karma.NOT_LIKED -> Comment.Karma.NOT_LIKED
 496 |                     else -> 0
 497 |                 }
 498 |             }
 499 | 
 500 |     private fun karmaActionCommentId(action: Comment.Action): Int =
 501 |             action.fields["c"]?.toIntOrNull()
 502 |                     ?: karmaQueryField(action.url, "c")?.toIntOrNull()
 503 |                     ?: 0
 504 | 
 505 |     private fun karmaActionVote(action: Comment.Action): Int =
 506 |             action.fields["v"]?.toIntOrNull()
 507 |                     ?: karmaQueryField(action.url, "v")?.toIntOrNull()
 508 |                     ?: when (action.type) {
 509 |                         Comment.Action.Type.COMMENT_UNLIKE -> Comment.Karma.NOT_LIKED
 510 |                         Comment.Action.Type.COMMENT_LIKE -> Comment.Karma.LIKED
 511 |                         else -> 0
 512 |                     }
 513 | 
 514 |     private fun karmaQueryField(url: String?, key: String): String? {
 515 |         val query = url.orEmpty().substringAfter('?', "")
 516 |         if (query.isEmpty()) return null
 517 |         return query.split('&')
 518 |                 .mapNotNull { part ->
 519 |                     val pieces = part.split('=', limit = 2)
 520 |                     if (pieces.size == 2 && pieces[0].equals(key, ignoreCase = true)) pieces[1] else null
 521 |                 }
 522 |                 .firstOrNull()
 523 |     }
 524 | 
 525 |     private fun executeReputationAction(action: Comment.Action, extraFields: Map<String, String>): Boolean {
 526 |         val url = action.url?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Empty reputation action url")
 527 |         val response = webClient.request(
 528 |                 NetworkRequest.Builder()
 529 |                         .url(url)
 530 |                         .xhrHeader()
 531 |                         .build()
 532 |         )
 533 |         ensureCommentActionAccepted(response.body, allowForm = true)
 534 |         val userId = action.fields["mid"]?.toIntOrNull() ?: 0
 535 |         val formAction = articleParser.parseReputationAction(response.body, action.type, userId)
 536 |                 ?: throw IllegalStateException("Сервер не вернул форму изменения репутации")
 537 |         val reasonField = formAction.reasonFieldName ?: action.reasonFieldName ?: "message"
 538 |         executeCommentAction(formAction, mapOf(reasonField to extraFields.values.firstOrNull().orEmpty()))
 539 |         return true
 540 |     }
 541 | 
 542 |     fun deleteComment(action: Comment.Action): Boolean {
 543 |         val submitAction = when {
 544 |             action.fields.isNotEmpty() -> action
 545 |             else -> buildDeleteActionFromUrl(action) ?: loadDeleteCommentForm(action)
 546 |                     ?: throw IllegalStateException("Сервер не вернул форму удаления комментария")
 547 |         }
 548 |         executeCommentAction(submitAction)
 549 |         return true
 550 |     }
 551 | 
 552 |     fun editComment(
 553 |             action: Comment.Action,
 554 |             text: String,
 555 |             context: CommentEditContext = CommentEditContext()
 556 |     ): Boolean {
 557 |         val formAction = when {
 558 |             hasParsedEditForm(action) -> action
 559 |             else -> runCatching { loadEditCommentForm(action, context) }
 560 |                     .getOrNull()
 561 |                     ?: buildEditActionFromUrl(action)
 562 |                     ?: throw IllegalStateException("Unable to parse comment edit form")
 563 |         }
 564 |         val textField = formAction.fields.keys.firstOrNull { isCommentTextField(it) }
 565 |                 ?: throw IllegalStateException("Unable to find comment edit field")
 566 |         val url = formAction.url?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Empty edit action url")
 567 |         val fields = LinkedHashMap(formAction.fields)
 568 |         fields.remove(textField)
 569 |         val request = buildEditCommentSubmitRequest(url, fields, textField, text, formAction, context.articleUrl)
 570 |         if (BuildConfig.DEBUG) {
 571 |             Timber.d("NewsCommentEdit submit url=%s fields=%s", url, fields.keys)
 572 |         }
 573 |         val response = webClient.request(request)
 574 |         if (BuildConfig.DEBUG) {
 575 |             Timber.d(
 576 |                     "NewsCommentEdit submit response url=%s bodyLength=%d",
 577 |                     response.url,
 578 |                     response.body.length
 579 |             )
 580 |         }
 581 |         ensureCommentActionApplied(response.body)
 582 |         return true
 583 |     }
 584 | 
 585 |     fun loadEditCommentForm(action: Comment.Action, context: CommentEditContext = CommentEditContext()): Comment.Action {
 586 |         if (hasParsedEditForm(action)) return action
 587 |         val commentId = resolveEditCommentId(action)
 588 |         if (!isHttpCommentActionUrl(action.url)) {
 589 |             resolveInlineEditForm(action, context, commentId)?.let { return it }
 590 |         }
 591 |         resolveEditFormFromContext(action, context, commentId)?.let { return it }
 592 |         if (!action.editableHtml.isNullOrBlank() || !action.editableElementId.isNullOrBlank()) {
 593 |             enrichInlineEditAction(action, context)?.let { return it }
 594 |         }
 595 |         val tryAjaxPost = shouldProbeCommentEditAjaxPost(action)
 596 |         val articleReferer = context.articleUrl
 597 |         for (url in buildCommentModerationProbeUrls(action, commentId, edit = true)) {
 598 |             val body = requestCommentModerationHtml(
 599 |                     url,
 600 |                     commentId,
 601 |                     edit = true,
 602 |                     tryAjaxPost = tryAjaxPost,
 603 |                     articleReferer = articleReferer
 604 |             )
 605 |             logCommentEditProbe(url, body)
 606 |             articleParser.parseCommentEditAction(body)?.let { parsed ->
 607 |                 logCommentEditFormResolved(url, parsed, "parsed-form")
 608 |                 return parsed
 609 |             }
 610 |             articleParser.extractCommentEditActionFromHtml(body, commentId)?.let { parsed ->
 611 |                 logCommentEditFormResolved(url, parsed, "extracted-nonce")
 612 |                 return parsed
 613 |             }
 614 |         }
 615 |         buildEditActionFromUrl(action)?.let { parsed ->
 616 |             logCommentEditFormResolved(action.url.orEmpty(), parsed, "url-nonce")
 617 |             return parsed
 618 |         }
 619 |         refetchArticleHtmlForCommentEdit(context)?.let { freshHtml ->
 620 |             val refreshedContext = context.copy(articleHtml = freshHtml)
 621 |             resolveEditFormFromContext(action, refreshedContext, commentId)?.let { return it }
 622 |             resolveInlineEditForm(action, refreshedContext, commentId)?.let { return it }
 623 |         }
 624 |         throw IllegalStateException("Unable to parse comment edit form")
 625 |     }
 626 | 
 627 |     private fun resolveInlineEditForm(
 628 |             action: Comment.Action,
 629 |             context: CommentEditContext,
 630 |             commentId: Int
 631 |     ): Comment.Action? {
 632 |         if (commentId <= 0) return null
 633 |         val inlineText = action.editableHtml?.takeIf { it.isNotBlank() }
 634 |         val sources = listOfNotNull(context.articleHtml, context.commentsSource).distinct()
 635 |         sources.forEach { source ->
 636 |             articleParser.buildInlineCommentEditAction(
 637 |                     source = source,
 638 |                     commentId = commentId,
 639 |                     articleId = context.articleId,
 640 |                     inlineText = inlineText,
 641 |                     editableElementId = action.editableElementId,
 642 |                     submitText = action.submitText
 643 |             )?.let { parsed ->
 644 |                 logCommentEditFormResolved(source.take(80), parsed, "inline-wp-comments-post")
 645 |                 return mergeEditActionWithInlineSource(parsed, action)
 646 |             }
 647 |         }
 648 |         return null
 649 |     }
 650 | 
 651 |     private fun enrichInlineEditAction(
 652 |             action: Comment.Action,
 653 |             context: CommentEditContext
 654 |     ): Comment.Action? {
 655 |         val commentId = resolveEditCommentId(action).takeIf { it > 0 } ?: return null
 656 |         val inlineText = action.editableHtml?.takeIf { it.isNotBlank() }
 657 |                 ?: context.commentsSource?.let { source ->
 658 |                     articleParser.extractCommentEditActionFromHtml(source, commentId)
 659 |                             ?.fields
 660 |                             ?.entries
 661 |                             ?.firstOrNull { isCommentTextField(it.key) }
 662 |                             ?.value
 663 |                 }
 664 |                 ?: return null
 665 |         val resolved = resolveEditFormFromContext(action, context, commentId)
 666 |                 ?: return null
 667 |         val fields = LinkedHashMap(resolved.fields)
 668 |         val textField = fields.keys.firstOrNull { isCommentTextField(it) } ?: "content"
 669 |         fields[textField] = inlineText
 670 |         return mergeEditActionWithInlineSource(resolved.copy(fields = fields), action)
 671 |     }
 672 | 
 673 |     private fun resolveEditFormFromContext(
 674 |             action: Comment.Action,
 675 |             context: CommentEditContext,
 676 |             commentId: Int
 677 |     ): Comment.Action? {
 678 |         buildEditActionFromUrl(action)?.let { parsed ->
 679 |             val enriched = enrichEditActionWithArticleId(parsed, context.articleId)
 680 |             logCommentEditFormResolved(action.url.orEmpty(), enriched, "url-nonce")
 681 |             return mergeEditActionWithInlineSource(enriched, action)
 682 |         }
 683 |         if (commentId <= 0) return null
 684 |         listOfNotNull(context.commentsSource, context.articleHtml)
 685 |                 .distinct()
 686 |                 .forEach { source ->
 687 |                     articleParser.extractCommentEditActionFromHtml(source, commentId)?.let { parsed ->
 688 |                         logCommentEditFormResolved(source.take(80), parsed, "page-extracted")
 689 |                         return mergeEditActionWithInlineSource(parsed, action)
 690 |                     }
 691 |                     articleParser.parseCommentEditAction(source)?.let { parsed ->
 692 |                         logCommentEditFormResolved(source.take(80), parsed, "page-parsed")
 693 |                         return mergeEditActionWithInlineSource(parsed, action)
 694 |                     }
 695 |                 }
 696 |         articleParser.extractCommentEditNonceFromPage(context.articleHtml)?.let { (nonceName, nonce) ->
 697 |             val built = buildEditActionFromPageNonce(
 698 |                     commentId = commentId,
 699 |                     articleId = context.articleId,
 700 |                     nonceName = nonceName,
 701 |                     nonce = nonce
 702 |             )
 703 |             logCommentEditFormResolved(context.articleUrl.orEmpty(), built, "page-nonce")
 704 |             return mergeEditActionWithInlineSource(built, action)
 705 |         }
 706 |         return null
 707 |     }
 708 | 
 709 |     private fun buildEditActionFromPageNonce(
 710 |             commentId: Int,
 711 |             articleId: Int,
 712 |             nonceName: String,
 713 |             nonce: String
 714 |     ): Comment.Action {
 715 |         val usesAjaxNonce = nonceName.contains("ajax", ignoreCase = true)
 716 |         val submitUrl = if (usesAjaxNonce) {
 717 |             "https://4pda.to/wp-admin/admin-ajax.php?action=editcomment"
 718 |         } else {
 719 |             "https://4pda.to/wp-admin/comment.php"
 720 |         }
 721 |         val actionName = if (usesAjaxNonce) "editcomment" else "editedcomment"
 722 |         return Comment.Action(
 723 |                 url = submitUrl,
 724 |                 method = Comment.Action.METHOD_POST,
 725 |                 fields = linkedMapOf<String, String>().apply {
 726 |                     put(nonceName, nonce)
 727 |                     put("comment_ID", commentId.toString())
 728 |                     put("c", commentId.toString())
 729 |                     put("action", actionName)
 730 |                     put("content", "")
 731 |                     if (articleId > 0) put("comment_post_ID", articleId.toString())
 732 |                 },
 733 |                 type = Comment.Action.Type.EDIT
 734 |         )
 735 |     }
 736 | 
 737 |     private fun enrichEditActionWithArticleId(action: Comment.Action, articleId: Int): Comment.Action {
 738 |         if (articleId <= 0 || action.fields.containsKey("comment_post_ID")) return action
 739 |         val fields = LinkedHashMap(action.fields)
 740 |         fields["comment_post_ID"] = articleId.toString()
 741 |         return action.copy(fields = fields)
 742 |     }
 743 | 
 744 |     private fun mergeEditActionWithInlineSource(
 745 |             resolved: Comment.Action,
 746 |             source: Comment.Action
 747 |     ): Comment.Action {
 748 |         val fields = LinkedHashMap(resolved.fields)
 749 |         source.editableHtml?.takeIf { it.isNotBlank() }?.let { inlineText ->
 750 |             val textField = fields.keys.firstOrNull { isCommentTextField(it) } ?: "content"
 751 |             fields[textField] = inlineText
 752 |         }
 753 |         return resolved.copy(
 754 |                 fields = fields,
 755 |                 editableHtml = source.editableHtml ?: resolved.editableHtml,
 756 |                 editableElementId = source.editableElementId ?: resolved.editableElementId,
 757 |                 submitText = source.submitText ?: resolved.submitText
 758 |         )
 759 |     }
 760 | 
 761 |     private fun resolveEditCommentId(action: Comment.Action): Int =
 762 |             extractCommentIdFromModerationUrl(action.url.orEmpty())
 763 |                     ?: action.editableElementId
 764 |                             ?.removePrefix("comment-form-edit-")
 765 |                             ?.toIntOrNull()
 766 |                     ?: action.fields["comment_ID"]?.toIntOrNull()
 767 |                     ?: action.fields["c"]?.toIntOrNull()
 768 |                     ?: 0
 769 | 
 770 |     private fun refetchArticleHtmlForCommentEdit(context: CommentEditContext): String? {
 771 |         val articleUrl = context.articleUrl?.takeIf { it.isNotBlank() } ?: return null
 772 |         if (!articleUrl.contains("4pda", ignoreCase = true)) return null
 773 |         return runCatching {
 774 |             webClient.requestWithoutMobileCookie(
 775 |                     NetworkRequest.Builder()
 776 |                             .url(articleUrl)
 777 |                             .addHeader("User-Agent", DESKTOP_USER_AGENT)
 778 |                             .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
 779 |                             .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
 780 |                             .addHeader("Pragma", "no-cache")
 781 |                             .build()
 782 |             ).body
 783 |         }.getOrNull()?.takeIf { it.isNotBlank() }
 784 |     }
 785 | 
 786 |     fun loadDeleteCommentForm(action: Comment.Action): Comment.Action? {
 787 |         buildDeleteActionFromUrl(action)?.let { return it }
 788 |         for (url in buildCommentModerationProbeUrls(action, resolveEditCommentId(action), edit = false)) {
 789 |             val body = requestCommentModerationHtml(
 790 |                     url,
 791 |                     extractCommentIdFromModerationUrl(url) ?: 0,
 792 |                     edit = false,
 793 |                     tryAjaxPost = false
 794 |             )
 795 |             ensureCommentActionAccepted(body, allowForm = true)
 796 |             articleParser.parseCommentDeleteAction(body)?.let { return it }
 797 |         }
 798 |         return null
 799 |     }
 800 | 
 801 |     private fun shouldProbeCommentEditAjaxPost(action: Comment.Action): Boolean {
 802 |         if (action.url.orEmpty().contains("_wpnonce=", ignoreCase = true)) return false
 803 |         return extractCommentIdFromModerationUrl(action.url.orEmpty())?.let { it > 0 } == true
 804 |     }
 805 | 
 806 |     private fun requestCommentModerationHtml(
 807 |             url: String,
 808 |             commentId: Int = 0,
 809 |             edit: Boolean = false,
 810 |             tryAjaxPost: Boolean = false,
 811 |             articleReferer: String? = null
 812 |     ): String {
 813 |         val referer = moderationReferer(url, articleReferer)
 814 |         val accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
 815 |         val attempts = buildList {
 816 |             if (edit && commentId > 0 && tryAjaxPost) {
 817 |                 add { requestCommentEditAjaxPost(commentId, referer) }
 818 |             }
 819 |             if (url.contains("wp-admin", ignoreCase = true)) {
 820 |                 add {
 821 |                     webClient.requestWithoutMobileCookie(
 822 |                             NetworkRequest.Builder()
 823 |                                     .url(url)
 824 |                                     .addHeader("User-Agent", DESKTOP_USER_AGENT)
 825 |                                     .addHeader("Accept", accept)
 826 |                                     .addHeader("Referer", referer)
 827 |                                     .build()
 828 |                     ).body
 829 |                 }
 830 |             }
 831 |             add {
 832 |                 webClient.request(
 833 |                         NetworkRequest.Builder()
 834 |                                 .url(url)
 835 |                                 .addHeader("Accept", accept)
 836 |                                 .addHeader("Referer", referer)
 837 |                                 .build()
 838 |                 ).body
 839 |             }
 840 |             add {
 841 |                 webClient.request(
 842 |                         NetworkRequest.Builder()
 843 |                                 .url(url)
 844 |                                 .xhrHeader()
 845 |                                 .addHeader("Referer", referer)
 846 |                                 .build()
 847 |                 ).body
 848 |             }
 849 |             if (!url.contains("wp-admin", ignoreCase = true)) {
 850 |                 add {
 851 |                     webClient.requestWithoutMobileCookie(
 852 |                             NetworkRequest.Builder()
 853 |                                     .url(url)
 854 |                                     .addHeader("User-Agent", DESKTOP_USER_AGENT)
 855 |                                     .addHeader("Accept", accept)
 856 |                                     .addHeader("Referer", referer)
 857 |                                     .build()
 858 |                     ).body
 859 |                 }
 860 |             }
 861 |         }
 862 |         var lastBody = ""
 863 |         attempts.forEach { fetch ->
 864 |             val body = runCatching { fetch() }.getOrDefault("")
 865 |             lastBody = body
 866 |             if (looksLikeCommentModerationForm(body)) return body
 867 |         }
 868 |         return lastBody
 869 |     }
 870 | 
 871 |     private fun requestCommentEditAjaxPost(commentId: Int, referer: String): String {
 872 |         fun buildRequest(desktopUa: Boolean): NetworkRequest {
 873 |             val builder = NetworkRequest.Builder()
 874 |                     .url("https://4pda.to/wp-admin/admin-ajax.php")
 875 |                     .xhrHeader()
 876 |                     .addHeader("Referer", referer)
 877 |                     .formHeader("action", "editcomment")
 878 |                     .formHeader("c", commentId.toString())
 879 |             if (desktopUa) {
 880 |                 builder.addHeader("User-Agent", DESKTOP_USER_AGENT)
 881 |             }
 882 |             return builder.build()
 883 |         }
 884 |         val attempts = listOf(
 885 |                 { webClient.requestWithoutMobileCookie(buildRequest(desktopUa = true)).body },
 886 |                 { webClient.request(buildRequest(desktopUa = false)).body },
 887 |         )
 888 |         var lastBody = ""
 889 |         attempts.forEach { fetch ->
 890 |             val body = fetch()
 891 |             lastBody = body
 892 |             if (looksLikeCommentModerationForm(body)) return body
 893 |         }
 894 |         return lastBody
 895 |     }
 896 | 
 897 |     private fun moderationReferer(url: String, articleReferer: String? = null): String =
 898 |             articleReferer?.takeIf { it.contains("4pda", ignoreCase = true) }
 899 |                     ?: if (url.contains("wp-admin", ignoreCase = true)) {
 900 |                         "https://4pda.to/"
 901 |                     } else {
 902 |                         "https://4pda.to/"
 903 |                     }
 904 | 
 905 |     private fun buildEditCommentSubmitRequest(
 906 |             url: String,
 907 |             fields: LinkedHashMap<String, String>,
 908 |             textField: String,
 909 |             text: String,
 910 |             sourceAction: Comment.Action,
 911 |             articleReferer: String? = null
 912 |     ): NetworkRequest {
 913 |         val referer = articleReferer?.takeIf { it.contains("4pda", ignoreCase = true) }
 914 |                 ?: moderationReferer(sourceAction.url.orEmpty(), articleReferer)
 915 |         val builder = NetworkRequest.Builder()
 916 |                 .url(url)
 917 |                 .addHeader("Referer", referer)
 918 |                 .formHeaders(fields)
 919 |                 .formHeader(textField, Cp1251Codec.encode(text), true)
 920 |         return if (url.contains("admin-ajax.php", ignoreCase = true)) {
 921 |             builder.xhrHeader().build()
 922 |         } else {
 923 |             builder.build()
 924 |         }
 925 |     }
 926 | 
 927 |     private fun hasParsedEditForm(action: Comment.Action): Boolean =
 928 |             action.fields.keys.any { isCommentTextField(it) } &&
 929 |                     action.fields.keys.any { isModerationNonceField(it) } &&
 930 |                     !action.url.isNullOrBlank()
 931 | 
 932 |     private fun looksLikeCommentModerationForm(body: String): Boolean {
 933 |         if (body.isBlank()) return false
 934 |         return articleParser.canExtractCommentEditAction(body) ||
 935 |                 articleParser.parseCommentDeleteAction(body) != null
 936 |     }
 937 | 
 938 |     private fun buildCommentModerationProbeUrls(
 939 |             action: Comment.Action,
 940 |             commentId: Int,
 941 |             edit: Boolean
 942 |     ): List<String> {
 943 |         val primary = action.url?.takeIf { isHttpCommentActionUrl(it) }
 944 |         val resolvedCommentId = commentId.takeIf { it > 0 }
 945 |                 ?: primary?.let { extractCommentIdFromModerationUrl(it) }
 946 |         if (primary == null && resolvedCommentId == null) {
 947 |             throw IllegalArgumentException("Empty comment moderation action url")
 948 |         }
 949 |         return buildList {
 950 |             if (resolvedCommentId != null) {
 951 |                 if (edit) {
 952 |                     add("https://4pda.to/wp-admin/comment.php?action=editcomment&c=$resolvedCommentId")
 953 |                 } else {
 954 |                     add("https://4pda.to/wp-admin/comment.php?action=deletecomment&c=$resolvedCommentId")
 955 |                 }
 956 |             }
 957 |             primary?.let { add(it) }
 958 |             if (resolvedCommentId != null) {
 959 |                 if (edit) {
 960 |                     add("https://4pda.to/wp-admin/admin-ajax.php?action=editcomment&c=$resolvedCommentId")
 961 |                 } else {
 962 |                     add("https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment&c=$resolvedCommentId")
 963 |                 }
 964 |             }
 965 |         }.distinct()
 966 |     }
 967 | 
 968 |     private fun isHttpCommentActionUrl(url: String?): Boolean {
 969 |         if (url.isNullOrBlank()) return false
 970 |         return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
 971 |     }
 972 | 
 973 |     private fun buildDeleteActionFromUrl(action: Comment.Action): Comment.Action? {
 974 |         val params = extractModerationQueryParams(action.url) ?: return null
 975 |         val nonce = params["_wpnonce"] ?: return null
 976 |         val commentId = params["c"] ?: params["comment_ID"] ?: return null
 977 |         val submitUrl = when {
 978 |             action.url.orEmpty().contains("admin-ajax.php", ignoreCase = true) ->
 979 |                 "https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment"
 980 |             else -> "https://4pda.to/wp-admin/comment.php"
 981 |         }
 982 |         return Comment.Action(
 983 |                 url = submitUrl,
 984 |                 method = Comment.Action.METHOD_POST,
 985 |                 fields = linkedMapOf(
 986 |                         "_wpnonce" to nonce,
 987 |                         "comment_ID" to commentId,
 988 |                         "action" to (params["action"] ?: "deletecomment")
 989 |                 ),
 990 |                 type = Comment.Action.Type.DELETE,
 991 |                 requiresConfirmation = true
 992 |         )
 993 |     }
 994 | 
 995 |     private fun buildEditActionFromUrl(action: Comment.Action): Comment.Action? {
 996 |         val params = extractModerationQueryParams(action.url) ?: return null
 997 |         val nonce = params["_wpnonce"] ?: return null
 998 |         val commentId = params["c"] ?: params["comment_ID"] ?: return null
 999 |         val usesCommentPhp = action.url.orEmpty().contains("comment.php", ignoreCase = true)
1000 |         val submitUrl = when {
1001 |             usesCommentPhp -> "https://4pda.to/wp-admin/comment.php"
1002 |             else -> "https://4pda.to/wp-admin/admin-ajax.php?action=editcomment"
1003 |         }
1004 |         val textField = "content"
1005 |         return Comment.Action(
1006 |                 url = submitUrl,
1007 |                 method = Comment.Action.METHOD_POST,
1008 |                 fields = linkedMapOf(
1009 |                         "_wpnonce" to nonce,
1010 |                         "comment_ID" to commentId,
1011 |                         "action" to if (usesCommentPhp) "editedcomment" else "editcomment",
1012 |                         textField to ""
1013 |                 ),
1014 |                 type = Comment.Action.Type.EDIT
1015 |         )
1016 |     }
1017 | 
1018 |     private fun extractModerationQueryParams(url: String?): Map<String, String>? {
1019 |         if (url.isNullOrBlank()) return null
1020 |         return try {
1021 |             val uri = android.net.Uri.parse(url)
1022 |             buildMap {
1023 |                 listOf("_wpnonce", "c", "comment_ID", "action").forEach { key ->
1024 |                     uri.getQueryParameter(key)?.takeIf { it.isNotBlank() }?.let { put(key, it) }
1025 |                 }
1026 |             }.takeIf { it.isNotEmpty() }
1027 |         } catch (_: Throwable) {
1028 |             null
1029 |         }
1030 |     }
1031 | 
1032 |     private fun extractCommentIdFromModerationUrl(url: String): Int? {
1033 |         return try {
1034 |             val uri = android.net.Uri.parse(url)
1035 |             uri.getQueryParameter("c")?.toIntOrNull()?.takeIf { it > 0 }
1036 |                     ?: uri.getQueryParameter("comment_ID")?.toIntOrNull()?.takeIf { it > 0 }
1037 |         } catch (_: Throwable) {
1038 |             null
1039 |         }
1040 |     }
1041 | 
1042 |     private fun buildCommentVoteAction(articleId: Int, commentId: Int, vote: Int): Comment.Action {
1043 |         val articlePart = articleId.takeIf { it > 0 }?.let { "p=$it&" }.orEmpty()
1044 |         return Comment.Action(
1045 |                 url = "https://4pda.to/pages/karma?${articlePart}c=$commentId&v=$vote",
1046 |                 type = if (vote == Comment.Karma.NOT_LIKED) {
1047 |                     Comment.Action.Type.COMMENT_UNLIKE
1048 |                 } else {
1049 |                     Comment.Action.Type.COMMENT_LIKE
1050 |                 }
1051 |         )
1052 |     }
1053 | 
1054 |     private fun ensureCommentActionAccepted(body: String, allowForm: Boolean = false) {
1055 |         detectCommentActionError(body, allowForm)?.let { throw IllegalStateException(it) }
1056 |     }
1057 | 
1058 |     private fun ensureCommentActionApplied(body: String) {
1059 |         detectCommentActionError(body)?.let { throw IllegalStateException(it) }
1060 |         if (body.isBlank()) {
1061 |             throw IllegalStateException("Сервер вернул пустой ответ")
1062 |         }
1063 |     }
1064 | 
1065 |     private fun detectCommentActionError(body: String, allowForm: Boolean = false): String? {
1066 |         if (body.isBlank()) return null
1067 |         val normalized = body.lowercase(Locale.US)
1068 |         if (allowForm && normalized.contains("<form")) return null
1069 |         if (isCommentModerationSuccessResponse(normalized)) return null
1070 |         return when {
1071 |             normalized.contains("не указан пользователь") ->
1072 |                 "Не указан пользователь, сообщение или сообщение слишком длинное"
1073 |             normalized.contains("no permission") || normalized.contains("нет прав") ->
1074 |                 "Нет прав для выполнения действия"
1075 |             normalized.contains("not allowed") || normalized.contains("not permitted") ||
1076 |                     normalized.contains("you are not allowed") -> "Нет прав для выполнения действия"
1077 |             normalized.contains("not authorized") || normalized.contains("войдите") ->
1078 |                 "Требуется авторизация"
1079 |             isNonceSecurityError(normalized) -> "Истёк токен действия"
1080 |             else -> null
1081 |         }
1082 |     }
1083 | 
1084 |     private fun isNonceSecurityError(normalized: String): Boolean {
1085 |         if (normalized.contains("invalid nonce")) return true
1086 |         if (normalized.contains("nonce failed") || normalized.contains("nonce failure")) return true
1087 |         if (normalized.contains("nonce verification")) return true
1088 |         if (normalized.contains("check_ajax_referer")) return true
1089 |         if (normalized.contains("link you followed has expired")) return true
1090 |         if (normalized.contains("security check failed") && !normalized.contains("name=\"_wpnonce\"")) return true
1091 |         if (normalized.contains("истёк") && (normalized.contains("ссылк") || normalized.contains("токен"))) return true
1092 |         if (normalized.contains("токен") && normalized.contains("недейств")) return true
1093 |         if (normalized.contains("token") && normalized.contains("expired")) return true
1094 |         return false
1095 |     }
1096 | 
1097 |     private fun isCommentModerationSuccessResponse(normalized: String): Boolean {
1098 |         if (normalized.contains("editedcomment") && normalized.contains("comment updated")) return true
1099 |         if (normalized.contains("комментарий") &&
1100 |                 (normalized.contains("обновлен") || normalized.contains("обновлён") || normalized.contains("сохранен"))) {
1101 |             return true
1102 |         }
1103 |         if (normalized.contains("comment updated") || normalized.contains("comment has been updated")) return true
1104 |         return false
1105 |     }
1106 | 
1107 |     private fun isCommentTextField(name: String): Boolean =
1108 |             name.equals("comment", ignoreCase = true) ||
1109 |                     name.equals("content", ignoreCase = true) ||
1110 |                     name.equals("message", ignoreCase = true) ||
1111 |                     name.equals("text", ignoreCase = true) ||
1112 |                     name.equals("newcomment_content", ignoreCase = true)
1113 | 
1114 |     private fun isModerationNonceField(name: String): Boolean =
1115 |             name.equals("_wpnonce", ignoreCase = true) ||
1116 |                     name.equals("_ajax_nonce-replyto-comment", ignoreCase = true) ||
1117 |                     name.equals("_ajax_nonce", ignoreCase = true) ||
1118 |                     name.equals("wpnonce", ignoreCase = true)
1119 | 
1120 |     fun parseComments(karmaMap: SparseArray<Comment.Karma>, source: String?): Comment {
1121 |         return articleParser.parseComments(karmaMap, source)
1122 |     }
1123 | 
1124 |     fun rebalanceCommentsSource(article: DetailsPage): Boolean {
1125 |         val balanced = articleParser.ensureBalancedCommentsHtml(article.commentsSource)
1126 |         if (balanced.isNullOrBlank() || balanced == article.commentsSource) return false
1127 |         article.commentsSource = balanced
1128 |         return true
1129 |     }
1130 | 
1131 |     fun hasCommentNodeMarkup(source: String?): Boolean =
1132 |             articleParser.hasCommentNodeMarkup(source)
1133 | 
1134 |     fun countCommentNodesInSource(source: String?): Int =
1135 |             articleParser.countCommentNodesInSource(source)
1136 | 
1137 |     fun commentsSourceUnderfetchesExpected(source: String?, expectedCount: Int): Boolean =
1138 |             articleParser.commentsSourceUnderfetchesExpected(source, expectedCount)
1139 | 
1140 |     /** Caps a parsed tree to one inline batch (depth-first, max [COMMENTS_PER_PAGE] nodes). */
1141 |     fun capPaginatedCommentBatch(root: Comment, commentPage: Int = 1): Comment =
1142 |             limitPaginatedCommentBatch(root, commentPage.coerceAtLeast(1))
1143 | 
1144 |     fun parseComments(article: DetailsPage, paginated: Boolean = false, commentPage: Int = 1): Comment {
1145 |         val expectedCount = article.commentsCount.coerceAtLeast(0)
1146 |         val mobileSource = articleParser.ensureBalancedCommentsHtml(article.commentsSource)
1147 |                 ?: article.commentsSource
1148 |         if (paginated) {
1149 |             val page = commentPage.coerceAtLeast(1)
1150 |             val perPage = forpdateam.ru.forpda.presentation.articles.detail.comments
1151 |                     .ArticleCommentsPagination.COMMENTS_PER_PAGE
1152 |             val skip = (page - 1).coerceAtLeast(0) * perPage
1153 |             var comments = articleParser.parseCommentsBatch(
1154 |                     article.karmaMap,
1155 |                     mobileSource,
1156 |                     skip,
1157 |                     perPage,
1158 |             )
1159 |             if (articleParser.countParsedComments(comments) <= 0) {
1160 |                 val tagTree = articleParser.parseCommentsViaTagsOnly(article.karmaMap, mobileSource)
1161 |                 comments = limitPaginatedCommentBatch(tagTree, page)
1162 |                 if (articleParser.countParsedComments(comments) <= 0) {
1163 |                     comments = limitPaginatedCommentBatch(
1164 |                             articleParser.parseComments(article.karmaMap, mobileSource),
1165 |                             page,
1166 |                     )
1167 |                 }
1168 |             }
1169 |             comments = articleParser.mergeCommentDesktopActions(comments, article.desktopCommentsSource)
1170 |             val userId = currentUserId()
1171 |             if (userId > 0) {
1172 |                 articleParser.applyFallbackOwnCommentActions(comments, userId)
1173 |             }
1174 |             if (page <= 1) {
1175 |                 logOwnCommentActions(comments, article.desktopCommentsSource)
1176 |             }
1177 |             articleParser.ensureCommentLikeActions(
1178 |                     comments,
1179 |                     article.id,
1180 |                     mobileSource ?: article.commentsSource,
1181 |             )
1182 |             return comments
1183 |         }
1184 |         var comments = articleParser.parseComments(article.karmaMap, mobileSource)
1185 |         var parsedCount = articleParser.countParsedComments(comments)
1186 |         val mobileUnderfetches = expectedCount > 0 &&
1187 |                 articleParser.commentsSourceUnderfetchesExpected(mobileSource, expectedCount)
1188 |         if (!paginated &&
1189 |                 (comments.children.isEmpty() || (mobileUnderfetches && parsedCount < expectedCount))
1190 |         ) {
1191 |             article.desktopCommentsSource
1192 |                     ?.let { articleParser.ensureBalancedCommentsHtml(it) ?: it }
1193 |                     ?.let { desktop ->
1194 |                         val desktopTree = articleParser.parseComments(article.karmaMap, desktop)
1195 |                         val desktopParsed = articleParser.countParsedComments(desktopTree)
1196 |                         if (desktopTree.children.isNotEmpty() && desktopParsed > parsedCount) {
1197 |                             comments = desktopTree
1198 |                             parsedCount = desktopParsed
1199 |                             if (!article.commentsSource.isNullOrBlank()) {
1200 |                                 article.commentsSource = desktop
1201 |                             }
1202 |                         }
1203 |                     }
1204 |         }
1205 |         comments = articleParser.mergeCommentDesktopActions(comments, article.desktopCommentsSource)
1206 |         articleParser.ensureCommentLikeActions(
1207 |                 comments,
1208 |                 article.id,
1209 |                 mobileSource ?: article.commentsSource,
1210 |         )
1211 |         val userId = currentUserId()
1212 |         if (userId > 0) {
1213 |             articleParser.applyFallbackOwnCommentActions(comments, userId)
1214 |         }
1215 |         logOwnCommentActions(comments, article.desktopCommentsSource)
1216 |         return comments
1217 |     }
1218 | 
1219 |     /**
1220 |      * Paginated loads must never surface more than one WP comment page (~20) per batch.
1221 |      * Desktop `comment-page-N` HTML may still contain the full list; [commentPage] skips
1222 |      * earlier pages in depth-first render order before taking the next batch.
1223 |      */
1224 |     private fun limitPaginatedCommentBatch(root: Comment, commentPage: Int): Comment {
1225 |         val max = forpdateam.ru.forpda.presentation.articles.detail.comments
1226 |                 .ArticleCommentsPagination.COMMENTS_PER_PAGE
1227 |         val skip = (commentPage - 1).coerceAtLeast(0) * max
1228 |         val flattenedCount = root.flattenComments().size
1229 |         if (flattenedCount <= skip) return Comment()
1230 |         if (commentPage <= 1 && flattenedCount <= max) return root
1231 |         val cursor = PaginatedCommentCursor(skip, max)
1232 |         val limited = Comment()
1233 |         for (child in root.children) {
1234 |             if (cursor.budgetExhausted()) break
1235 |             val (limitedChild, added) = takeCommentSubtreeForPaginatedBatch(child, cursor)
1236 |             if (added > 0) {
1237 |                 limited.children.add(limitedChild)
1238 |             }
1239 |         }
1240 |         if (flattenedCount > max || skip > 0) {
1241 |             FpdaDebugLog.log(
1242 |                     FpdaDebugLog.TAG_COMMENTS_SECTION,
1243 |                     "paginated_batch_capped",
1244 |                     mapOf(
1245 |                             "parsedCount" to flattenedCount,
1246 |                             "topLevelCount" to root.children.size,
1247 |                             "cappedTo" to max,
1248 |                             "skip" to skip,
1249 |                             "page" to commentPage,
1250 |                             "batchCount" to cursor.takenCount(),
1251 |                     )
1252 |             )
1253 |         }
1254 |         return limited
1255 |     }
1256 | 
1257 |     private class PaginatedCommentCursor(
1258 |             private val skip: Int,
1259 |             private val max: Int,
1260 |     ) {
1261 |         private var seen = 0
1262 |         private var taken = 0
1263 | 
1264 |         fun budgetExhausted(): Boolean = taken >= max
1265 | 
1266 |         fun takenCount(): Int = taken
1267 | 
1268 |         fun onNode(): PaginatedNodeDisposition {
1269 |             if (taken >= max) return PaginatedNodeDisposition.STOP
1270 |             val index = seen++
1271 |             return if (index < skip) {
1272 |                 PaginatedNodeDisposition.SKIP
1273 |             } else {
1274 |                 taken++
1275 |                 PaginatedNodeDisposition.TAKE
1276 |             }
1277 |         }
1278 |     }
1279 | 
1280 |     private enum class PaginatedNodeDisposition {
1281 |         SKIP,
1282 |         TAKE,
1283 |         STOP,
1284 |     }
1285 | 
1286 |     /** Depth-first walk matching inline comment render order. */
1287 |     private fun takeCommentSubtreeForPaginatedBatch(
1288 |             node: Comment,
1289 |             cursor: PaginatedCommentCursor,
1290 |     ): Pair<Comment, Int> {
1291 |         when (cursor.onNode()) {
1292 |             PaginatedNodeDisposition.STOP -> return Comment() to 0
1293 |             PaginatedNodeDisposition.SKIP -> {
1294 |                 for (child in node.children) {
1295 |                     if (cursor.budgetExhausted()) break
1296 |                     takeCommentSubtreeForPaginatedBatch(child, cursor)
1297 |                 }
1298 |                 return Comment() to 0
1299 |             }
1300 |             PaginatedNodeDisposition.TAKE -> {
1301 |                 val copy = Comment(node).apply { children.clear() }
1302 |                 var used = 1
1303 |                 for (child in node.children) {
1304 |                     if (cursor.budgetExhausted()) break
1305 |                     val (limitedChild, added) = takeCommentSubtreeForPaginatedBatch(child, cursor)
1306 |                     if (added > 0) {
1307 |                         copy.children.add(limitedChild)
1308 |                         used += added
1309 |                     }
1310 |                 }
1311 |                 return copy to used
1312 |             }
1313 |         }
1314 |     }
1315 | 
1316 |     private fun logCommentEditProbe(url: String, body: String) {
1317 |         if (!BuildConfig.DEBUG) return
1318 |         Timber.d(
1319 |                 "NewsCommentEdit probe url=%s hasForm=%s parsed=%s extracted=%s bodyLength=%d",
1320 |                 url,
1321 |                 body.contains("<form", ignoreCase = true),
1322 |                 articleParser.parseCommentEditAction(body) != null,
1323 |                 articleParser.extractCommentEditActionFromHtml(body) != null,
1324 |                 body.length
1325 |         )
1326 |     }
1327 | 
1328 |     private fun logCommentEditFormResolved(url: String, action: Comment.Action, source: String) {
1329 |         if (!BuildConfig.DEBUG) return
1330 |         Timber.d(
1331 |                 "NewsCommentEdit resolved source=%s url=%s submit=%s hasNonce=%s textField=%s",
1332 |                 source,
1333 |                 url,
1334 |                 action.url,
1335 |                 action.fields.containsKey("_wpnonce"),
1336 |                 action.fields.keys.firstOrNull { isCommentTextField(it) }
1337 |         )
1338 |     }
1339 | 
1340 |     private fun logOwnCommentActions(root: Comment, desktopCommentsSource: String?) {
1341 |         if (!BuildConfig.DEBUG) return
1342 |         val userId = currentUserId()
1343 |         if (userId <= 0) return
1344 |         val source = if (desktopCommentsSource.isNullOrBlank()) "mobile" else "mobile+desktop"
1345 |         root.flattenComments()
1346 |                 .filter {
1347 |                     it.userId == userId ||
1348 |                             it.actions.edit?.isValid() == true ||
1349 |                             it.actions.delete?.isValid() == true
1350 |                 }
1351 |                 .forEach { comment ->
1352 |                     Timber.d(
1353 |                             "NewsComments ownActions id=%d authorId=%d isOwn=%s hasEdit=%s hasDelete=%s hasRep=%s source=%s profile=%s like=%s report=%s",
1354 |                             comment.id,
1355 |                             comment.userId,
1356 |                             comment.userId == userId ||
1357 |                                     comment.actions.edit?.isValid() == true ||
1358 |                                     comment.actions.delete?.isValid() == true,
1359 |                             comment.actions.edit?.isValid() == true,
1360 |                             comment.actions.delete?.isValid() == true,
1361 |                             comment.actions.reputationPlus?.isValid() == true ||
1362 |                                     comment.actions.reputationMinus?.isValid() == true,
1363 |                             source,
1364 |                             comment.actions.profile?.isValid() == true,
1365 |                             comment.likeAction?.isValid() == true || comment.unlikeAction?.isValid() == true,
1366 |                             comment.actions.report?.isValid() == true
1367 |                     )
1368 |                 }
1369 |     }
1370 | 
1371 |     private fun Comment.flattenComments(): List<Comment> =
1372 |             children.flatMap { listOf(it) + it.flattenComments() }
1373 | 
1374 |     suspend fun replyComment(articleId: Int, commentId: Int, text: String): DetailsPage {
1375 |         val comment = Cp1251Codec.encode(text)
1376 |         val articleUrl = "https://4pda.to/index.php?p=$articleId"
1377 | 
1378 |         val builder = NetworkRequest.Builder()
1379 |                 .url("https://4pda.to/wp-comments-post.php")
1380 |                 .formHeader("comment_post_ID", articleId.toString())
1381 |                 .formHeader("comment_reply_ID", commentId.toString())
1382 |                 .formHeader("comment_reply_dp", if (commentId == 0) "0" else "1")
1383 |                 .formHeader("comment", comment, true)
1384 |         val response = webClient.request(builder.build())
1385 |         syncPollVoteCookies()
1386 |         val article = articleParser.parseArticle(response.body)
1387 |         article.url = response.redirectWithFragment
1388 |         return loadDesktopExtrasIfMissing(articleUrl, response, article)
1389 |     }
1390 | 
1391 | 
1392 |     private fun getLink(category: String?, pageNumber: Int): String {
1393 |         return getPageLink(getUrlCategory(category), pageNumber)
1394 |     }
1395 | 
1396 |     private fun getPageLink(url: String, pageNumber: Int): String {
1397 |         if (pageNumber < 2) {
1398 |             return url
1399 |         }
1400 |         return url + "page/" + pageNumber + "/"
1401 |     }
1402 | 
1403 |     private fun getUrlCategory(category: String?): String =
1404 |             Constants.getNewsCategoryUrl(Constants.normalizeNewsCategory(category))
1405 | 
1406 |     private fun Int?.orZero(): Int = this ?: 0
1407 | }
1408 | 
```

## Skipped Files

- .ai-bridge/ [not a file]
- app/src/main/assets/forpda/scripts/modules/theme.js [File is too large (156513 bytes). Limit: 120000 bytes.]
- app/src/main/assets/forpda/styles/modules/themes.less [missing]
- app/src/main/java/forpdateam/ru/forpda/common/di/ [not a file]
- app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemDao.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/entity/db/offline/OfflineItemRoom.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineArticleSource.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineImageDownloader.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineIndexPathHandler.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineRepository.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineSaveController.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineStorage.kt [missing]
- app/src/main/java/forpdateam/ru/forpda/model/data/offline/OfflineWebViewBaseUrl.kt [missing]
