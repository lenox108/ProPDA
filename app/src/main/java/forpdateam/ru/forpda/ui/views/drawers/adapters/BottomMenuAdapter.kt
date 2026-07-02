package forpdateam.ru.forpda.ui.views.drawers.adapters

import androidx.recyclerview.widget.DiffUtil
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter

/**
 * Created by radiationx on 25.02.18.
 */
class BottomMenuAdapter(private val listener: BottomMenuDelegate.Listener) : ListDelegationAdapter<MutableList<ListItem>>() {

    companion object {
        /** Payload: рисовать grow-in анимацию «таблетки» (только при реальной смене экрана). */
        const val PAYLOAD_ANIMATE_SELECT = "animate_select"
    }

    private var currentScreenKey: String? = null

    init {
        items = mutableListOf()
        delegatesManager.run {
            addDelegate(BottomMenuDelegate(listener))
        }
    }

    fun bindItems(menus: List<DrawerMenuItem>) {
        val oldList = (items ?: mutableListOf()).toList()
        val newList = menus.map { BottomTabListItem(it) }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val oldItem = oldList[o] as? BottomTabListItem ?: return false
                val newItem = newList[n]
                return oldItem.item.appItem.id == newItem.item.appItem.id
            }

            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val oldItem = oldList[o] as? BottomTabListItem ?: return false
                val newItem = newList[n]
                val oldMenu = oldItem.item
                val newMenu = newItem.item
                return oldMenu.title == newMenu.title &&
                    oldMenu.icon == newMenu.icon &&
                    oldMenu.appItem.count == newMenu.appItem.count &&
                    oldItem.selected == newItem.selected
            }
        })

        items = newList.toMutableList()
        diff.dispatchUpdatesTo(this)

        // Preserve current selection without rebuilding the whole list.
        currentScreenKey?.let { setSelected(it) }
    }

    fun setSelected(screenKey: String) {
        // Анимируем «таблетку» только при реальном переходе на ДРУГОЙ экран, а не при
        // rebuild-восстановлении (bindItems → setSelected того же ключа при обновлении
        // счётчиков по вебсокету) и не на самом первом выборе (холодный старт спокойный).
        val screenChanged = currentScreenKey != null && currentScreenKey != screenKey
        currentScreenKey = screenKey
        items?.forEachIndexed { index, item ->
            val listItem = (item as BottomTabListItem)
            val itemScreenKey = listItem.item.appItem.screen?.getKey()
            val lastSelected = listItem.selected
            listItem.selected = itemScreenKey == screenKey
            if (lastSelected != listItem.selected) {
                if (screenChanged && listItem.selected) {
                    notifyItemChanged(index, PAYLOAD_ANIMATE_SELECT)
                } else {
                    notifyItemChanged(index)
                }
            }
        }
    }

    interface Listener : BottomMenuDelegate.Listener
}