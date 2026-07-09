package forpdateam.ru.forpda.model.data.remote.api.sitecontent

import forpdateam.ru.forpda.entity.remote.sitecontent.SiteComment
import org.jsoup.Jsoup

/**
 * Парсер страницы комментариев пользователя (`https://4pda.to/<ник>/comments/`).
 *
 * Структура сверена на живом залогин. 4pda (2026-07): `ul.comment-list > li`, в каждом `li` —
 * `span.post-title` (заголовок статьи, ТЕКСТ без ссылки), `div.content` (текст коммента),
 * `span.h-meta` (дата), а ссылка на статью — пермалинк в `span.more-meta a` с `i.icon-link`
 * (`/<год>/<мес>/<день>/<articleId>/<slug>/#comment<id>`). id статьи — ПОСЛЕДНИЙ числовой сегмент
 * пути. Проверено JS-зеркалом: 50/50 комментов с корректными id/заголовком/текстом/датой.
 */
class SiteCommentsParser {

    fun parse(html: String): List<SiteComment> {
        val doc = Jsoup.parse(html, BASE_URL)
        val items = doc.select("ul.comment-list > li")

        val out = ArrayList<SiteComment>(items.size)
        for (li in items) {
            val permalink = li.selectFirst("span.more-meta a:has(i.icon-link)")
                    ?: li.selectFirst("span.more-meta a[href]")
                    ?: continue
            // attr("href") сохраняет фрагмент `#comment<id>` (absUrl его иногда режет) — берём id коммента отсюда.
            val rawHref = permalink.attr("href")
            val href = permalink.absUrl("href").ifBlank { rawHref }
            if (href.isBlank()) continue

            val articleId = lastPathNumber(href)
            val commentId = COMMENT_ANCHOR.find(rawHref)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val title = li.selectFirst("span.post-title")?.text()?.trim().orEmpty()
            val snippet = li.selectFirst("div.content")?.text()?.trim().orEmpty()
            val date = li.selectFirst("span.h-meta")?.text()?.trim()
            val nick = li.selectFirst("a.nickname")?.text()?.trim()

            out.add(SiteComment(
                    articleId = articleId,
                    commentId = commentId,
                    articleTitle = title.ifBlank { "Статья" },
                    articleUrl = href,
                    snippet = snippet,
                    date = date,
                    nick = nick,
            ))
        }
        return out
    }

    /** Последний числовой сегмент пути = id статьи (URL вида `/год/мес/день/<id>/slug/`). */
    private fun lastPathNumber(url: String): Int {
        val path = url.substringBefore('#').substringBefore('?')
        return SEGMENT_NUMBER.findAll(path).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private companion object {
        const val BASE_URL = "https://4pda.to/"
        val SEGMENT_NUMBER = Regex("""/(\d+)(?=/)""")
        val COMMENT_ANCHOR = Regex("""#comment(\d+)""")
    }
}
