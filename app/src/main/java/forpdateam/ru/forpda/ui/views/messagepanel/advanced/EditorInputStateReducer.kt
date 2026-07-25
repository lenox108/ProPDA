package forpdateam.ru.forpda.ui.views.messagepanel.advanced

/** Единое состояние взаимоисключающих способов ввода компактного редактора. */
enum class EditorInputState {
    HIDDEN,
    PANEL_OPENING,
    PANEL_OPEN,
    KEYBOARD_REQUESTED,
}

enum class EditorInputEvent {
    OPEN_PANEL,
    PANEL_READY,
    REQUEST_KEYBOARD,
    KEYBOARD_VISIBLE,
    HIDE,
}

object EditorInputStateReducer {
    fun reduce(state: EditorInputState, event: EditorInputEvent): EditorInputState = when (event) {
        EditorInputEvent.OPEN_PANEL -> EditorInputState.PANEL_OPENING
        EditorInputEvent.PANEL_READY -> when (state) {
            EditorInputState.PANEL_OPENING,
            EditorInputState.PANEL_OPEN -> EditorInputState.PANEL_OPEN
            else -> state
        }
        EditorInputEvent.REQUEST_KEYBOARD -> EditorInputState.KEYBOARD_REQUESTED
        EditorInputEvent.KEYBOARD_VISIBLE -> when (state) {
            EditorInputState.KEYBOARD_REQUESTED -> EditorInputState.HIDDEN
            else -> state
        }
        EditorInputEvent.HIDE -> EditorInputState.HIDDEN
    }
}
