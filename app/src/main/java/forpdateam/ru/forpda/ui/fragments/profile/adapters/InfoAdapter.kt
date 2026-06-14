package forpdateam.ru.forpda.ui.fragments.profile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import forpdateam.ru.forpda.databinding.ProfileSubItemInfoBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.repository.temp.TempHelper
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class InfoAdapter : BaseAdapter<ProfileModel.Info, InfoAdapter.InfoHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoHolder {
        val binding = ProfileSubItemInfoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return InfoHolder(binding)
    }

    override fun onBindViewHolder(holder: InfoHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InfoHolder(
        private val binding: ProfileSubItemInfoBinding
    ) : BaseViewHolder<ProfileModel.Info>(binding.root) {

        override fun bind(item: ProfileModel.Info) {
            binding.itemTitle.text = item.type?.let { binding.root.context.getString(TempHelper.getTypeString(it)) } ?: ""
            binding.itemValue.text = item.value
        }
    }
}
