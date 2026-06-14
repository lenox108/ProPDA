package forpdateam.ru.forpda.presentation.qms.themes

import forpdateam.ru.forpda.presentation.BaseViewModel
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.model.CountersHolder
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
@HiltViewModel
class QmsThemesViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val qmsInteractor: QmsInteractor,
        private val countersHolder: CountersHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    val themesUserId: Int = savedStateHandle["USER_ID_ARG"] ?: 0
    var avatarUrl: String? = savedStateHandle["USER_AVATAR_ARG"]
        private set

    companion object {
        const val ARG_USER_ID = "USER_ID_ARG"
        const val ARG_AVATAR_URL = "USER_AVATAR_ARG"

        fun themeNoteUrl(userId: Int, themeId: Int): String =
                "https://4pda.to/forum/index.php?act=qms&mid=$userId&t=$themeId"
    }

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

    private val _uiState = MutableStateFlow(UiState(toolbarAvatarUrl = avatarUrl))
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
        scope.launch {
            qmsInteractor.observeThemes(themesUserId)
                    .catch { e -> errorHandler.handle(e) }
                    .collect { data ->
                        currentData = data
                        _uiState.update { it.copy(themes = data) }
                    }
        }
    }

    fun loadThemes() {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { qmsInteractor.getThemesList(themesUserId) }
                    .onSuccess { data ->
                        currentData = data
                        if (data.themes.isEmpty() && data.nick != null) {
                            router.replaceScreen(Screen.QmsChat().apply {
                                userId = getUserId()
                                userNick = getNick()
                                avatarUrl = this@QmsThemesViewModel.avatarUrl
                            })
                        }
                    }
                    .onFailure { e -> errorHandler.handle(e) }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun blockUser() {
        val nick = currentData?.nick ?: return
        scope.launch {
            runCatching { qmsInteractor.blockUser(nick) }
                    .onSuccess { _blockDone.emit(Unit) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun deleteTheme(themeId: Int) {
        val userId = currentData?.userId ?: return
        scope.launch {
            runCatching { qmsInteractor.deleteTheme(userId, themeId) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun openProfile(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router)
    }

    private fun getUserId(): Int = currentData?.userId ?: themesUserId

    private fun getNick(): String? = currentData?.nick

    fun openChat() {
        val userId = getUserId()
        val data = currentData ?: return
        val themeId = data.themes.firstOrNull()?.id?.takeIf { it > 0 } ?: return
        router.replaceScreen(Screen.QmsChat().apply {
            this.userId = userId
            this.themeId = themeId
            themeTitle = data.themes.firstOrNull()?.name
            userNick = getNick()
            avatarUrl = this@QmsThemesViewModel.avatarUrl
        })
    }

    fun createNote() {
        val data = currentData ?: return
        val url = "https://4pda.to/forum/index.php?act=qms&mid=${data.userId}"
        scope.launch {
            _noteEffect.emit(NoteEffect.ForUser(data.nick.orEmpty(), url))
        }
    }

    fun createThemeNote(item: QmsTheme) {
        val data = currentData ?: return
        val url = themeNoteUrl(data.userId, item.id)
        scope.launch {
            _noteEffect.emit(NoteEffect.ForTheme(item.name.orEmpty(), data.nick.orEmpty(), url))
        }
    }

    fun onItemClick(item: QmsTheme) {
        val unread = item.countNew.coerceAtLeast(0)
        if (unread > 0) {
            // Opening the dialog marks it as read server-side, but the bottom-menu badge
            // is driven by cached counters; decrement locally for immediate UI sync.
            item.countNew = 0
            countersHolder.decrementQms(unread)
        }
        val userId = getUserId()
        // DEBUG: capture the exact ids handed to the chat screen. For the system contact (userId=0)
        // a themeId<=0 here means the themes list only had the synthesised virtual "Оповещения"
        // placeholder (id=0), which the chat screen rejects as an invalid theme → empty alerts.
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            forpdateam.ru.forpda.diagnostic.FpdaDebugLog.logQms(
                    forpdateam.ru.forpda.diagnostic.FpdaDebugLog.QmsArea.OPEN,
                    "themes_item_click",
                    mapOf(
                            "source" to "themes_list",
                            "mid" to userId,
                            "userId" to userId,
                            "themeId" to item.id,
                            "isSystemContact" to (userId == 0),
                            "themeName" to item.name?.take(24),
                            "unread" to unread
                    )
            )
        }
        router.navigateTo(Screen.QmsChat().apply {
            screenTitle = item.name
            screenSubTitle = getNick()
            this.userId = userId
            avatarUrl = this@QmsThemesViewModel.avatarUrl
            themeId = item.id
            themeTitle = item.name
        })
    }

}
