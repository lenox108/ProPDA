package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDomLoadAnchorPolicyTest {

    @Test
    fun resolveBlockedScrollRestoreAnchor_prefersAnchorPostId() {
        assertEquals(
                "entry143805431",
                ThemeDomLoadAnchorPolicy.resolveBlockedScrollRestoreAnchor(
                        anchorPostId = "entry143805431",
                        pageAnchor = "entry143804585",
                )
        )
    }

    @Test
    fun resolveBlockedScrollRestoreAnchor_fallsBackToPageAnchor() {
        assertEquals(
                "entry143805431",
                ThemeDomLoadAnchorPolicy.resolveBlockedScrollRestoreAnchor(
                        anchorPostId = null,
                        pageAnchor = "entry143805431",
                )
        )
    }

    @Test
    fun resolveBlockedScrollRestoreAnchor_emptyWhenNoServerAnchor_log1121483ReadRow() {
        assertEquals(
                "",
                ThemeDomLoadAnchorPolicy.resolveBlockedScrollRestoreAnchor(
                        anchorPostId = null,
                        pageAnchor = null,
                )
        )
    }

    @Test
    fun resolveBlockedScrollRestoreAnchor_keepsAnchorForReadGetlastpost_log1121483() {
        // Log 11_06: hasUnreadTarget=false must not strip server last-read anchor.
        assertEquals(
                "143805431",
                ThemeDomLoadAnchorPolicy.resolveBlockedScrollRestoreAnchor(
                        anchorPostId = "143805431",
                        pageAnchor = "entry143805431",
                )
        )
    }

    @Test
    fun shouldArmUnreadHybridGuard_onlyWhenHasUnreadTarget() {
        assertTrue(ThemeDomLoadAnchorPolicy.shouldArmUnreadHybridGuard(hasUnreadTarget = true))
        assertFalse(ThemeDomLoadAnchorPolicy.shouldArmUnreadHybridGuard(hasUnreadTarget = false))
    }

    @Test
    fun shouldScheduleSoftAnchorScroll_readLastUnreadResume_log1121483() {
        assertTrue(
                ThemeDomLoadAnchorPolicy.shouldScheduleSoftAnchorScroll(
                        hasUnreadTarget = false,
                        loadAction = ThemeLoadAction.Normal,
                        isEndNavigation = false,
                        isRefreshNavigation = false,
                        isPostedPageScroll = false,
                        anchorPostId = "143805431",
                )
        )
    }

    @Test
    fun shouldScheduleSoftAnchorScroll_unreadBackup_log033() {
        assertTrue(
                ThemeDomLoadAnchorPolicy.shouldScheduleSoftAnchorScroll(
                        hasUnreadTarget = true,
                        loadAction = ThemeLoadAction.Normal,
                        isEndNavigation = false,
                        isRefreshNavigation = false,
                        isPostedPageScroll = false,
                        anchorPostId = "143804664",
                )
        )
    }

    @Test
    fun normalizeAnchorPostId_stripsEntryPrefix() {
        assertEquals("143805431", ThemeDomLoadAnchorPolicy.normalizeAnchorPostId("entry143805431"))
        assertEquals("143805431", ThemeDomLoadAnchorPolicy.normalizeAnchorPostId("143805431"))
    }

    @Test
    fun shouldSuppressHybridTopForInitialAnchor_onlyWhenUnread() {
        assertTrue(ThemeDomLoadAnchorPolicy.shouldSuppressHybridTopForInitialAnchor(hasUnreadTarget = true))
        assertFalse(ThemeDomLoadAnchorPolicy.shouldSuppressHybridTopForInitialAnchor(hasUnreadTarget = false))
    }

    @Test
    fun ambiguousBottomSuppressesInitialTopBootstrap() {
        assertTrue(
                ThemeDomLoadAnchorPolicy.shouldSuppressAmbiguousAllReadInitialTopBootstrap(
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = false,
                )
        )
        assertFalse(
                ThemeDomLoadAnchorPolicy.shouldSuppressAmbiguousAllReadInitialTopBootstrap(
                        ambiguousBottomRedirect = true,
                        hasUnreadTarget = true,
                )
        )
        assertFalse(
                ThemeDomLoadAnchorPolicy.shouldSuppressAmbiguousAllReadInitialTopBootstrap(
                        ambiguousBottomRedirect = false,
                        hasUnreadTarget = false,
                )
        )
    }
}
