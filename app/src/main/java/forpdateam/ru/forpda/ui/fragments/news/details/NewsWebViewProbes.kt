package forpdateam.ru.forpda.ui.fragments.news.details

import org.json.JSONObject

/**
 * Pure parsers for the JSON payloads returned by the article WebView probe scripts (poll bind and
 * comments-section bind). Extracted from [ArticleContentFragment] so the payload shape and its
 * lenient unwrapping/decoding are in one testable place. Every function tolerates null/blank/quoted
 * input and never throws.
 */
object NewsWebViewProbes {

    data class PollProbe(
            val pollRootFound: Boolean = false,
            val pollId: String = "",
            val optionsCount: Int = 0,
            val canVote: Boolean = false,
            val hasToken: Boolean = false,
            val renderedPollBlock: Boolean = false,
            val readOnlyResults: Boolean = false,
            val boundSubmit: Boolean = false,
            val error: String? = null,
    )

    fun parsePoll(raw: String?): PollProbe =
            runCatching {
                val trimmed = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")
                        ?.replace("\\\\", "\\")
                        ?: return PollProbe()
                if (trimmed.isBlank() || trimmed == "null") return PollProbe()
                val json = JSONObject(trimmed)
                PollProbe(
                        pollRootFound = json.optBoolean("pollRootFound"),
                        pollId = json.optString("pollId"),
                        optionsCount = json.optInt("optionsCount"),
                        canVote = json.optBoolean("canVote"),
                        hasToken = json.optBoolean("hasToken"),
                        renderedPollBlock = json.optBoolean("renderedPollBlock"),
                        readOnlyResults = json.optBoolean("readOnlyResults"),
                        boundSubmit = json.optBoolean("boundSubmit"),
                        error = json.optString("error").takeIf { it.isNotBlank() },
                )
            }.getOrElse { PollProbe(error = it.message) }

    data class CommentsBindProbe(
            val hasRoot: Boolean,
            val hasToggle: Boolean,
            val sectionCount: Int,
            val delegationInstalled: Boolean,
            val commentsJsReady: Boolean,
    )

    fun parseCommentsBind(raw: String?): CommentsBindProbe {
        val text = raw?.trim().orEmpty().let { value ->
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value.substring(1, value.length - 1).replace("\\\"", "\"")
            } else {
                value
            }
        }
        return runCatching {
            val json = JSONObject(text.ifBlank { "{}" })
            CommentsBindProbe(
                    hasRoot = json.optBoolean("hasRoot"),
                    hasToggle = json.optBoolean("hasToggle"),
                    sectionCount = json.optInt("sectionCount"),
                    delegationInstalled = json.optBoolean("delegation"),
                    commentsJsReady = json.optBoolean("commentsJsReady"),
            )
        }.getOrElse { CommentsBindProbe(false, false, 0, false, false) }
    }
}
