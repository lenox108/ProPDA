package forpdateam.ru.forpda.presentation.qms.contacts

import app.cash.turbine.test
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
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

@OptIn(ExperimentalCoroutinesApi::class)
class QmsContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var qmsInteractor: QmsInteractor
    private lateinit var router: TabRouter
    private lateinit var linkHandler: ILinkHandler
    private lateinit var countersHolder: CountersHolder
    private lateinit var eventsRepository: EventsRepository
    private lateinit var errorHandler: IErrorHandler

    private val contactsFlow = MutableStateFlow<List<QmsContact>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        qmsInteractor = mockk(relaxed = true)
        router = mockk(relaxed = true)
        linkHandler = mockk(relaxed = true)
        countersHolder = mockk(relaxed = true)
        eventsRepository = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)

        every { qmsInteractor.observeContacts() } returns contactsFlow
        every { countersHolder.get() } returns mockk(relaxed = true) { every { qms } returns 0 }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): QmsContactsViewModel {
        return QmsContactsViewModel(qmsInteractor, router, linkHandler, countersHolder, eventsRepository, errorHandler)
    }

    private fun makeContact(id: Int, nick: String = "User$id", count: Int = 0): QmsContact {
        return QmsContact().apply {
            this.id = id
            this.nick = nick
            this.count = count
        }
    }

    @Test
    fun `initial state has empty contacts and not loading`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.contacts.isEmpty())
        assertFalse(state.loading)
    }

    @Test
    fun `contacts update reflects in state`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val contacts = listOf(makeContact(1), makeContact(2))
        contactsFlow.value = contacts
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.contacts.size)
        assertEquals(1, vm.uiState.value.contacts[0].id)
    }

    @Test
    fun `loadContacts sets loading to true then false`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns listOf(makeContact(1))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadContacts(showProgress = true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `loadContacts on error calls errorHandler`() = runTest {
        val error = RuntimeException("Load failed")
        coEvery { qmsInteractor.getContactList() } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadContacts(showProgress = true)
        advanceUntilIdle()

        verify { errorHandler.handle(error) }
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `searchLocal filters contacts by nick`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val contacts = listOf(
            makeContact(1, "Alice"),
            makeContact(2, "Bob"),
            makeContact(3, "Alex")
        )
        contactsFlow.value = contacts
        advanceUntilIdle()

        vm.searchLocal("al")
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.contacts.size)
        assertTrue(vm.uiState.value.contacts.all { it.nick?.lowercase()?.contains("al") == true })
    }

    @Test
    fun `searchLocal empty shows all contacts`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val contacts = listOf(makeContact(1), makeContact(2))
        contactsFlow.value = contacts
        advanceUntilIdle()

        vm.searchLocal("")
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.contacts.size)
    }

    @Test
    fun `deleteDialog calls interactor and reloads`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()
        coEvery { qmsInteractor.deleteDialog(any()) } returns "success"

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteDialog(5)
        advanceUntilIdle()

        coVerify { qmsInteractor.deleteDialog(5) }
    }

    @Test
    fun `deleteDialog on error calls errorHandler`() = runTest {
        val error = RuntimeException("Delete failed")
        coEvery { qmsInteractor.getContactList() } returns emptyList()
        coEvery { qmsInteractor.deleteDialog(any()) } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteDialog(1)
        advanceUntilIdle()

        verify { errorHandler.handle(error) }
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `blockUser emits result`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()
        coEvery { qmsInteractor.blockUser(any()) } returns listOf(makeContact(1, "TestUser"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.blockUserResult.test {
            vm.blockUser(makeContact(1, "TestUser"))
            val result = awaitItem()
            assertTrue(result)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `blockUser on error calls errorHandler`() = runTest {
        val error = RuntimeException("Block failed")
        coEvery { qmsInteractor.getContactList() } returns emptyList()
        coEvery { qmsInteractor.blockUser(any()) } throws error

        val vm = createViewModel()
        advanceUntilIdle()

        vm.blockUser(makeContact(1))
        advanceUntilIdle()

        verify { errorHandler.handle(error) }
    }

    @Test
    fun `onItemClick navigates to QmsThemes`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val contact = makeContact(123, "TestUser")
        vm.onItemClick(contact)

        verify { router.navigateTo(match { it.screenTitle == "TestUser" }) }
    }

    @Test
    fun `createNote emits nick and url`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.createNote.test {
            vm.createNote(makeContact(5, "TestNick"))
            val result = awaitItem()
            assertEquals("TestNick" to "https://4pda.to/forum/index.php?act=qms&mid=5", result)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `openProfile delegates to linkHandler`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        val contact = makeContact(123)
        vm.openProfile(contact)

        verify { linkHandler.handle("https://4pda.to/forum/index.php?showuser=123", router) }
    }

    @Test
    fun `openBlackList navigates to QmsBlackList`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openBlackList()

        verify { router.navigateTo(any<Screen.QmsBlackList>()) }
    }

    @Test
    fun `openChatCreator navigates to QmsChat`() = runTest {
        coEvery { qmsInteractor.getContactList() } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.openChatCreator()

        verify { router.navigateTo(any<Screen.QmsChat>()) }
    }
}
