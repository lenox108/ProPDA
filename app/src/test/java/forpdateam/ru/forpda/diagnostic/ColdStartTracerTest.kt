package forpdateam.ru.forpda.diagnostic

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ColdStartTracerTest {

    /**
     * In-process monotonic clock for tests. Starts at a real non-zero value
     * so that the "anchor not yet set" sentinel (`-1L`) is unambiguous; this
     * is what fixed the four pre-existing failures under
     * `unitTests.returnDefaultValues = true`, where
     * `SystemClock.elapsedRealtime()` returned 0 and collided with the
     * previous "not yet set" check.
     */
    private var fakeNow: Long = 1_000L
    private val savedClock = ColdStartTracer.clock

    @Before
    fun setUp() {
        ColdStartTracer.clock = { fakeNow }
        ColdStartTracer.reset(resetAnchor = true)
    }

    @After
    fun tearDown() {
        ColdStartTracer.clock = savedClock
        ColdStartTracer.reset(resetAnchor = true)
    }

    private fun advance(ms: Long) {
        fakeNow += ms
    }

    @Test
    fun snapshot_withoutProcessStartAnchor_returnsNoAnchorMessage() {
        // markProcessStart() was not called this test (setUp reset only
        // clears marks, not the anchor — but our setUp runs before each test
        // and we never set the anchor, so this is the right precondition).
        val snap = ColdStartTracer.snapshot()
        assertNotNull(snap)
        assertTrue(
            "snapshot must report the missing-anchor state, got: $snap",
            snap.contains("no process-start anchor recorded")
        )
    }

    @Test
    fun snapshot_afterProcessStartAndNoMarks_containsTotalField() {
        ColdStartTracer.markProcessStart()
        advance(123)
        val snap = ColdStartTracer.snapshot()
        assertTrue(
            "snapshot must contain 'total=' when no marks recorded, got: $snap",
            snap.contains("total=")
        )
        assertTrue(
            "snapshot must report 'no marks' when marks list is empty, got: $snap",
            snap.contains("no marks")
        )
        assertTrue(
            "total should reflect 123ms of advance time, got: $snap",
            snap.contains("123ms")
        )
    }

    @Test
    fun snapshot_afterProcessStartAndOneMark_includesNamedMark() {
        ColdStartTracer.markProcessStart()
        advance(50)
        ColdStartTracer.mark("first")
        advance(50)
        val snap = ColdStartTracer.snapshot()
        assertTrue("snapshot must include 'first' mark, got: $snap", snap.contains("first="))
    }

    @Test
    fun reset_clearsMarksButPreservesProcessStart() {
        ColdStartTracer.markProcessStart()
        advance(10)
        ColdStartTracer.mark("beforeReset")
        advance(10)
        ColdStartTracer.reset()
        val snap = ColdStartTracer.snapshot()
        assertTrue(
            "after reset() the snapshot must say 'no marks', got: $snap",
            snap.contains("no marks")
        )
    }

    @Test
    fun mark_beyondCap_isSilentlyDropped() {
        ColdStartTracer.markProcessStart()
        // Internal MAX_MARKS is 16; insert 30.
        repeat(30) { i ->
            advance(1)
            ColdStartTracer.mark("m$i")
        }
        val snap = ColdStartTracer.snapshot()
        // We expect at most MAX_MARKS (16) marks; "m0" should be present,
        // "m29" should NOT be present.
        assertTrue(
            "first mark (m0) must be present, got: $snap",
            snap.contains("m0=")
        )
        assertFalse(
            "mark beyond cap should be dropped, but snapshot contains m29: $snap",
            snap.contains("m29=")
        )
    }
}
