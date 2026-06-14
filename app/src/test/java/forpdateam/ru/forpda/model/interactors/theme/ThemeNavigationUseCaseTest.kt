package forpdateam.ru.forpda.model.interactors.theme

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThemeNavigationUseCaseTest {

    private lateinit var navigationUseCase: ThemeNavigationUseCase
    private val linkHandler: ILinkHandler = mockk(relaxed = true)
    private val router: TabRouter = mockk(relaxed = true)
    private val userHolder: IUserHolder = mockk(relaxed = true)

    @Before
    fun setup() {
        navigationUseCase = ThemeNavigationUseCase(linkHandler, router, userHolder)
    }

    @Test
    fun `openProfile should call linkHandler handle with user URL`() {
        val userId = 123
        
        navigationUseCase.openProfile(userId)
        
        verify { linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router) }
    }

    @Test
    fun `openQms should call linkHandler handle with QMS URL`() {
        val userId = 456
        
        navigationUseCase.openQms(userId)
        
        verify { linkHandler.handle("https://4pda.to/forum/index.php?act=qms&amp;mid=$userId", router) }
    }

    @Test
    fun `openSearchInTopic should use user id without emoji nick parameter`() {
        val url = slot<String>()

        navigationUseCase.openSearchInTopic(10, 20, "⚡ elektrik ⚡", 598)

        verify { linkHandler.handle(capture(url), router) }
        val settings = SearchSettings.parseSettings(url.captured)
        assertEquals("", settings.nick)
        assertEquals(598, settings.userId)
        assertEquals(SearchSettings.RESULT_POSTS.first, settings.result)
        assertEquals(SearchSettings.SOURCE_CONTENT.first, settings.source)
        assertEquals(SearchSettings.SUB_FORUMS_FALSE, settings.subforums)
        assertEquals(listOf("10"), settings.forums)
        assertEquals(listOf("20"), settings.topics)
    }

    @Test
    fun `openSearchUserMessages should use user id without emoji nick parameter`() {
        val url = slot<String>()

        navigationUseCase.openSearchUserMessages("⚡ elektrik ⚡", 598)

        verify { linkHandler.handle(capture(url), router) }
        val settings = SearchSettings.parseSettings(url.captured)
        assertEquals("", settings.nick)
        assertEquals(598, settings.userId)
        assertEquals(SearchSettings.RESULT_POSTS.first, settings.result)
        assertEquals(SearchSettings.SOURCE_ALL.first, settings.source)
        assertEquals(SearchSettings.SUB_FORUMS_TRUE, settings.subforums)
    }
}
