package forpdateam.ru.forpda.model.data.remote.api.reputation

/**
 * Classifies raw reputation HTTP bodies before parsing so login/error pages are not treated as empty history.
 */
object ReputationHtmlValidator {

    enum class PageKind {
        REPUTATION_HISTORY,
        REPORT_FORM,
        LOGIN,
        CAPTCHA,
        ERROR,
        UNKNOWN
    }

    fun classifyHistory(httpCode: Int, html: String): PageKind {
        if (httpCode !in 200..299) return PageKind.ERROR
        if (html.isBlank()) return PageKind.UNKNOWN
        if (looksLikeCaptcha(html)) return PageKind.CAPTCHA
        if (looksLikeErrorPage(html)) return PageKind.ERROR
        if (looksLikeReputationHistory(html)) return PageKind.REPUTATION_HISTORY
        if (looksLikeLoginPage(html)) return PageKind.LOGIN
        return PageKind.UNKNOWN
    }

    fun classifyReportForm(httpCode: Int, html: String): PageKind {
        if (httpCode !in 200..299) return PageKind.ERROR
        if (html.isBlank()) return PageKind.UNKNOWN
        if (looksLikeCaptcha(html)) return PageKind.CAPTCHA
        if (looksLikeErrorPage(html)) return PageKind.ERROR
        if (looksLikeReportForm(html)) return PageKind.REPORT_FORM
        if (looksLikeLoginPage(html)) return PageKind.LOGIN
        return PageKind.UNKNOWN
    }

    fun ensureHistoryPage(httpCode: Int, html: String) {
        when (classifyHistory(httpCode, html)) {
            PageKind.REPUTATION_HISTORY -> Unit
            PageKind.LOGIN -> throw IllegalStateException("Требуется авторизация")
            PageKind.CAPTCHA -> throw IllegalStateException("Требуется прохождение captcha")
            PageKind.ERROR -> throw IllegalStateException("Ошибка загрузки репутации")
            PageKind.UNKNOWN -> throw IllegalStateException("Не удалось распознать страницу репутации")
            PageKind.REPORT_FORM -> throw IllegalStateException("Не удалось распознать страницу репутации")
        }
    }

    fun ensureReportFormPage(httpCode: Int, html: String) {
        when (classifyReportForm(httpCode, html)) {
            PageKind.REPORT_FORM -> Unit
            PageKind.LOGIN -> throw IllegalStateException("Требуется авторизация")
            PageKind.CAPTCHA -> throw IllegalStateException("Требуется прохождение captcha")
            PageKind.ERROR -> throw IllegalStateException("Ошибка загрузки формы жалобы")
            PageKind.UNKNOWN -> throw IllegalStateException("Сервер не вернул форму жалобы")
            PageKind.REPUTATION_HISTORY -> throw IllegalStateException("Сервер не вернул форму жалобы")
        }
    }

    fun looksLikeLoginPage(html: String): Boolean =
            html.contains("wp-login.php", ignoreCase = true) ||
                    html.contains("loginform", ignoreCase = true) ||
                    html.contains("act=login", ignoreCase = true) ||
                    html.contains("act=auth", ignoreCase = true) ||
                    html.contains("необходимо авторизоваться", ignoreCase = true) ||
                    (html.contains("password", ignoreCase = true) &&
                            html.contains("auth", ignoreCase = true) &&
                            !looksLikeReputationHistory(html))

    fun looksLikeCaptcha(html: String): Boolean =
            html.contains("captcha", ignoreCase = true) &&
                    (html.contains("captcha-image", ignoreCase = true) ||
                            html.contains("captcha-time", ignoreCase = true) ||
                            html.contains("g-recaptcha", ignoreCase = true))

    fun looksLikeErrorPage(html: String): Boolean =
            html.contains("errors-list", ignoreCase = true) ||
                    html.contains("errorwrap", ignoreCase = true) ||
                    html.contains("страница не найдена", ignoreCase = true) ||
                    (html.length < 4_000 &&
                            (html.contains(">404<", ignoreCase = true) ||
                                    html.contains("error-404", ignoreCase = true)))

    fun looksLikeReputationHistory(html: String): Boolean =
            html.contains("rep-row-", ignoreCase = true) ||
                    html.contains("act=rep", ignoreCase = true) ||
                    html.contains("view=history", ignoreCase = true) ||
                    (html.contains("maintitle", ignoreCase = true) &&
                            html.contains("showuser=", ignoreCase = true) &&
                            html.contains("/-", ignoreCase = true))

    fun looksLikeReportForm(html: String): Boolean =
            (html.contains("<form", ignoreCase = true) &&
                    html.contains("act=report", ignoreCase = true)) ||
                    (html.contains("<form", ignoreCase = true) &&
                            html.contains("name=\"message\"", ignoreCase = true) &&
                            html.contains("reputation", ignoreCase = true))
}
