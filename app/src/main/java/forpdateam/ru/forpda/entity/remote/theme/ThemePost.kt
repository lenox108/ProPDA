package forpdateam.ru.forpda.entity.remote.theme

import android.util.Pair

import java.util.ArrayList

import forpdateam.ru.forpda.entity.remote.BaseForumPost

/**
 * Created by radiationx on 04.08.16.
 */
class ThemePost : BaseForumPost(), IThemePost {
    val attachImages = ArrayList<Pair<String, String>>()
    var userPostCount: Int? = null

    /**
     * Личная подпись автора (инлайновый HTML `div.signature`), «Показывать подписи пользователей».
     * Мобильная выдача, по которой рендерится страница, подписей НЕ содержит вовсе — поле заполняет
     * отложенное обогащение из десктопного ответа (см. `ThemeApi.fetchAndMergeDesktopTopicMetadata`),
     * который приложение и так качает ради рейтингов и «💬 N». Сервер сам режет длинные подписи.
     */
    var signatureHtml: String? = null
}
