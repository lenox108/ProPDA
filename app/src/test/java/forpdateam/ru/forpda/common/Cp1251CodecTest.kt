package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Cp1251CodecTest {

    @Test
    fun encode_emptyOrNull_returnsEmptyString() {
        assertEquals("", Cp1251Codec.encode(null))
        assertEquals("", Cp1251Codec.encode(""))
    }

    @Test
    fun encode_ascii_returnsSame() {
        assertEquals("hello", Cp1251Codec.encode("hello"))
        assertEquals("123", Cp1251Codec.encode("123"))
    }

    @Test
    fun encode_cyrillic_returnsEncoded() {
        val encoded = Cp1251Codec.encode("привет")
        assertFalse(encoded.contains("привет"))
        assertFalse(encoded.isEmpty())
    }

    @Test
    fun encode_cyrillic_roundTrip() {
        val original = "привет мир"
        val encoded = Cp1251Codec.encode(original)
        val decoded = Cp1251Codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun decode_emptyOrNull_returnsEmptyString() {
        assertEquals("", Cp1251Codec.decode(null))
        assertEquals("", Cp1251Codec.decode(""))
    }

    @Test
    fun decode_ascii_returnsSame() {
        assertEquals("hello", Cp1251Codec.decode("hello"))
    }

    @Test
    fun encodeSmart_cyrillic_usesCp1251() {
        val encoded = Cp1251Codec.encodeSmart("привет")
        assertFalse(encoded.contains("привет"))
        assertFalse(encoded.isEmpty())
    }

    @Test
    fun encodeSmart_emoji_usesUtf8() {
        val withEmoji = "привет😀"
        val encoded = Cp1251Codec.encodeSmart(withEmoji)
        assertFalse(encoded.isEmpty())
        // Emoji не может быть закодирован в cp1251, поэтому fallback в UTF-8
    }

    @Test
    fun encodeSmart_emojiPreservesSurrogatePairs() {
        val encoded = Cp1251Codec.encodeSmart("⚡ elektrik ⚡")
        assertEquals("%E2%9A%A1+elektrik+%E2%9A%A1", encoded)
        assertFalse(encoded.contains("%3F"))
    }

    @Test
    fun decodeSmart_utf8Emoji_roundTrip() {
        val original = "⚡ elektrik ⚡"
        val decoded = Cp1251Codec.decodeSmart(Cp1251Codec.encodeSmart(original))
        assertEquals(original, decoded)
    }

    @Test
    fun decodeSmart_cp1251Cyrillic_roundTrip() {
        val original = "пользователь"
        val decoded = Cp1251Codec.decodeSmart(Cp1251Codec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun encodeSmart_ascii_returnsEncoded() {
        val encoded = Cp1251Codec.encodeSmart("hello")
        assertEquals("hello", encoded)
    }

    @Test
    fun encodeSmart_roundTrip() {
        val original = "привет мир"
        val encoded = Cp1251Codec.encodeSmart(original)
        val decoded = Cp1251Codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodeSmart_withSpecialCharacters() {
        val original = "тест & проверка"
        val encoded = Cp1251Codec.encodeSmart(original)
        val decoded = Cp1251Codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodeFormValueWithEntities_emoji_becomesNumericEntity_not0x1A() {
        // 👍 = U+1F44D (128077), 😀 = U+1F600 (128512). Должны стать &#128077; / &#128512;
        // (percent-encoded), а НЕ 0x1A (SUB), который убивал смайлы. `&` кодируется как %26.
        val encoded = Cp1251Codec.encodeFormValueWithEntities("hi👍😀")
        assertEquals("hi%26%23128077%3B%26%23128512%3B", encoded)
        // никакого SUB (0x1A) и никакого '?' (0x3F, %3F) как замены непредставимого символа
        assertFalse(encoded.contains("%1A"))
        assertFalse(encoded.contains(""))
    }

    @Test
    fun encodeFormValueWithEntities_cyrillicAndAscii_unchangedFromCp1251Encode() {
        // Представимые в cp1251 символы кодируются как обычно (без мнемоник).
        assertEquals(Cp1251Codec.encode("Привет, мир 123!"),
                Cp1251Codec.encodeFormValueWithEntities("Привет, мир 123!"))
    }

    @Test
    fun encodeFormValueWithEntities_mixedCyrillicAndEmoji() {
        // Кириллица остаётся cp1251-percent, эмодзи → &#NNNN;. Проверяем, что мнемоника присутствует
        // и нет символа-замены SUB.
        val encoded = Cp1251Codec.encodeFormValueWithEntities("тест 👍")
        assertTrue(encoded.contains("%26%23128077%3B"))
        assertFalse(encoded.contains(""))
    }
}
