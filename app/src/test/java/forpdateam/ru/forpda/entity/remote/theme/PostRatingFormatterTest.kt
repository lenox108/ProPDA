package forpdateam.ru.forpda.entity.remote.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostRatingFormatterTest {

    @Test
    fun `positive rating always has plus`() {
        assertEquals("+3", PostRatingFormatter.normalize("3"))
        assertEquals("+3", PostRatingFormatter.normalize("+3"))
        assertEquals("+3", PostRatingFormatter.format(3))
    }

    @Test
    fun `negative rating always has minus`() {
        assertEquals("-3", PostRatingFormatter.normalize("-3"))
        assertEquals("-3", PostRatingFormatter.normalize("− 3"))
        assertEquals("-3", PostRatingFormatter.format(-3))
    }

    @Test
    fun `zero remains unsigned`() {
        assertEquals("0", PostRatingFormatter.normalize("+0"))
        assertEquals("0", PostRatingFormatter.format(0))
    }

    @Test
    fun `missing rating remains missing`() {
        assertNull(PostRatingFormatter.normalize(null))
        assertNull(PostRatingFormatter.normalize("  "))
    }
}
