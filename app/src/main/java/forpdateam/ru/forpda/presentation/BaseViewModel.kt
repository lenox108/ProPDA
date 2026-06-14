package forpdateam.ru.forpda.presentation

import androidx.lifecycle.ViewModel
import forpdateam.ru.forpda.BuildConfig
import io.appmetrica.analytics.AppMetrica
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * Базовый ViewModel c безопасным scope: любая необработанная ошибка
 * внутри [viewModelScope].launch { ... } не крашит приложение, а логируется.
 *
 * Поле [viewModelScope] намеренно перекрывает одноимённую extension-property
 * androidx.lifecycle.viewModelScope — все существующие call sites автоматически
 * получают общий CoroutineExceptionHandler без каких-либо правок.
 */
abstract class BaseViewModel : ViewModel() {

    private val job = SupervisorJob()

    protected open val coroutineExceptionHandler: CoroutineContext =
        CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Unhandled coroutine exception in ${this::class.java.simpleName}")
            if (!BuildConfig.DEBUG) {
                runCatching {
                    AppMetrica.reportError(
                        "vm_launch:${this::class.java.simpleName}:${throwable.message}",
                        throwable
                    )
                }
            }
        }

    /**
     * Безопасный scope с CoroutineExceptionHandler.
     * Используйте этот scope вместо androidx.lifecycle.viewModelScope
     * для автоматического перехвата необработанных исключений.
     */
    protected val scope: CoroutineScope =
        CoroutineScope(job + Dispatchers.Main.immediate + coroutineExceptionHandler)

    open fun clear() {
        // Override point for subclasses
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
