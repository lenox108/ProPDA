package forpdateam.ru.forpda.model.data.remote.api.theme

import android.util.Pair
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.absolutizeFourPdaForumHref
import forpdateam.ru.forpda.common.renderBbcodeLineBreakTagsInPostHtml
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.*
import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import forpdateam.ru.forpda.presentation.theme.TopicPrependedHatPolicy
import forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import timber.log.Timber
import java.util.regex.Matcher

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

class ThemeParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private companion object {
        const val UNREAD_OPEN_ANCHOR_TAG = "FPDA_THEME_UNREAD_OPEN"
        /** U-02: resolver exits that suppress the unread target by classifying the redirect bookmark. */
        val UNREAD_SUPPRESSED_REASONS = setOf(
                "all_read_bottom_redirect",
                "page_top_redirect_no_unread",
        )
        val USER_POST_COUNT_RAW_PATTERNS = listOf(
                Regex("""(?is)\bdata-member-posts\b\s*=\s*["']?([0-9][0-9\s.,]*)"""),
                Regex("""(?is)(?:[Сс]ообщени[йя]|[Пп]ост(?:ов|а|ы)?|[Pp]osts?)(?![а-яА-Яa-zA-Z])(?:\s|&nbsp;|&#160;|&#x0?A0;|:|</?[^>]+>)*([0-9][0-9\s.,]*)""")
        )
        val USER_POST_COUNT_TEXT_PATTERNS = listOf(
                Regex("""(?i)(?:сообщени[йя]|пост(?:ов|а|ы)?|posts?)(?![а-яa-z])\s*:?\s*([0-9][0-9\s.,]*)""")
        )
        val USER_POST_COUNT_INFO_START_PATTERN = Regex("""(?is)<(?:span|div)\b[^>]*(?:\bpost_user_info\b|\bclass\s*=\s*["'][^"']*\bpostdetails\b[^"']*["'])[^>]*>""")
        val POST_ENTRY_ANCHOR_PATTERN = Regex("""(?is)<(?:a|div)\b[^>]*(?:\bname|\bid)\s*=\s*["']entry(\d+)["'][^>]*>""")

