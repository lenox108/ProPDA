package forpdateam.ru.forpda.presentation

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Обертка над Cicerone 6.x с прежними именами методов для приложения.
 *
 * The Toast helpers use the Hilt-provided [appScope] (process-wide, [kotlinx.coroutines.SupervisorJob])
 * so we do not create a per-instance [kotlinx.coroutines.MainScope] that would leak when the
 * router outlives the current UI (e.g. after a configuration change).
 */
class TabRouter(
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

    fun showSystemMessage(message: String) {
        appScope.launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showSystemMessage(@StringRes messageId: Int) {
        appScope.launch {
            Toast.makeText(context, context.getString(messageId), Toast.LENGTH_SHORT).show()
        }
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
