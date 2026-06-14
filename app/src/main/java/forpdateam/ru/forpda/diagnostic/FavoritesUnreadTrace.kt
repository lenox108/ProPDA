package forpdateam.ru.forpda.diagnostic

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import java.security.MessageDigest
import java.util.Locale

/**
 * DEBUG-only structured tracing for favorites unread pipeline.
 *
 * Mapping: [FavItem.isNew] = server isUnread; [FavItem.unreadPostCount] = badge hint (+N / inspector).
 * No separate ReadState.UNKNOWN in Room — ambiguous rows keep cached unread via [preserveCachedUnreadOnRefresh].
 */
object FavoritesUnreadTrace {

    fun inspectorSnapshot(eventCount: Int, unreadTopicCount: Int) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "inspector_snapshot",
                mapOf(
                        "eventCount" to eventCount,
                        "unreadTopicCount" to unreadTopicCount,
                )
        )
    }

    fun inspectorRowMerged(
            topicId: Int,
            title: String?,
            inInspector: Boolean,
            timeStamp: Long?,
            lastTimeStamp: Long?,
            msgCount: Int?,
            inspectorUnread: Boolean,
            htmlReadState: String,
            mergeReason: String?
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "inspector_row_merged",
                mapOf(
                        "topicId" to topicId,
                        "titleHash" to titleHash(title),
                        "inInspector" to inInspector,
                        "timeStamp" to timeStamp,
                        "lastTimeStamp" to lastTimeStamp,
                        "msgCount" to msgCount,
                        "inspectorUnread" to inspectorUnread,
                        "htmlReadState" to htmlReadState,
                        "mergeReason" to mergeReason
                )
        )
    }

    fun loadStarted(source: String, sortUnreadOnTop: Boolean) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "favorites_load_started",
                mapOf(
                        "source" to source,
                        "sortUnreadOnTop" to sortUnreadOnTop
                )
        )
    }

    fun htmlReceived(source: String, htmlLen: Int, htmlHash: String, itemCountHint: Int? = null) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "favorites_html_received",
                mapOf(
                        "source" to source,
                        "htmlLen" to htmlLen,
                        "htmlHash" to htmlHash,
                        "itemCountHint" to itemCountHint
                )
        )
    }

    fun topicParsed(
            topicId: Int,
            title: String?,
            rowClasses: String? = null,
            hasUnreadIcon: Boolean = false,
            rawUnreadMarkerFound: String?,
            parsedReadState: String,
            parsedIsUnread: Boolean,
            unreadUrlPresent: Boolean,
            unreadPostCount: Int
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                if (parsedIsUnread) "unread_marker_detected" else "topic_parsed",
                mapOf(
                        "topicId" to topicId,
                        "titleHash" to titleHash(title),
                        "rowClasses" to rowClasses,
                        "hasUnreadIcon" to hasUnreadIcon,
                        "rawUnreadMarkerFound" to rawUnreadMarkerFound,
                        "parsedReadState" to parsedReadState,
                        "parsedIsUnread" to parsedIsUnread,
                        "unreadUrlPresent" to unreadUrlPresent,
                        "unreadPostCount" to unreadPostCount
                )
        )
    }

    fun unreadStateUnknown(topicId: Int, title: String?) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "unread_state_unknown",
                mapOf(
                        "topicId" to topicId,
                        "titleHash" to titleHash(title)
                )
        )
    }

    fun modelMapped(
            topicId: Int,
            title: String?,
            parsedReadState: String,
            parsedIsUnread: Boolean,
            unreadPostCount: Int,
            source: String
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "model_mapped",
                mapOf(
                        "topicId" to topicId,
                        "titleHash" to titleHash(title),
                        "parsedReadState" to parsedReadState,
                        "parsedIsUnread" to parsedIsUnread,
                        "unreadPostCount" to unreadPostCount,
                        "source" to source
                )
        )
    }

    fun cacheMerged(
            topicId: Int,
            title: String?,
            cachedIsUnread: Boolean?,
            cachedReadState: String? = null,
            finalReadState: String,
            finalIsUnread: Boolean,
            finalUnreadPostCount: Int,
            source: String,
            reason: String? = null
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "cache_merged",
                mapOf(
                        "topicId" to topicId,
                        "titleHash" to titleHash(title),
                        "cachedIsUnread" to cachedIsUnread,
                        "cachedReadState" to cachedReadState,
                        "finalReadState" to finalReadState,
                        "finalIsUnread" to finalIsUnread,
                        "finalUnreadPostCount" to finalUnreadPostCount,
                        "source" to source,
                        "reason" to reason
                )
        )
    }

    fun sortingApplied(
            sortUnreadOnTop: Boolean,
            unreadCount: Int,
            totalCount: Int
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "sorting_applied",
                mapOf(
                        "sortUnreadOnTop" to sortUnreadOnTop,
                        "unreadCount" to unreadCount,
                        "totalCount" to totalCount
                )
        )
    }

    fun uiBound(item: FavItem, showUnreadBadge: Boolean, showDot: Boolean) {
        if (item.isForum) return
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_FAVORITES_UNREAD,
                "ui_bound",
                mapOf(
                        "topicId" to item.topicId,
                        "titleHash" to titleHash(item.topicTitle),
                        "finalReadState" to item.readState.name,
                        "finalIsUnread" to item.isUnreadForDisplay(),
                        "showUnreadBadgeSetting" to showUnreadBadge,
                        "showDotSetting" to showDot,
                        "unreadPostCount" to item.unreadPostCount,
                        "unreadUrlPresent" to hasUnreadListingHref(item.listingHref)
                )
        )
    }

    private fun hasUnreadListingHref(listingHref: String?): Boolean {
        val href = listingHref?.trim().orEmpty()
        return href.isNotEmpty() && (
                href.contains("view=getnewpost", ignoreCase = true) ||
                        href.contains("view=getlastpost", ignoreCase = true)
                )
    }

    fun titleHash(title: String?): String {
        val text = title.orEmpty()
        if (text.isEmpty()) return "empty"
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }.take(12)
    }
}
