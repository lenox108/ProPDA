package forpdateam.ru.forpda.ui.fragments.theme.nativerender

/**
 * Pure resolution of "where should the topic open/restore scroll to" for the native topic
 * renderer — roadmap `native-topic-renderer.md`, Фаза 1 (the fail-fast de-risking of the
 * §2 "самый рискованный пласт": anchor/scroll).
 *
 * The whole point of going native is that an anchor becomes a DETERMINISTIC lookup of a
 * post id into the loaded list → an adapter position, which the LayoutManager pins via
 * `scrollToPositionWithOffset`. There is NO async WebView scroll race here: this class is
 * pure and synchronous, and the Android side is a thin wrapper that just calls
 * `scrollToPositionWithOffset(resolution.index, offset)`.
 *
 * Anchoring is by POST ID, never by pixel offset — so late-arriving image/WebFallback
 * heights (which resize rows after layout) cannot slide the target away (§2, §6 risk).
 */
class NativeAnchorResolver {

    /**
     * @param postIdsInOrder the ids of the currently loaded posts, in display order.
     * @param request what the caller wants to anchor on.
     */
    fun resolve(postIdsInOrder: List<Int>, request: AnchorRequest): AnchorResolution {
        if (postIdsInOrder.isEmpty()) return AnchorResolution.Empty
        return when (request) {
            AnchorRequest.Top -> AnchorResolution.Position(0)
            AnchorRequest.Bottom -> AnchorResolution.Position(postIdsInOrder.lastIndex)
            is AnchorRequest.Post -> {
                val index = postIdsInOrder.indexOf(request.postId)
                if (index >= 0) {
                    AnchorResolution.Position(index)
                } else {
                    // Target lives on a page not loaded into the list yet — the caller must
                    // load/paginate to it (server findpost / page fetch), then re-resolve.
                    AnchorResolution.PostNotLoaded(request.postId)
                }
            }
        }
    }
}

/** What the caller wants the topic to anchor on when it opens or restores. */
sealed interface AnchorRequest {
    /**
     * Anchor on a specific post id.
     * @param reason which §4 scenario this is (diagnostics / later offset policy).
     */
    data class Post(val postId: Int, val reason: Reason) : AnchorRequest {
        enum class Reason {
            /** «Открытие на первом непрочитанном» — getnewpost / unread-target. */
            FIRST_UNREAD,

            /** «Открытие на конкретном посте» — findpost / deep-link. */
            FIND_POST,

            /** «Где остановился» — restore-scroll / back-position. */
            RESTORE,
        }
    }

    /** «В начало». */
    data object Top : AnchorRequest

    /** «В конец». */
    data object Bottom : AnchorRequest
}

/** The resolved target for the LayoutManager, or a signal that more loading is needed. */
sealed interface AnchorResolution {
    /** Scroll the LayoutManager to this adapter [index]. */
    data class Position(val index: Int) : AnchorResolution

    /** The requested post is not in the loaded list; the caller must load/paginate to it. */
    data class PostNotLoaded(val postId: Int) : AnchorResolution

    /** The list is empty; nothing to anchor on. */
    data object Empty : AnchorResolution
}
