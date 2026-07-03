package forpdateam.ru.forpda.presentation

import android.net.Uri
import android.content.ActivityNotFoundException
import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.ArticleLinkResolver
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.MimeTypeUtil
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.common.webview.UrlDecision
import forpdateam.ru.forpda.common.webview.UrlPolicy
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern

/**
 * Created by radiationx on 03.02.18.
 */
class LinkHandler(
        private val systemLinkHandler: ISystemLinkHandler,
        private val router: TabRouter
) : ILinkHandler {

    companion object {
        const val LOG_TAG = "LinkHandler"
    }

    private val forumMediaPattern by lazy { Pattern.compile("https?:\\/\\/4pda\\.to\\/forum\\/dl\\/post\\/\\d+\\/([\\s\\S]*\\.([\\s\\S]*))") }

    private val supportImagePattern by lazy { Pattern.compile("\\/\\/.*?(4pda\\.to|ggpht\\.com|googleusercontent\\.com|windowsphone\\.com|mzstatic\\.com|savepic\\.net|savepice\\.ru|savepic\\.ru|.*?\\.ibb\\.com?)\\/[\\s\\S]*?\\.(png|jpg|jpeg|gif)") }

    private val forumLofiPattern by lazy { Pattern.compile("(?:http?s?:)?\\/\\/[\\s\\S]*?(?:4pda\\.to|4pda\\.ru)\\/forum\\/lofiversion\\/[^\\?]*?\\?(t|f)(\\d+)(?:-(\\d+))?") }

    private val baseFourPdaPattern by lazy { Pattern.compile("(?:http?s?:)?\\/\\/[\\s\\S]*?(?:4pda\\.to|4pda\\.ru)[\\s\\S]*") }

    // `[^#\s]*` перед `#comment` — потому что реальная ссылка news-упоминания несёт СЛАГ статьи между
    // id и якорем: `…/458379/onlajn_watch_dogs…/#comment10653747`. Старый `(?:/#comment(\d+))?` требовал
    // `#comment` СРАЗУ после id (`458379/#comment…`), поэтому commentId не парсился и переход к коммент-
    // ответу открывал новость без якоря. Слаг/квери-хвост поглощаем до первого `#`, затем ловим #comment.
    private val sitePattern by lazy { Pattern.compile("https?:\\/\\/4pda\\.to\\/(?:.+?p=|\\d+\\/\\d+\\/\\d+\\/|[\\w\\/]*?\\/?(newer|older)\\/)(\\d+)[^#\\s]*(?:#comment(\\d+))?") }


    private fun handleDownload(url: String, name: String? = null) {
        systemLinkHandler.handleDownload(url, name)
    }

    private fun externalIntent(url: String) {
        runCatching {
            systemLinkHandler.handle(url)
        }.onFailure { e ->
            if (e is ActivityNotFoundException) {
                Timber.w("No activity found for external URL")
            }
        }
    }

    private fun navigateTo(screen: Screen, router: TabRouter?, args: Map<String, String>) {
        router?.navigateTo(screen.apply {
            args[Screen.ARG_TITLE]?.let { screen.screenTitle = it }
            args[Screen.ARG_SUBTITLE]?.let { screen.screenSubTitle = it }
        })
    }

    override fun handle(inputUrl: String?, router: TabRouter?): Boolean {
        return handle(inputUrl, router, emptyMap())
    }

    override fun handle(inputUrl: String?, router: TabRouter?, args: Map<String, String>): Boolean {
        val someRouter = router ?: this.router
        var url = inputUrl.orEmpty()
        if (BuildConfig.DEBUG) Timber.d("handle: inputUrl=${inputUrl?.take(80)}")
        if (url.isBlank() || url == "#") {
            if (BuildConfig.DEBUG) Timber.d("handle: url is blank or #")
            return false
        }
        if (url.length >= 2 && url.substring(0, 2) == "//") {
            url = "https:$url"
        } else if (!url.contains("://")) {
            url = ArticleLinkResolver.resolveForNavigation(url) ?: return false
        } else {
            url = ArticleLinkResolver.normalizeMisplacedForumPrefix(url)
        }
        url = url.replace("&amp;", "&").replace("\"", "").trim()
        if (BuildConfig.DEBUG) Timber.d("handle: corrected url=${url.take(80)}")

        when (val decision = UrlPolicy.classify(url)) {
            UrlDecision.Blocked -> {
                Timber.w("Blocked unsafe link URL")
                return true
            }
            is UrlDecision.Internal -> url = decision.normalizedUrl
            is UrlDecision.External -> {
                externalIntent(decision.normalizedUrl)
                return true
            }
        }

        if (handleMedia(url, someRouter, args)) {
            return true
        }
        url = normalizeForumUrl(url)

        val uri = Uri.parse(url)
        if (baseFourPdaPattern.matcher(url).matches() && SiteUrls.isSiteUri(uri)) {
            val path0 = uri.pathSegments.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
            if (BuildConfig.DEBUG) Timber.d("Compare path0=$path0")

            if (uri.pathSegments.isNotEmpty()) {
                when (path0) {
                    "pages" -> if (handlePages(uri, someRouter, args)) {
                        return true
                    }
                    "forum" -> if (handleForum(uri, someRouter, args)) {
                        return true
                    }
                    "devdb" -> if (handleDevDb(uri, someRouter, args)) {
                        return true
                    }
                    else -> if (handleSite(uri, someRouter, args)) {
                        return true
                    }
                }
            } else {
                if (handleSite(uri, someRouter, args)) {
                    return true
                }
            }

        }

        externalIntent(url)

        return true
    }

    override fun findScreen(url: String): String? {
        return null
    }

    private fun handleForum(uri: Uri, router: TabRouter?, args: Map<String, String>): Boolean {
        uri.getQueryParameter("showuser")?.also { param ->
            navigateTo(Screen.Profile().apply {
                profileUrl = uri.toString()
            }, router, args)
            return true
        }
        uri.getQueryParameter("showtopic")?.also {
            val themeUrl = uri.toString()
            val openIntent = args[Screen.Theme.ARG_TOPIC_OPEN_INTENT]
                    ?: if (isExplicitTopicPostUrl(themeUrl)) "explicit_post" else "fresh"
            navigateTo(Screen.Theme().apply {
                this.themeUrl = themeUrl
                topicOpenSource = args[Screen.Theme.ARG_TOPIC_OPEN_SOURCE] ?: "link"
                topicOpenIntent = openIntent
                unreadUrlFromList = args[Screen.Theme.ARG_UNREAD_URL_FROM_LIST]
                unreadPostIdFromList = args[Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST]?.toIntOrNull() ?: 0
            }, router, args)
            return true
        }

        uri.getQueryParameter("showforum")?.also { param ->
            val id = param.toIntOrNull() ?: -1
            if (id == -1) return@also
            navigateTo(Screen.Topics().apply {
                forumId = id
            }, router, args)
            return true
        }

        uri.getQueryParameter("act")?.also { param ->
            if (BuildConfig.DEBUG) Timber.d("handleForum: act=$param")
            when (param) {
                "idx" -> {
                    navigateTo(Screen.Forum(), router, args)
                }
                "qms" -> {
                    val qmsUserId = uri.getQueryParameter("mid")
                    val qmsThemeId = uri.getQueryParameter("t")
                    val qmsSettings = uri.getQueryParameter("settings")
                    if (BuildConfig.DEBUG) Timber.d("handleForum: qms detected, settings=$qmsSettings")

                    // Handle QMS alerts/notifications page
                    if (qmsSettings == "alerts") {
                        if (BuildConfig.DEBUG) Timber.d("handleForum: opening QMS alerts")
                        navigateTo(Screen.Theme().apply {
                            themeUrl = uri.toString()
                        }, router, args)
                        return true
                    }

                    if (qmsUserId == null) {
                        if (BuildConfig.DEBUG) Timber.d("handleForum: opening QmsContacts")
                        navigateTo(Screen.QmsContacts(), router, args)
                    } else {
                        val uid = qmsUserId.toIntOrNull() ?: -1
                        if (uid == -1) return true // malformed — skip navigation
                        // themeId=0 — системные оповещения (виртуальная тема), открываем список тем
                        if (qmsThemeId != null && qmsThemeId != "0") {
                            val tid = qmsThemeId.toIntOrNull() ?: -1
                            if (tid == -1) return true
                            if (BuildConfig.DEBUG) Timber.d("handleForum: opening QmsChat")
                            navigateTo(Screen.QmsChat().apply {
                                userId = uid
                                themeId = tid
                            }, router, args)
                        } else {
                            if (BuildConfig.DEBUG) Timber.d("handleForum: opening QmsThemes")
                            navigateTo(Screen.QmsThemes().apply {
                                userId = uid
                            }, router, args)
                        }
                    }
                    return true
                }
                "boardrules" -> {
                    navigateTo(Screen.ForumRules(), router, args)
                    return true
                }
                "announce" -> {
                    navigateTo(Screen.Announce().apply {
                        uri.getQueryParameter("st")?.also {
                            announceId = it.toIntOrNull() ?: -1
                        }
                        uri.getQueryParameter("f")?.also {
                            forumId = it.toIntOrNull() ?: -1
                        }
                    }, router, args)
                    return true
                }
                "search" -> {
                    navigateTo(Screen.Search().apply {
                        searchUrl = uri.toString()
                    }, router, args)
                    return true
                }
                "rep" -> {
                    navigateTo(Screen.Reputation().apply {
                        reputationUrl = uri.toString()
                    }, router, args)
                    return true
                }
                "findpost" -> {
                    val themeUrl = uri.toString()
                    navigateTo(Screen.Theme().apply {
                        this.themeUrl = themeUrl
                        topicOpenSource = args[Screen.Theme.ARG_TOPIC_OPEN_SOURCE] ?: "link"
                        topicOpenIntent = args[Screen.Theme.ARG_TOPIC_OPEN_INTENT]
                                ?: if (isExplicitTopicPostUrl(themeUrl)) "explicit_post" else "fresh"
                    }, router, args)
                    return true
                }
                "fav" -> {
                    navigateTo(Screen.Favorites(), router, args)
                    return true
                }
                "mentions" -> {
                    if (BuildConfig.DEBUG) Timber.d("handleForum: opening Mentions")
                    navigateTo(Screen.Mentions(), router, args)
                    return true
                }
            }
        }
        return false
    }

    private fun handleSite(uri: Uri, router: TabRouter?, args: Map<String, String>): Boolean {
        val matcher = sitePattern.matcher(uri.toString())
        if (matcher.find()) {
            navigateTo(Screen.ArticleDetail().apply {
                matcher.group(2)?.also {
                    articleId = it.toIntOrNull() ?: -1
                }
                matcher.group(3)?.also {
                    commentId = it.toIntOrNull() ?: -1
                }
                articleUrl = uri.toString()
                articleOpenSource = resolveArticleOpenSource(args)
            }, router, args)
            return true
        }
        if (!uri.pathSegments.isEmpty() && uri.pathSegments[0].contains("special")) {
            return false
        }
        if (uri.pathSegments.isEmpty()) {
            navigateTo(Screen.ArticleList(), router, args)
            return true
        } else if (uri.pathSegments[0].matches("news|articles|reviews|tag|software|games|review".toRegex())) {
            navigateTo(Screen.ArticleList(), router, args)
            return true
        }

        return false
    }

    private fun handlePages(uri: Uri, router: TabRouter?, args: Map<String, String>): Boolean {
        if (uri.pathSegments.size > 1 && uri.pathSegments[1].equals("go", ignoreCase = true)) {
            uri.getQueryParameter("u")?.let {
                try {
                    URLDecoder.decode(it, "UTF-8")
                } catch (ignore: Exception) {
                    it
                }
            }?.also {
                when (val decision = UrlPolicy.classify(it)) {
                    UrlDecision.Blocked -> Timber.w("Blocked unsafe redirect URL")
                    is UrlDecision.External -> externalIntent(decision.normalizedUrl)
                    is UrlDecision.Internal -> handle(decision.normalizedUrl, router, args)
                }
                return true
            }
        }
        return false
    }

    private fun handleDevDb(uri: Uri, router: TabRouter?, args: Map<String, String>): Boolean {
        if (uri.pathSegments.size > 1) {
            if (uri.pathSegments[1].matches("phones|pad|ebook|smartwatch".toRegex())) {
                if (uri.pathSegments.size > 2 && !uri.pathSegments[2].matches("new|select".toRegex())) {
                    navigateTo(Screen.DevDbDevices().apply {
                        categoryId = uri.pathSegments[1]
                        brandId = uri.pathSegments[2]
                    }, router, args)
                    return true
                }
                navigateTo(Screen.DevDbBrands().apply {
                    categoryId = uri.pathSegments[1]
                }, router, args)
                return true
            } else {
                navigateTo(Screen.DevDbDevice().apply {
                    deviceId = uri.pathSegments[1]
                }, router, args)
                return true
            }
        } else {
            navigateTo(Screen.DevDbBrands(), router, args)
            return true
        }
    }

    private fun handleMedia(url: String, router: TabRouter?, args: Map<String, String>): Boolean {
        val uri = Uri.parse(url)
        val isSiteUrl = SiteUrls.isSiteUri(uri)
        val matcher = forumMediaPattern.matcher(url)
        if (matcher.find()) {
            var fullName = matcher.group(1) ?: return false
            try {
                fullName = URLDecoder.decode(fullName, "CP1251")
            } catch (ignore: Exception) {
            }

            val extension = matcher.group(2) ?: return false
            val isImage = MimeTypeUtil.isImage(extension)
            if (isImage) {
                navigateTo(Screen.ImageViewer().apply {
                    urls.add(FourPdaImageUrls.resolveViewerUrl(url))
                }, router, args)
            } else {
                handleDownload(url, fullName)
            }
            return true
        } else if (isSiteUrl && supportImagePattern.matcher(url).find()) {
            navigateTo(Screen.ImageViewer().apply {
                urls.add(FourPdaImageUrls.resolveViewerUrl(url))
            }, router, args)
            return true
        }
        // Keep 4PDA-hosted images in the in-app viewer; third-party images go to Android.
        val lowerUrl = url.lowercase(Locale.ROOT)
        if (isSiteUrl && (
            lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".bmp") || lowerUrl.endsWith(".webp")
            )
        ) {
            navigateTo(Screen.ImageViewer().apply {
                urls.add(FourPdaImageUrls.resolveViewerUrl(url))
            }, router, args)
            return true
        }
        return false
    }

    private fun normalizeForumUrl(inputUrl: String): String {
        val matcher = forumLofiPattern.matcher(inputUrl)
        if (matcher.find()) {
            val id = matcher.group(2) ?: return inputUrl
            var url = "https://4pda.to/forum/index.php?"

            url += when (matcher.group(1)) {
                "t" -> "showtopic="
                "f" -> "showforum="
                else -> ""
            } + id

            matcher.group(3)?.also {
                url += "&st=$it"
            }
            return url
        }
        return inputUrl
    }

    private fun isExplicitTopicPostUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.contains("act=findpost")) return true
        if (lower.contains("view=findpost")) return true
        if (Regex("""(?i)[?&]pid=\d+""").containsMatchIn(trimmed)) return true
        return false
    }

    private fun resolveArticleOpenSource(args: Map<String, String>): String {
        val raw = args[Screen.Theme.ARG_TOPIC_OPEN_SOURCE]?.lowercase(Locale.ROOT).orEmpty()
        return when (raw) {
            "bookmark", "bookmarks", "note", "notes" -> "bookmark"
            "favorites", "favorite" -> "favorites"
            "search" -> "search"
            else -> "news_list"
        }
    }

}