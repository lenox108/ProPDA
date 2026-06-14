package forpdateam.ru.forpda.model.data.remote.api.reputation

import forpdateam.ru.forpda.common.Cp1251Codec
import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.ReputationReportForm
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import java.util.regex.Pattern

/**
 * Created by radiationx on 20.03.17.
 */

class ReputationApi(
        private val webClient: IWebClient,
        private val reputationParser: ReputationParser
) {

    fun getReputation(userId: Int, mode: String, sort: String, st: Int): RepData {
        val response = webClient.get(
                "https://4pda.to/forum/index.php?act=rep&view=history&mid=$userId&mode=$mode&order=$sort&st=$st"
        )
        ReputationHtmlValidator.ensureHistoryPage(response.code, response.body)
        return reputationParser.parse(response.body)
    }

    fun editReputation(postId: Int, userId: Int, type: Boolean, message: String): Boolean {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php")
                .formHeader("act", "rep")
                .formHeader("mid", userId.toString())
                .formHeader("type", if (type) "add" else "minus")
                .formHeader("message", message)
        if (postId > 0) {
            builder.formHeader("p", postId.toString())
        }
        webClient.request(builder.build())
        return true
    }

    fun loadReportForm(userId: Int, reputationId: Int, reportUrl: String): ReputationReportForm {
        ReputationReportDiagnostics.log(
                userId = userId,
                reputationId = reputationId,
                reportUrl = reportUrl,
                formLoaded = null,
                tokenFound = null,
                submitStatus = "loading",
                errorReason = null,
        )
        val response = webClient.get(reportUrl)
        return try {
            ReputationHtmlValidator.ensureReportFormPage(response.code, response.body)
            val form = reputationParser.parseReportForm(response.body, reportUrl, reputationId)
            ReputationReportDiagnostics.log(
                    userId = userId,
                    reputationId = reputationId,
                    reportUrl = reportUrl,
                    formLoaded = true,
                    tokenFound = !form.token.isNullOrBlank(),
                    submitStatus = "loaded",
                    errorReason = null,
            )
            form
        } catch (e: Exception) {
            ReputationReportDiagnostics.log(
                    userId = userId,
                    reputationId = reputationId,
                    reportUrl = reportUrl,
                    formLoaded = false,
                    tokenFound = false,
                    submitStatus = "load_failed",
                    errorReason = e.message,
            )
            throw e
        }
    }

    fun submitReport(userId: Int, form: ReputationReportForm, message: String): Boolean {
        val submitUrl = buildSubmitUrl(form.actionUrl)
        val fields = LinkedHashMap(form.fields).apply {
            put(form.messageFieldName, message)
            if (!containsKey("send")) {
                put("send", "1")
            }
        }
        val builder = NetworkRequest.Builder()
                .url(submitUrl)
                .xhrHeader()
        fields.forEach { (key, value) ->
            if (key == form.messageFieldName) {
                builder.formHeader(key, Cp1251Codec.encode(value), encoded = true)
            } else {
                builder.formHeader(key, value)
            }
        }
        val request = builder.build()
        return try {
            val response = webClient.request(request)
            ensureReportAccepted(response.body)
            ReputationReportDiagnostics.log(
                    userId = userId,
                    reputationId = form.reputationId,
                    reportUrl = form.sourceReportUrl,
                    formLoaded = true,
                    tokenFound = !form.token.isNullOrBlank(),
                    submitStatus = "success",
                    errorReason = null,
            )
            true
        } catch (e: Exception) {
            ReputationReportDiagnostics.log(
                    userId = userId,
                    reputationId = form.reputationId,
                    reportUrl = form.sourceReportUrl,
                    formLoaded = true,
                    tokenFound = !form.token.isNullOrBlank(),
                    submitStatus = "failed",
                    errorReason = e.message,
            )
            throw e
        }
    }

    private fun buildSubmitUrl(actionUrl: String): String {
        if (actionUrl.contains("send=1", ignoreCase = true)) return actionUrl
        return if (actionUrl.contains("?")) {
            "$actionUrl&send=1"
        } else {
            "$actionUrl?send=1"
        }
    }

    private fun ensureReportAccepted(body: String) {
        detectReportError(body)?.let { throw IllegalStateException(it) }
    }

    private fun detectReportError(body: String): String? {
        if (body.isBlank()) return null
        val errorMatcher = ERROR_WRAP_PATTERN.matcher(body)
        if (errorMatcher.find()) {
            val reason = errorMatcher.group(1)?.let { forpdateam.ru.forpda.model.data.remote.api.ApiUtils.fromHtml(it) }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            if (reason != null) {
                return "Ошибка отправки жалобы: $reason"
            }
        }
        val normalized = body.lowercase()
        return when {
            normalized.contains("no permission") || normalized.contains("нет прав") ->
                "Нет прав для выполнения действия"
            normalized.contains("not authorized") || normalized.contains("войдите") ->
                "Требуется авторизация"
            normalized.contains("captcha") -> "Требуется прохождение captcha"
            else -> null
        }
    }

    companion object {
        private val ERROR_WRAP_PATTERN = Pattern.compile(
                "<div class=\"errorwrap\">[\\s\\S]*?<p>(.*?)</p>",
                Pattern.CASE_INSENSITIVE,
        )

        const val MODE_TO = "to"
        const val MODE_FROM = "from"
        const val SORT_ASC = "asc"
        const val SORT_DESC = "desc"

        fun fromUrl(url: String): RepData {
            return fromUrl(RepData(), url)
        }

        fun fromUrl(data: RepData, url: String): RepData {
            var matcher = Pattern.compile("st=(\\d+)").matcher(url)
            if (matcher.find()) {
                data.pagination.st = Integer.parseInt(matcher.group(1))
            }
            matcher = Pattern.compile("mid=(\\d+)").matcher(url)
            if (matcher.find())
                data.id = Integer.parseInt(matcher.group(1))
            matcher = Pattern.compile("mode=([^&]+)").matcher(url)
            if (matcher.find()) {
                when (matcher.group(1)) {
                    MODE_FROM -> data.mode = MODE_FROM
                    MODE_TO -> data.mode = MODE_TO
                }
            }

            matcher = Pattern.compile("order=([^&]+)").matcher(url)
            if (matcher.find()) {
                when (matcher.group(1)) {
                    SORT_ASC -> data.mode = SORT_ASC
                    SORT_DESC -> data.mode = SORT_DESC
                }
            }
            return data
        }
    }
}
