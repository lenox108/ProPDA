package forpdateam.ru.forpda.presentation.qms.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.TemplateManager
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

class QmsChatViewModel(
        private val qmsInteractor: QmsInteractor,
        private val qmsChatTemplate: QmsChatTemplate,
        private val avatarRepository: AvatarRepository,
        private val eventsRepository: EventsRepository,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val templateManager: TemplateManager,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : ViewModel(), QmsChatWebCallbacks {

    companion object {
        const val MODE_CHAT = "chat"
        const val MODE_CREATING = "creating"
    }

    @Volatile
    private var qmsChatView: QmsChatView? = null

    fun attachView(view: QmsChatView) {
        qmsChatView = view
    }

    fun detachView() {
        qmsChatView = null
    }

    private val rxSubscriptions = CompositeDisposable()
    private var subscriptionsStarted = false

    var themeId = 0
    var userId = 0
    var title: String? = null
    var nick: String? = null
    var avatarUrl: String? = null

    private var currentMode = MODE_CHAT

    private var currentData: QmsChatModel? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        mainPreferencesHolder
                .observeWebViewFontSize()
                .subscribe {
                    qmsChatView?.setFontSize(it)
                }
                .also { rxSubscriptions.add(it) }

        templateManager
                .observeThemeType()
                .subscribe {
                    qmsChatView?.setStyleType(it)
                }
                .also { rxSubscriptions.add(it) }
        viewModelScope.launch {
            eventsRepository.observeEventsTab()
                    .collect { handleEvent(it) }
        }
        nick?.let { n -> title?.let { t -> qmsChatView?.setTitles(t, n) } }

        updateMode()
        if (currentMode == MODE_CHAT) {
            tryShowAvatar()
            loadChat()
        }
    }

    override fun onCleared() {
        rxSubscriptions.clear()
        super.onCleared()
    }

    private fun updateMode() {
        currentMode = if (themeId == QmsChatModel.NOT_CREATED || userId == QmsChatModel.NOT_CREATED) {
            MODE_CREATING
        } else {
            MODE_CHAT
        }
        qmsChatView?.setChatMode(currentMode)
    }

    private fun updateCurrentData(newData: QmsChatModel) {
        currentData = newData
        themeId = newData.themeId
        userId = newData.userId
        title = newData.title
        nick = newData.nick
        avatarUrl = newData.avatarUrl
        updateMode()
    }

    fun findUser(nick: String) {
        viewModelScope.launch {
            runCatching { qmsInteractor.findUser(nick) }
                    .onSuccess { qmsChatView?.onShowSearchRes(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    private fun loadChat() {
        viewModelScope.launch {
            try {
                qmsChatView?.setRefreshing(true)
                val chat = qmsInteractor.getChat(userId, themeId)
                updateCurrentData(chat)
                qmsChatView?.showChat(chat)
                initOnNewMessages(chat)
                tryShowAvatar()
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                qmsChatView?.setRefreshing(false)
            }
        }
    }

    fun sendNewTheme(nick: String, title: String, message: String, files: List<AttachmentItem>) {
        viewModelScope.launch {
            try {
                qmsChatView?.setRefreshing(true)
                val chat = qmsInteractor.sendNewTheme(nick, title, message, files)
                updateCurrentData(chat)
                qmsChatView?.showChat(chat)
                qmsChatView?.onNewThemeCreate(chat)
                initOnNewMessages(chat)
                tryShowAvatar()
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                qmsChatView?.setRefreshing(false)
            }
        }
    }

    fun sendMessage(message: String, files: List<AttachmentItem>) {
        viewModelScope.launch {
            try {
                qmsChatView?.setMessageRefreshing(true)
                val messages = qmsInteractor.sendMessage(userId, themeId, message, files)
                qmsChatView?.onSentMessage(messages)
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                qmsChatView?.setMessageRefreshing(false)
            }
        }
    }

    fun blockUser() {
        currentData?.nick?.let { n ->
            viewModelScope.launch {
                runCatching { qmsInteractor.blockUser(n) }
                        .onSuccess { list ->
                            qmsChatView?.onBlockUser(list.firstOrNull { it.nick == n } != null)
                        }
                        .onFailure { errorHandler.handle(it) }
            }
        }
    }

    private fun tryShowAvatar() {
        val result = avatarUrl?.let { it } ?: currentData?.avatarUrl?.let { it }
        if (result != null) {
            qmsChatView?.showAvatar(result)
        } else {
            currentData?.let {
                viewModelScope.launch {
                    runCatching { avatarRepository.getAvatar(it.nick.orEmpty()).await() }
                            .onSuccess { url -> qmsChatView?.showAvatar(url) }
                            .onFailure { e -> errorHandler.handle(e) }
                }
            }
        }
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>) {
        viewModelScope.launch {
            runCatching { qmsInteractor.uploadFiles(files, pending) }
                    .onSuccess { qmsChatView?.onUploadFiles(it) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun handleEvent(event: TabNotification) {
        val tid = event.event.sourceId
        val messageId = event.event.messageId
        currentData?.let {
            if (tid == it.themeId) {
                when (event.type) {
                    NotificationEvent.Type.NEW -> {
                        onNewWsMessage(tid, messageId)
                    }
                    NotificationEvent.Type.READ -> {
                        qmsChatView?.makeAllRead()
                    }
                    NotificationEvent.Type.MENTION -> {
                    }
                    NotificationEvent.Type.HAT_EDITED -> {
                    }
                    null -> {
                    }
                }
            }
        }
    }

    private fun onNewWsMessage(themeId: Int, messageId: Int) {
        currentData?.let {
            val lastMessId = it.messages.lastOrNull()?.id ?: 0
            viewModelScope.launch {
                runCatching { qmsInteractor.getMessagesFromWs(themeId, messageId, lastMessId) }
                        .onSuccess { onNewMessages(it) }
                        .onFailure { errorHandler.handle(it) }
            }
        }
    }

    fun checkNewMessages() {
        currentData?.let {
            val lastMessId = it.messages.lastOrNull()?.id ?: 0
            viewModelScope.launch {
                runCatching { qmsInteractor.getMessagesAfter(userId, themeId, lastMessId) }
                        .onSuccess { msgs -> onNewMessages(msgs) }
                        .onFailure { e -> errorHandler.handle(e) }
            }
        }
    }

    private fun initOnNewMessages(data: QmsChatModel) {
        val end = data.messages.size
        val start = Math.max(end - 30, 0)
        data.showedMessIndex = start
        val newMessages = data.messages.subList(start, end).toList()
        qmsChatView?.onNewMessages(newMessages)
    }

    private fun onNewMessages(items: List<QmsMessage>) {
        currentData?.let { data ->
            val result = items.filter { new ->
                data.messages.find { it.id != new.id } != null
            }
            data.messages.addAll(result)
            qmsChatView?.onNewMessages(result)
        }
    }

    fun createThemeNote() {
        currentData?.let {
            val url = "https://4pda.to/forum/index.php?act=qms&mid=${it.userId}&t=${it.themeId}"
            qmsChatView?.showCreateNote(it.title.orEmpty(), it.nick.orEmpty(), url)
        }
    }

    fun openProfile() {
        currentData?.let {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${it.userId}", router)
        }
    }

    fun openDialogs() {
        currentData?.let {
            router.navigateTo(Screen.QmsThemes().apply {
                screenTitle = it.nick
                userId = it.userId
                avatarUrl = it.avatarUrl
            })
        }
    }

    fun onSendClick() {
        if (themeId == QmsChatModel.NOT_CREATED) {
            qmsChatView?.temp_sendNewTheme()
        } else {
            qmsChatView?.temp_sendMessage()
        }
    }

    override fun loadMoreMessages() {
        currentData?.let {
            val endIndex = it.showedMessIndex
            val startIndex = Math.max(endIndex - 30, 0)
            it.showedMessIndex = startIndex
            qmsChatView?.showMoreMessages(it.messages, startIndex, endIndex)
        }
    }

    class Factory(
            private val qmsInteractor: QmsInteractor,
            private val qmsChatTemplate: QmsChatTemplate,
            private val avatarRepository: AvatarRepository,
            private val eventsRepository: EventsRepository,
            private val mainPreferencesHolder: MainPreferencesHolder,
            private val templateManager: TemplateManager,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != QmsChatViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return QmsChatViewModel(
                    qmsInteractor,
                    qmsChatTemplate,
                    avatarRepository,
                    eventsRepository,
                    mainPreferencesHolder,
                    templateManager,
                    router,
                    linkHandler,
                    errorHandler
            ) as T
        }
    }
}
