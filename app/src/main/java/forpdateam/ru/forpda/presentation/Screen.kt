package forpdateam.ru.forpda.presentation

import forpdateam.ru.forpda.common.Constants
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm

sealed class Screen : com.github.terrakok.cicerone.Screen {
    override val screenKey: String get() = getKey()
    companion object {
        const val ARG_TITLE = "arg_title"
        const val ARG_SUBTITLE = "arg_subtitle"
        private val NO_ID = -1
    }

    open var screenTitle: String? = null
    open var screenSubTitle: String? = null
    open var fromMenu: Boolean = false
    open var isAlone: Boolean = false

    fun getKey(): String = this::class.java.simpleName

    /* Activities */

    class Main : Screen() {
        var checkWebView = true
    }

    class WebViewNotFound : Screen()

    class ImageViewer : Screen() {
        var urls: MutableList<String> = mutableListOf()
        var selected: Int = NO_ID
    }

    class Settings : Screen() {
        var fragment: String? = null
    }

    /* Fragments */
    class Auth : Screen() {
        override var isAlone: Boolean = true
    }

    class DevDbDevices : Screen() {
        var brandId: String? = null
        var categoryId: String? = null
    }

    class DevDbBrands : Screen() {
        override var isAlone: Boolean = true
        var categoryId: String? = null
    }

    class DevDbDevice : Screen() {
        var deviceId: String? = null
    }

    class DevDbSearch : Screen()

    class EditPost : Screen() {
        var editPostForm: EditPostForm? = null
        var postId: Int = NO_ID
        var topicId: Int = NO_ID
        var forumId: Int = NO_ID
        var st: Int = 0
        var themeName: String? = null
        var initialSelectionStart: Int? = null
        var initialSelectionEnd: Int? = null
        /** HTML тела из темы, если парсер формы редактирования не вернул текст */
        var initialBodyHtml: String? = null
    }

    class Favorites : Screen() {
        override var isAlone: Boolean = true
    }

    class Forum : Screen() {
        var forumId: Int = NO_ID
    }

    class History : Screen() {
        override var isAlone: Boolean = true
    }

    class Mentions : Screen() {
        override var isAlone: Boolean = true
    }

    class ArticleList : Screen() {
        override var isAlone: Boolean = true
    }

    class ArticleDetail : Screen() {
        var articleId: Int = NO_ID
        var commentId: Int = NO_ID
        var articleOpenSource: String = "news_list"
        var articleUrl: String? = null
        var articleTitle: String? = null
        var articleAuthorNick: String? = null
        var articleDate: String? = null
        var articleImageUrl: String? = null
        var articleCommentsCount: Int = 0
    }

    class Notes : Screen() {
        override var isAlone: Boolean = true
    }

    class Announce : Screen() {
        var forumId: Int = NO_ID
        var announceId: Int = NO_ID
    }

    class ForumRules : Screen() {
        override var isAlone: Boolean = true
    }

    class ForumBlackList : Screen()

    class GoogleCaptcha : Screen()

    class Profile : Screen() {
        var profileUrl: String? = null
    }

    class QmsContacts : Screen() {
        override var isAlone: Boolean = true
    }

    class QmsBlackList : Screen()
    class QmsThemes : Screen() {
        var userId: Int = NO_ID
        var avatarUrl: String? = null
    }

    class QmsChat : Screen() {
        /** One chat tab app-wide; reopening another dialog rebinds ids and reloads via [QmsChatFragment.applyChatScreenFromNavigator]. */
        override var isAlone: Boolean = true
        var userId: Int = NO_ID
        var themeId: Int = NO_ID
        var userNick: String? = null
        var themeTitle: String? = null
        var avatarUrl: String? = null
    }

    class Reputation : Screen() {
        var reputationUrl: String? = null
    }

    class Search : Screen() {
        var searchUrl: String? = null
    }

    class Downloads : Screen() {
        override var isAlone: Boolean = true
    }

    class Theme : Screen() {
        /** Одна вкладка темы на всё приложение; повторное открытие из списка перезагружает URL. */
        override var isAlone: Boolean = true

        companion object {
            /** Ключи для Cicerone 6+ [com.github.terrakok.cicerone.BaseRouter.sendResult] — только String. */
            const val CODE_RESULT_SYNC = "forpda.theme.EDIT_POST_SYNC"
            const val CODE_RESULT_PAGE = "forpda.theme.EDIT_POST_PAGE"
            const val ARG_TOPIC_OPEN_SOURCE = "topic_open_source"
            const val ARG_UNREAD_URL_FROM_LIST = "topic_unread_url_from_list"
            const val ARG_UNREAD_POST_ID_FROM_LIST = "topic_unread_post_id_from_list"
            const val ARG_LAST_READ_URL_FROM_LIST = "topic_last_read_url_from_list"
            const val ARG_INSPECTOR_MARKED_UNREAD = "topic_inspector_marked_unread"
            const val ARG_TOPIC_OPEN_INTENT = "topic_open_intent"
        }

        var themeUrl: String? = null
        var topicOpenSource: String = "unknown"
        var unreadUrlFromList: String? = null
        var unreadPostIdFromList: Int = 0
        var lastReadUrlFromList: String? = null
        /** Carried from favorites/topics list row — survives hint URL strip on navigation. */
        var listTopicMarkedUnread: Boolean = false
        /** Inspector fav snapshot marked this topic unread on last merge (separate from row state). */
        var inspectorMarkedUnread: Boolean = false
        /**
         * Navigation intent for opening a topic.
         * Values are stringly-typed to keep Fragment args stable.
         *
         * Expected: fresh_forum | fresh_favorites | fresh_search | explicit_post | back_restore | …
         */
        var topicOpenIntent: String = "fresh"
    }

    class Topics : Screen() {
        var forumId: Int = NO_ID
    }

    class OtherMenu() : Screen() {
        override var fromMenu = true
        override var isAlone = true
    }

}