package forpdateam.ru.forpda.common

import android.net.Uri
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

/**
 * Абсолютный URL ссылки из разметки форума (список тем, упоминания и т.д.).
 */
fun absolutizeFourPdaForumHref(raw: String): String? {
    val t = raw.trim()
        .replace("&amp;", "&")
        .replace("&#038;", "&")
        .replace("&#x26;", "&")
        .replace("&#47;", "/")
        .replace("&#x2f;", "/")
        .replace("&#x2F;", "/")
    if (t.isEmpty()) return null
    return when {
        t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
        t.startsWith("//") -> "https:$t"
        t.startsWith("/") -> "${SiteUrls.BASE_HTTPS}$t"
        t.startsWith("?", ignoreCase = true) -> "${SiteUrls.BASE_HTTPS}/forum/index.php$t"
        t.startsWith("./") -> "${SiteUrls.BASE_HTTPS}/forum/${t.removePrefix("./")}"
        t.startsWith("forum/", ignoreCase = true) -> "${SiteUrls.BASE_HTTPS}/${t}"
        t.startsWith("index.php", ignoreCase = true) -> "${SiteUrls.BASE_HTTPS}/forum/$t"
        else -> null
    }
}

/**
 * URI для открытия темы из строки списка: предпочитаем [listingHref] с сервера (редирект/перенос),
 * иначе синтетический [showtopic].
 */
fun uriForOpeningTopicFromListing(listingHref: String?, topicId: Int): Uri {
    return uriForOpeningTopicFromListing(listingHref, topicId, isRelocated = false)
}

/**
 * Вариант для открытия из списков, учитывающий «перенесённую» тему-указатель.
 *
 * Для обычных тем, если [listingHref] содержит `showtopic=...`, но он **не совпадает** с [topicId],
 * значит в строке списка есть «чужая» ссылка (иконка/кнопка/вложенный элемент) и по ней открываться нельзя —
 * в таком случае игнорируем [listingHref] и используем синтетический URL по [topicId].
 *
 * Для перенесённых тем-указателей ([isRelocated]=true) наоборот важно сохранить «серверный» href:
 * именно он 302-редиректит на новую тему.
 */
fun uriForOpeningTopicFromListing(listingHref: String?, topicId: Int, isRelocated: Boolean): Uri {
    val h = listingHref?.trim()?.replace("&amp;", "&").orEmpty()
    if (h.isNotEmpty()) {
        val abs = absolutizeFourPdaForumHref(h)
        if (abs != null) {
            if (!isRelocated) {
                val hrefTopicId = runCatching { Uri.parse(abs).getQueryParameter("showtopic")?.toIntOrNull() }.getOrNull()
                if (hrefTopicId != null && hrefTopicId != 0 && hrefTopicId != topicId) {
                    if (BuildConfig.DEBUG) {
                        Timber.tag("TopicsOpen").w(
                            "listingHref showtopic mismatch: topicId=%d hrefTopicId=%d href=%s",
                            topicId,
                            hrefTopicId,
                            abs
                        )
                    }
                } else {
                    return Uri.parse(abs)
                }
            } else {
                return Uri.parse(abs)
            }
        }
    }
    return Uri.parse("https://4pda.to/forum/index.php?showtopic=$topicId")
}

/**
 * Финальный URL открытия темы из списка с учётом «перенесённой» темы-указателя.
 *
 * Для обычных тем возвращает чистый URL, а выбор первой/непрочитанной страницы
 * применяет общий резолвер на экране темы.
 *
 * Для тем-указателей IPB (`isRelocated=true` — `&raquo;` + «перемещена:» в списке):
 * сервер 302-редиректит «голый» `?showtopic=OLD` на новый id, но 404-ит,
 * если в URL присутствует `view=getnewpost`. Поэтому **специально** оставляем URL без
 * `view=getnewpost` — он получит 302 и откроет новую тему.
 */
