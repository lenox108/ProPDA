package forpdateam.ru.forpda.common.animation

import java.io.OutputStream

/**
 * Минимальный кодировщик анимированного GIF89a.
 *
 * Нужен потому, что форум 4PDA принимает из анимированных форматов ТОЛЬКО gif:
 * animated WebP он отклоняет на аплоаде, а APNG принимает и пережимает в один кадр
 * (проверено живьём). Готового gif-энкодера в Android SDK нет, поэтому пишем свой:
 * медианное сечение под палитру ≤256 цветов + стандартный LZW.
 *
 * Кадры подаются уже склеенными на полный холст (ARGB_8888, `width * height` пикселей),
 * поэтому здесь нет логики dispose/blend — она в [AnimatedToGifConverter].
 * Полупрозрачность GIF не умеет: пиксели с alpha < 128 считаются прозрачными,
 * остальные — непрозрачными.
 */
object GifEncoder {

    /** Кадр: ARGB_8888-пиксели полного холста и задержка до следующего кадра. */
    class Frame(val pixels: IntArray, val delayMs: Int)

    private const val MAX_COLORS = 256
    private const val ALPHA_THRESHOLD = 128
    private const val MAX_CODE_SIZE = 12

    /**
     * Кадры берутся лениво (`Sequence`), поэтому в памяти живёт только текущий холст —
     * иначе анимация из сотни кадров 1000×1000 не влезла бы.
     *
     * @return число записанных кадров.
     */
    fun encode(
        out: OutputStream,
        width: Int,
        height: Int,
        frames: Sequence<Frame>,
        loopForever: Boolean = true,
    ): Int {
        require(width > 0 && height > 0) { "GIF canvas must be non-empty" }

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        // Глобальной палитры нет — у каждого кадра своя локальная (так качество выше).
        out.write(0x70) // color resolution = 8 бит
        out.write(0) // индекс фона
        out.write(0) // pixel aspect ratio

        if (loopForever) {
            writeNetscapeLoop(out)
        }

        var written = 0
        for (frame in frames) {
            writeFrame(out, width, height, frame)
            written++
        }

        out.write(0x3B) // trailer
        out.flush()
        return written
    }

    private fun writeNetscapeLoop(out: OutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(0x03)
        out.write(0x01)
        writeShort(out, 0) // 0 = бесконечно
        out.write(0x00)
    }

    private fun writeFrame(out: OutputStream, width: Int, height: Int, frame: Frame) {
        val quantized = quantize(frame.pixels)
        val paletteSize = paletteSlots(quantized.palette.size)
        val hasTransparency = quantized.transparentIndex >= 0

        // Graphic Control Extension: задержка + прозрачный индекс.
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        // disposal 2 (restore to background) для кадров с прозрачностью, иначе 1 (leave as is).
        val disposal = if (hasTransparency) 2 else 1
        out.write((disposal shl 2) or (if (hasTransparency) 1 else 0))
        // Задержка в сотых долях секунды. Меньше 2 сс многие рендереры трактуют как «10».
        writeShort(out, maxOf(2, (frame.delayMs + 5) / 10))
        out.write(if (hasTransparency) quantized.transparentIndex else 0)
        out.write(0x00)

        // Image Descriptor: кадр всегда во весь холст, палитра локальная.
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x80 or (log2(paletteSize) - 1))

        for (i in 0 until paletteSize) {
            val color = if (i < quantized.palette.size) quantized.palette[i] else 0
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }

