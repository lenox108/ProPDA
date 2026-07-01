package forpdateam.ru.forpda.ui.fragments.devdb.device

import forpdateam.ru.forpda.common.getDrawableAttr
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.FragmentDeviceBinding
import forpdateam.ru.forpda.databinding.ToolbarDeviceBinding
import forpdateam.ru.forpda.databinding.DeviceImagePageBinding
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import android.view.*
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.common.ForPdaCoil
import com.robohorse.pagerbullet.PagerBullet
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp2
import forpdateam.ru.forpda.ui.dp48
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.presentation.devdb.device.DeviceUiEvent
import forpdateam.ru.forpda.presentation.devdb.device.DeviceViewModel
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.devdb.DevDbHelper
import forpdateam.ru.forpda.ui.fragments.devdb.device.comments.CommentsFragment
import forpdateam.ru.forpda.ui.fragments.devdb.device.posts.PostsFragment
import forpdateam.ru.forpda.ui.fragments.devdb.device.specs.SpecsFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import java.util.*
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.ui.DimensionsProvider
import javax.inject.Inject

/**
 * Created by radiationx on 08.08.17.
 */

@AndroidEntryPoint
class DeviceFragment : TabFragment() {

    @Inject lateinit var notesRepository: NotesRepository

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private var _deviceBinding: FragmentDeviceBinding? = null
    private val deviceBinding get() = checkNotNull(_deviceBinding) { "Binding accessed after onDestroyView" }
    private var _toolbarBinding: ToolbarDeviceBinding? = null
    private val toolbarBinding get() = checkNotNull(_toolbarBinding) { "Binding accessed after onDestroyView" }

    private lateinit var imagesPager: PagerBullet
    private lateinit var tabLayout: TabLayout
    private lateinit var rating: TextView
    private lateinit var fragmentsPager: ViewPager2
    private lateinit var progressBar: ProgressBar
    private var tabLayoutMediator: TabLayoutMediator? = null
    private var toolbarContent: RelativeLayout? = null


    private lateinit var copyLinkMenuItem: MenuItem
    private lateinit var shareMenuItem: MenuItem
    private lateinit var noteMenuItem: MenuItem
    private lateinit var toBrandMenuItem: MenuItem
    private lateinit var toBrandsMenuItem: MenuItem

    private var appBarOffset = 0

