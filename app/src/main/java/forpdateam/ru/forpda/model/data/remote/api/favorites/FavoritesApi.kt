package forpdateam.ru.forpda.model.data.remote.api.favorites

import android.net.Uri
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import java.util.Collections
import kotlin.Comparator
import kotlin.math.min

/**
 * Created by radiationx on 22.09.16.
 */

class FavoritesApi(
        private val webClient: IWebClient,
        private val favoritesParser: FavoritesParser
) {

    fun getFavorites(st: Int, all: Boolean, sorting: Sorting): FavData {
        val uriBuilder = Uri.Builder()
                .scheme("https")
                .authority("4pda.to")
                .appendPath("forum")
                .appendQueryParameter("act", "fav")
                .appendQueryParameter("type", "all")
                .appendQueryParameter("st", st.toString())
                .appendQueryParameter(Sorting.Key.HEADER, sorting.key)
                .appendQueryParameter(Sorting.Order.HEADER, sorting.order)

        val response = webClient.get(uriBuilder.build().toString())

        val data = favoritesParser.parseFavorites(response.body)

        if (all) {
            while (true) {
                if (data.pagination.current >= data.pagination.all) {
                    break
                }
                val favData = getFavorites(data.pagination.getPage(data.pagination.current), false, sorting)
                data.pagination = favData.pagination
                if (favData.items.isEmpty()) {
                    break
                }
                data.items.addAll(favData.items)
            }
            data.pagination.all = 1

            if (data.sorting.key == Sorting.Key.TITLE) {
                if (data.sorting.order == Sorting.Order.DESC) {
                    Collections.sort(data.items, DESC_ORDER)
                } else if (data.sorting.order == Sorting.Order.ASC) {
                    Collections.sort(data.items, ASC_ORDER)
                }
            }
        }

        return data
    }

    fun editSubscribeType(type: String?, favId: Int): Boolean {
        checkNotNull(type)
        val response = webClient.get("https://4pda.to/forum/index.php?act=fav&sort_key=&sort_by=&type=all&st=0&tact=$type&selectedtids=$favId")
        return favoritesParser.checkIsComplete(response.body)
    }

    fun editPinState(type: String?, favId: Int): Boolean {
        checkNotNull(type)
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=fav")
                .formHeader("selectedtids", favId.toString())
                .formHeader("tact", type)
        val response = webClient.request(builder.build())
        return favoritesParser.checkIsComplete(response.body)
    }

    fun delete(favId: Int): Boolean {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=fav")
                .xhrHeader()
                .formHeader("selectedtids", favId.toString())
                .formHeader("tact", "delete")
        val response = webClient.request(builder.build())
        return favoritesParser.checkIsComplete(response.body)
    }

    /**
     * Пометить выбранные закладки прочитанными на стороне форума.
     * Много тем — пакеты с паузами; повторы при 429 и «остыв» в конце только здесь.
     */
    fun markFavoritesTopicsRead(entries: List<Pair<Int, Int>>): Boolean {
        if (entries.isEmpty()) {
            return true
        }
        val favIds = entries.map { it.first }.distinct()
        val singleOk = if (favIds.size <= READ_SINGLE_POST_MAX_IDS) {
            tryMarkFavoritesReadBatchWithRetry(favIds.joinToString(","))
        } else {
            false
        }
        if (singleOk) {
            cooldownAfterMarkAllRead()
            return true
        }
        val chunks = favIds.chunked(READ_CHUNK_SIZE)
        var allChunksOk = true
        for ((index, chunk) in chunks.withIndex()) {
            if (index > 0) {
                Thread.sleep(DELAY_MS_BETWEEN_SERVER_CALLS)
            }
            if (!tryMarkFavoritesReadBatchWithRetry(chunk.joinToString(","))) {
                allChunksOk = false
                break
            }
        }
        if (allChunksOk) {
            cooldownAfterMarkAllRead()
            return true
        }
        for ((favId, topicId) in entries) {
            Thread.sleep(DELAY_MS_BETWEEN_SERVER_CALLS)
            if (tryMarkFavoritesReadBatchWithRetry(favId.toString())) {
                continue
            }
            Thread.sleep(DELAY_MS_BEFORE_GETLASTPOST)
            if (!getLastPostMarkReadWithRetry(topicId)) {
                return false
            }
        }
        cooldownAfterMarkAllRead()
        return true
    }

    private fun cooldownAfterMarkAllRead() {
        Thread.sleep(COOLDOWN_AFTER_MARK_ALL_MS)
    }

    private fun tryMarkFavoritesReadBatchWithRetry(selectedTids: String): Boolean {
        var attempt = 0
        while (attempt < READ_MAX_ATTEMPTS) {
            try {
                val response = webClient.request(
                        NetworkRequest.Builder()
                                .url("https://4pda.to/forum/index.php?act=fav")
                                .xhrHeader()
                                .formHeader("selectedtids", selectedTids)
                                .formHeader("tact", "read")
                                .formHeader("auth_key", webClient.authKey)
                                .build()
                )
                return favoritesParser.checkFavoritesReadComplete(response.body)
            } catch (e: Exception) {
                val rateLimited = e is OkHttpResponseException && e.code == 429
                if (rateLimited && attempt < READ_MAX_ATTEMPTS - 1) {
                    Thread.sleep(backoffAfter429Millis(attempt))
                    attempt++
                } else {
                    return false
                }
            }
        }
        return false
    }

    private fun getLastPostMarkReadWithRetry(topicId: Int): Boolean {
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getlastpost"
        var attempt = 0
        while (attempt < READ_MAX_ATTEMPTS) {
            try {
                webClient.get(url)
                return true
            } catch (e: Exception) {
                val rateLimited = e is OkHttpResponseException && e.code == 429
                if (rateLimited && attempt < READ_MAX_ATTEMPTS - 1) {
                    Thread.sleep(backoffAfter429Millis(attempt))
                    attempt++
                } else {
                    return false
                }
            }
        }
        return false
    }

    private fun backoffAfter429Millis(attemptIndex: Int): Long {
        return min(READ_429_BACKOFF_BASE_MS * (1L shl attemptIndex), READ_429_BACKOFF_CAP_MS)
    }

    fun add(id: Int, action: Int, type: String?): Boolean {
        checkNotNull(type)
        var url = "https://4pda.to/forum/index.php?act=fav&type=add&track_type=$type"
        if (action == ACTION_ADD_FORUM) {
            url += "&f="
        } else if (action == ACTION_ADD) {
            url += "&t="
        }
        url += id
        val response = webClient.request(NetworkRequest.Builder().url(url).build())
        return favoritesParser.checkIsComplete(response.body)
    }

    companion object {

        /** До стольких id — один POST `tact=read`; больше — сразу пакетами (меньше 429). */
        private const val READ_SINGLE_POST_MAX_IDS = 25
        /** Сколько id в одном POST при поэтапной пометке. */
        private const val READ_CHUNK_SIZE = 12
        /** Пауза между запросами к форуму при пакетном/поштучном пути. */
        private const val DELAY_MS_BETWEEN_SERVER_CALLS = 1_200L
        /** GET getlastpost тяжёлый для лимита — только после паузы. */
        private const val DELAY_MS_BEFORE_GETLASTPOST = 1_800L
        /** После успешной массовой пометки — «остыв» перед следующими запросами приложения. */
        private const val COOLDOWN_AFTER_MARK_ALL_MS = 2_500L
        /** Повторы при HTTP 429 для одного и того же запроса. */
        private const val READ_MAX_ATTEMPTS = 6
        private const val READ_429_BACKOFF_BASE_MS = 2_500L
        private const val READ_429_BACKOFF_CAP_MS = 15_000L

        const val ACTION_EDIT_SUB_TYPE = 0
        const val ACTION_EDIT_PIN_STATE = 1
        const val ACTION_DELETE = 2
        const val ACTION_ADD = 3
        const val ACTION_ADD_FORUM = 4
        val SUB_TYPES = arrayOf("none", "delayed", "immediate", "daily", "weekly", "pinned")

        private val DESC_ORDER = Comparator<FavItem> { item1, item2 ->
            item1.topicTitle.orEmpty().compareTo(item2.topicTitle.orEmpty(), ignoreCase = true)
        }
        private val ASC_ORDER = Comparator<FavItem> { item1, item2 ->
            item2.topicTitle.orEmpty().compareTo(item1.topicTitle.orEmpty(), ignoreCase = true)
        }
    }
}
