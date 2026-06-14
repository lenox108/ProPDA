package forpdateam.ru.forpda.model.data.remote.api.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsCategoryConstantsTest {

    @Test
    fun selectableSiteMenuCategories_haveUrlMappings() {
        val visibleCategoryIds = listOf(
                Constants.NEWS_CATEGORY_ALL,
                Constants.NEWS_CATEGORY_TECH,
                Constants.NEWS_SUBCATEGORY_TECH_SMARTPHONES,
                Constants.NEWS_SUBCATEGORY_TECH_LAPTOPS,
                Constants.NEWS_SUBCATEGORY_TECH_AUDIO,
                Constants.NEWS_SUBCATEGORY_TECH_MONITORS,
                Constants.NEWS_SUBCATEGORY_TECH_APPLIANCES,
                Constants.NEWS_SUBCATEGORY_TECH_PC,
                Constants.NEWS_CATEGORY_REVIEWS,
                Constants.NEWS_SUBCATEGORY_SMARTPHONES_REVIEWS,
                Constants.NEWS_SUBCATEGORY_TABLETS_REVIEWS,
                Constants.NEWS_SUBCATEGORY_SMART_WATCH_REVIEWS,
                Constants.NEWS_SUBCATEGORY_ACCESSORIES_REVIEWS,
                Constants.NEWS_SUBCATEGORY_NOTEBOOKS_REVIEWS,
                Constants.NEWS_SUBCATEGORY_ACOUSTICS_REVIEWS,
                Constants.NEWS_CATEGORY_GAMES
        )

        visibleCategoryIds.forEach { categoryId ->
            assertTrue(categoryId, Constants.isSelectableNewsCategory(categoryId))
            assertTrue(categoryId, Constants.getNewsCategoryUrl(categoryId).startsWith("https://4pda.to/"))
        }
    }

    @Test
    fun legacyRootCategory_normalizesToAllNews() {
        assertEquals(Constants.NEWS_CATEGORY_ALL, Constants.normalizeNewsCategory(Constants.NEWS_CATEGORY_ROOT))
        assertEquals(Constants.NEWS_URL_ALL, Constants.getNewsCategoryUrl(Constants.normalizeNewsCategory(Constants.NEWS_CATEGORY_ROOT)))
    }

    @Test
    fun smartWatchReviews_usesLiveSiteUrl() {
        assertEquals("https://4pda.to/reviews/smart-watches/", Constants.NEWS_URL_SMART_WATCH_REVIEWS)
    }
}
