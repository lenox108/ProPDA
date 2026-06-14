package forpdateam.ru.forpda.model.data.remote.api.reputation

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.RepItem
import forpdateam.ru.forpda.entity.remote.reputation.ReputationReportForm
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Node
import forpdateam.ru.forpda.model.data.remote.api.regex.parser.Parser
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

class ReputationParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Reputation

    fun parse(response: String): RepData = RepData().also { data ->
        patternProvider
                .getPattern(scope.scope, scope.info)
                .matcher(response)
                .findOnce { matcher ->
                    data.id = matcher.groupInt(1) ?: 0
                    data.nick = matcher.group(2).fromHtml()
                    matcher.group(3)?.also {
                        data.positive = it.toIntOrNull() ?: 0
                    }
                    matcher.group(4)?.also {
                        data.negative = it.toIntOrNull() ?: 0
                    }
                }

        val repRows = parseRepRows(response)
        if (repRows.isNotEmpty()) {
            data.items.addAll(repRows)
        } else {
            patternProvider
                    .getPattern(scope.scope, scope.main)
                    .matcher(response)
                    .findAll { matcher ->
                        data.items.add(RepItem().apply {
                            userId = matcher.groupInt(1) ?: return@findAll
                            userNick = matcher.group(2).fromHtml()
                            matcher.group(3)?.also {
                                sourceUrl = normalizeUrl(it)
                                sourceTitle = matcher.group(4).fromHtml()
                            }
                            title = matcher.group(5).fromHtml()
                            image = matcher.group(6)
                            isPositive = parseReputationSign(image)
                            date = matcher.group(7)?.trim()
                        })
                    }
        }
        data.pagination = Pagination.parseForum(response)
        return data
    }

    fun parseReportForm(response: String, sourceReportUrl: String, reputationId: Int): ReputationReportForm {
        val document = Parser.parse(response)
        var fallback: ReputationReportForm? = null
        walkElements(document) { node ->
            if (!node.name.orEmpty().equals("form", ignoreCase = true)) return@walkElements
            val form = parseFormNode(node, sourceReportUrl, reputationId) ?: return@walkElements
            val marker = listOf(
                    form.actionUrl,
                    node.getAttribute("class").orEmpty(),
                    node.getAttribute("id").orEmpty(),
                    form.fields["act"].orEmpty(),
                    form.fields["reputation"].orEmpty(),
                    Parser.getHtml(node, true),
            ).joinToString(" ").lowercase()
            if (hasAnyMarker(marker, "act=report", "report")) {
                fallback = form
                return@walkElements
            }
        }
        return fallback ?: throw IllegalStateException("Сервер не вернул форму жалобы")
    }

    private fun parseRepRows(response: String): List<RepItem> {
        val items = mutableListOf<RepItem>()
        REP_ROW_PATTERN.matcher(response).findAll { matcher ->
            val reputationId = matcher.groupInt(1) ?: return@findAll
            val rowHtml = matcher.group(2).orEmpty()
            val cells = extractTdCells(rowHtml)
            if (cells.isEmpty()) return@findAll
            items += RepItem().apply {
                id = reputationId
                SHOWUSER_PATTERN.matcher(cells.first()).findOnce { authorMatcher ->
                    userId = authorMatcher.groupInt(1) ?: return@findOnce
                    userNick = authorMatcher.group(2).fromHtml()
                }
                if (userId <= 0) return@findAll
                if (cells.size > 1) {
                    SOURCE_PATTERN.matcher(cells[1]).findOnce { sourceMatcher ->
                        sourceUrl = normalizeUrl(sourceMatcher.group(1))
                        sourceTitle = sourceMatcher.group(2).fromHtml()
                    }
                }
                if (cells.size > 2) {
                    title = cells[2].fromHtml()?.trim()
                }
                if (cells.size > 3) {
                    image = IMAGE_PATTERN.matcher(cells[3]).mapOnce { it.group(1) }
                    isPositive = parseReputationSign(image)
                }
                if (cells.size > 4) {
                    date = cells[4].fromHtml()?.trim()
                }
                reportActionUrl = REPORT_URL_PATTERN.matcher(rowHtml).mapOnce {
                    normalizeUrl(decodeHtmlEntities(it.group(1)))
                }?.takeIf { url ->
                    url.contains("act=report", ignoreCase = true) &&
                            url.contains("reputation=$reputationId", ignoreCase = true)
                }
            }
        }
        return items
    }

    private fun extractTdCells(rowHtml: String): List<String> {
        val cells = mutableListOf<String>()
        TD_PATTERN.matcher(rowHtml).findAll { cells += it.group(1).orEmpty() }
        return cells
    }

    private fun parseFormNode(form: Node, sourceReportUrl: String, reputationId: Int): ReputationReportForm? {
        val rawAction = firstNonBlank(form.getAttribute("action"), form.getAttribute("data-action")) ?: return null
        val actionUrl = normalizeUrl(rawAction.fromHtml().orEmpty()) ?: return null
        val fields = collectFormFields(form)
        val messageFieldName = fields.keys.firstOrNull { isMessageField(it) } ?: "message"
        val token = fields["auth_key"] ?: fields["_wpnonce"]
        return ReputationReportForm(
                actionUrl = actionUrl,
                method = form.getAttribute("method")?.uppercase()?.takeIf { it == ReputationReportForm.METHOD_GET }
                        ?: ReputationReportForm.METHOD_POST,
                fields = fields,
                messageFieldName = messageFieldName,
                token = token,
                reputationId = reputationId.takeIf { it > 0 }
                        ?: fields["reputation"]?.toIntOrNull()
                        ?: reputationIdFromUrl(sourceReportUrl),
                sourceReportUrl = sourceReportUrl,
        )
    }

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
            fields[name] = value.fromHtml().orEmpty()
        }
        return fields
    }

    private fun walkElements(node: Node, action: (Node) -> Unit) {
        if (Parser.isNotElement(node)) return
        action(node)
        node.getNodes().forEach { walkElements(it, action) }
    }

    private fun parseReputationSign(imageUrl: String?): Boolean? {
        val src = imageUrl.orEmpty().lowercase()
        return when {
            src.contains("rep_add") || src.contains("win_add") || src.contains("plus") -> true
            src.contains("rep_minus") || src.contains("win_minus") || src.contains("minus") -> false
            else -> null
        }
    }

    private fun normalizeUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = decodeHtmlEntities(raw.trim())
        return when {
            decoded.startsWith("http://", ignoreCase = true) ||
                    decoded.startsWith("https://", ignoreCase = true) -> decoded
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "https://4pda.to$decoded"
            decoded.contains("index.php", ignoreCase = true) ->
                    "https://4pda.to/forum/${decoded.removePrefix("/")}"
            else -> decoded
        }
    }

    private fun decodeHtmlEntities(value: String): String =
            value.replace("&amp;", "&")
                    .replace("&#038;", "&")
                    .replace("&quot;", "\"")

    private fun reputationIdFromUrl(url: String): Int {
        val matcher = Pattern.compile("reputation=(\\d+)", Pattern.CASE_INSENSITIVE).matcher(url)
        return if (matcher.find()) matcher.groupInt(1) ?: 0 else 0
    }

    private fun isMessageField(name: String): Boolean =
            name.equals("message", ignoreCase = true) ||
                    name.equals("report", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true)

    private fun hasAnyMarker(source: String, vararg markers: String): Boolean =
            markers.any { source.contains(it, ignoreCase = true) }

    private fun firstNonBlank(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() }

    companion object {
        private val REP_ROW_PATTERN = Pattern.compile(
                """<tr[^>]*\bid\s*=\s*["']rep-row-(\d+)["'][^>]*>([\s\S]*?)</tr>""",
                Pattern.CASE_INSENSITIVE,
        )
        private val SHOWUSER_PATTERN = Pattern.compile(
                """showuser=(\d+)[^>]*>([\s\S]*?)</a""",
                Pattern.CASE_INSENSITIVE,
        )
        private val SOURCE_PATTERN = Pattern.compile(
                """<a[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)</a>""",
                Pattern.CASE_INSENSITIVE,
        )
        private val IMAGE_PATTERN = Pattern.compile(
                """<img[^>]*src\s*=\s*["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE,
        )
        private val TD_PATTERN = Pattern.compile(
                """<td[^>]*>([\s\S]*?)</td>""",
                Pattern.CASE_INSENSITIVE,
        )
        private val REPORT_URL_PATTERN = Pattern.compile(
                """href\s*=\s*["']([^"']*act=report[^"']*reputation=\d+[^"']*)["']""",
                Pattern.CASE_INSENSITIVE,
        )
    }
}
