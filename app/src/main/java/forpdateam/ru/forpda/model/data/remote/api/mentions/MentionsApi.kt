package forpdateam.ru.forpda.model.data.remote.api.mentions

import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest

/**
 * Created by radiationx on 21.01.17.
 */

class MentionsApi(
        private val webClient: IWebClient,
        private val mentionsParser: MentionsParser
) {
    fun getMentions(st: Int): MentionsData {
        val response = webClient.request(
                NetworkRequest.Builder()
                        .url("https://4pda.to/forum/index.php?act=mentions&st=$st")
                        .skipCounterUpdate()
                        .build()
        )
        // act=mentions периодически отдаёт HTTP 404 (закешированную Cloudflare страницу ошибки).
        // Раньше её тело парсилось в «0 упоминаний» и список «Ответы» опустошался. Считаем это
        // транзиентной ошибкой, а не пустым списком — пусть репозиторий сохранит прошлый список.
        if (response.code >= 400) {
            throw OkHttpResponseException(response.code, response.message, response.url)
        }
        return mentionsParser.parse(response.body)
    }
}
