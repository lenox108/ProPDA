package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.Comment

/**
 * Resolves the comment id to scroll to after a reply/post mutation.
 * Falls back beyond max-id heuristics when ids are localized or tree diff is ambiguous.
 */
object NewCommentDetector {

    private val commentIdFromFragmentRegex = Regex("""comment[-_]?(\d+)""", RegexOption.IGNORE_CASE)
    private val entryIdFromFragmentRegex = Regex("""entry(\d+)""", RegexOption.IGNORE_CASE)

    fun extractCommentIdFromUrl(url: String?): Int {
        if (url.isNullOrBlank()) return 0
        val fragment = url.substringAfter('#', "")
        commentIdFromFragmentRegex.find(fragment)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { id ->
            if (id > 0) return id
        }
        entryIdFromFragmentRegex.find(fragment)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { id ->
            if (id > 0) return id
        }
        return 0
    }

    fun resolvePendingScrollCommentId(
            tree: Comment?,
            knownIds: Set<Int>,
            redirectUrl: String?,
            submittedText: String? = null,
            currentUserId: Int = 0
    ): Int {
        val fromRedirect = extractCommentIdFromUrl(redirectUrl)
        val fromTree = tree?.let { findNewCommentId(it, knownIds, submittedText, currentUserId) } ?: 0
        // POST redirect anchor is authoritative when the parsed tree lags or is ambiguous.
        if (fromRedirect > 0 && fromRedirect !in knownIds) return fromRedirect
        if (fromTree > 0) return fromTree
        return fromRedirect.takeIf { it > 0 } ?: 0
    }

    fun findNewCommentId(
            tree: Comment,
            knownIds: Set<Int>,
            submittedText: String? = null,
            currentUserId: Int = 0
    ): Int {
        val newComments = flattenComments(tree).filter { it.id > 0 && it.id !in knownIds }
        if (newComments.isEmpty()) return 0
        if (newComments.size == 1) return newComments.first().id

        val normalizedSubmitted = normalizeCommentText(submittedText)
        if (normalizedSubmitted.isNotBlank()) {
            newComments.firstOrNull { candidate ->
                val normalizedContent = normalizeCommentText(candidate.content)
                normalizedContent.contains(normalizedSubmitted) ||
                        normalizedSubmitted.contains(normalizedContent.take(120))
            }?.id?.let { return it }
        }

        if (currentUserId > 0) {
            newComments.filter { it.userId == currentUserId }
                    .maxByOrNull { it.id }
                    ?.id
                    ?.let { return it }
        }

        return newComments.maxByOrNull { it.id }?.id ?: 0
    }

    internal fun normalizeCommentText(text: String?): String =
            text.orEmpty()
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ", ignoreCase = true)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .lowercase()

    private fun flattenComments(root: Comment): List<Comment> {
        val result = ArrayList<Comment>()
        fun walk(comment: Comment) {
            comment.children.forEach { child ->
                result.add(child)
                walk(child)
            }
        }
        walk(root)
        return result
    }
}
