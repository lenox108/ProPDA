package forpdateam.ru.forpda.presentation.theme

class ThemeScrollAnchorController {

    fun shouldOpenTopicWithUnreadFirst(rawUrl: String?): Boolean {
        return TopicOpenTargetResolver.resolveInitialUrl(
                rawUrl,
                forpdateam.ru.forpda.common.Preferences.Main.TopicOpenTarget.LAST_UNREAD
        ) != rawUrl?.trim().orEmpty()
    }

    fun chooseInitialAnchor(
            topicId: Int,
            page: Int,
            directPostId: Int?,
            hasUnreadTarget: Boolean
    ): ScrollAnchor? {
        if (topicId <= 0 || page <= 0) return null
        return when {
            directPostId != null && directPostId > 0 -> ScrollAnchor(
                    topicId = topicId,
                    page = page,
                    postId = directPostId,
                    yOffset = null,
                    source = ScrollAnchor.Source.FindPost
            )
            hasUnreadTarget -> ScrollAnchor(
                    topicId = topicId,
                    page = page,
                    postId = null,
                    yOffset = null,
                    source = ScrollAnchor.Source.Unread
            )
            else -> ScrollAnchor(
                    topicId = topicId,
                    page = page,
                    postId = null,
                    yOffset = null,
                    source = ScrollAnchor.Source.InitialOpen
            )
        }
    }

    fun chooseRestoreAnchor(
            topicId: Int,
            page: Int,
            visiblePostId: Int?,
            yOffset: Int?,
            source: ScrollAnchor.Source
    ): ScrollAnchor? {
        if (topicId <= 0 || page <= 0) return null
        if (source != ScrollAnchor.Source.ManualReload && source != ScrollAnchor.Source.RotationRestore) {
            return null
        }
        val safePostId = visiblePostId?.takeIf { it > 0 }
        val safeOffset = yOffset?.takeIf { it >= 0 }
        if (safePostId == null && safeOffset == null) return null
        return ScrollAnchor(
                topicId = topicId,
                page = page,
                postId = safePostId,
                yOffset = safeOffset,
                source = source
        )
    }
}
