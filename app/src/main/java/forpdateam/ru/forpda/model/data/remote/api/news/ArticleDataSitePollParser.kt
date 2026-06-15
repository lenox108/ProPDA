package forpdateam.ru.forpda.model.data.remote.api.news

import org.json.JSONArray

/**
 * Pure parser for the "data-site" embedded news-poll payload.
 *
 * Extracted from [ArticleParser] as part of the god-class
 * decomposition (§1.1 of REFACTOR_PLAN.md). The data-site poll is
 * the 4pda template that ships poll questions/answers inline in a
 * JSON array (and occasionally as a less-well-formed fallback that
 * we hand-split). This class owns the decode path end-to-end and
 * only depends on:
 *  - an `isUsablePollText` predicate (reused from ArticleParser
 *    for the same filtering rules),
 *  - a `pollVoteCookieAnswers` lookup (the voted-answer
 *    mirror held by ArticleParser from `syncPollVoteCookies`),
 *  - an `articleFromHtml` decoder (the String? extension that
 *    [ArticleParser] applies to URL fragments before parsing).
 *
 * Behaviour is byte-identical to the previous in-class methods.
 */
internal class ArticleDataSitePollParser(
        private val isUsablePollText: (String) -> Boolean,
        private val pollVoteCookieAnswers: (String) -> Set<String>,
        private val articleFromHtml: (String?) -> String?,
) {

    fun parse(encodedPayload: String): DataSitePoll? {
        val payload = articleFromHtml(articleFromHtml(encodedPayload))
                ?.replace('\u00A0', ' ')
                ?.trim()
                .orEmpty()
        if (payload.isBlank()) return null
        return parseJson(payload) ?: parseCompat(payload)
    }

    private fun parseJson(payload: String): DataSitePoll? = runCatching {
        val json = JSONArray(payload)
        val pollId = json.optLong(0).takeIf { it > 0 }?.toString() ?: return null
        val title = json.optString(1).takeIf { it.isNotBlank() } ?: "Опрос"
        val multiSelect = json.optInt(2, 0) != 0
        val answers = json.optJSONArray(3) ?: return null
        val jsonSelectedAnswers = json.optJSONArray(5)
                ?.let { selected ->
                    (0 until selected.length())
                            .mapNotNull { index -> selected.optLong(index).takeIf { it > 0 }?.toString() }
                            .toSet()
                }
                .orEmpty()
        val options = (0 until answers.length()).mapNotNull { index ->
            val answer = answers.optJSONArray(index) ?: return@mapNotNull null
            val value = answer.optLong(0).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
            val optionTitle = answer.optString(1).replace('\u00A0', ' ').takeIf { isUsablePollText(it) }
                    ?: return@mapNotNull null
            PollOption(
                    value = value,
                    title = optionTitle,
                    votes = answer.optInt(2, 0),
                    selected = false
            )
        }
        mergeVoteState(
                pollId = pollId,
                title = title,
                multiSelect = multiSelect,
                options = options,
                totalVotes = json.optInt(4, 0),
                jsonSelectedAnswers = jsonSelectedAnswers,
                jsonVotedFlag = json.optBoolean(6, false)
        )
    }.getOrNull()

    private fun parseCompat(payload: String): DataSitePoll? {
        val values = splitJsonArray(payload)
        if (values.size < 4) return null
        val pollId = values.getOrNull(0)?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.toString()
                ?: return null
        val title = decodeJsonString(values.getOrNull(1).orEmpty()).takeIf { it.isNotBlank() } ?: "Опрос"
        val multiSelect = values.getOrNull(2)?.trim()?.toIntOrNull() != 0
        val totalVotes = values.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
        val jsonSelectedAnswers = splitJsonArray(values.getOrNull(5).orEmpty())
                .mapNotNull { it.trim().toLongOrNull()?.takeIf { value -> value > 0 }?.toString() }
                .toSet()
        val options = splitJsonArray(values.getOrNull(3).orEmpty()).mapNotNull { rawAnswer ->
            val answer = splitJsonArray(rawAnswer)
            val value = answer.getOrNull(0)?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.toString()
                    ?: return@mapNotNull null
            val optionTitle = decodeJsonString(answer.getOrNull(1).orEmpty())
                    .replace('\u00A0', ' ')
                    .takeIf { isUsablePollText(it) }
                    ?: return@mapNotNull null
            PollOption(
                    value = value,
                    title = optionTitle,
                    votes = answer.getOrNull(2)?.trim()?.toIntOrNull() ?: 0,
                    selected = false
            )
        }
        return mergeVoteState(
                pollId = pollId,
                title = title,
                multiSelect = multiSelect,
                options = options,
                totalVotes = totalVotes,
                jsonSelectedAnswers = jsonSelectedAnswers,
                jsonVotedFlag = values.getOrNull(6)?.trim()?.equals("true", ignoreCase = true) == true
        )
    }

    private fun mergeVoteState(
            pollId: String,
            title: String,
            multiSelect: Boolean,
            options: List<PollOption>,
            totalVotes: Int,
            jsonSelectedAnswers: Set<String>,
            jsonVotedFlag: Boolean
    ): DataSitePoll {
        val cookieAnswers = pollVoteCookieAnswers(pollId)
                .filter { answerId -> options.any { it.value == answerId } }
                .toSet()
        val selectedAnswers = when {
            jsonSelectedAnswers.isNotEmpty() -> jsonSelectedAnswers
            cookieAnswers.isNotEmpty() -> cookieAnswers
            else -> emptySet()
        }
        val voted = selectedAnswers.isNotEmpty() || jsonVotedFlag
        return DataSitePoll(
                pollId = pollId,
                title = title,
                multiSelect = multiSelect,
                options = options.map { option -> option.copy(selected = option.value in selectedAnswers) },
                totalVotes = totalVotes,
                voted = voted
        )
    }

    private fun splitJsonArray(source: String): List<String> {
        val trimmed = source.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val values = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escaped = false
        var start = 1
        for (index in 1 until trimmed.lastIndex) {
            val char = trimmed[index]
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '[' -> depth++
                !inString && char == ']' -> depth--
                !inString && depth == 0 && char == ',' -> {
                    values += trimmed.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        values += trimmed.substring(start, trimmed.lastIndex).trim()
        return values.filter { it.isNotEmpty() }
    }

    private fun decodeJsonString(source: String): String {
        val trimmed = source.trim()
        val body = if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.lastIndex)
        } else {
            trimmed
        }
        return body
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
    }
}
