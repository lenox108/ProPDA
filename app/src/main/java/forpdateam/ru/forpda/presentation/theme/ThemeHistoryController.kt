package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.NavBackstackTrace
import forpdateam.ru.forpda.diagnostic.ReadStateTrace
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import android.util.Log
import timber.log.Timber

/**
 * Контроллер для управления историей навигации в теме.
 * Отвечает за сохранение, обновление и навигацию по загруженным страницам темы.
 */
class ThemeHistoryController {
    companion object {
        private const val THEME_HISTORY_TAG = "ThemeHistory"
        /**
         * Max scrollY delta (in pixels) under which a consecutive identical
         * (topicId + st + anchorPostId) history entry is considered a duplicate
         * and is replaced instead of appended. 100px is well below the typical
         * post height, so it filters out "I scrolled a hair while the page was
         * reloading" noise without losing meaningful scroll jumps.
         */
        private const val SCROLL_DEDUPE_THRESHOLD_PX = 100
    }

    private val history = mutableListOf<ThemePage>()
    private val backSnapshots = linkedMapOf<String, TopicBackSnapshot>()

    /**
     * C-fix (log 1121483: back after a cross-topic hop landed on st=1260/page-64 instead of the
     * st=1180/post-143876380 page the user was viewing). A cross-topic link opens Topic B in a new
     * tab WITHOUT pushing an in-tab entry, so the source page stays the in-tab history TOP. When
     * Topic B's tab is exhausted and the source tab regains the back gesture, [backPage] would pop
     * that TOP and land on the page BELOW it (a stale earlier page of the same topic). This guard
     * marks the source key so the FIRST back after the cross-topic open RESTORES the top entry in
     * place instead of discarding it. Cleared after one use and on any normal in-tab navigation.
     */
    private var crossTopicReturnGuardKey: String? = null

    fun snapshotKey(topicId: Int, pageSt: Int): String = TopicBackSnapshot.key(topicId, pageSt)

    fun captureBackSnapshot(snapshot: TopicBackSnapshot) {
        if (snapshot.topicId <= 0) return
        backSnapshots[snapshotKey(snapshot.topicId, snapshot.pageSt)] = snapshot
        if (BuildConfig.DEBUG) {
            Log.i(
                    THEME_HISTORY_TAG,
                    "snapshotCapture topic=${snapshot.topicId} st=${snapshot.pageSt} post=${snapshot.visiblePostId} y=${snapshot.scrollOffset} ratio=${snapshot.scrollRatio} status=${snapshot.status}"
            )
        }
    }

    fun peekBackSnapshot(topicId: Int, pageSt: Int): TopicBackSnapshot? =
            backSnapshots[snapshotKey(topicId, pageSt)]

    /**
     * Cross-topic BACK st/anchor mismatch fix (log 1121483: back landed on st=1260/page-64 while the
     * source post 143979796 lives on st=1240/page-63). In HYBRID mode the loaded `currentPage.st`
     * (1260) can differ from the page the user actually navigated FROM (st=1240), but the native back
     * snapshot is keyed by the SOURCE page's st. Building the restore URL from `currentPage.st` then
     * pairs the right post id with the WRONG st, so the post isn't on the loaded page and the anchor
     * falls back to last-post-on-page. This finds the usable snapshot for `topicId` whose captured
     * `visiblePostId` equals the post we are restoring to, so the caller can pull the CORRECT st.
     * Returns the most recently captured match.
     */
    fun findUsableBackSnapshotByPost(topicId: Int, visiblePostId: String?): TopicBackSnapshot? {
        if (topicId <= 0) return null
        val normalized = visiblePostId?.removePrefix("entry")?.takeIf { it.isNotBlank() } ?: return null
        // Prefer the FIRST-INSERTED matching capture. `backSnapshots` is a LinkedHashMap, and
        // re-assigning an existing key keeps its original insertion position — so iteration order is
        // capture order. The authoritative cross-topic source-anchor snapshot (keyed by the genuine
        // source page st, e.g. 1240) is captured at link-tap time, BEFORE the trailing `onPauseOrHide`
        // viewport snapshot that re-stamps the same visible post under the loaded `currentPage.st`
        // (e.g. 1260) — a st/post mismatch. The earliest entry is the one whose st actually contains
        // the post, and insertion order makes this deterministic regardless of clock ties.
        return backSnapshots.values
                .firstOrNull {
                    it.topicId == topicId &&
                            it.isUsable() &&
                            it.visiblePostId?.removePrefix("entry") == normalized
                }
    }

