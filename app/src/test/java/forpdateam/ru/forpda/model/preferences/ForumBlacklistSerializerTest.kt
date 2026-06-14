package forpdateam.ru.forpda.model.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumBlacklistSerializerTest {

    @Test
    fun `add user serializes without duplicates`() {
        val first = ForumBlacklistSerializer.add(null, ForumBlacklistedUser(42, "Tester"))
        val second = ForumBlacklistSerializer.add(first, ForumBlacklistedUser(42, "Tester Renamed"))

        val users = ForumBlacklistSerializer.deserialize(second)

        assertEquals(1, users.size)
        assertEquals(42, users.first().userId)
        assertEquals("Tester", users.first().nick)
    }

    @Test
    fun `remove user deletes by stable id`() {
        val raw = ForumBlacklistSerializer.serialize(
                listOf(
                        ForumBlacklistedUser(42, "Tester"),
                        ForumBlacklistedUser(7, "Other")
                )
        )

        val users = ForumBlacklistSerializer.deserialize(
                ForumBlacklistSerializer.remove(raw, ForumBlacklistedUser(42, "Tester Renamed"))
        )

        assertEquals(listOf(ForumBlacklistedUser(7, "Other")), users)
    }

    @Test
    fun `matches by user id first and nick fallback when id is missing`() {
        val byId = ForumBlacklistedUser(42, "Tester")
        val byNick = ForumBlacklistedUser(0, "Blocked Nick")

        assertTrue(byId.matches(42, "Another nick"))
        assertFalse(byId.matches(7, "Tester"))
        assertTrue(byNick.matches(0, " blocked nick "))
    }
}
