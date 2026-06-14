package forpdateam.ru.forpda.presentation.qms.contacts
import forpdateam.ru.forpda.BuildConfig

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Контакты QMS без Moxy.
 */
@HiltViewModel
class QmsContactsViewModel @Inject constructor(
        private val qmsInteractor: QmsInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val countersHolder: CountersHolder,
        private val eventsRepository: EventsRepository,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    data class UiState(
            val contacts: List<QmsContact> = emptyList(),
            val loading: Boolean = false
    )

    private val localItems = mutableListOf<QmsContact>()
    private var searchNick: String = ""

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _blockUserResult = MutableSharedFlow<Boolean>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val blockUserResult: SharedFlow<Boolean> = _blockUserResult.asSharedFlow()

    private val _createNote = MutableSharedFlow<Pair<String, String>>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val createNote: SharedFlow<Pair<String, String>> = _createNote.asSharedFlow()

    init {
        scope.launch {
            qmsInteractor.observeContacts()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { list ->
                        localItems.clear()
                        localItems.addAll(list)
                        if (BuildConfig.DEBUG) Timber.d("QmsContactsViewModel.observeContacts contacts=${list.size} qms=${list.sumOf { it.count }}")
                        countersHolder.set(countersHolder.get().apply {
                            qms = list.sumOf { it.count }
                        })
                        publishDisplayed()
                    }
        }
        scope.launch {
            var lastRefresh = 0L
            eventsRepository.observeEventsTab()
                    .filter { NotificationEvent.fromQms(it.source) }
                    .collect {
                        val now = System.currentTimeMillis()
                        if (now - lastRefresh >= 3000L) {
                            lastRefresh = now
                            loadContacts(showProgress = false)
                        }
                    }
        }
    }

    private fun publishDisplayed() {
        val nick = searchNick
        val displayed = if (nick.isEmpty()) {
            localItems.toList()
        } else {
            localItems.filter { contact ->
                contact.nick?.lowercase()?.contains(nick.lowercase()) == true
            }
        }
        _uiState.update { it.copy(contacts = displayed) }
    }

    fun searchLocal(nick: String) {
        searchNick = nick
        publishDisplayed()
    }

    private var loadJob: Job? = null

    fun loadContacts(showProgress: Boolean = true) {
        Timber.d("loadContacts called, showProgress=$showProgress")
        loadJob?.cancel()
        loadJob = scope.launch {
            if (showProgress) {
                _uiState.update { it.copy(loading = true) }
            }
            runCatching { qmsInteractor.getContactList() }
                    .onSuccess { list ->
                        Timber.d("loadContacts success, got ${list.size} contacts")
                        localItems.clear()
                        localItems.addAll(list)
                        publishDisplayed()
                    }
                    .onFailure { e ->
                        Timber.e(e, "loadContacts failed")
                        errorHandler.handle(e)
                    }
            if (showProgress) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun deleteDialog(id: Int) {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { qmsInteractor.deleteDialog(id) }
                    .onSuccess {
                        loadContacts(showProgress = true)
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                        _uiState.update { it.copy(loading = false) }
                    }
        }
    }

    fun blockUser(item: QmsContact) {
        scope.launch {
            runCatching {
                val list = qmsInteractor.blockUser(item.nick.orEmpty())
                list.firstOrNull { it.nick == item.nick } != null
            }.onSuccess { blocked ->
                _blockUserResult.emit(blocked)
            }.onFailure { e ->
                errorHandler.handle(e)
            }
        }
    }

    fun onItemClick(item: QmsContact) {
        // System contact "Сообщения 4PDA" has ID=0
        router.navigateTo(Screen.QmsThemes().apply {
            screenTitle = item.nick
            userId = item.id
            avatarUrl = item.avatar
        })
    }

    fun createNote(item: QmsContact) {
        val url = "https://4pda.to/forum/index.php?act=qms&mid=${item.id}"
        scope.launch {
            _createNote.emit(item.nick.orEmpty() to url)
        }
    }

    fun openProfile(item: QmsContact) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.id}", router)
    }

    fun openBlackList() {
        router.navigateTo(Screen.QmsBlackList())
    }

    fun openChatCreator() {
        router.navigateTo(Screen.QmsChat())
    }

}
