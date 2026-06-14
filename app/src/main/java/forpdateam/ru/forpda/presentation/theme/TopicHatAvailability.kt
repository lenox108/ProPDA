package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost

/**
 * Topic hat can exist at topic level while [ThemePage.topicHatPost] is still null on deep pages
 * until deferred page-1 metadata arrives.
 */
internal object TopicHatAvailability {

    fun hasTopicHat(
            page: ThemePage?,
            cachedHat: ThemePost?,
            cachedHatTopicId: Int?,
            firstPageHatPostId: Int?,
    ): Boolean {
        if (page == null || page.id <= 0) return false
        if (page.topicHatPost?.id?.let { it > 0 } == true) return true
        if (cachedHatTopicId != page.id) return false
        if (cachedHat?.id?.let { it > 0 } == true) return true
        return firstPageHatPostId?.let { it > 0 } == true
    }
}
