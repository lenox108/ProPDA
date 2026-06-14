package forpdateam.ru.forpda.entity.remote.reputation

/**
 * Created by radiationx on 20.03.17.
 */

/**
 * One reputation history row (desktop `act=rep&view=history`).
 * Also exposed as [ReputationEntry].
 */
class RepItem {
    /** Reputation entry id from `rep-row-{id}`. */
    var id: Int = 0
    var userId: Int = 0
    var title: String? = null
    var userNick: String? = null
    var sourceUrl: String? = null
    var sourceTitle: String? = null
    var image: String? = null
    var date: String? = null
    /** Server report/appeal URL (`act=report&reputation=...`). Null when action is unavailable. */
    var reportActionUrl: String? = null
    /** True = plus, false = minus, null = unknown. */
    var isPositive: Boolean? = null

    val authorId: Int get() = userId
    val authorName: String? get() = userNick
    val reason: String? get() = title

    fun hasReportAction(): Boolean = !reportActionUrl.isNullOrBlank()
}

typealias ReputationEntry = RepItem
