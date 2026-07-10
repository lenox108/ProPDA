package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicRefreshAnchorPolicyTest {

    @Test
    fun `no new posts - no override, caller keeps the bottom anchor`() {
        assertNull(TopicRefreshAnchorPolicy.firstUnseenPostId(listOf(10, 20, 30), seenUpToPostId = 30))
    }

    @Test
    fun `refresh brought new posts - anchor on the FIRST of them, not the last`() {
        val fresh = listOf(10, 20, 30, 40, 50, 60)
        assertEquals(40, TopicRefreshAnchorPolicy.firstUnseenPostId(fresh, seenUpToPostId = 30))
    }

    @Test
    fun `single new post - anchor on it`() {
        assertEquals(40, TopicRefreshAnchorPolicy.firstUnseenPostId(listOf(10, 20, 30, 40), seenUpToPostId = 30))
    }

    @Test
    fun `unknown boundary - no override`() {
        assertNull(TopicRefreshAnchorPolicy.firstUnseenPostId(listOf(10, 20), seenUpToPostId = 0))
    }

    @Test
    fun `empty page - no override`() {
        assertNull(TopicRefreshAnchorPolicy.firstUnseenPostId(emptyList(), seenUpToPostId = 30))
    }

    @Test
    fun `page reloaded without the seen post (window moved) - first newer post still wins`() {
        // Перезагруженная страница может начинаться уже ПОСЛЕ виденного поста.
        assertEquals(41, TopicRefreshAnchorPolicy.firstUnseenPostId(listOf(41, 42, 43), seenUpToPostId = 30))
    }
}
