package forpdateam.ru.forpda.blocklist

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загрузка удалённого списка забаненных аккаунтов с GitHub (raw-файл в репозитории
 * релизов ProPDA). Отдаётся через CDN raw.githubusercontent.com и не подвержен
 * лимиту api.github.com (та же причина, что у [forpdateam.ru.forpda.appupdates.GithubReleaseSource]).
 *
 * Формат файла [BLOCKLIST_URL] (оба ключа необязательны):
 * ```json
 * { "banned": [12345, 67890], "banned_nicks": ["mihey985"] }
 * ```
 *
 * Класс open, а сетевой [fetch] и чистый [parse] разделены для тестирования.
 */
@Singleton
open class BlocklistSource @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @return блоклист, либо `null` — если загрузка/разбор не удались (сеть, 5xx,
     *   битый JSON). `null` означает «не смогли обновить» → вызывающий должен
     *   оставить прежний кэш, а не считать, что забаненных нет. Пустой блоклист
     *   означает «список пуст / файла нет (404)» — забаненных нет.
     */
    open fun fetch(): Blocklist? {
        val request = Request.Builder()
            .url(BLOCKLIST_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            // Не тянуть из HTTP-кэша устаревший список — блок должен реагировать быстро.
            .header("Cache-Control", "no-cache")
            .build()

        val body: String = try {
            client.newCall(request).execute().use { response ->
                when {
                    // Файла нет — значит забаненных нет (осознанное «пусто», не ошибка).
                    response.code == 404 -> return Blocklist()
                    !response.isSuccessful -> return null
                    else -> response.body?.string().orEmpty()
                }
            }
        } catch (e: IOException) {
            return null
        }

        return parse(body)
    }

    /** Чистый разбор JSON. `null` при битом JSON; пустой блоклист при отсутствии ключей. */
    fun parse(json: String): Blocklist? {
        return try {
            val root = JSONObject(json)
            val ids = buildSet {
                root.optJSONArray("banned")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optInt(i, -1).takeIf { it > 0 }?.let { add(it) }
                    }
                }
            }
            val nicks = buildSet {
                root.optJSONArray("banned_nicks")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().lowercase().takeIf(String::isNotEmpty)?.let { add(it) }
                    }
                }
            }
            Blocklist(ids, nicks)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val OWNER = "lenox108"
        const val REPO = "ProPDA"
        const val BRANCH = "main"
        const val BLOCKLIST_URL =
            "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/blocklist.json"
        private const val USER_AGENT = "ProPDA-Blocklist"
    }
}
