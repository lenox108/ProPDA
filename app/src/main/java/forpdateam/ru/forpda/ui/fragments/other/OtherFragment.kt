package forpdateam.ru.forpda.ui.fragments.other

import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.presentation.other.OtherViewModel
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.tuneForListPerformance
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.ProfileListItem
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 16.12.17.
 */
@AndroidEntryPoint
class OtherFragment : TabFragment() {

    private val otherAdapter by lazy {
        OtherAdapter(
                profileClickListener,
                menuClickListener,
                exitClickListener,
                infoCloseClickListener,
                topicPreferencesHolder
        )
    }

    private var listScrollY = 0
    private var listBaseBottomPadding = 0
    private var bottomChromePadding = 0
    private lateinit var listRecycler: RecyclerView
    private var itemTouchHelper: ItemTouchHelper? = null

    private val viewModel: OtherViewModel by viewModels()

    init {
        configuration.defaultTitle = "Полное меню приложения"
    }

    override fun useTopBarRoundedBottomCorners(): Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_other)
        listRecycler = findViewById(R.id.recyclerView) as? RecyclerView ?: throw IllegalStateException("listRecycler not found")
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appBarLayout.visibility = View.GONE
        // Иначе под списком просвечивает ?background_for_lists координатора — полоса над нижним меню.
        coordinatorLayout.setBackgroundColor(requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        listRecycler.apply {
            listBaseBottomPadding = paddingBottom
            val gridLayoutManager = GridLayoutManager(this.context, MENU_GRID_SPAN_COUNT)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = otherAdapter.getSpanSize(position, MENU_GRID_SPAN_COUNT)
            }
            layoutManager = gridLayoutManager
            adapter = otherAdapter
            addItemDecoration(
                    MenuTileSpacingDecoration(
                            spanCount = MENU_GRID_SPAN_COUNT,
                            outerPx = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal),
                            gapPx = resources.getDimensionPixelSize(R.dimen.dp8),
                    )
            )
            tuneForListPerformance()
            otherAdapter.editModeChangeListener = { enabled ->
                viewModel.onMenuDragModeChange(enabled)
                if (enabled) {
                    showSnackbar("Режим редактирования")
                }
            }
            itemTouchHelper = ItemTouchHelper(OtherItemDragCallback(
                    otherAdapter,
                    object : OtherItemDragCallback.ItemTouchHelperListener {
                        override fun onDragStart() {
                        }

                        override fun onItemMove(fromPosition: Int, toPosition: Int) {
                            otherAdapter.moveItem(fromPosition, toPosition)
                        }

                        override fun onDragEnd() {
                            viewModel.onChangeMenuSequence(otherAdapter.currentVisibleMenuLayout())
                        }
                    }
            )).also { it.attachToRecyclerView(this) }
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                    if (event.actionMasked != MotionEvent.ACTION_DOWN || !otherAdapter.isMenuEditMode()) {
                        return false
                    }
                    val child = rv.findChildViewUnder(event.x, event.y)
                    if (child == null) {
                        exitMenuEditMode()
                        return false
                    }
                    val holder = rv.getChildViewHolder(child)
                    return if (otherAdapter.isDraggableItem(holder.bindingAdapterPosition)) {
                        itemTouchHelper?.startDrag(holder)
                        false
                    } else {
                        exitMenuEditMode()
                        true
                    }
                }
            })

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    listScrollY = recyclerView.computeVerticalScrollOffset()
                    updateToolbarShadow()
                }
            })
        }
        syncBottomChromePadding()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.isReady) return@collect
                    otherAdapter.setCustomLayout(state.menuTileLayout)
                    otherAdapter.bindItems(state.profileItem, state.infoList, state.menu, state.bottomNavDuplicateIds)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                topicPreferencesHolder.observeCircleAvatarsFlow().collect {
                    val items = otherAdapter.items
                    val idx = items?.indexOfFirst { it is ProfileListItem } ?: -1
                    if (idx >= 0) otherAdapter.notifyItemChanged(idx)
                }
            }
        }
    }

    override fun isShadowVisible(): Boolean {
        return listScrollY != 0
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        syncBottomChromePadding()
    }

    override fun onPauseOrHide() {
        exitMenuEditMode()
        super.onPauseOrHide()
    }

    override fun onBackPressed(): Boolean {
        return if (otherAdapter.isMenuEditMode()) {
            exitMenuEditMode()
            true
        } else {
            super.onBackPressed()
        }
    }

    // Read-only зеркало onBackPressed: перехватываем «назад» в режиме
    // редактирования меню (см. hasBackHandling в TabFragment).
    override fun hasBackHandling(): Boolean = otherAdapter.isMenuEditMode()

    override fun onBottomChromePaddingChanged(padding: Int) {
        super.onBottomChromePaddingChanged(padding)
        bottomChromePadding = padding
        updateListBottomPadding()
    }

    private fun syncBottomChromePadding() {
        val rootInsets = ViewCompat.getRootWindowInsets(fragmentContainer)
        bottomChromePadding = BottomNavWindowInset.fragmentsBottomPaddingPx(
                baseTabBarPx = resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height),
                rootInsets = rootInsets,
                fallbackNavBottomPx = dimensionsProvider.getDimensions().navigationBar
        )
        updateListBottomPadding()
    }

    private fun updateListBottomPadding() {
        if (::listRecycler.isInitialized) {
            listRecycler.updatePadding(bottom = listBaseBottomPadding + bottomChromePadding)
        }
    }

    private fun exitMenuEditMode() {
        if (otherAdapter.isMenuEditMode()) {
            otherAdapter.setEditMode(false)
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {}

    private val profileClickListener: (ProfileModel?) -> Unit = { _: ProfileModel? ->
        viewModel.onProfileClick()
    }

    private val menuClickListener = { item: DrawerMenuItem -> viewModel.onMenuClick(item.appItem) }

    private val exitClickListener = { requireActivity().finishAffinity() }

    private val infoCloseClickListener = { item: CloseableInfo -> viewModel.onCloseInfo(item) }

    companion object {
        private const val MENU_GRID_SPAN_COUNT = 12
    }

}
