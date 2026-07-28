package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LowRatedPostPolicy] tests — чистый JVM, без Robolectric.
 *
 * Главное, что здесь закреплено: отсутствующий рейтинг НИКОГДА не приводит к сворачиванию. Карма
 * гейтится аккаунтом (см. `post-rating-ka-data-absent`), и на аккаунте без кармы `postRating` пуст у
 * всех постов — фича должна просто ничего не делать, а не спрятать всю тему.
 */
class LowRatedPostPolicyTest {

    private fun collapse(
            postRating: String?,
            enabled: Boolean = true,
            threshold: Int = -3,
            isOwnPost: Boolean = false,
            isHat: Boolean = false,
            manuallyExpanded: Boolean = false,
    ) = LowRatedPostPolicy.shouldCollapse(
            enabled = enabled,
            threshold = threshold,
            postRating = postRating,
            isOwnPost = isOwnPost,
            isHat = isHat,
            manuallyExpanded = manuallyExpanded,
    )

    @Test
    fun collapses_atAndBelowThreshold() {
        assertTrue(collapse("-3"))
        assertTrue(collapse("-4"))
        assertTrue(collapse("-25"))
    }

    @Test
    fun keeps_aboveThreshold() {
        assertFalse(collapse("-2"))
        assertFalse(collapse("-1"))
        assertFalse(collapse("0"))
        assertFalse(collapse("+7"))
    }

    @Test
    fun keeps_whenRatingIsAbsentOrUnparsable() {
        assertFalse(collapse(null))
        assertFalse(collapse(""))
        assertFalse(collapse("   "))
        assertFalse(collapse("нет данных"))
    }

    @Test
    fun understandsUnicodeMinusFromTheSite() {
        // 4pda отдаёт минус и как U+2212, и как en dash — PostRatingFormatter это нормализует.
        assertTrue(collapse("−4"))
        assertTrue(collapse("–4"))
    }

    @Test
    fun keeps_whenDisabled() {
        assertFalse(collapse("-9", enabled = false))
    }

    @Test
    fun keeps_ownPostHatAndManuallyExpanded() {
        assertFalse(collapse("-9", isOwnPost = true))
        assertFalse(collapse("-9", isHat = true))
        assertFalse(collapse("-9", manuallyExpanded = true))
    }

    @Test
    fun honoursThresholdBounds() {
        assertFalse(collapse("-5", threshold = -10))
        assertTrue(collapse("-10", threshold = -10))
        assertTrue(collapse("-1", threshold = -1))
    }

    @Test
    fun normalizeThreshold_forcesNegativeAndClamps() {
        assertEquals(-3, LowRatedPostPolicy.normalizeThreshold(3))
        assertEquals(-3, LowRatedPostPolicy.normalizeThreshold(-3))
        assertEquals(-10, LowRatedPostPolicy.normalizeThreshold(-42))
        assertEquals(-1, LowRatedPostPolicy.normalizeThreshold(-1))
        // 0 и мусор — не порог: откатываемся на значение по умолчанию.
        assertEquals(LowRatedPostPolicy.DEFAULT_THRESHOLD, LowRatedPostPolicy.normalizeThreshold(0))
        assertEquals(LowRatedPostPolicy.DEFAULT_THRESHOLD, LowRatedPostPolicy.normalizeThreshold(null))
        assertEquals(LowRatedPostPolicy.DEFAULT_THRESHOLD, LowRatedPostPolicy.normalizeThreshold("abc"))
        assertEquals(-4, LowRatedPostPolicy.normalizeThreshold("-4"))
        assertEquals(-4, LowRatedPostPolicy.normalizeThreshold(" -4 "))
    }

    @Test
    fun isOwnPost_requiresAuthAndRealIds() {
        assertTrue(LowRatedPostPolicy.isOwnPost(postUserId = 42, authorized = true, memberId = 42))
        assertFalse(LowRatedPostPolicy.isOwnPost(postUserId = 42, authorized = false, memberId = 42))
        assertFalse(LowRatedPostPolicy.isOwnPost(postUserId = 42, authorized = true, memberId = 7))
        // NO_ID с обеих сторон не должен совпадать сам с собой.
        assertFalse(LowRatedPostPolicy.isOwnPost(postUserId = 0, authorized = true, memberId = 0))
    }
}
