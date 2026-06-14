package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.presentation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents alone QMS tab reuse (audit ID 18): navigator must rebind ids and reset WebView/VM,
 * not only focus the existing tab.
 */
class TabNavigatorQmsAloneTabPolicyTest {

    @Test
    fun qmsChatScreen_isAloneForTabReuse() {
        assertTrue(Screen.QmsChat().isAlone)
    }

    @Test
    fun isIdentityChanged_detectsDialogSwitch() {
        assertTrue(TabNavigatorQmsAloneTabPolicy.isIdentityChanged(42, 100, 42, 200))
        assertFalse(TabNavigatorQmsAloneTabPolicy.isIdentityChanged(42, 100, 42, 100))
    }

    @Test
    fun resolveReuseAction_fastSwitchWhenShellHealthy() {
        val action = TabNavigatorQmsAloneTabPolicy.resolveReuseAction(
                identityChanged = true,
                viewCreated = true,
                messagesApplied = true,
                domReady = true,
                jsBridgeReady = true,
        )
        assertEquals(TabNavigatorQmsAloneTabPolicy.ReuseAction.FAST_SWITCH, action)
    }

    @Test
    fun resolveReuseAction_fullReloadWhenBridgeStale() {
        val action = TabNavigatorQmsAloneTabPolicy.resolveReuseAction(
                identityChanged = true,
                viewCreated = true,
                messagesApplied = true,
                domReady = true,
                jsBridgeReady = false,
        )
        assertEquals(TabNavigatorQmsAloneTabPolicy.ReuseAction.FULL_RELOAD, action)
    }

    @Test
    fun resolveReuseAction_resyncStaleShellForSameDialog() {
        val action = TabNavigatorQmsAloneTabPolicy.resolveReuseAction(
                identityChanged = false,
                viewCreated = true,
                messagesApplied = false,
                domReady = true,
                jsBridgeReady = true,
        )
        assertEquals(TabNavigatorQmsAloneTabPolicy.ReuseAction.RESYNC_STALE, action)
    }

    @Test
    fun resolveReuseAction_viewNotReadyDefersWebWork() {
        val action = TabNavigatorQmsAloneTabPolicy.resolveReuseAction(
                identityChanged = true,
                viewCreated = false,
                messagesApplied = false,
                domReady = false,
                jsBridgeReady = false,
        )
        assertEquals(TabNavigatorQmsAloneTabPolicy.ReuseAction.VIEW_NOT_READY, action)
    }
}
