package forpdateam.ru.forpda.common.html

import android.text.Spanned
import forpdateam.ru.forpda.common.Html

/**
 * Single canonical entry point for HTML-entity decoding across the app.
 *
 * Historically the same `Html.fromHtml` calls were scattered across
 * `ApiUtils` (and would have been duplicated by any new decoder path). This
 * object centralizes the raw decode so there is exactly one place that owns
 * the flag selection:
 *
 *   - [decodeToSpanned]        : legacy mode, color attributes stripped.
 *   - [decodeColoredToSpanned] : honors inline CSS color / `<font color>`.
 *   - [decodeToString]         : flat String, legacy mode.
 *
 * The three variants are intentionally NOT interchangeable; see
 * `HtmlEntityDecoderTest` for the pinned contract.
 */
object HtmlEntityDecoder {

    fun decodeColoredToSpanned(source: String?): Spanned? {
        return source?.let { Html.fromHtml(it, Html.FROM_HTML_OPTION_USE_CSS_COLORS) }
    }

    fun decodeToSpanned(source: String?): Spanned? {
        return source?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY) }
    }

    fun decodeToString(source: String?): String? {
        return source?.let { decodeToSpanned(it)?.toString() }
    }
}
