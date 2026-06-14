package forpdateam.ru.forpda.presentation.search

import forpdateam.ru.forpda.common.Cp1251Codec
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.theme.TopicOpenContext
import forpdateam.ru.forpda.presentation.theme.TopicOpenResolution
import forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver

/** Поиск только в указанном разделе форума (без вложенных подразделов). */
fun forumSectionSearchUrl(forumId: Int): String {
    require(forumId > 0) { "forumId must be positive" }
    return SearchSettings().apply {
        addForum(forumId.toString())
        source = SearchSettings.SOURCE_TITLES.first
        result = SearchSettings.RESULT_TOPICS.first
        subforums = SearchSettings.SUB_FORUMS_FALSE
    }.toUrl()
}

fun SearchSettings.isUserPostSearch(): Boolean =
        resourceType == SearchSettings.RESOURCE_FORUM.first &&
                result == SearchSettings.RESULT_POSTS.first &&
                topics.isEmpty() &&
                (userId > 0 || !nick.isNullOrEmpty())

fun SearchSettings.isBroadUserSearch(): Boolean =
        resourceType == SearchSettings.RESOURCE_FORUM.first &&
                topics.isEmpty() &&
                (result == SearchSettings.RESULT_POSTS.first || result == SearchSettings.RESULT_TOPICS.first) &&
                (userId > 0 || !nick.isNullOrEmpty())

fun SearchSettings.userPostsInTopicSearchUrl(item: IBaseForumPost): String = SearchSettings().apply {
    if (item.forumId > 0) {
        addForum(item.forumId.toString())
    }
    addTopic(item.topicId.toString())
    source = SearchSettings.SOURCE_CONTENT.first
    nick = this@userPostsInTopicSearchUrl.nick
    userId = this@userPostsInTopicSearchUrl.userId
    result = SearchSettings.RESULT_POSTS.first
    subforums = SearchSettings.SUB_FORUMS_FALSE
}.toUrl().withNickForInternalSearch(nick, userId)

fun IBaseForumPost.userPostsInTopicSearchUrl(): String = SearchSettings().apply {
    if (forumId > 0) {
        addForum(forumId.toString())
    }
    addTopic(topicId.toString())
    source = SearchSettings.SOURCE_CONTENT.first
    nick = this@userPostsInTopicSearchUrl.nick
    userId = this@userPostsInTopicSearchUrl.userId
    result = SearchSettings.RESULT_POSTS.first
    subforums = SearchSettings.SUB_FORUMS_FALSE
}.toUrl()

private fun String.withNickForInternalSearch(nick: String?, userId: Int): String {
    if (userId <= 0 || nick.isNullOrEmpty()) return this
    return "$this&${SearchSettings.ARG_NICK}=${Cp1251Codec.encodeSmart(nick)}"
}

/** URL перехода к конкретному посту из результата поиска (кнопка «→» / заголовок поста). */
fun buildSearchFindPostTopicUrl(topicId: Int, postId: Int): String {
    require(postId > 0) { "postId must be positive" }
    return if (topicId > 0) {
        "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=$postId"
    } else {
        "https://4pda.to/forum/index.php?act=findpost&pid=$postId"
    }
}

fun isSearchFindPostTopicUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.contains("view=findpost") || lower.contains("act=findpost")
}

/** Как [TopicOpenTargetResolver.resolve] для открытия из поиска — findpost/p= не должны теряться при LAST_UNREAD. */
fun resolveSearchFindPostThemeOpen(
        findPostUrl: String,
        setting: AppPreferences.Main.TopicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
): TopicOpenResolution = TopicOpenTargetResolver.resolve(
        TopicOpenContext(
                rawUrl = findPostUrl,
                setting = setting,
                sourceScreen = "search"
        )
)

fun themeOpenArgsForSearchUrl(url: String): Map<String, String> = buildMap {
    put(Screen.Theme.ARG_TOPIC_OPEN_SOURCE, "search")
    if (isSearchFindPostTopicUrl(url)) {
        put(Screen.Theme.ARG_TOPIC_OPEN_INTENT, forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.EXPLICIT_POST)
    }
}
