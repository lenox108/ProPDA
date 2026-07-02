package forpdateam.ru.forpda.model.repository.qms
import forpdateam.ru.forpda.BuildConfig

import timber.log.Timber
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.*
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.cache.qms.QmsCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Created by radiationx on 01.01.18.
 */
class QmsRepository(
        private val qmsApi: QmsApi,
        private val attachmentsApi: AttachmentsApi,
        private val qmsCache: QmsCacheRoom,
        private val forumUsersCache: ForumUsersCacheRoom,
        private val countersHolder: CountersHolder
) {

    fun observeContacts(): Flow<List<QmsContact>> = qmsCache.observeContacts()

    fun observeThemes(userId: Int): Flow<QmsThemes> = qmsCache.observeThemes(userId)

    suspend fun findUser(nick: String): List<ForumUser> = withContext(Dispatchers.IO) {
        withTimeout(30_000L) { qmsApi.findUser(nick) }
    }

    suspend fun blockUser(nick: String): List<QmsContact> =
            withContext(Dispatchers.IO) { qmsApi.blockUser(nick) }

    suspend fun unBlockUsers(userId: Int): List<QmsContact> =
            withContext(Dispatchers.IO) { qmsApi.unBlockUsers(userId) }

    suspend fun getContactList(): List<QmsContact> = withContext(Dispatchers.IO) {
        val list = withTimeout(30_000L) { qmsApi.getContactList() }
        saveUsers(list)
        qmsCache.saveContacts(list)
        qmsCache.getContacts().also { contacts ->
            updateQmsCounterFromContacts(contacts, "getContactList")
        }
    }

    suspend fun getBlackList(): List<QmsContact> = withContext(Dispatchers.IO) {
        withTimeout(30_000L) { qmsApi.getBlackList() }
    }

    suspend fun deleteDialog(mid: Int): String =
            withContext(Dispatchers.IO) { qmsApi.deleteDialog(mid) }

    suspend fun getThemesList(id: Int): QmsThemes = withContext(Dispatchers.IO) {
        val data = withTimeout(30_000L) { qmsApi.getThemesList(id) }
        qmsCache.saveThemes(data)
        qmsCache.getThemes(id)
    }

    suspend fun deleteTheme(id: Int, themeId: Int): QmsThemes = withContext(Dispatchers.IO) {
        val data = qmsApi.deleteTheme(id, themeId)
        qmsCache.saveThemes(data)
        qmsCache.getThemes(data.userId)
    }

    suspend fun getChat(userId: Int, themeId: Int): QmsChatModel = withContext(Dispatchers.IO) {
        withTimeout(45_000L) { qmsApi.getChat(userId, themeId) }
    }

    suspend fun sendNewTheme(nick: String, title: String, mess: String, files: List<AttachmentItem>): QmsChatModel =
            withContext(Dispatchers.IO) {
                // Тот же таймаут-гейт, что и у sendMessage: страховка от зависшего сетевого
                // ответа create-thread. Основную же причину «вечного спиннера при первом
                // сообщении новому пользователю» лечит атомарная группа в chat_info-паттерне
                // (patterns.json) — там был катастрофический бэктрекинг O(n²) java.util.regex,
                // который таймаутом НЕ прерывается (CPU-bound, не кооперативная отмена).
                withTimeout(30_000L) { qmsApi.sendNewTheme(nick, title, mess, files) }
            }

    suspend fun sendMessage(userId: Int, themeId: Int, text: String, files: List<AttachmentItem>): List<QmsMessage> =
            withContext(Dispatchers.IO) {
                // Гейт по таймауту как у getMessagesFromWs/getMessagesAfter: без него зависший
                // ответ send-message (сервер сохранил сообщение, но не закрыл соединение) навсегда
                // держит корутину → спиннер `_messageRefreshing` крутится вечно, сообщение не
                // отрисовывается, хотя фактически отправлено. Таймаут переводит вечное зависание
                // в ограниченную ошибку (finally гасит спиннер, сообщение появится при обновлении).
                withTimeout(30_000L) { qmsApi.sendMessage(userId, themeId, text, files) }
            }

    suspend fun getMessagesFromWs(themeId: Int, messageId: Int, afterMessageId: Int): List<QmsMessage> = withContext(Dispatchers.IO) {
        withTimeout(30_000L) { qmsApi.getMessagesFromWs(themeId, messageId, afterMessageId) }
    }

    suspend fun getMessagesAfter(userId: Int, themeId: Int, afterMessageId: Int): List<QmsMessage> = withContext(Dispatchers.IO) {
        withTimeout(30_000L) { qmsApi.getMessagesAfter(userId, themeId, afterMessageId) }
    }

    suspend fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>): List<AttachmentItem> =
            withContext(Dispatchers.IO) { attachmentsApi.uploadQmsFiles(files, pending) }

    private suspend fun saveUsers(contacts: List<QmsContact>) {
        val forumUsers = contacts.map { contact ->
            ForumUser().apply {
                id = contact.id
                nick = contact.nick
                avatar = contact.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

    suspend fun handleEvent(event: TabNotification) {
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
        Timber.d("$targetDialog : $targetTheme")

        if (targetDialog != null && targetTheme != null) {
            Timber.d("${event.isWebSocket}, ${event.type}, ${event.source}, ${event.event.msgCount}")
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
                Timber.d("upd contact cound ${contact.count} to $newCount")
                contact.count = newCount

                qmsCache.updateContact(contact)
            }
        }

        val sumFromContacts = allContacts.sumOf { it.count }
        countersHolder.set(countersHolder.get().also { counters ->
            if (event.isWebSocket) {
                if (targetDialog != null && targetTheme != null) {
                    counters.qms = sumFromContacts
                } else if (NotificationEvent.isNew(event.type)) {
                    // Тема/диалог ещё не в локальном кэше — сумма по контактам не учитывает новое сообщение.
                    counters.qms = max(counters.qms, sumFromContacts) + 1
                } else {
                    counters.qms = sumFromContacts
                }
            } else {
                counters.qms = event.loadedEvents.sumOf { it.msgCount }
            }
        })
    }

    private suspend fun updateQmsCounterFromContacts(contacts: List<QmsContact>, source: String) {
        val count = contacts.sumOf { it.count }
        withContext(Dispatchers.Main.immediate) {
            if (BuildConfig.DEBUG) Timber.d("QmsRepository.$source contacts=${contacts.size} qms=$count")
            countersHolder.set(countersHolder.get().apply {
                qms = count
            })
        }
    }
}