    private val presenter: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_device)
        arguments?.apply {
            presenter.deviceId = getString(ARG_DEVICE_ID, null)
        }

        val transaction = childFragmentManager.beginTransaction()
        for (fragment in childFragmentManager.fragments) {
            transaction.remove(fragment)
        }
        transaction.commitNow()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _deviceBinding = FragmentDeviceBinding.inflate(inflater, fragmentContent, true)

        val viewStub = findViewById(R.id.toolbar_content) as? ViewStub ?: throw IllegalStateException("toolbar_content ViewStub not found")
        viewStub.layoutResource = R.layout.toolbar_device
        toolbarContent = viewStub.inflate() as? RelativeLayout ?: throw IllegalStateException("toolbarContent not RelativeLayout")
        _toolbarBinding = ToolbarDeviceBinding.bind(toolbarContent!!)

        imagesPager = toolbarBinding.imagesPager
        progressBar = deviceBinding.progressBar
        rating = toolbarBinding.itemRating
        fragmentsPager = deviceBinding.viewPager

        tabLayout = TabLayout(requireContext())
        val tabParams = CollapsingToolbarLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        tabParams.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
        tabLayout.layoutParams = tabParams
        toolbarLayout.addView(tabLayout)

        val params = toolbarLayout.layoutParams as AppBarLayout.LayoutParams
        params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
        toolbarLayout.layoutParams = params

        val newParams = toolbar.layoutParams as CollapsingToolbarLayout.LayoutParams
        newParams.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
        newParams.bottomMargin = dp48
        toolbar.layoutParams = newParams
        toolbar.requestLayout()
        return viewFragment
    }

    override fun onDestroyViewBinding() {
        _deviceBinding = null
        _toolbarBinding = null
        super.onDestroyViewBinding()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setListsBackground()
        toolbarTitleView.setShadowLayer(dp2.toFloat(), 0f, 0f, requireContext().getColorFromAttr(R.attr.colorPrimary))
        toolbarSubtitleView.setShadowLayer(dp2.toFloat(), 0f, 0f, requireContext().getColorFromAttr(R.attr.colorPrimary))

        toolbarLayout.setExpandedTitleColor(Color.TRANSPARENT)
        toolbarLayout.setCollapsedTitleTextColor(Color.TRANSPARENT)
        toolbarLayout.isTitleEnabled = false

        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.setBackgroundColor(requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurface))
        tabLayout.setTabTextColors(
                requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant),
                requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        )
        tabLayout.setSelectedTabIndicatorColor(requireContext().getColorFromAttr(R.attr.colorAccent))

        imagesPager.setIndicatorTintColorScheme(requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurface), requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))

        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, offset ->
            appBarOffset = offset
            updateToolbarShadow()
        })

        if (configuration.fitSystemWindow) {
            lifecycleScope.launch {
                dimensionsProvider.dimensionsFlow.collect { dimensions ->
                    toolbarContent?.post {
                        if (toolbarContent != null) {
                            updateDimens(dimensions)
                        }
                    }
                    updateDimens(dimensions)
                }
            }
        }

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        fragmentsPager.adapter = null
        super.onDestroyView()
    }

    override fun isShadowVisible(): Boolean {
        return appBarOffset != 0
    }

    private fun updateDimens(dimensions: DimensionHelper.Dimensions) {
        toolbarContent?.also {
            val params = it.layoutParams as CollapsingToolbarLayout.LayoutParams
            params.topMargin = dimensions.statusBar
            it.layoutParams = params
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        progressBar.visibility = if (isRefreshing) View.VISIBLE else View.GONE
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

    private fun handleUiEvent(event: DeviceUiEvent) {
        when (event) {
            is DeviceUiEvent.ShowData -> showData(event.device)
            is DeviceUiEvent.ShowCreateNote -> showCreateNote(event.title, event.url)
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        copyLinkMenuItem = menu.add(R.string.copy_link)
                .setOnMenuItemClickListener {
                    presenter.copyLink()
                    true
                }

        shareMenuItem = menu.add(R.string.share)
                .setOnMenuItemClickListener {
                    presenter.shareLink()
                    true
                }

        noteMenuItem = menu.add(R.string.create_note)
                .setOnMenuItemClickListener {
                    presenter.createNote()
                    true
                }

        toBrandMenuItem = menu.add(R.string.devices)
                .setOnMenuItemClickListener {
                    presenter.openDevices()
                    true
                }

        toBrandsMenuItem = menu.add(R.string.devices)
                .setOnMenuItemClickListener {
                    presenter.openBrands()
                    true
                }

        refreshToolbarMenuItems(false)
    }

    override fun refreshToolbarMenuItems(enable: Boolean) {
        super.refreshToolbarMenuItems(enable)
        if (enable) {
            copyLinkMenuItem.isEnabled = true
            shareMenuItem.isEnabled = true
            noteMenuItem.isEnabled = true
            toBrandMenuItem.isVisible = true
            toBrandsMenuItem.isVisible = true
        } else {
            copyLinkMenuItem.isEnabled = false
            shareMenuItem.isEnabled = false
            noteMenuItem.isEnabled = false
            toBrandMenuItem.isVisible = false
            toBrandsMenuItem.isVisible = false
        }
    }

    private fun showData(data: Device) {
        toBrandMenuItem.title = "${data.catTitle} ${data.brandTitle}"
        toBrandsMenuItem.title = data.catTitle
        refreshToolbarMenuItems(true)
        setTitle(data.title)
        setTabTitle("${data.catTitle} ${data.brandTitle}: ${data.title}")
        setSubtitle("${data.catTitle} ${data.brandTitle}")


        val urls = ArrayList<String>()
        val fullUrls = ArrayList<String>()
        for (pair in data.images) {
            urls.add(pair.first)
            fullUrls.add(pair.second)
        }
        val imagesAdapter = ImagesAdapter(requireContext(), urls, fullUrls)
        imagesPager.setAdapter(imagesAdapter)

        val pagerAdapter = DevicePagerAdapter(this, data)
        fragmentsPager.adapter = pagerAdapter
        tabLayoutMediator?.detach()
        tabLayoutMediator = TabLayoutMediator(tabLayout, fragmentsPager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.also { it.attach() }

        if (data.rating > 0) {
            rating.text = data.rating.toString()
            rating.background = rating.context.getDrawableAttr(R.attr.count_background)
            rating.background.colorFilter = DevDbHelper.getColorFilter(data.rating)
            rating.visibility = View.VISIBLE
            if (!data.comments.isEmpty()) {
                rating.isClickable = true
                rating.setOnClickListener { fragmentsPager.setCurrentItem(1, true) }
            }

        } else {
            rating.visibility = View.GONE
        }
    }

    private fun showCreateNote(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(context, title, url, notesRepository)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private class DevicePagerAdapter(host: Fragment, device: Device) : FragmentStateAdapter(host) {

        private val pages: List<Pair<String, () -> Fragment>> = buildList {
            val context = host.requireContext()
            if (device.specs.isNotEmpty()) {
                add(context.getString(R.string.device_page_specs) to { SpecsFragment().setDevice(device) })
            }
            if (device.comments.isNotEmpty()) {
                val title = String.format(Locale.getDefault(),
                        context.getString(R.string.device_page_comments),
                        device.comments.size)
                add(title to { CommentsFragment().setDevice(device) })
            }
            if (device.discussions.isNotEmpty()) {
                val title = String.format(Locale.getDefault(),
                        context.getString(R.string.device_page_discussions),
                        device.discussions.size)
                add(title to { PostsFragment().setSource(PostsFragment.SRC_DISCUSSIONS).setDevice(device) })
            }
            if (device.news.isNotEmpty()) {
                val title = String.format(Locale.getDefault(),
                        context.getString(R.string.device_page_news),
                        device.news.size)
                add(title to { PostsFragment().setSource(PostsFragment.SRC_NEWS).setDevice(device) })
            }
            if (device.firmwares.isNotEmpty()) {
                val title = String.format(Locale.getDefault(),
                        context.getString(R.string.device_page_firmwares),
                        device.firmwares.size)
                add(title to { PostsFragment().setSource(PostsFragment.SRC_FIRMWARES).setDevice(device) })
            }
        }

        override fun getItemCount(): Int = pages.size

        override fun createFragment(position: Int): Fragment = pages[position].second()

        fun getPageTitle(position: Int): String = pages[position].first
    }


    inner class ImagesAdapter(
            context: Context,
            private val urls: ArrayList<String>,
            private var fullUrls: ArrayList<String>
    ) : androidx.viewpager.widget.PagerAdapter() {
        //private SparseArray<View> views = new SparseArray<>();
        private val inflater: LayoutInflater = LayoutInflater.from(context)


        override fun getCount(): Int {
            return urls.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val binding = DeviceImagePageBinding.inflate(inflater, container, false)
            binding.root.setOnClickListener {
                ImageViewerActivity.startActivity(this@DeviceFragment.requireContext(), fullUrls, position)
            }
            container.addView(binding.root, 0)
            loadImage(binding, position)
            return binding.root
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }

        private fun loadImage(binding: DeviceImagePageBinding, position: Int) {
            ForPdaCoil.loadIntoWithProgress(binding.imageView, urls[position], binding.progressBar)
        }
    }

    companion object {
        const val ARG_DEVICE_ID = "DEVICE_ID"
    }

}
