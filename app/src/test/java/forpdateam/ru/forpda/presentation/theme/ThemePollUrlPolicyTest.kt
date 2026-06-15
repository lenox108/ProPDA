package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Golden tests for [ThemePollUrlPolicy] — pure URL helpers for the
 * topic poll UX. Extracted from `ThemeViewModel` (god-class §1.1).
 *
 * `rewriteAddPoll` uses [android.net.Uri] which needs Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemePollUrlPolicyTest {

    @Test
    fun `appendPollOpen adds param when none present`() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&poll_open=true",
                ThemePollUrlPolicy.appendPollOpen("https://4pda.to/forum/index.php?showtopic=1")
        )
    }

    @Test
    fun `appendPollOpen does not duplicate param`() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&poll_open=true",
                ThemePollUrlPolicy.appendPollOpen("https://4pda.to/forum/index.php?showtopic=1&poll_open=true")
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&poll_open=true",
                ThemePollUrlPolicy.appendPollOpen("https://4pda.to/forum/index.php?poll_open=true&showtopic=1")
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&poll_open=true",
                ThemePollUrlPolicy.appendPollOpen("https://4pda.to/forum/index.php?showtopic=1?poll_open=true")
        )
    }

    @Test
    fun `buildPollOpenUrl strips mode show and adds flag`() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&poll_open=true",
                ThemePollUrlPolicy.buildPollOpenUrl("https://4pda.to/forum/index.php?showtopic=1&mode=show")
        )
    }

    @Test
    fun `buildPollOpenUrl drops fragment and replaces flag`() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&poll_open=true",
                ThemePollUrlPolicy.buildPollOpenUrl(
                        "https://4pda.to/forum/index.php?showtopic=1#entry42&mode=show&poll_open=true"
                )
        )
    }

    @Test
    fun `rewriteAddPoll rewrites 4pda addpoll link with topic and st`() {
        val result = ThemePollUrlPolicy.rewriteAddPoll(
                url = "https://4pda.to/forum/index.php?act=addpoll=1",
                topicId = 42,
                stOffset = 60
        )
        // Uri builder may re-order the query parameters; we only check the
        // critical additions (showtopic + st) are present.
        assert(result != null) { "expected non-null rewrite" }
        assert(result!!.contains("showtopic=42")) { "missing showtopic: $result" }
        assert(result.contains("st=60")) { "missing st: $result" }
    }

    @Test
    fun `rewriteAddPoll returns null for non-poll urls`() {
        assertNull(
                ThemePollUrlPolicy.rewriteAddPoll(
                        url = "https://4pda.to/forum/index.php?showtopic=1",
                        topicId = 1,
                        stOffset = 0
                )
        )
    }
}