        // Hoisted out of String.asUserPostCountText()/toUserPostCount(): those
        // helpers run once per post (see parseUserPostCountsByPostId loop), so
        // compiling these literal patterns per call was needless per-post work.
        // Patterns are byte-for-byte identical to the former inline Regex(...).
        val USER_POST_COUNT_BR_TAG = Regex("""(?is)<br\b[^>]*>""")
        val USER_POST_COUNT_HTML_TAG = Regex("""(?is)<[^>]+>""")
        val USER_POST_COUNT_NBSP = Regex("""(?i)&nbsp;|&#160;|&#x0?A0;""")
        val USER_POST_COUNT_WHITESPACE = Regex("""\s+""")
        val USER_POST_COUNT_NON_DIGIT = Regex("""[^\d]""")
    }

    private val scope = ParserPatterns.Topic
    private val metadataParser = ThemePageMetadataParser()

    /**
     * @param initialRequestUrl исходный URL запроса (до редиректа). Нужен для view=getnewpost: в Location нет #entry,
     * OkHttp отдаёт финальный URL без фрагмента — иначе elem_to_scroll пустой и скролл к непрочитанному не срабатывает.
     */
    fun parsePage(
            response: String,
            argUrl: String,
            hatOpen: Boolean = false,
            pollOpen: Boolean = false,
            initialRequestUrl: String? = null,
            openFromUnreadListHint: Boolean = false
    ): ThemePage = ThemePage().also { page ->
        page.isHatOpen = hatOpen
        page.isPollOpen = pollOpen
        page.url = argUrl

        // findpost / act=findpost: id поста только в исходном URL; редирект даёт #entry… или &p= не того поста —
        // раньше scroll_anchor по argUrl заполнял anchors первым, addEntry…(initial) не вызывался, а anchor = последний в списке.
        val findpostPostId = initialRequestUrl
                ?.takeIf { u ->
                    u.contains("view=findpost", ignoreCase = true) || u.contains("act=findpost", ignoreCase = true)
                }
                ?.let { ThemeApi.extractPostIdFromTopicUrl(it) }

        val wantsUnreadScroll = initialRequestUrl?.contains("view=getnewpost", ignoreCase = true) == true

        if (findpostPostId != null) {
            page.addAnchor("entry$findpostPostId")
            // Mirrors the ensureGetNewPostScrollAnchor anchorPostId stamping: without
            // this, history.push logs `anchor=null` and the F8 dedupe in
            // ThemeHistoryController.saveToHistory compares a real anchor against null
            // and never fires (e.g. findpost reload after getnewpost redirect for
            // 1103268/1115025). The runtime still works because getAnchorPostId()
            // falls back to `anchor`, but the dedupe + ReadStateTrace stay wrong.
            page.anchorPostId = findpostPostId.toString()
        } else if (wantsUnreadScroll) {
            // view=getnewpost: редирект часто с #entry на первый пост страницы — якорь попадал в anchors до
            // ensureGetNewPostScrollAnchor, тот видел непустой список и не искал непрочитанное (остаётся «последняя страница, первый пост»).
            val initGetNew = initialRequestUrl
            if (!initGetNew.isNullOrBlank()) {
                patternProvider
                        .getPattern(scope.scope, scope.scroll_anchor)
                        .matcher(initGetNew)
                        .findAll {
                            it.group(1)?.let { a ->
                                page.addAnchor(if (a.startsWith("entry")) a else "entry$a")
                            }
                        }
                ThemeApi.addEntryAnchorFromPostParamsIfEmpty(page, initGetNew)
            }
            // Не подмешиваем scroll_anchor / p= из финального argUrl — только разбор HTML в ensureGetNewPostScrollAnchor.
        } else {
            patternProvider
                    .getPattern(scope.scope, scope.scroll_anchor)
                    .matcher(argUrl)
                    .findAll {
                        it.group(1)?.let { a -> page.addAnchor(a) }
                    }
            // Фрагмент #entry… иногда только в исходном URL (редирект обрезает hash).
            val initOther = initialRequestUrl
            if (page.anchors.isEmpty() && !initOther.isNullOrBlank()) {
                patternProvider
                        .getPattern(scope.scope, scope.scroll_anchor)
                        .matcher(initOther)
                        .findAll {
                            it.group(1)?.let { a -> page.addAnchor(a) }
                        }
            }

            // Сначала исходный запрос: приоритет намерения пользователя (findpost&p=…).
            if (!initOther.isNullOrBlank()) {
                ThemeApi.addEntryAnchorFromPostParamsIfEmpty(page, initOther)
            }
            ThemeApi.addEntryAnchorFromPostParamsIfEmpty(page, argUrl)
        }

        patternProvider
                .getPattern(scope.scope, scope.topic_id)
                .matcher(response)
                .findOnce {
                    page.forumId = it.group(1).orEmpty().toIntOrNull() ?: 0
                    page.id = it.group(2).orEmpty().toIntOrNull() ?: 0
                }

        // Fallback: если topic_id не найден через regex, пробуем через стабильные атрибуты
        if (page.id == 0) {
            metadataParser.parseTopicIds(response)?.let {
                page.id = it.first
                page.forumId = it.second
            }
        }

        page.pagination = Pagination.parseForum(response)

        patternProvider
                .getPattern(scope.scope, scope.title)
                .matcher(response)
                .findOnce {
                    page.title = it.group(1).orEmpty().fromHtml()
                    page.desc = it.group(2).orEmpty().fromHtml()
                }

        // Fallback: если title не найден через regex, пробуем через стабильные атрибуты
        if (page.title.isNullOrEmpty()) {
            metadataParser.parseTitle(response)?.let {
                page.title = it
            }
        }

        patternProvider
                .getPattern(scope.scope, scope.already_in_fav)
                .matcher(response)
                .findOnce {
                    page.isInFavorite = true
                    patternProvider
                            .getPattern(scope.scope, scope.fav_id)
                            .matcher(response)
                            .findOnce {
                                page.favId = it.group(1).orEmpty().toIntOrNull() ?: 0
                            }
                }

        val postRatings = ThemeRatingParser.parsePostRatings(response)
        val postVoteControls = ThemeRatingParser.parsePostVoteControls(response)
        val userPostCounts = parseUserPostCountsByPostId(response)
        var attachMatcher: Matcher? = null
        val posts = patternProvider
                .getPattern(scope.scope, scope.posts)
                .matcher(response)
                .map { matcher ->
                    ThemePost().apply {
                        fun g(i: Int): String = matcher.group(i).orEmpty()
                        topicId = page.id
                        forumId = page.forumId
                        id = g(1).toIntOrNull() ?: return@map null
                        date = g(5)
                        number = g(6).toIntOrNull() ?: return@map null
                        isOnline = g(7).contains("green")
                        g(8).also {
                            avatar = if (it.isNotEmpty()) "https://s.4pda.to/forum/uploads/$it" else it
                        }
                        nick = g(9).fromHtml()
                        userId = g(10).toIntOrNull() ?: return@map null
                        isCurator = matcher.group(11) != null
                        groupColor = g(12)
                        group = g(13)
                        userPostCount = userPostCounts[id] ?: parseUserPostCount(matcher.group(0).orEmpty())
                        canMinusRep = g(14).isNotEmpty()
                        reputation = g(15)
                        postRating = postRatings[id]
                        postVoteControls[id]?.let {
                            canPlusPostRating = it.canPlus
                            canMinusPostRating = it.canMinus
                        }
                        canPlusRep = g(16).isNotEmpty()
                        canReport = g(17).isNotEmpty()
                        canEdit = g(18).isNotEmpty()
                        canDelete = g(19).isNotEmpty()
                        page.canQuote = g(20).isNotEmpty()
                        canQuote = page.canQuote
                        val rawBody = g(21)
                        body = renderBbcodeLineBreakTagsInPostHtml(rawBody)
                        attachMatcher = attachMatcher?.reset(rawBody) ?: patternProvider
                                .getPattern(scope.scope, scope.attached_images)
                                .matcher(rawBody)
                        attachMatcher
                                ?.findAll {
                                    attachImages.add(Pair("https://${it.group(1)}", it.group(2).orEmpty()))
                                }
                    }

                    /*if (isCurator() && getUserId() == ClientHelper.getUserId())
                        page.setCurator(true);*/
                }
                .filterNotNull()
        page.posts.addAll(posts)
        logUserPostCountSourceDiagnostics(page, response)
        applyPaginationFromUrlIfNeeded(page, argUrl, initialRequestUrl)

        patternProvider
                .getPattern(scope.scope, scope.poll_main)
                .matcher(response)
                .findOnce { matcher ->
                    val pollBlock = matcher.group().orEmpty()
                    val votesCount = matcher.group(3).orEmpty().toIntOrNull()
                            ?: parsePollVotesCount(pollBlock)
                    val voteQuestions = parseGenericPollQuestions(pollBlock)
                    val resultQuestions = parsePollResultQuestions(matcher.group(2).orEmpty(), pollBlock)

                    val poll = Poll()
                    poll.isResult = voteQuestions.isEmpty() && resultQuestions.isNotEmpty()
                    poll.title = matcher.group(1).orEmpty().fromHtml()
                    poll.formAction = parsePollFormAction(pollBlock)
                    poll.formMethod = parsePollFormMethod(pollBlock)
                    poll.hiddenInputs.addAll(parsePollHiddenInputs(pollBlock))

                    poll.questions.addAll(
                            when {
                                voteQuestions.isNotEmpty() -> voteQuestions
                                resultQuestions.isNotEmpty() -> resultQuestions
                                else -> parseReadonlyPollQuestions(matcher.group(2).orEmpty())
                            }
                    )

                    patternProvider
                            .getPattern(scope.scope, scope.poll_buttons)
                            .matcher(matcher.group(4).orEmpty())
                            .findAll {
                                val value = it.group(1).orEmpty()
                                when {
                                    value.contains("Голосовать") -> poll.voteButton = true
                                    value.contains("результаты") -> poll.showResultsButton = true
                                    value.contains("пункты опроса") -> poll.showPollButton = true
                                }
                            }
                    if (!poll.isResult && poll.hasVoteInputs) {
                        poll.voteButton = true
                    }
                    poll.resultsUrl = parsePollResultsUrl(pollBlock, argUrl, allowTopicFallback = poll.showResultsButton)
                    normalizePollActions(poll, argUrl)

                    poll.votesCount = votesCount
                    page.poll = poll
                }
        if (page.poll == null) {
            page.poll = parseGenericPoll(response, argUrl)
        }
        ensureGetNewPostScrollAnchor(page, response, initialRequestUrl, openFromUnreadListHint)
        page.openSessionKind = TopicUnreadOpenPolicy.resolveOpenSessionKindFromPage(
                page = page,
                initialRequestUrl = initialRequestUrl,
                openFromUnreadListHint = openFromUnreadListHint,
        ).name
        val htmlUnreadIds = ThemeApi.collectUnreadPostEntryIds(
                response,
                resolvePrependedHatEntryIdForAnchor(page)
        )
        ThemePostReadStateDiagnostics.postsMapped(
                topicId = page.id,
                parsedPostCount = page.posts.size,
                htmlUnreadStyledCount = htmlUnreadIds.size
        )
        return page
    }

    fun parseUserPostCountsByPostId(pageHtml: String): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        val anchors = POST_ENTRY_ANCHOR_PATTERN
                .findAll(pageHtml)
                .toList()
        anchors.forEachIndexed { index, match ->
            val postId = match.groups[1]?.value?.toIntOrNull() ?: return@forEachIndexed
            val end = anchors.getOrNull(index + 1)?.range?.first
                    ?: findTopicPostTailIndex(pageHtml, match.range.last + 1)
                    ?: pageHtml.length
            if (end <= match.range.first) return@forEachIndexed
            parseUserPostCount(pageHtml.substring(match.range.first, end))?.let { result[postId] = it }
        }
        return result
    }

    private fun logUserPostCountSourceDiagnostics(page: ThemePage, response: String) {
        if (!BuildConfig.DEBUG || page.posts.isEmpty()) return
        val missing = page.posts.count { it.userPostCount == null }
        if (missing == 0) return
        Timber.tag("ThemeUserPosts").d(
                "parsePage userPostCount missing=%d/%d hasPostUserInfo=%s hasCountLabel=%s hasDataMemberPosts=%s bodyLen=%d parsedSample=%s",
                missing,
                page.posts.size,
                response.contains("post_user_info", ignoreCase = true),
                response.contains(Regex("""(?i)Сообщени[йя]|Posts?""")),
                response.contains("data-member-posts", ignoreCase = true),
                response.length,
                page.posts.asSequence().take(5).joinToString(prefix = "[", postfix = "]") { "${it.id}=${it.userPostCount ?: "-"}" }
        )
    }

    private fun findTopicPostTailIndex(pageHtml: String, start: Int): Int? {
        return listOf(
                pageHtml.indexOf("""<div class="topic_foot_nav"""", start, ignoreCase = true),
                pageHtml.indexOf("""<!-- TABLE FOOTER -->""", start, ignoreCase = true),
                pageHtml.indexOf("""<div><div class="pagination"""", start, ignoreCase = true),
                pageHtml.indexOf("""<div></div><br""", start, ignoreCase = true)
        ).filter { it >= 0 }.minOrNull()
    }

    private fun parseUserPostCount(postHtml: String): Int? {
        USER_POST_COUNT_RAW_PATTERNS.first()
                .find(postHtml)
                ?.groups
                ?.get(1)
                ?.value
                ?.toUserPostCount()
                ?.let { return it }
        userPostCountInfoBlocks(postHtml).forEach { infoBlock ->
            val rawNumber = USER_POST_COUNT_RAW_PATTERNS
                    .asSequence()
                    .mapNotNull { pattern ->
                        pattern.find(infoBlock)?.groups?.get(1)?.value
                    }
                    .firstOrNull()
                    ?: USER_POST_COUNT_TEXT_PATTERNS
                            .asSequence()
                            .mapNotNull { pattern ->
                                pattern.find(infoBlock.asUserPostCountText())?.groups?.get(1)?.value
                            }
                            .firstOrNull()
            rawNumber?.toUserPostCount()?.let { return it }
        }
        return null
    }

    private fun userPostCountInfoBlocks(postHtml: String): Sequence<String> {
        return USER_POST_COUNT_INFO_START_PATTERN
                .findAll(postHtml)
                .map { infoStart ->
                    val infoTail = postHtml.substring(infoStart.range.first)
                    infoTail.substring(
                            0,
                            listOf(
                                    infoTail.indexOf("""<span class="post_action"""", ignoreCase = true),
                                    infoTail.indexOf("""<span class='post_action"""", ignoreCase = true),
                                    infoTail.indexOf("""<div class="post_body"""", ignoreCase = true),
                                    infoTail.indexOf("""<div class='post_body""", ignoreCase = true)
                            ).filter { it >= 0 }.minOrNull() ?: infoTail.length
                    )
                }
    }

    private fun String.asUserPostCountText(): String {
        return replace(USER_POST_COUNT_BR_TAG, " ")
                .replace(USER_POST_COUNT_HTML_TAG, " ")
                .replace(USER_POST_COUNT_NBSP, " ")
                .replace(USER_POST_COUNT_WHITESPACE, " ")
                .trim()
    }

    private fun String.toUserPostCount(): Int? {
        return replace(USER_POST_COUNT_NON_DIGIT, "")
                .takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
    }

    private fun resolvePrependedHatEntryIdForAnchor(page: ThemePage): Int? {
        if (page.pagination.current <= 1 || page.posts.size <= 1) return null
        val first = page.posts.firstOrNull()?.takeIf { it.id > 0 } ?: return null
        val expectedMin = (page.pagination.current - 1) * page.pagination.perPage + 1
        return first.id.takeIf { first.number == 1 || first.number < expectedMin }
    }

    /** Bottom #entry on a multi-post page is often last-read — unless list marked topic unread. */
    private fun rejectsGetNewPostBottomHint(
            postId: Int,
            entryIds: List<Int>,
            openFromUnreadListHint: Boolean
    ): Boolean = TopicUnreadOpenPolicy.run {
        // Kept for tests that reference ThemeParser directly; policy is authoritative.
        !openFromUnreadListHint && ThemeApi.isLikelyLastReadPageBottomHint(postId, entryIds)
    }

    private fun ensureGetNewPostScrollAnchor(
            page: ThemePage,
            html: String,
            initialRequestUrl: String?,
            openFromUnreadListHint: Boolean
    ) {
        val openUrl = initialRequestUrl.orEmpty()
        if (!openUrl.contains("view=getnewpost", ignoreCase = true)) return
        val finalUrl = page.url.orEmpty()
        val urlSt = TopicUnreadOpenPolicy.urlSt(finalUrl)
        val onFirstPageByUrl = urlSt == 0
        val hatPostId = page.posts
                .takeIf { it.size > 1 && page.pagination.current == 1 && onFirstPageByUrl }
                ?.firstOrNull()
                ?.id
        val listProvidedAnchor = page.anchor
        page.anchors.clear()
        val entryIds = POST_ENTRY_ANCHOR_PATTERN
                .findAll(html)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toList()
        val redirectHashId = ThemeApi.extractHashEntryPostIdFromTopicUrl(finalUrl)?.toIntOrNull()
        val prependedHatEntryId = TopicPrependedHatPolicy.resolvePrependedHatId(page)
                ?: resolvePrependedHatEntryIdForAnchor(page)
        val hatEntryIdToSkip = hatPostId ?: prependedHatEntryId
        val onLastTopicPage = page.pagination.all > 0 && page.pagination.current >= page.pagination.all
        val resolution = TopicUnreadOpenPolicy.resolveGetNewPostAnchor(
                TopicUnreadOpenPolicy.GetNewPostAnchorContext(
                        html = html,
                        finalUrl = finalUrl,
                        entryIds = entryIds,
                        redirectHashId = redirectHashId,
                        hatEntryIdToSkip = hatEntryIdToSkip,
                        onLastTopicPage = onLastTopicPage,
                        listUnreadHint = openFromUnreadListHint,
                )
        )
        if (resolution.ambiguousBottomRedirect && listProvidedAnchor != null) {
            page.addAnchor(listProvidedAnchor)
            page.anchorPostId = page.anchor?.removePrefix("entry")?.takeIf { id -> id.isNotBlank() }
            page.hasUnreadTarget = true
            page.ambiguousLastUnreadBottomRedirect = true
            logUnreadOpenAnchor(
                    reason = "list_unread_url_anchor_over_ambiguous_bottom",
                    anchor = page.anchor,
                    hasUnreadTarget = true,
                    openFromUnreadListHint = openFromUnreadListHint,
                    redirectHashId = redirectHashId,
                    entryIds = entryIds,
                    page = page,
                    html = html,
                    bottomHashRejected = resolution.bottomHashRejected,
                    ambiguousBottomRedirect = true
            )
            return
        }
        resolution.anchorEntry?.let {
            page.addAnchor(it)
            page.anchorPostId = it.removePrefix("entry").takeIf { id -> id.isNotBlank() }
        }
        page.hasUnreadTarget = resolution.hasUnreadTarget
        page.ambiguousLastUnreadBottomRedirect = resolution.ambiguousBottomRedirect
        if (resolution.anchorEntry != null || resolution.ambiguousBottomRedirect) {
            logUnreadOpenAnchor(
                    reason = resolution.reason,
                    anchor = page.anchor,
                    hasUnreadTarget = resolution.hasUnreadTarget,
                    openFromUnreadListHint = openFromUnreadListHint,
                    redirectHashId = redirectHashId,
                    entryIds = entryIds,
                    page = page,
                    html = html,
                    bottomHashRejected = resolution.bottomHashRejected,
                    ambiguousBottomRedirect = resolution.ambiguousBottomRedirect
            )
            return
        }
        val firstParsedPostId = page.posts.firstOrNull()?.id
        if (firstParsedPostId != null) {
            page.addAnchor("entry$firstParsedPostId")
            page.anchorPostId = firstParsedPostId.toString()
            page.hasUnreadTarget = true
            logUnreadOpenAnchor(
                    reason = "fallback_first_parsed_post",
                    anchor = page.anchor,
                    hasUnreadTarget = true,
                    openFromUnreadListHint = openFromUnreadListHint,
                    redirectHashId = redirectHashId,
                    entryIds = entryIds,
                    page = page,
                    html = html
            )
        } else {
            // Phase 1 (audit §13): guarantee resolveGetNewPostAnchor emits a single structured trace
            // on EVERY exit path. The empty-page `no_anchor` outcome previously logged nothing, hiding
            // which branch fired for problems #1/#2/#9. hasUnreadTarget is already stamped above;
            // emit the resolver reason verbatim so the no-anchor exit is observable.
            logUnreadOpenAnchor(
                    reason = resolution.reason,
                    anchor = page.anchor,
                    hasUnreadTarget = resolution.hasUnreadTarget,
                    openFromUnreadListHint = openFromUnreadListHint,
                    redirectHashId = redirectHashId,
                    entryIds = entryIds,
                    page = page,
                    html = html,
                    bottomHashRejected = resolution.bottomHashRejected,
                    ambiguousBottomRedirect = resolution.ambiguousBottomRedirect
            )
        }
    }

    private fun logUnreadOpenAnchor(
            reason: String,
            anchor: String?,
            hasUnreadTarget: Boolean,
            openFromUnreadListHint: Boolean,
            redirectHashId: Int?,
            entryIds: List<Int>,
            page: ThemePage,
            html: String,
            bottomHashRejected: Boolean = false,
            ambiguousBottomRedirect: Boolean = false
    ) {
        val hatSkip = TopicPrependedHatPolicy.resolvePrependedHatId(page)
                ?: resolvePrependedHatEntryIdForAnchor(page)
        val serverUnreadIds = ThemeApi.collectUnreadPostEntryIds(html, hatSkip)
        val anchorDiagnostics = TopicUnreadOpenPolicy.buildAnchorDiagnostics(entryIds, redirectHashId, hatSkip)
        ThemePostReadStateDiagnostics.parserAnchorResolved(
                topicId = page.id,
                url = page.url,
                listUnreadHint = openFromUnreadListHint,
                reason = reason,
                anchorPostId = anchor?.removePrefix("entry"),
                hasUnreadTarget = hasUnreadTarget,
                serverUnreadPostIds = serverUnreadIds,
                htmlUnreadCount = serverUnreadIds.size,
                pageCurrent = page.pagination.current,
                pageTotal = page.pagination.all,
                redirectEntryId = redirectHashId,
                parsedPostCount = page.posts.size,
                extra = mapOf(
                        "bottomHashRejected" to bottomHashRejected,
                        "ambiguousBottomRedirect" to ambiguousBottomRedirect,
                        "entryCount" to entryIds.size,
                        "hatSkip" to hatSkip,
                        "firstEntryId" to anchorDiagnostics.firstEntryId,
                        "lastEntryId" to anchorDiagnostics.lastEntryId,
                        "redirectIsBottomEntry" to anchorDiagnostics.redirectIsBottomEntry,
                        "contentEntryCount" to anchorDiagnostics.contentEntryCount,
                )
        )
        forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.anchorResolved(
                topicId = page.id,
                anchorSource = reason,
                postId = anchor?.removePrefix("entry"),
                hasUnreadTarget = hasUnreadTarget,
                listUnreadHint = openFromUnreadListHint,
                redirectEntryId = redirectHashId,
                htmlUnreadCount = serverUnreadIds.size,
                pageCurrent = page.pagination.current,
                pageTotal = page.pagination.all,
                extra = mapOf(
                        "bottomHashRejected" to bottomHashRejected,
                        "redirectIsBottomEntry" to anchorDiagnostics.redirectIsBottomEntry,
                        "ambiguousBottomRedirect" to ambiguousBottomRedirect,
                ),
        )
        // U-02 (audit Finding U-02): a topic with HTML-unread markers whose anchor resolution still
        // ended on a redirect-bookmark classification (no confirmed unread target) likely had a valid
        // first-unread suppressed. Emit a warning so this is visible at runtime without changing the
        // resolution itself.
        if (!hasUnreadTarget &&
                reason in UNREAD_SUPPRESSED_REASONS &&
                serverUnreadIds.isNotEmpty()
        ) {
            forpdateam.ru.forpda.diagnostic.TopicUnreadAnchorDiagnostics.unreadTargetSuppressed(
                    topicId = page.id,
                    reason = reason,
                    redirectEntryId = redirectHashId,
                    htmlUnreadCount = serverUnreadIds.size,
                    listUnreadHint = openFromUnreadListHint,
                    pageCurrent = page.pagination.current,
                    pageTotal = page.pagination.all,
            )
        }
        if (BuildConfig.DEBUG) {
            Timber.tag(UNREAD_OPEN_ANCHOR_TAG).i(
                    "ensureGetNewPostAnchor reason=%s anchor=%s hasUnreadTarget=%s listUnreadHint=%s " +
                            "pagCur=%d pagAll=%d redirectHash=%s bottomRejected=%s ambiguousBottom=%s entryCount=%d hatSkip=%s",
                    reason,
                    anchor,
                    hasUnreadTarget,
                    openFromUnreadListHint,
                    page.pagination.current,
                    page.pagination.all,
                    redirectHashId?.toString().orEmpty(),
                    bottomHashRejected,
                    ambiguousBottomRedirect,
                    entryIds.size,
                    hatSkip?.toString().orEmpty()
            )
        }
    }

    private fun applyPaginationFromUrlIfNeeded(page: ThemePage, argUrl: String, initialRequestUrl: String?) {
        val finalSt = extractStFromUrl(argUrl) ?: 0
        val requestedSt = extractStFromUrl(initialRequestUrl.orEmpty()) ?: 0
        val st = when {
            finalSt > 0 -> finalSt
            requestedSt > 0 && responseLooksLikeRequestedPage(page, requestedSt) -> requestedSt
            else -> return
        }
        if (st <= 0 || page.pagination.perPage <= 0) return
        val pageFromSt = st / page.pagination.perPage + 1
        if (pageFromSt > 1 && page.pagination.current <= 1) {
            page.pagination.current = pageFromSt
        }
    }

    private fun responseLooksLikeRequestedPage(page: ThemePage, requestedSt: Int): Boolean {
        val perPage = page.pagination.perPage.coerceAtLeast(1)
        val requestedPage = requestedSt / perPage + 1
        if (requestedPage <= 1) return true
        val expectedMin = (requestedPage - 1) * perPage + 1
        val firstNumbers = page.posts.asSequence().take(3).map { it.number }.toList()
        return firstNumbers.firstOrNull()?.let { it >= expectedMin } == true ||
                (firstNumbers.firstOrNull() == 1 && firstNumbers.drop(1).firstOrNull()?.let { it >= expectedMin } == true)
    }

    private fun extractStFromUrl(url: String): Int? {
        return try {
            android.net.Uri.parse(url).getQueryParameter("st")?.toIntOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private fun parsePollFormAction(pollBlock: String): String {
        val action = Regex("""(?is)<form\b[^>]*\baction\s*=\s*["']([^"']+)["']""")
                .find(pollBlock)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
                .fromHtml()
                .orEmpty()
                .trim()
        return absolutizeFourPdaForumHref(action)
                ?: action.takeIf { it.isNotBlank() }
                ?: "https://4pda.to/forum/index.php"
    }

    private fun parsePollFormMethod(pollBlock: String): String {
        return Regex("""(?is)<form\b[^>]*\bmethod\s*=\s*["']([^"']+)["']""")
                .find(pollBlock)
                ?.groupValues
                ?.getOrNull(1)
                ?.lowercase()
                ?.takeIf { it == "post" || it == "get" }
                ?: "get"
    }

    private fun parsePollHiddenInputs(pollBlock: String): List<kotlin.Pair<String, String>> {
        val result = linkedMapOf<String, String>()
        Regex("""(?is)<input\b[^>]*\btype\s*=\s*["']hidden["'][^>]*>""")
                .findAll(pollBlock)
                .forEach { input ->
                    val tag = input.value
                    val name = parseHtmlAttribute(tag, "name") ?: return@forEach
                    val value = parseHtmlAttribute(tag, "value").orEmpty()
                    if (name.isNotBlank()) {
                        result[name] = value
                    }
                }
        if (!result.containsKey("addpoll")) {
            result["addpoll"] = "1"
        }
        return result.map { it.key to it.value }
    }

    private fun parsePollVoteItems(questionBlock: String): List<PollQuestionItem> {
        val rows = Regex("""(?is)<tr\b[^>]*>(.*?)</tr>""")
                .findAll(questionBlock)
                .map { it.groupValues[1] }
                .toList()
        val sourceRows = rows.ifEmpty { listOf(questionBlock) }
        return sourceRows.mapNotNull { row ->
            val inputTag = Regex("""(?is)<input\b(?=[^>]*\btype\s*=\s*["']?(?:radio|checkbox)["']?)[^>]*>""")
                    .find(row)
                    ?.value
                    ?: return@mapNotNull null
            val type = parseHtmlAttribute(inputTag, "type").orEmpty().lowercase()
            val name = parseHtmlAttribute(inputTag, "name").orEmpty()
            val rawValue = parseHtmlAttribute(inputTag, "value").orEmpty()
            if (name.isBlank() || (type != "radio" && type != "checkbox")) return@mapNotNull null

            PollQuestionItem().apply {
                this.type = type
                this.name = name
                value = rawValue
                title = parsePollChoiceTitle(row, inputTag)
            }
        }
    }

    private fun parsePollChoiceTitle(row: String, inputTag: String): String? {
        Regex("""(?is)<b\b[^>]*>(.*?)</b>""")
                .find(row)
                ?.groupValues
                ?.getOrNull(1)
                ?.fromHtml()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }

        return row
                .replace(inputTag, "")
                .replace(Regex("""(?is)<script\b[^>]*>.*?</script>"""), "")
                .replace(Regex("""(?is)<style\b[^>]*>.*?</style>"""), "")
                .replace(Regex("""(?is)<[^>]+>"""), " ")
                .fromHtml()
                .orEmpty()
                .replace(Regex("""\s+"""), " ")
                .trim()
    }

    private fun parseGenericPoll(response: String, pageUrl: String): Poll? {
        val formMatch = Regex("""(?is)<form\b[^>]*>.*?</form>""")
                .findAll(response)
                .firstOrNull { form ->
                    val block = form.value
                    block.contains("addpoll", ignoreCase = true) ||
                            block.contains("poll_vote", ignoreCase = true) ||
                            block.contains("Всего голосов", ignoreCase = true) ||
                            block.contains("poll", ignoreCase = true) &&
                            block.contains(Regex("""(?is)<input\b(?=[^>]*\btype\s*=\s*["']?(?:radio|checkbox)["']?)"""))
                }
                ?: return null
        val pollBlock = formMatch.value
        val questions = parseGenericPollQuestions(pollBlock)
        val resultQuestions = if (questions.isEmpty()) parseGenericPollResultQuestions(pollBlock) else emptyList()
        val hasResultRows = resultQuestions.isNotEmpty() || hasPollResultRows(pollBlock)
        if (questions.isEmpty() && resultQuestions.isEmpty() && !hasResultRows) return null

        return Poll().apply {
            isResult = questions.isEmpty() && hasResultRows
            title = parseGenericPollTitle(pollBlock) ?: "Опрос"
            formAction = parsePollFormAction(pollBlock)
            formMethod = parsePollFormMethod(pollBlock)
            hiddenInputs.addAll(parsePollHiddenInputs(pollBlock))
            this.questions.addAll(if (isResult && resultQuestions.isNotEmpty()) resultQuestions else questions)
            voteButton = !isResult && hasVoteInputs && hasPollVoteButton(pollBlock)
            resultsUrl = parsePollResultsUrl(pollBlock, pageUrl, allowTopicFallback = false)
            showResultsButton = !resultsUrl.isNullOrBlank()
            showPollButton = pollBlock.contains("пункты опроса", ignoreCase = true)
            normalizePollActions(this, pageUrl)
            votesCount = parsePollVotesCount(pollBlock)
        }
    }

    private fun normalizePollActions(poll: Poll, pageUrl: String) {
        if (poll.isResult) {
            poll.voteButton = false
            poll.showResultsButton = false
            poll.resultsUrl = null
            poll.showPollButton = isPollResultsMode(pageUrl) && poll.showPollButton
        } else {
            poll.showResultsButton = poll.canVote && (!poll.resultsUrl.isNullOrBlank() || poll.showResultsButton)
            poll.showPollButton = false
        }
    }

    private fun isPollResultsMode(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return Regex("""(?i)(?:[?&])mode=show(?:[&#]|$)""").containsMatchIn(url)
    }

    private fun parseGenericPollTitle(pollBlock: String): String? {
        listOf(
                Regex("""(?is)<th\b[^>]*>(.*?)</th>"""),
                Regex("""(?is)<h[1-6]\b[^>]*>(.*?)</h[1-6]>""")
        ).forEach { regex ->
            regex.find(pollBlock)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace(Regex("""(?is)<[^>]+>"""), " ")
                    ?.fromHtml()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
        }
        return null
    }

    private fun hasPollVoteButton(pollBlock: String): Boolean {
        return Regex("""(?is)<input\b[^>]*>""")
                .findAll(pollBlock)
                .any { input ->
                    val tag = input.value
                    val type = parseHtmlAttribute(tag, "type").orEmpty()
                    val value = parseHtmlAttribute(tag, "value").orEmpty()
                    (type.equals("submit", ignoreCase = true) || type.equals("button", ignoreCase = true)) &&
                            value.contains("голос", ignoreCase = true)
                } || pollBlockContainsButtonText(pollBlock, "голос")
    }

    private fun parsePollVotesCount(pollBlock: String): Int {
        return Regex("""(?is)Всего\s+голосов:\s*(\d+)""")
                .find(pollBlock)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
    }

    private fun hasPollVoteInputs(pollBlock: String): Boolean {
        return Regex("""(?is)<input\b(?=[^>]*\btype\s*=\s*["']?(?:radio|checkbox)["']?)[^>]*>""")
                .containsMatchIn(pollBlock)
    }

    private fun hasPollResultRows(pollBlock: String): Boolean {
        return pollBlock.contains(Regex("""(?is)Всего\s+голосов""")) ||
                pollBlock.contains(Regex("""(?is)\[\s*\d+\s*]\s*\|\s*\[\s*[\d,.]+\s*%]""")) ||
                pollBlock.contains(Regex("""(?is)<(?:img|div|span)\b[^>]*(?:poll|bar|percent|range)""")) ||
                pollBlock.contains(Regex("""(?is)<td\b[^>]*>\s*\[\s*<b\b[^>]*>\s*\d+\s*</b>\s*]\s*</td>\s*<td\b[^>]*>.*?\[\s*[\d,.]+\s*%]"""))
    }

    private fun parsePollResultsUrl(pollBlock: String, pageUrl: String, allowTopicFallback: Boolean): String? {
        findPollResultHref(pollBlock)?.let { return it }
        if (findPollResultSubmit(pollBlock)) {
            buildPollActionUrlWithModeShow(pollBlock)?.let { return it }
        }
        return if (allowTopicFallback) buildTopicUrlWithModeShow(pageUrl) else null
    }

    private fun findPollResultHref(pollBlock: String): String? {
        return Regex("""(?is)<a\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""")
                .findAll(pollBlock)
                .firstNotNullOfOrNull { match ->
                    val text = match.groupValues.getOrNull(2).orEmpty()
                            .replace(Regex("""(?is)<[^>]+>"""), " ")
                            .fromHtml()
                            .orEmpty()
                    val href = match.groupValues.getOrNull(1).orEmpty()
                    if (text.contains("результ", ignoreCase = true) || href.contains("mode=show", ignoreCase = true)) {
                        absolutizeFourPdaForumHref(href) ?: href.takeIf { it.startsWith("http", ignoreCase = true) }
                    } else {
                        null
                    }
                }
    }

    private fun findPollResultSubmit(pollBlock: String): Boolean {
        return Regex("""(?is)<input\b[^>]*>""")
                .findAll(pollBlock)
                .any { input ->
                    val tag = input.value
                    val type = parseHtmlAttribute(tag, "type").orEmpty()
                    val value = parseHtmlAttribute(tag, "value").orEmpty()
                    (type.equals("submit", ignoreCase = true) || type.equals("button", ignoreCase = true)) &&
                            value.contains("результ", ignoreCase = true)
                } || pollBlockContainsButtonText(pollBlock, "результ")
    }

    private fun pollBlockContainsButtonText(pollBlock: String, text: String): Boolean {
        return Regex("""(?is)<button\b[^>]*>([\s\S]*?)</button>""")
                .findAll(pollBlock)
                .any { button ->
                    button.groupValues[1]
                            .replace(Regex("""(?is)<[^>]+>"""), " ")
                            .fromHtml()
                            .orEmpty()
                            .contains(text, ignoreCase = true)
                }
    }

    private fun buildPollActionUrlWithModeShow(pollBlock: String): String? {
        val action = parsePollFormAction(pollBlock).takeIf { it.isNotBlank() } ?: return null
        return appendOrReplaceQueryParam(action, "mode", "show")
    }

    private fun buildTopicUrlWithModeShow(pageUrl: String): String? {
        if (pageUrl.isBlank() || !pageUrl.contains("showtopic=", ignoreCase = true)) return null
        val withoutFragment = pageUrl.replace(Regex("""#.*$"""), "")
        return appendOrReplaceQueryParam(withoutFragment, "mode", "show")
    }

    private fun appendOrReplaceQueryParam(url: String, name: String, value: String): String {
        val hashIndex = url.indexOf('#')
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""
        val base = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val paramRegex = Regex("""([?&])${Regex.escape(name)}=[^&#]*""", RegexOption.IGNORE_CASE)
        val existing = paramRegex.find(base)
        val withParam = if (existing != null) {
            base.replaceRange(existing.range, "${existing.groupValues[1]}$name=$value")
        } else {
            base + (if (base.contains("?")) "&" else "?") + "$name=$value"
        }
        return withParam + fragment
    }

    private fun parseGenericPollQuestions(pollBlock: String): List<PollQuestion> {
        val inputMatches = Regex("""(?is)<input\b(?=[^>]*\btype\s*=\s*["']?(?:radio|checkbox)["']?)[^>]*>""")
                .findAll(pollBlock)
                .toList()
        if (inputMatches.isEmpty()) return emptyList()

        val byName = linkedMapOf<String, PollQuestion>()
        inputMatches.forEach { match ->
            val inputTag = match.value
            val type = parseHtmlAttribute(inputTag, "type").orEmpty().lowercase()
            val name = parseHtmlAttribute(inputTag, "name").orEmpty()
            if (name.isBlank() || (type != "radio" && type != "checkbox")) return@forEach
            val value = parseHtmlAttribute(inputTag, "value").orEmpty()
            val row = extractContainingPollChoiceBlock(pollBlock, match.range.first, match.range.last + 1)
            val question = byName.getOrPut(name) {
                PollQuestion().apply { title = parseGenericPollQuestionTitle(pollBlock, match.range.first) }
            }
            question.questionItems.add(PollQuestionItem().apply {
                this.type = type
                this.name = name
                this.value = value
                title = parsePollChoiceTitle(row, inputTag)
                        ?: parseHtmlAttribute(inputTag, "aria-label")
                        ?: value
            })
        }
        return byName.values.toList()
    }

    private fun parsePollResultQuestions(questionsBlock: String, pollBlock: String): List<PollQuestion> {
        val patternQuestions = patternProvider
                .getPattern(scope.scope, scope.poll_questions)
                .matcher(questionsBlock)
                .map { questionMatch ->
                    val items = parsePollResultItems(questionMatch.group(2).orEmpty())
                    if (items.isEmpty()) {
                        null
                    } else {
                        PollQuestion().apply {
                            title = questionMatch.group(1).orEmpty().fromHtml()
                            questionItems.addAll(items)
                        }
                    }
                }
                .filterNotNull()
        if (patternQuestions.isNotEmpty()) return patternQuestions

        return parseGenericPollResultQuestions(pollBlock)
    }

    private fun parsePollResultItems(questionBlock: String): List<PollQuestionItem> {
        return Regex("""(?is)<tr\b[^>]*>(.*?)</tr>""")
                .findAll(questionBlock)
                .mapNotNull { match -> parseGenericPollResultRow(match.groupValues[1]) }
                .toList()
    }

    private fun parseReadonlyPollQuestions(questionsBlock: String): List<PollQuestion> {
        return patternProvider
                .getPattern(scope.scope, scope.poll_questions)
                .matcher(questionsBlock)
                .map { questionMatch ->
                    val items = patternProvider
                            .getPattern(scope.scope, scope.poll_question_item)
                            .matcher(questionMatch.group(2).orEmpty())
                            .map { itemMatch ->
                                (1..7)
                                        .asSequence()
                                        .map { index -> itemMatch.group(index).orEmpty() }
                                        .firstOrNull { it.isNotBlank() }
                                        ?.fromHtml()
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { title ->
                                            PollQuestionItem().apply { this.title = title }
                                        }
                            }
                            .filterNotNull()
                    if (items.isEmpty()) {
                        null
                    } else {
                        PollQuestion().apply {
                            title = questionMatch.group(1).orEmpty().fromHtml()
                            questionItems.addAll(items)
                        }
                    }
                }
                .filterNotNull()
    }

    private fun parseGenericPollResultQuestions(pollBlock: String): List<PollQuestion> {
        val rowItems = parseGenericPollResultRowsFromInnermostTables(pollBlock)
                .ifEmpty {
                    Regex("""(?is)<tr\b[^>]*>(.*?)</tr>""")
                            .findAll(pollBlock)
                            .mapNotNull { match -> parseGenericPollResultRow(match.groupValues[1]) }
                            .toList()
                }
                .ifEmpty { parseGenericPollResultDivItems(pollBlock) }
        if (rowItems.isEmpty()) return emptyList()

        return listOf(PollQuestion().apply {
            title = Regex("""(?is)<strong\b[^>]*>(.*?)</strong>""")
                    .find(pollBlock)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.fromHtml()
                    ?.trim()
            questionItems.addAll(rowItems)
        })
    }

    private fun parseGenericPollResultRow(row: String): PollQuestionItem? {
        val cells = Regex("""(?is)<td\b[^>]*>(.*?)</td>""")
                .findAll(row)
                .map { it.groupValues[1] }
                .toList()
        if (cells.size < 2) return null

        val title = cells.first()
                .replace(Regex("""(?is)<[^>]+>"""), " ")
                .fromHtml()
                .orEmpty()
                .replace(Regex("""\s+"""), " ")
                .trim()
        if (title.isBlank() || title.contains("Всего голосов", ignoreCase = true)) return null

        val meta = cells.drop(1).joinToString(" ")
        val metaText = meta
                .replace(Regex("""(?is)<[^>]+>"""), " ")
                .fromHtml()
                .orEmpty()
                .replace(Regex("""\s+"""), " ")
                .trim()
        val percentText = Regex("""(?is)([\d,.]+)\s*%""")
                .find(meta)
                ?.groupValues
                ?.getOrNull(1)
                ?: Regex("""(?is)([\d,.]+)\s*%""")
                        .find(metaText)
                        ?.groupValues
                        ?.getOrNull(1)
        val percent = percentText
                ?.replace(",", ".")
                ?.toFloatOrNull()
                ?: return null
        val votes = Regex("""(?is)<b\b[^>]*>\s*\[?\s*(\d+)\s*]?\s*</b>""")
                .find(meta)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: Regex("""(?is)\[\s*(\d+)\s*]\s*(?:\||\s|<|$)""")
                        .find(metaText)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                ?: Regex("""(?is)%\s*\(?\s*(\d+)\s*\)?""")
                        .find(metaText)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                ?: return null

        return PollQuestionItem().apply {
            this.title = title
            this.votes = votes
            this.percent = percent
        }
    }

    private fun parseGenericPollResultRowsFromInnermostTables(pollBlock: String): List<PollQuestionItem> {
        return Regex("""(?is)<table\b[^>]*>((?:(?!<table\b).)*?)</table>""")
                .findAll(pollBlock)
                .flatMap { tableMatch ->
                    Regex("""(?is)<tr\b[^>]*>(.*?)</tr>""")
                            .findAll(tableMatch.groupValues[1])
                            .mapNotNull { rowMatch -> parseGenericPollResultRow(rowMatch.groupValues[1]) }
                }
                .toList()
    }

    private fun parseGenericPollResultDivItems(pollBlock: String): List<PollQuestionItem> {
        return Regex("""(?is)<(?:li|div)\b[^>]*>(.*?(?:\[\s*\d+\s*]|\d+\s+голос).*?[\d,.]+\s*%.*?)</(?:li|div)>""")
                .findAll(pollBlock)
                .mapNotNull { match -> parseGenericPollResultFreeform(match.groupValues[1]) }
                .toList()
    }

    private fun parseGenericPollResultFreeform(block: String): PollQuestionItem? {
        val text = block
                .replace(Regex("""(?is)<script\b[^>]*>.*?</script>"""), " ")
                .replace(Regex("""(?is)<style\b[^>]*>.*?</style>"""), " ")
                .replace(Regex("""(?is)<[^>]+>"""), " ")
                .fromHtml()
                .orEmpty()
                .replace(Regex("""\s+"""), " ")
                .trim()
        val percent = Regex("""(?is)([\d,.]+)\s*%""")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(",", ".")
                ?.toFloatOrNull()
                ?: return null
        val votes = Regex("""(?is)\[\s*(\d+)\s*]""")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: Regex("""(?is)(\d+)\s+голос""")
                        .find(text)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                ?: return null
        val title = text
                .replace(Regex("""(?is)\[\s*\d+\s*].*$"""), "")
                .replace(Regex("""(?is)\d+\s+голос.*$"""), "")
                .replace(Regex("""(?is)[\d,.]+\s*%.*$"""), "")
                .trim()
        if (title.isBlank() || title.contains("Всего голосов", ignoreCase = true)) return null

        return PollQuestionItem().apply {
            this.title = title
            this.votes = votes
            this.percent = percent
        }
    }

    private fun extractContainingPollChoiceBlock(pollBlock: String, inputStart: Int, inputEnd: Int): String {
        listOf("label", "tr", "li", "div").forEach { tag ->
            findContainingHtmlBlock(pollBlock, tag, inputStart, inputEnd)?.let { return it }
        }
        val start = pollBlock.lastIndexOf('\n', inputStart).let { if (it >= 0) it + 1 else 0 }
        val end = pollBlock.indexOf('\n', inputEnd).let { if (it >= 0) it else pollBlock.length }
        return pollBlock.substring(start, end)
    }

    private fun findContainingHtmlBlock(html: String, tag: String, innerStart: Int, innerEnd: Int): String? {
        val openRegex = Regex("""(?is)<${tag}\b[^>]*>""")
        val closeRegex = Regex("""(?is)</${tag}>""")
        val open = openRegex.findAll(html)
                .lastOrNull { it.range.first <= innerStart }
                ?: return null
        val close = closeRegex.find(html, innerEnd) ?: return null
        return html.substring(open.range.first, close.range.last + 1)
    }

    private fun parseGenericPollQuestionTitle(pollBlock: String, inputStart: Int): String? {
        val beforeInput = pollBlock.substring(0, inputStart)
        return Regex("""(?is)<strong\b[^>]*>(.*?)</strong>""")
                .findAll(beforeInput)
                .lastOrNull()
                ?.groupValues
                ?.getOrNull(1)
                ?.fromHtml()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
    }

    private fun parseHtmlAttribute(tag: String, name: String): String? {
        val quoted = Regex("""(?is)\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""")
                .find(tag)
                ?.groupValues
                ?.getOrNull(1)
        if (quoted != null) return quoted.fromHtml().orEmpty()
        return Regex("""(?is)\b${Regex.escape(name)}\s*=\s*([^\s>]+)""")
                .find(tag)
                ?.groupValues
                ?.getOrNull(1)
                ?.fromHtml()
    }

}
