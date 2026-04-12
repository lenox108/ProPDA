package forpdateam.ru.forpda.ui.fragments.devdb.device

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.presentation.devdb.device.SubDeviceViewModel

/**
 * Created by radiationx on 09.08.17.
 */

open class SubDeviceFragment : Fragment() {
    protected lateinit var device: Device

    protected val presenter: SubDeviceViewModel by viewModels {
        SubDeviceViewModel.Factory(
                App.get().Di().router,
                App.get().Di().linkHandler
        )
    }

    fun setDevice(device: Device): SubDeviceFragment {
        this.device = device
        return this
    }
}
