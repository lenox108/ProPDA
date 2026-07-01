package forpdateam.ru.forpda.model.preferences

import android.content.Context
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.ui.AppFontMode
import kotlinx.coroutines.flow.Flow

class MainPreferencesHolder(
        private val context: Context
) {
    private val dataStore = MainDataStore(context)

    // --- Flow-наблюдения ---
    fun observeWebViewFontSizeFlow(): Flow<Int> = dataStore.observeWebViewFontSizeFlow()

    fun observeScrollButtonEnabledFlow(): Flow<Boolean> = dataStore.observeScrollButtonEnabledFlow()

    fun observeTopicPaginationPanelEnabledFlow(): Flow<Boolean> = dataStore.observeTopicPaginationPanelEnabledFlow()

    fun observeTopicScrollModeFlow(): Flow<AppPreferences.Main.TopicScrollMode> = dataStore.observeTopicScrollModeFlow()

    fun observeTopicPostDensityFlow(): Flow<AppPreferences.Main.TopicPostDensity> = dataStore.observeTopicPostDensityFlow()

    fun observeTopicToolbarBehaviorFlow(): Flow<AppPreferences.Main.TopicToolbarBehavior> = dataStore.observeTopicToolbarBehaviorFlow()

    fun observeTopicPageSwipeEnabledFlow(): Flow<Boolean> = dataStore.observeTopicPageSwipeEnabledFlow()

    fun observeTopicBottomRefreshGestureEnabledFlow(): Flow<Boolean> = dataStore.observeTopicBottomRefreshGestureEnabledFlow()

    fun observeTopicBackBehaviorFlow(): Flow<AppPreferences.Main.TopicBackBehavior> = dataStore.observeTopicBackBehaviorFlow()

    fun observeTopicOpenTargetFlow(): Flow<AppPreferences.Main.TopicOpenTarget> = dataStore.observeTopicOpenTargetFlow()

    fun observeTopicHeaderInitialStateFlow(): Flow<AppPreferences.Main.TopicHeaderInitialState> = dataStore.observeTopicHeaderInitialStateFlow()

    fun observeThemeModeFlow(): Flow<AppPreferences.Main.ThemeMode> = dataStore.observeThemeModeFlow()

    fun observeUiPaletteFlow(): Flow<AppPreferences.Main.UiPalette> = dataStore.observeUiPaletteFlow()

    fun observeAccentPaletteFlow(): Flow<AppPreferences.Main.AccentPalette> = dataStore.observeAccentPaletteFlow()

    fun observeEditorMonospaceFlow(): Flow<Boolean> = dataStore.observeEditorMonospaceFlow()

    fun observeShowBottomArrowFlow(): Flow<Boolean> = dataStore.observeShowBottomArrowFlow()

    fun observeBottomNavColumnsFlow(): Flow<Int> = dataStore.observeBottomNavColumnsFlow()

    fun observeUseSystemFontFlow(): Flow<Boolean> = dataStore.observeUseSystemFontFlow()

    fun observeAppFontModeFlow(): Flow<AppFontMode> = dataStore.observeAppFontModeFlow()

    fun observeDownloadMethodFlow(): Flow<AppPreferences.Main.DownloadMethod> = dataStore.observeDownloadMethodFlow()

    fun observeDownloadFolderUriFlow(): Flow<String?> = dataStore.observeDownloadFolderUriFlow()

    fun observeUseMaterialYouFlow(): Flow<Boolean> = dataStore.observeUseMaterialYouFlow()

    fun observeCompatibilityModeFlow(): Flow<Boolean> = dataStore.observeCompatibilityModeFlow()

    fun observeSmartPreloadFlow(): Flow<Boolean> = dataStore.observeSmartPreloadFlow()

    // --- Геттеры (instant mirror reads, no runBlocking) ---
    fun getWebViewFontSize(): Int = dataStore.getWebViewFontSizeImmediate()

    fun getSystemDownloader(): Boolean = dataStore.getSystemDownloaderImmediate()

    fun getDownloadMethod(): AppPreferences.Main.DownloadMethod = dataStore.getDownloadMethodImmediate()

    fun getDownloadFolderUri(): String? = dataStore.getDownloadFolderUriImmediate()

    fun getUseMaterialYou(): Boolean = dataStore.getUseMaterialYouImmediate()

    fun getEditorMonospace(): Boolean = dataStore.getEditorMonospaceImmediate()

    fun getEditorDefaultHidden(): Boolean = dataStore.getEditorDefaultHiddenImmediate()

    fun getScrollButtonEnabled(): Boolean = dataStore.getScrollButtonEnabledImmediate()

    fun getCompatibilityMode(): Boolean = dataStore.getCompatibilityModeImmediate()

    fun getSmartPreload(): Boolean = dataStore.getSmartPreloadImmediate()

    fun getTopicPaginationPanelEnabled(): Boolean = dataStore.getTopicPaginationPanelEnabledImmediate()

    fun getTopicScrollMode(): AppPreferences.Main.TopicScrollMode = dataStore.getTopicScrollModeImmediate()

    fun getTopicPostDensity(): AppPreferences.Main.TopicPostDensity = dataStore.getTopicPostDensityImmediate()

    fun getTopicToolbarBehavior(): AppPreferences.Main.TopicToolbarBehavior = dataStore.getTopicToolbarBehaviorImmediate()

    fun getTopicPageSwipeEnabled(): Boolean = dataStore.getTopicPageSwipeEnabledImmediate()

    fun getTopicBottomRefreshGestureEnabled(): Boolean = dataStore.getTopicBottomRefreshGestureEnabledImmediate()

    fun getTopicBackBehavior(): AppPreferences.Main.TopicBackBehavior = dataStore.getTopicBackBehaviorImmediate()

    fun getTopicOpenTarget(): AppPreferences.Main.TopicOpenTarget = dataStore.getTopicOpenTargetImmediate()

    fun getTopicHeaderInitialState(): AppPreferences.Main.TopicHeaderInitialState = dataStore.getTopicHeaderInitialStateImmediate()

    fun getBottomNavColumns(): Int = dataStore.getBottomNavColumnsImmediate()

    fun getThemeMode(): AppPreferences.Main.ThemeMode = dataStore.getThemeModeImmediate()

    fun getShowBottomArrow(): Boolean = dataStore.getShowBottomArrowImmediate()

    fun getUiPalette(): AppPreferences.Main.UiPalette = dataStore.getUiPaletteImmediate()

    fun getAccentPalette(): AppPreferences.Main.AccentPalette = dataStore.getAccentPaletteImmediate()

    fun getAccentCustomColor(): Int = dataStore.getAccentCustomColorImmediate()

    fun getAccentVibrant(): Boolean = dataStore.getAccentVibrantImmediate()

    fun getUseSystemFont(): Boolean = dataStore.getUseSystemFontImmediate()

    fun getAppFontMode(): AppFontMode = dataStore.getAppFontModeImmediate()

    fun getStartupScreen(): AppPreferences.Main.StartupScreen = dataStore.getStartupScreenImmediate()

    // --- Сеттеры (suspend — mirror updated inside DataStore) ---
    suspend fun setWebViewFontSize(size: Int) = dataStore.setWebViewFontSize(size)

    suspend fun setBottomNavColumns(columns: Int) = dataStore.setBottomNavColumns(columns)

    suspend fun setThemeMode(mode: AppPreferences.Main.ThemeMode) = dataStore.setThemeMode(mode)

    suspend fun setUiPalette(palette: AppPreferences.Main.UiPalette) = dataStore.setUiPalette(palette)

    suspend fun setAccentPalette(palette: AppPreferences.Main.AccentPalette) = dataStore.setAccentPalette(palette)

    suspend fun setAccentCustomColor(color: Int) = dataStore.setAccentCustomColor(color)

    suspend fun setAccentVibrant(value: Boolean) = dataStore.setAccentVibrant(value)

    suspend fun setShowBottomArrow(value: Boolean) = dataStore.setShowBottomArrow(value)

    suspend fun setScrollButtonEnabled(value: Boolean) = dataStore.setScrollButtonEnabled(value)

    suspend fun setCompatibilityMode(value: Boolean) = dataStore.setCompatibilityMode(value)

    suspend fun setSmartPreload(value: Boolean) = dataStore.setSmartPreload(value)

    suspend fun setTopicPaginationPanelEnabled(value: Boolean) = dataStore.setTopicPaginationPanelEnabled(value)

    suspend fun setTopicScrollMode(value: AppPreferences.Main.TopicScrollMode) = dataStore.setTopicScrollMode(value)

    suspend fun setTopicPostDensity(value: AppPreferences.Main.TopicPostDensity) = dataStore.setTopicPostDensity(value)

    suspend fun setTopicToolbarBehavior(value: AppPreferences.Main.TopicToolbarBehavior) = dataStore.setTopicToolbarBehavior(value)

    suspend fun setTopicPageSwipeEnabled(value: Boolean) = dataStore.setTopicPageSwipeEnabled(value)

    suspend fun setTopicBottomRefreshGestureEnabled(value: Boolean) = dataStore.setTopicBottomRefreshGestureEnabled(value)

    suspend fun setTopicBackBehavior(value: AppPreferences.Main.TopicBackBehavior) = dataStore.setTopicBackBehavior(value)

    suspend fun setTopicOpenTarget(value: AppPreferences.Main.TopicOpenTarget) = dataStore.setTopicOpenTarget(value)

    suspend fun setTopicHeaderInitialState(value: AppPreferences.Main.TopicHeaderInitialState) = dataStore.setTopicHeaderInitialState(value)

    suspend fun setEditorMonospace(value: Boolean) = dataStore.setEditorMonospace(value)

    suspend fun setEditorDefaultHidden(value: Boolean) = dataStore.setEditorDefaultHidden(value)

    suspend fun setSystemDownloader(value: Boolean) = dataStore.setSystemDownloader(value)

    suspend fun setDownloadMethod(method: AppPreferences.Main.DownloadMethod) = dataStore.setDownloadMethod(method)

    suspend fun setDownloadFolderUri(uri: String?) = dataStore.setDownloadFolderUri(uri)

    suspend fun setUseSystemFont(value: Boolean) = dataStore.setUseSystemFont(value)

    suspend fun setAppFontMode(mode: AppFontMode) = dataStore.setAppFontMode(mode)

    suspend fun setStartupScreen(value: AppPreferences.Main.StartupScreen) = dataStore.setStartupScreen(value)

    suspend fun setUseMaterialYou(value: Boolean) = dataStore.setUseMaterialYou(value)
}
