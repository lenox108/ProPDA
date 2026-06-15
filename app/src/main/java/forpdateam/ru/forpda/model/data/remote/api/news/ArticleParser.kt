package forpdateam.ru.forpda.model.data.remote.api.news

import timber.log.Timber
import android.util.SparseArray
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.ArticleParseTrace
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.entity.remote.news.*
import android.os.SystemClock
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Document
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Node
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Parser
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import okhttp3.Cookie
import org.json.JSONArray
import java.net.URLDecoder
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.LinkedHashMap

const val INLINE_COMMENT_EDIT_SUBMIT_URL = "https://4pda.to/wp-comments-post.php"

enum class ArticleParsePhase {
    /** Title, body, hero, lightweight embeds — no comment-tree parse, no desktop probe. */
    FIRST_RENDER,
    /** Full metadata (taxonomy, comment UL via DOM when regex misses). */
    FULL
}

class ArticleParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    @Volatile
    private var pollVoteCookies: Map<String, Set<String>> = emptyMap()

    /** Mirrors 4PDA site JS: voted state for news polls is stored in poll-{id} cookies. */
    fun syncPollVoteCookies(clientCookies: Map<String, Cookie>) {
        pollVoteCookies = extractPollVoteCookies(clientCookies)
    }

    private data class CommentProbeCacheEntry(
            val source: String,
            val parsed: Comment
    )

    private val commentProbeCache = object : LinkedHashMap<Int, CommentProbeCacheEntry>(COMMENT_PROBE_CACHE_MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CommentProbeCacheEntry>?): Boolean =
                size > COMMENT_PROBE_CACHE_MAX_ENTRIES
    }

    private companion object {
        /** Parsed when the page has comments but the count is not in the HTML snippet. */
        const val UNKNOWN_COMMENTS_COUNT = -1
        const val MIN_THUMB_SRCSET_WIDTH = 600
        const val COMMENT_PROBE_CACHE_MAX_ENTRIES = 4
        const val MAX_THUMB_SRCSET_WIDTH = 1200
        val newsPollFrameRegex = Regex(
                """(?is)<div\b(?=[^>]*\bid\s*=\s*["'][^"']*poll-ajax-frame[^"']*["'])[^>]*>[\s\S]*?</div>"""
        )
        val newsPollFallbackBlockRegex = Regex(
                """(?is)<div\b(?=[^>]*\bdata-poll-fallback\s*=\s*["']true["'])[^>]*>[\s\S]*?</div>"""
        )
        val rawPollContainerRegex = Regex(
                """(?is)<(?:div|section|aside|form)\b(?=[^>]*(?:poll|vote))[^>]*>[\s\S]*?(?:\{%.+?%\}|${'$'}args|${'$'}\{|showResult\(\)|getColor\(\)|Math\.round\(100\*)[\s\S]*?</(?:div|section|aside|form)>"""
        )
        val rawPollTemplateScriptRegex = Regex(
                """(?is)<script\b[^>]*(?:type\s*=\s*["']text/(?:x-)?template["'])?[^>]*>[\s\S]*?(?:\{%.+?%\}|${'$'}args|${'$'}\{|showResult\(\)|getColor\(\)|Math\.round\(100\*)[\s\S]*?</script>"""
        )
        val newsPollFormRegex = Regex(
                """(?is)<form\b[^>]*>[\s\S]*?</form>"""
        )
        val dataSitePollAttrRegex = Regex(
                """(?is)\bdata-site-poll\s*=\s*(["'])(.*?)\1"""
        )
        val pollFrameIdRegex = Regex("""(?is)\bid\s*=\s*(["'])([^"']*poll-ajax-frame[^"']*)\1""")
        val formTagRegex = Regex("""(?is)<form\b([^>]*)>[\s\S]*?</form>""")
        val pollStatusRegex = Regex("""(?is)<p\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bpoll_status\b[^"']*["'])[^>]*>[\s\S]*?</p>""")
        val pollParagraphRegex = Regex("""(?is)<p\b[^>]*>[\s\S]*?</p>""")
        val answerInputRegex = Regex("""(?is)<input\b(?=[^>]*\btype\s*=\s*["']?(?:radio|checkbox)["']?)(?=[^>]*\bname\s*=\s*["']?answer(?:\[\]|\[[^"'>\s]*\])?["']?)[^>]*>""")
        val pollIdInputRegex = Regex("""(?is)<input\b(?=[^>]*\bname\s*=\s*["']?(?:poll_id|poll)["']?)[^>]*>""")
        val pollActionRegex = Regex("""(?is)\baction\s*=\s*["'][^"']*(?:/pages/poll/|pages/poll|poll_id=|act=vote)[^"']*["']""")
        val pollResultRegex = Regex("""(?is)<ul\b[^>]*\bclass\s*=\s*["'][^"']*poll-list[^"']*["'][^>]*>[\s\S]*?(?:\bslider\b|\brange\b|\bvalue\b|Всего\s+голосов|num_votes|poll_status)[\s\S]*?</ul>|(?:\brange_bar\b|\bvotes_info\b|\bpoll_status\b|Всего\s+голосов)""")
        val renderedPollListRegex = Regex("""(?is)<h[1-6]\b[^>]*>([\s\S]*?)</h[1-6]>\s*<ul\b[^>]*>([\s\S]*?)</ul>""")
        val renderedPollOptionRegex = Regex("""(?is)<li\b[^>]*>([\s\S]*?)</li>""")
        val rejectedRenderedPollListRegex = Regex(
                """(?is)<a\b|\bhref\s*=|\b(?:v-count|comment-count|comments?|itemprop|article|post|news-list|popular|most)\b|самые\s+комментируем|комментируемые"""
        )
        val countSuffixRegex = Regex("""\s\d{1,5}$""")
        val articleIdRegex = Regex("""(?i)(?:[?&]p=|/)(\d{4,})(?:[/?#&"']|$)""")
        val articleIdMetaRegex = Regex("""(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*["']article:id["'])(?=[^>]*content\s*=\s*["'](\d+)["'])[^>]*>""")
        val articleItemIdRegex = Regex("""(?is)\bitemid\s*=\s*(["'])(\d{4,})\1""")
        val pollTitleRegex = Regex("""(?i)(^|\s)опрос\s*:""")
        // 4PDA "poll based on publication" design (poll-frame): button-based vote state and
        // <span> result state. This markup has NO answer[] radios / ul.poll-list, so the classic
        // poll detectors miss it entirely and its title leaks into the body as a bare bold heading.
        val pollFrameMarkerRegex = Regex("""(?i)\bpoll-frame(?:-option|-title|-options)\b""")
        val pollFrameTitleRegex = Regex("""(?is)<h\d\b[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame-title\b[^"']*["'][^>]*>([\s\S]*?)</h\d>""")
        val pollFrameButtonRegex = Regex("""(?is)<button\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame-option\b[^"']*["'])(?=[^>]*\bvalue\s*=\s*["']([^"']*)["'])[^>]*>([\s\S]*?)</button>""")
        val pollFrameResultSpanRegex = Regex("""(?is)<span\b[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame-option\b[^"']*["'][^>]*>([\s\S]*?)</span>""")
        val pollFrameFootRegex = Regex("""(?is)<div\b[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame-foot\b[^"']*["'][^>]*>([\s\S]*?)</div>""")
        val pollFramePercentRegex = Regex("""(?s)^(.*?)[\s\u00A0-]*?(\d{1,3})\s*%\s*$""")
        val pollFrameVotesRegex = Regex("""(?i)проголосовал[оаи]?\D*([\d\s\u00A0]+)""")
        val pollFrameOptionsIdRegex = Regex("""(?i)poll-frame-options-(\d+)\b""")
        val pollFrameAjaxIdRegex = Regex("""(?i)poll-ajax-frame-(\d+)\b""")
        val pollFrameContainerOpenRegex = Regex("""(?is)<div\b[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame\b[^"']*["'][^>]*>""")
        val pollAjaxFrameOpenRegex = Regex("""(?is)<div\b[^>]*\bid\s*=\s*["'][^"']*poll-ajax-frame[^"']*["'][^>]*>""")
        val pollFrameTitleHeadingRegex = Regex("""(?is)<h\d\b[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame-title\b[^"']*["'][^>]*>[\s\S]*?</h\d>""")
        val pollFrameOptionsFormRegex = Regex("""(?is)<form\b[^>]*\bclass\s*=\s*["'][^"']*\bpoll-frame-options\b[^"']*["'][^>]*>[\s\S]*?</form>""")
        val pollVoteCookieNameRegex = Regex("""(?i)^poll-(\d+)$""")

        fun extractPollVoteCookies(clientCookies: Map<String, Cookie>): Map<String, Set<String>> {
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
        val divOpenTagRegex = Regex("""(?is)<div\b""")
        val divCloseTagRegex = Regex("""(?is)</div\s*>""")
        val rawPollTemplateTokens = listOf(
                "{%",
                "%}",
                "\$args",
                "\${",
                "showResult()",
                "getColor()",
                "Math.round(100*"
        )
        val sponsoredCardLinkRegex = Regex(
                """(?is)<a\b(?=[^>]*\bhref\s*=\s*(['"])(https?://(?:(?!\1).)*?)\1)(?=[^>]*\bstyle\s*=\s*(['"])(?:(?!\3).)*background(?:-image|-color)?\s*:(?:(?!\3).)*\3)[^>]*>\s*<span\b(?=[^>]*\bstyle\s*=\s*(['"])(?:(?!\4).)*(?:background-color|font-size|color)\s*:(?:(?!\4).)*\4)[^>]*>([\s\S]*?)</span>\s*</a>"""
        )
        val sponsoredCardWrapperRegex = Regex(
                """(?is)(?:<link\b[^>]*>\s*)?<div\b(?=[^>]*\bstyle\s*=\s*(['"])(?:(?!\1).)*background-color\s*:(?:(?!\1).)*\1)[^>]*>\s*(?:<span\b[^>]*></span>\s*)?(<a\b[^>]*>[\s\S]*?</a>)\s*</div>"""
        )
        val whitespaceRegex = Regex("""\s+""")
        val mediaTagRegex = Regex("""(?is)<(?:img|picture|figure|video|iframe|embed|object|oembed)\b""")
        val youtubeMediaTagRegex = Regex(
                """(?is)<(?:iframe|embed)\b(?=[^>]*\b(?:src|data-src)\s*=\s*(["'])(.*?)\1)[^>]*>(?:\s*</(?:iframe|embed)>)?"""
        )
        val youtubeOembedTagRegex = Regex(
                """(?is)<oembed\b(?=[^>]*\burl\s*=\s*(["'])(.*?)\1)[^>]*>(?:\s*</oembed>)?"""
        )
        val youtubeLinkBlockRegex = Regex(
                """(?is)<(p|div)\b([^>]*)>\s*<a\b(?=[^>]*\bhref\s*=\s*(["'])(.*?)\3)[^>]*>([\s\S]*?)</a>\s*</\1>"""
        )
        val youtubeIframeSrcRegex = Regex("""(?is)\b(?:src|data-src)\s*=\s*(["'])(.*?)\1""")
        val youtubeOembedUrlRegex = Regex("""(?is)\burl\s*=\s*(["'])(.*?)\1""")
        val youtubeHrefRegex = Regex("""(?is)\bhref\s*=\s*(["'])(.*?)\1""")
        // Fast extraction of comments list markup from raw HTML.
        //
        // IMPORTANT: do NOT use a simple non-balanced ".*?</ul>" regex here.
        // Comment bodies may contain nested <ul>/<ol> lists; a non-balanced regex would truncate
        // the outer comment-list to the first inner </ul>, causing:
        // - only 1 comment rendered
        // - broken HTML which often results in empty comment body after parsing
        val commentListOpenTagRegex = Regex(
                """(?is)<(ul|ol)\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bcomments?-list\b[^"']*["'])[^>]*>"""
        )
        val articleContentRegexes = listOf(
                Regex("""(?is)<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bentry-content\b[^"']*["'])[^>]*>([\s\S]*?)</div>\s*<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\barticle-footer\b)"""),
                Regex("""(?is)<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bentry-content\b[^"']*["'])[^>]*>([\s\S]*?)</div>"""),
                Regex("""(?is)<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\barticle\b[^"']*["'])[^>]*>([\s\S]*?)<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\barticle-footer\b)"""),
                Regex("""(?is)<div\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bcontent-box\b[^"']*["'])(?=[^>]*\bitemprop\s*=\s*["']articleBody["'])[^>]*>([\s\S]*?)</div>"""),
                Regex("""(?is)<article\b[^>]*>([\s\S]*?)</article>""")
        )
        val leadClassMarkers = listOf("lead", "intro", "announce", "subtitle", "article__lead", "content__lead")
        val articleBodyClassMarkers = listOf(
                "entry-content",
                "article-content",
                "article__content",
                "article-body",
                "articlebody",
                "content-body",
                "content-box",
                "material-text",
                "post__message",
                "post-message",
                "post-content"
        )
        val articleMediaClassMarkers = listOf(
                "photo",
                "image",
                "picture",
                "figure",
                "gallery",
                "video",
                "article-photo",
                "article-image",
                "article__image",
                "article__photo",
                "embed",
                "youtube"
        )
        val skippedArticleClassMarkers = listOf(
                "article-header",
                "article-footer",
                "article-footer-tags",
                "meta",
                "tags",
                "comment",
                "materials"
        )
        val articleBodyMetaClassMarkers = listOf(
                "article-meta",
                "article-date",
                "article-comments",
                "article-title",
                "date",
                "time",
                "comments",
                "comment-count",
                "title"
        )
        val titleTagNames = setOf("h1", "h2", "h3", "h4", "strong", "legend")
        val contentHeadingTagNames = setOf("h2", "h3", "h4", "h5", "h6")
        val renderedPollContainerTagNames = setOf("form", "div", "section", "aside")
        val optionContainerTagNames = setOf("label", "li", "p")
        val htmlEntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]+);""")
        val youtubeIdRegexes = listOf(
                Regex("""(?i)(?:youtube\.com/(?:watch\?(?:[^"'<>#&amp;]*[&amp;])*v=|embed/|shorts/)|youtube-nocookie\.com/embed/)([A-Za-z0-9_-]{11})"""),
                Regex("""(?i)youtu\.be/([A-Za-z0-9_-]{11})""")
        )
        val metaImageRegex = Regex("""(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*(["'])((?:og|twitter):image(?::src)?)\1)(?=[^>]*content\s*=\s*(["'])(.*?)\3)[^>]*>""")
        val heroImageContainerRegex = Regex("""(?is)<(?:figure|picture|div)\b(?=[^>]*\bclass\s*=\s*(["'])(?:(?!\1).)*(?:article-image|article-photo|article__image|article__photo|article-header-image|article-image-main|post-image|entry-image)(?:(?!\1).)*\1)[^>]*>[\s\S]*?</(?:figure|picture|div)>""")
        val articleHeaderRegex = Regex("""(?is)<(?:header|div)\b(?=[^>]*\bclass\s*=\s*(["'])(?:(?!\1).)*article-header(?:(?!\1).)*\1)[^>]*>[\s\S]*?</(?:header|div)>""")
        val firstImageTagRegex = Regex("""(?is)<img\b[^>]*>""")
        val firstSourceTagRegex = Regex("""(?is)<source\b[^>]*>""")
        val articleLightboxImageRegex = Regex("""(?is)<a\b(?=[^>]*\bdata-lightbox\s*=\s*(["'])post-\d+\1)[^>]*>[\s\S]*?<img\b[^>]*>[\s\S]*?</a>""")
        val commentEditedTextRegex = Regex("""(?i)\bотредактирован[а-я]*\b|(?:message\s+)?edited\s+by\b""")
        val moderationNonceFieldNames = listOf(
                "_wpnonce",
                "_ajax_nonce-replyto-comment",
                "_ajax_nonce",
                "wpnonce"
        )
        val commentTextFieldNames = listOf("content", "comment", "message", "text", "newcomment_content")
        val wpAjaxCdataRegex = Regex("""(?is)<!\[CDATA\[([\s\S]*?)\]\]>""")
        val formActionAttrRegex = Regex("""(?is)<form\b[^>]*\baction\s*=\s*["']([^"']+)["']""")
        val textareaByIdRegex = Regex("""(?is)<textarea\b[^>]*\bid\s*=\s*["'](replycontent|comment)["'][^>]*>([\s\S]*?)</textarea>""")
        val commentEditedHtmlRegex = Regex("""(?is)<(?:span|em|small|div)\b(?=[^>]*\bclass\s*=\s*(["'])(?:(?!\1).)*(?:edited|edit|modified)(?:(?!\1).)*\1)[^>]*>[\s\S]*?</(?:span|em|small|div)>""")
        val commentEditedWrapperRegex = Regex("""(?is)(?:&nbsp;|\s|<br\s*/?>)*\((?:&nbsp;|\s|<br\s*/?>|<[^>]+>)*(?:сообщение\s+)?(?:отредактирован[а-я]*|(?:message\s+)?edited\s+by\b)[\s\S]{0,300}?\)(?:&nbsp;|\s|<br\s*/?>)*""")
        val commentNumericIdRegex = Regex("""(?i)^comment-?(\d+)$""")
        val commentOpenTagRegex =
                Regex("""(?is)<(?:div|article|li)\s+([^>]*(?:\bid\s*=\s*["']comment-?(\d+)["']|\bdata-comment(?:-id)?\s*=\s*["']?(\d+))[^>]*)>""")
        val commentDeletedClassRegex =
                Regex("""(?is)\bclass\s*=\s*["'][^"']*\bdeleted\b""")
        val commentDataIdRegex =
                Regex("""(?is)\bdata-comment(?:-id)?\s*=\s*["']?(\d+)""")
        val commentUserDataIdRegex =
                Regex("""(?is)\bdata-(?:user|author)(?:-id)?\s*=\s*["']?(\d+)""")
        val commentShowUserRegex = Regex("""(?is)showuser=(\d+)""")
        val commentFallbackNickRegex =
                Regex("""(?is)<(?:a|span)[^>]*class=["'][^"']*nickname[^"']*["'][^>]*>([\s\S]*?)</(?:a|span)>""")
        val commentFallbackDateRegex =
                Regex("""(?is)<(?:a|span|time)[^>]*class=["'][^"']*(?:date|time)[^"']*["'][^>]*>([\s\S]*?)</(?:a|span|time)>""")
        val commentFallbackContentRegex =
                Regex("""(?is)<(?:div|p)[^>]*class=["'][^"']*(?:comment-content|content)[^"']*["'][^>]*>([\s\S]*?)</(?:div|p)>""")
        val emptyCommentEditedWrapperRegex = Regex("""(?is)(?:&nbsp;|\s|<br\s*/?>)*\((?:&nbsp;|\s|<br\s*/?>)*\)(?:&nbsp;|\s|<br\s*/?>)*""")
        val inputTagRegex = Regex("""(?is)<input\b[^>]*>""")
        val dataKarmaRegex = Regex("""^\s*(\d+)\s*-\s*(\d+)\s*$""")
        val dataKarmaAttrRegex =
                Regex("""(?is)\bdata-karma(?:-actions)?\s*=\s*["'](\d+)\s*-\s*(\d+)["']""")
        val dataKarmaActLikeRegex =
                Regex("""(?is)\bdata-karma-act\s*=\s*["']1-(\d+)-(\d+)["']""")
        val karmaPageCommentIdRegex =
                Regex("""(?is)/pages/karma\?[^"'<>]*\bc=(\d+)""")
        val karmaEntryRegex = Regex("""(?s)"(\d+)":\[([^\]]*)\]""")
        val commentsAnchorCountRegex = Regex("""(?is)<a\b(?=[^>]*\bhref\s*=\s*(["'])(?:(?!\1).)*#comments\1)[^>]*>\s*(\d+)\s*</a>""")
        val commentsClassCountRegex = Regex("""(?is)\b(?:v-count|comment-count)\b[^>]*>\s*(\d+)""")
        // The CURRENT article's own comment counter (mobile layout). Unlike #comments anchors and
        // v-count (which also appear in the related/popular-news widgets for OTHER articles), these
        // two badges belong only to the article being viewed, so they are authoritative.
        // - <div class="...head-comments-count"><span title="Комментарии"><i class="icon-comment"></i>N</span>
        // - <div class="article-meta-comment"><a href="...#comments">N</a>
        val headCommentsCountRegex = Regex(
                """(?is)\bhead-comments-count\b[^>]*>\s*<span\b[^>]*>(?:\s*<i\b[^>]*>\s*</i>)?\s*(\d+)"""
        )
        val articleMetaCommentCountRegex = Regex(
                """(?is)\barticle-meta-comment\b[^>]*>\s*<a\b[^>]*>\s*(\d+)\s*<"""
        )
        val commentsMetaCountRegex = Regex("""(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*(["'])(?:comment:count|comments:count|article:comment_count|article:comments_count)\1)(?=[^>]*content\s*=\s*(["'])(\d+)\2)[^>]*>""")
        val commentsJsonLdCountRegex = Regex("""(?is)"(?:commentCount|comment_count|commentsCount|comments_count|userInteractionCount)"\s*:\s*"?(\d+)""")
        val articleCountScopeRegexes = listOf(
                Regex("""(?is)<article\b[^>]*>[\s\S]*?</article>"""),
                Regex("""(?is)<li\b(?=[^>]*\bitemscope\b)[^>]*>[\s\S]*?</li>"""),
                Regex("""(?is)<div\b(?=[^>]*\bclass\s*=\s*(["'])(?:(?!\1).)*\barticle\b(?:(?!\1).)*\1)[^>]*>[\s\S]*?</div>"""),
                Regex("""(?is)<header\b(?=[^>]*\bclass\s*=\s*(["'])(?:(?!\1).)*\barticle-header\b(?:(?!\1).)*\1)[^>]*>[\s\S]*?</header>""")
        )
    }

    private val scope = ParserPatterns.Articles

    /** Reuses one [Parser.parse] per article page instead of parsing the same HTML repeatedly. */
    private class ArticlePageContext(val response: String) {
        private var document: Document? = null
        private var documentResolved = false

        fun documentOrNull(): Document? {
            if (!documentResolved) {
                documentResolved = true
                document = runCatching { Parser.parse(response) }.getOrNull()
            }
            return document
        }
    }

    fun parseArticles(response: String): List<NewsItem> {
        val pattern = patternProvider.getPattern(scope.scope, scope.list)
        val matcher = pattern.matcher(response)
        if (matcher.find()) {
            matcher.reset()
            return matcher.map { m ->
                NewsItem().apply {
                    val isReview = m.group(1) == null
                    if (!isReview) {
                        url = m.group(1).orEmpty()
                        id = m.group(2).orEmpty().toIntOrNull() ?: 0
                        title = m.group(3).orEmpty().articleFromHtml().articleFromHtml()
                        imgUrl = selectArticleImageUrl(m.group(0).orEmpty(), m.group(4))
                        commentsCount = resolveListItemCommentsCount(m.group(0).orEmpty())
                        date = m.group(6)
                        authorId = m.group(7).orEmpty().toIntOrNull() ?: 0
                        author = m.group(8).orEmpty().articleFromHtml()
                        description = m.group(9).orEmpty().articleFromHtml()
                        m.group(10)?.let {
                            tags.addAll(parseTags(it))
                        }
                    } else {
                        url = m.group(11).orEmpty()
                        id = m.group(12).orEmpty().toIntOrNull() ?: 0
                        imgUrl = selectArticleImageUrl(m.group(0).orEmpty(), m.group(13))
                        title = m.group(14).orEmpty().articleFromHtml().articleFromHtml()
                        commentsCount = resolveListItemCommentsCount(m.group(0).orEmpty())
                        date = m.group(17).orEmpty().replace('-', '.')
                        author = m.group(18).orEmpty().articleFromHtml()
                        description = m.group(20).orEmpty().trim().articleFromHtml()
                    }
                }
            }
        }
        Timber.d("parseArticles: legacy regex miss, using fallback parser")
        return parseArticlesFallback(response)
    }

    private val reArticleBlock = Regex(
        """<article[^>]*?\bitemid\s*=\s*(["'])(\d+)\1[^>]*?>[\s\S]*?</article>""",
        RegexOption.IGNORE_CASE
    )
    private val reImgTag = Regex(
        "<img\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val reLink = Regex(
        """<a\b[^>]*\bhref\s*=\s*(["'])(https?://4pda\.to/(?:(?!\1).)*?/(\d+)/(?:(?!\1).)*?)\1[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val reCommentsCount = Regex(
        """class\s*=\s*(["'])(?:(?!\1).)*\bv-count\b(?:(?!\1).)*\1[^>]*?>\s*(\d+)\s*</a>""",
        RegexOption.IGNORE_CASE
    )
    private val reDate = Regex(
        """class\s*=\s*(["'])(?:(?!\1).)*\bdate\b(?:(?!\1).)*\1[^>]*?>([^<]*?)</(?:em|time|span)>""",
        RegexOption.IGNORE_CASE
    )
    private val reAuthor = Regex(
        """showuser=(\d+)(?:&[^"']*)?(["'])[^>]*?>([^<]*?)</a>""",
        RegexOption.IGNORE_CASE
    )
    private val reDescription = Regex(
        """itemprop\s*=\s*(["'])description\1[^>]*?>\s*(?:<p>)?([\s\S]*?)(?:</p>)?\s*</div>""",
        RegexOption.IGNORE_CASE
    )
    private val reTagsMeta = Regex(
        """<div[^>]*?class\s*=\s*(["'])(?:(?!\1).)*\bmeta\b(?:(?!\1).)*\1[^>]*?>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE
    )

    private fun parseArticlesFallback(response: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        for (articleMatch in reArticleBlock.findAll(response)) {
            val block = articleMatch.value
            val articleId = articleMatch.groupValues[2].toIntOrNull() ?: continue
            val item = NewsItem()
            item.id = articleId

            reLink.find(block)?.let { m ->
                item.url = m.groupValues[2]
                item.title = getAttribute(m.value, "title")
                        ?: extractListFallbackTitle(block)
                        ?: m.groupValues[2].substringBeforeLast('/').substringAfterLast('/').articleFromHtml()
            }
            item.imgUrl = selectArticleImageUrl(block, null)
            item.commentsCount = resolveListItemCommentsCount(block)
            reDate.find(block)?.let { m ->
                item.date = m.groupValues[2]
            }
            reAuthor.find(block)?.let { m ->
                item.authorId = m.groupValues[1].toIntOrNull() ?: 0
                item.author = m.groupValues[3].articleFromHtml()
            }
            reDescription.find(block)?.let { m ->
                item.description = m.groupValues[2].trim().articleFromHtml()
            }
            reTagsMeta.find(block)?.let { m ->
                item.tags.addAll(parseTags(m.groupValues[2]))
            }

            if (item.url != null) {
                items.add(item)
            }
        }
        Timber.d("parseArticles: fallback parsed ${items.size} items")
        return items
    }

    private fun extractListFallbackTitle(block: String): String? =
        Regex("""(?is)<h[1-6]\b[^>]*>\s*<a\b[^>]*>([\s\S]*?)</a>\s*</h[1-6]>|<a\b[^>]*\bclass\s*=\s*(["'])(?:(?!\2).)*\btitle\b(?:(?!\2).)*\2[^>]*>([\s\S]*?)</a>""")
            .find(block)
            ?.let { match ->
                (match.groups[1]?.value ?: match.groups[3]?.value)
                    ?.articleFromHtml()
                    ?.articleFromHtml()
                    ?.takeIf { it.isNotBlank() }
            }

    private fun selectArticleImageUrl(block: String, fallbackUrl: String?): String? {
        val tag = reImgTag
            .findAll(block)
            .map { it.value }
            .firstOrNull { it.contains("itemprop=\"image\"", ignoreCase = true) }
            ?: reImgTag.find(block)?.value

        if (tag == null) return fallbackUrl

        val srcsetUrl = parseSrcset(
            getAttribute(tag, "srcset") ?: getAttribute(tag, "data-srcset")
        )
        return srcsetUrl
            ?: getAttribute(tag, "data-original")
            ?: getAttribute(tag, "data-full")
            ?: getAttribute(tag, "data-src")
            ?: fallbackUrl
            ?: getAttribute(tag, "src")
    }

    private fun getAttribute(tag: String, name: String): String? {
        val pattern = Regex("""(?is)\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""")
        return pattern.find(tag)?.groupValues?.getOrNull(2).articleFromHtml()
    }

    private fun parseSrcset(srcset: String?): String? {
        if (srcset.isNullOrBlank()) return null
        val candidates = srcset
            .split(',')
            .mapNotNull { rawCandidate ->
                val parts = rawCandidate.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val url = parts.firstOrNull() ?: return@mapNotNull null
                val descriptor = parts.getOrNull(1)
                SrcsetCandidate(url, descriptorWidth(descriptor), descriptorDensity(descriptor))
            }
        if (candidates.isEmpty()) return null

        val widthCandidates = candidates.filter { it.width != null }
        if (widthCandidates.isNotEmpty()) {
            val preferred = widthCandidates
                .filter { it.width in MIN_THUMB_SRCSET_WIDTH..MAX_THUMB_SRCSET_WIDTH }
                .minByOrNull { it.width ?: Int.MAX_VALUE }
            val fallback = widthCandidates
                .filter { (it.width ?: 0) < MIN_THUMB_SRCSET_WIDTH }
                .maxByOrNull { it.width ?: 0 }
                ?: widthCandidates.minByOrNull { it.width ?: Int.MAX_VALUE }
            return (preferred ?: fallback)?.url
        }

        return candidates.maxByOrNull { it.density ?: 1f }?.url
    }

    private fun descriptorWidth(descriptor: String?): Int? {
        if (descriptor == null || !descriptor.endsWith("w", ignoreCase = true)) return null
        return descriptor.dropLast(1).toIntOrNull()
    }

    private fun descriptorDensity(descriptor: String?): Float? {
        if (descriptor == null || !descriptor.endsWith("x", ignoreCase = true)) return null
        return descriptor.dropLast(1).toFloatOrNull()
    }

    private data class SrcsetCandidate(
        val url: String,
        val width: Int?,
        val density: Float?
    )

    fun parseArticle(response: String): DetailsPage = parseArticle(response, ArticleParsePhase.FULL)

    fun parseArticle(response: String, phase: ArticleParsePhase): DetailsPage {
        val startedAt = SystemClock.elapsedRealtime()
        val bodyLen = response.length
        val detected = patternProvider
                .getPattern(scope.scope, ParserPatterns.Articles.detail_detector)
                .matcher(response)
                .mapOnce {
                    if (it.groupCount() < 4) {
                        return@mapOnce "v3_fallback"
                    }
                    val hasV1 = !it.group(1).isNullOrEmpty()
                    val hasV2 = !it.group(2).isNullOrEmpty()
                    val hasV3 = !it.group(3).isNullOrEmpty()
                    val hasV4 = !it.group(4).isNullOrEmpty()
                    when {
                        hasV1 -> "v1"
                        hasV2 -> "v2"
                        hasV3 || hasV4 -> "v3"
                        else -> null
                    }
                }
        val parserVersion = detected ?: "v3_fallback"
        val pageContext = ArticlePageContext(response)
        val page = when (parserVersion) {
            "v1" -> parseArticleV1(pageContext, phase)
            "v2" -> parseArticleV2(pageContext, phase)
            else -> parseArticleV3(pageContext, phase)
        }
        val metrics = ArticleHtmlValidator.measureBody(page.html, page.title, page.imgUrl)
        ArticleParseTrace.log(
                event = "article_parsed",
                articleId = page.id.takeIf { it > 0 },
                parserVersion = parserVersion,
                selectorSuccess = metrics.articleRootFound && page.id > 0 && !page.title.isNullOrBlank(),
                bodyLen = bodyLen,
                titleLen = page.title?.length,
                commentsCount = page.commentsCount,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                reason = if (page.html.isNullOrBlank()) "empty_html" else "ok",
                extra = mapOf(
                        "selector" to parserVersion,
                        "phase" to phase.name,
                        "articleBlocksCount" to metrics.articleBlocksCount,
                        "commentsParsedEagerly" to false,
                        "relatedParsedEagerly" to (phase == ArticleParsePhase.FULL)
                )
        )
        return page
    }

    /** Phase-2 metadata: taxonomy + comment UL (DOM) without re-parsing body. */
    fun enrichArticleMetadata(page: DetailsPage, response: String) {
        val pageContext = ArticlePageContext(response)
        if (page.category == null) {
            page.category = parseArticleTaxonomy(response).category
        }
        if (page.commentsSource.isNullOrBlank()) {
            page.commentsSource = stripCommentForm(extractCommentsUlHtmlFromPage(pageContext))
        }
    }

    fun commentsSourceNeedsDesktopProbe(commentsSource: String?, authUserId: Int): Boolean {
        val source = commentsSource.orEmpty()
        if (source.isBlank() || authUserId <= 0) return false
        if (!source.contains("comment", ignoreCase = true)) return false
        if (source.contains("act=rep", ignoreCase = true) &&
                !source.contains("editcomment", ignoreCase = true) &&
                !source.contains("action=editcomment", ignoreCase = true)) {
            return true
        }
        if (source.contains("comment-list", ignoreCase = true) &&
                source.contains("showuser=$authUserId", ignoreCase = true) &&
                !source.contains("editcomment", ignoreCase = true)) {
            return true
        }
        if (source.contains("comment-list", ignoreCase = true) &&
                source.contains("data-comment-id", ignoreCase = true) &&
                Regex("""showuser=(\d+)""").findAll(source).none { it.groupValues[1].toIntOrNull() == authUserId }) {
            return !source.contains("showuser=$authUserId", ignoreCase = true)
        }
        return false
    }

    private fun extractCommentsListHtmlFast(response: String): String? {
        if (response.isBlank()) return null
        val match = commentListOpenTagRegex.find(response) ?: return null
        val tag = match.groupValues.getOrNull(1)?.lowercase().orEmpty()
        if (tag != "ul" && tag != "ol") return null
        val start = match.range.first
        val afterOpenTagIndex = match.range.last + 1
        return extractBalancedTagBlock(
                html = response,
                tagName = tag,
                startIndex = start,
                afterOpenTagIndex = afterOpenTagIndex
        )
    }

    private fun extractBalancedTagBlock(
            html: String,
            tagName: String,
            startIndex: Int,
            afterOpenTagIndex: Int
    ): String? {
        if (startIndex < 0 || afterOpenTagIndex <= startIndex) return null
        val openTag = "<$tagName"
        val closeTag = "</$tagName"
        var depth = 1
        var i = afterOpenTagIndex
        val len = html.length
        while (i < len) {
            val nextOpen = html.indexOf(openTag, i, ignoreCase = true)
            val nextClose = html.indexOf(closeTag, i, ignoreCase = true)
            if (nextClose < 0) return null
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++
                i = nextOpen + openTag.length
                continue
            }
            depth--
            val closeEnd = html.indexOf('>', nextClose)
            if (closeEnd < 0) return null
            i = closeEnd + 1
            if (depth == 0) {
                return html.substring(startIndex, i)
            }
        }
        return null
    }

    private fun resolveCommentsSource(pageContext: ArticlePageContext, phase: ArticleParsePhase, regexFallback: String?): String? {
        // Phase-1 must not walk/extract the full comment-list UL: mobile pages often ship a
        // server-rendered list (100k+ chars) which blocked first paint when parsed eagerly.
        if (phase != ArticleParsePhase.FIRST_RENDER) {
            val fast = extractCommentsListHtmlFast(pageContext.response)
            if (!fast.isNullOrBlank()) {
                return stripCommentForm(fast)
            }
        }
        if (phase == ArticleParsePhase.FULL) {
            return stripCommentForm(extractCommentsUlHtmlFromPage(pageContext))
        }
        return stripCommentForm(regexFallback)
    }

    fun hasNewsPollMarkup(html: String?): Boolean =
            !html.isNullOrBlank() && isNewsPollBlock(html)

    fun hasNewsPollMarkers(html: String?): Boolean =
            !html.isNullOrBlank() && hasNewsPollMarkersInternal(html)

    fun hasWeakNewsPollMarker(html: String?): Boolean = hasNewsPollMarkers(html)

    fun hasRealNewsPollMarkup(html: String?): Boolean = hasNewsPollMarkup(html)

    fun hasRawTemplatePollMarker(html: String?): Boolean =
            !html.isNullOrBlank() && containsRawPollTemplate(html)

    fun hasNormalizedNewsPollBlock(html: String?): Boolean =
            !html.isNullOrBlank() && isNormalizedNewsPollBlock(html)

    fun hasFallbackNewsPollBlock(html: String?): Boolean =
            !html.isNullOrBlank() && isFallbackNewsPollBlock(html)

    fun hasForcedFallbackNewsPollBlock(html: String?): Boolean =
            !html.isNullOrBlank() && html.contains("data-forced-fallback-poll", ignoreCase = true)

    fun hasRenderableNewsPollMarkup(html: String?): Boolean =
            hasNormalizedNewsPollBlock(html) || hasFallbackNewsPollBlock(html)

    fun hasCommentReputationActions(source: String?): Boolean {
        if (source.isNullOrBlank() || !source.contains("act=rep", ignoreCase = true)) return false
        return parseCommentsForProbe(source).children.any { it.hasReputationActionsDeep() }
    }

    fun hasCommentOwnModerationActions(source: String?): Boolean =
            !source.isNullOrBlank() &&
                    parseCommentsForProbe(source).children.any { it.hasOwnModerationActionsDeep() }

    fun hasCommentEditActions(source: String?): Boolean =
            !source.isNullOrBlank() &&
                    parseCommentsForProbe(source).children.any { it.hasEditActionsDeep() }

    fun authorCommentsMissingOwnModeration(source: String?, authUserId: Int): Boolean {
        if (authUserId <= 0 || source.isNullOrBlank()) return false
        return parseCommentsForProbe(source).flattenComments()
                .any { it.userId == authUserId && it.id > 0 && !it.hasActionableOwnModeration() }
    }

    fun mobileCommentsIncludeAuthUser(source: String?, authUserId: Int): Boolean {
        if (authUserId <= 0 || source.isNullOrBlank()) return false
        return parseCommentsForProbe(source).flattenComments()
                .any { it.userId == authUserId && it.id > 0 }
    }

    fun hasAnyComments(source: String?): Boolean =
            !source.isNullOrBlank() &&
                    parseCommentsForProbe(source).flattenComments().any { it.id > 0 }

    fun hasCommentsWithMissingAuthorId(source: String?): Boolean =
            !source.isNullOrBlank() &&
                    parseCommentsForProbe(source).flattenComments().any { it.id > 0 && it.userId == 0 }

    fun authorHasOwnModerationActions(source: String?, authUserId: Int): Boolean {
        if (authUserId <= 0 || source.isNullOrBlank()) return false
        return parseCommentsForProbe(source).flattenComments()
                .any { it.userId == authUserId && it.id > 0 && it.hasActionableOwnModeration() }
    }

    fun applyFallbackOwnCommentActions(root: Comment, authUserId: Int) {
        if (authUserId <= 0) return
        root.flattenComments().forEach { comment ->
            if (comment.userId != authUserId || comment.id <= 0) return@forEach
            if (comment.actions.edit?.hasInlineEditPayload() == true) return@forEach
            if (comment.actions.edit?.isActionableModeration() != true) {
                comment.actions.edit = buildFallbackEditCommentAction(comment.id)
            }
            if (comment.actions.delete?.isActionableModeration() != true) {
                comment.actions.delete = buildFallbackDeleteCommentAction(comment.id)
            }
        }
    }

    fun buildFallbackEditCommentAction(commentId: Int): Comment.Action =
            Comment.Action(
                    url = "https://4pda.to/wp-admin/admin-ajax.php?action=editcomment&c=$commentId",
                    type = Comment.Action.Type.EDIT
            )

    fun buildFallbackDeleteCommentAction(commentId: Int): Comment.Action =
            Comment.Action(
                    url = "https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment&c=$commentId",
                    type = Comment.Action.Type.DELETE,
                    requiresConfirmation = true
            )

    fun extractCommentsSourceFromPage(response: String): String? {
        val normalized = ArticleResponseBody.normalize(response).orEmpty()
        if (normalized.isBlank()) return null
        return ensureBalancedCommentsHtml(normalized, stripReplyForm = true)
    }

    /** Re-extracts a balanced comment-list block from a partial or regex-truncated fragment. */
    fun ensureBalancedCommentsHtml(source: String?, stripReplyForm: Boolean = false): String? {
        val normalized = ArticleResponseBody.normalize(source).orEmpty()
        if (normalized.isBlank()) return null
        val balanced = extractCommentsListHtmlFast(normalized)
                ?: extractCommentsUlHtmlFromPage(ArticlePageContext(normalized))
                ?: normalized.takeIf { it.contains("comment", ignoreCase = true) }
        if (balanced.isNullOrBlank()) return null
        return if (stripReplyForm) stripCommentForm(balanced) else balanced
    }

    fun mergeCommentDesktopActions(primary: Comment, desktopSource: String?): Comment {
        if (desktopSource.isNullOrBlank() || primary.children.isEmpty()) return primary
        val desktop = parseComments(SparseArray(), desktopSource)
        val desktopById = desktop.flattenComments()
                .filter {
                    it.id > 0 && (
                            it.hasDesktopSupplementalActions() ||
                                    it.actions.like?.isValid() == true ||
                                    it.actions.unlike?.isValid() == true
                            )
                }
                .groupBy { it.id }
        val desktopByAuthorDate = desktop.flattenComments()
                .filter {
                    it.userId > 0 && !it.date.isNullOrBlank() && (
                            it.hasDesktopSupplementalActions() ||
                                    it.actions.like?.isValid() == true ||
                                    it.actions.unlike?.isValid() == true
                            )
                }
                .groupBy { AuthorDateKey(it.userId, normalizeCommentDate(it.date)) }
                .filterValues { it.size == 1 }

        primary.mergeDesktopActions(
                byId = desktopById,
                byAuthorDate = desktopByAuthorDate
        )
        return primary
    }

    fun mergeCommentReputationActions(primary: Comment, desktopSource: String?): Comment =
            mergeCommentDesktopActions(primary, desktopSource)

    fun extractNormalizedPollBlock(response: String, pollId: String? = null): String? =
            extractDataSitePollBlock(response)?.let { return it }
                    ?: extractNewsPollBlock(ArticlePageContext(response))
                            ?.takeIf { block ->
                                pollId.isNullOrBlank() ||
                                        pollIdFromText(block) == pollId ||
                                        block.contains("poll_id=$pollId", ignoreCase = true)
                            }

    fun countNewsPollOptions(html: String?): Int =
            html.orEmpty()
                    .let { source ->
                        runCatching { Parser.parse(source) }
                                .getOrNull()
                                ?.let { collectAnswerInputCount(it) }
                                ?: answerInputRegex.findAll(source).count()
                    }

    fun hasRejectedRelatedNewsPollOptions(html: String?): Boolean =
            !html.isNullOrBlank() && containsRejectedRenderedPollList(html)

    fun appendPollFromResponse(article: DetailsPage, response: String): DetailsPage {
        if (hasNormalizedNewsPollBlock(article.html) || hasFallbackNewsPollBlock(article.html)) {
            return article
        }
        val pollBlock = extractNewsPollBlock(ArticlePageContext(response))
        if (pollBlock == null) {
            val forcedFallback = forcedFallbackNewsPollBlock(
                    response = response,
                    articleId = article.id,
                    articleTitle = article.title,
                    currentHtml = article.html
            )
            if (forcedFallback != null) {
                article.html = appendOrReplaceFallbackPollBlock(article.html, forcedFallback)
                return article
            }
            return article
        }
        val currentHtml = if (hasFallbackNewsPollBlock(article.html)) {
            article.html.orEmpty().replace(newsPollFallbackBlockRegex, "")
        } else {
            article.html.orEmpty()
        }
        article.html = listOf(currentHtml, pollBlock)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        return article
    }

    private fun parseArticleV1(pageContext: ArticlePageContext, phase: ArticleParsePhase): DetailsPage = patternProvider
            .getPattern(scope.scope, scope.detail)
            .matcher(pageContext.response)
            .mapOnce { matcher ->
                DetailsPage().apply {
                    val extractedContent = resolveArticleBodyContent(pageContext, phase, matcher.group(10))
                    id = matcher.group(1).orEmpty().toIntOrNull() ?: 0
                    imgUrl = selectArticleHeroImage(pageContext.response, matcher.group(3), extractedContent.html)
                    title = matcher.group(4).orEmpty().articleFromHtml()
                    date = matcher.group(6)
                    authorId = matcher.group(7).orEmpty().toIntOrNull() ?: 0
                    author = matcher.group(8).orEmpty().articleFromHtml()
                    commentsCount = resolveArticleCommentsCount(pageContext.response, matcher.group(9))
                    html = appendPollBlock(extractedContent.html, pageContext, id, title, phase)
                    matcher.group(11)?.also {
                        materials.addAll(parseMaterials(it))
                    }
                    if (phase == ArticleParsePhase.FULL) {
                        val taxonomy = parseArticleTaxonomy(pageContext.response, matcher.group(5))
                        category = taxonomy.category
                    }
                    navId = matcher.group(12)

                    karmaMap = if (phase == ArticleParsePhase.FULL) parseKarma(pageContext.response) else SparseArray()

                    commentsSource = resolveCommentsSource(pageContext, phase, matcher.group(13))

                    /*Comment commentTree = parseComments(getKarmaMap(), getCommentsSource());
                    setCommentTree(commentTree);*/
                }
            } ?: throw Exception("Not found article by pattern v1")

    private fun parseArticleV2(pageContext: ArticlePageContext, phase: ArticleParsePhase): DetailsPage = patternProvider
            .getPattern(scope.scope, scope.detail_v2)
            .matcher(pageContext.response)
            .mapOnce { matcher ->
                DetailsPage().apply {
                    val extractedContent = resolveArticleBodyContent(pageContext, phase, matcher.group(6))
                    id = matcher.group(1).orEmpty().toIntOrNull() ?: 0

                    patternProvider
                            .getPattern(ParserPatterns.Global.scope, ParserPatterns.Global.meta_tags)
                            .matcher(pageContext.response)
                            .findAll {
                                val metaTarget = it.group(1)
                                val metaType = it.group(2)
                                val metaContent = it.group(3)
                                if (metaTarget == "og" && metaType == "image") {
                                    imgUrl = metaContent
                                }
                            }
                    imgUrl = selectArticleHeroImage(pageContext.response, imgUrl, extractedContent.html)

                    //imgUrl = matcher.group(3)
                    title = matcher.group(3).orEmpty().articleFromHtml()
                    date = matcher.group(4)

                    //Дефолтный юзер с ником News
                    authorId = 204809
                    author = "News"

                    commentsCount = resolveArticleCommentsCount(pageContext.response, matcher.group(5))
                    html = appendPollBlock(extractedContent.html, pageContext, id, title, phase)
                    if (phase == ArticleParsePhase.FULL) {
                        val taxonomy = parseArticleTaxonomy(pageContext.response, matcher.group(7))
                        category = taxonomy.category
                    }
                    matcher.group(8)?.also {
                        materials.addAll(parseMaterials(it))
                    }
                    navId = matcher.group(9)

                    karmaMap = if (phase == ArticleParsePhase.FULL) parseKarma(pageContext.response) else SparseArray()

                    commentsSource = resolveCommentsSource(pageContext, phase, matcher.group(10))

                    /*Comment commentTree = parseComments(getKarmaMap(), getCommentsSource());
                    setCommentTree(commentTree);*/
                }
            } ?: parseArticleV3(pageContext, phase) // Fallback to V3 if V2 fails

    /**
     * Universal parser that doesn't depend on data-ztm attribute.
     * Uses meta tags and common article structure.
     */
    private fun parseArticleV3(pageContext: ArticlePageContext, phase: ArticleParsePhase): DetailsPage {
        val response = pageContext.response
        // Extract basic info from meta tags
        var ogTitle: String? = null
        var ogImage: String? = null
        var ogDescription: String? = null
        var articleId = 0

        // Parse meta tags
        patternProvider
                .getPattern(ParserPatterns.Global.scope, ParserPatterns.Global.meta_tags)
                .matcher(response)
                .findAll {
                    val metaTarget = it.group(1)
                    val metaType = it.group(2)
                    val metaContent = it.group(3)
                    when {
                        metaTarget == "og" && metaType == "title" -> ogTitle = metaContent
                        metaTarget == "og" && metaType == "image" -> ogImage = metaContent
                        metaTarget == "og" && metaType == "description" -> ogDescription = metaContent
                    }
                }

        articleId = articleIdFromResponse(response) ?: 0

        // Try to find title in h1 if not in meta
        val title = ogTitle ?: run {
            val h1Pattern = Regex("<h1[^>]*>(?:<span[^>]*>)?([^<]+)(?:</span>)?</h1>", RegexOption.IGNORE_CASE)
            h1Pattern.find(response)?.groupValues?.get(1)?.trim() ?: ""
        }

        // Find article content - try multiple patterns
        val extractedContent = extractArticleContent(pageContext, phase)
        val content = extractedContent.html

        // Find date
        val date = run {
            val timePattern = Regex("<time[^>]*>([^<]+)</time>", RegexOption.IGNORE_CASE)
            timePattern.find(response)?.groupValues?.get(1)
                ?: Regex("<em[^>]*class=\"[^\"]*date[^\"]*\"[^>]*>([^<]+)</em>", RegexOption.IGNORE_CASE)
                    .find(response)?.groupValues?.get(1)
        }

        val commentsCount = resolveArticleCommentsCount(response)

        return DetailsPage().apply {
            id = articleId
            this.title = title.articleFromHtml()
                    imgUrl = selectArticleHeroImage(response, ogImage, extractedContent.html)
            this.date = date
            this.commentsCount = commentsCount
            authorId = 204809
            author = "News"
            html = appendPollBlock(content, pageContext, id, this.title, phase)
            karmaMap = if (phase == ArticleParsePhase.FULL) parseKarma(response) else SparseArray()
            commentsSource = resolveCommentsSource(pageContext, phase, null)
            if (phase == ArticleParsePhase.FULL) {
                val taxonomy = parseArticleTaxonomy(response)
                category = taxonomy.category
            }
        }
    }

    /** News list cards: same scoped extractor as article detail, without regex candidate max(). */
    fun resolveListItemCommentsCount(block: String): Int =
            extractArticleCommentsCount(block)?.takeIf { it >= 0 } ?: 0

    private fun resolveArticleCommentsCount(response: String, regexCandidate: String? = null): Int {
        // The article's own counter badge (head-comments-count / article-meta-comment) is the only
        // reliable source: page-wide #comments / v-count scans also match the related/popular-news
        // widgets and inflate the count to another article's value (e.g. 0-comment article showing
        // "345"). When the own badge is present, trust it verbatim instead of max()-ing heuristics.
        extractOwnArticleCommentsCount(response)?.let { return it }
        val candidates = mutableListOf<Int>()
        regexCandidate?.toIntOrNull()?.let { if (it >= 0) candidates += it }
        extractArticleCommentsCount(response)?.let { if (it >= 0) candidates += it }
        return candidates.maxOrNull() ?: UNKNOWN_COMMENTS_COUNT
    }

    /** Article-detail entry point for tests: resolved comment count for the page being viewed. */
    fun resolveArticleCommentsCountForPage(response: String): Int =
            resolveArticleCommentsCount(response, null)

    /**
     * Reads the current article's own comment-count badge, which is unique to the viewed article
     * (related/popular widgets use `v-count` instead). Returns null when neither badge is present,
     * so callers fall back to the broader heuristics.
     */
    private fun extractOwnArticleCommentsCount(response: String): Int? {
        headCommentsCountRegex.find(response)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?.let { return it }
        return articleMetaCommentCountRegex.find(response)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.takeIf { it >= 0 }
    }

    private fun extractArticleCommentsCount(response: String): Int? {
        val scoped = articleCountScopeRegexes
                .asSequence()
                .flatMap { it.findAll(response).asSequence().map { match -> match.value } }
                .mapNotNull { scope -> extractCommentsCountFromScope(scope) }
                .maxOrNull()
        val structured = extractStructuredCommentsCount(response)
        val global = extractCommentsCountFromScope(response)
        // JSON-LD / meta / v-count can exceed visible #comments anchors (deleted, paginated, stale).
        // Prefer scoped anchor counts when outer metadata is higher — matches DOM parse totals.
        val inflated = listOfNotNull(structured, global).maxOrNull()
        if (scoped != null && inflated != null && inflated > scoped) {
            return scoped
        }
        return listOfNotNull(scoped, structured, global).maxOrNull()
    }

    private fun extractCommentsCountFromScope(source: String): Int? {
        val anchorCounts = commentsAnchorCountRegex.findAll(source)
                .mapNotNull { it.groupValues.getOrNull(2)?.toIntOrNull() }
                .maxOrNull()
        if (anchorCounts != null) return anchorCounts
        return commentsClassCountRegex.findAll(source)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .maxOrNull()
    }

    private fun extractStructuredCommentsCount(source: String): Int? {
        val metaCounts = commentsMetaCountRegex.findAll(source)
                .mapNotNull { it.groupValues.getOrNull(3)?.toIntOrNull() }
        val jsonCounts = commentsJsonLdCountRegex.findAll(source)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        return (metaCounts + jsonCounts).maxOrNull()
    }

    private fun selectArticleHeroImage(response: String, candidate: String?, contentHtml: String?): String? {
        val normalizedCandidate = normalizeArticleImageUrl(
                selectArticleHeroCandidate(response, candidate),
                response
        )?.takeIf { it.isNotBlank() }
                ?: return null
        val firstBodyImage = firstBodyImageUrl(contentHtml)
                ?.let { normalizeArticleImageUrl(it, response) }
        val inlineArticleHero = firstBodyLightboxImageUrl(contentHtml)
                ?.let { normalizeArticleImageUrl(it, response) }
        val hasInlineLightbox = contentHtml.orEmpty().let { html ->
            html.contains("data-lightbox=\"post-", ignoreCase = true) ||
                    html.contains("data-lightbox='post-", ignoreCase = true)
        }
        val responseFirstImage = firstBodyImageUrl(response)
                ?.let { normalizeArticleImageUrl(it, response) }
        val responseHasPostLightbox = response.contains("data-lightbox=\"post-", ignoreCase = true) ||
                response.contains("data-lightbox='post-", ignoreCase = true)
        return normalizedCandidate.takeUnless {
            urlsReferToSameImage(it, firstBodyImage) ||
                    inlineArticleHero != null ||
                    hasInlineLightbox ||
                    (responseHasPostLightbox && urlsReferToSameImage(it, responseFirstImage))
        }
    }

    private fun selectArticleHeroCandidate(response: String, candidate: String?): String? =
            candidate?.takeIf { it.isNotBlank() }
                    ?: extractMetaImageCandidate(response)
                    ?: extractHeaderImageCandidate(response)

    private fun extractMetaImageCandidate(response: String): String? {
        val metaImages = linkedMapOf<String, String>()
        metaImageRegex.findAll(response).forEach { match ->
            val key = match.groupValues.getOrNull(2).orEmpty().lowercase()
            val value = match.groupValues.getOrNull(4).orEmpty().articleFromHtml().orEmpty()
            if (key.isNotBlank() && value.isNotBlank()) {
                metaImages.putIfAbsent(key, value)
            }
        }
        return metaImages["og:image"]
                ?: metaImages["twitter:image"]
                ?: metaImages["twitter:image:src"]
    }

    private fun extractHeaderImageCandidate(response: String): String? {
        heroImageContainerRegex.findAll(response).forEach { match ->
            selectImageUrlFromHtml(match.value)?.let { return it }
        }
        articleHeaderRegex.find(response)?.value?.let { header ->
            selectImageUrlFromHtml(header)?.let { return it }
        }
        return null
    }

    private fun firstBodyImageUrl(contentHtml: String?): String? =
            articleLightboxImageRegex.find(contentHtml.orEmpty())?.value?.let { selectArticleImageUrl(it, null) }
                    ?: firstImageTagRegex.find(contentHtml.orEmpty())?.value?.let { selectArticleImageUrl(it, null) }

    private fun firstBodyLightboxImageUrl(contentHtml: String?): String? =
            articleLightboxImageRegex.find(contentHtml.orEmpty())?.value?.let { selectArticleImageUrl(it, null) }

    private fun selectImageUrlFromHtml(html: String): String? {
        firstSourceTagRegex.find(html)?.value?.let { source ->
            parseSrcset(getAttribute(source, "srcset") ?: getAttribute(source, "data-srcset"))
                    ?.let { return it }
            getAttribute(source, "data-src")?.let { return it }
            getAttribute(source, "src")?.let { return it }
        }
        return firstImageTagRegex.find(html)?.value?.let { selectArticleImageUrl(it, null) }
    }

    private fun normalizeArticleImageUrl(rawUrl: String?, response: String): String? {
        val url = rawUrl?.articleFromHtml()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("http://", ignoreCase = true) -> url.replaceFirst("http://", "https://")
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://4pda.to$url"
            else -> articleUrlFromResponse(response)
                    ?.substringBeforeLast('/')
                    ?.let { "$it/$url" }
        }
    }

    private fun urlsReferToSameImage(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        fun key(url: String): String =
                url.substringBefore('?')
                        .substringBefore('#')
                        .replace(Regex("""-\d+x\d+(?=\.[a-zA-Z0-9]+$)"""), "")
                        .lowercase()
        return key(first) == key(second)
    }

    /**
     * Phase-1 tap-to-open: prefer detail-regex / lightweight regex extraction and skip full-page
     * [Parser.parse] — mobile article HTML is ~350KB and DOM walks blocked first paint (~3.5s in logs).
     */
    private fun resolveArticleBodyContent(
            pageContext: ArticlePageContext,
            phase: ArticleParsePhase,
            regexFallback: String? = null
    ): ArticleContent {
        regexFallback?.trim()?.takeIf { it.isNotBlank() }?.let { return ArticleContent(it, false) }
        extractArticleContentByRegex(pageContext)?.let { return it }
        if (phase == ArticleParsePhase.FIRST_RENDER) {
            return ArticleContent(null, false)
        }
        extractOrderedArticleContent(pageContext)?.let { return it }
        return ArticleContent(null, false)
    }

    private fun extractArticleContent(
            pageContext: ArticlePageContext,
            phase: ArticleParsePhase = ArticleParsePhase.FULL
    ): ArticleContent = resolveArticleBodyContent(pageContext, phase)

    private fun extractArticleContentByRegex(pageContext: ArticlePageContext): ArticleContent? {
        for (pattern in articleContentRegexes) {
            pattern.find(pageContext.response)?.groupValues?.get(1)?.let { body ->
                if (body.isNotBlank()) return ArticleContent(body, false)
            }
        }
        return null
    }

    private fun extractOrderedArticleContent(pageContext: ArticlePageContext): ArticleContent? {
        val document = pageContext.documentOrNull() ?: return null
        val article = findArticleNode(document) ?: return null
        val blocks = mutableListOf<String>()
        val seenTextBlocks = mutableSetOf<String>()
        var hasInlineHeroMedia = false

        for (child in article.getNodes()) {
            if (Parser.isTextNode(child)) {
                appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                continue
            }
            if (Parser.isNotElement(child)) continue
            if (isArticleFooterNode(child)) continue
            if (isArticleHeaderNode(child)) {
                appendArticleHeaderLead(child, blocks, seenTextBlocks)
                continue
            }
            if (shouldSkipArticleChild(child)) continue

            when {
                isArticleBodyNode(child) -> {
                    if (appendArticleBodyChildren(child, blocks, seenTextBlocks)) {
                        hasInlineHeroMedia = true
                    }
                }
                isLeadNode(child) || isArticleMediaNode(child) -> {
                    if (isArticleMediaNode(child)) hasInlineHeroMedia = true
                    appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                }
                hasArticleContentMarker(child) -> {
                    appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                }
            }
        }

        return blocks
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n")
                ?.let { ArticleContent(it, hasInlineHeroMedia) }
    }

    private fun appendArticleBodyChildren(
            node: Node,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ): Boolean {
        var hasInlineMedia = false
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) {
                appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                return@forEach
            }
            if (shouldSkipArticleBodyChild(child)) return@forEach
            if (isArticleMediaNode(child)) hasInlineMedia = true
            appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
        }
        return hasInlineMedia
    }

    private fun appendArticleHeaderLead(
            node: Node,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ) {
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) return@forEach
            if (isArticleAnonsNode(child) || isLeadNode(child)) {
                appendArticleLeadChildren(child, blocks, seenTextBlocks)
            }
        }
    }

    private fun appendArticleLeadChildren(
            node: Node,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ) {
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) {
                appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
                return@forEach
            }
            if (isArticleMetaNode(child)) return@forEach
            appendArticleBlock(Parser.getHtml(child, false), blocks, seenTextBlocks)
        }
    }

    private fun appendArticleBlock(
            html: String,
            blocks: MutableList<String>,
            seenTextBlocks: MutableSet<String>
    ) {
        val trimmed = html.trim()
        if (trimmed.isBlank()) return
        val textKey = normalizeArticleText(trimmed)
        if (textKey.isNotBlank() && !seenTextBlocks.add(textKey)) return
        blocks += trimmed
    }

    private fun findArticleNode(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null
        if (isMainArticleNode(node)) return node
        node.getNodes().forEach { child ->
            findArticleNode(child)?.let { return it }
        }
        return null
    }

    private fun isMainArticleNode(node: Node): Boolean {
        val tag = node.name.orEmpty()
        val classes = node.getAttribute("class").orEmpty()
        if (!tag.equals("article", ignoreCase = true) &&
                !hasClassMarker(classes, listOf("article"))) {
            return false
        }
        if (hasClassMarker(classes, listOf("article-header", "article-footer"))) return false
        return hasDescendant(node) { child ->
            isArticleBodyNode(child) || isLeadNode(child) || isArticleMediaNode(child)
        }
    }

    private fun hasDescendant(node: Node, predicate: (Node) -> Boolean): Boolean {
        node.getNodes().forEach { child ->
            if (Parser.isNotElement(child)) return@forEach
            if (predicate(child) || hasDescendant(child, predicate)) return true
        }
        return false
    }

    private fun isArticleBodyNode(node: Node): Boolean {
        val classes = node.getAttribute("class").orEmpty()
        return hasClassMarker(classes, articleBodyClassMarkers) ||
                node.getAttribute("itemprop").orEmpty().equals("articleBody", ignoreCase = true)
    }

    private fun isLeadNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), leadClassMarkers)

    private fun isArticleHeaderNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-header"))

    private fun isArticleAnonsNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-anons"))

    private fun isArticleMetaNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-meta"))

    private fun isArticleMediaNode(node: Node): Boolean {
        val tag = node.name.orEmpty()
        if (tag.equals("figure", ignoreCase = true) ||
                tag.equals("picture", ignoreCase = true) ||
                tag.equals("video", ignoreCase = true) ||
                tag.equals("iframe", ignoreCase = true) ||
                tag.equals("embed", ignoreCase = true) ||
                tag.equals("object", ignoreCase = true) ||
                tag.equals("oembed", ignoreCase = true) ||
                tag.equals("img", ignoreCase = true)) {
            return true
        }
        val classes = node.getAttribute("class").orEmpty()
        if (hasClassMarker(classes, articleMediaClassMarkers)) {
            return true
        }
        return findYoutubeUrlInNode(node) != null
    }

    private fun hasArticleContentMarker(node: Node): Boolean {
        val tag = node.name.orEmpty()
        if (tag.equals("p", ignoreCase = true) ||
                tag.equals("blockquote", ignoreCase = true) ||
                contentHeadingTagNames.any { tag.equals(it, ignoreCase = true) } ||
                tag.equals("pre", ignoreCase = true) ||
                tag.equals("hr", ignoreCase = true) ||
                tag.equals("ul", ignoreCase = true) ||
                tag.equals("ol", ignoreCase = true)) {
            return normalizeArticleText(Parser.getHtml(node, true)).isNotBlank()
        }
        return false
    }

    private fun isArticleFooterNode(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), listOf("article-footer"))

    private fun shouldSkipArticleChild(node: Node): Boolean =
            hasClassMarker(node.getAttribute("class").orEmpty(), skippedArticleClassMarkers) ||
                    node.name.orEmpty().equals("meta", ignoreCase = true) ||
                    node.name.orEmpty().equals("script", ignoreCase = true) ||
                    node.name.orEmpty().equals("style", ignoreCase = true)

    private fun shouldSkipArticleBodyChild(node: Node): Boolean {
        if (shouldSkipArticleChild(node) || isArticleHeaderNode(node)) return true
        val tag = node.name.orEmpty()
        val classes = node.getAttribute("class").orEmpty()
        return tag.equals("time", ignoreCase = true) ||
                tag.equals("h1", ignoreCase = true) ||
                hasArticleBodyMetaClass(classes)
    }

    private fun findYoutubeUrlInNode(node: Node): String? {
        if (Parser.isNotElement(node)) return null
        val tag = node.name.orEmpty()
        if (tag.equals("iframe", ignoreCase = true) || tag.equals("embed", ignoreCase = true)) {
            listOf("src", "data-src").forEach { attr ->
                node.getAttribute(attr)
                        ?.articleFromHtml()
                        ?.takeIf { extractYoutubeVideoId(it) != null }
                        ?.let { return it }
            }
        }
        if (tag.equals("oembed", ignoreCase = true)) {
            node.getAttribute("url")
                    ?.articleFromHtml()
                    ?.takeIf { extractYoutubeVideoId(it) != null }
                    ?.let { return it }
        }
        if (tag.equals("a", ignoreCase = true)) {
            node.getAttribute("href")
                    ?.articleFromHtml()
                    ?.takeIf { extractYoutubeVideoId(it) != null }
                    ?.let { return it }
        }
        node.getNodes().forEach { child ->
            findYoutubeUrlInNode(child)?.let { return it }
        }
        return null
    }

    private fun hasClassMarker(classes: String, markers: List<String>): Boolean {
        val normalizedClasses = classes.lowercase()
        return markers.any { marker -> normalizedClasses.contains(marker.lowercase()) }
    }

    private fun hasArticleBodyMetaClass(classes: String): Boolean {
        val tokens = classes
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .toSet()
        return articleBodyMetaClassMarkers.any { marker -> marker.lowercase() in tokens }
    }

    private fun normalizeArticleText(html: String): String =
            cleanPollText(html)
                    .replace('\u00A0', ' ')
                    .replace(whitespaceRegex, " ")
                    .trim()
                    .lowercase()

    private data class ArticleContent(
            val html: String?,
            val hasInlineHeroMedia: Boolean
    )

    internal data class ArticleTaxonomy(
            val category: Tag?
    )

    private fun appendPollBlock(
            content: String?,
            pageContext: ArticlePageContext,
            articleId: Int,
            articleTitle: String?,
            phase: ArticleParsePhase = ArticleParsePhase.FULL
    ): String? {
        val safeContent = stripRawPollTemplates(normalizeSponsoredArticleLinks(content))
        val extractedPollBlock = if (hasNewsPollMarkersInternal(pageContext.response) ||
                containsRawPollTemplate(pageContext.response)) {
            extractNewsPollBlock(pageContext, phase)
        } else {
            null
        }
        val pollBlock = extractedPollBlock
                ?: forcedFallbackNewsPollBlock(pageContext.response, articleId, articleTitle, safeContent)
                ?: run {
                    if (BuildConfig.DEBUG && (hasNewsPollMarkersInternal(pageContext.response) ||
                                    containsRawPollTemplate(pageContext.response))) {
                        FpdaDebugLog.warn(
                                FpdaDebugLog.TAG_ARTICLE_POLL,
                                "poll_extraction_failed",
                                mapOf(
                                        "articleId" to articleId,
                                        "hasMarkers" to hasNewsPollMarkersInternal(pageContext.response),
                                        "hasRawTemplate" to containsRawPollTemplate(pageContext.response),
                                        "hasPollFrame" to pollFrameMarkerRegex.containsMatchIn(pageContext.response),
                                        "responseSizeBytes" to pageContext.response.length
                                )
                        )
                    }
                    return safeContent
                }
        if (BuildConfig.DEBUG) {
            val dataSitePollCount = dataSitePollAttrRegex.findAll(pageContext.response).count()
            val normalizedPollCount = pollBlock.split("data-normalized-poll", ignoreCase = true).size - 1
            val pollKind = when {
                extractedPollBlock != null && extractedPollBlock.contains("data-normalized-poll", ignoreCase = true) ->
                        "normalized"
                extractedPollBlock != null -> "extracted"
                pollBlock.contains("data-forced-fallback-poll", ignoreCase = true) -> "forced_fallback"
                pollBlock.contains("data-poll-fallback", ignoreCase = true) -> "fallback"
                else -> "unknown"
            }
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_ARTICLE_POLL,
                    "poll_appended",
                    mapOf(
                            "articleId" to articleId,
                            "pollKind" to pollKind,
                            "pollBlockLen" to pollBlock.length,
                            "dataSitePollCount" to dataSitePollCount,
                            "normalizedPollCount" to normalizedPollCount.coerceAtLeast(0),
                            "hasToken" to pollBlock.contains("data-news-poll-token", ignoreCase = true),
                            "hasForm" to pollBlock.contains("<form", ignoreCase = true),
                            "optionCount" to countNewsPollOptions(pollBlock)
                    )
            )
            if (dataSitePollCount > 0 && normalizedPollCount < dataSitePollCount) {
                FpdaDebugLog.warn(
                        FpdaDebugLog.TAG_ARTICLE_POLL,
                        "poll_data_site_partial",
                        mapOf(
                                "articleId" to articleId,
                                "dataSitePollCount" to dataSitePollCount,
                                "normalizedPollCount" to normalizedPollCount.coerceAtLeast(0)
                        )
                )
            }
        }
        // When a real poll block was built, remove any server-rendered poll markup that leaked into
        // the body (poll-frame title/buttons, poll-ajax-frame wrapper) so it doesn't render as a bare
        // bold heading or duplicate the normalized poll.
        val cleanedContent = if (extractedPollBlock != null) {
            stripServerRenderedPollFromContent(safeContent)
        } else {
            safeContent
        }
        if (cleanedContent.orEmpty().contains(pollBlock)) return cleanedContent
        return listOf(cleanedContent.orEmpty(), pollBlock)
                .filter { it.isNotBlank() }
                .joinToString("\n")
    }

    private fun normalizeSponsoredArticleLinks(html: String?): String? {
        val source = html ?: return null
        val withVideoCards = normalizeYoutubeEmbeds(source)
        if (!withVideoCards.contains("background", ignoreCase = true)) return withVideoCards
        val withoutWrapper = sponsoredCardWrapperRegex.replace(withVideoCards) { match ->
            normalizeSponsoredArticleLink(match.groupValues.getOrNull(2).orEmpty()) ?: match.value
        }
        return sponsoredCardLinkRegex.replace(withoutWrapper) { match ->
            normalizeSponsoredArticleLink(match.value) ?: match.value
        }
    }

    private fun normalizeSponsoredArticleLink(linkHtml: String): String? {
        val match = sponsoredCardLinkRegex.find(linkHtml) ?: return null
        val href = match.groupValues.getOrNull(2).orEmpty().ifBlank { return null }
        val title = cleanPollText(match.groupValues.getOrNull(5).orEmpty()).ifBlank { return null }
        return """<a href="${articleHtmlEncode(href)}" target="_blank" rel="nofollow">${articleHtmlEncode(title)}</a>"""
    }

    private fun normalizeYoutubeEmbeds(html: String): String {
        val seenVideoIds = linkedSetOf<String>()
        fun buildUniqueCard(rawUrl: String, caption: String?, fallback: String): String {
            val url = rawUrl.articleFromHtml()?.trim().orEmpty()
            val videoId = extractYoutubeVideoId(url) ?: return fallback
            if (!seenVideoIds.add(videoId)) return ""
            return buildYoutubeCard(url, caption) ?: fallback
        }
        var result = youtubeMediaTagRegex.replace(html) { match ->
            val tag = match.value
            val url = youtubeIframeSrcRegex.find(tag)
                    ?.groupValues
                    ?.getOrNull(2)
                    .orEmpty()
            buildUniqueCard(url, null, match.value)
        }
        result = youtubeOembedTagRegex.replace(result) { match ->
            val tag = match.value
            val url = youtubeOembedUrlRegex.find(tag)
                    ?.groupValues
                    ?.getOrNull(2)
                    .orEmpty()
            buildUniqueCard(url, null, match.value)
        }
        return youtubeLinkBlockRegex.replace(result) { match ->
            val url = match.groupValues.getOrNull(4).orEmpty()
            val linkText = cleanPollText(match.groupValues.getOrNull(5).orEmpty())
            buildUniqueCard(
                    url,
                    linkText.takeIf { it.isNotBlank() && !it.equals(url, ignoreCase = true) },
                    match.value
            )
        }
    }

    private fun buildYoutubeCard(rawUrl: String, caption: String?): String? {
        val url = rawUrl.articleFromHtml()?.trim().orEmpty()
        val videoId = extractYoutubeVideoId(url) ?: return null
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&rel=0"
        val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        return buildString {
            append("""<div class="news-video-card" data-video-provider="youtube" data-video-id="""")
            append(articleHtmlEncode(videoId))
            append("""" data-video-embed-url="""")
            append(articleHtmlEncode(embedUrl))
            append("""">""")
            append("""<button type="button" class="news-video-card-preview" data-video-play="true" aria-label="Смотреть видео">""")
            append("""<span class="news-video-card-thumb" style="background-image:url(""")
            append(articleHtmlEncode(thumbnailUrl))
            append(""")"><span class="news-video-card-play">&#9658;</span></span>""")
            append("""<span class="news-video-card-title">Смотреть видео в статье</span>""")
            append("""</button>""")
            append("""<a class="news-video-card-youtube" href="""")
            append(articleHtmlEncode(watchUrl))
            append("""" target="_blank" rel="nofollow noopener">""")
            append("""Открыть в YouTube""")
            append("""</a>""")
            caption?.takeIf { it.isNotBlank() }?.let {
                append("""<div class="news-video-card-caption">""")
                append(articleHtmlEncode(it))
                append("""</div>""")
            }
            append("""</div>""")
        }
    }

    private fun extractYoutubeVideoId(url: String): String? =
            youtubeIdRegexes
                    .asSequence()
                    .mapNotNull { regex -> regex.find(url)?.groupValues?.getOrNull(1) }
                    .firstOrNull()

    private fun extractNewsPollBlock(
            pageContext: ArticlePageContext,
            phase: ArticleParsePhase = ArticleParsePhase.FULL
    ): String? {
        val response = pageContext.response
        extractDataSitePollBlock(response)?.let { return it }

        normalizedVotePollBlock(response, response)?.let { return it }

        normalizedPollFrameBlock(response, response)?.let { return it }

        val regexPollBlock = newsPollFrameRegex
                .findAll(response)
                .mapNotNull { match -> normalizeNewsPollBlock(match.value, response) }
                .firstOrNull { isNewsPollBlock(it) }
        if (regexPollBlock != null) {
            return regexPollBlock
        }

        val formPollBlock = newsPollFormRegex
                .findAll(response)
                .mapNotNull { match -> normalizeNewsPollBlock(match.value, response) }
                .firstOrNull { isNewsPollBlock(it) }
        if (formPollBlock != null) {
            return formPollBlock
        }

        if (phase != ArticleParsePhase.FIRST_RENDER) {
            val parsedPollBlock = try {
                pageContext.documentOrNull()
                        ?.let { findNewsPollNode(it) }
                        ?.let { normalizeNewsPollBlock(Parser.getHtml(it, false), response) }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Timber.w(ex, "extractNewsPollBlock")
                }
                null
            }
            if (parsedPollBlock != null && isNewsPollBlock(parsedPollBlock)) {
                return parsedPollBlock
            }
        }

        return if (containsRawPollTemplate(response)) {
            fallbackNewsPollBlock(response)
        } else {
            null
        }
    }

    private fun findNewsPollNode(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null

        if (isNewsPollNode(node)) {
            return node
        }

        val nodes = node.getNodes()
        for (i in 0 until nodes.size) {
            findNewsPollNode(nodes[i])?.let { return it }
        }
        return null
    }

    private fun isNewsPollNode(node: Node): Boolean {
        val tag = node.name.orEmpty()
        val id = node.getAttribute("id").orEmpty()
        val classes = node.getAttribute("class").orEmpty()
        val action = node.getAttribute("action").orEmpty()
        val attrs = listOf(id, classes, action).joinToString(" ")
        val html = Parser.getHtml(node, false)

        if (tag.equals("form", ignoreCase = true)) {
            return isNewsPollBlock(html) || containsRawPollTemplate(html)
        }

        if (!tag.equals("div", ignoreCase = true) &&
                !tag.equals("section", ignoreCase = true) &&
                !tag.equals("aside", ignoreCase = true)) {
            return false
        }

        val markedAsPoll = attrs.contains("poll", ignoreCase = true) ||
                attrs.contains("vote", ignoreCase = true) ||
                id.contains("poll-ajax-frame", ignoreCase = true)
        return markedAsPoll && (isNewsPollBlock(html) || containsRawPollTemplate(html))
    }

    private fun normalizeNewsPollBlock(block: String, response: String): String? =
            extractDataSitePollBlock(block, response)
                    ?: normalizedVotePollBlock(block, response)
                    ?: normalizedPollFrameBlock(block, response)
                    ?: if (containsRawPollTemplate(block)) {
                        extractDataSitePollBlock(response)
                                ?: normalizedVotePollBlock(response, response)
                                ?: normalizedRenderedPollBlock(response, block)
                                ?: fallbackNewsPollBlock(response)
                    } else {
                        normalizePollResultBlock(block)
                    }

    private fun normalizePollResultBlock(block: String): String {
        if (block.contains("poll-ajax-frame", ignoreCase = true) &&
                block.contains("news-poll", ignoreCase = true)) {
            return block
        }
        val frameId = pollFrameIdFromBlock(block)
        return """<div id="$frameId" class="poll-ajax-frame news-poll">$block</div>"""
    }

    private fun pollFrameIdFromBlock(block: String): String =
            pollFrameIdRegex.find(block)
                    ?.groupValues
                    ?.getOrNull(2)
                    ?.takeIf { it.isNotBlank() }
                    ?: "poll-ajax-frame-news"

    private fun isNewsPollBlock(block: String): Boolean {
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

    private fun hasNewsPollMarkersInternal(block: String): Boolean =
            block.contains("poll", ignoreCase = true) ||
                    block.contains("vote", ignoreCase = true) ||
                    block.contains("answer[]", ignoreCase = true) ||
                    block.contains("poll_id", ignoreCase = true) ||
                    block.contains("pages/poll", ignoreCase = true) ||
                    block.contains("data-site-poll", ignoreCase = true) ||
                    block.contains("опрос", ignoreCase = true) ||
                    block.contains("голос", ignoreCase = true)

    private fun extractDataSitePollBlock(source: String, response: String = source): String? =
            buildDataSitePollBlocks(source, response)
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }

    private fun buildDataSitePollBlocks(source: String, response: String = source): List<String> {
        val rawTemplatePoll = containsRawPollTemplate(source)
        val from = articlePathFromResponse(response)
        val sourceUrl = articleUrlFromResponse(response)
        return dataSitePollAttrRegex
                .findAll(source)
                .mapNotNull { match ->
                    parseDataSitePoll(match.groupValues.getOrNull(2).orEmpty())
                            ?.takeIf { it.options.size >= 2 }
                }
                .map { poll ->
                    NewsPollRenderer.buildNormalizedVotePollBlock(
                            title = poll.title,
                            pollId = poll.pollId,
                            from = from,
                            options = poll.options,
                            hiddenFields = emptyList(),
                            sourceUrl = sourceUrl,
                            rawTemplatePoll = rawTemplatePoll,
                            readOnly = poll.voted,
                            totalVotes = poll.totalVotes,
                            multiSelect = poll.multiSelect,
                            frameId = "poll-ajax-frame-${poll.pollId}",
                            renderToken = stablePollRenderToken(poll.pollId, source)
                    )
                }
                .toList()
    }


    private val dataSitePollParser = ArticleDataSitePollParser(
            isUsablePollText = ::isUsablePollText,
            pollVoteCookieAnswers = ::pollVoteCookieAnswers,
            articleFromHtml = { input -> input.articleFromHtml() }
    )

    private val taxonomyParser = ArticleTaxonomyParser(
            patternProvider = patternProvider,
            articleFromHtml = { input -> input.articleFromHtml() },
            cleanPollText = ::cleanPollText,
            articleHtmlEncode = ::articleHtmlEncode
    )

    private fun parseDataSitePoll(encodedPayload: String): DataSitePoll? =
            dataSitePollParser.parse(encodedPayload)

    private fun pollVoteCookieAnswers(pollId: String): Set<String> =
            pollVoteCookies[pollId].orEmpty()


    private fun normalizedVotePollBlock(block: String, response: String): String? {
        val document = runCatching { Parser.parse(block) }.getOrNull() ?: return null
        val form = findNewsPollFormNode(document) ?: return null
        val options = collectPollOptions(form)
        if (options.isEmpty()) return null

        val action = form.getAttribute("action").orEmpty()
        val pollId = pollIdFromForm(action, form) ?: return null
        val from = findInputByName(form, "from")?.getAttribute("value").orEmpty()
        val title = findPollTitle(form)
        val hiddenFields = collectHiddenFields(form)
                .filterNot { it.name.equals("from", ignoreCase = true) }
                .filterNot { it.name.equals("poll_id", ignoreCase = true) || it.name.equals("poll", ignoreCase = true) }

        return NewsPollRenderer.buildNormalizedVotePollBlock(
                title = title,
                pollId = pollId,
                from = from,
                options = options,
                hiddenFields = hiddenFields,
                sourceUrl = articleUrlFromResponse(response),
                rawTemplatePoll = containsRawPollTemplate(block),
                readOnly = false,
                statusHtml = extractPollStatusHtml(block),
                frameId = if (containsRawPollTemplate(block)) {
                    "poll-ajax-frame-news"
                } else {
                    pollFrameIdFromBlock(block)
                },
                renderToken = stablePollRenderToken(pollId, block)
        )
    }

    /**
     * Parses the 4PDA "poll based on publication" (poll-frame) design, which the classic detectors
     * miss because it has no answer[] radios or ul.poll-list. Supports both the vote state
     * (<button class="poll-frame-option" value="…">) and the result state
     * (<span class="poll-frame-option">title - NN%</span>), routing into the shared NewsPollRenderer.
     */
    private fun normalizedPollFrameBlock(block: String, response: String): String? {
        if (!pollFrameMarkerRegex.containsMatchIn(block)) return null
        // Drop the inline <script data-name="site-poll"> template so its {%…%} tokens don't pollute parsing.
        val cleaned = block.replace(rawPollTemplateScriptRegex, "")
        if (!pollFrameMarkerRegex.containsMatchIn(cleaned)) return null

        val title = pollFrameTitleRegex.findAll(cleaned)
                .map { cleanPollText(it.groupValues.getOrNull(1).orEmpty()) }
                .firstOrNull { isUsablePollText(it) }
                ?: return null
        val pollId = pollFramePollIdFromBlock(cleaned) ?: return null

        val voteOptions = pollFrameButtonRegex.findAll(cleaned)
                .mapNotNull { match ->
                    val value = match.groupValues.getOrNull(1).orEmpty().articleFromHtml()?.trim().orEmpty()
                    val optionTitle = cleanPollText(match.groupValues.getOrNull(2).orEmpty())
                    if (value.isBlank() || !isUsablePollText(optionTitle)) {
                        null
                    } else {
                        PollOption(value = value, title = optionTitle)
                    }
                }
                .distinctBy { it.value }
                .toList()
        if (voteOptions.size >= 2) {
            return NewsPollRenderer.buildNormalizedVotePollBlock(
                    title = title,
                    pollId = pollId,
                    from = articlePathFromResponse(response),
                    options = voteOptions,
                    hiddenFields = emptyList(),
                    sourceUrl = articleUrlFromResponse(response),
                    rawTemplatePoll = containsRawPollTemplate(block),
                    readOnly = false,
                    renderToken = stablePollRenderToken(pollId, block)
            )
        }

        val resultOptions = pollFrameResultSpanRegex.findAll(cleaned)
                .mapIndexedNotNull { index, match ->
                    val raw = cleanPollText(match.groupValues.getOrNull(1).orEmpty())
                    if (!isUsablePollText(raw)) return@mapIndexedNotNull null
                    val percentMatch = pollFramePercentRegex.find(raw)
                    val optionTitle = percentMatch?.groupValues?.getOrNull(1)?.trim()
                            ?.takeIf { it.isNotBlank() } ?: raw
                    val percent = percentMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                    PollOption(value = (index + 1).toString(), title = optionTitle, votes = percent)
                }
                .toList()
        if (resultOptions.size >= 2) {
            val totalVotes = pollFrameFootRegex.find(cleaned)
                    ?.groupValues?.getOrNull(1)
                    ?.let { pollFrameVotesRegex.find(cleanPollText(it))?.groupValues?.getOrNull(1) }
                    ?.replace(Regex("""[\s\u00A0]"""), "")
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
            val effectiveTotal = totalVotes ?: resultOptions.sumOf { it.votes }.takeIf { it > 0 } ?: 100
            val options = if (totalVotes != null) {
                resultOptions.map { option ->
                    option.copy(votes = Math.round(effectiveTotal.toDouble() * option.votes / 100.0).toInt())
                }
            } else {
                resultOptions
            }
            return NewsPollRenderer.buildNormalizedVotePollBlock(
                    title = title,
                    pollId = pollId,
                    from = articlePathFromResponse(response),
                    options = options,
                    hiddenFields = emptyList(),
                    sourceUrl = articleUrlFromResponse(response),
                    rawTemplatePoll = containsRawPollTemplate(block),
                    readOnly = true,
                    totalVotes = effectiveTotal,
                    renderToken = stablePollRenderToken(pollId, block)
            )
        }
        return null
    }

    private fun pollFramePollIdFromBlock(block: String): String? =
            (pollFrameOptionsIdRegex.find(block)?.groupValues?.getOrNull(1)
                    ?: pollFrameAjaxIdRegex.find(block)?.groupValues?.getOrNull(1)
                    ?: Regex("""(?i)[?&]poll_id=(\d+)""").find(block.articleFromHtml().orEmpty())
                            ?.groupValues?.getOrNull(1))
                    ?.takeIf { it.isNotBlank() }

    private fun isPollFrameMarkup(block: String): Boolean =
            pollFrameMarkerRegex.containsMatchIn(block) &&
                    (pollFrameTitleRegex.containsMatchIn(block) || pollFrameButtonRegex.containsMatchIn(block))

    /**
     * Removes server-rendered poll markup (poll-frame container and poll-ajax-frame wrapper) from the
     * article body once we have built a normalized poll block, so its question/options never leak
     * into the body as plain text (the on-device "bare bold heading" symptom) or render twice.
     */
    private fun stripServerRenderedPollFromContent(html: String?): String? {
        val source = html ?: return null
        var result = source
        result = removeBalancedDivBlocks(result, pollFrameContainerOpenRegex)
        result = removeBalancedDivBlocks(result, pollAjaxFrameOpenRegex)
        result = result.replace(pollFrameTitleHeadingRegex, "")
        result = result.replace(pollFrameOptionsFormRegex, "")
        return result.takeIf { it.isNotBlank() } ?: html
    }

    private fun removeBalancedDivBlocks(html: String, openTagRegex: Regex): String {
        if (!openTagRegex.containsMatchIn(html)) return html
        val builder = StringBuilder(html.length)
        var index = 0
        while (index < html.length) {
            val match = openTagRegex.find(html, index) ?: break
            val end = balancedDivEnd(html, match.range.last + 1)
            if (end < 0) {
                builder.append(html, index, match.range.last + 1)
                index = match.range.last + 1
                continue
            }
            builder.append(html, index, match.range.first)
            index = end
        }
        if (index < html.length) builder.append(html, index, html.length)
        return builder.toString()
    }

    private fun balancedDivEnd(html: String, fromIndex: Int): Int {
        var depth = 1
        var index = fromIndex
        while (index < html.length && depth > 0) {
            val nextOpen = divOpenTagRegex.find(html, index)
            val nextClose = divCloseTagRegex.find(html, index) ?: return -1
            if (nextOpen != null && nextOpen.range.first < nextClose.range.first) {
                depth++
                index = nextOpen.range.last + 1
            } else {
                depth--
                if (depth == 0) return nextClose.range.last + 1
                index = nextClose.range.last + 1
            }
        }
        return -1
    }

    private fun extractPollStatusHtml(block: String): String? =
            pollStatusRegex.find(block)?.value
                    ?: pollParagraphRegex
                            .findAll(block)
                            .map { it.value }
                            .firstOrNull { paragraph ->
                                val text = cleanPollText(paragraph)
                                text.contains("результ", ignoreCase = true) ||
                                        text.contains("голос", ignoreCase = true) ||
                                        text.contains("увид", ignoreCase = true)
                            }

    private fun normalizedRenderedPollBlock(response: String, rawBlock: String): String? {
        normalizedVotePollBlock(response, response)?.let { return it }
        val document = runCatching { Parser.parse(response) }.getOrNull() ?: return null
        renderedPollCandidateBlocks(document).forEach { block ->
            val renderedPoll = renderedPollFromContainer(block) ?: return@forEach
            if (isUsablePollText(renderedPoll.title) && renderedPoll.options.size >= 2) {
                return NewsPollRenderer.buildNormalizedVotePollBlock(
                        title = renderedPoll.title,
                        pollId = pollIdFromText(rawBlock) ?: pollIdFromText(response) ?: "0",
                        from = articlePathFromResponse(response),
                        options = renderedPoll.options,
                        hiddenFields = emptyList(),
                        sourceUrl = articleUrlFromResponse(response),
                        rawTemplatePoll = true,
                        readOnly = true,
                        renderToken = stablePollRenderToken(pollIdFromText(rawBlock) ?: "0", rawBlock)
                )
            }
        }
        return null
    }

    private fun stablePollRenderToken(pollId: String, source: String): String =
            "poll-${pollId.ifBlank { "0" }}-${source.length}"

    private fun renderedPollCandidateBlocks(node: Node?): List<String> {
        val result = mutableListOf<String>()
        fun walk(current: Node?) {
            if (current == null || Parser.isNotElement(current)) return
            if (current.name.orEmpty().lowercase() in renderedPollContainerTagNames &&
                    isRenderedPollFallbackContainer(current)) {
                result += Parser.getHtml(current, false)
                return
            }
            current.getNodes().forEach { walk(it) }
        }
        walk(node)
        return result
    }

    private fun isRenderedPollFallbackContainer(node: Node): Boolean {
        val html = Parser.getHtml(node, false)
        return hasRenderedPollContainerMarker(node, html) &&
                hasPollVoteControl(node) &&
                renderedPollFromContainer(html) != null
    }

    private fun hasRenderedPollContainerMarker(node: Node, html: String): Boolean {
        val attrs = listOf(
                node.getAttribute("id").orEmpty(),
                node.getAttribute("class").orEmpty(),
                node.getAttribute("action").orEmpty()
        ).joinToString(" ")
        return attrs.contains("poll", ignoreCase = true) ||
                attrs.contains("vote", ignoreCase = true) ||
                html.contains("poll_id", ignoreCase = true) ||
                html.contains("pages/poll", ignoreCase = true) ||
                html.contains("poll-ajax-frame", ignoreCase = true)
    }

    private fun hasPollVoteControl(node: Node): Boolean {
        if (Parser.isNotElement(node)) return false
        val tag = node.name.orEmpty()
        if (tag.equals("button", ignoreCase = true) || tag.equals("a", ignoreCase = true)) {
            val text = cleanPollText(Parser.getHtml(node, true))
            val attrs = listOf(
                    node.getAttribute("class").orEmpty(),
                    node.getAttribute("href").orEmpty(),
                    node.getAttribute("data-href").orEmpty()
            ).joinToString(" ")
            if (text.contains("голос", ignoreCase = true) ||
                    text.contains("результ", ignoreCase = true) ||
                    attrs.contains("poll", ignoreCase = true) ||
                    attrs.contains("vote", ignoreCase = true)) {
                return true
            }
        }
        if (tag.equals("input", ignoreCase = true)) {
            val type = node.getAttribute("type").orEmpty()
            val text = listOf(
                    node.getAttribute("value").orEmpty(),
                    node.getAttribute("class").orEmpty()
            ).joinToString(" ")
            if ((type.equals("submit", ignoreCase = true) || type.equals("button", ignoreCase = true)) &&
                    (text.contains("голос", ignoreCase = true) ||
                            text.contains("результ", ignoreCase = true) ||
                            text.contains("poll", ignoreCase = true) ||
                            text.contains("vote", ignoreCase = true))) {
                return true
            }
        }
        return node.getNodes().any { hasPollVoteControl(it) }
    }

    private fun renderedPollFromContainer(block: String): RenderedPoll? {
        renderedPollListRegex.findAll(block).forEach { match ->
            val title = cleanPollText(match.groupValues.getOrNull(1).orEmpty())
            val listHtml = match.groupValues.getOrNull(2).orEmpty()
            val optionTexts = renderedPollOptionRegex
                    .findAll(listHtml)
                    .map { cleanPollText(it.groupValues.getOrNull(1).orEmpty()) }
                    .filter { isUsablePollText(it) }
                    .toList()
            if (isUsablePollText(title) &&
                    optionTexts.size >= 2 &&
                    !isRejectedRenderedPollList(listHtml, optionTexts)) {
                return RenderedPoll(
                        title = title,
                        options = optionTexts.mapIndexed { index, text ->
                            PollOption(value = (index + 1).toString(), title = text)
                        }
                )
            }
        }
        return null
    }

    private fun isRejectedRenderedPollList(listHtml: String, optionTexts: List<String>): Boolean =
            rejectedRenderedPollListRegex.containsMatchIn(listHtml) ||
                    optionTexts.count { countSuffixRegex.containsMatchIn(it) } >= 2

    private fun containsRejectedRenderedPollList(block: String): Boolean =
            renderedPollListRegex.findAll(block).any { match ->
                val listHtml = match.groupValues.getOrNull(2).orEmpty()
                val optionTexts = renderedPollOptionRegex
                        .findAll(listHtml)
                        .map { cleanPollText(it.groupValues.getOrNull(1).orEmpty()) }
                        .filter { isUsablePollText(it) }
                        .toList()
                optionTexts.size >= 2 && isRejectedRenderedPollList(listHtml, optionTexts)
            }

    private fun findNewsPollFormNode(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null
        if (node.name.equals("form", ignoreCase = true)) {
            val html = Parser.getHtml(node, false)
            if (isNewsPollBlock(html) ||
                    (hasNewsPollMarkersInternal(html) && answerInputRegex.containsMatchIn(html))) {
                return node
            }
        }
        node.getNodes().forEach { child ->
            findNewsPollFormNode(child)?.let { return it }
        }
        return null
    }

    private fun collectPollOptions(form: Node): List<PollOption> {
        val options = mutableListOf<PollOption>()
        collectPollOptionNodes(form, options, "")
        return options.distinctBy { it.value }
                .filter { it.value.isNotBlank() && isUsablePollText(it.title) }
    }

    private fun collectPollOptionNodes(node: Node, options: MutableList<PollOption>, labelText: String) {
        if (Parser.isNotElement(node)) return
        val currentLabelText = when {
            node.name.orEmpty().lowercase() in optionContainerTagNames && containsAnswerInput(node) ->
                extractTextWithoutInputs(node).articleFromHtml()?.trim().orEmpty()
            else -> labelText
        }
        if (node.name.equals("input", ignoreCase = true) && isAnswerInput(node)) {
            val title = currentLabelText.ifBlank {
                extractTextWithoutInputs(node).articleFromHtml()?.trim().orEmpty()
            }.ifBlank { node.getAttribute("value").orEmpty() }
            options += PollOption(value = node.getAttribute("value").orEmpty(), title = title)
        }
        node.getNodes().forEach { collectPollOptionNodes(it, options, currentLabelText) }
    }

    private fun isAnswerInput(node: Node): Boolean {
        val type = node.getAttribute("type").orEmpty()
        val name = node.getAttribute("name").orEmpty()
        return (type.equals("radio", ignoreCase = true) || type.equals("checkbox", ignoreCase = true)) &&
                (name.equals("answer[]", ignoreCase = true) ||
                        name.equals("answer", ignoreCase = true) ||
                        name.startsWith("answer[", ignoreCase = true))
    }

    private fun containsAnswerInput(node: Node): Boolean {
        if (Parser.isNotElement(node)) return false
        if (node.name.equals("input", ignoreCase = true) && isAnswerInput(node)) return true
        return node.getNodes().any { containsAnswerInput(it) }
    }

    private fun collectAnswerInputCount(node: Node?): Int {
        if (node == null || Parser.isNotElement(node)) return 0
        val current = if (node.name.equals("input", ignoreCase = true) && isAnswerInput(node)) 1 else 0
        return current + node.getNodes().sumOf { collectAnswerInputCount(it) }
    }

    private fun extractTextWithoutInputs(node: Node): String {
        if (Parser.isTextNode(node)) return node.text.orEmpty()
        if (Parser.isNotElement(node)) return ""
        return node.getNodes().joinToString(separator = "") { extractTextWithoutInputs(it) }
    }

    private fun pollIdFromForm(action: String, form: Node): String? {
        Regex("""[?&]poll_id=(\d+)""", RegexOption.IGNORE_CASE)
                .find(action.articleFromHtml().orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return it }
        return (findInputByName(form, "poll_id") ?: findInputByName(form, "poll"))
                ?.getAttribute("value")
                ?.takeIf { it.isNotBlank() }
    }

    private fun findInputByName(node: Node, name: String): Node? {
        if (Parser.isNotElement(node)) return null
        if (node.name.equals("input", ignoreCase = true) &&
                node.getAttribute("name").orEmpty().equals(name, ignoreCase = true)) {
            return node
        }
        node.getNodes().forEach { child ->
            findInputByName(child, name)?.let { return it }
        }
        return null
    }

    private fun collectHiddenFields(node: Node): List<PollHiddenField> {
        val fields = mutableListOf<PollHiddenField>()
        fun walk(current: Node) {
            if (Parser.isNotElement(current)) return
            if (current.name.equals("input", ignoreCase = true) &&
                    current.getAttribute("type").orEmpty().equals("hidden", ignoreCase = true)) {
                val name = current.getAttribute("name").orEmpty()
                if (name.isNotBlank()) {
                    fields += PollHiddenField(name, current.getAttribute("value").orEmpty())
                }
            }
            current.getNodes().forEach { walk(it) }
        }
        walk(node)
        return fields
    }

    private fun findPollTitle(form: Node): String {
        fun walk(node: Node): String? {
            if (Parser.isNotElement(node)) return null
            if (node.name.orEmpty().lowercase() in titleTagNames) {
                val title = Parser.getHtml(node, true).articleFromHtml()?.trim().orEmpty()
                if (title.isNotBlank()) return title
            }
            node.getNodes().forEach { child ->
                walk(child)?.let { return it }
            }
            return null
        }
        return walk(form).orEmpty()
    }

    private fun isNormalizedNewsPollBlock(block: String): Boolean =
            block.contains("news-poll-normalized", ignoreCase = true) ||
                    block.contains("data-normalized-poll", ignoreCase = true)

    private fun isFallbackNewsPollBlock(block: String): Boolean =
            block.contains("news-poll-fallback", ignoreCase = true) ||
                    block.contains("data-poll-fallback", ignoreCase = true)

    private fun isRawTemplatePollBlock(block: String): Boolean =
            block.contains("data-raw-template-poll", ignoreCase = true) ||
                    containsRawPollTemplate(block)

    private fun containsRawPollTemplate(block: String?): Boolean {
        val source = block.orEmpty()
        return rawPollTemplateTokens.any { source.contains(it, ignoreCase = true) }
    }

    private fun cleanPollText(source: String): String =
            source
                    .replace(Regex("""(?is)<script\b[\s\S]*?</script>"""), " ")
                    .replace(Regex("""(?is)<style\b[\s\S]*?</style>"""), " ")
                    .replace(Regex("""(?is)<input\b[^>]*>"""), " ")
                    .replace(Regex("""(?is)<[^>]+>"""), " ")
                    .articleFromHtml()
                    ?.replace(Regex("""\s+"""), " ")
                    ?.trim()
                    .orEmpty()

    private fun isUsablePollText(text: String): Boolean =
            text.isNotBlank() && !containsRawPollTemplate(text)

    private fun stripRawPollTemplates(html: String?): String? {
        val source = html ?: return null
        if (!containsRawPollTemplate(source)) return source
        return source
                .replace(rawPollTemplateScriptRegex, "")
                .replace(rawPollContainerRegex, "")
                .takeIf { it.isNotBlank() }
    }

    private fun fallbackNewsPollBlock(response: String): String {
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

    private fun forcedFallbackNewsPollBlock(
            response: String,
            articleId: Int,
            articleTitle: String?,
            currentHtml: String?
    ): String? {
        // Lazy data-site-poll placeholders are not renderable; do not treat them as a real poll block.
        if (hasRenderableNewsPollMarkup(currentHtml)) return null
        val effectiveArticleId = articleId.takeIf { it > 0 } ?: articleIdFromResponse(response)
        val effectiveTitle = articleTitle.orEmpty().ifBlank { pollTitleFromResponse(response) }
        val shouldForceFallback = effectiveArticleId == 456521 || isPollTitle(effectiveTitle)
        return if (shouldForceFallback) fallbackNewsPollBlock(response, effectiveTitle) else null
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

    private fun appendOrReplaceFallbackPollBlock(currentHtml: String?, fallbackBlock: String): String =
            listOf(currentHtml.orEmpty().replace(newsPollFallbackBlockRegex, ""), fallbackBlock)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")

    private fun isPollTitle(title: String): Boolean =
            pollTitleRegex.containsMatchIn(title.articleFromHtml()?.trim().orEmpty())

    private fun articleIdFromResponse(response: String): Int? {
        articleIdMetaRegex.find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { return it }
        Regex("""(?i)(?:[?&]p=)(\d{4,})""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { return it }
        articleItemIdRegex.find(response)
                ?.groupValues
                ?.getOrNull(2)
                ?.toIntOrNull()
                ?.let { return it }
        articleIdRegex.find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { return it }
        return null
    }

    private fun pollIdFromText(source: String): String? =
            Regex("""(?i)(?:poll_id|poll)\D{0,24}(\d+)""")
                    .find(source.articleFromHtml().orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)

    private fun articlePathFromResponse(response: String): String =
            articleUrlFromResponse(response)
                    ?.substringAfter("https://4pda.to", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: ""

    private fun pollTitleFromResponse(response: String): String {
        Regex("""(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*["']og:title["'])(?=[^>]*content\s*=\s*["']([^"']+)["'])[^>]*>""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.articleFromHtml()
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

    private fun articleUrlFromResponse(response: String): String? {
        Regex("""(?is)<meta\b(?=[^>]*(?:property|name)\s*=\s*["']og:url["'])(?=[^>]*content\s*=\s*["']([^"']+)["'])[^>]*>""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.articleFromHtml()
                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                ?.let { return it }
        Regex("""(?i)(?:[?&]p=|["']article:id["']\s*content=["'])(\d+)""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return "https://4pda.to/index.php?p=$it" }
        articleItemIdRegex.find(response)
                ?.groupValues
                ?.getOrNull(2)
                ?.let { return "https://4pda.to/index.php?p=$it" }
        Regex("""(?i)/\d{4}/\d{2}/\d{2}/(\d{4,})/""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return "https://4pda.to/index.php?p=$it" }
        articleIdRegex.find(response)?.groupValues?.getOrNull(1)?.let {
            return "https://4pda.to/index.php?p=$it"
        }
        return null
    }

    private fun parseMaterials(source: String): List<Material> =
            taxonomyParser.parseMaterials(source)

    private fun parseTags(source: String): List<Tag> =
            taxonomyParser.parseTags(source)

    private fun parseArticleTaxonomy(response: String, primarySource: String? = null): ArticleTaxonomy =
            taxonomyParser.parseArticleTaxonomy(response, primarySource)

    fun parseKarmaMap(source: String): SparseArray<Comment.Karma> {
        val result = SparseArray<Comment.Karma>()
        patternProvider
                .getPattern(scope.scope, scope.karmaSource)
                .matcher(source)
                .findOnce { outer ->
                    if (BuildConfig.DEBUG) {
                        Timber.d("karma: ${outer.group(1)}")
                    }
                    parseKarmaJsonObject(outer.group(1).orEmpty(), result)
                }
        if (result.size() == 0) {
            parseKarmaJsonObject(source, result)
        }
        return result
    }

    internal fun parseKarmaEntry(values: List<String>): Comment.Karma =
            Comment.Karma().apply {
                status = values.firstOrNull()?.toIntOrNull() ?: 0
                count = when {
                    values.size >= 5 -> values[4].toIntOrNull() ?: 0
                    values.size >= 4 -> values[3].toIntOrNull() ?: 0
                    values.size >= 2 -> values.last().toIntOrNull() ?: 0
                    else -> 0
                }
            }

    private fun parseKarmaJsonObject(karmaJson: String, result: SparseArray<Comment.Karma>) {
        karmaEntryRegex.findAll(karmaJson).forEach { entry ->
            try {
                val commentId = entry.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                val values = entry.groupValues[2]
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                if (values.isEmpty()) return@forEach
                result.put(commentId, parseKarmaEntry(values))
            } catch (ex: Exception) {
                Timber.e(ex, "Article parse error")
            }
        }
    }

    private fun parseKarma(source: String): SparseArray<Comment.Karma> = parseKarmaMap(source)

    private fun stripCommentForm(comments: String?): String? {
        val raw = comments ?: return null
        return patternProvider
                .getPattern(scope.scope, scope.exclude_form_comment)
                .matcher(raw)
                .replaceFirst("")
    }

    /**
     * Если группа detail/detail_v2 не совпала с вёрсткой 4PDA, вытаскиваем &lt;ul/ol class="…comment-list…"&gt; из полного HTML.
     */
    private fun extractCommentsUlHtmlFromPage(pageContext: ArticlePageContext): String? {
        if (pageContext.response.isBlank()) return null
        return try {
            val doc = pageContext.documentOrNull() ?: return null
            val ul = findUlCommentList(doc)
            if (ul == null) return null
            Parser.getHtml(ul, false)
        } catch (ex: Exception) {
            if (BuildConfig.DEBUG) {
                Timber.w(ex, "extractCommentsUlHtmlFromPage")
            }
            null
        }
    }

    private fun findUlCommentList(node: Node?): Node? {
        if (node == null) return null
        if (Parser.isNotElement(node)) return null
        if ("ul".equals(node.name, ignoreCase = true) || "ol".equals(node.name, ignoreCase = true)) {
            val cls = node.getAttribute("class")
            if (cls != null && (cls.contains("comment-list") || cls.contains("comments-list"))) return node
        }
        val nodes = node.getNodes() ?: return null
        for (i in 0 until nodes.size) {
            findUlCommentList(nodes[i])?.let { return it }
        }
        return null
    }

    private fun findCommentAnchorNode(li: Node): Node? {
        Parser.findNode(li, "div", "id", "comment-")?.let { return it }
        Parser.findNode(li, "article", "id", "comment-")?.let { return it }
        Parser.findNode(li, "a", "id", "comment")?.let { anchor ->
            commentNumericIdFromAttribute(anchor.getAttribute("id"))?.let { return anchor }
        }
        commentNumericIdFromAttribute(li.getAttribute("data-comment-id"))?.let { return li }
        commentNumericIdFromAttribute(li.getAttribute("data-comment"))?.let { return li }
        return findNodeWithCommentId(li)
    }

    private fun commentNumericIdFromAttribute(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        commentNumericIdRegex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        return value.toIntOrNull()?.takeIf { it > 0 }
    }

    /** Обход, если id вынесен не на div (например data-атрибуты меняли вёрстку). */
    private fun findNodeWithCommentId(node: Node?): Node? {
        if (node == null || Parser.isNotElement(node)) return null
        val id = node.getAttribute("id")
        if (id != null && id.contains("comment-")) {
            val m = patternProvider.getPattern(scope.scope, scope.comment_id).matcher(id)
            if (m.find()) return node
        }
        val nodes = node.getNodes() ?: return null
        for (i in 0 until nodes.size) {
            findNodeWithCommentId(nodes[i])?.let { return it }
        }
        return null
    }

    private fun findCommentContentNode(scope: Node): Node? {
        // Prefer explicit comment body containers. Generic ".content" is too broad and may capture
        // nested article blocks or wrappers, leading to "duplicated article" inside comment body.
        Parser.findNode(scope, "div", "class", "comment-content")?.let { return it }
        Parser.findNode(scope, "p", "class", "comment-content")?.let { return it }
        Parser.findNode(scope, "p", "class", "content")?.let { return it }
        Parser.findNode(scope, "div", "class", "content")?.let { return it }
        return null
    }

    fun parseComments(karmaMap: SparseArray<Comment.Karma>, source: String?): Comment {
        val comments = Comment()
        val normalized = ensureBalancedCommentsHtml(source)
        if (normalized.isNullOrBlank()) {
            logCommentsParseDiagnostics(source, comments, path = "empty_source")
            return comments
        }
        val document = Parser.parse(normalized)
        recurseComments(karmaMap, document, comments, 0)
        if (comments.children.isEmpty() && hasCommentMarkup(normalized)) {
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_COMMENTS_SECTION,
                    "parse_root_missing",
                    mapOf(
                            "sourceLen" to normalized.length,
                            "hasCommentListClass" to normalized.contains("comment-list", ignoreCase = true),
                            "hasCommentsListClass" to normalized.contains("comments-list", ignoreCase = true),
                            "hasCommentIdDiv" to normalized.contains("id=\"comment-", ignoreCase = true),
                    )
            )
            parseCommentsFromTags(karmaMap, normalized, comments)
            logCommentsParseDiagnostics(normalized, comments, path = "tag_fallback")
        } else {
            logCommentsParseDiagnostics(normalized, comments, path = "dom")
        }
        return comments
    }

    /**
     * Tag-only comment parse (no DOM walk). Much faster than [parseComments] on full desktop pages.
     */
    fun parseCommentsViaTagsOnly(karmaMap: SparseArray<Comment.Karma>, source: String?): Comment {
        val parent = Comment()
        val normalized = ensureBalancedCommentsHtml(source).orEmpty()
        if (normalized.isBlank()) {
            return parent
        }
        parseCommentsFromTags(karmaMap, normalized, parent, resolveActions = true)
        return parent
    }

    /**
     * Parses only [limit] comment nodes after skipping [skip] in document order.
     * Used for paginated load-more when the server returns the full comment tree.
     */
    fun parseCommentsBatch(
            karmaMap: SparseArray<Comment.Karma>,
            source: String?,
            skip: Int,
            limit: Int,
    ): Comment {
        val parent = Comment()
        val normalized = ensureBalancedCommentsHtml(source).orEmpty()
        if (normalized.isBlank() || limit <= 0) {
            return parent
        }
        parseCommentsFromTags(karmaMap, normalized, parent, skip = skip.coerceAtLeast(0), limit = limit)
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_COMMENTS_SECTION,
                "paginated_batch_capped",
                mapOf(
                        "parsedCount" to countCommentNodesInSource(normalized),
                        "batchCount" to countParsedComments(parent),
                        "skip" to skip,
                        "cappedTo" to limit,
                        "path" to "tag_batch",
                )
        )
        return parent
    }

    private fun parseCommentsForProbe(source: String): Comment {
        val key = source.hashCode()
        synchronized(commentProbeCache) {
            commentProbeCache[key]?.takeIf { it.source == source }?.let { return it.parsed }
        }
        val parsed = parseComments(SparseArray(), source)
        synchronized(commentProbeCache) {
            commentProbeCache[key] = CommentProbeCacheEntry(source, parsed)
        }
        return parsed
    }

    fun hasCommentMarkup(source: String?): Boolean {
        val html = source.orEmpty()
        if (html.isBlank()) return false
        return html.contains("comment-list", ignoreCase = true) ||
                html.contains("comments-list", ignoreCase = true) ||
                html.contains("id=\"comment-", ignoreCase = true) ||
                html.contains("id='comment-", ignoreCase = true) ||
                html.contains("data-comment-id", ignoreCase = true) ||
                commentOpenTagRegex.containsMatchIn(html)
    }

    /**
     * Stricter than [hasCommentMarkup]: true only when the source carries an actual comment NODE
     * (a `<div|article|li>` with `id="comment-<id>"` or `data-comment(-id)="<id>"`), not merely an
     * empty/collapsed `comment-list` container shell. Used to tell "comments are present but failed
     * to parse" apart from "the fetched source does not contain the counted comments yet".
     */
    fun hasCommentNodeMarkup(source: String?): Boolean {
        val html = source.orEmpty()
        if (html.isBlank()) return false
        return commentOpenTagRegex.containsMatchIn(html) ||
                html.contains("data-comment-id", ignoreCase = true)
    }

    /** Counts unique comment ids present in raw HTML (anchors/data-comment-id). */
    fun countCommentNodesInSource(source: String?): Int {
        val html = source.orEmpty()
        if (html.isBlank()) return 0
        val ids = LinkedHashSet<Int>()
        commentOpenTagRegex.findAll(html).forEach { match ->
            val id = match.groupValues.getOrNull(2)?.toIntOrNull()
                    ?: match.groupValues.getOrNull(3)?.toIntOrNull()
            if (id != null && id > 0) {
                ids.add(id)
            }
        }
        commentDataIdRegex.findAll(html).forEach { match ->
            match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { ids.add(it) }
        }
        return ids.size
    }

    /**
     * True when the source carries fewer comment nodes than the article's own counter badge.
     * Mobile phase-1 pages may ship the first lazy-loaded batch (~10 nodes) even for articles
     * with hundreds of comments; treat that as incomplete and fetch the desktop list.
     */
    fun commentsSourceUnderfetchesExpected(source: String?, expectedCount: Int): Boolean {
        if (expectedCount <= 0) return false
        if (!hasCommentNodeMarkup(source)) return true
        return countCommentNodesInSource(source) < expectedCount
    }

    fun countParsedComments(root: Comment): Int = root.flattenComments().size

    private fun logCommentsParseDiagnostics(source: String?, root: Comment, path: String) {
        val html = source.orEmpty()
        val diagnostics = linkedMapOf<String, Any?>(
                "path" to path,
                "sourceLen" to html.length,
                "parsedCount" to root.flattenComments().size,
                "hasCommentListClass" to html.contains("comment-list", ignoreCase = true),
                "hasCommentsListClass" to html.contains("comments-list", ignoreCase = true),
                "hasCommentIdDiv" to html.contains("id=\"comment-", ignoreCase = true),
                "hasDataCommentId" to html.contains("data-comment-id", ignoreCase = true),
                "jsonEnvelope" to ArticleResponseBody.looksLikeJsonEnvelope(html),
                "tagOpenMatches" to commentOpenTagRegex.findAll(html).count()
        )
        FpdaDebugLog.log(FpdaDebugLog.TAG_COMMENTS_SECTION, "parse_selectors", diagnostics)
    }

    /**
     * Tag-batch blocks are raw `div#comment-*` fragments. The legacy path parsed them as a bare
     * document root and missed dropdown actions; wrap each block in a list item and reuse the DOM
     * comment parser so menu actions match [recurseComments].
     */
    private fun parseCommentActionsFromTagBlock(block: String, commentId: Int, userId: Int): Comment.Actions {
        if (block.isBlank() || commentId <= 0) return Comment.Actions()
        val wrapped = """<ul class="comment-list"><li>$block</li></ul>"""
        val actions = runCatching {
            parseComments(SparseArray(), wrapped).children.firstOrNull()?.actions
        }.getOrNull() ?: Comment.Actions()
        fillMissingCommentLikeActionsFromHtml(actions, block, commentId)
        if (actions.profile == null && userId > 0) {
            actions.profile = Comment.Action("https://4pda.to/forum/index.php?showuser=$userId")
                    .withType(Comment.Action.Type.PROFILE)
        }
        return actions
    }

    /**
     * DOM tree parse may miss XHR fragments (attribute order, anchor id without hyphen).
     */
    private fun parseCommentsFromTags(
            karmaMap: SparseArray<Comment.Karma>,
            source: String,
            parent: Comment,
            skip: Int = 0,
            limit: Int = Int.MAX_VALUE,
            resolveActions: Boolean = true,
    ) {
        val openings = commentOpenTagRegex.findAll(source)
                .map { match ->
                    val id = match.groupValues.getOrNull(2)?.toIntOrNull()
                            ?: match.groupValues.getOrNull(3)?.toIntOrNull()
                    match.range.first to id
                }
                .filter { (_, id) -> id != null && id > 0 }
                .toList()
        val seenIds = parent.flattenComments().map { it.id }.toMutableSet()
        var taken = 0
        openings.forEachIndexed { index, (start, commentId) ->
            if (taken >= limit) return@forEachIndexed
            val id = commentId ?: return@forEachIndexed
            if (index < skip) return@forEachIndexed
            if (!seenIds.add(id)) return@forEachIndexed
            val blockEnd = openings.getOrNull(index + 1)?.first ?: source.length
            val block = source.substring(start, blockEnd)
            val openTagEnd = block.indexOf('>')
            val openTag = if (openTagEnd >= 0) block.substring(0, openTagEnd + 1) else block
            val isDeleted = commentDeletedClassRegex.containsMatchIn(openTag)
            val comment = Comment().apply {
                this.id = id
                this.isDeleted = isDeleted
                userId = commentShowUserRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: commentUserDataIdRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: 0
                userNick = commentFallbackNickRegex
                        .find(block)?.groupValues?.getOrNull(1).orEmpty().articleFromHtml()
                date = commentFallbackDateRegex
                        .find(block)?.groupValues?.getOrNull(1).orEmpty().articleFromHtml()?.trim()
                val rawContent = commentFallbackContentRegex
                        .find(block)?.groupValues?.getOrNull(1).orEmpty()
                content = rawContent.articleFromHtml()?.trim().orEmpty()
                karma = karmaMap.get(id)
                actions = if (resolveActions) {
                    parseCommentActionsFromTagBlock(block, id, userId)
                } else {
                    Comment.Actions()
                }
            }
            // The node already carries a valid comment id (id > 0). Drop it only when there is no
            // recognizable signal at all; keeping author/date-only nodes avoids a full parse failure
            // (and the "comments present but parse empty" error) when one selector misses on a page
            // variant with many comments.
            val hasAnySignal = isDeleted ||
                    !comment.content.isNullOrBlank() ||
                    comment.userNick.orEmpty().isNotBlank() ||
                    comment.userId > 0 ||
                    !comment.date.isNullOrBlank()
            if (!hasAnySignal) return@forEachIndexed
            applyCommentLikeState(comment)
            parent.children.add(comment)
            taken++
        }
    }

    fun canExtractCommentEditAction(source: String?, commentId: Int = 0): Boolean =
            parseCommentEditAction(source) != null ||
                    extractCommentEditActionFromHtml(source, commentId) != null

    fun parseCommentEditAction(source: String?): Comment.Action? {
        if (source.isNullOrBlank()) return null
        val document = Parser.parse(source)
        var fallbackForm: Comment.Action? = null
        walkElements(document) { node ->
            if (fallbackForm != null && hasModerationNonce(fallbackForm!!) && isEditMarker(
                            listOf(
                                    fallbackForm?.url.orEmpty(),
                                    fallbackForm?.fields?.keys?.joinToString(" ").orEmpty()
                            ).joinToString(" ").lowercase()
                    )) {
                return@walkElements
            }
            if (!node.name.orEmpty().equals("form", ignoreCase = true)) return@walkElements
            val formAction = parseCommentFormAction(node, 0, 0) ?: return@walkElements
            val marker = listOf(
                    formAction.url.orEmpty(),
                    node.getAttribute("class").orEmpty(),
                    node.getAttribute("id").orEmpty(),
                    formAction.fields["action"].orEmpty(),
                    Parser.getHtml(node, true)
            ).joinToString(" ").lowercase()
            if (isEditMarker(marker) &&
                    formAction.fields.keys.any { isCommentTextField(it) } &&
                    hasModerationNonce(formAction)) {
                fallbackForm = formAction.withType(Comment.Action.Type.EDIT)
                return@walkElements
            }
            if (fallbackForm == null && isWordPressCommentEditForm(formAction) && hasModerationNonce(formAction)) {
                fallbackForm = formAction.withType(Comment.Action.Type.EDIT)
            }
        }
        return fallbackForm
                ?.withType(Comment.Action.Type.EDIT)
                ?.let { finalizeCommentEditAction(it, source) }
                ?: extractCommentEditActionFromHtml(source, 0)
    }

    fun extractCommentEditNonceFromPage(source: String?): Pair<String, String>? {
        if (source.isNullOrBlank()) return null
        moderationNonceFieldNames.forEach { name ->
            extractHiddenInputValue(source, name)?.takeIf { it.isNotBlank() }?.let { return name to it }
            val escaped = Regex.escape(name)
            val jsPatterns = listOf(
                    Regex("""["']$escaped["']\s*:\s*["']([0-9a-zA-Z_-]{4,})["']""", RegexOption.IGNORE_CASE),
                    Regex("""["']$escaped["']\s*=\s*["']([0-9a-zA-Z_-]{4,})["']""", RegexOption.IGNORE_CASE),
                    Regex("""\b$escaped=([0-9a-zA-Z_-]{4,})\b""", RegexOption.IGNORE_CASE)
            )
            jsPatterns.forEach { pattern ->
                pattern.find(source)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return name to it }
            }
        }
        return null
    }

    fun extractCommentEditActionFromHtml(source: String?, commentId: Int = 0): Comment.Action? {
        if (source.isNullOrBlank()) return null
        extractCommentEditFromWpAjaxResponse(source, commentId)?.let { return it }
        val nonce = extractModerationNonce(source) ?: return null
        val resolvedCommentId = commentId.takeIf { it > 0 }
                ?: extractHiddenInputValue(source, "comment_ID")?.toIntOrNull()?.takeIf { it > 0 }
                ?: extractHiddenInputValue(source, "c")?.toIntOrNull()?.takeIf { it > 0 }
                ?: return null
        val postId = extractHiddenInputValue(source, "comment_post_ID")
        val actionName = extractHiddenInputValue(source, "action")?.takeIf { it.isNotBlank() }
                ?: if (source.contains("admin-ajax.php", ignoreCase = true)) "editcomment" else "editedcomment"
        val textFieldValue = extractCommentTextField(source) ?: ("content" to "")
        val submitUrl = resolveCommentEditSubmitUrl(source, actionName)
        return Comment.Action(
                url = submitUrl,
                method = Comment.Action.METHOD_POST,
                fields = linkedMapOf<String, String>().apply {
                    put(nonce.first, nonce.second)
                    put("comment_ID", resolvedCommentId.toString())
                    put("action", actionName)
                    postId?.let { put("comment_post_ID", it) }
                    put(textFieldValue.first, textFieldValue.second)
                },
                type = Comment.Action.Type.EDIT
        ).let { finalizeCommentEditAction(it, source) }
    }

    fun buildInlineCommentEditAction(
            source: String?,
            commentId: Int,
            articleId: Int = 0,
            inlineText: String? = null,
            editableElementId: String? = null,
            submitText: String? = null
    ): Comment.Action? {
        if (commentId <= 0) return null
        val fields = linkedMapOf<String, String>()
        fields["comment_ID"] = commentId.toString()
        if (articleId > 0) {
            fields["comment_post_ID"] = articleId.toString()
        }
        inlineText?.takeIf { it.isNotBlank() }?.let { fields["comment"] = it }
        extractCommentEditNonceFromPage(source)?.let { (name, value) -> fields[name] = value }
        extractCommentFormFields(source)?.forEach { (name, value) ->
            if (!fields.containsKey(name) && value.isNotBlank()) {
                fields[name] = value
            }
        }
        if (!fields.keys.any { isModerationNonceField(it) }) return null
        return Comment.Action(
                url = INLINE_COMMENT_EDIT_SUBMIT_URL,
                method = Comment.Action.METHOD_POST,
                fields = fields,
                type = Comment.Action.Type.EDIT,
                editableElementId = editableElementId,
                editableHtml = inlineText,
                submitText = submitText
        )
    }

    private fun extractCommentFormFields(source: String?): Map<String, String>? {
        if (source.isNullOrBlank()) return null
        return runCatching {
            val document = Parser.parse(source)
            val form = Parser.findNode(document, "form", "id", "commentform")
                    ?: Parser.findNode(document, "form", "class", "comment-form")
                    ?: return null
            collectFormFields(form)
        }.getOrNull()
    }

    private fun finalizeCommentEditAction(action: Comment.Action, source: String?): Comment.Action {
        val fields = LinkedHashMap(action.fields)
        if (!fields.keys.any { isCommentTextField(it) }) {
            extractCommentTextField(source.orEmpty())?.let { (name, value) ->
                fields[name] = value
            }
        }
        val url = action.url?.takeIf { it.isNotBlank() }
                ?: resolveCommentEditSubmitUrl(source.orEmpty(), fields["action"].orEmpty())
        return action.copy(url = url, fields = fields)
    }

    private fun extractCommentEditFromWpAjaxResponse(source: String, commentId: Int): Comment.Action? {
        if (!source.contains("wp_ajax_response", ignoreCase = true)) return null
        val cdata = wpAjaxCdataRegex.find(source)?.groupValues?.getOrNull(1) ?: return null
        return extractCommentEditActionFromHtml(cdata, commentId)
    }

    private fun resolveCommentEditSubmitUrl(source: String, actionName: String): String {
        val formAction = formActionAttrRegex.find(source)?.groupValues?.getOrNull(1).orEmpty()
        normalize4pdaUrl(formAction)?.let { return it }
        return when {
            source.contains("admin-ajax.php", ignoreCase = true) &&
                    actionName.equals("editcomment", ignoreCase = true) ->
                "https://4pda.to/wp-admin/admin-ajax.php?action=editcomment"
            else -> "https://4pda.to/wp-admin/comment.php"
        }
    }

    private fun extractModerationNonce(source: String): Pair<String, String>? {
        moderationNonceFieldNames.forEach { name ->
            extractHiddenInputValue(source, name)?.takeIf { it.isNotBlank() }?.let { return name to it }
        }
        return null
    }

    private fun extractCommentTextField(source: String): Pair<String, String>? {
        commentTextFieldNames.forEach { name ->
            extractTextareaValue(source, name)?.let { return name to it }
            extractInputValue(source, name)?.let { return name to it }
        }
        textareaByIdRegex.find(source)?.let { match ->
            val id = match.groupValues.getOrNull(1).orEmpty().lowercase()
            val text = decodeArticleHtmlText(match.groupValues.getOrNull(2).orEmpty())
            val fieldName = when (id) {
                "replycontent" -> "content"
                "comment" -> "comment"
                else -> "content"
            }
            return fieldName to text
        }
        return null
    }

    private fun extractTextareaValue(source: String, name: String): String? {
        val escaped = Regex.escape(name)
        val pattern = Pattern.compile(
                """<textarea\b[^>]*\bname=["']$escaped["'][^>]*>([\s\S]*?)</textarea>""",
                Pattern.CASE_INSENSITIVE
        )
        val match = pattern.matcher(source)
        return if (match.find()) decodeArticleHtmlText(match.group(1).orEmpty()) else null
    }

    private fun hasModerationNonce(formAction: Comment.Action): Boolean =
            formAction.fields.keys.any { isModerationNonceField(it) } &&
                    formAction.fields.entries.any { isModerationNonceField(it.key) && it.value.isNotBlank() }

    private fun extractHiddenInputValue(source: String, name: String): String? =
            extractInputValue(source, name)

    private fun extractInputValue(source: String, name: String): String? {
        val escaped = Regex.escape(name)
        val patterns = listOf(
                Pattern.compile("""name=["']$escaped["'][^>]*value=["']([^"']*)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""value=["']([^"']*)["'][^>]*name=["']$escaped["']""", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val match = pattern.matcher(source)
            if (match.find()) {
                return decodeArticleHtmlText(match.group(1).orEmpty()).takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    fun parseCommentDeleteAction(source: String?): Comment.Action? {
        if (source.isNullOrBlank()) return null
        val document = Parser.parse(source)
        var fallbackForm: Comment.Action? = null
        walkElements(document) { node ->
            if (!node.name.orEmpty().equals("form", ignoreCase = true)) return@walkElements
            val formAction = parseCommentFormAction(node, 0, 0) ?: return@walkElements
            val marker = listOf(
                    formAction.url.orEmpty(),
                    node.getAttribute("class").orEmpty(),
                    node.getAttribute("id").orEmpty(),
                    formAction.fields["action"].orEmpty(),
                    Parser.getHtml(node, true)
            ).joinToString(" ").lowercase()
            if (isDeleteMarker(marker)) {
                fallbackForm = formAction.withType(Comment.Action.Type.DELETE, requiresConfirmation = true)
                return@walkElements
            }
            if (fallbackForm == null && formAction.fields.keys.any { isDeleteField(it) }) {
                fallbackForm = formAction.withType(Comment.Action.Type.DELETE, requiresConfirmation = true)
            }
        }
        return fallbackForm?.withType(Comment.Action.Type.DELETE, requiresConfirmation = true)
    }

    private fun isWordPressCommentEditForm(formAction: Comment.Action): Boolean {
        if (!formAction.fields.keys.any { isCommentTextField(it) }) return false
        if (formAction.fields["action"].orEmpty().equals("editedcomment", ignoreCase = true)) return true
        return hasCommentIdField(formAction)
    }

    private fun hasCommentIdField(formAction: Comment.Action): Boolean =
            formAction.fields.keys.any { isDeleteField(it) } ||
                    formAction.fields["c"]?.toIntOrNull()?.let { it > 0 } == true

    fun parseReputationAction(source: String?, type: Comment.Action.Type, userId: Int): Comment.Action? {
        if (source.isNullOrBlank()) return null
        val document = Parser.parse(source)
        var fallbackForm: Comment.Action? = null
        walkElements(document) { node ->
            if (!node.name.orEmpty().equals("form", ignoreCase = true)) return@walkElements
            val formAction = parseCommentFormAction(node, 0, userId) ?: return@walkElements
            val marker = listOf(
                    formAction.url.orEmpty(),
                    node.getAttribute("class").orEmpty(),
                    node.getAttribute("id").orEmpty(),
                    formAction.fields["act"].orEmpty(),
                    formAction.fields["type"].orEmpty(),
                    Parser.getHtml(node, true)
            ).joinToString(" ").lowercase()
            if (fallbackForm == null && hasAnyMarker(marker, "act=rep", "репутац", "reputation")) {
                fallbackForm = prepareReputationSubmitAction(formAction, userId, type)
            }
            val parsedType = formAction.fields["type"].orEmpty()
            if (formAction.fields["act"].equals("rep", ignoreCase = true) &&
                    ((type == Comment.Action.Type.REPUTATION_PLUS && hasAnyMarker(parsedType, "add")) ||
                            (type == Comment.Action.Type.REPUTATION_MINUS && hasAnyMarker(parsedType, "minus")))) {
                fallbackForm = prepareReputationSubmitAction(formAction, userId, type)
                return@walkElements
            }
        }
        return fallbackForm
    }


    private fun recurseComments(karmaMap: SparseArray<Comment.Karma>, root: Node, parentComment: Comment, argLevel: Int): Comment {
        var level = argLevel
        val rootComments = Parser.findNode(root, "ul", "class", "comment-list")
                ?: Parser.findNode(root, "ul", "class", "comments-list")
                ?: Parser.findNode(root, "ol", "class", "comment-list")
                ?: Parser.findNode(root, "ol", "class", "comments-list")
                ?: findUlCommentList(root)
        if (rootComments == null) {
            return parentComment
        }
        var commentNodes = Parser.findChildNodes(rootComments, "li", null, "")
        if (commentNodes.isEmpty()) {
            commentNodes = Parser.findChildNodes(rootComments, "div", "id", "comment-")
        }

        for (commentNode in commentNodes) {
            val comment = Comment()

            var id: String?
            var userId: String?
            var userNick: String?
            var date: String?
            var content: String?
            var matcher: Matcher
            val anchorNode = findCommentAnchorNode(commentNode) ?: continue

            id = anchorNode.getAttribute("id")
            commentNumericIdFromAttribute(id)?.let { comment.id = it }
            if (comment.id <= 0) {
                commentNumericIdFromAttribute(anchorNode.getAttribute("data-comment-id"))?.let { comment.id = it }
            }
            if (comment.id <= 0 && id != null) {
                matcher = patternProvider
                        .getPattern(scope.scope, scope.comment_id)
                        .matcher(id)
                if (matcher.find()) {
                    id = matcher.group(1)
                    comment.id = id?.toIntOrNull() ?: 0
                }
            }

            val deletedString = anchorNode.getAttribute("class")
            val isDeleted = deletedString != null && deletedString.contains("deleted")
            comment.isDeleted = isDeleted

            if (!isDeleted) {
                val avatarNode = Parser.findNode(commentNode, "a", "class", "comment-avatar")
                val nickNode = Parser.findNode(commentNode, "a", "class", "nickname")
                        ?: Parser.findNode(commentNode, "span", "class", "nickname")
                val metaNode = Parser.findNode(commentNode, "a", "class", "date")

                userId = avatarNode?.getAttribute("href")
                if (userId != null) {
                    matcher = patternProvider
                            .getPattern(scope.scope, scope.comment_user_id)
                            .matcher(userId)
                    if (matcher.find()) {
                        userId = matcher.group(1)
                        comment.userId = userId?.toIntOrNull() ?: 0
                    }
                }

                userNick = nickNode?.let { Parser.getHtml(it, true) }
                comment.userNick = userNick.orEmpty().articleFromHtml()

                date = metaNode?.let { Parser.ownText(metaNode).trim() }
                comment.date = date
            }

            val contentNode = findCommentContentNode(commentNode)
            content = contentNode?.let { Parser.getHtml(it, true) }
            comment.isEdited = isEditedComment(commentNode, content)
            comment.content = compactEditedMarker(
                    if (comment.isEdited) stripEditedMarker(content.orEmpty()) else content.orEmpty().trim()
            )
            comment.actions = parseCommentActions(commentNode, comment.id, comment.userId)
            if (comment.actions.profile == null && comment.userId > 0) {
                comment.actions.profile = Comment.Action("https://4pda.to/forum/index.php?showuser=${comment.userId}")
            }
            comment.level = level
            comment.karma = karmaMap.get(comment.id)
            applyCommentLikeState(comment)

            parentComment.children.add(comment)

            level++
            recurseComments(karmaMap, commentNode, comment, level)
            level--
        }

        return parentComment
    }

    fun ensureCommentLikeActions(root: Comment, articleId: Int, commentsSource: String? = null) {
        root.flattenComments().forEach { comment ->
            if (comment.isDeleted || comment.id <= 0) return@forEach
            if (comment.actions.like?.isValid() == true) {
                applyCommentLikeState(comment)
                return@forEach
            }
            val block = extractCommentBlockInSource(commentsSource, comment.id)
            fillMissingCommentLikeActionsFromHtml(comment.actions, block.orEmpty(), comment.id)
            if (comment.actions.like == null &&
                    articleId > 0 &&
                    !hasConflictingKarmaMarkup(block ?: commentsSource, comment.id)
            ) {
                fillMissingCommentLikeActions(comment.actions, comment.id, articleId)
            }
            applyCommentLikeState(comment)
        }
    }

    private fun parseCommentActions(commentNode: Node, commentId: Int, userId: Int): Comment.Actions {
        val actions = Comment.Actions()
        walkElements(commentNode) { node ->
            when (node.name.orEmpty().lowercase()) {
                "a", "button" -> {
                    parseKarmaActDataAction(node, commentId, 1)?.let { (articleId, currentCommentId) ->
                        if (actions.like == null) {
                            actions.like = buildKarmaAction(articleId, currentCommentId, 1)
                                    .withType(Comment.Action.Type.COMMENT_LIKE)
                        }
                    }
                    val href = firstNonBlank(
                            node.getAttribute("href"),
                            node.getAttribute("data-href"),
                            node.getAttribute("data-url"),
                            node.getAttribute("formaction")
                    )?.articleFromHtml()
                    val text = cleanPollText(Parser.getHtml(node, true))
                    val marker = listOf(
                            text,
                            node.getAttribute("class").orEmpty(),
                            node.getAttribute("id").orEmpty(),
                            node.getAttribute("title").orEmpty(),
                            node.getAttribute("aria-label").orEmpty(),
                            node.getAttribute("data-callfn").orEmpty(),
                            node.getAttribute("data-comment").orEmpty(),
                            node.getAttribute("data-karma-act").orEmpty(),
                            node.getAttribute("data-report-comment").orEmpty(),
                            href.orEmpty()
                    ).joinToString(" ").lowercase()
                    val action = href?.let { buildCommentAction(it) } ?: return@walkElements
                    when {
                        actions.profile == null && (marker.contains("showuser=") || marker.contains("profile")) ->
                            actions.profile = action.withType(Comment.Action.Type.PROFILE)
                        actions.edit == null && isInlineEditMarker(node, marker) -> {
                            val inlineCommentId = node.getAttribute("data-comment")?.toIntOrNull()
                                    ?: commentId.takeIf { it > 0 }
                                    ?: return@walkElements
                            val submitText = node.getAttribute("data-submit-text")
                                    ?.articleFromHtml()
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            val editableElementId = "comment-form-edit-$inlineCommentId"
                            val editableHtml = Parser.findNode(commentNode, "div", "id", editableElementId)
                                    ?.let { Parser.getHtml(it, true) }
                                    ?.articleFromHtml()
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            val articlePostId = resolveCommentArticleId(commentNode, inlineCommentId)
                            actions.edit = buildInlineCommentEditAction(
                                    source = Parser.getHtml(commentNode, true),
                                    commentId = inlineCommentId,
                                    articleId = articlePostId,
                                    inlineText = editableHtml,
                                    editableElementId = editableElementId,
                                    submitText = submitText
                            ) ?: action.withType(Comment.Action.Type.EDIT).copy(
                                    url = INLINE_COMMENT_EDIT_SUBMIT_URL,
                                    submitText = submitText,
                                    editableElementId = editableElementId,
                                    editableHtml = editableHtml,
                                    fields = linkedMapOf(
                                            "comment_ID" to inlineCommentId.toString()
                                    ).apply {
                                        if (articlePostId > 0) put("comment_post_ID", articlePostId.toString())
                                    }
                            )
                        }
                        actions.edit == null && isEditMarker(marker) ->
                            actions.edit = action.withType(Comment.Action.Type.EDIT)
                        actions.delete == null && isDeleteMarker(marker) ->
                            actions.delete = action.withType(Comment.Action.Type.DELETE, requiresConfirmation = true)
                        actions.reply == null && isReplyMarker(marker) ->
                            actions.reply = buildReplyAction(action, node, commentId)
                        actions.like == null && (isCommentKarmaAction(action, 1, commentId) || isCommentKarmaDataAction(node, commentId, 1)) ->
                            actions.like = buildCommentKarmaAction(action, node, commentId, 1)
                        actions.unlike == null && isCommentKarmaAction(action, 0, commentId) ->
                            actions.unlike = action.withType(Comment.Action.Type.COMMENT_UNLIKE)
                        actions.karmaPlus == null && isUserKarmaPlusAction(action, marker) ->
                            actions.karmaPlus = action.withType(Comment.Action.Type.KARMA_PLUS, requiresReason = action.requiresReason)
                        actions.hide == null && hasAnyMarker(marker, "hide", "collapse", "скры", "сверн") ->
                            actions.hide = action.withType(Comment.Action.Type.HIDE)
                        actions.reputationPlus == null && isReputationAction(action, true) ->
                            actions.reputationPlus = buildReputationEntryAction(action, userId, Comment.Action.Type.REPUTATION_PLUS)
                        actions.reputationMinus == null && isReputationAction(action, false) ->
                            actions.reputationMinus = buildReputationEntryAction(action, userId, Comment.Action.Type.REPUTATION_MINUS)
                        actions.report == null && hasAnyMarker(marker, "act=report", "report", "жалоб") ->
                            actions.report = action.withType(Comment.Action.Type.REPORT, requiresReason = true)
                    }
                }
                "form" -> parseCommentFormAction(node, commentId, userId)?.let { formAction ->
                    val marker = listOf(
                            formAction.url.orEmpty(),
                            node.getAttribute("class").orEmpty(),
                            node.getAttribute("id").orEmpty(),
                            formAction.fields["action"].orEmpty()
                    ).joinToString(" ").lowercase()
                    when {
                        actions.reply == null && hasAnyMarker(marker, "wp-comments-post", "comment_reply") ->
                            actions.reply = formAction.withType(Comment.Action.Type.REPLY)
                        actions.edit == null && isEditMarker(marker) ->
                            actions.edit = formAction.withType(Comment.Action.Type.EDIT)
                        actions.delete == null && isDeleteMarker(marker) ->
                            actions.delete = formAction.withType(Comment.Action.Type.DELETE, requiresConfirmation = true)
                        actions.report == null && hasAnyMarker(marker, "act=report", "report") ->
                            actions.report = formAction.withType(Comment.Action.Type.REPORT, requiresReason = true)
                        actions.reputationPlus == null && isReputationAction(formAction, true) ->
                            actions.reputationPlus = prepareReputationSubmitAction(formAction, userId, Comment.Action.Type.REPUTATION_PLUS)
                        actions.reputationMinus == null && isReputationAction(formAction, false) ->
                            actions.reputationMinus = prepareReputationSubmitAction(formAction, userId, Comment.Action.Type.REPUTATION_MINUS)
                    }
                }
            }
            parseKarmaPairAttribute(node.getAttribute("data-karma"), commentId)
                    ?: parseKarmaPairAttribute(node.getAttribute("data-karma-actions"), commentId)
                    ?: parseKarmaDataAction(node, commentId)
                    ?.let { (articleId, currentCommentId) ->
                        fillMissingCommentLikeActions(actions, currentCommentId, articleId)
                    }
        }
        fillMissingCommentLikeActionsFromHtml(actions, Parser.getHtml(commentNode, true), commentId)
        val articleId = resolveCommentArticleId(commentNode, commentId)
        if (articleId > 0) {
            fillMissingCommentLikeActions(actions, commentId, articleId)
        }
        return actions
    }

    private fun fillMissingCommentLikeActions(
            actions: Comment.Actions,
            commentId: Int,
            articleId: Int,
    ) {
        if (commentId <= 0 || articleId <= 0) return
        if (actions.like == null) {
            actions.like = buildKarmaAction(articleId, commentId, 1)
                    .withType(Comment.Action.Type.COMMENT_LIKE)
        }
        if (actions.unlike == null) {
            actions.unlike = buildKarmaAction(articleId, commentId, 0)
                    .withType(Comment.Action.Type.COMMENT_UNLIKE)
        }
    }

    private fun fillMissingCommentLikeActionsFromHtml(
            actions: Comment.Actions,
            html: String,
            commentId: Int,
    ) {
        if (commentId <= 0 || actions.like?.isValid() == true) return
        extractKarmaPairFromHtml(html, commentId)?.let { (articleId, currentCommentId) ->
            fillMissingCommentLikeActions(actions, currentCommentId, articleId)
        }
    }

    private fun extractKarmaPairFromHtml(html: String, commentId: Int): Pair<Int, Int>? {
        if (html.isBlank() || commentId <= 0) return null
        dataKarmaAttrRegex.findAll(html).forEach { match ->
            val articleId = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val dataCommentId = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            if (dataCommentId == commentId) return articleId to dataCommentId
        }
        dataKarmaActLikeRegex.findAll(html).forEach { match ->
            val articleId = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val dataCommentId = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            if (dataCommentId == commentId) return articleId to dataCommentId
        }
        return null
    }

    private fun hasConflictingKarmaMarkup(source: String?, commentId: Int): Boolean {
        val html = source.orEmpty()
        if (html.isBlank() || commentId <= 0) return false
        karmaPageCommentIdRegex.findAll(html).forEach { match ->
            val actionCommentId = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            if (actionCommentId != commentId) return true
        }
        dataKarmaAttrRegex.findAll(html).forEach { match ->
            val dataCommentId = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            if (dataCommentId != commentId) return true
        }
        dataKarmaActLikeRegex.findAll(html).forEach { match ->
            val dataCommentId = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            if (dataCommentId != commentId) return true
        }
        return false
    }

    private fun extractCommentBlockInSource(source: String?, commentId: Int): String? {
        val html = source.orEmpty()
        if (html.isBlank() || commentId <= 0) return null
        val openings = commentOpenTagRegex.findAll(html)
                .mapNotNull { match ->
                    val id = match.groupValues.getOrNull(2)?.toIntOrNull()
                            ?: match.groupValues.getOrNull(3)?.toIntOrNull()
                    id?.takeIf { it > 0 }?.let { match.range.first to it }
                }
                .toList()
        val index = openings.indexOfFirst { (_, id) -> id == commentId }
        if (index < 0) return null
        val start = openings[index].first
        val end = openings.getOrNull(index + 1)?.first ?: html.length
        return html.substring(start, end)
    }

    private fun parseKarmaPairAttribute(raw: String?, commentId: Int): Pair<Int, Int>? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val match = dataKarmaRegex.find(value) ?: return null
        val articleId = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val dataCommentId = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        if (commentId > 0 && dataCommentId != commentId) return null
        return articleId to dataCommentId
    }

    private fun applyCommentLikeState(comment: Comment) {
        val karma = comment.karma ?: Comment.Karma().also { comment.karma = it }
        comment.likedByMe = karma.status == Comment.Karma.LIKED
        comment.likeCount = karma.count
        comment.likeAction = comment.actions.like
        comment.unlikeAction = comment.actions.unlike
        comment.toggleAction = comment.actions.toggleLike
    }

    private data class AuthorDateKey(val userId: Int, val date: String)

    private fun Comment.flattenComments(): List<Comment> =
            children.flatMap { listOf(it) + it.flattenComments() }

    private fun Comment.hasReputationActionsDeep(): Boolean =
            hasReputationActions() || children.any { it.hasReputationActionsDeep() }

    private fun Comment.hasDesktopSupplementalActionsDeep(): Boolean =
            hasDesktopSupplementalActions() || children.any { it.hasDesktopSupplementalActionsDeep() }

    private fun Comment.hasOwnModerationActionsDeep(): Boolean =
            hasOwnModerationActions() || children.any { it.hasOwnModerationActionsDeep() }

    private fun Comment.hasEditActionsDeep(): Boolean =
            actions.edit?.isValid() == true || children.any { it.hasEditActionsDeep() }

    private fun Comment.hasReputationActions(): Boolean =
            actions.reputationPlus?.isValid() == true ||
                    actions.reputationMinus?.isValid() == true

    private fun Comment.hasOwnModerationActions(): Boolean =
            actions.edit?.isValid() == true ||
                    actions.delete?.isValid() == true

    private fun Comment.hasActionableOwnModeration(): Boolean =
            actions.edit?.isActionableModeration() == true ||
                    actions.delete?.isActionableModeration() == true

    fun Comment.Action.hasInlineEditPayload(): Boolean =
            !editableHtml.isNullOrBlank() || !editableElementId.isNullOrBlank()

    private fun Comment.Action.isActionableModeration(): Boolean {
        if (!isValid()) return false
        if (fields.keys.any { isModerationNonceField(it) } || fields.keys.any { isDeleteField(it) }) return true
        if (fields.keys.any { isCommentTextField(it) }) return true
        return url.orEmpty().contains("_wpnonce=", ignoreCase = true)
    }

    private fun Comment.hasDesktopSupplementalActions(): Boolean =
            hasReputationActions() || hasOwnModerationActions()

    private fun mergeMissingCommentAction(
            current: Comment.Action?,
            fallback: Comment.Action?,
    ): Comment.Action? = current?.takeIf { it.isValid() } ?: fallback?.takeIf { it.isValid() }?.copy()

    private fun Comment.mergeDesktopActions(
            byId: Map<Int, List<Comment>>,
            byAuthorDate: Map<AuthorDateKey, List<Comment>>
    ) {
        children.forEach { comment ->
            val desktop = when {
                comment.id > 0 -> byId[comment.id]?.singleOrNull()
                comment.userId > 0 && !comment.date.isNullOrBlank() ->
                    byAuthorDate[AuthorDateKey(comment.userId, normalizeCommentDate(comment.date))]?.singleOrNull()
                else -> null
            }
            if (desktop != null) {
                comment.actions.reputationPlus = mergeMissingCommentAction(
                        comment.actions.reputationPlus,
                        desktop.actions.reputationPlus
                )
                comment.actions.reputationMinus = mergeMissingCommentAction(
                        comment.actions.reputationMinus,
                        desktop.actions.reputationMinus
                )
                comment.actions.like = mergeMissingCommentAction(comment.actions.like, desktop.actions.like)
                comment.actions.unlike = mergeMissingCommentAction(comment.actions.unlike, desktop.actions.unlike)
                comment.actions.reply = mergeMissingCommentAction(comment.actions.reply, desktop.actions.reply)
                comment.actions.report = mergeMissingCommentAction(comment.actions.report, desktop.actions.report)
                comment.actions.hide = mergeMissingCommentAction(comment.actions.hide, desktop.actions.hide)
                comment.actions.karmaPlus = mergeMissingCommentAction(comment.actions.karmaPlus, desktop.actions.karmaPlus)
                comment.actions.profile = mergeMissingCommentAction(comment.actions.profile, desktop.actions.profile)
                desktop.actions.edit
                        ?.takeIf { it.isActionableModeration() }
                        ?.takeIf { comment.actions.edit?.isActionableModeration() != true }
                        ?.let { comment.actions.edit = it.copy() }
                desktop.actions.delete
                        ?.takeIf { it.isActionableModeration() }
                        ?.takeIf { comment.actions.delete?.isActionableModeration() != true }
                        ?.let { comment.actions.delete = it.copy() }
                applyCommentLikeState(comment)
            }
            comment.mergeDesktopActions(byId, byAuthorDate)
        }
    }

    private fun normalizeCommentDate(date: String?): String =
            date.orEmpty()
                    .replace(Regex("""\s+"""), " ")
                    .trim()

    private fun parseKarmaDataAction(node: Node, commentId: Int): Pair<Int, Int>? =
            parseKarmaPairAttribute(node.getAttribute("data-karma"), commentId)

    private fun parseKarmaActDataAction(node: Node, commentId: Int, vote: Int): Pair<Int, Int>? {
        val raw = node.getAttribute("data-karma-act") ?: return null
        val parts = raw.split("-")
        if (parts.size != 3 || parts[0].toIntOrNull() != vote) return null
        val articleId = parts[1].toIntOrNull() ?: return null
        val dataCommentId = parts[2].toIntOrNull() ?: return null
        if (commentId > 0 && dataCommentId != commentId) return null
        return articleId to dataCommentId
    }

    private fun isCommentKarmaDataAction(node: Node, commentId: Int, vote: Int): Boolean =
            parseKarmaActDataAction(node, commentId, vote) != null

    private fun buildCommentKarmaAction(action: Comment.Action, node: Node, commentId: Int, vote: Int): Comment.Action =
            parseKarmaActDataAction(node, commentId, vote)
                    ?.let { (articleId, currentCommentId) -> buildKarmaAction(articleId, currentCommentId, vote) }
                    ?: action.withType(if (vote == 0) Comment.Action.Type.COMMENT_UNLIKE else Comment.Action.Type.COMMENT_LIKE)

    private fun isReplyMarker(marker: String): Boolean =
            hasAnyMarker(marker, "commentform_move", "comment-reply", "comment_reply", "ответить")

    private fun isInlineEditMarker(node: Node, marker: String): Boolean {
        if (!hasAnyMarker(marker, "commentform_move")) return false
        return node.getAttribute("data-editcomment")?.trim() == "1"
    }

    private fun resolveCommentArticleId(commentNode: Node, commentId: Int): Int {
        var articleId = 0
        walkElements(commentNode) { node ->
            if (articleId > 0) return@walkElements
            parseKarmaActDataAction(node, commentId, 1)?.first?.let { articleId = it }
                    ?: parseKarmaActDataAction(node, commentId, 0)?.first?.let { articleId = it }
            parseKarmaDataAction(node, commentId)?.first?.let { articleId = it }
        }
        return articleId
    }

    private fun buildReplyAction(action: Comment.Action, node: Node, commentId: Int): Comment.Action? {
        val replyCommentId = node.getAttribute("data-comment")
                ?.toIntOrNull()
                ?: commentId.takeIf { it > 0 }
                ?: return null
        return action.withType(Comment.Action.Type.REPLY).copy(
                fields = linkedMapOf("comment_reply_ID" to replyCommentId.toString())
        )
    }

    private fun buildKarmaAction(articleId: Int, commentId: Int, vote: Int): Comment.Action =
            Comment.Action(
                    url = "https://4pda.to/pages/karma?p=$articleId&c=$commentId&v=$vote",
                    type = if (vote == 0) Comment.Action.Type.COMMENT_UNLIKE else Comment.Action.Type.COMMENT_LIKE
            )

    private fun parseCommentFormAction(form: Node, commentId: Int, userId: Int): Comment.Action? {
        val actionUrl = firstNonBlank(form.getAttribute("action"), form.getAttribute("data-action"))
                ?.articleFromHtml()
                ?: return null
        val normalizedUrl = normalize4pdaUrl(actionUrl)
                ?: actionUrl.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
                ?: actionUrl
        return Comment.Action(
                url = normalizedUrl,
                method = form.getAttribute("method")?.uppercase()?.takeIf { it == Comment.Action.METHOD_GET } ?: Comment.Action.METHOD_POST,
                fields = collectFormFields(form).apply {
                    if (!containsKey("comment_reply_ID") && commentId > 0 && actionUrl.contains("wp-comments-post", ignoreCase = true)) {
                        put("comment_reply_ID", commentId.toString())
                    }
                    if (!containsKey("mid") && userId > 0 &&
                            (actionUrl.contains("act=rep", ignoreCase = true) || get("act").equals("rep", ignoreCase = true))) {
                        put("mid", userId.toString())
                    }
                }
        )
    }

    private fun buildCommentAction(rawUrl: String): Comment.Action? {
        val url = normalize4pdaUrl(rawUrl) ?: rawUrl.takeIf {
            it.startsWith("#") || it.startsWith("javascript:", ignoreCase = true)
        } ?: return null
        return Comment.Action(url = url)
    }

    private fun isCommentKarmaAction(action: Comment.Action, vote: Int, commentId: Int = 0): Boolean {
        if (!action.url.orEmpty().contains("/pages/karma", ignoreCase = true)) return false
        if (actionField(action, "v") != vote.toString()) return false
        val actionCommentId = actionField(action, "c")?.toIntOrNull()
        return commentId <= 0 || actionCommentId == null || actionCommentId == commentId
    }

    private fun isUserKarmaPlusAction(action: Comment.Action, marker: String): Boolean {
        val url = action.url.orEmpty()
        if (url.contains("/pages/karma", ignoreCase = true)) return false
        val looksLikeKarma = hasAnyMarker(marker, "pluskarma", "karma-plus", "karma_plus", "плюс к карме")
        if (!looksLikeKarma) return false
        return !actionField(action, "mid").isNullOrBlank() ||
                !actionField(action, "user").isNullOrBlank() ||
                !actionField(action, "user_id").isNullOrBlank()
    }

    private fun isReputationAction(action: Comment.Action, positive: Boolean): Boolean {
        val url = action.url.orEmpty()
        if (!url.contains("/forum/index.php", ignoreCase = true) ||
                actionField(action, "act")?.equals("rep", ignoreCase = true) != true) {
            return false
        }
        val type = actionField(action, "type").orEmpty()
                .ifBlank { actionField(action, "view").orEmpty().removePrefix("win_") }
        val expectedTypes = if (positive) {
            setOf("add")
        } else {
            setOf("minus")
        }
        return expectedTypes.any { it.equals(type, ignoreCase = true) } &&
                !actionField(action, "mid").isNullOrBlank()
    }

    private fun buildReputationEntryAction(action: Comment.Action, userId: Int, type: Comment.Action.Type): Comment.Action? {
        val fields = LinkedHashMap(parseQueryFields(action.url.orEmpty()))
        if (!fields["act"].equals("rep", ignoreCase = true)) return null
        fields["type"] = fields["type"].orEmpty()
                .ifBlank { fields["view"].orEmpty().removePrefix("win_") }
        if (fields["mid"].isNullOrBlank() && userId > 0) {
            fields["mid"] = userId.toString()
        }
        if (fields["mid"].isNullOrBlank() || fields["type"].isNullOrBlank()) return null
        return Comment.Action(
                url = action.url,
                method = Comment.Action.METHOD_GET,
                fields = fields,
                type = type,
                requiresReason = true,
                reasonFieldName = "message"
        )
    }

    private fun prepareReputationSubmitAction(action: Comment.Action, userId: Int, type: Comment.Action.Type): Comment.Action? {
        val fields = LinkedHashMap(action.fields)
        if (fields["mid"].isNullOrBlank() && userId > 0) {
            fields["mid"] = userId.toString()
        }
        val reasonField = fields.keys.firstOrNull { isReputationReasonField(it) } ?: "message"
        return action.copy(
                type = type,
                requiresReason = true,
                reasonFieldName = reasonField,
                fields = fields
        )
    }

    private fun Comment.Action.withType(
            type: Comment.Action.Type,
            requiresReason: Boolean = false,
            requiresConfirmation: Boolean = false
    ): Comment.Action =
            copy(
                    type = type,
                    token = fields["_wpnonce"] ?: fields["auth_key"] ?: token,
                    requiresReason = requiresReason,
                    reasonFieldName = reasonFieldName,
                    requiresConfirmation = requiresConfirmation
            )

    private fun actionField(action: Comment.Action, name: String): String? =
            action.fields[name] ?: parseQueryFields(action.url.orEmpty())[name]

    private fun parseQueryFields(url: String): LinkedHashMap<String, String> {
        val query = url.substringAfter("?", missingDelimiterValue = "")
                .substringBefore("#")
                .takeIf { it.isNotBlank() }
                ?: return LinkedHashMap()
        val fields = LinkedHashMap<String, String>()
        query.split("&").forEach { part ->
            val key = part.substringBefore("=", missingDelimiterValue = "")
                    .replace("&amp;", "&")
                    .takeIf { it.isNotBlank() }
                    ?: return@forEach
            val value = part.substringAfter("=", missingDelimiterValue = "")
            fields[decodeUrlComponent(key)] = decodeUrlComponent(value)
        }
        return fields
    }

    private fun decodeUrlComponent(value: String): String =
            runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun collectFormFields(form: Node): LinkedHashMap<String, String> {
        val fields = LinkedHashMap<String, String>()
        walkElements(form) { node ->
            if (!node.name.orEmpty().equals("input", ignoreCase = true) &&
                    !node.name.orEmpty().equals("textarea", ignoreCase = true)) {
                return@walkElements
            }
            val name = node.getAttribute("name")?.takeIf { it.isNotBlank() } ?: return@walkElements
            val value = if (node.name.orEmpty().equals("textarea", ignoreCase = true)) {
                Parser.getHtml(node, true)
            } else {
                node.getAttribute("value").orEmpty()
            }
            fields[name] = value.articleFromHtml().orEmpty()
        }
        return fields
    }

    private fun walkElements(node: Node, action: (Node) -> Unit) {
        if (Parser.isNotElement(node)) return
        action(node)
        node.getNodes().forEach { walkElements(it, action) }
    }

    private fun isEditedComment(commentNode: Node, content: String?): Boolean {
        val html = Parser.getHtml(commentNode, false)
        return hasAnyMarker(commentNode.getAttribute("class").orEmpty().lowercase(), "edited", "modified") ||
                commentEditedTextRegex.containsMatchIn(cleanPollText(content.orEmpty())) ||
                commentEditedTextRegex.containsMatchIn(cleanPollText(html))
    }

    private fun stripEditedMarker(content: String): String =
            commentEditedWrapperRegex.replace(content, "").let {
                commentEditedHtmlRegex.replace(it, "")
            }.let {
                emptyCommentEditedWrapperRegex.replace(it, "")
            }.trim()

    private fun compactEditedMarker(content: String): String =
            content.takeIf { it.isNotBlank() }.orEmpty()

    private fun firstNonBlank(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() }

    private fun hasAnyMarker(source: String, vararg markers: String): Boolean =
            markers.any { source.contains(it, ignoreCase = true) }

    private fun isReputationMarker(marker: String): Boolean =
            hasAnyMarker(marker, "act=rep")

    private fun isEditMarker(marker: String): Boolean =
            hasAnyMarker(marker, "editcomment", "editedcomment", "comment-edit", "comment_edit", "edit-comment", "act=edit", "action=edit", " редакт")

    private fun isDeleteMarker(marker: String): Boolean =
            hasAnyMarker(marker, "deletecomment", "trashcomment", "comment-delete", "comment_delete", "delete-comment", "act=delete", "action=delete", " удалить", "trash")

    private fun isDeleteField(name: String): Boolean =
            name.equals("comment_ID", ignoreCase = true) ||
                    name.equals("c", ignoreCase = true) ||
                    name.equals("delete", ignoreCase = true)

    private fun isCommentTextField(name: String): Boolean =
            name.equals("comment", ignoreCase = true) ||
                    name.equals("content", ignoreCase = true) ||
                    name.equals("message", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true) ||
                    name.equals("newcomment_content", ignoreCase = true)

    private fun isModerationNonceField(name: String): Boolean =
            name.equals("_wpnonce", ignoreCase = true) ||
                    name.equals("_ajax_nonce-replyto-comment", ignoreCase = true) ||
                    name.equals("_ajax_nonce", ignoreCase = true) ||
                    name.equals("wpnonce", ignoreCase = true)

    private fun isReputationReasonField(name: String): Boolean =
            name.equals("message", ignoreCase = true) ||
                    name.equals("reason", ignoreCase = true) ||
                    name.equals("comment", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true)

    private fun String?.articleFromHtml(): String? =
            this?.let { decodeArticleHtmlText(it) }

    private fun decodeArticleHtmlText(source: String): String =
            htmlEntityRegex.replace(
                    source
                            .replace(Regex("""(?is)<br\s*/?>"""), "\n")
                            .replace(Regex("""(?is)</(?:p|div|li|h[1-6])\s*>"""), "\n")
                            .replace(Regex("""(?is)<[^>]+>"""), "")
            ) { match ->
                when (val entity = match.groupValues[1]) {
                    "amp" -> "&"
                    "quot" -> "\""
                    "apos" -> "'"
                    "lt" -> "<"
                    "gt" -> ">"
                    "nbsp" -> " "
                    "laquo" -> "«"
                    "raquo" -> "»"
                    "mdash" -> "—"
                    "ndash" -> "–"
                    else -> decodeNumericHtmlEntity(entity) ?: match.value
                }
            }

    private fun decodeNumericHtmlEntity(entity: String): String? {
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()
            else -> null
        } ?: return null
        return runCatching { String(Character.toChars(codePoint)) }.getOrNull()
    }

    private fun articleHtmlEncode(value: String): String =
            buildString(value.length) {
                value.forEach { char ->
                    when (char) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&#39;")
                        else -> append(char)
                    }
                }
            }

}
