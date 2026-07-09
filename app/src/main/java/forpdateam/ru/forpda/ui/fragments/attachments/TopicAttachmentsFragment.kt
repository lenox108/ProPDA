package forpdateam.ru.forpda.ui.fragments.attachments

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.attachments.TopicAttachment
import forpdateam.ru.forpda.presentation.attachments.TopicAttachmentsViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import kotlinx.coroutines.launch

/**
 * Нативный список вложений темы. Заменяет прежний переход в браузер на `act=attach&code=showtopic`
 * (у браузера нет forum-кук приложения → логин-стена). Тап по элементу переиспользует
 * медиа-роутинг LinkHandler во ViewModel. Список рендерится порциями (клиентская пагинация),
 * т.к. страница не пагинируется на сервере и на больших темах содержит тысячи вложений.
 */
@AndroidEntryPoint
class TopicAttachmentsFragment : RecyclerFragment() {

    companion object {
        const val ARG_TOPIC_ID = "attachments_topic_id"
        private const val LOAD_MORE_THRESHOLD = 10
    }

    private val presenter: TopicAttachmentsViewModel by viewModels()

    private lateinit var adapter: TopicAttachmentsAdapter
    private var truncatedNoticeShown = false

    private val adapterListener = object : BaseAdapter.OnItemClickListener<TopicAttachment> {
        override fun onItemClick(item: TopicAttachment) {
            presenter.onItemClick(item)
        }

        override fun onItemLongClick(item: TopicAttachment): Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.attachments)
        presenter.setTopicId(arguments?.getInt(ARG_TOPIC_ID, 0) ?: 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()
        setTitle(arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.attachments))

        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        adapter = TopicAttachmentsAdapter()
        adapter.setOnItemClickListener(adapterListener)
        recyclerView.adapter = adapter

        // Клиентская пагинация: подгружаем следующую порцию при подскролле к концу списка.
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (presenter.hasMore() && lastVisible >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    presenter.loadMore()
                }
            }
        })

        refreshLayout.setOnRefreshListener {
            truncatedNoticeShown = false
            presenter.load()
        }

        presenter.start()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { presenter.refreshing.collect { setRefreshing(it) } }
                launch { presenter.items.collect { showItems(it) } }
                launch { presenter.truncated.collect { maybeShowTruncatedNotice(it) } }
            }
        }
    }

    private fun showItems(items: List<TopicAttachment>) {
        if (items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_attachment)
                        .setTitle(R.string.no_attachments)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        adapter.addAll(items)
    }

    private fun maybeShowTruncatedNotice(truncated: Boolean) {
        if (!truncated || truncatedNoticeShown || view == null) return
        truncatedNoticeShown = true
        Toast.makeText(requireContext(), R.string.attachments_truncated, Toast.LENGTH_LONG).show()
    }
}
