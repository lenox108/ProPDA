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
    /**
     * Decode HTML to a [Spanned] **with CSS color support**.
     *
     * Use this when the input may contain `style="color:…"` or `<font color="…">`
     * (e.g. user signatures, post bodies) and the caller needs the colored
     * spans preserved. Backed by [Html.FROM_HTML_OPTION_USE_CSS_COLORS].
     */
    @JvmStatic
    fun coloredFromHtml(s: String?): Spanned? {
        return s?.let { Html.fromHtml(it, Html.FROM_HTML_OPTION_USE_CSS_COLORS) }
    }

    /**
     * Decode HTML to a [Spanned] in legacy mode (color attributes stripped).
     *
     * Use this when the caller only needs plain text styling (bold/italic/
     * links) and the surrounding theme already provides its own color scheme
     * — typical for body text rendered into the active theme. Backed by
     * [Html.FROM_HTML_MODE_LEGACY].
     */
    @JvmStatic
    fun spannedFromHtml(s: String?): Spanned? {
        return s?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY) }
    }

    /**
     * Decode HTML to a **String** (legacy mode, colors stripped).
     *
     * Use this when the caller only needs a flat string — typically for
     * comparison, logging, or as a fallback when the [Spanned] representation
     * is overkill. Equivalent to `spannedFromHtml(s).toString()`.
     */
    @JvmStatic
    fun fromHtml(s: String?): String? {
        return s?.let { spannedFromHtml(it)?.toString() }
    }

    /**
     * Encode a string for safe inclusion in HTML (entity-encoding `<>&"`).
     * This is the **encode** direction, not a decoder; the audit L05 only
     * deals with decoding.
     */
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
