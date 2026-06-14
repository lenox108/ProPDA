package forpdateam.ru.forpda.presentation.search

import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.ui.TemplateManager
import java.util.regex.Matcher
import java.util.regex.Pattern

class SearchTemplate(
        private val templateManager: TemplateManager,
        private val authHolder: AuthHolder,
        private val topicPreferencesHolder: TopicPreferencesHolder
) {

    private val firstLetter = Pattern.compile("([a-zA-Zа-яА-Я])")

    fun mapEntity(page: SearchResult, expandedPostIds: Set<Int> = emptySet()): SearchResult =
            page.apply { html = mapString(page, expandedPostIds) }

    private fun mapString(page: SearchResult, expandedPostIds: Set<Int>): String {
        val template = templateManager.getTemplate(TemplateManager.TEMPLATE_SEARCH)

        val authData = authHolder.get()
        template.apply {
            templateManager.fillStaticStrings(template)

            setVariableOpt("style_type", templateManager.getThemeType())
            setVariableOpt("theme_overrides_css", templateManager.getThemeOverridesCss())

            setVariableOpt("all_pages_int", page.pagination.all)
            setVariableOpt("posts_on_page_int", page.pagination.perPage)
            setVariableOpt("current_page_int", page.pagination.current)
            setVariableOpt("authorized_bool", java.lang.Boolean.toString(authData.isAuth()))
            setVariableOpt("member_id_int", authData.userId)


            setVariableOpt("body_type", "search")

            val isEnableAvatars = topicPreferencesHolder.getShowAvatars()
            setVariableOpt("enable_avatars_bool", java.lang.Boolean.toString(isEnableAvatars))
            setVariableOpt("enable_avatars", if (isEnableAvatars) "show_avatar" else "hide_avatar")
            setVariableOpt("avatar_type", if (topicPreferencesHolder.getCircleAvatars()) "circle_avatar" else "square_avatar")


            val st = page.settings ?: SearchSettings()
            val forumPostsResult = st.resourceType == SearchSettings.RESOURCE_FORUM.first
                    && st.result == SearchSettings.RESULT_POSTS.first

            var letterMatcher: Matcher? = null
            for (post in page.items) {
                setVariableOpt("topic_id", post.topicId)
                setVariableOpt("post_title", post.title)

                setVariableOpt("user_online", if (post.isOnline) "online" else "")
                setVariableOpt("post_id", post.id)
                val isPostContentExpanded = expandedPostIds.contains(post.id)
                setVariableOpt("post_content_state_class", if (isPostContentExpanded) "open" else "close")
                setVariableOpt("post_content_expanded_bool", isPostContentExpanded.toString())

                // Как [SearchViewModel.onItemClick]: showtopic + view=findpost + p= — тот же путь, что и у клика по строке.
                // act=findpost&pid= давал нестабильные редиректы: иногда тема без якоря на конкретный пост.
                val showJump = forumPostsResult && post.id > 0
                val jumpToPostUrl = if (showJump) {
                    buildSearchFindPostTopicUrl(post.topicId, post.id)
                } else {
                    "#"
                }
                val userTopicSearchUrl = if (st.isBroadUserSearch() && post.topicId > 0) {
                    st.userPostsInTopicSearchUrl(post)
                } else {
                    null
                }
                val postTitleHref = when {
                    userTopicSearchUrl != null -> userTopicSearchUrl
                    forumPostsResult && post.id > 0 -> jumpToPostUrl
                    post.topicId > 0 -> "https://4pda.to/forum/index.php?showtopic=${post.topicId}"
                    else -> "#"
                }
                val postOpenUrl = userTopicSearchUrl ?: "#"
                setVariableOpt("jump_to_post_url", jumpToPostUrl)
                setVariableOpt("post_title_href", postTitleHref)
                setVariableOpt("post_open_url", postOpenUrl)
                setVariableOpt("search_jump_row_style", if (showJump) "" else "display:none")
                setVariableOpt("user_id", post.userId)

                //Post header
                setVariableOpt("avatar", post.avatar)
                setVariableOpt("none_avatar", if (post.avatar.isNullOrEmpty()) "none_avatar" else "")

                val nickForLetter = post.nick.orEmpty()
                letterMatcher = letterMatcher?.reset(nickForLetter) ?: firstLetter.matcher(nickForLetter)
                val letter: String = letterMatcher?.run {
                    if (find()) group(1) else null
                } ?: post.nick?.take(1).orEmpty()

                setVariableOpt("nick_letter", letter)
                setVariableOpt("nick", ApiUtils.htmlEncode(post.nick))
                //t.setVariableOpt("curator", false ? "curator" : "");
                setVariableOpt("group_color", post.groupColor)
                setVariableOpt("group", post.group)
                setVariableOpt("reputation", post.reputation)
                val dateText = Utils.formatForumDisplayDateTime(post.date, "search.post") ?: run {
                    post.date?.trim().orEmpty()
                }
                setVariableOpt("date", dateText)
                //t.setVariableOpt("number", post.getNumber());

                //Post body
                setVariableOpt("body", post.body)

                //Post footer

                /*if (post.canReport() && authorized)
                    t.addBlockOpt("report_block");
                if (page.canQuote() && authorized && post.getUserId() != memberId)
                    t.addBlockOpt("reply_block");
                if (authorized && post.getUserId() != memberId)
                    t.addBlockOpt("vote_block");
                if (post.canDelete() && authorized)
                    t.addBlockOpt("delete_block");
                if (post.canEdit() && authorized)
                    t.addBlockOpt("edit_block");*/

                addBlockOpt("post")
            }
        }

        val result = template.generateOutput()
        template.reset()

        return result
    }

}