fun topicUrlForOpeningFromListing(listingHref: String?, topicId: Int, isRelocated: Boolean): String {
    val uri = uriForOpeningTopicFromListing(listingHref, topicId, isRelocated = isRelocated)
    return uri.toString()
}

/**
 * Нормализация URL темы при открытии из приложения:
 * — «голая» ссылка на тему → [view=getnewpost] (как явный переход к непрочитанному на сайте);
 * — [showtopic…&p=] без [view=] → [view=findpost] (позиционирование на пост);
 * — [st] только при ненулевом смещении страницы: [st=0] не отменяет getnewpost (часто встречается в ссылках).
 */
fun topicUrlWithUnreadIfPlainOpen(uri: Uri): String {
    if (uri.getQueryParameter("showtopic").isNullOrEmpty()) return uri.toString()
    if (uri.getQueryParameter("act") == "findpost") return uri.toString()
    val hasPostId = !uri.getQueryParameter("p").isNullOrEmpty() || !uri.getQueryParameter("pid").isNullOrEmpty()
    if (hasPostId && uri.getQueryParameter("view").isNullOrEmpty()) {
        return uri.buildUpon().appendQueryParameter("view", "findpost").build().toString()
    }
    if (!uri.getQueryParameter("view").isNullOrEmpty()) return uri.toString()
    val st = uri.getQueryParameter("st")
    if (isNonZeroTopicPageOffset(st)) return uri.toString()
    if (!uri.getQueryParameter("anchor").isNullOrEmpty()) return uri.toString()
    if (!uri.fragment.isNullOrEmpty()) return uri.toString()
    return uri.buildUpon()
            .appendQueryParameter("view", "getnewpost")
            .build()
            .toString()
}

private fun isNonZeroTopicPageOffset(st: String?): Boolean {
    val s = st?.trim().orEmpty()
    if (s.isEmpty()) return false
    return s.toIntOrNull()?.let { it != 0 } ?: true
}

/**
 * Для строкового URL темы: [st] считается «настоящей» пагинацией только если задано ненулевое число
 * (как в [topicUrlWithUnreadIfPlainOpen]).
 */
fun topicUrlHasNonZeroStParameter(url: String): Boolean {
    val st = try {
        Uri.parse(url).getQueryParameter("st")
    } catch (_: Exception) {
        null
    }
    return isNonZeroTopicPageOffset(st)
}

/** Non-zero `st` in forum list hrefs is a resume offset, not explicit pagination for unread opens. */
private val TOPIC_LIST_RESUME_ST_PATTERN = Regex("""(?i)([?&])st=[1-9]\d*""")

fun stripTopicListResumeSt(url: String): String {
    if (!TOPIC_LIST_RESUME_ST_PATTERN.containsMatchIn(url)) return url
    return url
            .replace(TOPIC_LIST_RESUME_ST_PATTERN) { it.groupValues[1] }
            .replace(Regex("""[?&]{2,}"""), "&")
            .replace("?&", "?")
            .trimEnd('?', '&')
}

/** `p`/`pid` in list/getnewpost hrefs often mean last-read context, not first unread — strip for unread opens. */
private val TOPIC_LAST_READ_POST_PARAM_PATTERN = Regex("""(?i)([?&])(?:p|pid)=[^&#]*""")

fun stripTopicLastReadPostParams(url: String): String {
    if (!TOPIC_LAST_READ_POST_PARAM_PATTERN.containsMatchIn(url)) return url
    val hashIdx = url.indexOf('#')
    val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
    val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
    val stripped = base
            .replace(TOPIC_LAST_READ_POST_PARAM_PATTERN, "$1")
            .replace(Regex("""[?&]{2,}"""), "&")
            .replace("?&", "?")
            .trimEnd('?', '&')
    return stripped + hash
}

/**
 * Server-side unread hint from a forum list row (favorites, topics, mentions).
 * Passed into topic open resolution as server unread fallback (priority 5).
 */
