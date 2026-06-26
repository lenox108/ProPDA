package forpdateam.ru.forpda.common.webview
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

import android.content.Context
import android.webkit.WebView
import androidx.core.util.Pair
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu

/**
 * Created by radiationx on 01.11.16.
 */
class DialogsHelper(
    context: Context,
    linkHandler: ILinkHandler,
    systemLinkHandler: ISystemLinkHandler,
    router: TabRouter,
    clipboardHelper: ClipboardHelper
) {
    private val dynamicDialogMenu = DynamicDialogMenu<Context, Pair<String, String>>()

    init {
        val openNewTab = context.getString(R.string.wv_open_new_tab)
        val openBrowser = context.getString(R.string.wv_open_in_browser)
        val copyUrl = context.getString(R.string.wv_copy_link)
        val openImage = context.getString(R.string.wv_open_image)
        val saveImage = context.getString(R.string.wv_save_image)
        val copyImageUrl = context.getString(R.string.wv_copy_image_link)

        // androidx.core.util.Pair exposes nullable first/second, but handleContextMenu()
        // always builds the Pair with non-null values, so `!!` documents that invariant.
        dynamicDialogMenu.addItem(openNewTab) { ctx, data -> linkHandler.handle(data.second!!, router) }
        dynamicDialogMenu.addItem(openBrowser) { ctx, data -> systemLinkHandler.handle(data.second!!) }
        dynamicDialogMenu.addItem(copyUrl) { ctx, data -> Utils.copyToClipBoard(data.second!!, clipboardHelper) }
        dynamicDialogMenu.addItem(openImage) { ctx, data ->
            ImageViewerActivity.startActivity(ctx, FourPdaImageUrls.resolveViewerUrl(data.first!!))
        }
        dynamicDialogMenu.addItem(saveImage) { ctx, data -> systemLinkHandler.handleDownload(data.second!!, null, ctx) }
        dynamicDialogMenu.addItem(copyImageUrl) { ctx, data -> Utils.copyToClipBoard(data.first!!, clipboardHelper) }
    }

    fun handleContextMenu(context: Context, type: Int, extra: String, nodeHref: String?) {
        if (BuildConfig.DEBUG) Timber.d("DialogsHelper handleContextMenu type=$type extra=$extra href=$nodeHref")
        if (type == WebView.HitTestResult.UNKNOWN_TYPE || type == WebView.HitTestResult.EDIT_TEXT_TYPE) return

        var t = type
        if (t == WebView.HitTestResult.ANCHOR_TYPE) t = WebView.HitTestResult.SRC_ANCHOR_TYPE
        if (t == WebView.HitTestResult.IMAGE_ANCHOR_TYPE) t = WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

        var anchor = false; var image = false
        when (t) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> anchor = true
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> { anchor = true; image = true }
            WebView.HitTestResult.IMAGE_TYPE -> image = true
        }

        if (image) image = !extra.contains("4pda.to/forum/style_images")
        if (!anchor && !image) return

        dynamicDialogMenu.disallowAll()
        if (anchor) { dynamicDialogMenu.allow(0); dynamicDialogMenu.allow(1); dynamicDialogMenu.allow(2) }
        if (image) { dynamicDialogMenu.allow(3); dynamicDialogMenu.allow(4); dynamicDialogMenu.allow(5) }

        val resolvedHref = nodeHref?.let { ArticleLinkResolver.resolveForNavigation(it) } ?: nodeHref
        val item = Pair(extra, resolvedHref ?: extra)
        dynamicDialogMenu.show(context, context, item)
    }
}
