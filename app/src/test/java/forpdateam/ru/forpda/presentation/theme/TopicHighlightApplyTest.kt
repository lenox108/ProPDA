package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.repository.theme.ThemeReadPositionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 6 / T7-T8: `TopicHighlightApply` is the single bridge between
 * [HighlightResolver] and the [ThemePage] data class. It must:
 *  - stamp the resolved target onto the page,
 *  - bump the render generation id,
 *  - keep the same generation on a re-apply with the same page (so a refresh
 *    of the *same* page does not change the generation; the JS guard then
 *    accepts callbacks for the same render and ignores older ones).
 */
class TopicHighlightApplyTest {

    private fun makePage(vararg postIds: Long): ThemePage = ThemePage().apply {
        id = 1
        pagination = Pagination().apply {
            current = 1
            all = 1
            perPage = postIds.size
        }
        posts.addAll(postIds.map { id ->
            ThemePost().apply {
                this.id = id.toInt()
                this.number = id.toInt()
            }
        })
    }

    @Test
    fun firstUnread_isAppliedToPage() {
        val page = makePage(100L, 200L, 300L)
        val repo = ThemeReadPositionRepository()
        val res = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = 200L,
                unreadPage = 1,
                unreadUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
        )
        assertNotNull(page.highlightTarget)
        assertEquals(HighlightType.FirstUnread, page.highlightTarget!!.type)
        assertEquals(200L, page.highlightTarget!!.postId)
        assertEquals("first_unread", res.reason)
        assertTrue(page.renderGenerationId > 0)
    }

    @Test
    fun lastRead_isAppliedToPage_whenReadPositionMatches() {
        val page = makePage(100L, 200L, 300L, 400L)
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 300L, lastViewedPage = 1))
        val res = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
        )
        assertNotNull(page.highlightTarget)
        assertEquals(HighlightType.LastRead, page.highlightTarget!!.type)
        assertEquals(300L, page.highlightTarget!!.postId)
        assertEquals("last_read", res.reason)
    }

    @Test
    fun noInputs_fallsBackToLastPostOnPage() {
        // An already-read topic with no unread / read-position / explicit input,
        // opened on a non-empty page, intentionally highlights the LAST post on
        // the page (the user read to the bottom). This mirrors
        // `HighlightResolverTest.noInputs_fallsBackToLastPostOnPage`.
        val page = makePage(100L, 200L, 300L)
        val repo = ThemeReadPositionRepository()
        val res = TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
        )
        assertNotNull(page.highlightTarget)
        assertEquals(HighlightType.LastRead, page.highlightTarget!!.type)
        assertEquals(300L, page.highlightTarget!!.postId)
        // The apply method still bumps the generation — the renderer needs
        // a fresh id so a stale callback cannot re-apply a previous highlight.
        assertTrue(page.renderGenerationId > 0)
        assertEquals("last_post_on_page_fallback", res.reason)
    }

    @Test
    fun reapply_preservesExistingGenerationId() {
        val page = makePage(100L, 200L, 300L)
        val repo = ThemeReadPositionRepository()
        TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = 200L,
        )
        val gen1 = page.renderGenerationId
        TopicHighlightApply.applyToPage(
                page = page,
                readPositionRepository = repo,
                firstUnreadPostId = 200L,
        )
        assertEquals(gen1, page.renderGenerationId)
    }
}
