package forpdateam.ru.forpda.presentation

import forpdateam.ru.forpda.client.GoogleCaptchaException
import forpdateam.ru.forpda.client.OkHttpResponseException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Created by radiationx on 23.02.18.
 */
class ErrorHandler(
        private val router: TabRouter
) : IErrorHandler {

    override fun handle(throwable: Throwable,  messageListener: ((Throwable, String?) -> Unit)?) {
        throwable.printStackTrace()
        val message = getMessage(throwable)
        if (messageListener != null) {
            messageListener.invoke(throwable, message)
        } else {
            router.showSystemMessage(message)
        }
    }

    private fun getMessage(throwable: Throwable): String {
        timeoutUserMessage(throwable)?.let { return it }
        when (throwable) {
            is GoogleCaptchaException ->
                return "Сайт запросил проверку (Cloudflare). Отключите VPN/прокси или откройте 4pda в браузере и повторите."
            is OkHttpResponseException ->
                if (throwable.code == 403) {
                    return "Доступ запрещён (403). Для части контента нужна авторизация или другая сеть."
                }
        }
        return throwable.message.orEmpty()
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
}