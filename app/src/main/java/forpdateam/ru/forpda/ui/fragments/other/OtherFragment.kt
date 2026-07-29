package forpdateam.ru.forpda.ui.fragments.other

import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import android.content.Intent
import javax.inject.Inject
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.app.other.OtherMenuBlock
import forpdateam.ru.forpda.entity.app.other.QuickSetting
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.appupdates.AppUpdateRepository
import forpdateam.ru.forpda.appupdates.runManualAppUpdateCheck
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.ui.FontController
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.fragments.settings.NotificationsSettingsFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.ui.views.dialog.AccentPickerDialog
import forpdateam.ru.forpda.ui.views.dialog.FontPickerDialog
import forpdateam.ru.forpda.ui.views.dialog.PalettePickerDialog
import forpdateam.ru.forpda.ui.views.dialog.ThemeModePickerDialog
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.presentation.other.OtherViewModel
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.tuneForListPerformance
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import forpdateam.ru.forpda.ui.views.drawers.adapters.ProfileListItem
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 16.12.17.
 */
@AndroidEntryPoint
class OtherFragment : TabFragment() {

    private val otherAdapter by lazy {
        OtherAdapter(
                profileClickListener,
                menuClickListener,
                exitClickListener,
                infoCloseClickListener,
                editDoneClickListener,
                editResetClickListener,
                addShortcutClickListener,
                removeShortcutClickListener,
                continueClickListener,
                quickSettingsRowClickListener,
                blockVisibilityClickListener,
                blockConfigureClickListener,
                topicPreferencesHolder
        )
    }

    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder

    @Inject lateinit var appUpdateRepository: AppUpdateRepository

    private var listScrollY = 0
    private var listBaseBottomPadding = 0
    private var bottomChromePadding = 0
    private lateinit var listRecycler: RecyclerView
    private var itemTouchHelper: ItemTouchHelper? = null

    private val viewModel: OtherViewModel by viewModels()

    init {
        configuration.defaultTitle = "Полное меню приложения"
    }

