package forpdateam.ru.forpda.common.animation

/**
 * Определение формата картинки по её байтам (не по расширению и не по mime от провайдера —
 * галерея и мессенджеры регулярно врут в обоих).
 */
object AnimatedImageProbe {

    enum class Format {
        /** Анимированный WebP (VP8X с флагом ANIMATION). Форум такое не принимает вовсе. */
        ANIMATED_WEBP,

        /** Статический WebP. */
        STATIC_WEBP,

        /** PNG с acTL — анимация. Форум принимает, но пережимает в один кадр. */
        ANIMATED_PNG,

        /** Всё остальное (jpeg, обычный png, gif, не-картинка) — трогать не нужно. */
        OTHER,
    }

    fun detect(bytes: ByteArray): Format = when {
        isWebP(bytes) -> if (isAnimatedWebP(bytes)) Format.ANIMATED_WEBP else Format.STATIC_WEBP
        isPng(bytes) && hasApngControlChunk(bytes) -> Format.ANIMATED_PNG
        else -> Format.OTHER
    }

    fun isWebP(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes.matchesAscii(0, "RIFF") &&
            bytes.matchesAscii(8, "WEBP")

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()

    /** Флаг ANIMATION (бит 1) в чанке VP8X. */
    private fun isAnimatedWebP(bytes: ByteArray): Boolean {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = readUInt32Le(bytes, offset + 4)
            if (bytes.matchesAscii(offset, "VP8X")) {
                return offset + 8 < bytes.size && (bytes[offset + 8].toInt() and 0x02) != 0
            }
            if (size < 0) return false
            offset += 8 + size + (size and 1)
        }
        return false
    }

    /**
     * acTL до первого IDAT = APNG. Ищем именно чанк, а не подстроку, чтобы не поймать
     * «acTL» внутри сжатых данных.
     */
    private fun hasApngControlChunk(bytes: ByteArray): Boolean {
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = readUInt32Be(bytes, offset)
            if (length < 0) return false
            if (bytes.matchesAscii(offset + 4, "acTL")) return true
            if (bytes.matchesAscii(offset + 4, "IDAT")) return false
            offset += 12 + length
        }
        return false
    }

    internal fun ByteArray.matchesAscii(offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > size) return false
        for (i in text.indices) {
            if (this[offset + i] != text[i].code.toByte()) return false
        }
        return true
    }

    /** @return -1, если размер не влезает в Int (битый/огромный файл). */
    internal fun readUInt32Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        val value = (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
        return if (value > Int.MAX_VALUE) -1 else value.toInt()
    }

    internal fun readUInt32Be(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return -1
        val value = ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
        return if (value > Int.MAX_VALUE) -1 else value.toInt()
    }

    internal fun readUInt24Le(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    }

    internal fun readUInt16Be(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return -1
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
