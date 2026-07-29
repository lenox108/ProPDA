package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
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
import java.text.NumberFormat
import java.util.Locale

class ProfileItemDelegate(
        private val clickListener: (ProfileModel?) -> Unit,
        private val editClickListener: () -> Unit,
        private val topicPreferencesHolder: TopicPreferencesHolder
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is ProfileListItem

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as ProfileListItem
        (holder as ViewHolder).bind(item.profileItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener, editClickListener, topicPreferencesHolder)
    }

    class ViewHolder(
            private val binding: ItemOtherProfileBinding,
            private val clickListener: (ProfileModel?) -> Unit,
            private val editClickListener: () -> Unit,
            private val topicPreferencesHolder: TopicPreferencesHolder
    ) : RecyclerView.ViewHolder(binding.root) {

        private var item: ProfileModel? = null

        init {
            binding.root.setOnClickListener { clickListener(item) }
            binding.profileEnd.setOnClickListener {
                // У гостя карандаша нет: кнопка ведёт туда же, куда и вся строка — на авторизацию.
                if (item == null) clickListener(null) else editClickListener()
            }
        }

        fun bind(profileItem: ProfileModel?) {
            item = profileItem
            binding.profileAvatar.applyForumAvatarShape(topicPreferencesHolder.getCircleAvatars())
            val imageUrl = profileItem?.avatar ?: "assets://av.png"
            ForPdaCoil.loadInto(binding.profileAvatar, imageUrl)

            if (profileItem != null) {
                binding.profileNick.text = profileItem.nick
                bindGroup(profileItem)
                bindStats(profileItem)
                binding.profileEnd.setImageResource(R.drawable.ic_profile_toolbar_create)
                binding.profileEnd.contentDescription =
                        binding.root.context.getString(R.string.other_menu_edit_layout)
            } else {
                binding.profileNick.setText(R.string.other_menu_profile_guest)
                binding.profileGroup.visibility = View.GONE
                binding.profileStats.visibility = View.GONE
                binding.profileDesc.visibility = View.VISIBLE
                binding.profileDesc.setText(R.string.other_menu_profile_guest_action)
                binding.profileEnd.setImageResource(R.drawable.ic_arrow_right)
                binding.profileEnd.contentDescription = null
            }
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                leftMargin = binding.root.dp16
                rightMargin = binding.root.dp16
                topMargin = binding.root.dp12
                bottomMargin = binding.root.dp8
            }
        }

        private fun bindGroup(profileItem: ProfileModel) {
            val group = profileItem.group?.trim().orEmpty()
            binding.profileGroup.visibility = if (group.isEmpty()) View.GONE else View.VISIBLE
            binding.profileGroup.text = group
        }

        /**
         * Репутация и число сообщений приезжают со страницы профиля ([ProfileModel.stats]).
         * Если их нет — офлайн, профиль ещё не загрузился или парсер не нашёл полей — строка
         * не пустеет, а откатывается к обычной подписи.
         */
        private fun bindStats(profileItem: ProfileModel) {
            val reputation = statValue(profileItem, ProfileModel.StatType.FORUM_REPUTATION)
            val posts = statValue(profileItem, ProfileModel.StatType.FORUM_POSTS)
            if (reputation == null && posts == null) {
                binding.profileStats.visibility = View.GONE
                binding.profileDesc.visibility = View.VISIBLE
                binding.profileDesc.setText(R.string.other_menu_profile_open)
                return
            }
            // profileDesc остаётся невидимым, но занимает место: к нему привязан ряд метрик.
            binding.profileDesc.visibility = View.INVISIBLE
            binding.profileStats.visibility = View.VISIBLE
            // Одинокая метрика не должна тащить за собой разделитель и чужую иконку.
            binding.profileStatsSeparator.visibility =
                    if (reputation == null || posts == null) View.GONE else View.VISIBLE
            binding.profileReputation.visibility = if (reputation == null) View.GONE else View.VISIBLE
            binding.profilePostsIcon.visibility = if (posts == null) View.GONE else View.VISIBLE
            binding.profilePosts.visibility = if (posts == null) View.GONE else View.VISIBLE
            binding.profileReputation.text = reputation.orEmpty()
            binding.profilePosts.text = posts.orEmpty()
        }

        private fun statValue(profileItem: ProfileModel, type: ProfileModel.StatType): String? {
            val raw = profileItem.stats.firstOrNull { it.type == type }?.value?.trim().orEmpty()
            if (raw.isEmpty()) return null
            // «18703» → «18 703»; нечисловое значение («—», «+12») оставляем как есть.
            val digits = raw.replace(" ", "").replace(" ", "")
            val number = digits.toLongOrNull() ?: return raw
            return NUMBER_FORMAT.format(number)
        }

        private companion object {
            val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale("ru")).apply {
                isGroupingUsed = true
            }
        }
    }
}
