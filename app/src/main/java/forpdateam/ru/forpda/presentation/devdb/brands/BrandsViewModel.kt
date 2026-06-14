package forpdateam.ru.forpda.presentation.devdb.brands

import forpdateam.ru.forpda.presentation.BaseViewModel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.entity.remote.devdb.Brands
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter

@HiltViewModel
class BrandsViewModel @Inject constructor(
        private val devDbRepository: DevDbRepository,
        private val router: TabRouter,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    private var loadJob: Job? = null
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

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<BrandsUiEvent>(replay = 2)
    val uiEvents: SharedFlow<BrandsUiEvent> = _uiEvents.asSharedFlow()

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
        scope.launch { _uiEvents.emit(BrandsUiEvent.InitCategories(categories, categories.indexOf(currentCategory))) }
        loadBrands()
    }

    fun loadBrands() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val brands = devDbRepository.getBrands(currentCategory)
                currentData = brands
                _uiEvents.emit(BrandsUiEvent.ShowData(brands))
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
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
}

sealed class BrandsUiEvent {
    data class InitCategories(val categories: Array<String>, val position: Int) : BrandsUiEvent()
    data class ShowData(val brands: Brands) : BrandsUiEvent()
}
