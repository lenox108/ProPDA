package forpdateam.ru.forpda.presentation.theme

/**
 * Multi-back anchor loss fix (device log 239158, in-tab findpost navigation).
 *
 * A page opened via an explicit findpost / `p=` link is pushed into history with the CLICKED
 * post as its anchor (e.g. push `st=14040 anchor=132558585`). The user then scrolls within that
 * page and taps ANOTHER in-tab link; the source-anchor captured at that click describes the post
 * visible at click time (e.g. `132558226`), NOT the post the page was opened at.
 *
 * Without a guard, a trailing source-anchor snapshot (`applyLinkSourceAnchorSnapshot` /
 * `updatePageHistoryHtml`) overwrites the entry's `anchorPostId` with that click-time visible
 * post, so BACK lands on a NEIGHBOR (`pop st=14040 anchor=132558226`) instead of the exact source
 * post the user navigated from (`#entry132558585`).
 *
 * This policy decides whether the page's AUTHORITATIVE explicit-open anchor must win over a later
 * visible-anchor snapshot. It is deliberately value-based so it is a no-op for the working
 * cross-topic case (where the click-time source post EQUALS the page's authoritative anchor, e.g.
 * `143876380` == `143876380`) — there nothing changes and back-restore-in-place is preserved.
 */
internal object ThemeAuthoritativeAnchorPolicy {

    /**
     * Regression fix (device log 25_06-09-41-46, already-read/unread fresh open landed on a stale
     * pinned post with a visible scroll).
     *
     * The authoritative anchor must be recorded ONLY for a genuine IN-TAB findpost link tap — the
     * user was already inside a loaded topic and tapped an in-topic link, so the page must keep
     * restoring that exact clicked source post (the 239158 fix). It must NOT be recorded for a FRESH
     * topic OPEN (favorites / topics / unread / already-read), even though such opens internally
     * resolve through a `view=getnewpost` / `view=findpost` redirect URL (so `openedViaFindPostLink`
     * is incidentally true). Pinning the open anchor there froze the entry on the unread/last-read
     * bookmark and prevented the user's real scroll position from being saved to the return-position
     * store, so tab re-entry restored a "random" post instead of where the user actually was.
     *
     * The reliable discriminator is the open-target resolution: a genuine in-tab findpost link
     * resolves to an EXPLICIT_POST target (the user tapped a specific `view=findpost&p=` link inside
     * a loaded topic), whereas a fresh already-read / unread open resolves to READ_RESUME /
     * SERVER_UNREAD_FALLBACK / SETTING_LAST_UNREAD even though it internally rides a
     * getnewpost/findpost redirect URL (so [openedViaFindPostLink] is incidentally true). We also
     * require a non-fresh open and the findpost marker so a fresh open can never pin an anchor.
     *
     * @param openedViaFindPostLink the navigation URL carried a findpost / `p=` marker.
     * @param isFreshTopicOpen the open originated from a list (favorites/topics/tracker/...) rather
     *        than an in-session link tap. A fresh open never records an authoritative anchor.
     * @param isExplicitPostTarget the open-target resolver selected EXPLICIT_POST — the signature of
     *        a genuine in-tab findpost link tap (NOT a fresh last-read / unread bookmark open).
     * @return true only when the explicit-open anchor should be pinned as authoritative.
     */
    fun shouldRecordAuthoritativeAnchor(
            openedViaFindPostLink: Boolean,
            isFreshTopicOpen: Boolean,
            isExplicitPostTarget: Boolean,
    ): Boolean = openedViaFindPostLink && !isFreshTopicOpen && isExplicitPostTarget

    /**
     * @return the post id that must be written as the history entry's restore anchor.
     *
     * - When the page carries an authoritative explicit-open anchor AND the trailing snapshot
     *   anchor DIFFERS from it, the authoritative anchor wins (BACK returns to the source post).
     * - Otherwise the candidate snapshot anchor is used unchanged (normal scroll pages keep
     *   updating their genuine viewed anchor; the cross-topic equal-value case is unaffected).
     */
    fun resolveEntryAnchor(
            authoritativeAnchorPostId: String?,
            candidateAnchorPostId: String?,
    ): String? {
        val authoritative = authoritativeAnchorPostId?.takeIf { it.isNotBlank() }
        if (authoritative != null && shouldKeepAuthoritative(authoritative, candidateAnchorPostId)) {
            return authoritative
        }
        return candidateAnchorPostId
    }

