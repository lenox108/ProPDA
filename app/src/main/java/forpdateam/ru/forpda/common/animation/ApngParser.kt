package forpdateam.ru.forpda.common.animation

import forpdateam.ru.forpda.common.animation.AnimatedImageProbe.matchesAscii
import forpdateam.ru.forpda.common.animation.AnimatedImageProbe.readUInt16Be
import forpdateam.ru.forpda.common.animation.AnimatedImageProbe.readUInt32Be
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Разбор APNG (acTL/fcTL/fdAT) на отдельные кадры.
 *
 * Android APNG не анимирует вовсе, а 4PDA принятый .png ещё и пережимает в один кадр,
 * поэтому такие файлы мы перекодируем в gif. Каждый кадр здесь собирается обратно
 * в самостоятельный .png (IHDR с размерами кадра + палитра/прозрачность исходника +
 * склеенные IDAT), который умеет декодировать `BitmapFactory`.
 *
 * Чистый разбор байтов, без Android-зависимостей — тестируется на JVM.
 */
object ApngParser {

    /** Значения dispose_op из спецификации APNG. */
    const val DISPOSE_NONE = 0
    const val DISPOSE_BACKGROUND = 1
    const val DISPOSE_PREVIOUS = 2

    /** Значения blend_op из спецификации APNG. */
    const val BLEND_SOURCE = 0
    const val BLEND_OVER = 1

    class Frame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val delayMs: Int,
        val disposeOp: Int,
        val blendOp: Int,
        /** Готовый однокадровый png. */
        val image: ByteArray,
    )

    class Animation(val width: Int, val height: Int, val frames: List<Frame>)

    private const val MAX_FRAMES = 512
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val COPIED_CHUNKS = setOf("PLTE", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT")

    fun parse(bytes: ByteArray): Animation? {
        if (!AnimatedImageProbe.isPng(bytes)) return null

        var header: ByteArray? = null
        var canvasWidth = 0
        var canvasHeight = 0
        var animated = false
        val ancillary = ByteArrayOutputStream()

        class PendingFrame(
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int,
            val delayMs: Int,
            val disposeOp: Int,
            val blendOp: Int,
        ) {
            val data = ByteArrayOutputStream()
        }

        val pending = mutableListOf<PendingFrame>()
        var current: PendingFrame? = null
        var seenIdat = false

        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = readUInt32Be(bytes, offset)
            if (length < 0 || offset + 12 + length > bytes.size) break
            val dataAt = offset + 8

            when {
                bytes.matchesAscii(offset + 4, "IHDR") && length >= 13 -> {
                    header = bytes.copyOfRange(dataAt, dataAt + 13)
                    canvasWidth = readUInt32Be(bytes, dataAt)
                    canvasHeight = readUInt32Be(bytes, dataAt + 4)
                }

                bytes.matchesAscii(offset + 4, "acTL") -> animated = true

                bytes.matchesAscii(offset + 4, "fcTL") && length >= 26 -> {
                    current?.let { pending.add(it) }
                    val delayNum = readUInt16Be(bytes, dataAt + 20)
                    val delayDen = readUInt16Be(bytes, dataAt + 22).let { if (it == 0) 100 else it }
                    // За пределами лимита кадры просто перестаём собирать (current = null).
                    current = if (pending.size >= MAX_FRAMES) {
                        null
                    } else {
                        PendingFrame(
                            x = readUInt32Be(bytes, dataAt + 12),
                            y = readUInt32Be(bytes, dataAt + 16),
                            width = readUInt32Be(bytes, dataAt + 4),
                            height = readUInt32Be(bytes, dataAt + 8),
                            delayMs = delayNum * 1000 / delayDen,
                            disposeOp = bytes[dataAt + 24].toInt() and 0xFF,
                            blendOp = bytes[dataAt + 25].toInt() and 0xFF,
                        )
                    }
                }

                bytes.matchesAscii(offset + 4, "IDAT") -> {
                    seenIdat = true
                    // IDAT принадлежит анимации, только если ему предшествовал fcTL.
                    current?.data?.write(bytes, dataAt, length)
                }

                bytes.matchesAscii(offset + 4, "fdAT") && length > 4 -> {
                    // Первые 4 байта — номер последовательности, дальше обычные данные IDAT.
                    current?.data?.write(bytes, dataAt + 4, length - 4)
                }

                !seenIdat && COPIED_CHUNKS.contains(chunkType(bytes, offset + 4)) -> {
                    writeChunk(ancillary, chunkType(bytes, offset + 4), bytes, dataAt, length)
                }
            }

            offset = dataAt + length + 4
        }

        current?.let { pending.add(it) }
        if (!animated || header == null || pending.isEmpty()) return null
        if (canvasWidth <= 0 || canvasHeight <= 0) return null

        val ancillaryBytes = ancillary.toByteArray()
        val frames = pending.mapNotNull { frame ->
            val data = frame.data.toByteArray()
            if (data.isEmpty() || frame.width <= 0 || frame.height <= 0) return@mapNotNull null
            Frame(
                x = frame.x,
                y = frame.y,
                width = frame.width,
                height = frame.height,
                delayMs = frame.delayMs,
                disposeOp = frame.disposeOp,
                blendOp = frame.blendOp,
                image = buildStandalonePng(header, ancillaryBytes, frame.width, frame.height, data),
            )
        }
        if (frames.isEmpty()) return null
        return Animation(canvasWidth, canvasHeight, frames)
    }

    private fun chunkType(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    /** IHDR исходника с подменёнными шириной/высотой + служебные чанки + склеенные данные кадра. */
    private fun buildStandalonePng(
        header: ByteArray,
        ancillary: ByteArray,
        width: Int,
        height: Int,
        data: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(data.size + ancillary.size + 64)
        out.write(PNG_SIGNATURE)

        val ihdr = header.copyOf()
        writeUInt32Be(ihdr, 0, width)
        writeUInt32Be(ihdr, 4, height)
        writeChunk(out, "IHDR", ihdr, 0, ihdr.size)
        out.write(ancillary)
        writeChunk(out, "IDAT", data, 0, data.size)
        writeChunk(out, "IEND", ByteArray(0), 0, 0)
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray, offset: Int, length: Int) {
        val header = ByteArray(4)
        writeUInt32Be(header, 0, length)
        out.write(header)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data, offset, length)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data, offset, length)
        val crcBytes = ByteArray(4)
        writeUInt32Be(crcBytes, 0, crc.value.toInt())
        out.write(crcBytes)
    }

    private fun writeUInt32Be(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
