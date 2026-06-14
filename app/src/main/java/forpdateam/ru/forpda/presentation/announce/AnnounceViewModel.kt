package forpdateam.ru.forpda.presentation.announce

import androidx.lifecycle.SavedStateHandle
import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.remote.forum.Announce
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.ui.AppFontMode
import forpdateam.ru.forpda.ui.TemplateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Объявление форума: ViewModel + StateFlow вместо Moxy-presenter.
 */
@HiltViewModel
class AnnounceViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val forumRepository: ForumRepository,
        private val announceTemplate: AnnounceTemplate,
        private val templateManager: TemplateManager,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    private val announceId: Int = savedStateHandle["announceId"] ?: 0
    private val forumId: Int = savedStateHandle["forumId"] ?: 0

    companion object {
        const val ARG_ANNOUNCE_ID = "announceId"
        const val ARG_FORUM_ID = "forumId"
    }

    data class UiState(
            val loading: Boolean = true,
            val announce: Announce? = null,
            val styleType: String = "",
            val appFontMode: AppFontMode = AppFontMode.SYSTEM
    )

    private val _uiState = MutableStateFlow(
            UiState(loading = true, styleType = templateManager.getThemeType())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            templateManager.observeThemeTypeFlow().collect { type ->
                _uiState.update { it.copy(styleType = type) }
            }
        }
        scope.launch {
            mainPreferencesHolder.observeAppFontModeFlow().collect { mode ->
                _uiState.update { it.copy(appFontMode = mode) }
            }
        }
        loadAnnounce()
    }

    private fun loadAnnounce() {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                val entity = forumRepository.getAnnounce(announceId, forumId)
                announceTemplate.mapEntity(entity)
            }.onSuccess { announce ->
                _uiState.update { it.copy(loading = false, announce = announce) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, announce = null) }
                errorHandler.handle(e)
            }
        }
    }

}
