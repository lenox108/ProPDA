package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.model.repository.temp.TempHelper
import forpdateam.ru.forpda.ui.TemplateManager
import java.util.regex.Matcher
import java.util.regex.Pattern

class ThemeTemplate(
        private val templateManager: TemplateManager,
        private val authHolder: AuthHolder,
        private val topicPreferencesHolder: TopicPreferencesHolder
) {

    private val firstLetter = Pattern.compile("([a-zA-Zа-яА-Я])")

    fun mapEntity(page: ThemePage): ThemePage = page.apply { html = mapString(page) }

    fun mapString(page: ThemePage): String {
        val template = templateManager.getTemplate(TemplateManager.TEMPLATE_THEME)

        val authData = authHolder.get()
        val authorized = authData.isAuth()
        val memberId = authData.userId
        template.apply {
            templateManager.fillStaticStrings(this)
            val prevDisabled = page.pagination.current <= 1
            val nextDisabled = page.pagination.current == page.pagination.all

            setVariableOpt("style_type", templateManager.getThemeType())

            setVariableOpt("topic_title", ApiUtils.htmlEncode(page.title))
            setVariableOpt("topic_description", ApiUtils.htmlEncode(page.desc))
            val topicUrl = page.url?.takeIf { it.contains("showtopic=", ignoreCase = true) }
                    ?: "https://4pda.to/forum/index.php?showtopic=${page.id}"
            setVariableOpt("topic_url", topicUrl)

            setVariableOpt("all_pages_int", page.pagination.all)
            setVariableOpt("posts_on_page_int", page.pagination.perPage)
            setVariableOpt("current_page_int", page.pagination.current)

            setVariableOpt("authorized_bool", authorized.toString())
            setVariableOpt("is_curator_bool", false.toString())
            setVariableOpt("member_id_int", memberId)
            setVariableOpt("elem_to_scroll", page.anchor ?: "")
            setVariableOpt("body_type", "topic")

            setVariableOpt("navigation_disable", TempHelper.getDisableStr(prevDisabled && nextDisabled))
            setVariableOpt("first_disable", TempHelper.getDisableStr(prevDisabled))
            setVariableOpt("prev_disable", TempHelper.getDisableStr(prevDisabled))
            setVariableOpt("next_disable", TempHelper.getDisableStr(nextDisabled))
            setVariableOpt("last_disable", TempHelper.getDisableStr(nextDisabled))

            setVariableOpt("in_favorite_bool", java.lang.Boolean.toString(page.isInFavorite))
            val isEnableAvatars = topicPreferencesHolder.getShowAvatars()
            setVariableOpt("enable_avatars_bool", java.lang.Boolean.toString(isEnableAvatars))
            setVariableOpt("enable_avatars", if (isEnableAvatars) "show_avatar" else "hide_avatar")
            setVariableOpt("avatar_type", if (topicPreferencesHolder.getCircleAvatars()) "circle_avatar" else "square_avatar")
            // theme.hat_opened: при false не разворачиваем шапку из JS при скролле к якорю (см. theme.js).
            setVariableOpt("hat_opened_pref_bool", java.lang.Boolean.toString(topicPreferencesHolder.getHatOpened()))


            var hatPostId = 0
            if (!page.posts.isEmpty()) {
                hatPostId = page.posts[0].id
            }
            var letterMatcher: Matcher? = null
            for (post in page.posts) {
                setVariableOpt("user_online", if (post.isOnline) "online" else "")
                setVariableOpt("post_id", post.id)
                setVariableOpt("user_id", post.userId)

                //Post header
                setVariableOpt("avatar", post.avatar)
                setVariableOpt("none_avatar", if (post.avatar.isNullOrEmpty()) "none_avatar" else "")

                val nickForLetter = post.nick.orEmpty()
                letterMatcher = letterMatcher?.reset(nickForLetter) ?: firstLetter.matcher(nickForLetter)
                val letter: String = letterMatcher?.run {
                    if (find()) group(1) else null
                } ?: post.nick?.takeIf { it.isNotEmpty() }?.substring(0, 1).orEmpty()

                setVariableOpt("nick_letter", letter)
                setVariableOpt("nick", ApiUtils.htmlEncode(post.nick))
                setVariableOpt("curator", if (post.isCurator) "curator" else "")
                setVariableOpt("group_color", post.groupColor)
                setVariableOpt("group", post.group)
                setVariableOpt("reputation", post.reputation)
                setVariableOpt("date", post.date)
                setVariableOpt("number", post.number)

                //Post body
                if (page.posts.size > 1 && hatPostId == post.id) {
                    val hatOpened = topicPreferencesHolder.getHatOpened() || prevDisabled || page.isHatOpen
                    setVariableOpt("hat_state_class", if (hatOpened) "open" else "close")
                    //t.setVariableOpt("hat_body_state", prevDisabled || page.isHatOpen() ? "" : "hidden");
                    addBlockOpt("hat_button")
                    addBlockOpt("hat_content_start")
                    addBlockOpt("hat_content_end")
                } else {
                    setVariableOpt("hat_state_class", "")
                }
                setVariableOpt("body", post.body)

                //Post footer

                if (!authorized || post.canReport)
                    addBlockOpt("report_block")
                if (!authorized || (page.canQuote && post.userId != memberId)) {
                    // Один блок без вложенных reply_block/quote_full_block — иначе MiniTemplator
                    // подставляет ${post_id} от предыдущей итерации поста.
                    addBlockOpt("reply_quote_row")
                }
                if (!authorized || post.canDelete)
                    addBlockOpt("delete_block")
                if (!authorized || post.canEdit)
                    addBlockOpt("edit_block")

                addBlockOpt("post")
            }

            //Poll block
            page.poll?.let { poll ->
                setVariableOpt("poll_state_class", if (page.isPollOpen) "open" else "close")
                val isResult = poll.isResult
                setVariableOpt("poll_type", if (isResult) "result" else "default")
                setVariableOpt("poll_title", if (poll.title.isNullOrEmpty() || poll.title == "-") App.get().getString(R.string.poll) else poll.title)

                for (question in poll.questions) {
                    setVariableOpt("question_title", question.title)

                    for (questionItem in question.questionItems) {
                        setVariableOpt("question_item_title", questionItem.title)

                        if (isResult) {
                            setVariableOpt("question_item_votes", questionItem.votes)
                            setVariableOpt("question_item_percent", java.lang.Float.toString(questionItem.percent))
                            addBlockOpt("poll_result_item")
                        } else {
                            setVariableOpt("question_item_type", questionItem.type)
                            setVariableOpt("question_item_name", questionItem.name)
                            setVariableOpt("question_item_value", questionItem.value)
                            addBlockOpt("poll_default_item")
                        }
                    }
                    addBlockOpt("poll_question_block")
                }
                setVariableOpt("poll_votes_count", poll.votesCount)
                if (poll.haveButtons()) {
                    if (poll.voteButton)
                        addBlockOpt("poll_vote_button")
                    if (poll.showResultsButton)
                        addBlockOpt("poll_show_results_button")
                    if (poll.showPollButton)
                        addBlockOpt("poll_show_poll_button")
                    addBlockOpt("poll_buttons")
                }
                addBlockOpt("poll_block")
            }
        }

        val result = template.generateOutput()
        template.reset()
        return result
    }

}