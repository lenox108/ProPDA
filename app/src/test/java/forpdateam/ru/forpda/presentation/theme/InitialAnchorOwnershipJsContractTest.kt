package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * S-01 / R-03 + R-04 (audit Findings S-01 / R-03 / R-04, §8.3 / §7.4).
 *
 * Phase 5 makes the Kotlin INITIAL_ANCHOR [ThemeScrollCommand] the single
 * authoritative owner of the initial-anchor scroll, with the JS DOM-anchor path
 * downgraded to a deterministic FALLBACK, and scopes the scroll-command id /
 * completion to the render generation so a stale completion across a reload is
 * dropped.
 *
 * The runtime lives in `app/src/main/assets/forpda/scripts/modules/theme.js` and
 * cannot run on the JVM. We therefore (a) model the *decision* of the handshake
 * + generation guard as a state machine and assert it, and (b) keep a tripwire
 * over the JS source so the contract the state machine encodes stays wired into
 * the asset.
 */
class InitialAnchorOwnershipJsContractTest {

    /**
     * Mirror of the `theme.js` DOM-anchor fallback gate
     * ([maybeRunDomInitialAnchorFallback]) and the Kotlin-command handshake.
     *
     * Invariants pinned:
     *  - while a Kotlin command is EXPECTED (handshake window in the future) the
     *    DOM fallback yields — it does NOT scroll;
     *  - once the command executes (sets the id, disarms the window) the fallback
     *    never runs;
     *  - only when the window elapses with NO command does the fallback run
     *    (preserving the safety net).
     */
    private class HandshakeStateMachine(private var nowMs: Long = 0L) {
        var expectedUntil: Long = 0L
            private set
        var commandId: String = ""
            private set
        var domFallbackRan: Boolean = false
            private set

        fun advance(ms: Long) {
            nowMs += ms
        }

        /** Kotlin announces it will own the initial-anchor scroll. */
        fun armExpectation(windowMs: Long) {
            expectedUntil = nowMs + windowMs
        }

        /** Kotlin's INITIAL_ANCHOR command arrives; it disarms the handshake. */
        fun executeCommand(id: String) {
            expectedUntil = 0L
            commandId = id
        }

        private fun isExpected(): Boolean = expectedUntil > 0L && nowMs < expectedUntil

        /**
         * Mirror of `maybeRunDomInitialAnchorFallback`: returns true only when it
         * would actually run the JS DOM scroll right now.
         */
        fun tryRunDomFallback(): Boolean {
            if (commandId.isNotEmpty()) return false
            if (isExpected()) return false
            domFallbackRan = true
            return true
        }
    }

    @Test
    fun domFallbackYields_whileKotlinCommandExpected() {
        val sm = HandshakeStateMachine()
        sm.armExpectation(700)
        // DOM event fires immediately after arming — must NOT run the fallback.
        assertFalse("DOM fallback must yield while a Kotlin command is expected", sm.tryRunDomFallback())
        assertFalse(sm.domFallbackRan)
    }

    @Test
    fun domFallbackSuppressed_afterKotlinCommandExecutes() {
        val sm = HandshakeStateMachine()
        sm.armExpectation(700)
        // Kotlin command arrives late (e.g. dispatched in onPageComplete batch).
        sm.advance(300)
        sm.executeCommand("cmd-1")
        // Even after the window would have elapsed, the command owns the scroll.
        sm.advance(1000)
        assertFalse("DOM fallback must never run once Kotlin owns the scroll", sm.tryRunDomFallback())
    }

    @Test
    fun domFallbackRuns_whenNoCommandArrivesWithinWindow() {
        val sm = HandshakeStateMachine()
        sm.armExpectation(700)
        // No command ever arrives. Before the window elapses: yield.
        sm.advance(699)
        assertFalse(sm.tryRunDomFallback())
        // After the window elapses: the safety-net fallback runs.
        sm.advance(2)
        assertTrue("DOM fallback safety net must run when no command arrives", sm.tryRunDomFallback())
    }

    @Test
    fun domFallbackRuns_whenNoHandshakeArmed() {
        // A load Kotlin does not own (e.g. no initial anchor) never arms the
        // window; the legacy DOM path runs immediately as before.
        val sm = HandshakeStateMachine()
        assertTrue(sm.tryRunDomFallback())
    }

