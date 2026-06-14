package forpdateam.ru.forpda.model.interactors.theme

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ThemeEditorUseCaseTest {

    private lateinit var editorUseCase: ThemeEditorUseCase
    private val editorRepository: PostEditorRepository = mockk(relaxed = true)
    private val errorHandler: IErrorHandler = mockk(relaxed = true)

    @Before
    fun setup() {
        editorUseCase = ThemeEditorUseCase(editorRepository, errorHandler)
    }

    @Test
    fun `sendPost should call repository sendPost`() = runTest {
        val form = mockk<EditPostForm>()
        
        coEvery { editorRepository.sendPost(form, any()) } returns mockk()
        
        editorUseCase.sendPost(form)
        
        coVerify { editorRepository.sendPost(form, any()) }
    }

    @Test
    fun `uploadFiles should call repository uploadFiles`() = runTest {
        val id = 123
        val files = listOf<RequestFile>()
        val pending = listOf<AttachmentItem>()
        
        coEvery { editorRepository.uploadFiles(id, files, pending) } returns listOf()
        
        editorUseCase.uploadFiles(id, files, pending)
        
        coVerify { editorRepository.uploadFiles(id, files, pending) }
    }

    @Test
    fun `bumpEditPrefetchGeneration should call repository bumpEditPrefetchGeneration`() {
        editorUseCase.bumpEditPrefetchGeneration()
        
        verify { editorRepository.bumpEditPrefetchGeneration() }
    }
}
