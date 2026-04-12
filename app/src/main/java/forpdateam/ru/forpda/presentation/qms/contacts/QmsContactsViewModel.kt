package forpdateam.ru.forpda.presentation.qms.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
/**
 * Контакты QMS без Moxy.
 */
class QmsContactsViewModel(
        private val qmsInteractor: QmsInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val countersHolder: CountersHolder,
        private val eventsRepository: EventsRepository,
        private val errorHandler: IErrorHandler
) : ViewModel() {

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
        viewModelScope.launch {
            qmsInteractor.observeContacts()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { list ->
                        localItems.clear()
                        localItems.addAll(list)
                        countersHolder.set(countersHolder.get().apply {
                            qms = list.sumOf { it.count }
                        })
                        publishDisplayed()
                    }
        }
        viewModelScope.launch {
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

    fun loadContacts(showProgress: Boolean = true) {
        viewModelScope.launch {
            if (showProgress) {
                _uiState.update { it.copy(loading = true) }
            }
            runCatching { qmsInteractor.getContactList() }
                    .onFailure { e -> errorHandler.handle(e) }
            if (showProgress) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun deleteDialog(id: Int) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        router.navigateTo(Screen.QmsThemes().apply {
            screenTitle = item.nick
            userId = item.id
            avatarUrl = item.avatar
        })
    }

    fun createNote(item: QmsContact) {
        val url = "https://4pda.to/forum/index.php?act=qms&mid=${item.id}"
        viewModelScope.launch {
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

    class Factory(
            private val qmsInteractor: QmsInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val countersHolder: CountersHolder,
            private val eventsRepository: EventsRepository,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != QmsContactsViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return QmsContactsViewModel(
                    qmsInteractor,
                    router,
                    linkHandler,
                    countersHolder,
                    eventsRepository,
                    errorHandler
            ) as T
        }
    }
}