    /**
     * C-fix: arm the cross-topic return guard for the CURRENT in-tab top entry. Called right
     * before a cross-topic link opens Topic B in a new tab. The next [backPage] for this topic+st
     * restores the top entry in place rather than popping past it. No-op if the top entry does not
     * match the supplied source (defensive — the source page must be the page actually on top).
     */
    fun armCrossTopicReturnGuard(topicId: Int, pageSt: Int) {
        if (topicId <= 0) return
        val top = history.lastOrNull() ?: return
        if (top.id != topicId || top.st != pageSt) return
        crossTopicReturnGuardKey = snapshotKey(topicId, pageSt)
    }

    fun isCrossTopicReturnGuardArmedFor(topicId: Int, pageSt: Int): Boolean =
            crossTopicReturnGuardKey != null &&
                    crossTopicReturnGuardKey == snapshotKey(topicId, pageSt)

    fun clearCrossTopicReturnGuard() {
        crossTopicReturnGuardKey = null
    }

    fun consumeBackSnapshot(topicId: Int, pageSt: Int): TopicBackSnapshot? =
            backSnapshots.remove(snapshotKey(topicId, pageSt))

    fun markBackSnapshotStale(topicId: Int, pageSt: Int) {
        val key = snapshotKey(topicId, pageSt)
        val existing = backSnapshots[key] ?: return
        backSnapshots[key] = existing.copy(status = TopicBackSnapshotStatus.STALE)
    }

    /**
     * Текущая страница истории (последняя)
     */
    val currentPage: ThemePage?
        get() = history.lastOrNull()

    /**
     * Размер истории
     */
    val size: Int
        get() = history.size

    /**
     * Можно ли вернуться назад (есть ли предыдущая страница в истории)
     */
    fun canGoBack(): Boolean = history.size > 1

    /**
     * Сохраняет страницу в историю.
     * Используется при первой загрузке темы или навигации на новую страницу.
     */
    fun saveToHistory(themePage: ThemePage) {
        // Fix F8: dedupe consecutive identical entries. When the network re-fetches
        // the same topic+st+anchor and emits another saveToHistory with a slightly
        // different scrollY (e.g. mid-scroll capture vs. anchor settle), we should
        // REPLACE the previous entry instead of growing the history. Without this,
        // the BACK stack can contain two visually identical "current" pages, and
        // the first BACK press just lands on the duplicate and feels like no-op.
        // Canonicalize the anchor for both the dedupe key and the diagnostic log:
        // parser sometimes sets only `anchor` (entry-prefixed) and leaves
        // `anchorPostId` null (e.g. findpost reload path), so comparing the
        // unprefixed form only would never dedupe across the two shapes.
        val themeAnchor = canonicalAnchor(themePage)
        val last = history.lastOrNull()
        if (last != null &&
                last.id == themePage.id &&
                last.st == themePage.st &&
                canonicalAnchor(last) == themeAnchor
        ) {
            val scrollDelta = kotlin.math.abs((last.scrollY) - (themePage.scrollY))
            if (scrollDelta <= SCROLL_DEDUPE_THRESHOLD_PX) {
                // Identical enough — replace the tail instead of appending.
                history[history.size - 1] = themePage
                if (BuildConfig.DEBUG) {
                    Timber.d("saveToHistory: deduped topicId=${themePage.id} st=${themePage.st} historySize=${history.size}")
                }
                Log.i(
                        THEME_HISTORY_TAG,
                        "dedupe topic=${themePage.id} st=${themePage.st} page=${themePage.pagination.current} url=${themePage.url} anchor=$themeAnchor prevY=${last.scrollY} newY=${themePage.scrollY} size=${history.size}"
                )
                ReadStateTrace.log(
                        event = "history_dedupe",
                        topicId = themePage.id,
                        pageSt = themePage.st,
                        scrollY = themePage.scrollY,
                        anchorPostId = themeAnchor,
                        allowedAsNavTarget = true,
                        source = "history",
                        reason = "saveToHistory"
                )
                return
            }
            // ScrollY differs by more than the threshold — keep both so BACK can
            // actually return the user to the previous scroll position.
        }
        // A genuine in-tab forward navigation invalidates the cross-topic return guard: the user
        // moved to a new page within this tab, so the next back is an ordinary in-tab pop.
        crossTopicReturnGuardKey = null
        history.add(themePage)
        if (BuildConfig.DEBUG) {
            Timber.d("saveToHistory: topicId=${themePage.id} st=${themePage.st} historySize=${history.size}")
        }
        Log.i(
                THEME_HISTORY_TAG,
                "push topic=${themePage.id} st=${themePage.st} page=${themePage.pagination.current} url=${themePage.url} anchor=$themeAnchor y=${themePage.scrollY} size=${history.size}"
        )
        ReadStateTrace.log(
                event = "history_push",
                topicId = themePage.id,
                pageSt = themePage.st,
                scrollY = themePage.scrollY,
                anchorPostId = themeAnchor,
                allowedAsNavTarget = true,
                source = "history",
                reason = "saveToHistory"
        )
        NavBackstackTrace.log(
                event = "theme_history_push",
                navigator = "ThemeHistoryController",
                topicId = themePage.id,
                historySize = history.size
        )
    }

