package forpdateam.ru.forpda.presentation.history

import forpdateam.ru.forpda.presentation.BaseViewModel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.model.repository.history.HistoryRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * История посещений без Moxy.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
        private val historyRepository: HistoryRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    data class UiState(
            val items: List<HistoryItem> = emptyList(),
            val loading: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        scope.launch {
            historyRepository.observeItems()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { items ->
                        _uiState.update { it.copy(items = items) }
                    }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { historyRepository.getHistory() }
                    .onSuccess { items ->
                        _uiState.update { it.copy(loading = false, items = items) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(loading = false) }
                        errorHandler.handle(e)
                    }
        }
    }

    fun remove(id: Int) {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                historyRepository.remove(id)
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    override fun clear() {
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                historyRepository.clear()
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun copyLink(item: HistoryItem) {
        item.url?.let { clipboardHelper.copyToClipboard(it) }
    }

    fun onItemClick(item: HistoryItem) {
        item.url?.let { url ->
            linkHandler.handle(url, router, mapOf(Screen.ARG_TITLE to (item.title ?: "")))
        }
    }

}
