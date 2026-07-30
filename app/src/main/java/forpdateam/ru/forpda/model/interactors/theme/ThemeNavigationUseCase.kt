package forpdateam.ru.forpda.model.interactors.theme

import com.github.terrakok.cicerone.ResultListenerHandler
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
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

    private var editSyncHandler: ResultListenerHandler? = null
    private var editPageHandler: ResultListenerHandler? = null

    /**
     * Open the standalone fullscreen post editor ([Screen.EditPost]), seeded with the current inline
     * draft [message]/[attachments]/selection — parity with the WebView's «полноэкранный редактор»
     * (ThemeFragment.fullButton → openEditPostForm). When [editPostId] > 0 the handoff is an EDIT of that
     * post (carry TYPE_EDIT_POST + postId so the submit patches the post instead of creating a duplicate);
     * otherwise it is a NEW reply. [onSync] fires when the user closes the editor without posting (restore
     * the draft into the inline panel); [onPosted] fires after a successful post (returns the fresh page so
     * the caller can refresh the topic).
     */
    fun openFullscreenEditor(
            forumId: Int,
            topicId: Int,
            st: Int,
            themeName: String?,
            message: String,
            attachments: List<AttachmentItem>,
            selectionStart: Int?,
            selectionEnd: Int?,
            editPostId: Int = 0,
            onSync: (EditPostSyncData) -> Unit,
            onPosted: (ThemePage) -> Unit,
    ) {
        val form = EditPostForm().apply {
            type = if (editPostId > 0) EditPostForm.TYPE_EDIT_POST else EditPostForm.TYPE_NEW_POST
            this.forumId = forumId
            this.topicId = topicId
            this.st = st
            this.postId = editPostId
            this.message = message
            this.attachments.addAll(attachments)
        }
        editSyncHandler?.dispose()
        editSyncHandler = router.setResultListener(Screen.Theme.CODE_RESULT_SYNC) { res ->
            (res as? EditPostSyncData)?.takeIf { it.topicId == topicId }?.let(onSync)
        }
        editPageHandler?.dispose()
        editPageHandler = router.setResultListener(Screen.Theme.CODE_RESULT_PAGE) { res ->
            (res as? ThemePage)?.let(onPosted)
        }
        router.navigateTo(Screen.EditPost().apply {
            editPostForm = form
            this.themeName = themeName
            initialSelectionStart = selectionStart
            initialSelectionEnd = selectionEnd
        })
    }

    fun openProfile(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router)
    }

    fun openQms(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?act=qms&amp;mid=$userId", router)
    }

    fun openForum(forumId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=$forumId", router)
    }

    /** Корень форума (список разделов) — первое звено пути темы, у него нет showforum. */
    fun openForumRoot() {
        linkHandler.handle("https://4pda.to/forum/index.php?act=idx", router)
    }

    /**
     * Server-side search restricted to one topic. [query] pre-fills the search field — the find-on-page
     * bar hands its own query over when the local pass (loaded pages only) comes up empty, so the user
     * continues the same search instead of retyping it on a blank screen.
     */
    fun openSearchInTopic(forumId: Int, topicId: Int, nick: String, userId: Int = 0, query: String = "") {
        linkHandler.handle(SearchSettings().apply {
            addForum(Integer.toString(forumId))
            addTopic(Integer.toString(topicId))
            source = SearchSettings.SOURCE_CONTENT.first
            this.nick = nick
            this.userId = userId
            if (query.isNotBlank()) this.query = query
            result = SearchSettings.RESULT_POSTS.first
            subforums = SearchSettings.SUB_FORUMS_FALSE
        }.toUrl(), router)
    }

    /**
     * Opens posts in the current topic that link back to [postId].
     *
     * Forum replies/quotes contain the source post id in their findpost link, so the legacy 4PDA
     * content search can be used as the post-mentions lookup.
     */
    fun openPostMentions(forumId: Int, topicId: Int, postId: Int) {
        if (topicId <= 0 || postId <= 0) return
        linkHandler.handle(SearchSettings().apply {
            if (forumId > 0) addForum(forumId.toString())
            addTopic(topicId.toString())
            source = SearchSettings.SOURCE_CONTENT.first
            query = postId.toString()
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
