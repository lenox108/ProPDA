package forpdateam.ru.forpda.presentation.mentions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class MentionsViewModel(
        private val mentionsRepository: MentionsRepository,
        private val favoritesRepository: FavoritesRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var mentionsView: MentionsView? = null

    fun attachView(view: MentionsView) {
        mentionsView = view
    }

    fun detachView() {
        mentionsView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var currentSt: Int = 0

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        getMentions()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun getMentions() {
        mentionsRepository
                .getMentions(currentSt)
                .doOnSubscribe { mentionsView?.setRefreshing(true) }
                .doAfterTerminate { mentionsView?.setRefreshing(false) }
                .subscribe({
                    mentionsView?.showMentions(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun addTopicToFavorite(topicId: Int, subType: String) {
        viewModelScope.launch {
            runCatching {
                favoritesRepository.editFavorites(FavoritesApi.ACTION_ADD, -1, topicId, subType)
            }.onSuccess { mentionsView?.onAddToFavorite(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun onItemClick(item: MentionItem) {
        linkHandler.handle(item.link, router, mapOf(
                Screen.ARG_TITLE to item.title.orEmpty()
        ))
    }

    fun onItemLongClick(item: MentionItem) {
        mentionsView?.showItemDialogMenu(item)
    }

    fun copyLink(item: MentionItem) {
        Utils.copyToClipBoard(item.link)
    }

    fun addToFavorites(item: MentionItem) {
        var id = 0
        val matcher = Pattern.compile("showtopic=(\\d+)").matcher(item.link.orEmpty())
        if (matcher.find()) {
            id = matcher.group(1)?.toIntOrNull() ?: 0
        }
        mentionsView?.showAddFavoritesDialog(id)
    }

    class Factory(
            private val mentionsRepository: MentionsRepository,
            private val favoritesRepository: FavoritesRepository,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != MentionsViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return MentionsViewModel(mentionsRepository, favoritesRepository, router, linkHandler, errorHandler) as T
        }
    }
}
