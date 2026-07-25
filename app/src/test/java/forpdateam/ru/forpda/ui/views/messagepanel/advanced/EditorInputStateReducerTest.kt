package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorInputStateReducerTest {

    @Test
    fun `panel and keyboard follow deterministic transitions`() {
        var state = EditorInputState.HIDDEN
        state = EditorInputStateReducer.reduce(state, EditorInputEvent.OPEN_PANEL)
        assertEquals(EditorInputState.PANEL_OPENING, state)

        state = EditorInputStateReducer.reduce(state, EditorInputEvent.PANEL_READY)
        assertEquals(EditorInputState.PANEL_OPEN, state)

        state = EditorInputStateReducer.reduce(state, EditorInputEvent.REQUEST_KEYBOARD)
        assertEquals(EditorInputState.KEYBOARD_REQUESTED, state)

        state = EditorInputStateReducer.reduce(state, EditorInputEvent.KEYBOARD_VISIBLE)
        assertEquals(EditorInputState.HIDDEN, state)
    }

    @Test
    fun `stale panel-ready event cannot reopen hidden panel`() {
        val state = EditorInputStateReducer.reduce(
            EditorInputState.HIDDEN,
            EditorInputEvent.PANEL_READY,
        )
        assertEquals(EditorInputState.HIDDEN, state)
    }
}
