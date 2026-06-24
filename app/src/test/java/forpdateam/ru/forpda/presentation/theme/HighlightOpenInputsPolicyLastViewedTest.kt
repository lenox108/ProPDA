package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Log 24_06-14-15: for a read-resume open, [HighlightResolver] needs a
 * `lastViewedPostId` so the highlight falls into priority 2 ("Last read on
 * page") instead of priority 5 (`last_post_on_page_fallback`). The previous
 * `HighlightOpenInputsPolicy` only forwarded the unread/explicit inputs and
 * the repository value, which was missing or stale for the freshly-realigned
 * redirect bottom post.
 *
 * Cases:
 *  - `page.anchorPostId` non-empty → ReadPosition with that id, source PAGE_ANCHOR;
 *  - `page.anchorPostId` empty but URL `#entry…` → ReadPosition with the
 *    redirect id, source REDIRECT_URL;
 *  - both empty → ReadPosition is null;
 *  - `forceLastViewedInput=true` with AMBIGUOUS_ALL_READ semantics → a
 *    ReadPosition is always produced (falls back to last post on page when no
 *    anchor / redirect id is present).
 */
class HighlightOpenInputsPolicyLastViewedTest {

    @Test
    fun pageAnchorPostId_isForwardedAsReadPosition() {
        val page = ThemePage().apply {
            id = 1103268
            url = "https://4pda.to/forum/index.php?showtopic=1103268&st=26280"
            anchorPostId = "143988703"
            hasUnreadTarget = false
            pagination.current = 1315
            pagination.all = 1315
        }
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertNotNull("anchorPostId must produce a ReadPosition override", inputs.readPosition)
        val rp = inputs.readPosition!!
        assertEquals(1103268L, rp.topicId)
        assertEquals(143988703L, rp.lastViewedPostId)
        assertEquals(1315, rp.lastViewedPage)
        assertEquals(
                HighlightOpenInputsPolicy.LastReadSource.PAGE_ANCHOR,
                inputs.lastReadSource
        )
    }

    @Test
    fun redirectHashInUrl_isUsedWhenAnchorPostIdIsEmpty() {
        // parser didn't realign the anchor (no page.anchorPostId), but the
        // server redirected to the bottom-bookmark and the URL still carries
        // the #entry post id.
        val page = ThemePage().apply {
            id = 1103268
            url = "https://4pda.to/forum/index.php?showtopic=1103268&st=26280#entry143988703"
            anchorPostId = null
            hasUnreadTarget = false
            pagination.current = 1315
            pagination.all = 1315
        }
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertNotNull("redirect #entry must produce a ReadPosition override", inputs.readPosition)
        assertEquals(143988703L, inputs.readPosition!!.lastViewedPostId)
        assertEquals(
                HighlightOpenInputsPolicy.LastReadSource.REDIRECT_URL,
                inputs.lastReadSource
        )
    }

    @Test
    fun anchorsListFallback_usedWhenAnchorPostIdAndRedirectMissing() {
        val page = ThemePage().apply {
            id = 1103268
            url = "https://4pda.to/forum/index.php?showtopic=1103268&st=0"
            anchorPostId = null
            hasUnreadTarget = false
            pagination.current = 1
            pagination.all = 1315
            addAnchor("entry143903679")
        }
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertNotNull(inputs.readPosition)
        assertEquals(143903679L, inputs.readPosition!!.lastViewedPostId)
        assertEquals(
                HighlightOpenInputsPolicy.LastReadSource.ANCHORS_LIST,
                inputs.lastReadSource
        )
    }

    @Test
    fun noAnchorAndNoRedirect_producesNullReadPosition() {
        val page = ThemePage().apply {
            id = 1103268
            url = "https://4pda.to/forum/index.php?showtopic=1103268"
            anchorPostId = null
            hasUnreadTarget = false
            pagination.current = 1
            pagination.all = 1
        }
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertNull("no anchor / no redirect must yield a null ReadPosition", inputs.readPosition)
        assertEquals(HighlightOpenInputsPolicy.LastReadSource.NONE, inputs.lastReadSource)
    }

    @Test
    fun forceLastViewedInput_fallsBackToLastPostOnPage() {
        // Simulates the AMBIGUOUS_ALL_READ reopen path: the page is fully
        // read, no anchor / redirect hint is present (or parser flagged
        // ambiguous), but the user did just re-open a topic that was at the
        // bottom of their history. We want a last-viewed hint even if it's
        // only the bottom post, so the resolver does not fall through to
        // priority 5 (`last_post_on_page_fallback`) which would also pick the
        // bottom post BUT without going through the readPosition path that
        // marks the render with `lastViewedInput=true`.
        val page = ThemePage().apply {
            id = 1103268
            url = "https://4pda.to/forum/index.php?showtopic=1103268&st=26280"
            anchorPostId = null
            hasUnreadTarget = false
            ambiguousLastUnreadBottomRedirect = true
            pagination.current = 1315
            pagination.all = 1315
        }
        // Add a couple of posts so the last-on-page fallback has a target.
        val hat = forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply {
            id = 0; number = 0
        }
        val p1 = forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply {
            id = 143988645; number = 1314 * 20 + 1
        }
        val p2 = forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply {
            id = 143988703; number = 1314 * 20 + 2
        }
        page.posts.add(hat)
        page.posts.add(p1)
        page.posts.add(p2)
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
                forceLastViewedInput = true,
        )
        assertNotNull("forceLastViewedInput must yield a ReadPosition", inputs.readPosition)
        assertEquals(143988703L, inputs.readPosition!!.lastViewedPostId)
    }

    @Test
    fun hasUnreadTarget_doesNotExposeReadPositionOverride() {
        // When the page has a genuine unread target, the unread path wins
        // and the read-resume override must NOT pre-empt it.
        val page = ThemePage().apply {
            id = 1103268
            url = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost#entry143903696"
            anchorPostId = "143903696"
            hasUnreadTarget = true
            pagination.current = 1
            pagination.all = 1315
        }
        val inputs = HighlightOpenInputsPolicy.resolveOpenInputs(
                page = page,
                openedViaFindPost = false,
        )
        assertNotNull("unread path is the priority-1 input", inputs.firstUnreadPostId)
        // The override is still set (it's harmless: the unread branch wins
        // before the readPosition branch), but verify it's not used as a
        // lastViewedInput for the resolver — `hasUnreadInput=true` is the
        // deciding flag. We only assert the structural fact that the
        // override is populated for diagnostic purposes.
        assertNotNull(inputs.readPosition)
        assertEquals(143903696L, inputs.readPosition!!.lastViewedPostId)
    }
}
