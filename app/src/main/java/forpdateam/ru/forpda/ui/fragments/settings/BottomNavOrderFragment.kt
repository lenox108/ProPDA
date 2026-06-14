package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.model.MenuMapper
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.databinding.FragmentBottomNavOrderBinding
import forpdateam.ru.forpda.databinding.ItemBottomNavOrderRowBinding
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.listPlateSegment
import java.util.Collections

/**
 * Порядок пунктов [MenuRepository.GROUP_MAIN]: первые четыре видимых на нижней панели, остальные в «меню».
 */
@AndroidEntryPoint
class BottomNavOrderFragment : Fragment() {

    @javax.inject.Inject lateinit var menuRepository: MenuRepository
    private lateinit var binding: FragmentBottomNavOrderBinding
    private lateinit var adapter: OrderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FragmentBottomNavOrderBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.setTitle(R.string.pref_title_bottom_nav_order)
        adapter = OrderAdapter(
                menuRepository.getMainMenuOrderForEdit().toMutableList()
        ) { menuRepository.setMainMenuSequence(it) }
        binding.bottomNavOrderList.layoutManager = LinearLayoutManager(requireContext())
        binding.bottomNavOrderList.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = true
        }).attachToRecyclerView(binding.bottomNavOrderList)
    }

    override fun onDestroyView() {
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.activity_title_settings)
        super.onDestroyView()
    }

    private class OrderAdapter(
            private val items: MutableList<AppMenuItem>,
            private val onReorder: (List<AppMenuItem>) -> Unit
    ) : RecyclerView.Adapter<OrderAdapter.VH>() {

        class VH(val binding: ItemBottomNavOrderRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemBottomNavOrderRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val res = holder.itemView.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            val last = items.lastIndex
            holder.binding.root.applyListRowPlate(
                    listPlateSegment(position > 0, position < last),
                    inset,
                    if (position == 0) gap else 0,
                    if (position == last) gap else 0,
                    ensureSelectableForeground = false,
            )
            holder.binding.icon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.context, MenuMapper.getIcon(item)))
            holder.binding.title.setText(MenuMapper.getTitle(item))
        }

        override fun getItemCount() = items.size

        fun move(from: Int, to: Int) {
            if (from == to) return
            if (from < to) {
                for (i in from until to) {
                    Collections.swap(items, i, i + 1)
                }
            } else {
                for (i in from downTo to + 1) {
                    Collections.swap(items, i, i - 1)
                }
            }
            notifyItemMoved(from, to)
            onReorder(items.toList())
        }
    }
}
