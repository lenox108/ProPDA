package forpdateam.ru.forpda.model.data.remote.api.favorites

import android.net.Uri
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FavoritesUnreadTrace
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import timber.log.Timber
import kotlin.math.min

/**
 * Created by radiationx on 22.09.16.
 */

class FavoritesApi(
        private val webClient: IWebClient,
        private val favoritesParser: FavoritesParser
) {

    fun getFavorites(st: Int, all: Boolean, sorting: Sorting, bypassCache: Boolean = false): FavData {
        val uriBuilder = Uri.Builder()
                .scheme("https")
                .authority("4pda.to")
                .appendPath("forum")
                .appendPath("index.php")
                .appendQueryParameter("act", "fav")
                .appendQueryParameter("st", st.toString())
        if (sorting.key.isNotEmpty()) {
            uriBuilder.appendQueryParameter(Sorting.Companion.Key.HEADER, sorting.key)
        }
        if (sorting.order.isNotEmpty()) {
            uriBuilder.appendQueryParameter(Sorting.Companion.Order.HEADER, sorting.order)
        }
        // Add timestamp to bypass cache when refreshing
        if (bypassCache) {
            uriBuilder.appendQueryParameter("_t", System.currentTimeMillis().toString())
        }

        val url = uriBuilder.build().toString()
        val response = if (bypassCache) {
            webClient.request(
                    NetworkRequest.Builder()
                            .url(url)
                            .addHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                            .addHeader("Pragma", "no-cache")
                            .build()
            )
        } else {
            webClient.get(url)
        }
        val body = response.body
        val htmlMeta = FpdaDebugLog.classifyHtml(body)
        FavoritesUnreadTrace.htmlReceived(
                source = if (bypassCache) "network_refresh" else "network",
                htmlLen = htmlMeta["htmlLen"] as? Int ?: body.length,
                htmlHash = htmlMeta["htmlHash"] as? String ?: "unknown"
        )
        val data = favoritesParser.parseFavorites(body)

        if (all) {
            while (true) {
                if (data.pagination.current >= data.pagination.all) {
                    break
                }
                val favData = getFavorites(data.pagination.getPage(data.pagination.current), false, sorting, bypassCache)
                data.pagination = favData.pagination
                if (favData.items.isEmpty()) {
                    break
                }
                data.items.addAll(favData.items)
            }
            data.pagination.all = 1
        }

        FavoritesSort.apply(data.items, data.sorting, unreadTop = false)

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
     * Server doesn't support reliable batch marking via tact=read, so favorites are marked
     * one-by-one through getlastpost with retry/backoff handled inside the request.
     */
    suspend fun markFavoriteTopicRead(topicId: Int): Boolean {
        kotlinx.coroutines.delay(DELAY_MS_BETWEEN_SERVER_CALLS)
        return getLastPostMarkReadWithRetry(topicId)
    }

    private suspend fun getLastPostMarkReadWithRetry(topicId: Int): Boolean {
        val url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getlastpost"
        var attempt = 0
        while (attempt < READ_MAX_ATTEMPTS) {
            try {
                if (BuildConfig.DEBUG) Timber.d("[MarkRead] GET getlastpost attempt ${attempt + 1}")
                webClient.get(url)
                if (BuildConfig.DEBUG) Timber.d("[MarkRead] GET success")
                return true
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Timber.e(e, "[MarkRead] GET failed")
                val shouldRetry = shouldRetryRequest(e, attempt)
                if (shouldRetry) {
                    kotlinx.coroutines.delay(backoffAfterErrorMillis(attempt))
                    attempt++
                } else {
                    return false
                }
            }
        }
        return false
    }

    private fun shouldRetryRequest(e: Exception, attempt: Int): Boolean {
        if (attempt >= READ_MAX_ATTEMPTS - 1) return false

        // Retry on 429 (rate limited)
        if (e is OkHttpResponseException && e.code == 429) return true

        // Retry on 5xx server errors
        if (e is OkHttpResponseException && e.code in 500..599) return true

        // Retry on 404 (temporary unavailable during high load)
        if (e is OkHttpResponseException && e.code == 404) return true

        // Retry on IO errors (network issues)
        if (e is java.io.IOException) return true

        return false
    }

    private fun backoffAfterErrorMillis(attemptIndex: Int): Long {
        return min(READ_BACKOFF_BASE_MS * (1L shl attemptIndex), READ_BACKOFF_CAP_MS)
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

        /** Пауза между запросами к форуму: действие массовое, но очередь остаётся щадящей. */
        private const val DELAY_MS_BETWEEN_SERVER_CALLS = 300L
        /** Повторы при любых сетевых/серверных ошибках (429, 5xx, 404, IO). */
        private const val READ_MAX_ATTEMPTS = 6
        private const val READ_BACKOFF_BASE_MS = 2_500L
        private const val READ_BACKOFF_CAP_MS = 15_000L

        const val ACTION_EDIT_SUB_TYPE = 0
        const val ACTION_EDIT_PIN_STATE = 1
        const val ACTION_DELETE = 2
        const val ACTION_ADD = 3
        const val ACTION_ADD_FORUM = 4
        val SUB_TYPES = arrayOf("none", "delayed", "immediate", "daily", "weekly", "pinned")
    }
}

internal object FavoritesSort {

    fun apply(items: MutableList<FavItem>, sorting: Sorting, unreadTop: Boolean = false) {
        when (sorting.key) {
            Sorting.Companion.Key.LAST_POST -> sortByLastPost(items, sorting.order, unreadTop)
            Sorting.Companion.Key.TITLE -> sortByTitle(items, sorting.order, unreadTop)
        }
    }

    private fun sortByLastPost(items: MutableList<FavItem>, order: String, unreadTop: Boolean) {
        if (order == Sorting.Companion.Order.DESC) {
            items.sortWith(
                    compareBy<FavItem> { sectionRank(it, unreadTop) }
                            .thenByDescending { lastPostMillis(it) }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.topicTitle.orEmpty() }
                            .thenBy { it.favId }
            )
        } else if (order == Sorting.Companion.Order.ASC) {
            items.sortWith(
                    compareBy<FavItem> { sectionRank(it, unreadTop) }
                            .thenBy { lastPostMillis(it) }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.topicTitle.orEmpty() }
                            .thenBy { it.favId }
            )
        }
    }

    private fun sortByTitle(items: MutableList<FavItem>, order: String, unreadTop: Boolean) {
        if (order == Sorting.Companion.Order.ASC) {
            items.sortWith(
                    compareBy<FavItem> { sectionRank(it, unreadTop) }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { item -> item.topicTitle.orEmpty() }
                            .thenBy { it.favId }
            )
        } else if (order == Sorting.Companion.Order.DESC) {
            items.sortWith(
                    compareBy<FavItem> { sectionRank(it, unreadTop) }
                            .thenByDescending(String.CASE_INSENSITIVE_ORDER) { item -> item.topicTitle.orEmpty() }
                            .thenBy { it.favId }
            )
        }
    }

    private fun lastPostMillis(item: FavItem): Long {
        return Utils.parseForumDateTime(item.date)?.time ?: Long.MIN_VALUE
    }

    private fun sectionRank(item: FavItem, unreadTop: Boolean): Int {
        return when {
            item.isPin -> 0
            unreadTop && item.isNew && !item.isForum && item.topicId > 0 -> 1
            else -> 2
        }
    }

}
