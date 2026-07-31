package forpdateam.ru.forpda.common.animation

import forpdateam.ru.forpda.common.animation.AnimatedImageProbe.matchesAscii
import forpdateam.ru.forpda.common.animation.AnimatedImageProbe.readUInt24Le
import forpdateam.ru.forpda.common.animation.AnimatedImageProbe.readUInt32Le
import java.io.ByteArrayOutputStream

/**
 * Разбор анимированного WebP (RIFF/VP8X/ANIM/ANMF) на отдельные кадры.
 *
 * Платформенный `ImageDecoder` анимированный WebP рисует, но покадрового доступа не даёт,
 * а нам кадры нужны для перекодирования в gif. Поэтому контейнер разбираем сами: каждый
 * ANMF заворачивается обратно в самостоятельный однокадровый .webp, который уже понимает
 * `BitmapFactory`.
 *
 * Чистый разбор байтов, без Android-зависимостей — тестируется на JVM.
 */
object WebPAnimParser {

    class Frame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val durationMs: Int,
        /** После отрисовки очистить область кадра в прозрачный фон. */
        val disposeToBackground: Boolean,
        /** true = альфа-смешение с холстом, false = кадр перетирает область. */
        val blend: Boolean,
        /** Готовый однокадровый webp. */
        val image: ByteArray,
    )

    class Animation(val width: Int, val height: Int, val frames: List<Frame>)

    private const val MAX_FRAMES = 512

    fun parse(bytes: ByteArray): Animation? {
        if (!AnimatedImageProbe.isWebP(bytes)) return null

        var canvasWidth = 0
        var canvasHeight = 0
        val frames = mutableListOf<Frame>()

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = readUInt32Le(bytes, offset + 4)
            if (size < 0 || offset + 8 + size > bytes.size) break
            val payload = offset + 8

            when {
                bytes.matchesAscii(offset, "VP8X") && size >= 10 -> {
                    canvasWidth = readUInt24Le(bytes, payload + 4) + 1
                    canvasHeight = readUInt24Le(bytes, payload + 7) + 1
                }

                bytes.matchesAscii(offset, "ANMF") && size >= 16 -> {
                    parseFrame(bytes, payload, size)?.let { frames.add(it) }
                    if (frames.size >= MAX_FRAMES) return buildAnimation(canvasWidth, canvasHeight, frames)
                }
            }

            offset = payload + size + (size and 1)
        }

        return buildAnimation(canvasWidth, canvasHeight, frames)
    }

    private fun buildAnimation(width: Int, height: Int, frames: List<Frame>): Animation? {
        if (frames.isEmpty()) return null
        val canvasWidth = if (width > 0) width else frames.maxOf { it.x + it.width }
        val canvasHeight = if (height > 0) height else frames.maxOf { it.y + it.height }
        if (canvasWidth <= 0 || canvasHeight <= 0) return null
        return Animation(canvasWidth, canvasHeight, frames)
    }

    private fun parseFrame(bytes: ByteArray, payload: Int, size: Int): Frame? {
        // Смещения кадра хранятся в единицах по 2 пикселя, размеры — «минус один».
        val x = readUInt24Le(bytes, payload) * 2
        val y = readUInt24Le(bytes, payload + 3) * 2
        val width = readUInt24Le(bytes, payload + 6) + 1
        val height = readUInt24Le(bytes, payload + 9) + 1
        val duration = readUInt24Le(bytes, payload + 12)
        if (x < 0 || y < 0 || width <= 0 || height <= 0) return null
        val flags = bytes[payload + 15].toInt()
        // Бит B: 0 = смешивать с холстом. Бит D: 1 = после кадра очистить его область.
        val blend = (flags and 0x02) == 0
        val dispose = (flags and 0x01) != 0

        val image = buildStandaloneWebP(bytes, payload + 16, payload + size, width, height) ?: return null
        return Frame(x, y, width, height, duration, dispose, blend, image)
    }

    /**
     * Собирает из под-чанков ANMF обычный однокадровый webp. Для «lossy + альфа»
     * (ALPH + VP8) нужен ещё и VP8X с флагом альфы, иначе декодер не примет файл.
     */
    private fun buildStandaloneWebP(
        bytes: ByteArray,
        from: Int,
        to: Int,
        width: Int,
        height: Int,
    ): ByteArray? {
        var alphOffset = -1
        var alphSize = 0
        var imageOffset = -1
        var imageSize = 0
        var imageTag = ""

        var offset = from
        while (offset + 8 <= to) {
            val size = readUInt32Le(bytes, offset + 4)
            if (size < 0 || offset + 8 + size > to) break
            when {
                bytes.matchesAscii(offset, "ALPH") -> {
                    alphOffset = offset + 8
                    alphSize = size
                }

                bytes.matchesAscii(offset, "VP8 ") -> {
                    imageOffset = offset + 8
                    imageSize = size
                    imageTag = "VP8 "
                }

                bytes.matchesAscii(offset, "VP8L") -> {
                    imageOffset = offset + 8
                    imageSize = size
                    imageTag = "VP8L"
                }
            }
            offset += 8 + size + (size and 1)
        }

        if (imageOffset < 0) return null

        val body = ByteArrayOutputStream()
        if (alphOffset >= 0) {
            val vp8x = ByteArray(10)
            vp8x[0] = 0x10 // ALPHA_FLAG
            writeUInt24Le(vp8x, 4, width - 1)
            writeUInt24Le(vp8x, 7, height - 1)
            writeChunk(body, "VP8X", vp8x, 0, vp8x.size)
            writeChunk(body, "ALPH", bytes, alphOffset, alphSize)
        }
        writeChunk(body, imageTag, bytes, imageOffset, imageSize)

        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream(bodyBytes.size + 12)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeUInt32Le(out, bodyBytes.size + 4)
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(bodyBytes)
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, tag: String, data: ByteArray, offset: Int, size: Int) {
        out.write(tag.toByteArray(Charsets.US_ASCII))
        writeUInt32Le(out, size)
        out.write(data, offset, size)
        if (size and 1 == 1) out.write(0)
    }

    private fun writeUInt32Le(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeUInt24Le(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
    }
}