    /**
     * Обновляет последнюю страницу в истории с сохранением scrollY/anchor.
     * Используется при REFRESH или BACK для сохранения позиции прокрутки.
     */
    fun updateHistoryLast(themePage: ThemePage) {
        if (history.isNotEmpty()) {
            history.last().let { prev ->
                // Не подмешиваем prev.anchors: addAll в конец делал anchor = последний = старый якорь
                // после REFRESH/BACK, хотя парсер уже выставил правильный elem_to_scroll.
                themePage.scrollY = prev.scrollY
                // Preserve the saved scroll anchor across REFRESH/BACK in canonical
                // form: if prev.anchorPostId is null (e.g. from an old build that
                // pre-dated the parser fix for the findpost path) but prev.anchor
                // is set, do not silently drop it.
                val prevAnchor = canonicalAnchor(prev)
                themePage.anchorPostId = prevAnchor ?: prev.anchorPostId
                // Multi-back anchor loss fix: preserve the authoritative explicit-open anchor across
                // REFRESH/BACK so a re-rendered findpost page keeps restoring its original source post.
                if (themePage.authoritativeAnchorPostId.isNullOrBlank()) {
                    themePage.authoritativeAnchorPostId = prev.authoritativeAnchorPostId
                }
                themePage.anchorOffsetTop = prev.anchorOffsetTop
                themePage.scrollRatio = prev.scrollRatio
                themePage.wasNearBottom = prev.wasNearBottom
                if (prev.renderSignature != null && prev.renderSignature != themePage.renderSignature) {
                    prev.html = null
                }
            }
            val themeAnchor = canonicalAnchor(themePage)
            history[history.size - 1] = themePage
            Log.i(
                    THEME_HISTORY_TAG,
                    "updateLast topic=${themePage.id} st=${themePage.st} page=${themePage.pagination.current} url=${themePage.url} anchor=$themeAnchor y=${themePage.scrollY} size=${history.size}"
            )
            ReadStateTrace.log(
                    event = "history_update_last",
                    topicId = themePage.id,
                    pageSt = themePage.st,
                    scrollY = themePage.scrollY,
                    anchorPostId = themeAnchor,
                    allowedAsNavTarget = true,
                    source = "history",
                    reason = "updateHistoryLast"
            )
        }
    }

