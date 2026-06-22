package forpdateam.ru.forpda.model.datastore

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.ui.AppFontMode
import forpdateam.ru.forpda.ui.FontController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

private val Context.mainDataStore: DataStore<Preferences> by preferencesDataStore(name = "main")

class MainDataStore(private val context: Context) {
    private val mirrorPrefs = context.getSharedPreferences("main_mirror", Context.MODE_PRIVATE)

    private inline fun <T> safeDataStoreFlow(
            flow: Flow<T>,
            default: T
    ): Flow<T> = flow.catch { e ->
        Timber.e(e, "DataStore read error, returning default")
        emit(default)
    }

    private suspend inline fun safeEdit(crossinline block: suspend (MutablePreferences) -> Unit) {
        try {
            context.mainDataStore.edit { preferences ->
                block(preferences)
            }
        } catch (e: Exception) {
            Timber.e(e, "DataStore edit error")
        }
    }

    private object PreferencesKeys {
        val WEBVIEW_FONT_SIZE = intPreferencesKey("webview_font_size")
        val IS_SYSTEM_DOWNLOADER = booleanPreferencesKey("is_system_downloader")
        val DOWNLOAD_METHOD = stringPreferencesKey("download_method")
        val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_uri")
        val IS_EDITOR_MONOSPACE = booleanPreferencesKey("is_editor_monospace")
        val IS_EDITOR_DEFAULT_HIDDEN = booleanPreferencesKey("is_editor_default_hidden")
        val SCROLL_BUTTON_ENABLE = booleanPreferencesKey("scroll_button_enable")
        val TOPIC_PAGINATION_PANEL_ENABLE = booleanPreferencesKey("topic_pagination_panel_enable")
        val TOPIC_SCROLL_MODE = stringPreferencesKey("topic_scroll_mode")
        val TOPIC_POST_DENSITY = stringPreferencesKey("topic_post_density")
        val TOPIC_TOOLBAR_BEHAVIOR = stringPreferencesKey("topic_toolbar_behavior")
        val TOPIC_PAGE_SWIPE_ENABLE = booleanPreferencesKey("topic_page_swipe_enable")
        val TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE = booleanPreferencesKey("topic_bottom_refresh_gesture_enable")
        val TOPIC_BACK_BEHAVIOR = stringPreferencesKey("topic_back_behavior")
        val TOPIC_OPEN_TARGET = stringPreferencesKey("topic_open_target")
        val TOPIC_HEADER_INITIAL_STATE = stringPreferencesKey("topic_header_initial_state")
        val BOTTOM_NAV_COLUMNS = stringPreferencesKey("bottom_nav_columns")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_BOTTOM_ARROW = booleanPreferencesKey("show_bottom_arrow")
        val UI_PALETTE = stringPreferencesKey("ui_palette")
        val APP_FONT_MODE = stringPreferencesKey("app_font_mode")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
        val USE_MATERIAL_YOU = booleanPreferencesKey("use_material_you")
        val WEBVIEW_COMPATIBILITY_MODE = booleanPreferencesKey("webview_compatibility_mode")
        val WEBVIEW_SMART_PRELOAD = booleanPreferencesKey("webview_smart_preload")
    }

