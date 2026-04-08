package forpdateam.ru.forpda.presentation.qms.contacts

import moxy.InjectViewState
import forpdateam.ru.forpda.common.mvp.BasePresenter
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import java.util.concurrent.TimeUnit
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter

/**
 * Created by radiationx on 11.11.17.
 */

@InjectViewState
class QmsContactsPresenter(
        private val qmsInteractor: QmsInteractor,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val countersHolder: CountersHolder,
        private val eventsRepository: EventsRepository,
        private val errorHandler: IErrorHandler
) : BasePresenter<QmsContactsView>() {

    private val localItems = mutableListOf<QmsContact>()
    private val searchContacts = mutableListOf<QmsContact>()

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        qmsInteractor
                .observeContacts()
                .subscribe {
                    localItems.clear()
                    localItems.addAll(it)
                    viewState.showContacts(it)
                    countersHolder.set(countersHolder.get().apply {
                        qms = it.sumOf { it.count }
                    })
                }
                .untilDestroy()

        eventsRepository
                .observeEventsTab()
                .filter { NotificationEvent.fromQms(it.source) }
                .throttleLatest(3, TimeUnit.SECONDS)
                .subscribe {
                    loadContacts(showProgress = false)
                }
                .untilDestroy()
    }

    fun searchLocal(nick: String) {
        searchContacts.clear()
        if (!nick.isEmpty()) {
            searchContacts.addAll(localItems.filter { contact ->
                contact.nick?.lowercase()?.contains(nick.lowercase()) == true
            })
            viewState.showContacts(searchContacts)
        } else {
            viewState.showContacts(localItems)
        }
    }

    fun loadContacts(showProgress: Boolean = true) {
        qmsInteractor
                .getContactList()
                .doOnSubscribe { if (showProgress) viewState.setRefreshing(true) }
                .doAfterTerminate { if (showProgress) viewState.setRefreshing(false) }
                .subscribe({}, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun deleteDialog(id: Int) {
        qmsInteractor
                .deleteDialog(id)
                .doOnSubscribe { viewState.setRefreshing(true) }
                .doAfterTerminate { viewState.setRefreshing(false) }
                .subscribe({
                    loadContacts()
                }, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun blockUser(item: QmsContact) {
        qmsInteractor
                .blockUser(item.nick.orEmpty())
                .map { it.firstOrNull { it.nick == item.nick } != null }
                .subscribe({
                    viewState.onBlockUser(it)
                }, {
                    errorHandler.handle(it)
                })
                .untilDestroy()
    }

    fun onItemClick(item: QmsContact) {
        router.navigateTo(Screen.QmsThemes().apply {
            screenTitle = item.nick
            userId = item.id
            avatarUrl = item.avatar
        })
    }

    fun onItemLongClick(item: QmsContact) {
        viewState.showItemDialogMenu(item)
    }

    fun createNote(item: QmsContact) {
        val url = "https://4pda.to/forum/index.php?act=qms&mid=${item.id}"
        viewState.showCreateNote(item.nick.orEmpty(), url)
    }

    fun openProfile(item: QmsContact) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.id}", router)
    }

    fun openBlackList() {
        router.navigateTo(Screen.QmsBlackList())
    }

    fun openChatCreator() {
        router.navigateTo(Screen.QmsChat())
    }
}
