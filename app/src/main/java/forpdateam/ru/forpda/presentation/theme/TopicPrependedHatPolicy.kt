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
        knownHatId?.takeIf { it > 0 }?.let { return it }
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
        if (first.id > 0 && first.number < expectedMin) {
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

        while (page.posts.isNotEmpty()) {
            val first = page.posts.first()
            val isPrependedByNumber = first.number < expectedMin
            val looksLikeKnownHat = hatId != null && first.id == hatId
            val looksLikeOnlyPrependedHat = first.number == 1 &&
                    page.posts.size > 1 &&
                    page.posts[1].number >= expectedMin
            val legacyPrepended = first.number == 1 && page.pagination.current != 1
            val anchorPrepended = first.id > 0 &&
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
        if (looksLikeProPdaTopicHat(post)) return true
        if (looksLikeTopicTitleHat(post, page)) return true
        if (post.number == 1) return true
        if (post.number == 0) return true
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

    private fun anchorEntryIds(page: ThemePage): Set<Int> {
        val ids = linkedSetOf<Int>()
        page.anchors.forEach { anchor ->
            anchor.removePrefix("entry").toIntOrNull()?.let { ids.add(it) }
        }
        page.anchorPostId?.removePrefix("entry")?.toIntOrNull()?.let { ids.add(it) }
        return ids
    }

    private fun hasPositiveSt(url: String): Boolean {
        return try {
            android.net.Uri.parse(url).getQueryParameter("st")?.toIntOrNull()?.let { it > 0 } == true
        } catch (_: Throwable) {
            false
        }
    }
}
