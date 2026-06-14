package forpdateam.ru.forpda.presentation.notes

import android.content.SharedPreferences
import app.cash.turbine.test
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var notesRepository: NotesRepository
    private lateinit var closeableInfoHolder: CloseableInfoHolder
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var errorHandler: IErrorHandler
    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var preferences: SharedPreferences

    private val notesFlow = MutableStateFlow<List<NoteItem>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<forpdateam.ru.forpda.entity.app.notes.NoteFolder>>(emptyList())
    private val infoFlow = MutableStateFlow<List<CloseableInfo>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notesRepository = mockk(relaxed = true)
        closeableInfoHolder = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        clipboardHelper = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        every { notesRepository.observeItems() } returns notesFlow
        every { notesRepository.observeFolders() } returns foldersFlow
        every { closeableInfoHolder.observe() } returns infoFlow
        every { preferences.getString(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NotesViewModel {
        return NotesViewModel(notesRepository, closeableInfoHolder, router, linkHandler, errorHandler, clipboardHelper, preferences)
    }

    private fun makeNote(id: Long, title: String = "Note $id", link: String = "https://4pda.to/$id"): NoteItem {
        return NoteItem().apply {
            this.id = id
            this.title = title
            this.link = link
            this.content = "Content $id"
        }
    }

    @Test
    fun `initial state loads notes from repository`() = runTest {
        val notes = listOf(makeNote(1), makeNote(2))
        coEvery { notesRepository.loadNotes() } coAnswers {
            notesFlow.value = notes
            notes
        }

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.refreshing)
        assertEquals(2, state.items.size)
    }

    @Test
    fun `loadNotes updates state with items`() = runTest {
        val notes = listOf(makeNote(10))
        coEvery { notesRepository.loadNotes() } coAnswers {
            notesFlow.value = notes
            notes
        }

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(10L, vm.uiState.value.items[0].id)
    }

    @Test
    fun `loadNotes on error calls errorHandler`() = runTest {
        val error = RuntimeException("DB error")
        coEvery { notesRepository.loadNotes() } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        verify { errorHandler.handle(error, null) }
        assertFalse(vm.uiState.value.refreshing)
    }

    @Test
    fun `deleteNote calls repository`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()
        coEvery { notesRepository.deleteNote(5L) } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteNote(5L)
        advanceUntilIdle()

        coVerify { notesRepository.deleteNote(5L) }
    }

    @Test
    fun `deleteNote on error calls errorHandler`() = runTest {
        val error = RuntimeException("Delete failed")
        coEvery { notesRepository.loadNotes() } returns emptyList()
        coEvery { notesRepository.deleteNote(any()) } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteNote(1L)
        advanceUntilIdle()

        verify { errorHandler.handle(error, null) }
    }

    @Test
    fun `observeItems updates state when flow emits`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())

        notesFlow.value = listOf(makeNote(42))
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(42L, vm.uiState.value.items[0].id)
    }

    @Test
    fun `exportNotes emits ExportDone effect`() = runTest {
        val outputStream = ByteArrayOutputStream()
        coEvery { notesRepository.loadNotes() } returns emptyList()
        coEvery { notesRepository.exportNotes(outputStream) } returns outputStream.writer()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.exportNotes(outputStream)
            val effect = awaitItem()
            assertTrue(effect is NotesViewModel.UiEffect.ExportDone)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `exportNotes on error calls errorHandler`() = runTest {
        val outputStream = ByteArrayOutputStream()
        val error = RuntimeException("Export failed")
        coEvery { notesRepository.loadNotes() } returns emptyList()
        coEvery { notesRepository.exportNotes(outputStream) } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.exportNotes(outputStream)
        advanceUntilIdle()

        verify { errorHandler.handle(error, null) }
    }

    @Test
    fun `importNotes emits ImportDone effect`() = runTest {
        val file = mockk<RequestFile>(relaxed = true)
        coEvery { notesRepository.loadNotes() } returns emptyList()
        coEvery { notesRepository.importNotes(file) } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.importNotes(file)
            val effect = awaitItem()
            assertTrue(effect is NotesViewModel.UiEffect.ImportDone)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `addNote emits ShowAddPopup effect`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.addNote()
            val effect = awaitItem()
            assertTrue(effect is NotesViewModel.UiEffect.ShowAddPopup)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `editNote emits ShowEditPopup effect with item`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()
        val note = makeNote(7)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.editNote(note)
            val effect = awaitItem()
            assertTrue(effect is NotesViewModel.UiEffect.ShowEditPopup)
            assertEquals(7L, (effect as NotesViewModel.UiEffect.ShowEditPopup).item.id)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onItemClick delegates to linkHandler`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val note = makeNote(1, link = "https://4pda.to/topic/123")
        vm.onItemClick(note)

        verify {
            linkHandler.handle(
                    "https://4pda.to/topic/123",
                    router,
                    mapOf(
                            Screen.Theme.ARG_TOPIC_OPEN_SOURCE to "bookmark",
                            Screen.Theme.ARG_TOPIC_OPEN_INTENT to TopicOpenIntentClassifier.EXPLICIT_POST
                    )
            )
        }
    }

    @Test
    fun `onInfoClick delegates to closeableInfoHolder`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val info = CloseableInfo(CloseableInfoHolder.item_notes_sync, false)
        vm.onInfoClick(info)

        verify { closeableInfoHolder.close(info) }
    }

    @Test
    fun `closeable info is filtered to notes_sync only`() = runTest {
        coEvery { notesRepository.loadNotes() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        infoFlow.value = listOf(
                CloseableInfo(CloseableInfoHolder.item_other_menu_drag, false),
                CloseableInfo(CloseableInfoHolder.item_notes_sync, false)
        )
        advanceUntilIdle()

        // Только item_notes_sync должен остаться (и только если не closed)
        val infos = vm.uiState.value.info
        assertEquals(1, infos.size)
        assertEquals(CloseableInfoHolder.item_notes_sync, infos[0].id)
    }
}
