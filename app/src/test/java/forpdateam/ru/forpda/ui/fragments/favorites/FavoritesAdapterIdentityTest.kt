package forpdateam.ru.forpda.ui.fragments.favorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FavoritesAdapterIdentityTest {

    @Test
    fun `pinned topics with same title keep different identity`() {
        val propda = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = true)
        val otherDisplayedAsPropda = favoriteTopic(favId = 11, topicId = 101, title = "ProPDA", isPin = true)

        assertNotEquals(
                FavoritesAdapter.itemIdentity(propda),
                FavoritesAdapter.itemIdentity(otherDisplayedAsPropda)
        )
    }

    @Test
    fun `title change keeps identity but changes content`() {
        val oldItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = true)
        val renamedItem = favoriteTopic(favId = 10, topicId = 100, title = "Another topic", isPin = true)

        assertEquals(FavoritesAdapter.itemIdentity(oldItem), FavoritesAdapter.itemIdentity(renamedItem))
        assertNotEquals(FavoritesAdapter.itemContentKey(oldItem), FavoritesAdapter.itemContentKey(renamedItem))
    }

    @Test
    fun `display date override changes content`() {
        val oldItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = true)
        val updatedItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = true).apply {
            displayDateOverride = "Сегодня, 12:00"
        }

        assertEquals(FavoritesAdapter.itemIdentity(oldItem), FavoritesAdapter.itemIdentity(updatedItem))
        assertNotEquals(FavoritesAdapter.itemContentKey(oldItem), FavoritesAdapter.itemContentKey(updatedItem))
    }

    @Test
    fun `unread and latest post changes update content key`() {
        val oldItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = false)
        val updatedItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = false).apply {
            lastUserNick = "fresh user"
            date = "16.05.2026, 10:05"
            isNew = true
            unreadPostCount = 3
        }

        assertEquals(FavoritesAdapter.itemIdentity(oldItem), FavoritesAdapter.itemIdentity(updatedItem))
        assertNotEquals(FavoritesAdapter.itemContentKey(oldItem), FavoritesAdapter.itemContentKey(updatedItem))
    }

    @Test
    fun `isUnreadForDisplay stays true when isNew is false but readState is unread`() {
        val item = favoriteTopic(favId = 10, topicId = 740069, title = "OnePlus 15", isPin = false).apply {
            isNew = false
            readState = FavoriteReadState.UNREAD
            unreadPostCount = 1
        }

        assertTrue(item.isUnreadForDisplay())
    }

    @Test
    fun `readState unread with stale isNew updates content key`() {
        val oldItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = false)
        val updatedItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = false).apply {
            readState = FavoriteReadState.UNREAD
            unreadPostCount = 1
        }

        assertEquals(FavoritesAdapter.itemIdentity(oldItem), FavoritesAdapter.itemIdentity(updatedItem))
        assertNotEquals(FavoritesAdapter.itemContentKey(oldItem), FavoritesAdapter.itemContentKey(updatedItem))
        assertTrue(updatedItem.isUnreadForDisplay())
    }

    @Test
    fun `page count changes content`() {
        val oldItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = false).apply {
            pages = 2
        }
        val updatedItem = favoriteTopic(favId = 10, topicId = 100, title = "ProPDA", isPin = false).apply {
            pages = 3
        }

        assertEquals(FavoritesAdapter.itemIdentity(oldItem), FavoritesAdapter.itemIdentity(updatedItem))
        assertNotEquals(FavoritesAdapter.itemContentKey(oldItem), FavoritesAdapter.itemContentKey(updatedItem))
    }

    @Test
    fun `section headers items and footers have unique stable ids`() {
        val adapter = FavoritesAdapter().apply {
            addSection("Pinned", listOf(favoriteTopic(favId = 10, topicId = 100, title = "Pinned", isPin = true)))
            addSection("Topics", listOf(favoriteTopic(favId = 11, topicId = 101, title = "Topic", isPin = false)))
        }

        val ids = (0 until adapter.itemCount).map(adapter::getItemId)

        assertFalse(ids.contains(-1L))
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `last post sorting keeps unread topics in one chronological section`() {
        val adapter = FavoritesAdapter().apply {
            setSorting(Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC))
            setUnreadTop(true)
        }
        val readToday = favoriteTopic(favId = 10, topicId = 100, title = "Read today", isPin = false).apply {
            isNew = false
            date = "Сегодня, 12:13"
        }
        val unreadYesterday = favoriteTopic(favId = 11, topicId = 101, title = "Unread yesterday", isPin = false).apply {
            isNew = true
            date = "Вчера, 21:48"
        }

        adapter.bindItems(listOf(unreadYesterday, readToday))

        assertEquals(1, adapter.sections.size)
        assertEquals(listOf(101, 100), adapter.sections.single().second.map { it.topicId })
    }

    @Test
    fun `unread top with title sorting splits unread section before read section`() {
        val adapter = FavoritesAdapter().apply {
            setSorting(Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.ASC))
            setUnreadTop(true)
        }
        val readToday = favoriteTopic(favId = 10, topicId = 100, title = "Read today 11 31", isPin = false).apply {
            isNew = false
            date = "Сегодня, 11:31"
        }
        val unreadTodayNewer = favoriteTopic(favId = 11, topicId = 101, title = "Unread today 11 04", isPin = false).apply {
            isNew = true
            date = "Сегодня, 11:04"
        }
        val unreadTodayOlder = favoriteTopic(favId = 12, topicId = 102, title = "Unread today 10 36", isPin = false).apply {
            isNew = true
            date = "Сегодня, 10:36"
        }

        adapter.bindItems(listOf(readToday, unreadTodayNewer, unreadTodayOlder))

        assertEquals(2, adapter.sections.size)
        assertEquals(listOf(101, 102), adapter.sections[0].second.map { it.topicId })
        assertEquals(listOf(100), adapter.sections[1].second.map { it.topicId })
    }

    @Test
    fun `last post sorting keeps read yesterday below unread today`() {
        val adapter = FavoritesAdapter().apply {
            setSorting(Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC))
            setUnreadTop(true)
        }
        val readYesterday = favoriteTopic(favId = 10, topicId = 100, title = "Read yesterday", isPin = false).apply {
            isNew = false
            date = "Вчера, 21:48"
        }
        val unreadToday = favoriteTopic(favId = 11, topicId = 101, title = "Unread today", isPin = false).apply {
            isNew = true
            date = "Сегодня, 10:36"
        }

        adapter.bindItems(listOf(unreadToday, readYesterday))

        assertEquals(1, adapter.sections.size)
        assertEquals(listOf(101, 100), adapter.sections.single().second.map { it.topicId })
    }

    @Test
    fun `title sorting splits unread topics into separate section`() {
        val adapter = FavoritesAdapter().apply {
            setSorting(Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.ASC))
            setUnreadTop(true)
        }
        val readYesterday = favoriteTopic(favId = 10, topicId = 100, title = "Read yesterday", isPin = false).apply {
            isNew = false
            date = "Вчера, 21:48"
        }
        val unreadToday = favoriteTopic(favId = 11, topicId = 101, title = "Unread today", isPin = false).apply {
            isNew = true
            date = "Сегодня, 10:36"
        }

        adapter.bindItems(listOf(unreadToday, readYesterday))

        assertEquals(2, adapter.sections.size)
        assertEquals(listOf(101), adapter.sections[0].second.map { it.topicId })
        assertEquals(listOf(100), adapter.sections[1].second.map { it.topicId })
    }

    @Test
    fun `latest post rendering keeps screenshot unread rows above read rows`() {
        val adapter = FavoritesAdapter().apply {
            setSorting(Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC))
            setUnreadTop(true)
        }
        val items = listOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Обсуждение модулей для Magisk", isPin = false).apply {
                    isNew = true
                    date = "Сегодня, 12:46"
                },
                favoriteTopic(favId = 7, topicId = 107, title = "WPS Office + PDF", isPin = false).apply {
                    isNew = true
                    date = "Сегодня, 10:36"
                },
                favoriteTopic(favId = 8, topicId = 108, title = "Обход блокировок WhatsApp и Telegram", isPin = false).apply {
                    isNew = true
                    date = "Сегодня, 09:33"
                },
                favoriteTopic(favId = 2, topicId = 102, title = "Apple MacBook", isPin = false).apply {
                    isNew = false
                    date = "Сегодня, 12:13"
                },
                favoriteTopic(favId = 3, topicId = 103, title = "Энергопотребление", isPin = false).apply {
                    isNew = false
                    date = "Сегодня, 11:31"
                },
                favoriteTopic(favId = 4, topicId = 104, title = "Adguard", isPin = false).apply {
                    isNew = false
                    date = "Сегодня, 11:25"
                },
                favoriteTopic(favId = 5, topicId = 105, title = "Kinopub", isPin = false).apply {
                    isNew = false
                    date = "Сегодня, 11:04"
                },
                favoriteTopic(favId = 6, topicId = 106, title = "KernelSU", isPin = false).apply {
                    isNew = false
                    date = "Сегодня, 10:55"
                }
        )

        adapter.bindItems(items)

        assertEquals(1, adapter.sections.size)
        assertEquals(listOf(101, 107, 108, 102, 103, 104, 105, 106), adapter.sections.single().second.map { it.topicId })
    }

    private fun favoriteTopic(favId: Int, topicId: Int, title: String, isPin: Boolean): FavItem {
        return FavItem().apply {
            this.favId = favId
            this.topicId = topicId
            topicTitle = title
            this.isPin = isPin
            isForum = false
            lastUserNick = "user"
            date = "16.05.2026, 10:00"
        }
    }
}
