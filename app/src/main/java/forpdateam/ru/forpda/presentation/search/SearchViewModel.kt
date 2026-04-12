package forpdateam.ru.forpda.presentation.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.common.topicUrlWithUnreadIfPlainOpen
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
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
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.ThemeWebCallbacks
import forpdateam.ru.forpda.ui.TemplateManager
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.launch

class SearchViewModel(
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
        private val errorHandler: IErrorHandler
) : ViewModel(), ThemeWebCallbacks {

    @Volatile
    private var searchView: SearchSiteView? = null

    fun attachView(view: SearchSiteView) {
        searchView = view
    }

    fun detachView() {
        searchView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

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

    private var currentData: SearchResult? = null

    init {
        initSearchSettings(otherPreferencesHolder.getSearchSettings())
    }

    fun initSearchSettings(url: String?) {
        url?.let {
            settings = SearchSettings.parseSettings(settings, it)
        }
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        topicPreferencesHolder
                .observeShowAvatars()
                .subscribe {
                    searchView?.updateShowAvatarState(it)
                }
                .also { rxSubscriptions.add(it) }

        topicPreferencesHolder
                .observeCircleAvatars()
                .subscribe {
                    searchView?.updateTypeAvatarState(it)
                }
                .also { rxSubscriptions.add(it) }

        mainPreferencesHolder
                .observeScrollButtonEnabled()
                .subscribe {
                    searchView?.updateScrollButtonState(it)
                }
                .also { rxSubscriptions.add(it) }

        mainPreferencesHolder
                .observeWebViewFontSize()
                .subscribe {
                    searchView?.setFontSize(it)
                }
                .also { rxSubscriptions.add(it) }

        templateManager
                .observeThemeType()
                .subscribe {
                    searchView?.setStyleType(it)
                }
                .also { rxSubscriptions.add(it) }
        searchView?.fillSettingsData(settings, fields)
        refreshData()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun refreshData() {
        if (settings.query.isEmpty() && settings.nick.isEmpty()) {
            return
        }
        val withHtml = settings.result == SearchSettings.RESULT_POSTS.first && settings.resourceType.equals(SearchSettings.RESOURCE_FORUM.first)
        searchRepository
                .getSearch(settings)
                .map {
                    if (withHtml) searchTemplate.mapEntity(it) else it
                }
                .doOnSubscribe {
                    searchView?.setRefreshing(true)
                    searchView?.onStartSearch(settings)
                }
                .doAfterTerminate { searchView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    searchView?.showData(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
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
                        searchView?.setNewsMode()
                    }
                    checkName(name, SearchSettings.RESOURCE_FORUM) -> {
                        settings.resourceType = SearchSettings.RESOURCE_FORUM.first
                        searchView?.setForumMode()
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

    fun saveSettings() {
        val saveSettings = SearchSettings()
        saveSettings.resourceType = settings.resourceType
        saveSettings.result = settings.result
        saveSettings.sort = settings.sort
        saveSettings.source = settings.source
        val saveUrl = saveSettings.toUrl()
        otherPreferencesHolder.setSearchSettings(saveUrl)
    }

    fun onItemClick(item: SearchItem) {
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
        linkHandler.handle(url, router)
    }

    fun onItemLongClick(item: SearchItem) {
        searchView?.showItemDialogMenu(item, settings)
    }

    fun copyLink() {
        Utils.copyToClipBoard(settings.toUrl())
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
        Utils.copyToClipBoard(url)
    }

    fun openTopicBegin(item: IBaseForumPost) {
        linkHandler.handle(
                topicUrlWithUnreadIfPlainOpen(
                        Uri.parse("https://4pda.to/forum/index.php?showtopic=${item.topicId}")
                ),
                router
        )
    }

    fun openTopicNew(item: IBaseForumPost) {
        linkHandler.handle("https://4pda.to/forum/index.php?showtopic=${item.topicId}&view=getnewpost", router)
    }

    fun openTopicLast(item: IBaseForumPost) {
        linkHandler.handle("https://4pda.to/forum/index.php?showtopic=${item.topicId}&view=getlastpost", router)
    }

    fun openForum(item: IBaseForumPost) {
        linkHandler.handle("https://4pda.to/forum/index.php?showforum=${item.forumId}", router)
    }

    fun onClickAddInFav(item: IBaseForumPost) {
        searchView?.showAddInFavDialog(item)
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { searchView?.onAddToFavorite(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun unavailableFunction() {
        router.showSystemMessage("Действие невозможно")
    }

    override fun onPollResultsClick() = unavailableFunction()

    override fun onPollClick() = unavailableFunction()

    override fun onReplyPostClick(postId: Int) = unavailableFunction()

    override fun onQuotePostClick(postId: Int, text: String) = unavailableFunction()

    override fun onQuoteFullPostClick(postId: Int) = unavailableFunction()

    override fun quoteFromBuffer(postId: Int) = unavailableFunction()

    override fun onPollHeaderClick(bValue: Boolean) = unavailableFunction()

    override fun onHatHeaderClick(bValue: Boolean) = unavailableFunction()

    override fun setHistoryBody(index: Int, body: String) = unavailableFunction()

    override fun shareText(text: String) {
        Utils.shareText(text)
    }

    private fun getPostById(postId: Int): IBaseForumPost? = currentData
            ?.items
            ?.firstOrNull {
                it.id == postId
            }

    override fun onFirstPageClick() {
        searchView?.firstPage()
    }

    override fun onPrevPageClick() {
        searchView?.prevPage()
    }

    override fun onNextPageClick() {
        searchView?.nextPage()
    }

    override fun onLastPageClick() {
        searchView?.lastPage()
    }

    override fun onSelectPageClick() {
        searchView?.selectPage()
    }

    override fun onUserMenuClick(postId: Int) {
        getPostById(postId)?.let { searchView?.showUserMenu(it) }
    }

    override fun onReputationMenuClick(postId: Int) {
        getPostById(postId)?.let { searchView?.showReputationMenu(it) }
    }

    override fun onPostMenuClick(postId: Int) {
        getPostById(postId)?.let { searchView?.showPostMenu(it) }
    }

    override fun onReportPostClick(postId: Int) {
        getPostById(postId)?.let { searchView?.reportPost(it) }
    }

    override fun onDeletePostClick(postId: Int) {
        getPostById(postId)?.let { searchView?.deletePost(it) }
    }

    override fun onEditPostClick(postId: Int) {
        getPostById(postId)?.let { searchView?.editPost(it) }
    }

    override fun onVotePostClick(postId: Int, type: Boolean) {
        getPostById(postId)?.let { searchView?.votePost(it, type) }
    }

    override fun onSpoilerCopyLinkClick(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let { searchView?.openSpoilerLinkDialog(it, spoilNumber) }
    }

    override fun onAnchorClick(postId: Int, name: String) {
        getPostById(postId)?.let { searchView?.openAnchorDialog(it, name) }
    }

    override fun copyText(text: String) {
        Utils.copyToClipBoard(text)
    }

    override fun toast(text: String) {
        router.showSystemMessage(text)
    }

    override fun log(text: String) {
        searchView?.log(text)
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
        getPostById(postId)?.let { searchView?.showChangeReputation(it, type) }
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

    override fun reportPost(postId: Int, message: String) {
        getPostById(postId)?.let { post ->
            currentData?.let {
                themeRepository
                        .reportPost(post.topicId, post.id, message)
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
                            searchView?.deletePostUi(post)
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
            val topicTitle: String = if (it is SearchItem) {
                it.title.orEmpty()
            } else {
                "пост из поиска_"
            }
            val title = String.format(App.get().getString(R.string.post_Topic_Nick_Number), topicTitle, it.nick, it.id)
            val url = "https://4pda.to/forum/index.php?s=&showtopic=${it.topicId}&view=findpost&p=${it.id}"
            searchView?.showNoteCreate(title, url)
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

    class Factory(
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
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != SearchViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return SearchViewModel(
                    searchRepository,
                    editPostRepository,
                    favoritesRepository,
                    themeRepository,
                    reputationRepository,
                    topicPreferencesHolder,
                    mainPreferencesHolder,
                    otherPreferencesHolder,
                    searchTemplate,
                    templateManager,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}
