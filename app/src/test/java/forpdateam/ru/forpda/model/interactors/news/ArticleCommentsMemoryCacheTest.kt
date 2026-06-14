package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleCommentsMemoryCacheTest {

    private fun treeWithId(id: Int): Comment =
            Comment().apply { children.add(Comment().apply { this.id = id }) }

    @Test
    fun `put and get returns valid entry`() {
        val cache = ArticleCommentsMemoryCache()
        val tree = treeWithId(42)
        assertTrue(cache.put(articleId = 10, tree = tree, nowMs = 1_000L))
        val lookup = cache.get(10, nowMs = 2_000L)
        assertTrue(lookup.hit)
        assertTrue(lookup.valid)
        assertNotNull(lookup.entry)
        assertEquals(42, lookup.entry!!.tree.children.first().id)
    }

    @Test
    fun `expired entry is rejected`() {
        val cache = ArticleCommentsMemoryCache(maxAgeMs = 1_000L)
        cache.put(5, treeWithId(1), nowMs = 0L)
        val lookup = cache.get(5, nowMs = 5_000L)
        assertTrue(lookup.hit)
        assertFalse(lookup.valid)
        assertNull(lookup.entry)
    }

    @Test
    fun `empty tree is not stored`() {
        val cache = ArticleCommentsMemoryCache()
        assertFalse(cache.put(3, Comment(), nowMs = 0L))
        assertFalse(cache.get(3).valid)
    }
}
