package forpdateam.ru.forpda.presentation.devdb.search

import android.content.Context
import forpdateam.ru.forpda.presentation.BaseViewModel
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter

@HiltViewModel
class DevDbSearchViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var searchJob: Job? = null

    var searchQuery: String? = null
    private val _currentData = MutableStateFlow<Brand?>(null)
    val currentData: StateFlow<Brand?> = _currentData.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<DevDbSearchUiEvent>()
    val uiEvents: SharedFlow<DevDbSearchUiEvent> = _uiEvents.asSharedFlow()

    fun start() {
        // no-op: search triggered explicitly
    }

    fun refresh() = search(searchQuery)

    fun search(query: String?) {
        searchQuery = query
        if (searchQuery.isNullOrEmpty()) {
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            _refreshing.value = true
            try {
                val result = devDbRepository.search(searchQuery.orEmpty())
                _currentData.value = result
                _uiEvents.emit(DevDbSearchUiEvent.ShowData(result, searchQuery.orEmpty()))
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun openDevice(item: Brand.DeviceItem) {
        _currentData.value?.let {
            router.navigateTo(Screen.DevDbDevice().apply {
                deviceId = item.id
            })
        }
    }

    fun openSearch() {
        router.navigateTo(Screen.DevDbSearch())
    }

    fun copyLink(item: Brand.DeviceItem) {
        _currentData.value?.let {
            clipboardHelper.copyToClipboard("https://4pda.to/devdb/${item.id}")
        }
    }

    fun shareLink(item: Brand.DeviceItem) {
        _currentData.value?.let {
            Utils.shareText(context, "https://4pda.to/devdb/${item.id}")
        }
    }

    fun createNote(item: Brand.DeviceItem) {
        _currentData.value?.let {
            val title = "DevDb: ${it.title} ${item.title}"
            val url = "https://4pda.to/devdb/" + item.id
            scope.launch { _uiEvents.emit(DevDbSearchUiEvent.ShowCreateNote(title, url)) }
        }
    }
}

sealed class DevDbSearchUiEvent {
    data class ShowData(val brand: Brand, val query: String) : DevDbSearchUiEvent()
    data class ShowCreateNote(val title: String, val url: String) : DevDbSearchUiEvent()
}