    /**
     * @return true when a trailing visible-anchor snapshot must NOT overwrite the page's
     * authoritative explicit-open anchor. Requires a non-blank authoritative anchor and a
     * candidate that is present and DIFFERS from it. A null/blank candidate never displaces the
     * authoritative anchor; an equal candidate is a harmless no-op.
     */
    fun shouldKeepAuthoritative(
            authoritativeAnchorPostId: String?,
            candidateAnchorPostId: String?,
    ): Boolean {
        val authoritative = authoritativeAnchorPostId?.takeIf { it.isNotBlank() } ?: return false
        val candidate = candidateAnchorPostId?.takeIf { it.isNotBlank() } ?: return false
        return authoritative != candidate
    }

    /**
     * Trailing-capture overwrite fix (device log 25_06-19-16-48, cross-topic back to wrong post):
     * when computing the post id to mirror into the cross-tab [TopicReturnPositionStore], prefer
     * the durable cross-topic back snapshot (stamped at link-tap time with the click-time source
     * post, e.g. 143876380) over a trailing JS-captured visible post (the post the user happened to
     * be looking at when scrolling, e.g. 143873895). The back snapshot survives the JS source-anchor
     * TTL (15 s) — by the time the user scrolls + backgrounds the app it is the only durable record
     * of the original source post. Falls back to the page's authoritative explicit-open anchor
     * (in-tab findpost), then to the raw candidate (normal scroll pages keep updating their genuine
     * viewed anchor).
     */
    fun resolveReturnPositionPostId(
            durableBackSnapshotPostId: String?,
            pageAuthoritativeAnchorPostId: String?,
            candidateAnchorPostId: String?,
    ): String? {
        val durable = durableBackSnapshotPostId?.takeIf { it.isNotBlank() }
        if (durable != null) return durable
        val authoritative = pageAuthoritativeAnchorPostId?.takeIf { it.isNotBlank() }
        if (authoritative != null) return authoritative
        return candidateAnchorPostId
    }

    /**
     * Geometry-consistency back-snapshot guard (device log 25_06-22-18-38, cross-topic / in-tab BACK
     * lands on the wrong post 143860995 instead of the source 143876380).
     *
     * When a back snapshot is (re)captured for a page that carries an AUTHORITATIVE explicit-open
     * anchor, the snapshot post id is the authoritative post (e.g. 143876380) but the captured pixel
     * geometry (`scrollOffset`/`scrollRatio`) is whatever the page's live `scrollY` was at capture
     * time — i.e. the post the user had scrolled to and was VISUALLY looking at when they tapped the
     * link (e.g. 143860995 at y=11810). Persisting that tuple pairs the RIGHT post with the WRONG
     * pixel offset: BACK loads `#entry143876380` but then scrolls to y=11810 (143860995's screen
     * location), so the user lands on the neighbor. The `cross_topic_return_in_place` path and
     * `applyBackHistoryRestoreSnapshot` both consume that pixel offset, so the wrong geometry wins.
     *
     * @return true when the candidate back-snapshot's pixel geometry MUST NOT overwrite the page's
     * existing authoritative back snapshot. Requires a non-blank authoritative anchor and a candidate
     * visible post that is present and DIFFERS from it. When the visible post EQUALS the authoritative
     * anchor (the normal cross-topic case where the user tapped the link from the same post the page
     * was opened at) this is false and the snapshot is captured unchanged — the geometry is genuine.
     */
    fun shouldRejectAuthoritativeMismatchedBackSnapshot(
            authoritativeAnchorPostId: String?,
            candidateVisiblePostId: String?,
    ): Boolean = shouldKeepAuthoritative(authoritativeAnchorPostId, candidateVisiblePostId)
}
