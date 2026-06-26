package forpdateam.ru.forpda.model.repository.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarNotFoundExceptionTest {

    @Test
    fun `message contains id when only id is provided`() {
        val ex = AvatarNotFoundException(avatarId = 42)

        assertEquals(42, ex.avatarId)
        assertNull(ex.nick)
        assertTrue(
            "message must include id, got: ${ex.message}",
            ex.message?.contains("id=42") == true
        )
    }

    @Test
    fun `message contains nick when only nick is provided`() {
        val ex = AvatarNotFoundException(nick = "alice")

        assertNull(ex.avatarId)
        assertEquals("alice", ex.nick)
        assertTrue(
            "message must include nick, got: ${ex.message}",
            ex.message?.contains("nick=alice") == true
        )
    }

    @Test
    fun `message contains both id and nick when both provided`() {
        val ex = AvatarNotFoundException(avatarId = 7, nick = "bob")

        assertEquals(7, ex.avatarId)
        assertEquals("bob", ex.nick)
        assertTrue(ex.message?.contains("id=7") == true)
        assertTrue(ex.message?.contains("nick=bob") == true)
    }

    @Test
    fun `exception is a RuntimeException so it survives plain rethrows`() {
        val ex = AvatarNotFoundException(avatarId = 1)

        assertTrue("must be a RuntimeException", ex is RuntimeException)
    }
}
