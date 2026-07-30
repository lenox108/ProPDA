package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicTitleTapPolicyTest {

    @Test
    fun `tap opens the hat when the topic has one`() {
        assertEquals(
                TopicTitleTapAction.OPEN_HAT,
                TopicTitleTapPolicy.resolve(hatAvailable = true),
        )
    }

    @Test
    fun `tap falls back to the full title popup without a hat`() {
        assertEquals(
                TopicTitleTapAction.SHOW_FULL_TITLE,
                TopicTitleTapPolicy.resolve(hatAvailable = false),
        )
    }
}
