package forpdateam.ru.forpda.presentation.qms.chat

/**
 * Machine-readable QMS dialog open failure stage (logcat / diagnostics).
 */
enum class QmsOpenErrorReason {
    NetworkError,
    AuthExpired,
    CaptchaPage,
    ParserRootMissing,
    EmptyInvalidResponse,
    CacheInvalid,
    StaleRequest,
    Unknown
}

fun QmsLoadErrorKind.toOpenErrorReason(message: String? = null): QmsOpenErrorReason =
        when (this) {
            QmsLoadErrorKind.NETWORK -> QmsOpenErrorReason.NetworkError
            QmsLoadErrorKind.SESSION -> QmsOpenErrorReason.AuthExpired
            QmsLoadErrorKind.CAPTCHA -> QmsOpenErrorReason.CaptchaPage
            QmsLoadErrorKind.PARSER -> when {
                message?.contains("unexpected_page", ignoreCase = true) == true ->
                        QmsOpenErrorReason.ParserRootMissing
                message?.contains("parser_empty", ignoreCase = true) == true ->
                        QmsOpenErrorReason.EmptyInvalidResponse
                else -> QmsOpenErrorReason.ParserRootMissing
            }
            QmsLoadErrorKind.SERVER -> QmsOpenErrorReason.NetworkError
            QmsLoadErrorKind.UNKNOWN -> when {
                message?.contains("canceled", ignoreCase = true) == true ->
                        QmsOpenErrorReason.StaleRequest
                else -> QmsOpenErrorReason.Unknown
            }
        }
