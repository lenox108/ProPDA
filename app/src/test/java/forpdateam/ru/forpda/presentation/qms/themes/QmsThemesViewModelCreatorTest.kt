package forpdateam.ru.forpda.presentation.qms.themes

import androidx.lifecycle.SavedStateHandle
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QmsThemesViewModelCreatorTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var qmsInteractor: QmsInteractor
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var countersHolder: CountersHolder
    private lateinit var errorHandler: IErrorHandler

    private val themesFlow = MutableStateFlow(QmsThemes())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        qmsInteractor = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        countersHolder = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        every { qmsInteractor.observeThemes(any()) } returns themesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): QmsThemesViewModel = QmsThemesViewModel(
            SavedStateHandle(mapOf("USER_ID_ARG" to 42, "USER_AVATAR_ARG" to "https://avatar")),
            qmsInteractor,
            countersHolder,
            router,
            linkHandler,
            errorHandler
    )

    private fun themesWithOne(): QmsThemes = QmsThemes().apply {
        userId = 42
        nick = "claude.test"
        themes.add(QmsTheme().apply {
            id = 777
            name = "we"
        })
    }

    @Test
    fun `fab opens new theme creator instead of the first existing theme`() = runTest {
        coEvery { qmsInteractor.getThemesList(42) } returns themesWithOne()

        val vm = createViewModel()
        vm.loadThemes()
        advanceUntilIdle()

        vm.openChatCreator()

        val screen = slot<Screen.QmsChat>()
        verify { router.navigateTo(capture(screen)) }
        assertEquals(42, screen.captured.userId)
        assertEquals("claude.test", screen.captured.userNick)
        assertEquals("https://avatar", screen.captured.avatarUrl)
        // themeId остаётся незаданным (-1) — это и переводит чат в режим создания темы
        assertEquals(-1, screen.captured.themeId)
        assertEquals(null, screen.captured.themeTitle)
    }
}
