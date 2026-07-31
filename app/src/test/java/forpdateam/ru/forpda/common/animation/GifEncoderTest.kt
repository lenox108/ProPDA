package forpdateam.ru.forpda.common.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Кодировщик проверяем независимым декодером [GifDecoderForTests], написанным по спецификации:
 * ошибка в LZW или в моменте расширения кода иначе всплыла бы только на живой отправке.
 */
class GifEncoderTest {

    @Test
    fun `encodes multi frame animation`() {
        val width = 24
        val height = 16
        val colors = listOf(0xFF0000, 0x00FF00, 0x0000FF)
        val frames = colors.map { color ->
            GifEncoder.Frame(IntArray(width * height) { (0xFF shl 24) or color }, delayMs = 100)
        }

        val bytes = encode(width, height, frames)
        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))

        val decoded = GifDecoderForTests.decode(bytes)
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        assertEquals(3, decoded.frames.size)
        decoded.frames.forEachIndexed { index, frame ->
            assertEquals("задержка кадра $index", 10, frame.delayCs)
            assertEquals("цвет кадра $index", colors[index], frame.pixels[width * height / 2] and 0xFFFFFF)
        }
    }

    @Test
    fun `keeps gradients recognizable after palette reduction`() {
        // Больше 256 уникальных цветов — включается медианное сечение.
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (0xFF shl 24) or ((x * 4) shl 16) or ((y * 4) shl 8) or 0x40
        }

        val decoded = GifDecoderForTests.decode(encode(width, height, listOf(GifEncoder.Frame(pixels, 100))))
        val frame = decoded.frames.single()
        val brightness = { color: Int ->
            ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)
        }
        val topLeft = frame.pixels[1 * width + 1]
        val bottomRight = frame.pixels[(height - 2) * width + (width - 2)]
        assertTrue(
            "градиент не должен схлопнуться в один цвет",
            brightness(bottomRight) - brightness(topLeft) > 200,
        )
        // Квантование не должно уводить цвет далеко от исходного.
        val expected = pixels[1 * width + 1]
        assertTrue(
            "цвет после квантования близок к исходному",
            kotlin.math.abs(brightness(topLeft) - brightness(expected)) < 30,
        )
    }

    @Test
    fun `transparent pixels survive the round trip`() {
        val width = 8
        val height = 8
        val pixels = IntArray(width * height) { index ->
            if (index % 2 == 0) 0xFF123456.toInt() else 0
        }

        val frame = GifDecoderForTests.decode(encode(width, height, listOf(GifEncoder.Frame(pixels, 100))))
            .frames.single()
        assertEquals(0, frame.pixels[1] ushr 24)
        assertEquals(255, frame.pixels[0] ushr 24)
        assertEquals(0x123456, frame.pixels[0] and 0xFFFFFF)
    }

    @Test
    fun `long runs push lzw through every code width`() {
        // 256×256 одного цвета: словарь дорастает до 12 бит и сбрасывается clear-кодом.
        val width = 256
        val height = 256
        val pixels = IntArray(width * height) { 0xFF808080.toInt() }

        val frame = GifDecoderForTests.decode(encode(width, height, listOf(GifEncoder.Frame(pixels, 100))))
            .frames.single()
        assertEquals(width * height, frame.pixels.size)
        assertTrue("все пиксели должны остаться серыми", frame.pixels.all { it and 0xFFFFFF == 0x808080 })
    }

    @Test
    fun `noisy image round trips pixel exactly when palette fits`() {
        // Ровно 256 цветов — палитра вмещает всё, значит потерь быть не должно.
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { index ->
            val value = index % 256
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }

        val frame = GifDecoderForTests.decode(encode(width, height, listOf(GifEncoder.Frame(pixels, 40))))
            .frames.single()
        for (i in pixels.indices) {
            assertEquals("пиксель $i", pixels[i] and 0xFFFFFF, frame.pixels[i] and 0xFFFFFF)
        }
        assertEquals("задержка меньше 20 мс округляется вверх до 2 сс", 4, frame.delayCs)
    }

    @Test
    fun `noisy large frame exhausts the dictionary and still decodes`() {
        // Шумная картинка 200×200 на 256 цветов переполняет словарь: поток проходит все
        // ширины кода и хотя бы один сброс clear-кодом.
        val width = 200
        val height = 200
        var seed = 12345L
        val pixels = IntArray(width * height) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            val value = ((seed ushr 33).toInt() and 0xFF)
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }

        val frame = GifDecoderForTests.decode(encode(width, height, listOf(GifEncoder.Frame(pixels, 100))))
            .frames.single()
        assertEquals(width * height, frame.pixels.size)
        for (i in pixels.indices) {
            assertEquals("пиксель $i", pixels[i] and 0xFFFFFF, frame.pixels[i] and 0xFFFFFF)
        }
    }

    private fun encode(width: Int, height: Int, frames: List<GifEncoder.Frame>): ByteArray {
        val out = ByteArrayOutputStream()
        val written = GifEncoder.encode(out, width, height, frames.asSequence())
        assertEquals(frames.size, written)
        return out.toByteArray()
    }
}
