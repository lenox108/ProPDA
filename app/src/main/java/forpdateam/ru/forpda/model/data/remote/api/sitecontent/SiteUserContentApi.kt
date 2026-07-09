package forpdateam.ru.forpda.model.data.remote.api.sitecontent

import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.entity.remote.sitecontent.SiteComment
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParser

/**
 * Загрузка сайтового контента пользователя (страницы `/<ник>/posts/` и `/<ник>/comments/`).
 *
 * «Постов» — та же вёрстка `<article class="post">`, что и лента новостей, поэтому переиспользуем
 * готовый [ArticleParser.parseArticles] (проверено на живом 4pda). «Комментов» — своя разметка,
 * парсим [SiteCommentsParser].
 */
class SiteUserContentApi(
        private val webClient: IWebClient,
        private val articleParser: ArticleParser,
        private val commentsParser: SiteCommentsParser,
) {
    fun loadPosts(url: String): List<NewsItem> =
            articleParser.parseArticles(webClient.get(url).body)

    fun loadComments(url: String): List<SiteComment> =
            commentsParser.parse(webClient.get(url).body)
}
