package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.databinding.ItemOtherExitButtonBinding
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuExitListItem

class ExitMenuItemDelegate(
        private val clickListener: () -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuExitListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherExitButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        (holder as ViewHolder).bind()
    }

    class ViewHolder(
            private val binding: ItemOtherExitButtonBinding,
            private val clickListener: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.otherExitButton.setOnClickListener { clickListener() }
        }

        fun bind() {
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                leftMargin = binding.root.dp16
                rightMargin = binding.root.dp16
                topMargin = binding.root.dp16
                bottomMargin = binding.root.dp12
            }
        }
    }
}
