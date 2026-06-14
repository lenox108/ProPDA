package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.IWebClient
import timber.log.Timber

/**
 * Created by radiationx on 01.02.17.
 */

class SearchApi(
        private val webClient: IWebClient,
        private val searchParser: SearchParser
) {

    fun getSearch(settings: SearchSettings): SearchResult {
        val url = settings.toUrl()
        if (BuildConfig.DEBUG) Timber.d("SearchApi.getSearch forums=${settings.forums.size} topics=${settings.topics.size}")
        val response = webClient.get(url)
        return SearchTitleRanker.rank(searchParser.parse(response.body, settings))
    }
}
