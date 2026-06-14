package forpdateam.ru.forpda.model.data.remote.api.mentions

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
        return mentionsParser.parse(response.body)
    }
}
