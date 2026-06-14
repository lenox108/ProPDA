package forpdateam.ru.forpda.model.data.remote.api.qms

/**
 * Classifies raw QMS HTTP bodies before parsing so login/error pages are not treated as empty chats.
 */
object QmsHtmlValidator {

    const val MIN_QMS_BODY_LEN = 80

    enum class PageKind {
        QMS_THREAD,
        QMS_EMPTY_THREAD,
        LOGIN,
        CAPTCHA,
        ERROR,
        UNKNOWN
    }

    data class ThreadSignals(
            val parserRootFound: Boolean,
            val hasEditorForm: Boolean,
            val hasPagination: Boolean,
            val messageMarkers: Int,
            val containerMessageMarkers: Int,
            val contentMarkers: Int
    )

    fun classify(httpCode: Int, html: String): PageKind {
        if (httpCode !in 200..299) return PageKind.ERROR
        if (html.isBlank()) return PageKind.UNKNOWN
        if (QmsResponseBody.looksLikeJsonEnvelope(html)) return PageKind.UNKNOWN
        if (looksLikeCaptcha(html)) return PageKind.CAPTCHA
        if (looksLikeErrorPage(html)) return PageKind.ERROR
        // QMS thread markers before login heuristics — forum nav links often contain act=auth.
        if (looksLikeQmsThread(html)) {
            return if (looksLikeEmptyThread(html)) PageKind.QMS_EMPTY_THREAD else PageKind.QMS_THREAD
        }
        if (looksLikeLoginPage(html)) return PageKind.LOGIN
        return PageKind.UNKNOWN
    }

    fun looksLikeLoginPage(html: String): Boolean =
            html.contains("wp-login.php", ignoreCase = true) ||
                    html.contains("loginform", ignoreCase = true) ||
                    html.contains("act=login", ignoreCase = true) ||
                    html.contains("необходимо авторизоваться", ignoreCase = true) ||
                    (html.contains("password", ignoreCase = true) &&
                            html.contains("auth", ignoreCase = true) &&
                            !looksLikeQmsThread(html))

    fun looksLikeCaptcha(html: String): Boolean =
            html.contains("captcha", ignoreCase = true) &&
                    (html.contains("captcha-image", ignoreCase = true) ||
                            html.contains("captcha-time", ignoreCase = true))

    fun looksLikeErrorPage(html: String): Boolean =
            html.contains("errors-list", ignoreCase = true) ||
                    html.contains("страница не найдена", ignoreCase = true) ||
                    (html.length < 4_000 &&
                            (html.contains(">404<", ignoreCase = true) ||
                                    html.contains("error-404", ignoreCase = true)))

    fun looksLikeQmsThread(html: String): Boolean =
            html.contains("act=qms", ignoreCase = true) ||
                    html.contains("mess_list", ignoreCase = true) ||
                    html.contains("mess_container", ignoreCase = true) ||
                    html.contains("msg-content", ignoreCase = true) ||
                    html.contains("list-group-item", ignoreCase = true) ||
                    (html.contains("name=\"mid\"", ignoreCase = true) &&
                            html.contains("name=\"t\"", ignoreCase = true))

    fun looksLikeEmptyThread(html: String): Boolean {
        val signals = measureThread(html)
        return signals.parserRootFound &&
                signals.hasEditorForm &&
                signals.messageMarkers == 0 &&
                signals.containerMessageMarkers == 0 &&
                // System-notification dialogs render `msg-content` rows without
                // `data-message-id`/`data-mess-id`; treat any message content as non-empty.
                signals.contentMarkers == 0
    }

    fun measureThread(html: String): ThreadSignals {
        val rootFound = html.contains("mess_list", ignoreCase = true) ||
                html.contains("mess_container", ignoreCase = true) ||
                html.contains("msg-content", ignoreCase = true) ||
                html.contains("list-group-item", ignoreCase = true)
        val hasEditor = html.contains("name=\"message\"", ignoreCase = true) ||
                html.contains("thread-inside-bottom", ignoreCase = true)
        val hasPagination = html.contains("data-message-id", ignoreCase = true) ||
                html.contains("data-mess-id", ignoreCase = true) ||
                html.contains("get-thread-messages", ignoreCase = true)
        val messageMarkers = Regex("""data-message-id\s*=\s*["']""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .count()
        val containerMessageMarkers = Regex("""data-mess-id\s*=\s*["']""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .count()
        val contentMarkers = Regex("""\bmsg-content\b""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .count()
        return ThreadSignals(
                parserRootFound = rootFound,
                hasEditorForm = hasEditor,
                hasPagination = hasPagination,
                messageMarkers = messageMarkers,
                containerMessageMarkers = containerMessageMarkers,
                contentMarkers = contentMarkers
        )
    }

    fun responseLooksLikeQms(html: String): Boolean =
            classify(200, html) == PageKind.QMS_THREAD ||
                    classify(200, html) == PageKind.QMS_EMPTY_THREAD
}
