package forpdateam.ru.forpda.presentation.theme

import android.net.Uri
import android.util.Log
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.normalizeEditPostBodyForEditor
import forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml
import forpdateam.ru.forpda.common.stripBbcodeQuotes
import forpdateam.ru.forpda.common.stripHtmlQuoteBlocks
import io.reactivex.disposables.CompositeDisposable
import io.appmetrica.analytics.AppMetrica
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.common.topicUrlHasNonZeroStParameter
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.app.profile.UserHolder
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import com.github.terrakok.cicerone.ResultListenerHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.TemplateManager
import forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
import forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

/**
 * Created by radiationx on 15.03.18.
 */
@OptIn(FlowPreview::class)
class ThemeViewModel(
        private val themeRepository: ThemeRepository,
        private val reputationRepository: ReputationRepository,
        private val editorRepository: PostEditorRepository,
        private val favoritesRepository: FavoritesRepository,
        private val eventsRepository: EventsRepository,
        private val userHolder: IUserHolder,
        private val authHolder: AuthHolder,
        private val topicPreferencesHolder: TopicPreferencesHolder,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val otherPreferencesHolder: OtherPreferencesHolder,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val themeTemplate: ThemeTemplate,
        private val templateManager: TemplateManager,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel(), ThemeWebCallbacks {
    @Volatile
    private var themeView: ThemeView? = null

    fun attachView(view: ThemeView) {
        themeView = view
    }

    fun detachView() {
        themeView = null
    }

    private val rxSubscriptions = io.reactivex.disposables.CompositeDisposable()



    var loadAction = ActionState.NORMAL
    var currentPage: ThemePage? = null
    var history = mutableListOf<ThemePage>()
    var themeUrl: String = ""

    /** Тема открыта по findpost; при возврате на вкладку не грузим getnewpost — иначе теряется якорь поста. */
    private var openedViaFindPostLink = false

    /** Короткий id для склейки логов одной загрузки темы (loadData / ответ / WebView). */
    private var themeLoadTraceId: String = ""

    private var themeSyncResultHandler: ResultListenerHandler? = null
    private var themePageResultHandler: ResultListenerHandler? = null

    /** Отменяем предыдущий HTTP-запрос темы, иначе два ответа гоняются и в WebView попадает случайный. */
    private val themeHttpRequests = CompositeDisposable()

    private var subscriptionsStarted = false

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        topicPreferencesHolder
                .observeShowAvatars()
                .subscribe {
                    themeView?.updateShowAvatarState(it)
                }
                .also { rxSubscriptions.add(it) }

        topicPreferencesHolder
                .observeCircleAvatars()
                .subscribe {
                    themeView?.updateTypeAvatarState(it)
                }
                .also { rxSubscriptions.add(it) }

        mainPreferencesHolder
                .observeScrollButtonEnabled()
                .subscribe {
                    themeView?.updateScrollButtonState(it)
                }
                .also { rxSubscriptions.add(it) }

        mainPreferencesHolder
                .observeWebViewFontSize()
                .subscribe {
                    themeView?.setFontSize(it)
                }
                .also { rxSubscriptions.add(it) }

        templateManager
                .observeThemeType()
                .subscribe {
                    themeView?.setStyleType(it)
                }
                .also { rxSubscriptions.add(it) }
        viewModelScope.launch {
            eventsRepository.observeEventsTab()
                    .debounce(2000L)
                    .collect { handleEvent(it) }
        }
        loadUrl(initialThemeLoadUrl())
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        themeHttpRequests.dispose()
        themeSyncResultHandler?.dispose()
        themePageResultHandler?.dispose()
        themeSyncResultHandler = null
        themePageResultHandler = null
        super.onCleared()
    }

    /** Первый заход в тему без якоря/страницы — сразу getnewpost, чтобы не зависеть от второго запроса после attach. */
    private fun initialThemeLoadUrl(): String {
        val u = themeUrl.trim()
        return if (shouldOpenTopicWithUnreadFirst(u)) appendGetNewPostParam(u) else u
    }

    private fun shouldOpenTopicWithUnreadFirst(url: String): Boolean {
        if (url.isBlank()) return false
        val u = url.lowercase()
        if (!u.contains("showtopic=")) return false
        if (u.contains("act=findpost")) return false
        if (u.contains("view=getnewpost")) return false
        if (u.contains("view=findpost")) return false
        if (Regex("[?&]p=\\d+").containsMatchIn(u)) return false
        if (Regex("[?&]pid=\\d+").containsMatchIn(u)) return false
        if (topicUrlHasNonZeroStParameter(url)) return false
        return true
    }

    private fun appendGetNewPostParam(url: String): String {
        val hashIdx = url.indexOf('#')
        val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val sep = if ('?' in base) "&" else "?"
        return "$base${sep}view=getnewpost$hash"
    }

    fun exit() {
        router.exit()
    }

    private fun handleEvent(event: TabNotification) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "handleEvent ws=${event.isWebSocket} source=${event.source} type=${event.type}")
        }
        if (!event.isWebSocket)
            return
        if (!isPageLoaded())
            return
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "handleEvent sourceId=${event.event.sourceId} selfId=${getId()}")
        }
        if (event.event.sourceId != getId())
            return
        if (event.event.userId == authHolder.get().userId)
            return

        if (event.source == NotificationEvent.Source.THEME) {
            when (event.type) {
                NotificationEvent.Type.NEW -> themeView?.onEventNew(event)
                NotificationEvent.Type.READ -> themeView?.onEventRead(event)
                NotificationEvent.Type.MENTION -> {
                }
                else -> {
                }
            }
        }
    }

    fun getPageScrollY() = currentPage?.scrollY ?: 0

    fun canQuote() = currentPage?.canQuote ?: false

    fun isPageLoaded() = currentPage != null

    fun isInFavorites() = currentPage?.isInFavorite ?: false

    fun getId() = currentPage?.id ?: -1

    private fun loadData(url: String, action: ActionState) {
        themeHttpRequests.clear()
        editorRepository.bumpEditPrefetchGeneration()
        themeLoadTraceId = UUID.randomUUID().toString().replace("-", "").take(8)
        var hatOpen = false
        var pollOpen = false
        currentPage?.let {
            hatOpen = it.isHatOpen
            pollOpen = it.isPollOpen
        }
        themeUrl = url
        val low = url.lowercase()
        openedViaFindPostLink = low.contains("view=findpost") || low.contains("act=findpost")
        loadAction = action
        themeView?.updateHistoryLastHtml()
        themeHttpRequests.add(
                themeRepository
                        .getTheme(url, true, hatOpen, pollOpen)
                        .map { themeTemplate.mapEntity(it) }
                        .doOnSubscribe { themeView?.setRefreshing(true) }
                        .doAfterTerminate { themeView?.setRefreshing(false) }
                        .subscribe({
                            onLoadData(it)
                        }, {
                            errorHandler.handle(it)
                        })
        )
    }

    private fun onLoadData(page: ThemePage) {
        if (BuildConfig.DEBUG) {
            val serverP = ThemeApi.extractScrollPostIdFromFinalTopicUrl(page.url.orEmpty())
            Log.d(
                    LOG_TAG,
                    "onLoadData trace=$themeLoadTraceId topicId=${page.id} loadAction=$loadAction anchor=${page.anchor} anchors=${page.anchors.size} finalUrl=${page.url} requestUrl=$themeUrl serverP=$serverP"
            )
        }
        if (page.pagination.current >= page.pagination.all) {
            crossScreenInteractor.onLoadTopic(page.id)
        }
        currentPage = page
        themeView?.onLoadData(page)
        if (authHolder.get().isAuth()) {
            val myId = authHolder.get().userId
            val editable = page.posts.filter { it.canEdit && it.id > 0 }
            // Сначала свои посты (что чаще всего редактируют), максимум два чужих — меньше «штурма» сервера и кэша.
            val mine = editable.filter { it.userId == myId }.map { it.id }
            val others = editable.filter { it.userId != myId }.map { it.id }.take(2)
            val prefetchIds = (mine + others).distinct()
            if (prefetchIds.isNotEmpty()) {
                editorRepository.prefetchEditForPosts(prefetchIds)
            }
        }
        if (loadAction === ActionState.NORMAL) {
            saveToHistory(page)
        }
        if (loadAction === ActionState.REFRESH || loadAction === ActionState.BACK) {
            updateHistoryLast(page)
        }
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { ok ->
                if (ok) {
                    currentPage?.isInFavorite = true
                }
                themeView?.onAddToFavorite(ok)
            }.onFailure { errorHandler.handle(it) }
        }
    }

    fun deleteTopicFromFavorite(favId: Int) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_DELETE, favId, -1, null)
            }.onSuccess { ok ->
                if (ok) {
                    currentPage?.isInFavorite = false
                }
                themeView?.onDeleteFromFavorite(ok)
            }.onFailure { errorHandler.handle(it) }
        }
    }

    private fun createEditPostForm(message: String, attachments: MutableList<AttachmentItem>): EditPostForm? = currentPage?.let {
        val form = EditPostForm()
        form.forumId = it.forumId
        form.topicId = it.id
        form.st = it.pagination.current * it.pagination.perPage
        form.message = message
        form.attachments.addAll(attachments)
        form
    }

    fun openEditPostForm(message: String, attachments: MutableList<AttachmentItem>) {
        currentPage?.let { page ->
            createEditPostForm(message, attachments)?.let {
                router.navigateTo(Screen.EditPost().apply {
                    editPostForm = it
                    themeName = page.title
                })
                themeSyncResultHandler?.dispose()
                themeSyncResultHandler = router.setResultListener(Screen.Theme.CODE_RESULT_SYNC) {
                    (it as? EditPostSyncData?)?.let { sync ->
                        if (sync.topicId == page.id) {
                            themeView?.syncEditPost(sync)
                        }
                    }
                }
                themePageResultHandler?.dispose()
                themePageResultHandler = router.setResultListener(Screen.Theme.CODE_RESULT_PAGE) {
                    (it as? ThemePage?)?.let { themePage ->
                        applyPostedThemePage(themePage, clearMessagePanel = true)
                    }
                }
            }
        }
    }

    fun openEditPostForm(postId: Int) {
        openEditPostForm(postId, null)
    }

    /**
     * [domBodyHtml] — только если явно передан (например из WebView); HTML из модели темы не подставляем —
     * это разметка поста без BBCode, из‑за неё в редакторе «пустой» текст без [b]/[code] до ответа сервера.
     */
    fun openEditPostForm(postId: Int, domBodyHtml: String?) {
        currentPage?.let { page ->
            editorRepository.kickWarmNetworkLoad(postId)
            val merged = domBodyHtml?.takeIf { it.isNotBlank() }?.trim()
            router.navigateTo(Screen.EditPost().apply {
                this.postId = postId
                topicId = page.id
                forumId = page.forumId
                st = page.st
                themeName = page.title
                initialBodyHtml = merged
            })
            themePageResultHandler?.dispose()
            themePageResultHandler = router.setResultListener(Screen.Theme.CODE_RESULT_PAGE) { result ->
                (result as? ThemePage?)?.let { p -> applyPostedThemePage(p, clearMessagePanel = false) }
            }
        }
    }

    /**
     * После отправки/редактирования поста: якорь для прокрутки + NORMAL loadAction (иначе theme.js при BACK
     * скроллит по scrollY и игнорирует entry). HTML пересобираем после добавления якоря.
     */
    private fun applyPostedThemePage(themePage: ThemePage, clearMessagePanel: Boolean) {
        themeLoadTraceId = UUID.randomUUID().toString().replace("-", "").take(8)
        ThemeApi.ensureScrollAnchorForPostedPage(themePage, null, themeLoadTraceId)
        loadAction = ActionState.NORMAL
        val mapped = themeTemplate.mapEntity(themePage)
        if (clearMessagePanel) {
            themeView?.onMessageSent()
        }
        onLoadData(mapped)
    }

    fun sendMessage(message: String, attachments: MutableList<AttachmentItem>) {
        createEditPostForm(message, attachments)?.let {
            themeLoadTraceId = UUID.randomUUID().toString().replace("-", "").take(8)
            themeView?.setMessageRefreshing(true)
            editorRepository
                    .sendPost(it, themeLoadTraceId)
                    .map { themeTemplate.mapEntity(it) }
                    .doOnSubscribe { themeView?.setMessageRefreshing(true) }
                    .doAfterTerminate { themeView?.setMessageRefreshing(false) }
                    .subscribe({
                        loadAction = ActionState.NORMAL
                        onLoadData(it)
                        themeView?.onMessageSent()
                    }, {
                        errorHandler.handle(it)
                    })
                    .also { rxSubscriptions.add(it) }
        }
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        editorRepository
                .uploadFiles(0, files, pending)
                .subscribe({
                    themeView?.onUploadFiles(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun deleteFiles(items: List<AttachmentItem>) {
        editorRepository
                .deleteFiles(0, items)
                .subscribe({
                    themeView?.onDeleteFiles(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun loadUrl(url: String) {
        loadData(url, ActionState.NORMAL)
    }

    fun reload() {
        loadData(themeUrl, ActionState.REFRESH)
    }

    /**
     * @param fromTabSwitch true — возврат на вкладку темы; для входа по findpost не перезагружаем на непрочитанное.
     */
    fun loadNewPosts(fromTabSwitch: Boolean = false) {
        if (fromTabSwitch && openedViaFindPostLink) return
        currentPage?.let {
            // Параметр против повторного использования одного и того же ответа при быстром переключении вкладок.
            val ts = System.currentTimeMillis()
            loadUrl("https://4pda.to/forum/index.php?showtopic=${it.id}&view=getnewpost&_=$ts")
        }
    }

    fun loadPage(page: Int) {
        currentPage?.let {
            var url = "https://4pda.to/forum/index.php?showtopic=${it.id}"
            if (page != 0) {
                url = "$url&st=$page"
            }
            loadUrl(url)
        }
    }

    fun backPage() {
        if (history.size <= 1) return
        history.removeAt(history.size - 1)
        val prev = history.last()
        themeUrl = prev.url.orEmpty()
        currentPage = prev
        // Перед loadUrl следующей темы loadData вызывает updateHistoryLastHtml("") — в истории html обнуляется.
        // Назад без сети рисовал бы пустой WebView; ручной refresh как раз делал loadData снова.
        if (prev.html.isNullOrBlank()) {
            loadData(themeUrl, ActionState.BACK)
        } else {
            loadAction = ActionState.BACK
            themeView?.updateView(prev)
        }
    }

    override fun onPollResultsClick() {
        val url = themeUrl
                .replaceFirst("#[^&]*", "")
                .replace("&mode=show", "")
                .replace("&poll_open=true", "") + "&mode=show&poll_open=true"
        loadUrl(url)
    }

    override fun onPollClick() {
        val url = themeUrl
                .replaceFirst("#[^&]*", "")
                .replace("&mode=show", "")
                .replace("&poll_open=true", "") + "&poll_open=true"
        loadUrl(url)
    }

    private fun saveToHistory(themePage: ThemePage) {
        history.add(themePage)
    }

    private fun updateHistoryLast(themePage: ThemePage) {
        if (history.isNotEmpty()) {
            history.last().let { prev ->
                // Не подмешиваем prev.anchors: addAll в конец делал anchor = последний = старый якорь
                // после REFRESH/BACK, хотя парсер уже выставил правильный elem_to_scroll.
                themePage.scrollY = prev.scrollY
            }
            history[history.size - 1] = themePage
        }
    }

    fun updateHistoryLastHtml(html: String, scrollY: Int) {
        if (history.isNotEmpty()) {
            history.last().let {
                it.scrollY = scrollY
                it.html = html
            }
        }
    }

    override fun shareText(text: String) {
        Utils.shareText(text)
    }

    fun copyLink() {
        currentPage?.let {
            Utils.copyToClipBoard("https://4pda.to/forum/index.php?showtopic=${it.id}")
        }
    }

    fun openSearch() {
        currentPage?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?forums=${it.forumId}&topics=${it.id}&act=search&source=pst&result=posts", router)
        }
    }

    fun openSearchMyPosts() {
        currentPage?.let {
            var url = ("https://4pda.to/forum/index.php?forums=${it.forumId}&topics=${it.id}&act=search&source=pst&result=posts&username=")

            try {
                url += URLEncoder.encode(userHolder.user?.nick.orEmpty(), "windows-1251")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }

            linkHandler.handle(url, router)
        }
    }

    fun openForum() {
        currentPage?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showforum=${it.forumId}", router)
        }
    }


    private fun getPostById(postId: Int): IBaseForumPost? = currentPage
            ?.posts
            ?.firstOrNull {
                it.id == postId
            }

    override fun onFirstPageClick() {
        themeView?.firstPage()
    }

    override fun onPrevPageClick() {
        themeView?.prevPage()
    }

    override fun onNextPageClick() {
        themeView?.nextPage()
    }

    override fun onLastPageClick() {
        themeView?.lastPage()
    }

    override fun onSelectPageClick() {
        themeView?.selectPage()
    }

    override fun onUserMenuClick(postId: Int) {
        getPostById(postId)?.let { themeView?.showUserMenu(it) }
    }

    override fun onReputationMenuClick(postId: Int) {
        getPostById(postId)?.let { themeView?.showReputationMenu(it) }
    }

    override fun onPostMenuClick(postId: Int) {
        getPostById(postId)?.let { themeView?.showPostMenu(it) }
    }

    override fun onReportPostClick(postId: Int) {
        getPostById(postId)?.let { themeView?.reportPost(it) }
    }

    override fun onReplyPostClick(postId: Int) {
        getPostById(postId)?.let {
            val text = "[snapback]${it.id}[/snapback] [b]${it.nick},[/b] \n"
            themeView?.insertText(text)
        }
    }

    override fun onQuotePostClick(postId: Int, text: String) {
        getPostById(postId)?.let {
            val date = Utils.getForumDateTime(Utils.parseForumDateTime(it.date))
            val trimmed = text.trim()
            // HTML из WebView (selectionToQuote), а не «5 < 6» или сырой BBCode без тегов.
            val looksLikeSelectionHtml = Regex("""(?i)<(?:div|p|br|img|span|a|strong|b|blockquote)\b""").containsMatchIn(trimmed)
            // Всегда нормализуем тело: без HTML-пути служебные строки «Сообщение отредактировал…» не снимались.
            val body = stripBbcodeQuotes(
                    if (looksLikeSelectionHtml) {
                        normalizeEditPostBodyFromDomHtml(trimmed)
                    } else {
                        normalizeEditPostBodyForEditor(trimmed)
                    }
            )
            val insert = "[quote name=\"${it.nick}\" date=\"$date\" post=${it.id}]$body[/quote]\n"
            themeView?.insertText(insert)
        }
    }

    override fun onQuoteFullPostClick(postId: Int) {
        getPostById(postId)?.let { post ->
            val raw = post.body.orEmpty()
            val withoutQuotesHtml = stripHtmlQuoteBlocks(raw)
            val normalized = normalizeEditPostBodyFromDomHtml(withoutQuotesHtml).ifEmpty {
                normalizeEditPostBodyFromDomHtml(raw)
            }
            val body = stripBbcodeQuotes(normalized).ifEmpty { normalized }
            onQuotePostClick(postId, body)
        }
    }

    override fun onDeletePostClick(postId: Int) {
        getPostById(postId)?.let { themeView?.deletePost(it) }
    }

    override fun onEditPostClick(postId: Int) {
        getPostById(postId)?.let { themeView?.editPost(it) }
    }

    override fun onVotePostClick(postId: Int, type: Boolean) {
        getPostById(postId)?.let { themeView?.votePost(it, type) }
    }

    override fun onSpoilerCopyLinkClick(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let { themeView?.openSpoilerLinkDialog(it, spoilNumber) }
    }

    override fun onAnchorClick(postId: Int, name: String) {
        getPostById(postId)?.let { themeView?.openAnchorDialog(it, name) }
    }

    override fun onPollHeaderClick(bValue: Boolean) {
        currentPage?.let { it.isPollOpen = bValue }
    }

    override fun onHatHeaderClick(bValue: Boolean) {
        currentPage?.let { it.isHatOpen = bValue }
    }

    override fun setHistoryBody(index: Int, body: String) {
        history[index].html = body
    }

    override fun copyText(text: String) {
        Utils.copyToClipBoard(text)
    }

    override fun toast(text: String) {
        //themeView?.toast(text)
        router.showSystemMessage(text)
    }

    override fun log(text: String) {
        themeView?.log(text)
    }

    private val LOG_TAG = ThemeFragmentWeb::class.java.simpleName

    fun getThemeLoadTraceId(): String = themeLoadTraceId

    /**
     * Ссылка на конкретный пост: view/act=findpost или короткий вид ?showtopic=&p= / &pid= (без act ответа/жалобы и т.д.).
     */
    private fun isFindPostNavigation(uri: Uri): Boolean {
        val view = uri.getQueryParameter("view")
        val act = uri.getQueryParameter("act")
        if (view == "findpost" || act == "findpost") return true
        val hasPid = !uri.getQueryParameter("pid").isNullOrBlank()
                || !uri.getQueryParameter("p").isNullOrBlank()
        if (!hasPid) return false
        if (act != null) {
            when (act.lowercase(Locale.ROOT)) {
                "post", "report", "qms", "fav", "zmod", "search" -> return false
            }
        }
        if (view != null && view != "findpost") return false
        return true
    }

    fun handleNewUrl(uri: Uri) {
        Log.d(LOG_TAG, "handle $uri")
        val url = uri.toString()
        try {
            if (checkIsPoll(url)) {
                return
            }
            if (SiteUrls.isSiteHost(uri.host)) {
                if (uri.pathSegments.getOrNull(0) == "forum") {
                    var param: String? = uri.getQueryParameter("showtopic")
                    Log.d(LOG_TAG, "param showtopic: $param")
                    if (param != null && param != Uri.parse(themeUrl).getQueryParameter("showtopic")) {
                        loadUrl(url)
                        return
                    }
                    if (isFindPostNavigation(uri)) {
                        var postId: String? = uri.getQueryParameter("pid")
                        if (postId == null)
                            postId = uri.getQueryParameter("p")
                        Log.d(LOG_TAG, "param pid|p: $postId")
                        if (postId != null) {
                            postId = postId.replace("[^\\d][\\s\\S]*?".toRegex(), "")
                        }
                        Log.d(LOG_TAG, "param postId: $postId")
                        if (postId != null && getPostById(Integer.parseInt(postId.trim { it <= ' ' })) != null) {
                            val matcher = ThemeApi.elemToScrollPattern.matcher(url)
                            var elem: String? = null
                            while (matcher.find()) {
                                elem = matcher.group(1)
                            }
                            Log.d(LOG_TAG, " scroll to $postId : $elem")
                            val finalAnchor = (if (elem == null) "entry" else "") + if (elem != null) elem else postId
                            currentPage?.let {
                                if (topicPreferencesHolder.getAnchorHistory()) {
                                    it.addAnchor(finalAnchor)
                                }
                            }

                            themeView?.scrollToAnchor(finalAnchor)
                            return
                        } else {
                            loadUrl(url)
                            return
                        }
                    }
                }
            }

            if (ThemeApi.attachImagesPattern.matcher(url).find()) {
                currentPage?.let {
                    for (post in it.posts) {
                        for (image in post.attachImages) {
                            if (image.first.contains(url)) {
                                val list = ArrayList<String>()
                                for (attaches in post.attachImages) {
                                    list.add(attaches.first)
                                }
                                ImageViewerActivity.startActivity(App.getContext(), list, post.attachImages.indexOf(image))
                                return
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            AppMetrica.reportError("${ex.message ?: ex.toString()}; uri $uri", ex)
            //ACRA.getErrorReporter().handleException(ex)
        }
        linkHandler.handle(url, router)
    }

    private fun checkIsPoll(url: String): Boolean {
        currentPage?.let {
            val m = Pattern.compile("4pda.to.*?addpoll=1").matcher(url)
            if (m.find()) {
                var uri = Uri.parse(url)
                uri = uri.buildUpon()
                        .appendQueryParameter("showtopic", Integer.toString(it.id))
                        .appendQueryParameter("st", "" + it.pagination.current * it.pagination.perPage)
                        .build()
                loadUrl(uri.toString())
                return true
            }
        }
        return false
    }


    fun onClickDeleteInFav() {
        currentPage?.let { themeView?.showDeleteInFavDialog(it) }
    }

    fun onClickAddInFav() {
        currentPage?.let { themeView?.showAddInFavDialog(it) }
    }

    fun onBackPressed(): Boolean {
        if (topicPreferencesHolder.getAnchorHistory()) {
            currentPage?.let {
                if (it.anchors.size > 1) {
                    it.removeAnchor()
                    themeView?.scrollToAnchor(it.anchor)
                    return true
                }
            }
        }
        if (history.size > 1) {
            backPage()
            return true
        }
        return false
    }


    override fun openProfile(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${it.userId}", router)
        }
    }

    override fun openQms(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?act=qms&amp;mid=${it.userId}", router)
        }
    }

    override fun openSearchUserTopic(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle(SearchSettings().apply {
                source = SearchSettings.SOURCE_ALL.first
                nick = it.nick
                result = SearchSettings.RESULT_TOPICS.first
            }.toUrl(), router)
        }
    }

    override fun openSearchInTopic(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle(SearchSettings().apply {
                addForum(Integer.toString(it.forumId))
                addTopic(Integer.toString(it.topicId))
                source = SearchSettings.SOURCE_CONTENT.first
                nick = it.nick
                result = SearchSettings.RESULT_POSTS.first
                subforums = SearchSettings.SUB_FORUMS_FALSE
            }.toUrl(), router)
        }
    }

    override fun openSearchUserMessages(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle(SearchSettings().apply {
                source = SearchSettings.SOURCE_CONTENT.first
                nick = it.nick
                result = SearchSettings.RESULT_POSTS.first
                subforums = SearchSettings.SUB_FORUMS_FALSE
            }.toUrl(), router)
        }
    }

    override fun onChangeReputationClick(postId: Int, type: Boolean) {
        getPostById(postId)?.let { themeView?.showChangeReputation(it, type) }
    }

    override fun changeReputation(postId: Int, type: Boolean, message: String) {
        getPostById(postId)?.let {
            reputationRepository
                    .changeReputation(it.id, it.userId, type, message)
                    .subscribe({
                        router.showSystemMessage(App.get().getString(R.string.reputation_changed))
                    }, {
                        errorHandler.handle(it)
                    })
                    .also { rxSubscriptions.add(it) }
        }
    }

    override fun votePost(postId: Int, type: Boolean) {
        getPostById(postId)?.let {
            themeRepository
                    .votePost(it.id, type)
                    .subscribe({
                        router.showSystemMessage(it)
                    }, {
                        errorHandler.handle(it)
                    })
                    .also { rxSubscriptions.add(it) }
        }
    }

    override fun openReputationHistory(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?act=rep&view=history&amp;mid=${it.userId}", router)
        }
    }

    override fun quoteFromBuffer(postId: Int) {
        getPostById(postId) ?: return
        val text = Utils.readFromClipboard()
        if (text.isNullOrEmpty()) {
            router.showSystemMessage(App.get().getString(R.string.quote_clipboard_empty))
            return
        }
        onQuotePostClick(postId, text)
    }

    override fun reportPost(postId: Int, message: String) {
        getPostById(postId)?.let { post ->
            currentPage?.let {
                themeRepository
                        .reportPost(it.id, post.id, message)
                        .subscribe({
                            router.showSystemMessage("Жалоба отправлена")
                        }, {
                            errorHandler.handle(it)
                        })
                        .also { rxSubscriptions.add(it) }
            }
        }
    }

    override fun deletePost(postId: Int) {
        getPostById(postId)?.let { post ->
            themeRepository
                    .deletePost(post.id)
                    .subscribe({
                        if (it) {
                            themeView?.deletePostUi(post)
                        }
                        router.showSystemMessage(App.get().getString(R.string.message_deleted))
                    }, {
                        errorHandler.handle(it)
                    })
                    .also { rxSubscriptions.add(it) }
        }
    }

    override fun createNote(postId: Int) {
        getPostById(postId)?.let {
            val themeTitle: String = currentPage?.title.orEmpty()
            val title = String.format(App.get().getString(R.string.post_Topic_Nick_Number), themeTitle, it.nick, it.id)
            val url = "https://4pda.to/forum/index.php?s=&showtopic=" + it.topicId + "&view=findpost&p=" + it.id
            themeView?.showNoteCreate(title, url)
        }
    }

    override fun copyPostLink(postId: Int) {
        getPostById(postId)?.let {
            val url = "https://4pda.to/forum/index.php?s=&showtopic=${it.topicId}&view=findpost&p=${it.id}"
            copyText(url)
        }
    }

    override fun sharePostLink(postId: Int) {
        getPostById(postId)?.let {
            val url = "https://4pda.to/forum/index.php?s=&showtopic=${it.topicId}&view=findpost&p=${it.id}"
            shareText(url)
        }
    }

    override fun copyAnchorLink(postId: Int, name: String) {
        getPostById(postId)?.let {
            val url = "https://4pda.to/forum/index.php?act=findpost&pid=${it.id}&anchor=$name"
            copyText(url)
        }
    }

    override fun copySpoilerLink(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let {
            val url = "https://4pda.to/forum/index.php?act=findpost&pid=${it.id}&anchor=Spoil-${it.id}-$spoilNumber"
            copyText(url)
        }
    }

    enum class ActionState(private val id: Int) {
        BACK(0),
        REFRESH(2),
        NORMAL(2);

        override fun toString() = id.toString()
    }

    class Factory(
            private val themeRepository: ThemeRepository,
            private val reputationRepository: ReputationRepository,
            private val editorRepository: PostEditorRepository,
            private val favoritesRepository: FavoritesRepository,
            private val eventsRepository: EventsRepository,
            private val userHolder: IUserHolder,
            private val authHolder: AuthHolder,
            private val topicPreferencesHolder: TopicPreferencesHolder,
            private val mainPreferencesHolder: MainPreferencesHolder,
            private val otherPreferencesHolder: OtherPreferencesHolder,
            private val crossScreenInteractor: CrossScreenInteractor,
            private val themeTemplate: ThemeTemplate,
            private val templateManager: TemplateManager,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ThemeViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return ThemeViewModel(
                    themeRepository,
                    reputationRepository,
                    editorRepository,
                    favoritesRepository,
                    eventsRepository,
                    userHolder,
                    authHolder,
                    topicPreferencesHolder,
                    mainPreferencesHolder,
                    otherPreferencesHolder,
                    crossScreenInteractor,
                    themeTemplate,
                    templateManager,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}