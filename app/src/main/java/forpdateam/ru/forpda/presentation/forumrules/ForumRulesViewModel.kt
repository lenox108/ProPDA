package forpdateam.ru.forpda.presentation.forumrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.remote.forum.ForumRules
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
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
 * Правила форума: ViewModel + StateFlow вместо Moxy-presenter.
 */
class ForumRulesViewModel(
        private val forumRepository: ForumRepository,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val forumRulesTemplate: ForumRulesTemplate,
        private val templateManager: TemplateManager,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    data class UiState(
            val loading: Boolean = true,
            val rules: ForumRules? = null,
            val styleType: String = "",
            val fontSize: Int = 16
    )

    private val _uiState = MutableStateFlow(
            UiState(
                    loading = true,
                    styleType = templateManager.getThemeType(),
                    fontSize = mainPreferencesHolder.getWebViewFontSize()
            )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            templateManager.observeThemeType().asFlow().collect { type ->
                _uiState.update { it.copy(styleType = type) }
            }
        }
        viewModelScope.launch {
            mainPreferencesHolder.observeWebViewFontSize().asFlow().collect { size ->
                _uiState.update { it.copy(fontSize = size) }
            }
        }
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                forumRepository.getRules()
                        .map { forumRulesTemplate.mapEntity(it) }
                        .await()
            }.onSuccess { rules ->
                _uiState.update { it.copy(loading = false, rules = rules) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, rules = null) }
                errorHandler.handle(e)
            }
        }
    }

    class Factory(
            private val forumRepository: ForumRepository,
            private val mainPreferencesHolder: MainPreferencesHolder,
            private val forumRulesTemplate: ForumRulesTemplate,
            private val templateManager: TemplateManager,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ForumRulesViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return ForumRulesViewModel(
                    forumRepository,
                    mainPreferencesHolder,
                    forumRulesTemplate,
                    templateManager,
                    errorHandler
            ) as T
        }
    }
}
