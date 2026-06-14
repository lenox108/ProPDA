package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertEquals
import org.junit.Test

class BlankRenderRetryPolicyTest {

    private val maxRetries = 2

    @Test
    fun `first blank re-renders cached html`() {
        assertEquals(
                BlankRenderRetryPolicy.Decision.RERENDER_CACHED,
                BlankRenderRetryPolicy.decide(retryCount = 1, maxRetries = maxRetries)
        )
    }

    @Test
    fun `second blank forces a network refetch`() {
        assertEquals(
                BlankRenderRetryPolicy.Decision.REFETCH,
                BlankRenderRetryPolicy.decide(retryCount = 2, maxRetries = maxRetries)
        )
    }

    @Test
    fun `blank beyond the budget gives up with an error placeholder`() {
        assertEquals(
                BlankRenderRetryPolicy.Decision.GIVE_UP,
                BlankRenderRetryPolicy.decide(retryCount = 3, maxRetries = maxRetries)
        )
        assertEquals(
                BlankRenderRetryPolicy.Decision.GIVE_UP,
                BlankRenderRetryPolicy.decide(retryCount = 10, maxRetries = maxRetries)
        )
    }

    @Test
    fun `escalation never repeats the same step so a blank screen cannot persist`() {
        // Walking the counter up must move RERENDER_CACHED -> REFETCH -> GIVE_UP and terminate.
        val sequence = (1..4).map { BlankRenderRetryPolicy.decide(it, maxRetries) }
        assertEquals(
                listOf(
                        BlankRenderRetryPolicy.Decision.RERENDER_CACHED,
                        BlankRenderRetryPolicy.Decision.REFETCH,
                        BlankRenderRetryPolicy.Decision.GIVE_UP,
                        BlankRenderRetryPolicy.Decision.GIVE_UP
                ),
                sequence
        )
    }
}
