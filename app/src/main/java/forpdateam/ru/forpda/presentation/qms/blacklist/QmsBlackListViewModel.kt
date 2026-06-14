package forpdateam.ru.forpda.presentation.qms.blacklist

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Чёрный список QMS без Moxy.
 */
@HiltViewModel
class QmsBlackListViewModel @Inject constructor(
        private val qmsInteractor: QmsInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    data class UiState(
            val contacts: List<QmsContact> = emptyList(),
            val loading: Boolean = false,
            val nickSuggestions: List<ForumUser> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _clearNick = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val clearNick: SharedFlow<Unit> = _clearNick.asSharedFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { qmsInteractor.getBlackList() }
                    .onSuccess { list ->
                        _uiState.update { it.copy(loading = false, contacts = list) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(loading = false) }
                        errorHandler.handle(e)
                    }
        }
    }

    fun blockUser(nick: String) {
        scope.launch {
            runCatching { qmsInteractor.blockUser(nick) }
                    .onSuccess { list ->
                        _uiState.update { it.copy(contacts = list) }
                        _clearNick.tryEmit(Unit)
                    }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun unBlockUser(id: Int) {
        scope.launch {
            runCatching { qmsInteractor.unBlockUsers(id) }
                    .onSuccess { list ->
                        _uiState.update { it.copy(contacts = list) }
                        _clearNick.tryEmit(Unit)
                    }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun searchUser(nick: String) {
        scope.launch {
            runCatching { qmsInteractor.findUser(nick) }
                    .onSuccess { users ->
                        _uiState.update { it.copy(nickSuggestions = users) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(nickSuggestions = emptyList()) }
                        errorHandler.handle(e)
                    }
        }
    }

    fun openProfile(item: QmsContact) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.id}", router)
    }

    fun openDialogs(item: QmsContact) {
        router.navigateTo(Screen.QmsThemes().apply {
            screenTitle = item.nick
            userId = item.id
            avatarUrl = item.avatar
        })
    }

}
