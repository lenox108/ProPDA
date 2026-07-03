package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage

/**
 * Rejects login/error/captcha/empty responses before treating an article load as success.
 */
object ArticleHtmlValidator {

    const val MIN_RAW_HTML_LEN: Int = 400
    const val MIN_ARTICLE_BODY_LEN: Int = 80
    const val MIN_RENDERABLE_MAPPED_LEN: Int = 120
    private const val MIN_RENDERABLE_TEXT_LEN: Int = 24

    enum class PageKind {
        ARTICLE,
        LOGIN,
        ERROR,
        CAPTCHA,
        UNKNOWN
    }

    data class BodyMetrics(
            val articleRootFound: Boolean,
            val articleBlocksCount: Int,
            val hasTitle: Boolean,
            val hasHeroImage: Boolean,
            val hasLeadParagraph: Boolean,
            val imageBlocksCount: Int,
            val videoBlocksCount: Int
    )

    data class CacheVerdict(
            val valid: Boolean,
            val reason: String?
    )

    fun classifyRawHtml(html: String): PageKind {
        if (html.isBlank()) return PageKind.UNKNOWN
        if (looksLikeCaptcha(html)) return PageKind.CAPTCHA
        if (looksLikeLoginPage(html)) return PageKind.LOGIN
        if (looksLikeErrorPage(html)) return PageKind.ERROR
        if (looksLikeArticlePage(html)) return PageKind.ARTICLE
        return PageKind.UNKNOWN
    }

    fun looksLikeLoginPage(html: String): Boolean {
        // Настоящая стена логина ЗАМЕНЯЕТ контент. Если в ответе есть тело статьи — это сама статья
        // (напр. новость ПРО пароли/PIN-коды): `act=auth` из шапки сайта + слово "password" в тексте
        // не должны ложно классифицировать её как страницу входа. Тот же гард, что в looksLikeErrorPage.
        if (html.length >= 8_000 && looksLikeArticlePage(html)) return false
        return html.contains("wp-login.php", ignoreCase = true) ||
                html.contains("loginform", ignoreCase = true) ||
                (html.contains("act=auth", ignoreCase = true) &&
                        html.contains("password", ignoreCase = true))
    }

    fun looksLikeCaptcha(html: String): Boolean =
            html.contains("captcha", ignoreCase = true) &&
                    (html.contains("captcha-image", ignoreCase = true) ||
                            html.contains("captcha-time", ignoreCase = true))

    fun looksLikeErrorPage(html: String): Boolean {
        if (html.length >= 8_000 && looksLikeArticlePage(html)) return false
        return html.contains("errors-list", ignoreCase = true) ||
                html.contains("страница не найдена", ignoreCase = true) ||
                (html.length < 4_000 &&
                        (html.contains(">404<", ignoreCase = true) ||
                                html.contains("error-404", ignoreCase = true)))
    }

    fun looksLikeArticlePage(html: String): Boolean =
            html.contains("entry-content", ignoreCase = true) ||
                    html.contains("article-body", ignoreCase = true) ||
                    html.contains("itemprop=\"articleBody\"", ignoreCase = true) ||
                    html.contains("class=\"article", ignoreCase = true) ||
                    html.contains("material_item", ignoreCase = true)

    fun hasNonEmptyParsedBody(page: DetailsPage): Boolean {
        val body = page.html.orEmpty()
        if (body.length < MIN_ARTICLE_BODY_LEN) return false
        return normalizeText(body).length >= 32
    }

    fun isRenderableMappedHtml(html: String): Boolean {
        if (html.length < MIN_RENDERABLE_MAPPED_LEN) return false
        if (!html.contains("<body", ignoreCase = true)) return false
        val hasShell = html.contains("class=\"content\"", ignoreCase = true) ||
                html.contains("news-detail-header", ignoreCase = true) ||
                html.contains("material_item", ignoreCase = true) ||
                html.contains("id=\"news\"", ignoreCase = true)
        if (!hasShell && !looksLikeArticlePage(html)) return false
        if (mappedContentPlainTextLen(html) >= MIN_RENDERABLE_TEXT_LEN) return true
        if (mappedBodyPlainTextLen(html) >= MIN_RENDERABLE_TEXT_LEN) return true
        return hasRenderableMedia(html)
    }

    /** Text inside the mapped `.content` block (balanced div scan, not first nested close). */
    fun mappedContentPlainTextLen(html: String): Int {
        val inner = extractFirstDivWithClassInner(html, "content") ?: return 0
        return normalizeText(inner).length
    }

