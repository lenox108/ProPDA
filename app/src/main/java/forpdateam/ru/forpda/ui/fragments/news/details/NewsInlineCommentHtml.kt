package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.common.Html

object NewsInlineCommentHtml {

    fun editedMarkerHtml(editedHint: String): String =
            """<span class="news-inline-comment-edited" title="${Html.escapeHtml(editedHint)}">✎</span>"""

    fun metaDateHtml(date: String, isEdited: Boolean, editedHint: String): String {
        val escapedDate = Html.escapeHtml(date)
        if (!isEdited) {
            return """<span class="news-inline-comment-date">$escapedDate</span>"""
        }
        return """<span class="news-inline-comment-meta-date"><span class="news-inline-comment-date">$escapedDate</span>${editedMarkerHtml(editedHint)}</span>"""
    }
}
