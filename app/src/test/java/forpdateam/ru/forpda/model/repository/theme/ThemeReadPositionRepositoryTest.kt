package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.presentation.theme.ReadPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Step 5 + Step 9: read-position repository contract.
 *
 * The repository is the *only* source of `lastViewedPostId` for the highlight
 * resolver's `LAST_READ` branch. Tests cover:
 *  - round-trip save/get,
 *  - non-positive ids are rejected,
 *  - save does not clobber an unrelated topic,
 *  - clear() removes only the targeted topic.
 */
class ThemeReadPositionRepositoryTest {

    @Test
    fun save_thenGet_returnsSavedPosition() {
        val repo = ThemeReadPositionRepository()
        val saved = ReadPosition(topicId = 1L, lastViewedPostId = 100L, lastViewedPage = 2)
        repo.save(saved)
        val got = repo.get(1L)
        assertEquals(saved, got)
    }

    @Test
    fun get_missingTopic_returnsNull() {
        val repo = ThemeReadPositionRepository()
        assertNull(repo.get(999L))
    }

    @Test
    fun save_invalidTopicId_isIgnored() {
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 0L, lastViewedPostId = 100L, lastViewedPage = 1))
        repo.save(ReadPosition(topicId = -1L, lastViewedPostId = 100L, lastViewedPage = 1))
        assertNull(repo.get(0L))
        assertNull(repo.get(-1L))
    }

    @Test
    fun save_invalidPostId_isIgnored() {
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 0L, lastViewedPage = 1))
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = -10L, lastViewedPage = 1))
        assertNull(repo.get(1L))
    }

    @Test
    fun save_invalidPage_isIgnored() {
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 100L, lastViewedPage = 0))
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 100L, lastViewedPage = -1))
        assertNull(repo.get(1L))
    }

    @Test
    fun save_overwritesPreviousPosition() {
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 100L, lastViewedPage = 1))
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 200L, lastViewedPage = 1))
        assertEquals(200L, repo.get(1L)?.lastViewedPostId)
    }

    @Test
    fun clear_removesTargetedTopicOnly() {
        val repo = ThemeReadPositionRepository()
        repo.save(ReadPosition(topicId = 1L, lastViewedPostId = 100L, lastViewedPage = 1))
        repo.save(ReadPosition(topicId = 2L, lastViewedPostId = 200L, lastViewedPage = 1))
        repo.clear(1L)
        assertNull(repo.get(1L))
        assertEquals(200L, repo.get(2L)?.lastViewedPostId)
    }
}
