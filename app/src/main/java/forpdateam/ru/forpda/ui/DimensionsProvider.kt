package forpdateam.ru.forpda.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
/**
 * Created by radiationx on 09.01.18.
 */
class DimensionsProvider {
    private val _dimensions = MutableStateFlow(DimensionHelper.Dimensions())
    val dimensionsFlow: StateFlow<DimensionHelper.Dimensions> = _dimensions.asStateFlow()

    fun getDimensions(): DimensionHelper.Dimensions = _dimensions.value
    fun update(dimensions: DimensionHelper.Dimensions) {
        _dimensions.value = dimensions
    }
}