package forpdateam.ru.forpda.appupdates

import android.text.Html

class AppUpdateParser {

    data class Candidate(
        val version: SemanticVersion,
        val url: String,
        val postId: Long?,
        val confidence: Int,
        val description: String? = null,
        val sourceSt: Int? = null
    )

    fun findBestCandidate(
        html: String,
        sourceUrl: String,
        allowedPostIds: Set<Long>? = null
    ): Candidate? {
        val posts = splitPosts(html)
        val postCandidates = posts
            .filter { post -> allowedPostIds == null || post.postId in allowedPostIds }
            .flatMap { post ->
                findCandidatesInHtml(post.html, postUrl(post.postId), post.postId, extractSt(sourceUrl))
            }

        if (postCandidates.isNotEmpty()) {
            return chooseBest(postCandidates)
        }

        if (allowedPostIds != null && posts.isNotEmpty()) {
            return null
        }

        val sourcePostId = extractPostId(sourceUrl)
        if (allowedPostIds != null && sourcePostId != null && sourcePostId !in allowedPostIds) {
            return null
        }
        val fallbackUrl = sourcePostId?.let { postUrl(it) } ?: sourceUrl
        return chooseBest(findCandidatesInHtml(html, fallbackUrl, sourcePostId, extractSt(sourceUrl)))
    }

    fun findLatestPageUrls(html: String): List<String> {
        val parsed = FORUM_PAGINATION_REGEX.find(html)
        if (parsed != null) {
            val lastPageIndex = parsed.groupValues[1].toIntOrNull()
            val perPage = parsed.groupValues[2].toIntOrNull()
            if (lastPageIndex != null && perPage != null && perPage > 0) {
                return latestStarts((lastPageIndex * perPage).coerceAtLeast(0), perPage)
                    .map { topicPageUrl(it) }
            }
        }

        val starts = ST_PARAM_REGEX.findAll(html.replace("&amp;", "&"))
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it >= 0 }
            .distinct()
            .sorted()
            .toList()
        if (starts.isEmpty()) return emptyList()

