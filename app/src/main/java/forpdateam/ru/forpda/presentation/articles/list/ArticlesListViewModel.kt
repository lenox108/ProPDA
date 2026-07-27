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
import forpdateam.ru.forpda.diagnostic.ColdStartTracer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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

    /**
     * Аватарки тянутся ЛЕНИВО, по мере показа строки, и не более [AVATAR_CONCURRENCY] за раз.
     * Раньше весь список разом уходил в `avatarRepository.getAvatar(nick)`, а каждый промах Room
     * превращался в сетевой `qms-xhr` — 10–15 параллельных запросов сразу после загрузки страницы,
     * что и приводило к 429 от анти-флуда 4pda.
     */
    private val avatarSemaphore = Semaphore(AVATAR_CONCURRENCY)
    private val avatarRequestedKeys = mutableSetOf<String>()

    /** Отпечаток ленты, показанной из кэша, — чтобы не перерисовывать её тем же самым из сети. */
    private var shownFromCacheSignature: String? = null

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
    /** A cancelled coroutine cannot interrupt IWebClient's synchronous call; serialize list GETs anyway. */
    private val listLoadMutex = Mutex()

    private fun loadArticles(page: Int, withClear: Boolean, bypassCache: Boolean = false) {
        loadJob?.cancel()
        currentPage = page
        val category = selectedCategoryId
        loadJob = scope.launch {
            try {
                _refreshing.value = true
                if (withClear && !bypassCache) {
                    showCachedListIfAny(category, page)
                }
                val items = listLoadMutex.withLock {
                    newsRepository.getNews(
                            category,
                            page,
                            bypassCache = bypassCache
                    )
                }
                // Метки видно в ColdStart-снимке: news.list.cache — момент показа ленты из кэша,
                // news.list.network — момент ответа сети. Разница между ними и есть выигрыш SWR.
                ColdStartTracer.mark("news.list.network")
                if (BuildConfig.DEBUG) {
                    ColdStartTracer.logSnapshot()
                }
                if (withClear && shownFromCacheSignature == listSignature(items)) {
                    // Сеть подтвердила ровно то, что уже показано из кэша: не пересобираем список,
                    // иначе пользователь получил бы мигание и потерю позиции скролла на ровном месте.
                    shownFromCacheSignature = null
                    onListSettled(items.firstOrNull())
                    return@launch
                }
                shownFromCacheSignature = null
                if (withClear) {
                    currentItems.clear()
                }
                currentItems.addAll(items)
                _uiEvents.emit(ArticlesListUiEvent.ShowNews(items, withClear))
                if (withClear) {
                    // Скролла ещё не было — прогреваем верхнюю карточку, самого вероятного кандидата.
                    onListSettled(items.firstOrNull())
                }
            } catch (e: Throwable) {
                var message: String? = null
                errorHandler.handle(e) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(ArticlesListUiEvent.ShowLoadError(message))
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * Stale-while-revalidate: до ответа сети отдаём ленту из кэша (память → диск), чтобы вкладка
     * открывалась с контентом, а не со скелетоном. Сетевой ответ придёт следом и заменит список.
     */
    private suspend fun showCachedListIfAny(category: String, page: Int) {
        val cached = runCatching { newsRepository.getCachedNews(category, page) }.getOrNull()
        if (cached.isNullOrEmpty()) return
        if (category != selectedCategoryId) return
        currentItems.clear()
        currentItems.addAll(cached)
        shownFromCacheSignature = listSignature(cached)
        ColdStartTracer.mark("news.list.cache")
        _uiEvents.emit(ArticlesListUiEvent.ShowNews(cached, true))
    }

    /** Компактный отпечаток ленты: по нему видно, принесла ли сеть что-то новое поверх кэша. */
    private fun listSignature(items: List<NewsItem>): String =
            items.joinToString(",") { "${it.id}:${it.commentsCount}" }

    /**
     * Догружает аватарку одного показанного автора. Уже известный аватар (его проставил
     * [NewsRepository] из кэша форум-юзеров) сети не стоит — такой элемент выходит сразу.
     */
    private fun requestAvatarIfNeeded(item: NewsItem) {
        if (!authHolder.get().isAuth()) return
        if (!item.avatar.isNullOrBlank()) return
        val nick = item.author?.trim().orEmpty()
        if (nick.isEmpty()) return
        val authorId = item.authorId
        val key = if (authorId > 0) "id:$authorId" else "nick:$nick"
        synchronized(avatarRequestedKeys) {
            if (!avatarRequestedKeys.add(key)) return
        }
        if (BuildConfig.DEBUG) {
            Timber.d("requestAvatar authorId=%d", authorId)
        }
        scope.launch {
            val url = avatarSemaphore.withPermit {
                withContext(Dispatchers.IO) {
                    runCatching { avatarRepository.getAvatar(nick, background = true) }.getOrNull()
                }
            } ?: return@launch
            applyAvatar(authorId, nick, url)
        }
    }

    private suspend fun applyAvatar(authorId: Int, nick: String, url: String) {
        val updItems = currentItems.filter { candidate ->
            candidate.avatar != url &&
                    if (authorId > 0) candidate.authorId == authorId else candidate.author?.trim() == nick
        }
        if (updItems.isEmpty()) return
        updItems.forEach { it.avatar = url }
        _uiEvents.emit(ArticlesListUiEvent.UpdateItems(updItems))
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
        // Promote the tapped row immediately; speculative visible-row warm-up has a debounce.
        articlePrefetchService.prefetchArticleNow(item.id)
        router.navigateTo(Screen.ArticleDetail().apply {
            articleId = item.id
            articleTitle = item.title
            articleAuthorNick = item.author
            articleDate = item.date
            articleImageUrl = item.imgUrl
            articleCommentsCount = item.commentsCount
        })
    }

    /** Строка показалась: аватарка догружается лениво, статья — нет (см. [onListSettled]). */
    fun onItemDisplayed(item: NewsItem) {
        requestAvatarIfNeeded(item)
    }

    /**
     * Скролл остановился: прогреваем кэш той статьи, что сейчас в центре экрана. Раньше кандидатом
     * была ПОСЛЕДНЯЯ забинденная строка — то есть карточка за нижней границей экрана, которую жмут
     * реже всего, и спекулятивный запрос уходил впустую.
     */
    fun onListSettled(item: NewsItem?) {
        val articleId = item?.id ?: return
        if (articleId > 0) {
            articlePrefetchService.prefetchArticle(articleId)
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

    private companion object {
        /** Больше двух одновременных lookup'ов ника 4pda не прощает (429). */
        const val AVATAR_CONCURRENCY = 2
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
