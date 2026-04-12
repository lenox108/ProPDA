package forpdateam.ru.forpda.presentation.devdb.device

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.devdb.Device

/**
 * Created by radiationx on 01.01.18.
 */
interface DeviceView : IBaseView {
    fun showData(data: Device)

    fun showCreateNote(title: String, url: String)
}
