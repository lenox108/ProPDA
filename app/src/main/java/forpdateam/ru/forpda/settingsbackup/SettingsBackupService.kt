package forpdateam.ru.forpda.settingsbackup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import forpdateam.ru.forpda.model.datastore.ListsDataStore
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.model.datastore.OtherDataStore
import forpdateam.ru.forpda.model.datastore.TopicDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notesBackupStore: NotesBackupStore,
    private val historyBackupStore: HistoryBackupStore,
    private val readBoundaryBackupStore: ReadBoundaryBackupStore,
    private val readBoundaryStore: forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore,
) {
    suspend fun write(uri: Uri, includeSession: Boolean) = withContext(Dispatchers.IO) {
        val root = JSONObject()
            .put(KEY_FORMAT, FORMAT)
            .put(KEY_VERSION, VERSION)
            .put("created_at", System.currentTimeMillis())
            .put("app_version", BuildConfig.VERSION_NAME)
            .put(KEY_CONTAINS_SESSION, includeSession)
            .put("shared_preferences", createSharedPreferencesSnapshot(includeSession))
            .put("data_stores", createDataStoreSnapshot())
            .put(KEY_BOOKMARKS, notesBackupStore.export())
            .put(KEY_HISTORY, historyBackupStore.export())
            .put(KEY_READ_BOUNDARY, readBoundaryBackupStore.export())
            .put(
                "auth_cookies",
                encodeValues(
                    if (includeSession) {
                        SecureCookiesPreferences.getInstance(context).exportAuthCookies()
                    } else {
                        emptyMap()
                    },
                ),
            )

        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Не удалось открыть файл для записи")
        output.bufferedWriter(StandardCharsets.UTF_8).use { it.write(root.toString()) }
    }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("Не удалось открыть файл")
        val bytes = input.use(::readLimited)
        val root = try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw BackupException("Это не файл бэкапа ProPDA", error)
        }
        validateHeader(root)
        val containsSession = root.optBoolean(KEY_CONTAINS_SESSION, false)

        // Сначала полностью разбираем и проверяем файл, и только потом меняем настройки.
        val sharedPrefs = decodeNamedSnapshots(root.getJSONObject("shared_preferences"))
        val dataStores = decodeNamedSnapshots(root.getJSONObject("data_stores"))
        val authCookies = decodeValues(root.getJSONObject("auth_cookies"))
            .mapValues { (_, value) ->
                value as? String ?: throw BackupException("Повреждён раздел авторизации")
            }
        // Разделы, добавленные после первой версии формата: в старом файле их нет — тогда
        // соответствующие данные просто не трогаем, а не затираем пустыми.
        val bookmarks = root.optJSONObject(KEY_BOOKMARKS)?.let(notesBackupStore::decode)
        val history = root.optJSONArray(KEY_HISTORY)?.let(historyBackupStore::decode)
        val readBoundary = root.optJSONArray(KEY_READ_BOUNDARY)?.let(readBoundaryBackupStore::decode)

        restoreSharedPreferences(sharedPrefs, containsSession)
        MainDataStore(context).restoreBackupValues(dataStores.required("main"))
        TopicDataStore(context).restoreBackupValues(dataStores.required("topic"))
        ListsDataStore(context).restoreBackupValues(dataStores.required("lists"))
        OtherDataStore(context).restoreBackupValues(dataStores.required("other"))
        bookmarks?.let { notesBackupStore.restore(it) }
        history?.let { historyBackupStore.restore(it) }
        readBoundary?.let {
            readBoundaryBackupStore.restore(it)
            // Иначе живой процесс продолжает работать по старому in-memory кэшу и первой же записью
            // REPLACE'ом затирает только что восстановленные строки в Room.
            readBoundaryStore.rehydrateAfterRestore()
        }
        if (containsSession &&
            !SecureCookiesPreferences.getInstance(context).restoreAuthCookies(authCookies)
        ) {
            throw BackupException("Не удалось сохранить сессию аккаунта")
        }
    }

    private suspend fun createDataStoreSnapshot(): JSONObject = JSONObject()
        .put("main", encodeValues(MainDataStore(context).exportBackupValues()))
        .put("topic", encodeValues(TopicDataStore(context).exportBackupValues()))
        .put("lists", encodeValues(ListsDataStore(context).exportBackupValues()))
        .put("other", encodeValues(OtherDataStore(context).exportBackupValues()))

    private fun createSharedPreferencesSnapshot(includeSession: Boolean): JSONObject {
        val result = JSONObject()
        SHARED_PREFS.forEach { name ->
            val prefs = prefs(name)
            val excluded = when {
                name == DEFAULT_PREFS && includeSession -> DEFAULT_EXCLUDED_KEYS
                name == DEFAULT_PREFS -> DEFAULT_EXCLUDED_KEYS + ACCOUNT_KEYS
                name == MAIN_MIRROR_PREFS -> setOf(MAIN_MIRROR_DOWNLOAD_FOLDER_KEY)
                else -> emptySet()
            }
            result.put(name, encodeValues(prefs.all.filterKeys { it !in excluded }))
        }
        return result
    }

    private fun restoreSharedPreferences(
        snapshots: Map<String, Map<String, Any>>,
        containsSession: Boolean,
    ) {
        SHARED_PREFS.forEach { name ->
            // Файлы настроек, появившиеся в бэкапе позже первой версии, в старом бэкапе
            // отсутствуют — такой раздел пропускаем, оставляя текущие значения как есть.
            val values = snapshots[name]
                ?: if (name in OPTIONAL_SHARED_PREFS) return@forEach else snapshots.required(name)
            val preferences = prefs(name)
            val preservedKeys = when {
                name == DEFAULT_PREFS && containsSession -> DEFAULT_EXCLUDED_KEYS
                name == DEFAULT_PREFS -> DEFAULT_EXCLUDED_KEYS + ACCOUNT_KEYS
                name == MAIN_MIRROR_PREFS -> setOf(MAIN_MIRROR_DOWNLOAD_FOLDER_KEY)
                else -> emptySet()
            }
            val preserved = preferences.all
                .filterKeys { it in preservedKeys }
                .mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap()
            val editor = preferences.edit().clear()
            (values + preserved).forEach { (key, value) -> editor.putValue(key, value) }
            if (!editor.commit()) throw BackupException("Не удалось восстановить настройки")
        }
    }

    private fun prefs(name: String): SharedPreferences =
        if (name == DEFAULT_PREFS) {
            PreferenceManager.getDefaultSharedPreferences(context)
        } else {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

    private fun validateHeader(root: JSONObject) {
        if (root.optString(KEY_FORMAT) != FORMAT) {
            throw BackupException("Это не файл бэкапа ProPDA")
        }
        if (root.optInt(KEY_VERSION, -1) !in SUPPORTED_VERSIONS) {
            throw BackupException("Версия файла бэкапа не поддерживается")
        }
    }

    private fun decodeNamedSnapshots(value: JSONObject): Map<String, Map<String, Any>> =
        value.keys().asSequence().associateWith { name ->
            decodeValues(value.getJSONObject(name))
        }

    private fun encodeValues(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) ->
            put(key, when (value) {
                is String -> typed("string", value)
                is Boolean -> typed("boolean", value)
                is Int -> typed("int", value)
                is Long -> typed("long", value)
                is Float -> typed("float", value.toDouble())
                is Double -> typed("double", value)
                is Set<*> -> typed(
                    "string_set",
                    JSONArray(value.filterIsInstance<String>().sorted()),
                )
                else -> throw BackupException("Настройка $key имеет неподдерживаемый тип")
            })
        }
    }

    private fun typed(type: String, value: Any): JSONObject =
        JSONObject().put("type", type).put("value", value)

    private fun decodeValues(values: JSONObject): Map<String, Any> =
        values.keys().asSequence().associateWith { key ->
            val item = values.getJSONObject(key)
            when (item.getString("type")) {
                "string" -> item.getString("value")
                "boolean" -> item.getBoolean("value")
                "int" -> item.getInt("value")
                "long" -> item.getLong("value")
                "float" -> item.getDouble("value").toFloat()
                "double" -> item.getDouble("value")
                "string_set" -> item.getJSONArray("value").toStringSet()
                else -> throw BackupException("Неизвестный тип настройки: $key")
            }
        }

    private fun JSONArray.toStringSet(): Set<String> =
        (0 until length()).mapTo(linkedSetOf()) { getString(it) }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> throw BackupException("Настройка $key имеет неподдерживаемый тип")
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BACKUP_BYTES) throw BackupException("Файл бэкапа слишком большой")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun Map<String, Map<String, Any>>.required(name: String): Map<String, Any> =
        get(name) ?: throw BackupException("В бэкапе отсутствует раздел $name")

    companion object {
        private const val FORMAT = "propda-settings-backup"
        // 2 — добавлен раздел закладок; 3 — история, граница прочитанного и прогресс чтения
        // новостей. Файлы прежних версий читаются как раньше: недостающие разделы не трогаем.
        private const val VERSION = 3
        private val SUPPORTED_VERSIONS = 1..VERSION
        private const val KEY_FORMAT = "format"
        private const val KEY_VERSION = "version"
        private const val KEY_CONTAINS_SESSION = "contains_session"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HISTORY = "history"
        private const val KEY_READ_BOUNDARY = "read_boundary"
        private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

        private const val DEFAULT_PREFS = "default"
        private const val MAIN_MIRROR_PREFS = "main_mirror"
        private const val MAIN_MIRROR_DOWNLOAD_FOLDER_KEY = "download_folder_uri"
        private const val ARTICLE_PROGRESS_PREFS = "article_reading_progress"
        private val SHARED_PREFS = listOf(
            DEFAULT_PREFS,
            MAIN_MIRROR_PREFS,
            "topic_mirror",
            "lists_mirror",
            "other_mirror",
            ARTICLE_PROGRESS_PREFS,
        )
        private val OPTIONAL_SHARED_PREFS = setOf(ARTICLE_PROGRESS_PREFS)
        private val DEFAULT_EXCLUDED_KEYS = setOf(
            Preferences.Main.DOWNLOAD_FOLDER_URI,
            Preferences.Auth.COOKIE_MEMBER_ID,
            Preferences.Auth.COOKIE_PASS_HASH,
            Preferences.Auth.COOKIE_SESSION_ID,
            Preferences.Auth.COOKIE_ANONYMOUS,
            Preferences.Auth.COOKIE_CF_CLEARANCE,
        )
        private val ACCOUNT_KEYS = setOf(
            Preferences.Auth.USER_ID,
            Preferences.Auth.AUTH_KEY,
            "auth_state",
            "current_user",
        )
    }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
