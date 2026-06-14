package forpdateam.ru.forpda.ui.fragments.devdb.device.posts

import forpdateam.ru.forpda.common.getColorFromAttr
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import java.util.ArrayList

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.fragments.devdb.device.SubDeviceFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 09.08.17.
 */

@AndroidEntryPoint
class PostsFragment : SubDeviceFragment() {

    private var source = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.device_fragment_specs, container, false)
        //view.setBackgroundColor(getContext().getColorFromAttr(R.attr.background_for_lists));
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.base_list) ?: throw IllegalStateException("recyclerView not found")
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(recyclerView.context)
        val adapter = PostsAdapter(object : PostsAdapter.PostHolder.Listener {
            override fun onClick(item: Device.PostItem) {
                presenter.onPostClick(item, source)
            }
        })
        adapter.source = source

        adapter.addAll(getList())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(dp8, true))
        return view
    }

    private fun getList(): List<Device.PostItem> = when (source) {
        SRC_DISCUSSIONS -> device.discussions
        SRC_FIRMWARES -> device.firmwares
        SRC_NEWS -> device.news
        else -> emptyList()
    }

    fun setSource(source: Int): SubDeviceFragment {
        this.source = source
        return this
    }

    companion object {
        const val SRC_DISCUSSIONS = 1
        const val SRC_FIRMWARES = 2
        const val SRC_NEWS = 3
    }
}
