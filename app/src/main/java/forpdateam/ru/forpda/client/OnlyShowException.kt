package forpdateam.ru.forpda.client

/**
 * Исключение для отображения сообщения пользователю без логирования/краша.
 *
 * Улучшения в Kotlin-версии:
 * - @JvmOverloads для конструкторов
 * - Упрощенный синтаксис
 */
class OnlyShowException : Exception {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    constructor(
        message: String?,
        cause: Throwable?,
        enableSuppression: Boolean,
        writableStackTrace: Boolean
    ) : super(message, cause, enableSuppression, writableStackTrace)
}
