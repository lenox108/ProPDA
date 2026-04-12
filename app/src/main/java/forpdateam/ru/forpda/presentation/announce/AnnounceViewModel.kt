package forpdateam.ru.forpda.presentation.announce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.remote.forum.Announce
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.ui.TemplateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await

/**
 * Объявление форума: ViewModel + StateFlow вместо Moxy-presenter.
 */
class AnnounceViewModel(
        private val announceId: Int,
        private val forumId: Int,
        private val forumRepository: ForumRepository,
        private val announceTemplate: AnnounceTemplate,
        private val templateManager: TemplateManager,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    data class UiState(
            val loading: Boolean = true,
            val announce: Announce? = null,
            val styleType: String = ""
    )

    private val _uiState = MutableStateFlow(
            UiState(loading = true, styleType = templateManager.getThemeType())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            templateManager.observeThemeType().asFlow().collect { type ->
                _uiState.update { it.copy(styleType = type) }
            }
        }
        loadAnnounce()
    }

    private fun loadAnnounce() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                forumRepository.getAnnounce(announceId, forumId)
                        .map { announceTemplate.mapEntity(it) }
                        .await()
            }.onSuccess { announce ->
                _uiState.update { it.copy(loading = false, announce = announce) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, announce = null) }
                errorHandler.handle(e)
            }
        }
    }

    class Factory(
            private val announceId: Int,
            private val forumId: Int,
            private val forumRepository: ForumRepository,
            private val announceTemplate: AnnounceTemplate,
            private val templateManager: TemplateManager,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != AnnounceViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return AnnounceViewModel(
                    announceId,
                    forumId,
                    forumRepository,
                    announceTemplate,
                    templateManager,
                    errorHandler
            ) as T
        }
    }
}
