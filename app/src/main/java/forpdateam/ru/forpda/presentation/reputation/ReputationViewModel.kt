package forpdateam.ru.forpda.presentation.reputation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.RepItem
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.reactivex.disposables.CompositeDisposable

class ReputationViewModel(
        private val reputationRepository: ReputationRepository,
        private val avatarRepository: AvatarRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    @Volatile
    private var reputationView: ReputationView? = null

    fun attachView(view: ReputationView) {
        reputationView = view
    }

    fun detachView() {
        reputationView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var currentData = RepData()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadReputation()
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    fun loadReputation() {
        reputationRepository
                .loadReputation(currentData.id, currentData.mode, currentData.sort, currentData.pagination.st)
                .doOnSubscribe { reputationView?.setRefreshing(true) }
                .doAfterTerminate { reputationView?.setRefreshing(false) }
                .subscribe({
                    currentData = it
                    reputationView?.showReputation(it)
                    tryShowAvatar(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun changeReputation(type: Boolean, message: String) {
        reputationRepository
                .changeReputation(0, currentData.id, type, message)
                .doOnSubscribe { reputationView?.setRefreshing(true) }
                .doAfterTerminate { reputationView?.setRefreshing(false) }
                .subscribe({
                    reputationView?.onChangeReputation(it)
                    loadReputation()
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    private fun tryShowAvatar(data: RepData) {
        avatarRepository
                .getAvatar(data.nick.orEmpty())
                .subscribe({
                    reputationView?.showAvatar(it)
                }, {
                    errorHandler.handle(it)
                })
                .also { rxSubscriptions.add(it) }
    }

    fun selectPage(page: Int) {
        currentData.pagination.st = page
        loadReputation()
    }

    fun setSort(sort: String) {
        currentData.sort = sort
        loadReputation()
    }

    fun changeReputationMode() {
        currentData.mode = if (currentData.mode == ReputationApi.MODE_FROM) ReputationApi.MODE_TO else ReputationApi.MODE_FROM
        loadReputation()
    }

    fun onItemClick(item: RepItem) {
        reputationView?.showItemDialogMenu(item)
    }

    fun onItemLongClick(item: RepItem) {
        reputationView?.showItemDialogMenu(item)
    }

    fun navigateToProfile(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router)
    }

    fun navigateToMessage(item: RepItem) {
        linkHandler.handle(item.sourceUrl, router)
    }

    class Factory(
            private val reputationRepository: ReputationRepository,
            private val avatarRepository: AvatarRepository,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ReputationViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return ReputationViewModel(reputationRepository, avatarRepository, router, linkHandler, errorHandler) as T
        }
    }
}
