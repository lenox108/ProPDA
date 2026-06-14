package forpdateam.ru.forpda.common

/**
 * Приватные заголовки и cookie-имена, которые не должны отправляться на внешние сайты
 * или логироваться в debug-режиме.
 */
object PrivateHeaders {
    val LIST = listOf("pass_hash", "session_id", "auth_key", "password")
}
