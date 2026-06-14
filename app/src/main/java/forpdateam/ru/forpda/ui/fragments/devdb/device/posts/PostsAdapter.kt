package forpdateam.ru.forpda.ui.fragments.devdb.device.posts

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import forpdateam.ru.forpda.databinding.DevicePostForumItemBinding
import forpdateam.ru.forpda.databinding.DevicePostNewsItemBinding
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class PostsAdapter(
    private val listener: PostHolder.Listener
) : BaseAdapter<Device.PostItem, PostsAdapter.PostHolder>() {

    var source: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostHolder {
        return if (source == PostsFragment.SRC_NEWS) {
            val binding = DevicePostNewsItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            PostHolder(binding, listener)
        } else {
            val binding = DevicePostForumItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            PostHolder(binding, listener)
        }
    }

    override fun onBindViewHolder(holder: PostHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class PostHolder : BaseViewHolder<Device.PostItem> {
        private val binding: Any
        private var currentItem: Device.PostItem? = null

        constructor(binding: DevicePostNewsItemBinding, listener: Listener) : super(binding.root) {
            this.binding = binding
            binding.root.setOnClickListener {
                currentItem?.let { item -> listener.onClick(item) }
            }
        }

        constructor(binding: DevicePostForumItemBinding, listener: Listener) : super(binding.root) {
            this.binding = binding
            binding.root.setOnClickListener {
                currentItem?.let { item -> listener.onClick(item) }
            }
        }

        override fun bind(item: Device.PostItem, position: Int) {
            currentItem = item
            when (binding) {
                is DevicePostNewsItemBinding -> {
                    binding.itemTitle.text = item.title
                    binding.itemDate.text = item.date
                    binding.itemDesc?.let { descView ->
                        if (item.desc != null) {
                            descView.text = ApiUtils.spannedFromHtml(item.desc)
                            descView.visibility = View.VISIBLE
                        } else {
                            descView.visibility = View.GONE
                        }
                    }
                }
                is DevicePostForumItemBinding -> {
                    binding.itemTitle.text = item.title
                    binding.itemDate.text = item.date
                    binding.itemDesc?.let { descView ->
                        if (!item.desc.isNullOrEmpty()) {
                            descView.text = ApiUtils.spannedFromHtml(item.desc)
                            descView.visibility = View.VISIBLE
                        } else {
                            descView.visibility = View.GONE
                        }
                    }
                }
            }
        }

        interface Listener {
            fun onClick(item: Device.PostItem)
        }
    }
}
