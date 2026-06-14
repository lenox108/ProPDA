package forpdateam.ru.forpda.model.data.remote.api.editpost

import timber.log.Timber
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPoll
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.common.decodeForumPostTextareaContent
import forpdateam.ru.forpda.common.normalizeEditPostBodyForEditor
import forpdateam.ru.forpda.common.selectBestEditBodyCandidate
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher

private const val EDIT_POST_DIAG = "ForPDA.EditPost"

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

private fun diagPreview(s: String, max: Int = 200): String =
        s.replace("\r\n", "\n").replace("\n", "↵").take(max)

class EditPostParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.EditPost

    private val fallbackMessagePatternsPrimary = listOf(
            Regex("(?is)<textarea[^>]*name=[\"']Post[\"'][^>]*>([\\s\\S]*?)</textarea>"),
            Regex("(?is)<textarea[^>]*name=[\"']post[\"'][^>]*>([\\s\\S]*?)</textarea>"),
            // Иногда в разметке: name = "Post" с пробелами
            Regex("(?is)<textarea[^>]*name\\s*=\\s*[\"']?[Pp]ost\\b[\"']?[^>]*>([\\s\\S]*?)</textarea>")
    )

    private val fallbackMessagePatternsSecondary = listOf(
            Regex("(?is)<textarea[^>]*id=[\"'][^\"']*(?:message|post|editor)[^\"']*[\"'][^>]*>([\\s\\S]*?)</textarea>"),
            Regex("(?is)<textarea[^>]*class=[\"'][^\"']*editor[^\"']*[\"'][^>]*>([\\s\\S]*?)</textarea>")
    )

    /** Все textarea на странице — если имя поля не Post, выбираем по длине и наличию [quote]. */
    private val fallbackAnyTextarea = Regex("(?is)<textarea[^>]*>([\\s\\S]*?)</textarea>")

    /** Как в веб-редакторе 4PDA: основное поле — #ed-0_textarea и т.п. (name="post"). */
    private val textareaMainEditor = Regex(
            // id может быть без кавычек (id=ed-0_textarea); пробелы вокруг =; дефис иногда как &#45; в htmlNorm
            "(?is)<textarea[^>]*\\bid\\s*=\\s*(?:\"|'|)ed-\\d+_textarea(?:\"|'|)[^>]*>([\\s\\S]*?)</textarea>"
    )

    /** В разметке id="ed&#45;0_textarea" — без этого ed-\d+_textarea не находится в строке. */
    private fun htmlForTextareaTagMatch(html: String): String =
            html.replace("&#45;", "-").replace("&#x2d;", "-").replace("&#X2D;", "-")

    private val postEditReasonPattern =
            Regex("(?is)<input[^>]*name=[\"']post_edit_reason[\"'][^>]*value=[\"']([^\"']*)[\"']")

    private fun decodeMessage(raw: String): String =
            normalizeEditPostBodyForEditor(decodeForumPostTextareaContent(raw))

    /** Все совпадения textarea: на странице редактирования может быть несколько полей — выбираем с полным [quote]. */
    private fun allRawTextareaContents(response: String): List<String> {
        val seen = linkedSetOf<String>()
        patternProvider
                .getPattern(scope.scope, scope.form)
                .matcher(response)
                .apply {
                    while (find()) {
                        seen.add(group(1) ?: "")
                    }
                }
        for (regex in fallbackMessagePatternsPrimary + fallbackMessagePatternsSecondary) {
            regex.findAll(response).forEach { mr ->
                mr.groupValues.getOrNull(1)?.let { seen.add(it) }
            }
        }
        fallbackAnyTextarea.findAll(response).forEach { mr ->
            mr.groupValues.getOrNull(1)?.let { seen.add(it) }
        }
        return seen.toList()
    }

    private fun selectBestFromRawTextareas(raws: List<String>): String {
        val decoded = raws.map { decodeMessage(it) }.filter { it.isNotBlank() }
        return selectBestEditBodyCandidate(decoded)
    }

    private fun extractPostEditReason(response: String): String? =
            postEditReasonPattern.find(response)?.groupValues?.getOrNull(1)

    fun parseForm(response: String): EditPostForm = EditPostForm().also { form ->
        if (response == "nopermission") {
            form.errorCode = EditPostForm.ERROR_NO_PERMISSION
            return form
        }
        // Несколько ed-N_textarea (черновик, подпись, основной текст): нельзя брать только max по длине —
        // иначе побеждает «длинный plain» без [quote], а пост с BBCode короче.
        val htmlNorm = htmlForTextareaTagMatch(response)
        val edMatches = textareaMainEditor.findAll(htmlNorm).toList()
        val fromEdDecoded = edMatches
                .mapNotNull { it.groupValues.getOrNull(1) }
                .mapNotNull { raw -> decodeMessage(raw).takeIf { it.isNotBlank() } }
        val fromMainEditor = fromEdDecoded.takeIf { it.isNotEmpty() }
                ?.let { selectBestEditBodyCandidate(it) }
                ?.takeIf { it.isNotBlank() }
        form.message = fromMainEditor ?: selectBestFromRawTextareas(allRawTextareaContents(response))
        Timber.d(
                "parseForm edTextareaMatches=%d fromMainEditorLen=%d finalLen=%d hasOpenBracket=%s preview=%s",
                edMatches.size,
                fromMainEditor?.length ?: 0,
                form.message.length,
                form.message.contains('['),
                diagPreview(form.message)
        )

        form.editReason = extractPostEditReason(response)?.trim()
                ?.takeIf { r -> r.isNotBlank() && r != "default_edit_reason" }
                .orEmpty()

        return form
    }

    fun parsePoll(response: String): EditPoll? = patternProvider
            .getPattern(scope.scope, scope.poll_info)
            .matcher(response)
            .mapOnce { matcher ->
                val poll = EditPoll()
                patternProvider
                        .getPattern(scope.scope, scope.poll_fucking_invalid_json)
                        .matcher(matcher.group(2).orEmpty())
                        .findAll { jsonMatcher ->
                            poll.addQuestion(EditPoll.Question().apply {
                                val questionIndex = jsonMatcher.group(1).orEmpty().toIntOrNull() ?: return@findAll
                                if (questionIndex > poll.baseIndexOffset) {
                                    poll.baseIndexOffset = questionIndex
                                }
                                index = questionIndex
                                title = jsonMatcher.group(3).orEmpty().fromHtml().orEmpty()
                            })
                        }
                        .reset(matcher.group(3).orEmpty()).findAll { jsonMatcher ->
                            val questionIndex = jsonMatcher.group(1).orEmpty().toIntOrNull() ?: return@findAll
                            EditPoll.findQuestionByIndex(poll, questionIndex)?.also { question ->
                                val choice = EditPoll.Choice()

                                val choiceIndex = jsonMatcher.group(2).orEmpty().toIntOrNull() ?: return@findAll
                                if (choiceIndex > question.baseIndexOffset) {
                                    question.baseIndexOffset = choiceIndex
                                }
                                choice.index = choiceIndex
                                choice.title = jsonMatcher.group(3).orEmpty().fromHtml().orEmpty()
                                question.addChoice(choice)
                            }
                        }
                        .reset(matcher.group(4).orEmpty()).findAll { jsonMatcher ->
                            val questionIndex = jsonMatcher.group(1).orEmpty().toIntOrNull() ?: return@findAll
                            EditPoll.findQuestionByIndex(poll, questionIndex)?.also { question ->
                                val choiceIndex = jsonMatcher.group(2).orEmpty().toIntOrNull() ?: return@findAll
                                val choice = EditPoll.findChoiceByIndex(question, choiceIndex)
                                if (choice != null) {
                                    choice.votes = jsonMatcher.group(3).orEmpty().toIntOrNull() ?: 0
                                }
                            }
                        }
                        .reset(matcher.group(5).orEmpty()).findAll { jsonMatcher ->
                            val questionIndex = jsonMatcher.group(1).orEmpty().toIntOrNull() ?: return@findAll
                            EditPoll.findQuestionByIndex(poll, questionIndex)?.also { question ->
                                question.isMulti = jsonMatcher.group(3).orEmpty() == "1"
                            }
                        }

                poll.maxQuestions = matcher.groupInt(6) ?: 0
                poll.maxChoices = matcher.groupInt(7) ?: 0
                poll.title = matcher.group(8).orEmpty().fromHtml().orEmpty()
                poll
            }

}
