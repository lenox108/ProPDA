package forpdateam.ru.forpda.model.interactors

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CrossScreenInteractor {

    data class ArticleCommentsCountUpdate(val articleId: Int, val commentsCount: Int)

    private val announceFlow = MutableSharedFlow<Int>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val articleFlow = MutableSharedFlow<Int>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val deviceFlow = MutableSharedFlow<Int>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val profileFlow = MutableSharedFlow<Int>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val chatFlow = MutableSharedFlow<Int>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val topicFlow = MutableSharedFlow<Int>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val articleCommentsCountFlow = MutableSharedFlow<ArticleCommentsCountUpdate>(
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun observeAnnounce(): Flow<Int> = announceFlow.asSharedFlow()
    fun observeArticle(): Flow<Int> = articleFlow.asSharedFlow()
    fun observeDevice(): Flow<Int> = deviceFlow.asSharedFlow()
    fun observeProfile(): Flow<Int> = profileFlow.asSharedFlow()
    fun observeChat(): Flow<Int> = chatFlow.asSharedFlow()
    fun observeTopic(): Flow<Int> = topicFlow.asSharedFlow()
    fun observeArticleCommentsCount(): Flow<ArticleCommentsCountUpdate> =
            articleCommentsCountFlow.asSharedFlow()

    fun onLoadAnnounce(id: Int) {
        announceFlow.tryEmit(id)
    }

    fun onLoadArticle(id: Int) {
        articleFlow.tryEmit(id)
    }

    fun onLoadDevice(id: Int) {
        deviceFlow.tryEmit(id)
    }

    fun onLoadProfile(id: Int) {
        profileFlow.tryEmit(id)
    }

    fun onLoadChat(id: Int) {
        chatFlow.tryEmit(id)
    }

    fun onLoadTopic(id: Int) {
        topicFlow.tryEmit(id)
    }

    fun onArticleCommentsCountReconciled(articleId: Int, commentsCount: Int) {
        if (articleId <= 0 || commentsCount < 0) return
        articleCommentsCountFlow.tryEmit(ArticleCommentsCountUpdate(articleId, commentsCount))
    }
}
