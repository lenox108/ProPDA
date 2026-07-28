package forpdateam.ru.forpda.notifications

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

/**
 * HTML сообщения QMS → одна строка для шторки.
 *
 * Отдельно от [QmsMessagePreviewLoader] и без единого обращения к Android, чтобы правила
 * (смайл-картинки, вложения, перенос строк, обрезка) проверялись обычным JVM-тестом:
 * именно здесь ломается вид уведомления, а сеть и настройки к этому отношения не имеют.
 */
object QmsPreviewText {

    /** Больше в шторку всё равно не влезает, а MessagingStyle обрезает по своему усмотрению. */
    const val MAX_LENGTH = 300

    private const val NBSP = '\u00A0'

    /**
     * @param imagePlaceholder чем заменить картинку без alt/title (локализуется вызывающим).
     */
    fun fromHtml(html: String?, imagePlaceholder: String = "[изображение]"): String {
        if (html.isNullOrBlank()) return ""
        val doc = runCatching { Jsoup.parseBodyFragment(html) }.getOrNull() ?: return ""
        doc.select("script, style").remove()
        // Цитата внутри сообщения — это чужой текст; в шторке она вытесняла бы сам ответ.
        doc.select("blockquote, .quote, .quote-block").remove()
        doc.select("img").forEach { img ->
            // Смайлы 4PDA несут свой код в alt/title (":)", ":D") — так превью читается
            // естественно. Всё остальное схлопывается в один заметный маркер.
            val alt = img.attr("alt").trim().ifBlank { img.attr("title").trim() }
            img.replaceWith(TextNode(if (alt.isNotEmpty()) " $alt " else " $imagePlaceholder "))
        }
        doc.select("br").forEach { it.replaceWith(TextNode(" ")) }
        return normalize(doc.text())
    }

    /** Схлопывает пробелы/переносы и подрезает по границе слова. */
    fun normalize(raw: String): String {
        val text = raw.replace(NBSP, ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        if (text.length <= MAX_LENGTH) return text
        val cut = text.take(MAX_LENGTH)
        val lastSpace = cut.lastIndexOf(' ')
        val body = if (lastSpace > MAX_LENGTH / 2) cut.take(lastSpace) else cut
        return body.trimEnd(' ', ',', '.', ';', ':', '-') + "…"
    }
}