    fun observeWebViewFontSizeFlow(): Flow<Int> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                max(min(preferences[PreferencesKeys.WEBVIEW_FONT_SIZE] ?: 16, 64), 8)
            }, 16)

    fun observeScrollButtonEnabledFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.SCROLL_BUTTON_ENABLE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.SCROLL_BUTTON_ENABLE, true)
            }, true)

    fun observeTopicPaginationPanelEnabledFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.TOPIC_PAGINATION_PANEL_ENABLE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.TOPIC_PAGINATION_PANEL_ENABLE, false)
            }, false)

    fun observeTopicScrollModeFlow(): Flow<AppPreferences.Main.TopicScrollMode> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseTopicScrollMode(
                    preferences[PreferencesKeys.TOPIC_SCROLL_MODE]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.TOPIC_SCROLL_MODE, null)
                )
            }, AppPreferences.Main.TopicScrollMode.HYBRID)

    fun observeTopicPostDensityFlow(): Flow<AppPreferences.Main.TopicPostDensity> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseTopicPostDensity(
                    preferences[PreferencesKeys.TOPIC_POST_DENSITY]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.TOPIC_POST_DENSITY, null)
                )
            }, AppPreferences.Main.TopicPostDensity.COMFORTABLE)

    fun observeTopicToolbarBehaviorFlow(): Flow<AppPreferences.Main.TopicToolbarBehavior> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseTopicToolbarBehavior(
                    preferences[PreferencesKeys.TOPIC_TOOLBAR_BEHAVIOR]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.TOPIC_TOOLBAR_BEHAVIOR, null)
                )
            }, AppPreferences.Main.TopicToolbarBehavior.PINNED)

    fun observeTopicPageSwipeEnabledFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.TOPIC_PAGE_SWIPE_ENABLE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.TOPIC_PAGE_SWIPE_ENABLE, false)
            }, false)

    fun observeTopicBottomRefreshGestureEnabledFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE, true)
            }, true)

    fun observeTopicBackBehaviorFlow(): Flow<AppPreferences.Main.TopicBackBehavior> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseTopicBackBehavior(
                    preferences[PreferencesKeys.TOPIC_BACK_BEHAVIOR]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.TOPIC_BACK_BEHAVIOR, null)
                )
            }, AppPreferences.Main.TopicBackBehavior.HISTORY)

    fun observeTopicOpenTargetFlow(): Flow<AppPreferences.Main.TopicOpenTarget> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseTopicOpenTarget(
                    preferences[PreferencesKeys.TOPIC_OPEN_TARGET]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.TOPIC_OPEN_TARGET, null)
                )
            }, AppPreferences.Main.TopicOpenTarget.LAST_UNREAD)

    fun observeTopicHeaderInitialStateFlow(): Flow<AppPreferences.Main.TopicHeaderInitialState> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseTopicHeaderInitialState(
                    preferences[PreferencesKeys.TOPIC_HEADER_INITIAL_STATE]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.TOPIC_HEADER_INITIAL_STATE, null)
                )
            }, AppPreferences.Main.TopicHeaderInitialState.EXPANDED)

    fun observeThemeModeFlow(): Flow<AppPreferences.Main.ThemeMode> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseThemeMode(preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM")
            }, AppPreferences.Main.ThemeMode.SYSTEM)

    fun observeUiPaletteFlow(): Flow<AppPreferences.Main.UiPalette> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseUiPalette(
                    preferences[PreferencesKeys.UI_PALETTE]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.UI_PALETTE, null)
                        ?: AppPreferences.Main.UiPalette.SYSTEM.name
                )
            }, AppPreferences.Main.UiPalette.SYSTEM)

    fun observeEditorMonospaceFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_EDITOR_MONOSPACE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.IS_EDITOR_MONOSPACE, true)
            }, true)

    fun observeShowBottomArrowFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.SHOW_BOTTOM_ARROW]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.SHOW_BOTTOM_ARROW, false)
            }, false)

    fun observeBottomNavColumnsFlow(): Flow<Int> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                val value = preferences[PreferencesKeys.BOTTOM_NAV_COLUMNS] ?: "6"
                max(min(value.toIntOrNull() ?: 6, 6), 5)
            }, 6)

    fun observeUseSystemFontFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                legacyUseSystemFont(preferences[PreferencesKeys.APP_FONT_MODE] ?: getLegacySharedAppFontMode())
            }, true)

    fun observeAppFontModeFlow(): Flow<AppFontMode> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseAppFontMode(preferences[PreferencesKeys.APP_FONT_MODE] ?: getLegacySharedAppFontMode())
            }, FontController.DEFAULT_FONT_MODE)

    fun observeUseMaterialYouFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.USE_MATERIAL_YOU] ?: false
            }, false)

    fun observeCompatibilityModeFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.WEBVIEW_COMPATIBILITY_MODE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.WEBVIEW_COMPATIBILITY_MODE, false)
            }, false)

    /** Smart Preload of the next topic page (Phase 8). Kill switch, default OFF. */
    fun observeSmartPreloadFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.WEBVIEW_SMART_PRELOAD]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.WEBVIEW_SMART_PRELOAD, false)
            }, false)

    fun observeDownloadMethodFlow(): Flow<AppPreferences.Main.DownloadMethod> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                parseDownloadMethod(
                    preferences[PreferencesKeys.DOWNLOAD_METHOD]
                        ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                            .getString(AppPreferences.Main.DOWNLOAD_METHOD, null)
                ) ?: legacyDownloadMethod(preferences[PreferencesKeys.IS_SYSTEM_DOWNLOADER])
            }, AppPreferences.Main.DownloadMethod.SYSTEM)

    fun observeDownloadFolderUriFlow(): Flow<String?> =
            safeDataStoreFlow(context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.DOWNLOAD_FOLDER_URI]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getString(AppPreferences.Main.DOWNLOAD_FOLDER_URI, null)
            }, null)

    suspend fun setScrollButtonEnabled(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SCROLL_BUTTON_ENABLE] = value
        }
        mirrorPrefs.edit().putBoolean("scroll_button_enable", value).apply()
    }

    fun getScrollButtonEnabledImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.SCROLL_BUTTON_ENABLE)) {
            return legacy.getBoolean(AppPreferences.Main.SCROLL_BUTTON_ENABLE, true)
        }
        if (mirrorPrefs.contains("scroll_button_enable")) {
            return mirrorPrefs.getBoolean("scroll_button_enable", true)
        }
        return true
    }

    suspend fun setTopicPaginationPanelEnabled(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_PAGINATION_PANEL_ENABLE] = value
        }
        mirrorPrefs.edit().putBoolean("topic_pagination_panel_enable", value).apply()
        // Keep the authoritative legacy key in sync so getTopicPaginationPanelEnabledImmediate
        // (which now reads legacy first) reflects programmatic changes too.
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean(AppPreferences.Main.TOPIC_PAGINATION_PANEL_ENABLE, value).apply()
    }

    fun getTopicPaginationPanelEnabledImmediate(): Boolean {
        // The androidx SwitchPreferenceCompat in SettingsFragment persists the user's live
        // choice straight into the default <package>_preferences file under the legacy key.
        // That legacy value is therefore authoritative whenever it is present. The previous
        // build mirrored mirrorPrefs.contains() first, but the mirror copy is only written by
        // the OnSharedPreferenceChangeListener and is skipped when the fragment is detached
        // (isAdded == false). A stale mirror "true" then overrode a legacy "false", so turning
        // the panel OFF was ignored. Read legacy first, fall back to the mirror only when the
        // toggle was never persisted to legacy (e.g. value set programmatically).
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.TOPIC_PAGINATION_PANEL_ENABLE)) {
            return legacy.getBoolean(AppPreferences.Main.TOPIC_PAGINATION_PANEL_ENABLE, false)
        }
        if (mirrorPrefs.contains("topic_pagination_panel_enable")) {
            return mirrorPrefs.getBoolean("topic_pagination_panel_enable", false)
        }
        return false
    }

    suspend fun setTopicScrollMode(value: AppPreferences.Main.TopicScrollMode) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_SCROLL_MODE] = value.name
        }
        mirrorPrefs.edit().putString("topic_scroll_mode", value.name).apply()
    }

    fun getTopicScrollModeImmediate(): AppPreferences.Main.TopicScrollMode {
        val mirrored = mirrorPrefs.getString("topic_scroll_mode", null)
        return parseTopicScrollMode(mirrored)
    }

    suspend fun setTopicPostDensity(value: AppPreferences.Main.TopicPostDensity) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_POST_DENSITY] = value.name
        }
        mirrorPrefs.edit().putString("topic_post_density", value.name).apply()
    }

    fun getTopicPostDensityImmediate(): AppPreferences.Main.TopicPostDensity {
        val mirrored = mirrorPrefs.getString("topic_post_density", null)
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.TOPIC_POST_DENSITY, null)
        return parseTopicPostDensity(mirrored ?: legacy)
    }

    suspend fun setTopicToolbarBehavior(value: AppPreferences.Main.TopicToolbarBehavior) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_TOOLBAR_BEHAVIOR] = value.name
        }
        mirrorPrefs.edit().putString("topic_toolbar_behavior", value.name).apply()
    }

    fun getTopicToolbarBehaviorImmediate(): AppPreferences.Main.TopicToolbarBehavior {
        val mirrored = mirrorPrefs.getString("topic_toolbar_behavior", null)
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.TOPIC_TOOLBAR_BEHAVIOR, null)
        return parseTopicToolbarBehavior(mirrored ?: legacy)
    }

    suspend fun setTopicPageSwipeEnabled(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_PAGE_SWIPE_ENABLE] = value
        }
        mirrorPrefs.edit().putBoolean("topic_page_swipe_enable", value).apply()
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean(AppPreferences.Main.TOPIC_PAGE_SWIPE_ENABLE, value).apply()
    }

    fun getTopicPageSwipeEnabledImmediate(): Boolean {
        // Same legacy-first logic as topic_pagination_panel_enable: the androidx toggle persists
        // the live choice into the legacy default-prefs key, so it is authoritative when present.
        // The mirror is only a fallback for programmatic sets that never touched legacy.
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.TOPIC_PAGE_SWIPE_ENABLE)) {
            return legacy.getBoolean(AppPreferences.Main.TOPIC_PAGE_SWIPE_ENABLE, false)
        }
        if (mirrorPrefs.contains("topic_page_swipe_enable")) {
            return mirrorPrefs.getBoolean("topic_page_swipe_enable", false)
        }
        return false
    }

    suspend fun setTopicBottomRefreshGestureEnabled(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE] = value
        }
        mirrorPrefs.edit().putBoolean("topic_bottom_refresh_gesture_enable", value).apply()
    }

    fun getTopicBottomRefreshGestureEnabledImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE)) {
            return legacy.getBoolean(AppPreferences.Main.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE, true)
        }
        if (mirrorPrefs.contains("topic_bottom_refresh_gesture_enable")) {
            return mirrorPrefs.getBoolean("topic_bottom_refresh_gesture_enable", true)
        }
        return true
    }

    suspend fun setTopicBackBehavior(value: AppPreferences.Main.TopicBackBehavior) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_BACK_BEHAVIOR] = value.name
        }
        mirrorPrefs.edit().putString("topic_back_behavior", value.name).apply()
    }

    fun getTopicBackBehaviorImmediate(): AppPreferences.Main.TopicBackBehavior {
        val mirrored = mirrorPrefs.getString("topic_back_behavior", null)
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.TOPIC_BACK_BEHAVIOR, null)
        return parseTopicBackBehavior(mirrored ?: legacy)
    }

    suspend fun setTopicOpenTarget(value: AppPreferences.Main.TopicOpenTarget) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_OPEN_TARGET] = value.name
        }
        mirrorPrefs.edit().putString("topic_open_target", value.name).apply()
    }

    fun getTopicOpenTargetImmediate(): AppPreferences.Main.TopicOpenTarget {
        // Prefer Android SharedPreferences (Settings UI writes here) over mirror cache.
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.TOPIC_OPEN_TARGET, null)
        if (!legacy.isNullOrBlank()) {
            return parseTopicOpenTarget(legacy)
        }
        val mirrored = mirrorPrefs.getString("topic_open_target", null)
        return parseTopicOpenTarget(mirrored)
    }

    suspend fun setTopicHeaderInitialState(value: AppPreferences.Main.TopicHeaderInitialState) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOPIC_HEADER_INITIAL_STATE] = value.name
        }
        mirrorPrefs.edit().putString("topic_header_initial_state", value.name).apply()
    }

    fun getTopicHeaderInitialStateImmediate(): AppPreferences.Main.TopicHeaderInitialState {
        val mirrored = mirrorPrefs.getString("topic_header_initial_state", null)
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.TOPIC_HEADER_INITIAL_STATE, null)
        return parseTopicHeaderInitialState(mirrored ?: legacy)
    }

    suspend fun setStartupScreen(value: AppPreferences.Main.StartupScreen) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.STARTUP_SCREEN] = value.name
        }
        mirrorPrefs.edit().putString("startup_screen", value.name).apply()
    }

    fun getStartupScreenImmediate(): AppPreferences.Main.StartupScreen {
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.STARTUP_SCREEN, null)
        if (!legacy.isNullOrBlank()) {
            return parseStartupScreen(legacy)
        }
        val mirrored = mirrorPrefs.getString("startup_screen", null)
        return parseStartupScreen(mirrored)
    }

    suspend fun setShowBottomArrow(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SHOW_BOTTOM_ARROW] = value
        }
        mirrorPrefs.edit().putBoolean("show_bottom_arrow", value).apply()
    }

    fun getShowBottomArrowImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.SHOW_BOTTOM_ARROW)) {
            return legacy.getBoolean(AppPreferences.Main.SHOW_BOTTOM_ARROW, false)
        }
        if (mirrorPrefs.contains("show_bottom_arrow")) {
            return mirrorPrefs.getBoolean("show_bottom_arrow", false)
        }
        return false
    }

    suspend fun setCompatibilityMode(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.WEBVIEW_COMPATIBILITY_MODE] = value
        }
        mirrorPrefs.edit().putBoolean("webview_compatibility_mode", value).apply()
        // Keep the legacy androidx-prefs key in sync so the immediate read reflects programmatic sets.
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean(AppPreferences.Main.WEBVIEW_COMPATIBILITY_MODE, value).apply()
    }

    fun getCompatibilityModeImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.WEBVIEW_COMPATIBILITY_MODE)) {
            return legacy.getBoolean(AppPreferences.Main.WEBVIEW_COMPATIBILITY_MODE, false)
        }
        if (mirrorPrefs.contains("webview_compatibility_mode")) {
            return mirrorPrefs.getBoolean("webview_compatibility_mode", false)
        }
        return false
    }

    suspend fun setSmartPreload(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.WEBVIEW_SMART_PRELOAD] = value
        }
        mirrorPrefs.edit().putBoolean("webview_smart_preload", value).apply()
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean(AppPreferences.Main.WEBVIEW_SMART_PRELOAD, value).apply()
    }

    fun getSmartPreloadImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.WEBVIEW_SMART_PRELOAD)) {
            return legacy.getBoolean(AppPreferences.Main.WEBVIEW_SMART_PRELOAD, false)
        }
        if (mirrorPrefs.contains("webview_smart_preload")) {
            return mirrorPrefs.getBoolean("webview_smart_preload", false)
        }
        return false
    }

    suspend fun setWebViewFontSize(size: Int) {
        val clamped = max(min(size, 64), 8)
        safeEdit { preferences ->
            preferences[PreferencesKeys.WEBVIEW_FONT_SIZE] = clamped
        }
        mirrorPrefs.edit().putInt("webview_font_size", clamped).apply()
    }

    /** Instant synchronous read from SharedPreferences mirror (no blocking I/O). */
    fun getWebViewFontSizeImmediate(): Int {
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        if (legacy.contains(AppPreferences.Main.WEBVIEW_FONT_SIZE)) {
            return max(min(legacy.getInt(AppPreferences.Main.WEBVIEW_FONT_SIZE, 16), 64), 8)
        }
        val mirrored = mirrorPrefs.getInt("webview_font_size", -1)
        if (mirrored > 0) return max(min(mirrored, 64), 8)
        return 16
    }

    suspend fun setBottomNavColumns(columns: Int) {
        val clamped = max(min(columns, 6), 5)
        safeEdit { preferences ->
            preferences[PreferencesKeys.BOTTOM_NAV_COLUMNS] = clamped.toString()
        }
        mirrorPrefs.edit().putString("bottom_nav_columns", clamped.toString()).apply()
    }

    fun getBottomNavColumnsImmediate(): Int {
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val raw = legacy.getString(AppPreferences.Main.BOTTOM_NAV_COLUMNS, null)
            ?: mirrorPrefs.getString("bottom_nav_columns", null)
        return max(min(raw?.toIntOrNull() ?: 6, 6), 5)
    }

    suspend fun setThemeMode(mode: AppPreferences.Main.ThemeMode) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
        // Mirror for instant synchronous read
        mirrorPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    suspend fun setUiPalette(palette: AppPreferences.Main.UiPalette) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.UI_PALETTE] = palette.name
        }
        // Mirror for instant synchronous theme selection before Activity.onCreate.
        mirrorPrefs.edit().putString("ui_palette", palette.name).apply()
    }

    suspend fun setEditorMonospace(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.IS_EDITOR_MONOSPACE] = value
        }
        mirrorPrefs.edit().putBoolean("is_editor_monospace", value).apply()
    }

    fun getEditorMonospaceImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.IS_EDITOR_MONOSPACE)) {
            return legacy.getBoolean(AppPreferences.Main.IS_EDITOR_MONOSPACE, true)
        }
        if (mirrorPrefs.contains("is_editor_monospace")) {
            return mirrorPrefs.getBoolean("is_editor_monospace", true)
        }
        return true
    }

    suspend fun setEditorDefaultHidden(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.IS_EDITOR_DEFAULT_HIDDEN] = value
        }
        mirrorPrefs.edit().putBoolean("is_editor_default_hidden", value).apply()
    }

    fun getEditorDefaultHiddenImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.IS_EDITOR_DEFAULT_HIDDEN)) {
            return legacy.getBoolean(AppPreferences.Main.IS_EDITOR_DEFAULT_HIDDEN, true)
        }
        if (mirrorPrefs.contains("is_editor_default_hidden")) {
            return mirrorPrefs.getBoolean("is_editor_default_hidden", true)
        }
        return true
    }

    suspend fun setSystemDownloader(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.IS_SYSTEM_DOWNLOADER] = value
        }
        mirrorPrefs.edit().putBoolean("is_system_downloader", value).apply()
    }

    fun getSystemDownloaderImmediate(): Boolean = mirrorPrefs.getBoolean("is_system_downloader", true)

    suspend fun setDownloadMethod(method: AppPreferences.Main.DownloadMethod) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_METHOD] = method.name
        }
        mirrorPrefs.edit().putString("download_method", method.name).apply()
    }

    fun getDownloadMethodImmediate(): AppPreferences.Main.DownloadMethod {
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val legacyRaw = legacy.getString(AppPreferences.Main.DOWNLOAD_METHOD, null)
        parseDownloadMethod(legacyRaw)?.let { return it }
        val mirrored = mirrorPrefs.getString("download_method", null)
        parseDownloadMethod(mirrored)?.let { return it }
        return legacyDownloadMethod(null)
    }

    suspend fun setDownloadFolderUri(uri: String?) {
        safeEdit { preferences ->
            if (uri.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.DOWNLOAD_FOLDER_URI)
            } else {
                preferences[PreferencesKeys.DOWNLOAD_FOLDER_URI] = uri
            }
        }
        mirrorPrefs.edit().apply {
            if (uri.isNullOrBlank()) remove("download_folder_uri") else putString("download_folder_uri", uri)
        }.apply()
    }

    fun getDownloadFolderUriImmediate(): String? {
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val legacyRaw = legacy.getString(AppPreferences.Main.DOWNLOAD_FOLDER_URI, null)
        if (!legacyRaw.isNullOrBlank()) return legacyRaw
        return mirrorPrefs.getString("download_folder_uri", null)
    }

    suspend fun setUseSystemFont(value: Boolean) {
        setAppFontMode(if (value) AppFontMode.SYSTEM else FontController.DEFAULT_FONT_MODE)
    }

    suspend fun setAppFontMode(mode: AppFontMode) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.APP_FONT_MODE] = mode.name
            preferences[PreferencesKeys.USE_SYSTEM_FONT] = mode == AppFontMode.SYSTEM
        }
        mirrorPrefs.edit()
            .putString("app_font_mode", mode.name)
            .putBoolean("use_system_font", mode == AppFontMode.SYSTEM)
            .apply()
    }

    suspend fun setUseMaterialYou(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.USE_MATERIAL_YOU] = value
        }
        mirrorPrefs.edit().putBoolean("use_material_you", value).apply()
    }

    /**
     * Material You (dynamic color) requires API 31+. If a user has a stale
     * `use_material_you = true` saved from a previous Android 12+ install and
     * later downgraded to an older device, the toggle is meaningless on the new
     * device. Force it off here so DataStore and the UI stay consistent with
     * the actual device capabilities.
     */
    suspend fun migrateMaterialYouForOldApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (getUseMaterialYouImmediate()) {
            if (BuildConfig.DEBUG) Timber.tag(LOG_TAG).d("migrate: forcing use_material_you=false on sdk=%d", Build.VERSION.SDK_INT)
            setUseMaterialYou(false)
        }
    }

    suspend fun getWebViewFontSize(): Int =
            observeWebViewFontSizeFlow().map { it }.first()

    suspend fun getSystemDownloader(): Boolean =
            context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_SYSTEM_DOWNLOADER]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.IS_SYSTEM_DOWNLOADER, true)
            }.first()

    suspend fun getDownloadMethod(): AppPreferences.Main.DownloadMethod =
            observeDownloadMethodFlow().map { it }.first()

    suspend fun getDownloadFolderUri(): String? =
            observeDownloadFolderUriFlow().map { it }.first()

    suspend fun getEditorMonospace(): Boolean =
            context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_EDITOR_MONOSPACE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.IS_EDITOR_MONOSPACE, true)
            }.first()

    suspend fun getEditorDefaultHidden(): Boolean =
            context.mainDataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_EDITOR_DEFAULT_HIDDEN]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Main.IS_EDITOR_DEFAULT_HIDDEN, true)
            }.first()

    suspend fun getScrollButtonEnabled(): Boolean =
            observeScrollButtonEnabledFlow().map { it }.first()

    suspend fun getCompatibilityMode(): Boolean =
            observeCompatibilityModeFlow().map { it }.first()

    suspend fun getSmartPreload(): Boolean =
            observeSmartPreloadFlow().map { it }.first()

    suspend fun getTopicPaginationPanelEnabled(): Boolean =
            observeTopicPaginationPanelEnabledFlow().map { it }.first()

    suspend fun getTopicScrollMode(): AppPreferences.Main.TopicScrollMode =
            observeTopicScrollModeFlow().map { it }.first()

    suspend fun getTopicPostDensity(): AppPreferences.Main.TopicPostDensity =
            observeTopicPostDensityFlow().map { it }.first()

    suspend fun getTopicToolbarBehavior(): AppPreferences.Main.TopicToolbarBehavior =
            observeTopicToolbarBehaviorFlow().map { it }.first()

    suspend fun getTopicPageSwipeEnabled(): Boolean =
            observeTopicPageSwipeEnabledFlow().map { it }.first()

    suspend fun getTopicBottomRefreshGestureEnabled(): Boolean =
            observeTopicBottomRefreshGestureEnabledFlow().map { it }.first()

    suspend fun getTopicBackBehavior(): AppPreferences.Main.TopicBackBehavior =
            observeTopicBackBehaviorFlow().map { it }.first()

    suspend fun getTopicOpenTarget(): AppPreferences.Main.TopicOpenTarget =
            observeTopicOpenTargetFlow().map { it }.first()

    suspend fun getTopicHeaderInitialState(): AppPreferences.Main.TopicHeaderInitialState =
            observeTopicHeaderInitialStateFlow().map { it }.first()

    suspend fun getBottomNavColumns(): Int =
            observeBottomNavColumnsFlow().map { it }.first()

    suspend fun getThemeMode(): AppPreferences.Main.ThemeMode =
            observeThemeModeFlow().map { it }.first()

    suspend fun getShowBottomArrow(): Boolean =
            observeShowBottomArrowFlow().map { it }.first()

    suspend fun getUiPalette(): AppPreferences.Main.UiPalette =
            observeUiPaletteFlow().map { it }.first()

    suspend fun getUseSystemFont(): Boolean =
            observeUseSystemFontFlow().map { it }.first()

    suspend fun getAppFontMode(): AppFontMode =
            observeAppFontModeFlow().map { it }.first()

    /** Instant synchronous read from SharedPreferences mirror (no blocking I/O). */
    fun getThemeModeImmediate(): AppPreferences.Main.ThemeMode {
        val mirrored = mirrorPrefs.getString("theme_mode", null)
        return if (mirrored != null) parseThemeMode(mirrored) else AppPreferences.Main.ThemeMode.SYSTEM
    }

    /** Instant synchronous read from SharedPreferences mirror (no blocking I/O). */
    fun getUiPaletteImmediate(): AppPreferences.Main.UiPalette {
        val mirrored = mirrorPrefs.getString("ui_palette", null)
        val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.UI_PALETTE, null)
        return parseUiPalette(mirrored ?: legacy ?: AppPreferences.Main.UiPalette.SYSTEM.name)
    }

    fun getUseSystemFontImmediate(): Boolean {
        return getAppFontModeImmediate() == AppFontMode.SYSTEM
    }

    fun getUseMaterialYouImmediate(): Boolean {
        val legacy = context.getSharedPreferences(
                context.packageName + "_preferences",
                Context.MODE_PRIVATE
        )
        if (legacy.contains(AppPreferences.Main.USE_MATERIAL_YOU)) {
            return legacy.getBoolean(AppPreferences.Main.USE_MATERIAL_YOU, false)
        }
        if (mirrorPrefs.contains("use_material_you")) {
            return mirrorPrefs.getBoolean("use_material_you", false)
        }
        return false
    }

    fun getAppFontModeImmediate(): AppFontMode {
        val mirrored = mirrorPrefs.getString("app_font_mode", null)
        return parseAppFontMode(mirrored ?: getLegacySharedAppFontMode())
    }

    private fun parseAppFontMode(value: String?): AppFontMode {
        if (!value.isNullOrBlank()) return FontController.parseMode(value)
        return FontController.DEFAULT_FONT_MODE
    }

    private fun legacyUseSystemFont(value: String?): Boolean =
        parseAppFontMode(value) == AppFontMode.SYSTEM

    private fun getLegacySharedAppFontMode(): String? =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            .getString(AppPreferences.Main.APP_FONT_MODE, null)

    private fun parseThemeMode(value: String): AppPreferences.Main.ThemeMode = try {
        AppPreferences.Main.ThemeMode.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.ThemeMode.SYSTEM
    }

    private fun parseUiPalette(value: String): AppPreferences.Main.UiPalette = try {
        // Unknown / legacy values (e.g. the removed CLASSIC_4PDA) fall back to SYSTEM below.
        AppPreferences.Main.UiPalette.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.UiPalette.SYSTEM
    }

    private fun parseDownloadMethod(value: String?): AppPreferences.Main.DownloadMethod? = try {
        if (value.isNullOrBlank()) null else AppPreferences.Main.DownloadMethod.valueOf(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseTopicScrollMode(value: String?): AppPreferences.Main.TopicScrollMode = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.TopicScrollMode.HYBRID
        } else {
            AppPreferences.Main.TopicScrollMode.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.TopicScrollMode.HYBRID
    }

    private fun parseTopicPostDensity(value: String?): AppPreferences.Main.TopicPostDensity = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.TopicPostDensity.COMFORTABLE
        } else {
            AppPreferences.Main.TopicPostDensity.valueOf(value.uppercase())
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.TopicPostDensity.COMFORTABLE
    }

    private fun parseTopicToolbarBehavior(value: String?): AppPreferences.Main.TopicToolbarBehavior = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.TopicToolbarBehavior.PINNED
        } else {
            AppPreferences.Main.TopicToolbarBehavior.valueOf(value.uppercase())
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.TopicToolbarBehavior.PINNED
    }

    private fun parseTopicBackBehavior(value: String?): AppPreferences.Main.TopicBackBehavior = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.TopicBackBehavior.HISTORY
        } else {
            AppPreferences.Main.TopicBackBehavior.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.TopicBackBehavior.HISTORY
    }

    private fun parseTopicOpenTarget(value: String?): AppPreferences.Main.TopicOpenTarget = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        } else {
            AppPreferences.Main.TopicOpenTarget.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
    }

    private fun parseTopicHeaderInitialState(value: String?): AppPreferences.Main.TopicHeaderInitialState = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.TopicHeaderInitialState.EXPANDED
        } else {
            AppPreferences.Main.TopicHeaderInitialState.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.TopicHeaderInitialState.EXPANDED
    }

    private fun parseStartupScreen(value: String?): AppPreferences.Main.StartupScreen = try {
        if (value.isNullOrBlank()) {
            AppPreferences.Main.StartupScreen.NEWS
        } else {
            AppPreferences.Main.StartupScreen.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        AppPreferences.Main.StartupScreen.NEWS
    }

    private fun legacyDownloadMethod(dataStoreLegacyValue: Boolean?): AppPreferences.Main.DownloadMethod {
        val legacySystemDownloader = dataStoreLegacyValue
            ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                .getBoolean(AppPreferences.Main.IS_SYSTEM_DOWNLOADER, true)
        return if (legacySystemDownloader) {
            AppPreferences.Main.DownloadMethod.SYSTEM
        } else {
            AppPreferences.Main.DownloadMethod.BROWSER
        }
    }

    private companion object {
        private const val LOG_TAG = "MainDataStore"
    }
}
