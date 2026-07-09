package forpdateam.ru.forpda.notifications.hatwatch

import org.jsoup.Jsoup
import java.net.URLDecoder

/**
 * Извлекает из HTML шапки темы множество прикреплённых `.apk`-файлов.
 *
 * Ключ элемента — стабильный attach-id из ссылки скачивания `…/dl/post/<id>/<name>` (или `?id=<id>`),
 * который меняется ТОЛЬКО при загрузке нового файла. Именно по появлению нового id детектор
 * [HatVersionWatcher] понимает, что в шапку добавили новую версию — без хрупкого сравнения всего
 * HTML шапки (счётчики/спойлеры/дата не влияют).
 *
 * Чистая функция (без сети/контекста) — легко тестировать на живой разметке.
 */
object HatApkExtractor {

    data class ApkRef(val id: String, val name: String)

    // Ссылки-вложения ровно как их выделяет нативный рендерер постов (PostBodyRenderer):
    // «кнопка-загрузки» тоже <a class="ipb-attach attach-file">.
    private const val ATTACH_SELECTOR =
            "a.ipb-attach.attach-file, a.ipb-attach:not(.attach-img):not(.attach-image)"

    private val DL_POST_ID = Regex("""/dl/post/(\d+)/""")
    private val QUERY_ID = Regex("""[?&](?:id|attach_id)=(\d+)""")

    fun extract(hatBodyHtml: String?): List<ApkRef> {
        if (hatBodyHtml.isNullOrBlank()) return emptyList()
        val doc = Jsoup.parse(hatBodyHtml)
        // LinkedHashMap: сохраняем порядок и схлопываем дубликаты по id.
        val out = LinkedHashMap<String, ApkRef>()
        for (link in doc.select(ATTACH_SELECTOR)) {
            val href = link.attr("href").trim()
            if (href.isEmpty()) continue
            val fileName = fileNameFromHref(href)
            val linkText = link.text().trim()
            if (!isApk(fileName, linkText, href)) continue
            val id = attachId(href) ?: href
            val name = when {
                !fileName.isNullOrBlank() && fileName.endsWith(".apk", true) -> fileName
                linkText.isNotBlank() -> linkText
                else -> "apk"
            }
            out[id] = ApkRef(id, name)
        }
        return out.values.toList()
    }

    private fun isApk(fileName: String?, linkText: String, href: String): Boolean {
        if (!fileName.isNullOrBlank() && fileName.endsWith(".apk", true)) return true
        if (linkText.substringBefore('?').endsWith(".apk", true)) return true
        return href.substringBefore('?').endsWith(".apk", true)
    }

    private fun attachId(href: String): String? =
            DL_POST_ID.find(href)?.groupValues?.getOrNull(1)
                    ?: QUERY_ID.find(href)?.groupValues?.getOrNull(1)

    private fun fileNameFromHref(href: String): String? {
        val path = href.substringBefore('?').trimEnd('/')
        val seg = path.substringAfterLast('/')
        if (seg.isBlank()) return null
        return runCatching { URLDecoder.decode(seg, "UTF-8") }.getOrDefault(seg)
    }
}
