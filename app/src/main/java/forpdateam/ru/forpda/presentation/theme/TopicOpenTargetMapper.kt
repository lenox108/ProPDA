package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi

/**
 * Maps resolver output + load intent into a [TopicOpenTarget] carried through ViewModel/render.
 */
object TopicOpenTargetMapper {

    fun from(
            resolution: TopicOpenResolution,
            loadAction: ThemeLoadAction,
            openIntentRaw: String?,
            backSnapshot: TopicBackSnapshot? = null,
            refreshRestoreId: String? = null,
            refreshRestoreMode: String? = null,
            refreshRestoreSource: String? = null
    ): TopicOpenTarget {
        val url = resolution.url
        val topicId = ThemeApi.extractTopicIdFromUrl(url)
        val pageSt = resolution.resolvedPageSt ?: ThemeApi.extractStFromUrl(url)

        if (loadAction == ThemeLoadAction.End) {
            return TopicOpenTarget.End(fetchUrl = url, topicId = topicId, pageSt = pageSt)
        }
        if (loadAction == ThemeLoadAction.Back) {
            val snapshot = backSnapshot
                    ?: topicId?.let { id ->
                        TopicBackSnapshot.fromPage(
                                topicId = id,
                                pageSt = pageSt ?: 0,
                                visiblePostId = resolution.resolvedPostId?.toString(),
                                scrollOffset = 0,
                                scrollRatio = null,
                                wasNearBottom = false,
                                status = TopicBackSnapshotStatus.PENDING
                        )
                    }
            return TopicOpenTarget.BackRestore(
                    fetchUrl = url,
                    topicId = topicId,
                    pageSt = pageSt,
                    snapshot = snapshot ?: TopicBackSnapshot(
                            topicId = topicId ?: 0,
                            pageSt = pageSt ?: 0,
                            visiblePostId = null,
                            scrollOffset = 0,
                            scrollRatio = null,
                            status = TopicBackSnapshotStatus.PENDING
                    )
            )
        }
        if (loadAction == ThemeLoadAction.Refresh && !refreshRestoreId.isNullOrBlank()) {
            return TopicOpenTarget.RefreshRestore(
                    fetchUrl = url,
                    topicId = topicId,
                    pageSt = pageSt,
                    restoreId = refreshRestoreId,
                    mode = refreshRestoreMode.orEmpty(),
                    source = refreshRestoreSource
            )
        }
        if (TopicOpenIntentClassifier.isRestoreIntent(openIntentRaw) && backSnapshot?.isUsable() == true) {
            return TopicOpenTarget.BackRestore(
                    fetchUrl = url,
                    topicId = topicId,
                    pageSt = pageSt,
                    snapshot = backSnapshot
            )
        }

        return when (resolution.targetType) {
            TopicOpenTargetType.EXPLICIT_POST -> TopicOpenTarget.ExplicitPost(
                    fetchUrl = url,
                    topicId = topicId,
                    postId = resolution.resolvedPostId ?: 0,
                    pageSt = pageSt
            )
            TopicOpenTargetType.EXPLICIT_PAGE,
            TopicOpenTargetType.SETTING_FIRST_PAGE -> TopicOpenTarget.ExplicitPage(
                    fetchUrl = url,
                    topicId = topicId,
                    pageSt = pageSt ?: 0
            )
            TopicOpenTargetType.SETTING_LAST_UNREAD,
            TopicOpenTargetType.READ_RESUME,
            TopicOpenTargetType.SERVER_UNREAD_FALLBACK -> TopicOpenTarget.Unread(
                    fetchUrl = url,
                    topicId = topicId,
                    reason = resolution.reason
            )
            TopicOpenTargetType.USER_ACTION -> mapUserAction(url, topicId, pageSt, resolution)
            TopicOpenTargetType.SAFE_FALLBACK -> TopicOpenTarget.Default(
                    fetchUrl = url,
                    topicId = topicId,
                    pageSt = pageSt,
                    postId = resolution.resolvedPostId,
                    allowSavedScrollRestore = !resolution.suppressScrollRestore,
                    reason = resolution.reason
            )
        }
    }

    private fun mapUserAction(
            url: String,
            topicId: Int?,
            pageSt: Int?,
            resolution: TopicOpenResolution
    ): TopicOpenTarget = when (resolution.reason) {
        "user_action_unread" -> TopicOpenTarget.Unread(fetchUrl = url, topicId = topicId, reason = resolution.reason)
        "user_action_first_page" -> TopicOpenTarget.ExplicitPage(fetchUrl = url, topicId = topicId, pageSt = 0)
        "user_action_last_post" -> TopicOpenTarget.End(fetchUrl = url, topicId = topicId, pageSt = pageSt)
        else -> TopicOpenTarget.Default(
                fetchUrl = url,
                topicId = topicId,
                pageSt = pageSt,
                postId = resolution.resolvedPostId,
                allowSavedScrollRestore = !resolution.suppressScrollRestore,
                reason = resolution.reason
        )
    }
}
