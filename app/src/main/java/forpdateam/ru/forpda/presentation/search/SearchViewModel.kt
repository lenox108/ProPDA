package forpdateam.ru.forpda.presentation.search

import android.content.Context
import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.model.repository.search.SearchRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.presentation.theme.TopicOpenContext
import forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver
import forpdateam.ru.forpda.presentation.theme.TopicUserOpenAction
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.fragments.theme.ThemeDialogsHelper_V2
import forpdateam.ru.forpda.presentation.theme.ThemeWebCallbacks
import timber.log.Timber
import forpdateam.ru.forpda.ui.TemplateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val searchRepository: SearchRepository,
        private val editPostRepository: PostEditorRepository,
        private val favoritesRepository: FavoritesRepository,
        private val themeRepository: ThemeRepository,
        private val reputationRepository: ReputationRepository,
        private val topicPreferencesHolder: TopicPreferencesHolder,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val otherPreferencesHolder: OtherPreferencesHolder,
        private val searchTemplate: SearchTemplate,
        private val templateManager: TemplateManager,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel(), ThemeWebCallbacks {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _currentData = MutableStateFlow<SearchResult?>(null)
    val currentData: StateFlow<SearchResult?> = _currentData.asStateFlow()

    private val _uiEvents = MutableSharedFlow<SearchUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<SearchUiEvent> = _uiEvents.asSharedFlow()

    private var subscriptionsStarted = false
    private val expandedPostIds = mutableSetOf<Int>()

    companion object {
        const val FIELD_RESOURCE = "resource"
        const val FIELD_RESULT = "result"
        const val FIELD_SORT = "sort"
        const val FIELD_SOURCE = "source"
    }

    private val resourceItems = listOf<String>(SearchSettings.RESOURCE_FORUM.second, SearchSettings.RESOURCE_NEWS.second)
    private val resultItems = listOf<String>(SearchSettings.RESULT_TOPICS.second, SearchSettings.RESULT_POSTS.second)
    private val sortItems = listOf<String>(SearchSettings.SORT_DA.second, SearchSettings.SORT_DD.second, SearchSettings.SORT_REL.second)
    private val sourceItems = listOf<String>(SearchSettings.SOURCE_ALL.second, SearchSettings.SOURCE_TITLES.second, SearchSettings.SOURCE_CONTENT.second)

    private val fields = mapOf(
            FIELD_RESOURCE to resourceItems,
            FIELD_RESULT to resultItems,
            FIELD_SORT to sortItems,
            FIELD_SOURCE to sourceItems
    )

    private var settings = SearchSettings()
    private var hasRouteSettings = false
    private var scopedForumTitle: String? = null

    fun emitFillSettings() {
        _uiEvents.tryEmit(SearchUiEvent.FillSettingsData(settings, fields))
    }

    fun initSearchSettings(url: String?, forumTitle: String? = null) {
        if (!url.isNullOrEmpty()) {
            hasRouteSettings = true
            settings = SearchSettings.parseSettings(SearchSettings(), url)
            scopedForumTitle = forumTitle?.takeIf { it.isNotBlank() }
        }
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        scope.launch {
            if (!hasRouteSettings) {
                val savedSettings = otherPreferencesHolder.getSearchSettings()
                if (savedSettings.isNotEmpty()) {
                    settings = SearchSettings.parseSettings(SearchSettings(), savedSettings)
                }
            }
            topicPreferencesHolder.observeShowAvatarsFlow().collect {
                _uiEvents.emit(SearchUiEvent.UpdateShowAvatarState(it))
            }
        }

        scope.launch {
            topicPreferencesHolder.observeCircleAvatarsFlow().collect {
                _uiEvents.emit(SearchUiEvent.UpdateTypeAvatarState(it))
            }
        }

        scope.launch {
            mainPreferencesHolder.observeWebViewFontSizeFlow().collect {
                _uiEvents.emit(SearchUiEvent.SetFontSize(it))
            }
        }

        scope.launch {
            mainPreferencesHolder.observeAppFontModeFlow().collect {
                _uiEvents.emit(SearchUiEvent.SetAppFontMode(it))
            }
        }

        scope.launch {
            templateManager.observeThemeTypeFlow().collect {
                _uiEvents.emit(SearchUiEvent.SetStyleType(it))
            }
        }
        _uiEvents.tryEmit(SearchUiEvent.FillSettingsData(settings, fields))
        refreshData()
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun refreshData() {
        if (settings.query.isEmpty() && settings.nick.isNullOrEmpty() && settings.userId <= 0) {
            scope.launch { _uiEvents.emit(SearchUiEvent.ShowInitialState) }
            return
        }
        val withHtml = settings.result == SearchSettings.RESULT_POSTS.first && settings.resourceType.equals(SearchSettings.RESOURCE_FORUM.first)
        scope.launch {
            _refreshing.value = true
            _uiEvents.emit(SearchUiEvent.OnStartSearch(settings))
            try {
                val result = searchRepository.getSearch(settings)
                expandedPostIds.retainAll(result.items.mapTo(mutableSetOf()) { it.id })
                val data = if (withHtml) searchTemplate.mapEntity(result, expandedPostIds) else result
                _currentData.value = data
                _uiEvents.emit(SearchUiEvent.ShowData(data))
            } catch (e: Exception) {
                var message: String? = null
                errorHandler.handle(e) { _, handledMessage -> message = handledMessage }
                _uiEvents.emit(SearchUiEvent.ShowLoadError(message))
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun search(query: String, nick: String) {
        settings.st = 0
        settings.query = query
        settings.nick = nick
        refreshData()
    }

    fun search(pageNumber: Int) {
        settings.st = pageNumber
        refreshData()
    }

    fun updateSettings(field: String, position: Int) {
        when (field) {
            FIELD_RESOURCE -> {
                val name = resourceItems[position]
                when {
                    checkName(name, SearchSettings.RESOURCE_NEWS) -> {
                        settings.resourceType = SearchSettings.RESOURCE_NEWS.first
                        _uiEvents.tryEmit(SearchUiEvent.SetNewsMode)
                    }
                    checkName(name, SearchSettings.RESOURCE_FORUM) -> {
                        settings.resourceType = SearchSettings.RESOURCE_FORUM.first
                        _uiEvents.tryEmit(SearchUiEvent.SetForumMode)
                    }
                }
            }
            FIELD_RESULT -> {
                val name = resultItems[position]
                when {
                    checkName(name, SearchSettings.RESULT_TOPICS) -> settings.result = SearchSettings.RESULT_TOPICS.first
                    checkName(name, SearchSettings.RESULT_POSTS) -> settings.result = SearchSettings.RESULT_POSTS.first
                }
            }
            FIELD_SORT -> {
                val name = sortItems[position]
                when {
                    checkName(name, SearchSettings.SORT_DA) -> settings.sort = SearchSettings.SORT_DA.first
                    checkName(name, SearchSettings.SORT_DD) -> settings.sort = SearchSettings.SORT_DD.first
                    checkName(name, SearchSettings.SORT_REL) -> settings.sort = SearchSettings.SORT_REL.first
                }
            }
            FIELD_SOURCE -> {
                val name = sourceItems[position]
                when {
                    checkName(name, SearchSettings.SOURCE_ALL) -> settings.source = SearchSettings.SOURCE_ALL.first
                    checkName(name, SearchSettings.SOURCE_TITLES) -> settings.source = SearchSettings.SOURCE_TITLES.first
                    checkName(name, SearchSettings.SOURCE_CONTENT) -> settings.source = SearchSettings.SOURCE_CONTENT.first
                }
            }
        }
    }

    private fun checkName(arg: String, pair: Pair<String, String>): Boolean {
        return arg == pair.second
    }

    suspend fun saveSettings() {
        val saveSettings = SearchSettings()
        saveSettings.resourceType = settings.resourceType
        saveSettings.result = settings.result
        saveSettings.sort = settings.sort
        saveSettings.source = settings.source
        saveSettings.subforums = settings.subforums
        settings.forums.forEach { saveSettings.addForum(it) }
        settings.topics.forEach { saveSettings.addTopic(it) }
        val saveUrl = saveSettings.toUrl()
        otherPreferencesHolder.setSearchSettings(saveUrl)
    }

    fun scopedForumTitleForUi(): String? = scopedForumTitle

    fun onItemClick(item: SearchItem) {
        val url = buildItemClickUrl(item)
        linkHandler.handle(url, router, themeOpenArgsForSearchUrl(url))
    }

    private fun buildItemClickUrl(item: SearchItem): String {
        if (settings.resourceType == SearchSettings.RESOURCE_NEWS.first) {
            return "https://4pda.to/index.php?p=${item.id}"
        }
        if (settings.isBroadUserSearch() && item.topicId > 0) {
            return settings.userPostsInTopicSearchUrl(item)
        }
        return if (item.id != 0) {
            buildSearchFindPostTopicUrl(item.topicId, item.id)
        } else {
            "https://4pda.to/forum/index.php?showtopic=${item.topicId}"
        }
    }

    fun onItemLongClick(item: SearchItem) {
        _uiEvents.tryEmit(SearchUiEvent.ShowItemDialogMenu(item, settings))
    }

    fun copyLink() {
        clipboardHelper.copyToClipboard(settings.toUrl())
    }

    fun copyLink(item: IBaseForumPost) {
        val url = if (settings.resourceType.equals(SearchSettings.RESOURCE_NEWS.first)) {
            "https://4pda.to/index.php?p=${item.id}"
        } else {
            buildString {
                append("https://4pda.to/forum/index.php?showtopic=${item.topicId}")
                if (item.id != 0) {
                    append("&view=findpost&p=${item.id}")
                }
            }
        }
        clipboardHelper.copyToClipboard(url)
    }

    fun openTopicBegin(item: IBaseForumPost) {
        linkHandler.handle(
                TopicOpenTargetResolver.resolve(
                        TopicOpenContext(
                                rawUrl = "https://4pda.to/forum/index.php?showtopic=${item.topicId}",
                                setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.FIRST_PAGE,
                                sourceScreen = "search",
                                userAction = TopicUserOpenAction.FIRST_PAGE
                        )
                ).url,
                router
        )
    }

    fun openTopicNew(item: IBaseForumPost) {
        linkHandler.handle(
                TopicOpenTargetResolver.resolve(
                        TopicOpenContext(
                                rawUrl = "https://4pda.to/forum/index.php?showtopic=${item.topicId}",
                                setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.LAST_UNREAD,
                                sourceScreen = "search",
                                userAction = TopicUserOpenAction.UNREAD
                        )
                ).url,
                router
        )
    }

    fun openTopicLast(item: IBaseForumPost) {
        linkHandler.handle(
                TopicOpenTargetResolver.resolve(
                        TopicOpenContext(
                                rawUrl = "https://4pda.to/forum/index.php?showtopic=${item.topicId}",
                                setting = forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.FIRST_PAGE,
                                sourceScreen = "search",
                                userAction = TopicUserOpenAction.LAST_POST
                        )
                ).url,
                router
        )
    }

    fun openForum(item: IBaseForumPost) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=${item.forumId}", router)
    }

    fun onClickAddInFav(item: IBaseForumPost) {
        _uiEvents.tryEmit(SearchUiEvent.ShowAddInFavDialog(item))
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        scope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { _uiEvents.emit(SearchUiEvent.OnAddToFavorite(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun unavailableFunction() {
        router.showSystemMessage("Действие невозможно")
    }

    override fun onPollResultsClick(url: String?) = unavailableFunction()

    override fun onPollClick() = unavailableFunction()

    override fun onPollSubmit(action: String, method: String, encodedForm: String) = unavailableFunction()

    override fun onReplyPostClick(postId: Int) = unavailableFunction()

    override fun onQuotePostClick(postId: Int, text: String, displayedDate: String?) = unavailableFunction()

    override fun onQuoteFullPostClick(postId: Int, displayedDate: String?) = unavailableFunction()

    override fun quoteFromBuffer(postId: Int) = unavailableFunction()

    override fun onPollHeaderClick(bValue: Boolean) = unavailableFunction()

    override fun onHatHeaderClick(bValue: Boolean) = unavailableFunction()

    override fun onPostContentToggle(postId: Int, expanded: Boolean) {
        if (expanded) {
            expandedPostIds.add(postId)
        } else {
            expandedPostIds.remove(postId)
        }
        // Persist expanded state for the next full HTML render (pagination/refresh).
        // Visual toggle is handled in WebView JS; avoid reloading the page on each click.
    }

    override fun setHistoryBody(index: Int, body: String) = unavailableFunction()

    override fun shareText(text: String) {
        Utils.shareText(context, text)
    }

    private fun getPostById(postId: Int): IBaseForumPost? = _currentData.value
            ?.items
            ?.firstOrNull {
                it.id == postId
            }

    private fun selectPaginationPage(pageNumber: Int) {
        settings.st = pageNumber
        refreshData()
    }

    override fun onFirstPageClick() {
        val pagination = _currentData.value?.pagination ?: return
        if (pagination.current <= 1) return
        selectPaginationPage(if (pagination.isForum) 0 else 1)
    }

    override fun onPrevPageClick() {
        val pagination = _currentData.value?.pagination ?: return
        if (pagination.current <= 1) return
        selectPaginationPage(pagination.getPage(pagination.current - if (pagination.isForum) 2 else 1))
    }

    override fun onNextPageClick() {
        val pagination = _currentData.value?.pagination ?: return
        if (pagination.current == pagination.all) return
        selectPaginationPage(pagination.getPage(pagination.current + if (pagination.isForum) 0 else 1))
    }

    override fun onLastPageClick() {
        val pagination = _currentData.value?.pagination ?: return
        if (pagination.current == pagination.all) return
        selectPaginationPage(pagination.getPage(pagination.all - if (pagination.isForum) 1 else 0))
    }

    override fun onSelectPageClick() {
        _uiEvents.tryEmit(SearchUiEvent.SelectPage)
    }

    override fun onSearchPageClick(st: Int) {
        if (st < 0) return
        selectPaginationPage(st)
    }

    override fun onUserMenuClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.ShowUserMenu(it)) }
    }

    override fun onReputationMenuClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.ShowReputationMenu(it)) }
    }

    override fun onPostMenuClick(postId: Int) {
        // Прогрев формы редактирования до клика по «Изменить» — см. ThemeViewModel.
        editPostRepository.kickWarmNetworkLoad(postId)
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.ShowPostMenu(it)) }
    }

    override fun onReportPostClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.ReportPost(it)) }
    }

    override fun onDeletePostClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.DeletePost(it)) }
    }

    override fun onEditPostClick(postId: Int) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.EditPost(it)) }
    }

    override fun onVotePostClick(postId: Int, type: Boolean) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.VotePost(it, type)) }
    }

    override fun onSpoilerCopyLinkClick(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.OpenSpoilerLinkDialog(it, spoilNumber)) }
    }

    override fun onAnchorClick(postId: Int, name: String) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.OpenAnchorDialog(it, name)) }
    }

    override fun onOpenLink(url: String) {
        if (BuildConfig.DEBUG) Timber.d("SearchViewModel.onOpenLink")
        linkHandler.handle(url, router, themeOpenArgsForSearchUrl(url))
    }

    override fun copyText(text: String) {
        clipboardHelper.copyToClipboard(text)
    }

    override fun toast(text: String) {
        router.showSystemMessage(text)
    }

    override fun log(text: String) {
        _uiEvents.tryEmit(SearchUiEvent.Log(text))
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
                userId = it.userId
                result = SearchSettings.RESULT_TOPICS.first
            }.toUrl(), router)
        }
    }

    override fun openSearchInTopic(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle(it.userPostsInTopicSearchUrl(), router)
        }
    }

    override fun openSearchUserMessages(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle(SearchSettings().apply {
                source = SearchSettings.SOURCE_ALL.first
                nick = it.nick
                userId = it.userId
                result = SearchSettings.RESULT_POSTS.first
                subforums = SearchSettings.SUB_FORUMS_TRUE
            }.toUrl(), router)
        }
    }

    override fun toggleForumBlacklist(postId: Int) = Unit

    override fun onChangeReputationClick(postId: Int, type: Boolean) {
        getPostById(postId)?.let { _uiEvents.tryEmit(SearchUiEvent.ShowChangeReputation(it, type)) }
    }

    override fun changeReputation(postId: Int, type: Boolean, message: String) {
        getPostById(postId)?.let { post ->
            scope.launch {
                try {
                    reputationRepository.changeReputation(post.id, post.userId, type, message)
                    router.showSystemMessage(R.string.reputation_changed)
                } catch (e: Exception) {
                    errorHandler.handle(e)
                }
            }
        }
    }

    override fun votePost(postId: Int, type: Boolean) {
        getPostById(postId)?.let { post ->
            scope.launch {
                try {
                    val msg = themeRepository.votePost(post.id, type)
                    router.showSystemMessage(msg)
                } catch (e: Exception) {
                    errorHandler.handle(e)
                }
            }
        }
    }

    override fun openReputationHistory(postId: Int) {
        getPostById(postId)?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?act=rep&view=history&amp;mid=${it.userId}", router)
        }
    }

    override fun reportPost(postId: Int, message: String) {
        getPostById(postId)?.let { post ->
            scope.launch {
                try {
                    themeRepository.reportPost(post.topicId, post.id, message)
                    router.showSystemMessage("Жалоба отправлена")
                } catch (e: Exception) {
                    errorHandler.handle(e)
                }
            }
        }
    }

    override fun deletePost(postId: Int) {
        getPostById(postId)?.let { post ->
            scope.launch {
                if (BuildConfig.DEBUG) Timber.d("deletePost: requested postId=${post.id} action=delete source=search")
                try {
                    val success = themeRepository.deletePost(post.id)
                    if (success) {
                        if (BuildConfig.DEBUG) Timber.d("deletePost: success postId=${post.id} source=search")
                        _uiEvents.tryEmit(SearchUiEvent.DeletePostUi(post))
                        router.showSystemMessage(R.string.message_deleted)
                    } else if (BuildConfig.DEBUG) {
                        Timber.w("deletePost: failed without exception postId=${post.id} source=search")
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Timber.w(e, "deletePost: failed postId=${post.id} source=search")
                    errorHandler.handle(e)
                }
            }
        }
    }

    override fun createNote(postId: Int) {
        getPostById(postId)?.let {
            val title = "пост ${it.topicId} ${it.nick} ${it.id}"
            val url = "https://4pda.to/forum/index.php?s=&showtopic=${it.topicId}&view=findpost&p=${it.id}"
            _uiEvents.tryEmit(SearchUiEvent.ShowNoteCreate(title, url))
        }
    }

    fun openEditPostForm(postId: Int) {
        openEditPostForm(postId, null)
    }

    fun openEditPostForm(postId: Int, domBodyHtml: String?) {
        getPostById(postId)?.let { post ->
            editPostRepository.kickWarmNetworkLoad(postId)
            val title: String = if (post is SearchItem) {
                post.title.orEmpty()
            } else {
                "пост из поиска_"
            }
            val merged = domBodyHtml?.takeIf { it.isNotBlank() }?.trim()
            router.navigateTo(Screen.EditPost().apply {
                this.postId = postId
                topicId = post.topicId
                forumId = post.forumId
                st = settings.st
                themeName = title
                initialBodyHtml = merged
            })
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
            val url = "https://4pda.to/forum/index.php?showtopic=${it.topicId}&act=findpost&pid=${it.id}&anchor=$name"
            copyText(url)
        }
    }

    override fun copySpoilerLink(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let {
            val url = "https://4pda.to/forum/index.php?showtopic=${it.topicId}&act=findpost&pid=${it.id}&anchor=Spoil-${it.id}-$spoilNumber"
            copyText(url)
        }
    }

}

