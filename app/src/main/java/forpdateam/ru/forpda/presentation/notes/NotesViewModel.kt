package forpdateam.ru.forpda.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
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
import kotlinx.coroutines.rx2.asFlow

/**
 * Заметки без Moxy.
 */
class NotesViewModel(
        private val notesRepository: NotesRepository,
        private val closeableInfoHolder: CloseableInfoHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    data class UiState(
            val items: List<NoteItem> = emptyList(),
            val info: List<CloseableInfo> = emptyList(),
            val refreshing: Boolean = false
    )

    sealed interface UiEffect {
        data class ShowEditPopup(val item: NoteItem) : UiEffect
        object ShowAddPopup : UiEffect
        object ImportDone : UiEffect
        data class ExportDone(val path: String) : UiEffect
    }

    private val closeableInfoIds = arrayOf(CloseableInfoHolder.item_notes_sync)

    private var currentItems = listOf<NoteItem>()
    private var currentInfos = listOf<CloseableInfo>()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            notesRepository.observeItems()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { list ->
                        currentItems = list
                        publish()
                    }
        }
        viewModelScope.launch {
            closeableInfoHolder.observe()
                    .asFlow()
                    .catch { e -> errorHandler.handle(e) }
                    .collect { info ->
                        currentInfos = info.filter {
                            closeableInfoIds.contains(it.id) && !it.isClosed
                        }
                        publish()
                    }
        }
        loadNotes()
    }

    private fun publish() {
        _uiState.update {
            it.copy(items = currentItems, info = currentInfos)
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true) }
            runCatching { notesRepository.loadNotes() }
                    .onSuccess { list ->
                        currentItems = list
                        publish()
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            runCatching { notesRepository.deleteNote(id) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun importNotes(file: RequestFile) {
        viewModelScope.launch {
            runCatching { notesRepository.importNotes(file) }
                    .onSuccess {
                        _effects.emit(UiEffect.ImportDone)
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
        }
    }

    fun exportNotes() {
        viewModelScope.launch {
            runCatching { notesRepository.exportNotes() }
                    .onSuccess { path ->
                        _effects.emit(UiEffect.ExportDone(path))
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
        }
    }

    fun onItemClick(item: NoteItem) {
        linkHandler.handle(item.link, router)
    }

    fun onInfoClick(info: CloseableInfo) {
        closeableInfoHolder.close(info)
    }

    fun copyLink(item: NoteItem) {
        Utils.copyToClipBoard(item.link)
    }

    fun editNote(item: NoteItem) {
        viewModelScope.launch {
            _effects.emit(UiEffect.ShowEditPopup(item))
        }
    }

    fun addNote() {
        viewModelScope.launch {
            _effects.emit(UiEffect.ShowAddPopup)
        }
    }

    class Factory(
            private val notesRepository: NotesRepository,
            private val closeableInfoHolder: CloseableInfoHolder,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != NotesViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return NotesViewModel(
                    notesRepository,
                    closeableInfoHolder,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}
