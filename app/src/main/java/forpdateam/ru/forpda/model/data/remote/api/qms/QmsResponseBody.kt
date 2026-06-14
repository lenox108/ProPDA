package forpdateam.ru.forpda.model.data.remote.api.qms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Normalizes QMS HTTP bodies: XHR may return JSON envelopes instead of raw HTML.
 */
object QmsResponseBody {

    private const val MIN_UNWRAPPED_HTML_LEN = 32

    private val htmlKeys = listOf(
            "html",
            "body",
            "content",
            "data",
            "result",
            "fragment",
            "markup"
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return raw
        return unwrapJson(trimmed) ?: raw
    }

    fun looksLikeJsonEnvelope(raw: String): Boolean {
        val t = raw.trim()
        return t.startsWith("{") || t.startsWith("[")
    }

    private fun unwrapJson(text: String): String? = runCatching {
        when {
            text.startsWith("{") -> extractHtmlFromObject(json.parseToJsonElement(text).jsonObject)
            text.startsWith("[") -> {
                val array = json.parseToJsonElement(text).jsonArray
                for (element in array) {
                    when (element) {
                        is JsonPrimitive -> {
                            val candidate = element.content
                            if (isHtmlCandidate(candidate)) return candidate
                        }
                        is JsonObject -> extractHtmlFromObject(element)?.let { return it }
                        else -> Unit
                    }
                }
                null
            }
            else -> null
        }
    }.getOrNull()

    private fun extractHtmlFromObject(obj: JsonObject): String? {
        for (key in htmlKeys) {
            val candidate = obj[key]?.jsonPrimitive?.content?.trim().orEmpty()
            if (isHtmlCandidate(candidate)) return candidate
        }
        val nested = obj["response"]?.jsonObject ?: obj["payload"]?.jsonObject
        if (nested != null) {
            extractHtmlFromObject(nested)?.let { return it }
        }
        return null
    }

    private fun isHtmlCandidate(text: String): Boolean =
            text.length >= MIN_UNWRAPPED_HTML_LEN &&
                    (QmsHtmlValidator.looksLikeQmsThread(text) || text.contains("<"))
}
