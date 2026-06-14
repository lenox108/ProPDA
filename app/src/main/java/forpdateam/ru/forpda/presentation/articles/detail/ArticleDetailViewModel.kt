package forpdateam.ru.forpda.presentation.articles.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import forpdateam.ru.forpda.presentation.BaseViewModel
import androidx.lifecycle.ViewModelProvider

import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.diagnostic.StateRaceTrace
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ArticleDetailViewModel(
        private val context: Context,
        private val articleInteractor: ArticleInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var subscriptionsStarted = false
    private var loadJob: Job? = null
    private var loadRequestId: Int = 0

    private val _currentData = MutableStateFlow<DetailsPage?>(null)
    val currentData: StateFlow<DetailsPage?> = _currentData.asStateFlow()

    private val _uiState = MutableStateFlow<ArticleUiState>(ArticleUiState.Idle)
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ArticleDetailUiEvent>()
    val uiEvents: SharedFlow<ArticleDetailUiEvent> = _uiEvents.asSharedFlow()

    fun start() {
        if (!subscriptionsStarted) {
            subscriptionsStarted = true
            scope.launch {
                articleInteractor.observeData()
                        .catch { errorHandler.handle(it) }
                        .collect { page ->
                            _currentData.value = page
                            _uiState.value = if (page == null) ArticleUiState.Idle else ArticleUiState.Content(page)
                        }
            }
        }
        loadArticle()
    }

    fun loadArticle(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        val requestId = ++loadRequestId
        val previous = _currentData.value
        _uiState.value = if (previous != null && previous.html.orEmpty().length >= 120) {
            ArticleUiState.Content(previous)
        } else {
            ArticleUiState.Loading
        }
        _refreshing.value = true
        StateRaceTrace.log(
                domain = "article_vm",
                event = "request_start",
                requestId = requestId,
                generation = articleInteractor.currentArticleGeneration()
        )
        loadJob = scope.launch {
            try {
                articleInteractor.loadArticle(bypassCache = forceRefresh)
                if (requestId == loadRequestId) {
                    StateRaceTrace.log(
                            domain = "article_vm",
                            event = "request_complete",
                            requestId = requestId,
                            generation = articleInteractor.currentArticleGeneration()
                    )
                }
            } catch (e: Throwable) {
                if (isCancellation(e)) {
                    StateRaceTrace.log(
                            domain = "article_vm",
                            event = "request_cancelled",
                            requestId = requestId,
                            reason = "cancellation"
                    )
                    if (requestId == loadRequestId) {
                        restoreUiStateAfterCancelledLoad()
                    }
                    return@launch
                }
                errorHandler.handle(e)
                if (requestId == loadRequestId) {
                    val cached = _currentData.value
                    _uiState.value = if (cached != null && cached.html.orEmpty().length >= 120) {
                        ArticleUiState.Content(cached)
                    } else {
                        ArticleUiState.Error(e)
                    }
                }
            } finally {
                if (requestId == loadRequestId) {
                    _refreshing.value = false
                } else {
                    StateRaceTrace.log(
                            domain = "article_vm",
                            event = "stale_ignored",
                            requestId = requestId,
                            currentGeneration = loadRequestId,
                            reason = "finally_refresh_state"
                    )
                }
            }
        }
    }

    fun reloadIfContentMissing() {
        val current = _currentData.value
        val html = current?.html.orEmpty()
        if (html.length >= 120) {
            if (_uiState.value is ArticleUiState.Loading) {
                _uiState.value = ArticleUiState.Content(current!!)
            }
            return
        }
        if (loadJob?.isActive == true) {
            return
        }
        if (_uiState.value is ArticleUiState.Loading) {
            _uiState.value = ArticleUiState.Idle
        }
        loadArticle(forceRefresh = current != null && html.isEmpty())
    }

    private fun restoreUiStateAfterCancelledLoad() {
        val cached = _currentData.value
        _uiState.value = when {
            cached != null && cached.html.orEmpty().length >= 120 -> ArticleUiState.Content(cached)
            else -> ArticleUiState.Idle
        }
    }

    fun isArticleLoading(): Boolean =
            loadJob?.isActive == true || _refreshing.value || _uiState.value is ArticleUiState.Loading

    private fun isCancellation(t: Throwable): Boolean {
        var c: Throwable? = t
        while (c != null) {
            if (c is CancellationException) return true
            c = c.cause
        }
        return false
    }

    fun openAuthorProfile() {
        _currentData.value?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${it.authorId}", router)
        }
    }

    fun copyLink() {
        _currentData.value?.let {
            clipboardHelper.copyToClipboard("https://4pda.to/index.php?p=${it.id}")
        }
    }

    fun shareLink() {
        _currentData.value?.let {
            Utils.shareText(context, "https://4pda.to/index.php?p=${it.id}")
        }
    }

    fun createNote() {
        _currentData.value?.let {
            val url = "https://4pda.to/index.php?p=${it.id}"
            scope.launch { _uiEvents.emit(ArticleDetailUiEvent.ShowCreateNote(it.title.orEmpty(), url)) }
        }
    }

    class Factory(
            private val context: Context,
            private val articleInteractor: ArticleInteractor,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler,
            private val clipboardHelper: ClipboardHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArticleDetailViewModel::class.java)) {
                return ArticleDetailViewModel(context, articleInteractor, router, linkHandler, errorHandler, clipboardHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

sealed class ArticleDetailUiEvent {
    data class ShowCreateNote(val title: String, val url: String) : ArticleDetailUiEvent()
}

sealed class ArticleUiState {
    data object Idle : ArticleUiState()
    data object Loading : ArticleUiState()
    data class Content(val page: DetailsPage) : ArticleUiState()
    data class Error(val throwable: Throwable) : ArticleUiState()
}
