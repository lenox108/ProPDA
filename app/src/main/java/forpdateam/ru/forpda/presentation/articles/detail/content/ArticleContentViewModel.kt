package forpdateam.ru.forpda.presentation.articles.detail.content

import androidx.lifecycle.ViewModel
import forpdateam.ru.forpda.presentation.BaseViewModel
import androidx.lifecycle.ViewModelProvider

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.ui.AppFontMode
import forpdateam.ru.forpda.ui.TemplateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HTML-контент статьи: ViewModel + StateFlow вместо Moxy-presenter.
 */
class ArticleContentViewModel(
        private val articleInteractor: ArticleInteractor,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val templateManager: TemplateManager,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    data class UiState(
            val article: DetailsPage? = null,
            val styleType: String = "",
            val fontSize: Int = 16,
            val appFontMode: AppFontMode = AppFontMode.SYSTEM
    )

    private val _uiState = MutableStateFlow(
            UiState(
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
        scope.launch {
            articleInteractor.observeData()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { article ->
                        _uiState.update { it.copy(article = article) }
                    }
        }
    }

    fun sendPoll(from: String, pollId: Int, answersId: IntArray) {
        scope.launch {
            runCatching {
                articleInteractor.sendPoll(from, pollId, answersId)
            }.onFailure { e ->
                errorHandler.handle(e)
            }
        }
    }

    fun votePoll(
            from: String,
            pollId: Int,
            answersId: IntArray,
            onSuccess: (String) -> Unit,
            onError: (String) -> Unit
    ) {
        scope.launch {
            runCatching {
                articleInteractor.votePoll(from, pollId, answersId)
            }.onSuccess { html ->
                onSuccess(html)
            }.onFailure { e ->
                errorHandler.handle(e)
                onError(e.message ?: "Не удалось отправить голос")
            }
        }
    }

    class Factory(
            private val articleInteractor: ArticleInteractor,
            private val mainPreferencesHolder: MainPreferencesHolder,
            private val templateManager: TemplateManager,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArticleContentViewModel::class.java)) {
                return ArticleContentViewModel(articleInteractor, mainPreferencesHolder, templateManager, errorHandler) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
