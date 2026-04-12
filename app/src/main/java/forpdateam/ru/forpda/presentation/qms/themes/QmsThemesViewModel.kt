package forpdateam.ru.forpda.presentation.qms.themes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Список диалогов QMS с пользователем без Moxy.
 */
class QmsThemesViewModel(
        val themesUserId: Int,
        initialAvatarUrl: String?,
        private val qmsInteractor: QmsInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    var avatarUrl: String? = initialAvatarUrl
        private set

    data class UiState(
            val themes: QmsThemes? = null,
            val loading: Boolean = false,
            val toolbarAvatarUrl: String? = null
    )

    sealed interface NoteEffect {
        data class ForUser(val nick: String, val url: String) : NoteEffect
        data class ForTheme(val name: String, val nick: String, val url: String) : NoteEffect
    }

    private var currentData: QmsThemes? = null

    private val _uiState = MutableStateFlow(UiState(toolbarAvatarUrl = initialAvatarUrl))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _blockDone = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val blockDone: SharedFlow<Unit> = _blockDone.asSharedFlow()

    private val _noteEffect = MutableSharedFlow<NoteEffect>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val noteEffect: SharedFlow<NoteEffect> = _noteEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            qmsInteractor.observeThemes(themesUserId)
                    .catch { e -> errorHandler.handle(e) }
                    .collect { data ->
                        currentData = data
                        _uiState.update { it.copy(themes = data) }
                    }
        }
    }

    fun loadThemes() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { qmsInteractor.getThemesList(themesUserId) }
                    .onSuccess { data ->
                        currentData = data
                        if (data.themes.isEmpty() && data.nick != null) {
                            openChat()
                        }
                    }
                    .onFailure { e -> errorHandler.handle(e) }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun blockUser() {
        val nick = currentData?.nick ?: return
        viewModelScope.launch {
            runCatching { qmsInteractor.blockUser(nick) }
                    .onSuccess { _blockDone.emit(Unit) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun deleteTheme(themeId: Int) {
        val userId = currentData?.userId ?: return
        viewModelScope.launch {
            runCatching { qmsInteractor.deleteTheme(userId, themeId) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun openProfile(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router)
    }

    fun openChat() {
        val data = currentData ?: return
        router.replaceScreen(Screen.QmsChat().apply {
            userId = data.userId
            userNick = data.nick
            avatarUrl = this@QmsThemesViewModel.avatarUrl
        })
    }

    fun createNote() {
        val data = currentData ?: return
        val url = "https://4pda.to/forum/index.php?act=qms&mid=${data.userId}"
        viewModelScope.launch {
            _noteEffect.emit(NoteEffect.ForUser(data.nick.orEmpty(), url))
        }
    }

    fun createThemeNote(item: QmsTheme) {
        val data = currentData ?: return
        val url = "https://4pda.to/forum/index.php?act=qms&mid=${data.userId}&t=${item.userId}"
        viewModelScope.launch {
            _noteEffect.emit(NoteEffect.ForTheme(item.name.orEmpty(), data.nick.orEmpty(), url))
        }
    }

    fun onItemClick(item: QmsTheme) {
        val data = currentData ?: return
        router.navigateTo(Screen.QmsChat().apply {
            screenTitle = item.name
            screenSubTitle = data.nick
            userId = data.userId
            avatarUrl = this@QmsThemesViewModel.avatarUrl
            themeId = item.id
            themeTitle = item.name
        })
    }

    class Factory(
            private val themesUserId: Int,
            private val initialAvatarUrl: String?,
            private val qmsInteractor: QmsInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != QmsThemesViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return QmsThemesViewModel(
                    themesUserId,
                    initialAvatarUrl,
                    qmsInteractor,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}
