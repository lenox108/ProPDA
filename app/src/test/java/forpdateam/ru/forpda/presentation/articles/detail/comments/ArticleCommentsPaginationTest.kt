package forpdateam.ru.forpda.presentation.articles.detail.comments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleCommentsPaginationTest {

    @Test
    fun `withCommentPage adds cp for index php urls`() {
        val url = "https://4pda.to/index.php?p=457253"
        assertEquals(url, ArticleCommentsPagination.withCommentPage(url, 1))
        assertEquals(
                "https://4pda.to/index.php?p=457253&cp=2",
                ArticleCommentsPagination.withCommentPage(url, 2)
        )
        assertEquals(
                "https://4pda.to/index.php?p=457253&cp=3",
                ArticleCommentsPagination.withCommentPage("$url&cp=2", 3)
        )
    }

    @Test
    fun `withCommentPage uses comment-page slug for pretty urls`() {
        val slug = "https://4pda.to/2026/05/26/456862/article-slug"
        assertEquals(
                "$slug/comment-page-2/",
                ArticleCommentsPagination.withCommentPage(slug, 2)
        )
    }

    @Test
    fun `hasMore compares loaded count to badge total`() {
        assertTrue(ArticleCommentsPagination.hasMore(20, 353))
        assertFalse(ArticleCommentsPagination.hasMore(353, 353))
        assertFalse(ArticleCommentsPagination.hasMore(0, 0))
    }

    @Test
    fun `hasMore assumes more when badge missing but batch is full`() {
        assertTrue(ArticleCommentsPagination.hasMore(20, 0))
        assertFalse(ArticleCommentsPagination.hasMore(10, 0))
    }

    @Test
    fun `hasMore detects undercounted badge against full batch`() {
        assertTrue(ArticleCommentsPagination.hasMore(20, 14))
        assertTrue(ArticleCommentsPagination.hasMore(21, 14))
        assertFalse(ArticleCommentsPagination.hasMore(14, 14))
    }

    @Test
    fun `isLikelyPaginatedPartialBatch detects wp and mobile lazy batches`() {
        assertTrue(ArticleCommentsPagination.isLikelyPaginatedPartialBatch(20, 181))
        assertTrue(ArticleCommentsPagination.isLikelyPaginatedPartialBatch(10, 353))
        assertFalse(ArticleCommentsPagination.isLikelyPaginatedPartialBatch(27, 222))
        assertFalse(ArticleCommentsPagination.isLikelyPaginatedPartialBatch(181, 181))
    }

    @Test
    fun `shouldPreserveExpectedCount keeps badge during paginated session with nested replies`() {
        assertTrue(
                ArticleCommentsPagination.shouldPreserveExpectedCount(
                        loadedCount = 27,
                        totalExpected = 181,
                        paginatedSessionActive = true,
                )
        )
        assertFalse(
                ArticleCommentsPagination.shouldPreserveExpectedCount(
                        loadedCount = 27,
                        totalExpected = 222,
                        paginatedSessionActive = false,
                )
        )
        assertTrue(
                ArticleCommentsPagination.shouldPreserveExpectedCount(
                        loadedCount = 20,
                        totalExpected = 181,
                        paginatedSessionActive = false,
                )
        )
    }

    @Test
    fun `extractCommentPageFromUrl reads cp and slug paths`() {
        assertEquals(1, ArticleCommentsPagination.extractCommentPageFromUrl("https://4pda.to/index.php?p=1"))
        assertEquals(2, ArticleCommentsPagination.extractCommentPageFromUrl("https://4pda.to/index.php?p=1&cp=2"))
        assertEquals(
                3,
                ArticleCommentsPagination.extractCommentPageFromUrl("https://4pda.to/post/comment-page-3/")
        )
    }
}
