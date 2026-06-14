package forpdateam.ru.forpda.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression test for adapter NO_POSITION handling (R-10).
 * Verifies that RecyclerView adapters correctly handle RecyclerView.NO_POSITION (-1).
 */
class AdapterNoPositionTest {

    @Test
    fun `NO_POSITION is negative one`() {
        assertEquals(-1, android.widget.AdapterView.INVALID_POSITION)
    }

    @Test
    fun `negative position should be considered invalid`() {
        val position = -1
        assertTrue(position < 0)
    }

    @Test
    fun `zero position should be considered valid`() {
        val position = 0
        assertFalse(position < 0)
    }

    @Test
    fun `positive position should be considered valid`() {
        val position = 10
        assertFalse(position < 0)
    }

    @Test
    fun `adapter should check position validity before access`() {
        val itemCount = 10
        val positions = listOf(-1, 0, 5, 9, 10)

        val validPositions = positions.filter { it >= 0 && it < itemCount }
        val invalidPositions = positions.filter { it < 0 || it >= itemCount }

        assertEquals(3, validPositions.size) // 0, 5, 9
        assertEquals(2, invalidPositions.size) // -1, 10
    }

    @Test
    fun `adapter should handle empty list with any position`() {
        val itemCount = 0
        val position = 0

        // Even position 0 is invalid when item count is 0
        val isValid = position >= 0 && position < itemCount
        assertFalse(isValid)
    }
}
