package forpdateam.ru.forpda.ui.fragments.profile.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.LinkMovementMethod
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.ProfileItemAboutBinding
import forpdateam.ru.forpda.databinding.ProfileItemListBinding
import forpdateam.ru.forpda.databinding.ProfileItemNoteBinding
import forpdateam.ru.forpda.databinding.ProfileItemStatsBinding
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.views.DividerItemDecoration
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder
import java.util.Collections

class ProfileAdapter(private val linkHandler: ILinkHandler) : RecyclerView.Adapter<BaseViewHolder<*>>() {

    companion object {
        private const val STATS_VIEW_TYPE = 1
        private const val ABOUT_VIEW_TYPE = 2
        private const val INFO_VIEW_TYPE = 3
        private const val DEVICES_VIEW_TYPE = 4
        private const val CONTACTS_VIEW_TYPE = 5
        private const val NOTE_VIEW_TYPE = 6
        private const val WARNING_VIEW_TYPE = 7
    }

    private val items = ArrayList<Int>()
    private var profileModel: ProfileModel? = null
    private var clickListener: ClickListener? = null

    fun setClickListener(listener: ClickListener) {
        this.clickListener = listener
    }

    fun setProfile(profile: ProfileModel) {
        items.add(STATS_VIEW_TYPE)
        if (profile.about != null) {
            items.add(ABOUT_VIEW_TYPE)
        }
        items.add(INFO_VIEW_TYPE)
        if (profile.devices.isNotEmpty()) {
            items.add(DEVICES_VIEW_TYPE)
        }
        if (profile.contacts.size > 1) {
            items.add(CONTACTS_VIEW_TYPE)
        }
        if (profile.note != null) {
            items.add(NOTE_VIEW_TYPE)
        }
        if (profile.warnings.isNotEmpty()) {
            items.add(WARNING_VIEW_TYPE)
        }
        profileModel = profile
    }

    override fun getItemViewType(position: Int): Int = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            STATS_VIEW_TYPE -> {
                val binding = ProfileItemStatsBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                StatsHolder(binding)
            }
            ABOUT_VIEW_TYPE -> {
                val binding = ProfileItemAboutBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AboutHolder(binding)
            }
            INFO_VIEW_TYPE, DEVICES_VIEW_TYPE, CONTACTS_VIEW_TYPE, WARNING_VIEW_TYPE -> {
                val binding = ProfileItemListBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                when (viewType) {
                    INFO_VIEW_TYPE -> InfosHolder(binding)
                    DEVICES_VIEW_TYPE -> DevicesHolder(binding)
                    CONTACTS_VIEW_TYPE -> ContactsHolder(binding)
                    WARNING_VIEW_TYPE -> WarningsHolder(binding)
                    else -> throw IllegalArgumentException("Unknown view type: $viewType")
                }
            }
            NOTE_VIEW_TYPE -> {
                val binding = ProfileItemNoteBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                NoteHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val profile = profileModel ?: return
        when (getItemViewType(position)) {
            STATS_VIEW_TYPE -> (holder as? StatsHolder)?.bind(profile)
            ABOUT_VIEW_TYPE -> (holder as? AboutHolder)?.bind(profile)
            INFO_VIEW_TYPE -> (holder as? InfosHolder)?.bind(profile)
            DEVICES_VIEW_TYPE -> (holder as? DevicesHolder)?.bind(profile)
            CONTACTS_VIEW_TYPE -> (holder as? ContactsHolder)?.bind(profile)
            NOTE_VIEW_TYPE -> (holder as? NoteHolder)?.bind(profile)
            WARNING_VIEW_TYPE -> (holder as? WarningsHolder)?.bind(profile)
        }
    }

    override fun getItemCount(): Int = items.size

