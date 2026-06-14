package forpdateam.ru.forpda.ui.fragments.devdb.device

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.presentation.devdb.device.SubDeviceViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 09.08.17.
 */

@AndroidEntryPoint
open class SubDeviceFragment : Fragment() {
    protected lateinit var device: Device

    protected val presenter: SubDeviceViewModel by viewModels()

    fun setDevice(device: Device): SubDeviceFragment {
        this.device = device
        return this
    }
}
