package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.ui.fragments.theme.nativerender.AnchorRequest.Post.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the anchor-core invariant the roadmap de-risks first (native-topic-renderer.md
 * §2/§5 Фаза 1): anchoring is a DETERMINISTIC post-id → position lookup, no async race.
 */
class NativeAnchorResolverTest {

    private val resolver = NativeAnchorResolver()
    private val page = listOf(100, 101, 102, 103, 104)

    @Test
    fun emptyList_resolvesEmpty_forEveryRequest() {
        assertEquals(AnchorResolution.Empty, resolver.resolve(emptyList(), AnchorRequest.Top))
        assertEquals(AnchorResolution.Empty, resolver.resolve(emptyList(), AnchorRequest.Bottom))
        assertEquals(
            AnchorResolution.Empty,
            resolver.resolve(emptyList(), AnchorRequest.Post(100, Reason.FIND_POST)),
        )
    }

    @Test
    fun top_isFirstPosition() {
        assertEquals(AnchorResolution.Position(0), resolver.resolve(page, AnchorRequest.Top))
    }

    @Test
    fun bottom_isLastPosition() {
        assertEquals(AnchorResolution.Position(4), resolver.resolve(page, AnchorRequest.Bottom))
    }

    @Test
    fun findPost_present_resolvesToItsIndex() {
        assertEquals(
            AnchorResolution.Position(2),
            resolver.resolve(page, AnchorRequest.Post(102, Reason.FIND_POST)),
        )
    }

    @Test
    fun firstUnread_present_resolvesToItsIndex() {
        assertEquals(
            AnchorResolution.Position(3),
            resolver.resolve(page, AnchorRequest.Post(103, Reason.FIRST_UNREAD)),
        )
    }

    @Test
    fun restore_present_resolvesToItsIndex() {
        assertEquals(
            AnchorResolution.Position(1),
            resolver.resolve(page, AnchorRequest.Post(101, Reason.RESTORE)),
        )
    }

    @Test
    fun post_absent_signalsNotLoaded_soCallerCanPaginate() {
        // 999 is on another (not-yet-loaded) page — the resolver must NOT clamp/guess a
        // position (that is exactly the class of "lands on wrong post" bugs); it defers.
        assertEquals(
            AnchorResolution.PostNotLoaded(999),
            resolver.resolve(page, AnchorRequest.Post(999, Reason.FIND_POST)),
        )
    }

    @Test
    fun reason_doesNotAffectResolvedPosition() {
        // Reason is diagnostics/offset-policy only; the resolved index is reason-independent.
        val byFind = resolver.resolve(page, AnchorRequest.Post(102, Reason.FIND_POST))
        val byUnread = resolver.resolve(page, AnchorRequest.Post(102, Reason.FIRST_UNREAD))
        val byRestore = resolver.resolve(page, AnchorRequest.Post(102, Reason.RESTORE))
        assertEquals(byFind, byUnread)
        assertEquals(byFind, byRestore)
    }

    @Test
    fun singleElementList_topAndBottomCoincide() {
        val one = listOf(500)
        assertEquals(AnchorResolution.Position(0), resolver.resolve(one, AnchorRequest.Top))
        assertEquals(AnchorResolution.Position(0), resolver.resolve(one, AnchorRequest.Bottom))
    }
}
