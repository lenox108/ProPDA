package forpdateam.ru.forpda.presentation.articles.detail.comments

import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

object CommentsTrace {
    private const val TAG = "CommentsTrace"

    fun log(
            articleId: Int,
            requestId: Int,
            generation: Int,
            phase: String,
            commentsCountHint: Int = -1,
            parsedCount: Int = -1,
            state: String = "",
            reason: String = ""
    ) {
        if (!BuildConfig.DEBUG) return
        Timber.tag(TAG).i(
                "articleId=$articleId requestId=$requestId generation=$generation phase=$phase " +
                        "commentsCountHint=$commentsCountHint parsedCount=$parsedCount state=$state reason=$reason"
        )
    }
}
