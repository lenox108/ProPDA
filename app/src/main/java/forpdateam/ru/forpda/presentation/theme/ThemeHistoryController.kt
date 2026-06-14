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
    }

    private val history = mutableListOf<ThemePage>()
    private val backSnapshots = linkedMapOf<String, TopicBackSnapshot>()

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
        history.add(themePage)
        if (BuildConfig.DEBUG) {
            Timber.d("saveToHistory: topicId=${themePage.id} st=${themePage.st} historySize=${history.size}")
        }
        Log.i(
                THEME_HISTORY_TAG,
                "push topic=${themePage.id} st=${themePage.st} page=${themePage.pagination.current} url=${themePage.url} anchor=${themePage.anchorPostId} y=${themePage.scrollY} size=${history.size}"
        )
        ReadStateTrace.log(
                event = "history_push",
                topicId = themePage.id,
                pageSt = themePage.st,
                scrollY = themePage.scrollY,
                anchorPostId = themePage.anchorPostId,
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
                themePage.anchorPostId = prev.anchorPostId
                themePage.anchorOffsetTop = prev.anchorOffsetTop
                themePage.scrollRatio = prev.scrollRatio
                themePage.wasNearBottom = prev.wasNearBottom
                if (prev.renderSignature != null && prev.renderSignature != themePage.renderSignature) {
                    prev.html = null
                }
            }
            history[history.size - 1] = themePage
            Log.i(
                    THEME_HISTORY_TAG,
                    "updateLast topic=${themePage.id} st=${themePage.st} page=${themePage.pagination.current} url=${themePage.url} anchor=${themePage.anchorPostId} y=${themePage.scrollY} size=${history.size}"
            )
            ReadStateTrace.log(
                    event = "history_update_last",
                    topicId = themePage.id,
                    pageSt = themePage.st,
                    scrollY = themePage.scrollY,
                    anchorPostId = themePage.anchorPostId,
                    allowedAsNavTarget = true,
                    source = "history",
                    reason = "updateHistoryLast"
            )
        }
    }

    /**
     * Возвращает предыдущую страницу из истории.
     * @return Пара (ThemePage, Boolean) где Boolean = true если нужно загрузить с сервера (html пустой)
     */
    fun backPage(): ThemePage? {
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
