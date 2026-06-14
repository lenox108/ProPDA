package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicHatAvailabilityTest {

    @Test
    fun `hasTopicHat accepts bound hat post`() {
        val page = ThemePage().apply {
            id = 824716
            topicHatPost = ThemePost().apply { id = 61638975 }
        }
        assertTrue(
                TopicHatAvailability.hasTopicHat(
                        page = page,
                        cachedHat = null,
                        cachedHatTopicId = null,
                        firstPageHatPostId = null,
                )
        )
    }

    @Test
    fun `hasTopicHat accepts cached hat on deep page`() {
        val page = ThemePage().apply {
            id = 824716
            pagination.current = 168
        }
        val cachedHat = ThemePost().apply { id = 61638975 }
        assertTrue(
                TopicHatAvailability.hasTopicHat(
                        page = page,
                        cachedHat = cachedHat,
                        cachedHatTopicId = 824716,
                        firstPageHatPostId = 61638975,
                )
        )
    }

    @Test
    fun `hasTopicHat accepts first page hat id when topic cache matches`() {
        val page = ThemePage().apply {
            id = 824716
            pagination.current = 42
        }
        assertTrue(
                TopicHatAvailability.hasTopicHat(
                        page = page,
                        cachedHat = null,
                        cachedHatTopicId = 824716,
                        firstPageHatPostId = 61638975,
                )
        )
    }

    @Test
    fun `hasTopicHat rejects unrelated cached hat`() {
        val page = ThemePage().apply { id = 824716 }
        val cachedHat = ThemePost().apply { id = 1 }
        assertFalse(
                TopicHatAvailability.hasTopicHat(
                        page = page,
                        cachedHat = cachedHat,
                        cachedHatTopicId = 928862,
                        firstPageHatPostId = 1,
                )
        )
    }
}
