package forpdateam.ru.forpda.presentation.theme.blacklist

import dagger.hilt.android.lifecycle.HiltViewModel
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.BaseViewModel
import forpdateam.ru.forpda.presentation.ILinkHandler
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
import javax.inject.Inject

@HiltViewModel
class ForumBlackListViewModel @Inject constructor(
        private val topicPreferencesHolder: TopicPreferencesHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler
) : BaseViewModel() {

    data class UiState(
            val users: List<ForumBlacklistedUser> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _userRemoved = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val userRemoved: SharedFlow<Unit> = _userRemoved.asSharedFlow()

    init {
        scope.launch {
            topicPreferencesHolder.observeForumBlacklistFlow()
                    .catch { /* prefs read errors are handled in DataStore */ }
                    .collect { users ->
                        _uiState.update { it.copy(users = users) }
                    }
        }
    }

    fun removeUser(user: ForumBlacklistedUser) {
        scope.launch {
            topicPreferencesHolder.removeForumBlacklistedUser(user)
            _userRemoved.tryEmit(Unit)
        }
    }

    fun openProfile(user: ForumBlacklistedUser) {
        if (user.userId <= 0) return
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${user.userId}", router)
    }
}
