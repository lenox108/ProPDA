package forpdateam.ru.forpda.presentation.devdb.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable

class DevDbSearchViewModel(
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var searchDevicesView: SearchDevicesView? = null

    fun attachView(view: SearchDevicesView) {
        searchDevicesView = view
    }

    fun detachView() {
        searchDevicesView = null
    }

    private val rxSubscriptions = CompositeDisposable()

    var searchQuery: String? = null
    var currentData: Brand? = null

    fun start() {
        // no-op: search triggered explicitly
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun refresh() = search(searchQuery)

    fun search(query: String?) {
        searchQuery = query
        if (searchQuery.isNullOrEmpty()) {
            return
        }
        devDbRepository
                .search(searchQuery.orEmpty())
                .doOnSubscribe { searchDevicesView?.setRefreshing(true) }
                .doAfterTerminate { searchDevicesView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    searchDevicesView?.showData(it, searchQuery.orEmpty())
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
            searchDevicesView?.showCreateNote(title, url)
        }
    }

    class Factory(
            private val devDbRepository: DevDbRepository,
            private val router: TabRouter,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != DevDbSearchViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return DevDbSearchViewModel(devDbRepository, router, errorHandler) as T
        }
    }
}
