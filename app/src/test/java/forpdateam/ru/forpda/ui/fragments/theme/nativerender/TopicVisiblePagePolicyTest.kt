package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicVisiblePagePolicyTest {

    @Test
    fun scrollingDown_selectsNextPageAsSoonAsItIsVisible() {
        assertEquals(
                105,
                TopicVisiblePagePolicy.resolve(
                        visiblePages = listOf(104, 105),
                        currentPage = 104,
                        scrollDelta = 1,
                ),
        )
    }

    @Test
    fun scrollingUp_selectsPreviousPageAsSoonAsItIsVisible() {
        assertEquals(
                104,
                TopicVisiblePagePolicy.resolve(
                        visiblePages = listOf(104, 105),
                        currentPage = 105,
                        scrollDelta = -1,
                ),
        )
    }

    @Test
    fun layoutOnlyUpdate_keepsCurrentPageWhileItRemainsVisible() {
        assertEquals(
                105,
                TopicVisiblePagePolicy.resolve(
                        visiblePages = listOf(104, 105),
                        currentPage = 105,
                        scrollDelta = 0,
                ),
        )
    }

    @Test
    fun noVisibleTopicPosts_returnsNoPage() {
        assertNull(
                TopicVisiblePagePolicy.resolve(
                        visiblePages = emptyList(),
                        currentPage = 104,
                        scrollDelta = 1,
                ),
        )
    }
}
