package forpdateam.ru.forpda.ui.views.drawers

import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.View
import timber.log.Timber
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.MenuMapper
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.BottomMenuAdapter
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.navigation.TabHelper
import forpdateam.ru.forpda.ui.navigation.TabNavigator
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import forpdateam.ru.forpda.ui.tuneForListPerformance
import forpdateam.ru.forpda.ui.views.control.BottomSheetBehaviorFixed
import forpdateam.ru.forpda.ui.views.control.BottomSheetBehaviorRecyclerManager
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.drawers.adapters.TabTouchCallback
import forpdateam.ru.forpda.ui.views.drawers.adapters.TabAdapter
import forpdateam.ru.forpda.ui.views.drawers.adapters.TabRowItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.TabScreenIcons
import forpdateam.ru.forpda.common.showSnackbarAboveSystemBars
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.widget.PopupMenu
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import forpdateam.ru.forpda.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.min

class BottomDrawer(
        private val activity: androidx.fragment.app.FragmentActivity,
        private val binding: ActivityMainBinding,
        private val tabNavigator: TabNavigator,
        private val router: TabRouter,
        private val menuRepository: MenuRepository,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val listsPreferencesHolder: ListsPreferencesHolder,
        private val authHolder: AuthHolder,
        private val userHolder: IUserHolder,
        private val favoritesCache: forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom,
) {
    private val menuAdapter = BottomMenuAdapter(object : BottomMenuAdapter.Listener {
        override fun onTabClick(menu: DrawerMenuItem) {
            menu.appItem.let { item ->
                val screen = item.screen
                if (screen != null) {
                    if (screen.getKey() == tabNavigator.tabController.getCurrent()?.screen?.key) {
                        (tabNavigator.getCurrentFragment() as? TabTopScroller)?.toggleScrollTop()
                    }
                    router.navigateTo(screen)
                } else {
                    // Пункты без предопределённого Screen: «Мои сообщения» резолвится по nick пользователя.
                    handleMenuItemWithoutScreen(item)
                }
                menuRepository.setLastOpened(item.id)
            }
            hide()
            // Update selection after navigation
            updateCurrentSelection()
        }
    })

    private fun handleMenuItemWithoutScreen(item: AppMenuItem) {
        when (item.id) {
            MenuRepository.item_my_messages -> {
                if (!authHolder.get().isAuth()) {
                    router.navigateTo(Screen.Auth())
                    return
                }
                val nick = userHolder.user?.nick.orEmpty()
                if (nick.isEmpty()) {
                    router.navigateTo(Screen.Auth())
                    return
                }
                try {
                    val url = SearchSettings().apply {
                        source = SearchSettings.SOURCE_CONTENT.first
                        this.nick = nick
                        result = SearchSettings.RESULT_POSTS.first
                    }.toUrl()
                    router.navigateTo(Screen.Search().apply { searchUrl = url })
                } catch (e: Exception) {
                    Timber.w(e, "handleMenuItemWithoutScreen: nick encode failed")
                }
            }
        }
    }

    private var drawerListener: DrawerListener? = null

    private val tabsAdapter = TabAdapter(object : TabAdapter.Listener {
        override fun onTabClick(tag: String) {
            tabNavigator.selectOpenedTab(tag)
            hide()
        }

        override fun onTabClose(tag: String) {
            closeTab(tag)
        }

        override fun onTabMenu(tag: String, anchor: View) {
            showTabMenu(tag, anchor)
        }

        override fun onTabDragStart(holder: RecyclerView.ViewHolder) {
            tabsTouchHelper?.startDrag(holder)
        }
    })

    private var tabsTouchHelper: ItemTouchHelper? = null

    /** Режим ручной сортировки («Переместить» в меню вкладки): вместо крестиков — ручки. */
    private var reorderMode = false

    /** Фильтр списка вкладок; пустая строка — фильтра нет. */
    private var searchQuery: String = ""

    private var searchMode = false

    /** Закрытые в этой сессии вкладки для восстановления: заголовок + экран, которым открывали. */
    private val recentlyClosed = ArrayDeque<Pair<String, Screen>>()

    /** topicId → число новых сообщений; берётся из кэша избранного, без единого сетевого запроса. */
    private var unreadByTopic: Map<Int, Int> = emptyMap()

    /**
     * Список вкладок деревом переходов вместо плоского («Вкладки деревом переходов» в настройках).
     * В этом режиме порядок задаёт само дерево, поэтому перетаскивание выключено.
     */
    private var treeView = mainPreferencesHolder.getTabsTreeView()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    fun cleanup() {
        scope.cancel()
    }

    private val bottomSheetBehavior: BottomSheetBehaviorFixed<View>

    private var lastBottomNavInsetPx: Int = -1

    /** Секция «Открытые вкладки» не должна участвовать в высоте COLLAPSED (peek = панель + inset навбара). */
    private fun setOpenTabsSectionVisible(expanded: Boolean) {
        val v = if (expanded) View.VISIBLE else View.GONE
        binding.bottomMenuViewTabs.visibility = if (expanded && !searchMode) View.VISIBLE else View.GONE
        binding.bottomTabsSearchInput.visibility = if (expanded && searchMode) View.VISIBLE else View.GONE
        binding.bottomTabsRecycler.visibility = v
        binding.bottomTabsActions.visibility = v
    }

    private val otherMenuItem = MenuMapper.mapToDrawer(AppMenuItem(MenuRepository.item_other_menu, Screen.OtherMenu()))
    private var localItems = listOf(otherMenuItem)
    private var currentMenuItems: List<AppMenuItem> = emptyList()
    private var showFavoritesUnreadBadge = true

    init {
        binding.apply {
            val params = bottomSheet2.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            val behavior = params?.behavior as? BottomSheetBehaviorFixed<*>
                ?: throw IllegalStateException("View is not associated with BottomSheetBehaviorFixed")

            @Suppress("UNCHECKED_CAST")
            bottomSheetBehavior = behavior as BottomSheetBehaviorFixed<View>

            behavior.apply {
                isHideable = false
                state = BottomSheetBehaviorFixed.STATE_COLLAPSED
                val basePeek = activity.resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
                peekHeight = basePeek
                // Иначе max(peek, mandatoryGestureInset) даёт лишнюю «пустую» высоту поверх nav bar.
                gestureInsetBottomIgnored = true

                addBottomSheetCallback(object : BottomSheetBehaviorFixed.BottomSheetCallback() {
                    private val colorDrawable = ColorDrawable(Color.TRANSPARENT)

                    init {
                        bottomMenuFade.background = colorDrawable
                    }

                    private fun getColor(offset: Float) = Color.argb((96 * offset).toInt(), 0, 0, 0)

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        colorDrawable.color = getColor(slideOffset)
                        drawerListener?.onSlide(slideOffset)
                        bottomToggleArrow.rotationX = 180 * slideOffset
                    }

                    @SuppressLint("SwitchIntDef")
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        // Секция вкладок нужна уже в DRAGGING/SETTLING: иначе высота листа = peek,
                        // expandedOffset совпадает с collapsed — анимации нет (жест и show()).
                        setOpenTabsSectionVisible(newState != BottomSheetBehaviorFixed.STATE_COLLAPSED)
                        bottomMenuContainer.requestLayout()

                        when (newState) {
                            BottomSheetBehaviorFixed.STATE_EXPANDED -> {
                                colorDrawable.color = getColor(1.0f)
                                bottomMenuContainer.setOnClickListener {
                                    hide()
                                }
                                bottomMenuContainer.isClickable = true
                                drawerListener?.onShow()
                            }
                            BottomSheetBehaviorFixed.STATE_COLLAPSED -> {
                                colorDrawable.color = Color.TRANSPARENT
                                bottomMenuContainer.setOnClickListener(null)
                                bottomMenuContainer.isClickable = false
                                drawerListener?.onHide()
                            }
                        }
                    }
                })
            }

            bottomSheet2.apply {
                clipToOutline = false
                // Панель = полотно ChromeCanvas — ТОТ ЖЕ тон, что «плоская» верхняя шапка
                // и фон страниц: под Material You (SYSTEM light/dark) это динамический тон
                // обоев, вне MY fallback = colorSurfaceContainerLowest (== background_base
                // во всех статических палитрах) — прежнее поведение. Низ и верх держатся
                // на одном источнике и не могут разойтись цветом.
                background = ColorDrawable(activity.chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
                ViewCompat.setElevation(this, 0f)
                ViewCompat.setTranslationZ(this, 0f)
            }

            ViewCompat.setOnApplyWindowInsetsListener(bottomSheet2) { _, insets ->
                this@BottomDrawer.syncBottomChromeWithInsets(insets)
                insets
            }
            bottomSheet2.post { ViewCompat.requestApplyInsets(bottomSheet2) }

            bottomToggleArrow.setOnClickListener {
                toggle()
            }
            updateArrowVisible(mainPreferencesHolder.getShowBottomArrow())

            bottomMenuRecycler.apply {
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, mainPreferencesHolder.getBottomNavColumns())
                adapter = menuAdapter
                isNestedScrollingEnabled = false
                tuneForListPerformance()
            }

            val manager = BottomSheetBehaviorRecyclerManager(bottomMenuContainer, bottomSheetBehavior, bottomSheet2)
            manager.addControl(bottomTabsRecycler)
            manager.create()

            bottomTabsCloseOthers.setOnClickListener { removeAllTabs() }
            bottomTabsDone.setOnClickListener { setReorderMode(false) }
            bottomTabsReorder.setOnClickListener { setReorderMode(true) }
            bottomTabsNew.setOnClickListener { anchor -> showNewTabMenu(anchor) }
            bottomTabsSearch.setOnClickListener { setSearchMode(!searchMode) }
            bottomTabsSearchInput.addTextChangedListener(
                    onTextChanged = { text, _, _, _ ->
                        searchQuery = text?.toString().orEmpty()
                        submitTabs(tabNavigator.currentTabs)
                    })

            bottomTabsRecycler.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                adapter = tabsAdapter
                tuneForListPerformance()
                // Анимация «изменения» строки конфликтует со свайпом: после свайпа влево (закрепить)
                // на месте строки оставалась пустая плашка — старая вью так и висела сдвинутой.
                (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
                        ?.supportsChangeAnimations = false

                val closeColor = context.getColorFromAttr(R.attr.item_tab_close_color)
                val pinColor = context.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)
                val touchCallback = object : TabTouchCallback(context, closeColor, pinColor) {
                    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                        val position = viewHolder.bindingAdapterPosition
                        val row = tabsAdapter.getItem(position) ?: return
                        if (direction == ItemTouchHelper.LEFT) {
                            // Свайп влево — закрепить/открепить: в отличие от закрытия строка остаётся
                            // в списке. ItemTouchHelper после доехавшего свайпа держит вью сдвинутой
                            // (ждёт, что элемент удалят), и одного notifyItemChanged мало — на месте
                            // строки оставалась пустая плашка. Переподключение хелпера сбрасывает его
                            // состояние вместе со сдвигом, после чего меняем закрепление.
                            resetSwipeState()
                            tabNavigator.setTabPinned(row.tag, !row.isPinned)
                            return
                        }
                        if (!closeTab(row.tag)) {
                            // Вкладка не закрылась — возвращаем строку на место, иначе она останется «уехавшей».
                            tabsAdapter.notifyItemChanged(position)
                        }
                    }

                    override fun getDragDirs(
                            recyclerView: androidx.recyclerview.widget.RecyclerView,
                            viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                    ): Int = if (reorderMode) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0

                    /** В режиме сортировки строка не должна закрываться свайпом из-под пальца. */
                    override fun getSwipeDirs(
                            recyclerView: androidx.recyclerview.widget.RecyclerView,
                            viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                    ): Int = if (reorderMode) 0 else ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT

                    override fun onRowMoved(from: Int, to: Int): Boolean {
                        if (!reorderMode) return false
                        tabsAdapter.moveRow(from, to)
                        return true
                    }

                    override fun onRowMoveFinished() {
                        tabNavigator.setTabOrder(tabsAdapter.currentRows().map { it.tag })
                        tabsAdapter.refreshRowPlates()
                    }
                }
                tabsTouchHelper = ItemTouchHelper(touchCallback).also { it.attachToRecyclerView(this) }
            }

            // Force refresh tabs when drawer opens
            bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehaviorFixed.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehaviorFixed.STATE_EXPANDED) {
                        tabNavigator.syncSubscribers()
                    }
                }
            })

            scope.launch {
                mainPreferencesHolder.observeShowBottomArrowFlow().collect {
                    updateArrowVisible(it)
                }
            }

            scope.launch {
                mainPreferencesHolder.observeTabsTreeViewFlow().collect { enabled ->
                    if (treeView == enabled) return@collect
                    treeView = enabled
                    submitTabs(tabNavigator.currentTabs)
                }
            }

            scope.launch {
                mainPreferencesHolder.observeBottomNavColumnsFlow().collect {
                    (binding.bottomMenuRecycler.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount = it
                    recalculateMenuItems()
                }
            }

            scope.launch {
                listsPreferencesHolder.observeFavShowUnreadBadgeFlow().collect {
                    showFavoritesUnreadBadge = it
                    recalculateMenuItems()
                }
            }

            scope.launch {
                menuRepository.observerMenu().collect { menu ->
                    menu[MenuRepository.group_main]?.let { newItems ->
                        currentMenuItems = newItems
                        rebuildLocalItems()
                    }
                    updateMenu()
                }
            }

            scope.launch {
                // Force sync and get initial value
                tabNavigator.syncSubscribers()
                tabNavigator.subscribersFlow.collect { tabs ->
                    submitTabs(tabs)
                }
            }

            // Метка новых сообщений берётся из кэша избранного — ровно как в «Истории»:
            // сетевая проба темы недопустима, GET страницы пометил бы её прочитанной.
            scope.launch {
                favoritesCache.ensureItemsPublished()
                favoritesCache.observeItems().collect { items ->
                    unreadByTopic = items
                            .filter { it.isUnreadForDisplay() }
                            .associate { it.topicId to it.unreadPostCount.coerceAtLeast(1) }
                    submitTabs(tabNavigator.currentTabs)
                }
            }
        }
    }

    /**
     * Peek листа и высота [bottomMenuRecycler] = [R.dimen.bottom_nav_tab_bar_height] + navigationBars.bottom.
     * Вызывается из insets bottom sheet и из [MainActivity.updateDimens] с тем же root window insets.
     */
    fun syncBottomChromeWithInsets(windowInsets: WindowInsetsCompat?) {
        val basePeekPx = activity.resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
        val nav = BottomNavWindowInset.navigationBarsBottomPx(windowInsets)
        if (nav == lastBottomNavInsetPx) return
        lastBottomNavInsetPx = nav
        bottomSheetBehavior.setPeekHeight(basePeekPx + nav, false)
        binding.bottomMenuRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = basePeekPx + nav
        }
        binding.bottomMenuRecycler.updatePadding(bottom = nav)
    }

    private fun recalculateMenuItems() {
        if (currentMenuItems.isEmpty()) return
        rebuildLocalItems()
        updateMenu()
    }

    private fun rebuildLocalItems() {
        val columns = mainPreferencesHolder.getBottomNavColumns()
        val mainItems = currentMenuItems
                .filter { it.id != MenuRepository.item_auth }
                .take(min(currentMenuItems.size, columns - 1))
                .map { item -> MenuMapper.mapToDrawer(item.forBottomNavBadge()) }
        val notExistMainCounters = currentMenuItems
                .filterNot { newItem ->
                    mainItems.indexOfFirst { newItem.id == it.appItem.id } >= 0
                }
                .map { it.forBottomNavBadge() }
                .filter { it.count > 0 }
        otherMenuItem.appItem.count = notExistMainCounters.sumOf { it.count }
        localItems = mainItems.plusElement(otherMenuItem)
    }

    private fun AppMenuItem.forBottomNavBadge(): AppMenuItem {
        if (id != MenuRepository.item_favorites || showFavoritesUnreadBadge) return this
        return AppMenuItem(id, screen).also { it.count = 0 }
    }

    private fun updateMenu() {
        val columns = mainPreferencesHolder.getBottomNavColumns()
        (binding.bottomMenuRecycler.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount = columns
        localItems.forEach { item ->
            Timber.d(
                    "BottomBadge",
                    "BottomDrawer.updateMenu id=${item.appItem.id} title=${activity.getString(item.title)} count=${item.appItem.count}"
            )
        }
        menuAdapter.bindItems(localItems)
        // Force update selection after menu update
        updateCurrentSelection()
    }

    private fun updateCurrentSelection() {
        tabNavigator.getCurrentFragment()?.let { currentFragment ->
            val screen = TabHelper.findScreenByFragment(currentFragment)
            findMenuItem(screen)?.also {
                val screenKey = screen.simpleName
                menuAdapter.setSelected(screenKey)
            }
        }
    }

    private fun updateArrowVisible(isVisible: Boolean) {
        binding.bottomToggleArrow.visibility = if (isVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    fun setListener(listener: DrawerListener) {
        drawerListener = listener
    }

    fun isShown() = bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED

    fun show() {
        if (isShown()) return
        // До layout с полной высотой (иконки + «Открытые вкладки») behavior считает from == to.
        setOpenTabsSectionVisible(true)
        binding.bottomMenuContainer.requestLayout()
        binding.bottomSheet2.requestLayout()
        binding.bottomSheet2.doOnLayout {
            if (!isShown()) {
                bottomSheetBehavior.setState(BottomSheetBehaviorFixed.STATE_EXPANDED)
                updateCurrentSelection()
            }
        }
    }

    fun hide() {
        setReorderMode(false)
        setSearchMode(false)
        bottomSheetBehavior.setState(BottomSheetBehaviorFixed.STATE_COLLAPSED)
    }

    /**
     * Режим ручной сортировки. Кнопка внизу превращается в «Готово», крестики — в ручки;
     * выход из режима — по кнопке или при закрытии шторки.
     */
    private fun setReorderMode(enabled: Boolean) {
        if (reorderMode == enabled) return
        if (enabled) setSearchMode(false)
        reorderMode = enabled
        tabsAdapter.reorderMode = enabled
        binding.apply {
            bottomTabsDone.visibility = if (enabled) View.VISIBLE else View.GONE
            bottomTabsNew.visibility = if (enabled) View.GONE else View.VISIBLE
            bottomTabsReorder.visibility = if (enabled) View.GONE else View.VISIBLE
            bottomTabsCloseOthers.visibility = if (enabled) View.GONE else View.VISIBLE
        }
        submitTabs(tabNavigator.currentTabs)
    }

    fun toggle() {
        if (isShown()) {
            hide()
        } else {
            show()
        }
    }

    /* Очень странная хрень с этими onstop|onpause - когда переходишь в браузер по ссылке,
    или просто открывается intentchoser и ты скрываешь приложение, то не обновляется список
    фрагментов. Прям вот вызывается notify... но ничего не происходит */
    fun onStop() {
        binding.bottomTabsRecycler.layoutManager = null
    }

    fun onStart() {
        binding.bottomTabsRecycler.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun findMenuItem(appMenuId: Int): DrawerMenuItem? {
        for (item in localItems) {
            if (item.appItem.id == appMenuId)
                return item
        }
        return null
    }

    private fun findMenuItem(classObject: Class<out Screen>): DrawerMenuItem? {
        for (item in localItems) {
            if (item.appItem.screen?.javaClass == classObject)
                return item
        }
        return null
    }

    private fun removeAllTabs() {
        MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.ask_close_other_tabs)
                .setPositiveButton(R.string.ok) { _, _ ->
                    tabNavigator.closeOthers()
                    hide()
                }
                .setNegativeButton(R.string.no, null)
                .showWithStyledButtons()
    }

    /** Строки списка вкладок + счётчик в заголовке секции. */
    private fun submitTabs(tabs: List<TabFragment>) {
        val rows = buildRows(tabs)
        val query = searchQuery.trim()
        val visible = if (query.isEmpty()) rows else rows.filter { it.matches(query) }
        tabsAdapter.submitRows(visible)
        binding.bottomMenuViewTabs.text = if (rows.isEmpty()) {
            activity.getString(R.string.bottom_nav_open_tabs_section)
        } else {
            activity.getString(R.string.bottom_nav_open_tabs_section_count, rows.size)
        }
        // Искать в списке из трёх строк незачем — кнопка появляется, когда вкладок реально много.
        binding.bottomTabsSearch.visibility =
                if (!reorderMode && (rows.size > SEARCH_THRESHOLD || searchMode)) View.VISIBLE else View.GONE
    }

    private fun TabRowItem.matches(query: String): Boolean =
            title.contains(query, ignoreCase = true) || subtitle?.contains(query, ignoreCase = true) == true

    /**
     * Поиск подменяет заголовок секции полем ввода: отдельной строки под него в шторке нет,
     * а заголовок в этот момент всё равно не нужен.
     */
    private fun setSearchMode(enabled: Boolean) {
        if (searchMode == enabled) return
        searchMode = enabled
        binding.bottomMenuViewTabs.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.bottomTabsSearchInput.apply {
            visibility = if (enabled) View.VISIBLE else View.GONE
            if (enabled) {
                requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            } else {
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(windowToken, 0)
                setText("")
            }
        }
        binding.bottomTabsSearch.setColorFilter(
                activity.getColorFromAttr(
                        if (enabled) androidx.appcompat.R.attr.colorPrimary
                        else com.google.android.material.R.attr.colorOnSurfaceVariant))
        if (!enabled) {
            searchQuery = ""
            submitTabs(tabNavigator.currentTabs)
        }
    }

    /**
     * Сброс состояния свайпа: [ItemTouchHelper] при отключении доигрывает все свои анимации и
     * возвращает вью на место. Нужен для свайпов, после которых строка остаётся в списке.
     */
    private fun resetSwipeState() {
        tabsTouchHelper?.attachToRecyclerView(null)
        tabsTouchHelper?.attachToRecyclerView(binding.bottomTabsRecycler)
    }

    /** Кнопка «+»: новая вкладка и восстановление недавно закрытых. */
    private fun showNewTabMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menu.add(R.string.tab_action_new).setOnMenuItemClickListener {
            // Экран разделов — единая точка входа: из него открывается что угодно.
            router.navigateTo(Screen.OtherMenu().apply { forceNewTab = true })
            hide()
            true
        }
        if (recentlyClosed.isNotEmpty()) {
            popup.menu.add(R.string.tab_recently_closed).apply {
                isEnabled = false
            }
            recentlyClosed.reversed().forEach { (title, screen) ->
                popup.menu.add(title).setOnMenuItemClickListener {
                    recentlyClosed.removeAll { it.second === screen }
                    restoreTab(screen)
                    true
                }
            }
        }
        popup.show()
    }

    private fun restoreTab(screen: Screen) {
        // Восстановление — явный запрос новой вкладки, поэтому в обход правил переиспользования.
        screen.forceNewTab = true
        router.navigateTo(screen)
    }

    private fun buildRows(tabs: List<TabFragment>): List<TabRowItem> {
        val controller = tabNavigator.tabController
        val currentTag = tabNavigator.getCurrentFragment()?.tag
        // В режиме дерева порядок строк задаёт само дерево (обход в глубину), иначе отступы
        // «поехали» бы: потомок мог оказаться выше своего родителя.
        val ordered = if (treeView) orderByTree(tabs) else tabs
        return ordered.mapNotNull { fragment ->
            val tag = fragment.tag ?: return@mapNotNull null
            val screenKey = controller.getScreenKey(tag)
            val sectionRes = TabScreenIcons.sectionTitleFor(screenKey)
            val section = if (sectionRes != 0) activity.getString(sectionRes) else null
            // Заголовок приезжает вместе с загруженной страницей; пока его нет (вкладка открыта по
            // ссылке «в новой вкладке»), строка не должна быть пустой — показываем раздел.
            val title = fragment.getTabTitle().asRowText().stripKindPrefix()
                    .ifBlank { section ?: activity.getString(R.string.tab_title_unknown) }
            TabRowItem(
                    tag = tag,
                    title = title,
                    subtitle = buildSubtitle(
                            section = section,
                            detail = fragment.getTabSubtitle()?.asRowText()?.takeIf { it.isNotBlank() },
                            title = title,
                    ),
                    iconRes = TabScreenIcons.iconFor(screenKey),
                    isActive = tag == currentTag,
                    isPinned = controller.isPinned(tag),
                    unreadCount = unreadCountFor(controller.getOrigin(tag)),
                    depth = controller.getDepth(tag),
                    showTree = treeView,
            )
        }
    }

    /** Порядок обхода дерева переходов; вкладки, которых в дереве нет, — в хвост. */
    private fun orderByTree(tabs: List<TabFragment>): List<TabFragment> {
        val byTag = tabs.mapNotNull { fragment -> fragment.tag?.let { it to fragment } }.toMap()
        val treeTags = tabNavigator.tabController.getList().map { it.tag }
        val ordered = treeTags.mapNotNull { byTag[it] }
        val known = treeTags.toHashSet()
        return ordered + tabs.filter { it.tag !in known }
    }

    /**
     * Текст фрагмента как строка списка: подзаголовок темы содержит `ImageSpan` (значок-глаз счётчика
     * читающих), и в plain-тексте от него остаётся служебный символ-заполнитель с лишними пробелами.
     */
    private fun String.asRowText(): String =
            replace('￼', ' ').replace(Regex("\\s+"), " ").trim()

    /**
     * Заголовки вкладок приходят обёрнутыми в тип: «Тема "…"», «Новость "…"». В списке тип и так
     * виден по иконке и подзаголовку, а обёртка съедает ширину — на длинных названиях именно она
     * вытесняла сам заголовок в многоточие.
     */
    private fun String.stripKindPrefix(): String =
            KIND_PREFIX_REGEX.find(this)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { this }

    /** Новых сообщений в теме по кэшу избранного; 0 — метки нет. */
    private fun unreadCountFor(origin: Screen?): Int {
        val topicId = (origin as? Screen.Theme)?.themeUrl?.let {
            forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi.extractTopicIdFromUrl(it)
        } ?: return 0
        return unreadByTopic[topicId] ?: 0
    }

    /** «Раздел · подробности». Раздел не повторяем, если он и так стоит заголовком строки. */
    private fun buildSubtitle(section: String?, detail: String?, title: String): String? {
        val head = section?.takeIf { !title.equals(it, ignoreCase = true) }
        return when {
            head != null && detail != null -> "$head · $detail"
            head != null -> head
            else -> detail
        }
    }

    /**
     * Закрытие одной вкладки — со снэкбаром «Отменить»: раньше промах по крестику или случайный
     * свайп уносили вкладку безвозвратно. Восстанавливаем тем же [Screen], которым вкладку открыли
     * ([forpdateam.ru.forpda.ui.navigation.TabItem.origin]); после пересоздания процесса его нет —
     * тогда просто закрываем без предложения отмены.
     */
    private fun closeTab(tag: String): Boolean {
        val closingTitle = tabsAdapter.currentRows().firstOrNull { it.tag == tag }?.title
        val origin = tabNavigator.close(tag)
        val closed = !tabNavigator.isTabOpen(tag)
        if (closed && origin != null) {
            // Снэкбар живёт пару секунд, поэтому та же вкладка остаётся доступной в меню кнопки «+».
            recentlyClosed.addLast((closingTitle ?: activity.getString(R.string.tab_title_unknown)) to origin)
            while (recentlyClosed.size > RECENTLY_CLOSED_LIMIT) recentlyClosed.removeFirst()
            binding.bottomSheet2.showSnackbarAboveSystemBars(
                    activity.getString(R.string.tab_closed_message),
                    Snackbar.LENGTH_LONG,
            ) {
                setAction(R.string.msg_panel_undo) {
                    // Восстановление — это явный запрос новой вкладки, поэтому в обход правил переиспользования.
                    origin.forceNewTab = true
                    router.navigateTo(origin)
                }
            }
        }
        return closed
    }

    private fun showTabMenu(tag: String, anchor: View) {
        val rows = tabsAdapter.currentRows()
        val index = rows.indexOfFirst { it.tag == tag }
        val popup = PopupMenu(anchor.context, anchor)

        val pinned = tabNavigator.isTabPinned(tag)
        popup.menu.add(if (pinned) R.string.tab_menu_unpin else R.string.tab_menu_pin)
                .setOnMenuItemClickListener {
                    tabNavigator.setTabPinned(tag, !pinned)
                    true
                }

        // Порядок в дереве задаёт само дерево, руками его двигать нечего.
        if (!treeView && rows.size > 1) {
            popup.menu.add(R.string.tab_menu_reorder).setOnMenuItemClickListener {
                setReorderMode(true)
                true
            }
        }

        tabNavigator.tabController.getOrigin(tag)?.tabUrl()?.also { url ->
            popup.menu.add(R.string.tab_menu_copy_link).setOnMenuItemClickListener {
                copyToClipboard(url)
                true
            }
        }

        popup.menu.add(R.string.tab_menu_close).setOnMenuItemClickListener {
            closeTab(tag)
            true
        }

        if (tabNavigator.canCloseThemeChainToOrigin(tag)) {
            popup.menu.add(R.string.tab_menu_close_branch).setOnMenuItemClickListener {
                tabNavigator.closeThemeChainToOrigin(tag)
                true
            }
        }

        if (index >= 0 && index < rows.size - 1) {
            popup.menu.add(R.string.tab_menu_close_below).setOnMenuItemClickListener {
                tabNavigator.closeTabs(rows.drop(index + 1).map { it.tag })
                true
            }
        }

        if (rows.size > 1) {
            popup.menu.add(R.string.close_other_tabs).setOnMenuItemClickListener {
                removeAllTabs()
                true
            }
        }

        popup.show()
    }

    /** Ссылка на содержимое вкладки, если экран её знает (иначе пункта меню нет). */
    private fun Screen.tabUrl(): String? = when (this) {
        is Screen.Theme -> themeUrl
        is Screen.ArticleDetail -> articleUrl
        is Screen.Profile -> profileUrl
        is Screen.Search -> searchUrl
        is Screen.SiteUserContent -> url
        is Screen.Reputation -> reputationUrl
        else -> null
    }?.takeIf { it.startsWith("http") }

    private fun copyToClipboard(url: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(url, url))
        binding.bottomSheet2.showSnackbarAboveSystemBars(R.string.link_copied)
    }

    interface DrawerListener {
        fun onHide()
        fun onShow()
        fun onSlide(slideOffset: Float)
    }

    private companion object {
        /** Ниже этого числа вкладок кнопка поиска не показывается. */
        const val SEARCH_THRESHOLD = 6
        const val RECENTLY_CLOSED_LIMIT = 5

        /** Пауза перед перестановкой закреплённой строки: даём доиграть возврату после свайпа. */
        const val PIN_REORDER_DELAY_MS = 250L

        /** «Тема "Название"», «Новость "Заголовок"» — тип и кавычки срезаются, остаётся название. */
        val KIND_PREFIX_REGEX = Regex("^\\p{L}[\\p{L} ]{0,20}\"(.+)\"$", RegexOption.DOT_MATCHES_ALL)
    }
}
