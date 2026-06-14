package forpdateam.ru.forpda.presentation.devdb.devices

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
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter

@HiltViewModel
class DevicesViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var loadJob: Job? = null
    private var subscriptionsStarted = false

    var categoryId: String? = null
    var brandId: String? = null
    private val _currentData = MutableStateFlow<Brand?>(null)
    val currentData: StateFlow<Brand?> = _currentData.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<DevicesUiEvent>()
    val uiEvents: SharedFlow<DevicesUiEvent> = _uiEvents.asSharedFlow()

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
                val brand = devDbRepository.getBrand(categoryId.orEmpty(), brandId.orEmpty())
                _currentData.value = brand
                _uiEvents.emit(DevicesUiEvent.ShowData(brand))
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
            scope.launch { _uiEvents.emit(DevicesUiEvent.ShowCreateNote(title, url)) }
        }
    }
}

sealed class DevicesUiEvent {
    data class ShowData(val brand: Brand) : DevicesUiEvent()
    data class ShowCreateNote(val title: String, val url: String) : DevicesUiEvent()
}
