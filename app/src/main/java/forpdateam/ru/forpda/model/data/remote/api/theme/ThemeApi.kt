package forpdateam.ru.forpda.model.data.remote.api.theme

import android.util.Log
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Created by radiationx on 04.08.16.
 */
class ThemeApi(
        private val webClient: IWebClient,
        private val themeParser: ThemeParser
) {

    fun getTheme(url: String, hatOpen: Boolean, pollOpen: Boolean): ThemePage {
        val response = webClient.get(url)
        val redirectUrl: String = response.redirect ?: url
        return themeParser.parsePage(response.body, redirectUrl, hatOpen, pollOpen, initialRequestUrl = url)
    }

    fun reportPost(topicId: Int, postId: Int, message: String): Boolean {
        val request = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=report&send=1&t=$topicId&p=$postId")
                .formHeader("message", URLEncoder.encode(message, "windows-1251"), true)
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
        val url = "https://4pda.to/forum/index.php?act=zmod&auth_key=${webClient.authKey}&code=postchoice&tact=delete&selectedpids=$postId"
        val response = webClient.request(NetworkRequest.Builder().url(url).xhrHeader().build())
        val body = response.body
        if (body != "ok") {
            throw Exception("Ошибка изменения репутации поста")
        }
        return true
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

    companion object {
        val elemToScrollPattern = Pattern.compile("(?:anchor=|#)([^&\\n\\=\\?\\.\\#]*)")
        val attachImagesPattern = Pattern.compile("(4pda\\.to\\/forum\\/dl\\/post\\/\\d+\\/[^\"']*?\\.(?:jpe?g|png|gif|bmp))\"?(?:[^>]*?title=\"([^\"']*?\\.(?:jpe?g|png|gif|bmp)) - [^\"']*?\")?")

        // Завершающий & не входит в match — иначе второй &p=… в строке не находится.
        private val topicUrlPostIdP = Regex("""[?&]p=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private val topicUrlPostIdPid = Regex("""[?&]pid=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private val topicUrlHighlight = Regex("""[?&]highlight=(\d+)(?=[&#]|$)""", RegexOption.IGNORE_CASE)
        private val topicUrlEntryFragment = Regex("""#entry(\d+)\b""", RegexOption.IGNORE_CASE)

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

        /**
         * Для скролла после `view=getnewpost`: фрагмент `#entry…` — то, что форум задаёт в hash после редиректа;
         * он важнее `highlight=` в query (скин часто оставляет highlight на другой пост, а целевой — в hash).
         */
        fun extractScrollPostIdFromFinalTopicUrl(url: String): String? {
            topicUrlEntryFragment.find(url)?.groupValues?.getOrNull(1)?.let { return it }
            val queryPart = url.substringBefore('#')
            topicUrlPostIdP.findAll(queryPart).lastOrNull()?.groupValues?.get(1)?.let { return it }
            topicUrlPostIdPid.findAll(queryPart).lastOrNull()?.groupValues?.get(1)?.let { return it }
            topicUrlHighlight.findAll(queryPart).lastOrNull()?.groupValues?.get(1)?.let { return it }
            return null
        }

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
            Log.d(
                    "ThemeApi",
                    "trace=${traceId.orEmpty()} ensureScrollAnchor[$source] before=$before after=${page.anchor} topicId=${page.id} url=${page.url} htmlLen=${parsedBody?.length ?: 0}"
            )
        }
    }
}
