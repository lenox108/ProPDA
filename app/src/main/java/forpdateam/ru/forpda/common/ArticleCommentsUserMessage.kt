package forpdateam.ru.forpda.common

/**
 * User-facing Russian text for news comment load failures (not technical codes).
 */
object ArticleCommentsUserMessage {

    fun forReason(code: String?): String = when (code?.trim().orEmpty()) {
        "comments_html_present_but_parse_empty" ->
            "Не удалось разобрать комментарии на странице. Нажмите «Повторить» или обновите статью."
        "comments_count_positive_but_no_source",
        "comment_list_shell_unresolved_positive_count" ->
            "Не удалось загрузить комментарии. Проверьте сеть и нажмите «Повторить»."
        "comments_load_timeout" ->
            "Загрузка комментариев заняла слишком много времени. Попробуйте ещё раз."
        else -> "Не удалось загрузить комментарии. Попробуйте ещё раз."
    }

    fun forThrowable(throwable: Throwable?): String {
        if (throwable == null) return forReason(null)
        val code = throwable.message?.trim().orEmpty()
        if (code in KNOWN_CODES || looksLikeTechnicalCode(code)) {
            return forReason(code)
        }
        return code.ifBlank { forReason(null) }
    }

    private fun looksLikeTechnicalCode(message: String): Boolean =
            message.contains('_') && !message.contains(' ')

    private val KNOWN_CODES = setOf(
            "comments_html_present_but_parse_empty",
            "comments_count_positive_but_no_source",
            "comment_list_shell_unresolved_positive_count",
            "comments_load_timeout"
    )
}
