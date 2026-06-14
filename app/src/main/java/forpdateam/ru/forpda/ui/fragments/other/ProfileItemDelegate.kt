package forpdateam.ru.forpda.ui.fragments.other

import timber.log.Timber
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.databinding.ItemOtherProfileBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.common.applyForumAvatarShape
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ProfileListItem

class ProfileItemDelegate(
        private val clickListener: (ProfileModel?) -> Unit,
        private val topicPreferencesHolder: TopicPreferencesHolder
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is ProfileListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as ProfileListItem
        (holder as ViewHolder).bind(item.profileItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener, topicPreferencesHolder)
    }

    class ViewHolder(
            private val binding: ItemOtherProfileBinding,
            private val clickListener: (ProfileModel?) -> Unit,
            private val topicPreferencesHolder: TopicPreferencesHolder
    ) : RecyclerView.ViewHolder(binding.root) {

        private var item: ProfileModel? = null

        init {
            binding.root.setOnClickListener { clickListener(item) }
        }

        fun bind(profileItem: ProfileModel?) {
            item = profileItem
            Timber.e("bind prfile %s", profileItem)
            binding.profileAvatar.applyForumAvatarShape(topicPreferencesHolder.getCircleAvatars())
            val imageUrl = profileItem?.avatar ?: "assets://av.png"
            ForPdaCoil.loadInto(binding.profileAvatar, imageUrl)

            if (profileItem != null) {
                binding.profileNick.text = profileItem.nick
                binding.profileDesc.text = "Перейти в профиль"
            } else {
                binding.profileNick.text = "Гость"
                binding.profileDesc.text = "Авторизоваться"
            }
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                leftMargin = binding.root.dp16
                rightMargin = binding.root.dp16
                topMargin = binding.root.dp12
                bottomMargin = binding.root.dp8
            }
        }
    }
}
