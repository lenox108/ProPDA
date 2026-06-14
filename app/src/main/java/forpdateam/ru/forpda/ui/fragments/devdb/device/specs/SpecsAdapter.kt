package forpdateam.ru.forpda.ui.fragments.devdb.device.specs

import android.util.Pair
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.DeviceSpecItemBinding
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils

class SpecsAdapter : RecyclerView.Adapter<SpecsAdapter.ViewHolder>() {

    private val list = ArrayList<Pair<String, List<Pair<String, String>>>>()

    fun addAll(results: Collection<Pair<String, List<Pair<String, String>>>>) {
        addAll(results, true)
    }

    fun addAll(results: Collection<Pair<String, List<Pair<String, String>>>>, clearList: Boolean) {
        val oldSize = list.size
        if (clearList) {
            clear()
        }
        list.addAll(results)
        if (oldSize == 0) {
            notifyItemRangeInserted(0, list.size)
        } else {
            notifyItemRangeChanged(0, list.size)
        }
    }

    fun clear() {
        list.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DeviceSpecItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.itemTitle.text = item.first
        
        val builder = StringBuilder()
        for (i in item.second.indices) {
            val pair = item.second[i]
            val strColor = String.format(
                "#%06X",
                0xFFFFFF and holder.binding.root.context.getColorFromAttr(R.attr.second_text_color)
            )
            builder.append("<small style=\"font-size:10px\"><span style=\"color: ")
                .append(strColor)
                .append("\">")
                .append(pair.first)
                .append("</span></small><br>")
                .append(pair.second)
            if (i + 1 < item.second.size) {
                builder.append("<br><br>")
            }
        }
        
        holder.binding.itemDesc.text = ApiUtils.coloredFromHtml(builder.toString())
    }

    override fun getItemCount(): Int = list.size

    fun getItem(position: Int): Pair<String, List<Pair<String, String>>> = list[position]

    class ViewHolder(val binding: DeviceSpecItemBinding) : RecyclerView.ViewHolder(binding.root)
}