        writeLzw(out, quantized.indices, maxOf(2, log2(paletteSize)))
    }

    /** Число слотов палитры — степень двойки от 2 до 256. */
    private fun paletteSlots(colors: Int): Int {
        var slots = 2
        while (slots < colors) slots = slots shl 1
        return minOf(slots, MAX_COLORS)
    }

    private fun log2(value: Int): Int {
        var bits = 0
        var v = value
        while (v > 1) {
            v = v shr 1
            bits++
        }
        return bits
    }

    private class Quantized(
        val palette: IntArray,
        val indices: ByteArray,
        val transparentIndex: Int,
    )

    /**
     * Медианное сечение: гистограмма непрозрачных цветов → до 255 боксов, цвет бокса —
     * его средневзвешенный цвет. Каждый цвет гистограммы получает индекс своего бокса,
     * поэтому поиск ближайшего цвета для каждого пикселя не нужен.
     */
    private fun quantize(pixels: IntArray): Quantized {
        val histogram = HashMap<Int, Int>()
        var hasTransparent = false
        for (pixel in pixels) {
            if ((pixel ushr 24) < ALPHA_THRESHOLD) {
                hasTransparent = true
                continue
            }
            val rgb = pixel and 0xFFFFFF
            histogram[rgb] = (histogram[rgb] ?: 0) + 1
        }

        val maxColors = if (hasTransparent) MAX_COLORS - 1 else MAX_COLORS
        val colorToIndex = HashMap<Int, Int>(histogram.size * 2)
        val palette: IntArray

        if (histogram.isEmpty()) {
            palette = intArrayOf(0)
        } else if (histogram.size <= maxColors) {
            palette = IntArray(histogram.size)
            var index = 0
            for (color in histogram.keys) {
                palette[index] = color
                colorToIndex[color] = index
                index++
            }
        } else {
            val boxes = medianCut(histogram, maxColors)
            palette = IntArray(boxes.size)
            for ((index, box) in boxes.withIndex()) {
                palette[index] = box.averageColor()
                for (entry in box.entries) {
                    colorToIndex[entry.rgb] = index
                }
            }
        }

        val transparentIndex = if (hasTransparent) palette.size else -1
        val indices = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            indices[i] = if ((pixel ushr 24) < ALPHA_THRESHOLD) {
                transparentIndex.toByte()
            } else {
                (colorToIndex[pixel and 0xFFFFFF] ?: 0).toByte()
            }
        }

        val fullPalette = if (transparentIndex >= 0) {
            palette.copyOf(palette.size + 1)
        } else {
            palette
        }
        return Quantized(fullPalette, indices, transparentIndex)
    }

    private class ColorEntry(val rgb: Int, val count: Int)

    private class Box(val entries: MutableList<ColorEntry>) {
        var minR = 255
        var maxR = 0
        var minG = 255
        var maxG = 0
        var minB = 255
        var maxB = 0

        init {
            for (entry in entries) {
                val r = (entry.rgb shr 16) and 0xFF
                val g = (entry.rgb shr 8) and 0xFF
                val b = entry.rgb and 0xFF
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (g < minG) minG = g
                if (g > maxG) maxG = g
                if (b < minB) minB = b
                if (b > maxB) maxB = b
            }
        }

        fun longestSide(): Int = maxOf(maxR - minR, maxG - minG, maxB - minB)

        fun averageColor(): Int {
            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var total = 0L
            for (entry in entries) {
                val weight = entry.count.toLong()
                totalR += ((entry.rgb shr 16) and 0xFF) * weight
                totalG += ((entry.rgb shr 8) and 0xFF) * weight
                totalB += (entry.rgb and 0xFF) * weight
                total += weight
            }
            if (total == 0L) return 0
            val r = (totalR / total).toInt().coerceIn(0, 255)
            val g = (totalG / total).toInt().coerceIn(0, 255)
            val b = (totalB / total).toInt().coerceIn(0, 255)
            return (r shl 16) or (g shl 8) or b
        }
    }

    private fun medianCut(histogram: Map<Int, Int>, maxColors: Int): List<Box> {
        val entries = histogram.entries.map { ColorEntry(it.key, it.value) }.toMutableList()
        val boxes = mutableListOf(Box(entries))
        while (boxes.size < maxColors) {
            // Режем самый «широкий» бокс, который вообще делится.
            val target = boxes
                .filter { it.entries.size > 1 && it.longestSide() > 0 }
                .maxByOrNull { it.longestSide() } ?: break
            val split = splitBox(target) ?: break
            boxes.remove(target)
            boxes.add(split.first)
            boxes.add(split.second)
        }
        return boxes
    }

    private fun splitBox(box: Box): Pair<Box, Box>? {
        val rangeR = box.maxR - box.minR
        val rangeG = box.maxG - box.minG
        val rangeB = box.maxB - box.minB
        val shift = when {
            rangeR >= rangeG && rangeR >= rangeB -> 16
            rangeG >= rangeB -> 8
            else -> 0
        }
        val sorted = box.entries.sortedBy { (it.rgb shr shift) and 0xFF }
        val total = sorted.sumOf { it.count.toLong() }
        var accumulated = 0L
        var splitAt = 0
        for (i in sorted.indices) {
            accumulated += sorted[i].count
            if (accumulated * 2 >= total && i < sorted.size - 1) {
                splitAt = i + 1
                break
            }
        }
        if (splitAt <= 0 || splitAt >= sorted.size) {
            splitAt = sorted.size / 2
        }
        if (splitAt <= 0 || splitAt >= sorted.size) return null
        val head = Box(sorted.subList(0, splitAt).toMutableList())
        val tail = Box(sorted.subList(splitAt, sorted.size).toMutableList())
        return head to tail
    }

    /** Классический GIF-LZW с переменной длиной кода и упаковкой в под-блоки по 255 байт. */
    private fun writeLzw(out: OutputStream, indices: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)

        val blockBuffer = ByteArray(255)
        var blockSize = 0
        var bitBuffer = 0
        var bitCount = 0

        fun flushBlock() {
            if (blockSize > 0) {
                out.write(blockSize)
                out.write(blockBuffer, 0, blockSize)
                blockSize = 0
            }
        }

        fun writeByte(value: Int) {
            blockBuffer[blockSize++] = value.toByte()
            if (blockSize == 255) flushBlock()
        }

        var codeSize = minCodeSize + 1

        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                writeByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val dictionary = HashMap<Int, Int>()
        var nextCode = endCode + 1

        emit(clearCode)

        if (indices.isEmpty()) {
            emit(endCode)
        } else {
            var prefix = indices[0].toInt() and 0xFF
            for (i in 1 until indices.size) {
                val suffix = indices[i].toInt() and 0xFF
                val key = (prefix shl 8) or suffix
                val known = dictionary[key]
                if (known != null) {
                    prefix = known
                    continue
                }
                emit(prefix)
                dictionary[key] = nextCode
                nextCode++
                // Словарь декодера отстаёт от нашего ровно на одну запись (он узнаёт её только
                // из СЛЕДУЮЩЕГО кода), поэтому ширину расширяем на шаг позже: строго `>`,
                // а не `>=`. С `>=` поток разъезжается на 512-м коде — PIL/libgif ловит
                // «broken data stream», хотя симметричный самописный декодер читает нормально.
                if (nextCode > (1 shl codeSize)) {
                    if (codeSize < MAX_CODE_SIZE) {
                        codeSize++
                    } else {
                        emit(clearCode)
                        dictionary.clear()
                        nextCode = endCode + 1
                        codeSize = minCodeSize + 1
                    }
                }
                prefix = suffix
            }
            emit(prefix)
            emit(endCode)
        }

        // Хвост битового буфера.
        while (bitCount > 0) {
            writeByte(bitBuffer and 0xFF)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
        flushBlock()
        out.write(0x00) // конец потока под-блоков
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}
