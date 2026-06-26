package forpdateam.ru.forpda.ui.fragments.profile

import javax.inject.Inject
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.FragmentProfileBinding
import forpdateam.ru.forpda.databinding.ToolbarProfileBinding
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import forpdateam.ru.forpda.common.showSnackbar
import androidx.fragment.app.viewModels
import com.google.android.material.progressindicator.CircularProgressIndicator
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.BitmapUtils
import forpdateam.ru.forpda.common.LinkMovementMethod
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.presentation.profile.ProfileUiEvent
import forpdateam.ru.forpda.presentation.profile.ProfileViewModel
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.profile.adapters.ProfileAdapter
import forpdateam.ru.forpda.ui.views.ScrimHelper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.presentation.ILinkHandler
import timber.log.Timber

/**
 * Created by radiationx on 03.08.16.
 */
@AndroidEntryPoint
class ProfileFragment : TabFragment(), ProfileAdapter.ClickListener {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var linkHandler: ILinkHandler

    private var _profileBinding: FragmentProfileBinding? = null
    private val profileBinding get() = checkNotNull(_profileBinding) { "Binding accessed after onDestroyView" }
    private var _toolbarBinding: ToolbarProfileBinding? = null
    private val toolbarBinding get() = checkNotNull(_toolbarBinding) { "Binding accessed after onDestroyView" }

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var nick: TextView
    private lateinit var group: TextView
    private lateinit var sign: TextView
    private lateinit var avatar: ImageView
    private lateinit var progressView: CircularProgressIndicator

    private var blurLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var hideProgressRunnable: Runnable? = null

    private lateinit var copyLinkMenuItem: MenuItem
    private lateinit var writeMenuItem: MenuItem

    private lateinit var adapter: ProfileAdapter


    private var isResume = false
    private var isScrim = false

    private var lastBlurWidth = 0
    private var lastBlurHeight = 0

    private val presenter: ProfileViewModel by viewModels()

    init {
        configuration.fitSystemWindow = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var profileUrl: String? = null
        arguments?.apply {
            profileUrl = getString(TabFragment.ARG_TAB)
        }
        if (profileUrl.isNullOrEmpty()) {
            profileUrl = "https://4pda.to/forum/index.php?showuser=${authHolder.get().userId}"
        }
        presenter.profileUrl = profileUrl
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _profileBinding = FragmentProfileBinding.inflate(inflater, fragmentContent, true)

        val viewStub = findViewById(R.id.toolbar_content) as? ViewStub ?: throw IllegalStateException("toolbar_content ViewStub not found")
        viewStub.layoutResource = R.layout.toolbar_profile
        val inflatedView = viewStub.inflate()
        _toolbarBinding = ToolbarProfileBinding.bind(inflatedView)

        nick = toolbarBinding.profileNick
        group = toolbarBinding.profileGroup
        sign = toolbarBinding.profileSign
        avatar = toolbarBinding.profileAvatar
        recyclerView = profileBinding.profileList
        progressView = toolbarBinding.profileProgress

        val params = toolbarLayout.layoutParams as AppBarLayout.LayoutParams
        params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED
        toolbarLayout.layoutParams = params
        // Базовый fragment_base — parallax у Toolbar; для профиля нужен pin при свёрнутой шапке.
        (toolbar.layoutParams as CollapsingToolbarLayout.LayoutParams).apply {
            collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            toolbar.layoutParams = this
        }
        return viewFragment
    }

    override fun onDestroyViewBinding() {
        _profileBinding = null
        _toolbarBinding = null
        super.onDestroyViewBinding()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(recyclerView.context)
        adapter = ProfileAdapter(linkHandler)
        adapter.setClickListener(this)
        recyclerView.adapter = adapter

        toolbarLayout.setExpandedTitleColor(Color.TRANSPARENT)
        toolbarLayout.setCollapsedTitleTextColor(Color.TRANSPARENT)
        toolbarLayout.isTitleEnabled = true
        toolbarTitleView.visibility = View.GONE

        val toolbarIconColor = requireContext().getColorFromAttr(R.attr.icon_toolbar)
        val scrimHelper = ScrimHelper(appBarLayout, toolbarLayout)
        scrimHelper.setScrimListener { scrim: Boolean ->
            isScrim = scrim
            if (scrim) {
                toolbar.navigationIcon?.clearColorFilter()
                toolbar.overflowIcon?.clearColorFilter()
            } else {
                toolbar.navigationIcon?.setColorFilter(toolbarIconColor, PorterDuff.Mode.SRC_ATOP)
                toolbar.overflowIcon?.setColorFilter(toolbarIconColor, PorterDuff.Mode.SRC_ATOP)
            }
            updateStatusBar()
        }

        toolbar.navigationIcon?.setColorFilter(toolbarIconColor, PorterDuff.Mode.SRC_ATOP)
        toolbar.overflowIcon?.setColorFilter(toolbarIconColor, PorterDuff.Mode.SRC_ATOP)

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        blurLayoutListener?.let { listener ->
            _binding?.toolbarImageBackground?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            blurLayoutListener = null
        }
        hideProgressRunnable?.let { runnable ->
            if (::progressView.isInitialized) progressView.removeCallbacks(runnable)
            hideProgressRunnable = null
        }
        super.onDestroyView()
    }

