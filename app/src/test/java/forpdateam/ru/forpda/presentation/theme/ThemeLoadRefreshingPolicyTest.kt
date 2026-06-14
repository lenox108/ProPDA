package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeLoadRefreshingPolicyTest {

    @Test
    fun clearsRefreshingOnlyForOwningTrace() {
        assertTrue(ThemeLoadRefreshingPolicy.shouldClearRefreshingOnJobEnd("abc12345", "abc12345"))
        assertFalse(ThemeLoadRefreshingPolicy.shouldClearRefreshingOnJobEnd("abc12345", "def67890"))
        assertFalse(ThemeLoadRefreshingPolicy.shouldClearRefreshingOnJobEnd("", "abc12345"))
    }
}
