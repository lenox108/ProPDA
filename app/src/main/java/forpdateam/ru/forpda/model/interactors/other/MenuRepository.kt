package forpdateam.ru.forpda.model.interactors.other

import android.content.SharedPreferences
import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.stringFlow
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.common.MessageCounters
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.presentation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MenuRepository(
        private val preferences: SharedPreferences,
        private val authHolder: AuthHolder,
        private val countersHolder: CountersHolder,
        private val listsPreferencesHolder: ListsPreferencesHolder
) {

    companion object {

        private const val KEY_MENU_SEQUENCE = "menu_items_sequence"

        const val group_main = 10
        const val group_system = 20

        const val item_auth = 110
        const val item_article_list = 120
        const val item_favorites = 130
        const val item_qms_contacts = 140
        const val item_mentions = 150
        const val item_my_messages = 155
        const val item_dev_db = 160
        const val item_forum = 170
        const val item_search = 180
        const val item_downloads = 185
        const val item_history = 190
        const val item_notes = 200
        const val item_forum_rules = 210
        const val item_settings = 220

        const val item_other_menu = 230

        /** Без item_auth: пункта «Вход» нет в [allItems], иначе getMenuItem падает и ломает порядок меню. */
        val GROUP_MAIN = arrayOf(
                item_article_list,
                item_favorites,
                item_qms_contacts,
                // Поиск вернули в нижнее меню (пользовательская настройка порядка).
                item_search,
                item_downloads,
                item_mentions,
                item_my_messages,
                item_forum,
                item_dev_db,
                item_history,
                item_notes
        )

        val GROUP_SYSTEM = arrayOf(
                item_settings
        )
    }

    private val allItems = listOf(
            //AppMenuItem(item_auth, Screen.Auth()),
            AppMenuItem(item_article_list, Screen.ArticleList()),
            AppMenuItem(item_favorites, Screen.Favorites()),
            AppMenuItem(item_qms_contacts, Screen.QmsContacts()),
            AppMenuItem(item_mentions, Screen.Mentions()),
            AppMenuItem(item_dev_db, Screen.DevDbBrands()),
            AppMenuItem(item_forum, Screen.Forum()),
            AppMenuItem(item_search, Screen.Search()),
            AppMenuItem(item_downloads, Screen.Downloads()),
            AppMenuItem(item_history, Screen.History()),
            AppMenuItem(item_notes, Screen.Notes()),
            AppMenuItem(item_settings, Screen.Settings()),
            AppMenuItem(item_my_messages)
    )

    private val mainGroupSequence = mutableListOf<Int>()

    private val blockedMenu = mutableListOf<Int>()

    private val blockUnAuth = listOf(
            item_favorites,
            item_qms_contacts,
            item_mentions,
            item_my_messages
    )

    private val blockAuth = listOf(
            item_auth
    )

    private val mainMenu = mutableListOf<AppMenuItem>()
    private val systemMenu = mutableListOf<AppMenuItem>()

    private val menuRelay = MutableStateFlow<Map<Int, List<AppMenuItem>>>(emptyMap())

    private val menuScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var localCounters = MessageCounters()
    private var showFavoritesUnreadBadge = listsPreferencesHolder.getFavShowUnreadBadge()

    init {
        allItems.forEach { it.screen?.fromMenu = true }

        loadMainMenuGroup()

        menuScope.launch {
            preferences.stringFlow(KEY_MENU_SEQUENCE, "").collect {
                if (BuildConfig.DEBUG) Timber.d("menuSequence pref change")
                loadMainMenuGroup()
                updateMenuItems()
            }
        }

        menuScope.launch {
            authHolder.observe().collect {
                loadMainMenuGroup()
                if (BuildConfig.DEBUG) {
                    Timber.d("observe auth ${it.state}")
                }
                updateMenuItems()
            }
        }

        menuScope.launch {
            countersHolder.observe().collect { counters ->
                localCounters = MessageCounters().apply {
                    qms = counters.qms
                    favorites = counters.favorites
                    mentions = counters.mentions
                }
                updateMenuItems()
            }
        }
        menuScope.launch {
            listsPreferencesHolder.observeFavShowUnreadBadgeFlow().collect {
                showFavoritesUnreadBadge = it
                updateMenuItems()
            }
        }
        updateMenuItems()
    }

    private fun loadMainMenuGroup() {
        mainGroupSequence.clear()
        mainGroupSequence.addAll(GROUP_MAIN)

        val savedArray = preferences.getString(KEY_MENU_SEQUENCE, "") ?: ""
        if (savedArray.isNotEmpty()) {
            val array = savedArray.split(',').mapNotNull { it.toIntOrNull() }.filter { GROUP_MAIN.contains(it) }
            val newItems = GROUP_MAIN.filterNot { array.contains(it) }
            val finalArray = newItems.plus(array)
            mainGroupSequence.clear()
            mainGroupSequence.addAll(finalArray)
        }
    }

    fun observerMenu(): StateFlow<Map<Int, List<AppMenuItem>>> = menuRelay.asStateFlow()

    /** Текущий порядок группы главного меню (для экрана настройки нижней панели). */
    fun getMainMenuOrderForEdit(): List<AppMenuItem> =
            mainGroupSequence.mapNotNull { id -> allItems.firstOrNull { it.id == id } }

    fun setMainMenuSequence(items: List<AppMenuItem>) {
        val orderedIds = items.map { it.id }.filter { GROUP_MAIN.contains(it) }
        val missingIds = mainGroupSequence.filter { !orderedIds.contains(it) }
        mainGroupSequence.clear()
        mainGroupSequence.addAll(orderedIds + missingIds)
        preferences.edit()
                .putString(KEY_MENU_SEQUENCE, mainGroupSequence.joinToString(",") { it.toString() })
                .apply()
        updateMenuItems()
    }

    fun setLastOpened(id: Int) {
        if (GROUP_MAIN.indexOfFirst { it == id } >= 0) {
            preferences.edit().putInt("app_menu_last_id", id).apply()
        }
    }

    fun getLastOpened(): Int {
        val menuId = preferences.getInt("app_menu_last_id", -1)
        return if (GROUP_MAIN.indexOfFirst { it == menuId } >= 0) {
            menuId
        } else {
            -1
        }
    }

    fun getMenuItem(id: Int): AppMenuItem = allItems.first { it.id == id }

    fun menuItemContains(id: Int): Boolean = allItems.indexOfFirst { it.id == id } >= 0

    fun updateMenuItems() {
        mainMenu.clear()
        systemMenu.clear()

        allItems.firstOrNull { it.id == item_qms_contacts }?.count = localCounters.qms
        allItems.firstOrNull { it.id == item_mentions }?.count = localCounters.mentions
        allItems.firstOrNull { it.id == item_favorites }?.count = localCounters.favorites

        if (authHolder.get().isAuth()) {
            blockedMenu.addAll(blockAuth)
            blockedMenu.removeAll(blockUnAuth)
        } else {
            blockedMenu.addAll(blockUnAuth)
            blockedMenu.removeAll(blockAuth)
        }

        mainGroupSequence.forEach {
            if (!blockedMenu.contains(it) && menuItemContains(it)) {
                mainMenu.add(snapshot(getMenuItem(it)))
            }
        }

        GROUP_SYSTEM.forEach {
            if (!blockedMenu.contains(it) && menuItemContains(it)) {
                systemMenu.add(snapshot(getMenuItem(it)))
            }
        }

        menuRelay.value = mapOf(
                group_main to mainMenu.toList(),
                group_system to systemMenu.toList()
        )
    }

    /**
     * `menuRelay` is a `StateFlow` and emits only when the new value is not `==` to the old value.
     * Since [AppMenuItem] does not override equals/hashCode and its [AppMenuItem.count] is mutable,
     * rebuilding the map with the same item instances (same references) prevents emissions when only
     * counters change. We snapshot items so any counter change produces a new (non-equal) list.
     */
    private fun snapshot(item: AppMenuItem): AppMenuItem = AppMenuItem(item.id, item.screen).also {
        it.count = if (item.id == item_favorites && !showFavoritesUnreadBadge) 0 else item.count
    }

}