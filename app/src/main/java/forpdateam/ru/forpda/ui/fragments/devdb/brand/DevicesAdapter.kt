package forpdateam.ru.forpda.ui.fragments.devdb.brand

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.getDrawableAttr
import forpdateam.ru.forpda.databinding.BrandItemBinding
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.ui.fragments.devdb.DevDbHelper
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class DevicesAdapter : BaseAdapter<Brand.DeviceItem, DevicesAdapter.DeviceItemHolder>() {

    private var itemClickListener: BaseAdapter.OnItemClickListener<Brand.DeviceItem>? = null

    fun setItemClickListener(listener: BaseAdapter.OnItemClickListener<Brand.DeviceItem>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceItemHolder {
        val binding = BrandItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceItemHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceItemHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class DeviceItemHolder(
        private val binding: BrandItemBinding
    ) : BaseViewHolder<Brand.DeviceItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.itemImage.tag = binding.progressBar
            binding.itemRating.background = binding.itemRating.context.getDrawableAttr(R.attr.count_background)
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: Brand.DeviceItem, position: Int) {
            binding.itemTitle.text = item.title
            if (item.rating > 0) {
                binding.itemRating.text = item.rating.toString()
                binding.itemRating.background.colorFilter = DevDbHelper.getColorFilter(item.rating)
                binding.itemRating.visibility = View.VISIBLE
            } else {
                binding.itemRating.visibility = View.GONE
            }
            ForPdaCoil.loadIntoWithProgress(
                binding.itemImage,
                item.imageSrc,
                binding.itemImage.tag as com.google.android.material.progressindicator.CircularProgressIndicator
            )
        }

        override fun onClick(view: View) {
            // layoutPosition = -1 (NO_POSITION) при удалении/анимации item →
            // getItem(-1) роняет IndexOutOfBoundsException. Гейтим по границам.
            val position = layoutPosition
            if (position < 0 || position >= getItemCount()) return
            itemClickListener?.onItemClick(getItem(position))
        }

        override fun onLongClick(view: View): Boolean {
            val position = layoutPosition
            if (position < 0 || position >= getItemCount()) return false
            return itemClickListener?.let {
                it.onItemLongClick(getItem(position))
                true
            } ?: false
        }
    }
}
