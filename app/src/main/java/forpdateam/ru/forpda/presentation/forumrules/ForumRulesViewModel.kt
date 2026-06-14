package forpdateam.ru.forpda.presentation.forumrules

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.remote.forum.ForumRules
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
 * Правила форума: ViewModel + StateFlow вместо Moxy-presenter.
 */
@HiltViewModel
class ForumRulesViewModel @Inject constructor(
        private val forumRepository: ForumRepository,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val forumRulesTemplate: ForumRulesTemplate,
        private val templateManager: TemplateManager,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    data class UiState(
            val loading: Boolean = true,
            val rules: ForumRules? = null,
            val styleType: String = "",
            val fontSize: Int = 16,
            val appFontMode: AppFontMode = AppFontMode.SYSTEM
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
        scope.launch {
            templateManager.observeThemeTypeFlow().collect { type ->
                _uiState.update { it.copy(styleType = type) }
            }
        }
        scope.launch {
            mainPreferencesHolder.observeWebViewFontSizeFlow().collect { size ->
                _uiState.update { it.copy(fontSize = size) }
            }
        }
        scope.launch {
            mainPreferencesHolder.observeAppFontModeFlow().collect { mode ->
                _uiState.update { it.copy(appFontMode = mode) }
            }
        }
        loadRules()
    }

    private fun loadRules() {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                val entity = forumRepository.getRules()
                forumRulesTemplate.mapEntity(entity)
            }.onSuccess { rules ->
                _uiState.update { it.copy(loading = false, rules = rules) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, rules = null) }
                errorHandler.handle(e)
            }
        }
    }

}
