package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.databinding.ItemOtherMenuBottomColumnsBinding
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuBottomColumnsListItem

/**
 * Сколько мест в нижней панели: 5 или 6. Стоит прямо под слотами панели в режиме
 * редактирования — менять её состав и размер логично в одном месте, а не на разных экранах.
 */
class OtherMenuBottomColumnsDelegate(
        private val changeListener: (Int) -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuBottomColumnsListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
            ViewHolder(
                    ItemOtherMenuBottomColumnsBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                    changeListener
            )

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) {
        (holder as ViewHolder).bind(items[position] as OtherMenuBottomColumnsListItem)
    }

    private class ViewHolder(
            private val binding: ItemOtherMenuBottomColumnsBinding,
            private val changeListener: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundColumns = 0

        init {
            binding.otherBottomColumnsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val columns = if (checkedId == binding.otherBottomColumns5.id) 5 else 6
                // Пересборка списка перевыставляет отметку — сообщаем только о реальной смене.
                if (columns != boundColumns) {
                    boundColumns = columns
                    changeListener(columns)
                }
            }
        }

        fun bind(item: OtherMenuBottomColumnsListItem) {
            boundColumns = item.columns
            binding.otherBottomColumnsGroup.check(
                    if (item.columns <= 5) binding.otherBottomColumns5.id else binding.otherBottomColumns6.id
            )
        }
    }
}
