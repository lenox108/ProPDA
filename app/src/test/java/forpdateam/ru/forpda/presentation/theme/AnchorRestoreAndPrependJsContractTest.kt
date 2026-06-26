package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * STEP 3 + STEP 4 JS-source contract tripwires.
 *
 * The runtime lives in `app/src/main/assets/forpda/scripts/modules/theme.js` and cannot run on
 * the JVM, so these tests are source-level tripwires that pin the contract the asset must keep:
 *
 * STEP 3 — anchor-relative restore is PRIMARY: when the anchor post IS in the DOM, the restore
 * must use its current `getBoundingClientRect().top` and IGNORE pixel/ratio fallbacks (a
 * reflow-corrupted ratio can otherwise land the viewport on the wrong post). The anchor-offset
 * branch must run regardless of `loadWasNearBottom` (as long as it's not an explicit BOTTOM
 * restore), and pixel/ratio must only run when the anchor is genuinely missing.
 *
 * STEP 4 — position-preserving top-prepend by element: `applyThemeInfinitePage('top')` must
 * pin the scroll to a captured topmost visible real post via `scrollBy(topAfter - topBefore)`
 * instead of the legacy document-height-delta formula (which drifted on late image/smile render).
 */
class AnchorRestoreAndPrependJsContractTest {

    @Test
    fun `STEP 3 restore uses anchor-offset regardless of loadWasNearBottom when anchor present`() {
        val js = readThemeJs()
        // The anchor-offset branch must not be gated on `!loadWasNearBottom` anymore.
        assertTrue(
                "anchor-offset branch must run whenever an anchor id is present (not gated on loadWasNearBottom)",
                js.contains("if (!isExplicitBottomRestore && anchorId) {")
        )
        // The old gate that suppressed the anchor-offset branch when loadWasNearBottom must be gone.
        assertFalse(
                "legacy `!window.loadWasNearBottom && anchorId` gate must be removed",
                js.contains("!window.loadWasNearBottom && anchorId && window.loadAnchorOffsetTop !== null")
        )
    }

    @Test
    fun `STEP 3 restore keeps anchorOffsetTop as intra-post offset`() {
        val js = readThemeJs()
        assertTrue(
                "anchor-offset targetY must subtract the saved intra-post offset",
                js.contains("var intraOffset = Number(window.loadAnchorOffsetTop) || 0;")
        )
    }

    @Test
    fun `STEP 4 top-prepend pins to topmost visible real post via scrollBy delta`() {
        val js = readThemeJs()
        assertTrue(
                "must capture the topmost visible real post before insert",
                js.contains("findTopmostVisibleRealThemePostForPrepend()")
        )
        assertTrue(
                "must measure the pinned element's top before insert",
                js.contains("pinnedElement.getBoundingClientRect().top")
        )
        assertTrue(
                "must scrollBy the delta of the pinned element's new top (not the height-delta formula)",
                js.contains("var pinnedTopAfter = pinnedElement.getBoundingClientRect().top;")
        )
        assertTrue(
                "must scrollBy the delta rather than scrollTo the height-delta",
                js.contains("window.scrollBy(0, delta);")
        )
    }

    @Test
    fun `STEP 4 height-delta formula is only a fallback`() {
        val js = readThemeJs()
        // The legacy height-delta formula must still exist but only as the fallback inside the
        // `else` branch (when no pinnable element was captured).
        assertTrue(
                "height-delta formula kept as a fallback",
                js.contains("window.scrollTo(0, oldY + Math.max(0, newHeight - oldHeight));")
        )
    }

    @Test
    fun `STEP 4 topmost visible post helper excludes topic hat entries`() {
        val js = readThemeJs()
        assertTrue(
                "findTopmostVisibleRealThemePostForPrepend must exclude hat entries/fixed hats",
                js.contains(":not(.topic_hat_entry):not(.topic_hat_fixed)")
        )
    }

    private fun readThemeJs(): String {
        val path: Path = listOf(
                Path.of("src/main/assets/forpda/scripts/modules/theme.js"),
                Path.of("app/src/main/assets/forpda/scripts/modules/theme.js"),
        ).firstOrNull { Files.exists(it) }
                ?: error("theme.js not found relative to working directory")
        return Files.newInputStream(path).bufferedReader().readText()
    }
}
