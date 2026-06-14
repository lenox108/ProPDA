package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NickEncoderTest {

    @Test
    fun encodeForSearch_cyrillic_returnsEncoded() {
        val encoded = NickEncoder.encodeForSearch("привет")
        assertFalse(encoded.contains("привет"))
        assertFalse(encoded.isEmpty())
    }

    @Test
    fun encodeForSearch_ascii_returnsSame() {
        assertEquals("hello", NickEncoder.encodeForSearch("hello"))
        assertEquals("nick123", NickEncoder.encodeForSearch("nick123"))
    }

    @Test
    fun encodeForSearch_empty_returnsEmpty() {
        assertEquals("", NickEncoder.encodeForSearch(""))
    }

    @Test
    fun encodeForSearch_roundTrip() {
        val original = "привет мир"
        val encoded = NickEncoder.encodeForSearch(original)
        val decoded = Cp1251Codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodeForSearch_withSpecialCharacters() {
        val original = "тест_ник"
        val encoded = NickEncoder.encodeForSearch(original)
        val decoded = Cp1251Codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodeForSearch_withEmoji_fallbackToUtf8() {
        val withEmoji = "ник😀"
        val encoded = NickEncoder.encodeForSearch(withEmoji)
        assertFalse(encoded.isEmpty())
        // Emoji не может быть закодирован в cp1251, поэтому fallback в UTF-8
    }

    @Test
    fun encodeForSearch_delegatesToCp1251Codec() {
        val original = "пользователь"
        val nickEncoderResult = NickEncoder.encodeForSearch(original)
        val cp1251Result = Cp1251Codec.encodeSmart(original)
        assertEquals(cp1251Result, nickEncoderResult)
    }
}
