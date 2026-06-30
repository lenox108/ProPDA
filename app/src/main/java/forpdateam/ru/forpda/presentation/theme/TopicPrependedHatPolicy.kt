package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost

/**
 * Detects and strips the topic hat post when the server prepends it to deep pages.
 */
internal object TopicPrependedHatPolicy {

    private val PROPDA_TITLE_REGEX = Regex("""(?i)\bProPDA\b""")
    private val PROPDA_VERSION_LINE_REGEX = Regex("""(?im)\bВерсия\s*:\s*\d+\.\d+\.\d+""")

    fun expectedFirstPostNumber(page: ThemePage, requestedPage: Int = page.pagination.current): Int? {
        if (requestedPage <= 1 || page.pagination.perPage <= 0) return null
        return (requestedPage - 1) * page.pagination.perPage + 1
    }

    /**
     * Resolves the topic hat post id for stripping on non-first pages.
     * Uses cached id, heuristics, scroll anchors, ProPDA signature, and number==1 anywhere in list.
     */
    fun resolvePrependedHatId(
            page: ThemePage,
            requestedPage: Int = page.pagination.current,
            knownHatId: Int? = null,
    ): Int? {
        // The open's NAVIGATION TARGET — the server redirect post in `page.url` (…#entry<id>) — is
        // NEVER the prepended hat. Resolving it as the hat is the recurring bug: once an earlier strip
        // pass removes the real hat, the target becomes the first post and `resolvePrependedHatIdRaw`'s
        // `first.id in anchors` branch returns it; removeAll then strips it via `post.id == hatId`,
        // BEFORE the anchor guard (device log 26_06-15-03, topic 1111449: the second hatMetadataPreload
        // strip ate the kept anchor 143983265). We key on the url #entry specifically — NOT the broader
        // anchor set — because the hat itself can legitimately be a scroll anchor (page.anchors may list
        // the hat, e.g. when the user navigated to the topic's first post), and that hat must still be
        // strippable. The url redirect target is always the content post the open lands on.
        val urlAnchorId = extractEntryHashPostId(page.url)
        val raw = resolvePrependedHatIdRaw(page, requestedPage, knownHatId)
        return raw?.takeIf { urlAnchorId == null || it != urlAnchorId }
    }

    private fun resolvePrependedHatIdRaw(
            page: ThemePage,
            requestedPage: Int,
            knownHatId: Int?,
    ): Int? {
        // The SERVER-marked prepended hat (post inside `data-spoil-poll-pinned-content`) is the most
        // authoritative signal for THIS page — prefer it over everything below. See [hasAuthoritativeHat].
        page.prependedHatPostId.takeIf { it > 0 }?.let { return it }
        knownHatId?.takeIf { it > 0 }?.let { return it }
        // When the authoritative hat is known, it IS the prepended hat — return it instead of letting
        // the number/anchor heuristics below mis-pick a real content post (see [hasAuthoritativeHat]).
        page.topicHatPost?.id?.takeIf { it > 0 }?.let { return it }
        if (requestedPage <= 1 && page.pagination.current <= 1) return null
        detectPrependedHat(page)?.id?.takeIf { it > 0 }?.let { return it }
        findHatCandidateInPosts(page, requestedPage)?.id?.takeIf { it > 0 }?.let { return it }
        val expectedMin = expectedFirstPostNumber(page, requestedPage) ?: return null
        val first = page.posts.firstOrNull()?.takeIf { it.id > 0 } ?: return null
        if (page.posts.size <= 1) return null
        val second = page.posts[1]
        if (second.number < expectedMin) return null
        val anchorIds = anchorEntryIds(page)
        if (first.id in anchorIds) return first.id
        val expectedMax = expectedMin + page.pagination.perPage - 1
        if (first.number < expectedMin || first.number > expectedMax || first.number == 1) {
            return first.id
        }
        if (first.number == expectedMin &&
                second.number >= expectedMin + 1 &&
                looksLikeTopicTitleHat(first, page)
        ) {
            return first.id
        }
        return null
    }

