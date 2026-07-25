package forpdateam.ru.forpda.ui.views

internal data class EditorEdit(
    val start: Int,
    val before: String,
    val after: String,
    val beforeSelectionStart: Int = start,
    val beforeSelectionEnd: Int = start,
    val afterSelectionStart: Int = start + after.length,
    val afterSelectionEnd: Int = start + after.length,
) {
    val weight: Int
        get() = before.length + after.length
}

/**
 * Чистая, независимая от Android история редактора. Группирует обычный набор и backspace,
 * ограничивает память и делает поведение undo/redo проверяемым unit-тестами.
 */
internal class EditorHistory(
    private val maxOperations: Int,
    private val maxChars: Int,
    private val groupingWindowMillis: Long = 1_000L,
) {
    private val undo = ArrayDeque<EditorEdit>()
    private val redo = ArrayDeque<EditorEdit>()
    private var undoChars = 0
    private var lastEditWasTyping = false
    private var lastEditWasDeleting = false
    private var lastEditAtMillis = Long.MIN_VALUE

    val canUndo: Boolean
        get() = undo.isNotEmpty()
    val canRedo: Boolean
        get() = redo.isNotEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
        undoChars = 0
        resetGrouping()
    }

    fun record(
        start: Int,
        before: CharSequence,
        after: CharSequence,
        beforeSelectionStart: Int = start,
        beforeSelectionEnd: Int = beforeSelectionStart,
        afterSelectionStart: Int = start + after.length,
        afterSelectionEnd: Int = afterSelectionStart,
        recordedAtMillis: Long = System.currentTimeMillis(),
    ) {
        if (before.isEmpty() && after.isEmpty()) return
        redo.clear()
        val beforeText = before.toString()
        val afterText = after.toString()
        val last = undo.lastOrNull()
        val isTyping = beforeText.isEmpty() && afterText.length == 1 && afterText[0] != '\n'
        val isDeleting = afterText.isEmpty() && beforeText.length == 1
        val withinGroupingWindow = lastEditAtMillis != Long.MIN_VALUE &&
            recordedAtMillis - lastEditAtMillis in 0..groupingWindowMillis
        var merged = false

        if (isTyping && lastEditWasTyping && withinGroupingWindow && last != null &&
            last.before.isEmpty() && last.start + last.after.length == start
        ) {
            undo[undo.lastIndex] = last.copy(
                after = last.after + afterText,
                afterSelectionStart = afterSelectionStart,
                afterSelectionEnd = afterSelectionEnd,
            )
            undoChars += afterText.length
            merged = true
        } else if (
            isDeleting && lastEditWasDeleting && withinGroupingWindow &&
            last != null && last.after.isEmpty()
        ) {
            when {
                start + beforeText.length == last.start -> {
                    undo[undo.lastIndex] = last.copy(
                        start = start,
                        before = beforeText + last.before,
                        afterSelectionStart = afterSelectionStart,
                        afterSelectionEnd = afterSelectionEnd,
                    )
                    undoChars += beforeText.length
                    merged = true
                }
                start == last.start -> {
                    undo[undo.lastIndex] = last.copy(
                        before = last.before + beforeText,
                        afterSelectionStart = afterSelectionStart,
                        afterSelectionEnd = afterSelectionEnd,
                    )
                    undoChars += beforeText.length
                    merged = true
                }
            }
        }

        if (!merged) {
            val operation = EditorEdit(
                start = start,
                before = beforeText,
                after = afterText,
                beforeSelectionStart = beforeSelectionStart,
                beforeSelectionEnd = beforeSelectionEnd,
                afterSelectionStart = afterSelectionStart,
                afterSelectionEnd = afterSelectionEnd,
            )
            undo.addLast(operation)
            undoChars += operation.weight
        }
        while (undo.size > 1 && (undoChars > maxChars || undo.size > maxOperations)) {
            undoChars -= undo.removeFirst().weight
        }
        lastEditWasTyping = isTyping
        lastEditWasDeleting = isDeleting
        lastEditAtMillis = recordedAtMillis
    }

    fun takeUndo(): EditorEdit? {
        val operation = undo.removeLastOrNull() ?: return null
        undoChars -= operation.weight
        redo.addLast(operation)
        resetGrouping()
        return operation
    }

    fun takeRedo(): EditorEdit? {
        val operation = redo.removeLastOrNull() ?: return null
        undo.addLast(operation)
        undoChars += operation.weight
        resetGrouping()
        return operation
    }

    private fun resetGrouping() {
        lastEditWasTyping = false
        lastEditWasDeleting = false
        lastEditAtMillis = Long.MIN_VALUE
    }
}
