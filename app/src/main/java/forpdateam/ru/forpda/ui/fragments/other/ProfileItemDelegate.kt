package forpdateam.ru.forpda.ui.fragments.other

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates3.AdapterDelegate
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.databinding.ItemOtherProfileBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ProfileListItem

class ProfileItemDelegate(
        private val clickListener: (ProfileModel?) -> Unit,
        private val logoutClickListener: () -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is ProfileListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as ProfileListItem
        (holder as ViewHolder).bind(item.profileItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener, logoutClickListener)
    }

    class ViewHolder(
            private val binding: ItemOtherProfileBinding,
            private val clickListener: (ProfileModel?) -> Unit,
            private val logoutClickListener: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var item: ProfileModel? = null

        init {
            binding.root.setOnClickListener { clickListener(item) }
            binding.profileLogout.setOnClickListener { logoutClickListener() }
        }

        fun bind(profileItem: ProfileModel?) {
            item = profileItem
            Log.e("S_DEF_LOG", "bind prfile " + profileItem)
            val imageUrl = profileItem?.avatar ?: "assets://av.png"
            ForPdaCoil.loadInto(binding.profileAvatar, imageUrl)

            if (profileItem != null) {
                binding.profileNick.text = profileItem.nick
                binding.profileDesc.text = "Перейти в профиль"
            } else {
                binding.profileNick.text = "Гость"
                binding.profileDesc.text = "Авторизоваться"
            }
        }
    }
}
