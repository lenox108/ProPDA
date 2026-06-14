package forpdateam.ru.forpda.model.data.remote.api.mentions

import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import timber.log.Timber

/**
 * Совпадение regex — часто только кусок ячейки; `data-post-id` и «правильные» href бывают в родительском `<tr>`.
 */
private fun mentionRowHtmlContext(fullResponse: String, matchStart: Int, matchEnd: Int, matchWhole: String): String {
    val trOpen = fullResponse.lastIndexOf("<tr", matchStart, ignoreCase = true)
    if (trOpen < 0) return matchWhole
    val trClose = fullResponse.indexOf("</tr>", matchEnd, ignoreCase = true)
    if (trClose < 0) return matchWhole
    val end = (trClose + 5).coerceAtMost(fullResponse.length)
    return fullResponse.substring(trOpen, end)
}

private fun absolutizeForumHref(t: String): String {
    val x = t.trim()
    return when {
        x.startsWith("http://", ignoreCase = true) || x.startsWith("https://", ignoreCase = true) -> x
        x.startsWith("//") -> "https:$x"
        x.startsWith("/") -> "${SiteUrls.BASE_HTTPS}$x"
        x.startsWith("index.php", ignoreCase = true) -> "${SiteUrls.BASE_HTTPS}/forum/$x"
        x.startsWith("?") -> "${SiteUrls.BASE_HTTPS}/forum/index.php$x"
        else -> x
    }
}

/**
 * В строке списка «Ответы» первый href часто ведёт только на тему; для прокрутки к сообщению
 * нужна ссылка с findpost / view=findpost или showtopic…&p= / &pid=.
 */
internal fun preferMentionPostUrl(rowHtml: String, primaryHref: String): String {
    val normRow = rowHtml.replace("&amp;", "&")
    val normPrimary = primaryHref.replace("&amp;", "&")

    // Если primaryHref уже содержит findpost, возвращаем её сразу
    if (Regex("(?i)findpost|view=findpost").containsMatchIn(normPrimary)) {
        return normPrimary
    }

    fun firstMatchingHref(predicate: (String) -> Boolean): String? {
        Regex("""(?i)href\s*=\s*"([^"]*)"""").findAll(normRow).forEach { mr ->
            val u = mr.groupValues[1]
            if (predicate(u)) return u
        }
        Regex("""(?i)href\s*=\s*'([^']*)'""").findAll(normRow).forEach { mr ->
            val u = mr.groupValues[1]
            if (predicate(u)) return u
        }
        return null
    }

    firstMatchingHref { u -> Regex("(?i)findpost|view=findpost").containsMatchIn(u) }
            ?.let {
                val result = absolutizeForumHref(it)
                return result
            }

    firstMatchingHref { u ->
        Regex("(?i)showtopic=").containsMatchIn(u) &&
                (Regex("(?i)[?&]p=\\d+").containsMatchIn(u) || Regex("(?i)[?&]pid=\\d+").containsMatchIn(u))
    }?.let {
        val result = absolutizeForumHref(it)
        return result
    }

    Regex("""(?i)https?://[^\s"'<>]+(?:findpost|view=findpost)[^\s"'<>]*""")
            .find(normRow)
            ?.value
            ?.let {
                return it
            }

    val showtopic = Regex("""(?i)showtopic=(\d+)""").find(normRow)?.groupValues?.get(1)
            ?: Regex("""(?i)showtopic=(\d+)""").find(normPrimary)?.groupValues?.get(1)
    val postId = Regex("""(?i)[?&]p=(\d+)""").find(normRow)?.groupValues?.get(1)
            ?: Regex("""(?i)[?&]pid=(\d+)""").find(normRow)?.groupValues?.get(1)
    if (showtopic != null && postId != null) {
        val result = "${SiteUrls.BASE_HTTPS}/forum/index.php?showtopic=$showtopic&view=findpost&p=$postId"
        return result
    }

    val result = absolutizeForumHref(normPrimary)
    return result
}

class MentionsParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Mentions

    fun parse(response: String): MentionsData {
        val data = MentionsData()
        patternProvider
                .getPattern(scope.scope, scope.main)
                .matcher(response)
                .findAll { matcher ->
                    val rowHtml = mentionRowHtmlContext(
                            response,
                            matcher.start(),
                            matcher.end(),
                            matcher.group(0).orEmpty()
                    )
                    val primaryHref = matcher.group(3).orEmpty()
                    var link = preferMentionPostUrl(rowHtml, primaryHref)
                    link = patchMentionLinkIfTopicOnly(link, rowHtml)
                    val title = matcher.group(4).fromHtml()
                    data.items.add(MentionItem().apply {
                        state = if (matcher.group(1) == "read") MentionItem.STATE_READ else MentionItem.STATE_UNREAD
                        type = if (matcher.group(2).equals("Форум", ignoreCase = true)) MentionItem.TYPE_TOPIC else MentionItem.TYPE_NEWS
                        this.link = link
                        this.title = title
                        desc = matcher.group(5).fromHtml()
                        date = matcher.group(6)
                        nick = matcher.group(7).fromHtml()
                    })
                }
        data.pagination = Pagination.parseForum(response)
        return data
    }
}

/**
 * Если в строке есть id поста (data-атрибут, якорь, findpost в onclick), а в ссылке только showtopic.
 */
internal fun patchMentionLinkIfTopicOnly(link: String, rowHtml: String): String {
    if (Regex("(?i)findpost|view=findpost").containsMatchIn(link)) {
        return link
    }
    if (Regex("(?i)(?:[?&])p=\\d+").containsMatchIn(link) || Regex("(?i)(?:[?&])pid=\\d+").containsMatchIn(link)) {
        return link
    }
    val topicId = Regex("""(?i)[?&]showtopic=(\d+)""").find(link)?.groupValues?.get(1) ?: return link

    val norm = rowHtml.replace("&amp;", "&")
    val postFromData = Regex("""(?i)data-post-id\s*=\s*["']?(\d+)["']?""").find(norm)?.groupValues?.get(1)
            ?: Regex("""(?i)data-post\s*=\s*["']?(\d+)["']?""").find(norm)?.groupValues?.get(1)
    val postFromAnchor = Regex("""(?i)name\s*=\s*["']entry(\d+)["']""").find(norm)?.groupValues?.get(1)
            ?: Regex("""(?i)href\s*=\s*["']#entry(\d+)["']""").find(norm)?.groupValues?.get(1)
    val postId = postFromData ?: postFromAnchor ?: return link
    val result = "${SiteUrls.BASE_HTTPS}/forum/index.php?showtopic=$topicId&view=findpost&p=$postId"
    return result
}
