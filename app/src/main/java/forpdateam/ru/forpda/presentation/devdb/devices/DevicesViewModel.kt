package forpdateam.ru.forpda.presentation.devdb.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable

class DevicesViewModel(
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var devicesView: DevicesView? = null

    fun attachView(view: DevicesView) {
        devicesView = view
    }

    fun detachView() {
        devicesView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var categoryId: String? = null
    var brandId: String? = null
    var currentData: Brand? = null

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
                .getBrand(categoryId.orEmpty(), brandId.orEmpty())
                .doOnSubscribe { devicesView?.setRefreshing(true) }
                .doAfterTerminate { devicesView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    devicesView?.showData(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun openDevice(item: Brand.DeviceItem) {
        currentData?.let {
            router.navigateTo(Screen.DevDbDevice().apply {
                deviceId = item.id
            })
        }
    }

    fun openSearch() {
        router.navigateTo(Screen.DevDbSearch())
    }

    fun copyLink(item: Brand.DeviceItem) {
        currentData?.let {
            Utils.copyToClipBoard("https://4pda.to/devdb/${item.id}")
        }
    }

    fun shareLink(item: Brand.DeviceItem) {
        currentData?.let {
            Utils.shareText("https://4pda.to/devdb/${item.id}")
        }
    }

    fun createNote(item: Brand.DeviceItem) {
        currentData?.let {
            val title = "DevDb: ${it.title} ${item.title}"
            val url = "https://4pda.to/devdb/" + item.id
            devicesView?.showCreateNote(title, url)
        }
    }

    class Factory(
            private val devDbRepository: DevDbRepository,
            private val router: TabRouter,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != DevicesViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return DevicesViewModel(devDbRepository, router, errorHandler) as T
        }
    }
}
