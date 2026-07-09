package forpdateam.ru.forpda.presentation.sitecontent

import dagger.hilt.android.lifecycle.HiltViewModel
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.entity.remote.sitecontent.SiteComment
import forpdateam.ru.forpda.model.data.remote.api.sitecontent.SiteUserContentApi
import forpdateam.ru.forpda.presentation.BaseViewModel
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SiteUserContentViewModel @Inject constructor(
        private val api: SiteUserContentApi,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
) : BaseViewModel() {

    private var url: String = ""
    private var kind: Screen.SiteUserContent.Kind = Screen.SiteUserContent.Kind.POSTS
    private var loadJob: Job? = null
    private var loaded = false

    private val _posts = MutableStateFlow<List<NewsItem>>(emptyList())
    val posts: StateFlow<List<NewsItem>> = _posts.asStateFlow()

    private val _comments = MutableStateFlow<List<SiteComment>>(emptyList())
    val comments: StateFlow<List<SiteComment>> = _comments.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun setArgs(url: String, kind: Screen.SiteUserContent.Kind) {
        this.url = url
        this.kind = kind
    }

    fun start() {
        if (loaded) return
        loaded = true
        load()
    }

    fun load() {
        if (url.isBlank()) return
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                when (kind) {
                    Screen.SiteUserContent.Kind.POSTS ->
                        _posts.value = withContext(Dispatchers.IO) { api.loadPosts(url) }
                    Screen.SiteUserContent.Kind.COMMENTS ->
                        _comments.value = withContext(Dispatchers.IO) { api.loadComments(url) }
                }
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Тап по статье (пост автора) → нативная статья (как в ленте новостей). */
    fun onPostClick(item: NewsItem) {
        router.navigateTo(Screen.ArticleDetail().apply {
            articleId = item.id
            articleTitle = item.title
            articleAuthorNick = item.author
            articleDate = item.date
            articleImageUrl = item.imgUrl
            articleCommentsCount = item.commentsCount
        })
    }

    /**
     * Тап по комментарию → открыть статью с ЯКОРЕМ на этот коммент. Роутим через
     * [ILinkHandler.handle] c URL вида `…/<articleId>/slug/#comment<id>` — это тот же путь, что у
     * нотификаций/упоминаний: LinkHandler.handleSite достаёт из URL articleId+commentId, а движок
     * статьи «вооружает» deeplink-скролл на коммент (`deeplink_comment_scroll_armed`). Ручной
     * `navigateTo(ArticleDetail)` этот скролл не запускал (открывал статью в начале).
     */
    fun onCommentClick(item: SiteComment) {
        val base = item.articleUrl.substringBefore('#')
        val url = if (item.commentId > 0) "$base#comment${item.commentId}" else item.articleUrl
        timber.log.Timber.i("SITE_CONTENT_DIAG onCommentClick cid=${item.commentId} aid=${item.articleId} url=$url")
        linkHandler.handle(url, router)
    }
}
