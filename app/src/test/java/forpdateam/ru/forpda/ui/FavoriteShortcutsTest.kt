package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пинит чистый отбор избранного под динамические App Shortcuts: фильтры
 * (форумы/скрытые/битые id/пустой заголовок), приоритет закреплённых и лимит MAX.
 */
class FavoriteShortcutsTest {

    private fun fav(
            topicId: Int,
            title: String? = "T$topicId",
            forum: Boolean = false,
            hidden: Boolean = false,
            pin: Boolean = false,
    ) = FavItem().apply {
        this.topicId = topicId
        this.topicTitle = title
        this.isForum = forum
        this.isHidden = hidden
        this.isPin = pin
    }

    @Test
    fun filtersForumsHiddenAndInvalid() {
        val result = FavoriteShortcuts.selectTopics(listOf(
                fav(1),
                fav(2, forum = true),          // форум — выкидываем
                fav(3, hidden = true),         // скрытый — выкидываем
                fav(0),                        // нет topicId — выкидываем
                fav(4, title = "  "),          // пустой заголовок — выкидываем
        ))
        assertEquals(listOf(1), result.map { it.topicId })
    }

    @Test
    fun cappedAtMax() {
        val result = FavoriteShortcuts.selectTopics((1..10).map { fav(it) })
        assertEquals(FavoriteShortcuts.MAX, result.size)
        assertEquals(listOf(1, 2, 3), result.map { it.topicId })
    }

    @Test
    fun pinnedFirstThenCacheOrder() {
        val result = FavoriteShortcuts.selectTopics(listOf(
                fav(1),
                fav(2, pin = true),
                fav(3),
        ))
        // Закреплённый (2) — вперёд, остальные в исходном порядке (1,3).
        assertEquals(listOf(2, 1, 3), result.map { it.topicId })
        assertTrue(result.first().isPin)
    }

    @Test
    fun emptyInEmptyOut() {
        assertTrue(FavoriteShortcuts.selectTopics(emptyList()).isEmpty())
    }
}
