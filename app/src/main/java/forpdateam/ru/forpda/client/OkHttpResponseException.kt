package forpdateam.ru.forpda.client

import java.io.IOException

/**
 * Исключение для ошибок HTTP ответа.
 * 
 * Улучшения в Kotlin-версии:
 * - Data class с val свойствами
 * - Автоматический toString()
 */
data class OkHttpResponseException(
    val code: Int,
    val name: String,
    val url: String,
    val retryAfterSeconds: Long? = null
) : IOException("Response {code=$code, message=$name}")
