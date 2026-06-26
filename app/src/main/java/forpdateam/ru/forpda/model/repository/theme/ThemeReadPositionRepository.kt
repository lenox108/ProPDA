package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.diagnostic.TopicHighlightDiagnostics
import forpdateam.ru.forpda.presentation.theme.ReadPosition
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-topic "last viewed" position.
 *
 * This is the input to [forpdateam.ru.forpda.presentation.theme.HighlightResolver]
 * for the `LAST_READ` highlight. It is intentionally in-memory for now —
 * persistence to disk can be added later as a separate, isolated change. The
 * repository is a singleton so all open topics share the same view of the
 * user's read position.
 *
 * Update rules (Step 5):
 *  - Do not save before a post is actually visible.
 *  - Do not save when the post id is missing.
 *  - Do not clear on refresh.
 *  - Overwriting the unread target is forbidden by the resolver contract;
 *    this repo never has to know about unread targets.
 */
@Singleton
class ThemeReadPositionRepository @Inject constructor() {

    private val store: MutableMap<Long, ReadPosition> = ConcurrentHashMap()

    fun get(topicId: Long): ReadPosition? {
        if (topicId <= 0L) return null
        val value = store[topicId]
        if (value != null) {
            TopicHighlightDiagnostics.readPositionLoaded(
                    topicId = topicId,
                    lastViewedPostId = value.lastViewedPostId,
                    lastViewedPage = value.lastViewedPage
            )
        }
        return value
    }

    fun save(position: ReadPosition) {
        if (position.topicId <= 0L) return
        if (position.lastViewedPostId <= 0L) return
        if (position.lastViewedPage <= 0) return
        store[position.topicId] = position
        TopicHighlightDiagnostics.readPositionSaved(
                topicId = position.topicId,
                lastViewedPostId = position.lastViewedPostId,
                lastViewedPage = position.lastViewedPage
        )
    }

    fun clear(topicId: Long) {
        if (topicId <= 0L) return
        store.remove(topicId)
    }
}
