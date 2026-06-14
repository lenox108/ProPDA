package forpdateam.ru.forpda.model.data.remote.api.news

/**
 * Minimal typed blocks for the article render pipeline.
 * Poll markup is still emitted as trusted HTML in the WebView template, but treating it as
 * [Poll] lets later stages assert the block survived sanitize/map (see [findPollBlock]).
 */
sealed class ArticleBlock {
    data class Text(val html: String) : ArticleBlock()
    data class Poll(val html: String, val pollId: String? = null) : ArticleBlock()

    companion object {
        private val pollOpenRegex = Regex(
                """<(?:div|section)[^>]*\b(?:news-poll-normalized|data-normalized-poll|data-poll-fallback)\b[^>]*>""",
                RegexOption.IGNORE_CASE
        )

        /**
         * Splits article body HTML into typed blocks. Poll markup is detected via [findPollBlock];
         * everything else is treated as [Text].
         */
        fun splitBody(html: String?): List<ArticleBlock> {
            if (html.isNullOrBlank()) return emptyList()
            val poll = findPollBlock(html) ?: return listOf(Text(html.trim()))
            val start = html.indexOf(poll.html)
            if (start < 0) return listOf(Text(html.trim()))
            val end = start + poll.html.length
            val blocks = mutableListOf<ArticleBlock>()
            html.substring(0, start).trim().takeIf { it.isNotBlank() }?.let { blocks += Text(it) }
            blocks += poll
            html.substring(end).trim().takeIf { it.isNotBlank() }?.let { blocks += Text(it) }
            return blocks
        }

        /**
         * Render-pipeline invariant: a typed poll present before sanitize/map must still be typed
         * after [ArticleHtmlSecuritySanitizer] (interactive vote form or browser fallback preserved).
         */
        fun pollSurvivedSanitize(before: String?, after: String?): Boolean {
            val beforePoll = findPollBlock(before) ?: return true
            val afterPoll = findPollBlock(after) ?: return false
            if (beforePoll.pollId != null &&
                    afterPoll.pollId != null &&
                    beforePoll.pollId != afterPoll.pollId) {
                return false
            }
            val beforeInteractive = beforePoll.html.contains("<form", ignoreCase = true) &&
                    beforePoll.html.contains("answer[]", ignoreCase = true)
            if (beforeInteractive) {
                return afterPoll.html.contains("<form", ignoreCase = true) &&
                        afterPoll.html.contains("name=\"answer[]\"", ignoreCase = true)
            }
            return afterPoll.html.contains("data-poll-fallback", ignoreCase = true) ||
                    afterPoll.html.contains("news-poll-browser-button", ignoreCase = true)
        }

        /** Extracts the first normalized poll block from mapped article body HTML. */
        fun findPollBlock(html: String?): Poll? {
            if (html.isNullOrBlank()) return null
            val match = pollOpenRegex.find(html) ?: return null
            val start = match.range.first
            val pollId = Regex("""poll_id=(\d+)""", RegexOption.IGNORE_CASE)
                    .find(html, start)
                    ?.groupValues
                    ?.getOrNull(1)
            val end = findBalancedDivEnd(html, start) ?: html.length
            val block = html.substring(start, end).trim()
            if (block.isEmpty()) return null
            return Poll(block, pollId)
        }

        private fun findBalancedDivEnd(html: String, openIndex: Int): Int? {
            var depth = 0
            var index = openIndex
            val openTag = Regex("""<div\b""", RegexOption.IGNORE_CASE)
            val closeTag = Regex("""</div>""", RegexOption.IGNORE_CASE)
            while (index < html.length) {
                val nextOpen = openTag.find(html, index)?.range?.first ?: html.length
                val nextClose = closeTag.find(html, index)?.range?.first ?: html.length
                if (nextOpen < nextClose) {
                    depth++
                    index = nextOpen + 4
                } else if (nextClose < html.length) {
                    depth--
                    index = nextClose + 6
                    if (depth <= 0) return index
                } else {
                    break
                }
            }
            return null
        }
    }
}
