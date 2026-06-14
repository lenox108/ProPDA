package forpdateam.ru.forpda.presentation.articles.list

import android.content.Context
import android.content.SharedPreferences
import forpdateam.ru.forpda.presentation.BaseViewModel
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.news.Constants
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.interactors.news.ArticlePrefetchService
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ArticlesListViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val newsRepository: NewsRepository,
        private val avatarRepository: AvatarRepository,
        private val authHolder: AuthHolder,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper,
        private val preferences: SharedPreferences,
        private val articlePrefetchService: ArticlePrefetchService,
        private val crossScreenInteractor: CrossScreenInteractor
) : BaseViewModel() {

    private var subscriptionsStarted = false

    private var selectedCategoryId = Constants.normalizeNewsCategory(preferences.getString(
            Preferences.Lists.News.CATEGORY,
            Constants.NEWS_CATEGORY_ALL
    ))
    private var currentPage = 1

    private val currentItems = mutableListOf<NewsItem>()
    private val avatarsData = mutableListOf<Pair<Int, String>>()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _selectedCategory = MutableStateFlow(selectedCategoryId)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ArticlesListUiEvent>()
    val uiEvents: SharedFlow<ArticlesListUiEvent> = _uiEvents.asSharedFlow()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        scope.launch {
            crossScreenInteractor.observeArticleCommentsCount().collect { update ->
                reconcileListCommentsCount(update.articleId, update.commentsCount)
            }
        }
        loadArticles(1, withClear = true)
    }

    private var loadJob: Job? = null

    private fun loadArticles(page: Int, withClear: Boolean, bypassCache: Boolean = false) {
        loadJob?.cancel()
        currentPage = page
        loadJob = scope.launch {
            try {
                _refreshing.value = true
                val items = newsRepository.getNews(
                        selectedCategoryId,
                        currentPage,
                        bypassCache = bypassCache
                )
                if (withClear) {
                    currentItems.clear()
                }
                currentItems.addAll(items)
                _uiEvents.emit(ArticlesListUiEvent.ShowNews(items, withClear))
                loadAvatars(items)
            } catch (e: Throwable) {
                var message: String? = null
                errorHandler.handle(e) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(ArticlesListUiEvent.ShowLoadError(message))
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun loadAvatars(items: List<NewsItem>) {
        if (!authHolder.get().isAuth()) {
            return
        }
        val newAvatarsData = mutableListOf<Pair<Int, String>>()
        items.forEach { item ->
            if (avatarsData.firstOrNull { it.first == item.authorId } == null) {
                Pair(item.authorId, item.author.orEmpty()).also {
                    avatarsData.add(it)
                    newAvatarsData.add(it)
                }
            }
        }
        if (BuildConfig.DEBUG) {
            newAvatarsData.forEach {
                Timber.d("newAvatarsData ${it.first} ${it.second}")
            }
        }
        coroutineScope {
            newAvatarsData.map { avatarData ->
                async(Dispatchers.IO) {
                    val url = runCatching {
                        avatarRepository.getAvatar(avatarData.second)
                    }.getOrNull()
                    Pair(avatarData, url)
                }
            }.awaitAll().forEach { pair: Pair<Pair<Int, String>, String?> ->
                val (avatarData, url) = pair
                val updItems = currentItems
                        .filter { it.authorId == avatarData.first && it.avatar != url }
                updItems.forEach {
                    it.avatar = url
                }
                withContext(Dispatchers.Main) {
                    _uiEvents.emit(ArticlesListUiEvent.UpdateItems(updItems))
                }
            }
        }
    }

    fun refreshArticles() {
        loadArticles(1, withClear = true, bypassCache = true)
    }

    fun selectCategory(category: String) {
        if (category == selectedCategoryId) {
            return
        }
        if (!Constants.isSelectableNewsCategory(category)) {
            Timber.w("Ignoring unknown news category: %s", category)
            return
        }
        selectedCategoryId = category
        preferences.edit().putString(Preferences.Lists.News.CATEGORY, category).apply()
        _selectedCategory.value = category
        currentItems.clear()
        avatarsData.clear()
        scope.launch { _uiEvents.emit(ArticlesListUiEvent.ClearNews) }
        loadArticles(1, true)
    }

    fun loadMore() {
        loadArticles(currentPage + 1, false)
    }

    private fun reconcileListCommentsCount(articleId: Int, commentsCount: Int) {
        val updated = currentItems.filter { it.id == articleId && it.commentsCount != commentsCount }
        if (updated.isEmpty()) return
        updated.forEach { it.commentsCount = commentsCount }
        scope.launch { _uiEvents.emit(ArticlesListUiEvent.UpdateItems(updated)) }
    }

    fun onItemClick(item: NewsItem) {
        // Keep warm-up for the tapped row; cancel only competing prefetches for other ids.
        articlePrefetchService.cancelPrefetch(exceptArticleId = item.id)
        articlePrefetchService.prefetchArticle(item.id)
        router.navigateTo(Screen.ArticleDetail().apply {
            articleId = item.id
            articleTitle = item.title
            articleAuthorNick = item.author
            articleDate = item.date
            articleImageUrl = item.imgUrl
            articleCommentsCount = item.commentsCount
        })
    }

    /** Warm disk/memory cache while the row is visible so tap-to-open can hit cache (~77ms in logs). */
    fun onItemDisplayed(item: NewsItem) {
        if (item.id > 0) {
            articlePrefetchService.prefetchArticle(item.id)
        }
    }

    fun onItemLongClick(item: NewsItem) {
        scope.launch { _uiEvents.emit(ArticlesListUiEvent.ShowItemDialogMenu(item)) }
    }

    fun copyLink(item: NewsItem) {
        clipboardHelper.copyToClipboard("https://4pda.to/index.php?p=${item.id}")
    }

    fun shareLink(item: NewsItem) {
        Utils.shareText(context, "https://4pda.to/index.php?p=${item.id}")
    }

    fun openProfile(item: NewsItem) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.authorId}", router)
    }

    fun createNote(item: NewsItem) {
        val url = "https://4pda.to/index.php?p=${item.id}"
        scope.launch { _uiEvents.emit(ArticlesListUiEvent.ShowCreateNote(item.title.orEmpty(), url)) }
    }

    fun openSearch() {
        router.navigateTo(Screen.Search().apply {
            searchUrl = "https://4pda.to/?s="
        })
    }
}

sealed class ArticlesListUiEvent {
    object ClearNews : ArticlesListUiEvent()
    data class ShowNews(val items: List<NewsItem>, val withClear: Boolean) : ArticlesListUiEvent()
    data class UpdateItems(val items: List<NewsItem>) : ArticlesListUiEvent()
    data class ShowItemDialogMenu(val item: NewsItem) : ArticlesListUiEvent()
    data class ShowCreateNote(val title: String, val url: String) : ArticlesListUiEvent()
    data class ShowLoadError(val message: String?) : ArticlesListUiEvent()
}
