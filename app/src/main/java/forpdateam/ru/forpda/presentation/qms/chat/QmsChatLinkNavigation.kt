package forpdateam.ru.forpda.presentation.qms.chat

import android.net.Uri
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.SiteUrls

/** Resolves links inside QMS messages for in-app navigation (forum topics, posts, news, profiles). */
object QmsChatLinkNavigation {

    fun resolveInAppUrl(rawUrl: String?): String? {
        val resolved = ArticleLinkResolver.resolveForNavigation(rawUrl) ?: return null
        val uri = Uri.parse(resolved)
        return if (SiteUrls.isSiteUri(uri)) resolved else null
    }
}
