package forpdateam.ru.forpda.model.data.remote.api.attachments

import forpdateam.ru.forpda.common.MimeTypeUtil
import forpdateam.ru.forpda.entity.remote.attachments.TopicAttachment
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Парсер страницы вложений темы (`act=attach&code=showtopic&tid=…`).
 *
 * Разметка сверена на живом залогиненном 4pda (2026-07): таблица, по одной `<tr>` на вложение,
 * колонки — [иконка] | «Прикрепление» (`<a href=".../forum/dl/post/<id>/<name>">name</a>( Добавлено ДАТА )`)
 * | «Размер:» (`7 КБ`) | «Сообщение №» (id поста). Ссылки — абсолютные `https://4pda.to/forum/dl/post/…`
 * без query-токена, поэтому тап переиспускает LinkHandler.handleMedia (картинки → ImageViewer,
 * прочие файлы → загрузчик).
 *
 * Основной разбор — по строкам таблицы (даёт размер + дату). Если структура изменится и строк не
 * найдётся, откатываемся на скан всех якорей `forum/dl/post/` (устойчиво к смене вёрстки).
 */
class TopicAttachmentsParser {

    fun parse(html: String): List<TopicAttachment> {
        val doc = Jsoup.parse(html, BASE_URL)
        val result = LinkedHashMap<String, TopicAttachment>() // dedup по url, порядок сохраняем

        // --- Основной путь: строки таблицы ---
        for (row in doc.select("tr")) {
            val anchor = row.selectFirst("a[href*=/forum/dl/post/]") ?: continue
            addFrom(anchor, row, result)
        }

        // --- Fallback: если таблица не распозналась — просто все якоря скачивания ---
        if (result.isEmpty()) {
            for (anchor in doc.select("a[href*=/forum/dl/post/]")) {
                addFrom(anchor, anchor.parent(), result)
            }
        }

        return result.values.toList()
    }

    private fun addFrom(anchor: Element, container: Element?, out: LinkedHashMap<String, TopicAttachment>) {
        val url = normalize(anchor.absUrl("href").ifBlank { anchor.attr("href") })
        if (url.isBlank() || out.containsKey(url)) return

        val name = anchor.text().trim().ifBlank { fileNameFromUrl(url) }
        val isImage = MimeTypeUtil.isImage(name.ifBlank { url })
        val sizeText = container?.let { findSizeCell(it) }
        val meta = container?.let { extractAddedMeta(it, name) }

        out[url] = TopicAttachment(
                name = name.ifBlank { url },
                url = url,
                sizeText = sizeText,
                isImage = isImage,
                meta = meta,
        )
    }

    /** Ищем в строке ячейку/фрагмент вида «7 КБ», «1,2 МБ». */
    private fun findSizeCell(row: Element): String? {
        // Сначала — отдельная ячейка «Размер»: её текст целиком является размером.
        for (td in row.select("td")) {
            val text = td.text().trim()
            if (SIZE_FULL_PATTERN.matcher(text).matches()) return text
        }
        // Иначе — первое вхождение размера в тексте строки.
        val m = SIZE_PATTERN.matcher(row.text())
        return if (m.find()) m.group().trim() else null
    }

    /** «Прикрепление»-ячейка содержит `name( Добавлено ДАТА )`; достаём хвост после имени файла. */
    private fun extractAddedMeta(row: Element, name: String): String? {
        val cell = row.select("td").firstOrNull { it.selectFirst("a[href*=/forum/dl/post/]") != null }
                ?: return null
        var text = cell.text().trim()
        if (name.isNotBlank() && text.startsWith(name)) text = text.substring(name.length)
        text = text.trim().trim('(', ')').trim()
        return text.ifBlank { null }
    }

    private fun normalize(url: String): String {
        var u = url.replace("&amp;", "&").trim()
        if (u.startsWith("//")) u = "https:$u"
        else if (u.startsWith("/")) u = BASE_URL.trimEnd('/') + u
        return u
    }

    private fun fileNameFromUrl(url: String): String {
        val raw = url.substringBefore('?').substringAfterLast('/')
        return runCatching { URLDecoder.decode(raw, "CP1251") }.getOrDefault(raw)
    }

    private companion object {
        const val BASE_URL = "https://4pda.to/"
        private const val UNITS = "байтов|байт|Кб|Мб|Гб|KB|MB|GB|KiB|MiB|GiB"
        // UNICODE_CASE обязателен: без него CASE_INSENSITIVE не сворачивает кириллицу и «КБ»/«МБ»
        // (реальные страницы 4pda пишут единицы в верхнем регистре) не матчатся к «Кб»/«Мб».
        private const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        val SIZE_PATTERN: Pattern = Pattern.compile("""\d[\d.,]*\s*(?:$UNITS)""", FLAGS)
        val SIZE_FULL_PATTERN: Pattern = Pattern.compile("""^\d[\d.,]*\s*(?:$UNITS)$""", FLAGS)
    }
}
