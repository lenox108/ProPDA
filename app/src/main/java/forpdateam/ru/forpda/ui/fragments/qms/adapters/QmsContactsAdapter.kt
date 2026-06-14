package forpdateam.ru.forpda.ui.fragments.qms.adapters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.databinding.QmsContactItemBinding
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class QmsContactsAdapter : BaseAdapter<QmsContact, QmsContactsAdapter.ContactHolder>() {

    private var itemClickListener: BaseAdapter.OnItemClickListener<QmsContact>? = null

    fun setOnItemClickListener(listener: BaseAdapter.OnItemClickListener<QmsContact>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactHolder {
        val binding = QmsContactItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ContactHolder(
        private val binding: QmsContactItemBinding
    ) : BaseViewHolder<QmsContact>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: QmsContact, position: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.qms_contact_card_gap_vertical)
            binding.root.applyListRowPlate(
                    ListPlateSegment.SINGLE,
                    inset,
                    if (position == 0) gap else 0,
                    gap,
                    ensureSelectableForeground = false,
            )
            val density = binding.root.context.currentUiDensityValues()
            binding.root.minimumHeight = density.qmsContactMinHeightPx
            binding.qmsContactNick.setTextSizePx(density.titleTextSizePx)
            binding.qmsContactCount.setTextSizePx(density.metadataTextSizePx)
            binding.qmsContactCount.setPaddingRelative(density.dividerSpacingPx, 0, density.dividerSpacingPx, 0)

            binding.qmsContactNick.text = item.nick
            ForPdaCoil.loadInto(binding.qmsContactAvatar, item.avatar)
            binding.qmsContactNick.typeface = if (item.count > 0) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
            if (item.count == 0) {
                binding.qmsContactCount.visibility = View.GONE
            } else {
                binding.qmsContactCount.text = item.count.toString()
                binding.qmsContactCount.visibility = View.VISIBLE
            }
        }

        override fun onClick(view: View) {
            val pos = bindingAdapterPosition
            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
            itemClickListener?.onItemClick(getItem(pos))
        }

        override fun onLongClick(view: View): Boolean {
            val pos = bindingAdapterPosition
            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return false
            return itemClickListener?.let {
                it.onItemLongClick(getItem(pos))
                true
            } ?: false
        }
    }
}
