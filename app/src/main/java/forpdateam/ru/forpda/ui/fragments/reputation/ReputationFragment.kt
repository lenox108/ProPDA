package forpdateam.ru.forpda.ui.fragments.reputation

import forpdateam.ru.forpda.presentation.TabRouter
import javax.inject.Inject
import forpdateam.ru.forpda.common.getVecDrawable
import android.content.Context
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.*
import forpdateam.ru.forpda.common.showSnackbar
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.RepItem
import forpdateam.ru.forpda.entity.remote.reputation.ReputationReportForm
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
import forpdateam.ru.forpda.presentation.reputation.ReputationUiEvent
import forpdateam.ru.forpda.presentation.reputation.ReputationViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.pagination.PaginationHelper
import forpdateam.ru.forpda.databinding.ReputationChangeLayoutBinding
import forpdateam.ru.forpda.databinding.ReportLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.AuthHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 20.03.17.
 */

@AndroidEntryPoint
class ReputationFragment : RecyclerFragment() {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var router: TabRouter

    private companion object {
        const val MENU_PROFILE = 0
        const val MENU_GO_TO_MESSAGE = 1
        const val MENU_REPORT = 2
    }


    private lateinit var adapter: ReputationAdapter
    private lateinit var paginationHelper: PaginationHelper
    private lateinit var dialogMenu: DynamicDialogMenu<ReputationFragment, RepItem>

    private lateinit var descSortMenuItem: MenuItem
    private lateinit var ascSortMenuItem: MenuItem
    private lateinit var repModeMenuItem: MenuItem
    private lateinit var upRepMenuItem: MenuItem
    private lateinit var downRepMenuItem: MenuItem


    private val presenter: ReputationViewModel by viewModels()

    private val paginationListener = object : PaginationHelper.PaginationListener {
        override fun onTabSelected(tab: TabLayout.Tab): Boolean {
            return refreshLayout.isRefreshing
        }

        override fun onSelectedPage(pageNumber: Int) {
            presenter.selectPage(pageNumber)
        }
    }

    private val adapterListener = object : BaseAdapter.OnItemClickListener<RepItem> {
        override fun onItemClick(item: RepItem) {
            presenter.onItemClick(item)
        }

        override fun onItemLongClick(item: RepItem): Boolean {
            presenter.onItemLongClick(item)
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_reputation)
        arguments?.apply {
            getString(TabFragment.ARG_TAB)?.also {
                presenter.setInitialData(ReputationApi.fromUrl(it))
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        paginationHelper = PaginationHelper(requireActivity(), dimensionsProvider)
        paginationHelper.addInToolbar(inflater, toolbarLayout, configuration.fitSystemWindow)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.profile)) { _, data ->
                presenter.navigateToProfile(data.userId)
            }
            addItem(getString(R.string.go_to_message)) { _, data ->
                presenter.navigateToMessage(data)
            }
            addItem(getString(R.string.reputation_report)) { _, data ->
                if (authHolder.get().isAuth()) {
                    presenter.onReportClick(data)
                } else {
                    Utils.showNeedAuthDialog(requireContext(), router)
                }
            }
        }

