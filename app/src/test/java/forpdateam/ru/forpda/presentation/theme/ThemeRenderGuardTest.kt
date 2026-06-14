package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRenderGuardTest {

    @Test
    fun newToken_generatesNonBlankCurrentToken() {
        val guard = ThemeRenderGuard()

        val token = guard.newToken()

        assertTrue(token.isNotBlank())
        assertEquals(token, guard.currentToken())
        assertTrue(guard.isValid(token))
    }

    @Test
    fun newToken_replacesPreviousToken() {
        val guard = ThemeRenderGuard()

        val first = guard.newToken()
        val second = guard.newToken()

        assertNotEquals(first, second)
        assertFalse(guard.isValid(first))
        assertTrue(guard.isValid(second))
    }

    @Test
    fun invalidate_rejectsCurrentToken() {
        val guard = ThemeRenderGuard()
        val token = guard.newToken()

        guard.invalidate()

        assertNotNull(token)
        assertEquals(null, guard.currentToken())
        assertFalse(guard.isValid(token))
    }

    @Test
    fun isValid_rejectsNullBlankAndStaleTokens() {
        val guard = ThemeRenderGuard()
        val stale = guard.newToken()
        guard.newToken()

        assertFalse(guard.isValid(null))
        assertFalse(guard.isValid(""))
        assertFalse(guard.isValid("   "))
        assertFalse(guard.isValid(stale))
        assertFalse(guard.isValid("invalid-token"))
    }
}