    private inner class StatsHolder(binding: ProfileItemStatsBinding) : BaseViewHolder<ProfileModel>(binding.root) {
        private val list: RecyclerView = binding.profileStatsList
        private val adapter: StatsAdapter

        init {
            list.setHasFixedSize(false)
            list.layoutManager = LinearLayoutManager(list.context, LinearLayoutManager.HORIZONTAL, false)
            adapter = StatsAdapter(object : StatsAdapter.StatHolder.Listener {
                override fun onClick(item: ProfileModel.Stat) {
                    clickListener?.onStatClick(item)
                }
            })
            list.adapter = adapter
            list.isNestedScrollingEnabled = false
        }

        override fun bind(item: ProfileModel) {
            if (adapter.itemCount == 0) {
                val statsList = ArrayList(item.stats)
                Collections.reverse(statsList)
                adapter.addAll(statsList)
            }
        }
    }

    private inner class AboutHolder(private val binding: ProfileItemAboutBinding) : BaseViewHolder<ProfileModel>(binding.root) {
        private val linkHandler: ILinkHandler = this@ProfileAdapter.linkHandler

        override fun bind(item: ProfileModel) {
            val ctx = binding.profileAboutText.context
            val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            // Контраст считаем от фактической поверхности карточки (content_card_surface), а не от
            // colorSurface — на Material You в тёмной они расходятся.
            val surface = ctx.getColorFromAttr(forpdateam.ru.forpda.R.attr.content_card_surface)
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
            // «О себе» приходит с сервера с зашитым бледным цветом ссылки — почти невидим на светлой карточке
            // (жалоба пользователя) и тускл на тёмной. Прошлые попытки (setLinkTextColor / ForegroundColorSpan
            // поверх URLSpan) не срабатывали: у URLSpan свой updateDrawState красит текст в `linkColor` темы и
            // перебивает наложенный цвет при отрисовке. Радикальный фикс — заменяем каждый URLSpan на подкласс
            // [ColoredUrlSpan], который САМ в updateDrawState ставит гарантированно-контрастный цвет (ниже —
            // clickable через тот же LinkMovementMethod, т.к. это по-прежнему URLSpan с `.url`). Тело — onSurface.
            val linkColor = contrastSafeColor(accent, onSurface, surface)
            val about = item.about
            binding.profileAboutText.setTextColor(onSurface)
            binding.profileAboutText.text = if (about is android.text.Spanned) {
                android.text.SpannableStringBuilder(about).also { sb ->
                    // Снимаем любые серверные цвета текста (бледные <font color> из LEGACY-парсинга).
                    sb.getSpans(0, sb.length, android.text.style.ForegroundColorSpan::class.java)
                            .forEach { sb.removeSpan(it) }
                    // Заменяем URLSpan'ы на самокрасящиеся — цвет тогда контролирует span, а не тема.
                    sb.getSpans(0, sb.length, android.text.style.URLSpan::class.java).forEach { u ->
                        val s = sb.getSpanStart(u)
                        val e = sb.getSpanEnd(u)
                        val flags = sb.getSpanFlags(u)
                        sb.removeSpan(u)
                        if (s in 0 until e) {
                            sb.setSpan(forpdateam.ru.forpda.common.ColoredUrlSpan(u.url, linkColor), s, e, flags)
                        }
                    }
                }
            } else {
                about
            }
            binding.profileAboutText.setLinkTextColor(linkColor)
            binding.profileAboutText.movementMethod = LinkMovementMethod(object : LinkMovementMethod.ClickListener {
                override fun onClick(url: String): Boolean {
                    return linkHandler.handle(url, null)
                }
            })
        }

        /** Brighten/blend [base] toward [onSurface] until it reaches a comfortable contrast on [surface],
         *  so the link is readable on every palette (some accents are dim on their own card). */
        private fun contrastSafeColor(base: Int, onSurface: Int, surfaceRaw: Int): Int {
            // calculateContrast требует НЕпрозрачный фон — форсим alpha (палитра может
            // дать полупрозрачный surface → IllegalArgumentException «translucent»).
            val surface = surfaceRaw or 0xFF000000.toInt()
            val target = 4.5
            if (androidx.core.graphics.ColorUtils.calculateContrast(base, surface) >= target) return base
            var c = base
            repeat(12) {
                c = androidx.core.graphics.ColorUtils.blendARGB(c, onSurface, 0.18f)
                if (androidx.core.graphics.ColorUtils.calculateContrast(c, surface) >= target) return c
            }
            return c
        }
    }