        val lastSt = starts.last()
        val perPage = starts.zipWithNext()
            .map { (left, right) -> right - left }
            .filter { it > 0 }
            .minOrNull()
            ?: DEFAULT_PER_PAGE
        return latestStarts(lastSt, perPage).map { topicPageUrl(it) }
    }

    private fun findCandidatesInHtml(html: String, url: String, postId: Long?, sourceSt: Int?): List<Candidate> {
        val text = htmlToText(html)
        val candidates = mutableListOf<Candidate>()

        candidates += parseNewVersionPost(text, url, postId, sourceSt)
        candidates += parseDownloadSection(text, url, postId, sourceSt)
        candidates += parseWeightedVersions(text, url, postId, sourceSt)

        return candidates
    }

    private fun parseNewVersionPost(text: String, url: String, postId: Long?, sourceSt: Int?): List<Candidate> {
        if (!TYPE_NEW_VERSION_REGEX.containsMatchIn(text)) return emptyList()
        return VERSION_LINE_REGEX.findAll(text).map { match ->
            val version = parseAppVersion(match.groupValues[1]) ?: return@map null
            Candidate(
                version = version,
                url = postId?.let { postUrl(it) } ?: url,
                postId = postId,
                confidence = CONFIDENCE_NEW_VERSION_POST,
                description = extractDescription(text, match.range.last),
                sourceSt = sourceSt
            )
        }.filterNotNull().toList()
    }

    private fun parseDownloadSection(text: String, url: String, postId: Long?, sourceSt: Int?): List<Candidate> {
        val downloadIndex = text.indexOf("Скачать", ignoreCase = true)
        if (downloadIndex < 0) return emptyList()
        val section = text.substring(downloadIndex, minOf(text.length, downloadIndex + DOWNLOAD_SECTION_LIMIT))
        return VERSION_LINE_REGEX.findAll(section).map { match ->
            val version = parseAppVersion(match.groupValues[1]) ?: return@map null
            Candidate(
                version = version,
                url = url,
                postId = postId,
                confidence = CONFIDENCE_DOWNLOAD_SECTION,
                description = section.lineAt(match.range.first)?.substringAfter(match.groupValues[1], "")?.trim()?.ifBlank { null },
                sourceSt = sourceSt
            )
        }.filterNotNull().toList()
    }

    private fun parseWeightedVersions(text: String, url: String, postId: Long?, sourceSt: Int?): List<Candidate> {
        return VERSION_ANYWHERE_REGEX.findAll(text).map { match ->
            val version = parseAppVersion(match.value) ?: return@map null
            val windowStart = maxOf(0, match.range.first - CONTEXT_WINDOW)
            val windowEnd = minOf(text.length, match.range.last + CONTEXT_WINDOW)
            val context = text.substring(windowStart, windowEnd)
            val confidence = if (WEIGHTED_CONTEXT_REGEX.containsMatchIn(context)) {
                CONFIDENCE_WEIGHTED_CONTEXT
            } else {
                CONFIDENCE_GENERIC_VERSION
            }
            Candidate(
                version = version,
                url = url,
                postId = postId,
                confidence = confidence,
                description = null,
                sourceSt = sourceSt
            )
        }.filterNotNull().toList()
    }

    private fun parseAppVersion(value: String): SemanticVersion? {
        if (value.isDateLikeVersionToken()) return null
        val version = SemanticVersion.parse(value.trimStart('v', 'V')) ?: return null
        if (version.major !in APP_VERSION_MAJOR_RANGE) return null
        if (version.minor !in APP_VERSION_PART_RANGE) return null
        if (version.patch !in APP_VERSION_PART_RANGE) return null
        return version
    }

    private fun String.isDateLikeVersionToken(): Boolean {
        val parts = trimStart('v', 'V')
            .substringBefore(' ')
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return false
        val (day, month, year) = parts
        return day in 1..31 && month in 1..12 && year in 1900..2100
    }

    private fun htmlToText(html: String): String {
        val withLineBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(?:p|div|li|tr|h[1-6])>"), "\n")
        return Html.fromHtml(withLineBreaks, Html.FROM_HTML_MODE_LEGACY)
            .toString()
    }

    private fun extractDescription(text: String, versionEnd: Int): String? {
        val tail = text.substring(versionEnd.coerceAtMost(text.length))
        DESCRIPTION_REGEX.find(tail)?.groupValues?.getOrNull(1)
            ?.substringBefore("Прикрепленные")
            ?.trim()
            ?.ifBlank { null }
            ?.let { return it }
        return tail.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(8)
            .firstOrNull { it.startsWith("Краткое описание:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.ifBlank { null }
    }

    private fun extractPostId(value: String): Long? {
        val decoded = value.replace("&amp;", "&")
        return POST_ID_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: ENTRY_ID_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun chooseBest(candidates: List<Candidate>): Candidate? {
        val distinct = candidates.distinctBy { it.version to it.url }
        val reliable = distinct.filter { it.confidence >= CONFIDENCE_WEIGHTED_CONTEXT }
        return (reliable.ifEmpty { distinct })
            .maxWithOrNull(
                compareBy<Candidate> { it.version }
                    .thenBy { it.confidence }
                    .thenBy { it.sourceSt ?: -1 }
                    .thenBy { it.postId ?: -1L }
            )
    }

    private fun splitPosts(html: String): List<PostHtml> {
        val markers = POST_MARKER_REGEX.findAll(html)
            .mapNotNull { match ->
                val postId = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toLongOrNull()
                    ?: return@mapNotNull null
                PostMarker(postId, match.range.first)
            }
            .distinctBy { it.postId }
            .sortedBy { it.start }
            .toList()
        if (markers.isEmpty()) return emptyList()

        return markers.mapIndexed { index, marker ->
            val end = markers.getOrNull(index + 1)?.start ?: html.length
            PostHtml(marker.postId, html.substring(marker.start, end))
        }
    }

    private fun latestStarts(lastSt: Int, perPage: Int): List<Int> {
        return (3 downTo 0)
            .map { pageOffset -> (lastSt - pageOffset * perPage).coerceAtLeast(0) }
            .distinct()
    }

    private fun postUrl(postId: Long): String = "$TOPIC_URL&view=findpost&p=$postId"

    private fun topicPageUrl(st: Int): String = "$TOPIC_URL&st=$st"

    private fun extractSt(value: String): Int? {
        val decoded = value.replace("&amp;", "&")
        return ST_PARAM_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun String.lineAt(index: Int): String? {
        if (index !in indices) return null
        val start = lastIndexOf('\n', index).let { if (it < 0) 0 else it + 1 }
        val end = indexOf('\n', index).let { if (it < 0) length else it }
        return substring(start, end).trim()
    }

    private data class PostMarker(val postId: Long, val start: Int)

    private data class PostHtml(val postId: Long, val html: String)

    companion object {
        const val TOPIC_ID = 1121483
        const val HEADER_POST_ID = 143179849L
        const val TOPIC_URL = "https://4pda.to/forum/index.php?showtopic=$TOPIC_ID"
        const val HEADER_POST_URL = "$TOPIC_URL&view=findpost&p=$HEADER_POST_ID"
        const val LAST_KNOWN_PAGE_URL = "$TOPIC_URL&st=840"

        private const val DEFAULT_PER_PAGE = 20
        private const val DOWNLOAD_SECTION_LIMIT = 6_000
        private const val CONTEXT_WINDOW = 80
        private const val CONFIDENCE_NEW_VERSION_POST = 100
        private const val CONFIDENCE_DOWNLOAD_SECTION = 90
        private const val CONFIDENCE_WEIGHTED_CONTEXT = 60
        private const val CONFIDENCE_GENERIC_VERSION = 10
        private val APP_VERSION_MAJOR_RANGE = 0..9
        private val APP_VERSION_PART_RANGE = 0..99

        private val VERSION_LINE_REGEX = Regex("""(?im)\bВерсия\s*:\s*(\d+\.\d+\.\d+)""")
        private val TYPE_NEW_VERSION_REGEX = Regex("""(?im)\bТип\s*:\s*Новая\s+версия\b""")
        private val DESCRIPTION_REGEX = Regex("""(?is)Краткое\s+описание\s*:\s*(.+?)(?:\n|<|$)""")
        private val VERSION_ANYWHERE_REGEX = Regex("""(?i)\b(?:v)?\d+\.\d+\.\d+(?:\s+beta)?\b""")
        private val WEIGHTED_CONTEXT_REGEX = Regex("""(?i)(версия|обновлен|обновление|propda|apk|скачать)""")
        private val POST_ID_REGEX = Regex("""[?&]p=(\d+)""")
        private val ENTRY_ID_REGEX = Regex("""#entry(\d+)""")
        private val ST_PARAM_REGEX = Regex("""[?&]st=(\d+)""")
        private val FORUM_PAGINATION_REGEX = Regex(
            """parseInt\((\d*)\)[\s\S]*?parseInt\(st\*(\d*)\)[\s\S]*?pagination">[\s\S]*?<span[^>]*?>([^<]*?)<\/span>"""
        )
        private val POST_MARKER_REGEX = Regex(
            """(?is)(?:\b(?:id|name)\s*=\s*["']entry(\d+)["']|\bdata-(?:post-id|post)\s*=\s*["'](\d+)["']|\bdata-entry-pid\s*=\s*["'](\d+)["'])"""
        )
    }
}
