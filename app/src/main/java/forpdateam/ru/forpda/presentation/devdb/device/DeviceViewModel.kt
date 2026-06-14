package forpdateam.ru.forpda.presentation.devdb.device

import android.content.Context
import forpdateam.ru.forpda.presentation.BaseViewModel
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
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter

@HiltViewModel
class DeviceViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var loadJob: Job? = null
    private var subscriptionsStarted = false

    var deviceId: String? = null
    private val _currentData = MutableStateFlow<Device?>(null)
    val currentData: StateFlow<Device?> = _currentData.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<DeviceUiEvent>()
    val uiEvents: SharedFlow<DeviceUiEvent> = _uiEvents.asSharedFlow()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadBrand()
    }

    fun loadBrand() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val device = devDbRepository.getDevice(deviceId.orEmpty())
                _currentData.value = device
                _uiEvents.emit(DeviceUiEvent.ShowData(device))
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun openSearch() {
        router.navigateTo(Screen.DevDbSearch())
    }

    fun copyLink() {
        _currentData.value?.let {
            clipboardHelper.copyToClipboard("https://4pda.to/index.php?p=${it.id}")
        }
    }

    fun shareLink() {
        _currentData.value?.let {
            Utils.shareText(context, "https://4pda.to/devdb/${it.id}")
        }
    }

    fun createNote() {
        _currentData.value?.let {
            val title = "DevDb: ${it.brandTitle} ${it.title}"
            val url = "https://4pda.to/devdb/${it.id}"
            scope.launch { _uiEvents.emit(DeviceUiEvent.ShowCreateNote(title, url)) }
        }
    }

    fun openDevices() {
        _currentData.value?.let {
            linkHandler.handle("https://4pda.to/devdb/${it.catId}/${it.brandId}", router)
        }
    }

    fun openBrands() {
        _currentData.value?.let {
            linkHandler.handle("https://4pda.to/devdb/${it.catId}", router)
        }
    }
}

sealed class DeviceUiEvent {
    data class ShowData(val device: Device) : DeviceUiEvent()
    data class ShowCreateNote(val title: String, val url: String) : DeviceUiEvent()
}
