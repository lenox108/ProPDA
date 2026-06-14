package forpdateam.ru.forpda.common

/**
 * Sealed class для представления состояний загрузки данных.
 * Используется в ViewModel для передачи состояния UI через StateFlow.
 */
sealed class LoadingState<out T> {
    /**
     * Данные успешно загружены
     */
    data class Success<T>(val data: T) : LoadingState<T>()

    /**
     * Ошибка загрузки
     */
    data class Error(val message: String, val cause: Throwable? = null) : LoadingState<Nothing>()

    /**
     * Данные загружены, но пустые
     */
    object Empty : LoadingState<Nothing>()

    /**
     * Идет загрузка
     */
    object Loading : LoadingState<Nothing>()

    /**
     * Начальное состояние
     */
    object Idle : LoadingState<Nothing>()

    /**
     * Проверка, есть ли данные
     */
    fun hasData(): Boolean = this is Success

    /**
     * Получить данные или null
     */
    fun getDataOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}
