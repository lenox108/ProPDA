package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the 👍/👎 visibility to the WebView's ThemeTemplate.resolvePostRatingUi behaviour. */
class NativePostRatingActionsTest {

    private fun resolve(
            canQuote: Boolean = true,
            postRating: String? = null,
            parsedCanPlus: Boolean = false,
            parsedCanMinus: Boolean = false,
            postUserId: Int = 100,
            authorized: Boolean = true,
            memberId: Int = 50,
    ) = NativePostRatingActions.resolve(
            canQuote, postRating, parsedCanPlus, parsedCanMinus, postUserId, authorized, memberId,
    )

    @Test
    fun `quotable non-own post with no metadata still shows both controls (mobile fallback)`() {
        assertEquals(true to true, resolve())
    }

    @Test
    fun `own post never shows controls`() {
        assertEquals(false to false, resolve(postUserId = 50, memberId = 50))
    }

    @Test
    fun `unauthorized never shows controls`() {
        assertEquals(false to false, resolve(authorized = false))
    }

    @Test
    fun `non-quotable post with no rating and no parsed actions shows nothing`() {
        assertEquals(false to false, resolve(canQuote = false))
    }

    @Test
    fun `parsed plus-only is respected without fabricating minus`() {
        assertEquals(true to false, resolve(canQuote = false, parsedCanPlus = true))
    }

    @Test
    fun `server rating enables both controls even when not quotable`() {
        assertEquals(true to true, resolve(canQuote = false, postRating = "5"))
    }
}
