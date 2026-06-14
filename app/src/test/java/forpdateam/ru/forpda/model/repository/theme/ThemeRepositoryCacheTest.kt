package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.cache.history.HistoryCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRepositoryCacheTest {

    @Test
    fun `memory cache hit does not rewrite forum users`() = runTest {
        val url = "https://4pda.to/forum/index.php?showtopic=42&st=0"
        val cached = ThemePage().apply {
            id = 42
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 1
                userId = 7
                nick = "cached"
            })
        }
        val pageMemoryCache = ThemePageMemoryCache()
        pageMemoryCache.put(ThemePageMemoryCache.Key(42, 0, false, false), cached)
        val themeApi = mockk<ThemeApi>(relaxed = true)
        val forumUsersCache = mockk<ForumUsersCacheRoom>(relaxed = true)
        val historyCache = mockk<HistoryCacheRoom>(relaxed = true)
        val repository = ThemeRepository(themeApi, historyCache, forumUsersCache, pageMemoryCache)

        val loaded = repository.getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false)

        assertEquals(42, loaded.id)
        assertEquals("cached", loaded.posts.first().nick)
        coVerify(exactly = 0) { forumUsersCache.saveUsers(any()) }
        coVerify(exactly = 0) { themeApi.getTheme(any(), any(), any()) }
    }

    @Test
    fun `network load still persists forum users best effort`() = runTest {
        val url = "https://4pda.to/forum/index.php?showtopic=99&st=0"
        val page = ThemePage().apply {
            id = 99
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 2
                userId = 11
                nick = "fresh"
            })
        }
        val themeApi = mockk<ThemeApi>()
        every { themeApi.getTheme(url, false, false) } returns page
        val forumUsersCache = mockk<ForumUsersCacheRoom>(relaxed = true)
        coEvery { forumUsersCache.saveUsers(any()) } returns Unit
        val historyCache = mockk<HistoryCacheRoom>(relaxed = true)
        val repository = ThemeRepository(themeApi, historyCache, forumUsersCache)

        repository.getTheme(url, _withHtml = true, hatOpen = false, pollOpen = false)

        coVerify(exactly = 1) { forumUsersCache.saveUsers(any()) }
    }
}
