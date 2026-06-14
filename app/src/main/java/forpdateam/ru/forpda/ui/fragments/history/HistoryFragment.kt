package forpdateam.ru.forpda.ui.fragments.history

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.View

import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.presentation.history.HistoryViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 06.09.17.
 */

@AndroidEntryPoint
class HistoryFragment : RecyclerFragment() {

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private lateinit var adapter: HistoryAdapter
    private lateinit var dialogMenu: DynamicDialogMenu<HistoryFragment, HistoryItem>

    private val adapterListener = object : BaseAdapter.OnItemClickListener<HistoryItem> {
        override fun onItemClick(item: HistoryItem) {
            viewModel.onItemClick(item)
        }

        override fun onItemLongClick(item: HistoryItem): Boolean {
            dialogMenu.apply {
                disallowAll()
                allowAll()
                show(requireContext(), this@HistoryFragment, item)
            }
            return true
        }
    }

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_history)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Единое поведение для вкладок: AppBar скрывается вниз и появляется при скролле вверх.
        clearToolbarScrollFlags()

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.copy_link)) { _, data ->
                viewModel.copyLink(data)
            }
            addItem(getString(R.string.delete)) { _, data ->
                viewModel.remove(data.id)
            }
        }

        adapter = HistoryAdapter()

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.adapter = adapter

        adapter.setItemClickListener(adapterListener)
        refreshLayout.setOnRefreshListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    setRefreshing(state.loading)
                    showHistory(state.items)
                }
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add("Удалить историю")
                .setOnMenuItemClickListener {
                    viewModel.clear()
                    false
                }
    }

    private fun showHistory(items: List<HistoryItem>) {
        if (items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_history)
                        .setTitle(R.string.funny_history_nodata_title)
                        .setDesc(R.string.funny_history_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        adapter.addAll(items)
    }

}
