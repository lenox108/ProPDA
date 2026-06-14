package forpdateam.ru.forpda.model.data.cache.qms

import forpdateam.ru.forpda.entity.db.qms.QmsContactDao
import forpdateam.ru.forpda.entity.db.qms.QmsContactRoom
import forpdateam.ru.forpda.entity.db.qms.QmsThemeDao
import forpdateam.ru.forpda.entity.db.qms.QmsThemeRoom
import forpdateam.ru.forpda.entity.db.qms.QmsThemesDao
import forpdateam.ru.forpda.entity.db.qms.QmsThemesRoom
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.ConcurrentHashMap

class QmsCacheRoom(
    private val qmsContactDao: QmsContactDao,
    private val qmsThemeDao: QmsThemeDao,
    private val qmsThemesDao: QmsThemesDao
) {
    private val _contacts = MutableStateFlow<List<QmsContact>>(emptyList())
    private val themesFlows = ConcurrentHashMap<Int, MutableStateFlow<QmsThemes?>>()

    fun observeContacts(): Flow<List<QmsContact>> = _contacts.asStateFlow()

    fun observeThemes(userId: Int): Flow<QmsThemes> =
            themesFlow(userId).asStateFlow().filterNotNull()

    private fun themesFlow(userId: Int): MutableStateFlow<QmsThemes?> =
            themesFlows.getOrPut(userId) { MutableStateFlow(null) }

    private fun QmsContactRoom.toContact(): QmsContact = QmsContact().apply {
        nick = this@toContact.nick
        avatar = this@toContact.avatar
        id = this@toContact.id
        count = this@toContact.count
    }

    private fun QmsThemeRoom.toTheme(): QmsTheme = QmsTheme().apply {
        id = this@toTheme.id
        countMessages = this@toTheme.countMessages
        countNew = this@toTheme.countNew
        name = this@toTheme.name
        date = this@toTheme.date
    }

    suspend fun getContacts(): List<QmsContact> {
        val items = qmsContactDao.getAllContactsList()
        val contacts = items.map { it.toContact() }
        _contacts.value = contacts
        return contacts
    }

    suspend fun saveContacts(items: List<QmsContact>) {
        qmsContactDao.deleteAllContacts()
        val contactsRoom = items.map { QmsContactRoom(
            nick = it.nick ?: "",
            id = it.id,
            count = it.count,
            avatar = it.avatar
        ) }
        qmsContactDao.insertContacts(contactsRoom)
        getContacts()
    }

    suspend fun updateContact(item: QmsContact) {
        val contactRoom = QmsContactRoom(
            nick = item.nick ?: "",
            id = item.id,
            count = item.count,
            avatar = item.avatar
        )
        qmsContactDao.updateContact(contactRoom)
        getContacts()
    }

    suspend fun getThemes(userId: Int): QmsThemes {
        val themesRoom = qmsThemesDao.getThemesByUserId(userId)
            ?: throw Exception("Themes not found")
        
        val themes = qmsThemeDao.getThemesByUserId(userId)
        
        val out = QmsThemes()
        out.userId = themesRoom.userId
        out.nick = themesRoom.nick
        themes.forEach { themeRoom ->
            out.themes.add(themeRoom.toTheme())
        }
        
        themesFlow(userId).value = out
        return out
    }

    suspend fun saveThemes(themes: QmsThemes) {
        val themesRoom = QmsThemesRoom(
            userId = themes.userId,
            nick = themes.nick
        )
        
        qmsThemesDao.deleteThemes(themes.userId)
        qmsThemesDao.insertThemes(themesRoom)
        
        qmsThemeDao.deleteThemesByUserId(themes.userId)
        val themeRooms = themes.themes.map { QmsThemeRoom(
            id = it.id,
            userId = themes.userId,
            countMessages = it.countMessages,
            countNew = it.countNew,
            name = it.name,
            date = it.date
        ) }
        qmsThemeDao.insertThemes(themeRooms)
        
        themesFlow(themes.userId).value = getThemes(themes.userId)
    }

    suspend fun getAllThemes(): List<QmsThemes> {
        val themesListRoom = qmsThemesDao.getAllThemesListSync()
        val themesList = mutableListOf<QmsThemes>()
        
        for (themesRoom in themesListRoom) {
            val themes = qmsThemeDao.getThemesByUserId(themesRoom.userId)
            val out = QmsThemes()
            out.userId = themesRoom.userId
            out.nick = themesRoom.nick
            themes.forEach { themeRoom ->
                out.themes.add(themeRoom.toTheme())
            }
            themesList.add(out)
            
            val flow = themesFlow(themesRoom.userId)
            if (flow.value == null) {
                flow.value = out
            }
        }
        
        return themesList
    }
}
