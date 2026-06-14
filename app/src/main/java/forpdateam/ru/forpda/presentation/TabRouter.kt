package forpdateam.ru.forpda.presentation

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Обертка над Cicerone 6.x с прежними именами методов для приложения.
 */
class TabRouter(private val context: Context) : Router() {

    private val mainScope = MainScope()

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
        mainScope.launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showSystemMessage(@StringRes messageId: Int) {
        mainScope.launch {
            Toast.makeText(context, context.getString(messageId), Toast.LENGTH_SHORT).show()
        }
    }

    fun exitWithResult(key: String, data: Any) {
        sendResult(key, data)
        exit()
    }

    /** Отменяет scope. Вызывать при завершении приложения (необязательно для @Singleton). */
    fun cleanup() {
        mainScope.cancel()
    }
}
