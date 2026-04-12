package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.qms.QmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class QmsInteractor(
        private val qmsRepository: QmsRepository,
        private val eventsRepository: EventsRepository
) {

    private val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventsJob: Job? = null

    fun subscribeEvents() {
        if (eventsJob?.isActive == true) return
        eventsJob = eventsScope.launch {
            eventsRepository.observeEventsTab().collect { qmsRepository.handleEvent(it) }
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
