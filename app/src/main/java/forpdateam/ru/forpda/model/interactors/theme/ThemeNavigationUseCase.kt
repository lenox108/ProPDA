package forpdateam.ru.forpda.model.interactors.theme

import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import javax.inject.Inject

/**
 * Инкапсулирует навигацию из темы:
 * открытие профиля, QMS, поиска, форума, обработка ссылок.
 *
 * Вынесен из ThemeViewModel для снижения числа зависимостей (SRP).
 */
class ThemeNavigationUseCase @Inject constructor(
        private val linkHandler: ILinkHandler,
        private val router: TabRouter,
        private val userHolder: IUserHolder
) {

    fun openProfile(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router)
    }

    fun openQms(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?act=qms&amp;mid=$userId", router)
    }

    fun openForum(forumId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=$forumId", router)
    }

    fun openSearchInTopic(forumId: Int, topicId: Int, nick: String, userId: Int = 0) {
        linkHandler.handle(SearchSettings().apply {
            addForum(Integer.toString(forumId))
            addTopic(Integer.toString(topicId))
            source = SearchSettings.SOURCE_CONTENT.first
            this.nick = nick
            this.userId = userId
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_FALSE
        }.toUrl(), router)
    }

    fun openSearchUserTopics(nick: String, userId: Int = 0) {
        linkHandler.handle(SearchSettings().apply {
            source = SearchSettings.SOURCE_ALL.first
            this.nick = nick
            this.userId = userId
            result = SearchSettings.RESULT_TOPICS.first
        }.toUrl(), router)
    }

    fun openSearchUserMessages(nick: String, userId: Int = 0) {
        linkHandler.handle(SearchSettings().apply {
            source = SearchSettings.SOURCE_ALL.first
            this.nick = nick
            this.userId = userId
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_TRUE
        }.toUrl(), router)
    }

    fun openReputationHistory(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?act=rep&view=history&amp;mid=$userId", router)
    }

    fun openSearchMyPosts(topicId: Int, forumId: Int) {
        val nick = userHolder.user?.nick.orEmpty()
        linkHandler.handle(SearchSettings().apply {
            addTopic(Integer.toString(topicId))
            source = SearchSettings.SOURCE_CONTENT.first
            this.nick = nick
            result = SearchSettings.RESULT_POSTS.first
        }.toUrl(), router)
    }

    fun handleLink(url: String) {
        linkHandler.handle(url, router)
    }

    fun isSiteHost(host: String?): Boolean = SiteUrls.isSiteHost(host)
}
