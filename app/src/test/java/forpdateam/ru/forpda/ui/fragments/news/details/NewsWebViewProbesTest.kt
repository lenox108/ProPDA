package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewsWebViewProbesTest {

    @Test
    fun parsePoll_readsAllFields() {
        val probe = NewsWebViewProbes.parsePoll(
                """{"pollRootFound":true,"pollId":"42","optionsCount":3,"canVote":true,"hasToken":true,"renderedPollBlock":true,"readOnlyResults":false,"boundSubmit":true}"""
        )

        assertTrue(probe.pollRootFound)
        assertEquals("42", probe.pollId)
        assertEquals(3, probe.optionsCount)
        assertTrue(probe.canVote)
        assertTrue(probe.hasToken)
        assertTrue(probe.boundSubmit)
        assertNull(probe.error)
    }

    @Test
    fun parsePoll_unwrapsQuotedAndEscapedPayload() {
        // evaluateJavascript часто отдаёт результат как JSON-строку в кавычках с экранированием.
        val probe = NewsWebViewProbes.parsePoll("\"{\\\"pollRootFound\\\":true,\\\"pollId\\\":\\\"7\\\"}\"")

        assertTrue(probe.pollRootFound)
        assertEquals("7", probe.pollId)
    }

    @Test
    fun parsePoll_toleratesNullBlankAndGarbage() {
        assertFalse(NewsWebViewProbes.parsePoll(null).pollRootFound)
        assertFalse(NewsWebViewProbes.parsePoll("").pollRootFound)
        assertFalse(NewsWebViewProbes.parsePoll("null").pollRootFound)
        // Не бросает на мусоре — возвращает дефолт.
        assertFalse(NewsWebViewProbes.parsePoll("not json").pollRootFound)
    }

    @Test
    fun parseCommentsBind_readsFields_andMapsDelegation() {
        val probe = NewsWebViewProbes.parseCommentsBind(
                """{"hasRoot":true,"hasToggle":true,"sectionCount":2,"delegation":true,"commentsJsReady":true}"""
        )

        assertTrue(probe.hasRoot)
        assertTrue(probe.hasToggle)
        assertEquals(2, probe.sectionCount)
        assertTrue(probe.delegationInstalled)
        assertTrue(probe.commentsJsReady)
    }

    @Test
    fun parseCommentsBind_toleratesBlankAndGarbage() {
        val blank = NewsWebViewProbes.parseCommentsBind("")
        assertFalse(blank.hasRoot)
        assertEquals(0, blank.sectionCount)

        val garbage = NewsWebViewProbes.parseCommentsBind("{oops")
        assertFalse(garbage.commentsJsReady)
    }
}
