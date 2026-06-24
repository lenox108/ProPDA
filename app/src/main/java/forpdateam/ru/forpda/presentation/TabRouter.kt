package forpdateam.ru.forpda.presentation

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.github.terrakok.cicerone.Router
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Обертка над Cicerone 6.x с прежними именами методов для приложения.
 *
 * The Toast helpers use the Hilt-provided [appScope] (process-wide, [kotlinx.coroutines.SupervisorJob])
 * so we do not create a per-instance [kotlinx.coroutines.MainScope] that would leak when the
 * router outlives the current UI (e.g. after a configuration change).
 *
 * [appScope] is provided on [kotlinx.coroutines.Dispatchers.Default] (see
 * [forpdateam.ru.forpda.common.di.AppCoroutineModule]); Android UI APIs
 * (Toast, View, etc.) require the main looper, so the helpers below hop to
 * [Dispatchers.Main] before touching the framework.
 */
open class TabRouter(
    private val context: Context,
    private val appScope: CoroutineScope,
) : Router() {

    fun newScreenChain(screen: Screen) {
        newRootChain(screen)
    }

    fun navigateTo(screen: Screen) {
        super.navigateTo(screen, clearContainer = true)
    }

    fun backTo(screen: Screen) {
        super.backTo(screen)
    }

    fun replaceScreen(screen: Screen) {
        super.replaceScreen(screen)
    }

    fun newRootScreen(screen: Screen) {
        super.newRootScreen(screen)
    }

    /**
     * Push a new [Screen.Theme] as a top-level tab so the system back stack
     * is updated even if the in-tab history of the current theme is corrupted.
     *
     * Returns false if the [url] is not a topic URL we can extract a topic id
     * from (the caller should fall back to an in-tab load in that case).
     *
     * Used by Fix E for cross-topic in-topic links: when a user taps a link
     * inside a theme that points to a different topic, we want the next back
     * press to leave the theme tab, not bounce around inside it.
     */
    fun tryOpenTopicInNewTab(url: String, sourceScreen: String): Boolean {
        val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: return false
        val screen = Screen.Theme().apply {
            themeUrl = url
            topicOpenSource = sourceScreen
            topicOpenIntent = TopicOpenIntentClassifier.freshIntentForSource(sourceScreen)
        }
        navigateTo(screen)
        return true
    }

    fun showSystemMessage(message: String) {
        appScope.launch {
            withContext(Dispatchers.Main) {
                showToast(message)
            }
        }
    }

    fun showSystemMessage(@StringRes messageId: Int) {
        appScope.launch {
            withContext(Dispatchers.Main) {
                showToast(context.getString(messageId))
            }
        }
    }

    /**
     * Dispatch point for the actual Toast. Extracted so tests can subclass
     * [TabRouter] and capture the active [kotlinx.coroutines.CoroutineDispatcher]
     * when the system call would fire — proving the work runs on
     * [Dispatchers.Main] even when [appScope] is provided on Default.
     */
    protected open suspend fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun exitWithResult(key: String, data: Any) {
        sendResult(key, data)
        exit()
    }

    /**
     * Kept for binary/source compatibility with previous callers. No-op: the
     * injected [appScope] is the shared process-wide scope, owned by Hilt, and
     * must not be cancelled from here.
     */
    @Suppress("unused")
    fun cleanup() {
        // intentionally empty
    }
}
