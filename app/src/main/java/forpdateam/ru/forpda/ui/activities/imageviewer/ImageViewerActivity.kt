package forpdateam.ru.forpda.ui.activities.imageviewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.chrisbanes.photoview.OnPhotoTapListener
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.databinding.ActivityImgViewerBinding
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.ui.EdgeToEdge
import javax.inject.Inject

@AndroidEntryPoint
class ImageViewerActivity : AppCompatActivity() {

    @Inject
    lateinit var systemLinkHandler: ISystemLinkHandler

    @Inject
    lateinit var clipboardHelper: ClipboardHelper

    private lateinit var binding: ActivityImgViewerBinding

    private val currentImages = mutableListOf<String>()
    private val names = mutableListOf<String>()
    private var currentIndex = 0
    private val adapter: ImageViewerAdapter = ImageViewerAdapter()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.ImageViewTheme)
        binding = ActivityImgViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Edge-to-edge: fullscreen image viewer; system bars stay transparent.
        EdgeToEdge.apply(this, binding.root, padTop = false, padBottom = false)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        binding.imageViewerPullBack.setCallback(pullBackCallback)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(binding.toolbar.context, R.drawable.ic_arrow_back_white_24dp)?.apply {
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        }

        val extUrls = mutableListOf<String>()
        intent.extras?.let { extras ->
            if (extras.containsKey(IMAGE_URLS_KEY)) {
                extras.getStringArrayList(IMAGE_URLS_KEY)?.let { extUrls.addAll(it) }
            }
        } ?: savedInstanceState?.getStringArrayList(IMAGE_URLS_KEY)?.let { extUrls.addAll(it) }

        currentImages.addAll(extUrls.map(FourPdaImageUrls::resolveViewerUrl))
        names.addAll(currentImages.map { Utils.getFileNameFromUrl(it) })

        currentIndex = (savedInstanceState?.getInt(SELECTED_INDEX_KEY, 0)
            ?: intent.extras?.getInt(SELECTED_INDEX_KEY, 0) ?: 0)
            .coerceIn(0, (currentImages.size - 1).coerceAtLeast(0))

        binding.imgViewerPager.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                updateTitle(position)
            }
        })
        adapter.setTapListener(OnPhotoTapListener { _, _, _ -> toggle() })
        adapter.setLongClickListener { position ->
            currentImages.getOrNull(position)?.let { url ->
                forpdateam.ru.forpda.ui.fragments.theme.nativerender.ImageActionsMenu
                        .show(this, url, systemLinkHandler, clipboardHelper, withOpenItem = false)
            }
        }
        adapter.bindItem(currentImages)
        binding.imgViewerPager.adapter = adapter
        binding.imgViewerPager.currentItem = currentIndex
        binding.imgViewerPager.clipChildren = false
        binding.toolbar.post { updateTitle(currentIndex) }
    }

    /** Toolbar actions over the CURRENT image: save / open in browser / copy link
     *  (the old WebView long-press image menu, now living in the viewer). */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(R.string.wv_save_image).setOnMenuItemClickListener {
            currentImageUrl()?.let { url -> systemLinkHandler.handleDownload(url, null, this) }
            true
        }
        menu.add(R.string.wv_open_in_browser).setOnMenuItemClickListener {
            currentImageUrl()?.let { url -> systemLinkHandler.handle(url) }
            true
        }
        menu.add(R.string.wv_copy_image_link).setOnMenuItemClickListener {
            currentImageUrl()?.let { url -> Utils.copyToClipBoard(url, clipboardHelper) }
            true
        }
        return true
    }

    private fun currentImageUrl(): String? = currentImages.getOrNull(currentIndex)

    private fun updateTitle(selectedPageIndex: Int) {
        if (currentImages.isEmpty() || names.isEmpty()) {
            currentIndex = 0
            binding.toolbar.title = ""
            binding.toolbar.subtitle = ""
            return
        }
        currentIndex = selectedPageIndex.coerceIn(0, currentImages.lastIndex)
        binding.toolbar.title = names.getOrNull(currentIndex).orEmpty()
        binding.toolbar.subtitle = String.format(getString(R.string.image_viewer_subtitle_Cur_All), currentIndex + 1, currentImages.size)
    }

    private fun toggle() {
        if (supportActionBar?.isShowing == true) {
            hide()
        } else {
            show()
        }
    }

    private fun hide() {
        supportActionBar?.hide()
        setShowNavigationBar(false)
    }

    private fun show() {
        supportActionBar?.show()
        setShowNavigationBar(true)
    }

    private fun setShowNavigationBar(value: Boolean) {
        val view = window.decorView
        var flags = view.systemUiVisibility
        flags = if (value) {
            flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
        } else {
            flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        view.systemUiVisibility = flags
    }

    private val pullBackCallback = object : PullBackLayout.Callback {
        override fun onPullStart() {}
        override fun onPull(@PullBackLayout.Direction direction: Int, progress: Float) {}
        override fun onPullCancel(@PullBackLayout.Direction direction: Int) {}
        override fun onPullComplete(@PullBackLayout.Direction direction: Int) {
            finish()
        }
    }

    companion object {
        const val IMAGE_URLS_KEY = "IMAGE_URLS_KEY"
        const val SELECTED_INDEX_KEY = "SELECTED_INDEX_KEY"

        @JvmStatic
        fun startActivity(context: Context, imageUrl: String) {
            val intent = Intent(context, ImageViewerActivity::class.java)
            val urls = ArrayList<String>()
            urls.add(imageUrl)
            intent.putExtra(IMAGE_URLS_KEY, urls)
            intent.flags = FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        @JvmStatic
        fun startActivity(context: Context, imageUrls: ArrayList<String>, selectedIndex: Int) {
            val intent = Intent(context, ImageViewerActivity::class.java)
            intent.putExtra(IMAGE_URLS_KEY, imageUrls)
            intent.putExtra(SELECTED_INDEX_KEY, selectedIndex)
            intent.flags = FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}
