package forpdateam.ru.forpda.presentation.articles.detail.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.ui.TemplateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await

/**
 * HTML-контент статьи: ViewModel + StateFlow вместо Moxy-presenter.
 */
class ArticleContentViewModel(
        private val articleInteractor: ArticleInteractor,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val templateManager: TemplateManager,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    data class UiState(
            val article: DetailsPage? = null,
            val styleType: String = "",
            val fontSize: Int = 16
    )

    private val _uiState = MutableStateFlow(
            UiState(
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
        viewModelScope.launch {
            articleInteractor.observeData()
                    .asFlow()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { article ->
                        _uiState.update { it.copy(article = article) }
                    }
        }
    }

    fun sendPoll(from: String, pollId: Int, answersId: IntArray) {
        viewModelScope.launch {
            runCatching {
                articleInteractor.sendPoll(from, pollId, answersId).await()
            }.onFailure { e ->
                errorHandler.handle(e)
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
            if (modelClass != ArticleContentViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return ArticleContentViewModel(
                    articleInteractor,
                    mainPreferencesHolder,
                    templateManager,
                    errorHandler
            ) as T
        }
    }
}
