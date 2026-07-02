package forpdateam.ru.forpda.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.diagnostic.NavBackstackTrace
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.activities.WebVewNotFoundActivity
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.fragments.search.SearchFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.qms.chat.QmsChatFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import com.github.terrakok.cicerone.Back
import com.github.terrakok.cicerone.BackTo
import com.github.terrakok.cicerone.Command
import com.github.terrakok.cicerone.Forward
import com.github.terrakok.cicerone.Navigator
import com.github.terrakok.cicerone.Replace

class TabNavigator(
        private val activity: androidx.fragment.app.FragmentActivity,
        private val containerId: Int
) : Navigator {
    companion object {
        private const val TAG_PREFIX = "Tab_"

        /** Направление enter-анимации показываемого фрагмента (M3 shared-axis Z). */
        private const val ENTER_NEUTRAL = 0   // смена вкладки/прочее → кроссфейд
        private const val ENTER_FORWARD = 1   // вглубь (forward/replace) → входящий растёт
        private const val ENTER_BACK = -1     // назад (back/backTo) → входящий сжимается
    }

    /**
     * Тег вкладки, показанной в прошлый раз. Enter-анимация проигрывается ТОЛЬКО
     * когда текущая вкладка реально сменилась (не при повторном show той же), и
     * не при первом появлении/восстановлении (lastCurrentTag == null).
     */
    private var lastCurrentTag: String? = null

    private val fragmentManager by lazy { activity.supportFragmentManager }
    val tabController by lazy { TabController() }

    private val subscribersMap = mutableMapOf<String, TabFragment>()
    private val _subscribersFlow = MutableStateFlow<List<TabFragment>>(emptyList())
    val subscribersFlow: StateFlow<List<TabFragment>> = _subscribersFlow.asStateFlow()
    
    private fun updateSubscribersFlow() {
        _subscribersFlow.value = subscribersMap.values.toList()
    }

    init {
        syncExistingFragments()
    }

    fun syncSubscribers() {
        syncExistingFragments()
    }

    private fun syncExistingFragments() {
        val allFragments = fragmentManager.fragments
        val existingFragments = allFragments.filterIsInstance<TabFragment>()
        existingFragments.forEach { fragment ->
            val tag = fragment.tag ?: return@forEach
            if (!subscribersMap.containsKey(tag)) {
                subscribersMap[tag] = fragment
            }
        }
        updateSubscribersFlow()
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.getString("tab_controller_json")?.also {
            tabController.onRestoreInstanceState(JSONObject(it))
            restoreSubscribers()
        }
    }

    private fun restoreSubscribers() {
        subscribersMap.clear()
        tabController.getList().forEach { tabItem ->
            val fragment = getByTag(tabItem.tag)
            if (fragment != null) {
                subscribersMap[tabItem.tag] = fragment
            }
        }
        updateSubscribersFlow()
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab_controller_json", tabController.onSaveInstanceState().toString())
    }

    fun subscribe(tab: TabFragment) {
        val tag = tab.tag ?: return
        subscribersMap[tag] = tab
        updateSubscribersFlow()
    }

    fun unsubscribe(tab: TabFragment) {
        val tag = tab.tag ?: return
        subscribersMap.remove(tag)
        updateSubscribersFlow()
    }

    fun notifyUpdate(tab: TabFragment) {
        val tag = tab.tag ?: return
        subscribersMap[tag] = tab
        updateSubscribersFlow()
    }

    fun getCurrentFragment(): TabFragment? {
        return tabController.getCurrent()?.let {
            getByTag(it.tag)
        }
    }

    fun select(tabTag: String?) {
        if (tabTag == null) return
        tabController.setCurrent(tabTag)
        updateFragmentsState()
    }

    fun selectOpenedTab(tabTag: String?) {
        if (tabTag == null || tabController.isCurrent(tabTag)) return
        tabController.setCurrent(tabTag)
        updateFragmentsState(notifyThemeTabBecameCurrent = false)
    }

    fun selectParentOf(tabTag: String?): Boolean {
        val parentTag = tabController.getParentTag(tabTag) ?: return false
        tabController.setCurrent(parentTag)
        updateFragmentsState()
        return true
    }

    fun close(tabTag: String?) {
        if (tabTag == null) return
        val fragment = getByTag(tabTag)
        if (tabController.getList().size <= 1) {
            exit()
        } else if (fragment != null && fragment.isAdded) {
            fragmentManager
                    .beginTransaction()
                    .remove(fragment)
                    .commit()
            tabController.remove(tabTag)
            // Обновляем subscribers для drawer
            subscribersMap.remove(tabTag)
            updateSubscribersFlow()
            updateFragmentsState()
            notifyThemeFragmentAfterChildRemoved()
        }
    }

    fun closeThemeChainToOrigin(tabTag: String?): Boolean {
        if (tabTag == null) return false
        val tagsRemove = tabController.getThemeChainTagsToOrigin(tabTag)
        if (tagsRemove.isEmpty()) return false
        val transaction = fragmentManager.beginTransaction()
        tagsRemove.forEach { tag ->
            getByTag(tag)?.also { fragment ->
                transaction.remove(fragment)
            }
            subscribersMap.remove(tag)
        }
        transaction.commitNow()
        tabController.removeThemeChainToOrigin(tabTag)
        updateSubscribersFlow()
        updateFragmentsState()
        return true
    }

    fun canCloseThemeChainToOrigin(tabTag: String?): Boolean =
            tabController.getThemeChainTagsToOrigin(tabTag).isNotEmpty()

    fun closeOthers() {
        val transaction = fragmentManager.beginTransaction()
        val itemTags = tabController.getList().map { it.tag }.filter { it != tabController.getCurrent()?.tag }
        itemTags.forEach { itemTag ->
            getByTag(itemTag)?.also { fragment ->
                transaction.remove(fragment)
                tabController.remove(itemTag)
                // Обновляем subscribers для drawer
                subscribersMap.remove(itemTag)
            }
        }
        transaction.commit()
        updateSubscribersFlow()
        updateFragmentsState()
    }

    /**
     * M3 shared-axis-Z enter-переход для показываемого фрагмента. Анимируется
     * ТОЛЬКО view контента (тулбар/нижний бар — вне контейнера, не затрагиваются),
     * поэтому семантика навигации (commitNow/show-hide) не меняется.
     *
     * ThemeFragmentWeb — только alpha-кроссфейд, без масштаба: у WebView-темы
     * тонкая логика scroll/anchor/highlight ([[theme-bare-scrollto-animates]] и
     * др.), которую нельзя тревожить трансформами.
     */
    private fun animateFragmentEnter(fragment: TabFragment?, direction: Int) {
        val view: View = fragment?.view ?: return
        view.animate().cancel()
        val fadeOnly = fragment is ThemeFragmentWeb || direction == ENTER_NEUTRAL
        view.alpha = 0f
        if (fadeOnly) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.animate()
                    .alpha(1f)
                    .setDuration(if (fragment is ThemeFragmentWeb) 160L else 200L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction { view.alpha = 1f }
                    .start()
            return
        }
        val from = if (direction == ENTER_FORWARD) 0.92f else 1.06f
        view.scaleX = from
        view.scaleY = from
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250L)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
                .start()
    }

    private fun updateFragmentsState(
            notifyThemeTabBecameCurrent: Boolean = true,
            enterDirection: Int = ENTER_NEUTRAL
    ) {
        /*tabController.getCurrent()?.tag?.let { getByTag(it) }?.also { fragment ->
            fragmentManager
                    .beginTransaction()
                    .show(fragment)
                    .commit()
        }*/
        val transaction = fragmentManager.beginTransaction()

        val itemFragments = tabController.getList().map { Pair(it, getByTag(it.tag)) }

        itemFragments.forEach {
            if (it.first.tag != tabController.getCurrent()?.tag) {
                it.second?.also { fragment -> transaction.hide(fragment) }
            }
        }
        itemFragments.forEach {
            if (it.first.tag == tabController.getCurrent()?.tag) {
                it.second?.also { fragment -> transaction.show(fragment) }
            }
        }

        transaction.commitNow()
        val currentTag = tabController.getCurrent()?.tag
        // Анимируем только реальную смену вкладки; не при первом появлении/восстановлении.
        if (currentTag != null && lastCurrentTag != null && currentTag != lastCurrentTag) {
            animateFragmentEnter(getByTag(currentTag), enterDirection)
        }
        lastCurrentTag = currentTag
        if (notifyThemeTabBecameCurrent) {
            notifyCurrentThemeTabJumpToUnread()
        }
        updateSubscribersFlow()
    }

    /** После каждого hide/show стека — getnewpost для текущей темы (в т.ч. повторный тап по той же вкладке). */
    private fun notifyCurrentThemeTabJumpToUnread() {
        (getCurrentFragment() as? ThemeFragmentWeb)?.onTabStackBecameCurrent()
    }

    private fun getByTag(tag: String): TabFragment? {
        return fragmentManager.findFragmentByTag(tag) as TabFragment?
    }

    private fun genTag() = TAG_PREFIX + System.currentTimeMillis()

    override fun applyCommands(commands: Array<out Command>) {
        fragmentManager.executePendingTransactions()
        commands.forEach {
            applyCommand(it)
        }
    }

    private fun applyCommand(command: Command) {
        when (command) {
            is Forward -> forward(command)
            is Back -> back()
            is Replace -> replace(command)
            is BackTo -> backTo(command)
        }
    }

    private fun forward(command: Forward) {
        val newScreen = command.screen as Screen
        NavBackstackTrace.log(
                event = "forward",
                navigator = "TabNavigator",
                command = "Forward",
                tabCount = tabController.getList().size,
                screenKey = newScreen.screenKey,
                topicId = (newScreen as? Screen.Theme)?.themeUrl?.let {
                    forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi.extractTopicIdFromUrl(it)
                }
        )
        logQmsNavigation("forward", newScreen)
        createActivityIntent(activity, newScreen.screenKey, newScreen)?.also {
            checkAndStartActivity(newScreen.screenKey, it)
            return
        }

        if (reuseExistingThemeTabForSameTopicFreshOpen(newScreen)) {
            return
        }
        if (reuseExistingThemeTabForSpecificPost(newScreen)) {
            return
        }

        if (activateAloneThemeTabIfPresent(newScreen)) {
            return
        }

        if (activateAloneQmsChatTabIfPresent(newScreen)) {
            return
        }

        tabController.findAlone(newScreen)?.also {
            tabController.setCurrent(it.tag)
            updateFragmentsState()
            return
        }

        val newFragment = createFragment(newScreen.screenKey, newScreen)
        if (newFragment != null) {
            val tag = genTag()
            fragmentManager
                    .beginTransaction()
                    .add(containerId, newFragment, tag)
                    .commitNow()
            tabController.addNew(tag, newScreen)
            updateFragmentsState(enterDirection = ENTER_FORWARD)
        }
    }

    private fun back() {
        NavBackstackTrace.log(
                event = "back",
                navigator = "TabNavigator",
                command = "Back",
                tabCount = tabController.getList().size
        )
        if (tabController.getList().size <= 1) {
            exit()
        } else {
            tabController.getCurrent()?.also { tab ->
                val fragment = getByTag(tab.tag)
                if (fragment != null) {
                    fragmentManager
                            .beginTransaction()
                            .remove(fragment)
                            .commitNow()
                    tabController.remove(tab.tag)
                    updateFragmentsState(enterDirection = ENTER_BACK)
                    notifyThemeFragmentAfterChildRemoved()
                }
            }
        }
    }

    private fun replace(command: Replace) {
        val newScreen = command.screen as Screen
        logQmsNavigation("replace", newScreen)
        createActivityIntent(activity, newScreen.screenKey, newScreen)?.also {
            checkAndStartActivity(newScreen.screenKey, it)
            activity.finish()
            return
        }

        if (activateAloneQmsChatTabIfPresent(newScreen)) {
            return
        }

        tabController.findAlone(newScreen)?.also {
            val currentTag = tabController.getCurrent()?.tag.orEmpty()
            if (it.tag != currentTag) {
                val fragment = getByTag(currentTag)
                if (fragment != null) {
                    fragmentManager
                            .beginTransaction()
                            .remove(fragment)
                            .commitNow()
                    tabController.remove(currentTag)
                    tabController.setCurrent(it.tag)
                    updateFragmentsState()
                    return
                }
            }
        }

        val newFragment = createFragment(newScreen.screenKey, newScreen)
        if (newFragment != null) {
            val tag = genTag()
            val fragment = getByTag(tabController.getCurrent()?.tag.orEmpty())
            fragmentManager
                    .beginTransaction()
                    .apply {
                        if (fragment != null) remove(fragment)
                    }
                    .add(containerId, newFragment, tag)
                    .commitNow()
            tabController.replace(tag, newScreen)
            updateFragmentsState(enterDirection = ENTER_FORWARD)
        }
    }

    private fun backTo(command: BackTo) {
        val key = command.screen?.screenKey
                ?: tabController.getList().firstOrNull()?.screen?.key
                ?: return
        val tagsRemove = tabController.backTo(key)
        val transaction = fragmentManager.beginTransaction()
        tagsRemove.forEach {
            val fragment = getByTag(it)
            if (fragment != null) {
                transaction.remove(fragment)
            }
        }
        transaction.commit()
        updateFragmentsState(enterDirection = ENTER_BACK)
        notifyThemeFragmentAfterChildRemoved()
    }

    /**
     * После remove() «верхней» вкладки снова показывается родитель; у WebView темы контент часто
     * пустой до ручного refresh — дублируем то же действие, что и pull-to-refresh.
     */
    private fun notifyThemeFragmentAfterChildRemoved() {
        val current = tabController.getCurrent() ?: return
        val fragment = getByTag(current.tag) ?: return
        (fragment as? ThemeFragmentWeb)?.onRestoredAfterChildFragmentRemoved()
    }


    fun exit() {
        activity.finish()
    }

    private fun createActivityIntent(context: Context?, screenKey: String?, data: Any?): Intent? {
        val screen = data as Screen
        return when (screen) {
            is Screen.Main ->
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.ARG_CHECK_WEBVIEW, screen.checkWebView)
                }
            is Screen.WebViewNotFound ->
                Intent(context, WebVewNotFoundActivity::class.java)
            is Screen.ImageViewer ->
                Intent(context, ImageViewerActivity::class.java).apply {
                    putExtra(ImageViewerActivity.IMAGE_URLS_KEY, ArrayList<String>(screen.urls))
                    putExtra(ImageViewerActivity.SELECTED_INDEX_KEY, screen.selected)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            is Screen.Settings ->
                Intent(context, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.ARG_NEW_PREFERENCE_SCREEN, screen.fragment)
                }
            else -> null
        }
    }

    private fun checkAndStartActivity(screenKey: String, activityIntent: Intent) {
        if (activityIntent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(activityIntent)
        }
    }

    private fun createFragment(screenKey: String?, data: Any?): androidx.fragment.app.Fragment? {
        return data?.let { TabHelper.createTab(it as Screen) }
    }

    /**
     * Из «Мои сообщения» / поиска: ссылка на конкретный пост в теме, которая уже открыта.
     * Без этого остаётся старая страница вкладки или срабатывает только getnewpost при фокусе.
     */
    private fun reuseExistingThemeTabForSpecificPost(screen: Screen): Boolean {
        if (screen !is Screen.Theme) return false
        val url = screen.themeUrl ?: return false
        val targetTopicId = extractShowTopicId(url) ?: return false
        if (!themeUrlTargetsSpecificPost(url)) return false

        for (item in tabController.getList()) {
            val themeFr = getByTag(item.tag) as? ThemeFragmentWeb ?: continue
            val openTopicId = themeFr.arguments?.getString(TabFragment.ARG_TAB)?.let { extractShowTopicId(it) }
                    ?: themeFr.getOpenTopicIdForReuse()
                    ?: continue
            if (openTopicId != targetTopicId) continue

            applyThemeScreenToFragment(themeFr, screen)
            themeFr.loadThemeUrlFromNavigator(
                    url = url,
                    sourceScreen = screen.topicOpenSource,
                    openIntent = screen.topicOpenIntent,
                    listHints = TabNavigatorThemeSwitchPolicy.listHintsFromThemeScreen(screen)
            )
            tabController.setCurrent(item.tag)
            updateFragmentsState()
            return true
        }
        return false
    }

    /**
     * If the same topic is already open in another tab, treat navigation as a fresh open
     * and force re-resolve the URL (e.g. apply LAST_UNREAD/getnewpost and suppress scroll restore).
     *
     * This prevents the "second open restores old scroll position" bug when the user re-opens
     * the same topic from a list while an old instance is still in the tab tree.
     */
    /**
     * [Screen.Theme] is [Screen.isAlone]: the second topic from a list must not only focus the
     * existing theme tab — it must reload URL, list unread hints, and reset hybrid pagination state.
     * Otherwise the WebView keeps topic A HTML and «В конец темы» uses stale [visibleCurrentPage].
     */
    /**
     * [Screen.QmsChat] is alone: re-opening a dialog must update ids and reload WebView HTML,
     * not only focus the existing tab (stale ViewModel + empty/stale WebView).
     */
    private fun activateAloneQmsChatTabIfPresent(screen: Screen): Boolean {
        if (screen !is Screen.QmsChat) return false
        val aloneTab = tabController.findAlone(screen) ?: return false
        val chatFr = getByTag(aloneTab.tag) as? QmsChatFragment ?: return false
        aloneTab.screen = TabScreen.fromScreen(screen)
        // Show the tab before WebView reload — hidden (GONE) fragments measure WebView at 0×0
        // and the QMS render pipeline can stall until the user leaves and re-enters the screen.
        tabController.setCurrent(aloneTab.tag)
        updateFragmentsState()
        chatFr.applyChatScreenFromNavigator(screen)
        FpdaDebugLog.logQms(
                FpdaDebugLog.QmsArea.CHAT,
                "alone_qms_chat_reuse",
                mapOf(
                        "tabTag" to aloneTab.tag,
                        "userId" to screen.userId,
                        "themeId" to screen.themeId
                )
        )
        return true
    }

    private fun activateAloneThemeTabIfPresent(screen: Screen): Boolean {
        if (screen !is Screen.Theme) return false
        // Prefer the CURRENT tab when it is a Theme tab: findThemeTab() returns the FIRST Theme tab
        // in the tree, which is wrong when multiple alone Theme tabs coexist (cross-topic opens).
        // Using the visible tab ensures openTopicId/source-anchor are computed from the active topic.
        val current = tabController.getCurrent()
        val aloneTab = current?.takeIf { it.screen?.key == Screen.Theme::class.java.simpleName }
                ?: tabController.findThemeTab()
                ?: return false
        val themeFr = getByTag(aloneTab.tag) as? ThemeFragmentWeb ?: return false
        val url = screen.themeUrl ?: return false
        val targetTopicId = extractShowTopicId(url)
        val openTopicId = themeFr.arguments?.getString(TabFragment.ARG_TAB)?.let { extractShowTopicId(it) }
                ?: themeFr.getOpenTopicIdForReuse()
        // Cross-topic navigation (e.g. an in-topic link to a DIFFERENT topic) must NOT reuse the
        // existing alone Theme tab: it would corrupt in-tab history with mixed topics and break
        // system back. Fall through to createFragment so a new tab is opened instead.
        if (TabNavigatorThemeSwitchPolicy.isCrossTopicFreshOpen(
                        targetTopicId = targetTopicId,
                        openTopicId = openTopicId,
                        openIntent = screen.topicOpenIntent
                )) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_TOPIC_SWITCH,
                    event = "alone_theme_skip_reuse_cross_topic",
                    fields = mapOf(
                            "targetTopicId" to targetTopicId,
                            "openTopicId" to openTopicId,
                            "source" to screen.topicOpenSource,
                            "intent" to screen.topicOpenIntent,
                            "tabTag" to aloneTab.tag
                    )
            )
            val newFragment = createFragment(screen.screenKey, screen)
            if (newFragment != null) {
                val tag = genTag()
                fragmentManager
                        .beginTransaction()
                        .add(containerId, newFragment, tag)
                        .commitNow()
                tabController.addNew(tag, screen)
                updateFragmentsState()
            }
            return true
        }
        if (TabNavigatorThemeSwitchPolicy.mustReloadAloneThemeOnNavigation(screen.topicOpenIntent)) {
            applyThemeScreenToFragment(themeFr, screen)
            themeFr.loadThemeUrlFromNavigator(
                    url = url,
                    sourceScreen = screen.topicOpenSource,
                    openIntent = screen.topicOpenIntent,
                    listHints = TabNavigatorThemeSwitchPolicy.listHintsFromThemeScreen(screen)
            )
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_TOPIC_SWITCH,
                    event = "alone_theme_fresh_reload",
                    fields = mapOf(
                            "targetTopicId" to targetTopicId,
                            "openTopicId" to openTopicId,
                            "source" to screen.topicOpenSource,
                            "tabTag" to aloneTab.tag
                    )
            )
        } else {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_TOPIC_SWITCH,
                    event = "alone_theme_focus_only",
                    fields = mapOf(
                            "targetTopicId" to targetTopicId,
                            "openTopicId" to openTopicId,
                            "intent" to screen.topicOpenIntent
                    )
            )
        }
        tabController.setCurrent(aloneTab.tag)
        updateFragmentsState()
        return true
    }

    private fun applyThemeScreenToFragment(themeFr: ThemeFragmentWeb, screen: Screen.Theme) {
        val url = screen.themeUrl ?: return
        themeFr.arguments?.apply {
            putString(TabFragment.ARG_TAB, url)
            screen.screenTitle?.let { putString(TabFragment.ARG_TITLE, it) }
            putString(Screen.Theme.ARG_TOPIC_OPEN_SOURCE, screen.topicOpenSource)
            putString(Screen.Theme.ARG_TOPIC_OPEN_INTENT, screen.topicOpenIntent)
            screen.unreadUrlFromList?.let { putString(Screen.Theme.ARG_UNREAD_URL_FROM_LIST, it) }
                    ?: remove(Screen.Theme.ARG_UNREAD_URL_FROM_LIST)
            screen.lastReadUrlFromList?.let { putString(Screen.Theme.ARG_LAST_READ_URL_FROM_LIST, it) }
                    ?: remove(Screen.Theme.ARG_LAST_READ_URL_FROM_LIST)
            if (screen.unreadPostIdFromList > 0) {
                putInt(Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST, screen.unreadPostIdFromList)
            } else {
                remove(Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST)
            }
            putBoolean(Screen.Theme.ARG_INSPECTOR_MARKED_UNREAD, screen.inspectorMarkedUnread)
        }
    }

    private fun reuseExistingThemeTabForSameTopicFreshOpen(screen: Screen): Boolean {
        if (getCurrentFragment() is SearchFragment) return false
        if (screen !is Screen.Theme) return false
        if (!TabNavigatorThemeSwitchPolicy.isFreshOpenForReuse(screen.topicOpenIntent)) return false
        val url = screen.themeUrl ?: return false
        val targetTopicId = extractShowTopicId(url) ?: return false

        for (item in tabController.getList()) {
            val themeFr = getByTag(item.tag) as? ThemeFragmentWeb ?: continue
            val openTopicId = themeFr.arguments?.getString(TabFragment.ARG_TAB)?.let { extractShowTopicId(it) }
                    ?: themeFr.getOpenTopicIdForReuse()
                    ?: continue
            if (openTopicId != targetTopicId) continue

            applyThemeScreenToFragment(themeFr, screen)
            themeFr.loadThemeUrlFromNavigator(
                    url = url,
                    sourceScreen = screen.topicOpenSource,
                    openIntent = screen.topicOpenIntent,
                    listHints = TabNavigatorThemeSwitchPolicy.listHintsFromThemeScreen(screen)
            )
            tabController.setCurrent(item.tag)
            updateFragmentsState()
            return true
        }
        return false
    }

    private fun extractShowTopicId(url: String): Int? =
            try {
                Uri.parse(url).getQueryParameter("showtopic")?.toIntOrNull()
            } catch (_: Exception) {
                null
            }

    private fun themeUrlTargetsSpecificPost(url: String): Boolean {
        val u = url.lowercase()
        if (u.contains("act=findpost")) return true
        if (u.contains("view=findpost")) return true
        if (Regex("[?&]pid=\\d+").containsMatchIn(u)) return true
        return false
    }

    private fun logQmsNavigation(command: String, screen: Screen) {
        if (screen !is Screen.QmsChat) return
        FpdaDebugLog.logQms(
                FpdaDebugLog.QmsArea.OPEN,
                "nav_$command",
                mapOf(
                        "userId" to screen.userId,
                        "themeId" to screen.themeId,
                        "screenTitle" to screen.screenTitle?.take(32),
                        "source" to "TabNavigator"
                )
        )
    }
}