package forpdateam.ru.forpda.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * История посещений без Moxy.
 */
class HistoryViewModel(
        private val historyRepository: HistoryRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    data class UiState(
            val items: List<HistoryItem> = emptyList(),
            val loading: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.observeItems()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { items ->
                        _uiState.update { it.copy(items = items) }
                    }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { historyRepository.remove(id) }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun clear() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { historyRepository.clear() }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun copyLink(item: HistoryItem) {
        Utils.copyToClipBoard(item.url)
    }

    fun onItemClick(item: HistoryItem) {
        linkHandler.handle(item.url, router, mapOf(Screen.ARG_TITLE to item.title))
    }

    class Factory(
            private val historyRepository: HistoryRepository,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != HistoryViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return HistoryViewModel(historyRepository, router, linkHandler, errorHandler) as T
        }
    }
}
