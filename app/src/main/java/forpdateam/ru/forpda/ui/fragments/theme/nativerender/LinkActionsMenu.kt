package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu

/**
 * Контекстное меню действий над текстовой ссылкой — паритет с WebView-меню ссылок из
 * [forpdateam.ru.forpda.common.webview.DialogsHelper] (лонг-тап по ссылке): открыть в браузере /
 * поделиться / скопировать ссылку. Общее для нативной темы форума и QMS-чата.
 */
object LinkActionsMenu {

    fun show(
            context: Context,
            url: String,
            systemLinkHandler: ISystemLinkHandler,
            clipboardHelper: ClipboardHelper,
    ) {
        val menu = DynamicDialogMenu<Context, String>()
        menu.addItem(context.getString(R.string.wv_open_in_browser)) { _, link ->
            systemLinkHandler.handle(link)
        }
        menu.addItem(context.getString(R.string.share)) { ctx, link ->
            Utils.shareText(ctx, link)
        }
        menu.addItem(context.getString(R.string.wv_copy_link)) { _, link ->
            Utils.copyToClipBoard(link, clipboardHelper)
        }
        menu.allowAll()
        menu.show(context, context, url, url, STYLE)
    }

    // Same look as ImageActionsMenu / NativeTopicFragment.showM3Menu, so every native popup matches.
    private val STYLE = DynamicDialogMenu.Style(
            titleTextSizeSp = 18f,
            itemTextSizeSp = 16f,
            itemMinHeightDp = 52,
            contentVerticalPaddingDp = 8,
            itemVerticalPaddingDp = 12,
            titleBottomPaddingDp = 4,
    )
}
