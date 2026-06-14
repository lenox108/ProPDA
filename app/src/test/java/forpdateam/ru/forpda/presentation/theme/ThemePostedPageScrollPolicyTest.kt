package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePostedPageScrollPolicyTest {

    @Test
    fun shouldApplyPostedScroll_falseWhenPendingMissing() {
        assertFalse(ThemePostedPageScrollPolicy.shouldApplyPostedScroll(null))
        assertFalse(ThemePostedPageScrollPolicy.shouldApplyPostedScroll(""))
    }

    @Test
    fun shouldApplyPostedScroll_trueWhenPendingPresent() {
        assertTrue(ThemePostedPageScrollPolicy.shouldApplyPostedScroll("143769603"))
    }

    @Test
    fun resolveDomScrollAnchor_prefersHighestParsedPostOnLastPage() {
        val page = ThemePage().apply {
            addAnchor("entry143765539")
            posts.add(ThemePost().apply { id = 143765539 })
            posts.add(ThemePost().apply { id = 143769603 })
        }
        assertEquals(
                "143769603",
                ThemePostedPageScrollPolicy.resolveDomScrollAnchor("143765539", page)
        )
    }

    @Test
    fun resolveDomScrollAnchor_fallsBackToPendingWithoutPage() {
        assertEquals(
                "143769603",
                ThemePostedPageScrollPolicy.resolveDomScrollAnchor("143769603", null)
        )
    }

    @Test
    fun resolveDomScrollAnchor_nullWhenPendingMissing() {
        assertNull(ThemePostedPageScrollPolicy.resolveDomScrollAnchor(null, ThemePage()))
    }

    @Test
    fun resolveDomScrollAnchor_exactAnchor_keepsEditedPostOnMiddleOfPage() {
        val page = ThemePage().apply {
            posts.add(ThemePost().apply { id = 143765539 })
            posts.add(ThemePost().apply { id = 143769603 })
            posts.add(ThemePost().apply { id = 143770000 })
        }
        assertEquals(
                "143769603",
                ThemePostedPageScrollPolicy.resolveDomScrollAnchor("143769603", page, exactAnchor = true)
        )
    }
}
