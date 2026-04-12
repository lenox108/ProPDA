package forpdateam.ru.forpda.presentation.devdb.search

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.devdb.Brand

/**
 * Created by radiationx on 01.01.18.
 */
interface SearchDevicesView : IBaseView {
    fun showData(data: Brand, query: String)

    fun showCreateNote(title: String, url: String)
}
