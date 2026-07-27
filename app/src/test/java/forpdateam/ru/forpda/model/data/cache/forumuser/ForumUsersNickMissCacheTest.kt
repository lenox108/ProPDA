package forpdateam.ru.forpda.model.data.cache.forumuser

import forpdateam.ru.forpda.entity.db.ForumUserDao
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Негативный кэш промахов по нику: именно он гасит залп `qms-xhr` при каждой загрузке ленты.
 */
class ForumUsersNickMissCacheTest {

    private class CountingUserSource(private val result: List<ForumUser> = emptyList()) : UserSource {
        var calls = 0
            private set

        override fun getUsers(nick: String, background: Boolean): List<ForumUser> {
            calls++
            return result
        }
    }

    private fun dao(): ForumUserDao = mockk<ForumUserDao>(relaxed = true).also {
        coEvery { it.getUserByNick(any()) } returns null
        coEvery { it.getUserById(any()) } returns null
    }

    @Test
    fun `second lookup of a missing nick does not hit the network`() = runTest {
        val source = CountingUserSource()
        val cache = ForumUsersCacheRoom(dao(), source)

        assertNull(cache.getUserByNick("ghost"))
        assertNull(cache.getUserByNick("ghost"))

        assertEquals(1, source.calls)
    }

    @Test
    fun `explicit search ignores the miss cache`() = runTest {
        val source = CountingUserSource()
        val cache = ForumUsersCacheRoom(dao(), source)

        cache.getUserByNick("ghost")
        cache.getUserByNick("ghost", useNegativeCache = false)

        assertEquals(2, source.calls)
    }

    @Test
    fun `different nicks are cached independently`() = runTest {
        val source = CountingUserSource()
        val cache = ForumUsersCacheRoom(dao(), source)

        cache.getUserByNick("ghost")
        cache.getUserByNick("phantom")
        cache.getUserByNick("ghost")

        assertEquals(2, source.calls)
    }

    @Test
    fun `found nick is returned and not remembered as a miss`() = runTest {
        val found = ForumUser().apply {
            id = 7
            nick = "alice"
            avatar = "https://4pda.to/avatar.png"
        }
        val source = CountingUserSource(listOf(found))
        val cache = ForumUsersCacheRoom(dao(), source)

        assertNotNull(cache.getUserByNick("alice"))
        // Room в тесте всегда пуст, поэтому второй вызов снова идёт в источник — но не блокируется.
        assertNotNull(cache.getUserByNick("alice"))

        assertEquals(2, source.calls)
    }

    @Test
    fun `clearNickMisses lets a nick be retried`() = runTest {
        val source = CountingUserSource()
        val cache = ForumUsersCacheRoom(dao(), source)

        cache.getUserByNick("ghost")
        cache.clearNickMisses()
        cache.getUserByNick("ghost")

        assertEquals(2, source.calls)
    }
}
