package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Decides when the inline topic hat (шапка темы) block should start expanded on page 1.
 *
 * [AppPreferences.Main.TopicOpenTarget.LAST_UNREAD] opens deep into a topic; when the user later
 * jumps to page 1 via pagination the inline hat stays collapsed unless they toggle it manually.
 * [AppPreferences.Main.TopicHeaderInitialState.EXPANDED] applies only to a natural page-1 open
 * (fresh list open with [AppPreferences.Main.TopicOpenTarget.FIRST_PAGE], or a read topic that
 * lands on page 1 under LAST_UNREAD).
 */
internal object TopicInlineHatOpenPolicy {

    fun shouldOpenForLoad(
            url: String,
            requestedTopicId: Int?,
            currentPage: ThemePage?,
            topicHeaderInitialState: AppPreferences.Main.TopicHeaderInitialState,
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget,
            sourceScreen: String?,
            preserveInSessionInlineHatState: Boolean,
    ): Boolean {
        if (preserveInSessionInlineHatState) {
            currentPage?.let { page ->
                if (requestedTopicId != null && requestedTopicId > 0 && page.id == requestedTopicId) {
                    return page.isInlineHatOpen
                }
            }
        }
        if (requestedTopicId == null || requestedTopicId <= 0) return false
        if (!isFirstPageTopicUrl(url)) return false
        if (shouldForceCollapsedForLoad(
                        url = url,
                        requestedTopicId = requestedTopicId,
                        topicOpenTarget = topicOpenTarget,
                        sourceScreen = sourceScreen,
                        currentPage = currentPage,
                )
        ) {
            return false
        }
        return topicHeaderInitialState == AppPreferences.Main.TopicHeaderInitialState.EXPANDED
    }

    fun isFirstPageTopicUrl(url: String): Boolean =
            TopicOpenTargetResolver.isOrdinaryInitialTopicUrl(url)

    fun isExplicitInSessionPageNavigation(
            sourceScreen: String?,
            requestedTopicId: Int?,
            currentPage: ThemePage?,
    ): Boolean {
        if (sourceScreen?.lowercase() != "pagination") return false
        if (requestedTopicId == null || requestedTopicId <= 0) return false
        return currentPage?.id == requestedTopicId
    }

    /**
     * LAST_UNREAD pagination within the same topic is in-session navigation: reuse the current
     * inline-hat flag instead of re-applying the global EXPANDED setting.
     */
    fun shouldTreatAsInSessionForInlineHat(
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget,
            sourceScreen: String?,
            requestedTopicId: Int?,
            currentPage: ThemePage?,
    ): Boolean =
            topicOpenTarget == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD &&
                    isExplicitInSessionPageNavigation(sourceScreen, requestedTopicId, currentPage)

    fun shouldForceCollapsedForLoad(
            url: String,
            requestedTopicId: Int?,
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget,
            sourceScreen: String?,
            currentPage: ThemePage?,
    ): Boolean =
            topicOpenTarget == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD &&
                    isFirstPageTopicUrl(url) &&
                    isExplicitInSessionPageNavigation(sourceScreen, requestedTopicId, currentPage)

    fun shouldSuppressStoredHatPreferenceForLoad(
            url: String,
            requestedTopicId: Int?,
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget,
            sourceScreen: String?,
            currentPage: ThemePage?,
    ): Boolean = shouldForceCollapsedForLoad(
            url = url,
            requestedTopicId = requestedTopicId,
            topicOpenTarget = topicOpenTarget,
            sourceScreen = sourceScreen,
            currentPage = currentPage,
    )

    /** Inline «шапка темы» block is only valid on real topic page 1 — never on deep pages or hybrid fragments. */
    fun shouldRenderInlineBlock(page: ThemePage): Boolean {
        if (page.id <= 0) return false
        if (page.pagination.current != 1) return false
        if (!isFirstPageTopicUrl(page.url.orEmpty())) return false
        if (page.topicHatPost?.id?.let { it > 0 } != true) return false
        if (page.isHatOpen) return false
        return true
    }

    /**
     * Per-topic inline hat preference wins over the global «шапка при открытии» setting once the user
     * has toggled the inline block; otherwise the setting applies on first page-1 load.
     */
    fun resolveOpenStateForLoad(
            storedOpen: Boolean,
            hasStoredPreference: Boolean,
            initialFromSetting: Boolean,
    ): Boolean = if (hasStoredPreference) storedOpen else initialFromSetting
}
