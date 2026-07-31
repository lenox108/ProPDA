package forpdateam.ru.forpda.common.animation

import java.io.ByteArrayOutputStream

/**
 * Независимый минимальный декодер GIF89a — только для тестов.
 *
 * Юнит-тесты приложения компилируются против android.jar, где нет `javax.imageio`, поэтому
 * проверить кодировщик готовым декодером негде. Этот написан отдельно, прямо по спецификации
 * (LZW с переменной шириной кода), чтобы ошибка в [GifEncoder] не «подтверждалась» сама собой.
 *
 * Ширина кода растёт по размеру СЛОВАРЯ декодера (как в libgif/PIL): запись декодер добавляет
 * на один код позже кодировщика, поэтому кодировщик обязан расширять код на шаг позже
 * (`nextCode > 1 shl codeSize`). Именно на этом расхождении ломался первый вариант
 * [GifEncoder] — самописный симметричный декодер его читал, а PIL нет.
 */
internal object GifDecoderForTests {

    class Frame(
        val width: Int,
        val height: Int,
        val delayCs: Int,
        /** ARGB-пиксели; прозрачные — с нулевой альфой. */
        val pixels: IntArray,
    )

    class Image(val width: Int, val height: Int, val frames: List<Frame>)

    fun decode(bytes: ByteArray): Image {
        val reader = Reader(bytes)
        require(String(bytes, 0, 6, Charsets.US_ASCII) == "GIF89a") { "не GIF89a" }
        reader.skip(6)

        val width = reader.short()
        val height = reader.short()
        val packed = reader.byte()
        reader.byte() // background
        reader.byte() // aspect
        require(packed and 0x80 == 0) { "глобальная палитра этим кодировщиком не пишется" }

        val frames = mutableListOf<Frame>()
        var delayCs = 0
        var transparentIndex = -1

        loop@ while (reader.hasMore()) {
            when (val block = reader.byte()) {
                0x3B -> break@loop

                0x21 -> {
                    val label = reader.byte()
                    if (label == 0xF9) {
                        require(reader.byte() == 4) { "размер блока GCE" }
                        val flags = reader.byte()
                        delayCs = reader.short()
                        val transparent = reader.byte()
                        transparentIndex = if (flags and 0x01 != 0) transparent else -1
                        require(reader.byte() == 0) { "терминатор GCE" }
                    } else {
                        reader.skipSubBlocks()
                    }
                }

                0x2C -> {
                    val left = reader.short()
                    val top = reader.short()
                    val frameWidth = reader.short()
                    val frameHeight = reader.short()
                    val imagePacked = reader.byte()
                    require(left == 0 && top == 0) { "кадры пишутся во весь холст" }
                    require(imagePacked and 0x80 != 0) { "ожидалась локальная палитра" }
                    require(imagePacked and 0x40 == 0) { "чересстрочные кадры не поддержаны" }

                    val paletteSize = 1 shl ((imagePacked and 0x07) + 1)
                    val palette = IntArray(paletteSize) {
                        (reader.byte() shl 16) or (reader.byte() shl 8) or reader.byte()
                    }
                    val minCodeSize = reader.byte()
                    val data = reader.readSubBlocks()
                    val indices = inflateLzw(data, minCodeSize, frameWidth * frameHeight)

                    val pixels = IntArray(frameWidth * frameHeight) { i ->
                        val index = indices[i]
                        if (index == transparentIndex) 0 else (0xFF shl 24) or palette[index]
                    }
                    frames.add(Frame(frameWidth, frameHeight, delayCs, pixels))
                }

                else -> error("неизвестный блок 0x${block.toString(16)}")
            }
        }
        return Image(width, height, frames)
    }

    private class Reader(private val bytes: ByteArray) {
        private var offset = 0

        fun hasMore(): Boolean = offset < bytes.size
        fun skip(count: Int) { offset += count }
        fun byte(): Int = bytes[offset++].toInt() and 0xFF
        fun short(): Int = byte() or (byte() shl 8)

        fun readSubBlocks(): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val size = byte()
                if (size == 0) break
                out.write(bytes, offset, size)
                offset += size
            }
            return out.toByteArray()
        }

        fun skipSubBlocks() {
            while (true) {
                val size = byte()
                if (size == 0) break
                offset += size
            }
        }
    }

    private fun inflateLzw(data: ByteArray, minCodeSize: Int, expected: Int): IntArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val out = IntArray(expected)
        var written = 0

        val prefix = IntArray(4096) { -1 }
        val suffix = IntArray(4096) { if (it < clearCode) it else 0 }
        val firstChar = IntArray(4096) { if (it < clearCode) it else 0 }
        val stack = IntArray(4096)

        var codeSize = minCodeSize + 1
        var nextEntry = endCode + 1   // индекс следующей записи словаря
        var previous = -1

        var bitBuffer = 0
        var bitCount = 0
        var offset = 0

        fun emitString(code: Int, tail: Int): Int {
            var depth = 0
            var walk = code
            while (walk >= 0) {
                stack[depth++] = suffix[walk]
                walk = prefix[walk]
            }
            if (tail >= 0) {
                // Случай KwKwK: строка = предыдущая + её первый символ.
                System.arraycopy(stack, 0, stack, 1, depth)
                stack[0] = tail
                depth++
            }
            for (i in depth - 1 downTo 0) {
                if (written < expected) out[written++] = stack[i]
            }
            return depth
        }

        while (true) {
            while (bitCount < codeSize && offset < data.size) {
                bitBuffer = bitBuffer or ((data[offset++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            if (bitCount < codeSize) break

            val code = bitBuffer and ((1 shl codeSize) - 1)
            bitBuffer = bitBuffer ushr codeSize
            bitCount -= codeSize

            if (code == endCode) break
            if (code == clearCode) {
                codeSize = minCodeSize + 1
                nextEntry = endCode + 1
                previous = -1
                continue
            }

            if (previous < 0) {
                require(code < clearCode) { "первый код после clear обязан быть корневым" }
                emitString(code, -1)
            } else {
                if (code < nextEntry) {
                    emitString(code, -1)
                    prefix[nextEntry] = previous
                    suffix[nextEntry] = firstChar[code]
                    firstChar[nextEntry] = firstChar[previous]
                } else {
                    require(code == nextEntry) { "код вне словаря: $code (ожидался ≤ $nextEntry)" }
                    emitString(previous, firstChar[previous])
                    prefix[nextEntry] = previous
                    suffix[nextEntry] = firstChar[previous]
                    firstChar[nextEntry] = firstChar[previous]
                }
                if (nextEntry < 4095) nextEntry++
                // Стандартное правило декодера: ширина растёт, как только очередной индекс
                // словаря перестаёт помещаться в текущую ширину.
                if (nextEntry >= (1 shl codeSize) && codeSize < 12) codeSize++
            }
            previous = code
        }
        return out
    }
}
