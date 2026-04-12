package forpdateam.ru.forpda.presentation.devdb.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable

class DeviceViewModel(
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var deviceView: DeviceView? = null

    fun attachView(view: DeviceView) {
        deviceView = view
    }

    fun detachView() {
        deviceView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var deviceId: String? = null
    var currentData: Device? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadBrand()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun loadBrand() {
        devDbRepository
                .getDevice(deviceId.orEmpty())
                .doOnSubscribe { deviceView?.setRefreshing(true) }
                .doAfterTerminate { deviceView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    deviceView?.showData(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun openSearch() {
        router.navigateTo(Screen.DevDbSearch())
    }

    fun copyLink() {
        currentData?.let {
            Utils.copyToClipBoard("https://4pda.to/index.php?p=${it.id}")
        }
    }

    fun shareLink() {
        currentData?.let {
            Utils.shareText("https://4pda.to/devdb/${it.id}")
        }
    }

    fun createNote() {
        currentData?.let {
            val title = "DevDb: ${it.brandTitle} ${it.title}"
            val url = "https://4pda.to/devdb/${it.id}"
            deviceView?.showCreateNote(title, url)
        }
    }

    fun openDevices() {
        currentData?.let {
            linkHandler.handle("https://4pda.to/devdb/${it.catId}/${it.brandId}", router)
        }
    }

    fun openBrands() {
        currentData?.let {
            linkHandler.handle("https://4pda.to/devdb/${it.catId}", router)
        }
    }

    class Factory(
            private val devDbRepository: DevDbRepository,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != DeviceViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return DeviceViewModel(devDbRepository, router, linkHandler, errorHandler) as T
        }
    }
}
