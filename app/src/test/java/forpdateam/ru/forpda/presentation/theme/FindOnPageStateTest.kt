package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindOnPageStateTest {

    @Test
    fun `пустой текст даёт Clear и сбрасывает состояние`() {
        val state = FindOnPageState()

        val decision = state.onTextChanged("   ")

        assertTrue(decision is FindOnPageState.Decision.Clear)
        assertNull(state.activeQuery)
        assertEquals(0, state.matchCount)
        assertEquals(-1, state.activeMatchIndex)
        assertFalse(state.hasMatches)
    }

    @Test
    fun `непустой текст обрезается и даёт Find`() {
        val state = FindOnPageState()

        val decision = state.onTextChanged("  hello  ")

        assertTrue(decision is FindOnPageState.Decision.Find)
        assertEquals("hello", (decision as FindOnPageState.Decision.Find).query)
        assertEquals("hello", state.activeQuery)
    }

    @Test
    fun `findNext запрещён до получения результата с совпадениями`() {
        val state = FindOnPageState()
        state.onTextChanged("hello")

        // Результат ещё не пришёл — навигация запрещена.
        assertFalse(state.canFindNext())
    }

    @Test
    fun `findNext разрешён после результата с совпадениями`() {
        val state = FindOnPageState()
        state.onTextChanged("hello")

        state.onFindResult(activeMatchOrdinal = 0, numberOfMatches = 3)

        assertTrue(state.canFindNext())
        assertTrue(state.hasMatches)
        assertEquals(3, state.matchCount)
        assertEquals(0, state.activeMatchIndex)
    }

    @Test
    fun `нулевые совпадения отключают навигацию и индекс`() {
        val state = FindOnPageState()
        state.onTextChanged("zzz")

        state.onFindResult(activeMatchOrdinal = 0, numberOfMatches = 0)

        assertFalse(state.canFindNext())
        assertFalse(state.hasMatches)
        assertEquals(0, state.matchCount)
        assertEquals(-1, state.activeMatchIndex)
    }

    @Test
    fun `новый запрос обнуляет счётчик до прихода результата`() {
        val state = FindOnPageState()
        state.onTextChanged("hello")
        state.onFindResult(activeMatchOrdinal = 1, numberOfMatches = 5)

        state.onTextChanged("world")

        assertEquals("world", state.activeQuery)
        assertEquals(0, state.matchCount)
        assertEquals(-1, state.activeMatchIndex)
        assertFalse(state.canFindNext())
    }

    @Test
    fun `reset полностью очищает состояние`() {
        val state = FindOnPageState()
        state.onTextChanged("hello")
        state.onFindResult(activeMatchOrdinal = 2, numberOfMatches = 7)

        state.reset()

        assertNull(state.activeQuery)
        assertEquals(0, state.matchCount)
        assertEquals(-1, state.activeMatchIndex)
        assertFalse(state.hasMatches)
    }

    @Test
    fun `отрицательное число совпадений нормализуется в ноль`() {
        val state = FindOnPageState()
        state.onTextChanged("hello")

        state.onFindResult(activeMatchOrdinal = -1, numberOfMatches = -5)

        assertEquals(0, state.matchCount)
        assertEquals(-1, state.activeMatchIndex)
    }
}
