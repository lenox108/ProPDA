package forpdateam.ru.forpda.presentation.devdb.devices

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.devdb.Brand

/**
 * Created by radiationx on 01.01.18.
 */
interface DevicesView : IBaseView {
    fun showData(data: Brand)

    fun showCreateNote(title: String, url: String)
}
