package forpdateam.ru.forpda.presentation

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.github.terrakok.cicerone.Router
import forpdateam.ru.forpda.App

/**
 * Обертка над Cicerone 6.x с прежними именами методов для приложения.
 */
class TabRouter : Router() {

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
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(App.getContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    fun exitWithResult(key: String, data: Any) {
        sendResult(key, data)
        exit()
    }
}
