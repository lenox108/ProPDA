package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu

/**
 * Контекстное меню действий над текстовой ссылкой — паритет с WebView-меню ссылок из
 * [forpdateam.ru.forpda.common.webview.DialogsHelper] (лонг-тап по ссылке): открыть в новой вкладке /
 * открыть в браузере / поделиться / скопировать ссылку. Общее для нативной темы форума и QMS-чата.
 */
object LinkActionsMenu {

    fun show(
            context: Context,
            url: String,
            linkHandler: ILinkHandler,
            router: TabRouter,
            systemLinkHandler: ISystemLinkHandler,
            clipboardHelper: ClipboardHelper,
    ) {
        // Quote snapbacks and old forum markup commonly carry `/forum/index.php?...` rather than
        // an absolute URL. LinkHandler can resolve that for in-app navigation, but
        // SystemLinkHandler deliberately rejects relative URLs. Resolve once before building the
        // menu so «Открыть в браузере», share and copy all receive the real link too.
        val resolvedUrl = resolveForActions(url)
        val menu = DynamicDialogMenu<Context, String>()
        menu.addItem(context.getString(R.string.wv_open_new_tab)) { _, link ->
            // Passing the router explicitly follows the established WebView context-menu path:
            // internal 4PDA links become a parallel native app tab instead of replacing the
            // currently open topic.
            linkHandler.handle(
                    link,
                    router,
                    mapOf(Screen.ARG_FORCE_NEW_TAB to true.toString()),
            )
        }
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
        menu.show(context, context, resolvedUrl, resolvedUrl, STYLE)
    }

    internal fun resolveForActions(url: String): String =
            ArticleLinkResolver.resolveForNavigation(url) ?: url

    // Same look as ImageActionsMenu / NativeTopicFragment.showM3Menu, so every native popup matches.
    private val STYLE = DynamicDialogMenu.Style(
            titleTextSizeSp = 18f,
            itemTextSizeSp = 16f,
            itemMinHeightDp = 48,
            contentVerticalPaddingDp = 8,
            itemVerticalPaddingDp = 8,
            titleBottomPaddingDp = 4,
    )
}
