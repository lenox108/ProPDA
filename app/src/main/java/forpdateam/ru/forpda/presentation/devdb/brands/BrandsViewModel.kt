package forpdateam.ru.forpda.presentation.devdb.brands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.entity.remote.devdb.Brands
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable

class BrandsViewModel(
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var brandsView: BrandsView? = null

    fun attachView(view: BrandsView) {
        brandsView = view
    }

    fun detachView() {
        brandsView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    companion object {
        const val CATEGORY_PHONES = "phones"
        const val CATEGORY_PAD = "pad"
        const val CATEGORY_EBOOK = "ebook"
        const val CATEGORY_SMARTWATCH = "smartwatch"
    }

    private val categories = arrayOf(
            CATEGORY_PHONES,
            CATEGORY_PAD,
            CATEGORY_EBOOK,
            CATEGORY_SMARTWATCH
    )
    private var currentCategory = categories[0]
    private var currentData: Brands? = null

    fun initCategory(categoryId: String) {
        categories.firstOrNull { it == categoryId }?.let {
            currentCategory = it
        }
    }

    fun selectCategory(position: Int) {
        currentCategory = categories[position]
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        brandsView?.initCategories(categories, categories.indexOf(currentCategory))
        loadBrands()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun loadBrands() {
        devDbRepository
                .getBrands(currentCategory)
                .doOnSubscribe { brandsView?.setRefreshing(true) }
                .doAfterTerminate { brandsView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    brandsView?.showData(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun openBrand(item: Brands.Item) {
        currentData?.let {
            router.navigateTo(Screen.DevDbDevices().apply {
                categoryId = it.catId
                brandId = item.id
            })
        }
    }

    fun openSearch() {
        router.navigateTo(Screen.DevDbSearch())
    }

    class Factory(
            private val devDbRepository: DevDbRepository,
            private val router: TabRouter,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != BrandsViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return BrandsViewModel(devDbRepository, router, errorHandler) as T
        }
    }
}
