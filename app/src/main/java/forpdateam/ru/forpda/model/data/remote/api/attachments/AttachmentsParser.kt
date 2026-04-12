package forpdateam.ru.forpda.model.data.remote.api.attachments

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.text.DecimalFormat
import java.util.regex.Matcher

class AttachmentsParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.EditPost

    fun parseAttachments(response: String): List<AttachmentItem> {
        val primary = patternProvider
                .getPattern(scope.scope, scope.attachments)
                .matcher(response)
                .map {
                    fillAttachment(AttachmentItem(), it)
                }
        if (primary.isNotEmpty()) return primary
        return parseAttachmentsFallback(response)
    }

    /**
     * Если ответ форума сменился и STX-шаблон не матчится — вытаскиваем id вложений эвристиками.
     */
    private fun parseAttachmentsFallback(body: String): List<AttachmentItem> {
        val seen = LinkedHashSet<Int>()
        val out = ArrayList<AttachmentItem>()
        fun addId(id: Int) {
            if (id <= 0 || !seen.add(id)) return
            out.add(AttachmentItem().apply {
                setId(id)
                setName("attachment_$id")
                setLoadState(AttachmentItem.STATE_LOADED)
                setStatus(AttachmentItem.STATUS_READY)
            })
        }
        fun addIdsFromCommaList(raw: String) {
            for (part in raw.split(',')) {
                part.trim().toIntOrNull()?.let(::addId)
            }
        }
        // Скрытое поле при редактировании поста (как в отправляемой форме): id через запятую.
        Regex("""(?is)<input[^>]+name\s*=\s*["']file-list["'][^>]*value\s*=\s*["']([^"']*)["'][^>]*>""")
                .find(body)?.groupValues?.getOrNull(1)?.let(::addIdsFromCommaList)
        Regex("""(?is)<input[^>]+value\s*=\s*["']([0-9,\s]*)["'][^>]*name\s*=\s*["']file-list["'][^>]*>""")
                .find(body)?.groupValues?.getOrNull(1)?.let(::addIdsFromCommaList)
        Regex("""(?i)file-list["']?\s*[:=]\s*["']([0-9,\s]*)["']""")
                .find(body)?.groupValues?.getOrNull(1)?.let(::addIdsFromCommaList)
        // BBCode в разметке страницы / превью.
        Regex("""(?i)\[attachment\s*=\s*(\d+)\s*:""").findAll(body).forEach { m ->
            m.groupValues.getOrNull(1)?.toIntOrNull()?.let(::addId)
        }
        // Типичные ссылки скачивания 4PDA: /forum/dl/post/{id}/имяфайла (id — внутренний id вложения)
        Regex("""(?i)https?://4pda\.to/forum/dl/post/(\d+)/[^"'\s<>]+""").findAll(body).forEach { m ->
            m.groupValues.getOrNull(1)?.toIntOrNull()?.let(::addId)
        }
        val patterns = listOf(
                Regex("""(?i)removeAttach\s*\(\s*(\d+)\s*\)"""),
                Regex("""(?i)removeattach\s*\(\s*(\d+)\s*\)"""),
                Regex("""(?i)ips\.attach\.remove\s*\(\s*(\d+)\s*\)"""),
                Regex("""(?i)code=remove[^"'<>]*["']?[^>]*\bid=(\d+)"""),
                Regex("""(?i)["']attach_id["']\s*:\s*(\d+)"""),
                Regex("""(?i)["']id["']\s*:\s*(\d+)[^}]*["']name["']"""),
                Regex("""(?i)data-fileid=["'](\d+)["']"""),
                Regex("""(?i)data-attach-id=["'](\d+)["']"""),
                Regex("""(?i)data-ipsattach-id=["'](\d+)["']"""),
                Regex("""(?i)ipsAttach[^\d]{0,40}(\d{3,})"""),
                Regex("""(?i)forum/index\.php\?[^"']*act=attach[^"']*id=(\d+)"""),
                Regex("""(?i)forum/index\.php\?[^"']*act=attach[^"']*attach_id=(\d+)"""),
        )
        for (re in patterns) {
            re.findAll(body).forEach { m ->
                m.groupValues.getOrNull(1)?.toIntOrNull()?.let(::addId)
            }
        }
        return out
    }

    fun parseAttachment(response: String, item: AttachmentItem?): AttachmentItem {
        val result = item ?: AttachmentItem()
        val filled = patternProvider
            .getPattern(scope.scope, scope.attachments)
            .matcher(response)
            .mapOnce {
                fillAttachment(result, it)
            }
        if (filled == null) {
            // Сервер вернул не разметку вложения (ошибка/пусто)
            result.loadState = AttachmentItem.STATE_NOT_LOADED
            result.isError = true
            result.errorText = response
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(200)
                .ifBlank { "Не удалось загрузить файл" }
        }
        return result
    }

    private fun fillAttachment(item: AttachmentItem, matcher: Matcher): AttachmentItem {
        item.id = (matcher.group(1) ?: "0").toInt()
        item.name = matcher.group(2) ?: ""
        /*try {
            item.setName(URLDecoder.decode(matcher.group(2), "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }*/
        item.extension = matcher.group(3) ?: ""
        item.weight = readableFileSize(java.lang.Long.parseLong(matcher.group(5) ?: "0"))
        item.md5 = matcher.group(6) ?: ""
        val temp = matcher.group(7)

        if (temp != null) {
            item.typeFile = AttachmentItem.TYPE_IMAGE
            item.imageUrl = "https:$temp"
            item.width = (matcher.group(8) ?: "0").toInt()
            item.height = (matcher.group(9) ?: "0").toInt()
        }
        item.loadState = AttachmentItem.STATE_LOADED
        return item
    }

    private fun readableFileSize(size: Long): String {
        if (size <= 0) return "0"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.##").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}