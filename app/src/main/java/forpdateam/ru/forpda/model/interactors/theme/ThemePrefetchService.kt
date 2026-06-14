package forpdateam.ru.forpda.model.interactors.theme

import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-priority background prefetch for topics opened from list screens (favorites, etc.).
 */
class ThemePrefetchService(
        private val themeRepository: ThemeRepository
) {

    data class PrefetchKey(
            val topicId: Int,
            val url: String,
            val hatOpen: Boolean,
            val pollOpen: Boolean,
            val openFromUnreadListHint: Boolean = false
    )

    private data class WarmEntry(val page: ThemePage, val expiresAt: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private val inflightByKey = ConcurrentHashMap<PrefetchKey, Deferred<Unit>>()
    private val warmByKey = ConcurrentHashMap<PrefetchKey, WarmEntry>()
    @Volatile
    private var activePrefetchTopicId: Int = -1

    /** Waits for an in-flight warm-up so tap-to-open can reuse network work without a second fetch. */
    suspend fun awaitWarm(
            topicId: Int,
            url: String,
            hatOpen: Boolean = false,
            pollOpen: Boolean = false,
            openFromUnreadListHint: Boolean = false
    ) {
        if (topicId <= 0) return
        inflightByKey.entries
                .filter {
                    it.key.topicId == topicId &&
                            it.key.url == url &&
                            it.key.hatOpen == hatOpen &&
                            it.key.pollOpen == pollOpen &&
                            it.key.openFromUnreadListHint == openFromUnreadListHint
                }
                .map { it.value }
                .forEach { it.await() }
    }

    /**
     * Cancel background prefetch for other topics so tap-to-open is not competing for CPU/network.
     * Keeps an in-flight/completed prefetch for [exceptTopicId] so list warm-up can hand off to open.
     */
    fun cancelPrefetch(exceptTopicId: Int = -1) {
        if (exceptTopicId > 0 && activePrefetchTopicId == exceptTopicId) return
        prefetchJob?.cancel()
        prefetchJob = null
        if (exceptTopicId > 0) {
            inflightByKey.entries.removeIf { (key, deferred) ->
                if (key.topicId != exceptTopicId) {
                    deferred.cancel()
                    true
                } else {
                    false
                }
            }
            warmByKey.entries.removeIf { it.key.topicId != exceptTopicId }
            activePrefetchTopicId = exceptTopicId
        } else {
            inflightByKey.values.forEach { it.cancel() }
            inflightByKey.clear()
            warmByKey.clear()
            activePrefetchTopicId = -1
        }
    }

    fun prefetchTopic(
            topicId: Int,
            url: String,
            hatOpen: Boolean = false,
            pollOpen: Boolean = false,
            openFromUnreadListHint: Boolean = false
    ) {
        if (topicId <= 0 || url.isBlank()) return
        val key = PrefetchKey(topicId, url, hatOpen, pollOpen, openFromUnreadListHint)
        warmByKey[key]?.takeIf { it.expiresAt > nowMs() }?.let { return }
        ThemePostReadStateDiagnostics.prefetchStart(topicId, url, openFromUnreadListHint)
        prefetchJob?.cancel()
        activePrefetchTopicId = topicId
        val deferred = scope.async {
            try {
                prefetchTopicLocked(key)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                Timber.w(error, "Theme prefetch failed topicId=%d url=%s", topicId, url)
            } finally {
                if (activePrefetchTopicId == topicId) {
                    activePrefetchTopicId = -1
                }
            }
        }
        inflightByKey[key] = deferred
        deferred.invokeOnCompletion { inflightByKey.remove(key, deferred) }
        prefetchJob = deferred
    }

    fun tryConsumeWarm(
            url: String,
            hatOpen: Boolean = false,
            pollOpen: Boolean = false,
            openFromUnreadListHint: Boolean = false
    ): ThemePage? {
        pruneExpired()
        val key = warmByKey.keys.firstOrNull {
            it.url == url &&
                    it.hatOpen == hatOpen &&
                    it.pollOpen == pollOpen &&
                    it.openFromUnreadListHint == openFromUnreadListHint
        }
        if (key == null) {
            ThemePostReadStateDiagnostics.prefetchConsume(
                    url = url,
                    openFromUnreadListHint = openFromUnreadListHint,
                    hit = false
            )
            return null
        }
        val page = warmByKey.remove(key)?.page
        ThemePostReadStateDiagnostics.prefetchConsume(
                url = url,
                openFromUnreadListHint = openFromUnreadListHint,
                hit = page != null,
                anchorPostId = page?.anchorPostId ?: page?.anchor?.removePrefix("entry"),
                hasUnreadTarget = page?.hasUnreadTarget,
                topicId = page?.id ?: key.topicId
        )
        return page
    }

    private suspend fun prefetchTopicLocked(key: PrefetchKey) {
        pruneExpired()
        warmByKey[key]?.takeIf { it.expiresAt > nowMs() }?.let { return }
        val page = themeRepository.getTheme(
                key.url,
                _withHtml = true,
                key.hatOpen,
                key.pollOpen,
                key.openFromUnreadListHint
        )
        if (page.id > 0 && page.id != key.topicId) return
        if (activePrefetchTopicId != key.topicId) return
        if (inflightByKey[key] == null) return
        warmByKey[key] = WarmEntry(page, nowMs() + WARM_TTL_MS)
    }

    private fun pruneExpired() {
        val now = nowMs()
        warmByKey.entries.removeAll { it.value.expiresAt <= now }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    companion object {
        const val WARM_TTL_MS = 30_000L
    }
}
