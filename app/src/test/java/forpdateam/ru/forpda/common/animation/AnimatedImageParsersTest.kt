package forpdateam.ru.forpda.common.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * Разбор анимированных контейнеров на кадры. Фикстуры — трёхкадровые 8×6 анимации
 * (красный → зелёный → синий), сгенерированные Pillow.
 */
class AnimatedImageParsersTest {

    @Test
    fun `detects animated webp and animated png`() {
        assertEquals(
            AnimatedImageProbe.Format.ANIMATED_WEBP,
            AnimatedImageProbe.detect(fixture("three_frames.webp")),
        )
        assertEquals(
            AnimatedImageProbe.Format.ANIMATED_PNG,
            AnimatedImageProbe.detect(fixture("three_frames.png")),
        )
    }

    @Test
    fun `single frame png is not treated as animated`() {
        // Кадр, вырезанный из APNG, — обычный png без acTL: конвертировать его не нужно.
        val singleFrame = ApngParser.parse(fixture("three_frames.png"))!!.frames[0].image
        assertEquals(AnimatedImageProbe.Format.OTHER, AnimatedImageProbe.detect(singleFrame))
    }

    @Test
    fun `webp animation splits into standalone frames`() {
        val animation = WebPAnimParser.parse(fixture("three_frames.webp"))
        assertNotNull(animation)
        requireNotNull(animation)

        assertEquals(8, animation.width)
        assertEquals(6, animation.height)
        assertEquals(3, animation.frames.size)
        animation.frames.forEach { frame ->
            assertEquals(100, frame.durationMs)
            assertEquals(8, frame.width)
            assertEquals(6, frame.height)
            assertEquals("RIFF", String(frame.image, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(frame.image, 8, 4, Charsets.US_ASCII))
            // Заявленный размер RIFF обязан совпасть с фактическим, иначе декодер файл отвергнет.
            assertEquals(frame.image.size - 8, AnimatedImageProbe.readUInt32Le(frame.image, 4))
            // Внутри должен лежать реальный кадр (lossy VP8 или lossless VP8L).
            val tag = String(frame.image, 12, 4, Charsets.US_ASCII)
            assertTrue("неожиданный чанк: $tag", tag == "VP8 " || tag == "VP8L" || tag == "VP8X")
        }
    }

    @Test
    fun `apng animation splits into valid png frames`() {
        val animation = ApngParser.parse(fixture("three_frames.png"))
        assertNotNull(animation)
        requireNotNull(animation)

        assertEquals(8, animation.width)
        assertEquals(6, animation.height)
        assertEquals(3, animation.frames.size)

        animation.frames.forEachIndexed { index, frame ->
            assertEquals("задержка кадра $index", 100, frame.delayMs)
            val chunks = pngChunks(frame.image)
            assertEquals("IHDR", chunks.first().type)
            assertEquals("IEND", chunks.last().type)
            assertTrue("кадр $index должен нести данные", chunks.any { it.type == "IDAT" })

            val ihdr = chunks.first().data
            assertEquals(frame.width, readInt(ihdr, 0))
            assertEquals(frame.height, readInt(ihdr, 4))

            // Данные кадра должны распаковываться и давать ровно height строк по (1 + width*канал).
            val raw = inflate(chunks.filter { it.type == "IDAT" }.fold(ByteArray(0)) { acc, c -> acc + c.data })
            val channels = when (val colorType = ihdr[9].toInt()) {
                6 -> 4
                2 -> 3
                else -> error("неожиданный тип цвета $colorType")
            }
            assertEquals(frame.height * (1 + frame.width * channels), raw.size)
        }
    }

    @Test
    fun `non image bytes are not parsed`() {
        val garbage = ByteArray(64) { it.toByte() }
        assertEquals(AnimatedImageProbe.Format.OTHER, AnimatedImageProbe.detect(garbage))
        assertNull(WebPAnimParser.parse(garbage))
        assertNull(ApngParser.parse(garbage))
    }

    private class Chunk(val type: String, val data: ByteArray)

    /** Разбирает png на чанки, попутно сверяя CRC — собранный вручную файл обязан быть валидным. */
    private fun pngChunks(bytes: ByteArray): List<Chunk> {
        assertTrue("сигнатура png", AnimatedImageProbe.isPng(bytes))
        val chunks = mutableListOf<Chunk>()
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val data = bytes.copyOfRange(offset + 8, offset + 8 + length)
            val crc = CRC32().apply {
                update(bytes, offset + 4, 4 + length)
            }.value.toInt()
            assertEquals("CRC чанка $type", crc, readInt(bytes, offset + 8 + length))
            chunks.add(Chunk(type, data))
            offset += 12 + length
        }
        return chunks
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("animation/$name")) {
            "нет фикстуры animation/$name"
        }.use { it.readBytes() }
}
