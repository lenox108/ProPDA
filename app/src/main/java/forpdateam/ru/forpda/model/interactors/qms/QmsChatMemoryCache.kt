package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator
import java.util.concurrent.ConcurrentHashMap

data class CachedQmsChat(
        val chat: QmsChatModel,
        val storedAtMs: Long,
        val pageKind: QmsHtmlValidator.PageKind,
        val messageCount: Int
)

object QmsChatMemoryCache {

    private const val MAX_AGE_MS = 15 * 60 * 1000L
    private val entries = ConcurrentHashMap<String, CachedQmsChat>()

    fun key(userId: Int, themeId: Int) = "$userId:$themeId"

    fun get(userId: Int, themeId: Int, nowMs: Long = System.currentTimeMillis()): CachedQmsChat? {
        val cached = entries[key(userId, themeId)] ?: return null
        if (nowMs - cached.storedAtMs > MAX_AGE_MS) {
            entries.remove(key(userId, themeId))
            return null
        }
        return if (isValid(cached)) cached else {
            entries.remove(key(userId, themeId))
            null
        }
    }

    fun put(userId: Int, themeId: Int, chat: QmsChatModel, pageKind: QmsHtmlValidator.PageKind) {
        if (!isValidKind(pageKind, chat)) return
        entries[key(userId, themeId)] = CachedQmsChat(
                chat = chat,
                storedAtMs = System.currentTimeMillis(),
                pageKind = pageKind,
                messageCount = chat.messages.size
        )
    }

    fun invalidate(userId: Int, themeId: Int) {
        entries.remove(key(userId, themeId))
    }

    fun invalidateAll() {
        entries.clear()
    }

    fun isValid(cached: CachedQmsChat): Boolean = isValidKind(cached.pageKind, cached.chat)

    fun cacheAgeMinutes(userId: Int, themeId: Int, nowMs: Long = System.currentTimeMillis()): Int? =
            get(userId, themeId, nowMs)?.let { cached ->
                ((nowMs - cached.storedAtMs) / 60_000L).toInt().coerceAtLeast(0)
            }

    fun cacheAgeMs(userId: Int, themeId: Int, nowMs: Long = System.currentTimeMillis()): Long? =
            get(userId, themeId, nowMs)?.let { cached ->
                (nowMs - cached.storedAtMs).coerceAtLeast(0L)
            }

    /** In-memory cache hit suitable for immediate UI (same rules as [QmsChatOpenPipeline]). */
    fun toLoadOutcome(userId: Int, themeId: Int): QmsChatLoadOutcome? {
        val cached = get(userId, themeId) ?: return null
        val cacheValid = cached.messageCount > 0 ||
                cached.pageKind == QmsHtmlValidator.PageKind.QMS_EMPTY_THREAD
        if (!cacheValid) {
            invalidate(userId, themeId)
            return null
        }
        return if (cached.messageCount > 0) {
            QmsChatLoadOutcome.Content(cached.chat, fromCache = true, pageKind = cached.pageKind)
        } else {
            QmsChatLoadOutcome.Empty(cached.chat, fromCache = true)
        }
    }

    private fun isValidKind(pageKind: QmsHtmlValidator.PageKind, chat: QmsChatModel): Boolean =
            when (pageKind) {
                QmsHtmlValidator.PageKind.QMS_THREAD -> chat.messages.isNotEmpty()
                QmsHtmlValidator.PageKind.QMS_EMPTY_THREAD -> true
                else -> false
            }
}
