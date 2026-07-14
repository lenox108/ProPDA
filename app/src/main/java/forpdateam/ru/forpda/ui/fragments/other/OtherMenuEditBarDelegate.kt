package forpdateam.ru.forpda.ui.fragments.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.databinding.ItemOtherMenuEditBarBinding
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuEditBarListItem

class OtherMenuEditBarDelegate(
        private val doneListener: () -> Unit,
        private val resetListener: () -> Unit
) : AdapterDelegate<MutableList<ListItem>>() {

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is OtherMenuEditBarListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemOtherMenuEditBarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.otherMenuEditDone.setOnClickListener { doneListener() }
        binding.otherMenuEditReset.setOnClickListener { resetListener() }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
            items: MutableList<ListItem>,
            position: Int,
            holder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
    ) = Unit

    private class ViewHolder(binding: ItemOtherMenuEditBarBinding) : RecyclerView.ViewHolder(binding.root)
}
