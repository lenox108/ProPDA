package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.normalizeEditPostBodyForEditor
import forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml
import forpdateam.ru.forpda.common.stripBbcodeQuotes
import forpdateam.ru.forpda.common.stripHtmlQuoteBlocks
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Central entry point for topic reply actions.
 *
 * Current callers:
 * - WebView JS bridge: reply(), quotePostWithDate(), quoteFullPostWithDate(), editPost()
 * - Native post menu: reply, quote from clipboard, edit
 * - Toolbar reply button still opens the existing inline message panel without a post context.
 *
 * This keeps the future floating composer foundation limited to action routing; editor UI and
 * backend behavior stay owned by the existing ThemeUiEvent/EditPost flow.
 */
class ThemeReplyActionLauncher(
        private val uiEvents: MutableSharedFlow<ThemeUiEvent>,
        private val getPostById: (Int) -> IBaseForumPost?,
        private val logThemeQuote: (String, Array<out Any?>) -> Unit
) {

    fun openReply(postId: Int) {
        getPostById(postId)?.let {
            val text = "[snapback]${it.id}[/snapback] [b]${it.nick},[/b] \n"
            uiEvents.tryEmit(ThemeUiEvent.InsertText(text))
        }
    }

    fun openQuote(postId: Int, text: String, displayedDate: String? = null) {
        getPostById(postId)?.let {
            val date = Utils.resolveForumQuoteDate(it.date, displayedDate, "theme.quote")
            val trimmed = text.trim()
            val looksLikeSelectionHtml = Regex("""(?i)<(?:div|p|br|img|span|a|strong|b|blockquote)\b""")
                    .containsMatchIn(trimmed)
            val body = stripBbcodeQuotes(
                    if (looksLikeSelectionHtml) {
                        normalizeEditPostBodyFromDomHtml(trimmed)
                    } else {
                        normalizeEditPostBodyForEditor(trimmed)
                    }
            )
            val insert = "[quote name=\"${it.nick}\" date=\"$date\" post=${it.id}]$body[/quote]\n"
            logThemeQuote(
                    "insert postId=%d rawDate=%s displayDate=%s quoteDate=%s bodyLen=%d htmlSelection=%s",
                    arrayOf(it.id, it.date, displayedDate, date, body.length, looksLikeSelectionHtml)
            )
            uiEvents.tryEmit(ThemeUiEvent.InsertText(insert))
        }
    }

    fun openFullQuote(postId: Int, displayedDate: String?) {
        getPostById(postId)?.let { post ->
            val raw = post.body.orEmpty()
            val withoutQuotesHtml = stripHtmlQuoteBlocks(raw)
            val normalized = normalizeEditPostBodyFromDomHtml(withoutQuotesHtml).ifEmpty {
                normalizeEditPostBodyFromDomHtml(raw)
            }
            val body = stripBbcodeQuotes(normalized).ifEmpty { normalized }
            openQuote(postId, body, displayedDate)
        }
    }

    fun openEdit(postId: Int) {
        getPostById(postId)?.let { uiEvents.tryEmit(ThemeUiEvent.EditPost(it)) }
    }
}
