package forpdateam.ru.forpda.presentation.theme

import android.content.Context
import android.util.Log
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.ui.TemplateManager
import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.security.MessageDigest
import java.util.LinkedHashMap

class ThemeTemplate(
        private val context: Context,
        private val templateManager: TemplateManager,
        private val authHolder: AuthHolder,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val topicPreferencesHolder: TopicPreferencesHolder
) {

    private data class PostRatingUi(
            val postRating: String,
            val hasServerPostRating: Boolean,
            val hasNonZeroServerPostRating: Boolean,
            val isOwnPost: Boolean,
            val canPlusPostRating: Boolean,
            val canMinusPostRating: Boolean
    ) {
        val hasPostRatingActions: Boolean
            get() = canPlusPostRating || canMinusPostRating
    }

    private data class TopHatRatingSource(
            val id: Int,
            val userId: Int,
            val postRating: String?,
            val canPlusPostRating: Boolean,
            val canMinusPostRating: Boolean
    ) {
        fun toThemePost(): ThemePost = ThemePost().also { post ->
            post.id = id
            post.userId = userId
            post.postRating = postRating
            post.canPlusPostRating = canPlusPostRating
            post.canMinusPostRating = canMinusPostRating
        }
    }

    private data class TopicPageRenderModel(
            val pageNumber: Int,
            val posts: List<ThemePost>,
            val topicHeader: ThemePost?,
            val shouldRenderInlineTopicHeader: Boolean,
            val overlayHeaderExpanded: Boolean,
            val inlineHeaderExpanded: Boolean
    )

    private companion object {
        const val POSTS_LIST_START_MARKER = "<!-- theme_posts_list_start -->"
        const val POSTS_LIST_END_MARKER = "<!-- theme_posts_list_end -->"
        const val TEMPLATE_RESOURCES_VERSION = "theme-template-v19-forum-blacklist-inline-post-block"
        const val POSTS_FRAGMENT_CACHE_SIZE = 8
    }

    private val firstLetter = Pattern.compile("([a-zA-Zа-яА-Я])")
    private val postsFragmentCache = object : LinkedHashMap<String, String>(POSTS_FRAGMENT_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > POSTS_FRAGMENT_CACHE_SIZE
        }
    }
    private val topHatRatingSourceCache = object : LinkedHashMap<Int, TopHatRatingSource>(POSTS_FRAGMENT_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TopHatRatingSource>?): Boolean {
            return size > POSTS_FRAGMENT_CACHE_SIZE
        }
    }

    /** Posts that [mapRawString] will iterate after topic-hat filtering. */
    fun expectedListPostCount(page: ThemePage): Int = buildRenderModel(page).posts.size

    @Synchronized
    fun mapEntity(page: ThemePage): ThemePage = page.apply {
        renderSignature = renderSignature()
        html = mapString(page)
    }

    @Synchronized
    fun mapHybridPages(basePage: ThemePage, pages: Collection<ThemePage>): ThemePage = basePage.apply {
        renderSignature = renderSignature()
        val baseHtml = mapRawString(basePage, AppPreferences.Main.TopicScrollMode.HYBRID)
        val range = findPostsListInnerRange(baseHtml)
        if (range == null) {
            html = mapString(basePage)
            return@apply
        }
        val knownHatId = basePage.topicHatPost?.id?.takeIf { it > 0 }
        val pageFragments = pages.sortedBy { it.pagination.current }.map { page ->
            TopicPrependedHatPolicy.preparePagePostsForNonFirstPageList(
                    page = page,
                    requestedPage = page.pagination.current,
                    knownHatId = knownHatId ?: page.topicHatPost?.id,
            )
            val postsInner = dedupePostContainersInPostsHtml(
                    extractPostsListInner(
                            mapRawString(page, AppPreferences.Main.TopicScrollMode.HYBRID)
                    ).orEmpty()
            )
            buildPageFragment(page.pagination.current, postsInner).also {
                page.postsFragmentHtml = it
            }
        }
        val replacement = buildString {
            append(infiniteStateContainer("top"))
            append(dedupePostContainersInPostsHtml(pageFragments.joinToString("")))
            append(infiniteStateContainer("bottom"))
        }
        html = baseHtml.substring(0, range.first) + replacement + baseHtml.substring(range.second)
    }

    @Synchronized
    fun mapString(page: ThemePage): String {
        val mode = mainPreferencesHolder.getTopicScrollMode()
        val html = mapRawString(page, mode)
        return if (mode == AppPreferences.Main.TopicScrollMode.HYBRID) {
            injectInfiniteScrollScaffold(html, page.pagination.current)
        } else {
            html
        }
    }

    @Synchronized
    fun mapPostsFragment(page: ThemePage): String {
        TopicPrependedHatPolicy.preparePagePostsForNonFirstPageList(page)
        val key = postsFragmentCacheKey(page)
        postsFragmentCache[key]?.let {
            page.postsFragmentHtml = it
            return it
        }
        val posts = dedupePostContainersInPostsHtml(
                extractPostsListInner(mapRawString(page, AppPreferences.Main.TopicScrollMode.HYBRID)).orEmpty()
        )
        return buildPageFragment(page.pagination.current, posts).also {
            postsFragmentCache[key] = it
            page.postsFragmentHtml = it
        }
    }

    @Synchronized
    fun renderSignature(): String {
        return listOf(
                TEMPLATE_RESOURCES_VERSION,
                templateManager.getThemeType(),
                templateManager.getThemeOverridesCss().hashCode().toString(),
                mainPreferencesHolder.getTopicScrollMode().name,
                mainPreferencesHolder.getTopicPostDensity().name,
                topicPreferencesHolder.getShowAvatars().toString(),
                topicPreferencesHolder.getCircleAvatars().toString(),
                topicPreferencesHolder.getForumBlacklist().joinToString(";") { it.stableKey() }
        ).joinToString("|")
    }

    private fun mapRawString(page: ThemePage, scrollMode: AppPreferences.Main.TopicScrollMode): String {
        val template = templateManager.getTemplate(TemplateManager.TEMPLATE_THEME)

        template.apply {
            templateManager.fillStaticStrings(this)
            setVariable("res_s_hat", context.getString(R.string.hat))
            setVariable("res_s_group", context.getString(R.string.res_s_group))
            setVariable("res_s_poll_all_votes_count", context.getString(R.string.poll_all_votes_count))
            setVariable("res_s_poll_vote_btn", context.getString(R.string.poll_vote_btn))
            setVariable("res_s_poll_results_btn", context.getString(R.string.poll_results_btn))
            setVariable("res_s_poll_show_btn", context.getString(R.string.poll_show_btn))
            setVariable("res_s_forum_blacklist_post_hidden", context.getString(R.string.forum_blacklist_post_hidden))
            setVariableOpt("res_s_forum_blacklist_posts_hidden", context.getString(R.string.forum_blacklist_posts_hidden))
        }
        val authData = authHolder.get()
        val authorized = authData.isAuth()
        val memberId = authData.userId
        template.apply {
            setVariableOpt("style_type", templateManager.getThemeType())
            setVariableOpt("theme_overrides_css", templateManager.getThemeOverridesCss())

            setVariableOpt("topic_title", htmlEncode(page.title))
            setVariableOpt("topic_description", htmlEncode(page.desc))
            val topicUrl = page.url?.takeIf { it.contains("showtopic=", ignoreCase = true) }
                    ?: "https://4pda.to/forum/index.php?showtopic=${page.id}"
            setVariableOpt("topic_url", topicUrl)
            setVariableOpt("topic_id_int", page.id)
            val overlayHatId = page.topicHatPost?.id?.takeIf { it > 0 }
                    ?: TopicPrependedHatPolicy.resolvePrependedHatId(page)
                    ?: 0
            setVariableOpt("topic_hat_post_id_int", overlayHatId)

            setVariableOpt("all_pages_int", page.pagination.all)
            setVariableOpt("posts_on_page_int", page.pagination.perPage)
            setVariableOpt("current_page_int", page.pagination.current)
            setVariableOpt("topic_scroll_mode", scrollMode.name.lowercase())
            setVariableOpt("topic_hybrid_scroll_bool", (scrollMode == AppPreferences.Main.TopicScrollMode.HYBRID).toString())
            setVariableOpt("debug_bool", BuildConfig.DEBUG.toString())
            setVariableOpt("top_chrome_padding_css_px", topChromePaddingCssPx().toString())

            setVariableOpt("authorized_bool", authorized.toString())
            setVariableOpt("is_curator_bool", false.toString())
            setVariableOpt("member_id_int", memberId)
            val fullAnchor = page.anchor ?: ""
            setVariableOpt("elem_to_scroll", fullAnchor)
            setVariableOpt("anchor_id", fullAnchor.removePrefix("entry"))
            setVariableOpt("body_type", "topic")
            setVariableOpt("post_density_class", topicPostDensityClass())

            setVariableOpt("in_favorite_bool", java.lang.Boolean.toString(page.isInFavorite))
            val isEnableAvatars = topicPreferencesHolder.getShowAvatars()
            setVariableOpt("enable_avatars_bool", java.lang.Boolean.toString(isEnableAvatars))
            setVariableOpt("enable_avatars", if (isEnableAvatars) "show_avatar" else "hide_avatar")
            setVariableOpt("avatar_type", if (topicPreferencesHolder.getCircleAvatars()) "circle_avatar" else "square_avatar")
            val renderModel = buildRenderModel(page)
            val forumBlacklist = topicPreferencesHolder.getForumBlacklist()
            val hatPost = renderModel.topicHeader
            hatPost?.let { rememberTopHatRatingSource(page, it) }
            val hatRatingUi = hatPost?.let { resolveTopicHatRatingUi(page, it, authorized, memberId) }
            var letterMatcher: Matcher? = null
            for (post in renderModel.posts) {
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
                } ?: post.nick?.takeIf { it.isNotEmpty() }?.take(1).orEmpty()

                setVariableOpt("nick_letter", letter)
                setVariableOpt("nick", htmlEncode(post.nick))
                setVariableOpt("curator", if (post.isCurator) "curator" else "")
                setVariableOpt("group_color", post.groupColor)
                setVariableOpt("group", post.group)
                setVariableOpt("reputation", post.reputation)
                setUserPostCountHtml(post)
                val dateText = Utils.formatForumDisplayDateTime(post.date, "theme.preview") ?: run {
                    // Шапка и нестандартные «даты» (например, «Шапка — обновляется…») —
                    // показываем сырую строку, чтобы под ником не было пустого места.
                    post.date?.trim().orEmpty()
                }
                setVariableOpt("date", dateText)
                setVariableOpt("number", post.number)

                //Post body
                setVariableOpt("hat_state_class", "")
                setVariableOpt("body", post.body)
                val isBlacklisted = isPostForumBlacklisted(post, forumBlacklist, memberId)

                //Post footer
                val ratingUi = resolvePostRatingUi(post, authorized, memberId, "post", page.canQuote)
                setVariableOpt("post_rating", htmlEncode(ratingUi.postRating))
                setVariableOpt("post_rating_state", "")
                setVariableOpt(
                        "post_rating_hidden_class",
                        if (themePostRatingToInt(ratingUi.postRating) == 0) "post_rating_hidden" else ""
                )
                setVariableOpt("blacklisted_post_class", if (isBlacklisted) "blacklisted_post" else "")
                setVariableOpt("post_class_extras", buildPostClassExtras(isBlacklisted, post.isOnline))

                if (isBlacklisted) {
                    addBlockOpt("blacklisted_stub_open")
                    addBlockOpt("blacklisted_post_body")
                    addPostInteractionBlocks(
                            ratingUi = ratingUi,
                            authorized = authorized,
                            post = post,
                            page = page,
                            footerBlock = "blacklisted_post_footer",
                    )
                    addBlockOpt("blacklisted_stub_close")
                } else {
                    addBlockOpt("visible_post_body")
                    addPostInteractionBlocks(
                            ratingUi = ratingUi,
                            authorized = authorized,
                            post = post,
                            page = page,
                            footerBlock = "visible_post_footer",
                    )
                }
                addBlockOpt("post")
            }
            if (hatPost != null && hatRatingUi != null) {
                addTopHatOverlayBlock(hatPost, page.posts, hatRatingUi, renderModel.overlayHeaderExpanded, authorized, letterMatcher)
                if (renderModel.shouldRenderInlineTopicHeader) {
                    addInlineTopHatBlock(hatPost, page.posts, hatRatingUi, renderModel.inlineHeaderExpanded, authorized, letterMatcher)
                }
            }

            //Poll block
            page.poll?.let { poll ->
                val showPollEntry = shouldShowTopTopicEntry(page)
                setVariableOpt("poll_state_class", if (showPollEntry && page.isPollOpen) "open" else "close")
                setVariableOpt("poll_overlay_state_class", if (!showPollEntry && page.isPollOpen) "open" else "close")
                val hasResultItems = poll.questions.any { question ->
                    question.questionItems.any { item -> item.percent > 0f || item.votes > 0 }
                }
                val hasChoiceInputs = poll.questions.any { question ->
                    question.questionItems.any { item ->
                        !item.name.isNullOrBlank() &&
                                (item.type.equals("radio", ignoreCase = true) ||
                                        item.type.equals("checkbox", ignoreCase = true))
                    }
                }
                val hasAnyInputNames = poll.questions.any { question ->
                    question.questionItems.any { item -> !item.name.isNullOrBlank() }
                }
                val isResult = poll.isResult ||
                        hasResultItems ||
                        (!hasAnyInputNames && poll.votesCount > 0)
                val canVote = !isResult && poll.canVote && hasChoiceInputs
                val pollResultsUrl = resolvePollResultsUrl(page, poll)
                val isPollResultsMode = isPollResultsMode(page.url)
                val showResultsAction = !isResult && canVote && (poll.showResultsButton || !pollResultsUrl.isNullOrBlank())
                val showPollAction = isResult && isPollResultsMode && poll.showPollButton
                val hasAnyAction = canVote || showResultsAction || showPollAction
                val isReadOnlyChoices = !isResult && !canVote && !hasAnyAction
                setVariableOpt("poll_type", if (isResult) "result" else if (isReadOnlyChoices) "default readonly" else "default")
                val pollDisabledAttr = if (isReadOnlyChoices) "disabled" else ""
                setVariableOpt("poll_form_action", ApiUtils.htmlEncode(poll.formAction?.takeIf { it.isNotBlank() } ?: "https://4pda.to/forum/index.php"))
                setVariableOpt("poll_form_method", if (poll.formMethod.equals("post", ignoreCase = true)) "post" else "get")
                setVariableOpt("poll_results_url", htmlEncode(pollResultsUrl.orEmpty()).orEmpty().replace("'", "&#39;"))
                setVariableOpt("poll_title", if (poll.title.isNullOrEmpty() || poll.title == "-") context.getString(R.string.poll) else poll.title)

                for (question in poll.questions) {
                    setVariableOpt("question_title", htmlEncode(question.title))

                    for (questionItem in question.questionItems) {
                            setVariableOpt("question_item_title", htmlEncode(questionItem.title))

                        if (isResult) {
                            setVariableOpt("question_item_votes", questionItem.votes)
                            setVariableOpt("question_item_percent", java.lang.Float.toString(questionItem.percent))
                            addBlockOpt("poll_overlay_result_item")
                            addBlockOpt("poll_result_item")
                        } else {
                            setVariableOpt("question_item_type", sanitizePollInputType(questionItem.type))
                            setVariableOpt("question_item_name", htmlEncode(questionItem.name))
                            setVariableOpt("question_item_value", htmlEncode(questionItem.value))
                            setVariableOpt("question_item_disabled", pollDisabledAttr)
                            addBlockOpt("poll_overlay_default_item")
                            addBlockOpt("poll_default_item")
                        }
                    }
                    addBlockOpt("poll_overlay_question_block")
                    addBlockOpt("poll_question_block")
                }
                setVariableOpt("poll_votes_count", poll.votesCount)
                if (isReadOnlyChoices) {
                    addBlockOpt("poll_overlay_unavailable_status")
                    addBlockOpt("poll_unavailable_status")
                }
                if (hasAnyAction) {
                    if (canVote) {
                        addBlockOpt("poll_overlay_vote_button")
                        addBlockOpt("poll_vote_button")
                    }
                    if (showResultsAction) {
                        addBlockOpt("poll_overlay_show_results_button")
                        addBlockOpt("poll_show_results_button")
                    }
                    if (showPollAction) {
                        addBlockOpt("poll_overlay_show_poll_button")
                        addBlockOpt("poll_show_poll_button")
                    }
                    addBlockOpt("poll_overlay_buttons")
                    addBlockOpt("poll_buttons")
                }
                val hiddenInputs = poll.hiddenInputs
                        .ifEmpty { listOf("addpoll" to "1") }
                        .filter { (name, _) -> name.isNotBlank() }
                for ((name, value) in hiddenInputs) {
                    setVariableOpt("poll_hidden_name", htmlEncode(name))
                    setVariableOpt("poll_hidden_value", htmlEncode(value))
                    addBlockOpt("poll_overlay_hidden_input")
                    addBlockOpt("poll_hidden_input")
                }
                addBlockOpt("poll_overlay_block")
                if (showPollEntry) {
                    addBlockOpt("poll_block")
                }
            }
            if (scrollMode == AppPreferences.Main.TopicScrollMode.CLASSIC) {
                addBottomPaginationBlock(this, page)
            }
        }

        val result = template.generateOutput()
        template.reset()
        return result
    }

    private fun addBottomPaginationBlock(template: MiniTemplator, page: ThemePage) {
        val currentPage = page.pagination.current.coerceAtLeast(1)
        val allPages = page.pagination.all.coerceAtLeast(currentPage)
        template.apply {
            setVariableOpt("bottom_pagination_current", currentPage)
            setVariableOpt("bottom_pagination_all", allPages)
            setVariableOpt("bottom_pagination_label", "$currentPage/$allPages")
            setVariableOpt(
                    "bottom_pagination_accessibility_label",
                    context.getString(R.string.pagination_current_page_desc, currentPage, allPages)
            )
            setVariableOpt("res_s_first", context.getString(R.string.pagination_first))
            setVariableOpt("res_s_prev", context.getString(R.string.pagination_prev))
            setVariableOpt("res_s_select_desc", context.getString(R.string.pagination_select_desc))
            setVariableOpt("res_s_select", context.getString(R.string.pagination_select))
            setVariableOpt("res_s_next", context.getString(R.string.pagination_next))
            setVariableOpt("res_s_last", context.getString(R.string.pagination_last))
            if (currentPage > 1) {
                addBlockOpt("bottom_pagination_first_enabled")
                addBlockOpt("bottom_pagination_prev_enabled")
            } else {
                addBlockOpt("bottom_pagination_first_disabled")
                addBlockOpt("bottom_pagination_prev_disabled")
            }
            if (currentPage < allPages) {
                addBlockOpt("bottom_pagination_next_enabled")
                addBlockOpt("bottom_pagination_last_enabled")
            } else {
                addBlockOpt("bottom_pagination_next_disabled")
                addBlockOpt("bottom_pagination_last_disabled")
            }
            addBlockOpt("bottom_pagination")
        }
    }

    private fun topicPostDensityClass(): String =
            TopicPostDensityPolicy.webBodyClass(mainPreferencesHolder.getTopicPostDensity())

    private fun topChromePaddingCssPx(): Int = 0

    private fun MiniTemplator.bindTopicHatPost(
            post: ThemePost,
            pagePosts: List<ThemePost>,
            ratingUi: PostRatingUi,
            reusableLetterMatcher: Matcher?
    ) {
        setVariableOpt("user_online", if (post.isOnline) "online" else "")
        setVariableOpt("post_id", post.id)
        setVariableOpt("user_id", post.userId)
        setVariableOpt("avatar", post.avatar)
        setVariableOpt("none_avatar", if (post.avatar.isNullOrEmpty()) "none_avatar" else "")
        val nickForLetter = post.nick.orEmpty()
        val letter = (reusableLetterMatcher?.reset(nickForLetter) ?: firstLetter.matcher(nickForLetter)).run {
            if (find()) group(1) else null
        } ?: post.nick?.takeIf { it.isNotEmpty() }?.take(1).orEmpty()
        setVariableOpt("nick_letter", letter)
        setVariableOpt("nick", htmlEncode(post.nick))
        setVariableOpt("curator", if (post.isCurator) "curator" else "")
        setVariableOpt("group_color", post.groupColor)
        setVariableOpt("group", post.group)
        setVariableOpt("reputation", post.reputation)
        setUserPostCountHtml(post, pagePosts)
        val dateText = Utils.formatForumDisplayDateTime(post.date, "theme.post") ?: run {
            post.date?.trim().orEmpty()
        }
        setVariableOpt("date", dateText)
        setVariableOpt("number", post.number)
        setVariableOpt("hat_state_class", "")
        setVariableOpt("body", post.body)

        setVariableOpt("post_rating", htmlEncode(ratingUi.postRating))
        setVariableOpt("post_rating_state", "")
        setVariableOpt(
                "post_rating_hidden_class",
                if (!ratingUi.hasServerPostRating) "post_rating_hidden" else ""
        )

    }

    private fun MiniTemplator.addTopHatOverlayBlock(
            post: ThemePost,
            pagePosts: List<ThemePost>,
            ratingUi: PostRatingUi,
            isCurrentlyOpen: Boolean,
            authorized: Boolean,
            reusableLetterMatcher: Matcher?
    ) {
        bindTopicHatPost(post, pagePosts, ratingUi, reusableLetterMatcher)
        setVariableOpt("top_hat_state_class", if (isCurrentlyOpen) "open" else "close")
        if (!authorized || post.canReport) {
            addBlockOpt("top_hat_report_block")
        }
        if (ratingUi.hasServerPostRating || (post.canQuote && !ratingUi.isOwnPost)) {
            addBlockOpt("top_hat_reply_quote_row")
        }
        if (!authorized || post.canDelete) {
            addBlockOpt("top_hat_delete_block")
        }
        if (!authorized || post.canEdit) {
            addBlockOpt("top_hat_edit_block")
        }
        addBlockOpt("top_hat")
    }

    private fun MiniTemplator.addInlineTopHatBlock(
            post: ThemePost,
            pagePosts: List<ThemePost>,
            ratingUi: PostRatingUi,
            inlineHatOpen: Boolean,
            authorized: Boolean,
            reusableLetterMatcher: Matcher?
    ) {
        bindTopicHatPost(post, pagePosts, ratingUi, reusableLetterMatcher)
        setVariableOpt("inline_hat_state_class", if (inlineHatOpen) "open" else "close")
        if (!authorized || post.canReport) {
            addBlockOpt("top_hat_entry_report_block")
        }
        if (ratingUi.hasServerPostRating || (post.canQuote && !ratingUi.isOwnPost)) {
            addBlockOpt("top_hat_entry_reply_quote_row")
        }
        if (!authorized || post.canDelete) {
            addBlockOpt("top_hat_entry_delete_block")
        }
        if (!authorized || post.canEdit) {
            addBlockOpt("top_hat_entry_edit_block")
        }
        addBlockOpt("top_hat_entry")
    }

    private fun injectInfiniteScrollScaffold(html: String, pageNumber: Int): String {
        val range = findPostsListInnerRange(html) ?: return html
        val posts = html.substring(range.first, range.second)
        val replacement = buildString {
            append(infiniteStateContainer("top"))
            append(buildPageFragment(pageNumber, posts))
            append(infiniteStateContainer("bottom"))
        }
        return html.substring(0, range.first) + replacement + html.substring(range.second)
    }

    private fun extractPostsListInner(html: String): String? {
        val range = findPostsListInnerRange(html) ?: return null
        return html.substring(range.first, range.second)
    }

    private fun findPostsListInnerRange(html: String): Pair<Int, Int>? {
        val markerStart = html.indexOf(POSTS_LIST_START_MARKER).takeIf { it >= 0 }
        if (markerStart != null) {
            val start = markerStart + POSTS_LIST_START_MARKER.length
            val end = html.indexOf(POSTS_LIST_END_MARKER, start).takeIf { it >= 0 } ?: return null
            return start to end
        }

        val open = Regex("<div\\b(?=[^>]*\\bclass\\s*=\\s*['\"][^'\"]*\\bposts_list\\b)[^>]*>", RegexOption.IGNORE_CASE)
                .find(html)
                ?: return null
        val start = open.range.last + 1
        val bottomPagination = html.indexOf("<!-- \$BeginBlock bottom_pagination -->", start).takeIf { it >= 0 }
        val bottomSpacer = html.indexOf("<div id=\"bottom_chrome_spacer\"", start).takeIf { it >= 0 }
        val end = bottomPagination ?: bottomSpacer ?: return null
        return start to end
    }

    private fun buildPageFragment(pageNumber: Int, postsHtml: String): String {
        return buildString {
            append("\n            <div class=\"theme_page_container\" data-page-number=\"")
            append(pageNumber)
            append("\">\n")
            append(postsHtml)
            append("\n            </div>\n")
        }
    }

    private fun buildRenderModel(page: ThemePage): TopicPageRenderModel {
        val pageNumber = page.pagination.current.coerceAtLeast(1)
        val topicHeader = resolveTopicHatPost(page)
        val shouldRenderInlineTopicHeader = topicHeader != null &&
                TopicInlineHatOpenPolicy.shouldRenderInlineBlock(page)
        val posts = dedupePostsById(
                TopicPrependedHatPolicy.filterPostsForPageList(
                        page = page,
                        requestedPage = pageNumber,
                        knownHatId = topicHeader?.id,
                )
        )
        return TopicPageRenderModel(
                pageNumber = pageNumber,
                posts = posts,
                topicHeader = topicHeader,
                shouldRenderInlineTopicHeader = shouldRenderInlineTopicHeader,
                overlayHeaderExpanded = page.isHatOpen && page.topicHatPost?.id?.let { it > 0 } == true,
                inlineHeaderExpanded = page.isInlineHatOpen
        )
    }

    /** Extracts the floating overlay host markup for JS injection without a full WebView reload. */
    fun extractTopHatOverlayHostHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val marker = "top_hat_overlay_host"
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) return null
        val start = html.lastIndexOf("<div", markerIndex)
        if (start < 0) return null
        var depth = 0
        var index = start
        while (index < html.length) {
            val nextOpen = html.indexOf("<div", index)
            val nextClose = html.indexOf("</div>", index)
            if (nextOpen != -1 && nextOpen < nextClose) {
                depth++
                index = nextOpen + 4
                continue
            }
            if (nextClose == -1) return null
            depth--
            index = nextClose + 6
            if (depth == 0) {
                return html.substring(start, index)
            }
        }
        return null
    }

    private fun infiniteStateContainer(position: String): String {
        return "\n            <div id=\"theme_infinite_${position}\" " +
                "class=\"theme_infinite_state\" data-position=\"$position\" " +
                "style=\"display:none;margin:0.75em 0;text-align:center;font-size:0.875em;opacity:0.8;\"></div>\n"
    }

    private fun postsFragmentCacheKey(page: ThemePage): String {
        return buildString {
            append(page.id)
            append('|')
            append(page.pagination.current)
            append('|')
            append(page.pagination.perPage)
            append('|')
            append(renderSignature())
            append('|')
            append(page.poll?.hashCode() ?: 0)
            append('|')
            append(page.topicHatPost?.id ?: 0)
            append('|')
            append(page.canQuote)
            append('|')
            append(page.posts.joinToString(separator = ",") { post ->
                "${post.id}:${post.number}:${postHash(post)}"
            })
        }
    }

    private fun postHash(post: ThemePost): String {
        val raw = buildString {
            append(post.id).append('|')
            append(post.number).append('|')
            append(post.date.orEmpty()).append('|')
            append(post.nick.orEmpty()).append('|')
            append(post.groupColor.orEmpty()).append('|')
            append(post.group.orEmpty()).append('|')
            append(post.reputation.orEmpty()).append('|')
            append(post.userPostCount?.toString().orEmpty()).append('|')
            append(post.postRating.orEmpty()).append('|')
            append(post.canPlusPostRating).append('|')
            append(post.canMinusPostRating).append('|')
            append(post.canReport).append('|')
            append(post.canEdit).append('|')
            append(post.canDelete).append('|')
            append(post.canQuote).append('|')
            append(post.body.orEmpty())
        }
        return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }

    private fun resolveTopicHatPost(page: ThemePage): ThemePost? =
            page.topicHatPost?.takeIf { it.id > 0 }

    private fun resolveTopicHatRatingUi(
            page: ThemePage,
            hatPost: ThemePost,
            authorized: Boolean,
            memberId: Int
    ): PostRatingUi {
        val ratingSource = resolveTopicHatActionPost(page, hatPost)
        return resolvePostRatingUi(ratingSource, authorized, memberId, "top_hat")
    }

    private fun resolveTopicHatActionPost(page: ThemePage, hatPost: ThemePost): ThemePost {
        val candidates = listOfNotNull(
                page.posts.firstOrNull { it.id == hatPost.id && it !== hatPost },
                page.posts.firstOrNull { it.number == 1 && it.id > 0 && it !== hatPost },
                topHatRatingSourceCache[page.id]?.takeIf { it.id == hatPost.id }?.toThemePost(),
                hatPost
        )
        val ratingSource = candidates.firstOrNull { !it.postRating.isNullOrBlank() }
        return ThemePost().also { resolved ->
            resolved.id = hatPost.id
            resolved.userId = (ratingSource ?: candidates.first()).userId
            resolved.postRating = ratingSource?.postRating
        }
    }

    private fun rememberTopHatRatingSource(page: ThemePage, hatPost: ThemePost) {
        if (page.id <= 0 || hatPost.id <= 0) return
        val source = listOfNotNull(
                hatPost,
                page.posts.firstOrNull { it.id == hatPost.id && it !== hatPost },
                page.posts.firstOrNull { it.number == 1 && it.id > 0 && it !== hatPost }
        ).firstOrNull { it.hasPostRatingMetadata() } ?: return
        topHatRatingSourceCache[page.id] = TopHatRatingSource(
                id = source.id,
                userId = source.userId,
                postRating = source.postRating,
                canPlusPostRating = source.canPlusPostRating,
                canMinusPostRating = source.canMinusPostRating
        )
    }

    private fun ThemePost.hasPostRatingMetadata(): Boolean =
            !postRating.isNullOrBlank() || hasParsedPostRatingActions()

    private fun ThemePost.hasParsedPostRatingActions(): Boolean =
            canPlusPostRating || canMinusPostRating

    private fun shouldShowTopTopicEntry(page: ThemePage): Boolean =
            page.pagination.current == 1 && !hasPositiveSt(page.url)

    private fun isTopicHeaderSourcePost(post: ThemePost, hatPost: ThemePost, pageNumber: Int): Boolean {
        if (hatPost.id <= 0) return false
        if (post.id == hatPost.id) return true
        if (pageNumber == 1 && post.number == 1) return true
        return false
    }

    private fun hasPositiveSt(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            android.net.Uri.parse(url).getQueryParameter("st")?.toIntOrNull()?.let { it > 0 } == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun themePostRatingToInt(raw: String): Int {
        val s = raw.trim()
                .replace("+", "")
                .replace("\u2212", "-")
                .replace("\u2013", "-")
        return s.toIntOrNull() ?: 0
    }

    private fun MiniTemplator.setUserPostCountHtml(post: ThemePost) {
        setUserPostCountHtml(post, emptyList())
    }

    private fun MiniTemplator.setUserPostCountHtml(post: ThemePost, fallbackPosts: List<ThemePost>) {
        val count = post.userPostCount?.takeIf { it > 0 } ?: fallbackPosts.firstNotNullOfOrNull { candidate ->
            candidate.userPostCount?.takeIf {
                it > 0 &&
                candidate !== post &&
                        (candidate.id == post.id || (post.number > 0 && candidate.number == post.number))
            }
        }
        setVariableOpt("user_post_count_html", renderUserPostCountHtml(count))
    }

    private fun renderUserPostCountHtml(count: Int?): String {
        val safeCount = count?.takeIf { it > 0 } ?: return """<span class="inf user_post_count user_post_count_placeholder" aria-hidden="true"><svg class="user_post_count_icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.5-5.6a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5z"/></svg><span></span></span>"""
        val accessibility = "Сообщений: $safeCount"
        return """<span class="inf user_post_count" aria-label="$accessibility"><svg class="user_post_count_icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.5-5.6a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5z"/></svg><span>$safeCount</span></span>"""
    }

    private fun MiniTemplator.addPostInteractionBlocks(
            ratingUi: PostRatingUi,
            authorized: Boolean,
            post: ThemePost,
            page: ThemePage,
            footerBlock: String,
    ) {
        val isBlacklistedFooter = footerBlock == "blacklisted_post_footer"
        val reportBlock = if (isBlacklistedFooter) "blacklisted_report_block" else "report_block"
        val replyQuoteRowBlock = if (isBlacklistedFooter) "blacklisted_reply_quote_row" else "reply_quote_row"
        val repUpBlock = if (isBlacklistedFooter) "blacklisted_rep_up_block" else "rep_up_block"
        val repDownBlock = if (isBlacklistedFooter) "blacklisted_rep_down_block" else "rep_down_block"
        val deleteBlock = if (isBlacklistedFooter) "blacklisted_delete_block" else "delete_block"
        val editBlock = if (isBlacklistedFooter) "blacklisted_edit_block" else "edit_block"
        if (!authorized || post.canReport) {
            addBlockOpt(reportBlock)
        }
        if ((ratingUi.hasServerPostRating && ratingUi.hasNonZeroServerPostRating) ||
                ratingUi.hasPostRatingActions ||
                (page.canQuote && !ratingUi.isOwnPost)
        ) {
            // Один блок без вложенных reply_block/quote_full_block — иначе MiniTemplator
            // подставляет ${post_id} от предыдущей итерации поста.
            if (ratingUi.hasPostRatingActions) {
                if (ratingUi.canPlusPostRating) {
                    addBlockOpt(repUpBlock)
                }
                if (ratingUi.canMinusPostRating) {
                    addBlockOpt(repDownBlock)
                }
            }
            addBlockOpt(replyQuoteRowBlock)
        }
        if (!authorized || post.canDelete) {
            addBlockOpt(deleteBlock)
        }
        if (!authorized || post.canEdit) {
            addBlockOpt(editBlock)
        }
        addBlockOpt(footerBlock)
    }

    private fun isPostForumBlacklisted(
            post: ThemePost,
            blacklist: List<ForumBlacklistedUser>,
            currentUserId: Int
    ): Boolean {
        if (post.userId > 0 && post.userId == currentUserId) return false
        return blacklist.any { it.matches(post.userId, post.nick) }
    }

    private fun buildPostClassExtras(isBlacklisted: Boolean, isOnline: Boolean): String {
        return buildString {
            if (isBlacklisted) append(" blacklisted_post")
            if (isOnline) append(" online")
        }
    }

    private fun htmlEncode(value: String?): String? = value?.let { ApiUtils.htmlEncode(it) ?: it }

    private fun resolvePostRatingUi(
            post: ThemePost,
            authorized: Boolean,
            memberId: Int,
            source: String,
            canQuote: Boolean = false
    ): PostRatingUi {
        val hasServerPostRating = !post.postRating.isNullOrBlank()
        val postRating = post.postRating.takeUnless { it.isNullOrBlank() } ?: "0"
        val hasNonZeroServerPostRating = themePostRatingToInt(postRating) != 0
        val isTopHatSource = source.startsWith("top_hat")
        val isOwnPost = authorized &&
                memberId != AuthData.NO_ID &&
                post.userId != AuthData.NO_ID &&
                post.userId == memberId
        val hasParsedPostRatingActions = post.canPlusPostRating || post.canMinusPostRating
        val metadataMissing = !hasServerPostRating && !hasParsedPostRatingActions
        // Mobile topic HTML often omits ka_p / post_action until desktop metadata merge; if reply/quote
        // is available, still render +/- controls so the footer is not empty on some posts only.
        val allowQuoteFallback = !isTopHatSource && canQuote && metadataMissing && !isOwnPost
        val canRatePost = authorized && !isOwnPost &&
                (hasServerPostRating || hasParsedPostRatingActions || allowQuoteFallback)
        val fallbackPostRatingActions = !isTopHatSource && !hasParsedPostRatingActions &&
                (hasServerPostRating || allowQuoteFallback)
        val canPlusPostRating = canRatePost && (post.canPlusPostRating || fallbackPostRatingActions)
        val canMinusPostRating = canRatePost && (post.canMinusPostRating || fallbackPostRatingActions)

        if (BuildConfig.DEBUG && isTopHatSource && authorized && !isOwnPost && !canPlusPostRating && !canMinusPostRating) {
            Log.d(
                    "ThemeRatingUi",
                    "suppress source=$source post=${post.id} user=${post.userId} hasRating=$hasServerPostRating rating=$postRating parsedPlus=${post.canPlusPostRating} parsedMinus=${post.canMinusPostRating}"
            )
        }

        return PostRatingUi(
                postRating = postRating,
                hasServerPostRating = hasServerPostRating,
                hasNonZeroServerPostRating = hasNonZeroServerPostRating,
                isOwnPost = isOwnPost,
                canPlusPostRating = canPlusPostRating,
                canMinusPostRating = canMinusPostRating
        )
    }

    private fun sanitizePollInputType(type: String?): String {
        return if (type.equals("checkbox", ignoreCase = true)) "checkbox" else "radio"
    }

    private fun resolvePollResultsUrl(page: ThemePage, poll: forpdateam.ru.forpda.entity.remote.theme.Poll): String? {
        poll.resultsUrl?.takeIf { it.isNotBlank() }?.let { return it }
        val topicUrl = page.url?.takeIf { it.contains("showtopic=", ignoreCase = true) }
                ?: page.id.takeIf { it > 0 }?.let { "https://4pda.to/forum/index.php?showtopic=$it" }
                ?: return null
        return appendPollModeShow(topicUrl)
    }

    private fun isPollResultsMode(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return Regex("""(?i)(?:[?&])mode=show(?:[&#]|$)""").containsMatchIn(url)
    }

    private fun appendPollModeShow(url: String): String {
        val hashIndex = url.indexOf('#')
        val base = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val hash = if (hashIndex >= 0) url.substring(hashIndex) else ""
        val withoutMode = base
                .replace(Regex("""([?&])mode=show(&)?""")) { match ->
                    val prefix = match.groupValues[1]
                    val hasTrailing = match.groupValues.getOrNull(2) == "&"
                    if (hasTrailing) prefix else ""
                }
                .trimEnd('?', '&')
        val separator = if (withoutMode.contains("?")) "&" else "?"
        return "$withoutMode${separator}mode=show$hash"
    }

    private fun dedupePostsById(posts: List<ThemePost>): List<ThemePost> {
        val seen = LinkedHashSet<Int>()
        return posts.filter { post ->
            val postId = post.id
            if (postId <= 0) {
                true
            } else if (seen.add(postId)) {
                true
            } else {
                false
            }
        }
    }

    private fun dedupePostContainersInPostsHtml(postsHtml: String): String {
        if (postsHtml.isBlank()) return postsHtml
        val postIdPattern = Regex(
                """<div\b(?=[^>]*\bclass="[^"]*\bpost_container\b)(?![^>]*\btopic_hat_entry\b)(?![^>]*\btop_hat_entry\b)[^>]*\bdata-post-id="(\d+)"[^>]*>""",
                RegexOption.IGNORE_CASE,
        )
        val seen = LinkedHashSet<String>()
        val matches = postIdPattern.findAll(postsHtml).toList()
        if (matches.size <= 1) return postsHtml
        val removeRanges = mutableListOf<IntRange>()
        for (match in matches) {
            val postId = match.groupValues[1]
            if (!seen.add(postId)) {
                val start = match.range.first
                val end = findMatchingDivClose(postsHtml, start)
                if (end > start) {
                    removeRanges.add(start until end)
                }
            }
        }
        if (removeRanges.isEmpty()) return postsHtml
        return buildString {
            var cursor = 0
            for (range in removeRanges.sortedBy { it.first }) {
                if (range.first < cursor) continue
                append(postsHtml, cursor, range.first)
                cursor = range.last + 1
            }
            append(postsHtml, cursor, postsHtml.length)
        }
    }

    private fun findMatchingDivClose(html: String, openIndex: Int): Int {
        val openTagEnd = html.indexOf('>', openIndex)
        if (openTagEnd < 0) return -1
        var depth = 1
        var cursor = openTagEnd + 1
        val openPattern = Regex("""<div\b""", RegexOption.IGNORE_CASE)
        val closePattern = Regex("""</div>""", RegexOption.IGNORE_CASE)
        while (cursor < html.length && depth > 0) {
            val nextOpen = openPattern.find(html, cursor)?.range?.first ?: html.length
            val nextClose = closePattern.find(html, cursor)?.range?.first ?: html.length
            if (nextClose < nextOpen) {
                depth--
                cursor = nextClose + "</div>".length
                if (depth == 0) return cursor
            } else {
                depth++
                cursor = nextOpen + 4
            }
        }
        return -1
    }

}