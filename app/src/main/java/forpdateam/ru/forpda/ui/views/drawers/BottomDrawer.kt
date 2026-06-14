package forpdateam.ru.forpda.ui.views.drawers

import forpdateam.ru.forpda.common.getColorFromAttr
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
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.control.BottomSheetBehaviorFixed
import forpdateam.ru.forpda.ui.views.control.BottomSheetBehaviorRecyclerManager
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.drawers.adapters.TabSwipeToDeleteCallback
import forpdateam.ru.forpda.ui.views.drawers.adapters.TabAdapter
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
        private val userHolder: IUserHolder
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

    private val tabsAdapter = TabAdapter()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    fun cleanup() {
        scope.cancel()
    }

    private val bottomSheetBehavior: BottomSheetBehaviorFixed<View>

    private var lastBottomNavInsetPx: Int = -1

    /** Секция «Открытые вкладки» не должна участвовать в высоте COLLAPSED (peek = панель + inset навбара). */
    private fun setOpenTabsSectionVisible(expanded: Boolean) {
        val v = if (expanded) View.VISIBLE else View.GONE
        binding.bottomMenuViewTabs.visibility = v
        binding.bottomTabsRecycler.visibility = v
        binding.bottomCloseAllTabs.visibility = v
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
                background = ColorDrawable(activity.getColorFromAttr(R.attr.background_for_lists))
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

            bottomCloseAllTabs.setOnClickListener {
                removeAllTabs()
            }

            bottomTabsRecycler.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                adapter = tabsAdapter
                tuneForListPerformance()

                val color = context.getColorFromAttr(R.attr.item_tab_close_color)
                val swipeHandler = object : TabSwipeToDeleteCallback(color) {
                    override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, p1: Int) {
                        val tab = tabsAdapter.getItem(viewHolder.bindingAdapterPosition)
                        tabNavigator.close(tab.tag)
                    }
                }
                val itemTouchHelper = ItemTouchHelper(swipeHandler)
                itemTouchHelper.attachToRecyclerView(this)
            }

            tabsAdapter.setItemClickListener(object : BaseAdapter.OnItemClickListener<TabFragment> {
                override fun onItemClick(item: TabFragment) {
                    tabNavigator.selectOpenedTab(item.tag)
                    hide()
                }

                override fun onItemLongClick(item: TabFragment): Boolean {
                    return false
                }
            })

            tabsAdapter.setCloseClickListener(object : BaseAdapter.OnItemClickListener<TabFragment> {
                override fun onItemClick(item: TabFragment) {
                    tabNavigator.close(item.tag)
                }

                override fun onItemLongClick(item: TabFragment): Boolean {
                    return false
                }
            })

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
                    tabsAdapter.submitTabs(tabs, tabNavigator.getCurrentFragment()?.tag)
                    binding.bottomTabsRecycler.requestLayout()
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
        bottomSheetBehavior.setState(BottomSheetBehaviorFixed.STATE_COLLAPSED)
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

    private fun selectMenuItem(item: DrawerMenuItem) {
        if (BuildConfig.DEBUG) Timber.d("selectMenuItem ${item.appItem.screen?.getKey()}")
        item.appItem.screen?.getKey()?.let { menuAdapter.setSelected(it) }
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

    interface DrawerListener {
        fun onHide()
        fun onShow()
        fun onSlide(slideOffset: Float)
    }
}
