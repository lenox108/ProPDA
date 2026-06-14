package forpdateam.ru.forpda.ui.fragments.devdb.device.comments

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

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
class CommentsFragment : SubDeviceFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.device_fragment_specs, container, false)

        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.base_list) ?: throw IllegalStateException("recyclerView not found")
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(recyclerView.context)
        val adapter = CommentsAdapter(object : CommentsAdapter.CommentHolder.Listener {
            override fun onClick(item: Device.Comment) {
                presenter.onCommentClick(item)
            }
        })
        adapter.addAll(device.comments)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(dp8, true))
        return view
    }
}
