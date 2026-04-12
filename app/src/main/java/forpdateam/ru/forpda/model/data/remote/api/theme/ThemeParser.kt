package forpdateam.ru.forpda.model.data.remote.api.theme

import android.util.Log
import android.util.Pair
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.*
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher

class ThemeParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Topic

    /**
     * @param initialRequestUrl исходный URL запроса (до редиректа). Нужен для view=getnewpost: в Location нет #entry,
     * OkHttp отдаёт финальный URL без фрагмента — иначе elem_to_scroll пустой и скролл к непрочитанному не срабатывает.
     */
    fun parsePage(
            response: String,
            argUrl: String,
            hatOpen: Boolean = false,
            pollOpen: Boolean = false,
            initialRequestUrl: String? = null
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
        } else if (wantsUnreadScroll) {
            // view=getnewpost: редирект часто с #entry на первый пост страницы — якорь попадал в anchors до
            // ensureGetNewPostScrollAnchor, тот видел непустой список и не искал непрочитанное (остаётся «последняя страница, первый пост»).
            val initGetNew = initialRequestUrl
            if (!initGetNew.isNullOrBlank()) {
                patternProvider
                        .getPattern(scope.scope, scope.scroll_anchor)
                        .matcher(initGetNew)
                        .findAll {
                            it.group(1)?.let { a -> page.addAnchor(a) }
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

        page.pagination = Pagination.parseForum(response)

        patternProvider
                .getPattern(scope.scope, scope.title)
                .matcher(response)
                .findOnce {
                    page.title = it.group(1).orEmpty().fromHtml()
                    page.desc = it.group(2).orEmpty().fromHtml()
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

        var attachMatcher: Matcher? = null
        val posts = patternProvider
                .getPattern(scope.scope, scope.posts)
                .matcher(response)
                .map { matcher ->
                    ThemePost().apply {
                        fun g(i: Int): String = matcher.group(i).orEmpty()
                        topicId = page.id
                        forumId = page.forumId
                        id = g(1).toInt()
                        date = g(5)
                        number = g(6).toInt()
                        isOnline = g(7).contains("green")
                        g(8).also {
                            avatar = if (it.isNotEmpty()) "https://s.4pda.to/forum/uploads/$it" else it
                        }
                        nick = g(9).fromHtml()
                        userId = g(10).toInt()
                        isCurator = matcher.group(11) != null
                        groupColor = g(12)
                        group = g(13)
                        canMinusRep = g(14).isNotEmpty()
                        reputation = g(15)
                        canPlusRep = g(16).isNotEmpty()
                        canReport = g(17).isNotEmpty()
                        canEdit = g(18).isNotEmpty()
                        canDelete = g(19).isNotEmpty()
                        page.canQuote = g(20).isNotEmpty()
                        canQuote = page.canQuote
                        val rawBody = g(21)
                        body = rawBody
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
        page.posts.addAll(posts)

        patternProvider
                .getPattern(scope.scope, scope.poll_main)
                .matcher(response)
                .findOnce { matcher ->
                    val pollBlock = matcher.group().orEmpty()
                    val isResult = pollBlock.contains("<img")

                    val poll = Poll()
                    poll.isResult = isResult
                    poll.title = matcher.group(1).orEmpty().fromHtml()

                    val questions = patternProvider
                            .getPattern(scope.scope, scope.poll_questions)
                            .matcher(matcher.group(2).orEmpty())
                            .map {
                                PollQuestion().apply {
                                    title = it.group(1).orEmpty().fromHtml()
                                    val items = patternProvider
                                            .getPattern(scope.scope, scope.poll_question_item)
                                            .matcher(it.group(2).orEmpty())
                                            .map {
                                                PollQuestionItem().apply {
                                                    if (!isResult) {
                                                        type = it.group(1).orEmpty()
                                                        name = it.group(2).orEmpty().fromHtml()
                                                        value = it.group(3).orEmpty().toIntOrNull() ?: 0
                                                        title = it.group(4).orEmpty().fromHtml()
                                                    } else {
                                                        title = it.group(5).orEmpty().fromHtml()
                                                        votes = it.group(6).orEmpty().toIntOrNull() ?: 0
                                                        percent = java.lang.Float.parseFloat(
                                                                it.group(7).orEmpty().replace(",", ".")
                                                        )
                                                    }
                                                }
                                            }
                                    this.questionItems.addAll(items)
                                }
                            }
                    poll.questions.addAll(questions)

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

                    poll.votesCount = matcher.group(3).orEmpty().toIntOrNull() ?: 0
                    page.poll = poll
                }
        ensureGetNewPostScrollAnchor(page, response, initialRequestUrl)
        return page
    }

    private fun ensureGetNewPostScrollAnchor(page: ThemePage, html: String, initialRequestUrl: String?) {
        val openUrl = initialRequestUrl.orEmpty()
        if (!openUrl.contains("view=getnewpost", ignoreCase = true)) return
        // Финальный URL: #entry… приоритетнее highlight=; OkHttp часто отдаёт URL без hash — тогда смотрим canonical ниже.
        ThemeApi.extractScrollPostIdFromFinalTopicUrl(page.url.orEmpty())?.toIntOrNull()?.let { serverPostId ->
            page.anchors.clear()
            page.addAnchor("entry$serverPostId")
            return
        }
        Regex("""(?is)<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)""").find(html)?.groupValues?.getOrNull(1)?.let { href ->
            ThemeApi.extractScrollPostIdFromFinalTopicUrl(href)?.toIntOrNull()?.let { cid ->
                page.anchors.clear()
                page.addAnchor("entry$cid")
                return
            }
        }
        // Шапка только на 1-й странице; иначе posts.first() — это просто первый пост листа, не «hat».
        val hatPostId = page.posts
                .takeIf { it.size > 1 && page.pagination.current == 1 }
                ?.firstOrNull()
                ?.id
        val lastAnchor = page.anchors.lastOrNull()
        val redirectPinnedHat = hatPostId != null &&
                lastAnchor.equals("entry$hatPostId", ignoreCase = true)
        if (redirectPinnedHat) {
            page.anchors.clear()
        } else if (page.anchors.isNotEmpty()) {
            // Без &p= в финальном URL якорь из #… в запросе не гарантирует непрочитанное — не блокируем разбор HTML.
            page.anchors.clear()
        }
        var unreadId = ThemeApi.findUnreadPostEntryIdForGetNewPost(html)
        if (hatPostId != null && unreadId == hatPostId) {
            unreadId = ThemeApi.findUnreadPostEntryIdForGetNewPost(html, hatPostId)
        }
        unreadId?.let {
            page.addAnchor("entry$it")
            return
        }
        val entry = Regex("""<a[^>]+name=["']entry(\d+)["']""", RegexOption.IGNORE_CASE)
        val entryIds = entry.findAll(html).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.toList()
        val onLastPage = page.pagination.all > 0 && page.pagination.current >= page.pagination.all
        val fallbackEntry = when {
            entryIds.isEmpty() -> null
            hatPostId != null && entryIds.firstOrNull() == hatPostId && entryIds.size > 1 -> entryIds[1]
            // Нет ни p=/маркеров непрочитанного: на последней странице «верх» листа — не то же самое, что новые посты; чаще они внизу.
            onLastPage -> entryIds.lastOrNull()
            else -> entryIds.firstOrNull()
        }
        if (fallbackEntry != null) {
            page.addAnchor("entry$fallbackEntry")
            return
        }
        Regex("""<div[^>]+data-post=["'](\d+)["']""", RegexOption.IGNORE_CASE)
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let {
                    page.addAnchor("entry$it")
                    return
                }
        page.posts.firstOrNull()?.id?.let { page.addAnchor("entry$it") }
    }

}
