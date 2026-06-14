package forpdateam.ru.forpda.common

/**
 * Расширения для удобной работы с Kotlin Result<T>
 */

/**
 * Выполняет действие в случае успеха
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (isSuccess) action(getOrThrow())
    return this
}

/**
 * Выполняет действие в случае ошибки
 */
inline fun <T> Result<T>.onFailure(action: (Throwable) -> Unit): Result<T> {
    if (isFailure) exceptionOrNull()?.let(action)
    return this
}

/**
 * Преобразует результат в другой тип
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = mapCatching { transform(getOrThrow()) }

/**
 * Преобразует результат с возможностью выброса исключения
 */
inline fun <T, R> Result<T>.mapCatching(transform: (T) -> R): Result<R> {
    return try {
        Result.success(transform(getOrThrow()))
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