data class TopicOpenListHints(
        val unreadUrlFromList: String? = null,
        val unreadPostIdFromList: Int? = null,
        /** List row marked unread (+N, bold title, inspector) even when href lacks view=getnewpost. */
        val topicMarkedUnread: Boolean = false,
        /** Read list row resume hint — server last-read page (getlastpost), not first unread. */
        val lastReadUrlFromList: String? = null
)

private const val TOPIC_OPEN_LIST_BASE = "https://4pda.to/forum/index.php?showtopic="

fun syntheticTopicUnreadListUrl(topicId: Int): String =
        "$TOPIC_OPEN_LIST_BASE$topicId&view=getnewpost"

fun syntheticTopicLastReadListUrl(topicId: Int): String =
        "$TOPIC_OPEN_LIST_BASE$topicId&view=getlastpost"

/**
 * Build last-read navigation URL for a read favorites/topics row.
 * Site puts view=getnewpost on every row; for read opens upgrade to getlastpost.
 */
fun topicOpenListReadResumeFromListing(listingHref: String?, topicId: Int): String {
    if (topicId <= 0) return syntheticTopicLastReadListUrl(topicId)
    val href = listingHref?.trim().orEmpty()
    if (href.isEmpty()) return syntheticTopicLastReadListUrl(topicId)
    val abs = absolutizeFourPdaForumHref(href) ?: href
    val hrefTopicId = runCatching { Uri.parse(abs).getQueryParameter("showtopic")?.toIntOrNull() }.getOrNull()
    if (hrefTopicId != null && hrefTopicId != 0 && hrefTopicId != topicId) {
        return syntheticTopicLastReadListUrl(topicId)
    }
    return forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver.normalizeLastReadNavigationUrl(abs)
}

/**
 * Build list unread hints from a list row href.
 * Resume `st` from list rows is stripped — it points at last-read page, not first unread.
 *
 * When [topicMarkedUnread] is true but the href has no server unread view, synthesize getnewpost
 * so LAST_UNREAD resolution does not fall through to [list_read_no_unread_hint].
 */
fun topicOpenListHintsFromListing(
        listingHref: String?,
        topicId: Int,
        isRelocated: Boolean = false,
        topicMarkedUnread: Boolean = false
): TopicOpenListHints {
    if (topicId <= 0) return TopicOpenListHints()
    val href = listingHref?.trim().orEmpty()
    if (href.isNotEmpty() && !isRelocated) {
        val abs = absolutizeFourPdaForumHref(href) ?: href
        val hrefTopicId = runCatching { Uri.parse(abs).getQueryParameter("showtopic")?.toIntOrNull() }.getOrNull()
        if (hrefTopicId == null || hrefTopicId == 0 || hrefTopicId == topicId) {
            val lowered = abs.lowercase()
            when {
                lowered.contains("view=getnewpost") -> {
                    val unreadUrl = if (abs.contains("#entry", ignoreCase = true)) {
                        abs
                    } else {
                        stripTopicListResumeSt(stripTopicLastReadPostParams(abs))
                    }
                    return TopicOpenListHints(
                            unreadUrlFromList = unreadUrl,
                            topicMarkedUnread = topicMarkedUnread
                    )
                }
                lowered.contains("view=getlastpost") -> {
                    val unread = abs
                            .replace(Regex("(?i)([?&])view=getlastpost"), "$1view=getnewpost")
                            .let { stripTopicListResumeSt(stripTopicLastReadPostParams(it)) }
                    return TopicOpenListHints(
                            unreadUrlFromList = unread,
                            topicMarkedUnread = topicMarkedUnread
                    )
                }
            }
        }
    }
    if (topicMarkedUnread) {
        return TopicOpenListHints(
                unreadUrlFromList = syntheticTopicUnreadListUrl(topicId),
                topicMarkedUnread = true
        )
    }
    return TopicOpenListHints()
}