        refreshLayout.setOnRefreshListener { presenter.loadReputation() }
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        adapter = ReputationAdapter()
        recyclerView.adapter = adapter
        paginationHelper.setListener(paginationListener)
        adapter.setOnItemClickListener(adapterListener)

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun useCompactToolbarPaginationChrome(): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        paginationHelper.destroy()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        val subMenu = menu.addSubMenu(R.string.sorting_title)
        subMenu.item.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        subMenu.item.icon = requireContext().getVecDrawable(R.drawable.ic_toolbar_sort)
        descSortMenuItem = subMenu.add(R.string.sorting_desc).setOnMenuItemClickListener {
            presenter.setSort(ReputationApi.SORT_DESC)
            false
        }
        ascSortMenuItem = subMenu.add(R.string.sorting_asc).setOnMenuItemClickListener {
            presenter.setSort(ReputationApi.SORT_ASC)
            false
        }
        repModeMenuItem = menu.add(getString(if (presenter.currentData.value.mode == ReputationApi.MODE_FROM) R.string.reputation_mode_from else R.string.reputation_mode_to))
                .setOnMenuItemClickListener {
                    presenter.changeReputationMode()
                    false
                }
        upRepMenuItem = menu.add(R.string.increase)
                .setOnMenuItemClickListener {
                    if (authHolder.get().isAuth()) {
                        showChangeReputationDialog(true)
                    } else {
                        Utils.showNeedAuthDialog(requireContext(), router)
                    }
                    false
                }
        downRepMenuItem = menu.add(R.string.decrease)
                .setOnMenuItemClickListener {
                    if (authHolder.get().isAuth()) {
                        showChangeReputationDialog(false)
                    } else {
                        Utils.showNeedAuthDialog(requireContext(), router)
                    }
                    false
                }
        refreshToolbarMenuItems(false)
    }

    override fun refreshToolbarMenuItems(enable: Boolean) {
        super.refreshToolbarMenuItems(enable)
        if (enable) {
            descSortMenuItem.isEnabled = true
            ascSortMenuItem.isEnabled = true
            repModeMenuItem.isEnabled = true
            repModeMenuItem.title = getString(if (presenter.currentData.value.mode == ReputationApi.MODE_FROM) R.string.reputation_mode_from else R.string.reputation_mode_to)
            if (presenter.currentData.value.id != authHolder.get().userId) {
                upRepMenuItem.isEnabled = true
                upRepMenuItem.isVisible = true
                downRepMenuItem.isEnabled = true
                downRepMenuItem.isVisible = true
            }
        } else {
            descSortMenuItem.isEnabled = false
            ascSortMenuItem.isEnabled = false
            repModeMenuItem.isEnabled = false
            upRepMenuItem.isEnabled = false
            upRepMenuItem.isEnabled = false
            upRepMenuItem.isVisible = false
            downRepMenuItem.isVisible = false
        }
    }

    fun showChangeReputationDialog(type: Boolean) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = ReputationChangeLayoutBinding.inflate(LayoutInflater.from(builder.context))
        binding.reputationText.text =
            String.format(getString(R.string.change_reputation_Type_Nick), getString(if (type) R.string.increase else R.string.decrease), presenter.currentData.value.nick)

        builder
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { _, _ ->
                    presenter.changeReputation(type, binding.reputationTextField.text.toString())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        setRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: ReputationUiEvent) {
        when (event) {
            is ReputationUiEvent.ShowReputation -> showReputation(event.data)
            is ReputationUiEvent.ShowAvatar -> showAvatar(event.avatarUrl)
            is ReputationUiEvent.ShowItemDialogMenu -> showItemDialogMenu(event.item)
            is ReputationUiEvent.OnChangeReputation -> onChangeReputation(event.result)
            is ReputationUiEvent.ShowReportDialog -> showReportDialog(event.item, event.form)
            is ReputationUiEvent.OnReportSubmitted -> onReportSubmitted(event.result)
        }
    }

    private fun onReportSubmitted(result: Boolean) {
        if (result) {
            showSnackbar(getString(R.string.report_post_success))
        }
    }

    private fun onChangeReputation(result: Boolean) {
        showSnackbar(getString(R.string.reputation_changed))
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        super.setRefreshing(isRefreshing)
        refreshToolbarMenuItems(!isRefreshing)
    }

    private fun showAvatar(avatarUrl: String) {
        ForPdaCoil.loadInto(toolbarImageView, avatarUrl)
        toolbarImageView.visibility = View.VISIBLE
        toolbarImageView.contentDescription = getString(R.string.user_avatar)
    }

    private fun showReputation(repData: RepData) {
        if (repData.items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_history)
                        .setTitle(R.string.funny_reputation_nodata_title)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }

        adapter.addAll(repData.items)
        paginationHelper.updatePagination(repData.pagination)
        refreshToolbarMenuItems(true)
        setSubtitle("${repData.positive - repData.negative} (+${repData.positive} / -${repData.negative})")
        setTabTitle("Репутация ${repData.nick}${if (repData.mode == ReputationApi.MODE_FROM) ": кому изменял" else ""}")
        setTitle("Репутация ${repData.nick}${if (repData.mode == ReputationApi.MODE_FROM) ": кому изменял" else ""}")
        listScrollTop()
        toolbarImageView.setOnClickListener { presenter.navigateToProfile(repData.id) }
    }

    private fun showItemDialogMenu(item: RepItem) {
        dialogMenu.disallowAll()
        dialogMenu.allow(MENU_PROFILE)
        if (item.sourceUrl != null) {
            dialogMenu.allow(MENU_GO_TO_MESSAGE)
        }
        if (item.hasReportAction()) {
            dialogMenu.changeTitle(
                    MENU_REPORT,
                    getString(if (item.isPositive == false) R.string.reputation_appeal else R.string.reputation_report)
            )
            dialogMenu.allow(MENU_REPORT)
        }
        dialogMenu.show(requireContext(), this@ReputationFragment, item, item.userNick)
    }

    private fun showReportDialog(item: RepItem, form: ReputationReportForm) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = ReportLayoutBinding.inflate(LayoutInflater.from(builder.context))
        binding.reportInputLayout.hint = getString(R.string.report_text)
        builder
                .setTitle(
                        getString(
                                if (item.isPositive == false) R.string.reputation_appeal else R.string.reputation_report
                        )
                )
                .setView(binding.root)
                .setPositiveButton(R.string.send) { _, _ ->
                    presenter.submitReport(item, form, binding.reportTextField.text.toString())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }
}
