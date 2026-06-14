package forpdateam.ru.forpda.client

import android.os.Build
import androidx.annotation.RequiresApi

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
    
    @RequiresApi(Build.VERSION_CODES.N)
    constructor(
        message: String?, 
        cause: Throwable?, 
        enableSuppression: Boolean, 
        writableStackTrace: Boolean
    ) : super(message, cause, enableSuppression, writableStackTrace)
}
