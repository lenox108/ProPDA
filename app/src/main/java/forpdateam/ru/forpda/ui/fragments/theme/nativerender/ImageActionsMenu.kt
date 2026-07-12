package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu

/**
 * Контекстное меню действий над изображением — паритет с WebView-меню картинок из
 * [forpdateam.ru.forpda.common.webview.DialogsHelper] (лонг-тап по картинке): открыть во
 * вьюере / сохранить / открыть в браузере / скопировать ссылку. Общее для нативной темы
 * форума, QMS-чата и самого просмотрщика изображений.
 */
object ImageActionsMenu {

    /**
     * [imageUrl] must already be viewer-resolved (the full-size image, not a thumbnail).
     * [withOpenItem] adds «Открыть изображение» — pass false when already inside the viewer.
     */
    fun show(
            context: Context,
            imageUrl: String,
            systemLinkHandler: ISystemLinkHandler,
            clipboardHelper: ClipboardHelper,
            withOpenItem: Boolean = true,
    ) {
        val menu = DynamicDialogMenu<Context, String>()
        if (withOpenItem) {
            menu.addItem(context.getString(R.string.wv_open_image)) { ctx, url ->
                ImageViewerActivity.startActivity(ctx, url)
            }
        }
        menu.addItem(context.getString(R.string.wv_save_image)) { ctx, url ->
            systemLinkHandler.handleDownload(url, null, ctx)
        }
        menu.addItem(context.getString(R.string.wv_open_in_browser)) { _, url ->
            systemLinkHandler.handle(url)
        }
        menu.addItem(context.getString(R.string.share)) { ctx, url ->
            Utils.shareText(ctx, url)
        }
        menu.addItem(context.getString(R.string.wv_copy_image_link)) { _, url ->
            Utils.copyToClipBoard(url, clipboardHelper)
        }
        menu.allowAll()
        menu.show(context, context, imageUrl, null, STYLE)
    }

    // Same look as NativeTopicFragment.showM3Menu, so every native popup menu matches.
    private val STYLE = DynamicDialogMenu.Style(
            titleTextSizeSp = 18f,
            itemTextSizeSp = 16f,
            itemMinHeightDp = 48,
            contentVerticalPaddingDp = 8,
            itemVerticalPaddingDp = 8,
            titleBottomPaddingDp = 4,
    )
}
