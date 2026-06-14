package forpdateam.ru.forpda.entity.remote.news

import java.util.ArrayList

/**
 * Created by radiationx on 02.09.17.
 */

class Comment {
    var id: Int = 0
    var userId: Int = 0
    var userNick: String? = null
    var date: String? = null
    var content: String? = null
    var isEdited = false
    var isDeleted = false
    var isCollapsed = false
    var isCanReply = false
    var actions: Actions = Actions()
    val children = mutableListOf<Comment>()
    var level: Int = 0
    var karma: Karma? = null
    var likedByMe: Boolean = false
    var likeCount: Int = 0
    var likeAction: Action? = null
    var unlikeAction: Action? = null
    var toggleAction: Action? = null
    var isLikePending: Boolean = false

    constructor() {}

    constructor(comment: Comment) {
        this.id = comment.id
        this.userId = comment.userId
        this.userNick = comment.userNick
        this.date = comment.date
        this.content = comment.content
        this.isEdited = comment.isEdited
        this.isDeleted = comment.isDeleted
        this.isCanReply = comment.isCanReply
        this.actions = comment.actions.copy()
        this.level = comment.level
        this.isCollapsed = comment.isCollapsed
        this.karma = comment.karma?.copy()
        this.likedByMe = comment.likedByMe
        this.likeCount = comment.likeCount
        this.likeAction = comment.likeAction?.copy()
        this.unlikeAction = comment.unlikeAction?.copy()
        this.toggleAction = comment.toggleAction?.copy()
        this.isLikePending = comment.isLikePending
    }

    data class Action(
            var url: String? = null,
            var method: String = METHOD_GET,
            val fields: LinkedHashMap<String, String> = LinkedHashMap(),
            var type: Type = Type.UNKNOWN,
            var title: String? = null,
            var token: String? = null,
            var submitText: String? = null,
            var editableElementId: String? = null,
            var editableHtml: String? = null,
            var requiresReason: Boolean = false,
            var reasonFieldName: String? = null,
            var requiresConfirmation: Boolean = false,
            var enabled: Boolean = true
    ) {
        fun isValid(): Boolean = enabled && !url.isNullOrBlank()

        enum class Type {
            UNKNOWN,
            PROFILE,
            REPLY,
            COMMENT_LIKE,
            COMMENT_UNLIKE,
            KARMA_PLUS,
            REPUTATION_PLUS,
            REPUTATION_MINUS,
            EDIT,
            DELETE,
            REPORT,
            HIDE
        }

        companion object {
            const val METHOD_GET = "GET"
            const val METHOD_POST = "POST"
        }
    }

    data class Actions(
            var reply: Action? = null,
            var profile: Action? = null,
            var edit: Action? = null,
            var delete: Action? = null,
            var karmaPlus: Action? = null,
            var like: Action? = null,
            var unlike: Action? = null,
            var toggleLike: Action? = null,
            var hide: Action? = null,
            var reputationPlus: Action? = null,
            var reputationMinus: Action? = null,
            var report: Action? = null
    ) {
        fun hasAny(): Boolean =
                reply?.isValid() == true ||
                        profile?.isValid() == true ||
                        edit?.isValid() == true ||
                        delete?.isValid() == true ||
                        karmaPlus?.isValid() == true ||
                        like?.isValid() == true ||
                        unlike?.isValid() == true ||
                        toggleLike?.isValid() == true ||
                        hide?.isValid() == true ||
                        reputationPlus?.isValid() == true ||
                        reputationMinus?.isValid() == true ||
                        report?.isValid() == true
    }

    class Karma {

        var status: Int = 0
        var count: Int = 0
        private val unknown1: Int = 0
        private val unknown2: Int = 0

        fun copy(): Karma =
                Karma().also {
                    it.status = status
                    it.count = count
                }

        companion object {
            const val NOT_LIKED = 0
            const val LIKED = 1
            const val DISLIKED = -1
            const val FORBIDDEN = 2
        }
    }
}
