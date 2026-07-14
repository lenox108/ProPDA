package forpdateam.ru.forpda.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.otherDataStore: DataStore<Preferences> by preferencesDataStore(name = "other")

class OtherDataStore(private val context: Context) {
    private val mirrorPrefs = context.getSharedPreferences("other_mirror", Context.MODE_PRIVATE)

    private inline fun <T> safeDataStoreFlow(
            flow: Flow<T>,
            default: T
    ): Flow<T> = flow.catch { e ->
        Timber.e(e, "DataStore read error, returning default")
        emit(default)
    }

    private suspend inline fun safeEdit(crossinline block: suspend (MutablePreferences) -> Unit) {
        try {
            context.otherDataStore.edit { preferences ->
                block(preferences)
            }
        } catch (e: Exception) {
            Timber.e(e, "DataStore edit error")
        }
    }

    private object PreferencesKeys {
        val APP_FIRST_START = booleanPreferencesKey("app_first_start")
        val APP_VERSIONS_HISTORY = stringPreferencesKey("app_versions_history")
        val SEARCH_SETTINGS = stringPreferencesKey("search_settings")
        val MESSAGE_PANEL_BBCODES_SORT = stringPreferencesKey("message_panel_bbcodes_sort")
        val SHOW_REPORT_WARNING = booleanPreferencesKey("show_report_warning")
        val TOOLTIP_SEARCH_SETTINGS = booleanPreferencesKey("tooltip_search_settings")
        val TOOLTIP_MESSAGE_PANEL_SORTING = booleanPreferencesKey("tooltip_message_panel_sorting")
        val SMART_NAV_LONG_PRESS_HINT_DISABLED = booleanPreferencesKey(AppPreferences.Other.SMART_NAV_LONG_PRESS_HINT_DISABLED)
        val OTHER_MENU_TILE_ORDER = stringPreferencesKey("other_menu_tile_order")
        val OTHER_MENU_SHORTCUTS = stringPreferencesKey("other_menu_shortcuts")
        val OTHER_MENU_QUICK_SETTINGS = stringPreferencesKey("other_menu_quick_settings")
        val OTHER_MENU_HIDDEN_BLOCKS = stringPreferencesKey("other_menu_hidden_blocks")
    }

    val appFirstStart: Flow<Boolean> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_FIRST_START] ?: true
    }, true)

    suspend fun setAppFirstStart(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.APP_FIRST_START] = value
        }
    }

    val appVersionsHistory: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_VERSIONS_HISTORY] ?: ""
    }, "")

    suspend fun setAppVersionsHistory(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.APP_VERSIONS_HISTORY] = value
        }
    }

    val searchSettings: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.SEARCH_SETTINGS] ?: ""
    }, "")

    suspend fun setSearchSettings(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SEARCH_SETTINGS] = value
        }
    }

    val messagePanelBbCodes: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.MESSAGE_PANEL_BBCODES_SORT] ?: ""
    }, "")

    suspend fun setMessagePanelBbCodes(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.MESSAGE_PANEL_BBCODES_SORT] = value
        }
        mirrorPrefs.edit().putString("message_panel_bbcodes_sort", value).apply()
    }

    /** Instant synchronous read from SharedPreferences mirror (no blocking I/O). */
    fun getMessagePanelBbCodesSync(): String =
        mirrorPrefs.getString("message_panel_bbcodes_sort", "").orEmpty()

    suspend fun deleteMessagePanelBbCodes() {
        safeEdit { preferences ->
            preferences.remove(PreferencesKeys.MESSAGE_PANEL_BBCODES_SORT)
        }
        mirrorPrefs.edit().remove("message_panel_bbcodes_sort").apply()
    }

    val showReportWarning: Flow<Boolean> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_REPORT_WARNING] ?: true
    }, true)

    suspend fun setShowReportWarning(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SHOW_REPORT_WARNING] = value
        }
        mirrorPrefs.edit().putBoolean("show_report_warning", value).apply()
    }

    /** Instant synchronous read from SharedPreferences mirror (no blocking I/O). */
    fun getShowReportWarningSync(): Boolean =
        mirrorPrefs.getBoolean("show_report_warning", true)

    val tooltipSearchSettings: Flow<Boolean> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.TOOLTIP_SEARCH_SETTINGS] ?: true
    }, true)

    suspend fun setTooltipSearchSettings(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOOLTIP_SEARCH_SETTINGS] = value
        }
    }

    val tooltipMessagePanelSorting: Flow<Boolean> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.TOOLTIP_MESSAGE_PANEL_SORTING] ?: true
    }, true)

    suspend fun setTooltipMessagePanelSorting(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.TOOLTIP_MESSAGE_PANEL_SORTING] = value
        }
        mirrorPrefs.edit().putBoolean("tooltip_message_panel_sorting", value).apply()
    }

    /** Instant synchronous read from SharedPreferences mirror (no blocking I/O). */
    fun getTooltipMessagePanelSortingSync(): Boolean =
        mirrorPrefs.getBoolean("tooltip_message_panel_sorting", true)

    val smartNavLongPressHintDisabled: Flow<Boolean> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.SMART_NAV_LONG_PRESS_HINT_DISABLED] ?: false
    }, false)

    suspend fun setSmartNavLongPressHintDisabled(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SMART_NAV_LONG_PRESS_HINT_DISABLED] = value
        }
    }

    val otherMenuTileOrder: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.OTHER_MENU_TILE_ORDER] ?: ""
    }, "")

    suspend fun setOtherMenuTileOrder(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.OTHER_MENU_TILE_ORDER] = value
        }
    }

    /** JSON-массив пользовательских плиток меню (см. MenuShortcutsRepository). */
    val otherMenuShortcuts: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.OTHER_MENU_SHORTCUTS] ?: ""
    }, "")

    suspend fun setOtherMenuShortcuts(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.OTHER_MENU_SHORTCUTS] = value
        }
    }

    /** Состав ряда быстрых настроек: имена QuickSetting через запятую. Пусто = набор по умолчанию. */
    val otherMenuQuickSettings: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.OTHER_MENU_QUICK_SETTINGS] ?: ""
    }, "")

    suspend fun setOtherMenuQuickSettings(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.OTHER_MENU_QUICK_SETTINGS] = value
        }
    }

    /** Скрытые блоки экрана меню: имена OtherMenuBlock через запятую. */
    val otherMenuHiddenBlocks: Flow<String> = safeDataStoreFlow(context.otherDataStore.data.map { preferences ->
        preferences[PreferencesKeys.OTHER_MENU_HIDDEN_BLOCKS] ?: ""
    }, "")

    suspend fun setOtherMenuHiddenBlocks(value: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.OTHER_MENU_HIDDEN_BLOCKS] = value
        }
    }
}
