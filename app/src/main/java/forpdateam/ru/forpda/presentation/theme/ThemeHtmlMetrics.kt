package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * HTML post counters for theme render guards.
 * [post_container] appears on topic hat overlays too — list metrics exclude hat chrome.
 */
internal object ThemeHtmlMetrics {

    private const val POSTS_LIST_START_MARKER = "<!-- theme_posts_list_start -->"
    private const val POSTS_LIST_END_MARKER = "<!-- theme_posts_list_end -->"

    fun postsListInnerHtml(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        val markerStart = html.indexOf(POSTS_LIST_START_MARKER).takeIf { it >= 0 }
        if (markerStart != null) {
            val start = markerStart + POSTS_LIST_START_MARKER.length
            val end = html.indexOf(POSTS_LIST_END_MARKER, start).takeIf { it >= 0 } ?: return null
            return html.substring(start, end)
        }
        val open = Regex(
                "<div\\b(?=[^>]*\\bclass\\s*=\\s*['\"][^'\"]*\\bposts_list\\b)[^>]*>",
                RegexOption.IGNORE_CASE
        ).find(html) ?: return null
        val start = open.range.last + 1
        val bottomPagination = html.indexOf("<!-- \$BeginBlock bottom_pagination -->", start).takeIf { it >= 0 }
        val bottomSpacer = html.indexOf("<div id=\"bottom_chrome_spacer\"", start).takeIf { it >= 0 }
        val end = bottomPagination ?: bottomSpacer ?: return null
        return html.substring(start, end)
    }

    fun countListPostContainers(html: String?): Int {
        val inner = postsListInnerHtml(html) ?: return 0
        val openingTag = Regex(
                """<div\b[^>]*\bclass\s*=\s*["'][^"']*\bpost_container\b[^"']*["'][^>]*>""",
                RegexOption.IGNORE_CASE
        )
        return openingTag.findAll(inner).count { match ->
            val tag = match.value
            !tag.contains("topic_hat_fixed") && !tag.contains("topic_hat_entry")
        }
    }

    fun isListPostsUnderRendered(page: ThemePage, html: String?, expectedListPosts: Int = page.posts.size): Boolean {
        if (page.posts.isEmpty() || expectedListPosts <= 0) return false
        val actual = countListPostContainers(html)
        return actual < expectedListPosts
    }

    fun shouldRetryRenderWithoutHat(page: ThemePage, expectedListPosts: Int): Boolean {
        if (page.posts.isEmpty() || expectedListPosts <= 0) return false
        val listPostsInHtml = countListPostContainers(page.html)
        if (listPostsInHtml == 0) return page.topicHatPost != null
        if (listPostsInHtml >= expectedListPosts) return false
        return page.topicHatPost != null
    }
}
