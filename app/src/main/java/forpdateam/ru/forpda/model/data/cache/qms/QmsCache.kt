package forpdateam.ru.forpda.model.data.cache.qms

import forpdateam.ru.forpda.entity.db.qms.QmsContactBd
import forpdateam.ru.forpda.entity.db.qms.QmsThemesBd
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import io.realm.Realm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.ConcurrentHashMap

class QmsCache {
    private val _contacts = MutableStateFlow<List<QmsContact>>(emptyList())
    private val themesFlows = ConcurrentHashMap<Int, MutableStateFlow<QmsThemes?>>()

    fun observeContacts(): Flow<List<QmsContact>> = _contacts.asStateFlow()

    fun observeThemes(userId: Int): Flow<QmsThemes> =
            themesFlow(userId).asStateFlow().filterNotNull()

    private fun themesFlow(userId: Int): MutableStateFlow<QmsThemes?> =
            themesFlows.getOrPut(userId) { MutableStateFlow(null) }

    private fun QmsContactBd.toContact(): QmsContact = QmsContact().apply {
        nick = this@toContact.nick
        avatar = this@toContact.avatar
        id = this@toContact.id
        count = this@toContact.count
    }

    private fun QmsThemesBd.toThemes(): QmsThemes {
        val out = QmsThemes()
        out.userId = this@toThemes.userId
        out.nick = this@toThemes.nick
        getThemes().forEach { themeBd ->
            out.themes.add(QmsTheme(themeBd))
        }
        return out
    }

    private fun loadContactsFromRealm(): List<QmsContact> =
            Realm.getDefaultInstance().use { realm ->
                realm.where(QmsContactBd::class.java).findAll().map { it.toContact() }
            }

    fun getContacts(): List<QmsContact> {
        val list = loadContactsFromRealm()
        _contacts.value = list
        return list
    }

    fun saveContacts(items: List<QmsContact>) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { realmTr ->
                realmTr.delete(QmsContactBd::class.java)
                for (item in items) {
                    realmTr.copyToRealmOrUpdate(QmsContactBd(item))
                }
            }
        }
        _contacts.value = loadContactsFromRealm()
    }

    fun updateContact(item: QmsContact) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { realmTr ->
                realmTr.copyToRealmOrUpdate(QmsContactBd(item))
            }
        }
        _contacts.value = loadContactsFromRealm()
    }

    fun getThemes(userId: Int): QmsThemes {
        val themes = Realm.getDefaultInstance().use { realm ->
            val results = realm.where(QmsThemesBd::class.java).equalTo("userId", userId).findAll()
            if (results.isEmpty()) {
                throw Exception("Themes not found")
            }
            results[0]!!.toThemes()
        }
        themesFlow(userId).value = themes
        return themes
    }

    fun saveThemes(themes: QmsThemes) {
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { realmTr ->
                realmTr.copyToRealmOrUpdate(QmsThemesBd(themes))
            }
        }
        themesFlow(themes.userId).value = getThemes(themes.userId)
    }

    fun getAllThemes(): List<QmsThemes> {
        val themesList = Realm.getDefaultInstance().use { realm ->
            realm.where(QmsThemesBd::class.java).findAll().map { it.toThemes() }
        }
        for (themes in themesList) {
            val flow = themesFlow(themes.userId)
            if (flow.value == null) {
                flow.value = themes
            }
        }
        return themesList
    }
}
