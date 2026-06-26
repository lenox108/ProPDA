package forpdateam.ru.forpda.model.repository.theme

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped, cross-tab record of the position a topic was last VIEWED at in this session.
 *
 * Why this exists (log 24_06-23-12-50, multi-back anchor loss): each Theme tab owns its own
 * [forpdateam.ru.forpda.presentation.theme.ThemeHistoryController] and the per-tab
 * `TopicBackSnapshot` map is wiped by `resetTransientStateForNewTopic -> clear()`. When a tab's
 * in-tab history is exhausted (`historySize=0 canGoBack=false`) the next BACK is consumed by the
 * `TabNavigator`, which RE-OPENS the topic in a (possibly different) tab via the
 * `list_read_use_getlastpost` resolver. That fresh open lands on the SERVER last-read bookmark
 * (e.g. 1121483 -> st=1260#entry143992836, page 64) instead of the post the user was actually on
 * (st=1180#entry143876380, page 60) — the "thrown to a random post" symptom.
 *
 * This singleton remembers the real viewed position per topic ACROSS tabs so the re-open path can
 * restore it instead of re-resolving getlastpost. It is intentionally in-memory and session-scoped
 * (mirrors [ThemeReadPositionRepository]); it never overrides a genuine first open (no entry =>
 * resolver keeps its LAST_UNREAD/getlastpost behavior).
 */
@Singleton
class TopicReturnPositionStore @Inject constructor() {

    data class Position(
            val topicId: Int,
            val pageSt: Int,
            val postId: String?,
            val scrollY: Int,
            val updatedAt: Long = System.currentTimeMillis()
    )

    private val store: MutableMap<Int, Position> = ConcurrentHashMap()

    /**
     * Topics that have been marked READ this session. While a topic is sealed, [save] is a no-op and
     * any previously stored position is cleared.
     *
     * Why (log 25_06-10-52-43, "stuck on old already-read post 143999521"): a topic that has just
     * become READ resolves every fresh list re-open to the server `getlastpost` bookmark (the genuine
     * last/read post). The return-position store, however, also records the post that happened to be
     * in the viewport when the user left — and on a READ topic that is frequently a post the user
     * scrolled UP to re-read (e.g. the TOP post of the last page). [TabReentryRestorePolicy] then
     * overrides the authoritative `getlastpost` re-open with that drifted-up snapshot, so EVERY
     * subsequent open sticks on the stale post.
     *
     * The store is meant to resume an *in-progress* (unread / actively-read) topic across a TAB
     * re-entry, NOT to hijack fresh re-opens of a finished topic. Sealing on mark-read keeps the
     * documented multi-back tab-reentry restore intact (that topic is not marked read during the
     * back navigation) while stopping a read topic from accumulating a hijacking snapshot. The seal
     * is lifted by [clearReadSeal] the moment the topic is genuinely unread again.
     */
    private val readSealedTopics: MutableSet<Int> = java.util.Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Record the position the user is leaving. Only stores entries that can actually re-position
     * the viewport: a positive topic id and a non-blank anchor post id (the #entry the restore URL
     * is built from). Calls without a usable post id are ignored so we never downgrade a good saved
     * position to a bare st= page-top. Sealed (already-read) topics are ignored entirely.
     */
    fun save(topicId: Int, pageSt: Int, postId: String?, scrollY: Int) {
        if (topicId <= 0) return
        if (topicId in readSealedTopics) return
        val normalizedPost = postId?.trim()?.removePrefix("entry")?.removePrefix("ENTRY")
                ?.takeIf { it.isNotEmpty() }
                ?: return
        store[topicId] = Position(
                topicId = topicId,
                pageSt = pageSt,
                postId = normalizedPost,
                scrollY = scrollY
        )
    }

    fun peek(topicId: Int): Position? {
        if (topicId <= 0) return null
        return store[topicId]
    }

    fun clear(topicId: Int) {
        if (topicId <= 0) return
        store.remove(topicId)
    }

    /**
     * Seal a topic as READ: drop any stored position and ignore future [save] calls until the topic
     * is genuinely unread again ([clearReadSeal]). Re-opens then defer to the server `getlastpost`
     * bookmark instead of a drifted-up viewport snapshot.
     */
    fun markRead(topicId: Int) {
        if (topicId <= 0) return
        store.remove(topicId)
        readSealedTopics.add(topicId)
    }

    /** Lift the READ seal (topic is unread again) so a fresh in-progress position can be recorded. */
    fun clearReadSeal(topicId: Int) {
        if (topicId <= 0) return
        readSealedTopics.remove(topicId)
    }

    fun isReadSealed(topicId: Int): Boolean = topicId > 0 && topicId in readSealedTopics
}
