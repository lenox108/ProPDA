package forpdateam.ru.forpda.ui.views.messagepanel

/**
 * UI-independent state shared by compact and fullscreen presentations of one editor session.
 */
data class EditorSessionState(
    val message: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val mode: String = "full",
    val revision: Long = 0L,
)

class EditorSessionStore {
    private var current = EditorSessionState()

    fun snapshot(): EditorSessionState = current

    fun replace(
        message: String,
        selectionStart: Int = message.length,
        selectionEnd: Int = selectionStart,
        mode: String = current.mode,
    ): EditorSessionState {
        current = normalized(
            message = message,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            mode = mode,
            revision = current.revision + 1,
        )
        return current
    }

    fun updateSelection(start: Int, end: Int, mode: String = current.mode): EditorSessionState =
        replace(current.message, start, end, mode)

    private fun normalized(
        message: String,
        selectionStart: Int,
        selectionEnd: Int,
        mode: String,
        revision: Long,
    ): EditorSessionState {
        var start = selectionStart.coerceIn(0, message.length)
        var end = selectionEnd.coerceIn(0, message.length)
        if (end < start) {
            val swap = start
            start = end
            end = swap
        }
        return EditorSessionState(message, start, end, mode, revision)
    }
}
