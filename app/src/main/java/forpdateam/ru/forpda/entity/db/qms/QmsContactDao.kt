package forpdateam.ru.forpda.entity.db.qms

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QmsContactDao {
    @Query("SELECT * FROM qms_contacts")
    fun getAllContacts(): Flow<List<QmsContactRoom>>

    @Query("SELECT * FROM qms_contacts")
    suspend fun getAllContactsList(): List<QmsContactRoom>

    @Query("SELECT * FROM qms_contacts WHERE nick = :nick")
    suspend fun getContactByNick(nick: String): QmsContactRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: QmsContactRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<QmsContactRoom>)

    @Update
    suspend fun updateContact(contact: QmsContactRoom)

    @Query("DELETE FROM qms_contacts WHERE nick = :nick")
    suspend fun deleteContact(nick: String)

    @Query("DELETE FROM qms_contacts")
    suspend fun deleteAllContacts()
}
