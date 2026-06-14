package forpdateam.ru.forpda.presentation.qms.chat

import forpdateam.ru.forpda.BuildConfig

/**
 * DEBUG snackbar detail for QMS load failures (fragment uses [formatQmsDebugSnackbarMessage]).
 */
fun formatQmsDebugSnackbarMessage(
        kind: QmsLoadErrorKind,
        failureDetail: String,
        baseMessage: String,
        traceId: String,
        debugBuild: Boolean = BuildConfig.DEBUG,
): String? {
    if (!debugBuild) return null
    val detail = when {
        kind == QmsLoadErrorKind.PARSER -> {
            val trace = traceId.take(8)
            listOfNotNull(
                    failureDetail.trim().take(72).takeIf { it.isNotEmpty() && it != baseMessage },
                    "trace=$trace"
            ).joinToString(separator = " · ")
        }
        else -> {
            failureDetail.trim().take(96).takeIf { it.isNotEmpty() && it != baseMessage }
        }
    } ?: return null
    if (detail.isEmpty()) return null
    return "$baseMessage — $detail"
}
