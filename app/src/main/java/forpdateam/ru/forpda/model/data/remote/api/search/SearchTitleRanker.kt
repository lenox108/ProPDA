package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import java.util.Locale
import kotlin.math.min

object SearchTitleRanker {

    fun rank(result: SearchResult): SearchResult {
        val settings = result.settings ?: return result
        if (!settings.supportsTitleFuzzyRanking()) return result

        val query = SearchTitleSimilarity.normalize(settings.query)
        if (query.isEmpty()) return result

        val ranked = result.items
                .mapIndexed { index, item -> RankedItem(item, SearchTitleSimilarity.rank(query, item.title), index) }
                .sortedWith(compareBy<RankedItem> { it.rank.priority }.thenBy { it.rank.distance }.thenBy { it.index })
                .map { it.item }

        result.items.clear()
        result.items.addAll(ranked)
        return result
    }

    private fun SearchSettings.supportsTitleFuzzyRanking(): Boolean =
            resourceType == SearchSettings.RESOURCE_FORUM.first &&
                    result == SearchSettings.RESULT_TOPICS.first &&
                    source == SearchSettings.SOURCE_TITLES.first

    private data class RankedItem(
            val item: SearchItem,
            val rank: SearchTitleSimilarity.Rank,
            val index: Int
    )
}

object SearchTitleSimilarity {

    private const val PRIORITY_EXACT = 0
    private const val PRIORITY_CONTAINS = 1
    private const val PRIORITY_FUZZY = 2
    private const val PRIORITY_OTHER = 3

    data class Rank(val priority: Int, val distance: Int) {
        val isMatch: Boolean
            get() = priority != PRIORITY_OTHER
    }

    fun rank(normalizedQuery: String, title: String?): Rank {
        val normalizedTitle = normalize(title.orEmpty())
        if (normalizedQuery.isEmpty() || normalizedTitle.isEmpty()) {
            return Rank(PRIORITY_OTHER, Int.MAX_VALUE)
        }
        if (normalizedTitle == normalizedQuery) return Rank(PRIORITY_EXACT, 0)
        if (normalizedTitle.contains(normalizedQuery)) return Rank(PRIORITY_CONTAINS, 0)

        val distance = minTokenDistance(normalizedQuery, normalizedTitle)
        return if (distance <= maxDistance(normalizedQuery)) {
            Rank(PRIORITY_FUZZY, distance)
        } else {
            Rank(PRIORITY_OTHER, Int.MAX_VALUE)
        }
    }

    fun normalize(value: String): String =
            value.lowercase(Locale.ROOT)
                    .replace('ё', 'е')
                    .replace(Regex("""[^\p{L}\p{Nd}]+"""), " ")
                    .trim()
                    .replace(Regex("""\s+"""), " ")

    private fun minTokenDistance(query: String, title: String): Int {
        var best = damerauLevenshtein(query, title)
        for (token in title.split(' ')) {
            if (token.isEmpty()) continue
            best = min(best, damerauLevenshtein(query, token))
        }
        return best
    }

    private fun maxDistance(query: String): Int = when {
        query.length < 4 -> 0
        query.length < 8 -> 1
        else -> 2
    }

    private fun damerauLevenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previousPrevious = IntArray(right.length + 1)
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val substitutionCost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                        current[j - 1] + 1,
                        previous[j] + 1,
                        previous[j - 1] + substitutionCost
                )
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
                    current[j] = min(current[j], previousPrevious[j - 2] + 1)
                }
            }
            val swap = previousPrevious
            for (j in previous.indices) {
                swap[j] = previous[j]
            }
            previous = current.also { current = previous }
        }
        return previous[right.length]
    }
}
