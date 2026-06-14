package forpdateam.ru.forpda.model.data.remote.api.news

import forpdateam.ru.forpda.model.data.remote.api.qms.QmsResponseBody

/**
 * Normalizes article/comment HTTP bodies: XHR may return JSON envelopes instead of raw HTML.
 */
object ArticleResponseBody {

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        return QmsResponseBody.normalize(raw)
    }

    fun looksLikeJsonEnvelope(raw: String?): Boolean =
            !raw.isNullOrBlank() && QmsResponseBody.looksLikeJsonEnvelope(raw)
}