sealed class SearchUiEvent {
    data class UpdateShowAvatarState(val show: Boolean) : SearchUiEvent()
    data class UpdateTypeAvatarState(val circle: Boolean) : SearchUiEvent()
    data class UpdateScrollButtonState(val enabled: Boolean) : SearchUiEvent()
    data class SetFontSize(val size: Int) : SearchUiEvent()
    data class SetAppFontMode(val mode: forpdateam.ru.forpda.ui.AppFontMode) : SearchUiEvent()
    data class SetStyleType(val styleType: String) : SearchUiEvent()
    data class FillSettingsData(val settings: SearchSettings, val fields: Map<String, List<String>>) : SearchUiEvent()
    data class OnStartSearch(val settings: SearchSettings) : SearchUiEvent()
    data class ShowData(val data: SearchResult) : SearchUiEvent()
    data class ShowLoadError(val message: String?) : SearchUiEvent()
    object ShowInitialState : SearchUiEvent()
    object SetNewsMode : SearchUiEvent()
    object SetForumMode : SearchUiEvent()
    data class ShowItemDialogMenu(val item: SearchItem, val settings: SearchSettings) : SearchUiEvent()
    data class ShowAddInFavDialog(val item: IBaseForumPost) : SearchUiEvent()
    data class OnAddToFavorite(val result: Boolean) : SearchUiEvent()
    object FirstPage : SearchUiEvent()
    object PrevPage : SearchUiEvent()
    object NextPage : SearchUiEvent()
    object LastPage : SearchUiEvent()
    object SelectPage : SearchUiEvent()
    data class ShowUserMenu(val post: IBaseForumPost) : SearchUiEvent()
    data class ShowReputationMenu(val post: IBaseForumPost) : SearchUiEvent()
    data class ShowPostMenu(val post: IBaseForumPost) : SearchUiEvent()
    data class ReportPost(val post: IBaseForumPost) : SearchUiEvent()
    data class DeletePost(val post: IBaseForumPost) : SearchUiEvent()
    data class EditPost(val post: IBaseForumPost) : SearchUiEvent()
    data class VotePost(val post: IBaseForumPost, val type: Boolean) : SearchUiEvent()
    data class OpenSpoilerLinkDialog(val post: IBaseForumPost, val spoilNumber: String) : SearchUiEvent()
    data class OpenAnchorDialog(val post: IBaseForumPost, val name: String) : SearchUiEvent()
    data class Log(val text: String) : SearchUiEvent()
    data class ShowChangeReputation(val post: IBaseForumPost, val type: Boolean) : SearchUiEvent()
    data class DeletePostUi(val post: IBaseForumPost) : SearchUiEvent()
    data class ShowNoteCreate(val title: String, val url: String) : SearchUiEvent()
}
