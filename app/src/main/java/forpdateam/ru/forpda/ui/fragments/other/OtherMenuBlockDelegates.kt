package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.ItemOtherMenuBlockHeaderBinding
import forpdateam.ru.forpda.databinding.ItemOtherMenuContinueBinding
import forpdateam.ru.forpda.databinding.ItemOtherMenuQuickSettingsBinding
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.app.other.OtherMenuBlock
import forpdateam.ru.forpda.entity.app.other.QuickSetting
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuContinueListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuHeaderListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuQuickSettingsListItem

/**
 * Заголовок блока («Продолжить чтение», «Быстрые настройки»). В режиме редактирования
 * показывает кнопки «Изменить» (состав быстрых настроек) и «Скрыть»/«Показать».
 */
class OtherMenuHeaderDelegate(
        private val visibilityListener: (OtherMenuBlock) -> Unit,
        private val configureListener: (OtherMenuBlock) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuHeaderListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
            ViewHolder(
                    ItemOtherMenuBlockHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    visibilityListener,
                    configureListener
            )

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) {
        (holder as ViewHolder).bind(items[position] as OtherMenuHeaderListItem)
    }

    private class ViewHolder(
            private val binding: ItemOtherMenuBlockHeaderBinding,
            visibilityListener: (OtherMenuBlock) -> Unit,
            configureListener: (OtherMenuBlock) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var block: OtherMenuBlock? = null

        init {
            binding.blockHeaderVisibility.setOnClickListener { block?.let(visibilityListener) }
            binding.blockHeaderConfigure.setOnClickListener { block?.let(configureListener) }
        }

        fun bind(item: OtherMenuHeaderListItem) {
            block = item.block
            binding.blockHeaderTitle.setText(item.titleRes)
            val showActions = item.editMode && item.block != null
            binding.blockHeaderVisibility.visibility = if (showActions) View.VISIBLE else View.GONE
            binding.blockHeaderVisibility.setText(
                    if (item.hidden) R.string.other_menu_block_show else R.string.other_menu_block_hide
            )
            // «Изменить» бессмысленно у скрытого блока: менять состав нечему, пока он не виден.
            binding.blockHeaderConfigure.visibility =
                    if (showActions && item.configurable && !item.hidden) View.VISIBLE else View.GONE
        }
    }
}

/**
 * Строка «Продолжить чтение». Открывается через LinkHandler по сохранённому URL темы,
 * поэтому попадает ровно туда же, куда обычный переход из истории.
 */
class OtherMenuContinueDelegate(
        private val clickListener: (HistoryItem) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuContinueListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
            ViewHolder(ItemOtherMenuContinueBinding.inflate(LayoutInflater.from(parent.context), parent, false), clickListener)

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) {
        (holder as ViewHolder).bind(items, position)
    }

    private class ViewHolder(
            private val binding: ItemOtherMenuContinueBinding,
            clickListener: (HistoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var current: HistoryItem? = null

        init {
            binding.root.setOnClickListener { current?.let(clickListener) }
            val ctx = binding.root.context
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            binding.otherContinueIconCircle.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, ICON_CIRCLE_ALPHA))
            }
        }

        fun bind(items: List<ListItem>, position: Int) {
            val item = (items[position] as OtherMenuContinueListItem).item
            current = item
            binding.otherContinueTitle.text = item.title.orEmpty()
            binding.otherContinueDate.text = item.date.orEmpty()

            val row = otherMenuPlateRow(items, position) { it is OtherMenuContinueListItem }
            binding.root.setBackgroundResource(drawableForPlateRow(row))
            // Rows inside one plate are separated by an inset divider; the plate's own bottom edge closes
            // the last one, so it must not draw a divider on top of it.
            binding.otherContinueDivider.visibility = when (row) {
                OtherMenuPlateRow.LAST, OtherMenuPlateRow.SINGLE -> View.GONE
                else -> View.VISIBLE
            }
            val horizontal = binding.root.resources.getDimensionPixelSize(R.dimen.other_menu_plate_horizontal_margin)
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                leftMargin = horizontal
                rightMargin = horizontal
            }
        }

        private companion object {
            /** ~12% accent: a tonal circle that reads on a white card and on an AMOLED black one alike. */
            const val ICON_CIRCLE_ALPHA = 31
        }
    }
}

/** Ряд быстрых настроек. Состав задаёт пользователь, поэтому чипы строятся динамически. */
class OtherMenuQuickSettingsDelegate(
        private val clickListener: (QuickSetting) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuQuickSettingsListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
            ViewHolder(
                    ItemOtherMenuQuickSettingsBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    clickListener
            )

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) {
        (holder as ViewHolder).bind(items[position] as OtherMenuQuickSettingsListItem)
    }

    private class ViewHolder(
            private val binding: ItemOtherMenuQuickSettingsBinding,
            private val clickListener: (QuickSetting) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OtherMenuQuickSettingsListItem) {
            val group = binding.otherQuickChips
            group.removeAllViews()
            item.items.forEach { setting ->
                val chip = Chip(group.context).apply {
                    text = context.getString(quickSettingTitle(setting))
                    setOnClickListener { clickListener(setting) }
                }
                group.addView(chip)
            }
        }
    }
}

internal fun quickSettingTitle(setting: QuickSetting): Int = when (setting) {
    QuickSetting.THEME -> R.string.other_menu_quick_theme
    QuickSetting.PALETTE -> R.string.other_menu_quick_palette
    QuickSetting.ACCENT -> R.string.other_menu_quick_accent
    QuickSetting.FONT -> R.string.other_menu_quick_font
    QuickSetting.DENSITY -> R.string.other_menu_quick_density
    QuickSetting.PAGINATION -> R.string.other_menu_quick_pagination
    QuickSetting.BLACKLIST -> R.string.other_menu_quick_blacklist
}
