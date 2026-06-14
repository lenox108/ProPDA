package forpdateam.ru.forpda.ui.fragments.theme.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: duplicate DOM/page lifecycle callbacks must not arm scroll twice per render.
 */
class ThemeWebControllerLifecycleTest {

    @Test
    fun domLifecycle_claimedOnlyOncePerRenderGeneration() {
        val generation = object {
            var renderGeneration = 0
            var domLifecycleGeneration = 0

            fun tryClaimDomLifecycle(): Boolean {
                if (renderGeneration <= 0) return false
                if (domLifecycleGeneration == renderGeneration) return false
                if (domLifecycleGeneration != 0) return false
                domLifecycleGeneration = renderGeneration
                return true
            }
        }
        generation.renderGeneration = 1
        assertTrue(generation.tryClaimDomLifecycle())
        assertFalse(generation.tryClaimDomLifecycle())
    }

    @Test
    fun pageLifecycle_requiresDomClaimFirst() {
        val generation = object {
            var renderGeneration = 0
            var domLifecycleGeneration = 0
            var pageLifecycleGeneration = 0

            fun tryClaimDomLifecycle(): Boolean {
                if (renderGeneration <= 0) return false
                if (domLifecycleGeneration == renderGeneration) return false
                if (domLifecycleGeneration != 0) return false
                domLifecycleGeneration = renderGeneration
                return true
            }

            fun tryClaimPageLifecycle(): Boolean {
                if (renderGeneration <= 0) return false
                if (domLifecycleGeneration != renderGeneration) return false
                if (pageLifecycleGeneration == renderGeneration) return false
                if (pageLifecycleGeneration != 0) return false
                pageLifecycleGeneration = renderGeneration
                return true
            }
        }
        generation.renderGeneration = 2
        assertFalse(generation.tryClaimPageLifecycle())
        assertTrue(generation.tryClaimDomLifecycle())
        assertTrue(generation.tryClaimPageLifecycle())
        assertFalse(generation.tryClaimPageLifecycle())
    }
}
