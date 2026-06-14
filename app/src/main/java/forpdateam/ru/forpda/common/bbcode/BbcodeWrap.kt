package forpdateam.ru.forpda.common.bbcode

/**
 * Pure string/selection BBCode wrapper.
 *
 * Behavior:
 * - Non-empty selection: wraps selection with [open] + [close] and keeps selection on original text.
 * - Empty selection: inserts [open][close] and places cursor inside (or after close when [placeCursorInsideIfEmpty]=false).
 */
object BbcodeWrap {
    data class Result(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    fun wrap(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        open: String,
        close: String,
        keepSelection: Boolean = true,
        placeCursorInsideIfEmpty: Boolean = true
    ): Result {
        val len = text.length
        var s = selectionStart.coerceIn(0, len)
        var e = selectionEnd.coerceIn(0, len)
        if (e < s) {
            val t = s
            s = e
            e = t
        }

        val hasSelection = s != e
        if (!hasSelection) {
            val out = StringBuilder(text.length + open.length + close.length)
                .append(text, 0, s)
                .append(open)
                .append(close)
                .append(text, s, len)
                .toString()

            val cursor = if (placeCursorInsideIfEmpty) s + open.length else s + open.length + close.length
            return Result(out, cursor, cursor)
        }

        val out = StringBuilder(text.length + open.length + close.length)
            .append(text, 0, s)
            .append(open)
            .append(text, s, e)
            .append(close)
            .append(text, e, len)
            .toString()

        return if (keepSelection) {
            Result(out, s + open.length, e + open.length)
        } else {
            val cursor = e + open.length + close.length
            Result(out, cursor, cursor)
        }
    }
}

