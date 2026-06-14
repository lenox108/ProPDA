package forpdateam.ru.forpda.model.data.remote.api.topcis

import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.IWebClient

/**
 * Created by radiationx on 01.03.17.
 */

class TopicsApi(
        private val webClient: IWebClient,
        private val topicsParser: TopicsParser
) {

    fun getTopics(id: Int, st: Int): TopicsData {
        // Добавляем timestamp и no-cache чтобы избежать кэширования
        val url = "https://4pda.to/forum/index.php?showforum=$id&st=$st&_t=${System.currentTimeMillis()}"
        val request = forpdateam.ru.forpda.model.data.remote.api.NetworkRequest.Builder()
            .url(url)
            .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .addHeader("Pragma", "no-cache")
            .build()
        val response = webClient.request(request)
        return topicsParser.parse(response.body, id)
    }
}
