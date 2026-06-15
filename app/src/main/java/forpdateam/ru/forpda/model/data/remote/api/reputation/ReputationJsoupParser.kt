package forpdateam.ru.forpda.model.data.remote.api.reputation

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.RepItem
import forpdateam.ru.forpda.entity.remote.reputation.ReputationReportForm
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Jsoup-based re-implementation of the regex-based [ReputationParser].
 *
 * Per the §2.1 plan, the regex path stays the default until the new
 * implementation is verified against the golden fixtures (in
 * `app/src/test/resources/parser/reputation/`) and the
 * `ReputationParserGoldenTest`. A boolean flag in [ReputationParser]
 * switches between the two paths; the regex path is deleted once
 * Jsoup is stable in production.
 *
 * Goal: produce the same `RepData` / `RepItem` / `ReputationReportForm`
 * for the same input HTML. The selector strategy mirrors the
 * `rep-row-{id}` structure and the desktop `act=reputation` page layout.
 */
class ReputationJsoupParser : BaseParser() {

    fun parse(response: String): RepData = RepData().also { data ->
        val document = Jsoup.parse(response)
        // 1) user header (maintitle): "nick [+42/-7]" with a showuser link
        val headerLink = document.selectFirst("div.maintitle a[href*=showuser=]")
        data.id = headerLink?.attr("href")?.extractShowUserId() ?: 0
        data.nick = headerLink?.text()?.fromHtml()

        val headerText = document.selectFirst("div.maintitle")?.text().orEmpty()
        val (pos, neg) = parseRepCounts(headerText)
        data.positive = pos
        data.negative = neg

        // 2) rep rows: prefer the structured `rep-row-{id}` rows
        val rowItems = parseRepRows(document)
        if (rowItems.isNotEmpty()) {
            data.items.addAll(rowItems)
        } else {
            // Fallback for older pages: regex-style main pattern.
            // We just leave the list empty here — the regex path
            // covers this case, and §2.1 marks the legacy path
            // for removal after the structured rows are confirmed
            // to be universally present.
        }

        // 3) pagination block — reuse the same logic as the legacy parser
        data.pagination = Pagination.parseForum(response)
    }

    fun parseReportForm(response: String, sourceReportUrl: String, reputationId: Int): ReputationReportForm {
        val document = Jsoup.parse(response)
        var fallback: ReputationReportForm? = null
        val forms = document.select("form")
        for (form in forms) {
            val parsed = parseFormNode(form, sourceReportUrl, reputationId) ?: continue
            val marker = listOf(
                parsed.actionUrl,
                form.attr("class"),
                form.id().orEmpty(),
                parsed.fields["act"].orEmpty(),
                parsed.fields["reputation"].orEmpty(),
                form.html(),
            ).joinToString(" ").lowercase()
            if (marker.contains("act=report") || marker.contains("report")) {
                fallback = parsed
                break
            }
        }
        return fallback ?: throw IllegalStateException("Сервер не вернул форму жалобы")
    }

    private fun parseRepRows(document: Document): List<RepItem> {
        val items = mutableListOf<RepItem>()
        val rows = document.select("tr[id^=rep-row-]")
        for (row in rows) {
            val reputationId = row.id().removePrefix("rep-row-").toIntOrNull() ?: continue
            val cells = row.select("td")
            if (cells.isEmpty()) continue
            // Build the item via locals first so the `continue` semantics are
            // explicit and not dependent on K2 inline-lambda behavior.
            val authorLink = if (cells.size > 0) cells[0].selectFirst("a[href*=showuser=]") else null
            val userId = authorLink?.attr("href")?.extractShowUserId() ?: 0
            if (userId <= 0) continue
            val userNick = authorLink?.text()?.fromHtml()
            val sourceLink = if (cells.size > 1) cells[1].selectFirst("a[href]") else null
            val sourceUrl = sourceLink?.let { normalizeUrl(it.attr("href")) }
            val sourceTitle = sourceLink?.text()?.fromHtml()
            val title = if (cells.size > 2) cells[2].text().fromHtml()?.trim() else null
            val img = if (cells.size > 3) cells[3].selectFirst("img[src]") else null
            val image = img?.attr("src")
            val isPositive = parseReputationSign(image)
            val date = if (cells.size > 4) cells[4].text().fromHtml()?.trim() else null
            // Report action URL is a sibling `a[href*=act=report]` inside the row
            val reportLink = row.selectFirst("a[href*=\"act=report\"][href*=\"reputation=$reputationId\"]")
            val reportHref = reportLink?.attr("href")
            val reportActionUrl = reportHref
                ?.let { normalizeUrl(decodeHtmlEntities(it)) }
                ?.takeIf { url ->
                    url.contains("act=report", ignoreCase = true) &&
                            url.contains("reputation=$reputationId", ignoreCase = true)
                }
            val item = RepItem().apply {
                id = reputationId
                this.userId = userId
                this.userNick = userNick
                this.sourceUrl = sourceUrl
                this.sourceTitle = sourceTitle
                this.title = title
                this.image = image
                this.isPositive = isPositive
                this.date = date
                this.reportActionUrl = reportActionUrl
            }
            items += item
        }
        return items
    }

    private fun parseFormNode(
        form: Element,
        sourceReportUrl: String,
        reputationId: Int,
    ): ReputationReportForm? {
        val rawAction = firstNonBlank(form.attr("action"), form.attr("data-action")) ?: return null
        val actionUrl = normalizeUrl(rawAction.fromHtml().orEmpty()) ?: return null
        val fields = collectFormFields(form)
        val messageFieldName = fields.keys.firstOrNull { isMessageField(it) } ?: "message"
        val token = fields["auth_key"] ?: fields["_wpnonce"]
        return ReputationReportForm(
                actionUrl = actionUrl,
                method = form.attr("method").uppercase().takeIf { it == ReputationReportForm.METHOD_GET }
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

    private fun collectFormFields(form: Element): LinkedHashMap<String, String> {
        val fields = LinkedHashMap<String, String>()
        val inputs = form.select("input[name], textarea[name]")
        for (input in inputs) {
            val name = input.attr("name").takeIf { it.isNotBlank() } ?: continue
            val value = if (input.tagName().lowercase() == "textarea") {
                input.text()
            } else {
                input.attr("value")
            }
            fields[name] = value.fromHtml().orEmpty()
        }
        return fields
    }

    private fun parseRepCounts(headerText: String): Pair<Int, Int> {
        // Look for "[+N/-M]" or "[N/M]" pattern.
        val bracketContent = Regex("""\[([^\]]+)]""").find(headerText)?.groupValues?.get(1).orEmpty()
        val parts = bracketContent.split("/")
        if (parts.size != 2) return 0 to 0
        val pos = parts[0].trim().removePrefix("+").toIntOrNull() ?: 0
        val neg = parts[1].trim().removePrefix("-").toIntOrNull() ?: 0
        return pos to neg
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
        val matcher = Regex("""reputation=(\d+)""", RegexOption.IGNORE_CASE).find(url)
        return matcher?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun isMessageField(name: String): Boolean =
            name.equals("message", ignoreCase = true) ||
                    name.equals("report", ignoreCase = true) ||
                    name.equals("text", ignoreCase = true)

    private fun firstNonBlank(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() }

    private fun String?.extractShowUserId(): Int {
        if (this == null) return 0
        val matcher = Regex("""showuser=(\d+)""", RegexOption.IGNORE_CASE).find(this)
        return matcher?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

}