    fun detectPrependedHat(page: ThemePage): ThemePost? {
        if (page.posts.size <= 1) return null
        val first = page.posts.firstOrNull() ?: return null
        if (page.pagination.current == 1 && !hasPositiveSt(page.url.orEmpty())) {
            return first.takeIf { it.id > 0 }
        }
        val expectedMin = expectedFirstPostNumber(page) ?: return null
        // The `number < expectedMin` heuristic only means "prepended hat" when post numbers are
        // trustworthy. On deep/last pages the parser sometimes leaves EVERY content post at number==0
        // (device log 26_06-12-14, topic 928862): then this branch would flag the FIRST content post
        // — including the first-unread anchor once an earlier strip pass made it first — as the hat,
        // and removeAll strips it by `post.id == hatId` BEFORE the anchor guard. Require at least one
        // post to reach the expected window before trusting the number heuristic.
        if (first.id > 0 && first.number < expectedMin && pageNumbersReliable(page, page.pagination.current)) {
            return first
        }
        val classicPrepended = first.takeIf {
            it.id > 0 &&
                    it.number == 1 &&
                    page.posts.drop(1).firstOrNull()?.number?.let { nextNumber ->
                        nextNumber >= expectedMin
                    } == true
        }
        if (classicPrepended != null) return classicPrepended
        val inWindowTitleHat = first.takeIf {
            it.id > 0 &&
                    it.number == expectedMin &&
                    page.posts.getOrNull(1)?.number?.let { nextNumber -> nextNumber >= expectedMin + 1 } == true &&
                    looksLikeTopicTitleHat(it, page)
        }
        if (inWindowTitleHat != null) return inWindowTitleHat
        if (page.pagination.current > 1 || hasPositiveSt(page.url.orEmpty())) {
            return findHatCandidateInPosts(page, page.pagination.current)
        }
        return null
    }

    fun stripFromNonFirstPage(
            page: ThemePage,
            requestedPage: Int,
            knownHatId: Int?,
    ): Boolean {
        if (requestedPage <= 1 && page.pagination.current <= 1) return true
        if (page.posts.isEmpty()) return true
        val expectedMin = expectedFirstPostNumber(page, requestedPage) ?: return true
        val hatId = resolvePrependedHatId(page, requestedPage, knownHatId)

        page.posts.removeAll { post ->
            shouldStripHatPost(page, post, requestedPage, hatId)
        }

        val anchorIds = anchorEntryIds(page)
        val numbersReliable = pageNumbersReliable(page, requestedPage)
        // Once the real hat is known the bare number/anchor heuristics may only confirm the hat by id
        // (looksLikeKnownHat) or a positive signature; they must not trim a non-hat leading post whose
        // number the parser left low/zero (see [hasAuthoritativeHat]).
        val allowNumberHeuristics = !hasAuthoritativeHat(page)
        while (page.posts.isNotEmpty()) {
            val first = page.posts.first()
            // Stop trimming once the leading post is the open's anchor target: it is real content the
            // user is being navigated to, never the prepended hat. Without this the number-based
            // `isPrependedByNumber` check below strips the first-unread post when its sequential
            // `number` is 0/below expectedMin on a deep/last page (device log 26_06-11-11, 1122662).
            if (first.id > 0 && first.id in anchorIds) break
            // Only trust the number window when the page actually has trustworthy numbers; otherwise
            // (every post number==0, device log 26_06-12-14 topic 928862) this would eat real content
            // post by post until the unread anchor is gone.
            val isPrependedByNumber = allowNumberHeuristics && numbersReliable && first.number < expectedMin
            val looksLikeKnownHat = hatId != null && first.id == hatId
            val looksLikeOnlyPrependedHat = allowNumberHeuristics && first.number == 1 &&
                    page.posts.size > 1 &&
                    page.posts[1].number >= expectedMin
            val legacyPrepended = allowNumberHeuristics && first.number == 1 && page.pagination.current != 1
            val anchorPrepended = allowNumberHeuristics && first.id > 0 &&
                    first.id in anchorEntryIds(page) &&
                    page.posts.size > 1 &&
                    page.posts[1].number >= expectedMin
            val inWindowTitleHat = first.number == expectedMin &&
                    page.posts.size > 1 &&
                    page.posts[1].number >= expectedMin + 1 &&
                    looksLikeTopicTitleHat(first, page)
            if (looksLikeKnownHat || isPrependedByNumber || looksLikeOnlyPrependedHat ||
                    legacyPrepended || anchorPrepended || inWindowTitleHat ||
                    looksLikeProPdaTopicHat(first)
            ) {
                page.posts.removeAt(0)
            } else {
                break
            }
        }
        return page.posts.isNotEmpty()
    }

