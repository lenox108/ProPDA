package forpdateam.ru.forpda.entity.remote.favorites

/**
 * Created by radiationx on 22.09.16.
 */

class FavItem : IFavItem {
    override var favId: Int = 0
    override var topicId: Int = 0
    override var forumId: Int = 0
    override var authorId: Int = 0
    override var lastUserId: Int = 0
    override var stParam: Int = 0
    override var pages: Int = 0
    override var curatorId: Int = 0
    override var trackType: String? = null
    override var infoColor: String? = null
    override var topicTitle: String? = null
    override var forumTitle: String? = null
    override var authorUserNick: String? = null
    override var lastUserNick: String? = null
    override var date: String? = null
    var displayDateOverride: String? = null
    /** Parsed showtopic href from favorites row; used for unread navigation hints (not persisted). */
    var listingHref: String? = null
    /** Inspector fav snapshot marked this topic unread on last merge (not persisted). */
    var inspectorMarkedUnread: Boolean = false
    /** Locally hidden from the main favorites list (routed into the collapsible "Скрытое" section). Not persisted on the item. */
    var isHidden: Boolean = false
    /** Notifications for this topic are muted locally (device-side mute set). Transient UI flag, not persisted. */
    var isNotifyMuted: Boolean = false
    override var desc: String? = null
    override var curatorNick: String? = null
    override var subType: String? = null
    override var isPin = false
    override var isForum = false
    override var isNew: Boolean = false
    var readState: FavoriteReadState = FavoriteReadState.UNKNOWN
    override var unreadPostCount: Int = 0
    override var localReadPostId: Int = 0
    override var localReadPostDateMillis: Long = 0L
    override var isPoll: Boolean = false
    override var isClosed: Boolean = false

    constructor() {}

    /** Row should render as unread (bold title / badge) even if legacy [isNew] flag is stale. */
    fun isUnreadForDisplay(): Boolean =
            readState == FavoriteReadState.UNREAD || isNew || unreadPostCount > 0

    constructor(item: IFavItem) {
        favId = item.favId
        topicId = item.topicId
        forumId = item.forumId
        authorId = item.authorId
        lastUserId = item.lastUserId
        stParam = item.stParam
        pages = item.pages
        curatorId = item.curatorId

        trackType = item.trackType
        infoColor = item.infoColor
        topicTitle = item.topicTitle
        forumTitle = item.forumTitle
        authorUserNick = item.authorUserNick
        lastUserNick = item.lastUserNick
        date = item.date
        displayDateOverride = (item as? FavItem)?.displayDateOverride
        listingHref = (item as? FavItem)?.listingHref
        inspectorMarkedUnread = (item as? FavItem)?.inspectorMarkedUnread == true
        isHidden = (item as? FavItem)?.isHidden == true
        isNotifyMuted = (item as? FavItem)?.isNotifyMuted == true
        desc = item.desc
        curatorNick = item.curatorNick
        subType = item.subType

        isPin = item.isPin
        isForum = item.isForum

        isNew = item.isNew
        readState = (item as? FavItem)?.readState ?: when {
            item.isNew -> FavoriteReadState.UNREAD
            else -> FavoriteReadState.UNKNOWN
        }
        unreadPostCount = item.unreadPostCount
        localReadPostId = item.localReadPostId
        localReadPostDateMillis = item.localReadPostDateMillis
        isPoll = item.isPoll
        isClosed = item.isClosed
    }

}
