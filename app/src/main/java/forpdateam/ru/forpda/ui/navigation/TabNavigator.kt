package forpdateam.ru.forpda.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import android.util.Log
import com.jakewharton.rxrelay2.BehaviorRelay
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.activities.WebVewNotFoundActivity
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.activities.updatechecker.UpdateCheckerActivity
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
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
    }

    private val fragmentManager by lazy { activity.supportFragmentManager }
    val tabController by lazy { TabController() }
    private val compositeDisposable = CompositeDisposable()
    private val schedulers = App.get().Di().schedulers

    private val subscribers = mutableListOf<TabFragment>()
    private val subscribersRelay = BehaviorRelay.createDefault(subscribers as List<TabFragment>)

    init {

    }

    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.getString("tab_controller_json")?.also {
            Log.e("TabController", "restore tab_controller_json: $it")
            tabController.onRestoreInstanceState(JSONObject(it))
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        tabController.onSaveInstanceState().toString().also {
            Log.e("TabController", "save tab_controller_json: $it")
            outState.putString("tab_controller_json", it)
        }
    }

    fun subscribe(tab: TabFragment) {
        Log.e("TabNavigator", "subscribe $tab")
        subscribers.add(tab)
        subscribersRelay.accept(subscribers)
    }

    fun unsubscribe(tab: TabFragment) {
        Log.e("TabNavigator", "unsubscribe $tab")
        subscribers.remove(tab)
        subscribersRelay.accept(subscribers)
    }

    fun notifyUpdate(tab: TabFragment) {
        Log.e("TabNavigator", "notifyUpdate $tab")
        subscribersRelay.accept(subscribers)
    }

    fun observeSubscribers(): Observable<List<TabFragment>> = subscribersRelay
            .subscribeOn(schedulers.io())
            .observeOn(schedulers.ui())

    fun getCurrentFragment(): TabFragment? {
        return tabController.getCurrent()?.let {
            getByTag(it.tag)
        }
    }

    fun select(tabTag: String?) {
        if (tabTag == null) {
            Log.e("TabNavigator", "select CANCEL: tabTag==null")
            return
        }
        Log.e("TabNavigator", "select tag=$tabTag fr=${getByTag(tabTag)}")
        tabController.setCurrent(tabTag)
        updateFragmentsState()
    }

    fun close(tabTag: String?) {
        if (tabTag == null) {
            Log.e("TabNavigator", "close CANCEL: tabTag==null")
            return
        }
        val fragment = getByTag(tabTag)
        Log.e("TabNavigator", "close tag=$tabTag fr=$fragment")
        if (tabController.getList().size <= 1) {
            exit()
        } else {
            fragmentManager
                    .beginTransaction()
                    .remove(fragment!!)
                    .commit()
            // Иначе remove ещё не применён — show предыдущей вкладки в updateFragmentsState даёт пустой экран.
            fragmentManager.executePendingTransactions()
            tabController.remove(tabTag)
            updateFragmentsState()
            notifyThemeFragmentAfterChildRemoved()
        }
    }

    fun closeOthers() {
        val transaction = fragmentManager.beginTransaction()
        val itemTags = tabController.getList().map { it.tag }.filter { it != tabController.getCurrent()?.tag }
        Log.e("TabNavigator", "closeOthers")
        itemTags.forEach { itemTag ->
            getByTag(itemTag)?.also { fragment ->
                Log.e("TabNavigator", "closeOthers item=${itemTag} fr=$fragment")
                transaction.remove(fragment)
                tabController.remove(itemTag)
            }
        }
        transaction.commit()
        fragmentManager.executePendingTransactions()
        updateFragmentsState()
    }

    private fun updateFragmentsState() {
        /*tabController.getCurrent()?.tag?.let { getByTag(it) }?.also { fragment ->
            fragmentManager
                    .beginTransaction()
                    .show(fragment)
                    .commit()
        }*/
        tabController.printTabItems("TabNavigator")
        Log.e("TabNavigator", "updateFragmentsState")
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

        transaction.commit()
        fragmentManager.executePendingTransactions()
        notifyCurrentThemeTabJumpToUnread()
        subscribersRelay.accept(subscribers)
    }

    /** После каждого hide/show стека — getnewpost для текущей темы (в т.ч. повторный тап по той же вкладке). */
    private fun notifyCurrentThemeTabJumpToUnread() {
        (getCurrentFragment() as? ThemeFragmentWeb)?.onTabStackBecameCurrent()
    }

    private fun getByTag(tag: String): TabFragment? {
        val result = fragmentManager.findFragmentByTag(tag) as TabFragment?
        Log.e("TabNavigator", "getByTag tag=$tag, tab=${tabController.getCurrent()?.tag}, fr=$result")
        return result
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
        createActivityIntent(activity, newScreen.screenKey, newScreen)?.also {
            checkAndStartActivity(newScreen.screenKey, it)
            return
        }

        if (reuseExistingThemeTabForSpecificPost(newScreen)) {
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

            Log.e("TabNavigator", "forward f=$newFragment")
            fragmentManager
                    .beginTransaction()
                    .add(containerId, newFragment, tag)
                    .commit()
            fragmentManager.executePendingTransactions()
            tabController.addNew(tag, newScreen)
            updateFragmentsState()
        }
    }

    private fun back() {
        if (tabController.getList().size <= 1) {
            exit()
        } else {
            tabController.getCurrent()?.also { tab ->
                val fragment = getByTag(tab.tag)

                Log.e("TabNavigator", "back f=$fragment")
                fragmentManager
                        .beginTransaction()
                        .remove(fragment!!)
                        .commit()
                fragmentManager.executePendingTransactions()
                tabController.remove(tab.tag)
                updateFragmentsState()
                notifyThemeFragmentAfterChildRemoved()
            }
        }
    }

    private fun replace(command: Replace) {
        val newScreen = command.screen as Screen
        createActivityIntent(activity, newScreen.screenKey, newScreen)?.also {
            checkAndStartActivity(newScreen.screenKey, it)
            activity.finish()
            return
        }

        tabController.findAlone(newScreen)?.also {
            val currentTag = tabController.getCurrent()?.tag.orEmpty()
            if (it.tag != currentTag) {
                val fragment = getByTag(currentTag)
                fragmentManager
                        .beginTransaction()
                        .remove(fragment!!)
                        .commit()
                fragmentManager.executePendingTransactions()
                tabController.remove(currentTag)
                tabController.setCurrent(it.tag)
                updateFragmentsState()
                return
            }
        }

        val newFragment = createFragment(newScreen.screenKey, newScreen)
        if (newFragment != null) {
            val tag = genTag()
            val fragment = getByTag(tabController.getCurrent()?.tag.orEmpty())
            Log.e("TabNavigator", "replace nf=$newFragment, of=$fragment")
            fragmentManager
                    .beginTransaction()
                    .remove(fragment!!)
                    .add(containerId, newFragment, tag)
                    .commit()
            fragmentManager.executePendingTransactions()
            tabController.replace(tag, newScreen)
            updateFragmentsState()
        }
    }

    private fun backTo(command: BackTo) {
        val key = command.screen?.screenKey
                ?: tabController.getList().firstOrNull()?.screen?.key
                ?: return
        val tagsRemove = tabController.backTo(key)
        val transaction = fragmentManager.beginTransaction()
        Log.e("TabNavigator", "backTo tags=${tagsRemove.size}")
        tagsRemove.forEach {
            val fragment = getByTag(it)
            Log.e("TabNavigator", "backTo remove t=$fragment")
            transaction.remove(fragment!!)
        }
        transaction.commit()
        fragmentManager.executePendingTransactions()
        updateFragmentsState()
        notifyThemeFragmentAfterChildRemoved()
    }

    /**
     * После remove() «верхней» вкладки снова показывается родитель; у WebView темы контент часто
     * пустой до ручного refresh — дублируем то же действие, что и pull-to-refresh.
     */
    private fun notifyThemeFragmentAfterChildRemoved() {
        fragmentManager.executePendingTransactions()
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
            is Screen.UpdateChecker ->
                Intent(context, UpdateCheckerActivity::class.java)
            is Screen.WebViewNotFound ->
                Intent(context, WebVewNotFoundActivity::class.java)
            is Screen.ImageViewer ->
                Intent(context, ImageViewerActivity::class.java).apply {
                    putExtra(ImageViewerActivity.IMAGE_URLS_KEY, ArrayList<String>(screen.urls))
                    putExtra(ImageViewerActivity.SELECTED_INDEX_KEY, screen.selected)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

            themeFr.loadThemeUrlFromNavigator(url)
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
}