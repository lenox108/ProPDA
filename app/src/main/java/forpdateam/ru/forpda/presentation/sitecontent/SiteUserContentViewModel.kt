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

    /** Тап по комментарию → открыть статью, на которой он оставлен (нативно через LinkHandler). */
    fun onCommentClick(item: SiteComment) {
        if (item.articleId > 0) {
            router.navigateTo(Screen.ArticleDetail().apply {
                articleId = item.articleId
                articleTitle = item.articleTitle
                articleUrl = item.articleUrl
            })
        } else {
            linkHandler.handle(item.articleUrl, router)
        }
    }
}
