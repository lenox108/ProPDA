package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTitleRankerTest {

    @Test
    fun exactAndContainsTitlesOutrankFuzzyTitles() {
        val result = titleResult("adguard").apply {
            items.add(topic("Adugard"))
            items.add(topic("AdGuard"))
            items.add(topic("AdGuard для Android"))
        }

        SearchTitleRanker.rank(result)

        assertEquals(listOf("AdGuard", "AdGuard для Android", "Adugard"), result.items.map { it.title })
    }

    @Test
    fun oneOrTwoCharacterTypoMatchesTopicTitle() {
        val result = titleResult("adguuard").apply {
            items.add(topic("Firewall"))
            items.add(topic("AdGuard"))
            items.add(topic("DNS"))
        }

        SearchTitleRanker.rank(result)

        assertEquals("AdGuard", result.items.first().title)
    }

    @Test
    fun bodyAndDescriptionDoNotAffectTitleRank() {
        val result = titleResult("adguard").apply {
            items.add(topic("Zeta", body = "AdGuard exact body hit", desc = "AdGuard exact desc hit"))
            items.add(topic("Aduard"))
        }

        SearchTitleRanker.rank(result)

        assertEquals(listOf("Aduard", "Zeta"), result.items.map { it.title })
    }

    @Test
    fun postResultsAreNotRankedByTitleFuzzyPolicy() {
        val result = SearchResult().apply {
            settings = SearchSettings().apply {
                query = "adguard"
                result = SearchSettings.RESULT_POSTS.first
                source = SearchSettings.SOURCE_CONTENT.first
            }
            items.add(topic("Aduard"))
            items.add(topic("AdGuard"))
        }

        SearchTitleRanker.rank(result)

        assertEquals(listOf("Aduard", "AdGuard"), result.items.map { it.title })
    }

    @Test
    fun sourceAllTopicResultsKeepServerOrder() {
        val result = titleResult("adguard").apply {
            settings?.source = SearchSettings.SOURCE_ALL.first
            items.add(topic("Aduard"))
            items.add(topic("AdGuard"))
        }

        SearchTitleRanker.rank(result)

        assertEquals(listOf("Aduard", "AdGuard"), result.items.map { it.title })
    }

    private fun titleResult(query: String): SearchResult = SearchResult().apply {
        settings = SearchSettings().apply {
            this.query = query
            resourceType = SearchSettings.RESOURCE_FORUM.first
            result = SearchSettings.RESULT_TOPICS.first
            source = SearchSettings.SOURCE_TITLES.first
        }
    }

    private fun topic(title: String, body: String? = null, desc: String? = null): SearchItem =
            SearchItem().apply {
                this.title = title
                this.body = body
                this.desc = desc
            }
}
