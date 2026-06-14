package forpdateam.ru.forpda.ui.fragments.profile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.ProfileSubItemContactBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.repository.temp.TempHelper
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class ContactsAdapter(
    private val listener: InfoHolder.Listener
) : BaseAdapter<ProfileModel.Contact, ContactsAdapter.InfoHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoHolder {
        val binding = ProfileSubItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return InfoHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: InfoHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class InfoHolder(
        private val binding: ProfileSubItemContactBinding,
        private val listener: Listener
    ) : BaseViewHolder<ProfileModel.Contact>(binding.root) {

        private var currentItem: ProfileModel.Contact? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let { item -> listener.onClick(item) }
            }
        }

        override fun bind(item: ProfileModel.Contact) {
            currentItem = item
            binding.itemIcon.setImageDrawable(
                binding.itemIcon.context.getVecDrawable(TempHelper.getContactIcon(item.type))
            )
            binding.itemIcon.contentDescription = item.title
        }

        interface Listener {
            fun onClick(item: ProfileModel.Contact)
        }
    }
}