    override fun isShadowVisible(): Boolean {
        return false
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        copyLinkMenuItem = menu.add(R.string.copy_link)
                .setOnMenuItemClickListener {
                    presenter.copyUrl()
                    true
                }
        writeMenuItem = menu.add(R.string.write)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_profile_toolbar_create))
                .setOnMenuItemClickListener {
                    presenter.navigateToQms()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        refreshToolbarMenuItems(false)
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        isResume = true
        updateStatusBar()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        isResume = false
        updateStatusBar()
    }

    private fun updateStatusBar() {
        if (isResume) {
            SystemBarAppearance.syncStatusBarIconContrast(
                    requireActivity(),
                    requireContext().getColorFromAttr(topBarSurfaceColorAttr())
            )
        } else {
            SystemBarAppearance.syncStatusBarIconContrast(requireActivity())
        }
    }

    override fun refreshToolbarMenuItems(enable: Boolean) {
        super.refreshToolbarMenuItems(enable)
        if (enable) {
            copyLinkMenuItem.isEnabled = true
        } else {
            copyLinkMenuItem.isEnabled = false
            writeMenuItem.isVisible = false
        }
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

    private fun handleUiEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.ShowProfile -> showProfile(event.profile)
            is ProfileUiEvent.ShowAvatar -> showAvatar(event.bitmap)
            is ProfileUiEvent.OnSaveNote -> onSaveNote(event.result)
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        super.setRefreshing(isRefreshing)
        if (isRefreshing) {
            refreshToolbarMenuItems(false)
        }
    }

    override fun onSaveClick(text: String) {
        presenter.saveNote(text)
    }

    override fun onContactClick(item: ProfileModel.Contact) {
        presenter.onContactClick(item)
    }

    override fun onDeviceClick(item: ProfileModel.Device) {
        presenter.onDeviceClick(item)
    }

    override fun onStatClick(item: ProfileModel.Stat) {
        presenter.onStatClick(item)
    }

    private fun onSaveNote(success: Boolean) {
        showSnackbar(getString(if (success) R.string.profile_note_saved else R.string.error_occurred))
    }

    private fun showProfile(data: ProfileModel) {
        refreshToolbarMenuItems(true)
        val oldCount = adapter.itemCount
        adapter.setProfile(data)
        // Delay adapter update to prevent RecyclerView crash during layout
        recyclerView.post {
            if (oldCount == 0) {
                adapter.notifyItemRangeInserted(0, adapter.itemCount)
            } else {
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
        }

        setTabTitle(String.format(getString(R.string.profile_with_Nick), data.nick))
        setTitle(data.nick)
        nick.text = data.nick
        group.text = data.group
        if (data.sign != null) {
            sign.text = data.sign
            sign.visibility = View.VISIBLE
            sign.movementMethod = LinkMovementMethod(object : LinkMovementMethod.ClickListener {
                override fun onClick(url: String): Boolean {
                    return linkHandler.handle(url, null)
                }
            })
        }

        if (!data.contacts.isEmpty()) {
            val isMe = data.id == authHolder.get().userId
            writeMenuItem.isVisible = !isMe
        }
    }

    private fun showAvatar(bitmap: Bitmap) {
        toolbarBackground.visibility = View.VISIBLE
        if (blurLayoutListener == null) {
            try {
                blurLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                    try {
                        blur(bitmap)
                    } catch (e: Exception) {
                        // Ignore errors in onGlobalLayout to prevent crashes
                    }
                }
                toolbarBackground.viewTreeObserver.addOnGlobalLayoutListener(blurLayoutListener)
            } catch (e: Exception) {
                // Ignore errors when adding listener
            }
        }

        avatar.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 500
            fillAfter = true
        })
        avatar.setImageBitmap(bitmap)

        progressView.startAnimation(AlphaAnimation(1f, 0f).apply {

        })
        hideProgressRunnable?.let { progressView.removeCallbacks(it) }
        val runnable = Runnable { progressView.visibility = View.GONE }
        hideProgressRunnable = runnable
        progressView.postDelayed(runnable, 500)
    }

    private fun blur(bkg: Bitmap) {
        val scaleFactor = 3f
        val radius = 4
        val blurWidth = toolbarBackground.width
        val blurHeight = toolbarBackground.height
        if (blurWidth <= 0 && blurHeight <= 0 || blurWidth == lastBlurWidth || blurHeight == lastBlurHeight) {
            return
        }
        lastBlurWidth = blurWidth
        lastBlurHeight = blurHeight
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    val overlay = BitmapUtils.centerCrop(bkg, lastBlurWidth, lastBlurHeight, scaleFactor)
                    BitmapUtils.fastBlur(overlay, radius, true)
                    overlay
                }
                toolbarBackground.startAnimation(AlphaAnimation(0f, 1f).apply {
                    duration = 500
                    fillAfter = true
                })
                toolbarBackground.setImageBitmap(bitmap)
            } catch (throwable: Throwable) {
                Timber.e(throwable, "Profile blur error")
                showSnackbar(throwable.message ?: "")
            }
        }
    }

}
