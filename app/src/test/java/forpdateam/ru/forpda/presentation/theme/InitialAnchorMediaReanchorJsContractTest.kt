package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * S-02 (audit Finding S-02, §8.4): the scroll retry ladder ([1,120,400,900]) can
 * settle before a tall image finishes decoding, drifting the target below the
 * fold. Phase 5 adds a bounded, single re-anchor on the late media load.
 *
 * Invariants pinned (the runtime lives in `theme.js`; we model the decision and
 * keep a source tripwire):
 *  - re-anchor is armed only after an initial-anchor scroll settles;
 *  - it fires at most while the bounded window is open;
 *  - it never fires once the user has scrolled (respects the user-scroll guard);
 *  - it never fires when already near the viewport top (no redundant jump);
 *  - it cannot loop (each fire is a single instant correction; the window is
 *    bounded and a new page-load disarms it).
 */
class InitialAnchorMediaReanchorJsContractTest {

    private class MediaReanchorModel(private var nowMs: Long = 0L) {
        var anchor: String = ""
            private set
        private var until: Long = 0L
        var userScrolled: Boolean = false
        var commandInFlight: Boolean = false
        var nearViewportTop: Boolean = false
        var fireCount: Int = 0
            private set

        fun advance(ms: Long) {
            nowMs += ms
        }

        /** Armed when an initial-anchor scroll settles. */
        fun armOnSettle(name: String, windowMs: Long) {
            anchor = name
            until = nowMs + windowMs
        }

        /** A new page-load disarms any pending re-anchor. */
        fun newPageLoad() {
            anchor = ""
            until = 0L
        }

        fun onUserScroll() {
            userScrolled = true
            anchor = ""
            until = 0L
        }

        /** Mirror of `maybeReanchorInitialAfterMediaLoad`: returns whether it re-anchored. */
        fun onMediaLoaded(): Boolean {
            if (anchor.isEmpty()) return false
            if (nowMs >= until) {
                anchor = ""
                until = 0L
                return false
            }
            if (userScrolled) {
                anchor = ""
                until = 0L
                return false
            }
            if (commandInFlight) return false
            if (nearViewportTop) return false
            fireCount += 1
            return true
        }
    }

    @Test
    fun reanchorsOnLateMediaWithinWindow() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        m.nearViewportTop = false
        m.advance(400)
        assertTrue("Late media within the window must re-pin the anchor", m.onMediaLoaded())
        assertEquals(1, m.fireCount)
    }

    @Test
    fun doesNotReanchorAfterWindowElapsed() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        m.advance(2600)
        assertFalse("Past the bounded window the re-anchor must not fire", m.onMediaLoaded())
        assertEquals(0, m.fireCount)
    }

    @Test
    fun doesNotReanchorAfterUserScroll() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        m.onUserScroll()
        assertFalse("User-scroll guard must suppress the re-anchor", m.onMediaLoaded())
        assertEquals(0, m.fireCount)
    }

    @Test
    fun doesNotReanchorWhenAlreadyNearTop() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        m.nearViewportTop = true
        assertFalse("Already near top: no redundant jump", m.onMediaLoaded())
    }

    @Test
    fun doesNotReanchorWhileCommandInFlight() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        m.commandInFlight = true
        assertFalse("A live command owns the scroll; re-anchor must defer", m.onMediaLoaded())
    }

    @Test
    fun isBounded_multipleMediaLoadsCannotLoopPastWindow() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        // Several images finish within the window — each may correct once.
        m.advance(100); m.onMediaLoaded()
        m.advance(100); m.onMediaLoaded()
        // After the window, further media loads cannot fire.
        m.advance(2500)
        assertFalse(m.onMediaLoaded())
        assertEquals(2, m.fireCount)
    }

    @Test
    fun newPageLoadDisarmsPendingReanchor() {
        val m = MediaReanchorModel()
        m.armOnSettle("entry100", windowMs = 2500)
        m.newPageLoad()
        assertFalse("A new page-load must disarm the previous re-anchor", m.onMediaLoaded())
    }

    @Test
    fun jsSource_pinsMediaReanchorContract() {
        val js = readThemeJs()
        assertTrue(
                "theme.js must expose the media re-anchor entry point",
                js.contains("function maybeReanchorInitialAfterMediaLoad(")
        )
        assertTrue(
                "media load must invoke the bounded re-anchor",
                js.contains("maybeReanchorInitialAfterMediaLoad()")
        )
        assertTrue(
                "re-anchor must be armed when the initial anchor settles",
                js.contains("armThemeInitialAnchorMediaReanchor(name)")
        )
        assertTrue(
                "re-anchor must respect the user-scroll guard",
                js.contains("if (themeInfiniteScroll.userScrolled)")
        )
        assertTrue(
                "re-anchor must skip when already near the viewport top",
                js.contains("if (isThemeAnchorNearViewportTop(name)) return;")
        )
        assertTrue(
                "user input must disarm the pending re-anchor",
                js.contains("clearThemeInitialAnchorMediaReanchor();")
        )
    }

    private fun readThemeJs(): String {
        val path: Path = listOf(
                Path.of("src/main/assets/forpda/scripts/modules/theme.js"),
                Path.of("app/src/main/assets/forpda/scripts/modules/theme.js"),
        ).first { Files.exists(it) }
        return Files.newInputStream(path).bufferedReader().readText()
    }
}