    private inner class InfosHolder(binding: ProfileItemListBinding) : BaseViewHolder<ProfileModel>(binding.root) {
        private val list: RecyclerView = binding.profileSubList
        private val adapter: InfoAdapter

        init {
            list.setHasFixedSize(false)
            list.layoutManager = LinearLayoutManager(list.context)
            list.isNestedScrollingEnabled = false
            list.addItemDecoration(
                DevicesFragment.SpacingItemDecoration(
                    binding.root.context.resources.getDimensionPixelSize(R.dimen.dp16),
                    true
                )
            )
            adapter = InfoAdapter()
            list.adapter = adapter
            binding.profileSubTitle.setText(R.string.profile_title_information)
        }

        override fun bind(item: ProfileModel) {
            if (adapter.itemCount == 0) {
                adapter.addAll(item.info)
            }
        }
    }

    private inner class DevicesHolder(binding: ProfileItemListBinding) : BaseViewHolder<ProfileModel>(binding.root) {
        private val list: RecyclerView = binding.profileSubList
        private val adapter: DevicesAdapter

        init {
            list.setHasFixedSize(false)
            list.layoutManager = LinearLayoutManager(list.context)
            list.isNestedScrollingEnabled = false
            adapter = DevicesAdapter(object : DevicesAdapter.InfoHolder.Listener {
                override fun onClick(item: ProfileModel.Device) {
                    clickListener?.onDeviceClick(item)
                }
            })
            list.adapter = adapter
            binding.profileSubTitle.setText(R.string.profile_title_devices)
        }

        override fun bind(item: ProfileModel) {
            adapter.addAll(item.devices)
        }
    }

    private inner class ContactsHolder(binding: ProfileItemListBinding) : BaseViewHolder<ProfileModel>(binding.root) {
        private val list: RecyclerView = binding.profileSubList
        private val adapter: ContactsAdapter

        init {
            list.setHasFixedSize(false)
            list.layoutManager = LinearLayoutManager(list.context, LinearLayoutManager.HORIZONTAL, false)
            adapter = ContactsAdapter(object : ContactsAdapter.InfoHolder.Listener {
                override fun onClick(item: ProfileModel.Contact) {
                    clickListener?.onContactClick(item)
                }
            })
            list.adapter = adapter
            list.isNestedScrollingEnabled = false
            binding.profileSubTitle.setText(R.string.profile_title_contacts)
        }

        override fun bind(item: ProfileModel) {
            val contacts = item.contacts.toMutableList()
            if (contacts.getOrNull(0)?.type == ProfileModel.ContactType.QMS) {
                contacts.removeAt(0)
            }
            adapter.addAll(contacts)
        }
    }

    private inner class WarningsHolder(binding: ProfileItemListBinding) : BaseViewHolder<ProfileModel>(binding.root) {
        private val list: RecyclerView = binding.profileSubList
        private val adapter: WarningsAdapter

        init {
            list.setHasFixedSize(false)
            list.layoutManager = LinearLayoutManager(list.context)
            list.isNestedScrollingEnabled = false
            list.addItemDecoration(DividerItemDecoration(list.context))
            adapter = WarningsAdapter()
            list.adapter = adapter
            binding.profileSubTitle.setText(R.string.profile_title_warnings)
        }

        override fun bind(item: ProfileModel) {
            adapter.addAll(item.warnings)
        }
    }

    private inner class NoteHolder(private val binding: ProfileItemNoteBinding) : BaseViewHolder<ProfileModel>(binding.root) {

        init {
            binding.profileSaveNote.setOnClickListener {
                clickListener?.onSaveClick(binding.profileNoteText.text.toString())
            }
        }

        override fun bind(item: ProfileModel) {
            binding.profileNoteText.setText(item.note)
        }
    }

    interface ClickListener {
        fun onSaveClick(text: String)
        fun onContactClick(item: ProfileModel.Contact)
        fun onDeviceClick(item: ProfileModel.Device)
        fun onStatClick(item: ProfileModel.Stat)
    }
}
