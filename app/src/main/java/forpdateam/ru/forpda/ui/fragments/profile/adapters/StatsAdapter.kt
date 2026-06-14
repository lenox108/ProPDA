package forpdateam.ru.forpda.ui.fragments.profile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import forpdateam.ru.forpda.databinding.ProfileSubItemStatBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.repository.temp.TempHelper
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class StatsAdapter(
    private val listener: StatHolder.Listener
) : BaseAdapter<ProfileModel.Stat, StatsAdapter.StatHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatHolder {
        val binding = ProfileSubItemStatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StatHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: StatHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StatHolder(
        private val binding: ProfileSubItemStatBinding,
        private val listener: Listener
    ) : BaseViewHolder<ProfileModel.Stat>(binding.root) {

        private var currentItem: ProfileModel.Stat? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let { item -> listener.onClick(item) }
            }
        }

        override fun bind(item: ProfileModel.Stat) {
            currentItem = item
            item.type?.let { binding.itemTitle.setText(TempHelper.getTypeString(it)) }
            binding.itemValue.text = item.value
        }

        interface Listener {
            fun onClick(item: ProfileModel.Stat)
        }
    }
}
