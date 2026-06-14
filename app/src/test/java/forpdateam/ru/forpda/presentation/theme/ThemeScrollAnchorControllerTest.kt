package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeScrollAnchorControllerTest {
    private val controller = ThemeScrollAnchorController()

    @Test
    fun shouldOpenTopicWithUnreadFirst_forPlainTopicOnly() {
        assertTrue(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123"))

        assertFalse(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=456"))
        assertFalse(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?act=findpost&pid=456"))
        assertTrue(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&p=456"))
        assertTrue(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&pid=456"))
        assertTrue(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&st=0"))
        assertTrue(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&st=20"))
        assertFalse(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&view=getnewpost"))
        assertTrue(controller.shouldOpenTopicWithUnreadFirst("https://4pda.to/forum/index.php?showtopic=123&view=getlastpost"))
        assertFalse(controller.shouldOpenTopicWithUnreadFirst(null))
    }

    @Test
    fun chooseInitialAnchor_directPostWinsOverUnread() {
        val anchor = controller.chooseInitialAnchor(
                topicId = 123,
                page = 2,
                directPostId = 456,
                hasUnreadTarget = true
        )

        assertEquals(ScrollAnchor(123, 2, 456, null, ScrollAnchor.Source.FindPost), anchor)
    }

    @Test
    fun chooseInitialAnchor_unreadWinsOverDefault() {
        val anchor = controller.chooseInitialAnchor(
                topicId = 123,
                page = 1,
                directPostId = null,
                hasUnreadTarget = true
        )

        assertEquals(ScrollAnchor(123, 1, null, null, ScrollAnchor.Source.Unread), anchor)
    }

    @Test
    fun chooseInitialAnchor_usesDefaultForValidTopicWithoutTarget() {
        val anchor = controller.chooseInitialAnchor(
                topicId = 123,
                page = 1,
                directPostId = null,
                hasUnreadTarget = false
        )

        assertEquals(ScrollAnchor(123, 1, null, null, ScrollAnchor.Source.InitialOpen), anchor)
    }

    @Test
    fun chooseRestoreAnchor_manualReloadPreservesVisiblePosition() {
        val anchor = controller.chooseRestoreAnchor(
                topicId = 123,
                page = 3,
                visiblePostId = 456,
                yOffset = 78,
                source = ScrollAnchor.Source.ManualReload
        )

        assertEquals(ScrollAnchor(123, 3, 456, 78, ScrollAnchor.Source.ManualReload), anchor)
    }

    @Test
    fun chooseRestoreAnchor_rotationPreservesYOffsetWithoutPost() {
        val anchor = controller.chooseRestoreAnchor(
                topicId = 123,
                page = 3,
                visiblePostId = null,
                yOffset = 78,
                source = ScrollAnchor.Source.RotationRestore
        )

        assertEquals(ScrollAnchor(123, 3, null, 78, ScrollAnchor.Source.RotationRestore), anchor)
    }

    @Test
    fun chooseRestoreAnchor_rejectsInvalidDataSafely() {
        assertNull(controller.chooseInitialAnchor(topicId = 0, page = 1, directPostId = 456, hasUnreadTarget = true))
        assertNull(controller.chooseInitialAnchor(topicId = 123, page = 0, directPostId = 456, hasUnreadTarget = true))
        assertNull(controller.chooseRestoreAnchor(0, 1, 456, 78, ScrollAnchor.Source.ManualReload))
        assertNull(controller.chooseRestoreAnchor(123, 1, -1, -1, ScrollAnchor.Source.ManualReload))
        assertNull(controller.chooseRestoreAnchor(123, 1, 456, 78, ScrollAnchor.Source.FindPost))
    }
}
