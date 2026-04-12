package forpdateam.ru.forpda.ui.fragments.other

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.presentation.other.OtherViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem

/**
 * Created by radiationx on 16.12.17.
 */
class OtherFragment : TabFragment() {

    private val otherAdapter by lazy {
        OtherAdapter(
                profileClickListener,
                logoutClickListener,
                menuClickListener,
                menuSequenceListener,
                infoCloseClickListener
        )
    }

    private var listScrollY = 0
    private lateinit var listRecycler: RecyclerView

    private val viewModel: OtherViewModel by viewModels {
        OtherViewModel.Factory(
                App.get().Di().router,
                App.get().Di().authRepository,
                App.get().Di().profileRepository,
                App.get().Di().authHolder,
                App.get().Di().userHolder,
                App.get().Di().errorHandler,
                App.get().Di().menuRepository,
                App.get().Di().closeableInfoHolder
        )
    }

    init {
        configuration.defaultTitle = "Полное меню приложения"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_other)
        listRecycler = findViewById(R.id.recyclerView) as RecyclerView
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appBarLayout.visibility = View.GONE
        listRecycler.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this.context)
            adapter = otherAdapter

            val touchHelper = ItemTouchHelper(OtherItemDragCallback(otherAdapter, itemDragListener))
            touchHelper.attachToRecyclerView(this)

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    listScrollY = recyclerView.computeVerticalScrollOffset()
                    updateToolbarShadow()
                }
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    otherAdapter.bindItems(state.profileItem, state.infoList, state.menu)
                }
            }
        }
    }

    override fun isShadowVisible(): Boolean {
        return listScrollY != 0
    }

    override fun setRefreshing(isRefreshing: Boolean) {}

    private val profileClickListener: (ProfileModel?) -> Unit = { _: ProfileModel? ->
        viewModel.onProfileClick()
    }

    private val logoutClickListener = { viewModel.signOut() }

    private val menuClickListener = { item: DrawerMenuItem -> viewModel.onMenuClick(item.appItem) }

    private val infoCloseClickListener = { item: CloseableInfo -> viewModel.onCloseInfo(item) }

    private val menuSequenceListener = { items: List<AppMenuItem> ->
        viewModel.onChangeMenuSequence(items)
        Unit
    }

    private val itemDragListener = object : OtherItemDragCallback.ItemTouchHelperListener {
        override fun onDragStart() {
            viewModel.onMenuDragModeChange(true)
        }

        override fun onDragEnd() {
            viewModel.onMenuDragModeChange(false)
        }

        override fun onItemMove(fromPosition: Int, toPosition: Int) {
            otherAdapter.onItemMove(fromPosition, toPosition)
        }
    }

}
