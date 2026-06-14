package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMissedPageLifecyclePolicyTest {

    @Test
    fun `probes when dom claimed but page lifecycle missing`() {
        assertTrue(
                ThemeMissedPageLifecyclePolicy.shouldProbeMissedPageLifecycle(
                        renderGeneration = 3,
                        domLifecycleGeneration = 3,
                        pageLifecycleGeneration = 0,
                )
        )
    }

    @Test
    fun `skips probe before dom lifecycle`() {
        assertFalse(
                ThemeMissedPageLifecyclePolicy.shouldProbeMissedPageLifecycle(
                        renderGeneration = 3,
                        domLifecycleGeneration = 0,
                        pageLifecycleGeneration = 0,
                )
        )
    }

    @Test
    fun `skips probe after page lifecycle claimed`() {
        assertFalse(
                ThemeMissedPageLifecyclePolicy.shouldProbeMissedPageLifecycle(
                        renderGeneration = 3,
                        domLifecycleGeneration = 3,
                        pageLifecycleGeneration = 3,
                )
        )
    }

    @Test
    fun `skips probe when render generation not started`() {
        assertFalse(
                ThemeMissedPageLifecyclePolicy.shouldProbeMissedPageLifecycle(
                        renderGeneration = 0,
                        domLifecycleGeneration = 0,
                        pageLifecycleGeneration = 0,
                )
        )
    }
}
