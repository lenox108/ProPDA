package forpdateam.ru.forpda.presentation.attachments

import dagger.hilt.android.lifecycle.HiltViewModel
import forpdateam.ru.forpda.entity.remote.attachments.TopicAttachment
import forpdateam.ru.forpda.model.data.remote.api.attachments.TopicAttachmentsApi
import forpdateam.ru.forpda.presentation.BaseViewModel
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TopicAttachmentsViewModel @Inject constructor(
        private val api: TopicAttachmentsApi,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
) : BaseViewModel() {

    private var topicId: Int = 0
    private var loadJob: Job? = null
    private var loaded = false

    /** Полный распарсенный список; в UI отдаём его порциями (клиентская пагинация). */
    private var fullList: List<TopicAttachment> = emptyList()

    private val _items = MutableStateFlow<List<TopicAttachment>>(emptyList())
    val items: StateFlow<List<TopicAttachment>> = _items.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Список обрезан по лимиту размера (очень много вложений) — показать пользователю уведомление. */
    private val _truncated = MutableStateFlow(false)
    val truncated: StateFlow<Boolean> = _truncated.asStateFlow()

    fun setTopicId(id: Int) {
        topicId = id
    }

    fun start() {
        if (loaded) return
        loaded = true
        load()
    }

    fun load() {
        if (topicId <= 0) return
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val result = withContext(Dispatchers.IO) { api.getAttachments(topicId) }
                fullList = result.items
                _truncated.value = result.truncated
                _items.value = fullList.take(PAGE_SIZE) // первая страница
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Есть ли ещё нерендеренные вложения в [fullList]. */
    fun hasMore(): Boolean = _items.value.size < fullList.size

    /** Показать следующую порцию (вызывается при подскролле к концу списка). */
    fun loadMore() {
        val shown = _items.value.size
        if (shown >= fullList.size) return
        _items.value = fullList.take((shown + PAGE_SIZE).coerceAtMost(fullList.size))
    }

    /**
     * Тап по вложению → переиспользуем медиа-роутинг LinkHandler: `forum/dl/post/…` картинки
     * уходят во встроенный ImageViewer, прочие файлы — во встроенный загрузчик.
     */
    fun onItemClick(item: TopicAttachment) {
        linkHandler.handle(item.url, router)
    }

    private companion object {
        const val PAGE_SIZE = 40
    }
}
