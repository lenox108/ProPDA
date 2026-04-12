package forpdateam.ru.forpda.model.repository.qms

import android.util.Log
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.*
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCache
import forpdateam.ru.forpda.model.data.cache.qms.QmsCache
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.repository.BaseRepository
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */
class QmsRepository(
        private val schedulers: SchedulersProvider,
        private val qmsApi: QmsApi,
        private val attachmentsApi: AttachmentsApi,
        private val qmsCache: QmsCache,
        private val forumUsersCache: ForumUsersCache,
        private val countersHolder: CountersHolder
) : BaseRepository(schedulers) {

    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()

    fun observeContacts(): Flow<List<QmsContact>> = qmsCache.observeContacts()

    fun observeThemes(userId: Int): Flow<QmsThemes> = qmsCache.observeThemes(userId)

    private suspend fun <T> ioSingle(
            timeoutSec: Long = 30,
            withRetry: Boolean = true,
            block: () -> T
    ): T = withContext(ioDispatcher) {
        val single = Single.fromCallable(block).withNetworkTimeout(timeoutSec)
        (if (withRetry) single.withNetworkRetry() else single).await()
    }

    suspend fun findUser(nick: String): List<ForumUser> =
            ioSingle { qmsApi.findUser(nick) }

    suspend fun blockUser(nick: String): List<QmsContact> =
            withContext(ioDispatcher) { qmsApi.blockUser(nick) }

    suspend fun unBlockUsers(userId: Int): List<QmsContact> =
            withContext(ioDispatcher) { qmsApi.unBlockUsers(userId) }

    suspend fun getContactList(): List<QmsContact> {
        val list = ioSingle { qmsApi.getContactList() }
        saveUsers(list)
        withContext(ioDispatcher) {
            qmsCache.saveContacts(list)
        }
        return withContext(ioDispatcher) { qmsCache.getContacts() }
    }

    suspend fun getBlackList(): List<QmsContact> =
            ioSingle { qmsApi.getBlackList() }

    suspend fun deleteDialog(mid: Int): String =
            withContext(ioDispatcher) { qmsApi.deleteDialog(mid) }

    suspend fun getThemesList(id: Int): QmsThemes {
        val data = ioSingle { qmsApi.getThemesList(id) }
        withContext(ioDispatcher) {
            qmsCache.saveThemes(data)
        }
        return withContext(ioDispatcher) { qmsCache.getThemes(id) }
    }

    suspend fun deleteTheme(id: Int, themeId: Int): QmsThemes {
        val data = withContext(ioDispatcher) { qmsApi.deleteTheme(id, themeId) }
        withContext(ioDispatcher) {
            qmsCache.saveThemes(data)
        }
        return withContext(ioDispatcher) { qmsCache.getThemes(data.userId) }
    }

    suspend fun getChat(userId: Int, themeId: Int): QmsChatModel =
            ioSingle(timeoutSec = 45) { qmsApi.getChat(userId, themeId) }

    suspend fun sendNewTheme(nick: String, title: String, mess: String, files: List<AttachmentItem>): QmsChatModel =
            withContext(ioDispatcher) { qmsApi.sendNewTheme(nick, title, mess, files) }

    suspend fun sendMessage(userId: Int, themeId: Int, text: String, files: List<AttachmentItem>): List<QmsMessage> =
            withContext(ioDispatcher) { qmsApi.sendMessage(userId, themeId, text, files) }

    suspend fun getMessagesFromWs(themeId: Int, messageId: Int, afterMessageId: Int): List<QmsMessage> =
            ioSingle { qmsApi.getMessagesFromWs(themeId, messageId, afterMessageId) }

    suspend fun getMessagesAfter(userId: Int, themeId: Int, afterMessageId: Int): List<QmsMessage> =
            ioSingle { qmsApi.getMessagesAfter(userId, themeId, afterMessageId) }

    suspend fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>): List<AttachmentItem> =
            withContext(ioDispatcher) { attachmentsApi.uploadQmsFiles(files, pending) }

    private fun saveUsers(contacts: List<QmsContact>) {
        val forumUsers = contacts.map { contact ->
            ForumUser().apply {
                id = contact.id
                nick = contact.nick
                avatar = contact.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

    fun handleEvent(event: TabNotification) {
        if (!NotificationEvent.fromQms(event.source)) {
            return
        }
        val themesList = qmsCache.getAllThemes()
        val allContacts = qmsCache.getContacts()

        var targetTheme: QmsTheme? = null
        var targetDialog: QmsThemes? = null

        for (dialog in themesList) {
            for (theme in dialog.themes) {
                if (theme.id == event.event.sourceId) {
                    targetDialog = dialog
                    targetTheme = theme
                    break
                }
            }
            if (targetTheme != null) {
                break
            }
        }
        Log.d("kokoso", "$targetDialog : $targetTheme")

        if (targetDialog != null && targetTheme != null) {
            Log.d("kokoso", "${event.isWebSocket}, ${event.type}, ${event.source}, ${event.event.msgCount}")
            if (event.isWebSocket) {
                if (NotificationEvent.isRead(event.type)) {
                    targetTheme.countNew = 0
                } else if (NotificationEvent.isNew(event.type)) {
                    targetTheme.countNew++
                }
            } else {
                if (NotificationEvent.isRead(event.type)) {
                    targetTheme.countNew = 0
                } else if (NotificationEvent.isNew(event.type)) {
                    targetTheme.countNew = event.event.msgCount
                }
            }

            qmsCache.saveThemes(targetDialog)
            allContacts.firstOrNull { it.id == targetDialog.userId }?.let { contact ->
                val newCount = targetDialog.themes.sumOf { it.countNew }
                Log.d("kokoso", "upd contact cound ${contact.count} to $newCount")
                contact.count = newCount

                qmsCache.updateContact(contact)
            }
        }

        countersHolder.set(countersHolder.get().also { counters ->
            if (event.isWebSocket) {
                counters.qms = allContacts.sumOf { it.count }
            } else {
                counters.qms = event.loadedEvents.sumOf { it.msgCount }
            }
        })
    }
}
