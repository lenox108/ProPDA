package forpdateam.ru.forpda.model.interactors.theme

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.common.ClipboardHelper
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ThemeInteractionUseCaseTest {

    private lateinit var interactionUseCase: ThemeInteractionUseCase
    private val reputationRepository: ReputationRepository = mockk(relaxed = true)
    private val favoritesRepository: FavoritesRepository = mockk(relaxed = true)
    private val themeRepository: ThemeRepository = mockk(relaxed = true)
    private val errorHandler: IErrorHandler = mockk(relaxed = true)
    private val clipboardHelper: ClipboardHelper = mockk(relaxed = true)
    private val router: TabRouter = mockk(relaxed = true)

    @Before
    fun setup() {
        interactionUseCase = ThemeInteractionUseCase(
            reputationRepository,
            favoritesRepository,
            themeRepository,
            errorHandler,
            clipboardHelper,
            router
        )
    }

    @Test
    fun `addTopicToFavorite should call repository editFavorites`() = runTest {
        val topicId = 123
        val subType = "topic"
        
        coEvery { favoritesRepository.editFavorites(any(), any(), any(), any()) } returns true
        
        interactionUseCase.addTopicToFavorite(topicId, subType)
        
        coVerify { favoritesRepository.editFavorites(any(), any(), any(), any()) }
    }

    @Test
    fun `copyLink should call clipboardHelper copyToClipboard`() {
        val topicId = 123
        
        interactionUseCase.copyLink(topicId)
        
        verify { clipboardHelper.copyToClipboard(any()) }
    }

    @Test
    fun `copyText should call clipboardHelper copyToClipboard`() {
        val text = "Test text"
        
        interactionUseCase.copyText(text)
        
        verify { clipboardHelper.copyToClipboard(text) }
    }
}
