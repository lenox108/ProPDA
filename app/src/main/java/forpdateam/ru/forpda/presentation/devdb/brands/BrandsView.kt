package forpdateam.ru.forpda.presentation.devdb.brands

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.devdb.Brands

/**
 * Created by radiationx on 01.01.18.
 */
interface BrandsView : IBaseView {
    fun showData(data: Brands)
    fun initCategories(categories: Array<String>, position: Int)
}
