package forpdateam.ru.forpda.model.data.remote.api.theme

import timber.log.Timber
import android.net.Uri
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.common.absolutizeFourPdaForumHref
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.common.Cp1251Codec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.regex.Pattern

/**
 * Created by radiationx on 04.08.16.
 */
class ThemeApi(
        private val webClient: IWebClient,
        private val themeParser: ThemeParser,
        private val authHolder: forpdateam.ru.forpda.model.AuthHolder? = null
) {

    fun getTheme(
            url: String,
            hatOpen: Boolean,
            pollOpen: Boolean,
            openFromUnreadListHint: Boolean = false
    ): ThemePage {
        // Если ID темы уже известен как «перенесённый» (resolved в этом процессе),
        // сразу подменяем showtopic=OLD на showtopic=NEW — экономим лишний 404/302 раунд.
        val effectiveUrl = redirectKnownMovedTopic(url) ?: url
        return loadThemeWithRelocationFallback(
                effectiveUrl,
                hatOpen,
                pollOpen,
                relocationAttemptsLeft = 2,
                openFromUnreadListHint = openFromUnreadListHint
        )
    }

    private fun redirectKnownMovedTopic(url: String): String? {
        val oldId = extractTopicIdFromUrl(url) ?: return null
        val newId = MovedTopicResolver.resolve(oldId) ?: return null
        return runCatching {
            val rebuilt = "https://4pda.to/forum/index.php?showtopic=$newId"
            mergeTopicRequestParamsOntoRelocation(originalRequest = url, relocationUrl = rebuilt)
        }.getOrNull()
    }

    private fun loadThemeWithRelocationFallback(
            url: String,
            hatOpen: Boolean,
            pollOpen: Boolean,
            relocationAttemptsLeft: Int,
            openFromUnreadListHint: Boolean = false
    ): ThemePage {
        val response = webClient.get(url)
        val redirectUrl: String = response.redirectWithFragment.ifBlank { response.redirect ?: url }
        if (BuildConfig.DEBUG) {
            Timber.tag("ThemeReloc").d(
                    "req url=%s redirect=%s frag=%s loc=%s code=%d bodyLen=%d attemptsLeft=%d",
                    url,
                    response.redirect,
                    response.redirectFragment,
                    response.locationHeader ?: "",
                    response.code,
                    response.body.length,
                    relocationAttemptsLeft
            )
            if (response.code == 404) {
                val htmlDiag = FpdaDebugLog.classifyHtml(response.body)
                Timber.tag("ThemeReloc").w(
                        "404 url=%s htmlLen=%s htmlHash=%s",
                        url,
                        htmlDiag["htmlLen"],
                        htmlDiag["htmlHash"]
                )
            }
        }
        var page = themeParser.parsePage(
                response.body,
                redirectUrl,
                hatOpen,
                pollOpen,
                initialRequestUrl = url,
                openFromUnreadListHint = openFromUnreadListHint
        )
        Timber.d(
                "ThemeApi.getTheme parsed: anchor=%s anchors=%d posts=%d pagCur=%d/%d topicId=%d",
                page.anchor, page.anchors.size, page.posts.size, page.pagination.current, page.pagination.all, page.id
        )

        // ВНИМАНИЕ: эвристика по телу страницы легко срабатывает ложно (например, «404»/ссылки в контенте поста),
        // что приводит к извлечению случайного showtopic=… и навигации «в другую тему».
        // Релокацию разрешаем только при реальном 404 или когда парсер вернул пустые посты.
        val shouldTryRelocation = relocationAttemptsLeft > 0 && (response.code == 404 || page.posts.isEmpty())

        if (shouldTryRelocation) {
            val relocationRaw = extractRelocationCandidate(
                    responseLocationHeader = response.locationHeader,
                    html = response.body,
                    originalRequestUrl = url
            )
            val relocationMerged = relocationRaw?.let {
                mergeTopicRequestParamsOntoRelocation(originalRequest = url, relocationUrl = it)
            }

            // Last resort: если удалось извлечь новый showtopic id, пробуем «классический» URL.
            val relocationShowtopic = runCatching {
                relocationMerged?.let { merged ->
                    extractTopicIdFromUrl(merged)?.let { newId ->
                        val rebuilt = "https://4pda.to/forum/index.php?showtopic=$newId"
                        mergeTopicRequestParamsOntoRelocation(originalRequest = url, relocationUrl = rebuilt)
                    }
                }
            }.getOrNull()

            val relocation = relocationShowtopic ?: relocationMerged
            if (BuildConfig.DEBUG) {
                val bodyLooks404Strict = looksLike404ErrorPage(response.body) && page.posts.isEmpty()
                Timber.tag("ThemeReloc").d(
                        "relocCheck url=%s code=%d posts=%d bodyLooks404Strict=%s relocationRaw=%s relocationMerged=%s relocationFinal=%s",
                        url,
                        response.code,
                        page.posts.size,
                        bodyLooks404Strict,
                        relocationRaw ?: "",
                        relocationMerged ?: "",
                        relocation ?: ""
                )
                if (response.code == 404 && relocationRaw.isNullOrBlank()) {
                    val htmlDiag = FpdaDebugLog.classifyHtml(response.body)
                    Timber.tag("ThemeReloc").w(
                            "404 no relocation extracted htmlLen=%s htmlHash=%s",
                            htmlDiag["htmlLen"],
                            htmlDiag["htmlHash"]
                    )
                }
            }
            if (!relocation.isNullOrBlank() &&
                    !urlsEqualIgnoringFragment(relocation, redirectUrl) &&
                    !urlsEqualIgnoringFragment(relocation, url)
            ) {
                Timber.d("ThemeApi.getTheme: relocation fallback retry -> %s", relocation)
                return loadThemeWithRelocationFallback(
                        relocation,
                        hatOpen,
                        pollOpen,
                        relocationAttemptsLeft - 1,
                        openFromUnreadListHint = openFromUnreadListHint
                )
            }

            // Strip-and-probe: для перенесённых тем-указателей сервер 404-ит на
            // `?showtopic=OLD&view=getnewpost`, но 302-редиректит на новый id для «голого»
            // `?showtopic=OLD`. В 404-теле ни canonical, ни meta-refresh, ни ссылок
            // на новую тему не содержится — поэтому extractRelocationCandidate возвращает null.
            // Решение: повторно дёрнуть URL без `view`/`p`/`pid`/`highlight`/`anchor` и фрагмента.
            // Если после редиректа showtopic= другой — это и есть новая тема, переносим намерение
            // пользователя на неё.
            val probeUrl = stripTopicIntentParams(url)
            if (probeUrl != null && !urlsEqualIgnoringFragment(probeUrl, url)) {
                if (BuildConfig.DEBUG) {
                    Timber.tag("ThemeReloc").d("strip-probe try url=%s probe=%s", url, probeUrl)
                }
                val probeResolved = runCatching { resolveMovedTopicViaStrippedProbe(originalUrl = url, probeUrl = probeUrl) }
                        .onFailure { Timber.tag("ThemeReloc").w(it, "strip-probe failed") }
                        .getOrNull()
                if (!probeResolved.isNullOrBlank() &&
                        !urlsEqualIgnoringFragment(probeResolved, redirectUrl) &&
                        !urlsEqualIgnoringFragment(probeResolved, url)
                ) {
                    Timber.d("ThemeApi.getTheme: strip-probe relocation -> %s", probeResolved)
                    return loadThemeWithRelocationFallback(
                            probeResolved,
                            hatOpen,
                            pollOpen,
                            relocationAttemptsLeft - 1,
                            openFromUnreadListHint = openFromUnreadListHint
                    )
                }
            }
        }

        return page
    }

    /**
     * Сетевой пробник для определения нового id «перенесённой» темы:
     *  - GET [probeUrl] (без `view=`/`p=`/...) → сервер 302-редиректит на актуальный showtopic.
     *  - Если итоговый URL даёт другой `showtopic`, рассчитываем целевой URL с переносом
     *    исходных параметров (view=getnewpost/p=…/anchor=…) и сохраняем mapping в [MovedTopicResolver].
     */
    private fun resolveMovedTopicViaStrippedProbe(originalUrl: String, probeUrl: String): String? {
        val response = webClient.get(probeUrl)
        val finalUrl: String = response.redirectWithFragment.ifBlank { response.redirect ?: probeUrl }
        if (BuildConfig.DEBUG) {
            Timber.tag("ThemeReloc").d(
                    "strip-probe response code=%d redirect=%s loc=%s probeUrl=%s",
                    response.code,
                    response.redirect,
                    response.locationHeader ?: "",
                    probeUrl
            )
        }
        val origId = extractTopicIdFromUrl(originalUrl) ?: return null
        val newId = extractTopicIdFromUrl(finalUrl) ?: return null
        if (newId == origId) return null
        MovedTopicResolver.remember(oldTopicId = origId, newTopicId = newId)
        val rebuilt = "https://4pda.to/forum/index.php?showtopic=$newId"
        return mergeTopicRequestParamsOntoRelocation(originalRequest = originalUrl, relocationUrl = rebuilt)
    }

    /**
     * Страницы-заглушки при переносе темы иногда отдаются с 200 OK без цепочки HTTP-редиректов
     * (meta refresh / canonical). OkHttp их не следует — парсер постов пустой.
     */
    /** Сохраняем намерение пользователя ([view=getnewpost] и т.д.), если канонический URL его не содержит. */
    private fun urlsEqualIgnoringFragment(a: String, b: String): Boolean {
        fun strip(u: String): String {
            val i = u.indexOf('#')
            return if (i >= 0) u.substring(0, i) else u
        }
        return strip(a).equals(strip(b), ignoreCase = true)
    }

    /**
     * Merges per-post rating text and +/- controls from desktop HTML into [page].
     * Mobile topic HTML (including the body returned after posting) omits `ka_p` / vote UI; this
     * merge is NOT performed by [getTheme] — first render always uses mobile HTML. It is driven by
     * [ThemeViewModel.scheduleDeferredPageMetadataEnrichment] (primary open) and
     * [ThemeViewModel.scheduleDeferredPageMetadataEnrichmentForPage] (hybrid infinite-scroll append),
     * which defer the fetch so it does not compete with first paint and then emit DOM-patch events.
     */
    suspend fun mergeDesktopRatingsIntoPage(page: ThemePage, finalUrl: String) {
        enrichPageMetadata(page, finalUrl)
    }

    /** Desktop/profile metadata merge; not called from [getTheme] so first render stays on mobile HTML. */
    suspend fun enrichPageMetadata(page: ThemePage, finalUrl: String) {
        fetchAndMergeDesktopTopicMetadata(page, finalUrl)
        fetchAndMergeProfileUserPostCounts(page)
        mergeTopicHatUserPostCount(page)
    }

    private fun fetchAndMergeDesktopTopicMetadata(page: ThemePage, finalUrl: String) {
        val desktopUrl = buildDesktopThemeUrl(page, finalUrl) ?: return
        if (page.posts.isEmpty()) {
            if (BuildConfig.DEBUG) Timber.d("ThemeRating: skipped empty posts")
            return
        }

        try {
            val request = NetworkRequest.Builder()
                    .url(desktopUrl)
                    .addHeader("User-Agent", DESKTOP_USER_AGENT)
                    .build()
            val response = webClient.requestWithoutMobileCookie(request)
            val body = response.body
            val ratingTextCount = ThemeRatingParser.countRatingTextMarkers(body)
            val voteControlCount = ThemeRatingParser.countVoteControlMarkers(body)
            val diag = ThemeRatingParser.countDiagnosticsMarkers(body)
            val kaPEntries = ThemeRatingParser.countKaPEntries(body)
            val userPostCounts = themeParser.parseUserPostCountsByPostId(body)
            val ratings = ThemeRatingParser.parsePostRatings(body)
            val postVoteControls = ThemeRatingParser.parsePostVoteControls(body)
            val mergedUserPostCounts = mergeUserPostCounts(page, userPostCounts)
            val propagatedUserPostCounts = propagateUserPostCountsByAuthor(page)
            val merged = mergePostRatings(page, ratings)
            val mergedControls = mergePostVoteControls(page, postVoteControls)
            logUserPostCountDesktopDiagnostics(
                    page = page,
                    body = body,
                    desktopUrl = desktopUrl,
                    parsedCounts = userPostCounts,
                    mergedCounts = mergedUserPostCounts + propagatedUserPostCounts
            )

            if (ratings.isEmpty() || BuildConfig.DEBUG) {
                val anchorPostId = page.anchor
                        ?.removePrefix("entry")
                        ?.trim()
                        ?.toIntOrNull()
                val firstPostId = anchorPostId ?: page.posts.firstOrNull()?.id
                logRatingSnippets(
                        body = body,
                        desktopUrl = desktopUrl,
                        postId = firstPostId,
                        parsedCount = ratings.size
                )
            }
            Timber.d(
                    "ThemeRating: url=%s finalUrl=%s status=%d bodyLen=%d ratingText=%d voteControls=%d kaPEntries=%d zka=%d voteTotal=%d postAction=%d kaId=%d ratingLabel=%d repPost=%d ratePost=%d userPostCounts=%d mergedUserPostCounts=%d userPostCountSample=%s parsed=%d parsedSample=%s parsedControls=%d merged=%d mergedControls=%d mergedSample=%s",
                    desktopUrl,
                    response.redirect ?: "",
                    response.code,
                    body.length,
                    ratingTextCount,
                    voteControlCount,
                    kaPEntries,
                    diag.zkaPhp,
                    diag.voteTotal,
                    diag.postAction,
                    diag.kaId,
                    diag.ratingLabel,
                    diag.repPostControls,
                    diag.ratePost,
                    userPostCounts.size,
                    mergedUserPostCounts + propagatedUserPostCounts,
                    sampleUserPostCounts(page),
                    ratings.size,
                    sampleRatings(ratings),
                    postVoteControls.size,
                    merged,
                    mergedControls,
                    sampleMergedRatings(page)
            )
        } catch (e: Throwable) {
            Timber.w(e, "ThemeRating: desktop metadata merge failed")
        }
    }

    private suspend fun fetchAndMergeProfileUserPostCounts(page: ThemePage): Int {
        val missingPosts = page.posts
                .asSequence()
                .filter { it.userPostCount == null && it.userId > 0 }
                .distinctBy { it.userId }
                .take(MAX_PROFILE_POST_COUNT_FETCHES_PER_PAGE)
                .toList()
        if (missingPosts.isEmpty()) return 0

        // Профильные fetch'и не зависят друг от друга — выполняем их параллельно (с ограничением),
        // чтобы не превращать дозагрузку счётчиков в цепочку из ~N последовательных round-trip'ов.
        // Semaphore ограничивает параллелизм, чтобы не долбить форум десятками одновременных
        // запросов профилей на страницах с большим числом уникальных авторов.
        // Применение к page.posts — строго последовательно (page не потокобезопасен).
        val gate = Semaphore(PROFILE_POST_COUNT_FETCH_CONCURRENCY)
        val resolvedCounts: List<Pair<ThemePost, Int>> = if (missingPosts.size == 1) {
            listOfNotNull(resolveProfileUserPostCount(missingPosts.first())?.let { missingPosts.first() to it })
        } else {
            runCatching {
                coroutineScope {
                    missingPosts
                            .map { post -> async(Dispatchers.IO) { gate.withPermit { resolveProfileUserPostCount(post)?.let { post to it } } } }
                            .awaitAll()
                            .filterNotNull()
                }
            }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                missingPosts.mapNotNull { post -> resolveProfileUserPostCount(post)?.let { count -> post to count } }
            }
        }
        if (resolvedCounts.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Timber.tag("ThemeUserPosts").d(
                        "profileFallback merged=0 unique=%d missingAfter=%d pageSample=%s",
                        missingPosts.size,
                        page.posts.count { it.userPostCount == null },
                        sampleUserPostCounts(page)
                )
            }
            return 0
        }

        var merged = 0
        resolvedCounts.forEach { (post, count) ->
            page.posts.forEach { candidate ->
                if (candidate.userPostCount == null && sameAuthor(candidate, post)) {
                    candidate.userPostCount = count
                    merged++
                }
            }
            page.topicHatPost?.takeIf { it.userPostCount == null && sameAuthor(it, post) }?.userPostCount = count
        }
        if (BuildConfig.DEBUG) {
            Timber.tag("ThemeUserPosts").d(
                    "profileFallback merged=%d unique=%d missingAfter=%d pageSample=%s",
                    merged,
                    missingPosts.size,
                    page.posts.count { it.userPostCount == null },
                    sampleUserPostCounts(page)
            )
        }
        return merged
    }

    private fun resolveProfileUserPostCount(post: ThemePost): Int? {
        val userId = post.userId.takeIf { it > 0 } ?: return null
        synchronized(profileUserPostCountById) {
            profileUserPostCountById[userId]?.let { return it }
        }
        val nickKey = post.nick?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (nickKey != null) {
            synchronized(profileUserPostCountByNick) {
                profileUserPostCountByNick[nickKey]?.let { cached ->
                    synchronized(profileUserPostCountById) { profileUserPostCountById[userId] = cached }
                    return cached
                }
            }
        }

        val profileUrl = "https://4pda.to/forum/index.php?showuser=$userId"
        // Fetch the profile with the DESKTOP skin (no mobile cookie + desktop UA), exactly like
        // [fetchAndMergeDesktopTopicMetadata]. The reliable parse branches in
        // [parseProfileUserPostCount] (the `title`+`area` layout) mirror the production
        // `forum_stats`/`site_stats` patterns (patterns.json), which describe the DESKTOP profile.
        // The mobile-skin profile page can omit the «Постов» stat row (or render it via a layout
        // none of the branches match), so for a subset of users `webClient.get` returned a body that
        // parsed to null — the count silently went missing only for them. Forcing the desktop skin
        // makes the markup consistent for every user.
        val body = runCatching {
            val request = NetworkRequest.Builder()
                    .url(profileUrl)
                    .addHeader("User-Agent", DESKTOP_USER_AGENT)
                    .build()
            webClient.requestWithoutMobileCookie(request).body
        }.onFailure {
            if (BuildConfig.DEBUG) Timber.tag("ThemeUserPosts").d(it, "profileFallback fetch failed user=%d", userId)
        }.getOrNull() ?: return null
        val count = parseProfileUserPostCount(body)?.takeIf { it > 0 } ?: run {
            if (BuildConfig.DEBUG) {
                // No exception, but no count parsed — capture exactly what's needed to pin a
                // not-yet-handled profile layout (or a login wall / redirect) for THIS user.
                Timber.tag("ThemeUserPosts").d(
                        "profileFallback parsedNull user=%d nick=%s bodyLen=%d hasTitleArea=%s hasPostsLabel=%s hasDataMemberPosts=%s loginWall=%s",
                        userId,
                        post.nick.orEmpty(),
                        body.length,
                        body.contains("class=\"area\"", ignoreCase = true) &&
                                body.contains("class=\"title\"", ignoreCase = true),
                        body.contains(Regex("""(?i)Постов|Сообщени[йя]|Posts?""")),
                        body.contains("data-member-posts", ignoreCase = true),
                        body.contains(Regex("""(?i)act=login|enterusername|name=["']password["']"""))
                )
            }
            return null
        }

        synchronized(profileUserPostCountById) { profileUserPostCountById[userId] = count }
        if (nickKey != null) synchronized(profileUserPostCountByNick) { profileUserPostCountByNick[nickKey] = count }
        return count
    }

    private fun sameAuthor(a: ThemePost, b: ThemePost): Boolean {
        if (a.userId > 0 && b.userId > 0) return a.userId == b.userId
        val an = a.nick?.trim()
        val bn = b.nick?.trim()
        return !an.isNullOrEmpty() && an.equals(bn, ignoreCase = true)
    }

    private fun mergeTopicHatUserPostCount(page: ThemePage) {
        val hat = page.topicHatPost ?: return
        if (hat.userPostCount != null) return
        page.posts.firstOrNull { candidate ->
            candidate.userPostCount?.let { it > 0 } == true &&
                    (candidate.id == hat.id || sameAuthor(candidate, hat) || (hat.number > 0 && candidate.number == hat.number))
        }?.let { hat.userPostCount = it.userPostCount }
    }

    private fun mergeUserPostCounts(page: ThemePage, counts: Map<Int, Int>): Int {
        var merged = 0
        page.posts.forEach { post ->
            val count = counts[post.id]?.takeIf { it > 0 } ?: return@forEach
            if (post.userPostCount?.let { it > 0 } == true) return@forEach
            if (post.userPostCount != count) {
                post.userPostCount = count
                merged++
            }
        }
        return merged
    }

    private fun propagateUserPostCountsByAuthor(page: ThemePage): Int {
        val countByUserId = linkedMapOf<Int, Int>()
        val countByNick = linkedMapOf<String, Int>()
        page.posts.forEach { post ->
            val count = post.userPostCount?.takeIf { it > 0 } ?: return@forEach
            if (post.userId > 0) countByUserId.putIfAbsent(post.userId, count)
            post.nick
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { countByNick.putIfAbsent(it, count) }
        }
        var propagated = 0
        page.posts.forEach { post ->
            if (post.userPostCount?.let { it > 0 } == true) return@forEach
            val count = when {
                post.userId > 0 -> countByUserId[post.userId]
                else -> null
            } ?: post.nick
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { countByNick[it] }
            if (count != null && count > 0) {
                post.userPostCount = count
                propagated++
            }
        }
        return propagated
    }

    private fun logUserPostCountDesktopDiagnostics(
            page: ThemePage,
            body: String,
            desktopUrl: String,
            parsedCounts: Map<Int, Int>,
            mergedCounts: Int
    ) {
        if (!BuildConfig.DEBUG) return
        val missing = page.posts.count { it.userPostCount == null }
        if (missing == 0 && parsedCounts.isNotEmpty()) return
        Timber.tag("ThemeUserPosts").d(
                "desktopMerge url=%s missing=%d/%d parsed=%d merged=%d hasPostUserInfo=%s hasCountLabel=%s hasDataMemberPosts=%s bodyLen=%d parsedSample=%s pageSample=%s",
                desktopUrl,
                missing,
                page.posts.size,
                parsedCounts.size,
                mergedCounts,
                body.contains("post_user_info", ignoreCase = true),
                body.contains(Regex("""(?i)Сообщени[йя]|Posts?""")),
                body.contains("data-member-posts", ignoreCase = true),
                body.length,
                sampleCounts(parsedCounts),
                sampleUserPostCounts(page)
        )
    }

    private fun logRatingSnippets(body: String, desktopUrl: String, postId: Int?, parsedCount: Int) {
        fun windowAround(term: String): String? {
            val idx = body.indexOf(term)
            if (idx < 0) return null
            return "term=$term idx=$idx"
        }

        fun windowAroundFirstMatch(terms: List<String>): String? {
            for (t in terms) {
                windowAround(t)?.let { return it }
            }
            return null
        }

        val entryTerm = postId?.let { id ->
            windowAroundFirstMatch(
                    terms = listOf("id=\"entry$id\"", "id='entry$id'", "entry$id"),
            )
        }

        val actRep = windowAround("act=rep")
        val findPost = windowAround("view=findpost&p=")

        val parts = listOfNotNull(
                entryTerm?.let { "entryMatch{$it}" },
                actRep?.let { "actRepMatch{$it}" },
                findPost?.let { "findpostMatch{$it}" },
        )
        if (parts.isEmpty()) return
        val htmlDiag = FpdaDebugLog.classifyHtml(body)
        Timber.tag("ThemeRatingSnip").d(
                "url=%s parsed=%d bodyLen=%d htmlHash=%s %s",
                desktopUrl,
                parsedCount,
                body.length,
                htmlDiag["htmlHash"],
                parts.joinToString(" | ")
        )
    }

    private fun mergePostRatings(page: ThemePage, ratings: Map<Int, String>): Int {
        var merged = 0
        page.posts.forEach { post ->
            val rating = ratings[post.id] ?: return@forEach
            if (post.postRating != rating) {
                post.postRating = rating
                merged++
            }
        }
        return merged
    }

    private fun mergePostVoteControls(page: ThemePage, controls: Map<Int, ThemeRatingParser.PostVoteControls>): Int {
        var merged = 0
        page.posts.forEach { post ->
            val postControls = controls[post.id] ?: return@forEach
            if (post.canPlusPostRating != postControls.canPlus || post.canMinusPostRating != postControls.canMinus) {
                post.canPlusPostRating = postControls.canPlus
                post.canMinusPostRating = postControls.canMinus
                merged++
            }
        }
        return merged
    }

    private fun sampleRatings(ratings: Map<Int, String>): String {
        return ratings.entries
                .take(5)
                .joinToString(prefix = "[", postfix = "]") { "${it.key}=${it.value}" }
    }

    private fun sampleCounts(counts: Map<Int, Int>): String {
        return counts.entries
                .take(5)
                .joinToString(prefix = "[", postfix = "]") { "${it.key}=${it.value}" }
    }

    private fun sampleUserPostCounts(page: ThemePage): String {
        return page.posts
                .asSequence()
                .filter { it.userPostCount != null }
                .take(5)
                .joinToString(prefix = "[", postfix = "]") { "${it.id}=${it.userPostCount}" }
    }

    private fun sampleMergedRatings(page: ThemePage): String {
        return page.posts
                .asSequence()
                .filter { !it.postRating.isNullOrBlank() }
                .take(5)
                .joinToString(prefix = "[", postfix = "]") { "${it.id}=${it.postRating}" }
    }

    private fun buildDesktopThemeUrl(page: ThemePage, finalUrl: String): String? {
        val topicId = page.id.takeIf { it > 0 }
                ?: extractTopicIdFromUrl(finalUrl)
                ?: return null
        val st = extractStFromUrl(finalUrl) ?: page.st
        return "https://4pda.to/forum/index.php?showtopic=$topicId&st=${st.coerceAtLeast(0)}"
    }

    fun reportPost(topicId: Int, postId: Int, message: String): Boolean {
        val request = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=report&send=1&t=$topicId&p=$postId")
                .formHeader("message", Cp1251Codec.encode(message), true)
                .build()
        val response = webClient.request(request)
        val p = Pattern.compile("<div class=\"errorwrap\">\n" +
                "\\s*<h4>Причина:</h4>\n" +
                "\\s*\n" +
                "\\s*<p>(.*)</p>", Pattern.MULTILINE)
        val m = p.matcher(response.body)
        if (m.find()) {
            throw Exception("Ошибка отправки жалобы: " + m.group(1))
        }
        return true
    }

    fun deletePost(postId: Int): Boolean {
        val url = "https://4pda.to/forum/index.php?act=zmod&auth_key=${webClient.getAuthKey()}&code=postchoice&tact=delete&selectedpids=$postId"
        val response = webClient.request(NetworkRequest.Builder().url(url).xhrHeader().build())
        val body = response.body
        val bodyTrimmed = body.trim()
        val success = response.code in 200..299 && bodyTrimmed.equals("ok", ignoreCase = true)
        if (BuildConfig.DEBUG) {
            Timber.d(
                    "deletePost: postId=%d action=delete status=%d bodyClue=%s success=%s",
                    postId,
                    response.code,
                    deletePostBodyClue(body),
                    success
            )
        }
        if (!success) {
            throw Exception("Ошибка удаления сообщения: ${deletePostBodyClue(body)}")
        }
        return true
    }

    private fun deletePostBodyClue(body: String): String {
        val errorWrap = Pattern.compile(
                "<div class=\"errorwrap\">[\\s\\S]*?<p>(.*?)</p>",
                Pattern.CASE_INSENSITIVE
        ).matcher(body)
        val raw = if (errorWrap.find()) {
            errorWrap.group(1).orEmpty()
        } else {
            body
        }
        val clue = raw
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace(Regex("\\s+"), " ")
                .trim()
        return clue.take(180).ifBlank { "пустой ответ сервера" }
    }

    fun votePost(postId: Int, type: Boolean): String {
        val response = webClient.get("https://4pda.to/forum/zka.php?i=$postId&v=${if (type) "1" else "-1"}")
        var result: String? = null

        val alreadyVote = "Ошибка: Вы уже голосовали за это сообщение"

        val m = Pattern.compile("ok:\\s*?((?:\\+|\\-)?\\d+)").matcher(response.body.orEmpty())
        if (m.find()) {
            when (m.group(1)?.toIntOrNull()) {
                0 -> result = alreadyVote
                1 -> result = "Репутация поста повышена"
                -1 -> result = "Репутация поста понижена"
                else -> {}
            }
        }
        if (response.body == "evote") {
            result = alreadyVote
        }
        if (result == null) {
            throw Exception("Ошибка изменения репутации поста")
        }
        return result
    }

    fun submitPoll(action: String, method: String, encodedForm: String): ThemePage {
        val normalizedAction = absolutizeFourPdaForumHref(action)?.takeIf { it.isNotBlank() }
                ?: action.takeIf { it.isNotBlank() }
                ?: "https://4pda.to/forum/index.php"
        val response = if (method.equals("post", ignoreCase = true)) {
            val request = NetworkRequest.Builder()
                    .url(normalizedAction)
                    .rawBody(encodedForm)
                    .build()
            webClient.request(request)
        } else {
            val separator = if (normalizedAction.contains("?")) "&" else "?"
            webClient.get(normalizedAction + separator + encodedForm)
        }
        val finalUrl = response.redirectWithFragment.ifBlank { response.redirect ?: normalizedAction }
        return themeParser.parsePage(response.body, finalUrl, pollOpen = true, initialRequestUrl = finalUrl)
    }

    companion object {
        val elemToScrollPattern = Pattern.compile("(?:anchor=|#)([^&\\n\\=\\?\\.\\#]*)")
        val attachImagesPattern = Pattern.compile("(4pda\\.to\\/forum\\/dl\\/post\\/\\d+\\/[^\"']*?\\.(?:jpe?g|png|gif|bmp|webp)(?:\\?[^\"'\\s<>]*)?)\"?(?:[^>]*?title=\"([^\"']*?\\.(?:jpe?g|png|gif|bmp|webp)) - [^\"']*?\")?")

        // Завершающий & не входит в match — иначе второй &p=… в строке не находится.
        private val topicUrlPostIdP = Regex("""[?&]p=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private val topicUrlPostIdPid = Regex("""[?&]pid=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private val topicUrlHighlight = Regex("""[?&]highlight=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private val topicUrlEntryFragment = Regex("""#entry(\d+)\b""", RegexOption.IGNORE_CASE)
        private val topicUrlSt = Regex("""[?&]st=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private const val MAX_PROFILE_POST_COUNT_FETCHES_PER_PAGE = 200
        /**
         * Upper bound on parallel profile-page fetches inside [fetchAndMergeProfileUserPostCounts].
         * Keeps the forum from being hammered when a page has many distinct authors; profile fetches
         * are independent so they still run concurrently up to this limit.
         */
        private const val PROFILE_POST_COUNT_FETCH_CONCURRENCY = 8

        private val topicUrlTopicId = Regex("""[?&]showtopic=(\d+)""", RegexOption.IGNORE_CASE)
        private val topicUrlTopicIdSlash = Regex("""(?:forum/index\.php/topic/|/topic/)(\d+)""", RegexOption.IGNORE_CASE)
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val profileUserPostCountById = linkedMapOf<Int, Int>()
        private val profileUserPostCountByNick = linkedMapOf<String, Int>()

        /**
         * Извлекает «куда на самом деле надо идти», если открыли тему по устаревшей ссылке (перенос / заглушка / 404).
         *
         * Источники:
         * - `<link rel="canonical" …>`
         * - `<meta http-equiv="refresh" content="…; url=…">`
         * - HTML-заглушка «Тема перенесена» с явной ссылкой на новую тему (showtopic=/topic/…)
         */
        internal fun extractTopicRelocationUrlFromHtml(html: String, originalRequestUrl: String? = null): String? {
            fun isTopicLikeAbsUrl(u: String): Boolean {
                return u.contains("showtopic=", ignoreCase = true) || topicUrlTopicIdSlash.containsMatchIn(u)
            }

            fun htmlDecodeUrl(u: String): String {
                return u.trim()
                        .replace("&amp;", "&")
                        .replace("&#038;", "&")
                        .replace("&#x26;", "&")
                        .replace("&#47;", "/")
                        .replace("&#x2f;", "/")
                        .replace("&#x2F;", "/")
                        .replace("\\u0026", "&")
                        .replace("\\/", "/")
            }

            fun tryCanonical(m: MatchResult): String? {
                val href = htmlDecodeUrl(m.groupValues.getOrNull(1)?.trim().orEmpty())
                val abs = absolutizeFourPdaForumHref(href) ?: return null
                return abs.takeIf { isTopicLikeAbsUrl(it) }
            }

            Regex("""(?is)<link[^>]*\brel\s*=\s*["']canonical["'][^>]*\bhref\s*=\s*["']([^"']+)["']""")
                .find(html)?.let { tryCanonical(it) }?.let { return it }
            Regex("""(?is)<link[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\brel\s*=\s*["']canonical["']""")
                .find(html)?.let { tryCanonical(it) }?.let { return it }

            fun extractUrlFromRefreshContent(content: String?): String {
                if (content.isNullOrBlank()) return ""
                val parts = content.split(";")
                for (p in parts) {
                    val t = p.trim()
                    val low = t.lowercase()
                    val idx = low.indexOf("url=")
                    if (idx >= 0) {
                        return htmlDecodeUrl(t.substring(idx + 4).trim().trim('"', '\''))
                    }
                }
                return ""
            }

            Regex("""(?is)<meta[^>]*\bhttp-equiv\s*=\s*["']refresh["'][^>]*\bcontent\s*=\s*["']([^"']+)["']""").find(html)?.let { m ->
                extractUrlFromRefreshContent(m.groupValues.getOrNull(1)).let { u ->
                    absolutizeFourPdaForumHref(u)?.takeIf { isTopicLikeAbsUrl(it) }
                }
            }?.let { return it }
            Regex("""(?is)<meta[^>]*\bcontent\s*=\s*["']([^"']+)["'][^>]*\bhttp-equiv\s*=\s*["']refresh["']""").find(html)?.let { m ->
                extractUrlFromRefreshContent(m.groupValues.getOrNull(1)).let { u ->
                    absolutizeFourPdaForumHref(u)?.takeIf { isTopicLikeAbsUrl(it) }
                }
            }?.let { return it }

            // Иногда 404/заглушка редиректит через JS (без meta refresh / canonical).
            fun tryJsRedirect(re: Regex): String? {
                val raw = re.find(html)?.groupValues?.getOrNull(1)?.orEmpty() ?: return null
                val u = htmlDecodeUrl(raw)
                val abs = absolutizeFourPdaForumHref(u) ?: u.takeIf { it.startsWith("http", ignoreCase = true) }
                return abs?.takeIf { isTopicLikeAbsUrl(it) }
            }

            listOf(
                    Regex("""(?is)\b(?:window\.)?location(?:\.href)?\s*=\s*["']([^"']+)["']"""),
                    Regex("""(?is)\b(?:window\.)?location\.replace\(\s*["']([^"']+)["']\s*\)"""),
                    Regex("""(?is)\bdocument\.location(?:\.href)?\s*=\s*["']([^"']+)["']"""),
            ).forEach { re ->
                tryJsRedirect(re)?.let { return it }
            }

            // Частый случай переноса: 404/заглушка с текстом и ссылкой на новую тему.
            // ВАЖНО: не сканируем все href подряд по всей странице — иначе ловим showtopic= из контента постов/навигации.
            fun errorLikeHtmlScope(): String? {
                val markers = listOf(
                        "errorwrap",
                        "wrap1", // некоторые шаблоны 4pda для ошибок
                        "Тема перенесена",
                        "Тема перемещена",
                        "Тема не найдена",
                        "Ошибка 404",
                        "Topic not found",
                )
                val idx = markers
                        .asSequence()
                        .map { m -> html.indexOf(m, ignoreCase = true) }
                        .filter { it >= 0 }
                        .minOrNull()
                        ?: return null
                val from = (idx - 6000).coerceAtLeast(0)
                val to = (idx + 12000).coerceAtMost(html.length)
                return html.substring(from, to)
            }

            val origId = originalRequestUrl?.let { extractTopicIdFromUrl(it) }
            val scopedHtml = errorLikeHtmlScope() ?: return null
            val hrefRe = Regex(
                """(?is)\bhref\s*=\s*["']([^"']*(?:showtopic=\d+|/topic/\d+)[^"']*)["']"""
            )
            val candidates = hrefRe.findAll(scopedHtml)
                .mapNotNull { m ->
                    val raw = htmlDecodeUrl(m.groupValues.getOrNull(1)?.trim().orEmpty())
                    absolutizeFourPdaForumHref(raw)
                }
                .filter { isTopicLikeAbsUrl(it) }
                .distinct()
                .toList()
            if (candidates.isNotEmpty()) {
                if (origId != null) {
                    candidates.firstOrNull { extractTopicIdFromUrl(it) != null && extractTopicIdFromUrl(it) != origId }
                        ?.let { return it }
                }
                return candidates.first()
            }

            // Последний шанс: в некоторых заглушках URL встречается как текст (без href).
            // Ограничиваемся только topic-like подпоследовательностями, чтобы не ловить мусор.
            val rawTextTopic = Regex(
                    """(?is)(?:https?:)?//4pda\.to/forum/index\.php\?showtopic=\d+[^\s"'<>]*|/forum/index\.php\?showtopic=\d+[^\s"'<>]*|\bshowtopic=\d+\b"""
            ).find(scopedHtml)?.value
            if (!rawTextTopic.isNullOrBlank()) {
                val decoded = rawTextTopic
                        .trim()
                        .replace("&amp;", "&")
                        .replace("&#038;", "&")
                        .replace("&#x26;", "&")
                        .replace("\\u0026", "&")
                val abs = absolutizeFourPdaForumHref(decoded)
                        ?: if (decoded.startsWith("http", ignoreCase = true)) decoded else null
                        ?: if (decoded.startsWith("showtopic=", ignoreCase = true)) "https://4pda.to/forum/index.php?$decoded" else null
                if (!abs.isNullOrBlank() && isTopicLikeAbsUrl(abs)) {
                    if (origId == null) return abs
                    val newId = extractTopicIdFromUrl(abs)
                    if (newId != null && newId != origId) return abs
                }
            }
            extractMinimalRedirectTopicUrl(html)?.let { return it }
            return null
        }

        /**
         * Minimal redirect/stub pages: a lone topic link without forum post markup.
         */
        internal fun extractMinimalRedirectTopicUrl(html: String): String? {
            if (htmlContainsTopicPostContent(html)) return null
            val hrefRe = Regex(
                    """(?is)\bhref\s*=\s*["']([^"']*(?:showtopic=\d+|/topic/\d+|forum/index\.php\?showtopic=\d+)[^"']*)["']"""
            )
            fun decode(u: String): String = u.trim()
                    .replace("&amp;", "&")
                    .replace("&#038;", "&")
                    .replace("&#x26;", "&")
            return hrefRe.findAll(html)
                    .mapNotNull { m ->
                        val raw = decode(m.groupValues.getOrNull(1).orEmpty())
                        absolutizeFourPdaForumHref(raw)
                    }
                    .firstOrNull { u ->
                        u.contains("showtopic=", ignoreCase = true) || topicUrlTopicIdSlash.containsMatchIn(u)
                    }
        }

        private fun htmlContainsTopicPostContent(html: String): Boolean {
            if (Regex("""(?is)<a\s+name\s*=\s*["']entry\d+["']""").containsMatchIn(html)) return true
            return Regex("""(?is)class\s*=\s*["'][^"']*\bpost[-_ ]?(?:block|container|body)\b""").containsMatchIn(html)
        }

        private fun looksLike404ErrorPage(html: String): Boolean {
            // Строгая эвристика: только явные заголовки/тексты ошибок, без «404» в произвольном контенте.
            val h = html
            return h.contains("Ой! Ошибка 404", ignoreCase = true) ||
                    h.contains("Ошибка 404", ignoreCase = true) ||
                    h.contains("Тема не найдена", ignoreCase = true) ||
                    h.contains("Topic not found", ignoreCase = true)
        }

        /**
         * Candidate relocation URL for "moved topic"/stub/404 pages.
         * Prefers server `Location` header when present even for non-3xx responses.
         */
        internal fun extractRelocationCandidate(
                responseLocationHeader: String?,
                html: String,
                originalRequestUrl: String? = null
        ): String? {
            fun isTopicLikeAbsUrl(u: String): Boolean {
                return u.contains("showtopic=", ignoreCase = true) || topicUrlTopicIdSlash.containsMatchIn(u)
            }
            val loc = responseLocationHeader
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.replace("&amp;", "&")
                    ?.replace("&#038;", "&")
                    ?.replace("&#x26;", "&")
                    ?.replace("\\u0026", "&")
            if (!loc.isNullOrBlank()) {
                val abs = absolutizeFourPdaForumHref(loc) ?: loc.takeIf { it.startsWith("http", ignoreCase = true) }
                if (!abs.isNullOrBlank() && isTopicLikeAbsUrl(abs)) return abs
            }
            return extractTopicRelocationUrlFromHtml(html = html, originalRequestUrl = originalRequestUrl)
        }

        /** Сохраняем намерение пользователя ([view=getnewpost] и т.д.), если канонический URL его не содержит. */
        @JvmStatic
        internal fun mergeTopicRequestParamsOntoRelocation(originalRequest: String, relocationUrl: String): String {
            val orig = runCatching { Uri.parse(originalRequest) }.getOrNull() ?: return relocationUrl
            val relocation = runCatching { Uri.parse(relocationUrl) }.getOrNull() ?: return relocationUrl
            var builder = relocation.buildUpon()

            fun copyQueryParamIfMissing(name: String) {
                val vOrig = orig.getQueryParameter(name)?.trim().orEmpty()
                if (vOrig.isEmpty()) return
                if (!relocation.getQueryParameter(name).isNullOrEmpty()) return
                builder = builder.appendQueryParameter(name, vOrig)
            }

            // Сохраняем намерение пользователя: «к непрочитанному», позиционирование на пост, смещение и т.д.
            listOf("view", "st", "p", "pid", "highlight", "anchor").forEach { copyQueryParamIfMissing(it) }

            // Если исходный URL явно указывал фрагмент, стараемся не терять его.
            val fragOrig = orig.fragment?.trim().orEmpty()
            val fragRel = relocation.fragment?.trim().orEmpty()
            if (fragOrig.isNotEmpty() && fragRel.isEmpty()) {
                builder = builder.fragment(fragOrig)
            }
            return builder.build().toString()
        }

        /**
         * URL «без намерения пользователя»: `showtopic=` (и `st=` если он значимый), без
         * `view=`/`p=`/`pid=`/`highlight=`/`anchor=` и фрагмента. Сервер для тем-указателей
         * именно на такой «голый» URL отдаёт 302 → новый id; добавление `view=getnewpost`
         * заставляет его отдать 404 без подсказок.
         *
         * Возвращает null, если topic id извлечь не удалось или URL и так уже «голый».
         */
        @JvmStatic
        internal fun stripTopicIntentParams(url: String): String? {
            val topicId = extractTopicIdFromUrl(url) ?: return null
            val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            val builder = Uri.Builder()
                    .scheme(parsed.scheme?.takeIf { it.isNotBlank() } ?: "https")
                    .authority(parsed.authority?.takeIf { it.isNotBlank() } ?: "4pda.to")
                    .path(parsed.path?.takeIf { it.isNotBlank() } ?: "/forum/index.php")
                    .appendQueryParameter("showtopic", topicId.toString())
            val st = parsed.getQueryParameter("st")?.trim()?.toIntOrNull()
            if (st != null && st > 0) {
                builder.appendQueryParameter("st", st.toString())
            }
            val stripped = builder.build().toString()
            // Если URL и так был «голым» (без интент-параметров), пробник не имеет смысла.
            if (stripped.equals(url, ignoreCase = true)) return null
            return stripped
        }

        /**
         * ID темы из URL: showtopic=X или /topic/X.
         */
        fun extractTopicIdFromUrl(url: String): Int? {
            topicUrlTopicId.find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            topicUrlTopicIdSlash.find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            return null
        }

        fun extractStFromUrl(url: String): Int? {
            return topicUrlSt.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        /**
         * ID поста из query (p / pid / highlight) или фрагмента `#entry…` в URL темы.
         * После `view=getnewpost` часть скинов даёт `highlight=`, а не `p=`.
         */
        fun extractPostIdFromTopicUrl(url: String): String? {
            topicUrlPostIdP.findAll(url).lastOrNull()?.groupValues?.get(1)?.let { return it }
            topicUrlPostIdPid.findAll(url).lastOrNull()?.groupValues?.get(1)?.let { return it }
            topicUrlHighlight.findAll(url).lastOrNull()?.groupValues?.get(1)?.let { return it }
            topicUrlEntryFragment.find(url)?.groupValues?.get(1)?.let { return it }
            return null
        }

        internal fun parseProfileUserPostCount(profileHtml: String): Int? {
            // Modern IPS Community profile layout: a list item whose label is «Сообщений»/«Posts»
            // and whose count lives in an element carrying the `data-member-posts` marker, e.g.
            //   <li class="ipsDataItem">
            //     <span class="ipsDataItem_generic ..."><strong>Сообщений</strong></span>
            //     <span class="ipsDataItem_main ..." data-member-posts="1234">1 234</span>
            //   </li>
            // The attribute may carry the count directly (data-member-posts="1234") or be empty
            // with the count in the element text. Require BOTH the IPS marker and a nearby
            // «Сообщений/Постов/Posts» label so this branch can't accidentally grab the wrong
            // «Сообщений» instance on a non-IPS profile.
            Regex("""(?is)(Постов|Сообщений|Posts?)(?:\s|&nbsp;|&#1?60;|&#x0?A0;|:|</?[^>]+>){0,80}?<[^>]*\bdata-member-posts\b[^>]*>(?:([^>"']{0,40})|)</[^>]*>""")
                    .find(profileHtml)
                    ?.let { match ->
                        // Prefer the inline text between the marker tag and its closing tag; fall
                        // back to the `data-member-posts="..."` attribute value when the tag is empty.
                        val inlineText = match.groups[2]?.value.orEmpty().ifBlank {
                            Regex("""(?is)\bdata-member-posts\s*=\s*["']([^"']+)["']""")
                                    .find(match.value)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    .orEmpty()
                        }
                        Regex("""[0-9][0-9\s.,]*""")
                                .find(inlineText)
                                ?.value
                                ?.replace(Regex("""[^\d]"""), "")
                                ?.takeIf { it.isNotEmpty() }
                                ?.toIntOrNull()
                                ?.takeIf { it > 0 }
                    }
                    ?.let { return it }

            Regex("""(?is)<span\b[^>]*\bclass\s*=\s*["'][^"']*\btitle\b[^"']*["'][^>]*>\s*(Постов|Сообщений|Posts?)\s*</span>\s*<div\b[^>]*\bclass\s*=\s*["'][^"']*\barea\b[^"']*["'][^>]*>([\s\S]*?)</div>""")
                    .findAll(profileHtml)
                    .firstNotNullOfOrNull { match ->
                        val label = match.groups[1]?.value.orEmpty()
                        if (!label.contains(Regex("""(?i)Постов|Сообщений|Posts?"""))) return@firstNotNullOfOrNull null
                        val areaText = match.groups[2]?.value.orEmpty()
                                .replace(Regex("""(?is)<[^>]+>"""), " ")
                                .replace("&nbsp;", " ")
                                .replace("&#160;", " ")
                                .replace("&#xA0;", " ")
                        Regex("""[0-9][0-9\s.,]*""")
                                .find(areaText)
                                ?.value
                                ?.replace(Regex("""[^\d]"""), "")
                                ?.takeIf { it.isNotEmpty() }
                                ?.toIntOrNull()
                                ?.takeIf { it > 0 }
                    }
                    ?.let { return it }

            Regex("""(?is)(?:Постов|Сообщений|Posts?)(?:\s|&nbsp;|&#160;|&#x0?A0;|:|</?[^>]+>)*([0-9][0-9\s.,]*)""")
                    .find(profileHtml)
                    ?.groups
                    ?.get(1)
                    ?.value
                    ?.replace(Regex("""[^\d]"""), "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { return it }

            val text = profileHtml
                    .replace(Regex("""(?is)<br\b[^>]*>"""), " ")
                    .replace(Regex("""(?is)</(?:span|div|li|td|tr|p|a|strong|b)>"""), " ")
                    .replace(Regex("""(?is)<[^>]+>"""), " ")
                    .replace("&nbsp;", " ")
                    .replace("&#160;", " ")
                    .replace("&#xA0;", " ")
                    .replace("&amp;", "&")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
            val raw = listOf(
                    Regex("""(?i)(?:форум(?:\s+|&nbsp;)*:?\s*)?(?:пост(?:ов|а|ы)?|сообщени[йя]|posts?)(?![а-яa-z])\s*:?\s*([0-9][0-9\s.,]*)"""),
                    Regex("""(?i)(?:forum\s+)?posts?\s*:?\s*([0-9][0-9\s.,]*)""")
            ).asSequence()
                    .mapNotNull { it.find(text)?.groups?.get(1)?.value }
                    .firstOrNull()
                    ?: return null
            return raw.replace(Regex("""[^\d]"""), "").takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf { it > 0 }
        }

        /**
         * Для скролла после `view=getnewpost`: фрагмент `#entry…` — то, что форум задаёт в hash после редиректа;
         * он важнее `highlight=` в query (скин часто оставляет highlight на другой пост, а целевой — в hash).
         */
        fun extractScrollPostIdFromFinalTopicUrl(url: String): String? {
            topicUrlEntryFragment.find(url)?.groupValues?.getOrNull(1)?.let { return it }
            extractExplicitQueryPostIdFromTopicUrl(url)?.let { return it }
            return null
        }

        /** `p` / `pid` / `highlight` в query (без hash) — явное указание поста сервером. */
        fun extractExplicitQueryPostIdFromTopicUrl(url: String): String? {
            extractLastReadStylePostIdFromTopicUrl(url)?.let { return it }
            extractHighlightPostIdFromTopicUrl(url)?.let { return it }
            return null
        }

        /** `highlight=` после `view=getnewpost` — целевой пост скролла (в отличие от `p=`/`pid=`). */
        fun extractHighlightPostIdFromTopicUrl(url: String): String? {
            val queryPart = url.substringBefore('#')
            return topicUrlHighlight.findAll(queryPart).lastOrNull()?.groupValues?.get(1)
        }

        /** `p`/`pid` в query — часто last-read контекст, не первый непрочитанный. */
        fun extractLastReadStylePostIdFromTopicUrl(url: String): String? {
            val queryPart = url.substringBefore('#')
            topicUrlPostIdP.findAll(queryPart).lastOrNull()?.groupValues?.get(1)?.let { return it }
            return topicUrlPostIdPid.findAll(queryPart).lastOrNull()?.groupValues?.get(1)
        }

        /**
         * При `view=getnewpost` сервер часто ставит `p=`/`#entry` на первый пост страницы (уже прочитанный).
         * Не используем такой hint, если на странице есть другие посты ниже.
         */
        fun isLikelyLastReadPageTopHint(postId: Int, entryIds: List<Int>, hatPostIdToSkip: Int? = null): Boolean {
            if (entryIds.size <= 1) return false
            if (postId == entryIds.firstOrNull()) return true
            if (hatPostIdToSkip != null && postId == hatPostIdToSkip) return true
            return false
        }

        /**
         * При `view=getnewpost` сервер иногда ставит `#entry` на **последний** пост страницы (новейший
         * в листе), хотя первое непрочитанное — выше. Не доверяем такому hint при нескольких постах.
         */
        fun isLikelyLastReadPageBottomHint(postId: Int, entryIds: List<Int>): Boolean {
            if (entryIds.size <= 1) return false
            return postId == entryIds.lastOrNull()
        }

        /**
         * `view=getnewpost` без HTML-маркеров непрочитанного: редирект на последний пост страницы —
         * позиция last-read, а не первое непрочитанное.
         */
        fun isLikelyAllReadGetNewPostBottomRedirect(redirectHashId: Int?, entryIds: List<Int>): Boolean {
            val hashId = redirectHashId ?: return false
            return isLikelyLastReadPageBottomHint(hashId, entryIds)
        }

        fun extractHashEntryPostIdFromTopicUrl(url: String): String? =
                topicUrlEntryFragment.find(url)?.groupValues?.getOrNull(1)

        fun addEntryAnchorFromPostParamsIfEmpty(page: ThemePage, argUrl: String) {
            if (page.anchors.isNotEmpty()) return
            extractPostIdFromTopicUrl(argUrl)?.let { page.addAnchor("entry$it") }
        }

        private val entryNameAnchor = Regex("""(?i)<a[^>]+name=["']entry(\d+)["']""")
        private val dataPostAttr = Regex("""(?i)(?:data-post-id|data-post)=["'](\d+)["']""")

        /**
         * Окно между маркером непрочитанного и `<a name="entry…">` (длинные посты со спойлерами/цитатами).
         */
        private const val GETNEWPOST_UNREAD_TO_ENTRY_WINDOW = """[\s\S]{0,56000}?"""

        /**
         * ID поста (число из `entry{id}`) для скролла при `view=getnewpost`, если в **финальном URL** после редиректа
         * нет `p=` / `pid=` (их обрабатывает парсер темы раньше эвристик по HTML).
         * [hatPostIdToSkip] — первый пост темы-шапка (в приложении сворачивается); не якорим на него, если есть другие кандидаты.
         */
        @JvmStatic
        @JvmOverloads
        fun findUnreadPostEntryIdForGetNewPost(html: String, hatPostIdToSkip: Int? = null): Int? {
            val dp = """(?:data-post-id|data-post)=["'](\d+)["']"""
            val w = GETNEWPOST_UNREAD_TO_ENTRY_WINDOW
            // Сначала узкие «постовые» шаблоны, потом общий class=…unread… (иначе цепляет шапку темы / счётчики).
            val markers = listOf(
                    // data-post / data-post-id + class с unread (любой порядок атрибутов в одном теге)
                    Regex("""(?is)<(?:div|article|section|li|tr|td)[^>]*$dp[^>]*class=["'][^"']*\bunread\b[^"']*["']"""),
                    Regex("""(?is)<(?:div|article|section|li|tr|td)[^>]*class=["'][^"']*\bunread\b[^"']*["'][^>]*$dp"""),
                    // IPS / Invision Community
                    Regex(
                            """(?is)class=["'][^"']*(?:\bipsComment_unread\b|\bipsComment-unread\b|\bipsPost_unread\b|\bipsPost-unread\b|\bipsType_medium_unread\b|\bipsType-medium-unread\b|\bipsItemStatus_unread\b|\bcTopicPostUnread\b|\bcPostUnread\b|\bcUnreadMessages\b)[^"']*["']$w<a[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    Regex(
                            """(?is)class=["'][^"']*\bpost_unread\b[^"']*["']$w<[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    // IPB: обёртки поста + unread
                    Regex(
                            """(?is)class=["'][^"']*(?:\bpost_block\b|\bpost_wrap\b|\bpost_container\b)[^"']*\bunread\b[^"']*["']$w<[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    Regex(
                            """(?is)class=["'][^"']*\bunread\b[^"']*(?:\bpost_block\b|\bpost_wrap\b)[^"']*["']$w<[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    Regex(
                            """(?is)<article[^>]*class=["'][^"']*\bunread\b[^"']*["'][^>]*>$w<a[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    // class unread + data-post внутри блока
                    Regex(
                            """(?is)class=["'][^"']*\bunread\b[^"']*["'][^>]*>$w$dp""",
                            RegexOption.IGNORE_CASE
                    ),
                    Regex(
                            """(?is)class=["'][^"']*\bpost_unread\b[^"']*["']$w$dp""",
                            RegexOption.IGNORE_CASE
                    ),
                    Regex(
                            """(?is)post_unread$w$dp""",
                            RegexOption.IGNORE_CASE
                    ),
                    // Явные data-/boolean-маркеры непрочитанного
                    Regex(
                            """(?is)<[^>]+(?:\bisUnread=["']true["']|\bdata-is-unread=["']1["']|\bdata-unread=["']1["']|\bdata-readState=["']unread["']|\bdata-read-state=["']unread["']|\bdata-readstate=["']unread["']|\bdata-ipsscrollto=["']unread["'])[^>]*>$w<a[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    // «Новое» / new (англ. классы)
                    Regex(
                            """(?is)class=["'][^"']*(?:\bpost_new\b|\bnew_post\b|\bis_new\b|\bhasUnread\b|\bpost_is_new\b)[^"']*["']$w<a[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    // Русские/смешанные подписи в class (реже)
                    Regex(
                            """(?is)class=["'][^"']*(?:непрочит|newmessage|new_message)[^"']*["']$w<a[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
                    // Общий «unread» в class — только после узких шаблонов (риск шапки темы)
                    Regex(
                            """(?is)class=["'][^"']*\bunread\b[^"']*["'][^>]*>$w<a[^>]+name=["']entry(\d+)["']""",
                            RegexOption.IGNORE_CASE
                    ),
            )
            // Берём кандидата с минимальным смещением в HTML — порядок регэкспов не должен перебивать порядок в документе.
            var bestPos: Int? = null
            var bestId: Int? = null
            for (re in markers) {
                for (m in re.findAll(html)) {
                    val id = m.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
                    if (hatPostIdToSkip != null && id == hatPostIdToSkip) continue
                    val pos = m.range.first
                    if (bestPos == null || pos < bestPos) {
                        bestPos = pos
                        bestId = id
                    }
                }
            }
            bestId?.let { return it }
            return scanUnreadBeforeEntryAnchors(html, hatPostIdToSkip)
        }

        /**
         * Все entry-id постов с unread-разметкой в HTML (для диагностики якоря getnewpost).
         */
        @JvmStatic
        @JvmOverloads
        fun collectUnreadPostEntryIds(html: String, hatPostIdToSkip: Int? = null): List<Int> {
            val entryPoints = mutableListOf<Pair<Int, Int>>()
            Regex("""(?is)<a[^>]*\bname\s*=\s*["']entry(\d+)["']""").findAll(html).forEach { m ->
                m.groupValues.getOrNull(1)?.toIntOrNull()?.let { entryPoints.add(m.range.first to it) }
            }
            Regex("""(?is)<(?:div|article|section|li|tr|td)[^>]*\bid\s*=\s*["']entry(\d+)["']""").findAll(html).forEach { m ->
                m.groupValues.getOrNull(1)?.toIntOrNull()?.let { entryPoints.add(m.range.first to it) }
            }
            entryPoints.sortBy { it.first }
            val broadClassUnread = Regex(
                    """(?is)(?:class|id)\s*=\s*["'][^"']*(?:\bunread\b|\bnotread\b|\bpost_notread\b|\bhasUnread\b|\bitem_unread\b|\bstatus_unread\b)[^"']*["']"""
            )
            val postScopedUnread = Regex(
                    """(?is)(?:class|id)\s*=\s*["'][^"']*(?:\bpost_unread\b|\bipsComment[_-]unread\b|\bipsPost[_-]unread\b|\bmessage_unread\b|\bcomment_unread\b|\bpost_new\b|\bnew_post\b|\bpost_is_new\b)[^"']*["']"""
            )
            val unreadWithPostWrapper = Regex(
                    """(?is)(?:class|id)\s*=\s*["'][^"']*(?:\b(?:post_block|post_wrap|post_container|cat_name)\b[^"']*\bunread\b|\bunread\b[^"']*(?:\bpost_block\b|\bpost_wrap\b|\bpost_container\b))[^"']*["']"""
            )
            val dataHint = Regex(
                    """(?is)(?:\bdata-unread\s*=|\bisUnread\s*=\s*["']true["']|\bdata-is-unread\s*=|\bdata-readState\s*=\s*["']unread["']|\bdata-read-state\s*=\s*["']unread["']|\bdata-readstate\s*=\s*["']unread["']|\bdata-ipsscrollto\s*=\s*["']unread["'])"""
            )
            val looseAttrUnread = Regex("""(?is)=\s*["'][^"']*\bunread\b[^"']*["']""")
            val result = linkedSetOf<Int>()
            for ((index, pair) in entryPoints.withIndex()) {
                val (pos, id) = pair
                if (hatPostIdToSkip != null && id == hatPostIdToSkip) continue
                val lookback = if (index == 0) 3200 else 12000
                val from = (pos - lookback).coerceAtLeast(0)
                val window = html.substring(from, pos)
                val dataOk = dataHint.containsMatchIn(window)
                val postScoped = postScopedUnread.containsMatchIn(window) || unreadWithPostWrapper.containsMatchIn(window)
                val broad = broadClassUnread.containsMatchIn(window)
                val loose = looseAttrUnread.containsMatchIn(window)
                val accept = if (index == 0) {
                    dataOk || postScoped
                } else {
                    dataOk || postScoped || broad || loose
                }
                if (accept) result.add(id)
            }
            return result.toList()
        }

        /**
         * Для скинов без совпадения с жёсткими шаблонами: перед каждым `<a name="entryN">` смотрим предыдущий фрагмент HTML.
         * Маркер непрочитанного часто в `class=` / `data-*` родительского блока поста.
         */
        private fun scanUnreadBeforeEntryAnchors(html: String, skipPostId: Int? = null): Int? {
            val entryPoints = mutableListOf<Pair<Int, Int>>()
            Regex("""(?is)<a[^>]*\bname\s*=\s*["']entry(\d+)["']""").findAll(html).forEach { m ->
                m.groupValues.getOrNull(1)?.toIntOrNull()?.let { entryPoints.add(m.range.first to it) }
            }
            // Часть скинов ставит якорь только как id="entryN" на обёртке поста.
            Regex("""(?is)<(?:div|article|section|li|tr|td)[^>]*\bid\s*=\s*["']entry(\d+)["']""").findAll(html).forEach { m ->
                m.groupValues.getOrNull(1)?.toIntOrNull()?.let { entryPoints.add(m.range.first to it) }
            }
            entryPoints.sortBy { it.first }
            // Для первого якоря: не цепляем «unread» из шапки темы (topic_title, навигация, счётчики).
            val broadClassUnread = Regex(
                    """(?is)(?:class|id)\s*=\s*["'][^"']*(?:\bunread\b|\bnotread\b|\bpost_notread\b|\bhasUnread\b|\bitem_unread\b|\bstatus_unread\b)[^"']*["']"""
            )
            val postScopedUnread = Regex(
                    """(?is)(?:class|id)\s*=\s*["'][^"']*(?:\bpost_unread\b|\bipsComment[_-]unread\b|\bipsPost[_-]unread\b|\bmessage_unread\b|\bcomment_unread\b|\bpost_new\b|\bnew_post\b|\bpost_is_new\b)[^"']*["']"""
            )
            val unreadWithPostWrapper = Regex(
                    """(?is)(?:class|id)\s*=\s*["'][^"']*(?:\b(?:post_block|post_wrap|post_container|cat_name)\b[^"']*\bunread\b|\bunread\b[^"']*(?:\bpost_block\b|\bpost_wrap\b|\bpost_container\b))[^"']*["']"""
            )
            val dataHint = Regex(
                    """(?is)(?:\bdata-unread\s*=|\bisUnread\s*=\s*["']true["']|\bdata-is-unread\s*=|\bdata-readState\s*=\s*["']unread["']|\bdata-read-state\s*=\s*["']unread["']|\bdata-readstate\s*=\s*["']unread["']|\bdata-ipsscrollto\s*=\s*["']unread["'])"""
            )
            val looseAttrUnread = Regex("""(?is)=\s*["'][^"']*\bunread\b[^"']*["']""")
            for ((index, pair) in entryPoints.withIndex()) {
                val (pos, id) = pair
                if (skipPostId != null && id == skipPostId) continue
                val lookback = if (index == 0) 3200 else 12000
                val from = (pos - lookback).coerceAtLeast(0)
                val window = html.substring(from, pos)
                val dataOk = dataHint.containsMatchIn(window)
                val postScoped = postScopedUnread.containsMatchIn(window) || unreadWithPostWrapper.containsMatchIn(window)
                val broad = broadClassUnread.containsMatchIn(window)
                val loose = looseAttrUnread.containsMatchIn(window)
                // Первый якорь на странице: «широкий» unread из шапки темы не используем (скролл открывает шапку).
                val accept = if (index == 0) {
                    dataOk || postScoped
                } else {
                    dataOk || postScoped || broad || loose
                }
                if (accept) return id
            }
            return null
        }

        /**
         * После публикации/редактирования поста: прокрутка к новому сообщению.
         * [parsedBody] — сырой HTML ответа (если список постов в модели пуст или URL без p=).
         */
        @JvmOverloads
        fun ensureScrollAnchorForPostedPage(page: ThemePage, parsedBody: String?, traceId: String? = null) {
            val anchorBefore = page.anchor
            if (page.anchors.isNotEmpty()) return
            extractPostIdFromTopicUrl(page.url.orEmpty())?.let { pid ->
                page.addAnchor("entry$pid")
                debugAnchorLog("url p/pid", anchorBefore, page, parsedBody, traceId)
                return
            }
            page.posts.lastOrNull()?.id?.let { lastId ->
                page.addAnchor("entry$lastId")
                debugAnchorLog("last post id", anchorBefore, page, parsedBody, traceId)
                return
            }
            parsedBody?.let { html ->
                entryNameAnchor.findAll(html).lastOrNull()?.groupValues?.getOrNull(1)?.let { id ->
                    page.addAnchor("entry$id")
                    debugAnchorLog("html name=entry", anchorBefore, page, parsedBody, traceId)
                    return
                }
                dataPostAttr.findAll(html).lastOrNull()?.groupValues?.getOrNull(1)?.let { id ->
                    page.addAnchor("entry$id")
                    debugAnchorLog("html data-post", anchorBefore, page, parsedBody, traceId)
                }
            }
            if (page.anchors.isEmpty()) {
                debugAnchorLog("none", anchorBefore, page, parsedBody, traceId)
            }
        }

        private fun debugAnchorLog(
                source: String,
                before: String?,
                page: ThemePage,
                parsedBody: String?,
                traceId: String?
        ) {
            if (!BuildConfig.DEBUG) return
            Timber.d(
                    "trace=${traceId.orEmpty()} ensureScrollAnchor[$source] before=$before after=${page.anchor} topicId=${page.id} url=${page.url} htmlLen=${parsedBody?.length ?: 0}"
            )
        }
    }
}