    /** Plain text in &lt;body&gt; excluding header/scripts (fallback when `.content` is nested). */
    fun mappedBodyPlainTextLen(html: String): Int {
        val bodyInner = bodyInnerRegex.find(html)?.groupValues?.get(1) ?: html
        val withoutHeader = newsDetailHeaderRegex.replace(bodyInner, " ")
        val withoutScripts = scriptAndStyleRegex.replace(withoutHeader, " ")
        return normalizeText(withoutScripts).length
    }

    /**
     * Extracts inner HTML of the first &lt;div class="… className …"&gt; using depth counting.
     * Shared with [forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate] remap path.
     */
    fun extractFirstDivWithClassInner(html: String, className: String): String? {
        val openPattern = Regex(
                """(?is)<div\b[^>]*\bclass\s*=\s*["'][^"']*\b""" +
                        Regex.escape(className) +
                        """\b[^"']*["'][^>]*>"""
        )
        val open = openPattern.find(html) ?: return null
        var depth = 1
        var index = open.range.last + 1
        while (index < html.length && depth > 0) {
            val nextOpen = divOpenTagRegex.find(html, index)
            val nextClose = divCloseTagRegex.find(html, index)
            when {
                nextClose == null -> return null
                nextOpen != null && nextOpen.range.first < nextClose.range.first -> {
                    depth++
                    index = nextOpen.range.last + 1
                }
                else -> {
                    depth--
                    if (depth == 0) {
                        return html.substring(open.range.last + 1, nextClose.range.first)
                    }
                    index = nextClose.range.last + 1
                }
            }
        }
        return null
    }

    fun validateCached(
            page: DetailsPage,
            parserVersion: Int,
            storedAtMs: Long,
            maxAgeMs: Long,
            nowMs: Long
    ): CacheVerdict {
        if (parserVersion != ARTICLE_PARSER_VERSION) {
            return CacheVerdict(false, "parser_version_mismatch")
        }
        if (page.id <= 0) return CacheVerdict(false, "missing_article_id")
        if (page.title.isNullOrBlank()) return CacheVerdict(false, "missing_title")
        if (!hasNonEmptyParsedBody(page)) return CacheVerdict(false, "empty_body")
        if (storedAtMs > 0L && nowMs - storedAtMs > maxAgeMs) {
            return CacheVerdict(false, "expired")
        }
        return CacheVerdict(true, null)
    }

    fun measureBody(html: String?, title: String?, heroUrl: String?): BodyMetrics {
        val body = html.orEmpty()
        val rootFound = looksLikeArticlePage(body) || body.contains("<article", ignoreCase = true)
        val blocks = blockTagRegex.findAll(body).count().coerceAtLeast(
                if (normalizeText(body).length >= 32) 1 else 0
        )
        val hasLead = leadClassRegex.containsMatchIn(body) ||
                body.contains("<p", ignoreCase = true)
        return BodyMetrics(
                articleRootFound = rootFound,
                articleBlocksCount = blocks,
                hasTitle = !title.isNullOrBlank(),
                hasHeroImage = !heroUrl.isNullOrBlank(),
                hasLeadParagraph = hasLead,
                imageBlocksCount = imgTagRegex.findAll(body).count(),
                videoBlocksCount = videoTagRegex.findAll(body).count() +
                        iframeTagRegex.findAll(body).count()
        )
    }

    private fun hasRenderableMedia(html: String): Boolean =
            html.length >= MIN_RENDERABLE_MAPPED_LEN &&
                    (imgTagRegex.containsMatchIn(html) ||
                            iframeTagRegex.containsMatchIn(html) ||
                            videoTagRegex.containsMatchIn(html))

    private fun normalizeText(html: String): String =
            html.replace(Regex("<[^>]+>"), " ")
                    .replace('\u00A0', ' ')
                    .replace(Regex("\\s+"), " ")
                    .trim()

    private val bodyInnerRegex = Regex("""(?is)<body\b[^>]*>([\s\S]*)</body>""")
    private val newsDetailHeaderRegex = Regex("""(?is)<article\b[^>]*\bnews-detail-header\b[\s\S]*?</article>""")
    private val scriptAndStyleRegex = Regex("""(?is)<(?:script|style)\b[^>]*>[\s\S]*?</(?:script|style)>""")
    private val divOpenTagRegex = Regex("""(?is)<div\b""")
    private val divCloseTagRegex = Regex("""(?is)</div\s*>""")
    private val blockTagRegex = Regex("""(?i)<(?:p|blockquote|h[2-6]|ul|ol|pre|figure)\b""")
    private val leadClassRegex = Regex("""(?i)\bclass\s*=\s*["'][^"']*\b(?:lead|intro|announce)\b""")
    private val imgTagRegex = Regex("""(?i)<img\b""")
    private val videoTagRegex = Regex("""(?i)<video\b""")
    private val iframeTagRegex = Regex("""(?i)<iframe\b""")
}
