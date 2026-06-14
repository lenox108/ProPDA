package forpdateam.ru.forpda.presentation

import forpdateam.ru.forpda.client.GoogleCaptchaException
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.common.ArticleCommentsUserMessage
import forpdateam.ru.forpda.model.interactors.news.ArticleLoadException
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * Created by radiationx on 23.02.18.
 */
class ErrorHandler @Inject constructor(
        private val router: TabRouter
) : IErrorHandler {

    override fun handle(throwable: Throwable,  messageListener: ((Throwable, String?) -> Unit)?) {
        // Отмена корутины/подписки — часть нормального жизненного цикла, не показываем пользователю
        // (иначе во viewModelScope при закрытии экрана всплывает «GP/Job was cancelled»).
        if (isCancellation(throwable)) {
            return
        }
        Timber.e(throwable, "Error handled: %s", throwable.message ?: "unknown")
        val message = getMessage(throwable)
        if (messageListener != null) {
            messageListener.invoke(throwable, message)
        } else {
            router.showSystemMessage(message)
        }
    }

    /** Усечение URL для тоста: убираем схему и хост, оставляем только путь + query (до 120 символов). */
    private fun shortUrl(url: String): String {
        val stripped = url
                .removePrefix("https://")
                .removePrefix("http://")
                .substringAfter('/', missingDelimiterValue = url)
        val withSlash = if (url.contains("://")) "/$stripped" else url
        return if (withSlash.length > 120) withSlash.take(117) + "..." else withSlash
    }

    private fun isCancellation(t: Throwable): Boolean {
        var c: Throwable? = t
        while (c != null) {
            if (c is CancellationException) return true
            c = c.cause
        }
        return false
    }

    private fun getMessage(throwable: Throwable): String {
        timeoutUserMessage(throwable)?.let { return it }
        networkErrorUserMessage(throwable)?.let { return it }
        when (throwable) {
            is GoogleCaptchaException ->
                return "Сайт запросил проверку (Cloudflare). Отключите VPN/прокси или откройте 4pda в браузере и повторите."
            is OkHttpResponseException ->
                when (throwable.code) {
                    401 -> return "Требуется авторизация (401). Войдите в аккаунт для доступа к этому разделу."
                    403 -> return "Доступ запрещён (403). Для части контента нужна авторизация или другая сеть."
                    404 -> return "Страница не найдена (404). Возможно, API форума изменилось или страница удалена.\nURL: ${shortUrl(throwable.url)}"
                    429 -> return "Слишком много запросов. Подождите немного и попробуйте снова."
                }
        }
        if (throwable is ArticleLoadException) {
            return throwable.message ?: "Не удалось загрузить новость"
        }
        // Check for auth-related messages
        val msg = throwable.message.orEmpty()
        if (msg.contains("авторизация", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) {
            return msg
        }
        if (msg.contains("empty after template", ignoreCase = true) ||
                msg.contains("empty after parse", ignoreCase = true)) {
            return "Не удалось загрузить новость"
        }
        if (msg == "comments_html_present_but_parse_empty" ||
                msg == "comments_count_positive_but_no_source" ||
                msg == "comment_list_shell_unresolved_positive_count" ||
                msg.startsWith("parsed_zero_with_hint_")) {
            return ArticleCommentsUserMessage.forReason(msg)
        }
        return msg
    }

    /**
     * RxJava `timeout()` даёт английское «The source did not signal…» — показываем нормальный текст.
     * Цепочка cause учитывает обёртки вокруг [TimeoutException].
     */
    private fun timeoutUserMessage(t: Throwable): String? {
        var c: Throwable? = t
        while (c != null) {
            if (c is TimeoutException || c is SocketTimeoutException) {
                return "Сервер или сеть не ответили вовремя. Проверьте подключение и попробуйте снова."
            }
            val msg = c.message
            if (msg != null && msg.contains("did not signal an event", ignoreCase = true)) {
                return "Сервер или сеть не ответили вовремя. Проверьте подключение и попробуйте снова."
            }
            c = c.cause
        }
        return null
    }

    /**
     * DNS и сетевые ошибки: UnknownHostException, SSLException, etc.
     * Показываем понятное сообщение вместо технического текста.
     */
    private fun networkErrorUserMessage(t: Throwable): String? {
        var c: Throwable? = t
        while (c != null) {
            if (c is UnknownHostException) {
                val msg = c.message.orEmpty()
                return when {
                    msg.contains("4pda.to", ignoreCase = true) ->
                        "Не удалось подключиться к 4pda.to. Проверьте интернет или используйте VPN (сайт может быть заблокирован)."
                    else ->
                        "Не удалось найти сервер. Проверьте подключение к интернету."
                }
            }
            // SSL ошибки часто связаны с блокировками
            val msg = c.message.orEmpty()
            if (c.javaClass.simpleName.contains("SSL", ignoreCase = true) ||
                msg.contains("SSL", ignoreCase = true) ||
                msg.contains("TLS", ignoreCase = true) ||
                msg.contains("certificate", ignoreCase = true)) {
                return "Ошибка защищённого соединения. Возможно, сайт заблокирован провайдером или истёк сертификат."
            }
            c = c.cause
        }
        return null
    }
}