    override fun useTopBarRoundedBottomCorners(): Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_other)
        listRecycler = findViewById(R.id.recyclerView) as? RecyclerView ?: throw IllegalStateException("listRecycler not found")
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appBarLayout.visibility = View.GONE
        // Иначе под списком просвечивает ?background_for_lists координатора — полоса над нижним меню.
        coordinatorLayout.setBackgroundColor(requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        listRecycler.apply {
            listBaseBottomPadding = paddingBottom
            val gridLayoutManager = GridLayoutManager(this.context, MENU_GRID_SPAN_COUNT)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = otherAdapter.getSpanSize(position, MENU_GRID_SPAN_COUNT)
            }
            layoutManager = gridLayoutManager
            adapter = otherAdapter
            addItemDecoration(
                    MenuTileSpacingDecoration(
                            spanCount = MENU_GRID_SPAN_COUNT,
                            outerPx = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal),
                            gapPx = resources.getDimensionPixelSize(R.dimen.dp8),
                    )
            )
            tuneForListPerformance()
            otherAdapter.editModeChangeListener = { enabled ->
                viewModel.onMenuDragModeChange(enabled)
                if (enabled) {
                    showSnackbar("Режим редактирования")
                }
            }
            itemTouchHelper = ItemTouchHelper(OtherItemDragCallback(
                    otherAdapter,
                    object : OtherItemDragCallback.ItemTouchHelperListener {
                        override fun onDragStart() {
                        }

                        override fun onItemMove(fromPosition: Int, toPosition: Int) {
                            otherAdapter.moveItem(fromPosition, toPosition)
                        }

                        override fun onDragEnd() {
                            // post: пересборка списка (зоны пустых секций) прилетает из clearView,
                            // когда RecyclerView ещё может считать layout.
                            listRecycler.post {
                                val layout = otherAdapter.commitDragLayout()
                                // Панель и сетка — одна последовательность: сначала пункты панели,
                                // потом остальные. Сохраняем оба порядка одним движением.
                                viewModel.onChangeBottomNav(
                                        otherAdapter.currentBottomNavIds(),
                                        otherAdapter.currentMenuNavIds(),
                                )
                                viewModel.onChangeMenuSequence(layout)
                            }
                        }
                    }
            )).also { it.attachToRecyclerView(this) }
            // Перехватчика касаний здесь больше нет: он стартовал drag на ACTION_DOWN и гасил режим
            // редактирования при касании чего угодно, кроме плиток. Из-за этого список нельзя было
            // прокрутить (скролл начинается тем же ACTION_DOWN), а кнопки заголовков не получали
            // кликов. Теперь перетаскивание — долгим нажатием (OtherItemDragCallback), а выход из
            // режима — кнопка «Готово» и системное «назад».

            // Отступы плиток считаются от позиции в ряду (MenuTileSpacingDecoration), а RecyclerView
            // кэширует их у уже привязанных вью. После удаления/переноса плитки соседи по ряду
            // меняют колонку, но со старыми отступами — ряд уезжает вбок. Пересчитываем декорации.
            otherAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() = invalidateTileSpacing()
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = invalidateTileSpacing()
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = invalidateTileSpacing()
                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = invalidateTileSpacing()
            })

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    listScrollY = recyclerView.computeVerticalScrollOffset()
                    updateToolbarShadow()
                }
            })
        }
        syncBottomChromePadding()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.isReady) return@collect
                    otherAdapter.setCustomLayout(state.menuTileLayout)
                    otherAdapter.setShortcuts(state.shortcuts)
                    otherAdapter.bindItems(
                            state.profileItem,
                            state.infoList,
                            state.menu,
                            state.bottomNavDuplicateIds,
                            state.bottomNavIds,
                            state.continueItems,
                            state.quickSettings,
                            quickSettingsSummary(),
                            state.hiddenBlocks
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shortcutEvents.collect { event ->
                    when (event) {
                        is OtherViewModel.ShortcutEvent.HistoryLoaded -> MenuShortcutDialogs.showHistoryPicker(
                                requireContext(),
                                event.items
                        ) { title, url -> addShortcut(title, url, event.section) }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                topicPreferencesHolder.observeCircleAvatarsFlow().collect {
                    val items = otherAdapter.items
                    val idx = items?.indexOfFirst { it is ProfileListItem } ?: -1
                    if (idx >= 0) otherAdapter.notifyItemChanged(idx)
                }
            }
        }
    }

    override fun isShadowVisible(): Boolean {
        return listScrollY != 0
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        syncBottomChromePadding()
    }

    override fun onPauseOrHide() {
        exitMenuEditMode()
        super.onPauseOrHide()
    }

    override fun onBackPressed(): Boolean {
        return if (otherAdapter.isMenuEditMode()) {
            exitMenuEditMode()
            true
        } else {
            super.onBackPressed()
        }
    }

    // Read-only зеркало onBackPressed: перехватываем «назад» в режиме
    // редактирования меню (см. hasBackHandling в TabFragment).
    override fun hasBackHandling(): Boolean = otherAdapter.isMenuEditMode()

    override fun onBottomChromePaddingChanged(padding: Int) {
        super.onBottomChromePaddingChanged(padding)
        bottomChromePadding = padding
        updateListBottomPadding()
    }

    private fun syncBottomChromePadding() {
        val rootInsets = ViewCompat.getRootWindowInsets(fragmentContainer)
        bottomChromePadding = BottomNavWindowInset.fragmentsBottomPaddingPx(
                baseTabBarPx = resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height),
                rootInsets = rootInsets,
                fallbackNavBottomPx = dimensionsProvider.getDimensions().navigationBar
        )
        updateListBottomPadding()
    }

    private fun updateListBottomPadding() {
        if (::listRecycler.isInitialized) {
            listRecycler.updatePadding(bottom = listBaseBottomPadding + bottomChromePadding)
        }
    }

    private fun invalidateTileSpacing() {
        if (!::listRecycler.isInitialized) return
        listRecycler.post {
            if (::listRecycler.isInitialized && !listRecycler.isComputingLayout) {
                listRecycler.invalidateItemDecorations()
            }
        }
    }

    private fun exitMenuEditMode() {
        if (otherAdapter.isMenuEditMode()) {
            otherAdapter.setEditMode(false)
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {}

    private val profileClickListener: (ProfileModel?) -> Unit = { _: ProfileModel? ->
        viewModel.onProfileClick()
    }

    private val menuClickListener = { item: DrawerMenuItem -> viewModel.onMenuClick(item.appItem) }

    private val exitClickListener = { requireActivity().finishAffinity() }

    private val editDoneClickListener: () -> Unit = { exitMenuEditMode() }

    private val editResetClickListener: () -> Unit = {
        exitMenuEditMode()
        viewModel.onResetMenuLayout()
        showSnackbar(getString(R.string.other_menu_layout_reset))
    }

    private val addShortcutClickListener: (OtherMenuSection) -> Unit = { section ->
        MenuShortcutDialogs.showSourceChooser(
                requireContext(),
                onHistory = { viewModel.onPickShortcutFromHistory(section) },
                onSearch = {
                    MenuShortcutDialogs.showSearchQueryInput(requireContext()) { title, url ->
                        addShortcut(title, url, section)
                    }
                },
                onLink = {
                    MenuShortcutDialogs.showLinkInput(
                            requireContext(),
                            onEntered = { title, url -> addShortcut(title, url, section) },
                            onInvalid = { showSnackbar(getString(R.string.other_menu_add_link_invalid)) }
                    )
                }
        )
    }

    private val removeShortcutClickListener: (DrawerMenuItem) -> Unit = { item ->
        item.appItem.shortcut?.let { shortcut ->
            viewModel.onRemoveShortcut(shortcut.id)
            showSnackbar(getString(R.string.other_menu_shortcut_removed))
        }
    }

    private val continueClickListener: (HistoryItem) -> Unit = { item -> viewModel.onContinueClick(item) }

    /**
     * Строка «Быстрые настройки» разворачивается в лист со значениями: ряд чипсов не показывал,
     * что именно сейчас выбрано, и занимал две строки экрана.
     */
    private val quickSettingsRowClickListener: () -> Unit = {
        QuickSettingsSheet.show(
                context = requireContext(),
                items = viewModel.uiState.value.quickSettings,
                valueProvider = ::quickSettingValue,
                onPick = quickSettingClickListener,
                onEditComposition = {
                    QuickSettingsPickerDialog.show(
                            requireContext(),
                            viewModel.uiState.value.quickSettings
                    ) { picked -> viewModel.onChangeQuickSettings(picked) }
                },
                onAllSettings = { startActivity(Intent(requireContext(), SettingsActivity::class.java)) },
        )
    }

    /** Сводка для строки меню: тема, шрифт и плотность — то, что меняют чаще всего. */
    private fun quickSettingsSummary(): String = listOfNotNull(
            quickSettingValue(QuickSetting.THEME),
            quickSettingValue(QuickSetting.FONT),
            quickSettingValue(QuickSetting.DENSITY),
    ).joinToString(" · ")

    /** Текущее значение настройки; null — у действий, у которых значения нет (ЧС, обновления). */
    private fun quickSettingValue(setting: QuickSetting): String? = when (setting) {
        QuickSetting.THEME -> getString(when (mainPreferencesHolder.getThemeMode()) {
            Preferences.Main.ThemeMode.LIGHT -> R.string.pref_value_theme_mode_light
            Preferences.Main.ThemeMode.DARK -> R.string.pref_value_theme_mode_dark
            Preferences.Main.ThemeMode.AMOLED -> R.string.pref_value_theme_mode_amoled
            Preferences.Main.ThemeMode.SYSTEM_AMOLED -> R.string.pref_value_theme_mode_system_amoled
            else -> R.string.pref_value_theme_mode_system
        })
        QuickSetting.PALETTE -> getString(when (mainPreferencesHolder.getUiPalette()) {
            Preferences.Main.UiPalette.SEPIA_READING -> R.string.pref_value_ui_palette_sepia_reading
            Preferences.Main.UiPalette.SEPIA_BLUE -> R.string.pref_value_ui_palette_sepia_blue
            Preferences.Main.UiPalette.MINIMAL_READER -> R.string.pref_value_ui_palette_minimal_reader
            Preferences.Main.UiPalette.GREEN_CARE -> R.string.pref_value_ui_palette_green_care
            Preferences.Main.UiPalette.NORD -> R.string.pref_value_ui_palette_nord
            Preferences.Main.UiPalette.SOLARIZED -> R.string.pref_value_ui_palette_solarized
            Preferences.Main.UiPalette.GRUVBOX -> R.string.pref_value_ui_palette_gruvbox
            Preferences.Main.UiPalette.ROSE_PINE -> R.string.pref_value_ui_palette_rose_pine
            Preferences.Main.UiPalette.DRACULA -> R.string.pref_value_ui_palette_dracula
            else -> R.string.pref_value_ui_palette_system
        })
        QuickSetting.ACCENT -> getString(
                AccentPickerDialog.titleRes(mainPreferencesHolder.getAccentPalette()))
        QuickSetting.FONT -> getString(when (FontController.getCurrentFontMode(mainPreferencesHolder)) {
            forpdateam.ru.forpda.ui.AppFontMode.ROBOTO -> R.string.pref_value_app_font_roboto
            forpdateam.ru.forpda.ui.AppFontMode.INTER -> R.string.pref_value_app_font_inter
            forpdateam.ru.forpda.ui.AppFontMode.SOURCE_SANS_3 -> R.string.pref_value_app_font_source_sans_3
            forpdateam.ru.forpda.ui.AppFontMode.OPEN_SANS -> R.string.pref_value_app_font_open_sans
            forpdateam.ru.forpda.ui.AppFontMode.IBM_PLEX_SANS -> R.string.pref_value_app_font_ibm_plex_sans
            forpdateam.ru.forpda.ui.AppFontMode.GOLOS_TEXT -> R.string.pref_value_app_font_golos_text
            forpdateam.ru.forpda.ui.AppFontMode.LITERATA -> R.string.pref_value_app_font_literata
            forpdateam.ru.forpda.ui.AppFontMode.APPETITE_PRO -> R.string.pref_value_app_font_appetite_pro
            forpdateam.ru.forpda.ui.AppFontMode.MAYONEZ_ITALIC -> R.string.pref_value_app_font_mayonez_italic
            forpdateam.ru.forpda.ui.AppFontMode.ROBOTO_MONO -> R.string.pref_value_app_font_roboto_mono
            else -> R.string.pref_value_app_font_system
        })
        QuickSetting.DENSITY -> getString(densityLabel(mainPreferencesHolder.getTopicPostDensity()))
        QuickSetting.PAGINATION -> getString(paginationLabel(mainPreferencesHolder.getTopicPaginationPanels()))
        QuickSetting.BLACKLIST, QuickSetting.NOTIFICATIONS, QuickSetting.UPDATE -> null
    }

    // Действия те же, что в настройках: смена темы перезапускает приложение, палитра/акцент/шрифт
    // пересоздают активити, тумблеры применяются мгновенно.
    private val quickSettingClickListener: (QuickSetting) -> Unit = { setting ->
        when (setting) {
            QuickSetting.THEME -> showQuickThemePicker()
            QuickSetting.PALETTE -> showQuickPalettePicker()
            QuickSetting.ACCENT -> showQuickAccentPicker()
            QuickSetting.FONT -> showQuickFontPicker()
            QuickSetting.DENSITY -> showQuickDensityPicker()
            QuickSetting.PAGINATION -> showQuickPaginationPicker()
            QuickSetting.BLACKLIST -> viewModel.onOpenForumBlackList()
            QuickSetting.NOTIFICATIONS -> openNotificationsSettings()
            QuickSetting.UPDATE -> runManualAppUpdateCheck(appUpdateRepository)
        }
    }

    // «Уведомления» — тот же экран настроек уведомлений, что открывается из основных настроек
    // (SettingsFragment → open_notifications): SettingsActivity с preference-экраном.
    private fun openNotificationsSettings() {
        val intent = Intent(requireContext(), SettingsActivity::class.java)
        intent.putExtra(
                SettingsActivity.ARG_NEW_PREFERENCE_SCREEN,
                NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME
        )
        startActivity(intent)
    }

    private val blockVisibilityClickListener: (OtherMenuBlock) -> Unit = { block ->
        viewModel.onToggleBlockHidden(block)
    }

    private val blockConfigureClickListener: (OtherMenuBlock) -> Unit = { block ->
        if (block == OtherMenuBlock.QUICK_SETTINGS) {
            QuickSettingsPickerDialog.show(
                    requireContext(),
                    viewModel.uiState.value.quickSettings
            ) { picked -> viewModel.onChangeQuickSettings(picked) }
        }
    }

    private fun showQuickThemePicker() {
        ThemeModePickerDialog.show(requireContext(), mainPreferencesHolder.getThemeMode()) { mode ->
            lifecycleScope.launch {
                val ctx = requireContext().applicationContext
                mainPreferencesHolder.setThemeMode(mode)
                DayNightHelper.applyTheme(mode)
                val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (intent != null) {
                    ctx.startActivity(intent)
                    activity?.finishAffinity()
                }
            }
        }
    }

    private fun showQuickPalettePicker() {
        PalettePickerDialog.show(requireContext(), mainPreferencesHolder.getUiPalette()) { palette ->
            lifecycleScope.launch {
                mainPreferencesHolder.setUiPalette(palette)
                activity?.recreate()
            }
        }
    }

    private fun showQuickAccentPicker() {
        AccentPickerDialog.show(
                requireContext(),
                mainPreferencesHolder.getAccentPalette(),
                mainPreferencesHolder.getAccentCustomColor(),
                mainPreferencesHolder.getAccentStyle()
        ) { picked, customColor, style ->
            if (!isAdded) return@show
            lifecycleScope.launch {
                if (picked == Preferences.Main.AccentPalette.CUSTOM && customColor != null) {
                    mainPreferencesHolder.setAccentCustomColor(customColor)
                }
                mainPreferencesHolder.setAccentStyle(style)
                mainPreferencesHolder.setAccentPalette(picked)
                activity?.recreate()
            }
        }
    }

    private fun showQuickDensityPicker() {
        val values = listOf(
                Preferences.Main.TopicPostDensity.COMFORTABLE,
                Preferences.Main.TopicPostDensity.COMPACT,
                Preferences.Main.TopicPostDensity.SUPER_COMPACT
        )
        val labels = values.map { getString(densityLabel(it)) }.toTypedArray()
        val current = values.indexOf(mainPreferencesHolder.getTopicPostDensity()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_title_topic_post_density)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    lifecycleScope.launch { mainPreferencesHolder.setTopicPostDensity(values[which]) }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun densityLabel(density: Preferences.Main.TopicPostDensity): Int = when (density) {
        Preferences.Main.TopicPostDensity.COMFORTABLE -> R.string.pref_value_topic_post_density_comfortable
        Preferences.Main.TopicPostDensity.COMPACT -> R.string.pref_value_topic_post_density_compact
        Preferences.Main.TopicPostDensity.SUPER_COMPACT -> R.string.pref_value_topic_post_density_super_compact
    }

    /**
     * Панель страниц: в гибридном режиме прокрутки нижняя панель недоступна (её место занимает
     * бесконечная лента), поэтому список вариантов там короче — как в настройках.
     */
    private fun showQuickPaginationPicker() {
        lifecycleScope.launch {
            val classic = mainPreferencesHolder.getTopicScrollMode() == Preferences.Main.TopicScrollMode.CLASSIC
            val current = mainPreferencesHolder.getTopicPaginationPanels()
            val values = if (classic) {
                listOf(
                        Preferences.Main.TopicPaginationPanels.NONE,
                        Preferences.Main.TopicPaginationPanels.TOP,
                        Preferences.Main.TopicPaginationPanels.BOTTOM,
                        Preferences.Main.TopicPaginationPanels.BOTH
                )
            } else {
                listOf(
                        Preferences.Main.TopicPaginationPanels.NONE,
                        Preferences.Main.TopicPaginationPanels.TOP
                )
            }
            val labels = values.map { getString(paginationLabel(it)) }.toTypedArray()
            val checkedIndex = if (classic) {
                values.indexOf(current).coerceAtLeast(0)
            } else {
                values.indexOf(
                        if (current.hasTop) Preferences.Main.TopicPaginationPanels.TOP
                        else Preferences.Main.TopicPaginationPanels.NONE
                ).coerceAtLeast(0)
            }
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.pref_title_topic_pagination_panel)
                    .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                        val picked = values[which]
                        val merged = if (classic) picked else current.withTop(picked.hasTop)
                        lifecycleScope.launch { mainPreferencesHolder.setTopicPaginationPanels(merged) }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showWithStyledButtons()
        }
    }

    private fun paginationLabel(panels: Preferences.Main.TopicPaginationPanels): Int = when (panels) {
        Preferences.Main.TopicPaginationPanels.NONE -> R.string.pref_value_topic_pagination_panels_none
        Preferences.Main.TopicPaginationPanels.TOP -> R.string.pref_value_topic_pagination_panels_top
        Preferences.Main.TopicPaginationPanels.BOTTOM -> R.string.pref_value_topic_pagination_panels_bottom
        Preferences.Main.TopicPaginationPanels.BOTH -> R.string.pref_value_topic_pagination_panels_both
    }

    private fun showQuickFontPicker() {
        FontPickerDialog.show(requireContext(), FontController.getCurrentFontMode(mainPreferencesHolder)) { mode ->
            lifecycleScope.launch {
                mainPreferencesHolder.setAppFontMode(mode)
                activity?.recreate()
            }
        }
    }

    private fun addShortcut(title: String, url: String, section: OtherMenuSection) {
        viewModel.onAddShortcut(MenuShortcutDialogs.typeOf(url), title, url, section)
        showSnackbar(getString(R.string.other_menu_shortcut_added))
    }

    private val infoCloseClickListener = { item: CloseableInfo -> viewModel.onCloseInfo(item) }

    companion object {
        private const val MENU_GRID_SPAN_COUNT = 12
    }

}
