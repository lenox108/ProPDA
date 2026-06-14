package forpdateam.ru.forpda.entity.remote.topics

/**
 * Created by radiationx on 01.03.17.
 * Converted to Kotlin.
 */
data class TopicItem(
    var isPinned: Boolean = false,
    var isAnnounce: Boolean = false,
    var isForum: Boolean = false,
    var isNew: Boolean = false,
    var isPoll: Boolean = false,
    var isClosed: Boolean = false,
    var id: Int = 0,
    /**
     * Исходный id из атрибутов списка (например, `data-topic`), если в процессе парсинга
     * мы выбрали другой id из href заголовка темы (showtopic=...).
     *
     * Полезно для диагностики и кейсов «перенесённой» темы-указателя.
     */
    var oldId: Int = 0,
    var authorId: Int = 0,
    var lastUserId: Int = 0,
    var curatorId: Int = 0,
    var title: String? = null,
    var desc: String? = null,
    var authorNick: String? = null,
    var lastUserNick: String? = null,
    var date: String? = null,
    var curatorNick: String? = null,
    var announceUrl: String? = null,
    var pages: Int = 0,
    /** Сырой href заголовка темы в списке — может отличаться от showtopic=id после переноса темы. */
    var listingHref: String? = null,
    /**
     * Тема-«перенесённый указатель»: в исходном форуме остаётся заглушка с `&raquo;` и подписью «перемещена:»,
     * а сам пост лежит уже в новой теме (другом id). Сервер 302-редиректит «голый» `?showtopic=ID`,
     * но 404-ит на `?showtopic=ID&view=getnewpost` — поэтому такие пункты нельзя открывать через unread-fallback.
     */
    var isRelocated: Boolean = false
)
