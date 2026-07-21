package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.qms.QmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class QmsInteractor(
        private val qmsRepository: QmsRepository,
        private val eventsRepository: EventsRepository,
        qmsApi: QmsApi
) {

    private val chatOpenPipeline = QmsChatOpenPipeline(qmsApi)

    private val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventsJob: Job? = null

    fun subscribeEvents() {
        if (eventsJob?.isActive == true) return
        // Счётчики и меню — UI-состояние: [CountersHolder]/[MenuRepository] должны обновляться на Main,
        // иначе бейдж QMS в нижней навигации не перерисовывается при WS-событии с фона (IO-поток).
        eventsJob = eventsScope.launch {
            eventsRepository.observeEventsTab().collect { event ->
                // A throw here (a Room hiccup while recomputing the counters) used to cancel this
                // collector for the rest of the process — every later event was then silently lost and
                // the QMS badge stayed frozen until the app restarted. Keep the subscription alive.
                runCatching { qmsRepository.handleEvent(event) }
                        .onFailure { Timber.e(it, "QMS counters: handleEvent failed") }
            }
        }
    }

    fun observeContacts(): Flow<List<QmsContact>> = qmsRepository.observeContacts()

    fun observeThemes(userId: Int): Flow<QmsThemes> = qmsRepository.observeThemes(userId)

    suspend fun findUser(nick: String): List<ForumUser> = qmsRepository.findUser(nick)

    suspend fun blockUser(nick: String): List<QmsContact> = qmsRepository.blockUser(nick)

    suspend fun unBlockUsers(userId: Int): List<QmsContact> = qmsRepository.unBlockUsers(userId)

    suspend fun getContactList(): List<QmsContact> = qmsRepository.getContactList()

    suspend fun getBlackList(): List<QmsContact> = qmsRepository.getBlackList()

    suspend fun deleteDialog(mid: Int): String = qmsRepository.deleteDialog(mid)

    suspend fun getThemesList(id: Int): QmsThemes = qmsRepository.getThemesList(id)

    suspend fun deleteTheme(id: Int, themeId: Int): QmsThemes = qmsRepository.deleteTheme(id, themeId)

    suspend fun getChat(userId: Int, themeId: Int): QmsChatModel = qmsRepository.getChat(userId, themeId)

    /**
     * Удаляет сообщения из открытого треда и инвалидирует in-memory кэш чата, чтобы повторное
     * открытие диалога не воскресило удалённые сообщения из кэша (сеть вернёт уже актуальный список).
     */
    suspend fun deleteMessages(userId: Int, themeId: Int, messageIds: List<Int>) {
        qmsRepository.deleteMessages(userId, themeId, messageIds)
        QmsChatMemoryCache.invalidate(userId, themeId)
    }

    /**
     * Диалог прочитан в приложении: обнуляем локальный счётчик непрочитанных и снимаем уведомления
     * этого треда из шторки.
     */
    suspend fun markThreadRead(userId: Int, themeId: Int) {
        qmsRepository.markThreadRead(userId, themeId)
        eventsRepository.onQmsThreadRead(themeId)
    }

    suspend fun loadChatThread(
            userId: Int,
            themeId: Int,
            traceId: String,
            requestId: Int,
            bypassCache: Boolean = false,
            sourceScreen: String = "qms_chat"
    ): QmsChatLoadOutcome = withContext(Dispatchers.IO) {
        // Pipeline calls QmsApi.fetchChat → Client.request (blocking OkHttp); must not run on Main
        // (BaseViewModel.scope uses Dispatchers.Main.immediate).
        val outcome = chatOpenPipeline.loadChat(
                userId = userId,
                themeId = themeId,
                traceId = traceId,
                requestId = requestId,
                bypassCache = bypassCache,
                sourceScreen = sourceScreen
        )
        // Only a page actually fetched from the network marks the thread read server-side; a cache
        // render changes nothing there, so it must not clear the local unread state either.
        val readOnServer = when (outcome) {
            is QmsChatLoadOutcome.Content -> !outcome.fromCache
            is QmsChatLoadOutcome.Empty -> !outcome.fromCache
            is QmsChatLoadOutcome.Failure -> false
        }
        if (readOnServer) {
            markThreadRead(userId, themeId)
        }
        outcome
    }

    suspend fun sendNewTheme(nick: String, title: String, mess: String, files: List<AttachmentItem>): QmsChatModel =
            qmsRepository.sendNewTheme(nick, title, mess, files)

    suspend fun sendMessage(userId: Int, themeId: Int, text: String, files: List<AttachmentItem>): List<QmsMessage> =
            qmsRepository.sendMessage(userId, themeId, text, files)

    suspend fun getMessagesFromWs(themeId: Int, messageId: Int, afterMessageId: Int): List<QmsMessage> =
            qmsRepository.getMessagesFromWs(themeId, messageId, afterMessageId)

    suspend fun getMessagesAfter(userId: Int, themeId: Int, afterMessageId: Int): List<QmsMessage> =
            qmsRepository.getMessagesAfter(userId, themeId, afterMessageId)

    suspend fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>): List<AttachmentItem> =
            qmsRepository.uploadFiles(files, pending)
}
