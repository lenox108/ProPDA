package forpdateam.ru.forpda.ui.fragments.profile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ProfileSubItemWarningBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class WarningsAdapter : BaseAdapter<ProfileModel.Warning, WarningsAdapter.WarningHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WarningHolder {
        val binding = ProfileSubItemWarningBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WarningHolder(binding)
    }

    override fun onBindViewHolder(holder: WarningHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WarningHolder(
        private val binding: ProfileSubItemWarningBinding
    ) : BaseViewHolder<ProfileModel.Warning>(binding.root) {

        override fun bind(item: ProfileModel.Warning) {
            binding.itemTitle.text = item.title
            binding.itemDate.text = item.date
            binding.itemContent.text = item.content
            when (item.type) {
                ProfileModel.WarningType.POSITIVE -> {
                    binding.itemTitle.setTextColor(
                        ContextCompat.getColor(binding.itemTitle.context, R.color.md_green_400)
                    )
                }
                ProfileModel.WarningType.NEGATIVE -> {
                    binding.itemTitle.setTextColor(
                        ContextCompat.getColor(binding.itemTitle.context, R.color.md_red_400)
                    )
                }
                else -> { /* no color change for other types */ }
            }
        }
    }
}
