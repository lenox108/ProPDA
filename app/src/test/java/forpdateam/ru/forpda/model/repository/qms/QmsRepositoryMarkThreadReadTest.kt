package forpdateam.ru.forpda.model.repository.qms

import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.cache.qms.QmsCacheRoom
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Reading a QMS thread marks it read on the server; these tests pin the LOCAL bookkeeping that used to
 * drift out of sync with it (phantom unread chip on the contacts screen, stale bottom-menu badge).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QmsRepositoryMarkThreadReadTest {

    private val cache = mockk<QmsCacheRoom>(relaxed = true)
    private val counters = mockk<CountersHolder>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository() = QmsRepository(
            mockk(relaxed = true),
            mockk(relaxed = true),
            cache,
            mockk(relaxed = true),
            counters,
    )

    private fun theme(id: Int, unread: Int) = QmsTheme().apply {
        this.id = id
        countNew = unread
    }

    private fun contact(id: Int, count: Int) = QmsContact().apply {
        this.id = id
        this.count = count
    }

    private fun themes(userId: Int, vararg items: QmsTheme) = QmsThemes().apply {
        this.userId = userId
        themes.addAll(items)
    }

    @Test
    fun `clears the thread unread count and the contact total`() = runTest {
        val dialog = themes(7, theme(id = 11, unread = 3))
        coEvery { cache.getThemes(7) } returns dialog
        coEvery { cache.getContacts() } returns listOf(contact(id = 7, count = 3))

        repository().markThreadRead(userId = 7, themeId = 11)

        assertEquals(0, dialog.themes.single().countNew)
        coVerify { cache.saveThemes(dialog) }
        val saved = slot<QmsContact>()
        coVerify { cache.updateContact(capture(saved)) }
        assertEquals(0, saved.captured.count)
    }

    @Test
    fun `keeps unread of the other threads of the same contact`() = runTest {
        val dialog = themes(7, theme(id = 11, unread = 3), theme(id = 12, unread = 2))
        coEvery { cache.getThemes(7) } returns dialog
        coEvery { cache.getContacts() } returns listOf(contact(id = 7, count = 5))

        repository().markThreadRead(userId = 7, themeId = 11)

        assertEquals(0, dialog.themes.first { it.id == 11 }.countNew)
        assertEquals(2, dialog.themes.first { it.id == 12 }.countNew)
        val saved = slot<QmsContact>()
        coVerify { cache.updateContact(capture(saved)) }
        assertEquals("only the read thread is subtracted", 2, saved.captured.count)
    }

    @Test
    fun `already read thread does not touch the cache`() = runTest {
        val dialog = themes(7, theme(id = 11, unread = 0))
        coEvery { cache.getThemes(7) } returns dialog

        repository().markThreadRead(userId = 7, themeId = 11)

        coVerify(exactly = 0) { cache.saveThemes(any()) }
        coVerify(exactly = 0) { cache.updateContact(any()) }
    }

    @Test
    fun `unknown thread does not touch the cache`() = runTest {
        coEvery { cache.getThemes(7) } returns themes(7, theme(id = 11, unread = 3))

        repository().markThreadRead(userId = 7, themeId = 999)

        coVerify(exactly = 0) { cache.saveThemes(any()) }
    }

    @Test
    fun `uncached dialog is survived without throwing`() = runTest {
        coEvery { cache.getThemes(7) } throws Exception("Themes not found")

        repository().markThreadRead(userId = 7, themeId = 11)

        coVerify(exactly = 0) { cache.saveThemes(any()) }
    }
}
