package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.entity.remote.theme.TopicForumPathItem

/**
 * Путь темы по разделам форума («хлебные крошки») из навигационной строки страницы темы.
 *
 * 4PDA отдаёт её как `<div class="navstrip">` со ссылками от корня к НЕПОСРЕДСТВЕННОМУ разделу темы:
 * `4PDA (act=idx) > Android (showforum=281) > Android - Устройства (showforum=269) > OnePlus (showforum=1061)`.
 * Самой темы в строке нет — только предки, что и нужно для перехода «на уровень выше».
 *
 * Раньше приложение знало лишь [ThemePage.forumId] (последнее звено), поэтому «Открыть форум темы»
 * умело подниматься ровно на один уровень. Полный путь даёт переход в любой раздел цепочки.
 *
 * Корневой пункт (`act=idx`) отдаётся с [TopicForumPathItem.forumId] = 0 — это список форумов, а не раздел.
 */
object TopicForumPathParser {

    // Выдача, которую получает приложение, помечает строку через id (`<div id="navstrip">`), а полный
    // скин сайта — через class. Принимаем оба: атрибут различается по версии вёрстки, смысл один.
    private val NAVSTRIP = Regex("""(?is)<div[^>]*\b(?:id|class)\s*=\s*["'][^"']*\bnavstrip\b[^"']*["'][^>]*>(.*?)</div>""")
    private val ANCHOR = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""")
    private val HREF = Regex("""(?i)href\s*=\s*["']([^"']*)["']""")
    private val SHOWFORUM = Regex("""(?i)showforum=(\d+)""")
    private val ACT_IDX = Regex("""(?i)act=idx""")
    private val TAGS = Regex("""(?is)<[^>]+>""")

    /**
     * @return цепочка от корня форума к разделу темы; пустой список, если навигационной строки нет
     * (гостевая заглушка, 404, урезанная вёрстка) — вызывающий тогда остаётся на прежнем поведении.
     */
    fun parse(html: String): List<TopicForumPathItem> {
        val strip = NAVSTRIP.find(html)?.groupValues?.getOrNull(1) ?: return emptyList()
        val items = ArrayList<TopicForumPathItem>()
        ANCHOR.findAll(strip).forEach { match ->
            val attrs = match.groupValues.getOrNull(1).orEmpty()
            val title = TAGS.replace(match.groupValues.getOrNull(2).orEmpty(), "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
            if (title.isEmpty()) return@forEach
            val href = HREF.find(attrs)?.groupValues?.getOrNull(1).orEmpty()
            val forumId = SHOWFORUM.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
            when {
                forumId != null && forumId > 0 -> items.add(TopicForumPathItem(title, forumId))
                ACT_IDX.containsMatchIn(href) -> items.add(TopicForumPathItem(title, 0))
                // Прочие ссылки строки (иконка «домой», служебные) пропускаем: в путь идут только
                // корень и разделы.
            }
        }
        return items
    }
}
