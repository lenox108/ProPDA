package forpdateam.ru.forpda.ui.views.pagination

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.util.Locale
import forpdateam.ru.forpda.R

class PaginationAdapter(
    context: Context,
    private val data: IntArray
) : BaseAdapter() {

    private val page: String = context.getString(R.string.pagination_page_number)
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = data.size

    override fun getItem(i: Int): Any = data[i]

    override fun getItemId(i: Int): Long = i.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(android.R.layout.simple_list_item_single_choice, parent, false)
            holder = ViewHolder()
            holder.text = view.findViewById(android.R.id.text1)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        holder.text.text = String.format(Locale.getDefault(), page, getItem(position) as Int)
        return view
    }

    private class ViewHolder {
        lateinit var text: TextView
    }
}
