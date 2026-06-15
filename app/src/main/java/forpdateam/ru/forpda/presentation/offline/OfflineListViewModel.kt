package forpdateam.ru.forpda.presentation.offline

import forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
import forpdateam.ru.forpda.model.data.offline.OfflineRepository
import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 3 ViewModel for the offline-reading list screen (§5.1 of
 * REFACTOR_PLAN.md).
 *
 * Exposes:
 *  - a [StateFlow] of the saved-item list and total bytes;
 *  - a [SharedFlow] of one-shot events for item-open navigation.
 *
 * Phase 3 keeps the surface area intentionally small — no FAB
 * triggers, no progress UI, no "open all" / "delete all" actions.
 * Those land with the Compose fragment host in a follow-up commit.
 */
@HiltViewModel
class OfflineListViewModel @Inject constructor(
        private val repository: OfflineRepository,
) : BaseViewModel() {

    data class UiState(
            val items: List<OfflineItemRoom> = emptyList(),
            val totalBytes: Long = 0L,
    )

    sealed class Event {
        data class OpenItem(val id: String) : Event()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        scope.launch {
            repository.observeAll().collect { items ->
                _uiState.update { it.copy(items = items, totalBytes = items.sumOf { row -> row.sizeBytes }) }
            }
        }
    }

    fun onItemClick(item: OfflineItemRoom) {
        _events.tryEmit(Event.OpenItem(item.id))
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }
}
