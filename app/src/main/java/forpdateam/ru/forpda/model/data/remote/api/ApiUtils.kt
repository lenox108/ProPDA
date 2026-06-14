package forpdateam.ru.forpda.model.data.remote.api

import android.text.Spanned
import android.text.TextUtils
import forpdateam.ru.forpda.common.Html
import org.json.JSONObject

/**
 * Created by radiationx on 26.03.17.
 * Converted to Kotlin.
 */
object ApiUtils {
    @JvmStatic
    fun coloredFromHtml(s: String?): Spanned? {
        return s?.let { Html.fromHtml(it, Html.FROM_HTML_OPTION_USE_CSS_COLORS) }
    }

    @JvmStatic
    fun spannedFromHtml(s: String?): Spanned? {
        return s?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY) }
    }

    @JvmStatic
    fun fromHtml(s: String?): String? {
        return s?.let { spannedFromHtml(it)?.toString() }
    }

    @JvmStatic
    fun htmlEncode(s: String?): String? {
        return s?.let { TextUtils.htmlEncode(it) }
    }

    @JvmStatic
    fun escapeNewLine(s: String?): String {
        if (s == null) return ""
        val sb = StringBuilder()
        for (c in s) {
            if (c == '\n') {
                sb.append("<br>")
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun escapeQuotes(s: String?): String? {
        return s?.let {
            var escaped = JSONObject.quote(it)
            escaped = escaped.substring(1, escaped.length - 1)
            escaped
        }
    }
}
