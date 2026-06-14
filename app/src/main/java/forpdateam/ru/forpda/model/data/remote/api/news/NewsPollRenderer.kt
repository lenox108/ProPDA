package forpdateam.ru.forpda.model.data.remote.api.news

import java.util.UUID
import kotlin.math.roundToInt

internal object NewsPollRenderer {
    fun buildNormalizedVotePollBlock(
            title: String,
            pollId: String,
            from: String,
            options: List<PollOption>,
            hiddenFields: List<PollHiddenField>,
            sourceUrl: String?,
            rawTemplatePoll: Boolean,
            readOnly: Boolean,
            totalVotes: Int? = null,
            multiSelect: Boolean = false,
            frameId: String = "poll-ajax-frame-news",
            statusHtml: String? = null,
            renderToken: String = newRenderToken()
    ): String =
            buildString {
                append("""<div id="""")
                append(newsPollHtmlEncode(frameId))
                append("""" class="poll-ajax-frame news-poll news-poll-normalized" data-normalized-poll="true" data-news-poll-token="""")
                append(newsPollHtmlEncode(renderToken))
                append("""">""")
                if (rawTemplatePoll) {
                    append("""<span data-raw-template-poll="true" style="display:none"></span>""")
                }
                append("""<h2>""")
                append(newsPollHtmlEncode(title.takeIf { it.isNotBlank() } ?: "Опрос"))
                append("""</h2>""")
                val resultTotalVotes = totalVotes?.takeIf { it > 0 }
                if (readOnly && resultTotalVotes != null && options.any { it.votes > 0 }) {
                    appendNormalizedPollResults(options, resultTotalVotes)
                    append("""<p class="poll_status">Проголосовало """)
                    append(newsPollHtmlEncode(resultTotalVotes.toString()))
                    append(""" чел.</p>""")
                    sourceUrl?.let {
                        append("""<button type="button" class="btn news-poll-browser-button" data-open-external-browser="true" data-href="""")
                        append(newsPollHtmlEncode(it))
                        append("""">Открыть статью в браузере</button>""")
                    }
                    append("""</div>""")
                    return@buildString
                }
                append("""<form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=""")
                append(newsPollHtmlEncode(pollId))
                append("\" method=\"post\">")
                append("<input type=\"hidden\" name=\"poll_id\" value=\"")
                append(newsPollHtmlEncode(pollId))
                append("""">""")
                append("<input type=\"hidden\" name=\"from\" value=\"")
                append(newsPollHtmlEncode(from))
                append("""">""")
                hiddenFields.forEach { field ->
                    append("<input type=\"hidden\" name=\"")
                    append(newsPollHtmlEncode(field.name))
                    append("\" value=\"")
                    append(newsPollHtmlEncode(field.value))
                    append("""">""")
                }
                append("""<ul class="poll-list">""")
                options.forEachIndexed { index, option ->
                    append("<li")
                    if (option.selected) {
                        append(""" class="select-option"""")
                    }
                    append("><label class=\"text\"><input type=\"")
                    append(if (multiSelect) "checkbox" else "radio")
                    append("\" name=\"answer[]\" value=\"")
                    append(newsPollHtmlEncode(option.value))
                    append("\"")
                    if (index == 0) {
                        append(" autocomplete=\"off\"")
                    }
                    if (readOnly) {
                        append(" disabled")
                    }
                    if (option.selected) {
                        append(" checked")
                    }
                    append("""> <span>""")
                    append(newsPollHtmlEncode(option.title))
                    append("""</span></label></li>""")
                }
                append("""</ul>""")
                if (readOnly) {
                    totalVotes?.takeIf { it > 0 }?.let {
                        append("""<p class="poll_status">Проголосовало """)
                        append(newsPollHtmlEncode(it.toString()))
                        append(""" чел.</p>""")
                    }
                    append("""<p class="poll_status">Опрос доступен на сайте</p>""")
                    sourceUrl?.let {
                        append("""<button type="button" class="btn news-poll-browser-button" data-open-external-browser="true" data-href="""")
                        append(newsPollHtmlEncode(it))
                        append("""">Открыть статью в браузере</button>""")
                    }
                } else {
                    append("""<button type="submit" class="btn">Проголосовать</button>""")
                    statusHtml?.takeIf { it.isNotBlank() }?.let { append(it) }
                    sourceUrl?.let {
                        append("""<button type="button" class="btn news-poll-browser-button" data-open-external-browser="true" data-href="""")
                        append(newsPollHtmlEncode(it))
                        append("""">Открыть статью в браузере</button>""")
                    }
                }
                append("""</form>""")
                append("""</div>""")
            }

    private fun newRenderToken(): String = UUID.randomUUID().toString()

    private fun StringBuilder.appendNormalizedPollResults(options: List<PollOption>, totalVotes: Int) {
        append("""<ul class="poll-list">""")
        options.forEach { option ->
            val percent = if (totalVotes > 0) {
                (option.votes.toDouble() * 100 / totalVotes.toDouble()).roundToInt()
            } else {
                0
            }
            append("<li")
            if (option.selected) {
                append(""" class="select-option"""")
            }
            append("""><span class="title">""")
            append(newsPollHtmlEncode(option.title))
            append("""</span><span class="slider"><span class="range" style="width: """)
            append(newsPollHtmlEncode(percent.toString()))
            append("""%;"><span class="fill"></span></span><span class="value">""")
            append(newsPollHtmlEncode(percent.toString()))
            append("""% <span class="num_votes">""")
            append(newsPollHtmlEncode(option.votes.toString()))
            append("""</span></span></span></li>""")
        }
        append("""</ul>""")
    }

    private fun newsPollHtmlEncode(value: String): String =
            buildString(value.length) {
                value.forEach { char ->
                    when (char) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&#39;")
                        else -> append(char)
                    }
                }
            }
}

internal data class PollOption(
        val value: String,
        val title: String,
        val votes: Int = 0,
        val selected: Boolean = false
)

internal data class DataSitePoll(
        val pollId: String,
        val title: String,
        val multiSelect: Boolean,
        val options: List<PollOption>,
        val totalVotes: Int,
        val voted: Boolean
)

internal data class RenderedPoll(
        val title: String,
        val options: List<PollOption>
)

internal data class PollHiddenField(
        val name: String,
        val value: String
)