    /**
     * Mirror of the R-04 generation guard in `maybeCompleteThemeScrollCommand`:
     * a completion is delivered only when its captured generation still matches
     * the live render generation.
     */
    private class CommandGenerationModel {
        var liveGeneration: Int = 0
            private set
        private var commandId: String = ""
        private var execGeneration: Int = 0

        /** setLoadAction bumps the generation on every new page-load. */
        fun newPageLoad() {
            liveGeneration += 1
        }

        fun executeCommand(id: String) {
            commandId = id
            execGeneration = liveGeneration
        }

        /** Returns true when the completion would be delivered (not dropped as stale). */
        fun completeDelivered(): Boolean {
            if (commandId.isEmpty()) return false
            if (execGeneration != 0 && execGeneration != liveGeneration) {
                // dropped: a reload bumped the generation between exec and complete
                commandId = ""
                execGeneration = 0
                return false
            }
            commandId = ""
            execGeneration = 0
            return true
        }
    }

    @Test
    fun completion_deliveredWhenGenerationMatches() {
        val m = CommandGenerationModel()
        m.newPageLoad()
        m.executeCommand("cmd-1")
        assertTrue("Same-generation completion must be delivered", m.completeDelivered())
    }

    @Test
    fun completion_droppedWhenReloadBumpedGeneration() {
        val m = CommandGenerationModel()
        m.newPageLoad()
        m.executeCommand("cmd-1")
        // A reload to a new topic bumps the generation before the stale
        // completion arrives.
        m.newPageLoad()
        assertFalse("Stale-generation completion must be dropped", m.completeDelivered())
    }

    @Test
    fun jsSource_pinsHandshakeAndGenerationContract() {
        val js = readThemeJs()
        // S-01/R-03: the DOM listener delegates to the fallback-only gate, which
        // honours the expectation window.
        assertTrue(
                "theme.js must expose setThemeInitialAnchorExpected",
                js.contains("function setThemeInitialAnchorExpected(")
        )
        assertTrue(
                "theme.js must expose the fallback-only DOM gate",
                js.contains("function maybeRunDomInitialAnchorFallback(")
        )
        assertTrue(
                "DOM fallback must yield while a Kotlin command is expected",
                js.contains("if (isThemeInitialAnchorExpected())")
        )
        assertTrue(
                "executeThemeScrollCommand must disarm the handshake window",
                js.contains("window.__themeInitialAnchorExpectedUntil = 0;")
        )
        // R-04: generation captured on exec and validated on completion.
        assertTrue(
                "executeThemeScrollCommand must capture the render generation",
                js.contains("window.__themeScrollCommandGenerationAtExec = Number(window.__themeScrollCommandGeneration)")
        )
        assertTrue(
                "maybeCompleteThemeScrollCommand must drop a stale-generation completion",
                js.contains("execGeneration !== 0 && execGeneration !== liveGeneration")
        )
        assertTrue(
                "setLoadAction must bump the scroll-command generation per page-load",
                js.contains("var __prevScrollGen = Number(window.__themeScrollCommandGeneration) || 0;") &&
                        js.contains("window.__themeScrollCommandGeneration = __prevScrollGen + 1;")
        )
    }

    private fun readThemeJs(): String {
        val path: Path = listOf(
                Path.of("src/main/assets/forpda/scripts/modules/theme.js"),
                Path.of("app/src/main/assets/forpda/scripts/modules/theme.js"),
        ).first { Files.exists(it) }
        return Files.newInputStream(path).bufferedReader().readText()
    }

    @Test
    fun jsSource_keepsLegacyFallbackLadderIntact() {
        // Conservative guarantee: Phase 5 must NOT remove the retry ladder or the
        // near-top de-dup the JS-facing tests rely on.
        val js = readThemeJs()
        assertTrue(js.contains("scrollToElementWithRetries(domInitialAnchor, true)"))
        assertTrue(js.contains("themeAnchorRetryPendingName === domInitialAnchor || isThemeAnchorNearViewportTop(domInitialAnchor)"))
        assertEquals(
                "SCROLL_ANCHOR_RETRY_DELAYS_MS ladder must be preserved",
                true,
                js.contains("SCROLL_ANCHOR_RETRY_DELAYS_MS = [1, 120, 400, 900]")
        )
    }
}