    /**
     * Returns the unprefixed (no `entry`/`ENTRY`) post id for the page's scroll
     * anchor. Mirrors `ThemeViewModel.getAnchorPostId()` fallback. Used as the
     * dedupe key in saveToHistory and as the diagnostic anchor in history logs,
     * so a parser path that only fills `anchor` (entry-prefixed) and leaves
     * `anchorPostId` null no longer breaks the F8 dedupe.
     */
    private fun canonicalAnchor(themePage: ThemePage): String? {
        themePage.anchorPostId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return themePage.anchor
                ?.trim()
                ?.removePrefix("entry")
                ?.removePrefix("ENTRY")
                ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Возвращает предыдущую страницу из истории.
     * @return Пара (ThemePage, Boolean) где Boolean = true если нужно загрузить с сервера (html пустой)
     */
    fun backPage(): ThemePage? {
        // C-fix: if the in-tab top entry is the source of a cross-topic open, the user is
        // returning to it from Topic B's (now-closed) tab — restore it in place rather than
        // popping past it onto an earlier page of the same topic.
        val top = history.lastOrNull()
        if (top != null &&
                crossTopicReturnGuardKey != null &&
                crossTopicReturnGuardKey == snapshotKey(top.id, top.st)
        ) {
            crossTopicReturnGuardKey = null
            if (BuildConfig.DEBUG) {
                Timber.d(
                        "backPage: cross-topic return restore-in-place id=${top.id} st=${top.st} historySize=${history.size}"
                )
            }
            Log.i(
                    THEME_HISTORY_TAG,
                    "back cross_topic_return_in_place topic=${top.id} st=${top.st} page=${top.pagination.current} url=${top.url} anchor=${top.anchorPostId} y=${top.scrollY} size=${history.size}"
            )
            NavBackstackTrace.log(
                    event = "theme_history_cross_topic_return",
                    navigator = "ThemeHistoryController",
                    topicId = top.id,
                    historySize = history.size,
                    reason = "restore_in_place"
            )
            return top
        }
        if (history.size <= 1) return null
        history.removeAt(history.size - 1)
        val prev = history.last()
        if (BuildConfig.DEBUG) {
            Timber.d("backPage: prev.id=${prev.id} prev.st=${prev.st} prev.htmlBlank=${prev.html.isNullOrBlank()} historySize=${history.size}")
        }
        Log.i(
                THEME_HISTORY_TAG,
                "pop topic=${prev.id} st=${prev.st} page=${prev.pagination.current} url=${prev.url} htmlBlank=${prev.html.isNullOrBlank()} anchor=${prev.anchorPostId} y=${prev.scrollY} size=${history.size}"
        )
        ReadStateTrace.log(
                event = "history_pop",
                topicId = prev.id,
                pageSt = prev.st,
                scrollY = prev.scrollY,
                anchorPostId = prev.anchorPostId,
                allowedAsNavTarget = true,
                source = "history",
                reason = if (prev.html.isNullOrBlank()) "reload_required" else "restore_cached_page"
        )
        NavBackstackTrace.log(
                event = "theme_history_pop",
                navigator = "ThemeHistoryController",
                topicId = prev.id,
                historySize = history.size,
                reason = if (prev.html.isNullOrBlank()) "blank_html" else "ok"
        )
        return prev
    }

    /**
     * Сохранение scrollY/html/anchorPostId напрямую в указанную страницу, минуя [history.last].
     * Нужно, чтобы при гонке между async-колбэком JS (findFirstVisiblePostId) и подменой
     * history.last() новой темой запись попала в исходный объект страницы, а не в чужой.
     *
     * Валидация: проверяет, что target всё ещё находится в истории, чтобы избежать
     * записи в устаревший объект страницы после навигации.
     */
    fun updatePageHistoryHtml(
            target: ThemePage,
            html: String?,
            scrollY: Int,
            anchorPostId: String?,
            anchorOffsetTop: Double?,
            scrollRatio: Double?,
            wasNearBottom: Boolean? = null
    ) {
        if (target !in history) {
            val lastIndex = history.lastIndex
            val last = history.lastOrNull()
            if (lastIndex >= 0 && last?.id == target.id) {
                history[lastIndex] = target
                Log.i(
                        THEME_HISTORY_TAG,
                        "promoteVisible topic=${target.id} fromSt=${last.st} toSt=${target.st} fromPage=${last.pagination.current} toPage=${target.pagination.current}"
                )
            } else {
                Timber.w("updatePageHistoryHtml: target page not in history, skipping update")
                return
            }
        }
        val currentSignature = currentPage?.renderSignature
        if (currentSignature != null && target.renderSignature != null && currentSignature != target.renderSignature) {
            target.html = null
            Log.i(
                    THEME_HISTORY_TAG,
                    "skipStaleHtml topic=${target.id} st=${target.st} page=${target.pagination.current} targetSig=${target.renderSignature} currentSig=$currentSignature"
            )
            return
        }
        val keepAuthoritative = anchorPostId != null && ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                authoritativeAnchorPostId = target.authoritativeAnchorPostId,
                candidateAnchorPostId = anchorPostId,
        )
        if (keepAuthoritative) {
            // Geometry consistency fix (device log 25_06-22-18-38): a trailing visible-anchor
            // snapshot (e.g. onPauseOrHide JS capture of post 143860995 at y=11810) must NOT
            // overwrite this entry's AUTHORITATIVE explicit-open anchor (143876380 at y=3807)
            // — nor its scroll geometry. Earlier fixes correctly preserved `anchorPostId` but
            // still wrote the visible post's `scrollY`/`anchorOffsetTop`/`scrollRatio` and fed
            // that inconsistent tuple into the back snapshot, so BACK loaded #entry143876380 but
            // scrolled to the visible post's screen location. When the authoritative anchor wins,
            // reject the ENTIRE geometry mutation and do NOT capture a new back snapshot — the
            // existing snapshot for the authoritative post survives untouched.
            Log.i(
                    THEME_HISTORY_TAG,
                    "updatePage kept authoritative topic=${target.id} st=${target.st} page=${target.pagination.current} authoritativePostId=${target.authoritativeAnchorPostId} ignoredPost=$anchorPostId ignoredY=$scrollY"
            )
            return
        }
        target.html = html
        target.scrollY = scrollY
        if (anchorPostId != null) {
            target.anchorPostId = anchorPostId
        }
        if (anchorOffsetTop != null) {
            target.anchorOffsetTop = anchorOffsetTop
        }
        if (scrollRatio != null) {
            target.scrollRatio = scrollRatio
        }
        if (wasNearBottom != null) {
            target.wasNearBottom = wasNearBottom
        }
        captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = target.id,
                        pageSt = target.st,
                        visiblePostId = target.anchorPostId,
                        scrollOffset = scrollY,
                        scrollRatio = scrollRatio,
                        wasNearBottom = wasNearBottom ?: target.wasNearBottom,
                        status = TopicBackSnapshotStatus.CAPTURED
                )
        )
        Log.i(
                THEME_HISTORY_TAG,
                "updatePage topic=${target.id} st=${target.st} page=${target.pagination.current} htmlLen=${html?.length ?: 0} y=$scrollY anchor=$anchorPostId offset=$anchorOffsetTop ratio=$scrollRatio bottom=$wasNearBottom"
        )
    }

    /**
     * Очищает историю.
     */
    fun clear() {
        history.clear()
        backSnapshots.clear()
        crossTopicReturnGuardKey = null
    }

    /**
     * Обновляет HTML содержимое страницы по индексу.
     */
    fun setHistoryBody(index: Int, body: String) {
        if (index in history.indices) {
            val target = history[index]
            val currentSignature = currentPage?.renderSignature
            if (currentSignature == null || target.renderSignature == null || currentSignature == target.renderSignature) {
                target.html = body
            } else {
                target.html = null
                Log.i(
                        THEME_HISTORY_TAG,
                        "skipStaleBody topic=${target.id} st=${target.st} page=${target.pagination.current} targetSig=${target.renderSignature} currentSig=$currentSignature"
                )
            }
        }
    }
}
