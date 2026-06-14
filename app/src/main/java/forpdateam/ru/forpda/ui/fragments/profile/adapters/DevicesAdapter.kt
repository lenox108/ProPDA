package forpdateam.ru.forpda.ui.fragments.profile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import forpdateam.ru.forpda.databinding.ProfileSubItemDeviceBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class DevicesAdapter(
    private val listener: InfoHolder.Listener
) : BaseAdapter<ProfileModel.Device, DevicesAdapter.InfoHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoHolder {
        val binding = ProfileSubItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return InfoHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: InfoHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class InfoHolder(
        private val binding: ProfileSubItemDeviceBinding,
        private val listener: Listener
    ) : BaseViewHolder<ProfileModel.Device>(binding.root) {

        private var currentItem: ProfileModel.Device? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let { item -> listener.onClick(item) }
            }
        }

        override fun bind(item: ProfileModel.Device) {
            currentItem = item
            binding.itemTitle.text = "${item.name} ${item.accessory}"
        }

        interface Listener {
            fun onClick(item: ProfileModel.Device)
        }
    }
}
