package forpdateam.ru.forpda.ui.fragments

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class BaseJsInterface {
    private val scope = CoroutineScope(Dispatchers.Main)
    protected fun runInUiThread(runnable: Runnable) = scope.launch { runnable.run() }

    /** Отменяет scope. Вызывать владельцем при destroy (например, в onDestroyView/onDestroy). */
    fun cancel() {
        scope.cancel()
    }
}