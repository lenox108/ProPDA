package forpdateam.ru.forpda.model.data.remote.api.usercp

import forpdateam.ru.forpda.entity.remote.usercp.ForumSettings
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest

/**
 * Доступ к разделу «Настройки форума» полной версии 4pda.
 *
 * Чтение — обычный GET страницы UserCP с парсингом состояния полей.
 * Сохранение — POST act=usercp&action=forum-settings; форма уходит только с куками
 * сессии (как и на сайте — скрытого auth_key у неё нет).
 */
class UserCpApi(
        private val webClient: IWebClient,
        private val parser: UserCpParser
) {

    fun loadSettings(): ForumSettings {
        val response = webClient.get(LOAD_URL)
        return parser.parse(response.body)
    }

    /**
     * Отправляет ВЕСЬ набор полей (включая не показываемые в клиенте) и
     * перечитывает страницу, чтобы вернуть подтверждённое сервером состояние.
     *
     * Чекбоксы IPB: поле присутствует со значением "1" только когда включено,
     * иначе опускается — пропуск трактуется сервером как «выключено».
     */
    fun saveSettings(settings: ForumSettings): ForumSettings {
        val builder = NetworkRequest.Builder().url(SAVE_URL)

        addCheckbox(builder, "tz-autoset", settings.tzAutoset)
        builder.formHeader("time-offset", settings.timeOffset)
        addCheckbox(builder, "dst-in-use", settings.dstInUse)
        addCheckbox(builder, "view-sigs", settings.viewSigs)
        addCheckbox(builder, "view-img", settings.viewImg)
        addCheckbox(builder, "view-avs", settings.viewAvs)
        addCheckbox(builder, "ucp-show-qr-code", settings.ucpShowQrCode)
        builder.formHeader("topicpage", settings.topicPage)
        builder.formHeader("postpage", settings.postPage)
        addCheckbox(builder, "mention-notify", settings.mentionNotify)
        addCheckbox(builder, "send-full-msg", settings.sendFullMsg)
        addCheckbox(builder, "auto-track", settings.autoTrack)
        builder.formHeader("trackchoice", settings.trackChoice)
        addCheckbox(builder, "qr-open", settings.qrOpen)
        addCheckbox(builder, "rep-notify", settings.repNotify)

        webClient.request(builder.build())
        return loadSettings()
    }

    private fun addCheckbox(builder: NetworkRequest.Builder, name: String, enabled: Boolean) {
        if (enabled) {
            builder.formHeader(name, "1")
        }
    }

    companion object {
        private const val LOAD_URL = "https://4pda.to/forum/index.php?act=UserCP"
        private const val SAVE_URL = "https://4pda.to/forum/index.php?act=usercp&action=forum-settings"
    }
}
