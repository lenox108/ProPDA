package forpdateam.ru.forpda.ui.fragments.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.util.Log
import timber.log.Timber
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.common.makeSnackbarAboveSystemBars
import forpdateam.ru.forpda.common.showSnackbarAboveSystemBars
import forpdateam.ru.forpda.common.showSnackbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.content.SharedPreferences
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.appupdates.DownloadLink
import forpdateam.ru.forpda.appupdates.AppUpdatePreferences
import forpdateam.ru.forpda.appupdates.AppUpdateRepository
import forpdateam.ru.forpda.appupdates.AppUpdateScheduler
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.ui.AppFontMode
import forpdateam.ru.forpda.ui.FontController
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 25.12.16.
 */

@AndroidEntryPoint
class SettingsFragment : BaseSettingFragment() {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var appUpdatePreferences: AppUpdatePreferences
    @Inject lateinit var appUpdateRepository: AppUpdateRepository
    @Inject lateinit var appUpdateScheduler: AppUpdateScheduler
    @Inject lateinit var preferences: SharedPreferences
    @Inject lateinit var dayNightHelper: DayNightHelper
    @Inject lateinit var clipboardHelper: ClipboardHelper

    // Запас под последней плашкой («Аккаунт» с правилами форума), чтобы она не липла к низу.
    override val extraBottomPaddingPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.dp24)

    private var logoutJob: kotlinx.coroutines.Job? = null
    private val prefs by lazy { preferences }
    private val downloadFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        persistDownloadFolder(uri)
    }
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        if (key == Preferences.Main.BOTTOM_NAV_COLUMNS) {
            val value = sharedPrefs.getString(key, "6")?.toIntOrNull() ?: 6
            lifecycleScope.launch { mainPreferencesHolder.setBottomNavColumns(value) }
        }
        if (key == "lists.favorites.load_all") {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder(requireContext()).setFavLoadAll(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Lists.Favorites.SHOW_UNREAD_BADGE) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder(requireContext()).setFavShowUnreadBadge(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Lists.Topic.UNREAD_TOP) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder(requireContext()).setUnreadTop(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Lists.Topic.SHOW_DOT) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder(requireContext()).setShowDot(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.SHOW_BOTTOM_ARROW) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setShowBottomArrow(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.SCROLL_BUTTON_ENABLE) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setScrollButtonEnabled(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_SCROLL_MODE) {
            val value = sharedPrefs.getString(key, Preferences.Main.TopicScrollMode.HYBRID.name)
            val mode = try {
                Preferences.Main.TopicScrollMode.valueOf(value ?: Preferences.Main.TopicScrollMode.HYBRID.name)
            } catch (_: IllegalArgumentException) {
                Preferences.Main.TopicScrollMode.HYBRID
            }
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicScrollMode(mode)
                }
            }
            updateTopicScrollModeSummary(mode)
            updateTopicPageSwipePreferenceState(mode)
            updateTopicPaginationPanelsSummary(mainPreferencesHolder.getTopicPaginationPanels(), mode)
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_POST_DENSITY) {
            val density = SettingsPreferenceParsers.parseTopicPostDensity(sharedPrefs.getString(key, Preferences.Main.TopicPostDensity.COMFORTABLE.name))
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicPostDensity(density)
                }
            }
            updateTopicPostDensitySummary(density)
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_TOOLBAR_BEHAVIOR) {
            val behavior = SettingsPreferenceParsers.parseTopicToolbarBehavior(sharedPrefs.getString(key, Preferences.Main.TopicToolbarBehavior.PINNED.name))
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicToolbarBehavior(behavior)
                }
            }
            updateTopicToolbarBehaviorSummary(behavior)
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_PAGE_SWIPE_ENABLE) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicPageSwipeEnabled(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicBottomRefreshGestureEnabled(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_BACK_BEHAVIOR) {
            val behavior = SettingsPreferenceParsers.parseTopicBackBehavior(sharedPrefs.getString(key, Preferences.Main.TopicBackBehavior.HISTORY.name))
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicBackBehavior(behavior)
                }
            }
            updateTopicBackBehaviorSummary(behavior)
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_OPEN_TARGET) {
            val target = SettingsPreferenceParsers.parseTopicOpenTarget(sharedPrefs.getString(key, Preferences.Main.TopicOpenTarget.LAST_UNREAD.name))
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicOpenTarget(target)
                }
            }
            updateTopicOpenTargetSummary(target)
            updateTopicHeaderInitialStateEnabled(target)
        }
        if (key == Preferences.Main.STARTUP_SCREEN) {
            val startup = SettingsPreferenceParsers.parseStartupScreen(sharedPrefs.getString(key, Preferences.Main.StartupScreen.NEWS.name))
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setStartupScreen(startup)
                }
            }
            updateStartupScreenSummary(startup)
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.TOPIC_HEADER_INITIAL_STATE) {
            val state = SettingsPreferenceParsers.parseTopicHeaderInitialState(sharedPrefs.getString(key, Preferences.Main.TopicHeaderInitialState.EXPANDED.name))
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setTopicHeaderInitialState(state)
                }
            }
            updateTopicHeaderInitialStateSummary(state)
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.IS_EDITOR_MONOSPACE) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setEditorMonospace(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.IS_EDITOR_DEFAULT_HIDDEN) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setEditorDefaultHidden(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.USE_MATERIAL_YOU) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setUseMaterialYou(value)
                    activity?.recreate()
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Main.DOWNLOAD_METHOD) {
            val value = sharedPrefs.getString(key, Preferences.Main.DownloadMethod.SYSTEM.name)
            val method = try {
                Preferences.Main.DownloadMethod.valueOf(value ?: Preferences.Main.DownloadMethod.SYSTEM.name)
            } catch (_: IllegalArgumentException) {
                Preferences.Main.DownloadMethod.SYSTEM
            }
            if (isAdded) {
                lifecycleScope.launch {
                    mainPreferencesHolder.setDownloadMethod(method)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Theme.SHOW_AVATARS) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).setShowAvatars(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Theme.CIRCLE_AVATARS) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).setCircleAvatars(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Theme.ANIMATED_SMILES) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).setAnimatedSmiles(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Theme.FLAT_POSTS) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).setFlatPosts(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Theme.MODERN_POST_HEADER) {
            val value = sharedPrefs.getBoolean(key, false)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).setModernPostHeader(value)
                }
            }
        }
        if (key == forpdateam.ru.forpda.common.Preferences.Theme.HIGHLIGHT_UNREAD_POST) {
            val value = sharedPrefs.getBoolean(key, true)
            if (isAdded) {
                lifecycleScope.launch {
                    forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).setHighlightUnreadPost(value)
                }
            }
        }
        if (key == AppUpdatePreferences.KEY_CHECK_ENABLED) {
            appUpdatePreferences.setCheckEnabled(sharedPrefs.getBoolean(key, true))
            appUpdateScheduler.reschedule()
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)

        // Синхронизируем DataStore → SwitchPreference, чтобы переключатели
        // показывали актуальные значения, а не XML-defaults.
        lifecycleScope.launch {
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.SHOW_BOTTOM_ARROW)
                ?.isChecked = mainPreferencesHolder.observeShowBottomArrowFlow().first()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.IS_EDITOR_MONOSPACE)
                ?.isChecked = mainPreferencesHolder.observeEditorMonospaceFlow().first()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.IS_EDITOR_DEFAULT_HIDDEN)
                ?.isChecked = mainPreferencesHolder.getEditorDefaultHidden()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.USE_MATERIAL_YOU)
                ?.isChecked = mainPreferencesHolder.getUseMaterialYou()
            findPreference<Preference>(Preferences.Main.APP_FONT_MODE)
                ?.let {
                    updateAppFontSummary(mainPreferencesHolder.observeAppFontModeFlow().first())
                }
            findPreference<ListPreference>(Preferences.Main.DOWNLOAD_METHOD)
                ?.let {
                    val method = mainPreferencesHolder.getDownloadMethod()
                    it.value = method.name
                    updateDownloadMethodSummary(method)
                }
            updateDownloadFolderSummary(mainPreferencesHolder.observeDownloadFolderUriFlow().first())
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.SCROLL_BUTTON_ENABLE)
                ?.isChecked = mainPreferencesHolder.observeScrollButtonEnabledFlow().first()
            updateTopicPaginationPanelsSummary(
                    mainPreferencesHolder.observeTopicPaginationPanelsFlow().first(),
                    mainPreferencesHolder.observeTopicScrollModeFlow().first())
            findPreference<ListPreference>(Preferences.Main.TOPIC_SCROLL_MODE)
                ?.let {
                    val mode = mainPreferencesHolder.observeTopicScrollModeFlow().first()
                    it.value = mode.name
                    updateTopicScrollModeSummary(mode)
                    updateTopicPageSwipePreferenceState(mode)
                }
            findPreference<ListPreference>(Preferences.Main.TOPIC_POST_DENSITY)
                ?.let {
                    val density = mainPreferencesHolder.observeTopicPostDensityFlow().first()
                    it.value = density.name.lowercase()
                    updateTopicPostDensitySummary(density)
                }
            findPreference<ListPreference>(Preferences.Main.TOPIC_TOOLBAR_BEHAVIOR)
                ?.let {
                    val behavior = mainPreferencesHolder.observeTopicToolbarBehaviorFlow().first()
                    it.value = behavior.name
                    updateTopicToolbarBehaviorSummary(behavior)
                }
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.TOPIC_PAGE_SWIPE_ENABLE)
                ?.isChecked = mainPreferencesHolder.observeTopicPageSwipeEnabledFlow().first()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE)
                ?.isChecked = mainPreferencesHolder.observeTopicBottomRefreshGestureEnabledFlow().first()
            findPreference<ListPreference>(Preferences.Main.TOPIC_BACK_BEHAVIOR)
                ?.let {
                    val behavior = mainPreferencesHolder.observeTopicBackBehaviorFlow().first()
                    it.value = behavior.name
                    updateTopicBackBehaviorSummary(behavior)
                }
            findPreference<ListPreference>(Preferences.Main.TOPIC_OPEN_TARGET)
                ?.let {
                    val target = mainPreferencesHolder.observeTopicOpenTargetFlow().first()
                    it.value = target.name
                    updateTopicOpenTargetSummary(target)
                    updateTopicHeaderInitialStateEnabled(target)
                }
            findPreference<ListPreference>(Preferences.Main.STARTUP_SCREEN)
                ?.let {
                    val startup = mainPreferencesHolder.getStartupScreen()
                    it.value = startup.name
                    updateStartupScreenSummary(startup)
                }
            findPreference<ListPreference>(Preferences.Main.TOPIC_HEADER_INITIAL_STATE)
                ?.let {
                    val state = mainPreferencesHolder.observeTopicHeaderInitialStateFlow().first()
                    it.value = state.name
                    updateTopicHeaderInitialStateSummary(state)
                }
            updateTopicPageSwipePreferenceState(mainPreferencesHolder.observeTopicScrollModeFlow().first())
            // Topic preferences
            val topicHolder = forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext())
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Theme.SHOW_AVATARS)
                ?.isChecked = topicHolder.getShowAvatars()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Theme.CIRCLE_AVATARS)
                ?.isChecked = topicHolder.getCircleAvatars()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Theme.ANIMATED_SMILES)
                ?.isChecked = topicHolder.getAnimatedSmiles()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Theme.FLAT_POSTS)
                ?.isChecked = topicHolder.getFlatPosts()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Theme.MODERN_POST_HEADER)
                ?.isChecked = topicHolder.getModernPostHeader()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Theme.HIGHLIGHT_UNREAD_POST)
                ?.isChecked = topicHolder.getHighlightUnreadPost()
            // Lists preferences
            val listsHolder = forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder(requireContext())
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Lists.Topic.UNREAD_TOP)
                ?.isChecked = listsHolder.getUnreadTop()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Lists.Topic.SHOW_DOT)
                ?.isChecked = listsHolder.getShowDot()
            findPreference<androidx.preference.SwitchPreferenceCompat>("lists.favorites.load_all")
                ?.isChecked = listsHolder.getFavLoadAll()
            findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Lists.Favorites.SHOW_UNREAD_BADGE)
                ?.isChecked = listsHolder.getFavShowUnreadBadge()
        }

        if (authHolder.get().isAuth()) {
            findPreference<Preference>("auth.action.logout")?.apply {
                setOnPreferenceClickListener {
                    MaterialAlertDialogBuilder(requireActivity())
                            .setMessage(R.string.ask_logout)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                logoutRequest()
                            }
                            .setNegativeButton(R.string.no, null)
                            .showWithStyledButtons()
                    false
                }
            }
        } else {
            findPreference<Preference>("auth.action.logout")?.apply {
                isEnabled = false
            }
        }

        findPreference<Preference>("clear_menu_sequence")?.apply {
            setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireActivity())
                        .setMessage("Подтвердите действие")
                        .setPositiveButton(R.string.ok) { _, _ ->
                            preferences.edit().remove("menu_items_sequence").apply()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .showWithStyledButtons()
                false
            }
        }

        findPreference<Preference>("bottom_nav_order")?.setOnPreferenceClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_content, BottomNavOrderFragment())
                    .addToBackStack("bottom_nav_order")
                    .commit()
            true
        }

        findPreference<Preference>(Preferences.Main.ACCENT_PALETTE)?.apply {
            updateAccentSummary(mainPreferencesHolder.getAccentPalette())
            setOnPreferenceClickListener {
                forpdateam.ru.forpda.ui.views.dialog.AccentPickerDialog.show(
                        requireContext(),
                        mainPreferencesHolder.getAccentPalette(),
                        mainPreferencesHolder.getAccentCustomColor(),
                        mainPreferencesHolder.getAccentStyle(),
                ) { picked, customColor, style ->
                    if (!isAdded) return@show
                    lifecycleScope.launch {
                        if (picked == Preferences.Main.AccentPalette.CUSTOM && customColor != null) {
                            mainPreferencesHolder.setAccentCustomColor(customColor)
                        }
                        mainPreferencesHolder.setAccentStyle(style)
                        mainPreferencesHolder.setAccentPalette(picked)
                        updateAccentSummary(picked)
                        activity?.recreate()
                    }
                }
                true
            }
        }

        // Акцент/Material You активны только для палитры «Системный стиль».
        applyAccentPaletteGating()

        findPreference<Preference>(Preferences.Main.DOWNLOAD_FOLDER_URI)?.setOnPreferenceClickListener {
            showDownloadFolderDialog()
            true
        }

        findPreference<Preference>("about.application")?.apply {
            summary = String.format(getString(R.string.version_Build), BuildConfig.VERSION_NAME)
        }
        findPreference<androidx.preference.SwitchPreferenceCompat>(AppUpdatePreferences.KEY_CHECK_ENABLED)?.apply {
            isChecked = appUpdatePreferences.isCheckEnabled()
        }
        updateAppUpdateSummary()
        findPreference<Preference>("app_updates.check_now")?.setOnPreferenceClickListener {
            checkAppUpdateNow()
            true
        }

        findPreference<Preference>("about.support_author")?.setOnPreferenceClickListener {
            showSupportAuthorDialog()
            true
        }

        // Форк: скрываем/убираем встроенную проверку обновлений
        findPreference<Preference>("about.check_update")?.apply {
            isVisible = false
        }

        findPreference<Preference>(Preferences.Main.WEBVIEW_FONT_SIZE)?.apply {
            setOnPreferenceClickListener { _ ->

                val dialogView = requireActivity().layoutInflater.inflate(R.layout.dialog_font_size, null)
                    ?: throw IllegalStateException("Failed to inflate dialog_font_size")
                val seekBar = dialogView.findViewById<SeekBar>(R.id.value_seekbar) ?: throw IllegalStateException("seekBar not found")
                val textView = dialogView.findViewById<TextView>(R.id.value_textview) ?: throw IllegalStateException("textView not found")

                seekBar.progress = mainPreferencesHolder.getWebViewFontSize() - 1 - 7

                textView.text = (seekBar.progress + 1 + 7).toString()
                textView.textSize = (seekBar.progress + 1 + 7).toFloat()

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                        textView.text = (i + 1 + 7).toString()
                        textView.textSize = (i + 1 + 7).toFloat()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
                MaterialAlertDialogBuilder(requireActivity())
                        .setTitle(R.string.text_size)
                        .setView(dialogView)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                mainPreferencesHolder.setWebViewFontSize(seekBar.progress + 1 + 7)
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.reset, null)
                        .showWithStyledButtons()
                        .getButton(DialogInterface.BUTTON_NEUTRAL)
                        .setOnClickListener {
                            seekBar.progress = 16 - 1 - 7
                            viewLifecycleOwner.lifecycleScope.launch {
                                mainPreferencesHolder.setWebViewFontSize(16)
                            }
                        }

                false
            }
        }

        // Уточняем в коде, что существующий «Размер шрифта» — это размер текста именно в темах/постах.
        findPreference<Preference>(Preferences.Main.WEBVIEW_FONT_SIZE)?.title = "Размер шрифта в темах"

        // НОВЫЙ отдельный ползунок: размер шрифта ВСЕГО приложения (интерфейс).
        findPreference<Preference>("main.app_font_size")?.apply {
            fun appSizeSummary() =
                "Весь интерфейс, 100% = 16 (сейчас ${mainPreferencesHolder.getAppFontSize()}); применится после возврата из настроек"
            title = "Размер шрифта приложения"
            summary = appSizeSummary()
            setOnPreferenceClickListener {
                val dialogView = requireActivity().layoutInflater.inflate(R.layout.dialog_font_size, null)
                    ?: throw IllegalStateException("Failed to inflate dialog_font_size")
                val seekBar = dialogView.findViewById<SeekBar>(R.id.value_seekbar)
                    ?: throw IllegalStateException("seekBar not found")
                val textView = dialogView.findViewById<TextView>(R.id.value_textview)
                    ?: throw IllegalStateException("textView not found")
                seekBar.progress = mainPreferencesHolder.getAppFontSize() - 8
                textView.text = (seekBar.progress + 8).toString()
                textView.textSize = (seekBar.progress + 8).toFloat()
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, i: Int, b: Boolean) {
                        textView.text = (i + 8).toString()
                        textView.textSize = (i + 8).toFloat()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
                MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Размер шрифта приложения")
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            mainPreferencesHolder.setAppFontSize(seekBar.progress + 8)
                            summary = appSizeSummary()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset, null)
                    .showWithStyledButtons()
                    .getButton(DialogInterface.BUTTON_NEUTRAL)
                    .setOnClickListener {
                        seekBar.progress = 16 - 8
                        viewLifecycleOwner.lifecycleScope.launch {
                            mainPreferencesHolder.setAppFontSize(16)
                            summary = appSizeSummary()
                        }
                    }
                false
            }
        }

        findPreference<Preference>("open_notifications")?.apply {
            setOnPreferenceClickListener {
                val intent = Intent(activity, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.ARG_NEW_PREFERENCE_SCREEN, NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME)
                startActivity(intent)
                true
            }
        }

        findPreference<Preference>("open_forum_settings")?.apply {
            setOnPreferenceClickListener {
                val intent = Intent(activity, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.ARG_NEW_PREFERENCE_SCREEN, ForumSettingsFragment.PREFERENCE_SCREEN_NAME)
                startActivity(intent)
                true
            }
        }

        findPreference<Preference>("open_forum_rules")?.apply {
            setOnPreferenceClickListener {
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_FORUM_RULES, true)
                }
                startActivity(intent)
                true
            }
        }

        findPreference<Preference>("open_forum_blacklist")?.apply {
            setOnPreferenceClickListener {
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_FORUM_BLACKLIST, true)
                }
                startActivity(intent)
                true
            }
        }

        findPreference<Preference>(Preferences.Main.UI_PALETTE)?.apply {
            lifecycleScope.launch {
                updateUiPaletteSummary(mainPreferencesHolder.observeUiPaletteFlow().first())
            }
            setOnPreferenceClickListener {
                forpdateam.ru.forpda.ui.views.dialog.PalettePickerDialog.show(
                        requireContext(), mainPreferencesHolder.getUiPalette()
                ) { palette ->
                    updateUiPaletteSummary(palette)
                    lifecycleScope.launch {
                        mainPreferencesHolder.setUiPalette(palette)
                        activity?.recreate()
                    }
                }
                true
            }
        }

        findPreference<Preference>(Preferences.Main.APP_FONT_MODE)?.setOnPreferenceClickListener {
            forpdateam.ru.forpda.ui.views.dialog.FontPickerDialog.show(
                    requireContext(), FontController.getCurrentFontMode(mainPreferencesHolder)
            ) { mode ->
                updateAppFontSummary(mode)
                lifecycleScope.launch {
                    mainPreferencesHolder.setAppFontMode(mode)
                    activity?.recreate()
                }
            }
            true
        }

        findPreference<Preference>(Preferences.Main.TOPIC_PAGINATION_PANELS)?.setOnPreferenceClickListener {
            showTopicPaginationPanelsDialog()
            true
        }

        findPreference<ListPreference>(Preferences.Main.TOPIC_SCROLL_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            val mode = SettingsPreferenceParsers.parseTopicScrollMode(newValue as? String)
            updateTopicScrollModeSummary(mode)
            updateTopicPageSwipePreferenceState(mode)
            // The pagination-panels summary depends on the mode (hybrid hides the bottom bit).
            updateTopicPaginationPanelsSummary(mainPreferencesHolder.getTopicPaginationPanels(), mode)
            lifecycleScope.launch {
                mainPreferencesHolder.setTopicScrollMode(mode)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.TOPIC_POST_DENSITY)?.setOnPreferenceChangeListener { _, newValue ->
            val density = SettingsPreferenceParsers.parseTopicPostDensity(newValue as? String)
            updateTopicPostDensitySummary(density)
            lifecycleScope.launch {
                mainPreferencesHolder.setTopicPostDensity(density)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.TOPIC_BACK_BEHAVIOR)?.setOnPreferenceChangeListener { _, newValue ->
            val behavior = SettingsPreferenceParsers.parseTopicBackBehavior(newValue as? String)
            updateTopicBackBehaviorSummary(behavior)
            lifecycleScope.launch {
                mainPreferencesHolder.setTopicBackBehavior(behavior)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.TOPIC_OPEN_TARGET)?.setOnPreferenceChangeListener { _, newValue ->
            val target = SettingsPreferenceParsers.parseTopicOpenTarget(newValue as? String)
            updateTopicOpenTargetSummary(target)
            updateTopicHeaderInitialStateEnabled(target)
            lifecycleScope.launch {
                mainPreferencesHolder.setTopicOpenTarget(target)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.STARTUP_SCREEN)?.setOnPreferenceChangeListener { _, newValue ->
            val startup = SettingsPreferenceParsers.parseStartupScreen(newValue as? String)
            updateStartupScreenSummary(startup)
            lifecycleScope.launch {
                mainPreferencesHolder.setStartupScreen(startup)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.TOPIC_TOOLBAR_BEHAVIOR)?.setOnPreferenceChangeListener { _, newValue ->
            val behavior = SettingsPreferenceParsers.parseTopicToolbarBehavior(newValue as? String)
            updateTopicToolbarBehaviorSummary(behavior)
            lifecycleScope.launch {
                mainPreferencesHolder.setTopicToolbarBehavior(behavior)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.TOPIC_HEADER_INITIAL_STATE)?.setOnPreferenceChangeListener { _, newValue ->
            val state = SettingsPreferenceParsers.parseTopicHeaderInitialState(newValue as? String)
            updateTopicHeaderInitialStateSummary(state)
            lifecycleScope.launch {
                mainPreferencesHolder.setTopicHeaderInitialState(state)
            }
            true
        }

        findPreference<ListPreference>(Preferences.Main.DOWNLOAD_METHOD)?.setOnPreferenceChangeListener { _, newValue ->
            val method = SettingsPreferenceParsers.parseDownloadMethod(newValue as? String)
            updateDownloadMethodSummary(method)
            lifecycleScope.launch {
                mainPreferencesHolder.setDownloadMethod(method)
            }
            true
        }

        findPreference<ListPreference>(LocaleHelper.SELECTED_LANGUAGE)?.setOnPreferenceChangeListener { _, _ ->
            requireActivity().window.decorView.post {
                MainActivity.restartApplication(requireActivity())
            }
            true
        }

        // Visual theme picker with light/dark/AMOLED preview panes.
        findPreference<Preference>("main.theme.mode")?.apply {
            lifecycleScope.launch {
                updateThemeModeSummary(mainPreferencesHolder.observeThemeModeFlow().first())
            }
            setOnPreferenceClickListener {
                forpdateam.ru.forpda.ui.views.dialog.ThemeModePickerDialog.show(
                        requireContext(), mainPreferencesHolder.getThemeMode()
                ) { mode ->
                    Timber.d("[THEME] Preference changed to: $mode")
                    updateThemeModeSummary(mode)
                    // 1. Save to DataStore asynchronously (suspend, no runBlocking), then restart
                    lifecycleScope.launch {
                        // Захватываем app-context ДО suspend/applyTheme: applyTheme →
                        // setDefaultNightMode → recreate() отсоединяет фрагмент, после чего
                        // Fragment.startActivity падал «not attached to Activity».
                        val ctx = requireContext().applicationContext
                        mainPreferencesHolder.setThemeMode(mode)
                        Timber.d("[THEME] Saved to DataStore: $mode")
                        // 2. Apply night mode immediately
                        DayNightHelper.applyTheme(mode)
                        // 3. Force restart entire app to pick up new theme styles.
                        // Context.startActivity (app-context + NEW_TASK) не требует
                        // прикреплённого фрагмента — безопасно после recreate().
                        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        if (intent != null) {
                            ctx.startActivity(intent)
                            activity?.finishAffinity()
                        }
                    }
                }
                true
            }
        }
    }

    private fun showAppFontRestartNotice() {
        view?.makeSnackbarAboveSystemBars(
            R.string.pref_app_font_restart_notice,
            Snackbar.LENGTH_LONG
        )?.setAction(R.string.restart) {
            if (isAdded) {
                MainActivity.restartApplication(requireActivity())
            }
        }?.show()
    }

    private fun updateAppFontSummary(mode: AppFontMode) {
        findPreference<Preference>(Preferences.Main.APP_FONT_MODE)?.summary = when (mode) {
            AppFontMode.SYSTEM -> getString(R.string.pref_summary_app_font_system)
            AppFontMode.ROBOTO -> getString(R.string.pref_summary_app_font_roboto)
            AppFontMode.INTER -> getString(R.string.pref_summary_app_font_inter)
            AppFontMode.SOURCE_SANS_3 -> getString(R.string.pref_summary_app_font_source_sans_3)
            AppFontMode.OPEN_SANS -> getString(R.string.pref_summary_app_font_open_sans)
            AppFontMode.ROBOTO_MONO -> "Roboto Mono — моноширинный; применится после возврата из настроек"
        }
    }

    private fun updateAccentSummary(palette: Preferences.Main.AccentPalette) {
        findPreference<Preference>(Preferences.Main.ACCENT_PALETTE)?.summary = getString(
                R.string.pref_summary_accent_active,
                getString(forpdateam.ru.forpda.ui.views.dialog.AccentPickerDialog.titleRes(palette))
        )
    }

    /**
     * Акцент и Material You действуют только для палитры «Системный стиль»
     * (см. AccentPolicy: palette != SYSTEM → Mode.NONE). Для палитр чтения
     * (Sepia/SepiaBlue/Minimal) — гасим оба пункта и поясняем это в описании.
     *
     * Плюс Material You имеет приоритет над курируемым акцентом (Mode.WALLPAPER),
     * поэтому при включённых цветах системы выбор акцента ни на что не влияет —
     * гасим пункт, а не оставляем кликабельную пустышку.
     */
    private fun applyAccentPaletteGating() {
        val isSystem = mainPreferencesHolder.getUiPalette() == Preferences.Main.UiPalette.SYSTEM
        val materialYouWins = forpdateam.ru.forpda.presentation.theme.AccentPolicy.resolveMode(
                mainPreferencesHolder.getUseMaterialYou(),
                mainPreferencesHolder.getUiPalette(),
                mainPreferencesHolder.getAccentPalette(),
                android.os.Build.VERSION.SDK_INT,
        ) == forpdateam.ru.forpda.presentation.theme.AccentPolicy.Mode.WALLPAPER
        findPreference<Preference>(Preferences.Main.ACCENT_PALETTE)?.apply {
            isEnabled = isSystem && !materialYouWins
            when {
                !isSystem -> setSummary(R.string.pref_summary_accent_non_system)
                materialYouWins -> setSummary(R.string.pref_summary_accent_material_you)
                else -> updateAccentSummary(mainPreferencesHolder.getAccentPalette())
            }
        }
        findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.USE_MATERIAL_YOU)?.apply {
            isEnabled = isSystem
            setSummary(if (isSystem) R.string.pref_summary_use_material_you
            else R.string.pref_summary_accent_non_system)
        }
    }

    private fun updateThemeModeSummary(mode: Preferences.Main.ThemeMode) {
        findPreference<Preference>("main.theme.mode")?.setSummary(
                when (mode) {
                    Preferences.Main.ThemeMode.LIGHT -> R.string.pref_summary_theme_mode_light
                    Preferences.Main.ThemeMode.DARK -> R.string.pref_summary_theme_mode_dark
                    Preferences.Main.ThemeMode.AMOLED -> R.string.pref_summary_theme_mode_amoled
                    Preferences.Main.ThemeMode.SYSTEM_AMOLED -> R.string.pref_summary_theme_mode_system_amoled
                    else -> R.string.pref_summary_theme_mode_system
                }
        )
    }

    private fun updateUiPaletteSummary(palette: Preferences.Main.UiPalette) {
        findPreference<Preference>(Preferences.Main.UI_PALETTE)?.setSummary(
                when (palette) {
                    Preferences.Main.UiPalette.SEPIA_READING -> R.string.pref_summary_ui_palette_sepia_reading
                    Preferences.Main.UiPalette.SEPIA_BLUE -> R.string.pref_summary_ui_palette_sepia_blue
                    Preferences.Main.UiPalette.MINIMAL_READER -> R.string.pref_summary_ui_palette_minimal_reader
                    Preferences.Main.UiPalette.GREEN_CARE -> R.string.pref_value_ui_palette_green_care
                    Preferences.Main.UiPalette.NORD -> R.string.pref_value_ui_palette_nord
                    Preferences.Main.UiPalette.SOLARIZED -> R.string.pref_value_ui_palette_solarized
                    Preferences.Main.UiPalette.GRUVBOX -> R.string.pref_value_ui_palette_gruvbox
                    Preferences.Main.UiPalette.ROSE_PINE -> R.string.pref_value_ui_palette_rose_pine
                    Preferences.Main.UiPalette.DRACULA -> R.string.pref_value_ui_palette_dracula
                    else -> R.string.pref_summary_ui_palette_system
                }
        )
    }

    private fun updateTopicScrollModeSummary(mode: Preferences.Main.TopicScrollMode) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_SCROLL_MODE)?.setSummary(
                when (mode) {
                    Preferences.Main.TopicScrollMode.CLASSIC -> R.string.pref_summary_topic_scroll_mode_classic
                    else -> R.string.pref_summary_topic_scroll_mode_hybrid
                }
        )
    }

    /** Label for one [TopicPaginationPanels] value (also reused as the dialog item text). */
    private fun paginationPanelsLabel(panels: Preferences.Main.TopicPaginationPanels): String = getString(
            when (panels) {
                Preferences.Main.TopicPaginationPanels.NONE -> R.string.pref_value_topic_pagination_panels_none
                Preferences.Main.TopicPaginationPanels.TOP -> R.string.pref_value_topic_pagination_panels_top
                Preferences.Main.TopicPaginationPanels.BOTTOM -> R.string.pref_value_topic_pagination_panels_bottom
                Preferences.Main.TopicPaginationPanels.BOTH -> R.string.pref_value_topic_pagination_panels_both
            })

    /**
     * Summary reflects what is EFFECTIVELY shown in the current reading mode: hybrid ignores the bottom
     * bit, so a stored BOTTOM/BOTH collapses to «Нет»/«Сверху» there (mirrors the mode-aware picker).
     */
    private fun updateTopicPaginationPanelsSummary(
            panels: Preferences.Main.TopicPaginationPanels,
            mode: Preferences.Main.TopicScrollMode,
    ) {
        val effective = if (mode == Preferences.Main.TopicScrollMode.CLASSIC) panels
        else if (panels.hasTop) Preferences.Main.TopicPaginationPanels.TOP
        else Preferences.Main.TopicPaginationPanels.NONE
        findPreference<Preference>(Preferences.Main.TOPIC_PAGINATION_PANELS)?.summary = paginationPanelsLabel(effective)
    }

    /**
     * Mode-aware single-choice picker. CLASSIC offers all four combinations; HYBRID offers only
     * «Нет»/«Сверху» (the bottom bar is meaningless over infinite scroll). To avoid losing the user's
     * classic bottom choice, a hybrid pick edits ONLY the top bit via [TopicPaginationPanels.withTop].
     */
    private fun showTopicPaginationPanelsDialog() {
        lifecycleScope.launch {
            val mode = mainPreferencesHolder.observeTopicScrollModeFlow().first()
            val current = mainPreferencesHolder.observeTopicPaginationPanelsFlow().first()
            val classic = mode == Preferences.Main.TopicScrollMode.CLASSIC
            val values = if (classic) listOf(
                    Preferences.Main.TopicPaginationPanels.NONE,
                    Preferences.Main.TopicPaginationPanels.TOP,
                    Preferences.Main.TopicPaginationPanels.BOTTOM,
                    Preferences.Main.TopicPaginationPanels.BOTH,
            ) else listOf(
                    Preferences.Main.TopicPaginationPanels.NONE,
                    Preferences.Main.TopicPaginationPanels.TOP,
            )
            val labels = values.map { paginationPanelsLabel(it) }.toTypedArray()
            val checked = if (classic) values.indexOf(current).coerceAtLeast(0)
            else values.indexOf(
                    if (current.hasTop) Preferences.Main.TopicPaginationPanels.TOP
                    else Preferences.Main.TopicPaginationPanels.NONE)
            MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_title_topic_pagination_panel)
                    .setSingleChoiceItems(labels, checked) { dialog, which ->
                        val picked = values[which]
                        val merged = if (classic) picked else current.withTop(picked.hasTop)
                        updateTopicPaginationPanelsSummary(merged, mode)
                        lifecycleScope.launch { mainPreferencesHolder.setTopicPaginationPanels(merged) }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
        }
    }

    private fun updateTopicPostDensitySummary(density: Preferences.Main.TopicPostDensity) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_POST_DENSITY)?.setSummary(
                when (density) {
                    Preferences.Main.TopicPostDensity.SUPER_COMPACT -> R.string.pref_summary_topic_post_density_super_compact
                    Preferences.Main.TopicPostDensity.COMPACT -> R.string.pref_summary_topic_post_density_compact
                    else -> R.string.pref_summary_topic_post_density_comfortable
                }
        )
    }

    private fun updateTopicToolbarBehaviorSummary(behavior: Preferences.Main.TopicToolbarBehavior) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_TOOLBAR_BEHAVIOR)?.setSummary(
                when (behavior) {
                    Preferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL -> R.string.pref_topic_toolbar_behavior_hide_on_scroll_summary
                    else -> R.string.pref_topic_toolbar_behavior_pinned_summary
                }
        )
    }

    private fun updateTopicBackBehaviorSummary(behavior: Preferences.Main.TopicBackBehavior) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_BACK_BEHAVIOR)?.setSummary(
                when (behavior) {
                    Preferences.Main.TopicBackBehavior.ORIGIN -> R.string.pref_summary_topic_back_behavior_origin
                    else -> R.string.pref_summary_topic_back_behavior_history
                }
        )
    }

    private fun updateTopicOpenTargetSummary(target: Preferences.Main.TopicOpenTarget) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_OPEN_TARGET)?.setSummary(
                when (target) {
                    Preferences.Main.TopicOpenTarget.FIRST_PAGE -> R.string.pref_summary_topic_open_target_first_page
                    else -> R.string.pref_summary_topic_open_target_last_unread
                }
        )
    }

    private fun updateStartupScreenSummary(startup: Preferences.Main.StartupScreen) {
        findPreference<ListPreference>(Preferences.Main.STARTUP_SCREEN)?.setSummary(
                when (startup) {
                    Preferences.Main.StartupScreen.FAVORITES -> R.string.pref_summary_startup_screen_favorites
                    Preferences.Main.StartupScreen.FORUM -> R.string.pref_summary_startup_screen_forum
                    Preferences.Main.StartupScreen.REPLIES -> R.string.pref_summary_startup_screen_replies
                    Preferences.Main.StartupScreen.QMS -> R.string.pref_summary_startup_screen_qms
                    Preferences.Main.StartupScreen.MENU -> R.string.pref_summary_startup_screen_menu
                    else -> R.string.pref_summary_startup_screen_news
                }
        )
    }

    private fun updateTopicHeaderInitialStateSummary(state: Preferences.Main.TopicHeaderInitialState) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_HEADER_INITIAL_STATE)?.setSummary(
                when (state) {
                    Preferences.Main.TopicHeaderInitialState.COLLAPSED -> R.string.pref_summary_topic_header_initial_state_collapsed
                    else -> R.string.pref_summary_topic_header_initial_state_expanded
                }
        )
    }

    private fun updateTopicHeaderInitialStateEnabled(target: Preferences.Main.TopicOpenTarget) {
        findPreference<ListPreference>(Preferences.Main.TOPIC_HEADER_INITIAL_STATE)?.isEnabled =
                target == Preferences.Main.TopicOpenTarget.FIRST_PAGE
    }

    private fun updateDownloadMethodSummary(method: Preferences.Main.DownloadMethod) {
        findPreference<ListPreference>(Preferences.Main.DOWNLOAD_METHOD)?.setSummary(
                when (method) {
                    Preferences.Main.DownloadMethod.EXTERNAL_MANAGER -> R.string.download_method_external_manager
                    Preferences.Main.DownloadMethod.BROWSER -> R.string.download_method_browser
                    Preferences.Main.DownloadMethod.ASK -> R.string.download_method_ask
                    else -> R.string.download_method_system
                }
        )
    }

    private fun updateTopicPageSwipePreferenceState(mode: Preferences.Main.TopicScrollMode) {
        findPreference<androidx.preference.SwitchPreferenceCompat>(Preferences.Main.TOPIC_PAGE_SWIPE_ENABLE)?.apply {
            val isClassic = mode == Preferences.Main.TopicScrollMode.CLASSIC
            isEnabled = isClassic
            setSummary(if (isClassic) R.string.pref_summary_topic_page_swipe else R.string.pref_summary_topic_page_swipe_disabled)
        }
    }

    private fun updateAppUpdateSummary() {
        findPreference<Preference>("app_updates.check_now")?.summary =
            getString(R.string.pref_summary_app_update_check_now, BuildConfig.VERSION_NAME)
    }

    private fun checkAppUpdateNow() {
        showSnackbarAboveSystemBars(R.string.app_update_checking)
        Log.i(AppUpdateRepository.LOG_TAG, "manual UI start enabled=${appUpdatePreferences.isCheckEnabled()}")
        Timber.tag(AppUpdateRepository.LOG_TAG).i("manual UI start enabled=%s", appUpdatePreferences.isCheckEnabled())
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { appUpdateRepository.check(manual = true) }
                .onSuccess { result ->
                    when (result) {
                        is AppUpdateRepository.CheckResult.UpdateAvailable -> {
                            val preferred = appUpdateRepository.pickPreferredDownload(result.downloads)
                            Log.i(
                                AppUpdateRepository.LOG_TAG,
                                "manual UI result update_available version=${result.version} topicUrl=${result.topicUrl} downloads=${result.downloads.size} preferredUrl=${preferred?.url} openActionRegistered=true openActionFired=false"
                            )
                            Timber.tag(AppUpdateRepository.LOG_TAG).i(
                                "manual UI result update_available version=%s topicUrl=%s downloads=%d preferredUrl=%s openActionRegistered=true openActionFired=false",
                                result.version,
                                result.topicUrl,
                                result.downloads.size,
                                preferred?.url
                            )
                            showAppUpdateAvailableSnackbar(result)
                        }
                        is AppUpdateRepository.CheckResult.UpToDate -> {
                            Log.i(
                                AppUpdateRepository.LOG_TAG,
                                "manual UI result up_to_date latest=${result.latestVersion} current=${BuildConfig.VERSION_NAME}"
                            )
                            Timber.tag(AppUpdateRepository.LOG_TAG).i(
                                "manual UI result up_to_date latest=%s current=%s",
                                result.latestVersion,
                                BuildConfig.VERSION_NAME
                            )
                            showSnackbarAboveSystemBars(getString(R.string.app_update_up_to_date, BuildConfig.VERSION_NAME))
                        }
                    }
                }
                .onFailure { error ->
                    Log.w(AppUpdateRepository.LOG_TAG, "manual UI failed", error)
                    Timber.tag(AppUpdateRepository.LOG_TAG).w(error, "manual UI failed")
                    showSnackbarAboveSystemBars(appUpdateCheckErrorMessage(error))
                }
        }
    }

    private fun showAppUpdateAvailableSnackbar(result: AppUpdateRepository.CheckResult.UpdateAvailable) {
        // Раньше уведомление о новой версии показывалось Snackbar'ом с кнопкой-действием
        // «Скачать». Но на Android 14+/16 инфляция Snackbar$SnackbarLayout падает
        // (в контексте темы не разрешается colorOnSurface, см. SnackbarHelper), и показ
        // деградировал до Toast — а у Toast НЕТ кнопки-действия, поэтому «Скачать»
        // пропадала и обновление было не скачать («пишет что версия есть, скачать не даёт»).
        // Диалог этой проблемы не имеет (materialAlertDialogTheme → ThemeOverlay) и кнопку
        // потерять невозможно.
        if (!isAdded) return
        val preferred = appUpdateRepository.pickPreferredDownload(result.downloads)
        val title = getString(R.string.app_update_available, result.version.toString())
        val notes = result.description?.takeIf { it.isNotBlank() }

        Log.i(
            AppUpdateRepository.LOG_TAG,
            "manual UI dialog shown version=${result.version} downloads=${result.downloads.size} preferredUrl=${preferred?.url}"
        )
        Timber.tag(AppUpdateRepository.LOG_TAG).i(
            "manual UI dialog shown version=%s downloads=%d preferredUrl=%s",
            result.version,
            result.downloads.size,
            preferred?.url
        )

        runCatching {
            val builder = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(title)
            if (notes != null) builder.setMessage(notes)

            if (preferred != null) {
                // Есть прямая ссылка на APK — основное действие «Скачать», запасное «Открыть тему».
                builder.setPositiveButton(R.string.app_update_action_download) { _, _ ->
                    Log.i(
                        AppUpdateRepository.LOG_TAG,
                        "manual UI download action fired version=${result.version} url=${preferred.url}"
                    )
                    Timber.tag(AppUpdateRepository.LOG_TAG).i(
                        "manual UI download action fired version=%s url=%s",
                        result.version,
                        preferred.url
                    )
                    startApkDownload(preferred)
                }
                builder.setNeutralButton(R.string.app_update_action_open_topic) { _, _ ->
                    openAppUpdateTopicUrl(result.topicUrl)
                }
            } else {
                // Прямой ссылки нет (например, версия отдана из кэша) — единственное
                // действие «Открыть тему».
                builder.setPositiveButton(R.string.app_update_action_open_topic) { _, _ ->
                    Log.i(
                        AppUpdateRepository.LOG_TAG,
                        "manual UI open topic action fired version=${result.version} url=${result.topicUrl}"
                    )
                    Timber.tag(AppUpdateRepository.LOG_TAG).i(
                        "manual UI open topic action fired version=%s url=%s",
                        result.version,
                        result.topicUrl
                    )
                    openAppUpdateTopicUrl(result.topicUrl)
                }
            }
            builder.setNegativeButton(R.string.close, null)
            builder.showWithStyledButtons()
        }.onFailure { e ->
            // Даже диалог не построился (крайне маловероятно) — не роняем настройки,
            // показываем хотя бы текст. Toast без кнопки — деградация, но лучше вылета.
            Timber.tag(AppUpdateRepository.LOG_TAG).w(e, "update-available dialog failed; toast fallback")
            runCatching { Toast.makeText(requireContext(), title, Toast.LENGTH_LONG).show() }
        }
    }

    private fun startApkDownload(link: DownloadLink) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                showSnackbarAboveSystemBars(
                    getString(R.string.app_update_check_failed_unknown)
                )
            }
    }

    private fun openAppUpdateTopicUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setClass(requireContext(), MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun appUpdateCheckErrorMessage(error: Throwable): String {
        val reason = (error as? AppUpdateRepository.CheckException)?.reason
        val reasonText = when (reason) {
            AppUpdateRepository.FailureReason.Network -> getString(R.string.app_update_check_failed_network)
            AppUpdateRepository.FailureReason.RateLimited -> getString(R.string.app_update_check_failed_rate_limited)
            AppUpdateRepository.FailureReason.Forbidden,
            AppUpdateRepository.FailureReason.Captcha -> getString(R.string.app_update_check_failed_forbidden)
            AppUpdateRepository.FailureReason.NotFound -> getString(R.string.app_update_check_failed_not_found)
            AppUpdateRepository.FailureReason.Parse -> getString(R.string.app_update_check_failed_parse)
            AppUpdateRepository.FailureReason.Server -> getString(R.string.app_update_check_failed_server)
            else -> error.message?.takeIf { it.isNotBlank() }?.take(80)
                ?: getString(R.string.app_update_check_failed_unknown)
        }
        return getString(R.string.app_update_check_failed_with_reason, reasonText)
    }

    private fun showSupportAuthorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_support_author, null)
        val paymentLink = getString(R.string.support_author_payment_link)
        val tbankCardNumber = getString(R.string.support_author_tbank_card)
        val sberCardNumber = getString(R.string.support_author_sber_card)
        val usdtAddress = getString(R.string.support_author_usdt_trc20)

        dialogView.findViewById<TextView>(R.id.supportAuthorPaymentLink)?.apply {
            text = SpannableString(paymentLink).apply {
                setSpan(UnderlineSpan(), 0, paymentLink.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setOnClickListener {
                openSupportPaymentLink(paymentLink)
            }
        }
        dialogView.findViewById<View>(R.id.supportAuthorCopyTbankCard)?.setOnClickListener {
            copySupportValue(tbankCardNumber)
        }
        dialogView.findViewById<View>(R.id.supportAuthorCopySberCard)?.setOnClickListener {
            copySupportValue(sberCardNumber)
        }
        dialogView.findViewById<View>(R.id.supportAuthorCopyUsdt)?.setOnClickListener {
            copySupportValue(usdtAddress)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.support_author_title)
            .setView(dialogView)
            .setNegativeButton(R.string.close, null)
            .showWithStyledButtons()
    }

    private fun copySupportValue(value: String) {
        clipboardHelper.copyToClipboard(value)
        showSnackbar(R.string.copied)
    }

    private fun openSupportPaymentLink(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        }.onFailure { error ->
            if (error !is ActivityNotFoundException) {
                Timber.e(error, "Failed to open support payment link")
            }
            showSnackbar(R.string.error_occurred)
        }
    }

    private fun showDownloadFolderDialog() {
        val currentUri = mainPreferencesHolder.getDownloadFolderUri()
        val items = mutableListOf(getString(R.string.download_folder_choose))
        if (!currentUri.isNullOrBlank()) {
            items += getString(R.string.download_folder_reset)
        }
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.pref_title_download_folder)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    openDownloadFolderPicker()
                } else {
                    resetDownloadFolder()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    private fun openDownloadFolderPicker() {
        val downloadsDoc = Uri.parse("content://com.android.externalstorage.documents/root/primary")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDoc)
        }
        downloadFolderLauncher.launch(intent)
    }

    private fun persistDownloadFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure { e ->
            Timber.e(e, "Failed to persist downloads folder permission")
            showSnackbar(R.string.download_folder_unavailable)
            return
        }
        lifecycleScope.launch {
            mainPreferencesHolder.setDownloadFolderUri(uri.toString())
            updateDownloadFolderSummary(uri.toString())
            showSnackbar(R.string.download_folder_saved)
        }
    }

    private fun resetDownloadFolder() {
        val currentUri = mainPreferencesHolder.getDownloadFolderUri()
        currentUri?.let { value ->
            runCatching {
                requireContext().contentResolver.releasePersistableUriPermission(
                    Uri.parse(value),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        lifecycleScope.launch {
            mainPreferencesHolder.setDownloadFolderUri(null)
            updateDownloadFolderSummary(null)
            showSnackbar(R.string.download_folder_reset_done)
        }
    }

    private fun updateDownloadFolderSummary(uriString: String?) {
        findPreference<Preference>(Preferences.Main.DOWNLOAD_FOLDER_URI)?.summary = if (uriString.isNullOrBlank()) {
            getString(R.string.pref_summary_download_folder_default)
        } else {
            getString(R.string.pref_summary_download_folder_selected, readableFolderName(uriString))
        }
    }

    private fun readableFolderName(uriString: String): String {
        return runCatching {
            val uri = Uri.parse(uriString)
            DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/')
                .ifBlank { uri.lastPathSegment.orEmpty() }
        }.getOrDefault(uriString)
    }

    private fun logoutRequest() {
        logoutJob?.cancel()
        logoutJob = lifecycleScope.launch {
            try {
                val success = authRepository.signOut()
                if (success) {
                    showSnackbar("Logout complete")
                } else {
                    showSnackbar("Logout error")
                }
            } catch (e: Exception) {
                showSnackbar("Logout error: $e")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logoutJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar
                ?.setTitle(R.string.activity_title_settings)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

}
