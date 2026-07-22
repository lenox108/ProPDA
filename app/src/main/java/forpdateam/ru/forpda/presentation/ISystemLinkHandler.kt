package forpdateam.ru.forpda.presentation

import android.content.Context

interface ISystemLinkHandler {
    /** Явно открыть ссылку в браузере (пункт меню «Открыть в браузере»). */
    fun handle(url: String)

    /**
     * Открыть внешнюю ссылку «по-умному»: сначала нативным приложением, назначенным обработчиком
     * этого хоста (App Links / выбор пользователя), и только при его отсутствии — в браузере.
     * Используется для обычного тапа по ссылке, а не для явного «Открыть в браузере».
     */
    fun openExternal(url: String)
    fun handleDownload(
            url: String,
            inputFileName: String? = null,
            uiContext: Context? = null,
            contentDisposition: String? = null
    )
}