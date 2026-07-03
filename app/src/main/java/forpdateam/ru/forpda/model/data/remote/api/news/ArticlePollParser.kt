package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import okhttp3.Cookie
import java.net.URLDecoder

/**
 * Sub-parser extracted from [ArticleParser] (§1.1 decomposition), slice 1: news-poll **detection
 * predicates** and **vote-cookie state**. These are the lowest layer of the poll subsystem —
 * pure `String -> Boolean` classification plus the per-session voted-answers mirror — with no
 * dependency on the DOM parser, the poll renderer, or the block extractors that still live in
 * [ArticleParser]. Subsequent slices (block extraction, DOM traversal, rendering) will move here
 * and lean on these predicates.
 *
 * Following the established pattern (see [ArticleBodyParser]): the regexes and token list stay
 * owned by [ArticleParser]'s companion (some are still used by not-yet-moved poll code) and are
 * injected here, so both sides share a single source of truth. [ArticleParser] keeps thin
 * delegating wrappers for every method a caller outside this class still references.
 */
internal class ArticlePollParser(
        private val rawPollTemplateTokens: List<String>,
        private val formTagRegex: Regex,
        private val pollActionRegex: Regex,
        private val pollIdInputRegex: Regex,
        private val answerInputRegex: Regex,
        private val dataSitePollAttrRegex: Regex,
        private val pollResultRegex: Regex,
        private val pollFrameMarkerRegex: Regex,
        private val pollFrameTitleRegex: Regex,
        private val pollFrameButtonRegex: Regex,
        private val pollVoteCookieNameRegex: Regex,
        private val pollTitleRegex: Regex,
        private val rawPollTemplateScriptRegex: Regex,
        private val rawPollContainerRegex: Regex,
        private val newsPollFallbackBlockRegex: Regex,
        private val articleFromHtml: (String) -> String?,
        private val articleUrlFromResponse: (String) -> String?,
        private val articleHtmlEncode: (String) -> String,
        private val articleIdFromResponse: (String) -> Int?,
) : BaseParser() {

    /** Mirrors 4PDA site JS: voted state for news polls is stored in poll-{id} cookies. */
    @Volatile
    private var voteCookies: Map<String, Set<String>> = emptyMap()

    fun syncVoteCookies(clientCookies: Map<String, Cookie>) {
        voteCookies = extractVoteCookies(clientCookies)
    }

    fun voteCookieAnswers(pollId: String): Set<String> = voteCookies[pollId].orEmpty()

    private fun extractVoteCookies(clientCookies: Map<String, Cookie>): Map<String, Set<String>> {
        val result = LinkedHashMap<String, MutableSet<String>>()
        clientCookies.values.forEach { cookie ->
            val pollId = pollVoteCookieNameRegex.find(cookie.name)?.groupValues?.getOrNull(1)
                    ?: return@forEach
            val answers = runCatching { URLDecoder.decode(cookie.value, "UTF-8") }
                    .getOrElse { cookie.value }
                    .split(',')
                    .mapNotNull { part -> part.trim().toLongOrNull()?.takeIf { it > 0 }?.toString() }
            if (answers.isEmpty()) return@forEach
            result.getOrPut(pollId) { linkedSetOf() }.addAll(answers)
        }
        return result
    }

    fun isNewsPollBlock(block: String): Boolean {
        if (containsRawPollTemplate(block) && !isNormalizedNewsPollBlock(block) && !isFallbackNewsPollBlock(block)) {
            return false
        }
        val hasPollMarker = hasNewsPollMarkersInternal(block)
        return hasPollMarker && hasRealNewsPollMarkupInternal(block)
    }

    private fun hasRealNewsPollMarkupInternal(block: String): Boolean {
        val hasVoteForm = formTagRegex.findAll(block).any { match ->
            val form = match.value
            val formAttrs = match.groupValues.getOrNull(1).orEmpty()
            val hasPollTarget = pollActionRegex.containsMatchIn(formAttrs) ||
                    pollIdInputRegex.containsMatchIn(form) ||
                    form.contains("pages/poll", ignoreCase = true) ||
                    form.contains("poll_id", ignoreCase = true)
            hasPollTarget && answerInputRegex.containsMatchIn(form)
        }
        return hasVoteForm ||
                dataSitePollAttrRegex.containsMatchIn(block) ||
                pollResultRegex.containsMatchIn(block) ||
                isPollFrameMarkup(block) ||
                isFallbackNewsPollBlock(block)
    }

    fun hasNewsPollMarkersInternal(block: String): Boolean =
            block.contains("poll", ignoreCase = true) ||
                    block.contains("vote", ignoreCase = true) ||
                    block.contains("answer[]", ignoreCase = true) ||
                    block.contains("poll_id", ignoreCase = true) ||
                    block.contains("pages/poll", ignoreCase = true) ||
                    block.contains("data-site-poll", ignoreCase = true) ||
                    block.contains("опрос", ignoreCase = true) ||
                    block.contains("голос", ignoreCase = true)

    fun isNormalizedNewsPollBlock(block: String): Boolean =
            block.contains("news-poll-normalized", ignoreCase = true) ||
                    block.contains("data-normalized-poll", ignoreCase = true)

    fun isFallbackNewsPollBlock(block: String): Boolean =
            block.contains("news-poll-fallback", ignoreCase = true) ||
                    block.contains("data-poll-fallback", ignoreCase = true)

    @Suppress("UnusedPrivateMember")
    fun isRawTemplatePollBlock(block: String): Boolean =
            block.contains("data-raw-template-poll", ignoreCase = true) ||
                    containsRawPollTemplate(block)

    fun containsRawPollTemplate(block: String?): Boolean {
        val source = block.orEmpty()
        return rawPollTemplateTokens.any { source.contains(it, ignoreCase = true) }
    }

    private fun isPollFrameMarkup(block: String): Boolean =
            pollFrameMarkerRegex.containsMatchIn(block) &&
                    (pollFrameTitleRegex.containsMatchIn(block) || pollFrameButtonRegex.containsMatchIn(block))

    // --- Text helpers (slice 2) ---

    fun cleanPollText(source: String): String =
            articleFromHtml(
                    source
                            .replace(Regex("""(?is)<script\b[\s\S]*?</script>"""), " ")
                            .replace(Regex("""(?is)<style\b[\s\S]*?</style>"""), " ")
                            .replace(Regex("""(?is)<input\b[^>]*>"""), " ")
                            .replace(Regex("""(?is)<[^>]+>"""), " ")
            )
                    ?.replace(Regex("""\s+"""), " ")
                    ?.trim()
                    .orEmpty()

    fun isUsablePollText(text: String): Boolean =
            text.isNotBlank() && !containsRawPollTemplate(text)

    fun stripRawPollTemplates(html: String?): String? {
        val source = html ?: return null
        if (!containsRawPollTemplate(source)) return source
        return source
                .replace(rawPollTemplateScriptRegex, "")
                .replace(rawPollContainerRegex, "")
                .takeIf { it.isNotBlank() }
    }

    fun pollIdFromText(source: String): String? =
            Regex("""(?i)(?:poll_id|poll)\D{0,24}(\d+)""")
                    .find(articleFromHtml(source).orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)

    fun isPollTitle(title: String): Boolean =
            pollTitleRegex.containsMatchIn(articleFromHtml(title)?.trim().orEmpty())

    // --- Fallback poll-block builders (slice 3) ---

    private fun hasRenderableMarkup(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        return isNormalizedNewsPollBlock(html) || isFallbackNewsPollBlock(html)
    }

    private fun pollTitleFromResponse(response: String): String {
        Regex("""(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*["']og:title["'])(?=[^>]*content\s*=\s*["']([^"']+)["'])[^>]*>""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { articleFromHtml(it) }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        Regex("""(?is)<h1\b[^>]*>([\s\S]*?)</h1>""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { cleanPollText(it) }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        return ""
    }

    fun fallbackNewsPollBlock(response: String): String {
        val sourceUrl = articleUrlFromResponse(response)
        val title = pollTitleFromResponse(response).ifBlank { "Опрос" }
        return buildString {
            append("""<div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized news-poll-fallback" data-poll-fallback="true">""")
            append("""<span data-raw-template-poll="true" style="display:none"></span>""")
            append("""<h2>""")
            append(articleHtmlEncode(title))
            append("""</h2><p class="poll_status">Опрос доступен на сайте</p>""")
            sourceUrl?.let {
                append("""<button type="button" class="btn news-poll-browser-button" data-open-external-browser="true" data-href="""")
                append(articleHtmlEncode(it))
                append("""">Открыть статью в браузере</button>""")
            }
            append("""</div>""")
        }
    }

    private fun fallbackNewsPollBlock(response: String, titleOverride: String): String {
        val sourceUrl = articleUrlFromResponse(response)
        val title = titleOverride.takeIf { isUsablePollText(it) } ?: "Опрос доступен на сайте"
        return buildString {
            append("""<div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized news-poll-fallback" data-poll-fallback="true" data-forced-fallback-poll="true">""")
            append("""<h2>""")
            append(articleHtmlEncode(title))
            append("""</h2><p class="poll_status">Опрос доступен на сайте</p>""")
            sourceUrl?.let {
                append("""<button type="button" class="btn news-poll-browser-button" data-open-external-browser="true" data-href="""")
                append(articleHtmlEncode(it))
                append("""">Открыть статью в браузере</button>""")
            }
            append("""</div>""")
        }
    }

    fun forcedFallbackNewsPollBlock(
            response: String,
            articleId: Int,
            articleTitle: String?,
            currentHtml: String?
    ): String? {
        // Lazy data-site-poll placeholders are not renderable; do not treat them as a real poll block.
        if (hasRenderableMarkup(currentHtml)) return null
        val effectiveArticleId = articleId.takeIf { it > 0 } ?: articleIdFromResponse(response)
        val effectiveTitle = articleTitle.orEmpty().ifBlank { pollTitleFromResponse(response) }
        val shouldForceFallback = effectiveArticleId == 456521 || isPollTitle(effectiveTitle)
        return if (shouldForceFallback) fallbackNewsPollBlock(response, effectiveTitle) else null
    }

    fun appendOrReplaceFallbackPollBlock(currentHtml: String?, fallbackBlock: String): String =
            listOf(currentHtml.orEmpty().replace(newsPollFallbackBlockRegex, ""), fallbackBlock)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
}
