package forpdateam.ru.forpda.presentation.notes

import android.content.SharedPreferences
import forpdateam.ru.forpda.presentation.BaseViewModel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.app.notes.NoteSortMode
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier
import java.io.OutputStream
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Заметки без Moxy.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
        private val notesRepository: NotesRepository,
        private val closeableInfoHolder: CloseableInfoHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper,
        private val preferences: SharedPreferences
) : BaseViewModel() {

    data class UiState(
            val items: List<NoteItem> = emptyList(),
            val folders: List<NoteFolder> = emptyList(),
            val includeAllFolders: Boolean = true,
            val selectedFolderId: Long? = null,
            val expandedFolderIds: Set<Long> = emptySet(),
            val sortMode: NoteSortMode = NoteSortMode.CREATED_DESC,
            val searchQuery: String = "",
            val info: List<CloseableInfo> = emptyList(),
            val refreshing: Boolean = false
    )

    sealed interface UiEffect {
        data class ShowEditPopup(val item: NoteItem) : UiEffect
        object ShowAddPopup : UiEffect
        object ImportDone : UiEffect
        object ExportDone : UiEffect
    }

    private val closeableInfoIds = arrayOf(CloseableInfoHolder.item_notes_sync)

    private val _uiState = MutableStateFlow(UiState(sortMode = readSortMode(preferences)))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    init {
        scope.launch {
            combine(
                    notesRepository.observeItems()
                            .catch { e -> errorHandler.handle(e) },
                    notesRepository.observeFolders()
                            .catch { e -> errorHandler.handle(e) },
                    closeableInfoHolder.observe()
                            .catch { e -> errorHandler.handle(e) }
            ) { notes, folders, allInfos ->
                val filteredInfos = allInfos.filter {
                    closeableInfoIds.contains(it.id) && !it.isClosed
                }
                Triple(notes, folders, filteredInfos)
            }.collect { (notes, folders, infos) ->
                _uiState.update { it.copy(items = notes, folders = folders, info = infos) }
            }
        }
        loadNotes()
        loadFolders()
    }

    private var loadJob: Job? = null

    fun loadNotes() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _uiState.update { it.copy(refreshing = true) }
            val state = _uiState.value
            runCatching { notesRepository.loadNotes(state.selectedFolderId, state.includeAllFolders, state.sortMode) }
                    .onSuccess { list ->
                        _uiState.update { it.copy(items = list) }
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    fun loadFolders() {
        scope.launch {
            runCatching { notesRepository.loadFolders() }
                    .onSuccess { folders ->
                        _uiState.update { it.copy(folders = folders) }
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
        }
    }

    fun deleteNote(id: Long) {
        scope.launch {
            runCatching { notesRepository.deleteNote(id) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun deleteNotes(ids: List<Long>) {
        scope.launch {
            runCatching { notesRepository.deleteNotes(ids) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun selectAllFolders() {
        _uiState.update { it.copy(includeAllFolders = true, selectedFolderId = null, searchQuery = "") }
        loadNotes()
    }

    fun selectFolder(folderId: Long?) {
        _uiState.update { state ->
            state.copy(
                    includeAllFolders = false,
                    selectedFolderId = folderId,
                    expandedFolderIds = folderId?.let { state.expandedFolderIds + it } ?: state.expandedFolderIds,
                    searchQuery = ""
            )
        }
        loadNotes()
    }

    fun toggleFolder(folderId: Long) {
        _uiState.update { state ->
            val expanded = state.expandedFolderIds
            state.copy(
                    expandedFolderIds = if (expanded.contains(folderId)) {
                        expanded - folderId
                    } else {
                        expanded + folderId
                    }
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        setSearchQuery("")
    }

    fun setSortMode(sortMode: NoteSortMode) {
        preferences.edit().putString(PREF_SORT_MODE, sortMode.name).apply()
        _uiState.update { it.copy(sortMode = sortMode) }
        loadNotes()
    }

    fun createFolder(name: String, onCreated: ((NoteFolder) -> Unit)? = null) {
        scope.launch {
            runCatching { notesRepository.createFolder(name) }
                    .onSuccess { folder -> onCreated?.invoke(folder) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun renameFolder(id: Long, name: String) {
        scope.launch {
            runCatching { notesRepository.renameFolder(id, name) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun deleteFolder(id: Long) {
        scope.launch {
            runCatching { notesRepository.deleteFolder(id) }
                    .onSuccess {
                        if (_uiState.value.selectedFolderId == id) {
                            _uiState.update { state -> state.copy(includeAllFolders = true, selectedFolderId = null) }
                            loadNotes()
                        }
                    }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun moveNoteToFolder(noteId: Long, folderId: Long?) {
        scope.launch {
            runCatching { notesRepository.moveNoteToFolder(noteId, folderId) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun moveNotesToFolder(noteIds: List<Long>, folderId: Long?) {
        scope.launch {
            runCatching { notesRepository.moveNotesToFolder(noteIds, folderId) }
                    .onFailure { e -> errorHandler.handle(e) }
        }
    }

    fun importNotes(file: RequestFile) {
        scope.launch {
            runCatching { notesRepository.importNotes(file) }
                    .onSuccess {
                        _effects.emit(UiEffect.ImportDone)
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
        }
    }

    fun createExportFileName(): String = notesRepository.createExportFileName()

    fun exportNotes(outputStream: OutputStream) {
        scope.launch {
            runCatching { notesRepository.exportNotes(outputStream) }
                    .onSuccess {
                        _effects.emit(UiEffect.ExportDone)
                    }
                    .onFailure { e ->
                        errorHandler.handle(e)
                    }
        }
    }

    fun onItemClick(item: NoteItem) {
        // A bookmark points at an exact saved post; tag the open as an explicit-post navigation so the
        // topic resolver lands on that post instead of downgrading `p=`/`pid=` to a last-read/unread jump.
        linkHandler.handle(
                item.link,
                router,
                mapOf(
                        Screen.Theme.ARG_TOPIC_OPEN_SOURCE to TOPIC_OPEN_SOURCE_BOOKMARK,
                        Screen.Theme.ARG_TOPIC_OPEN_INTENT to TopicOpenIntentClassifier.EXPLICIT_POST
                )
        )
    }

    fun onInfoClick(info: CloseableInfo) {
        closeableInfoHolder.close(info)
    }

    fun copyLink(item: NoteItem) {
        clipboardHelper.copyToClipboard(item.link)
    }

    fun editNote(item: NoteItem) {
        scope.launch {
            _effects.emit(UiEffect.ShowEditPopup(item))
        }
    }

    fun addNote() {
        scope.launch {
            _effects.emit(UiEffect.ShowAddPopup)
        }
    }

    companion object {
        private const val PREF_SORT_MODE = "notes.sort_mode"
        private const val TOPIC_OPEN_SOURCE_BOOKMARK = "bookmark"

        private fun readSortMode(preferences: SharedPreferences): NoteSortMode {
            val value = preferences.getString(PREF_SORT_MODE, null)
            return runCatching { value?.let(NoteSortMode::valueOf) }.getOrNull() ?: NoteSortMode.CREATED_DESC
        }
    }

}