    fun looksLikeTopicTitleHat(post: ThemePost, page: ThemePage): Boolean {
        if (post.id <= 0) return false
        val body = post.body.orEmpty()
        page.title?.trim()?.takeIf { it.length >= 8 }?.let { title ->
            if (body.contains(title, ignoreCase = true)) return true
        }
        page.desc?.trim()?.takeIf { it.length >= 8 }?.let { desc ->
            if (body.contains(desc, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Posts that must not appear in the paginated list on pages after the first.
     * Used by [stripFromNonFirstPage] and HTML render filtering.
     */
    fun filterPostsForPageList(
            page: ThemePage,
            requestedPage: Int = page.pagination.current,
            knownHatId: Int? = null,
    ): List<ThemePost> {
        val topicHeader = page.topicHatPost?.takeIf { it.id > 0 }
        val hatId = knownHatId
                ?: topicHeader?.id?.takeIf { it > 0 }
                ?: resolvePrependedHatId(page, requestedPage, knownHatId)
        if (requestedPage <= 1 && page.pagination.current <= 1) {
            val filtered = page.posts.filterNot { post ->
                shouldExcludeFromPageList(page, post, requestedPage, topicHeader, hatId)
            }
            return filtered.takeIf { it.isNotEmpty() } ?: page.posts.toList()
        }
        val filtered = page.posts.filterNot { post ->
            shouldExcludeFromPageList(page, post, requestedPage, topicHeader, hatId)
        }
        return filtered.takeIf { it.isNotEmpty() } ?: page.posts.toList()
    }

    fun preparePagePostsForNonFirstPageList(
            page: ThemePage,
            requestedPage: Int = page.pagination.current,
            knownHatId: Int? = null,
    ) {
        if (requestedPage <= 1 && page.pagination.current <= 1) return
        stripFromNonFirstPage(
                page = page,
                requestedPage = requestedPage,
                knownHatId = knownHatId ?: resolvePrependedHatId(page, requestedPage, knownHatId),
        )
    }

    fun looksLikeProPdaTopicHat(post: ThemePost): Boolean {
        if (post.id <= 0) return false
        val body = post.body.orEmpty()
        if (!PROPDA_TITLE_REGEX.containsMatchIn(body) ||
                !PROPDA_VERSION_LINE_REGEX.containsMatchIn(body)
        ) {
            return false
        }
        if (post.number == 1) return true
        return post.nick.equals("Lenox30", ignoreCase = true)
    }

    private fun findHatCandidateInPosts(page: ThemePage, requestedPage: Int): ThemePost? {
        if (requestedPage <= 1 && page.pagination.current <= 1) return null
        page.posts.firstOrNull { looksLikeProPdaTopicHat(it) }?.let { return it }
        page.posts.firstOrNull { looksLikeTopicTitleHat(it, page) }?.let { return it }
        page.posts.firstOrNull { looksLikeAnchorExcludedPrependedHat(page, it, requestedPage) }?.let { return it }
        if (page.posts.size <= 1) return null
        val expectedMin = expectedFirstPostNumber(page, requestedPage) ?: return null
        return page.posts.firstOrNull { post ->
            post.id > 0 && post.number == 1 && page.posts.any { other ->
                other !== post && other.number >= expectedMin
            }
        }
    }

    private fun shouldStripHatPost(
            page: ThemePage,
            post: ThemePost,
            requestedPage: Int,
            hatId: Int?,
    ): Boolean = shouldExcludeFromPageList(
            page = page,
            post = post,
            requestedPage = requestedPage,
            topicHeader = null,
            hatId = hatId,
    )

    private fun shouldExcludeFromPageList(
            page: ThemePage,
            post: ThemePost,
            requestedPage: Int,
            topicHeader: ThemePost?,
            hatId: Int?,
    ): Boolean {
        if (requestedPage <= 1 && page.pagination.current <= 1) {
            if (hatId != null && post.id == hatId) return true
            if (topicHeader != null && post.id == topicHeader.id) return true
            if (post.number == 1 && looksLikeProPdaTopicHat(post)) return true
            if (post.number == 1 && looksLikeTopicTitleHat(post, page)) return true
            return false
        }
        if (topicHeader != null && post.id == topicHeader.id) return true
        if (hatId != null && post.id == hatId) return true
        // Never strip the post the open is navigating to (unread / scroll / explicit anchor). The
        // topic hat is never the anchor target, so an anchor post being flagged here is always a
        // false positive. Guards the number-based strips below, which fire on a deep/last page where
        // the anchor's sequential `number` is 0 or below `expectedMin` (device log 26_06-11-11, topic
        // 1122662: first-unread 143996702 — the first content post after the prepended hat — was
        // removed from page.posts as a "hat", so the highlight resolver saw it off-page, fell back to
        // last_post_on_page_fallback (144013662) and the reveal scrolled visibly to the wrong post).
        if (post.id > 0 && post.id in anchorEntryIds(page)) return false
        if (looksLikeProPdaTopicHat(post)) return true
        if (looksLikeTopicTitleHat(post, page)) return true
        // The real hat is known (and handled by the id match above); a non-matching post that merely
        // carries a low/zero number is mis-numbered content, not the prepended hat — keep it.
        if (hasAuthoritativeHat(page)) return false
        if (post.number == 1) return true
        // `number == 0` only signals a hat when the page's numbers are trustworthy. When the parser
        // left every post at number==0 (deep/last page, device log 26_06-12-14 topic 928862) this rule
        // would strip real content — including the unread anchor — post by post across re-strip passes.
        if (post.number == 0 && pageNumbersReliable(page, requestedPage)) return true
        return looksLikeAnchorExcludedPrependedHat(page, post, requestedPage)
    }

    /**
     * On sparse deep pages the server prepends the hat beside the anchor target; the hat is not
     * in [anchorEntryIds] while the unread/scroll target post is.
     */
    private fun looksLikeAnchorExcludedPrependedHat(
            page: ThemePage,
            post: ThemePost,
            requestedPage: Int,
    ): Boolean {
        if (post.id <= 0 || page.posts.size > 3) return false
        val anchorIds = anchorEntryIds(page)
        if (anchorIds.isEmpty() || post.id in anchorIds) return false
        if (!page.posts.any { it.id in anchorIds }) return false
        val expectedMin = expectedFirstPostNumber(page, requestedPage) ?: return false
        val expectedMax = expectedMin + page.pagination.perPage - 1
        if (post.number < expectedMin) return true
        if (post.number == expectedMin && page.posts.any { other ->
                    other !== post && other.id in anchorIds
                }
        ) {
            return true
        }
        if (post.number in expectedMin..expectedMax &&
                page.posts.count { it.number in expectedMin..expectedMax } <= 2
        ) {
            return true
        }
        return false
    }

    /**
     * True when the authoritative topic hat (the real opening post) is known for this page via
     * [ThemePage.topicHatPost]. When it is, the prepended hat is exactly that post; a DIFFERENT leading
     * post carrying a low/zero number is content the parser failed to number, NOT the hat. The bare
     * number / anchor heuristics cannot tell the two apart, so they must be suppressed for non-matching
     * posts once the real hat is known — the hat itself is still stripped by id. Device report 30_06:
     * favorites getlastpost, topic 1115315 page 51 — the real hat (140711020) was cached, yet the first
     * content post of the last page got number-stripped at render and vanished until a manual refresh.
     */
    private fun hasAuthoritativeHat(page: ThemePage): Boolean =
            page.prependedHatPostId > 0 || (page.topicHatPost?.id ?: 0) > 0

    private fun anchorEntryIds(page: ThemePage): Set<Int> {
        val ids = linkedSetOf<Int>()
        page.anchors.forEach { anchor ->
            anchor.removePrefix("entry").toIntOrNull()?.let { ids.add(it) }
        }
        page.anchorPostId?.removePrefix("entry")?.toIntOrNull()?.let { ids.add(it) }
        // Durable fallback: the open's #entry target survives in page.url even after page.anchors /
        // anchorPostId have been cleared by a SECOND strip pass (hat-overlay remap / ThemeTemplate
        // fragment mapping). Device log 26_06-12-04, topic 461675: the first strip keeps the anchor
        // (anchors=[entry143885374]), but a later strip runs with anchors=[] and re-strips the
        // first-unread post 143885374 by the number==0 rule -> highlight off-page -> visible scroll to
        // the wrong post. The redirect hash in page.url (…#entry143885374) is stable across both passes.
        extractEntryHashPostId(page.url)?.let { ids.add(it) }
        return ids
    }

    /**
     * True when this page's posts carry trustworthy sequential numbers — at least one post reaches
     * the expected number window for [requestedPage]. On deep/last pages the parser can leave EVERY
     * content post at number==0; then the number-based hat heuristics are meaningless and must be
     * suppressed so they don't strip real content (device log 26_06-12-14, topic 928862). When numbers
     * are unreliable only positively-identified hats (knownHatId / ProPDA / title) are removed.
     */
    private fun pageNumbersReliable(page: ThemePage, requestedPage: Int): Boolean {
        val expectedMin = expectedFirstPostNumber(page, requestedPage) ?: return true
        return page.posts.any { it.number >= expectedMin }
    }

    /** Post id from the trailing `#entry<digits>` of a topic url (handles a doubled `#entry…#entry…`). */
    private fun extractEntryHashPostId(url: String?): Int? {
        val u = url ?: return null
        val idx = u.lastIndexOf("#entry")
        if (idx < 0) return null
        return u.substring(idx + "#entry".length).takeWhile(Char::isDigit).toIntOrNull()
    }

    private fun hasPositiveSt(url: String): Boolean {
        return try {
            android.net.Uri.parse(url).getQueryParameter("st")?.toIntOrNull()?.let { it > 0 } == true
        } catch (_: Throwable) {
            false
        }
    }
}
